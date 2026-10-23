package com.towerchat.app.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val EC_CURVE = "secp256r1"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    /**
     * Generates an EC keypair on curve secp256r1 (P-256).
     */
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE)
        keyPairGenerator.initialize(ecSpec, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Derives a 32-byte shared secret from our private key and the remote public key
     * using Elliptic Curve Diffie-Hellman (ECDH) and SHA-256 KDF.
     */
    fun deriveSharedSecret(myKeyPair: KeyPair, theirPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val keySpec = X509EncodedKeySpec(theirPublicKeyBytes)
        val theirPublicKey: PublicKey = keyFactory.generatePublic(keySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myKeyPair.private)
        keyAgreement.doPhase(theirPublicKey, true)

        val rawSharedSecret = keyAgreement.generateSecret()
        // Run through SHA-256 to ensure exactly 32 uniform bytes for AES-256
        return MessageDigest.getInstance("SHA-256").digest(rawSharedSecret)
    }

    /**
     * Encrypts plaintext with AES-256-GCM using the 32-byte shared secret.
     * Returns Base64 string of (12-byte random nonce + ciphertext + 16-byte auth tag).
     */
    fun encrypt(plaintext: String, sharedSecret: ByteArray): String {
        val key = SecretKeySpec(sharedSecret.copyOf(32), "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        val nonce = ByteArray(GCM_NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Prepend nonce to ciphertext so receiver can extract it
        val payload = nonce + ciphertext
        return Base64.getEncoder().encodeToString(payload)
    }

    /**
     * Decrypts AES-256-GCM ciphertext using the 32-byte shared secret.
     * Extracts 12-byte nonce from the beginning and authenticates the GCM tag.
     */
    fun decrypt(encodedCiphertext: String, sharedSecret: ByteArray): String {
        val bytes = Base64.getDecoder().decode(encodedCiphertext)
        require(bytes.size > GCM_NONCE_LENGTH) { "Invalid ciphertext length" }

        val nonce = bytes.copyOfRange(0, GCM_NONCE_LENGTH)
        val ciphertextWithTag = bytes.copyOfRange(GCM_NONCE_LENGTH, bytes.size)

        val key = SecretKeySpec(sharedSecret.copyOf(32), "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
        val plaintextBytes = cipher.doFinal(ciphertextWithTag)
        return String(plaintextBytes, Charsets.UTF_8)
    }
}
