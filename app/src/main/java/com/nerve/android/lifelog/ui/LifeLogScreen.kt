package com.nerve.android.lifelog.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.screenshot.ui.ScreenshotSettings
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.RoseAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeLogScreen(vm: LifeLogViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Sense", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Sensory Perception",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Microphone Button with Ripple
            SensoryPulsar(
                isActive = vm.recording && !vm.paused,
                onToggle = { vm.toggleRecording() },
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            val statusText = when {
                !vm.recording -> "Standby"
                vm.paused -> "Paused"
                else -> "Active Listening"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (vm.recording && !vm.paused) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (vm.recording) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { vm.togglePause() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(if (vm.paused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Dashboard Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Sync Queue",
                    value = vm.pendingCount.toString(),
                    unit = "CHUNKS",
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Stability",
                    value = if (vm.failedCount == 0) "100" else "92",
                    unit = "OPTIMAL",
                    color = if (vm.failedCount > 0) RoseAccent else Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { vm.flushNow() },
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = AmberPrimary
                )
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Force Cloud Sync", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }

            if (showSettings) {
                Spacer(modifier = Modifier.height(32.dp))
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        OutlinedTextField(
                            value = vm.homeUrl,
                            onValueChange = { vm.homeUrl = it },
                            label = { Text("Server URL") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = vm.token,
                            onValueChange = { vm.token = it },
                            label = { Text("Auth Token") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Button(
                            onClick = { 
                                vm.saveSettings()
                                showSettings = false
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Screenshot watcher settings — parallel to LifeLog, same Sense tab
            ScreenshotSettings()

            Spacer(modifier = Modifier.height(120.dp)) // padding for pill nav
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, unit: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.headlineLarge, color = color, fontWeight = FontWeight.Black)
                if (value == "100" || value == "92") {
                    Text(text = "%", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Text(text = unit, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AmberPrimary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SensoryPulsar(isActive: Boolean, onToggle: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        if (isActive) {
            // Ripple 1
            Surface(
                modifier = Modifier.size(200.dp).scale(outerScale).alpha(1f - (outerScale - 1f) / 0.6f),
                shape = CircleShape,
                color = AmberPrimary.copy(alpha = 0.1f)
            ) {}
            // Glow
            Surface(
                modifier = Modifier.size(160.dp).scale(pulseScale),
                shape = CircleShape,
                color = AmberPrimary.copy(alpha = 0.05f)
            ) {}
        }
        
        Surface(
            modifier = Modifier.size(180.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(4.dp, if (isActive) AmberPrimary else MaterialTheme.colorScheme.surfaceVariant),
            onClick = onToggle
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(80.dp).scale(if (isActive) pulseScale else 1f),
                    shape = CircleShape,
                    color = if (isActive) AmberPrimary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Toggle Sensory Perception",
                            tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}