package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.BotConfigScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.VilServicesScreen
import com.example.ui.theme.TeleManageTheme

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    BOT_CONFIG("Telegram Bot", Icons.Default.Key),
    PERMISSIONS("Permissions", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start TelegramRemoteService automatically
        val serviceIntent = android.content.Intent(this, com.example.service.TelegramRemoteService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            TeleManageTheme {
                val viewModel: MainViewModel = viewModel()
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                var isUnlocked by remember { mutableStateOf(false) }

                BackHandler(enabled = isUnlocked && currentScreen != Screen.DASHBOARD) {
                    currentScreen = Screen.DASHBOARD
                }

                if (!isUnlocked) {
                    VilServicesScreen(onUnlock = { isUnlocked = true })
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = if (currentScreen == Screen.DASHBOARD) "TeleManage Remote" else currentScreen.title,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    if (currentScreen != Screen.DASHBOARD) {
                                        IconButton(
                                            onClick = { currentScreen = Screen.DASHBOARD },
                                            modifier = Modifier.testTag("top_bar_back_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back to Dashboard"
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.testTag("bottom_nav_bar"),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Screen.values().forEach { screen ->
                                    NavigationBarItem(
                                        selected = currentScreen == screen,
                                        onClick = { currentScreen = screen },
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = { Text(screen.title) },
                                        modifier = Modifier.testTag("nav_tab_${screen.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Crossfade(
                            targetState = currentScreen,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                Screen.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToBotConfig = { currentScreen = Screen.BOT_CONFIG },
                                    onNavigateToPermissions = { currentScreen = Screen.PERMISSIONS }
                                )
                                Screen.BOT_CONFIG -> BotConfigScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.DASHBOARD }
                                )
                                Screen.PERMISSIONS -> PermissionsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.DASHBOARD }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
