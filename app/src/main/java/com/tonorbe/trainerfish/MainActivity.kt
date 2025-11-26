package com.tonorbe.trainerfish

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.tonorbe.trainerfish.billing.BillingManager
import com.tonorbe.trainerfish.opening.BinaryOpeningBook
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Simple anti-piracy: only run under the official package ID ---
        if (packageName != "com.tonorbe.trainerfish") {
            // If someone re-signed / renamed the app, just exit quietly.
            finish()
            return
        }

        // 🔓 Initialise Billing (reads any existing Pro purchase)
        BillingManager.init(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


        setContent {
            MaterialTheme {
                TLAGMApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BillingManager.shutdown()
    }
}

/** Simple two-screen nav without tabs. */
private enum class RootScreen { Play, Clock }

@Composable
private fun TLAGMApp() {
    val ctx = LocalContext.current

    var screen by rememberSaveable { mutableStateOf(RootScreen.Play) }


    when (screen) {
        RootScreen.Play -> {
            // Your main puzzle/board screen.
            // Keep this as-is if ReplayScreen only requires Context:
            ReplayScreen(
                context = ctx,
                onClock = { screen = RootScreen.Clock }
            )

        }

        RootScreen.Clock -> {
            ClockScreen(
                // Long-press power exits back to Play (board visible)
                onExit = { screen = RootScreen.Play }
            )
        }
    }

}
