package com.contax.app.util

import android.content.res.Configuration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * دیجیت فارسی — آگاه از زبان برنامه (Locale-aware).
 *
 * فقط در زبان فارسی، اعداد لاتین به رقم‌های فارسی تبدیل می‌شوند.
 * در زبان انگلیسی، همان اعداد لاتین بدون تغییر برگردانده می‌شوند.
 */
object Fa {
    private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** زبان جاری برنامه — پیش‌فرض فارسی */
    @Volatile
    private var localeLanguage: String = "fa"

    /**
     * همگام‌سازی زبان برنامه با کانفیگ اکتیویتی.
     * در MainActivity.onCreate (پیش از setContent) فراخوانی می‌شود؛
     * با تغییر زبان، اکتیویتی ساخته از نو می‌شود و این تابع دوباره اجرا می‌شود.
     */
    fun syncLocale(configuration: Configuration) {
        val lang = configuration.locales.get(0)?.language?.lowercase()
        localeLanguage = if (lang == "en") "en" else "fa"
    }

    /**
     * همگام‌سازی زبان برنامه از لوکیل اعمال‌شده در AppCompat
     * (منبع معتبر per-app locale؛ فقط «fa» یا «en» پشتیبانی می‌شود).
     */
    fun syncLanguage(languageTag: String?) {
        val lang = languageTag?.substringBefore('-')?.lowercase()
        localeLanguage = if (lang == "en") "en" else "fa"
    }

    /** آیا زبان فعلی برنامه فارسی است؟ */
    val isPersian: Boolean
        get() = localeLanguage.startsWith("fa")

    fun str(s: String): String {
        if (!isPersian) return s
        return buildString {
            for (c in s) append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
        }
    }

    fun num(n: Number): String = str(n.toString())
}

/**
 * Pure-Kotlin Gregorian → Jalali (Persian) date conversion.
 * No dependency on android.icu so it compiles on every SDK level.
 */
object JalaliDate {
    data class JDate(val year: Int, val month: Int, val day: Int) // month 1..12

    private val G_MONTH_DAYS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromMillis(ms: Long): JDate {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return gregorianToJalali(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun gregorianToJalali(gy0: Int, gm: Int, gd: Int): JDate {
        var gy = gy0
        val jy0 = if (gy <= 1600) 0 else 979
        gy -= if (gy <= 1600) 621 else 1600
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 365 * gy +
            (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 400) / 400 -
            80 + gd + G_MONTH_DAYS[gm - 1]
        var jy = jy0 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return JDate(jy, jm, jd)
    }
}

private val JALALI_MONTHS = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

/** فارسی: «۱ فروردین ۱۴۰۴، ۰۹:۳۰» — انگلیسی: «Jan 5, 2025 09:30» */
fun formatJalali(ms: Long, withTime: Boolean = true): String = try {
    if (Fa.isPersian) {
        val j = JalaliDate.fromMillis(ms)
        val base = "${j.day} ${JALALI_MONTHS[j.month - 1]} ${j.year}"
        if (withTime) {
            val c = Calendar.getInstance()
            c.timeInMillis = ms
            val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
            val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
            Fa.str("$base، $hh:$mm")
        } else {
            Fa.str(base)
        }
    } else {
        val pattern = if (withTime) "MMM d, yyyy HH:mm" else "MMM d, yyyy"
        SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(ms))
    }
} catch (t: Throwable) {
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(ms))
}

/** فارسی: «۱ فروردین» — انگلیسی: «Jan 5» */
fun formatJalaliShort(ms: Long): String = try {
    if (Fa.isPersian) {
        val j = JalaliDate.fromMillis(ms)
        Fa.str("${j.day} ${JALALI_MONTHS[j.month - 1]}")
    } else {
        SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(ms))
    }
} catch (t: Throwable) {
    SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(ms))
}

/** فارسی: «۲ مگابایت» — انگلیسی: «2 MB» */
fun formatSize(kb: Double): String = if (Fa.isPersian) {
    when {
        kb >= 1024 -> "${Fa.str(trim1(kb / 1024))} مگابایت"
        kb >= 1 -> "${Fa.num(kb.toInt())} کیلوبایت"
        else -> "${Fa.num((kb * 1024).toInt())} بایت"
    }
} else {
    when {
        kb >= 1024 -> "${trim1(kb / 1024)} MB"
        kb >= 1 -> "${kb.toInt()} KB"
        else -> "${(kb * 1024).toInt()} B"
    }
}

private fun trim1(v: Double): String {
    val r = Math.round(v * 10.0) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
}

/** فارسی: «۵ دقیقه پیش» — انگلیسی: «5 min ago» */
fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ms
    val minutes = diff / 60000
    return if (Fa.isPersian) {
        when {
            minutes < 1 -> "همین حالا"
            minutes < 60 -> "${Fa.num(minutes)} دقیقه پیش"
            minutes < 1440 -> "${Fa.num(minutes / 60)} ساعت پیش"
            minutes < 43200 -> "${Fa.num(minutes / 1440)} روز پیش"
            minutes < 525600 -> "${Fa.num(minutes / 43200)} ماه پیش"
            else -> formatJalali(ms, withTime = false)
        }
    } else {
        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60} hr ago"
            minutes < 43200 -> "${minutes / 1440} days ago"
            minutes < 525600 -> "${minutes / 43200} months ago"
            else -> formatJalali(ms, withTime = false)
        }
    }
}

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when (parts.size) {
        0 -> "؟"
        1 -> parts[0].take(1)
        else -> parts[0].take(1) + parts[1].take(1)
    }
}

/**
 * نمایش زیبای شماره تلفن.
 * علامت LRM (U+200E) در ابتدا و انتها باعث می‌شود شماره در متن راست‌به‌چپ فارسی،
 * چپ‌به‌راست و بدون جابه‌جایی گروه‌های عددی نمایش داده شود.
 * ارقام فقط در زبان فارسی به‌صورت فارسی نمایش داده می‌شوند.
 */
fun formatPhone(raw: String): String {
    val digits = buildString {
        for (c in raw) if (c in '0'..'9') append(c)
    }
    val grouped = when {
        digits.length == 11 && digits.startsWith("0") ->
            "${digits.substring(0, 4)} ${digits.substring(4, 7)} ${digits.substring(7, 9)} ${digits.substring(9, 11)}"
        digits.length == 12 && digits.startsWith("98") ->
            "+98 ${digits.substring(2, 5)} ${digits.substring(5, 8)} ${digits.substring(8, 12)}"
        digits.length == 10 && digits.startsWith("9") ->
            "+98 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 10)}"
        else -> raw
    }
    return "\u200E" + Fa.str(grouped) + "\u200E"
}

/** Persian-aware avatar palette index */
fun avatarColorIndex(seed: String): Int {
    var h = 0
    for (c in seed) h = h * 31 + c.code
    return Math.abs(h) % 6
}

/** Normalize phone numbers for comparison: +98/0098 → 0, strip non-digits, Persian digits → latin */
object PhoneNormalizer {
    fun normalize(raw: String): String {
        val sb = StringBuilder()
        for (c in raw) {
            when (c) {
                in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                in '0'..'9' -> sb.append(c)
            }
        }
        var s = sb.toString()
        s = when {
            s.startsWith("0098") -> "0" + s.substring(4)
            s.startsWith("98") && s.length >= 12 -> "0" + s.substring(2)
            s.startsWith("9") && s.length == 10 -> "0" + s
            else -> s
        }
        return s
    }

    fun isIrMobile(raw: String): Boolean = normalize(raw).matches(Regex("^09\\d{9}$"))
}
