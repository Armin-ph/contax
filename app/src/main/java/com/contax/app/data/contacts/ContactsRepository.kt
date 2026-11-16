package com.contax.app.data.contacts

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.DuplicateGroup
import com.contax.app.data.model.EmailEntry
import com.contax.app.data.model.EmailLabel
import com.contax.app.data.model.ParsedContact
import com.contax.app.data.model.PhoneEntry
import com.contax.app.data.model.PhoneLabel
import com.contax.app.util.PhoneNormalizer
import com.contax.app.util.avatarColorIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.abs

/**
 * Main repository for device (ContactsProvider), SIM and account contacts.
 */
class ContactsRepository(private val context: Context) {

    private val cr = context.contentResolver
    private val simHelper = SimContactsHelper(cr)

    // ─────────────────────────── permissions ───────────────────────────

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────── sources ───────────────────────────

    suspend fun sources(): List<ContactSource> = withContext(Dispatchers.IO) {
        val accounts = ArrayList<ContactSource.Account>()
        if (hasPermission()) {
            try {
                cr.query(
                    RawContacts.CONTENT_URI,
                    arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                    null, null, null
                )?.use { c ->
                    val seen = HashSet<String>()
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: ""
                        val type = c.getString(1) ?: continue
                        if (type.contains("sim", ignoreCase = true) || type == "com.android.localphone") continue
                        val key = "$name|$type"
                        if (seen.add(key)) accounts.add(ContactSource.Account(name, type))
                    }
                }
            } catch (t: Throwable) { /* ignore */ }
        }
        val sims = simHelper.availableSims().map { ContactSource.Sim(it.slot) }
        listOf<ContactSource>(ContactSource.Device) + sims + accounts
    }

    private fun accountOf(source: ContactSource): Account? = when (source) {
        is ContactSource.Account -> Account(source.name, source.type)
        else -> null
    }

    private fun simFor(slot: Int): SimContactsHelper.Sim? =
        simHelper.availableSims().firstOrNull { it.slot == slot }

    // ─────────────────────────── read ───────────────────────────

    suspend fun getAll(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) emptyList() else queryAll()
    }

    suspend fun getById(id: Long): Contact? = withContext(Dispatchers.IO) {
        if (!hasPermission()) null else queryAll().firstOrNull { it.id == id }
    }

    private fun queryAll(): List<Contact> {
        val result = ArrayList<Contact>()

        // raw contact → account info
        data class RawInfo(val contactId: Long, val accountName: String?, val accountType: String?)
        val raws = HashMap<Long, RawInfo>()
        try {
            cr.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    raws[c.getLong(0)] = RawInfo(c.getLong(1), c.getString(2), c.getString(3))
                }
            }
        } catch (t: Throwable) { /* ignore */ }

        // starred map
        val starred = HashSet<Long>()
        try {
            cr.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.STARRED), null, null, null)?.use { c ->
                while (c.moveToNext()) if (c.getInt(1) != 0) starred.add(c.getLong(0))
            }
        } catch (t: Throwable) { /* ignore */ }

        // aggregate data rows per raw contact
        class Acc {
            var contactId = 0L
            var first = ""
            var last = ""
            var display = ""
            var org: String? = null
            var note: String? = null
            var address: String? = null
            val phones = ArrayList<PhoneEntry>()
            val emails = ArrayList<EmailEntry>()
        }
        val byRaw = HashMap<Long, Acc>()

        val mimes = listOf(
            StructuredName.CONTENT_ITEM_TYPE,
            Phone.CONTENT_ITEM_TYPE,
            Email.CONTENT_ITEM_TYPE,
            Note.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE,
            StructuredPostal.CONTENT_ITEM_TYPE
        )
        val selection = "${Data.MIMETYPE} IN (${mimes.joinToString(",") { "?" }})"
        val projection = arrayOf(
            Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1,
            StructuredName.GIVEN_NAME, StructuredName.FAMILY_NAME,
            Phone.TYPE, Email.TYPE,
            StructuredPostal.FORMATTED_ADDRESS,
            Organization.COMPANY, Note.NOTE, Data.DISPLAY_NAME
        )
        try {
            cr.query(Data.CONTENT_URI, projection, selection, mimes.toTypedArray(), null)?.use { c ->
                while (c.moveToNext()) {
                    val rawId = c.getLong(0)
                    val acc = byRaw.getOrPut(rawId) { Acc() }
                    if (acc.contactId == 0L) acc.contactId = raws[rawId]?.contactId ?: 0L
                    // NOTE: switch on mimetype with explicit column reads
                    when (c.getString(1)) {
                        StructuredName.CONTENT_ITEM_TYPE -> {
                            acc.first = c.getString(3) ?: ""
                            acc.last = c.getString(4) ?: ""
                            acc.display = c.getString(10) ?: ""
                        }
                        Phone.CONTENT_ITEM_TYPE -> {
                            val number = c.getString(2)
                            if (!number.isNullOrBlank()) {
                                val t = if (!c.isNull(5)) c.getInt(5) else Phone.TYPE_MOBILE
                                acc.phones.add(PhoneEntry(number = number.trim(), label = PhoneLabel.fromType(t)))
                            }
                        }
                        Email.CONTENT_ITEM_TYPE -> {
                            val email = c.getString(2)
                            if (!email.isNullOrBlank()) {
                                val t = if (!c.isNull(6)) c.getInt(6) else Email.TYPE_HOME
                                acc.emails.add(EmailEntry(email = email.trim(), label = EmailLabel.fromType(t)))
                            }
                        }
                        Note.CONTENT_ITEM_TYPE -> acc.note = c.getString(9)?.takeIf { it.isNotBlank() }
                        Organization.CONTENT_ITEM_TYPE -> acc.org = (c.getString(8) ?: c.getString(2))?.takeIf { it.isNotBlank() }
                        StructuredPostal.CONTENT_ITEM_TYPE -> acc.address = (c.getString(7) ?: c.getString(2))?.takeIf { it.isNotBlank() }
                    }
                }
            }
        } catch (t: Throwable) { /* ignore */ }

        // build device/account contacts (skip SIM-owned raw contacts; SIM read separately)
        data class ContactBuilder(val contactId: Long) {
            var display = ""
            var first = ""
            var last = ""
            var org: String? = null
            var note: String? = null
            var address: String? = null
            var photoBytes: ByteArray? = null
            var accountName: String? = null
            var accountType: String? = null
            val phones = ArrayList<PhoneEntry>()
            val emails = ArrayList<EmailEntry>()
        }
        val builders = LinkedHashMap<Long, ContactBuilder>()
        for ((rawId, acc) in byRaw) {
            val info = raws[rawId]
            if (info == null) continue
            if (info.accountType?.contains("sim", ignoreCase = true) == true) continue
            val b = builders.getOrPut(info.contactId) { ContactBuilder(info.contactId) }
            if (b.display.isBlank() && acc.display.isNotBlank()) b.display = acc.display
            if (b.first.isBlank()) b.first = acc.first
            if (b.last.isBlank()) b.last = acc.last
            if (b.org == null) b.org = acc.org
            if (b.note == null) b.note = acc.note
            if (b.address == null) b.address = acc.address
            if (b.accountName == null && info.accountType != null) {
                b.accountName = info.accountName
                b.accountType = info.accountType
            }
            for (p in acc.phones) if (b.phones.none { PhoneNormalizer.normalize(it.number) == PhoneNormalizer.normalize(p.number) }) b.phones.add(p)
            for (e in acc.emails) if (b.emails.none { it.email.equals(e.email, true) }) b.emails.add(e)
        }

        val collator = Collator.getInstance(Locale("fa"))
        for ((_, b) in builders) {
            if (b.phones.isEmpty() && b.emails.isEmpty() && b.display.isBlank()) continue
            val display = b.display.ifBlank {
                listOf(b.first, b.last).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
                    b.phones.firstOrNull()?.number ?: "?"
                }
            }
            val source = when {
                b.accountType == null -> ContactSource.Device
                else -> ContactSource.Account(b.accountName ?: "", b.accountType!!)
            }
            result.add(
                Contact(
                    id = b.contactId,
                    firstName = b.first,
                    lastName = b.last,
                    displayName = display,
                    phones = b.phones,
                    emails = b.emails,
                    address = b.address,
                    note = b.note,
                    organization = b.org,
                    source = source,
                    avatarColor = avatarColorIndex(display),
                    isFavorite = starred.contains(b.contactId),
                    photoBytes = readPhoto(b.contactId)
                )
            )
        }

        // SIM contacts (negative synthetic ids)
        for (sim in simHelper.availableSims()) {
            simHelper.read(sim).forEachIndexed { i, (name, number) ->
                result.add(
                    Contact(
                        id = simSyntheticId(sim.slot, i),
                        displayName = name,
                        phones = listOf(PhoneEntry(number = number)),
                        source = ContactSource.Sim(sim.slot),
                        avatarColor = avatarColorIndex(name)
                    )
                )
            }
        }

        return result.sortedWith(Comparator { a, b -> collator.compare(a.displayName, b.displayName) })
    }

    private fun readPhoto(contactId: Long): ByteArray? = try {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        ContactsContract.Contacts.openContactPhotoInputStream(cr, uri, true)?.use { it.readBytes() }
    } catch (t: Throwable) {
        null
    }

    private fun simSyntheticId(slot: Int, index: Int) = -(slot * 100_000L + index)

    // ─────────────────────────── insert ───────────────────────────

    private fun buildInsertOps(pc: ParsedContact, account: Account?): ArrayList<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        val raw = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
        if (account != null) {
            raw.withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
        }
        ops.add(raw.build())

        ops.add(
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, pc.firstName)
                .withValue(StructuredName.FAMILY_NAME, pc.lastName)
                .withValue(StructuredName.DISPLAY_NAME, pc.displayName)
                .build()
        )
        for (p in pc.phones) {
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, p.number)
                    .withValue(Phone.TYPE, p.label.typeInt)
                    .build()
            )
        }
        for (e in pc.emails) {
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, e.email)
                    .withValue(Email.TYPE, e.label.typeInt)
                    .build()
            )
        }
        pc.organization?.takeIf { it.isNotBlank() }?.let {
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                    .withValue(Organization.COMPANY, it)
                    .build()
            )
        }
        pc.note?.takeIf { it.isNotBlank() }?.let {
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                    .withValue(Note.NOTE, it)
                    .build()
            )
        }
        pc.address?.takeIf { it.isNotBlank() }?.let {
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(StructuredPostal.FORMATTED_ADDRESS, it)
                    .build()
            )
        }
        pc.photoBytes?.let {
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, it)
                    .build()
            )
        }
        return ops
    }

    /** Insert a contact into the given source. SIM targets accept only name + one number. */
    suspend fun insert(pc: ParsedContact, source: ContactSource): Result<Long> = withContext(Dispatchers.IO) {
        try {
            when (source) {
                is ContactSource.Sim -> {
                    val sim = simFor(source.slot)
                        ?: return@withContext Result.failure(IllegalStateException("sim_unavailable"))
                    val ok = simHelper.insert(sim, pc.displayName, pc.primaryPhone ?: "")
                    if (ok) Result.success(-1L) else Result.failure(IllegalStateException("sim_write_failed"))
                }
                else -> {
                    if (!hasPermission()) return@withContext Result.failure(SecurityException("no_permission"))
                    val results = cr.applyBatch(ContactsContract.AUTHORITY, buildInsertOps(pc, accountOf(source)))
                    val rawUri = results[0].uri ?: return@withContext Result.failure(IllegalStateException("insert_failed"))
                    val rawId = ContentUris.parseId(rawUri)
                    var contactId = 0L
                    cr.query(
                        RawContacts.CONTENT_URI,
                        arrayOf(RawContacts.CONTACT_ID),
                        "${RawContacts._ID}=?",
                        arrayOf(rawId.toString()), null
                    )?.use { c -> if (c.moveToFirst()) contactId = c.getLong(0) }
                    Result.success(contactId)
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ─────────────────────────── update ───────────────────────────

    private val clearableMimes = listOf(
        StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
        Note.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
        Photo.CONTENT_ITEM_TYPE
    )

    /** Rebuild all data rows of an existing device/account contact (keeps raw contact & account). */
    suspend fun update(contactId: Long, pc: ParsedContact): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!hasPermission()) return@withContext Result.failure(SecurityException("no_permission"))
            val rawIds = ArrayList<Long>()
            cr.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID}=?", arrayOf(contactId.toString()), null
            )?.use { c -> while (c.moveToNext()) rawIds.add(c.getLong(0)) }
            if (rawIds.isEmpty()) return@withContext Result.failure(IllegalStateException("not_found"))

            val ops = ArrayList<ContentProviderOperation>()
            for (rid in rawIds) {
                for (mime in clearableMimes) {
                    ops.add(
                        ContentProviderOperation.newDelete(Data.CONTENT_URI)
                            .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(rid.toString(), mime))
                            .build()
                    )
                }
            }
            val first = rawIds.first()
            fun dataOp(vararg values: Pair<String, Any?>): ContentProviderOperation.Builder {
                val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, first)
                for ((k, v) in values) b.withValue(k, v)
                return b
            }
            ops.add(
                dataOp(Data.MIMETYPE to StructuredName.CONTENT_ITEM_TYPE,
                    StructuredName.GIVEN_NAME to pc.firstName,
                    StructuredName.FAMILY_NAME to pc.lastName,
                    StructuredName.DISPLAY_NAME to pc.displayName).build()
            )
            for (p in pc.phones) {
                ops.add(dataOp(Data.MIMETYPE to Phone.CONTENT_ITEM_TYPE,
                    Phone.NUMBER to p.number, Phone.TYPE to p.label.typeInt).build())
            }
            for (e in pc.emails) {
                ops.add(dataOp(Data.MIMETYPE to Email.CONTENT_ITEM_TYPE,
                    Email.ADDRESS to e.email, Email.TYPE to e.label.typeInt).build())
            }
            pc.organization?.takeIf { it.isNotBlank() }?.let {
                ops.add(dataOp(Data.MIMETYPE to Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to it).build())
            }
            pc.note?.takeIf { it.isNotBlank() }?.let {
                ops.add(dataOp(Data.MIMETYPE to Note.CONTENT_ITEM_TYPE, Note.NOTE to it).build())
            }
            pc.address?.takeIf { it.isNotBlank() }?.let {
                ops.add(dataOp(Data.MIMETYPE to StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.FORMATTED_ADDRESS to it).build())
            }
            pc.photoBytes?.let {
                ops.add(dataOp(Data.MIMETYPE to Photo.CONTENT_ITEM_TYPE, Photo.PHOTO to it).build())
            }
            cr.applyBatch(ContactsContract.AUTHORITY, ops)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** SIM contacts can only change name & single number. */
    suspend fun updateSim(contact: Contact, newName: String, newNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        val s = contact.source as? ContactSource.Sim
            ?: return@withContext Result.failure(IllegalStateException("not_sim"))
        val sim = simFor(s.slot) ?: return@withContext Result.failure(IllegalStateException("sim_unavailable"))
        val oldName = contact.displayName
        val oldNumber = contact.primaryPhone ?: ""
        // ۱) ویرایش درجای سیم‌کارت (فرمت صحیح IccProvider) — جای رکورد عوض نمی‌شود
        if (simHelper.update(sim, oldName, oldNumber, newName, newNumber)) {
            return@withContext Result.success(Unit)
        }
        // ۲) پشتیبان: حذف + درج
        val del = simHelper.delete(sim, oldName, oldNumber)
        val ins = if (del) simHelper.insert(sim, newName, newNumber) else false
        if (ins) Result.success(Unit)
        else Result.failure(IllegalStateException("sim_write_failed"))
    }

    // ─────────────────────────── delete / favorite ───────────────────────────

    suspend fun delete(ids: List<Long>): Result<Int> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext Result.failure(SecurityException("no_permission"))
        val all = queryAll()
        var n = 0
        var attempted = 0
        var simFailure = false
        for (id in ids) {
            val c = all.firstOrNull { it.id == id } ?: continue
            attempted++
            try {
                when (val s = c.source) {
                    is ContactSource.Sim -> {
                        val sim = simFor(s.slot)
                        if (sim == null || !simHelper.delete(sim, c.displayName, c.primaryPhone ?: "")) {
                            simFailure = true
                        } else n++
                    }
                    else -> {
                        var d = cr.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), null, null)
                        if (d == 0) {
                            // مسیر پشتیبان: حذف همه raw contactهای وابسته به این مخاطب
                            d = cr.delete(
                                RawContacts.CONTENT_URI,
                                "${RawContacts.CONTACT_ID}=?",
                                arrayOf(id.toString())
                            )
                        }
                        n += d
                    }
                }
            } catch (t: Throwable) {
                if (c.source is ContactSource.Sim) simFailure = true
                /* ادامه */
            }
        }
        // اگر هیچ ردیفی حذف نشد، شکست واقعی است — نه پیام موفقیت دروغین
        if (n == 0 && attempted > 0) {
            val code = if (simFailure) "sim_write_failed" else "delete_failed"
            Result.failure(IllegalStateException(code))
        } else
            Result.success(n)
    }

    suspend fun setFavorite(id: Long, fav: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (id < 0) return@withContext Result.failure(IllegalStateException("not_supported"))
            val cv = ContentValues().apply { put(Contacts.STARRED, if (fav) 1 else 0) }
            cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), cv, null, null)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ─────────────────────────── copy / move ───────────────────────────

    data class TransferResult(val ok: Int, val failed: Int)

    suspend fun copyOrMove(ids: List<Long>, target: ContactSource, move: Boolean): TransferResult = withContext(Dispatchers.IO) {
        val all = queryAll()
        var ok = 0
        var failed = 0
        for (id in ids) {
            val c = all.firstOrNull { it.id == id } ?: continue
            val toSim = target is ContactSource.Sim
            val pc = ParsedContact(
                firstName = c.firstName,
                lastName = c.lastName,
                displayName = c.displayName,
                phones = if (toSim) c.phones.take(1) else c.phones,
                emails = if (toSim) emptyList() else c.emails,
                address = if (toSim) null else c.address,
                note = if (toSim) null else c.note,
                organization = if (toSim) null else c.organization,
                photoBytes = if (toSim) null else c.photoBytes
            )
            val res = insert(pc, target)
            if (res.isSuccess) {
                ok++
                if (move) delete(listOf(id))
            } else failed++
        }
        TransferResult(ok, failed)
    }

    // ─────────────────────────── merge & duplicates ───────────────────────────

    private fun fillScore(c: Contact): Int =
        c.phones.size + c.emails.size +
            listOfNotNull(c.address, c.note, c.organization).size +
            if (c.photoBytes != null) 1 else 0

    suspend fun merge(ids: List<Long>): Result<Contact?> = withContext(Dispatchers.IO) {
        try {
            if (!hasPermission()) return@withContext Result.failure(SecurityException("no_permission"))
            val all = queryAll()
            val group = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
            if (group.size < 2) return@withContext Result.failure(IllegalStateException("need_two"))

            // نگه‌دارنده ترجیحاً مخاطب دستگاه/حساب است؛ مخاطب سیم‌کارت قابل بازنویسی کامل نیست
            // و اگر همه اعضا سیم‌کارتی باشند، از بین خودشان بهترین انتخاب می‌شود.
            val deviceMembers = group.filter { it.source !is ContactSource.Sim }
            val keeper = (if (deviceMembers.isNotEmpty()) deviceMembers else group)
                .maxByOrNull { fillScore(it) } ?: group.first()

            val phones = LinkedHashMap<String, PhoneEntry>()
            for (c in group) for (p in c.phones) {
                val k = PhoneNormalizer.normalize(p.number)
                if (k.isNotBlank() && !phones.containsKey(k)) phones[k] = p
            }
            val emails = LinkedHashMap<String, EmailEntry>()
            for (c in group) for (e in c.emails) {
                if (!emails.containsKey(e.email.lowercase())) emails[e.email.lowercase()] = e
            }
            val merged = ParsedContact(
                firstName = keeper.firstName,
                lastName = keeper.lastName,
                displayName = keeper.displayName,
                phones = phones.values.toList(),
                emails = emails.values.toList(),
                address = group.mapNotNull { it.address }.maxByOrNull { it.length },
                note = group.mapNotNull { it.note }.maxByOrNull { it.length },
                organization = group.mapNotNull { it.organization }.maxByOrNull { it.length },
                photoBytes = group.firstNotNullOfOrNull { it.photoBytes }
            )

            val upd = if (keeper.source is ContactSource.Sim) {
                // سیم‌کارت فقط یک شماره نگه می‌دارد؛ فقط اگر نام/شماره نهایی فرق کرده باشد بازنویسی می‌کنیم
                val finalName = merged.displayName.ifBlank { keeper.displayName }
                val finalNumber = merged.primaryPhone ?: keeper.primaryPhone ?: ""
                if (finalName != keeper.displayName ||
                    (finalNumber.isNotBlank() && finalNumber != keeper.primaryPhone)
                ) {
                    updateSim(keeper, finalName, finalNumber)
                } else {
                    Result.success(Unit)
                }
            } else {
                update(keeper.id, merged)
            }
            if (upd.isFailure) return@withContext Result.failure(upd.exceptionOrNull() ?: IllegalStateException("merge_failed"))

            val others = group.filter { it.id != keeper.id }.map { it.id }
            val del = delete(others)
            if (del.isFailure || del.getOrDefault(0) < others.size) {
                // ادغام ناقص: نگه‌دارنده به‌روز شد ولی حذف برخی تکراری‌ها شکست خورد —
                // نباید پیام موفقیت بدهیم وگرنه کاربر فکر می‌کند کار انجام شده است
                val code = if (del.exceptionOrNull()?.message == "sim_write_failed")
                    "sim_write_failed" else "merge_partial"
                return@withContext Result.failure(IllegalStateException(code))
            }
            Result.success(getById(keeper.id))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun findDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val all = queryAll()
        // union-find over contact ids
        val parent = HashMap<Long, Long>()
        fun find(x: Long): Long {
            var root = x
            while (parent[root] != root) root = parent[root] ?: root
            var cur = x
            while (parent[cur] != null && parent[cur] != cur) {
                val next = parent[cur]!!
                parent[cur] = root
                cur = next
            }
            return root
        }
        fun union(a: Long, b: Long) {
            parent.putIfAbsent(a, a); parent.putIfAbsent(b, b)
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val byPhone = HashMap<String, MutableList<Long>>()
        val byName = HashMap<String, MutableList<Long>>()
        val map = HashMap<Long, Contact>()
        for (c in all) {
            map[c.id] = c
            parent.putIfAbsent(c.id, c.id)
            val p = c.primaryPhone?.let { PhoneNormalizer.normalize(it) }
            if (!p.isNullOrBlank()) byPhone.getOrPut(p) { ArrayList() }.add(c.id)
            val nk = c.displayName.trim().replace(Regex("\\s+"), " ").lowercase()
            if (nk.isNotBlank()) byName.getOrPut(nk) { ArrayList() }.add(c.id)
        }
        val hasPhoneDup = HashSet<Long>()
        for ((_, members) in byPhone) if (members.size >= 2) {
            hasPhoneDup.addAll(members)
            for (i in 1 until members.size) union(members[0], members[i])
        }
        for ((_, members) in byName) if (members.size >= 2) {
            for (i in 1 until members.size) union(members[0], members[i])
        }

        val groupsMap = LinkedHashMap<Long, MutableList<Contact>>()
        for (c in all) {
            val root = find(c.id)
            groupsMap.getOrPut(root) { ArrayList() }.add(c)
        }
        val groups = groupsMap.values.filter { it.size >= 2 }.mapIndexed { idx, members ->
            val reason = if (members.any { hasPhoneDup.contains(it.id) }) DuplicateGroup.Reason.PHONE else DuplicateGroup.Reason.NAME
            DuplicateGroup(
                key = "g$idx",
                reason = reason,
                contacts = members.sortedWith(Comparator { a, b -> a.displayName.compareTo(b.displayName) })
            )
        }.sortedByDescending { it.contacts.size }
        groups
    }
}
