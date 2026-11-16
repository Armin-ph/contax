package com.contax.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.contax.app.util.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * اطلاعات یک نسخه جدید منتشرشده در GitHub Releases.
 * متن changelog از توضیحات (body) همان Release خوانده می‌شود.
 */
data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val apkUrl: String?,
    val releaseUrl: String
)

object UpdateChecker {

    /** مقایسه نسخه‌ها به‌صورت عددی؛ true یعنی [remote] جدیدتر از [local] است */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.trim().toLongOrNull() ?: 0L }
        val l = local.split(".").map { it.trim().toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0L }
            val b = l.getOrElse(i) { 0L }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * آخرین Release مخزن را از GitHub API می‌خواند.
     * اگر نسخه جدیدی نسبت به نسخه نصب‌شده وجود داشته باشد UpdateInfo برمی‌گرداند وگرنه null.
     *
     * نحوه انتشار نسخه جدید (راهنمای کامل: GITHUB_RELEASE_GUIDE.md):
     * ۱) در مخزن گیت‌هاب یک Release با تگ مثلاً v1.0.3 بسازید
     * ۲) فایل APK را به‌عنوان asset ضمیمه کنید (باید پسوند .apk داشته باشد)
     * ۳) متن تغییرات را در توضیحات Release بنویسید — همین متن در پاپ‌آپ کاربر نمایش داده می‌شود
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(AppInfo.latestReleaseApiUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Contax-App")
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tag = json.optString("tag_name")
                .removePrefix("v").removePrefix("V").trim()
            if (tag.isEmpty() || !isNewer(tag, AppInfo.versionName)) return@withContext null

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            UpdateInfo(
                versionName = tag,
                changelog = json.optString("body").ifBlank { "—" },
                apkUrl = apkUrl,
                releaseUrl = json.optString("html_url").ifBlank { AppInfo.GITHUB_URL }
            )
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

/** دانلود فایل APK نسخه جدید و ارسال آن به نصاب اندروید */
object UpdateInstaller {

    /**
     * APK را در کش برنامه دانلود می‌کند و بعد از اتمام، صفحه نصب را باز می‌کند.
     * اگر نصب مستقیم ممکن نباشد، صفحه Release در مرورگر باز می‌شود.
     */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val downloadedFile = withContext(Dispatchers.IO) {
            val url = info.apkUrl ?: throw IllegalStateException("no_apk_url")
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val out = File(dir, "contax-${info.versionName}.apk")
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                if (conn.responseCode != 200) {
                    throw IllegalStateException("download_failed_${conn.responseCode}")
                }
                conn.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out
            } finally {
                conn?.disconnect()
            }
        }

        withContext(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", downloadedFile
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(install)
            } catch (t: Throwable) {
                // fallback: باز کردن صفحه انتشار در مرورگر
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (t2: Throwable) { /* هیچ مرورگری نیست */ }
            }
        }
    }
}
