from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_exact_count(path: Path, old: str, new: str, expected: int, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} matches in {path}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")


root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/com/tonorbe/trainerfish/MainActivity.kt"
landing = root / "app/src/main/java/com/tonorbe/trainerfish/TrainerFishLanding.kt"
play_games = root / "app/src/main/java/com/tonorbe/trainerfish/playgames/TrainerFishPlayGamesController.kt"
tv_screen = root / "app/src/main/java/com/tonorbe/trainerfish/LichessTvScreen.kt"

# -----------------------------------------------------------------------------
# Chess TV: keep displayed clocks moving locally between server updates.
# -----------------------------------------------------------------------------
replace_once(
    tv_screen,
    "import android.net.Uri\nimport android.widget.Toast\n",
    "import android.net.Uri\nimport android.os.SystemClock\nimport android.widget.Toast\n",
    "SystemClock import",
)

replace_once(
    tv_screen,
    "    var selectedSquare by remember { mutableStateOf<Int?>(null) }\n"
    "    var promotionChoices by remember { mutableStateOf<List<LibMove>>(emptyList()) }\n",
    "    var selectedSquare by remember { mutableStateOf<Int?>(null) }\n"
    "    var promotionChoices by remember { mutableStateOf<List<LibMove>>(emptyList()) }\n"
    "    var displayWhiteSeconds by remember { mutableStateOf<Int?>(null) }\n"
    "    var displayBlackSeconds by remember { mutableStateOf<Int?>(null) }\n",
    "local TV clock state",
)

clock_effect = '''    LaunchedEffect(tv.gameId) {
        if (!detached && tv.gameId != null) whiteBottom = tv.orientationWhite
    }

    // Lichess clock snapshots do not arrive every second. Between snapshots,
    // advance the side-to-move clock locally so the TV display remains smooth.
    // Any fresh FEN or clock value from the server restarts this effect and
    // immediately corrects the local display to the authoritative values.
    LaunchedEffect(
        tv.gameId,
        tv.white.seconds,
        tv.black.seconds,
        tv.fen,
        tv.status,
        tv.watchedGameOngoing,
        detached
    ) {
        if (detached) return@LaunchedEffect

        val serverWhiteSeconds = tv.white.seconds
        val serverBlackSeconds = tv.black.seconds
        displayWhiteSeconds = serverWhiteSeconds
        displayBlackSeconds = serverBlackSeconds

        val liveOrRecovering =
            tv.status == LichessTvConnectionStatus.LIVE ||
                tv.status == LichessTvConnectionStatus.RECONNECTING
        val sourceStillPlaying =
            tv.source != LichessTvSource.WATCHED_PLAYER || tv.watchedGameOngoing
        if (!liveOrRecovering || !sourceStillPlaying) return@LaunchedEffect
        if (serverWhiteSeconds == null && serverBlackSeconds == null) return@LaunchedEffect

        val whiteClockRunning = tv.fen.split(' ').getOrNull(1) != "b"
        val snapshotReceivedAt = SystemClock.elapsedRealtime()

        while (true) {
            delay(200L)
            val elapsedWholeSeconds =
                ((SystemClock.elapsedRealtime() - snapshotReceivedAt) / 1_000L).toInt()

            if (whiteClockRunning) {
                displayWhiteSeconds = serverWhiteSeconds
                    ?.minus(elapsedWholeSeconds)
                    ?.coerceAtLeast(0)
                displayBlackSeconds = serverBlackSeconds
            } else {
                displayWhiteSeconds = serverWhiteSeconds
                displayBlackSeconds = serverBlackSeconds
                    ?.minus(elapsedWholeSeconds)
                    ?.coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(detached, tv.pgnLoaded, tv.gameId, tv.uciMoves) {'''
replace_once(
    tv_screen,
    '''    LaunchedEffect(tv.gameId) {
        if (!detached && tv.gameId != null) whiteBottom = tv.orientationWhite
    }

    LaunchedEffect(detached, tv.pgnLoaded, tv.gameId, tv.uciMoves) {''',
    clock_effect,
    "local TV clock effect",
)

replace_once(
    tv_screen,
    '''    val showBroadcastGameControls =
        !detached &&
            tv.source == LichessTvSource.BROADCAST_BOARD &&
            selectedBroadcastGames.size > 1

    BackHandler(onBack = onHome)
''',
    '''    val showBroadcastGameControls =
        !detached &&
            tv.source == LichessTvSource.BROADCAST_BOARD &&
            selectedBroadcastGames.size > 1
    val displayedWhitePlayer = tv.white.copy(
        seconds = displayWhiteSeconds ?: tv.white.seconds
    )
    val displayedBlackPlayer = tv.black.copy(
        seconds = displayBlackSeconds ?: tv.black.seconds
    )

    BackHandler(onBack = onHome)
''',
    "displayed TV players",
)

replace_exact_count(
    tv_screen,
    "LichessTvPlayers(white = tv.white, black = tv.black)",
    "LichessTvPlayers(white = displayedWhitePlayer, black = displayedBlackPlayer)",
    2,
    "TV player cards",
)

# -----------------------------------------------------------------------------
# Leaderboards: viewing remains free; only score publication is Pro.
# -----------------------------------------------------------------------------
replace_once(
    main,
    "    var showLeaderboardProDialog by rememberSaveable { mutableStateOf(false) }\n",
    "    var showLeaderboardViewDialog by rememberSaveable { mutableStateOf(false) }\n",
    "leaderboard dialog state",
)

replace_once(
    main,
    '''    TrainerFishFeatureProDialog(
        show = showLeaderboardProDialog && !rootProUnlocked,
        title = "TrainerFish Leaderboards are Pro",
        message = "The leaderboards are TrainerFish's remaining full Pro feature. Unlock Pro to view the rankings and publish your training records to Google Play Games.",
        confirmLabel = "Buy Pro",
        dismissLabel = "Maybe later",
        onConfirm = {
            showLeaderboardProDialog = false
            (ctx as? Activity)?.let { BillingManager.launchPurchase(it) }
        },
        onDismiss = { showLeaderboardProDialog = false }
    )
''',
    '''    TrainerFishFeatureProDialog(
        show = showLeaderboardViewDialog && !rootProUnlocked,
        title = "View free. Compete with Pro.",
        message = "Everyone can view the TrainerFish rankings for free. Free users' training records continue to be saved locally, but they are not published to Google Play Games and will not appear on the leaderboards. Unlock Pro to publish your current and future statistics; your retained local records will be submitted after Pro is activated.",
        confirmLabel = "Buy Pro",
        dismissLabel = "View free",
        onConfirm = {
            showLeaderboardViewDialog = false
            (ctx as? Activity)?.let { BillingManager.launchPurchase(it) }
        },
        onDismiss = {
            showLeaderboardViewDialog = false
            onOpenLeaderboards()
        }
    )
''',
    "leaderboard information dialog",
)

replace_once(
    main,
    '''                onOpenLeaderboards = {
                    if (rootProUnlocked) onOpenLeaderboards()
                    else showLeaderboardProDialog = true
                },
''',
    '''                onOpenLeaderboards = {
                    if (rootProUnlocked) onOpenLeaderboards()
                    else showLeaderboardViewDialog = true
                },
''',
    "leaderboard landing action",
)

replace_once(
    landing,
    '''    val subtitle = when {
        !state.isConfigured -> "Play Console IDs are not configured yet"
        !proUnlocked && state.isChecking -> "Checking Pro access..."
        !proUnlocked -> "Pro feature • Unlock Pro to view and publish TrainerFish rankings"
        state.isChecking -> "Connecting to Google Play Games..."
        state.isAuthenticated -> {
            val player = state.playerName?.takeIf { it.isNotBlank() } ?: "Play Games player"
            "Signed in as $player • Compare your training records"
        }
        else -> "Sign in to compare ELO, puzzle records, streaks, and cycles"
    }
    val action = when {
        !state.isConfigured -> "Setup"
        !proUnlocked && state.isChecking -> "Wait"
        !proUnlocked -> "Unlock"
        state.isChecking -> "Wait"
        state.isAuthenticated -> "View"
        else -> "Sign in"
    }
''',
    '''    val subtitle = when {
        !state.isConfigured -> "Play Console IDs are not configured yet"
        !proUnlocked && state.isChecking ->
            "Connecting • Viewing is free; Pro publishes your statistics"
        !proUnlocked && state.isAuthenticated -> {
            val player = state.playerName?.takeIf { it.isNotBlank() } ?: "Play Games player"
            "Signed in as $player • View-only for Free — unlock Pro to publish your statistics"
        }
        !proUnlocked ->
            "View every ranking free • Unlock Pro to publish your statistics and appear on the boards"
        state.isChecking -> "Connecting to Google Play Games..."
        state.isAuthenticated -> {
            val player = state.playerName?.takeIf { it.isNotBlank() } ?: "Play Games player"
            "Signed in as $player • Your TrainerFish records are published with Pro"
        }
        else -> "Sign in to compare ELO, puzzle records, streaks, and cycles"
    }
    val action = when {
        !state.isConfigured -> "Setup"
        state.isChecking -> "Wait"
        state.isAuthenticated -> "View"
        else -> "Sign in"
    }
''',
    "leaderboard card copy",
)

replace_once(
    play_games,
    '''    fun refreshAuthentication(onAuthenticated: (() -> Unit)? = null) {
        if (!proEntitled || !BillingManager.isPro.value) {
            uiState = uiState.copy(
                isChecking = false,
                isAuthenticated = false,
                playerName = null,
                playerTitle = null,
                playerIconUri = null,
                statusMessage = null
            )
            return
        }
        if (!configured) {
''',
    '''    fun refreshAuthentication(onAuthenticated: (() -> Unit)? = null) {
        // Authentication and leaderboard browsing are available to Free users.
        // Entitlement is checked separately at every score-submission boundary.
        if (!configured) {
''',
    "free leaderboard authentication",
)

replace_once(
    play_games,
    '''    fun setProEntitlement(isPro: Boolean) {
        proEntitled = isPro
        // Leaderboard browsing and score publication are the app's full Pro gate.
        refreshAuthentication()
    }
''',
    '''    fun setProEntitlement(isPro: Boolean) {
        proEntitled = isPro
        // Everyone may browse. Pro controls only publication of locally retained stats.
        refreshAuthentication()
    }
''',
    "leaderboard entitlement comment",
)

replace_once(
    play_games,
    '''    fun showLeaderboards() {
        if (!proEntitled || !BillingManager.isPro.value) {
            uiState = uiState.copy(statusMessage = "TrainerFish Leaderboards require Pro")
            return
        }
        if (!configured) {
''',
    '''    fun showLeaderboards() {
        // Viewing is free. flushBestScores() is intentionally a no-op for Free users.
        if (!configured) {
''',
    "free leaderboard viewing",
)

# Safety checks: all three score publication layers must remain Pro-guarded.
play_text = play_games.read_text(encoding="utf-8")
required_guard = "if (!proEntitled || !BillingManager.isPro.value"
if play_text.count(required_guard) < 3:
    raise RuntimeError("Leaderboard score publication lost one or more Pro guards")

# Ensure the old hard-gate wording and state are gone.
for path in (main, landing, play_games):
    text = path.read_text(encoding="utf-8")
    for forbidden in (
        "showLeaderboardProDialog",
        "TrainerFish Leaderboards require Pro",
        "Unlock Pro to view and publish TrainerFish rankings",
    ):
        if forbidden in text:
            raise RuntimeError(f"Old leaderboard hard gate remains in {path}: {forbidden}")

print("Applied local Chess TV clocks and free-view/Pro-publish leaderboard policy.")
