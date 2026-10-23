package com.towerchat.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Telephony system broadcasts SMS_RECEIVED directly to SmsBroadcastReceiver
        // without requiring a continuous foreground service.
    }
}
