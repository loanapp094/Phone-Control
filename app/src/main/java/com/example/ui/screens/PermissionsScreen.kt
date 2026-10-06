package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun PermissionsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isMediaGranted by viewModel.isMediaPermissionGranted.collectAsState()
    val isNotifListenerGranted by viewModel.isNotificationListenerGranted.collectAsState()
    val isContactsGranted by viewModel.isContactsPermissionGranted.collectAsState()
    val isCallLogGranted by viewModel.isCallLogPermissionGranted.collectAsState()
    val isSmsGranted by viewModel.isSmsPermissionGranted.collectAsState()
    val isSendSmsGranted by viewModel.isSendSmsPermissionGranted.collectAsState()

    var isNotificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var isBatteryOptExempt by remember {
        mutableStateOf(
            run {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    // Media permission launcher
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    // Notifications permission launcher (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationPermissionGranted = granted
    }

    // Contacts permission launcher
    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    // Call log permission launcher
    val callLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    // SMS permission launcher
    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    // SMS Sending permission launcher
    val sendSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Export & Remote Permission Center",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "TeleManage operates strictly on personal devices with official Android APIs. Each capability is isolated behind explicit user permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 1. Contacts
        PermissionCard(
            title = "Contacts",
            description = "Enables address book queries (/contacts) and data export (contacts.csv).",
            isGranted = isContactsGranted,
            icon = Icons.Default.Contacts,
            actionLabel = "Grant Contacts Access",
            testTag = "grant_contacts_button",
            onAction = {
                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        )

        // 2. Call History
        PermissionCard(
            title = "Call History",
            description = "Enables call log queries (/calls) and call history export (call_logs.csv).",
            isGranted = isCallLogGranted,
            icon = Icons.Default.Call,
            actionLabel = "Grant Call History Access",
            testTag = "grant_call_log_button",
            onAction = {
                callLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        )

        // 3. SMS Reading
        PermissionCard(
            title = "SMS Reading",
            description = "Enables SMS queries (/sms), thread views, and message export (sms.json).",
            isGranted = isSmsGranted,
            icon = Icons.Default.Message,
            actionLabel = "Grant SMS Access",
            testTag = "grant_sms_button",
            onAction = {
                smsLauncher.launch(Manifest.permission.READ_SMS)
            }
        )

        // 4. SMS Sending (SEND_SMS)
        PermissionCard(
            title = "SMS Sending",
            description = "Allows sending SMS via your personal SIM (/send_sms). Every request requires your explicit Telegram inline confirmation. If restricted by Android or carrier policy, enable 'SMS' in App Settings.",
            isGranted = isSendSmsGranted,
            icon = Icons.AutoMirrored.Filled.Send,
            actionLabel = "Grant SMS Sending Permission",
            testTag = "grant_send_sms_button",
            onAction = {
                sendSmsLauncher.launch(Manifest.permission.SEND_SMS)
            }
        )

        // 4. Photos
        PermissionCard(
            title = "Photos",
            description = "Enables photo downloads (/photos, /photo <id>) and archive export (photos.zip).",
            isGranted = isMediaGranted,
            icon = Icons.Default.Image,
            actionLabel = "Grant Photos Access",
            testTag = "grant_photos_button",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                } else {
                    mediaLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            }
        )

        // 5. Videos
        PermissionCard(
            title = "Videos",
            description = "Enables video downloads (/videos) and archive export (videos.zip).",
            isGranted = isMediaGranted,
            icon = Icons.Default.Videocam,
            actionLabel = "Grant Videos Access",
            testTag = "grant_videos_button",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                } else {
                    mediaLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            }
        )

        // 6. Files
        PermissionCard(
            title = "Files",
            description = "Application sandbox & accessible storage directories for files backup (/export files).",
            isGranted = true,
            statusLabel = "User-accessible",
            icon = Icons.Default.Folder,
            actionLabel = "Configured",
            testTag = "files_access_status",
            onAction = {}
        )

        // 7. Notification Access
        PermissionCard(
            title = "Notification Access",
            description = "Allows capturing incoming notifications & SMS alerts to forward to your private Telegram chat.",
            isGranted = isNotifListenerGranted,
            icon = Icons.Default.NotificationsActive,
            actionLabel = "Open Notification Access",
            testTag = "grant_listener_button",
            onAction = {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )

        // 8. Persistent Notifications
        PermissionCard(
            title = "Persistent Service Notifications",
            description = "Required to display the ongoing notification for background remote management & export services.",
            isGranted = isNotificationPermissionGranted,
            icon = Icons.Default.Notifications,
            actionLabel = "Allow Notifications",
            testTag = "grant_notifications_button",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        // 9. Battery Optimization Exemption
        PermissionCard(
            title = "Background Execution Exemption",
            description = "Prevents Android from killing the remote management service or stopping large backup transfers.",
            isGranted = isBatteryOptExempt,
            icon = Icons.Default.PowerSettingsNew,
            actionLabel = "Exempt Battery Saver",
            testTag = "exempt_battery_button",
            onAction = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )

        // OEM Guidance Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Battery Optimization & OEM Setup Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "To ensure background longevity on modern Android devices, configure Unrestricted Battery:\n\n" +
                            "<b>Standard Android:</b>\n" +
                            "Settings → Apps → TeleManage → Battery → Select 'Unrestricted'\n\n" +
                            "<b>Samsung (One UI):</b>\n" +
                            "Settings → Battery → Background usage limits → Add TeleManage to 'Never sleeping apps'\n\n" +
                            "<b>Xiaomi / MIUI / HyperOS:</b>\n" +
                            "Security app → Manage Apps → TeleManage → Enable 'Autostart' & set Battery Saver to 'No restrictions'\n\n" +
                            "<b>OnePlus / Oppo / Realme:</b>\n" +
                            "Settings → Battery → More Settings → App battery management → Allow foreground & background activity\n\n" +
                            "⚠️ <i>Note on Force Stop:</i> If the app is manually closed via Settings → 'Force Stop', Android strictly prevents any background service execution or boot reception until the user taps the app icon again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    statusLabel: String? = null,
    icon: ImageVector,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGranted) StatusGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                val label = statusLabel ?: if (isGranted) "Granted" else "Not Granted"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isGranted) StatusGreen.copy(alpha = 0.15f)
                            else StatusRed.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isGranted) StatusGreen else StatusRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted && statusLabel == null) {
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
