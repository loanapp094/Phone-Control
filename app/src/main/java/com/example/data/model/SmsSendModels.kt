package com.example.data.model

data class SentSmsRecord(
    val id: String,
    val recipientNumber: String,
    val recipientName: String? = null,
    val messageSnippet: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SENT", "DELIVERED", "FAILED"
    val simInfo: String? = null,
    val failureReason: String? = null
)

data class PendingSmsRequest(
    val requestId: String,
    val toPhone: String,
    val displayName: String? = null,
    val messageText: String,
    val creationTimeMs: Long = System.currentTimeMillis(),
    val simSlot: Int = -1,
    val simDisplayName: String = "Default SIM"
)

data class SimCardInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
)

data class SmsSendResult(
    val isSuccess: Boolean,
    val isDelivered: Boolean = false,
    val statusText: String,
    val failureReason: String? = null,
    val partCount: Int = 1
)

data class PendingContactResolution(
    val query: String,
    val originalMessage: String,
    val candidates: List<ContactCandidate>,
    val timestamp: Long = System.currentTimeMillis()
)

data class ContactCandidate(
    val index: Int,
    val name: String,
    val phoneNumber: String
)
