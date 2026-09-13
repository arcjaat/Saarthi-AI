package com.saarthi.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SaarthiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_OVERLAY,
                "Saarthi Guidance Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active screen guidance status and emergency kill switch"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_OVERLAY = "saarthi_overlay_service_channel"
        lateinit var instance: SaarthiApplication
            private set
    }
}
