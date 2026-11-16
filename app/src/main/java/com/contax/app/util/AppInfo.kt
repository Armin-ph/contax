package com.contax.app.util

import com.contax.app.BuildConfig

/** اطلاعات یک ولت کریپتو برای نمایش در پنجره حمایت مالی */
data class CryptoWallet(val label: String, val address: String)

/**
 * ─────────────────────────────────────────────────────────────
 *  اطلاعات برنامه و لینک‌های سازنده
 *
 *  ⚠️ موارد زیر را هنگام انتشار نهایی به‌روزرسانی کنید:
 *     GITHUB_URL / REPO_OWNER / REPO_NAME  ← مخزن گیتهاب برنامه (برای آپدیت‌چکر)
 *
 *  توجه: اگر OWNER یا NAME مخزن را عوض کنید، آپدیت‌چکر داخلی
 *  هم به‌صورت خودکار از همین مخزن نسخه‌های جدید را می‌خواند.
 * ─────────────────────────────────────────────────────────────
 */
object AppInfo {

    /** نسخه فعلی برنامه — مستقیماً از build.gradle.kts خوانده می‌شود */
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    /** سایت رسمی سازنده */
    const val WEBSITE_URL = "http://armidev.ir"

    /** کانال تلگرام سازنده */
    const val TELEGRAM_URL = "https://t.me/armidev"

    /** حمایت مالی — پرداخت ریالی (درگاه دونیت) */
    const val DONATE_RIAL_URL = "https://donito.me/armidev"

    /** حمایت مالی — ولت‌های کریپتو (در پنجره «پرداخت با کریپتو» نمایش داده می‌شوند) */
    val CRYPTO_WALLETS = listOf(
        CryptoWallet("USDT (Bep20)", "0x9d72f2F36B7359Db21597E924a56a9b4896c9Ec1"),
        CryptoWallet("BNB", "0x9d72f2F36B7359Db21597E924a56a9b4896c9Ec1"),
        CryptoWallet("Gram (Tonchain)", "UQD2MR9CGSTUcq4jv6RIpUtKg1x20pzuFEg-gO1f5-XGPf3i"),
        CryptoWallet("Doge", "DA1v8sYLPnGdFJo6wc4Zadt62gARoKKgJC"),
        CryptoWallet("LTC", "ltc1qull3zr3wejqypteeay9x4q4hpma0jc2225q8t5"),
        CryptoWallet("TRX", "TYiDrFSs7qHzJpM7xmhq66QpTtTYu1YYyW")
    )

    /** مخزن گیت‌هاب برنامه — آپدیت‌چکر از Releases همین مخزن نسخه‌های جدید را می‌خواند */
    const val GITHUB_URL = "https://github.com/Armin-ph/contax"

    const val REPO_OWNER = "Armin-ph"
    const val REPO_NAME = "contax"

    val latestReleaseApiUrl: String
        get() = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
}
