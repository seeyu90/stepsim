package com.seeyu90.stepsim.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.seeyu90.stepsim.ui.theme.StepSimTheme

/** Health Connect 授權視窗要求的權限用途說明頁（從 Health Connect 查看權限用途時開啟）。 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StepSimTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RationaleContent(Modifier.padding(innerPadding))
                }
            }
        }
    }
}
