package com.towerchat.app.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.towerchat.app.crypto.CryptoManager
import com.towerchat.app.crypto.HandshakeProtocol
import com.towerchat.app.crypto.KeySessionRepository
import com.towerchat.app.data.model.Contact
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.session.ChatSessionManager
import com.towerchat.app.crypto.TowerchatProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: ChatSessionManager,
    private val keySessionRepository: KeySessionRepository
) {

    // Throttled typing indicator: max once per 60s per contact
    private val lastTypingSentMap = mutableMapOf<String, Long>()

    companion object {
        const val ACTION_SMS_SENT = "com.towerchat.app.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.towerchat.app.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_RECIPIENT_NUMBER = "extra_recipient_number"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"
    }

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * Sends an X25519/ECDH public key exchange handshake over SMS.
     */
    fun sendHandshake(recipientNumber: String): Result<Unit> {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val handshakeBody = HandshakeProtocol.buildHandshakeMessage(keySessionRepository.myPublicKeyBase64)
        return try {
            val smsManager = getSmsManager()
            smsManager.sendTextMessage(normalizedRecipient, null, handshakeBody, null, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dispatches an AES-256-GCM encrypted SMS to the cellular network.
     * Carrier receives only Base64 ciphertext with TC_MSG: prefix.
     */
    fun sendMessage(recipientNumber: String, text: String): Result<Message> {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val sharedSecret = keySessionRepository.getSharedSecret(normalizedRecipient)

        // If no key exchange completed yet, automatically initiate handshake
        if (sharedSecret == null) {
            sendHandshake(normalizedRecipient)
        }

        // Encrypt payload if shared secret exists, else inform in payload
        val wirePayload = if (sharedSecret != null) {
            val ciphertext = CryptoManager.encrypt(text, sharedSecret)
            HandshakeProtocol.buildEncryptedMessage(ciphertext)
        } else {
            // Initiate handshake and send first message
            text
        }

        val quota = sessionManager.quotaState.value
        val segments = QuotaCalculator.calculateSmsSegments(wirePayload)

        if (quota.remaining < segments) {
            return Result.failure(
                IllegalStateException("Daily SMS quota exceeded. Remaining: ${quota.remaining}, Required: $segments")
            )
        }

        val myNumber = sessionManager.userPhoneNumber.value ?: "Self"

        // In-memory message stores plaintext for user readability
        val message = Message(
            senderNumber = myNumber,
            recipientNumber = normalizedRecipient,
            body = text,
            isOutgoing = true,
            status = MessageStatus.SENDING,
            partsCount = segments
        )

        sessionManager.addMessage(message)

        try {
            val smsManager = getSmsManager()
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            if (segments <= 1) {
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    message.id.hashCode(),
                    Intent(ACTION_SMS_SENT).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_MESSAGE_ID, message.id)
                        putExtra(EXTRA_RECIPIENT_NUMBER, normalizedRecipient)
                        putExtra(EXTRA_PART_INDEX, 0)
                        putExtra(EXTRA_TOTAL_PARTS, 1)
                    },
                    flags
                )

                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    message.id.hashCode() + 1,
                    Intent(ACTION_SMS_DELIVERED).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_MESSAGE_ID, message.id)
                    },
                    flags
                )

                smsManager.sendTextMessage(
                    normalizedRecipient,
                    null,
                    wirePayload,
                    sentIntent,
                    deliveredIntent
                )
            } else {
                // Multipart SMS for larger payloads
                val parts = smsManager.divideMessage(wirePayload)
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()

                parts.forEachIndexed { index, _ ->
                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        message.id.hashCode() + index,
                        Intent(ACTION_SMS_SENT).apply {
                            setPackage(context.packageName)
                            putExtra(EXTRA_MESSAGE_ID, message.id)
                            putExtra(EXTRA_RECIPIENT_NUMBER, normalizedRecipient)
                            putExtra(EXTRA_PART_INDEX, index)
                            putExtra(EXTRA_TOTAL_PARTS, parts.size)
                        },
                        flags
                    )
                    val deliveryIntent = PendingIntent.getBroadcast(
                        context,
                        message.id.hashCode() + 1000 + index,
                        Intent(ACTION_SMS_DELIVERED).apply {
                            setPackage(context.packageName)
                            putExtra(EXTRA_MESSAGE_ID, message.id)
                        },
                        flags
                    )
                    sentIntents.add(sentIntent)
                    deliveryIntents.add(deliveryIntent)
                }

                smsManager.sendMultipartTextMessage(
                    normalizedRecipient,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            }

            return Result.success(message)
        } catch (e: Exception) {
            sessionManager.updateMessageStatus(
                message.id,
                MessageStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to send SMS"
            )
            return Result.failure(e)
        }
    }

    /**
     * Cellular protocol: typing indicator (throttled to max 1 SMS / 60 seconds per recipient).
     */
    fun sendTypingIndicator(recipientNumber: String) {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val now = System.currentTimeMillis()
        val lastSent = lastTypingSentMap[normalizedRecipient] ?: 0L
        if (now - lastSent < 60_000) return // Throttled

        lastTypingSentMap[normalizedRecipient] = now
        val payload = TowerchatProtocol.buildTyping()
        dispatchRawProtocolPayload(normalizedRecipient, payload)
    }

    /**
     * Cellular protocol: read receipt for active open conversation.
     */
    fun sendReadReceipt(recipientNumber: String, timestamp: Long) {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val payload = TowerchatProtocol.buildReadReceipt(timestamp)
        dispatchRawProtocolPayload(normalizedRecipient, payload)
    }

    /**
     * Cellular protocol: emoji message reaction attached to message timestamp.
     */
    fun sendReaction(recipientNumber: String, emoji: String, targetTimestamp: Long) {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val payload = TowerchatProtocol.buildReaction(emoji, targetTimestamp)
        dispatchRawProtocolPayload(normalizedRecipient, payload)
        // Also apply reaction to message in local RAM
        sessionManager.addReaction(normalizedRecipient, targetTimestamp, emoji)
    }

    private fun dispatchRawProtocolPayload(recipientNumber: String, plaintext: String) {
        try {
            val sharedSecret = keySessionRepository.getSharedSecret(recipientNumber)
            val wirePayload = if (sharedSecret != null) {
                val ciphertext = CryptoManager.encrypt(plaintext, sharedSecret)
                HandshakeProtocol.buildEncryptedMessage(ciphertext)
            } else {
                plaintext
            }
            val smsManager = getSmsManager()
            smsManager.sendTextMessage(recipientNumber, null, wirePayload, null, null)
        } catch (_: Exception) {}
    }
}
