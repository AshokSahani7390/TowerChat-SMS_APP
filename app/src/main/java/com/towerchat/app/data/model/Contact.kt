package com.towerchat.app.data.model

data class Contact(
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String = normalizePhoneNumber(phoneNumber)
) {
    companion object {
        fun normalizePhoneNumber(number: String): String {
            val digits = number.replace(Regex("[^0-9+]"), "")
            // Standardize Indian phone numbers (10 digits prefixed by +91 or raw 10 digits)
            return when {
                digits.startsWith("+91") && digits.length == 13 -> digits
                digits.startsWith("91") && digits.length == 12 -> "+$digits"
                digits.startsWith("0") && digits.length == 11 -> "+91" + digits.substring(1)
                digits.length == 10 -> "+91$digits"
                else -> digits
            }
        }
    }
}
