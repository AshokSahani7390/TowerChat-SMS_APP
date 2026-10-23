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

    companion object {
        const val ACTION_DATA_SMS_RECEIVED = "android.intent.action.DATA_SMS_RECEIVED"
        private val dataChunkBuffer = mutableMapOf<String, MutableMap<Int, ByteArray>>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // 1. Handle WhatsApp-Style App-to-App Data SMS on Port 8901 (Bypasses Google Messages)
        if (action == ACTION_DATA_SMS_RECEIVED) {
            val uri = intent.data
            val port = uri?.port ?: -1
            if (port != 8901 && port != -1) return

            val bundle = intent.extras ?: return
            val pdus = bundle.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")
            if (pdus.isEmpty()) return

            var senderAddress: String? = null
            var timestamp = System.currentTimeMillis()
            val fullDataBytes = ArrayList<Byte>()

            for (pduObj in pdus) {
                val pdu = pduObj as? ByteArray ?: continue
                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.telephony.SmsMessage.createFromPdu(pdu, format)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsMessage.createFromPdu(pdu)
                }
                if (senderAddress == null) {
                    senderAddress = sms.displayOriginatingAddress ?: sms.originatingAddress
                    timestamp = sms.timestampMillis
                }
                val userData = sms.userData
                if (userData != null && userData.isNotEmpty()) {
                    for (b in userData) fullDataBytes.add(b)
                }
            }

            if (senderAddress == null) return
            val normalizedSender = Contact.normalizePhoneNumber(senderAddress)
            val rawPayload = String(fullDataBytes.toByteArray(), Charsets.UTF_8)
            val assembledBody = reassembleChunk(normalizedSender, rawPayload) ?: return

            processIncomingMessage(context, normalizedSender, assembledBody, timestamp, pdus.size)
            return
        }

        // 2. Handle Standard Cellular Text SMS (If received as normal SMS)
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (smsMessages.isEmpty()) return

            val senderAddress = smsMessages[0].displayOriginatingAddress ?: return
            val normalizedSender = Contact.normalizePhoneNumber(senderAddress)

            val fullBody = buildString {
                for (sms in smsMessages) {
                    append(sms.displayMessageBody)
                }
            }

            processIncomingMessage(
                context,
                normalizedSender,
                fullBody,
                smsMessages[0].timestampMillis,
                smsMessages.size
            )
        }
    }

    private fun reassembleChunk(sender: String, payload: String): String? {
        if (!payload.startsWith("[") || !payload.contains("]")) return payload
        val closeBracket = payload.indexOf(']')
        val tag = payload.substring(1, closeBracket)
        val slash = tag.indexOf('/')
        if (slash == -1) return payload
        val current = tag.substring(0, slash).toIntOrNull() ?: return payload
        val total = tag.substring(slash + 1).toIntOrNull() ?: return payload
        val content = payload.substring(closeBracket + 1)

        val userMap = dataChunkBuffer.getOrPut(sender) { mutableMapOf() }
        userMap[current] = content.toByteArray(Charsets.UTF_8)

        if (userMap.size == total) {
            val fullBytes = ArrayList<Byte>()
            for (i in 1..total) {
                userMap[i]?.let { for (b in it) fullBytes.add(b) }
            }
            dataChunkBuffer.remove(sender)
            return String(fullBytes.toByteArray(), Charsets.UTF_8)
        }
        return null // Awaiting remaining chunks
    }

    private fun processIncomingMessage(
        context: Context,
        normalizedSender: String,
        fullBody: String,
        timestamp: Long,
        partsCount: Int
    ) {
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
                    timestamp = timestamp,
                    isOutgoing = false,
                    status = MessageStatus.DELIVERED,
                    partsCount = partsCount
                )
            )

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
            // Silently ignore typing payloads to avoid noisy UI updates
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            return
        }

        if (TowerchatProtocol.isReadReceipt(displayBody)) {
            val receiptTimestamp = TowerchatProtocol.extractReadReceiptTimestamp(displayBody) ?: timestamp
            sessionManager.markOutgoingMessagesAsRead(normalizedSender, receiptTimestamp)
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
                    timestamp = timestamp,
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
                    timestamp = timestamp,
                    isOutgoing = false,
                    status = MessageStatus.DELIVERED,
                    partsCount = partsCount
                )
            )
        }

        messagesToAdd.forEach { sessionManager.addMessage(it) }

        val isActiveChat = sessionManager.isActiveChat(normalizedSender)

        if (isActiveChat) {
            try {
                abortBroadcast()
            } catch (_: Exception) {}
            // Send Read Receipt back to sender over Port 8901
            smsDispatcher.sendReadReceipt(normalizedSender, timestamp)
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
