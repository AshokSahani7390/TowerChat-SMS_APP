package com.towerchat.app.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class InCallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.towerchat.app.telephony.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.towerchat.app.telephony.ACTION_DECLINE"
        const val ACTION_CALL_BACK = "com.towerchat.app.telephony.ACTION_CALL_BACK"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallRepository.answerIncomingCall()
                InCallNotificationManager.cancelIncomingNotification(context)
                // Open InCallActivity
                val activityIntent = Intent(context, com.towerchat.app.ui.InCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(activityIntent)
            }
            ACTION_DECLINE -> {
                CallRepository.declineIncomingCall()
                InCallNotificationManager.cancelIncomingNotification(context)
            }
            ACTION_CALL_BACK -> {
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                if (!number.isNullOrBlank()) {
                    val callManager = CallManager(context.applicationContext)
                    callManager.placeCall(number, null)
                }
            }
        }
    }
}
