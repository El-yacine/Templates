package com.elyacine.pocketscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elyacine.pocketscan.ui.AppRoot
import com.elyacine.pocketscan.ui.PocketScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketScanTheme {
                AppRoot()
            }
        }
    }
}
