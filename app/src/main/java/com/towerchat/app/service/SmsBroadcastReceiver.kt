package com.towerchat.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.towerchat.app.R
import com.towerchat.app.TowerchatApp
import com.towerchat.app.crypto.CryptoManager
import com.towerchat.app.crypto.HandshakeProtocol
import com.towerchat.app.crypto.KeySessionRepository
import com.towerchat.app.data.model.Contact
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.session.ChatSessionManager
import com.towerchat.app.crypto.TowerchatProtocol
import com.towerchat.app.telephony.SmsDispatcher
import com.towerchat.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionManager: ChatSessionManager

    @Inject
    lateinit var keySessionRepository: KeySessionRepository

    @Inject
    lateinit var smsDispatcher: SmsDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (smsMessages.isEmpty()) return

        // Group multipart SMS PDUs by originating address
        val senderAddress = smsMessages[0].displayOriginatingAddress ?: return
        val normalizedSender = Contact.normalizePhoneNumber(senderAddress)

        val fullBody = buildString {
            for (sms in smsMessages) {
                append(sms.displayMessageBody)
            }
        }

        // 1. Handle X25519 / ECDH Handshake
        if (HandshakeProtocol.isHandshake(fullBody)) {
            val theirKeyBase64 = HandshakeProtocol.extractHandshakeKey(fullBody)
            val isKeyChanged = keySessionRepository.processRemotePublicKey(normalizedSender, theirKeyBase64)

            val statusText = if (isKeyChanged) {
                "⚠️ SECURITY ALERT: Key changed for $normalizedSender. Verify safety number to prevent MITM/SIM swap."
            } else {
                "🔒 Key agreement established. End-to-end encrypted channel active."
            }

            sessionManager.addMessage(
                Message(
                    senderNumber = normalizedSender,
                    recipientNumber = sessionManager.userPhoneNumber.value ?: "Self",
                    body = statusText,
                    timestamp = smsMessages[0].timestampMillis,
                    isOutgoing = false,
                    status = MessageStatus.DELIVERED,
                    partsCount = smsMessages.size
                )
            )

            // Suppress handshake SMS from default SMS app
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            return
        }

        // 2. Handle AES-256-GCM Encrypted Message
        val displayBody: String = if (HandshakeProtocol.isEncryptedMessage(fullBody)) {
            val ciphertext = HandshakeProtocol.extractEncryptedPayload(fullBody)
            val sharedSecret = keySessionRepository.getSharedSecret(normalizedSender)

            if (sharedSecret != null) {
                try {
                    CryptoManager.decrypt(ciphertext, sharedSecret)
                } catch (e: Exception) {
                    "🔒 [Decryption failed: Message tampered or key expired]"
                }
            } else {
                "🔒 [Encrypted message received before key agreement]"
            }
        } else {
            fullBody
        }

        // 3. Handle Towerchat Protocol messages
        if (TowerchatProtocol.isTyping(displayBody)) {
            sessionManager.setTyping(normalizedSender, true)
            CoroutineScope(Dispatchers.Main).launch {
                delay(5000)
                sessionManager.setTyping(normalizedSender, false)
            }
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            return
        }

        if (TowerchatProtocol.isReadReceipt(displayBody)) {
            val timestamp = TowerchatProtocol.extractReadReceiptTimestamp(displayBody) ?: System.currentTimeMillis()
            sessionManager.markOutgoingMessagesAsRead(normalizedSender, timestamp)
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            return
        }

        if (TowerchatProtocol.isReaction(displayBody)) {
            val reactionPair = TowerchatProtocol.extractReaction(displayBody)
            if (reactionPair != null) {
                sessionManager.addReaction(normalizedSender, reactionPair.second, reactionPair.first)
            }
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            return
        }

        val messagesToAdd = if (TowerchatProtocol.isBatch(displayBody)) {
            TowerchatProtocol.extractBatchMessages(displayBody).map { text ->
                Message(
                    senderNumber = normalizedSender,
                    recipientNumber = sessionManager.userPhoneNumber.value ?: "Self",
                    body = text,
                    timestamp = smsMessages[0].timestampMillis,
                    isOutgoing = false,
                    status = MessageStatus.DELIVERED,
                    partsCount = 1
                )
            }
        } else {
            listOf(
                Message(
                    senderNumber = normalizedSender,
                    recipientNumber = sessionManager.userPhoneNumber.value ?: "Self",
                    body = displayBody,
                    timestamp = smsMessages[0].timestampMillis,
                    isOutgoing = false,
                    status = MessageStatus.DELIVERED,
                    partsCount = smsMessages.size
                )
            )
        }

        messagesToAdd.forEach { sessionManager.addMessage(it) }

        val isActiveChat = sessionManager.isActiveChat(normalizedSender)

        if (isActiveChat) {
            // High priority interception: suppress notification in default SMS app for active chat
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            // Send Read Receipt back to sender
            smsDispatcher.sendReadReceipt(normalizedSender, smsMessages[0].timestampMillis)
        } else {
            // Not in active chat or backgrounded: show Towerchat notification
            val lastText = messagesToAdd.lastOrNull()?.body ?: displayBody
            showIncomingNotification(context, normalizedSender, lastText)
        }
    }

    private fun showIncomingNotification(context: Context, sender: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.towerchat.app.OPEN_CHAT"
            putExtra("recipient_number", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            openIntent,
            flags
        )

        val notification = NotificationCompat.Builder(context, TowerchatApp.CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_towerchat_logo)
            .setContentTitle("Message from $sender")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(sender.hashCode(), notification)
    }
}
