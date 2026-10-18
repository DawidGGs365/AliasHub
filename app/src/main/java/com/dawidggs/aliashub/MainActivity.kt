package com.dawidggs.aliashub

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dawidggs.aliashub.ui.AliasHubApp
import com.dawidggs.aliashub.ui.AliasHubTheme
import com.dawidggs.aliashub.ui.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AliasViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = state.themeMode.isDark()

            // enableEdgeToEdge() alone follows the system theme; re-apply it so the status and
            // navigation bar icons match an in-app Light/Dark choice.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { dark },
                )
                onDispose {}
            }

            AliasHubTheme(mode = state.themeMode, systemFont = state.systemFont) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AliasHubApp(vm)
                }
            }
        }
    }
}
