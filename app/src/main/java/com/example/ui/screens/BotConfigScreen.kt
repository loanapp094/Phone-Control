package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun BotConfigScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val currentConfig by viewModel.botConfig.collectAsState()
    val testState by viewModel.botTestState.collectAsState()

    var tokenInput by remember(currentConfig.botToken) { mutableStateOf(currentConfig.botToken) }
    var userIdInput by remember(currentConfig.authorizedUserId) {
        mutableStateOf(if (currentConfig.authorizedUserId != 0L) currentConfig.authorizedUserId.toString() else "")
    }
    var isTokenVisible by remember { mutableStateOf(false) }
    var saveSuccessMessage by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Security Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = "Hardware-Backed Keystore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Credentials are encrypted using AES-256-GCM backed by Android KeyStore. Only your authorized User ID can issue commands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Input Form Card
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
                    text = "Telegram Bot Credentials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Bot Token
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        saveSuccessMessage = false
                    },
                    label = { Text("Telegram Bot Token") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstuVWXyz") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bot_token_input"),
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle token visibility"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // User ID
                OutlinedTextField(
                    value = userIdInput,
                    onValueChange = {
                        userIdInput = it.filter { char -> char.isDigit() }
                        saveSuccessMessage = false
                    },
                    label = { Text("Authorized Telegram User ID") },
                    placeholder = { Text("e.g. 987654321") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_id_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = "Only this numerical Telegram ID will be allowed to issue commands. All others are blocked.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Buttons: Test Connection & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.testBotConnection(tokenInput) },
                        enabled = tokenInput.isNotBlank() && !testState.isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_bot_connection_button")
                    ) {
                        if (testState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Bot")
                        }
                    }

                    Button(
                        onClick = {
                            val id = userIdInput.toLongOrNull() ?: 0L
                            viewModel.saveCredentials(tokenInput, id, testState.botUsername)
                            saveSuccessMessage = true
                        },
                        enabled = tokenInput.isNotBlank() && userIdInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_credentials_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }

                // Test Connection Feedback
                AnimatedVisibility(visible = testState.message.isNotEmpty()) {
                    Text(
                        text = testState.message,
                        color = if (testState.isSuccess) StatusGreen else StatusRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Save feedback
                AnimatedVisibility(visible = saveSuccessMessage) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Credentials saved securely to KeyStore!",
                            color = StatusGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Setup Guide Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Setup Instructions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "1. Create your Telegram Bot:\n" +
                            "   • Open Telegram and search for @BotFather\n" +
                            "   • Send /newbot and choose a name & username\n" +
                            "   • Copy the HTTP API token into the field above.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "2. Find your Telegram User ID:\n" +
                            "   • Search for @userinfobot or @raw_data_bot on Telegram\n" +
                            "   • Send /start — it will reply with your numerical Id\n" +
                            "   • Copy your ID into the 'Authorized User ID' field.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "3. Verify & Start:\n" +
                            "   • Tap 'Test Bot' then 'Save'\n" +
                            "   • Return to the dashboard and start the Remote Service.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
