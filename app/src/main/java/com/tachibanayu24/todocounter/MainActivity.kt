package com.tachibanayu24.todocounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
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
                if (hasNotificationPermission) {
                    TaskCounterService.start(context)
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted && isSignedIn) {
            TaskCounterService.start(context)
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (hasOverlayPermission && isSignedIn) {
            // サービスを更新してオーバーレイを有効化
            TaskCounterService.update(context)
        }
    }

    LaunchedEffect(Unit) {
        if (isSignedIn) {
            isLoading = true
            taskCount = repository.getTaskCount()
            isLoading = false

            if (hasNotificationPermission) {
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
                text = "ToDo Counter",
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
                    Text("Googleでサインイン")
                }
            } else {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                                text = "通知の許可が必要です",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            ) {
                                Text("許可する")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

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
                                text = "オーバーレイ表示（任意）",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "画面上に常時表示",
                                color = Color.Gray,
                                fontSize = 12.sp
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
                                Text("設定を開く")
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
                                text = "期限切れ",
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
                                text = "今日",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    Text(
                        text = "タスクを取得できませんでした",
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
                    Text("サインアウト", color = Color.Gray)
                }
            }
        }
    }
}
