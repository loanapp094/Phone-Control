package com.example.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactPhoneNumber(
    val number: String,
    val type: String
)

data class Contact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<ContactPhoneNumber>
)

class ContactRepository(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val contactsMap = linkedMapOf<Long, MutableList<ContactPhoneNumber>>()
        val namesMap = linkedMapOf<Long, String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Unknown"
                    val number = it.getString(numberCol) ?: ""
                    val typeInt = it.getInt(typeCol)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        typeInt,
                        ""
                    ).toString()

                    if (number.isNotBlank()) {
                        namesMap.putIfAbsent(id, name)
                        val numbersList = contactsMap.getOrPut(id) { mutableListOf() }
                        // Deduplicate identical numbers under same contact
                        if (numbersList.none { p -> p.number.replace("\\s|-".toRegex(), "") == number.replace("\\s|-".toRegex(), "") }) {
                            numbersList.add(ContactPhoneNumber(number = number.trim(), type = typeLabel))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        contactsMap.map { (id, numbers) ->
            Contact(
                id = id,
                displayName = namesMap[id] ?: "Unknown",
                phoneNumbers = numbers
            )
        }
    }

    suspend fun searchContacts(query: String, limit: Int = 10): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission() || query.isBlank()) return@withContext emptyList()
        val all = getAllContacts()
        val cleanQuery = query.trim().lowercase()

        all.filter { contact ->
            contact.displayName.lowercase().contains(cleanQuery) ||
                    contact.phoneNumbers.any { it.number.replace("\\s|-".toRegex(), "").contains(cleanQuery) }
        }.take(limit)
    }
}
