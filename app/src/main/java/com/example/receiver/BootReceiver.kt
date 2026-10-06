package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.model.LogType
import com.example.data.security.PreferenceManager
import com.example.domain.log.LogRepository
import com.example.service.TelegramRemoteService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferenceManager = PreferenceManager(context)
            val config = preferenceManager.botConfigFlow.value

            val shouldResume = (config.isAutoStartOnBoot || preferenceManager.isServiceDesiredEnabled()) && preferenceManager.isConfigured()

            if (shouldResume) {
                preferenceManager.recordServiceRecovery()
                preferenceManager.setLastKnownLifecycleState("RECOVERED_AFTER_BOOT")
                LogRepository.addLog(
                    LogType.SYSTEM,
                    "Device Boot Completed",
                    "Restoring TeleManage foreground service, Telegram polling, notification forwarding & configured rules"
                )
                TelegramRemoteService.startService(context)
            } else {
                LogRepository.addLog(
                    LogType.SYSTEM,
                    "Device Boot Completed",
                    "Service remains stopped per user configuration"
                )
            }
        }
    }
}
