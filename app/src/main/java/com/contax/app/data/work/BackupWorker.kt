package com.contax.app.data.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contax.app.ContaxApp
import com.contax.app.R
import com.contax.app.data.backup.BackupRepository
import com.contax.app.data.model.BackupDestination
import com.contax.app.data.model.EncryptionType
import com.contax.app.data.model.ScheduleMode
import com.contax.app.data.prefs.SettingsDataStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackupScheduler {

    private const val WORK_NAME = "contax_auto_backup"

    fun apply(context: Context, mode: ScheduleMode, hour: Int) {
        val wm = WorkManager.getInstance(context)
        if (mode == ScheduleMode.OFF) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val days = if (mode == ScheduleMode.DAILY) 1L else 7L
        val request = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val prefs = SettingsDataStore(appContext)
        val settings = prefs.current()
        val repo = BackupRepository(appContext)
        // نام یکتا با مهر زمانی — قبلاً همه بکاپ‌های خودکار روی «یک فایل ثابت»
        // نوشته می‌شدند و ردیف‌های تاریخچه همگی به یک فایل مشترک اشاره می‌کردند؛
        // در نتیجه حذف هر ردیف، فایل بقیه را هم از بین می‌برد.
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))
        val result = repo.create(
            BackupRepository.NewBackup(
                format = settings.autoFormat,
                destination = BackupDestination.INTERNAL,
                encryption = EncryptionType.NONE,
                isAuto = true,
                name = "contax-auto-$stamp"
            )
        )
        result.fold(
            onSuccess = { info ->
                prefs.setLastAutoBackupAt(System.currentTimeMillis())
                // هرس نسخه‌های خودکار قدیمی — فقط AUTO_KEEP تا آخر می‌ماند
                try { repo.pruneAutoBackups() } catch (t: Throwable) { /* هرس حیاتی نیست */ }
                notifyDone(info.contactCount)
            },
            onFailure = { notifyFail() }
        )
        return Result.success()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notifyDone(count: Int) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(applicationContext, ContaxApp.CHANNEL_BACKUP)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(applicationContext.getString(R.string.auto_backup_done_title))
            .setContentText(applicationContext.getString(R.string.auto_backup_done_text, count))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1001, n)
    }

    private fun notifyFail() {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(applicationContext, ContaxApp.CHANNEL_BACKUP)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(applicationContext.getString(R.string.auto_backup_failed_title))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1002, n)
    }
}
