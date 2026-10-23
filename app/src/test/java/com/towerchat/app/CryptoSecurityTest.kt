package com.towerchat.app

import com.towerchat.app.crypto.CryptoManager
import com.towerchat.app.crypto.FingerprintManager
import com.towerchat.app.crypto.HandshakeProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoSecurityTest {

    @Test
    fun testEcdhKeyExchangeAndSharedSecretAgreement() {
        val aliceKeyPair = CryptoManager.generateKeyPair()
        val bobKeyPair = CryptoManager.generateKeyPair()

        val aliceSharedSecret = CryptoManager.deriveSharedSecret(aliceKeyPair, bobKeyPair.public.encoded)
        val bobSharedSecret = CryptoManager.deriveSharedSecret(bobKeyPair, aliceKeyPair.public.encoded)

        assertEquals(32, aliceSharedSecret.size)
        assertEquals(32, bobSharedSecret.size)
        assertArrayEquals(
            "Alice and Bob must independently derive the exact same 32-byte shared secret",
            aliceSharedSecret,
            bobSharedSecret
        )
    }

    @Test
    fun testAes256GcmEncryptionAndDecryption() {
        val aliceKeyPair = CryptoManager.generateKeyPair()
        val bobKeyPair = CryptoManager.generateKeyPair()

        val sharedSecret = CryptoManager.deriveSharedSecret(aliceKeyPair, bobKeyPair.public.encoded)

        val originalPlaintext = "Top-secret message transmitted over cellular SMS channel"
        val ciphertextBase64 = CryptoManager.encrypt(originalPlaintext, sharedSecret)

        // Carrier only sees Base64 blob, not plaintext
        assertFalse(ciphertextBase64.contains("Top-secret"))

        val decryptedText = CryptoManager.decrypt(ciphertextBase64, sharedSecret)
        assertEquals(originalPlaintext, decryptedText)
    }

    @Test(expected = Exception::class)
    fun testTamperedCiphertextFailsAuthenticationTag() {
        val keyPair = CryptoManager.generateKeyPair()
        val sharedSecret = CryptoManager.deriveSharedSecret(keyPair, keyPair.public.encoded)

        val ciphertextBase64 = CryptoManager.encrypt("Hello World", sharedSecret)

        // Corrupt one character in ciphertext payload
        val tampered = if (ciphertextBase64.startsWith("A")) "B" + ciphertextBase64.substring(1)
                       else "A" + ciphertextBase64.substring(1)

        CryptoManager.decrypt(tampered, sharedSecret)
    }

    @Test
    fun testSafetyNumberConsistency() {
        val aliceKey = CryptoManager.generateKeyPair().public.encoded
        val bobKey = CryptoManager.generateKeyPair().public.encoded

        val safetyNumberFromAlice = FingerprintManager.generateSafetyNumber(aliceKey, bobKey)
        val safetyNumberFromBob = FingerprintManager.generateSafetyNumber(bobKey, aliceKey)

        assertEquals(
            "Safety numbers must be identical regardless of key order",
            safetyNumberFromAlice,
            safetyNumberFromBob
        )

        // Validate format: 4 groups of 3 digits e.g. "047 183 291 504"
        assertTrue(safetyNumberFromAlice.matches(Regex("^\\d{3} \\d{3} \\d{3} \\d{3}\$")))
    }

    @Test
    fun testKeyChangeDetection() {
        val keyOriginal = byteArrayOf(1, 2, 3, 4)
        val keySame = byteArrayOf(1, 2, 3, 4)
        val keyImpostor = byteArrayOf(1, 2, 3, 5)

        assertFalse(FingerprintManager.detectKeyChange(null, keyOriginal))
        assertFalse(FingerprintManager.detectKeyChange(keyOriginal, keySame))
        assertTrue(FingerprintManager.detectKeyChange(keyOriginal, keyImpostor))
    }

    @Test
    fun testHandshakeProtocolParsing() {
        val testKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"
        val handshakeMsg = HandshakeProtocol.buildHandshakeMessage(testKey)

        assertTrue(HandshakeProtocol.isHandshake(handshakeMsg))
        assertEquals(testKey, HandshakeProtocol.extractHandshakeKey(handshakeMsg))

        val encryptedPayload = "c29tZV9jaXBoZXJfdGV4dF9kYXRh"
        val wireMsg = HandshakeProtocol.buildEncryptedMessage(encryptedPayload)

        assertTrue(HandshakeProtocol.isEncryptedMessage(wireMsg))
        assertEquals(encryptedPayload, HandshakeProtocol.extractEncryptedPayload(wireMsg))
    }
}
