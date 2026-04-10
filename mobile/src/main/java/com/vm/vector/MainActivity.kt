package com.vm.vector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.vm.core.ui.theme.VectorTheme
import com.vm.core.ui.theme.PureBlack
import com.vm.vector.alarm.SleepAlarmScheduler
import com.vm.vector.audio.DailyPlanAudioHelper
import com.vm.vector.ui.DriveConsentLauncher
import com.vm.vector.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FROM_ALARM = "from_alarm"
    }

    private var dailyPlanAudioHelper: DailyPlanAudioHelper? = null

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Stop any playing alarm when app opens
        SleepAlarmScheduler(this).stopAlarmIfPlaying()
        if (intent?.getBooleanExtra(EXTRA_FROM_ALARM, false) == true) {
            intent?.removeExtra(EXTRA_FROM_ALARM)
            playDailyPlanAudioIfAvailable()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FROM_ALARM, false)) {
            intent.removeExtra(EXTRA_FROM_ALARM)
            SleepAlarmScheduler(this).stopAlarmIfPlaying()
            playDailyPlanAudioIfAvailable()
        }
    }

    /** Plays Daily Plan audio when opened from alarm; uses fixed middle volume (0.5). */
    private fun playDailyPlanAudioIfAvailable() {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                val db = (applicationContext as com.vm.vector.VectorApplication).database
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                db.dailyHomeEntryDao().getByDateSync(today)?.dailyPlanAudioPath
            }
            if (!path.isNullOrBlank()) {
                val helper = DailyPlanAudioHelper(applicationContext)
                if (helper.fileExists(path)) {
                    dailyPlanAudioHelper = helper
                    helper.startPlayback(
                        path,
                        onCompletion = { dailyPlanAudioHelper = null },
                        onError = { dailyPlanAudioHelper = null },
                        volume = 0.5f  // fixed middle volume when opened from alarm
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        dailyPlanAudioHelper?.stopPlayback()
        dailyPlanAudioHelper = null
        super.onDestroy()
    }
}