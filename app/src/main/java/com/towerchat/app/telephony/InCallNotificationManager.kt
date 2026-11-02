package com.towerchat.app.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import androidx.core.app.NotificationCompat
import com.towerchat.app.R
import com.towerchat.app.ui.InCallActivity
import com.towerchat.app.ui.MainActivity

object InCallNotificationManager {

    const val INCOMING_CALL_CHANNEL_ID = "towerchat_incoming_calls"
    const val MISSED_CALL_CHANNEL_ID = "towerchat_missed_calls"

    private const val INCOMING_NOTIFICATION_ID = 4001
    private const val MISSED_NOTIFICATION_BASE_ID = 5000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Incoming Calls Channel (High importance, heads-up + full screen)
            val callSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val callChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows incoming call notifications and full-screen call screens"
                setSound(
                    callSound,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 2. Missed Calls Channel (Default importance)
            val missedChannel = NotificationChannel(
                MISSED_CALL_CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies you when an incoming call was missed"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(missedChannel)
        }
    }

    /**
     * Checks whether the app has permission to trigger full-screen intents on Android 14+ (API 34).
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
    }

    fun showIncomingCallNotification(context: Context, callerNumber: String, callerName: String?) {
        createNotificationChannels(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Full Screen Intent pointing to InCallActivity
        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer Action
        val answerIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_ANSWER
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action
        val declineIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_DECLINE
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = if (!callerName.isNullOrBlank()) callerName else callerNumber

        val builder = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayName)
            .setContentText("Incoming cellular call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                answerPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                declinePendingIntent
            )

        notificationManager.notify(INCOMING_NOTIFICATION_ID, builder.build())
    }

    fun cancelIncomingNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(INCOMING_NOTIFICATION_ID)
    }

    fun showMissedCallNotification(context: Context, phoneNumber: String, callerName: String?) {
        createNotificationChannels(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val displayName = if (!callerName.isNullOrBlank()) callerName else phoneNumber

        // Action 1: Call Back
        val callBackIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_CALL_BACK
            putExtra(InCallActionReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        val callBackPendingIntent = PendingIntent.getBroadcast(
            context,
            phoneNumber.hashCode() + 1,
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Message (opens encrypted chat in MainActivity)
        val messageIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("recipient_number", phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val messagePendingIntent = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode() + 2,
            messageIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MISSED_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Missed Call")
            .setContentText(displayName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(callBackPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Call back",
                callBackPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_send,
                "Message",
                messagePendingIntent
            )

        val notifId = MISSED_NOTIFICATION_BASE_ID + (phoneNumber.hashCode() and 0x7FFF)
        notificationManager.notify(notifId, builder.build())
    }
}
