package com.nexarq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nexarq.app.ui.NexarqRoot
import com.nexarq.app.ui.theme.NexarqTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NexarqApp).container
        setContent {
            NexarqTheme {
                NexarqRoot(container = container)
            }
        }
    }
}
