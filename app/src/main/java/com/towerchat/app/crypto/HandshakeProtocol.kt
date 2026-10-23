package com.towerchat.app.crypto

object HandshakeProtocol {

    const val HANDSHAKE_PREFIX = "TC_HANDSHAKE:"
    const val MESSAGE_PREFIX = "TC_MSG:"

    fun buildHandshakeMessage(myPublicKeyBase64: String): String {
        return "$HANDSHAKE_PREFIX$myPublicKeyBase64"
    }

    fun buildEncryptedMessage(ciphertextBase64: String): String {
        return "$MESSAGE_PREFIX$ciphertextBase64"
    }

    fun isHandshake(smsBody: String): Boolean {
        return smsBody.startsWith(HANDSHAKE_PREFIX)
    }

    fun extractHandshakeKey(smsBody: String): String {
        return smsBody.removePrefix(HANDSHAKE_PREFIX).trim()
    }

    fun isEncryptedMessage(smsBody: String): Boolean {
        return smsBody.startsWith(MESSAGE_PREFIX)
    }

    fun extractEncryptedPayload(smsBody: String): String {
        return smsBody.removePrefix(MESSAGE_PREFIX).trim()
    }
}
