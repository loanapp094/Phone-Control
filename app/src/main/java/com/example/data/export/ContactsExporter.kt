package com.example.data.export

import com.example.data.contacts.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class ContactsExporter(private val contactRepository: ContactRepository) {

    suspend fun exportToCsv(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        if (!contactRepository.hasPermission()) {
            return@withContext Result.failure(SecurityException("READ_CONTACTS permission is required"))
        }

        try {
            outputFile.parentFile?.mkdirs()
            val contacts = contactRepository.getAllContacts()

            OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
                // Header
                writer.write("Name,Phone,Type\n")

                for (contact in contacts) {
                    val safeName = escapeCsv(contact.displayName)
                    if (contact.phoneNumbers.isEmpty()) {
                        writer.write("$safeName,,\n")
                    } else {
                        for (phone in contact.phoneNumbers) {
                            val safePhone = escapeCsv(phone.number)
                            val safeType = escapeCsv(phone.type.ifBlank { "Mobile" })
                            writer.write("$safeName,$safePhone,$safeType\n")
                        }
                    }
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
