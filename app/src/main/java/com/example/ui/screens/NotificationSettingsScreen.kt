package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel

@Composable
fun NotificationSettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val config by viewModel.botConfig.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Remote Access & Forwarding Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Configure which capabilities TeleManage responds to remotely via Telegram.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Remote Commands Access Control Card (Requirements 8 & 7)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Telegram Remote Command Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Contact Access
                    SettingToggleRow(
                        title = "Contact Access",
                        subtitle = "Allow responding to /contacts and /contact search commands",
                        isChecked = config.isContactAccessEnabled,
                        onCheckedChange = { viewModel.preferenceManager.setContactAccess(it) },
                        testTag = "contact_access_switch"
                    )

                    // Call History Access
                    SettingToggleRow(
                        title = "Call History Access",
                        subtitle = "Allow responding to /calls and filtered call log commands",
                        isChecked = config.isCallHistoryAccessEnabled,
                        onCheckedChange = { viewModel.preferenceManager.setCallHistoryAccess(it) },
                        testTag = "call_history_access_switch"
                    )

                    // SMS Access
                    SettingToggleRow(
                        title = "SMS Access",
                        subtitle = "Allow responding to /sms and conversation thread queries",
                        isChecked = config.isSmsAccessEnabled,
                        onCheckedChange = { viewModel.preferenceManager.setSmsAccess(it) },
                        testTag = "sms_access_switch"
                    )

                    // SMS Notification Forwarding
                    SettingToggleRow(
                        title = "SMS Notification Alerts",
                        subtitle = "Forward incoming SMS notifications to authorized Telegram chat",
                        isChecked = config.isSmsNotificationForwardingEnabled,
                        onCheckedChange = { viewModel.preferenceManager.setSmsNotificationForwarding(it) },
                        testTag = "sms_notif_forwarding_switch"
                    )
                }
            }
        }

        // Notification Forwarding Switches Card
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Notification Listener Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Master Switch
                    SettingToggleRow(
                        title = "Enable Notification Forwarding",
                        subtitle = "Master switch to capture and forward phone notifications",
                        isChecked = config.isNotificationForwardingEnabled,
                        onCheckedChange = { viewModel.preferenceManager.setNotificationForwarding(it) },
                        testTag = "master_notification_switch"
                    )

                    // Telegram Loop Protection
                    SettingToggleRow(
                        title = "Loop Protection (Ignore Telegram)",
                        subtitle = "Prevents infinite loops by dropping notifications originating from Telegram",
                        isChecked = config.isIgnoreTelegramNotifs,
                        onCheckedChange = { viewModel.preferenceManager.setIgnoreTelegramNotifs(it) },
                        testTag = "loop_protection_switch"
                    )

                    // Ignore Ongoing Notifications
                    SettingToggleRow(
                        title = "Ignore Ongoing Notifications",
                        subtitle = "Skip persistent notifications (media players, navigation, active timers)",
                        isChecked = config.isIgnoreOngoingNotifs,
                        onCheckedChange = { viewModel.preferenceManager.setIgnoreOngoingNotifs(it) },
                        testTag = "ongoing_filter_switch"
                    )

                    // Auto-start on boot
                    SettingToggleRow(
                        title = "Auto-Start Remote Service on Boot",
                        subtitle = "Resumes Telegram remote management after device restart",
                        isChecked = config.isAutoStartOnBoot,
                        onCheckedChange = { viewModel.preferenceManager.setAutoStartOnBoot(it) },
                        testTag = "auto_start_switch"
                    )
                }
            }
        }

        // App Whitelist/Blacklist Filtering Section
        item {
            Text(
                text = "App-Specific Filtering",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Toggle off any application you do not want forwarded to Telegram.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search installed apps") },
                placeholder = { Text("e.g. WhatsApp, Instagram, Messages") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search_input"),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (isLoadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                AppFilterRow(
                    app = app,
                    onToggle = { viewModel.toggleAppFilter(app.packageName) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun AppFilterRow(
    app: com.example.data.model.AppFilterItem,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (app.isIgnored) "Muted" else "Forwarding",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.isIgnored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = !app.isIgnored,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("app_filter_switch_${app.packageName}")
                )
            }
        }
    }
}
