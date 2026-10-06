package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SentSmsRecord
import com.example.data.model.SimCardInfo
import com.example.domain.export.ExportProgress
import com.example.domain.export.ExportState
import com.example.domain.export.ExportType
import com.example.service.ServiceConnectionState
import com.example.ui.DiagnosticResult
import com.example.ui.MainViewModel
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToBotConfig: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val connState by viewModel.serviceConnectionState.collectAsState()
    val config by viewModel.botConfig.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val isMediaGranted by viewModel.isMediaPermissionGranted.collectAsState()
    val isNotifListenerGranted by viewModel.isNotificationListenerGranted.collectAsState()
    val isContactsGranted by viewModel.isContactsPermissionGranted.collectAsState()
    val isCallLogGranted by viewModel.isCallLogPermissionGranted.collectAsState()
    val isSmsGranted by viewModel.isSmsPermissionGranted.collectAsState()
    val isSendSmsGranted by viewModel.isSendSmsPermissionGranted.collectAsState()
    val availableSims by viewModel.availableSims.collectAsState()
    val sentSmsHistory by viewModel.sentSmsHistory.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val testPingState by viewModel.testPingState.collectAsState()
    val diagnosticResults by viewModel.diagnosticResults.collectAsState()
    val lastTelegramUpdate by viewModel.lastUpdateReceived.collectAsState()
    val lastNotificationForwarded by viewModel.lastNotificationForwarded.collectAsState()
    val lastHeartbeat by viewModel.lastHeartbeat.collectAsState()
    val lastRecoveryTime by viewModel.lastRecoveryTimestamp.collectAsState()
    val lastKnownLifecycle by viewModel.lastKnownLifecycle.collectAsState()

    // Pulse animation for status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Hero Status Card
            HeroStatusCard(
                isRunning = isRunning,
                connState = connState,
                isConfigured = config.botToken.isNotBlank() && config.authorizedUserId != 0L,
                pulseAlpha = pulseAlpha,
                onToggleService = { viewModel.toggleService() }
            )
        }

        // Permissions Matrix Card (Requirement 7 & 12)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Permissions & Remote Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = onNavigateToPermissions,
                            modifier = Modifier.testTag("manage_permissions_button")
                        ) {
                            Text("Manage", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Grid of permissions
                    PermissionStatusRow(
                        title = "Contacts",
                        isGranted = isContactsGranted,
                        isEnabled = config.isContactAccessEnabled,
                        icon = Icons.Default.Contacts
                    )
                    PermissionStatusRow(
                        title = "Call History",
                        isGranted = isCallLogGranted,
                        isEnabled = config.isCallHistoryAccessEnabled,
                        icon = Icons.Default.Call
                    )
                    PermissionStatusRow(
                        title = "SMS Reading",
                        isGranted = isSmsGranted,
                        isEnabled = config.isSmsAccessEnabled,
                        icon = Icons.Default.Message
                    )
                    PermissionStatusRow(
                        title = "SMS Sending",
                        isGranted = isSendSmsGranted,
                        isEnabled = config.isRemoteSmsSendingEnabled,
                        icon = Icons.AutoMirrored.Filled.Send
                    )
                    PermissionStatusRow(
                        title = "Notification Access",
                        isGranted = isNotifListenerGranted,
                        isEnabled = config.isNotificationForwardingEnabled,
                        icon = Icons.Default.NotificationsActive
                    )
                    PermissionStatusRow(
                        title = "Photos & Videos",
                        isGranted = isMediaGranted,
                        isEnabled = true,
                        icon = Icons.Default.PermMedia
                    )
                }
            }
        }

        item {
            // System Overview
            Text(
                text = "System Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusMetricItem(
                    title = "Telegram Bot",
                    statusText = if (config.botToken.isNotBlank()) "Configured" else "Missing",
                    isPositive = config.botToken.isNotBlank(),
                    icon = Icons.Default.CloudDone,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToBotConfig
                )
                StatusMetricItem(
                    title = "Remote Service",
                    statusText = if (isRunning) "Running" else "Stopped",
                    isPositive = isRunning,
                    icon = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.toggleService() }
                )
            }
        }

        item {
            // Background Reliability & Live Telemetry Card (Requirement 7 & 8)
            ServiceHealthTelemetryCard(
                isServiceRunning = isRunning,
                connState = connState,
                isNotificationListenerActive = isNotifListenerGranted,
                lastTelegramUpdate = lastTelegramUpdate,
                lastNotificationForwarded = lastNotificationForwarded,
                lastHeartbeat = lastHeartbeat,
                lastRecoveryTime = lastRecoveryTime,
                lastKnownLifecycle = lastKnownLifecycle,
                onRestartConnection = { viewModel.restartTelegramConnection() }
            )
        }

        item {
            // Device Status Card
            DeviceStatusCard(deviceStatus = deviceStatus)
        }

        item {
            // Remote Data Export & Backup Module
            RemoteExportBackupCard(
                exportProgress = exportProgress,
                isConfigured = config.botToken.isNotBlank() && config.authorizedUserId != 0L,
                hasContacts = isContactsGranted && config.isContactAccessEnabled,
                hasCalls = isCallLogGranted && config.isCallHistoryAccessEnabled,
                hasSms = isSmsGranted && config.isSmsAccessEnabled,
                hasMedia = isMediaGranted,
                onStartExport = { type -> viewModel.startExport(type) },
                onCancelExport = { viewModel.cancelExport() }
            )
        }

        item {
            // SMS Sending Section (Requirements 12 & 13)
            SmsSendingDashboardCard(
                isPermissionGranted = isSendSmsGranted,
                isRemoteSmsEnabled = config.isRemoteSmsSendingEnabled,
                messagesSentCount = config.sentSmsCount,
                availableSims = availableSims,
                preferredSimId = config.preferredSimSubscriptionId,
                recentHistory = sentSmsHistory,
                onToggleRemoteSms = { viewModel.toggleRemoteSmsSending() },
                onSelectSim = { subId -> viewModel.setPreferredSimSubscriptionId(subId) },
                onNavigateToPermissions = onNavigateToPermissions
            )
        }

        // Diagnostics Card (Requirements 10 & 13)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "System Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Permissions, queries & Telegram auth",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.runDiagnostics() },
                            modifier = Modifier.testTag("run_diagnostics_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test")
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        diagnosticResults.forEach { diag ->
                            DiagnosticItemRow(diag = diag)
                        }
                    }
                }
            }
        }

        item {
            // Quick Actions Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Control & Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.sendTestPing() },
                            enabled = !testPingState.isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_test_ping_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (testPingState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending...")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Ping")
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.refreshDeviceStatus()
                                viewModel.runDiagnostics()
                            },
                            modifier = Modifier.testTag("refresh_device_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }

                    AnimatedVisibility(visible = testPingState.message.isNotEmpty()) {
                        val isSuccess = testPingState.isSuccess
                        Text(
                            text = testPingState.message,
                            color = if (isSuccess) StatusGreen else StatusRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            // Recent Logs quick peek
            Card(
                onClick = onNavigateToLogs,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_logs_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Activity & Security Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View incoming commands & forwarded events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "View Logs →",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    isEnabled: Boolean,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) StatusGreen else StatusRed)
            )
            Text(
                text = if (isGranted) {
                    if (isEnabled) "Granted" else "Granted (Muted)"
                } else "Not Granted",
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) StatusGreen else StatusRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DiagnosticItemRow(diag: DiagnosticResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = diag.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (diag.details.isNotBlank()) {
                Text(
                    text = diag.details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (diag.isPassed) StatusGreen.copy(alpha = 0.15f)
                    else StatusRed.copy(alpha = 0.15f)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (diag.isPassed) "PASS" else "FAIL",
                color = if (diag.isPassed) StatusGreen else StatusRed,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HeroStatusCard(
    isRunning: Boolean,
    connState: ServiceConnectionState,
    isConfigured: Boolean,
    pulseAlpha: Float,
    onToggleService: () -> Unit
) {
    val (statusTitle, statusSubtitle, statusColor) = when {
        !isConfigured -> Triple(
            "Configuration Required",
            "Set Bot Token & Authorized User ID to begin",
            StatusAmber
        )
        !isRunning -> Triple(
            "Remote Access Inactive",
            "Service stopped. Tap Start to listen for Telegram commands",
            Color(0xFF8892B0)
        )
        connState == ServiceConnectionState.CONNECTED -> Triple(
            "Remote Access Active",
            "● Connected and listening for Telegram commands",
            StatusGreen
        )
        connState == ServiceConnectionState.CONNECTING -> Triple(
            "Connecting...",
            "Establishing connection to Telegram API",
            StatusAmber
        )
        connState == ServiceConnectionState.RECONNECTING -> Triple(
            "Reconnecting...",
            "Network issue or rate limit. Retrying...",
            StatusAmber
        )
        else -> Triple(
            "Connection Warning",
            "Error reaching Telegram API. Check bot credentials or network.",
            StatusRed
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) statusColor.copy(alpha = pulseAlpha) else statusColor
                            )
                    )
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("service_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) StatusRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Stop Remote Service" else "Start Remote Service",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun StatusMetricItem(
    title: String,
    statusText: String,
    isPositive: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPositive) StatusGreen else StatusAmber)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DeviceStatusCard(deviceStatus: com.example.data.model.DeviceStatus) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = deviceStatus.deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Battery Progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (deviceStatus.isCharging) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Battery: ${deviceStatus.batteryPercent}% (${deviceStatus.chargingSource})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", deviceStatus.batteryTemperatureCelsius)} °C",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { deviceStatus.batteryPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (deviceStatus.batteryPercent > 20) StatusGreen else StatusRed,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Storage Progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val usedGb = deviceStatus.usedStorageBytes.toDouble() / (1024 * 1024 * 1024)
                val totalGb = deviceStatus.totalInternalStorageBytes.toDouble() / (1024 * 1024 * 1024)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Storage: ${String.format("%.1f", usedGb)} GB / ${String.format("%.1f", totalGb)} GB",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${deviceStatus.storagePercentUsed}% used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { (deviceStatus.storagePercentUsed / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Network & OS Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Network: ${deviceStatus.networkType}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Android ${deviceStatus.androidVersion} (API ${deviceStatus.sdkInt})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RemoteExportBackupCard(
    exportProgress: ExportProgress,
    isConfigured: Boolean,
    hasContacts: Boolean,
    hasCalls: Boolean,
    hasSms: Boolean,
    hasMedia: Boolean,
    onStartExport: (ExportType) -> Unit,
    onCancelExport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("remote_export_backup_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "Export & Backup",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Remote Data Export & Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Export to your private Telegram bot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live progress when running
            if (exportProgress.isRunning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏳ ${exportProgress.type?.title ?: "Backup"} Exporting...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${exportProgress.percentage}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (exportProgress.percentage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = exportProgress.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedButton(
                            onClick = onCancelExport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_export_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StatusRed
                            )
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel Export & Clean Files")
                        }
                    }
                }
            } else if (exportProgress.state == ExportState.COMPLETED) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatusGreen.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Last export finished: ${exportProgress.uploadedFiles.size} file(s) uploaded successfully.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGreen
                        )
                    }
                }
            } else if (exportProgress.state == ExportState.FAILED) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatusRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = StatusRed, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Export failed: ${exportProgress.error ?: "Unknown error"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed
                        )
                    }
                }
            }

            Text(
                text = "Available Export Categories:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Category trigger buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onStartExport(ExportType.CONTACTS) },
                    enabled = isConfigured && hasContacts && !exportProgress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_contacts_button")
                ) {
                    Text("Contacts", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = { onStartExport(ExportType.CALLS) },
                    enabled = isConfigured && hasCalls && !exportProgress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_calls_button")
                ) {
                    Text("Calls", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = { onStartExport(ExportType.SMS) },
                    enabled = isConfigured && hasSms && !exportProgress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_sms_button")
                ) {
                    Text("SMS", style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onStartExport(ExportType.MEDIA) },
                    enabled = isConfigured && hasMedia && !exportProgress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_media_button")
                ) {
                    Text("Media", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = { onStartExport(ExportType.FILES) },
                    enabled = isConfigured && !exportProgress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_files_button")
                ) {
                    Text("Files", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { onStartExport(ExportType.ALL) },
                    enabled = isConfigured && !exportProgress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_all_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Full Backup", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                text = "🔒 Security Guarantee: TeleManage accesses data strictly via official Android APIs under your explicit permissions. Private third-party app databases remain isolated by Android sandbox. All archives are wiped from cache immediately after upload.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SmsSendingDashboardCard(
    isPermissionGranted: Boolean,
    isRemoteSmsEnabled: Boolean,
    messagesSentCount: Int,
    availableSims: List<SimCardInfo>,
    preferredSimId: Int,
    recentHistory: List<SentSmsRecord>,
    onToggleRemoteSms: () -> Unit,
    onSelectSim: (Int) -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms_sending_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "SMS Sending",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SMS Sending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Send SMS via Telegram with confirmation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isRemoteSmsEnabled,
                    onCheckedChange = { onToggleRemoteSms() },
                    modifier = Modifier.testTag("toggle_remote_sms_switch")
                )
            }

            // Status metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Permission status chip
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPermissionGranted) StatusGreen.copy(alpha = 0.1f) else StatusRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Permission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (isPermissionGranted) "🟢 Granted" else "🔴 Not Granted",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPermissionGranted) StatusGreen else StatusRed
                        )
                    }
                }

                // Remote SMS Status chip
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRemoteSmsEnabled) StatusGreen.copy(alpha = 0.1f) else StatusAmber.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Remote SMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (isRemoteSmsEnabled) "🟢 Enabled" else "⏸️ Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRemoteSmsEnabled) StatusGreen else StatusAmber
                        )
                    }
                }

                // Messages Sent Count chip
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Messages Sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$messagesSentCount",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // SIM Selection if multiple SIMs detected
            if (availableSims.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Active Carrier / SIM:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSims.forEach { sim ->
                            val isSelected = sim.subscriptionId == preferredSimId ||
                                    (preferredSimId == -1 && sim == availableSims.first())
                            FilledTonalButton(
                                onClick = { onSelectSim(sim.subscriptionId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "SIM ${sim.slotIndex}: ${sim.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Action / Warning Button
            if (!isPermissionGranted) {
                Button(
                    onClick = onNavigateToPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_send_sms_nav_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Grant SMS Sending Permission")
                }
            } else {
                FilledTonalButton(
                    onClick = onToggleRemoteSms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_remote_sms_button")
                ) {
                    Text(if (isRemoteSmsEnabled) "Disable Remote SMS" else "Enable Remote SMS")
                }
            }

            // Recent activity peek
            if (recentHistory.isNotEmpty()) {
                val latest = recentHistory.first()
                Text(
                    text = "Latest: ${latest.recipientNumber} (${latest.status}) - \"${latest.messageSnippet.take(30)}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "🛡️ Security: Telegram commands (/send_sms) require explicit inline confirmation before dispatch. Commands from unauthorized users are immediately rejected. Rate-limited to prevent accidental spam.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServiceHealthTelemetryCard(
    isServiceRunning: Boolean,
    connState: ServiceConnectionState,
    isNotificationListenerActive: Boolean,
    lastTelegramUpdate: Long,
    lastNotificationForwarded: Long,
    lastHeartbeat: Long,
    lastRecoveryTime: Long,
    lastKnownLifecycle: String,
    onRestartConnection: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val lastUpdateStr = if (lastTelegramUpdate > 0) timeFormat.format(Date(lastTelegramUpdate)) else "None"
    val lastNotifStr = if (lastNotificationForwarded > 0) timeFormat.format(Date(lastNotificationForwarded)) else "None"
    val now = System.currentTimeMillis()
    val heartbeatSec = if (lastHeartbeat > 0) ((now - lastHeartbeat) / 1000).coerceAtLeast(0) else -1

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (isServiceRunning) StatusGreen else StatusRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Service Health & Recovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isServiceRunning) {
                    FilledTonalButton(
                        onClick = onRestartConnection,
                        modifier = Modifier.testTag("reconnect_polling_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reconnect", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Health Status Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthStatusRow(
                    label = "Remote Service",
                    value = if (isServiceRunning) "Running (START_STICKY)" else "Stopped",
                    isGood = isServiceRunning
                )

                val connText = when (connState) {
                    ServiceConnectionState.CONNECTED -> "Connected"
                    ServiceConnectionState.CONNECTING -> "Connecting..."
                    ServiceConnectionState.RECONNECTING -> "Reconnecting..."
                    ServiceConnectionState.AUTH_ERROR -> "Auth Required"
                    ServiceConnectionState.NETWORK_ERROR -> "Network Issue"
                    ServiceConnectionState.STOPPED -> "Stopped"
                }
                HealthStatusRow(
                    label = "Telegram Connection",
                    value = connText,
                    isGood = connState == ServiceConnectionState.CONNECTED
                )

                HealthStatusRow(
                    label = "Notification Listener",
                    value = if (isNotificationListenerActive) "Active" else "Inactive",
                    isGood = isNotificationListenerActive
                )

                HealthStatusRow(
                    label = "Last Telegram Update",
                    value = lastUpdateStr,
                    isGood = lastTelegramUpdate > 0
                )

                HealthStatusRow(
                    label = "Last Notification",
                    value = lastNotifStr,
                    isGood = lastNotificationForwarded > 0
                )

                HealthStatusRow(
                    label = "Service Heartbeat",
                    value = if (heartbeatSec >= 0) "${heartbeatSec}s ago" else "Inactive",
                    isGood = heartbeatSec in 0..60
                )

                if (lastRecoveryTime > 0) {
                    val recoveryMins = ((now - lastRecoveryTime) / 1000 / 60)
                    HealthStatusRow(
                        label = "Auto-Recovery",
                        value = "Restored ${recoveryMins}m ago ($lastKnownLifecycle)",
                        isGood = true
                    )
                }
            }

            Text(
                text = "ℹ️ Note: If TeleManage is Force Stopped from Android Settings, Android isolates the package until manually launched on device. Normal app termination or device reboots auto-restore the background service.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HealthStatusRow(
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isGood) StatusGreen else StatusAmber)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
