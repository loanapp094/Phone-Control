package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VilServicesScreen(onUnlock: () -> Unit) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIL Services") },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E), // Dark background
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "FLASH",
                color = Color(0xFF00BFFF), // Cyan/Blue color
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            val items = listOf("Activation", "Service Info", "My Topics", "Alert")
            
            items.forEach { item ->
                val isAlert = item == "Alert"
                val interactionSource = remember { MutableInteractionSource() }

                Text(
                    text = item,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null // Removing ripple to make it more native/stealthy, or keep it standard. Let's keep standard ripple for normal items, but user needs to tap 5 times.
                        ) {
                            if (isAlert) {
                                val currentTime = System.currentTimeMillis().toInt()
                                if (currentTime - lastTapTime < 500 || tapCount == 0) {
                                    tapCount++
                                } else {
                                    tapCount = 1
                                }
                                lastTapTime = currentTime

                                if (tapCount >= 5) {
                                    onUnlock()
                                    tapCount = 0
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
                Divider(color = Color(0xFF2A2A2A), thickness = 1.dp)
            }
        }
    }
}
