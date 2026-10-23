package com.towerchat.app.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.session.ChatSessionManager
import com.towerchat.app.telephony.SmsDispatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionManager: ChatSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsDispatcher.EXTRA_MESSAGE_ID) ?: return

        when (intent.action) {
            SmsDispatcher.ACTION_SMS_SENT -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        sessionManager.updateMessageStatus(messageId, MessageStatus.SENT)
                        val recipientNumber = intent.getStringExtra(SmsDispatcher.EXTRA_RECIPIENT_NUMBER)
                        if (!recipientNumber.isNullOrBlank()) {
                            com.towerchat.app.telephony.SmsCleanupHelper.deleteFromSystemSmsDb(context, recipientNumber)
                        }
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        sessionManager.updateMessageStatus(messageId, MessageStatus.FAILED, "Carrier generic send failure")
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        sessionManager.updateMessageStatus(messageId, MessageStatus.FAILED, "No cellular signal / service")
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        sessionManager.updateMessageStatus(messageId, MessageStatus.FAILED, "Airplane mode or radio off")
                    }
                    else -> {
                        sessionManager.updateMessageStatus(messageId, MessageStatus.FAILED, "Send failed (code $resultCode)")
                    }
                }
            }
            SmsDispatcher.ACTION_SMS_DELIVERED -> {
                sessionManager.updateMessageStatus(messageId, MessageStatus.DELIVERED)
            }
        }
    }
}
