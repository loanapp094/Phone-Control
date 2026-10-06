package com.example.data.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.data.model.SimCardInfo
import com.example.data.model.SmsSendResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class SmsSender(private val context: Context) {

    fun hasSendSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Detects active SIM cards if supported and permitted.
     */
    fun getAvailableSims(): List<SimCardInfo> {
        val list = mutableListOf<SimCardInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                if (subManager != null && hasPhoneStatePermission()) {
                    val activeSubs: List<SubscriptionInfo>? = subManager.activeSubscriptionInfoList
                    activeSubs?.forEach { info ->
                        list.add(
                            SimCardInfo(
                                subscriptionId = info.subscriptionId,
                                slotIndex = info.simSlotIndex + 1,
                                displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                                carrierName = info.carrierName?.toString() ?: "Carrier"
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }

    /**
     * Resolves the appropriate SmsManager instance, respecting dual-SIM if configured.
     */
    @Suppress("DEPRECATION")
    private fun getSmsManagerForSubscription(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val systemSmsManager = context.getSystemService(SmsManager::class.java)
            if (subId >= 0) systemSmsManager.createForSubscriptionId(subId) else systemSmsManager
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subId >= 0) {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            SmsManager.getDefault()
        }
    }

    /**
     * Cleans and normalizes user-provided phone numbers.
     */
    fun normalizePhoneNumber(input: String): String {
        val trimmed = input.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Validates normalized phone number syntax.
     */
    fun isValidPhoneNumber(normalized: String): Boolean {
        val digits = normalized.filter { it.isDigit() }
        return digits.length in 3..16
    }

    /**
     * Sends SMS with official Android SmsManager, supporting multipart messages and PendingIntent status tracking.
     */
    suspend fun sendSms(
        recipientNumber: String,
        messageText: String,
        subId: Int = -1
    ): SmsSendResult = withContext(Dispatchers.IO) {
        if (!hasSendSmsPermission()) {
            return@withContext SmsSendResult(
                isSuccess = false,
                statusText = "FAILED",
                failureReason = "SEND_SMS permission is not granted on this device."
            )
        }

        val normalized = normalizePhoneNumber(recipientNumber)
        if (!isValidPhoneNumber(normalized)) {
            return@withContext SmsSendResult(
                isSuccess = false,
                statusText = "FAILED",
                failureReason = "Invalid phone number format: $recipientNumber"
            )
        }

        if (messageText.isBlank()) {
            return@withContext SmsSendResult(
                isSuccess = false,
                statusText = "FAILED",
                failureReason = "Message text cannot be empty."
            )
        }

        val uniqueAction = "com.example.telemanage.SMS_SENT_${UUID.randomUUID()}"
        val sentIntent = Intent(uniqueAction).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }
        val sentPendingIntent = PendingIntent.getBroadcast(context, 0, sentIntent, flags)

        val deferredResult = CompletableDeferred<SmsSendResult>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val resultCode = resultCode
                val (isSuccess, failureReason) = when (resultCode) {
                    Activity.RESULT_OK -> true to null
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> false to "Generic cellular carrier or device failure."
                    SmsManager.RESULT_ERROR_NO_SERVICE -> false to "No cellular network service or SIM is inactive."
                    SmsManager.RESULT_ERROR_NULL_PDU -> false to "Null PDU network transmission error."
                    SmsManager.RESULT_ERROR_RADIO_OFF -> false to "Cellular radio is turned off (e.g., Airplane mode)."
                    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> false to "Carrier SMS dispatch limit exceeded."
                    else -> false to "Transmission failed (Result code: $resultCode)."
                }
                deferredResult.complete(
                    SmsSendResult(
                        isSuccess = isSuccess,
                        statusText = if (isSuccess) "SENT" else "FAILED",
                        failureReason = failureReason
                    )
                )
            }
        }

        val filter = IntentFilter(uniqueAction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        try {
            val smsManager = getSmsManagerForSubscription(subId)
            val parts = smsManager.divideMessage(messageText)

            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    // Attach callback to the first or last part
                    sentIntents.add(if (i == parts.size - 1) sentPendingIntent else PendingIntent.getBroadcast(context, i + 1, sentIntent, flags))
                }
                smsManager.sendMultipartTextMessage(
                    normalized,
                    null,
                    parts,
                    sentIntents,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    normalized,
                    null,
                    messageText,
                    sentPendingIntent,
                    null
                )
            }

            // Await broadcast confirmation with 12s timeout
            val result = withTimeoutOrNull(12000L) {
                deferredResult.await()
            }

            result ?: SmsSendResult(
                isSuccess = true,
                statusText = "SENT",
                failureReason = null,
                partCount = parts.size
            )
        } catch (e: SecurityException) {
            SmsSendResult(
                isSuccess = false,
                statusText = "FAILED",
                failureReason = "SecurityException: SEND_SMS permission denied."
            )
        } catch (e: Exception) {
            SmsSendResult(
                isSuccess = false,
                statusText = "FAILED",
                failureReason = e.message ?: "Failed to dispatch SMS through system provider."
            )
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}
