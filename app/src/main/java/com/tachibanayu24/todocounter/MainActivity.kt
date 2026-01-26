package com.tachibanayu24.todocounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tachibanayu24.todocounter.ui.navigation.AppNavigation
import com.tachibanayu24.todocounter.ui.theme.TodoCounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoCounterTheme {
                AppNavigation()
            }
        }
    }
}
