from pathlib import Path

BRANCH_FILES = {
    "main": Path("app/src/main/java/com/tonorbe/trainerfish/MainActivity.kt"),
    "landing": Path("app/src/main/java/com/tonorbe/trainerfish/TrainerFishLanding.kt"),
    "tv": Path("app/src/main/java/com/tonorbe/trainerfish/LichessTvScreen.kt"),
    "playgames": Path("app/src/main/java/com/tonorbe/trainerfish/playgames/TrainerFishPlayGamesController.kt"),
}


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# MainActivity: Chess TV soft prompt on every Free entry and a true Pro gate for leaderboards.
p = BRANCH_FILES["main"]
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '''    var autoContinueCycleRequest by rememberSaveable { mutableStateOf(false) }
    var showRootExitSupportDialog by rememberSaveable { mutableStateOf(false) }
    val rootProUnlocked by BillingManager.isPro.collectAsState(
''',
    '''    var autoContinueCycleRequest by rememberSaveable { mutableStateOf(false) }
    var showRootExitSupportDialog by rememberSaveable { mutableStateOf(false) }
    var showChessTvSupportDialog by rememberSaveable { mutableStateOf(false) }
    var showLeaderboardProDialog by rememberSaveable { mutableStateOf(false) }
    val rootProUnlocked by BillingManager.isPro.collectAsState(
''',
    "root prompt state",
)

t = replace_once(
    t,
    '''    fun selectedMode(): TrainerMode =
''',
    '''    TrainerFishFeatureProDialog(
        show = showChessTvSupportDialog && !rootProUnlocked,
        title = "Support TrainerFish Chess TV",
        message = "Chess TV is fully available to Free users. Before entering, please consider the lifetime Pro unlock to support live viewing, engine analysis, broadcasts, and continued TrainerFish development.",
        confirmLabel = "Buy Pro",
        dismissLabel = "Maybe later",
        onConfirm = {
            showChessTvSupportDialog = false
            screen = RootScreen.LichessTv
            (ctx as? Activity)?.let { BillingManager.launchPurchase(it) }
        },
        onDismiss = {
            showChessTvSupportDialog = false
            screen = RootScreen.LichessTv
        }
    )

    TrainerFishFeatureProDialog(
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

    fun selectedMode(): TrainerMode =
''',
    "feature prompts",
)

t = replace_once(
    t,
    '''                onOpenLeaderboards = onOpenLeaderboards,
                onWatchLichessTv = { screen = RootScreen.LichessTv }
''',
    '''                onOpenLeaderboards = {
                    if (rootProUnlocked) onOpenLeaderboards()
                    else showLeaderboardProDialog = true
                },
                onWatchLichessTv = {
                    if (rootProUnlocked) screen = RootScreen.LichessTv
                    else showChessTvSupportDialog = true
                }
''',
    "landing callbacks",
)

t = replace_once(
    t,
    '''@Composable
private fun TrainerFishExitSupportDialog(
''',
    '''@Composable
private fun TrainerFishFeatureProDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Composable
private fun TrainerFishExitSupportDialog(
''',
    "feature prompt composable",
)
p.write_text(t, encoding="utf-8")


# Landing: describe the leaderboard as fully Pro rather than view-only for Free.
p = BRANCH_FILES["landing"]
t = p.read_text(encoding="utf-8")
old = '''    val subtitle = when {
        !state.isConfigured -> "Play Console IDs are not configured yet"
        !proUnlocked && state.isChecking ->
            "Connecting • Free users can view; Pro is required to publish scores"
        !proUnlocked && state.isAuthenticated -> {
            val player = state.playerName?.takeIf { it.isNotBlank() } ?: "Play Games player"
            "Signed in as $player • View-only for Free — unlock Pro to publish your scores"
        }
        !proUnlocked ->
            "View all rankings • View-only for Free — unlock Pro to publish your scores"
        state.isChecking -> "Connecting to Google Play Games..."
'''
new = '''    val subtitle = when {
        !state.isConfigured -> "Play Console IDs are not configured yet"
        !proUnlocked && state.isChecking -> "Checking Pro access..."
        !proUnlocked -> "Pro feature • Unlock Pro to view and publish TrainerFish rankings"
        state.isChecking -> "Connecting to Google Play Games..."
'''
t = replace_once(t, old, new, "leaderboard subtitle")
t = replace_once(
    t,
    '''        !state.isConfigured -> "Setup"
        !proUnlocked && state.isChecking -> "Wait"
        !proUnlocked -> "View"
        state.isChecking -> "Wait"
''',
    '''        !state.isConfigured -> "Setup"
        !proUnlocked && state.isChecking -> "Wait"
        !proUnlocked -> "Unlock"
        state.isChecking -> "Wait"
''',
    "leaderboard action",
)
p.write_text(t, encoding="utf-8")


# Play Games controller: enforce the full Pro gate, including defense in depth.
p = BRANCH_FILES["playgames"]
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '''    fun refreshAuthentication(onAuthenticated: (() -> Unit)? = null) {
        if (!configured) {
''',
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
    "pro authentication gate",
)
t = replace_once(
    t,
    '''    fun setProEntitlement(isPro: Boolean) {
        proEntitled = isPro
        // Leaderboard browsing is available to everyone. Entitlement controls only
        // score submission and the in-app Google Play Games profile-name override.
        refreshAuthentication()
    }
''',
    '''    fun setProEntitlement(isPro: Boolean) {
        proEntitled = isPro
        // Leaderboard browsing and score publication are the app's full Pro gate.
        refreshAuthentication()
    }
''',
    "entitlement comment",
)
t = replace_once(
    t,
    '''    fun showLeaderboards() {
        if (!configured) {
''',
    '''    fun showLeaderboards() {
        if (!proEntitled || !BillingManager.isPro.value) {
            uiState = uiState.copy(statusMessage = "TrainerFish Leaderboards require Pro")
            return
        }
        if (!configured) {
''',
    "leaderboard launch gate",
)
p.write_text(t, encoding="utf-8")


# Chess TV: save, copy, and hand the complete PGN to Chess Openings Coach.
p = BRANCH_FILES["tv"]
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '''package com.tonorbe.trainerfish

import android.widget.Toast
''',
    '''package com.tonorbe.trainerfish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
''',
    "android clipboard imports",
)
t = replace_once(
    t,
    '''import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Board
''',
    '''import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.github.bhlangonijr.chesslib.Board
''',
    "file provider import",
)
t = replace_once(
    t,
    '''import java.text.SimpleDateFormat
import java.util.Date
''',
    '''import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
''',
    "file import",
)

anchor = '''private fun tvPgnName(s:LichessTvState):String{
    fun safe(x:String)=x.trim().replace(Regex("[^A-Za-z0-9._-]+"),"_").trim('_').take(32).ifBlank{"player"}
    val d=SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())
    return "TrainerFish_${safe(s.white.name)}_vs_${safe(s.black.name)}_$d.pgn"
}

'''
replacement = anchor + '''private fun copyTvPgnToClipboard(context: Context, pgn: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TrainerFish PGN", pgn))
}

private fun tvPgnContentUri(context: Context, fileName: String, pgn: String): Uri? = runCatching {
    val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val pgnFile = File(shareDir, fileName).apply { writeText(pgn, Charsets.UTF_8) }
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pgnFile
    )
}.getOrNull()

'''
t = replace_once(t, anchor, replacement, "PGN handoff helpers")

t = replace_once(
    t,
    '''    fun savePgn() {
        if(tv.sanMoves.isEmpty()) { Toast.makeText(context,"No moves to save yet.",Toast.LENGTH_SHORT).show(); return }
        pendingPgn=tvPgn(tv); pgnLauncher.launch(tvPgnName(tv))
    }

''',
    '''    fun savePgn() {
        if(tv.sanMoves.isEmpty()) { Toast.makeText(context,"No moves to save yet.",Toast.LENGTH_SHORT).show(); return }
        pendingPgn=tvPgn(tv); pgnLauncher.launch(tvPgnName(tv))
    }

    fun copyPgnToClipboard() {
        if (tv.sanMoves.isEmpty()) {
            Toast.makeText(context, "No moves to copy yet.", Toast.LENGTH_SHORT).show()
            return
        }
        copyTvPgnToClipboard(context, tvPgn(tv))
        Toast.makeText(
            context,
            "PGN copied. Beat the Fish can paste it from the clipboard.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun openPgnInChessOpeningsCoach() {
        if (tv.sanMoves.isEmpty()) {
            Toast.makeText(context, "No moves to open yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val pgn = tvPgn(tv)
        copyTvPgnToClipboard(context, pgn)
        val uri = tvPgnContentUri(context, tvPgnName(tv), pgn)
        if (uri == null) {
            Toast.makeText(
                context,
                "PGN copied, but the temporary PGN file could not be prepared.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        runCatching {
            context.grantUriPermission(
                "com.tonorbe.chessopeningscoach",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val opened = context.openChessOpeningsCoach(CocTarget.PGN_READER, uri)
        if (opened) {
            Toast.makeText(
                context,
                "PGN copied and opened in Chess Openings Coach.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            context.openChessOpeningsCoachPlayStore()
            Toast.makeText(
                context,
                "Chess Openings Coach was not found. The PGN is still on the clipboard.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

''',
    "PGN actions",
)

t = replace_once(
    t,
    '''                    onTopGame = ::showTopGame,
                    onSavePgn = ::savePgn,
                    onFlip = { whiteBottom = !whiteBottom },
''',
    '''                    onTopGame = ::showTopGame,
                    onSavePgn = ::savePgn,
                    onOpenInCoach = ::openPgnInChessOpeningsCoach,
                    onCopyPgn = ::copyPgnToClipboard,
                    onFlip = { whiteBottom = !whiteBottom },
''',
    "study panel callbacks",
)

t = replace_once(
    t,
    '''    onTopGame: () -> Unit,
    onSavePgn: () -> Unit,
    onFlip: () -> Unit,
''',
    '''    onTopGame: () -> Unit,
    onSavePgn: () -> Unit,
    onOpenInCoach: () -> Unit,
    onCopyPgn: () -> Unit,
    onFlip: () -> Unit,
''',
    "study panel parameters",
)

t = replace_once(
    t,
    '''                        DropdownMenuItem(
                            text = { Text("Save game to PGN") }, enabled = canSavePgn,
                            onClick = { controlsMenuExpanded = false; onSavePgn() }
                        )
                        DropdownMenuItem(
                            text = {
''',
    '''                        DropdownMenuItem(
                            text = { Text("Save game to PGN") }, enabled = canSavePgn,
                            onClick = { controlsMenuExpanded = false; onSavePgn() }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in Chess Openings Coach") }, enabled = canSavePgn,
                            onClick = { controlsMenuExpanded = false; onOpenInCoach() }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy PGN to clipboard") }, enabled = canSavePgn,
                            onClick = { controlsMenuExpanded = false; onCopyPgn() }
                        )
                        DropdownMenuItem(
                            text = {
''',
    "PGN menu actions",
)
p.write_text(t, encoding="utf-8")

print("Applied Chess TV semi-gate, leaderboard Pro gate, and PGN handoff changes.")
