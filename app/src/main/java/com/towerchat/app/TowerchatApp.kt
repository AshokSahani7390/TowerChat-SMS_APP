package com.towerchat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TowerchatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService<NotificationManager>() ?: return

            // Channel for foreground listener service
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Towerchat Cellular Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps cellular SMS listener active without internet connection"
                setShowBadge(false)
            }

            // Channel for incoming chat messages
            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                "Incoming Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming SMS messages from active contacts"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "towerchat_cellular_listener_channel"
        const val CHANNEL_MESSAGES_ID = "towerchat_messages_channel"
    }
}
