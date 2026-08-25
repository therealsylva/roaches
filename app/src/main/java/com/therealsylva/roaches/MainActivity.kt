package com.therealsylva.roaches

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.therealsylva.roaches.ui.RoachesApp

class MainActivity : ComponentActivity() {
    private var playerActive = false
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readSharedText(intent)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent {
            RoachesApp(
                onPlayerMode = ::setPlayerMode,
                isInPictureInPicture = { isInPictureInPictureMode },
                sharedText = sharedText,
                onSharedTextConsumed = {
                    sharedText = null
                    intent.removeExtra(Intent.EXTRA_TEXT)
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(4_096)
    }

    fun setPlayerMode(active: Boolean) {
        playerActive = active
        requestedOrientation = if (active) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(active)
            setPictureInPictureParams(builder.build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerActive && Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
            )
        }
    }
}
