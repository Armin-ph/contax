package com.contax.app.data.contacts

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri

/**
 * Reads/writes SIM card contacts through the ICC ADN content provider.
 * Different OEMs expose different URIs, so we probe a list of known candidates.
 *
 * نکتهٔ مهم دربارهٔ IccProvider (اندروید خام):
 * این provider برخلاف بقیه، از «?» و whereArgs پشتیبانی نمی‌کند؛ رشتهٔ where را
 * به‌صورت متنی تجزیه می‌کند: جداسازی روی " AND " و "=" و مقدارها باید داخل
 * گیومهٔ تک ('...') به‌صورت literal داده شوند. فرمت صحیح:
 *     tag='Ali' AND number='09121234567'
 * بنابراین همهٔ عملیات حذف/ویرایش اول با همین فرمت literal امتحان می‌شوند و
 * در صورت نیاز، فرمت SQL استاندارد (برای رام‌های OEM که موتور SQL واقعی دارند)
 * به‌عنوان پشتیبان اجرا می‌شود. بعد از هر تلاش، با خواندن مجدد سیم‌کارت
 * تأیید می‌شود که تغییر واقعاً اعمال شده است.
 */
class SimContactsHelper(private val cr: ContentResolver) {

    data class Sim(val slot: Int, val uri: Uri)

    private val sim1Uris = listOf(
        "content://icc/adn",
        "content://sim/adn",
        "content://gsm-sim/adn"
    )
    private val sim2Uris = listOf(
        "content://iccmsim/adn",
        "content://iccmsim2/adn",
        "content://icc/adn_sub2",
        "content://gsm-sim2/adn",
        "content://icc2/adn"
    )

    /** Probe known URIs; a queryable provider counts as an available SIM. */
    fun availableSims(): List<Sim> {
        val out = ArrayList<Sim>()
        for (u in sim1Uris) if (probe(u)) { out.add(Sim(1, Uri.parse(u))); break }
        for (u in sim2Uris) if (probe(u)) { out.add(Sim(2, Uri.parse(u))); break }
        return out
    }

    private fun probe(uriStr: String): Boolean = try {
        cr.query(Uri.parse(uriStr), arrayOf("name"), null, null, null)?.use { it.count >= 0 } == true
    } catch (t: Throwable) {
        false
    }

    /** @return list of (name, number) */
    fun read(sim: Sim): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            cr.query(sim.uri, arrayOf("name", "number"), null, null, null)?.use { c ->
                val ni = c.getColumnIndex("name")
                val ii = c.getColumnIndex("number")
                while (c.moveToNext()) {
                    val n = if (ni >= 0) c.getString(ni) else null
                    val num = if (ii >= 0) c.getString(ii) else null
                    if (!n.isNullOrBlank() && !num.isNullOrBlank()) out.add(n.trim() to num.trim())
                }
            }
        } catch (t: Throwable) { /* SIM not ready */ }
        return out
    }

    /**
     * مقدار را برای where-clause سبک IccProvider داخل گیومهٔ تک می‌پیچد.
     * تجزیه‌گر AOSP فقط کاراکتر اول و آخر را حذف می‌کند، پس گیومهٔ داخلیِ
     * (مثلاً در O'Brien) بدون آسیب باقی می‌ماند.
     */
    private fun q(s: String): String = "'$s'"

    fun insert(sim: Sim, name: String, number: String): Boolean = try {
        val v = ContentValues().apply {
            put("name", name)
            put("number", number)
        }
        cr.insert(sim.uri, v) != null
    } catch (t: Throwable) {
        try {
            // فرمت کلاسیک AOSP: کلید tag
            val v = ContentValues().apply {
                put("tag", name)
                put("number", number)
            }
            cr.insert(sim.uri, v) != null
        } catch (t2: Throwable) {
            try {
                // برخی رام‌های قدیمی OEM کلیدهای بزرگ‌دار
                val v = ContentValues().apply {
                    put("TAG", name)
                    put("NUMBER", number)
                }
                cr.insert(sim.uri, v) != null
            } catch (t3: Throwable) {
                false
            }
        }
    }

    /**
     * حذف مخاطب سیم‌کارت — مقاوم در برابر تفاوت پیاده‌سازی OEMها.
     *
     * ترتیب تلاش‌ها:
     *  ۱) فرمت literal گیومه‌دار سبک AOSP با کلید tag و بعد name
     *     (فرمت صحیح IccProvider اندروید خام — whereArgs پشتیبانی نمی‌شود)
     *  ۲) فرمت SQL استاندارد با «?» و whereArgs برای رام‌های OEM
     * بعد از هر تلاش با خواندن دوبارهٔ سیم‌کارت تأیید می‌شود که رکورد واقعاً حذف شده است.
     */
    fun delete(sim: Sim, name: String, number: String): Boolean {
        val hasName = name.isNotBlank()
        val hasNum = number.isNotBlank()

        // ── حالت ۱: literal گیومه‌دار (سبک IccProvider اندروید خام) ──
        val literalAttempts = buildList {
            if (hasName && hasNum) {
                add("tag=${q(name)} AND number=${q(number)}")
                add("name=${q(name)} AND number=${q(number)}")
            }
            if (hasName) {
                add("tag=${q(name)}")
                add("name=${q(name)}")
            }
            if (hasNum) {
                add("number=${q(number)}")
            }
        }
        for (where in literalAttempts) {
            try {
                cr.delete(sim.uri, where, null)
            } catch (t: Throwable) { /* تلاش بعدی */ }
            if (!stillExists(sim, name, number)) return true
        }

        // ── حالت ۲: SQL استاندارد با placeholder (برخی رام‌های OEM) ──
        data class SqlAttempt(val where: String, val args: Array<String>)
        val sqlAttempts = buildList {
            if (hasName && hasNum) {
                add(SqlAttempt("name=? AND number=?", arrayOf(name, number)))
                add(SqlAttempt("TAG=? AND NUMBER=?", arrayOf(name, number)))
            }
            if (hasName) {
                add(SqlAttempt("name=?", arrayOf(name)))
                add(SqlAttempt("TAG=?", arrayOf(name)))
            }
            if (hasNum) {
                add(SqlAttempt("number=?", arrayOf(number)))
                add(SqlAttempt("NUMBER=?", arrayOf(number)))
            }
        }
        for (a in sqlAttempts) {
            try {
                cr.delete(sim.uri, a.where, a.args)
            } catch (t: Throwable) { /* تلاش بعدی */ }
            if (!stillExists(sim, name, number)) return true
        }

        return false
    }

    /**
     * ویرایش درجای مخاطب سیم‌کارت (name/number جدید).
     * IccProvider اندروید خام مقادیر جدید را هم از where می‌خواند با کلیدهای
     * newTag/newName و newNumber؛ برخی OEMها آن‌ها را از ContentValues می‌خوانند.
     * اگر هیچ‌کدام جواب نداد، false برمی‌گردد تا تماس‌گیرنده مسیر حذف+درج را برود.
     */
    fun update(
        sim: Sim,
        oldName: String,
        oldNumber: String,
        newName: String,
        newNumber: String
    ): Boolean {
        // ۱) سبک AOSP: همهٔ پارامترها در where (کلیدهای newTag/newNumber)
        try {
            val where = "tag=${q(oldName)} AND number=${q(oldNumber)} " +
                "AND newTag=${q(newName)} AND newNumber=${q(newNumber)}"
            cr.update(sim.uri, ContentValues(), where, null)
            if (updatedOk(sim, oldName, oldNumber, newName, newNumber)) return true
        } catch (t: Throwable) { /* تلاش بعدی */ }

        // ۲) سبک OEM: مقادیر جدید در ContentValues، شرط قدیمی با placeholder
        try {
            val cv = ContentValues().apply {
                put("newTag", newName)
                put("newNumber", newNumber)
            }
            cr.update(sim.uri, cv, "tag=? AND number=?", arrayOf(oldName, oldNumber))
            if (updatedOk(sim, oldName, oldNumber, newName, newNumber)) return true
        } catch (t: Throwable) { /* تلاش بعدی */ }

        // ۳) همان سبک OEM ولی با کلیدهای newName/newNumber (بعضی رام‌ها)
        try {
            val cv = ContentValues().apply {
                put("newName", newName)
                put("newNumber", newNumber)
            }
            cr.update(sim.uri, cv, "name=? AND number=?", arrayOf(oldName, oldNumber))
            if (updatedOk(sim, oldName, oldNumber, newName, newNumber)) return true
        } catch (t: Throwable) { /* تلاش بعدی */ }

        return false
    }

    /** تأیید ویرایش: رکورد جدید موجود و رکورد قدیمی (در صورت تفاوت) حذف شده باشد */
    private fun updatedOk(
        sim: Sim,
        oldName: String,
        oldNumber: String,
        newName: String,
        newNumber: String
    ): Boolean {
        val newThere = stillExists(sim, newName, newNumber)
        val oldGone = if (oldName == newName && oldNumber == newNumber) true
        else !stillExists(sim, oldName, oldNumber)
        return newThere && oldGone
    }

    /** آیا این رکورد (نام/شماره با نرمال‌سازی) هنوز روی سیم‌کارت هست؟ */
    private fun stillExists(sim: Sim, name: String, number: String): Boolean {
        fun norm(s: String) = s.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val nameKey = name.trim().lowercase()
        val numKey = norm(number)
        return read(sim).any { (n, num) ->
            val nameMatch = nameKey.isBlank() || n.trim().lowercase() == nameKey
            val numMatch = numKey.isBlank() || norm(num) == numKey
            nameMatch && numMatch
        }
    }
}
