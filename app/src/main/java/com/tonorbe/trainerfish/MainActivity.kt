package com.tonorbe.trainerfish

import android.app.Activity
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tonorbe.trainerfish.billing.BillingManager
import com.tonorbe.trainerfish.playgames.TrainerFishLeaderboardUpdate
import com.tonorbe.trainerfish.playgames.TrainerFishPlayGamesController
import com.tonorbe.trainerfish.playgames.TrainerFishPlayGamesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class MainActivity : ComponentActivity() {

    private var pendingOpenPgnUri by mutableStateOf<Uri?>(null)
    private var pendingOpenTacticsFromNotification by mutableStateOf(false)
    private lateinit var playGamesController: TrainerFishPlayGamesController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingOpenPgnUri = extractPgnUri(intent)
        pendingOpenTacticsFromNotification = intent?.getBooleanExtra("tf_open_tactics", false) == true

        // Option A: apply user's orientation lock (Auto/Portrait/Landscape)
        runCatching {
            val sp = getSharedPreferences("tf_orientation", MODE_PRIVATE)
            when (sp.getString("orientation_lock", "auto") ?: "auto") {
                "portrait" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "landscape" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        // --- Simple anti-piracy: only run under the official package ID ---
        if (packageName != "com.tonorbe.trainerfish") {
            // If someone re-signed / renamed the app, just exit quietly.
            finish()
            return
        }

        playGamesController = TrainerFishPlayGamesController(this)

        // \uD83D\uDD13 Initialise Billing (reads any existing Pro purchase)
        BillingManager.init(applicationContext)

        // Daily TrainerFish motivational notification at around noon.
        requestTrainerFishNotificationPermissionIfNeeded()
        TrainerFishDailyNotificationScheduler.scheduleDailyNoon(applicationContext)

        // Do NOT preload the opening book at app start.
        // It is a large in-memory structure and can collide with tactics/theme index loading,
        // causing GC-pressure ANRs on some Android 14 devices.
        // Opening/Beat-the-Fish code still loads it lazily when actually needed.

        // Bundled PGN books moved to Trainer Chess Openings Coach.
        // Do not auto-export PGNs from Trainer Fish anymore.

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide system bars (immersive)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            val ctx = LocalContext.current
            val cosSp = remember {
                ctx.getSharedPreferences("gm_cosmetics", MODE_PRIVATE)
            }

            // App theme state (Light / Dark / Silver) persisted in gm_cosmetics.app_theme
            var appThemeKey by rememberSaveable {
                mutableStateOf(
                    cosSp.getString("app_theme", AppThemeKeys.DARK) ?: AppThemeKeys.DARK
                )
            }

            TrainerFishTheme(appThemeKey = appThemeKey) {
                // 👇 This Surface actually paints the screen background
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TLAGMApp(
                        appThemeKey = appThemeKey,
                        onChangeAppThemeKey = { key ->
                            appThemeKey = key
                            cosSp.edit().putString("app_theme", key).apply()
                        },
                        externalPgnUri = pendingOpenPgnUri,
                        onExternalPgnUriConsumed = { pendingOpenPgnUri = null },
                        openTacticsFromNotification = pendingOpenTacticsFromNotification,
                        onOpenTacticsFromNotificationConsumed = { pendingOpenTacticsFromNotification = false },
                        playGamesState = playGamesController.uiState,
                        onOpenLeaderboards = playGamesController::showLeaderboards,
                        onLeaderboardScoreUpdate = playGamesController::recordAndSubmit
                    )
                }
            }
        }
    }

    // ===== Bundled PGN auto-export (assets -> /Android/data/.../files/pgn) =====

    data class BundledPgn(
        val assetName: String,
        val displayName: String
    )

    // Bump this number whenever you add/remove bundled PGNs,
// so existing users will auto-export the new ones.
    private val BUNDLED_PGN_PACK_VERSION = 2

    val bundledPgns: List<BundledPgn> = listOf(
        BundledPgn("chess_fundamentals.pgn", "Chess Fundamentals (Tagalog)"),
        BundledPgn("1001_checkmates.pgn", "Reinfeld - 1001 Checkmates"),
        BundledPgn("1001_sacrifices.pgn", "Reinfeld - 1001 Sacrifices")
    )

    private suspend fun exportBundledPgnsIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("tf_bundled_pgn", MODE_PRIVATE)
        val lastExportVer = prefs.getInt("export_version", 0)

        // Always ensure files exist, but only show toast + update version on pack bump.
        val isPackUpgrade = lastExportVer < BUNDLED_PGN_PACK_VERSION

        val pgnDir = File(getExternalFilesDir(null), "pgn")
        if (!pgnDir.exists()) pgnDir.mkdirs()

        var copiedAny = false

        for (pgn in bundledPgns) {
            val outFile = File(pgnDir, pgn.assetName)
            if (outFile.exists() && outFile.length() > 0L) continue

            runCatching {
                assets.open(pgn.assetName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                copiedAny = true
            }
        }

        if (isPackUpgrade) {
            prefs.edit().putInt("export_version", BUNDLED_PGN_PACK_VERSION).apply()

            // Optional: toast only on pack upgrade (first install or when you add new books)
            if (copiedAny) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Bundled PGNs saved to: ${pgnDir.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenPgnUri = extractPgnUri(intent)
        pendingOpenTacticsFromNotification = intent.getBooleanExtra("tf_open_tactics", false)
    }

    override fun onResume() {
        super.onResume()
        if (::playGamesController.isInitialized) {
            playGamesController.refreshAuthentication()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::playGamesController.isInitialized) {
            playGamesController.shutdown()
        }
        BillingManager.shutdown()
    }
}

/** Simple root nav: landing -> trainer module / clock. */
private enum class RootScreen { Home, Trainer, Clock }

private const val ROOT_INITIAL_TACTICS_ELO = 1200

@Composable
private fun TLAGMApp(
    appThemeKey: String,
    onChangeAppThemeKey: (String) -> Unit,
    externalPgnUri: Uri? = null,
    onExternalPgnUriConsumed: () -> Unit = {},
    openTacticsFromNotification: Boolean = false,
    onOpenTacticsFromNotificationConsumed: () -> Unit = {},
    playGamesState: TrainerFishPlayGamesUiState = TrainerFishPlayGamesUiState(),
    onOpenLeaderboards: () -> Unit = {},
    onLeaderboardScoreUpdate: (TrainerFishLeaderboardUpdate) -> Unit = {}
) {
    val ctx = LocalContext.current

    val startupProfile = remember { ProfilePrefs(ctx) }
    var startupNickname by remember { mutableStateOf(startupProfile.nickname) }
    var showFirstUseProfileDialog by rememberSaveable {
        mutableStateOf(startupNickname.trim().isBlank())
    }

    LaunchedEffect(Unit) {
        val savedName = startupProfile.nickname.trim()
        startupNickname = savedName

        if (savedName.isBlank()) {
            showFirstUseProfileDialog = true
            return@LaunchedEffect
        }

        // Legacy users who already had a nickname but were not initialized yet
        // get their initial Elo before opening the Training Planner.
        if (!startupProfile.ratingInitialized) {
            startupProfile.eloRating = ROOT_INITIAL_TACTICS_ELO
            startupProfile.ratingInitialized = true
        }
    }

    var screen by rememberSaveable { mutableStateOf(RootScreen.Home) }
    var selectedModeName by rememberSaveable { mutableStateOf(TrainerMode.WOODPECKER.name) }
    var autoContinueCycleRequest by rememberSaveable { mutableStateOf(false) }
    var showRootExitSupportDialog by rememberSaveable { mutableStateOf(false) }
    val rootProUnlocked by BillingManager.isPro.collectAsState(
        initial = BillingManager.isProUnlocked(ctx)
    )

    LaunchedEffect(openTacticsFromNotification) {
        if (openTacticsFromNotification) {
            selectedModeName = TrainerMode.WOODPECKER.name
            autoContinueCycleRequest = true
            screen = RootScreen.Trainer
            onOpenTacticsFromNotificationConsumed()
        }
    }

    BackHandler(enabled = !showFirstUseProfileDialog) {
        when (screen) {
            RootScreen.Home -> showRootExitSupportDialog = true
            RootScreen.Trainer -> screen = RootScreen.Home
            RootScreen.Clock -> screen = RootScreen.Home
        }
    }

    TrainerFishExitSupportDialog(
        show = showRootExitSupportDialog,
        proUnlocked = rootProUnlocked,
        onDismiss = { showRootExitSupportDialog = false },
        onUnlockPro = {
            showRootExitSupportDialog = false
            (ctx as? Activity)?.let { BillingManager.launchPurchase(it) }
        },
        onExit = {
            showRootExitSupportDialog = false
            (ctx as? Activity)?.let { activity ->
                runCatching { activity.finishAndRemoveTask() }
                runCatching { activity.finish() }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    )

    fun selectedMode(): TrainerMode =
        runCatching { TrainerMode.valueOf(selectedModeName) }
            .getOrDefault(TrainerMode.WOODPECKER)

    var pendingCocTarget by remember { mutableStateOf<CocTarget?>(null) }

    fun openCocOrShowDialog(target: CocTarget, pgnUri: Uri? = null) {
        val opened = ctx.openChessOpeningsCoach(target = target, pgnUri = pgnUri)
        if (!opened) {
            pendingCocTarget = target
            screen = RootScreen.Home
        }
    }

    LaunchedEffect(externalPgnUri) {
        val uri = externalPgnUri ?: return@LaunchedEffect
        openCocOrShowDialog(CocTarget.PGN_READER, uri)
        onExternalPgnUriConsumed()
    }

    pendingCocTarget?.let { target ->
        ChessOpeningsCoachRedirectDialog(
            target = target,
            onDownload = {
                pendingCocTarget = null
                ctx.openChessOpeningsCoachPlayStore()
            },
            onHome = {
                pendingCocTarget = null
                screen = RootScreen.Home
            },
            onDismiss = {
                pendingCocTarget = null
                screen = RootScreen.Home
            }
        )
    }

    if (showFirstUseProfileDialog) {
        TrainerFishFirstUseProfileDialog(
            onSave = { rawName ->
                val trimmed = rawName.trim()
                if (trimmed.isNotBlank()) {
                    startupNickname = trimmed
                    startupProfile.nickname = trimmed

                    // Do not reset existing users who already have an Elo.
                    // Fresh installs and uninitialized legacy profiles start at 1200.
                    if (!startupProfile.ratingInitialized) {
                        startupProfile.eloRating = ROOT_INITIAL_TACTICS_ELO
                        startupProfile.ratingInitialized = true
                    }

                    showFirstUseProfileDialog = false
                }
            }
        )
    }

    when (screen) {
        RootScreen.Home -> {
            TrainerFishLandingScreen(
                appThemeKey = appThemeKey,
                onChangeAppThemeKey = onChangeAppThemeKey,
                onSelectMode = { mode ->
                    when (mode) {
                        TrainerMode.OPENING -> openCocOrShowDialog(CocTarget.OPENING_EXPLORER)
                        TrainerMode.PGN -> openCocOrShowDialog(CocTarget.PGN_READER)
                        else -> {
                            selectedModeName = mode.name
                            screen = RootScreen.Trainer
                        }
                    }
                },
                onClock = { screen = RootScreen.Clock },
                playGamesState = playGamesState,
                onOpenLeaderboards = onOpenLeaderboards
            )
        }

        RootScreen.Trainer -> {
            // Key by the chosen landing tile so ReplayScreen is rebuilt cleanly
            // when entering a different room from the landing page. This prevents
            // stale Woodpecker/Tactics state from being reused for PGN Reader.
            key(selectedModeName) {
                ReplayScreen(
                    context = ctx,
                    onClock = { screen = RootScreen.Clock },
                    onHome = { screen = RootScreen.Home },
                    initialMode = selectedMode(),
                    appThemeKey = appThemeKey,
                    onChangeAppThemeKey = onChangeAppThemeKey,
                    externalPgnUri = null,
                    onExternalPgnUriConsumed = onExternalPgnUriConsumed,
                    autoContinueCycle = autoContinueCycleRequest,
                    onLeaderboardScoreUpdate = onLeaderboardScoreUpdate
                )
            }
        }

        RootScreen.Clock -> {
            ClockScreen(
                onExit = { screen = RootScreen.Home }
            )
        }
    }
}



@Composable
private fun TrainerFishExitSupportDialog(
    show: Boolean,
    proUnlocked: Boolean,
    onDismiss: () -> Unit,
    onUnlockPro: () -> Unit,
    onExit: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (proUnlocked) "Exit TrainerFish?" else "Support TrainerFish before you go?") },
        text = {
            Text(
                if (proUnlocked) {
                    "Are you sure you want to close the app?"
                } else {
                    "TrainerFish now gives you about 2 million rated puzzles, improved Endgame training, and Beat the Fish for almost free. Pro is a voluntary lifetime unlock that helps keep the app alive and improving."
                }
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!proUnlocked) {
                    TextButton(onClick = onUnlockPro) {
                        Text("Unlock Pro", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onExit) {
                    Text(if (proUnlocked) "Exit" else "Exit anyway")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
private fun TrainerFishFirstUseProfileDialog(
    onSave: (String) -> Unit
) {
    var draftName by rememberSaveable { mutableStateOf("") }
    val trimmed = draftName.trim()

    Dialog(
        onDismissRequest = {
            // Profile is required on first use so the tactics Elo gate is ready
            // before the Training Planner is opened.
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.Transparent,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFEF4444),
                                Color(0xFFF97316),
                                Color(0xFF7C3AED)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(32.dp))
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🐟", fontSize = 40.sp)
                        }
                    }

                    Text(
                        text = "Welcome to TrainerFish",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Create your tactics profile. Your rating and difficulty gates will be ready before you open the Training Planner.",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.96f)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it.take(20) },
                                label = { Text("Profile name", color = Color(0xFF0369A1)) },
                                placeholder = { Text("e.g. Tonorbe", color = Color(0xFF64748B)) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color(0xFF111827),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Black.copy(alpha = 0.24f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "STARTING TACTICS ELO",
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Easy puzzles unlocked",
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "1200",
                                color = Color.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Raise your Elo to unlock Medium, Difficult, and Masterclass tactics permanently.",
                            color = Color.White.copy(alpha = 0.84f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    Button(
                        onClick = { onSave(trimmed) },
                        enabled = trimmed.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.95f),
                            contentColor = Color(0xFF111827),
                            disabledContainerColor = Color.White.copy(alpha = 0.34f),
                            disabledContentColor = Color(0xFF111827).copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = "Start at 1200",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun ChessOpeningsCoachRedirectDialog(
    target: CocTarget,
    onDownload: () -> Unit,
    onHome: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (target) {
        CocTarget.OPENING_EXPLORER -> "Opening Explorer moved to Chess Openings Coach"
        CocTarget.PGN_READER -> "PGN Reader moved to Chess Openings Coach"
    }

    val body = when (target) {
        CocTarget.OPENING_EXPLORER ->
            "Trainer Fish's Opening Explorer is now a separate app called Trainer Chess Openings Coach.\n\n" +
                "It is much better for studying openings because its opening tree is larger, it includes a full PGN reader, database search, player/game tools, and faster opening study features.\n\n" +
                "Please download Trainer Chess Openings Coach from Google Play."

        CocTarget.PGN_READER ->
            "Trainer Fish's PGN Reader is now part of Trainer Chess Openings Coach.\n\n" +
                "It is a stronger PGN reader for books, games, comments, variations, and opening study. If you have already used it before, Chess Openings Coach can resume your last PGN Reader session.\n\n" +
                "Please download Trainer Chess Openings Coach from Google Play."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("Download Chess Openings Coach")
            }
        },
        dismissButton = {
            TextButton(onClick = onHome) {
                Text("Home Screen")
            }
        }
    )
}

private fun copyAssetPgnToAppFolder(context: Context, assetName: String) {
    try {
        val pgnDir = File(context.getExternalFilesDir(null), "pgn")
        if (!pgnDir.exists()) pgnDir.mkdirs()

        val outFile = File(pgnDir, assetName)

        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }

        Toast.makeText(
            context,
            "Saved to: ${outFile.absolutePath}",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving PGN", Toast.LENGTH_LONG).show()
    }
}

private fun extractPgnUri(intent: android.content.Intent?): android.net.Uri? {
    if (intent == null) return null

    val action = intent.action
    val uri = intent.data

    if (action == android.content.Intent.ACTION_VIEW && uri != null) {
        return uri
    }

    return null
}
