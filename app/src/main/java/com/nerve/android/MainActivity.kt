package com.nerve.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nerve.android.morning.MORNING_BRIEF_OPEN_EXTRA

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as NerveApp).shouldOpenMorningBrief =
            intent?.getBooleanExtra(MORNING_BRIEF_OPEN_EXTRA, false) == true
        requestNotificationPermission()

        setContent {
            NerveAppContent(application as NerveApp)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MORNING_BRIEF_OPEN_EXTRA, false)) {
            (application as NerveApp).shouldOpenMorningBrief = true
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 830)
        }
    }
}
