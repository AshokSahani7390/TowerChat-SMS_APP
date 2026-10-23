package com.towerchat.app.telephony

object QuotaCalculator {

    /**
     * Determines whether text contains any character outside the standard GSM 7-bit charset.
     */
    fun isGsm7Bit(text: String): Boolean {
        // Standard GSM 7-bit basic character set + common extension
        val gsmRegex = Regex("^[A-Za-z0-9 \\r\\n@£\$¥èéùìòÇØøÅåΔ_ΦΓΛΩΠΨΣΘΞ^{}\\\\\\[~\\]|€ÆæßÉ!\"#%&'()*+,\\-./:;<=>?]*\$")
        return gsmRegex.matches(text)
    }

    /**
     * Computes the number of SMS segments (carrier billable units) required for this text.
     */
    fun calculateSmsSegments(text: String): Int {
        if (text.isEmpty()) return 0
        val isGsm = isGsm7Bit(text)

        return if (isGsm) {
            if (text.length <= 160) 1 else ((text.length + 152) / 153)
        } else {
            // Unicode / regional characters
            if (text.length <= 70) 1 else ((text.length + 66) / 67)
        }
    }

    /**
     * Returns a human readable breakdown, e.g. "42/160 · 1 SMS" or "175/306 · 2 SMS"
     */
    fun getCharacterCountLabel(text: String): String {
        if (text.isEmpty()) return "0/160 · 0 SMS"
        val isGsm = isGsm7Bit(text)
        val segments = calculateSmsSegments(text)

        val maxInCurrentSegment = if (isGsm) {
            if (segments <= 1) 160 else segments * 153
        } else {
            if (segments <= 1) 70 else segments * 67
        }

        return "${text.length}/$maxInCurrentSegment · $segments SMS"
    }
}
