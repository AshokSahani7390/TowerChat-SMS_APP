package com.towerchat.app.crypto

import com.towerchat.app.data.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyPair
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeySessionRepository @Inject constructor() {

    // Device's own EC KeyPair generated for this session
    private val myKeyPair: KeyPair = CryptoManager.generateKeyPair()

    val myPublicKeyBytes: ByteArray
        get() = myKeyPair.public.encoded

    val myPublicKeyBase64: String
        get() = Base64.getEncoder().encodeToString(myPublicKeyBytes)

    // Contact phone number -> Public key bytes
    private val contactPublicKeys = mutableMapOf<String, ByteArray>()

    // Contact phone number -> 32-byte derived shared secret
    private val contactSharedSecrets = mutableMapOf<String, ByteArray>()

    // Set of contact phone numbers with detected key changes (SIM swap warning)
    private val _flaggedKeyChanges = MutableStateFlow<Set<String>>(emptySet())
    val flaggedKeyChanges: StateFlow<Set<String>> = _flaggedKeyChanges.asStateFlow()

    fun hasSharedSecret(contactNumber: String): Boolean {
        val key = Contact.normalizePhoneNumber(contactNumber)
        return contactSharedSecrets.containsKey(key)
    }

    fun getSharedSecret(contactNumber: String): ByteArray? {
        val key = Contact.normalizePhoneNumber(contactNumber)
        return contactSharedSecrets[key]
    }

    /**
     * Registers a remote contact's public key and derives the ECDH shared secret.
     * Returns true if a key change was detected (indicating possible SIM swap / MITM).
     */
    fun processRemotePublicKey(contactNumber: String, remotePublicKeyBase64: String): Boolean {
        val normalized = Contact.normalizePhoneNumber(contactNumber)
        val newKeyBytes = Base64.getDecoder().decode(remotePublicKeyBase64)
        val existingKey = contactPublicKeys[normalized]

        val isChanged = FingerprintManager.detectKeyChange(existingKey, newKeyBytes)
        if (isChanged) {
            _flaggedKeyChanges.value = _flaggedKeyChanges.value + normalized
        }

        contactPublicKeys[normalized] = newKeyBytes
        val sharedSecret = CryptoManager.deriveSharedSecret(myKeyPair, newKeyBytes)
        contactSharedSecrets[normalized] = sharedSecret

        return isChanged
    }

    fun getSafetyNumber(contactNumber: String): String? {
        val normalized = Contact.normalizePhoneNumber(contactNumber)
        val theirKey = contactPublicKeys[normalized] ?: return null
        return FingerprintManager.generateSafetyNumber(myPublicKeyBytes, theirKey)
    }

    fun isKeyChanged(contactNumber: String): Boolean {
        val normalized = Contact.normalizePhoneNumber(contactNumber)
        return _flaggedKeyChanges.value.contains(normalized)
    }

    fun dismissKeyChangeWarning(contactNumber: String) {
        val normalized = Contact.normalizePhoneNumber(contactNumber)
        _flaggedKeyChanges.value = _flaggedKeyChanges.value - normalized
    }

    /**
     * Cryptographic Hygiene: Zeroizes the derived shared secret in RAM and clears public key
     * for a specific contact when their chat history is deleted.
     */
    fun clearKeysForContact(contactNumber: String) {
        val normalized = Contact.normalizePhoneNumber(contactNumber)
        contactSharedSecrets[normalized]?.let { secret ->
            java.util.Arrays.fill(secret, 0.toByte())
        }
        contactSharedSecrets.remove(normalized)
        contactPublicKeys.remove(normalized)
        _flaggedKeyChanges.value = _flaggedKeyChanges.value - normalized
    }

    /**
     * Cryptographic Hygiene: Zeroizes all derived shared secrets in RAM and wipes all stored keys.
     */
    fun wipeAllKeys() {
        contactSharedSecrets.values.forEach { secret ->
            java.util.Arrays.fill(secret, 0.toByte())
        }
        contactSharedSecrets.clear()
        contactPublicKeys.clear()
        _flaggedKeyChanges.value = emptySet()
    }
}
