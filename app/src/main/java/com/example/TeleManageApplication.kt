package com.example

import android.app.Application
import com.example.data.model.LogType
import com.example.domain.log.LogRepository

class TeleManageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LogRepository.addLog(
            LogType.SYSTEM,
            "Application Initialized",
            "TeleManage core initialized securely with Android Keystore support"
        )
    }
}
