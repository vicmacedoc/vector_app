package com.vm.vector

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vm.core.ui.theme.VectorTheme
import com.vm.core.ui.theme.PureBlack
import com.vm.vector.ui.DriveConsentLauncher
import com.vm.vector.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            SideEffect {
                window.statusBarColor = PureBlack.toArgb()
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = true
                }
            }
            VectorTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    MainScreen()
                    DriveConsentLauncher()
                }
            }
        }
    }
}