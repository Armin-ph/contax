package com.contax.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class ContaxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_BACKUP,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_BACKUP = "contax_backup"
    }
}
