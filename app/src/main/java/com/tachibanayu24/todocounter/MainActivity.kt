package com.tachibanayu24.todocounter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tachibanayu24.todocounter.api.TaskCount
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.auth.GoogleAuthManager
import com.tachibanayu24.todocounter.service.TaskCounterService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authManager = remember { GoogleAuthManager(context) }
    val repository = remember { TasksRepository(context) }

    var isSignedIn by remember { mutableStateOf(authManager.isSignedIn()) }
    var taskCount by remember { mutableStateOf<TaskCount?>(null) }
    var isLoading by remember { mutableStateOf(false) }
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
            scope.launch {
                isLoading = true
                taskCount = repository.getTaskCount()
                isLoading = false
                if (hasOverlayPermission) {
                    TaskCounterService.start(context)
                }
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
        if (isSignedIn) {
            isLoading = true
            taskCount = repository.getTaskCount()
            isLoading = false

            if (hasOverlayPermission) {
                TaskCounterService.start(context)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0f0f23)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!isSignedIn) {
                Button(
                    onClick = { signInLauncher.launch(authManager.getSignInIntent()) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(stringResource(R.string.sign_in_with_google))
                }
            } else {
                if (!hasOverlayPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1a3e)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.overlay_permission_required),
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayPermissionLauncher.launch(intent)
                                }
                            ) {
                                Text(stringResource(R.string.open_settings))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (!hasBatteryOptimizationExemption) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1a3e)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.battery_optimization_title),
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.battery_optimization_description),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    batteryOptimizationLauncher.launch(intent)
                                }
                            ) {
                                Text(stringResource(R.string.open_settings))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else if (taskCount != null) {
                    Text(
                        text = "${taskCount!!.total}",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (taskCount!!.total > 0) Color(0xFFff6b6b) else Color(0xFF51cf66)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${taskCount!!.overdue}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFff6b6b)
                            )
                            Text(
                                text = stringResource(R.string.overdue_tasks),
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${taskCount!!.today}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFffd43b)
                            )
                            Text(
                                text = stringResource(R.string.today_tasks),
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.fetch_failed),
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                TextButton(
                    onClick = {
                        scope.launch {
                            TaskCounterService.stop(context)
                            authManager.signOut()
                            isSignedIn = false
                            taskCount = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.sign_out), color = Color.Gray)
                }
            }
        }
    }
}
