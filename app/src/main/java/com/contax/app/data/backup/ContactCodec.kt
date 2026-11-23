package com.contax.app.data.backup

import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.EmailEntry
import com.contax.app.data.model.EmailLabel
import com.contax.app.data.model.ParsedContact
import com.contax.app.data.model.PhoneEntry
import com.contax.app.data.model.PhoneLabel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * VCF / CSV / JSON encode & decode for contacts.
 *
 * از java.util.Base64 استفاده می‌کنیم (نه android.util.Base64) تا کلاس روی JVM
 * هم قابل تست باشد؛ minSdk 26 کاملاً از آن پشتیبانی می‌کند.
 * دیکد با getMimeDecoder انجام می‌شود تا base64 چندخطیِ فایل‌های VCF سایر برنامه‌ها هم پذیرفته شود.
 */

/** VCF / CSV / JSON encode & decode for contacts. */
object ContactCodec {

    // ───────────────── encode ─────────────────

    fun encode(contacts: List<ParsedContact>, format: BackupFormat): String = when (format) {
        BackupFormat.VCF -> encodeVcf(contacts)
        BackupFormat.CSV -> encodeCsv(contacts)
        BackupFormat.JSON -> encodeJson(contacts)
    }

    private fun esc(s: String) = s.replace("\\", "\\\\")
        .replace("\n", "\\n").replace("\r", "")
        .replace(";", "\\;").replace(",", "\\,")

    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\;", ";")
        .replace("\\,", ",").replace("\\\\", "\\")

    private fun encodeVcf(list: List<ParsedContact>): String = buildString {
        for (c in list) {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("N:").append(esc(c.lastName)).append(";").append(esc(c.firstName)).append(";;;\r\n")
            append("FN:").append(esc(c.displayName)).append("\r\n")
            for (p in c.phones) {
                val t = when (p.label) {
                    PhoneLabel.MOBILE -> "CELL"
                    PhoneLabel.HOME -> "HOME"
                    PhoneLabel.WORK -> "WORK"
                    else -> "VOICE"
                }
                append("TEL;TYPE=").append(t).append(":").append(esc(p.number)).append("\r\n")
            }
            for (e in c.emails) {
                val t = when (e.label) {
                    EmailLabel.HOME -> "HOME"
                    EmailLabel.WORK -> "WORK"
                    else -> "INTERNET"
                }
                append("EMAIL;TYPE=").append(t).append(":").append(esc(e.email)).append("\r\n")
            }
            c.address?.let { append("ADR;TYPE=HOME:;;").append(esc(it)).append(";;;;\r\n") }
            c.note?.let { append("NOTE:").append(esc(it)).append("\r\n") }
            c.organization?.let { append("ORG:").append(esc(it)).append("\r\n") }
            c.sourceTag?.let { append("X-CONTAX-SOURCE:").append(esc(it)).append("\r\n") }
            c.photoBytes?.let {
                append("PHOTO;ENCODING=BASE64;TYPE=JPEG:")
                    .append(Base64.getEncoder().encodeToString(it))
                    .append("\r\n")
            }
            append("END:VCARD\r\n")
        }
    }

    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s

    private fun encodeCsv(list: List<ParsedContact>): String = buildString {
        append("firstName,lastName,displayName,phones,emails,organization,address,note,source\r\n")
        for (c in list) {
            val phones = c.phones.joinToString(";") { "${it.number}|${it.label.name.lowercase()}" }
            val emails = c.emails.joinToString(";") { "${it.email}|${it.label.name.lowercase()}" }
            val row = listOf(
                c.firstName, c.lastName, c.displayName, phones, emails,
                c.organization ?: "", c.address ?: "", c.note ?: "", c.sourceTag ?: "device"
            )
            append(row.joinToString(",") { csvField(it) }).append("\r\n")
        }
    }

    private fun encodeJson(list: List<ParsedContact>): String {
        val arr = JSONArray()
        for (c in list) {
            val o = JSONObject()
            o.put("firstName", c.firstName)
            o.put("lastName", c.lastName)
            o.put("displayName", c.displayName)
            o.put("phones", JSONArray().apply {
                for (p in c.phones) put(JSONObject().put("number", p.number).put("label", p.label.name.lowercase()))
            })
            o.put("emails", JSONArray().apply {
                for (e in c.emails) put(JSONObject().put("email", e.email).put("label", e.label.name.lowercase()))
            })
            o.put("organization", c.organization ?: JSONObject.NULL)
            o.put("address", c.address ?: JSONObject.NULL)
            o.put("note", c.note ?: JSONObject.NULL)
            o.put("source", c.sourceTag ?: "device")
            o.put("photoBase64", c.photoBytes?.let { Base64.getEncoder().encodeToString(it) })
            arr.put(o)
        }
        return arr.toString(2)
    }

    // ───────────────── decode ─────────────────

    fun detectFormat(text: String): BackupFormat? {
        val t = text.trimStart('\uFEFF').trimStart()
        return when {
            t.startsWith("BEGIN:VCARD", ignoreCase = true) -> BackupFormat.VCF
            t.startsWith("[") || t.startsWith("{") -> BackupFormat.JSON
            else -> {
                val first = t.lineSequence().firstOrNull()?.lowercase() ?: ""
                if (first.contains("displayname") && first.contains("phones")) BackupFormat.CSV else null
            }
        }
    }

    fun decode(text: String, format: BackupFormat): List<ParsedContact> = when (format) {
        BackupFormat.VCF -> decodeVcf(text)
        BackupFormat.CSV -> decodeCsv(text)
        BackupFormat.JSON -> decodeJson(text)
    }

    private data class Prop(val name: String, val params: List<String>, val value: String)

    private fun parseProp(line: String): Prop? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val head = line.substring(0, idx)
        val value = line.substring(idx + 1)
        val parts = head.split(';')
        return Prop(parts[0].trim().uppercase(), parts.drop(1).map { it.trim().uppercase() }, value)
    }

    /**
     * تفکیک روی جداکننده‌های «escape نشده» — دنباله‌های \; و \, و \\ را نمی‌شکند
     * تا مقادیرِ escape شده (مثل آدرس حاوی سمی‌کولون) سالم بمانند.
     */
    private fun splitUnescaped(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                cur.append(c).append(s[i + 1])
                i += 2
                continue
            }
            if (c == sep) {
                out.add(cur.toString())
                cur.clear()
                i++
                continue
            }
            cur.append(c)
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun decodeVcf(text0: String): List<ParsedContact> {
        // BOM را حذف کن (مثل detectFormat) — وگرنه «BEGIN:VCARD» اولین کارت شناخته نمی‌شود
        val text = text0.removePrefix("\uFEFF")
        // unfold folded lines (continuation starts with space/tab)
        val lines = ArrayList<String>()
        for (raw in text.split("\r\n", "\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + raw.trimStart(' ', '\t')
            } else lines.add(raw)
        }

        val out = ArrayList<ParsedContact>()
        var props: MutableList<Prop>? = null
        for (line in lines) {
            val l = line.trim()
            if (l.equals("BEGIN:VCARD", true)) {
                props = ArrayList()
                continue
            }
            if (l.equals("END:VCARD", true)) {
                props?.let { out.add(buildFromVcf(it)) }
                props = null
                continue
            }
            if (props != null && l.isNotBlank()) parseProp(l)?.let { props.add(it) }
        }
        return out
    }

    private fun buildFromVcf(props: List<Prop>): ParsedContact {
        var first = ""
        var last = ""
        var display = ""
        var address: String? = null
        var note: String? = null
        var org: String? = null
        var photo: ByteArray? = null
        var sourceTag: String? = null
        val phones = ArrayList<PhoneEntry>()
        val emails = ArrayList<EmailEntry>()

        for (p in props) {
            when {
                p.name == "N" -> {
                    // تفکیک escape-آگاه: اگر نام خودش «;» داشته باشد (به \; انکود شده) نمی‌شکند
                    val seg = splitUnescaped(p.value, ';')
                    last = unesc(seg.getOrNull(0) ?: "")
                    first = unesc(seg.getOrNull(1) ?: "")
                }
                p.name == "FN" -> display = unesc(p.value)
                p.name == "TEL" -> {
                    val label = when {
                        p.params.any { it.contains("CELL") } -> PhoneLabel.MOBILE
                        p.params.any { it.contains("HOME") } -> PhoneLabel.HOME
                        p.params.any { it.contains("WORK") } -> PhoneLabel.WORK
                        else -> PhoneLabel.MOBILE
                    }
                    val v = unesc(p.value).trim()
                    if (v.isNotBlank()) phones.add(PhoneEntry(number = v, label = label))
                }
                p.name == "EMAIL" -> {
                    val label = when {
                        p.params.any { it.contains("HOME") } -> EmailLabel.HOME
                        p.params.any { it.contains("WORK") } -> EmailLabel.WORK
                        else -> EmailLabel.HOME
                    }
                    val v = unesc(p.value).trim()
                    if (v.isNotBlank()) emails.add(EmailEntry(email = v, label = label))
                }
                p.name == "ADR" -> {
                    // ADR:;;formatted;;;; — تفکیک escape-آگاه تا آدرس حاوی «;» سالم بماند
                    val seg = splitUnescaped(p.value, ';')
                    val v = seg.getOrNull(2)?.let { unesc(it) } ?: ""
                    if (v.isNotBlank()) address = v
                }
                p.name == "NOTE" -> note = unesc(p.value).takeIf { it.isNotBlank() }
                p.name == "ORG" -> org = unesc(splitUnescaped(p.value, ';').firstOrNull() ?: "").takeIf { it.isNotBlank() }
                p.name == "X-CONTAX-SOURCE" -> sourceTag = unesc(p.value)
                p.name == "PHOTO" -> photo = try {
                    Base64.getMimeDecoder().decode(p.value)
                } catch (t: Throwable) {
                    null
                }
            }
        }
        val name = display.ifBlank { listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phones.firstOrNull()?.number ?: "?" } }
        return ParsedContact(firstName = first, lastName = last, displayName = name,
            phones = phones, emails = emails, address = address, note = note,
            organization = org, photoBytes = photo, sourceTag = sourceTag)
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val cell = StringBuilder()
        var inQ = false
        var i = 0
        val s = text.replace("\r\n", "\n")
        while (i < s.length) {
            val ch = s[i]
            when {
                inQ -> when {
                    ch == '"' && i + 1 < s.length && s[i + 1] == '"' -> { cell.append('"'); i++ }
                    ch == '"' -> inQ = false
                    else -> cell.append(ch)
                }
                ch == '"' -> inQ = true
                ch == ',' -> { row.add(cell.toString()); cell.clear() }
                ch == '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row.toList()); row.clear() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row.toList()) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun decodeCsv(text: String): List<ParsedContact> {
        val rows = parseCsvRows(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows[0].map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val out = ArrayList<ParsedContact>()
        for (r in rows.drop(1)) {
            fun get(name: String): String {
                val i = col(name)
                return if (i >= 0 && i < r.size) r[i] else ""
            }
            val phones = ArrayList<PhoneEntry>()
            for (item in get("phones").split(';')) {
                if (item.isBlank()) continue
                val seg = item.split('|')
                val number = seg.getOrNull(0)?.trim() ?: ""
                if (number.isBlank()) continue
                val label = when (seg.getOrNull(1)?.trim()?.lowercase()) {
                    "home" -> PhoneLabel.HOME
                    "work" -> PhoneLabel.WORK
                    "other" -> PhoneLabel.OTHER
                    else -> PhoneLabel.MOBILE
                }
                phones.add(PhoneEntry(number = number, label = label))
            }
            val emails = ArrayList<EmailEntry>()
            for (item in get("emails").split(';')) {
                if (item.isBlank()) continue
                val seg = item.split('|')
                val email = seg.getOrNull(0)?.trim() ?: ""
                if (email.isBlank()) continue
                val label = when (seg.getOrNull(1)?.trim()?.lowercase()) {
                    "work" -> EmailLabel.WORK
                    "other" -> EmailLabel.OTHER
                    else -> EmailLabel.HOME
                }
                emails.add(EmailEntry(email = email, label = label))
            }
            val first = get("firstName")
            val last = get("lastName")
            val display = get("displayName").ifBlank {
                listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phones.firstOrNull()?.number ?: "?" }
            }
            out.add(
                ParsedContact(
                    firstName = first, lastName = last, displayName = display,
                    phones = phones, emails = emails,
                    address = get("address").takeIf { it.isNotBlank() },
                    note = get("note").takeIf { it.isNotBlank() },
                    organization = get("organization").takeIf { it.isNotBlank() },
                    sourceTag = get("source").takeIf { it.isNotBlank() }
                )
            )
        }
        return out
    }

    private fun decodeJson(text: String): List<ParsedContact> = try {
        val arr = JSONArray(text)
        val out = ArrayList<ParsedContact>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val phones = ArrayList<PhoneEntry>()
            val pa = o.optJSONArray("phones")
            if (pa != null) for (j in 0 until pa.length()) {
                val p = pa.optJSONObject(j)
                if (p != null) {
                    val number = p.optString("number", "").trim()
                    if (number.isNotBlank()) {
                        val label = when (p.optString("label", "mobile")) {
                            "home" -> PhoneLabel.HOME
                            "work" -> PhoneLabel.WORK
                            "other" -> PhoneLabel.OTHER
                            else -> PhoneLabel.MOBILE
                        }
                        phones.add(PhoneEntry(number = number, label = label))
                    }
                } else {
                    val s = pa.optString(j, "").trim()
                    if (s.isNotBlank()) phones.add(PhoneEntry(number = s))
                }
            }
            val emails = ArrayList<EmailEntry>()
            val ea = o.optJSONArray("emails")
            if (ea != null) for (j in 0 until ea.length()) {
                val e = ea.optJSONObject(j)
                if (e != null) {
                    val email = e.optString("email", "").trim()
                    if (email.isNotBlank()) {
                        // لیبل هم برمی‌گردد (هم‌راستا با CSV/VCF) — قبلاً نادیده گرفته می‌شد
                        val label = when (e.optString("label", "home")) {
                            "work" -> EmailLabel.WORK
                            "other" -> EmailLabel.OTHER
                            else -> EmailLabel.HOME
                        }
                        emails.add(EmailEntry(email = email, label = label))
                    }
                } else {
                    val s = ea.optString(j, "").trim()
                    if (s.isNotBlank()) emails.add(EmailEntry(email = s))
                }
            }
            val first = o.optString("firstName", "")
            val last = o.optString("lastName", "")
            val display = o.optString("displayName", "").ifBlank {
                listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phones.firstOrNull()?.number ?: "?" }
            }
            out.add(
                ParsedContact(
                    firstName = first, lastName = last, displayName = display,
                    phones = phones, emails = emails,
                    address = o.optString("address", "").takeIf { it.isNotBlank() },
                    note = o.optString("note", "").takeIf { it.isNotBlank() },
                    organization = o.optString("organization", "").takeIf { it.isNotBlank() },
                    photoBytes = o.optString("photoBase64", "").takeIf { it.isNotBlank() }?.let {
                        try { Base64.getMimeDecoder().decode(it) } catch (t: Throwable) { null }
                    },
                    sourceTag = o.optString("source", "device").takeIf { it.isNotBlank() }
                )
            )
        }
        out
    } catch (t: Throwable) {
        emptyList()
    }
}
