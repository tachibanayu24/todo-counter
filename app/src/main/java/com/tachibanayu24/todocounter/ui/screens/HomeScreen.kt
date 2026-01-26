package com.tachibanayu24.todocounter.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.tachibanayu24.todocounter.R
import com.tachibanayu24.todocounter.auth.GoogleAuthManager
import com.tachibanayu24.todocounter.service.TaskCounterService
import com.tachibanayu24.todocounter.ui.components.AnimatedCounter
import com.tachibanayu24.todocounter.ui.theme.Green
import com.tachibanayu24.todocounter.ui.theme.Red
import com.tachibanayu24.todocounter.ui.theme.Yellow
import com.tachibanayu24.todocounter.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val authManager = remember { GoogleAuthManager(context) }

    var isSignedIn by remember { mutableStateOf(authManager.isSignedIn()) }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasBatteryOptimizationExemption by remember {
        val pm = context.getSystemService(PowerManager::class.java)
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = authManager.handleSignInResult(result.data)
        isSignedIn = account != null
        if (account != null) {
            viewModel.loadData()
            if (hasOverlayPermission) {
                TaskCounterService.start(context)
            }
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (hasOverlayPermission && isSignedIn) {
            TaskCounterService.start(context)
        }
    }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(PowerManager::class.java)
        hasBatteryOptimizationExemption = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        if (isSignedIn && hasOverlayPermission) {
            TaskCounterService.start(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!isSignedIn) {
            // Sign in prompt
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { signInLauncher.launch(authManager.getSignInIntent()) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(stringResource(R.string.sign_in_with_google))
                }
            }
        } else {
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading),
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Permission cards
                if (!hasOverlayPermission) {
                    PermissionCard(
                        title = stringResource(R.string.overlay_permission_required),
                        description = null,
                        onRequestClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!hasBatteryOptimizationExemption) {
                    PermissionCard(
                        title = stringResource(R.string.battery_optimization_title),
                        description = stringResource(R.string.battery_optimization_description),
                        onRequestClick = {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            batteryOptimizationLauncher.launch(intent)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Main task count display
                if (uiState.taskCount != null) {
                    val taskCount = uiState.taskCount!!

                    // Total tasks with animation
                    AnimatedCounter(
                        count = taskCount.total,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (taskCount.total > 0) Red else Green
                    )
                    Text(
                        text = "remaining tasks",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Task breakdown
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        StatItem(
                            value = taskCount.overdue,
                            label = stringResource(R.string.overdue_tasks),
                            color = Red
                        )
                        StatItem(
                            value = taskCount.today,
                            label = stringResource(R.string.today_tasks),
                            color = Yellow
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Quick stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.TaskAlt,
                            value = "${uiState.todayCompleted}",
                            label = "Completed today"
                        )
                        QuickStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.LocalFireDepartment,
                            value = "${uiState.currentStreak}",
                            label = "Day streak"
                        )
                    }
                } else if (uiState.error != null) {
                    Text(
                        text = stringResource(R.string.fetch_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String?,
    onRequestClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRequestClick) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@Composable
private fun StatItem(
    value: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
