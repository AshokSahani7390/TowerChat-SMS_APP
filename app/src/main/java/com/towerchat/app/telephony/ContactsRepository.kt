package com.towerchat.app.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.towerchat.app.data.model.PhoneContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        ContactsContract.CommonDataKinds.Phone.STARRED,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
    )

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Loads all phone contacts sorted alphabetically by display name.
     */
    suspend fun getContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val list = mutableListOf<PhoneContact>()
        val seenNumbers = mutableSetOf<String>()

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starredIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val lookupIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numberIdx != -1) c.getString(numberIdx) ?: "" else ""
                    val photo = if (photoIdx != -1) c.getString(photoIdx) else null
                    val starred = if (starredIdx != -1) c.getInt(starredIdx) == 1 else false
                    val lookup = if (lookupIdx != -1) c.getString(lookupIdx) else null

                    val clean = number.filter { it.isDigit() || it == '+' }
                    if (clean.isNotEmpty() && !seenNumbers.contains(clean)) {
                        seenNumbers.add(clean)
                        list.add(
                            PhoneContact(
                                id = id,
                                name = name,
                                number = number,
                                photoUri = photo,
                                isStarred = starred,
                                lookupKey = lookup
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Failsafe on OEM provider failure
        }

        list
    }

    /**
     * Loads starred favorite contacts.
     */
    suspend fun getFavorites(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val contacts = getContacts()
        contacts.filter { it.isStarred }
    }
}
