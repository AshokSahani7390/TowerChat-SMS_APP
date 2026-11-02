package com.towerchat.app.telephony.smartdial

/**
 * T9SmartDialEngine performs fast in-memory T9 algorithmic matching for keypad search.
 * It matches user-entered keypad digits (2-9) against contact names (converted to T9 digits)
 * and against raw phone numbers.
 *
 * Designed with zero external dependencies for 100% JVM unit testability.
 */
object T9SmartDialEngine {

    /**
     * Standard ITU E.161 / ISO 9995-8 Telephone Keypad Character Mapping:
     * 2 = A, B, C
     * 3 = D, E, F
     * 4 = G, H, I
     * 5 = J, K, L
     * 6 = M, N, O
     * 7 = P, Q, R, S
     * 8 = T, U, V
     * 9 = W, X, Y, Z
     */
    fun charToDigit(char: Char): Char? {
        return when (char.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            '0' -> '0'
            '1' -> '1'
            else -> null
        }
    }

    /**
     * Converts an alphabetical display name into its T9 numerical representation.
     * Non-alphanumeric characters (spaces, punctuation) are preserved as spaces.
     */
    fun nameToT9(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) {
            val digit = charToDigit(c)
            if (digit != null) {
                sb.append(digit)
            } else if (c.isWhitespace()) {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    /**
     * Extracts T9 initials from words in the name (e.g. "John Doe" -> "53").
     */
    fun nameToInitialsT9(name: String): String {
        return name.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .mapNotNull { word -> word.firstOrNull()?.let { charToDigit(it) } }
            .joinToString("")
    }

    /**
     * Determines whether a contact matches the user's entered keypad digit sequence.
     * Matches if:
     * 1. Query digits are empty (matches all).
     * 2. Raw phone number contains the query digits.
     * 3. The T9 representation of any word in the name starts with or contains the query.
     * 4. The T9 initials of the name start with the query.
     */
    fun matches(name: String, phoneNumber: String, queryDigits: String): Boolean {
        if (queryDigits.isBlank()) return true

        val cleanQuery = queryDigits.filter { it.isDigit() }
        if (cleanQuery.isEmpty()) return false

        // 1. Phone number substring match
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        if (cleanNumber.contains(cleanQuery)) {
            return true
        }

        // 2. Name T9 word-by-word match
        val words = name.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        for (word in words) {
            val wordT9 = nameToT9(word)
            if (wordT9.contains(cleanQuery)) {
                return true
            }
        }

        // 3. Name full contiguous T9 match
        val fullT9WithoutSpaces = nameToT9(name).filter { it.isDigit() }
        if (fullT9WithoutSpaces.contains(cleanQuery)) {
            return true
        }

        // 4. Initials match (e.g. "J D" = "53" matching query "53")
        val initialsT9 = nameToInitialsT9(name)
        if (initialsT9.startsWith(cleanQuery)) {
            return true
        }

        return false
    }

    /**
     * Calculates match priority / relevance score for sorting smart dial results:
     * - Exact number match: score 100
     * - Name starts with query (T9): score 80
     * - Initials match query (T9): score 70
     * - Word in name starts with query (T9): score 60
     * - Phone number prefix match: score 50
     * - Name contains query (T9): score 40
     * - Phone number contains query: score 30
     * - No match: score 0
     */
    fun computeMatchScore(name: String, phoneNumber: String, queryDigits: String): Int {
        val cleanQuery = queryDigits.filter { it.isDigit() }
        if (cleanQuery.isEmpty()) return 1

        val cleanNumber = phoneNumber.filter { it.isDigit() }
        if (cleanNumber == cleanQuery) return 100

        val fullT9 = nameToT9(name).filter { it.isDigit() }
        if (fullT9.startsWith(cleanQuery)) return 80

        val initials = nameToInitialsT9(name)
        if (initials.startsWith(cleanQuery)) return 70

        val words = name.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        for (word in words) {
            val wordT9 = nameToT9(word)
            if (wordT9.startsWith(cleanQuery)) return 60
        }

        if (cleanNumber.startsWith(cleanQuery)) return 50
        if (fullT9.contains(cleanQuery)) return 40
        if (cleanNumber.contains(cleanQuery)) return 30

        return 0
    }
}
