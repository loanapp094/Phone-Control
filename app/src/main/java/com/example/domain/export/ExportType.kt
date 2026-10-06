package com.example.domain.export

enum class ExportType(val title: String, val commandName: String) {
    CONTACTS("Contacts", "contacts"),
    CALLS("Call History", "calls"),
    SMS("SMS Messages", "sms"),
    PHOTOS("Photos", "photos"),
    VIDEOS("Videos", "videos"),
    MEDIA("Media", "media"),
    FILES("Files", "files"),
    ALL("Full Device Backup", "all");

    companion object {
        fun fromCommand(name: String): ExportType? {
            val clean = name.lowercase().trim()
            return values().find { it.commandName == clean }
        }
    }
}
