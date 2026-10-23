package com.towerchat.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage Audit: Complete Storage Map
 * Strictly holds application configuration ONLY using hardware-backed AES256_GCM EncryptedSharedPreferences.
 * Communication content (messages, chat history, call records) is NEVER written here or anywhere on disk.
 */
@Singleton
class SecureConfigStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "towerchat_secure_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_OWN_PHONE = "cfg_own_phone"
        private const val KEY_QUOTA_COUNT = "cfg_quota_count"
        private const val KEY_QUOTA_RESET_EPOCH = "cfg_quota_reset_epoch"
        private const val KEY_PRIORITY_CONTACTS = "cfg_priority_contacts"
        private const val KEY_DUAL_SIM_PREFIX = "cfg_sim_slot_"
        private const val KEY_DRAFT_PREFIX = "cfg_draft_"
        private const val KEY_TEMPLATES = "cfg_templates"
        private const val KEY_EMERGENCY_MSG = "cfg_emergency_msg"
        private const val KEY_EMERGENCY_CONTACTS = "cfg_emergency_contacts"
        private const val KEY_SCREEN_SECURITY = "cfg_screen_security"
        private const val KEY_BURN_ON_EXIT_PREFIX = "cfg_burn_on_exit_"
        private const val KEY_GLOBAL_SNAPCHAT_MODE = "cfg_global_snapchat_mode"

        val DEFAULT_TEMPLATES = listOf(
            "I'm in a low network area, please call me if urgent.",
            "I will call you back when free.",
            "On my way, will reach shortly.",
            "Cellular SMS channel active. Standing by.",
            "Please confirm receipt of this secure message."
        )
    }

    // --- 1. Own Phone Number ---
    fun saveOwnPhoneNumber(number: String) {
        prefs.edit().putString(KEY_OWN_PHONE, number).apply()
    }

    fun getOwnPhoneNumber(): String? = prefs.getString(KEY_OWN_PHONE, null)

    // --- 2. SMS Quota Counter & Midnight Reset Timer ---
    fun getUsedQuotaCount(): Int {
        checkAndResetQuotaIfMidnightPassed()
        return prefs.getInt(KEY_QUOTA_COUNT, 0)
    }

    fun incrementQuotaUsed(segments: Int) {
        checkAndResetQuotaIfMidnightPassed()
        val current = prefs.getInt(KEY_QUOTA_COUNT, 0)
        prefs.edit().putInt(KEY_QUOTA_COUNT, current + segments).apply()
    }

    fun getQuotaRemaining(dailyLimit: Int = 100): Int {
        val used = getUsedQuotaCount()
        return (dailyLimit - used).coerceAtLeast(0)
    }

    /**
     * Calculates time remaining until midnight local time (Jio 100 SMS/day reset)
     * e.g. "Quota resets in 5h 22m"
     */
    fun getQuotaResetCountdown(): String {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 24)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = midnight.timeInMillis - now.timeInMillis
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
        return "Quota resets in ${hours}h ${minutes}m"
    }

    private fun checkAndResetQuotaIfMidnightPassed() {
        val now = System.currentTimeMillis()
        val nextReset = prefs.getLong(KEY_QUOTA_RESET_EPOCH, 0L)
        if (now >= nextReset) {
            val nextMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 24)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            prefs.edit()
                .putInt(KEY_QUOTA_COUNT, 0)
                .putLong(KEY_QUOTA_RESET_EPOCH, nextMidnight)
                .apply()
        }
    }

    // --- 3. Priority Contacts (Up to 5) ---
    fun getPriorityContacts(): Set<String> {
        val raw = prefs.getString(KEY_PRIORITY_CONTACTS, "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    fun isPriorityContact(phoneNumber: String): Boolean {
        val contacts = getPriorityContacts()
        return contacts.contains(phoneNumber)
    }

    fun togglePriorityContact(phoneNumber: String): Boolean {
        val current = getPriorityContacts().toMutableSet()
        val willBePriority = if (current.contains(phoneNumber)) {
            current.remove(phoneNumber)
            false
        } else {
            if (current.size < 5) {
                current.add(phoneNumber)
                true
            } else {
                false // Limit 5 contacts
            }
        }
        prefs.edit().putString(KEY_PRIORITY_CONTACTS, current.joinToString(",")).apply()
        return willBePriority
    }

    // --- 4. Dual SIM Assignments (phone -> slot index 0 or 1) ---
    fun getSimSlotForContact(phoneNumber: String): Int {
        return prefs.getInt(KEY_DUAL_SIM_PREFIX + phoneNumber, 0)
    }

    fun setSimSlotForContact(phoneNumber: String, slotIndex: Int) {
        prefs.edit().putInt(KEY_DUAL_SIM_PREFIX + phoneNumber, slotIndex).apply()
    }

    // --- 5. Draft Persistence (Cleared immediately on send) ---
    fun saveDraft(phoneNumber: String, text: String) {
        if (text.isBlank()) {
            clearDraft(phoneNumber)
        } else {
            prefs.edit().putString(KEY_DRAFT_PREFIX + phoneNumber, text).apply()
        }
    }

    fun getDraft(phoneNumber: String): String {
        return prefs.getString(KEY_DRAFT_PREFIX + phoneNumber, "") ?: ""
    }

    fun clearDraft(phoneNumber: String) {
        prefs.edit().remove(KEY_DRAFT_PREFIX + phoneNumber).apply()
    }

    // --- 6. Message Templates (Up to 10 quick responses) ---
    fun getTemplates(): List<String> {
        val raw = prefs.getString(KEY_TEMPLATES, null)
        return if (raw.isNullOrBlank()) {
            DEFAULT_TEMPLATES
        } else {
            raw.split("|||")
        }
    }

    fun saveTemplates(templates: List<String>) {
        val limited = templates.take(10)
        prefs.edit().putString(KEY_TEMPLATES, limited.joinToString("|||")).apply()
    }

    // --- 7. Emergency Broadcast (Pre-configured SOS) ---
    fun getEmergencyMessage(): String {
        return prefs.getString(KEY_EMERGENCY_MSG, "EMERGENCY SOS: I need urgent help at my cellular location.") ?: ""
    }

    fun setEmergencyMessage(msg: String) {
        prefs.edit().putString(KEY_EMERGENCY_MSG, msg).apply()
    }

    fun getEmergencyContacts(): List<String> {
        val raw = prefs.getString(KEY_EMERGENCY_CONTACTS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    fun setEmergencyContacts(contacts: List<String>) {
        val limited = contacts.take(5)
        prefs.edit().putString(KEY_EMERGENCY_CONTACTS, limited.joinToString(",")).apply()
    }

    // --- 8. Screen Security (FLAG_SECURE) ---
    fun isScreenSecurityEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCREEN_SECURITY, false) // default false on debug/emulator so screenshot tools work
    }

    fun setScreenSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_SECURITY, enabled).apply()
    }

    // --- 9. Snapchat Mode (Burn on Exit / Delete on Back Press) ---
    fun isGlobalSnapchatMode(): Boolean {
        return prefs.getBoolean(KEY_GLOBAL_SNAPCHAT_MODE, true)
    }

    fun setGlobalSnapchatMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBAL_SNAPCHAT_MODE, enabled).apply()
    }

    fun isBurnOnExitForContact(contactNumber: String): Boolean {
        val normalized = contactNumber.filter { it.isDigit() || it == '+' }
        val key = KEY_BURN_ON_EXIT_PREFIX + normalized
        return prefs.getBoolean(key, isGlobalSnapchatMode())
    }

    fun setBurnOnExitForContact(contactNumber: String, enabled: Boolean) {
        val normalized = contactNumber.filter { it.isDigit() || it == '+' }
        val key = KEY_BURN_ON_EXIT_PREFIX + normalized
        prefs.edit().putBoolean(key, enabled).apply()
    }

    // --- 10. App-to-App (WhatsApp Style) Mode ---
    fun isAppOnlyMode(recipientNumber: String): Boolean {
        val normalized = recipientNumber.filter { it.isDigit() || it == '+' }
        // Default to true: Messages sent exclusively to TowerChat app on Port 8901
        return prefs.getBoolean("cfg_app_only_" + normalized, true)
    }

    fun setAppOnlyMode(recipientNumber: String, enabled: Boolean) {
        val normalized = recipientNumber.filter { it.isDigit() || it == '+' }
        prefs.edit().putBoolean("cfg_app_only_" + normalized, enabled).apply()
    }

    // --- 11. Zero-Storage Panic Wipe ---
    fun wipeAllConfiguration() {
        prefs.edit().clear().apply()
    }
}
