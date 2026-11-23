package com.contax.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.contax.app.data.contacts.ContactsRepository
import com.contax.app.data.db.AppDatabase
import com.contax.app.data.db.BackupEntity
import com.contax.app.data.model.BackupDestination
import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.BackupInfo
import com.contax.app.data.model.CompareResult
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.EncryptionType
import com.contax.app.data.model.ParsedContact
import com.contax.app.data.model.RestoreResult
import com.contax.app.util.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Conflict { SKIP, DUPLICATE }

class BackupRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).backupDao()
    private val contactsRepo = ContactsRepository(context)

    data class NewBackup(
        val format: BackupFormat,
        val destination: BackupDestination,
        val encryption: EncryptionType,
        val password: String? = null,
        val safUri: Uri? = null,
        val contactIds: List<Long>? = null,
        val isAuto: Boolean = false,
        val name: String? = null
    )

    // ───────────────────────── create ─────────────────────────

    suspend fun create(req: NewBackup): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val list: List<Contact> = if (req.contactIds != null) {
                val all = contactsRepo.getAll()
                req.contactIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
            } else contactsRepo.getAll()
            if (list.isEmpty()) return@withContext Result.failure(IllegalStateException("no_contacts"))

            val plain = list.map { it.toParsed() }
            val text = ContactCodec.encode(plain, req.format)
            val bytes: ByteArray = when (req.encryption) {
                EncryptionType.NONE -> text.toByteArray(Charsets.UTF_8)
                EncryptionType.PASSWORD -> BackupCrypto.encryptPassword(text, req.password ?: "")
                EncryptionType.DEVICE_KEY -> BackupCrypto.encryptDeviceKey(text)
            }

            val ts = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(ts))
            val baseName = req.name ?: "contax-${req.format.ext}-$stamp"

            var fileName: String? = null
            var uriStr: String? = null
            when (req.destination) {
                BackupDestination.INTERNAL -> {
                    val dir = File(context.filesDir, "backups").apply { mkdirs() }
                    val ext = req.format.ext + if (req.encryption == EncryptionType.NONE) "" else ".enc"
                    val f = File(dir, "$baseName.$ext")
                    f.writeBytes(bytes)
                    fileName = f.name
                }
                BackupDestination.SAF -> {
                    val u = req.safUri
                        ?: return@withContext Result.failure(IllegalStateException("no_uri"))
                    val out = context.contentResolver.openOutputStream(u, "wt")
                        ?: return@withContext Result.failure(IllegalStateException("storage_unavailable"))
                    out.use { it.write(bytes) }
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            u,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (t: Throwable) { /* not persistable — still usable now */ }
                    uriStr = u.toString()
                }
            }

            val sizeKb = bytes.size / 1024.0
            val id = dao.insert(
                BackupEntity(
                    name = baseName, fileName = fileName, uri = uriStr,
                    format = req.format.name, destination = req.destination.name,
                    encryption = req.encryption.name, sizeKb = sizeKb,
                    contactCount = plain.size, isAuto = req.isAuto, createdAt = ts
                )
            )
            Result.success(
                BackupInfo(id, baseName, fileName, uriStr, req.format, req.destination,
                    req.encryption, sizeKb, plain.size, req.isAuto, ts)
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun Contact.toParsed(): ParsedContact = ParsedContact(
        firstName = firstName, lastName = lastName, displayName = displayName,
        phones = phones, emails = emails, address = address, note = note,
        organization = organization, photoBytes = photoBytes,
        sourceTag = when (val s = source) {
            is ContactSource.Device -> "device"
            is ContactSource.Sim -> "sim${s.slot}"
            is ContactSource.Account -> s.type
        }
    )

    // ───────────────────────── history ─────────────────────────

    private fun BackupEntity.toInfo() = BackupInfo(
        id = id, name = name, fileName = fileName, uri = uri,
        format = runCatching { BackupFormat.valueOf(format) }.getOrDefault(BackupFormat.VCF),
        destination = runCatching { BackupDestination.valueOf(destination) }.getOrDefault(BackupDestination.INTERNAL),
        encryption = runCatching { EncryptionType.valueOf(encryption) }.getOrDefault(EncryptionType.NONE),
        sizeKb = sizeKb, contactCount = contactCount, isAuto = isAuto, createdAt = createdAt
    )

    suspend fun history(): List<BackupInfo> = withContext(Dispatchers.IO) {
        dao.all().map { it.toInfo() }
    }

    suspend fun deleteBackup(info: BackupInfo): Unit = withContext(Dispatchers.IO) {
        dao.delete(info.id)
        info.fileName?.let {
            try { File(File(context.filesDir, "backups"), it).delete() } catch (t: Throwable) { /* ignore */ }
        }
    }

    /**
     * هرس بکاپ‌های خودکار قدیمی: فقط [keep] نسخه آخر (به‌همراه فایل‌شان) می‌ماند.
     * قبل از این، همه بکاپ‌های خودکار روی «یک فایل ثابت» overwrite می‌شدند ولی
     * هر بار یک ردیف جدید در تاریخچه ثبت می‌شد؛ حالا هر بکاپ خودکار نام یکتا دارد
     * و نسخه‌های قدیمی با فایل‌شان حذف می‌شوند.
     */
    suspend fun pruneAutoBackups(keep: Int = AUTO_KEEP): Unit = withContext(Dispatchers.IO) {
        val old = dao.autoAll().drop(keep)
        for (e in old) {
            dao.delete(e.id)
            e.fileName?.let {
                try { File(File(context.filesDir, "backups"), it).delete() } catch (t: Throwable) { /* ignore */ }
            }
        }
    }

    companion object {
        /** حداکثر تعداد بکاپ خودکار نگه‌داشته‌شده در تاریخچه */
        const val AUTO_KEEP = 10
    }

    // ───────────────────────── read / decrypt ─────────────────────────

    suspend fun readFile(info: BackupInfo): ByteArray? = withContext(Dispatchers.IO) {
        try {
            when (info.destination) {
                BackupDestination.INTERNAL -> info.fileName
                    ?.let { File(File(context.filesDir, "backups"), it) }
                    ?.takeIf { it.exists() }?.readBytes()
                BackupDestination.SAF -> info.uri?.let {
                    context.contentResolver.openInputStream(Uri.parse(it))?.use { s -> s.readBytes() }
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun decryptText(info: BackupInfo, bytes: ByteArray, password: String?): Result<String> {
        val header = if (bytes.size >= 5) String(bytes, 0, 5, Charsets.UTF_8) else ""
        val encryption = when {
            info.encryption != EncryptionType.NONE -> info.encryption
            header == BackupCrypto.MAGIC + "K" -> EncryptionType.DEVICE_KEY
            header == BackupCrypto.MAGIC -> EncryptionType.PASSWORD
            else -> EncryptionType.NONE
        }
        return when (encryption) {
            EncryptionType.NONE -> Result.success(String(bytes, Charsets.UTF_8))
            EncryptionType.PASSWORD -> {
                val pw = password ?: return Result.failure(IllegalStateException("password_required"))
                val text = BackupCrypto.decryptPassword(bytes, pw)
                    ?: return Result.failure(IllegalStateException("bad_password"))
                Result.success(text)
            }
            EncryptionType.DEVICE_KEY -> {
                val text = BackupCrypto.decryptDeviceKey(bytes)
                    ?: return Result.failure(IllegalStateException("decrypt_failed"))
                Result.success(text)
            }
        }
    }

    suspend fun preview(info: BackupInfo, password: String?): Result<List<ParsedContact>> = withContext(Dispatchers.IO) {
        try {
            val bytes = readFile(info) ?: return@withContext Result.failure(IllegalStateException("file_missing"))
            val text = decryptText(info, bytes, password).getOrThrow()
            Result.success(ContactCodec.decode(text, info.format))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ───────────────────────── restore ─────────────────────────

    suspend fun restore(
        info: BackupInfo,
        password: String?,
        selectedIndexes: List<Int>?,
        conflict: Conflict,
        target: ContactSource
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            val parsed = preview(info, password).getOrThrow()
            val chosen = if (selectedIndexes != null) {
                parsed.filterIndexed { i, _ -> selectedIndexes.contains(i) }
            } else parsed

            val existing = contactsRepo.getAll()
            val existingPhones = HashSet<String>()
            for (c in existing) for (p in c.phones) {
                val n = PhoneNormalizer.normalize(p.number)
                if (n.isNotBlank()) existingPhones.add(n)
            }

            var added = 0
            var skipped = 0
            for (pc in chosen) {
                val primary = pc.primaryPhone?.let { PhoneNormalizer.normalize(it) }
                if (conflict == Conflict.SKIP && !primary.isNullOrBlank() && existingPhones.contains(primary)) {
                    skipped++
                    continue
                }
                val pcTarget = if (target is ContactSource.Sim) pc.copy(
                    phones = pc.phones.take(1), emails = emptyList(),
                    address = null, note = null, organization = null, photoBytes = null
                ) else pc
                val res = contactsRepo.insert(pcTarget, target)
                if (res.isSuccess) {
                    added++
                    primary?.let { existingPhones.add(it) }
                } else skipped++
            }
            Result.success(RestoreResult(added, skipped, chosen.size))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ───────────────────────── compare ─────────────────────────

    suspend fun compare(info: BackupInfo, password: String?): Result<CompareResult> = withContext(Dispatchers.IO) {
        try {
            val parsed = preview(info, password).getOrThrow()
            val device = contactsRepo.getAll()

            val devicePhones = HashSet<String>()
            val deviceNames = HashSet<String>()
            for (c in device) {
                for (p in c.phones) devicePhones.add(PhoneNormalizer.normalize(p.number))
                deviceNames.add(c.displayName.trim().replace(Regex("\\s+"), " ").lowercase())
            }
            val backupPhones = HashSet<String>()
            for (pc in parsed) for (p in pc.phones) backupPhones.add(PhoneNormalizer.normalize(p.number))

            fun inDevice(pc: ParsedContact) = pc.phones.any { devicePhones.contains(PhoneNormalizer.normalize(it.number)) } ||
                deviceNames.contains(pc.displayName.trim().replace(Regex("\\s+"), " ").lowercase())
            fun inBackup(c: Contact) = c.phones.any { backupPhones.contains(PhoneNormalizer.normalize(it.number)) }

            val matched = parsed.count { inDevice(it) }
            val onlyInBackup = parsed.filter { !inDevice(it) }
            val onlyInDevice = device.filter { !inBackup(it) }
            Result.success(CompareResult(matched, onlyInBackup, onlyInDevice))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ───────────────────────── share ─────────────────────────

    fun shareUri(info: BackupInfo): Uri? = try {
        when (info.destination) {
            BackupDestination.INTERNAL -> {
                val f = info.fileName?.let { File(File(context.filesDir, "backups"), it) } ?: return null
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            }
            BackupDestination.SAF -> info.uri?.let { Uri.parse(it) }
        }
    } catch (t: Throwable) {
        null
    }
}
