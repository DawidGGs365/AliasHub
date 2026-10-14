package com.dawidggs.aliashub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dawidggs.aliashub.ui.AliasHubApp
import com.dawidggs.aliashub.ui.AliasHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AliasHubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AliasHubApp()
                }
            }
        }
    }
}
