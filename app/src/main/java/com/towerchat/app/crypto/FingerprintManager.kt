package com.towerchat.app.crypto

import java.security.MessageDigest

object FingerprintManager {

    /**
     * Generates a 12-digit safety number from two public keys.
     * Keys are sorted lexicographically so both parties produce the identical 12-digit number.
     * Output format: "047 183 291 504" for easy verbal comparison over a phone call.
     */
    fun generateSafetyNumber(myPublicKey: ByteArray, theirPublicKey: ByteArray): String {
        // Compare arrays lexicographically by byte values
        val isFirst = compareByteArrays(myPublicKey, theirPublicKey) <= 0
        val first = if (isFirst) myPublicKey else theirPublicKey
        val second = if (isFirst) theirPublicKey else myPublicKey

        val combined = first + second
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)

        // Take first 6 bytes -> compute 12 decimal digits, grouped as 4 triplets
        val num = digest.take(6).fold(0L) { acc, b ->
            (acc * 256) + (b.toLong() and 0xFF)
        }

        return (num % 1_000_000_000_000L).toString()
            .padStart(12, '0')
            .chunked(3)
            .joinToString(" ")
    }

    /**
     * Detects key change (possible SIM swap or MITM attack).
     * Returns true if a key was previously registered and the new key is different.
     */
    fun detectKeyChange(storedKey: ByteArray?, newPublicKey: ByteArray): Boolean {
        return storedKey != null && !storedKey.contentEquals(newPublicKey)
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val byteA = a[i].toInt() and 0xFF
            val byteB = b[i].toInt() and 0xFF
            if (byteA != byteB) return byteA.compareTo(byteB)
        }
        return a.size.compareTo(b.size)
    }
}
