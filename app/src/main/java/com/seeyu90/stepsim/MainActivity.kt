package com.seeyu90.stepsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.seeyu90.stepsim.ui.GuideScreen
import com.seeyu90.stepsim.ui.PermissionRoute
import com.seeyu90.stepsim.ui.SimulatorRoute
import com.seeyu90.stepsim.ui.theme.StepSimTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StepSimTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Simulator) }
                val backToSimulator = { screen = Screen.Simulator }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                    when (screen) {
                        // 返回模擬頁時，SimulatorRoute 重新進入畫面會再檢查一次權限
                        Screen.Permission -> PermissionRoute(onBack = backToSimulator, modifier = contentModifier)
                        Screen.Guide -> GuideScreen(onBack = backToSimulator, modifier = contentModifier)
                        Screen.Simulator -> SimulatorRoute(
                            onOpenPermission = { screen = Screen.Permission },
                            onOpenGuide = { screen = Screen.Guide },
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}

/** 沒有 Navigation 元件：以這個 enum 切換畫面；授權頁與使用說明頁的返回都回到模擬頁。 */
private enum class Screen { Simulator, Permission, Guide }
