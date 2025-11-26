package com.tonorbe.trainerfish

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tonorbe.trainerfish.billing.BillingManager
import com.tonorbe.trainerfish.engine.EnginePrefs
import com.tonorbe.trainerfish.engine.JniStockfishUci
import com.tonorbe.trainerfish.engine.ProcEngine
import com.tonorbe.trainerfish.opening.BinaryOpeningBook
import com.tonorbe.trainerfish.opening.BookMove
import com.tonorbe.trainerfish.opening.EcoClassifier
import com.tonorbe.trainerfish.opening.OpeningArrowsOverlay
import com.tonorbe.trainerfish.opening.OpeningPack
import com.tonorbe.trainerfish.pgn.PgnGameInfo
import com.tonorbe.trainerfish.pgn.PgnSession
import com.tonorbe.trainerfish.pgn.RatingBucket
import com.tonorbe.trainerfish.pgn.countGamesInResource
import com.tonorbe.trainerfish.pgn.listThemesCached
import com.tonorbe.trainerfish.pgn.listThemesInRawQuick
import com.tonorbe.trainerfish.pgn.loadGamesByIndexes
import com.tonorbe.trainerfish.pgn.loadRatingBuckets
import com.tonorbe.trainerfish.pgn.loadThemeIndex
import com.tonorbe.trainerfish.pgn.sampleIdsFromBucketsQuick
import com.tonorbe.trainerfish.pgn.sampleIdsFromThemeAndBucketsQuick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random


// ---------- Utilities ----------
private fun sign(v: Int) = when {
    v > 0 -> 1
    v < 0 -> -1
    else -> 0
}

private const val CYCLE_MAX = 500



// --- FEN + user-side helpers -----------------------------------------------

// True if the FEN says it's White to move. If FEN is blank, default to White.
private fun fenWhiteToMove(fen: String?): Boolean {
    if (fen.isNullOrBlank()) return true
    val sideField = fen.trim().split(Regex("\\s+")).getOrNull(1)?.lowercase()
    return sideField != "b"
}

private fun squareFromAlgebra(algebra: String): com.github.bhlangonijr.chesslib.Square =
    com.github.bhlangonijr.chesslib.Square.valueOf(algebra.uppercase())


// The user always plays the *second* mover from the FEN.
private fun userPlaysWhiteFromFen(startFen: String?): Boolean = !fenWhiteToMove(startFen)


// Simple mapping from XP → fish badge emoji (or null if none yet)
// ----- Fish badge helpers -----

private fun fishBadgeForXp(xp: Int): String? = when {
    xp >= 10000 -> "Rainbow Fish"
    xp >= 8000 -> "Black Fish"
    xp >= 6000 -> "Orange Fish"
    xp >= 4000 -> "Red Fish"
    xp >= 2000 -> "Blue Fish"
    else -> null
}

fun fishBadgeLabel(xp: Int): String? = when {
    xp >= 10000 -> "GM🐟"
    xp >= 8000 -> "IM🐟"
    xp >= 6000 -> "FM🐟"
    xp >= 4000 -> "CM🐟"
    xp >= 2000 -> "🐟"
    else -> null
}


// --- UI: indicator that always shows the USER's side (not whose turn it is) --
@Composable
private fun TurnIndicatorUserSide(
    userIsWhite: Boolean,
    nickname: String,
    elapsedLabel: String?,
    xp: Int
) {
    val who = if (userIsWhite) "White" else "Black"
    val trimmedNick = nickname.trim()
    val playLabel = if (trimmedNick.isEmpty()) {
        "Play $who"
    } else {
        "$trimmedNick, play $who"
    }

    val badgeEmoji = fishBadgeLabel(xp)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!elapsedLabel.isNullOrBlank()) {
                Text(elapsedLabel, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badgeEmoji != null) {
                    Text(
                        text = badgeEmoji,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(playLabel, fontSize = 12.sp)
            }
        }

        Box(
            Modifier
                .size(14.dp)
                .border(1.dp, Color.DarkGray, CircleShape)
                .background(if (userIsWhite) Color.White else Color.Black, CircleShape)
        )
    }
}



@Composable
private fun PuzzleHeaderRow(label: String, rating: Int?) {
    if (label.isBlank() && rating == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
        if (rating != null) {
            Text(
                text = "Rating: $rating",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// ---------- Series ----------
enum class Series(val id: String, val title: String, val rawRes: Int) {
    CHALLENGER("challenger", "Challenger", R.raw.train_all),
    MASTER("master", "Masterclass", R.raw.train_all)
}



// ---------- Confetti & tuning ----------
private const val CONFETTI_DURATION_MS = 4000
private const val CONFETTI_COUNT = 28
private const val CONFETTI_FALL_SQUARES = 3.5f
private const val CONFETTI_MAX_DRIFT = 1.2f


// Move animation duration (user asked 1.5s)
private const val MOVE_ANIM_MS = 400

// ---------- Per-track prefs ----------
class CyclePrefs(ctx: Context, key: String) {
    private val sp = ctx.getSharedPreferences("gm_cycle_$key", Context.MODE_PRIVATE)

    var poolAbsCsv: String
        get() = sp.getString("poolAbsCsv", "") ?: ""
        set(v) { sp.edit().putString("poolAbsCsv", v).apply() }

    // Last-used cycle parameters (for display only)
    var lastMin: Int
        get() = sp.getInt("last_min", 0)
        set(v) { sp.edit().putInt("last_min", v).apply() }

    var lastMax: Int
        get() = sp.getInt("last_max", 0)
        set(v) { sp.edit().putInt("last_max", v).apply() }

    var lastTheme: String
        get() = sp.getString("last_theme", "All") ?: "All"
        set(v) { sp.edit().putString("last_theme", v).apply() }


    var size: Int
        get() = sp.getInt("size", 25)
        set(v) { sp.edit().putInt("size", v).apply() }

    var poolCsv: String
        get() = sp.getString("pool_csv", "") ?: ""
        set(v) { sp.edit().putString("pool_csv", v).apply() }

    var solvedCsv: String
        get() = sp.getString("solved_csv", "") ?: ""
        set(v) { sp.edit().putString("solved_csv", v).apply() }

    var ptsEarned: Int
        get() = sp.getInt("pts_earned", 0)
        set(v) { sp.edit().putInt("pts_earned", v).apply() }

    var ptsTotal: Int
        get() = sp.getInt("pts_total", 0)
        set(v) { sp.edit().putInt("pts_total", v).apply() }

    var elapsedMs: Long
        get() = sp.getLong("elapsed_ms", 0L)
        set(v) { sp.edit().putLong("elapsed_ms", v).apply() }

    var solvedCount: Int
        get() = sp.getInt("solved_count", 0)
        set(v) { sp.edit().putInt("solved_count", v).apply() }

    var streak: Int
        get() = sp.getInt("streak", 0)
        set(v) { sp.edit().putInt("streak", v).apply() }

    var maxStreak: Int
        get() = sp.getInt("max_streak", 0)
        set(v) { sp.edit().putInt("max_streak", v).apply() }

    var xp: Int
        get() = sp.getInt("xp", 0)
        set(v) { sp.edit().putInt("xp", v).apply() }

    var cycleId: Int
        get() = sp.getInt("cycle_id", 1)
        set(v) { sp.edit().putInt("cycle_id", v).apply() }

    var globalSeenCsv: String
        get() = sp.getString("global_seen_csv", "") ?: ""
        set(v) { sp.edit().putString("global_seen_csv", v).apply() }

    var soundOn: Boolean
        get() = sp.getBoolean("sound_on", true)
        set(v) { sp.edit().putBoolean("sound_on", v).apply() }

    // Consumable
    var shieldCharges: Int
        get() = sp.getInt("shield_charges", 0)
        set(v) { sp.edit().putInt("shield_charges", v).apply() }


    fun resetStatsOnly() {
        ptsEarned = 0
        ptsTotal = 0
        elapsedMs = 0L
        solvedCount = 0
        solvedCsv = ""
        streak = 0
        maxStreak = 0
    }
}

data class OpeningPvLine(
    val eval: String,
    val moves: String
)


// ---------- Profile (nickname) prefs ----------
class ProfilePrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gm_profile", Context.MODE_PRIVATE)
    var nickname: String
        get() = sp.getString("nickname", "") ?: ""
        set(v) { sp.edit().putString("nickname", v).apply() }
}

// ---------- 3D Push Button ----------
@Composable
fun PushButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elev by androidx.compose.animation.core.animateDpAsState(if (pressed) 2.dp else 8.dp, label = "btn_elev")
    val offsetY by androidx.compose.animation.core.animateDpAsState(if (pressed) 1.dp else 0.dp, label = "btn_off")
    val padH = if (compact) 10.dp else 14.dp
    val padV = if (compact) 6.dp else 10.dp
    val fontSize = if (compact) 13.sp else 16.sp

    Surface(
        modifier = modifier
            .offset(y = offsetY)
            .shadow(elev, shape, clip = false)
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = shape,
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (pressed) 0.dp else 2.dp
    ) {
        Row(
            Modifier.padding(horizontal = padH, vertical = padV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leading != null) leading()
            Text(
                text,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



// ---------- Nickname dialog ----------
@Composable
private fun NicknameDialog(
    current: String,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var text by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Set your nickname") },
        text = {
            Column {
                Text("This will be shown on your milestone posters.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 20) text = it },
                    placeholder = { Text("e.g. Tonorbe") },
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

// ---------- Helper text ----------
private fun tierTextFor(s: Series, p: Float): String = when (s) {
    Series.CHALLENGER ->
        if (p >= 70f) "You're ready for Masterclass!" else "Keep training to improve!"
    Series.MASTER -> when {
        p >= 90f -> "Grandmaster level"
        p >= 80f -> "Master level"
        p >= 70f -> "Expert level"
        p >= 60f -> "Advanced level"
        p >= 50f -> "Intermediate level"
        else     -> "Patzer level"
    }
}

@Composable
private fun LoadingGalleryDialog(
    show: Boolean,
    images: List<Int>,                 // e.g., many drawables
    captions: List<String> = emptyList(),
    message: String = "Preparing your puzzles… This may take a minute.",
    sampleCount: Int = 12,             // how many distinct images to cycle (random subset)
    switchMs: Long = 5000L,            // frame duration
    onDismiss: (() -> Unit)? = null
) {
    if (!show || images.isEmpty()) return

    // Build a random order (optionally sample a random subset) once per 'images' change
    val order: List<Int> = remember(images, sampleCount) {
        val idx = images.indices.shuffled()
        idx.take(sampleCount.coerceIn(1, images.size))
    }

    // Start at a random frame within the order
    var frame by remember(order) { mutableStateOf(kotlin.random.Random.nextInt(order.size)) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (onDismiss != null) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            // Advance while shown
            LaunchedEffect(show, order, switchMs) {
                while (show && order.isNotEmpty()) {
                    kotlinx.coroutines.delay(switchMs)
                    frame = (frame + 1) % order.size
                }
            }

            val cur = order[frame]

            Image(
                painter = painterResource(id = images[cur]),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .align(Alignment.TopCenter),
                contentScale = ContentScale.Fit
            )

            // Optional caption aligned with the randomized order
            captions.getOrNull(cur)?.takeIf { it.isNotBlank() }?.let { cap ->
                Text(
                    cap,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .align(Alignment.Center),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        message,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "These are the World Champions of Chess. You could be next!",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private const val START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

// ---------- Opening: tolerant SAN → legal move matcher ----------

fun normalizeSan(s: String) = s
    .replace("+","")
    .replace("#","")
    .replace("e.p.","")
    .trim()

private fun pieceTypeFromLetter(ch: Char): com.github.bhlangonijr.chesslib.PieceType = when (ch) {
    'K' -> com.github.bhlangonijr.chesslib.PieceType.KING
    'Q' -> com.github.bhlangonijr.chesslib.PieceType.QUEEN
    'R' -> com.github.bhlangonijr.chesslib.PieceType.ROOK
    'B' -> com.github.bhlangonijr.chesslib.PieceType.BISHOP
    'N' -> com.github.bhlangonijr.chesslib.PieceType.KNIGHT
    else -> com.github.bhlangonijr.chesslib.PieceType.PAWN
}

fun moveToUci(m: com.github.bhlangonijr.chesslib.move.Move): String {
    val from = m.from.toString().lowercase()
    val to   = m.to.toString().lowercase()
    val promo = m.promotion
    if (promo != null && promo != com.github.bhlangonijr.chesslib.Piece.NONE) {
        val ch = when (promo.pieceType) {
            com.github.bhlangonijr.chesslib.PieceType.QUEEN  -> 'q'
            com.github.bhlangonijr.chesslib.PieceType.ROOK   -> 'r'
            com.github.bhlangonijr.chesslib.PieceType.BISHOP -> 'b'
            com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> 'n'
            else -> 'q'
        }
        return "$from$to$ch"
    }
    return "$from$to"
}

/** Try to resolve JSON SAN to a legal Move on the current board without relying on our SAN printer. */
fun sanToLegalMove(board: com.github.bhlangonijr.chesslib.Board, sanRaw: String): com.github.bhlangonijr.chesslib.move.Move? {
    var san = normalizeSan(sanRaw)

    // 1) Castling
    if (san.startsWith("O-O")) {
        val longCastle = san.startsWith("O-O-O")
        val white = (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE)
        val toSq = when {
            white && longCastle -> com.github.bhlangonijr.chesslib.Square.C1
            white && !longCastle-> com.github.bhlangonijr.chesslib.Square.G1
            !white && longCastle-> com.github.bhlangonijr.chesslib.Square.C8
            else                -> com.github.bhlangonijr.chesslib.Square.G8
        }
        return com.github.bhlangonijr.chesslib.move.MoveGenerator
            .generateLegalMoves(board)
            .firstOrNull { mv ->
                val p = board.getPiece(mv.from)
                p.pieceType == com.github.bhlangonijr.chesslib.PieceType.KING && mv.to == toSq
            }
    }

    // Regexes
    val rePromo = Regex("""^([a-h])x?([a-h][18])=([QRBN])$""")          // exd8=Q, a8=Q
    val rePawnCap = Regex("""^([a-h])x([a-h][1-8])$""")                  // exd5
    val rePawnPush = Regex("""^([a-h][1-8])$""")                         // e4
    val rePiece = Regex("""^([KQRBN])([a-h1-8]?)(x?)([a-h][1-8])$""")    // Nf3, Nbd2, R1e1, Bxe6

    // 2) Promotion
    rePromo.matchEntire(san)?.let { m ->
        val fromFile = m.groupValues[1][0]
        val to = squareFromAlgebra(m.groupValues[2])
        val promoType = pieceTypeFromLetter(m.groupValues[3][0])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN &&
                    mv.to == to &&
                    mv.promotion?.pieceType == promoType &&
                    mv.from.toString().lowercase()[0] == fromFile
        }
    }

    // 3) Pawn capture (incl. en passant)
    rePawnCap.matchEntire(san)?.let { m ->
        val fromFile = m.groupValues[1][0]
        val to = squareFromAlgebra(m.groupValues[2])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN &&
                    mv.to == to &&
                    mv.from.toString().lowercase()[0] == fromFile
        }
    }

    // 4) Pawn push
    rePawnPush.matchEntire(san)?.let { m ->
        val to = squareFromAlgebra(m.groupValues[1])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN && mv.to == to
        }
    }

    // 5) Piece move with optional disambiguation & capture
    rePiece.matchEntire(san)?.let { m ->
        val pt = pieceTypeFromLetter(m.groupValues[1][0])
        val disamb = m.groupValues[2]              // file or rank or empty
        val to = squareFromAlgebra(m.groupValues[4])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            if (p.pieceType != pt) return@firstOrNull false
            if (mv.to != to) return@firstOrNull false
            if (disamb.isNotEmpty()) {
                val ch = disamb[0]
                val fromStr = mv.from.toString().lowercase() // e2
                if (ch in 'a'..'h' && fromStr[0] != ch) return@firstOrNull false
                if (ch in '1'..'8' && fromStr[1] != ch) return@firstOrNull false
            }
            true
        }
    }

    // Fallback: match by our own SAN printer if available
    runCatching {
        val legal = com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board)
        val target = normalizeSan(san)
        return legal.firstOrNull { mv ->
            normalizeSan(prettyFromUci(board, moveToUci(mv))) == target
        }
    }.getOrNull()?.let { return it }

    return null
}

// ---------- Screen ----------
@Composable
private fun FullscreenBlackout() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
}
// Top-level (file scope)
private enum class EndgameResult { WIN, LOSS, DRAW }

@Composable
fun ReplayScreen(
    context: android.content.Context,
    onUtilities: () -> Unit = {},
    onFenChanged: (String) -> Unit = {},
    onClock: () -> Unit = {},
    autoStart: Boolean = true,

) {


    // Global mode switch for this screen
    var mode by rememberSaveable { mutableStateOf(TrainerMode.WOODPECKER) }
    // Core state
    var games by remember { mutableStateOf(listOf<PgnGameInfo>()) }
    var session by remember { mutableStateOf<PgnSession?>(null) }
    var current by remember { mutableStateOf<PgnGameInfo?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }
    var status by remember { mutableStateOf("Loading…") }
    var loading by remember { mutableStateOf(false) }

    var engine by remember { mutableStateOf<JniStockfishUci?>(null) }

    // Scoring / puzzle
    var plyTick by remember { mutableStateOf(0) }
    var whiteBottom by remember { mutableStateOf(true) }
    var puzzleStartMs by remember { mutableStateOf(0L) }
    var puzzleSteps by remember { mutableStateOf(0) }
    var puzzleTotalPoints by remember { mutableStateOf(0) }
    var earnedPoints by remember { mutableStateOf(0) }
    var scoringEnabled by remember { mutableStateOf(true) }
    var didApplaud by remember { mutableStateOf(false) }

    var lastFrom by remember { mutableStateOf<Int?>(null) }
    var lastTo   by remember { mutableStateOf<Int?>(null) }
    // Changing between Tactics / Endgame / Opening → start with no last-move highlight.
    LaunchedEffect(mode) {
        lastFrom = null
        lastTo = null
    }

    // Read once, synchronously, so the very first frame is already gated
    val firstRun = remember { FirstRunPrefs(context) }
    var showFirstRun by rememberSaveable { mutableStateOf(!firstRun.seenWelcome) }
    var showWelcome  by rememberSaveable { mutableStateOf(firstRun.seenWelcome) }
    var showCycleComplete by remember { mutableStateOf(false) }


    // NEW: Settings & nested menus
    var showSettings by remember { mutableStateOf(false) }
    var showCelebrationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }



    // ---- TACTICS SNAPSHOT HELPERS ----

     fun saveTacticsSnapshot() {
        val g   = games
        val cur = current
        if (g.isNullOrEmpty() || cur == null || currentIndex < 0) return

        ReplaySnapshot.save(
            games       = g,
            session     = session,      // PgnSession?
            current     = cur,          // PgnGameInfo?
            index       = currentIndex,
            whiteBottom = whiteBottom,
            mode        = TrainerMode.WOODPECKER
        )
    }


    fun restoreTacticsFromSnapshot(): Boolean {
        if (!ReplaySnapshot.has) return false

        val g   = ReplaySnapshot.games ?: return false
        val cur = ReplaySnapshot.current ?: return false
        val idx = ReplaySnapshot.index
        if (idx < 0) return false

        games        = g
        session      = ReplaySnapshot.session
        current      = cur
        currentIndex = idx
        whiteBottom  = ReplaySnapshot.whiteBottom

        // No welcome / gallery — go straight to the board
        showWelcome = false
        status      = "Trainer — continue cycle."

        return true
    }





    // ---- Wall clock timer (elapsed since puzzle start) ----
    var wallStartMs by remember { mutableStateOf<Long?>(null) }
    var wallElapsedSec by remember { mutableStateOf(0) }
    fun startWallTimer() { wallStartMs = SystemClock.elapsedRealtime(); wallElapsedSec = 0 }
    fun formatWallMmSs(sec: Int) = "%02d:%02d".format(sec / 60, sec % 60)

    // Restart wall timer whenever the current puzzle changes (use your existing index)
    LaunchedEffect(currentIndex) {
        wallStartMs = SystemClock.elapsedRealtime()
        wallElapsedSec = 0
    }


    // Tick the wall timer once per second while active
    LaunchedEffect(wallStartMs) {
        while (wallStartMs != null) {
            wallElapsedSec = (((SystemClock.elapsedRealtime() - (wallStartMs ?: 0L)) / 1000L).toInt())
            delay(1000)
        }
    }

    // --- Engine UI state (declare BEFORE any usage) ---
    var engineEnabled by remember { mutableStateOf(false) }         // UI toggle
    var engineStatus  by remember { mutableStateOf("TrainerFish: off") }  // small status line


    // Multi-PV for OPENING explorer only
    var multiPv by remember { mutableStateOf(1) }   // 1..4 from the UI
    var openingPvLines by remember { mutableStateOf<List<String>>(emptyList()) }

    val appCtx = LocalContext.current

    // ---- Endgame Trainer state ----

    var endgameEvents by remember { mutableStateOf(listOf<String>()) }
    var showEndgameVerdict by remember { mutableStateOf(false) } // reveal verdict after 2s wait
    val enginePrefs = remember { EnginePrefs(appCtx) }   // remembers BUILTIN/LATEST choice
    // Stream job for continuous eval
    var evalJob by remember { mutableStateOf<Job?>(null) }
    val engineScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }



    // ==== Endgame Trainer: picker + progress ====
    val egSp = remember { context.getSharedPreferences("endgame_prefs", Context.MODE_PRIVATE) }
    var hidePlayed by rememberSaveable { mutableStateOf(egSp.getBoolean("hide_played", false)) }
    var playedSet by remember { mutableStateOf(egSp.getStringSet("eg_played", emptySet())!!.toMutableSet()) }
    fun markEgPlayed(idx: Int) {
        playedSet.add(idx.toString())
        egSp.edit().putStringSet("eg_played", playedSet).apply()
    }
    LaunchedEffect(hidePlayed) { egSp.edit().putBoolean("hide_played", hidePlayed).apply() }

    // Which Endgame index is currently loaded (in endgameEvents)?
    var endgameIdx by rememberSaveable { mutableStateOf(-1) }

    // ===== Opening Explorer prefs (which tree to use) =====
    val openingSp = remember { context.getSharedPreferences("opening_prefs", Context.MODE_PRIVATE) }
    var openingPack by rememberSaveable {
        mutableStateOf(
            when (openingSp.getString("opening_pack", "E4")) {
                "D4"     -> OpeningPack.D4
                "OTHERS" -> OpeningPack.OTHERS
                else     -> OpeningPack.E4
            }
        )
    }

    var showOpeningArrows by rememberSaveable { mutableStateOf(true) }


    // Cancel scope only when this screen actually leaves
    DisposableEffect(Unit) {
        onDispose { engineScope.cancel() }
    }

    // Selected series (persist)
    val seriesSp = remember { context.getSharedPreferences("gm_series", Context.MODE_PRIVATE) }
    fun readSeries(): Series = when (seriesSp.getString("selected", Series.CHALLENGER.name)) {
        Series.MASTER.name -> Series.MASTER
        else -> Series.CHALLENGER
    }
    var series by remember { mutableStateOf(readSeries()) }
    fun saveSeries(s: Series) { seriesSp.edit().putString("selected", s.name).apply() }

    var showExitConfirm by remember { mutableStateOf(false) }

    // Profile
    val profile = remember { ProfilePrefs(context) }
    var nickname by remember { mutableStateOf(profile.nickname) }
    var showProfile by remember { mutableStateOf(false) }

    // Posters
    var showPoster by remember { mutableStateOf(false) }
    var posterHeadline by remember { mutableStateOf("") }
    var posterSub by remember { mutableStateOf("") }
    var posterFoot by remember { mutableStateOf("") }

    val context = LocalContext.current

    val rnd = remember { Random(System.currentTimeMillis()) }
    // Multi-cycle: one bank per series
    val cycles = remember(series) { CycleBank(context, series.id) }
    var activeCycleId by remember(series) { mutableStateOf(cycles.ensureInit()) }
    var prefs by remember(series, activeCycleId) { mutableStateOf(cycles.prefs(activeCycleId)) }

    var soundOn by remember(series, activeCycleId) { mutableStateOf(prefs.soundOn) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val tinyButtons = screenWidthDp < 360

    // Cosmetics prefs
    val cosSp = remember { context.getSharedPreferences("gm_cosmetics", Context.MODE_PRIVATE) }

    // Celebration
    var celebration by remember { mutableStateOf(cosSp.getString("celebration", "none") ?: "none") }
    fun setCelebration(mode: String) { celebration = mode; cosSp.edit().putString("celebration", mode).apply() }

    // Board / pieces / sounds
    var boardTheme by remember { mutableStateOf(cosSp.getString("board_theme", "classic") ?: "classic") }
    fun equipBoardTheme(t: String) { boardTheme = t; cosSp.edit().putString("board_theme", t).apply() }

    var pieceStylePref by remember { mutableStateOf(cosSp.getString("piece_style", "solid") ?: "solid") }

    var soundPack by remember { mutableStateOf(cosSp.getString("sound_pack", "wood") ?: "wood") }
    fun equipSoundPack(s: String) { soundPack = s; cosSp.edit().putString("sound_pack", s).apply() }

    data class BoardTheme(val light: Color, val dark: Color)
    val themeColors: BoardTheme = when (boardTheme) {
        "wood" -> BoardTheme(
            light = Color(0xFFEED8B0), // light wood
            dark  = Color(0xFF8D6E63)  // dark wood
        )

        "night" -> BoardTheme(
            light = Color(0xFFB0BEC5), // light slate
            dark  = Color(0xFF37474F)  // dark slate
        )

        "blue" -> BoardTheme(
            light = Color(0xFFBBDEFB), // pale blue
            dark  = Color(0xFF1976D2)  // royal blue
        )

        "sand" -> BoardTheme(
            light = Color(0xFFF5E0C3), // sand
            dark  = Color(0xFFCC8E35)  // desert brown
        )

        "forest" -> BoardTheme(
            light = Color(0xFFC8E6C9), // soft green
            dark  = Color(0xFF2E7D32)  // deep forest
        )

        else -> BoardTheme(
            light = Color(0xFFEEEED2), // classic green board
            dark  = Color(0xFF769656)
        )
    }

    val pieceStyle = if (pieceStylePref == "outline") PieceStyle.Outline else PieceStyle.Solid

    // ===== Opening Explorer state (binary book) =====
    var openingReady by remember { mutableStateOf(false) }

    val openingBoard = remember { com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(START_FEN) } }
    var openingFen by remember { mutableStateOf(openingBoard.fen) }
    var openingPly by remember { mutableStateOf(0) }
    val openingSanPath = remember { mutableStateListOf<String>() }


    fun openingReset() {
        // Reset last-move highlight whenever a new opening position/tree is shown
        lastFrom = null
        lastTo = null
        openingBoard.loadFromFen(START_FEN)
        openingFen = openingBoard.fen
        openingPly = 0
        openingSanPath.clear()
    }


    var endgameOver by remember { mutableStateOf(false) }
    var endgameResult by remember { mutableStateOf<EndgameResult?>(null) }
    var endgamePlaying by remember { mutableStateOf(false) }

    // When true, we blink the "Play position" button to tell the user to press it
    var endgamePlayHint by remember { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (mode != TrainerMode.ENDGAME) {
            endgamePlayHint = false
        }
    }


    // When user drags/taps a pawn to the last rank we store "from+to"
    var pendingPromotionUci by remember { mutableStateOf<String?>(null) }


    // Reset on new endgame position
    LaunchedEffect(session) {
        if (mode == TrainerMode.ENDGAME) {
            endgameOver = false
            endgameResult = null
        }
    }


    // Endgame-only UI state
    val endgameMoves = remember { mutableStateListOf<String>() }
    var endgameUserIsWhite by remember { mutableStateOf(true) }


    // On loading an Endgame (FEN-only) position, clear move list and lock user color.
    // Also orient board so the user is at the bottom.
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        if (s.totalPly == 0) {
            endgameMoves.clear()
            endgameUserIsWhite = (s.board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE)
            whiteBottom = endgameUserIsWhite
        }
    }

    // Reset when a new FEN-only session is loaded
    LaunchedEffect(session) {
        if (mode == TrainerMode.ENDGAME) endgameOver = false
    }


    // Reset on new endgame position
    LaunchedEffect(session) {
        if (mode == TrainerMode.ENDGAME) {
            endgameOver = false
            endgameResult = null
        }
    }


    // Mistakes/timeouts for streak logic
    var madeMistake by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }

    // Once true, this puzzle is considered finished and we ignore further taps
    var puzzleOver by remember { mutableStateOf(false) }

    // Sounds & haptics
    val feedback = rememberFeedback(context)

    LaunchedEffect(soundOn) { feedback.soundsEnabled = soundOn }
    val view = LocalView.current
    val ctx = LocalContext.current

    // Board + animation
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var lastDragPos by remember { mutableStateOf<Offset?>(null) }
    var selectedSq by remember { mutableStateOf<Int?>(null) }
    var animFrom by remember { mutableStateOf<Int?>(null) }
    var animTo by remember { mutableStateOf<Int?>(null) }
    val animT = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var animJob by remember { mutableStateOf<Job?>(null) }

    var openingLoading by remember { mutableStateOf(false) }

    // Raw PV lines coming from ProcEngine ("+0.20 e2e4 e7e5 ...")
    var enginePvLines by remember { mutableStateOf(emptyList<String>()) }

    val scope = rememberCoroutineScope()

    // 5-minute per-move timer
    var deadlineAtMs by remember { mutableStateOf<Long?>(null) }
    fun resetTurnTimer() { /* disabled: no per-move countdown */ }
    fun stopTurnTimer()  { deadlineAtMs = null /* keep null so any UI reads are safe */ }


    // "Good Job" state depends on timer
    val goodJobNow = session?.let { it.ply >= it.totalPly } == true
    val iconOnlyTopBar = tinyButtons || goodJobNow


    // Live FEN from your current session (adjust getter if needed)
    val currentFen = remember(session?.ply) { session?.board?.fen ?: current?.startFen ?: "" }

    // Warm-up nudge so the *first* Engine ON actually produces an eval
    LaunchedEffect(engineEnabled, mode, currentFen) {
        if (!engineEnabled) return@LaunchedEffect
        // Only care in trainer modes that use the generic eval loop
        if (mode != TrainerMode.WOODPECKER && mode != TrainerMode.OPENING) return@LaunchedEffect

        // Give ProcEngine a moment to fully start the first time
        delay(400)

        // If the engine is still on after the delay, bump plyTick so the main
        // LaunchedEffect below re-runs and sends the eval command.
        if (engineEnabled) {
            plyTick++
        }
    }

    LaunchedEffect(engineEnabled, endgameOver, currentIndex, plyTick, mode) {
        if (!engineEnabled || endgameOver) return@LaunchedEffect

        // Only run the engine in trainer modes
        if (mode != TrainerMode.WOODPECKER && mode != TrainerMode.OPENING) return@LaunchedEffect

        // Which position should FrankenFish evaluate?
        val fen = when (mode) {
            TrainerMode.OPENING ->
                openingBoard.fen
            else ->
                session?.board?.fen ?: current?.startFen.orEmpty()
        }

        if (fen.isBlank()) return@LaunchedEffect

        // Temporary text until info lines start coming in
        engineStatus = "TrainerFish: running"

        // Separate search settings for Tactics vs Opening:
        //  - WOODPECKER: think ~3 seconds, then stop until the position changes
        //  - OPENING: allow very deep search (up to ~1 hour) unless the board changes
        val movetimeMs =
            if (mode == TrainerMode.OPENING)
                3_600_000          // always allow 1 hour think time
            else if (mode == TrainerMode.WOODPECKER)
                3_000              // quick for tactics
            else
                1_500

        runCatching {
            com.tonorbe.trainerfish.engine.ProcEngine.evaluateFen(
                fen = fen,
                movetimeMs = movetimeMs
            )
        }
    }


    // Helper: true if side-to-move is white (FEN field #2)
    //fun whiteToMoveFen(f: String) = f.split(' ').getOrNull(1) == "w"

    // 2) Read raw engine centipawns (UCI is POV = side-to-move)
    //val rawCp by ProcEngine.scoreCp.collectAsState(initial = null)

    // --- Pro/Lite gating: real purchase + dev override ---
    val proCtx = LocalContext.current

    // 1) Real purchase state (Play Billing)
    val billingPro by BillingManager.isPro.collectAsState(
        initial = BillingManager.isProUnlocked(proCtx)
    )

    // 2) Dev / testing override stored in shared prefs
    val premiumPrefs = remember { PremiumPrefs(proCtx) }
    var devProOverride by remember { mutableStateOf(premiumPrefs.isPro) }

    // 3) Final flag used everywhere
    val proUnlocked = billingPro || devProOverride

    var showProDialog by remember { mutableStateOf(false) }
    var proDialogReason by remember { mutableStateOf<String?>(null) }

    fun requirePro(reason: String) {
        proDialogReason = reason
        showProDialog = true
    }


    // 3) Normalize to "White advantage = positive"
    //val whiteCp = remember(rawCp, currentFen) {
    //    rawCp?.let { cp -> if (whiteToMoveFen(currentFen)) cp else -cp }
    //}

    val uciLines by ProcEngine.lines.collectAsState(initial = emptyList())

    LaunchedEffect(uciLines, mode, multiPv) {
        if (mode == TrainerMode.ENDGAME) return@LaunchedEffect

        val infoLines = uciLines.filter { line ->
            line.startsWith("info ") && line.contains(" score ")
        }
        if (infoLines.isEmpty()) return@LaunchedEffect

        val cpRegex       = Regex("""\bscore\s+cp\s+(-?\d+)""")
        val mateRegex     = Regex("""\bscore\s+mate\s+(-?\d+)""")
        val multipvRegex  = Regex("""\bmultipv\s+(\d+)""")

        data class LineInfo(
            val multi: Int,
            val cpSort: Int,
            val raw: String
        )

        val map = mutableMapOf<Int, LineInfo>()

        for (raw in infoLines) {
            val mp   = multipvRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val mate = mateRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val cp   = cpRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val cpSort = when {
                mate != null && mate > 0  ->  100000 - mate
                mate != null && mate < 0  -> -100000 - mate
                cp   != null              -> cp
                else                      -> 0
            }

            map[mp] = LineInfo(mp, cpSort, raw)
        }

        if (map.isEmpty()) return@LaunchedEffect

        val ordered = map.values
            .sortedByDescending { it.cpSort }
            .take(multiPv.coerceAtLeast(1))

        val showPv = (mode == TrainerMode.OPENING)

        // Human-readable strings (used for header + optional text list)
        val prettyLines: List<String> = ordered.map { li ->
            val full = buildEngineStatusFromInfo(li.raw, showPv = showPv)
            full.removePrefix("TrainerFish:").trim()
        }

        if (prettyLines.isNotEmpty()) {
            engineStatus = "TrainerFish: " + prettyLines.first()
        }

        // For the simple text list (if you still use openingPvLines)
        openingPvLines = if (mode == TrainerMode.OPENING) prettyLines else emptyList()

        // ⬇️ NEW: raw PV lines for the colored multi-PV panel
        if (mode == TrainerMode.OPENING) {
            val pvRegex = Regex("""\bpv\s+(.+)$""")

            val lines = ordered.mapNotNull { li ->
                val raw = li.raw

                // Extract eval again (cp or mate) for display
                val mate = mateRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val cp   = cpRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

                val evalStr = when {
                    mate != null -> "M$mate"
                    cp   != null -> "%.2f".format(cp / 100.0)
                    else         -> "0.00"
                }

                // Extract the PV moves (UCI list after "pv")
                val pvMatch   = pvRegex.find(raw) ?: return@mapNotNull null
                val movesPart = pvMatch.groupValues.getOrNull(1)?.trim().orEmpty()
                if (movesPart.isBlank()) return@mapNotNull null

                // Format expected by prettyOpeningPv: "eval uci1 uci2 ..."
                "$evalStr $movesPart"
            }

            enginePvLines = lines
        } else {
            enginePvLines = emptyList()
        }
    }



    fun stopEngineNow(setState: Boolean = true) {
        if (setState) engineEnabled = false      // make the toggle reflect OFF immediately
        engineStatus = "TrainerFish: OFF"
        engineScope.launch {
            runCatching { ProcEngine.stop() }.onFailure { /* engine may already be gone; ignore */ }
            enginePvLines = emptyList()

        }
    }

    // Confetti
    var showConfetti by remember { mutableStateOf(false) }

    // Restore snapshot (from Clock OR tab switch) when we enter that mode
    LaunchedEffect(ReplaySnapshot.has, mode) {
        if (ReplaySnapshot.has && mode == ReplaySnapshot.mode) {

            // Only Tactics actually uses the big games list
            if (ReplaySnapshot.mode == TrainerMode.WOODPECKER) {
                ReplaySnapshot.games?.let { games = it }
            }

            session      = ReplaySnapshot.session
            current      = ReplaySnapshot.current
            currentIndex = ReplaySnapshot.index
            whiteBottom  = ReplaySnapshot.whiteBottom

            // Safety: if we’re in Endgame, don’t let the Woodpecker eval ticker run.
            if (mode == TrainerMode.ENDGAME) {
                try { evalJob?.cancel() } catch (_: Throwable) {}
                evalJob = null
                stopEngineNow(setState = true)
                engineEnabled = false
                // If you want to reopen the Endgame picker/UI, do it here:
                // showEndgames = true
                // status = "Endgame trainer — choose a position"
            }

            showWelcome = false
            plyTick++            // force a board redraw
            ReplaySnapshot.clear()
        }
    }

    // ---- Endgame Trainer helpers ----
    fun listEventLabels(context: android.content.Context, @androidx.annotation.RawRes resId: Int): List<String> {
        val out = ArrayList<String>()
        val re = Regex("""^\s*\[Event\s+"([^"]*)"]\s*$""")
        context.resources.openRawResource(resId).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line -> re.find(line)?.let { out += it.groupValues[1] } }
        }
        return out
    }



    // NEW: start the in-process JNI engine (Frankenfish) directly
    fun startEngineNow() {
        // Optimistic status while we spin up the engine
        engineStatus = "TrainerFish: starting…"

        engineScope.launch(Dispatchers.IO) {
            try {
                // This is idempotent: if it's already running, ProcEngine.start() just returns.
                com.tonorbe.trainerfish.engine.ProcEngine.start("inline")

                withContext(Dispatchers.Main) {
                    engineStatus = "TrainerFish: running"
                }
            } catch (t: Throwable) {
                // If anything goes wrong, turn the toggle off and show the error
                withContext(Dispatchers.Main) {
                    engineEnabled = false
                    engineStatus = "TrainerFish: failed (${t.message ?: "unknown error"})"
                }
            }
        }
    }


    suspend fun loadEndgameAt(index: Int) {

            // Lite: only the first 20 positions are playable
            if (!proUnlocked && index >= 20) {
                withContext(Dispatchers.Main) {
                    requirePro("Only the first 20 endgame positions are available in the free version of TrainerFish.")
                }
                return
            }

        // New endgame position → no stale highlight from the previous one
            lastFrom = null
            lastTo = null

            // Always stop any ongoing engine/eval before changing the position
            try { evalJob?.cancel() } catch (_: Throwable) {}


            // Always stop any ongoing engine/eval before changing the position
        try { evalJob?.cancel() } catch (_: Throwable) {}
        evalJob = null
        stopEngineNow(setState = true)
        engineEnabled = false
        endgamePlaying = false
        showEndgameVerdict = false

        // Clear board while loading so the user sees a clean preview
        session = null
        current = null
        currentIndex = -1
        status = "Loading endgame…"
        mode = TrainerMode.ENDGAME

        val info = withContext(Dispatchers.IO) {
            com.tonorbe.trainerfish.pgn
                .loadGamesByIndexes(context, R.raw.endgames, listOf(index))
                .firstOrNull()
        }
        if (info == null) {
            status = "Failed to load endgame."
            return
        }

        val sess = com.tonorbe.trainerfish.pgn.PgnSession(info.game).apply {
            reset(info.startFen)
        }
        session = sess
        current = info
        currentIndex = index
        // Keep selected index in sync so the list highlight matches the board
        endgameIdx = index

        status = if (info.event.isNotBlank()) info.event else "Endgame preview"
    }


    // ======================= Endgame: start playing the current position =======================

    fun startEndgamePlay() {
        try {
            if (mode != TrainerMode.ENDGAME) {
                status = "Switch to Endgame Trainer first."
                return
            }
            val s = session ?: run {
                status = "Pick a position first."
                return
            }
            if (endgamePlaying) {
                status = "Already playing this position."
                return
            }

            // Fresh state for this position
            try { evalJob?.cancel() } catch (_: Throwable) {}
            evalJob = null
            try { stopEngineNow(setState = false) } catch (_: Throwable) {}
            showEndgameVerdict = false
            endgameOver = false
            endgameResult = null
            endgameMoves.clear()

            endgamePlaying = true

            // Engine is completely idle until AFTER the first user move.
            engineStatus = "Your move."
            status = "Your move."
            // We leave engineEnabled as-is; the Endgame logic will drive the fish explicitly.
        } catch (t: Throwable) {
            Log.e("TrainerFish", "startEndgamePlay error", t)
            status = "Error starting endgame."
        }
    }


    val wp = remember { WoodpeckerStore(context) }


    fun computePuzzleSteps(totalPly: Int) = (totalPly + 1) / 2

    fun interruptAnimation() { animJob?.cancel() }

    fun capFor(s: Series): Int =
        if (series == s && games.isNotEmpty()) kotlin.math.min(games.size, CYCLE_MAX) else CYCLE_MAX

    fun csvToSet(csv: String): MutableSet<Int> =
        if (csv.isBlank()) mutableSetOf() else csv.split(',').mapNotNull { it.toIntOrNull() }.toMutableSet()
    fun setToCsv(s: Set<Int>): String = s.joinToString(",")

    // Prefer puzzles with NO recorded best time/score; then fill remaining slots randomly.
    fun buildNewCyclePool(totalGames: Int, sizeReq: Int): Set<Int> {
        val capLocal = kotlin.math.max(25, kotlin.math.min(CYCLE_MAX, totalGames))
        val size = kotlin.math.max(25, kotlin.math.min(sizeReq, capLocal))

        val unseen = (0 until totalGames).filter { idx ->
            val hasBestTime = wp.puzzleBestMs(idx) > 0L
            val (bestPts, bestTot) = wp.puzzleBestPoints(idx)
            !(hasBestTime || bestTot > 0)
        }

        val pick = mutableSetOf<Int>()
        pick += unseen.shuffled(rnd).take(size)
        if (pick.size < size) {
            val filler = (0 until totalGames).filterNot { it in pick }.shuffled(rnd).take(size - pick.size)
            pick += filler
        }

        prefs.size = size
        prefs.poolCsv = setToCsv(pick)
        prefs.solvedCsv = ""
        prefs.ptsEarned = 0
        prefs.ptsTotal = 0
        prefs.elapsedMs = 0L
        prefs.solvedCount = 0
        prefs.streak = 0
        prefs.maxStreak = 0

        return pick
    }


    // Build the same caption used in Welcome: "<Theme> • <min>–<max> • <size>"
    fun cycleCaptionFor(pf: CyclePrefs): String {
        val theme = pf.lastTheme.ifBlank { "All" }
        val min   = (pf.lastMin.takeIf { it > 0 } ?: 1800)
        val max   = (pf.lastMax.takeIf { it > 0 } ?: 3210)
        val size  = (pf.size.takeIf { it > 0 } ?: 25)
        return "$theme • $min–$max • $size"
    }


    fun currentPool(): Set<Int> {
        val s = csvToSet(prefs.poolCsv)
        return if (s.isEmpty() && games.isNotEmpty()) buildNewCyclePool(games.size, prefs.size) else s
    }
    fun solvedSet(): MutableSet<Int> = csvToSet(prefs.solvedCsv)
    fun nextIndexFromPool(): Int? {
        val pool = currentPool()
        val solved = solvedSet()
        val remaining = pool.filterNot { it in solved }
        return if (remaining.isEmpty()) null else remaining.random(rnd)
    }

    fun markSolvedForCycle(idx: Int, spentMs: Long, earnedForRecord: Int) {
        if (scoringEnabled) {
            prefs.ptsEarned += earnedForRecord
            prefs.ptsTotal  += puzzleTotalPoints
            prefs.elapsedMs += spentMs
            prefs.solvedCount += 1
        }
        val solved = solvedSet()
        solved += idx
        prefs.solvedCsv = setToCsv(solved)
        val pool = currentPool()
        if (solved.size >= pool.size) {
            val seen = csvToSet(prefs.globalSeenCsv)
            seen += pool
            prefs.globalSeenCsv = setToCsv(seen)
            showCycleComplete = true
        }
    }

    // Keep pool size without wiping stats (unless there is no pool yet).
    fun ensurePoolSize(totalGames: Int, requestedSize: Int) {
        val cap = kotlin.math.max(25, kotlin.math.min(CYCLE_MAX, totalGames))
        val req = requestedSize.coerceIn(25, cap)

        // If there is no pool yet (first start or after "Reset stats") create a new one.
        if (prefs.poolCsv.isBlank()) {
            buildNewCyclePool(totalGames, req)
            return
        }

        val pool = csvToSet(prefs.poolCsv).toMutableSet()
        val solved = csvToSet(prefs.solvedCsv)
        val curSize = pool.size
        if (curSize == req) return

        if (req > curSize) {
            // Grow
            val need = req - curSize
            val unseen = (0 until totalGames)
                .filter { idx ->
                    val hasBestTime = wp.puzzleBestMs(idx) > 0L
                    val (bp, bt) = wp.puzzleBestPoints(idx)
                    !(hasBestTime || bt > 0)
                }
                .filterNot { it in pool }
                .shuffled(rnd)
                .toMutableList()

            val add = mutableListOf<Int>()
            while (add.size < need && unseen.isNotEmpty()) add += unseen.removeAt(0)

            if (add.size < need) {
                val rest = (0 until totalGames)
                    .filterNot { it in pool || it in add }
                    .shuffled(rnd)
                for (i in rest) {
                    add += i
                    if (add.size == need) break
                }
            }
            pool.addAll(add)
            prefs.poolCsv = setToCsv(pool)
        } else {
            // Shrink
            val removeCount = curSize - req
            val toRemove = mutableSetOf<Int>()

            val unsolvedInPool = pool.filterNot { it in solved }.shuffled(rnd).toMutableList()
            while (toRemove.size < removeCount && unsolvedInPool.isNotEmpty()) {
                toRemove += unsolvedInPool.removeAt(0)
            }
            if (toRemove.size < removeCount) {
                val rest = (pool - toRemove).shuffled(rnd)
                for (i in rest) {
                    toRemove += i
                    if (toRemove.size == removeCount) break
                }
            }

            pool.removeAll(toRemove)
            prefs.poolCsv = setToCsv(pool)

            val newSolved = solved.filter { it in pool }.toSet()
            if (newSolved.size != solved.size) prefs.solvedCsv = setToCsv(newSolved)
        }
    }

    fun startPuzzleClock() { puzzleStartMs = SystemClock.elapsedRealtime() }

    fun cellSize(): Float = if (boardSize.width == 0) 0f else min(boardSize.width, boardSize.height) / 8f
    fun idxToFileRank(idx: Int): Pair<Int, Int> = (idx % 8) to (idx / 8)
    fun idxToUci(idx: Int, whiteAtBottom: Boolean): String {
        val base = if (whiteAtBottom) idx else 63 - idx
        val file = "abcdefgh"[base % 8]
        val rank = '1' + (base / 8)
        return "$file$rank"
    }
    fun uciSquareToIdx(sq: String, whiteAtBottom: Boolean): Int {
        val file = "abcdefgh".indexOf(sq[0])
        val rank = (sq[1] - '1').coerceIn(0,7)
        val base = rank * 8 + file
        return if (whiteAtBottom) base else 63 - base
    }
    fun idxCenter(idx: Int): Offset {
        val (file, rank) = idxToFileRank(idx)
        val sq = cellSize()
        return Offset((file + 0.5f) * sq, ((7 - rank) + 0.5f) * sq)
    }

    // User move handler for Opening Explorer (2-tap input on the board)
    fun performOpeningUserMove(fromIdx: Int, toIdx: Int) {
        // Bounds + trivial sanity
        if (fromIdx !in 0..63 || toIdx !in 0..63) return
        if (fromIdx == toIdx) return

        // --- FREE GATE: only first 8 full moves (16 plies) from the starting position ---
        if (!proUnlocked && openingSanPath.size >= 16) {
            requirePro(
                "In the free version, the Opening Explorer is limited to the first 8 moves. " +
                        "The full version unlocks the complete opening tree."
            )
            return
        }

        // Convert UI indices to UCI squares, respecting board orientation
        val fromUci = idxToUci(fromIdx, whiteBottom)  // e.g. "e2"
        val toUci   = idxToUci(toIdx,   whiteBottom)  // e.g. "e4"

        // Find a legal move on the underlying chesslib board matching from/to
        val legalMove = openingBoard.legalMoves().firstOrNull { mv ->
            val fromSq = mv.from.toString().lowercase() // "E2" -> "e2"
            val toSq   = mv.to.toString().lowercase()
            fromSq == fromUci && toSq == toUci
        } ?: run {
            // Illegal move: just ignore for now
            return
        }

        // Build SAN-ish text BEFORE mutating the board
        val uciString = moveToUci(legalMove)
        val sanText = runCatching {
            prettyFromUci(openingBoard, uciString)
        }.getOrElse {
            "${fromUci}-${toUci}"
        }

        // Apply move on the opening board
        openingBoard.doMove(legalMove)
        openingFen = openingBoard.fen

        // Update path / ply
        openingSanPath.add(sanText)
        openingPly = openingSanPath.size

        // Last-move highlight in UI coordinates
        lastFrom = fromIdx
        lastTo   = toIdx

        // Let the shared engine loop (if enabled) pick up the new FEN
        if (engineEnabled) {
            plyTick++
        }
    }

    // Move animation
    suspend fun animateUci(uci: String) {
        val s = session ?: return

        // Guard against malformed/short moves (e.g., "", "e2")
        if (uci.length < 4) {
            if (s.next()) plyTick++    // advance without anim to avoid a deadlock
            return
        }

        // Wait for board to size up
        var tries = 0
        while ((boardSize.width == 0 || boardSize.height == 0) && tries < 32) {
            delay(16); tries++
        }
        if (boardSize.width == 0 || boardSize.height == 0) {
            if (s.next()) plyTick++
            return
        }

        val from = uciSquareToIdx(uci.substring(0, 2), whiteBottom)
        val to   = uciSquareToIdx(uci.substring(2, 4), whiteBottom)

        // Remember last move for yellow highlight
        lastFrom = from
        lastTo   = to


        animFrom = from
        animTo = to
        isAnimating = true
        animT.snapTo(0f)
        animJob = coroutineContext.job


        try {
            animT.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = MOVE_ANIM_MS, easing = LinearEasing)
            )
        } catch (_: CancellationException) {
            // interrupted -> fall through and apply the move instantly
        } catch (t: Throwable) {
            // absolutely never crash from here
            status = "Animation error: ${t.javaClass.simpleName}"
        } finally {
            if (s.next()) plyTick++
            animFrom = null
            animTo = null
            isAnimating = false
            animJob = null
        }
    }

    LaunchedEffect(session, mode) {
        val s = session ?: return@LaunchedEffect
        // Only for Woodpecker: PGN-based (totalPly > 0), at the start (ply == 0)
        if (mode == TrainerMode.WOODPECKER && (s.totalPly ?: 0) > 0 && s.ply == 0) {
            // Make sure we’re not mid-timer animation; then auto-play the reference move.
            stopTurnTimer()
            s.peekNextMoveUci()?.let { animateUci(uci = it) }
            // (No need to restart any countdown here since we removed it.)
        }
    }




    fun finishIfEndOfGame(s: PgnSession, force: Boolean = false) {
        if ((s.ply >= s.totalPly || force) && currentIndex >= 0) {
            // Mark puzzle as finished so we ignore any further taps
            puzzleOver = true
            // stop the engine cleanly and show OFF
            try {
                evalJob?.cancel()
            } catch (_: Throwable) {
            }
            evalJob = null
            stopEngineNow(setState = true)


            try {
                wallStartMs = null
            } catch (_: Throwable) {
            }
            stopTurnTimer()
            val spent = SystemClock.elapsedRealtime() - puzzleStartMs


            // score & streak
            // If we closed the puzzle early via a mate override, treat it as a full score.
            val baseEarn = if (force && earnedPoints > 0) puzzleTotalPoints else earnedPoints
            var streakBonusXp = 0

            if (scoringEnabled) {
                if (!madeMistake && !timedOut) {
                    prefs.streak = prefs.streak + 1
                    prefs.maxStreak = kotlin.math.max(prefs.maxStreak, prefs.streak)
                    streakBonusXp = (2 * prefs.streak).coerceAtMost(20)
                } else {
                    prefs.streak = 0
                }
                prefs.xp += baseEarn + streakBonusXp
            }

            wp.markSolved(currentIndex, spent, baseEarn, puzzleTotalPoints)
            markSolvedForCycle(currentIndex, spent, baseEarn)

            if (!didApplaud) {
                feedback.applause(); didApplaud = true
            }

            if (celebration == "confetti" || celebration == "gold") {
                showConfetti = true
                GlobalScope.launch {
                    delay(CONFETTI_DURATION_MS.toLong())
                    showConfetti = false
                }
            }


            val pct = if (puzzleTotalPoints > 0) (baseEarn * 100 / puzzleTotalPoints) else 0
            status = if (scoringEnabled) {
                val xpMsg = if (streakBonusXp > 0) " (+$streakBonusXp XP streak)" else ""
                "🎉 $baseEarn/$puzzleTotalPoints ($pct%). Tap Next$xpMsg"
            } else {
                "📝 Review complete (no score recorded). Tap Next"
            }

            // --- Milestone posters & toasts ---
            val solvedNow = prefs.solvedCount
            val accPctNow =
                if (prefs.ptsTotal > 0) (prefs.ptsEarned * 100f / prefs.ptsTotal) else 0f
            val cycle = prefs.cycleId

            // ---- Fish badge milestone (XP-based) ----
            if (scoringEnabled) {
                val totalGain = baseEarn + streakBonusXp
                if (totalGain > 0) {
                    val oldXp = (prefs.xp - totalGain).coerceAtLeast(0)
                    val prevBadge = fishBadgeForXp(oldXp)
                    val newBadge = fishBadgeForXp(prefs.xp)

                    if (newBadge != null && newBadge != prevBadge) {
                        val headline = when (newBadge) {
                            "Blue Fish"    -> "💙 Fish badge unlocked!"
                            "Red Fish"     -> "❤️ Fish badge unlocked!"
                            "Orange Fish"  -> "🧡 Fish badge unlocked!"
                            "Black Fish"   -> "⚫ Fish badge unlocked!"
                            "Rainbow Fish" -> "🌈 Rainbow Fish unlocked!"
                            else           -> "New Fish badge unlocked!"
                        }

                        Toast.makeText(ctx, headline, Toast.LENGTH_LONG).show()

                        if (!showPoster) {
                            posterHeadline = headline
                            posterSub = "${series.title} • Cycle #$cycle  •  XP ${prefs.xp}"
                            posterFoot = "Trainer Fish"
                            showPoster = true
                        }
                    }
                }
            }
            // 90% accuracy poster only at 25-game milestones:
            // 25, 50, 75, ... (so it doesn't fire every single puzzle)
            if (accPctNow >= 90f && solvedNow >= 25 && solvedNow % 25 == 0 && !showPoster) {
                posterHeadline = "90% Accuracy!"
                posterSub = "${series.title} • Cycle #$cycle  •  $solvedNow solved"
                posterFoot = "Trainer Fish"
                showPoster = true
            }


            // Every 25 solved (25, 50, 75, ...)
            // Always show the toast, but only show the poster if
            // another poster (like the 90% one) hasn't already claimed it.
            if (solvedNow >= 25 && solvedNow % 25 == 0) {
                Toast.makeText(
                    ctx,
                    "🏆 Milestone: Solved $solvedNow puzzles!",
                    Toast.LENGTH_LONG
                ).show()
                if (!showPoster) {
                    posterHeadline = "Solved $solvedNow puzzles!"
                    posterSub =
                        "${series.title} • Cycle #$cycle  •  Accuracy ${"%.0f".format(accPctNow)}%"
                    posterFoot = "Trainer Fish"
                    showPoster = true
                }
            }

            // 10-streak poster
            if (prefs.maxStreak == 10 && !showPoster) {
                posterHeadline = "🔥 10-puzzle streak!"
                posterSub =
                    "${series.title} • Cycle #$cycle  •  Accuracy ${"%.0f".format(accPctNow)}%"
                posterFoot = "Trainer Fish"
                showPoster = true
            }

            // If no nickname yet and a milestone popped, nudge for one first
            if (showPoster && nickname.isBlank()) {
                showPoster = false
                showProfile = true
            }
        }

    }
    fun loadGameAt(idx: Int) {
        if (idx !in games.indices) return

        // New puzzle → drop any previous last-move highlight
        lastFrom = null
        lastTo = null

        val gi = games[idx]
        val ns = PgnSession(gi.game).also { it.reset(gi.startFen) }

        session = ns
        current = gi
        currentIndex = idx

        // Orient the board to the user's side (second mover from FEN)
        val userIsWhite = userPlaysWhiteFromFen(gi.startFen)
        whiteBottom = userIsWhite

        // Reset UI/scoring state
        earnedPoints = 0
        selectedSq = null
        scoringEnabled = true
        didApplaud = false
        madeMistake = false
        timedOut = false
        puzzleOver = false

        // Puzzle meta
        puzzleSteps = computePuzzleSteps(ns.totalPly)
        puzzleTotalPoints = puzzleSteps * 10
        status = "$puzzleTotalPoints pts (${puzzleSteps} moves). Get ready…"

        // Draw initial FEN
        plyTick++

        // 1) auto-play the FIRST PGN move (reference move),
        // 2) then hand the turn to the user and start timers
        GlobalScope.launch {
            stopTurnTimer()
            session?.peekNextMoveUci()?.let { firstUci ->
                animateUci(firstUci)  // uses your existing animation timing
            }
            startWallTimer()              // <— add this line
            resetTurnTimer()              // (kept as-is if you want to preserve internal logic)
            startPuzzleClock()
            status = cycleCaptionFor(prefs)

            plyTick++  // let UI recompose after the reference move
        }
    }



    fun tryGoNextPuzzle() {
        val idx = nextIndexFromPool()
        if (idx == null) showCycleComplete = true else loadGameAt(idx)
    }

    fun revealSolution() {
        if (scoringEnabled) { earnedPoints = 0; scoringEnabled = false }
        val sess = session ?: return
        status = "Review mode: solution shown; scoring disabled."
        GlobalScope.launch {
            stopTurnTimer()
            sess.peekNextMoveUci()?.let { animateUci(it) }
            if (sess.ply < sess.totalPly) sess.peekNextMoveUci()?.let { animateUci(it) }
            finishIfEndOfGame(sess)
            if (sess.ply < sess.totalPly) {
                status = "Your turn again."
                resetTurnTimer()
            }
        }
    }

    fun isPseudoLegal(uiBoard: Array<Piece?>, from: Int, to: Int, whiteAtBottom: Boolean): Boolean {
        if (from !in 0..63 || to !in 0..63 || from == to) return false
        val p = uiBoard.getOrNull(from) ?: return false
        val target = uiBoard.getOrNull(to)
        if (target != null && target.isWhite == p.isWhite) return false

        fun inside(x: Int, y: Int) = x in 0..7 && y in 0..7
        fun at(x: Int, y: Int): Piece? = if (inside(x, y)) uiBoard.getOrNull(y * 8 + x) else null

        val (fx, fy) = idxToFileRank(from)
        val (tx, ty) = idxToFileRank(to)
        val dx = tx - fx
        val dy = ty - fy
        val adx = kotlin.math.abs(dx)
        val ady = kotlin.math.abs(dy)

        fun clearLine(stepX: Int, stepY: Int): Boolean {
            if (stepX == 0 && stepY == 0) return false
            var x = fx + stepX; var y = fy + stepY
            while (x != tx || y != ty) {
                if (!inside(x, y)) return false
                if (at(x, y) != null) return false
                x += stepX; y += stepY
            }
            return inside(tx, ty)
        }

        return when (p.type) {
            PieceType.KNIGHT -> (adx == 1 && ady == 2) || (adx == 2 && ady == 1)
            PieceType.BISHOP -> (adx == ady) && clearLine(sign(dx), sign(dy))
            PieceType.ROOK   -> (adx == 0 || ady == 0) && clearLine(sign(dx), sign(dy))
            PieceType.QUEEN  -> ((adx == ady) || adx == 0 || ady == 0) && clearLine(sign(dx), sign(dy))
            PieceType.KING   -> if (adx <= 1 && ady <= 1) true else (ady == 0 && adx == 2 && clearLine(sign(dx), 0))
            PieceType.PAWN   -> {
                val bottomSide = (p.isWhite == whiteBottom)
                val dir = if (bottomSide) 1 else -1
                val startRank = if (bottomSide) 1 else 6
                if (dx == 0) {
                    if (target != null) return false
                    if (dy == dir) return inside(tx, ty)
                    if (fy == startRank && dy == 2 * dir) {
                        val mid = (fy + dir) * 8 + fx
                        uiBoard.getOrNull(mid) == null && inside(tx, ty)
                    } else false
                } else {
                    (ady == dir && adx == 1 && target != null)
                }
            }
        }
    }

    // Local helper (no visibility modifier inside a function)
     fun finalizeEndgameResult(
        b: com.github.bhlangonijr.chesslib.Board,
        lastMoverIsUser: Boolean,
        stopEngine: () -> Unit,
        setStatus: (String) -> Unit,
        setResult: (EndgameResult) -> Unit
    ) {
        when {
            b.isMated -> {
                if (lastMoverIsUser) {
                    setStatus("✅ You win! Checkmate.")
                    setResult(EndgameResult.WIN)
                } else {
                    setStatus("♟️ Fish wins by checkmate.")
                    setResult(EndgameResult.LOSS)
                }
            }
            b.isStaleMate -> { setStatus("½–½ Draw by stalemate."); setResult(EndgameResult.DRAW) }
            b.isInsufficientMaterial -> { setStatus("½–½ Draw by insufficient material."); setResult(EndgameResult.DRAW) }
            b.isDraw -> { setStatus("½–½ Draw."); setResult(EndgameResult.DRAW) }
            else -> { setStatus("Game over."); setResult(EndgameResult.DRAW) }
        }
        stopEngine()
    }

    // ======================= Endgame: handle user's move + engine reply =======================

    fun performEndgameUserMove(uciFull: String) {
        val s = session ?: return
        if (endgameOver) {
            status = "Game over — tap Exit."
            return
        }

        // Parse and validate user's move
        val mv = uciToMoveOnBoard(s.board, uciFull)
        if (mv == null) {
            status = "Move parse error."
            feedback.wrong()
            return
        }
        val legal = com.github.bhlangonijr.chesslib.move.MoveGenerator
            .generateLegalMoves(s.board).any { it == mv }
        if (!legal) {
            status = "Illegal move."
            feedback.wrong()
            return
        }

        // Pretty BEFORE mutating board (handles =Q/R/B/N automatically)
        val prettyUser = prettyFromUci(s.board, uciFull)

        // Make sure engine is completely idle while we apply the user move
        try { stopEngineNow(setState = false) } catch (_: Throwable) {}

        // Apply user's move
        if (!runCatching { s.board.doMove(mv) }.isSuccess) {
            status = "Illegal move."
            feedback.wrong()
            return
        }
        endgameMoves.add(prettyUser)
        plyTick++

        // Check if the game already finished after user's move
        if (s.board.isMated || s.board.isStaleMate || s.board.isInsufficientMaterial || s.board.isDraw) {
            endgameOver = true
            finalizeEndgameResult(
                b = s.board,
                lastMoverIsUser = true,
                stopEngine = { try { stopEngineNow(setState = true) } catch (_: Throwable) {} },
                setStatus = { msg -> status = msg },
                setResult = { r ->
                    endgameResult = r
                    engineEnabled = false
                    engineStatus = "TrainerFish: stopped"
                    showEndgameVerdict = false
                    endgameIdx.takeIf { it >= 0 }?.let { markEgPlayed(it) }
                }
            )
            return
        }

        // Now it's Fish's turn.
        status = "TrainerFish is thinking…"
        engineEnabled = true
        engineStatus = "TrainerFish: thinking…"

        scope.launch {
            // Single 5-second search to get the engine's move.
            val bm = bestMoveForFen(ctx, s.board.fen, movetimeMs = 5_000)

            if (bm == null) {
                // Treat as engine failure, but don't adjudicate the game.
                try { stopEngineNow(setState = false) } catch (_: Throwable) {}
                engineEnabled = false
                engineStatus = "Engine: error"
                status = "Engine error — you can keep playing or tap Back."
                return@launch
            }

            val emv = uciToMoveOnBoard(s.board, bm)
            val eLegal = emv != null && com.github.bhlangonijr.chesslib.move.MoveGenerator
                .generateLegalMoves(s.board).any { it == emv }

            if (!eLegal) {
                try { stopEngineNow(setState = false) } catch (_: Throwable) {}
                engineEnabled = false
                engineStatus = "TrainerFish: error"
                status = "Engine failed to find a legal move — you can keep playing or tap Back."
                return@launch
            }

            // Apply engine move
            s.board.doMove(emv)
            val prettyEngine = prettyFromUci(s.board, bm)
            endgameMoves.add(prettyEngine)
            plyTick++

            // Check result after engine move
            if (s.board.isMated || s.board.isStaleMate || s.board.isInsufficientMaterial || s.board.isDraw) {
                endgameOver = true
                finalizeEndgameResult(
                    b = s.board,
                    lastMoverIsUser = false,
                    stopEngine = { try { stopEngineNow(setState = true) } catch (_: Throwable) {} },
                    setStatus = { msg -> status = msg },
                    setResult = { r ->
                        endgameResult = r
                        engineEnabled = false
                        engineStatus = "TrainerFish: stopped"
                        showEndgameVerdict = false
                        endgameIdx.takeIf { it >= 0 }?.let { markEgPlayed(it) }
                    }
                )
            } else {
                // Engine move done; turn engine OFF again and hand the move back to the user.
                try { stopEngineNow(setState = false) } catch (_: Throwable) {}
                engineEnabled = false
                engineStatus = "Your move."
                status = "Your move."
            }
        }
    }


    fun tryUserMove(fromIdx: Int, toIdx: Int) {
        try {
            if (fromIdx !in 0..63 || toIdx !in 0..63) return
            val s = session ?: return
            if (isAnimating) return

            // Only block taps after a finished puzzle in Tactics mode.
            if (mode == TrainerMode.WOODPECKER && puzzleOver) {
                // Already finished (e.g. via mate override); ignore extra taps.
                return
            }

            val userUci = idxToUci(fromIdx, whiteBottom) + idxToUci(toIdx, whiteBottom)

            // Remember last move for yellow highlight
            lastFrom = fromIdx
            lastTo   = toIdx

            // --- Endgame Trainer: promotion-first, then legality in performEndgameUserMove ---
            if (mode == TrainerMode.ENDGAME) {
                if (!endgamePlaying) {
                    status = "Tap Play position to start."
                    feedback.wrong()
                    endgamePlayHint = true      // ⟵ start blinking
                    return
                }

                val s = session ?: return
                if (endgameOver) { status = "Game over — tap Exit."; return }
                if (pendingPromotionUci != null) { status = "Choose a piece to promote to…"; return }

                val from = idxToUci(fromIdx, whiteBottom)
                val to   = idxToUci(toIdx,   whiteBottom)
                if (from == to) { status = "Select a destination square."; feedback.wrong(); return }

                val uciBase = from + to

                // PROMOTION detection happens BEFORE any legality check
                val fromSq   = com.github.bhlangonijr.chesslib.Square.valueOf(from.uppercase())
                val piece    = s.board.getPiece(fromSq)
                val side     = s.board.sideToMove
                val lastRank = (side == com.github.bhlangonijr.chesslib.Side.WHITE && to.endsWith("8")) ||
                        (side == com.github.bhlangonijr.chesslib.Side.BLACK && to.endsWith("1"))

                if (piece?.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN && lastRank) {
                    // Ask user; legality will be checked after they choose the piece
                    pendingPromotionUci = uciBase
                    status = "Choose a piece to promote to…"
                    return
                }

                // Not a promotion → proceed (performEndgameUserMove internally checks legality)
                performEndgameUserMove(uciBase)
                endgamePlayHint = false
                endgamePlaying = true

                return
            }



            // --- Woodpecker branch (original behavior + mate-in-1 exception) ---
            if (s.ply >= s.totalPly) {
                finishIfEndOfGame(s)
                return
            }

            val expected = s.peekNextMoveUci()?.lowercase() ?: ""
            val okPrefix = expected.length >= 4 && userUci.startsWith(expected.take(4))

// NEW: special case – last move & user delivers checkmate
            val isLastMove = (s.totalPly > 0 && s.ply == s.totalPly - 1)
            var mateOverride = false

            if (!okPrefix && isLastMove) {
                // Try to interpret the user's move on the REAL chesslib board
                val mv = uciToMoveOnBoard(s.board, userUci)
                if (mv != null) {
                    val legal = com.github.bhlangonijr.chesslib.move.MoveGenerator
                        .generateLegalMoves(s.board)
                        .any { it == mv }

                    if (legal) {
                        // Work on a temporary board so we don't mutate the session state
                        val tmp = com.github.bhlangonijr.chesslib.Board()
                        tmp.loadFromFen(s.board.fen)
                        tmp.doMove(mv)

                        if (tmp.isMated) {
                            // ✔ Any mating move on the final ply is accepted,
                            // even if it doesn't match the official solution.
                            mateOverride = true
                        }
                    }
                }
            }

// Keep the "Illegal move" feedback, unless we’re in the mate override path
            if (!okPrefix && !mateOverride) {
                val orientedBoard = run {
                    val base = boardToUiPieces(s.board)
                    if (whiteBottom) base else Array(64) { i -> base[63 - i] }
                }
                if (!isPseudoLegal(orientedBoard, fromIdx, toIdx, whiteBottom)) {
                    status = "Illegal move. Try again."
                    return
                }
            }

            when {
                // 1) Normal “matches PGN” correct move
                okPrefix -> {
                    if (s.next()) {
                        if (scoringEnabled) earnedPoints += 10
                        feedback.correct()
                        stopTurnTimer()
                        scope.launch {
                            if (s.ply < s.totalPly) {
                                s.peekNextMoveUci()?.let { animateUci(it) }
                            }
                            if (s.ply >= s.totalPly) status = "✅ Correct!"
                            finishIfEndOfGame(s)
                            if (s.ply < s.totalPly) resetTurnTimer()
                        }
                        plyTick++
                    } else {
                        status = s.lastError ?: "Could not advance."
                    }
                }

                // 2) Mate override: user found a different mate-in-1 on the final ply
                mateOverride -> {
                    if (scoringEnabled) earnedPoints += 10
                    feedback.correct()
                    stopTurnTimer()

                    status = "✅ Checkmate!"
                    finishIfEndOfGame(s, force = true)
                    // No next move, so no timer restart.
                    plyTick++

                }

                // 3) Wrong move
                else -> {
                    // ❌ No more shield logic — a wrong move is simply a mistake
                    madeMistake = true
                    feedback.wrong()
                    stopTurnTimer()

                    scope.launch {
                        val pretty = if (expected.isNotEmpty()) expected else "?"
                        status = "❌ Not quite. The correct move is $pretty."

                        // Show the correct move (or step through if we only have prefix)
                        if (expected.length >= 4) {
                            animateUci(expected)
                        } else if (s.next()) {
                            plyTick++
                        }

                        // Optionally show the next move too, like before
                        if (s.ply < s.totalPly) {
                            s.peekNextMoveUci()?.let { animateUci(it) }
                        }

                        finishIfEndOfGame(s)

                        if (s.ply < s.totalPly) {
                            status = "Your turn again."
                            resetTurnTimer()
                        }
                    }
                }
            }

        } catch (t: Throwable) {
            status = "Move error: ${t.javaClass.simpleName}"
        } finally {
            selectedSq = null
            dragFrom = null
            lastDragPos = null
        }
    }

    LaunchedEffect(autoStart, series, showFirstRun, showWelcome, loading) {
        // While Tour/Welcome is up OR we’re busy loading/preparing, do absolutely nothing.
        if (showFirstRun || showWelcome || loading) return@LaunchedEffect

        // Only nudge to Welcome when the screen is truly idle.
        if (autoStart && games.isEmpty() && session == null) {
            showWelcome = true
        }
    }

    // Pause timer on background, resume on return
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // If a turn timer were running, just stop it; we don't track “remaining seconds”.
                    stopTurnTimer()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Friendly status only; no countdown restore.
                    status = "Welcome back!"
                }
                else -> Unit
            }
        }
        val lc = lifecycleOwner.lifecycle
        lc.addObserver(observer)
        onDispose { lc.removeObserver(observer) }
    }

    fun formatHms(ms: Long): String {
        val sec = (ms / 1000).toInt()
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun continueCycleNow(onDone: () -> Unit = {}) {
        // Continue whatever the active cycle is
        val pf  = cycles.prefs(cycles.activeId)
        val ids = pf.poolAbsCsv.split(',')
            .mapNotNull { it.trim().toIntOrNull() }

        // Nothing saved for this cycle
        if (ids.isEmpty()) {
            onDone()
            return
        }

        // NOTE: we no longer set `loading = true` here.
        // That means the LoadingGalleryDialog is NOT shown
        // when simply continuing an existing cycle.

        GlobalScope.launch {
            val exact = withContext(Dispatchers.IO) {
                loadGamesByIndexes(
                    context = context,
                    resId   = series.rawRes,
                    indexes = ids
                )
            }

            withContext(Dispatchers.Main) {
                if (exact.isNotEmpty()) {
                    games = exact
                    prefs = pf                // bind gameplay to this cycle
                    prefs.size = exact.size
                    ensurePoolSize(
                        totalGames    = exact.size,
                        requestedSize = exact.size
                    )
                    // Jump straight to the next puzzle
                    nextIndexFromPool()?.let { loadGameAt(it) }
                }

                // Leave `loading` alone so the gallery doesn't appear
                showWelcome = false
                onDone()
            }
        }
    }

    fun handleOpeningBack() {
        // Only if there’s at least one move in the path
        if (openingSanPath.isNotEmpty()) {

            // 1) Remove last SAN from the path
            openingSanPath.removeAt(openingSanPath.lastIndex)

            // 2) Rebuild the board from START_FEN and replay remaining moves
            openingBoard.loadFromFen(START_FEN)
            for (san in openingSanPath) {
                val mv = sanToLegalMove(openingBoard, san) ?: break
                openingBoard.doMove(mv)
            }

            // 3) Update derived state
            openingFen = openingBoard.fen
            openingPly = openingSanPath.size

            // 4) Clear last-move highlight
            lastFrom = null
            lastTo = null
        }
    }



    // ----------------- UI SECTION -----------------
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Show loading dialog (overlay) when preparing cycle

        ReplayLoadingOverlay(loading = loading)

        // Top control strip: Mute | center label | Brain + Exit (offline)
        ReplayTopBar(
            mode = mode,
            iconOnlyTopBar = iconOnlyTopBar,
            soundOn = soundOn,
            engineEnabled = engineEnabled,
            endgameResult = endgameResult,
            engineStatus = engineStatus,
            goodJobNow = goodJobNow,
            nickname = nickname,
            onToggleSound = {
                soundOn = !soundOn
                prefs.soundOn = soundOn
            },
            onToggleEngine = {
                try { evalJob?.cancel() } catch (_: Throwable) {}
                if (engineEnabled) {
                    stopEngineNow(setState = true)
                    engineEnabled = false
                    status = "TrainerFish: off"
                } else {
                    startEngineNow()
                    engineEnabled = true
                    status = "TrainerFish: on"
                }
                plyTick++ // force a quick redraw
            },
            onOpenSettings = {
                showSettings = true
                status = "Settings"
            },
            onExitRequested = {
                showExitConfirm = true
            }
        )


        // Top eval line: keep for Tactics, hide for Opening & Endgame
        if (mode == TrainerMode.WOODPECKER) {
            Text(
                text = engineStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        ReplayModeChips(
            mode = mode,
            openingLoading = openingLoading,
            onSelectTactics = {
                if (mode != TrainerMode.WOODPECKER) {
                    try { evalJob?.cancel() } catch (_: Throwable) {}
                    stopEngineNow(setState = true)
                    engineEnabled = false
                    endgamePlaying = false
                    engineStatus = "TrainerFish: OFF"
                    mode = TrainerMode.WOODPECKER

                    val hasActiveSession =
                        session != null &&
                                current != null &&
                                currentIndex >= 0

                    val restoredFromSnapshot =
                        if (!hasActiveSession) restoreTacticsFromSnapshot() else false

                    if (hasActiveSession || restoredFromSnapshot) {
                        showWelcome = false
                        status = "Trainer – continue cycle."
                    } else {
                        showWelcome = true
                        status = "Trainer – choose a cycle or continue."
                    }
                }
            },
            onSelectEndgame = {
                if (mode != TrainerMode.ENDGAME) {

                    // If we are leaving Tactics, remember where we were
                    if (mode == TrainerMode.WOODPECKER) {
                        saveTacticsSnapshot()
                    }

                    mode = TrainerMode.ENDGAME

                    // wipe any tactics (puzzle) session UI state
                    session = null
                    current = null
                    currentIndex = -1

                    // make sure the generic (tactics) eval loop is not running
                    try { evalJob?.cancel() } catch (_: Throwable) {}
                    evalJob = null
                    stopEngineNow(setState = true)
                    engineEnabled = false   // will become true on first endgame selection
                }
            },
            onSelectOpening = {
                if (mode != TrainerMode.OPENING) {

                    // Leaving Tactics? Save its state first.
                    if (mode == TrainerMode.WOODPECKER) {
                        saveTacticsSnapshot()
                    }

                    try { evalJob?.cancel() } catch (_: Throwable) {}
                    stopEngineNow(setState = true)
                    engineEnabled = false

                    mode = TrainerMode.OPENING

                    // Are we already holding THIS pack (e4 / d4 / others) in memory?
                    val alreadyLoaded =
                        BinaryOpeningBook.isLoaded &&
                                BinaryOpeningBook.currentPack == openingPack

                    openingReady   = alreadyLoaded
                    openingLoading = !alreadyLoaded

                    status = if (!alreadyLoaded)
                        "Loading opening book…"
                    else
                        "Opening explorer — tap a move below"

                    if (!alreadyLoaded) {
                        scope.launch {
                            status = "Loading opening book…"
                            try {
                                // load the correct split book (e4 / d4 / others)
                                BinaryOpeningBook.loadIfNeeded(
                                    context = context,
                                    pack    = openingPack
                                )
                                openingReady = true
                                status = "Opening explorer — tap a move below"
                            } catch (t: Throwable) {
                                status = "Failed to load opening book: ${t.message}"
                            }
                            openingReset()
                            openingLoading = false
                        }
                    } else {
                        // already in memory, just reset view
                        openingReset()
                    }
                }
            }
        )


        // ===== Endgame Trainer UI =====

        // Ensure we have the labels list when entering Endgame, and keep tactics eval off
        LaunchedEffect(mode) {
            if (mode == TrainerMode.ENDGAME) {
                if (endgameEvents.isEmpty()) {
                    endgameEvents = listEventLabels(context, R.raw.endgames)
                }
                try { evalJob?.cancel() } catch (_: Throwable) {}
                evalJob = null
                stopEngineNow(setState = true)
                endgamePlaying = false
                engineEnabled = false
                status = "Pick a position below"
            }
        }

        if (mode == TrainerMode.ENDGAME) {
            val s = session

            // Lock orientation: if we have a loaded FEN, use that side as "user",
            // otherwise default to White at the bottom on the empty board.
            LaunchedEffect(endgameUserIsWhite, s) {
                whiteBottom = if (s != null) endgameUserIsWhite else true
            }

            // Build UI board snapshot (if no session yet, show a neutral START_FEN board)
            val uiBoardEg = remember(plyTick, s, whiteBottom) {
                val board = s?.board ?: com.github.bhlangonijr.chesslib.Board().apply {
                    loadFromFen(START_FEN)
                }
                val base = boardToUiPieces(board)
                if (whiteBottom) base else Array(64) { i -> base[63 - i] }
            }
            val boardForRenderEg = remember(plyTick, uiBoardEg, dragFrom, animFrom) {
                uiBoardEg.copyOf().also {
                    dragFrom?.takeIf { it in 0..63 }?.let { idx -> it[idx] = null }
                    animFrom?.takeIf { it in 0..63 }?.let { idx -> it[idx] = null }
                }
            }

            // Promotion picker overlay (only ever used once a real session is running)
            if (pendingPromotionUci != null) {
                val stm = session?.board?.sideToMove
                    ?: com.github.bhlangonijr.chesslib.Side.WHITE
                val whiteTurn = (stm == com.github.bhlangonijr.chesslib.Side.WHITE)
                fun glyphFor(p: Char) = when (p) {
                    'q' -> if (whiteTurn) "♕" else "♛"
                    'r' -> if (whiteTurn) "♖" else "♜"
                    'b' -> if (whiteTurn) "♗" else "♝"
                    else -> if (whiteTurn) "♘" else "♞"
                }

                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { pendingPromotionUci = null },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnClickOutside = true
                    )
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Promote pawn to",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf('q', 'r', 'b', 'n').forEach { p ->
                                    Surface(
                                        shape = CircleShape,
                                        tonalElevation = 3.dp,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clickable {
                                                val base = pendingPromotionUci ?: return@clickable
                                                pendingPromotionUci = null
                                                performEndgameUserMove(base + p)
                                            }
                                    ) {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(glyphFor(p), fontSize = 28.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { pendingPromotionUci = null }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Board (tap + drag) – this now always shows, even before a position is chosen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .onSizeChanged { boardSize = it }
                    .pointerInput(plyTick, whiteBottom, isAnimating) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                if (isAnimating) interruptAnimation()
                                if (isAnimating) return@detectDragGestures
                                dragFrom = posToIndex(pos, boardSize)
                                lastDragPos = pos
                                selectedSq = dragFrom
                            },
                            onDrag = { change, _ ->
                                if (!isAnimating) lastDragPos = change.position
                            },
                            onDragEnd = {
                                if (!isAnimating) {
                                    val f = dragFrom
                                    val t = lastDragPos?.let { posToIndex(it, boardSize) }
                                    if (f != null && t != null) tryUserMove(f, t)
                                }
                                dragFrom = null; lastDragPos = null
                            },
                            onDragCancel = {
                                dragFrom = null; lastDragPos = null
                            }
                        )
                    }
            ) {
                ChessBoard(
                    board = boardForRenderEg,
                    selected = selectedSq,
                    lastMoveFrom = lastFrom,
                    lastMoveTo = lastTo,
                    onSquareClick = onSq@ { idx ->
                        if (isAnimating) {
                            interruptAnimation(); return@onSq
                        }
                        val prev = selectedSq
                        val pieceAt = boardForRenderEg.getOrNull(idx)
                        if (prev == null) {
                            if (pieceAt != null) selectedSq = idx
                        } else {
                            if (idx == prev) {
                                selectedSq = null
                            } else {
                                val prevPiece = boardForRenderEg.getOrNull(prev)
                                if (prevPiece != null &&
                                    pieceAt != null &&
                                    prevPiece.isWhite == pieceAt.isWhite
                                ) {
                                    selectedSq = idx
                                } else {
                                    tryUserMove(prev, idx)
                                    selectedSq = null
                                }
                            }
                        }
                    },
                    light = themeColors.light,
                    dark = themeColors.dark,
                    pieceStyle = pieceStyle,
                    whiteBottom = whiteBottom
                )

                // --- Drag + animation overlay for Endgame board ---
                // Drag overlay – piece follows the finger while dragging
                lastDragPos?.let { pos ->
                    val fromIdx = dragFrom?.takeIf { it in 0..63 }
                    if (fromIdx != null) {
                        drawPieceOverlay(uiBoardEg.getOrNull(fromIdx), pos, boardSize)
                    }
                }

                // Optional: slide animation overlay, same as tactics
                val af = animFrom?.takeIf { it in 0..63 }
                val at = animTo?.takeIf { it in 0..63 }
                if (af != null && at != null && boardSize.width > 0) {
                    val fromC = idxCenter(af)
                    val toC   = idxCenter(at)
                    val t     = animT.value
                    val p = Offset(
                        fromC.x + (toC.x - fromC.x) * t,
                        fromC.y + (toC.y - fromC.y) * t
                    )
                    drawPieceOverlay(uiBoardEg.getOrNull(af), p, boardSize)
                }
            }

            // USER turn indicator under the board
            Spacer(Modifier.height(6.dp))
            val timeText = wallStartMs?.let { "⏱ ${formatWallMmSs(wallElapsedSec)}" }
            TurnIndicatorUserSide(
                userIsWhite   = whiteBottom,
                nickname      = nickname,
                elapsedLabel  = timeText,
                xp            = prefs.xp
            )


            // Pretty move ribbon (one line, horizontally scrollable)
            Spacer(Modifier.height(6.dp))

// Scroll state for the move ribbon
            val movesScroll = rememberScrollState()

// Whenever a new move is added, auto-scroll to the right end
            LaunchedEffect(endgameMoves.size) {
                if (endgameMoves.isNotEmpty()) {
                    // Jump or animate; animateScrollTo looks nicer
                    movesScroll.animateScrollTo(movesScroll.maxValue)
                }
            }

            val moveLine = remember(endgameMoves.size) {
                buildString {
                    endgameMoves.forEachIndexed { i, m ->
                        if (i % 2 == 0) append("${i / 2 + 1}. ")
                        append(m).append(' ')
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(movesScroll)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    moveLine,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(Modifier.height(4.dp))

            // Filter row + quick counts
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Positions: ${endgameEvents.size} • Played: ${playedSet.size}",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hidePlayed, onCheckedChange = { hidePlayed = it })
                    Text("Hide played")
                }
            }

            Spacer(Modifier.height(4.dp))

            // --- Derived list to show (Endgame selector) ---
            val filtered = remember(endgameEvents, playedSet, hidePlayed) {
                endgameEvents
                    .mapIndexed { idx, label -> idx to label }
                    .filter { (i, _) ->
                        if (hidePlayed) !playedSet.contains(i.toString()) else true
                    }
            }

            // Navigation + Play buttons above the list (preview + play)
            val currentPos = filtered.indexOfFirst { (i, _) -> i == endgameIdx }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BACK
                PushButton(
                    text = "Back",
                    onClick = {
                        if (currentPos > 0) {
                            val (targetIdx, _) = filtered[currentPos - 1]
                            endgameMoves.clear()
                            scope.launch {
                                loadEndgameAt(targetIdx)
                                plyTick++
                            }
                        }
                    },
                    enabled = currentPos > 0,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )

                // --- Blinking PLAY button ---
                // Blink animation only when endgamePlayHint is true
                val blinkTransition = rememberInfiniteTransition(label = "endgamePlayBlink")
                val blinkPhase by blinkTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "endgamePlayBlinkPhase"
                )

                val playHighlightColor =
                    if (endgamePlayHint && blinkPhase < 0.5f)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    else
                        Color.Transparent

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(playHighlightColor, RoundedCornerShape(999.dp))
                        .padding(2.dp)
                ) {
                    PushButton(
                        text = if (endgamePlaying) "Playing…" else "Play position",
                        onClick = {
                            endgamePlayHint = false   // stop blinking once they actually press Play
                            startEndgamePlay()
                        },
                        enabled = (session != null && !endgamePlaying),
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // NEXT
                PushButton(
                    text = "Next",
                    onClick = {
                        if (currentPos >= 0 && currentPos < filtered.size - 1) {
                            val (targetIdx, _) = filtered[currentPos + 1]
                            endgameMoves.clear()
                            scope.launch {
                                loadEndgameAt(targetIdx)
                                plyTick++
                            }
                        }
                    },
                    enabled = currentPos >= 0 && currentPos < filtered.size - 1,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
            }


            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(filtered) { (i, label) ->
                    val played   = playedSet.contains(i.toString())
                    val selected = i == endgameIdx
                    val locked   = (!proUnlocked && i >= 20)   // Pro: all open, Free: 21+ locked


                    val bg =
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, RoundedCornerShape(10.dp))
                            .clickable {
                                if (locked) {
                                    // Lite paywall message
                                    status =
                                        "Endgame positions 21 and above are available in the full TrainerFish Pro version."
                                    feedback.wrong()
                                } else {
                                    endgameMoves.clear()
                                    scope.launch {
                                        loadEndgameAt(i)
                                        plyTick++
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Number
                        Text("${i + 1}.", modifier = Modifier.width(32.dp))

                        // Label
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Played marker
                        if (played) {
                            Text(
                                "✔",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Lock marker for Pro-only positions
                        if (locked) {
                            Text(
                                "🔒",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }

            // Do not render anything else in Endgame mode:
            return@Column
        }


        // ===== Opening Explorer UI =====
        if (mode == TrainerMode.OPENING) {

            // Entering OPENING → load tree, reset state and hint text.
            LaunchedEffect(mode) {
                if (mode == TrainerMode.OPENING) {
                    openingReset()
                    status = "Opening Explorer — tap a move below"

                    stopEngineNow(setState = true)
                }
            }

            // ---------- UI (your existing rendering) ----------

            /// Board snapshot (Opening)
            val uiBoardOpen = remember(openingFen, whiteBottom) {
                val base = boardToUiPieces(openingBoard)
                if (whiteBottom) base else Array(64) { i -> base[63 - i] }
            }

            // Hide the piece while dragging or animating, just like in Tactics
            val boardForRenderOpen = remember(openingFen, uiBoardOpen, dragFrom, animFrom) {
                uiBoardOpen.copyOf().also { arr ->
                    dragFrom?.takeIf { it in 0..63 }?.let { idx -> arr[idx] = null }
                    animFrom?.takeIf { it in 0..63 }?.let { idx -> arr[idx] = null }
                }
            }

            /// ECO + path header (above the board)
            // Derive ECO from current FEN using EcoClassifier (separate from the big tree)
            val ecoEntry = remember(openingFen) {
                EcoClassifier.classify(context, openingFen)
            }

            // Sticky ECO: remember the last seen ECO code (and name) so that
            // when classification disappears we still show the code.
            var stickyEcoCode by remember { mutableStateOf<String?>(null) }
            var stickyEcoName by remember { mutableStateOf<String?>(null) }

            // Whenever we *do* have an ECO entry for this FEN, update the sticky values.
            LaunchedEffect(ecoEntry) {
                if (ecoEntry != null && !ecoEntry.code.isNullOrBlank()) {
                    stickyEcoCode = ecoEntry.code
                    stickyEcoName = ecoEntry.name
                }
            }

            // Label rules:
            //  - If current position has ECO → show "CODE • Name" (or just CODE if name is blank)
            //  - Else, if we have a previous ECO code → show just that CODE
            //  - Else → show nothing
            val ecoLabel: String? = when {
                ecoEntry != null -> {
                    val code = ecoEntry.code
                    val name = ecoEntry.name
                    if (!name.isNullOrBlank()) "$code • $name" else code
                }
                !stickyEcoCode.isNullOrBlank() -> stickyEcoCode
                else -> null
            }


            val pathText = remember(openingSanPath.size) {
                buildString {
                    openingSanPath.forEachIndexed { i, san ->
                        if (i % 2 == 0) append("${i / 2 + 1}. ")
                        append(san).append(' ')
                    }
                }.trim()
            }

            // Scroll state for the opening path line
            val openingPathScroll = rememberScrollState()

            // Auto-scroll to the right whenever a new move is appended
            LaunchedEffect(openingSanPath.size) {
                if (openingSanPath.isNotEmpty()) {
                    openingPathScroll.animateScrollTo(openingPathScroll.maxValue)
                }
            }

            if (ecoLabel != null || pathText.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ecoLabel ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (pathText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .horizontalScroll(openingPathScroll)
                        ) {
                            Text(
                                text = pathText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }


            // Clamp (10 / 8 / 6)
            val clamp = if (openingPly < 20) 10 else 8

            // Current position in the binary book (may be null if not found)
            val bookPos = if (openingReady && BinaryOpeningBook.isLoaded) {
                BinaryOpeningBook.lookup(openingFen)
            } else {
                null
            }

            fun handleEnginePvClick(pvIndex: Int) {
                if (mode != TrainerMode.OPENING) return
                if (pvIndex !in enginePvLines.indices) return

                // Same free-version gate as the book moves
                if (!proUnlocked && openingSanPath.size >= 16) {
                    requirePro(
                        "In the free version, the Opening Explorer is limited to the first 8 moves. " +
                                "The full version unlocks the complete opening tree."
                    )
                    return
                }

                val raw = enginePvLines[pvIndex]
                val tokens = raw.split(" ")
                if (tokens.size < 2) return

                // First PV move in UCI, e.g. "e2e4", "e7e8q"
                val firstUci = tokens[1]
                if (firstUci.length < 4) return

                val fromAlg = firstUci.substring(0, 2)  // "e2"
                val toAlg   = firstUci.substring(2, 4)  // "e4"

                // Promotion code 0..4 (same mapping you use for the book & arrows)
                val promoCode = when (firstUci.getOrNull(4)?.lowercaseChar()) {
                    'q' -> 4
                    'r' -> 3
                    'b' -> 2
                    'n' -> 1
                    else -> 0
                }

                val targetFrom = uciSquareToIdx(fromAlg, true)
                val targetTo   = uciSquareToIdx(toAlg,   true)

                // Find matching legal move on the current opening board
                val move = openingBoard.legalMoves().find { cand ->
                    val fromIdx = uciSquareToIdx(cand.from.toString().lowercase(), true)
                    val toIdx   = uciSquareToIdx(cand.to.toString().lowercase(),   true)

                    fromIdx == targetFrom &&
                            toIdx == targetTo &&
                            promoMatches(cand, promoCode)
                } ?: return

                // Get a SAN-ish string for the top path line
                val san = runCatching {
                    prettyFromUci(openingBoard, firstUci)
                }.getOrNull() ?: firstUci

                // 1) Play the move on the board
                openingBoard.doMove(move)

                // 2) Update the PGN-ish path / FEN / ply
                openingSanPath.add(san)
                openingFen = openingBoard.fen
                openingPly = openingSanPath.size

                // 3) Update last-move highlight, respecting board orientation
                val fromSq = move.from.toString().lowercase()
                val toSq   = move.to.toString().lowercase()
                lastFrom = uciSquareToIdx(fromSq, whiteBottom)
                lastTo   = uciSquareToIdx(toSq,   whiteBottom)

                // 4) If engine is on, nudge it to re-evaluate from the new FEN
                if (engineEnabled) {
                    plyTick++
                }
            }


            // --- Board (Opening) with arrows overlay ---
            var openingSelectedSq by remember { mutableStateOf<Int?>(null) }

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .onSizeChanged { boardSize = it }
                    // Drag support (same pattern as Tactics, but using performOpeningUserMove)
                    .pointerInput(openingPly, whiteBottom, isAnimating) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                if (isAnimating) interruptAnimation()
                                if (isAnimating) return@detectDragGestures

                                dragFrom = posToIndex(pos, boardSize)
                                lastDragPos = pos
                                openingSelectedSq = dragFrom
                            },
                            onDrag = { change, _ ->
                                if (!isAnimating) lastDragPos = change.position
                            },
                            onDragEnd = {
                                if (!isAnimating) {
                                    val from = dragFrom
                                    val to = lastDragPos?.let { posToIndex(it, boardSize) }
                                    if (from != null && to != null && from != to) {
                                        performOpeningUserMove(from, to)
                                    }
                                }
                                dragFrom = null
                                lastDragPos = null
                            },
                            onDragCancel = {
                                dragFrom = null
                                lastDragPos = null
                            }
                        )
                    }
            ) {
                // 1) Draw the board itself (full 2-tap logic preserved)
                ChessBoard(
                    board = boardForRenderOpen,
                    selected = openingSelectedSq,
                    // Now we DO highlight last move using the shared lastFrom/lastTo
                    lastMoveFrom = lastFrom,
                    lastMoveTo = lastTo,
                    onSquareClick = onSq@ { idx ->
                        if (isAnimating) {
                            // Re-use the same “don’t move while animating” guard as other modes
                            interruptAnimation()
                            return@onSq
                        }

                        val prev = openingSelectedSq
                        val pieceAt = boardForRenderOpen.getOrNull(idx)

                        if (prev == null) {
                            // First tap: select a square if it has a piece
                            if (pieceAt != null) openingSelectedSq = idx
                        } else {
                            if (idx == prev) {
                                // Tap same square again → deselect
                                openingSelectedSq = null
                            } else {
                                val prevPiece = boardForRenderOpen.getOrNull(prev)

                                // Same-color piece on destination → just move selection there
                                if (prevPiece != null &&
                                    pieceAt != null &&
                                    prevPiece.isWhite == pieceAt.isWhite
                                ) {
                                    openingSelectedSq = idx
                                } else {
                                    // Different color / empty → attempt a user move
                                    performOpeningUserMove(prev, idx)
                                    openingSelectedSq = null
                                }
                            }
                        }
                    },
                    light = themeColors.light,
                    dark = themeColors.dark,
                    pieceStyle = pieceStyle,
                    whiteBottom = whiteBottom
                )

                // 2) Drag overlay (piece follows the finger)
                lastDragPos?.let { pos ->
                    val fromIdx = dragFrom?.takeIf { it in 0..63 }
                    if (fromIdx != null) {
                        drawPieceOverlay(uiBoardOpen.getOrNull(fromIdx), pos, boardSize)
                    }
                }

                // 3) Arrows overlay (only when enabled and we actually have book moves)
                if (showOpeningArrows && bookPos != null) {
                    OpeningArrowsOverlay(
                        bookPos = bookPos,
                        whiteBottom = whiteBottom,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }


            // Live FEN from your current session (adjust getter if needed)
            val currentFen = remember(session?.ply) { session?.board?.fen ?: current?.startFen ?: "" }

            LaunchedEffect(engineEnabled, endgameOver, currentIndex, plyTick, mode, multiPv) {
                if (!engineEnabled) return@LaunchedEffect

                // Only run the engine in trainer modes
                if (
                    mode != TrainerMode.WOODPECKER &&
                    mode != TrainerMode.OPENING
                ) return@LaunchedEffect

                // Which position to evaluate?
                val fen = when (mode) {
                    TrainerMode.OPENING ->
                        openingBoard.fen
                    else ->
                        session?.board?.fen ?: current?.startFen.orEmpty()
                }

                if (fen.isBlank()) return@LaunchedEffect

                // Configure MultiPV: in Opening we honor the UI setting; elsewhere force 1
                runCatching {
                    if (mode == TrainerMode.OPENING) {
                        com.tonorbe.trainerfish.engine.ProcEngine.setMultiPv(multiPv)
                    } else {
                        com.tonorbe.trainerfish.engine.ProcEngine.setMultiPv(1)
                    }
                }

                // Temporary text until info lines start coming in
                engineStatus = "TrainerFish: running"

                // Separate search settings for Tactics vs Opening:

                // FrankenFish is strong; short think time is enough here.
                val movetimeMs = 3_600_000

                val lines = multiPv.coerceIn(1, 4)

                runCatching {
                    // Tell Stockfish how many best lines to calculate
                    com.tonorbe.trainerfish.engine.ProcEngine.send(
                        "setoption name MultiPV value $lines"
                    )
                    com.tonorbe.trainerfish.engine.ProcEngine.evaluateFen(
                        fen = fen,
                        movetimeMs = movetimeMs
                    )
                }
            }



            Spacer(Modifier.height(8.dp))


            // If not found fallback message
            if (bookPos == null) {
                Text(
                    "No book moves available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )

            }


            // Convert BookMove → display
            data class Disp(val san: String, val count: Int, val move: BookMove)

            val dispMoves = remember(bookPos, openingFen) {
                val bp = bookPos ?: return@remember emptyList<Disp>()

                val moves = openingBoard.legalMoves().mapNotNull { mv ->
                    // Convert chesslib Squares → 0..63 indices (always white-at-bottom)
                    val fromIdx = uciSquareToIdx(mv.from.toString().lowercase(), true)
                    val toIdx   = uciSquareToIdx(mv.to.toString().lowercase(),   true)

                    // Promotion code 0..4 (same mapping as Python + BinaryOpeningBook)
                    val promo = when (mv.promotion) {
                        com.github.bhlangonijr.chesslib.Piece.WHITE_QUEEN,
                        com.github.bhlangonijr.chesslib.Piece.BLACK_QUEEN -> 4
                        com.github.bhlangonijr.chesslib.Piece.WHITE_ROOK,
                        com.github.bhlangonijr.chesslib.Piece.BLACK_ROOK  -> 3
                        com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP,
                        com.github.bhlangonijr.chesslib.Piece.BLACK_BISHOP -> 2
                        com.github.bhlangonijr.chesslib.Piece.WHITE_KNIGHT,
                        com.github.bhlangonijr.chesslib.Piece.BLACK_KNIGHT -> 1
                        else -> 0
                    }

                    // Find matching book entry
                    val bookMatch = bp.moves.find {
                        it.from == fromIdx && it.to == toIdx && it.promo == promo
                    } ?: return@mapNotNull null

                    // Use our existing pretty SAN-ish printer from UCI
                    val sanText = runCatching {
                        val uci = moveToUci(mv)
                        prettyFromUci(openingBoard, uci)
                    }.getOrElse {
                        "${mv.from}-${mv.to}"
                    }

                    Disp(
                        san = sanText,
                        count = bookMatch.count,
                        move = bookMatch
                    )
                }

                val clamp = when {
                    openingPly < 10 -> 10
                    openingPly < 20 -> 8
                    else            -> 6
                }

                moves.sortedByDescending { it.count }.take(clamp)
            }

            val maxCount = dispMoves.maxOfOrNull { it.count }?.takeIf { it > 0 } ?: 1

            val hasBookMoves = dispMoves.isNotEmpty()


            val prettyOpeningPv: List<OpeningPvLine> = remember(enginePvLines, openingFen) {
                if (enginePvLines.isEmpty()) return@remember emptyList()

                enginePvLines.mapNotNull { raw ->
                    val tokens = raw.split(" ")
                    if (tokens.size < 2) return@mapNotNull null

                    val eval = tokens[0]
                    val uciMoves = tokens.drop(1)

                    // We approximate the move number from how many moves we already played
                    var ply = openingSanPath.size // 0-based plies from start
                    val prettyParts = mutableListOf<String>()

                    val maxPlies = 8  // 🔴 clamp: show at most 8 plies per line

                    for (uci in uciMoves) {
                        val pretty = runCatching { prettyFromUci(openingBoard, uci) }.getOrNull()
                        if (pretty != null) {
                            val moveNumber = ply / 2 + 1
                            val whiteToMove = (ply % 2 == 0)

                            if (whiteToMove) {
                                // "15. Nf3"
                                prettyParts.add("$moveNumber. $pretty")
                            } else {
                                // "...Nf3"
                                prettyParts.add("$pretty")
                            }
                            ply++

                            // 🔒 stop after maxPlies plies
                            if (prettyParts.size >= maxPlies) break
                        }
                    }

                    OpeningPvLine(
                        eval = eval,
                        moves = prettyParts.joinToString(" ")
                    )
                }
            }


            // Moves list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // LEFT: Moves list – only if we actually have book moves
                if (hasBookMoves) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(dispMoves) { m ->
                            val frac = m.count.toFloat() / maxCount.toFloat()

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                tonalElevation = 2.dp
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // --- FREE GATE: Only first 8 moves in Opening Explorer ---
                                            if (!proUnlocked && openingSanPath.size >= 16) {
                                                requirePro(
                                                    "In the free version, the Opening Explorer " +
                                                            "is limited to the first 8 moves. " +
                                                            "The full version unlocks the complete opening tree."
                                                )
                                                return@clickable
                                            }

                                            // Apply the actual move by matching indices and promo
                                            val move = openingBoard.legalMoves().find { cand ->
                                                val fromIdx = uciSquareToIdx(
                                                    cand.from.toString().lowercase(),
                                                    true
                                                )
                                                val toIdx = uciSquareToIdx(
                                                    cand.to.toString().lowercase(),
                                                    true
                                                )

                                                fromIdx == m.move.from &&
                                                        toIdx == m.move.to &&
                                                        promoMatches(cand, m.move.promo)
                                            }

                                            if (move != null) {
                                                // 1) Play the move on the opening board
                                                openingBoard.doMove(move)

                                                // 2) Update PGN-ish path / FEN / ply
                                                openingSanPath.add(m.san)
                                                openingFen = openingBoard.fen
                                                openingPly = openingSanPath.size

                                                // 3) Update last-move highlight
                                                val fromSq = move.from.toString().lowercase()
                                                val toSq = move.to.toString().lowercase()
                                                lastFrom = uciSquareToIdx(fromSq, whiteBottom)
                                                lastTo = uciSquareToIdx(toSq, whiteBottom)
                                            }

                                            if (engineEnabled) {
                                                // Let the engine loop pick up the new FEN
                                                plyTick++
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            m.san,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            "${m.count}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { frac.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // RIGHT: Multi-PV eval panel (only in Opening)
                if (mode == TrainerMode.OPENING) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // --- Lines/Fish row ---
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play/stop fish
                            IconButton(
                                onClick = {
                                    if (engineEnabled) {
                                        // STOP ENGINE
                                        stopEngineNow(setState = true)
                                    } else {
                                        // START ENGINE
                                        enginePvLines = emptyList()   // clear old lines in the panel
                                        startEngineNow()              // make sure ProcEngine is running
                                        engineEnabled = true          // let the eval loop know we’re ON
                                        plyTick++                     // nudge the loop to (re)evaluate
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_fish),
                                    contentDescription = null,
                                    tint = if (engineEnabled) Color.Red else Color.Blue,
                                    modifier = Modifier.size(50.dp)
                                )
                            }


                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { multiPv = (multiPv - 1).coerceAtLeast(1) }) {
                                    Text("−", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { multiPv = (multiPv + 1).coerceAtMost(4) }) {
                                    Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Thin divider closer to the controls
                        Divider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // --- Scrollable PV list ---
                        if (prettyOpeningPv.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                prettyOpeningPv.forEachIndexed { index, pv ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { handleEnginePvClick(index) }   // 🟢 play PV move
                                            .padding(bottom = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Red eval
                                        Text(
                                            text = pv.eval,
                                            color = Color.Red,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        // Moves
                                        Text(
                                            text = "• ${pv.moves}",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 17.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }




                // Back / Reset / Flip row
            ReplayOpeningBottomBar(
                showOpeningArrows = showOpeningArrows,
                onBack = { handleOpeningBack() },
                onReset = {
                    openingReset()
                    stopEngineNow(setState = true)   // also stops engine & clears PV
                },
                onFlip = { whiteBottom = !whiteBottom },
                onToggleArrows = { showOpeningArrows = !showOpeningArrows }
            )



            // Don’t render the Tactics UI when in Opening mode.
            return@Column
        }

        val s = session
        val info = current


        // ------- Puzzle ID badge (Event tag) -------
        val currentPuzzleEvent = games.getOrNull(currentIndex)?.event.orEmpty()
        val puzzleLabel = remember(currentPuzzleEvent) {
            // Extract "Puzzle #12345" if present; otherwise use raw event
            Regex("""Puzzle #\d+""").find(currentPuzzleEvent)?.value
                ?: currentPuzzleEvent
        }
        val currentPuzzleRating = games.getOrNull(currentIndex)?.rating


        if (s != null && info != null) {

            // --- Auto-play the first move (opponent's last move) once per puzzle ---
            var firstMoveAppliedAtIndex by remember { mutableStateOf(-1) }
            LaunchedEffect(s, currentIndex) {
                val sess = s
                if (sess != null && currentIndex >= 0 && currentIndex != firstMoveAppliedAtIndex) {
                    // Ensure we are at the starting ply (0), then play first ply if available
                    // prev() returns false at ply 0, so this is safe.
                    while (sess.prev()) { /* rewind to start */ }
                    if (sess.totalPly > 0) {
                        sess.next() // play the first PGN move automatically
                    }
                    firstMoveAppliedAtIndex = currentIndex
                }
            }


            val uiBoard = remember(plyTick, s.board, whiteBottom) {
                val base = boardToUiPieces(s.board)
                if (whiteBottom) base else Array(64) { i -> base[63 - i] }
            }
            val boardForRender = remember(plyTick, uiBoard, dragFrom, animFrom) {
                uiBoard.copyOf().also {
                    dragFrom?.takeIf { it in 0..63 }?.let { idx -> it[idx] = null }
                    animFrom?.takeIf { it in 0..63 }?.let { idx -> it[idx] = null }
                }
            }

            val boardComposable: @Composable BoxScope.() -> Unit = {
                ChessBoard(
                    board = boardForRender,
                    selected = selectedSq,
                    lastMoveFrom = lastFrom,
                    lastMoveTo = lastTo,
                    onSquareClick = onSq@ { idx ->
                        if (isAnimating) { interruptAnimation(); return@onSq }
                        val prev = selectedSq
                        val pieceAt = boardForRender.getOrNull(idx)
                        if (prev == null) {
                            if (pieceAt != null) selectedSq = idx
                        } else {
                            if (idx == prev) {
                                selectedSq = null
                            } else {
                                val prevPiece = boardForRender.getOrNull(prev)
                                if (prevPiece != null && pieceAt != null && prevPiece.isWhite == pieceAt.isWhite) {
                                    selectedSq = idx
                                } else {
                                    tryUserMove(prev, idx)
                                    selectedSq = null
                                }
                            }
                        }
                    },
                    light = themeColors.light,
                    dark = themeColors.dark,
                    pieceStyle = pieceStyle,
                    whiteBottom = whiteBottom
                )

                // Drag overlay
                lastDragPos?.let { pos ->
                    val fromIdx = dragFrom?.takeIf { it in 0..63 }
                    if (fromIdx != null) drawPieceOverlay(uiBoard.getOrNull(fromIdx), pos, boardSize)
                }
                // Animation overlay
                val af = animFrom?.takeIf { it in 0..63 }
                val at = animTo?.takeIf { it in 0..63 }
                if (af != null && at != null && boardSize.width > 0) {
                    val fromC = idxCenter(af); val toC = idxCenter(at)
                    val t = animT.value
                    val p = Offset(fromC.x + (toC.x - fromC.x) * t, fromC.y + (toC.y - fromC.y) * t)
                    drawPieceOverlay(uiBoard.getOrNull(af), p, boardSize)
                }

                // Confetti (falling)
                if (showConfetti) {
                    val sq = min(boardSize.width, boardSize.height) / 8f
                    val sizeDp = with(LocalDensity.current) { (sq * 0.9f).toDp() }

                    data class Particle(
                        val x0: Float, val y0: Float, val emoji: String,
                        val driftAmp: Float, val phase: Float, val fallMul: Float
                    )
                    val particles = remember(showConfetti, boardSize) {
                        val r = kotlin.random.Random(System.currentTimeMillis())
                        val emojis = if (celebration == "gold") {
                            // Frank Marshall shower of coins ✨
                            listOf("🪙", "🪙", "💰", "⭐")
                        } else {
                            listOf("🎉", "🎊", "✨", "⭐")
                        }

                        List(CONFETTI_COUNT) {
                            Particle(
                                x0 = r.nextFloat() * 7.8f,
                                y0 = r.nextFloat() * 7.8f,
                                emoji = emojis[r.nextInt(emojis.size)],
                                driftAmp = 0.2f + r.nextFloat() * CONFETTI_MAX_DRIFT,
                                phase = r.nextFloat() * (2f * kotlin.math.PI.toFloat() ),
                                fallMul = 0.9f + r.nextFloat() * 0.6f
                            )
                        }
                    }
                    val progress = remember { Animatable(0f) }
                    LaunchedEffect(showConfetti, boardSize) {
                        progress.snapTo(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = CONFETTI_DURATION_MS, easing = LinearEasing)
                        )
                    }
                    val t = progress.value
                    for (p in particles) {
                        val xSq = p.x0 + kotlin.math.sin(p.phase + t * 6f) * p.driftAmp
                        val ySq = p.y0 + CONFETTI_FALL_SQUARES * p.fallMul * t
                        val xPx = (xSq.coerceIn(0f, 7.8f) * sq).roundToInt()
                        val yPx = ((7f - ySq.coerceAtMost(7.8f)) * sq).roundToInt()
                        val fs = (18f + 6f * kotlin.math.abs(kotlin.math.sin(p.phase + t * 10f))).sp
                        Box(
                            Modifier
                                .offset { IntOffset(xPx, yPx) }
                                .size(sizeDp),
                            contentAlignment = Alignment.Center
                        ) { Text(p.emoji, fontSize = fs) }
                    }
                }
            }

            // Dynamic labels for tight screens

            val labelSolution = if (tinyButtons) "Sol’n" else "Solution"
            val labelNext = "Next"

            val isEndgame = (session?.totalPly == 0)
            if (isEndgame) {
                // --- Endgame Trainer: minimal shell ---
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Top-right Exit (back to ReplayScreen welcome)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            endgameMoves.clear()
                            showWelcome = true  // stay in ReplayScreen; just leave Endgame mode
                        }) { Text("🚪 Exit") }
                    }

                    // Board (tap + drag identical to your main board box)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .onSizeChanged { boardSize = it }
                            .pointerInput(plyTick, whiteBottom, isAnimating) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        if (isAnimating) interruptAnimation()
                                        if (isAnimating) return@detectDragGestures
                                        dragFrom = posToIndex(pos, boardSize)
                                        lastDragPos = pos
                                        selectedSq = dragFrom
                                    },
                                    onDrag = { change, _ ->
                                        if (!isAnimating) lastDragPos = change.position
                                    },
                                    onDragEnd = {
                                        if (!isAnimating) {
                                            val from = dragFrom
                                            val to = lastDragPos?.let { posToIndex(it, boardSize) }
                                            if (from != null && to != null) tryUserMove(from, to)
                                        }
                                        dragFrom = null; lastDragPos = null
                                    },
                                    onDragCancel = { dragFrom = null; lastDragPos = null }
                                )
                            }
                    ) { boardComposable() }

                    Spacer(Modifier.height(8.dp))

                    // One-line, horizontally scrollable move list (UCI for now)
                    val scroll = rememberScrollState()
                    val moveLine = remember(endgameMoves.size) {
                        buildString {
                            endgameMoves.forEachIndexed { i, u ->
                                if (i % 2 == 0) append("${i / 2 + 1}. ")
                                append(u).append(' ')
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scroll)
                            .padding(vertical = 4.dp)
                    ) {
                        Text(moveLine, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else if (isLandscape) {

                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // LEFT: board + indicator stacked, width-bound indicator
                    Column(Modifier.align(Alignment.CenterVertically)) {
                        // >>> Badge above the board (landscape)
                        if (puzzleLabel.isNotBlank()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                // line 1: Puzzle label (left) + Rating (right)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = puzzleLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    currentPuzzleRating?.let { r ->
                                        Text(
                                            text = "Rating: $r",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                // line 2: Cycle info (restored)
                                val poolSize = currentPool().size
                                val solved = prefs.solvedCount
                                Text(
                                    text = "${series.title} • Cycle #${prefs.cycleId} • $solved/$poolSize solved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // BOARD + EVAL BAR (bar height = board height)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .onSizeChanged { boardSize = it }
                                    .pointerInput(plyTick, whiteBottom, isAnimating) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                if (isAnimating) interruptAnimation()
                                                if (isAnimating) return@detectDragGestures
                                                dragFrom = posToIndex(pos, boardSize)
                                                lastDragPos = pos
                                                selectedSq = dragFrom
                                            },
                                            onDrag = { change, _ ->
                                                if (!isAnimating) lastDragPos = change.position
                                            },
                                            onDragEnd = {
                                                if (!isAnimating) {
                                                    val from = dragFrom
                                                    val to = lastDragPos?.let {
                                                        posToIndex(
                                                            it,
                                                            boardSize
                                                        )
                                                    }
                                                    if (from != null && to != null && from != to) tryUserMove(
                                                        from,
                                                        to
                                                    )
                                                }
                                                dragFrom = null; lastDragPos = null
                                            },
                                            onDragCancel = { dragFrom = null; lastDragPos = null }
                                        )
                                    }
                            ) { boardComposable() }

                        }

                        Spacer(Modifier.height(4.dp))
                        val boardDpWidth = with(LocalDensity.current) { boardSize.width.toDp() }
                        Box(Modifier.width(boardDpWidth)) {
                            val userIsWhite = userPlaysWhiteFromFen(info.startFen)
                            // 'nickname' is already defined in ReplayScreen as a state tied to ProfilePrefs
                            val timeText = wallStartMs?.let { "⏱ ${formatWallMmSs(wallElapsedSec)}" }
                            TurnIndicatorUserSide(
                                userIsWhite   = whiteBottom,
                                nickname      = nickname,
                                elapsedLabel  = timeText,
                                xp            = prefs.xp
                            )


                        }
                    }

                    // RIGHT: controls + stats
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {

                            PushButton(
                                text = labelSolution,
                                onClick = { revealSolution() },
                                enabled = (session?.totalPly ?: 0) > 0 && !isAnimating,
                                compact = true
                            )
                            PushButton(
                                text = labelNext,
                                onClick = { tryGoNextPuzzle() },
                                enabled = games.isNotEmpty() && !isAnimating,
                                compact = true
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // --- Cycle-wide stats (precompute) ---
                        val poolSize = currentPool().size
                        val solved = prefs.solvedCount
                        val avgMs = if (solved > 0) prefs.elapsedMs / solved else 0L
                        val accPct = if (prefs.ptsTotal > 0) (prefs.ptsEarned * 100f / prefs.ptsTotal) else 0f

                        // --- Per-puzzle stats FIRST ---
                        Text("Stats for this puzzle", fontWeight = FontWeight.SemiBold)
                        val bestMs = if (currentIndex >= 0) wp.puzzleBestMs(currentIndex) else 0L
                        val (bestPts, bestTot) = if (currentIndex >= 0) wp.puzzleBestPoints(currentIndex) else (0 to 0)
                        Text("Best time: ${if (bestMs > 0) formatHms(bestMs) else "—"}")
                        Text("Best score: ${if (bestTot > 0) "$bestPts/$bestTot (${bestPts * 100 / bestTot}%)" else "—"}")

                        Spacer(Modifier.height(6.dp))

                        // --- Tier line: “You’re ready for Masterclass!” ---
                        Text(
                            tierTextFor(series, accPct),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )

                        // --- Cycle summary UNDER the tier line ---
                        Text("${series.title} • Cycle #${prefs.cycleId} • ${solved}/${poolSize} solved")
                        Text("Accuracy: ${String.format("%.0f", accPct)}%")
                        Text("Average time: ${formatHms(avgMs)}")

                        Spacer(Modifier.height(8.dp))

                        // --- Streak stays at the bottom ---
                        Text("Streak: ${prefs.streak} (max ${prefs.maxStreak})  •  XP: ${prefs.xp}")
                        LinearProgressIndicator(
                            progress = { (kotlin.math.min(prefs.streak, 10)) / 10f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                }
            } else {

                // PORTRAIT
                // >>> Badge above the board (portrait)
                if (puzzleLabel.isNotBlank()) {
                    PuzzleHeaderRow(
                        label = puzzleLabel,
                        rating = currentPuzzleRating
                    )
                }

                // Board + Eval bar (bar height = board height)


                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // BOARD
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .onSizeChanged { boardSize = it }
                            .pointerInput(plyTick, whiteBottom, isAnimating) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        if (isAnimating) interruptAnimation()
                                        if (isAnimating) return@detectDragGestures
                                        dragFrom = posToIndex(pos, boardSize)
                                        lastDragPos = pos
                                        selectedSq = dragFrom
                                    },
                                    onDrag = { change, _ ->
                                        if (!isAnimating) lastDragPos = change.position
                                    },
                                    onDragEnd = {
                                        if (!isAnimating) {
                                            val from = dragFrom
                                            val to = lastDragPos?.let { posToIndex(it, boardSize) }
                                            if (from != null && to != null && from != to) tryUserMove(
                                                from,
                                                to
                                            )
                                        }
                                        dragFrom = null; lastDragPos = null
                                    },
                                    onDragCancel = { dragFrom = null; lastDragPos = null }
                                )
                            }
                    ) { boardComposable() }

                }

                Spacer(Modifier.height(4.dp))
                val timeText = wallStartMs?.let { "⏱ ${formatWallMmSs(wallElapsedSec)}" }
                TurnIndicatorUserSide(
                    userIsWhite   = whiteBottom,
                    nickname      = nickname,
                    elapsedLabel  = timeText,
                    xp            = prefs.xp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    PushButton(text = labelSolution, onClick = { revealSolution() }, enabled = (session?.totalPly ?: 0) > 0 && !isAnimating, compact = true)
                    PushButton(text = labelNext, onClick = { tryGoNextPuzzle() }, enabled = games.isNotEmpty() && !isAnimating, compact = true)
                }

                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(status, style = MaterialTheme.typography.bodyLarge)

                    Spacer(Modifier.height(6.dp))

                    // --- Cycle-wide stats (precompute) ---
                    val poolSize = currentPool().size
                    val solved = prefs.solvedCount
                    val avgMs = if (solved > 0) prefs.elapsedMs / solved else 0L
                    val accPct = if (prefs.ptsTotal > 0) (prefs.ptsEarned * 100f / prefs.ptsTotal) else 0f

                    // --- Per-puzzle stats first ---
                    Text("Stats for this puzzle", fontWeight = FontWeight.SemiBold)
                    val bestMs = if (currentIndex >= 0) wp.puzzleBestMs(currentIndex) else 0L
                    val (bestPts, bestTot) = if (currentIndex >= 0) wp.puzzleBestPoints(currentIndex) else (0 to 0)
                    Text("Best time: ${if (bestMs > 0) formatHms(bestMs) else "—"}")
                    Text("Best score: ${if (bestTot > 0) "$bestPts/$bestTot (${bestPts * 100 / bestTot}%)" else "—"}")

                    // --- Tier line ---
                    Text(
                        tierTextFor(series, accPct),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )

                    // --- Cycle-level summary ---
                    Text("${series.title} • Cycle #${prefs.cycleId} • ${solved}/${poolSize} solved")
                    Text("Accuracy: ${String.format("%.0f", accPct)}%")
                        Text("Average time: ${formatHms(avgMs)}")


                    Spacer(Modifier.height(6.dp))
                    Text("Streak: ${prefs.streak} (max ${prefs.maxStreak})  •  XP: ${prefs.xp}")
                    LinearProgressIndicator(
                        progress = { (kotlin.math.min(prefs.streak, 10)) / 10f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }

            }
        }
    }

// Render the cover FIRST so the dialogs (declared next) appear above it.
    val gateActive = showFirstRun || showWelcome
    if (gateActive) {
        FullscreenBlackout()
    }

// FIRST-RUN horizontal tour (full-screen dialog)
    if (showFirstRun) {
        Dialog(
            onDismissRequest = { /* block back/outside during tour */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(Modifier
                .fillMaxSize()
                .background(Color.Black)) {
                WelcomeTour(
                    modifier = Modifier.fillMaxSize(),
                    onFinish = {
                        FirstRunPrefs(context).seenWelcome = true
                        showFirstRun = false
                        showWelcome  = true      // → hand off to the big Welcome
                    }
                )
            }
        }
    }

    LaunchedEffect(gateActive) {
        if (gateActive) {
            // Give Compose a frame to lay out; if somehow no dialog is visible, force Welcome.
            yield()
            if (!showFirstRun && !showWelcome) {
                showWelcome = true
            }
        }
    }


// --------------- Welcome ---------------
    LaunchedEffect(Unit) {
        val dir = appCtx.applicationInfo.nativeLibraryDir
    }

    if (showWelcome) {
        var welcomeRefresh by remember { mutableStateOf(0) }
        var pick by remember(showWelcome, series) { mutableStateOf(series) }

        // Reset confirmation wiring (keep if you use it below)
        var showResetConfirm by remember { mutableStateOf(false) }
        var nextPick by remember { mutableStateOf(series) }
        data class NextParams(val size: Int, val theme: String, val lo: Int?, val hi: Int?)
        var nextParams by remember { mutableStateOf<NextParams?>(null) }

        val context = LocalContext.current
        val scroll = rememberScrollState()
        val cfg = LocalConfiguration.current
        val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
        val dense = isLandscape
        val maxH = (cfg.screenHeightDp * if (dense) 0.92f else 0.82f).dp
        val btnPad = if (dense)
            PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        else
            ButtonDefaults.TextButtonContentPadding

        var preparing by remember { mutableStateOf(false) }

        // --- at the top of the Welcome block (once) ---
        var showStatsConfirm by remember { mutableStateOf(false) }


        // --- Bounds ---
        val minStart = 1800
        val absoluteMax = 3210
        val freeCap = if (proUnlocked) absoluteMax else 2000


        // --- Inputs ---
        var minText by rememberSaveable(showWelcome) { mutableStateOf("$minStart") }
        var maxText by rememberSaveable(showWelcome) { mutableStateOf("$freeCap") }
        val minPreview = minText.toIntOrNull()?.coerceIn(minStart, absoluteMax) ?: minStart
        val maxPreview = maxText.toIntOrNull()?.coerceIn(minStart, freeCap) ?: freeCap

        val cap = CYCLE_MAX
        var slider by remember(cap, pick) { mutableStateOf(25) }

        var themeChoice by rememberSaveable(pick) { mutableStateOf("All") }
        var themeOptions by remember(pick) { mutableStateOf(listOf("All")) }
        var themeMenuOpen by remember { mutableStateOf(false) }

        // --- Unified cycles (merge both series) ---
        data class CycleRef(val series: Series, val id: Int)

        fun listAllCycles(): List<CycleRef> {
            fun collect(series: Series): List<CycleRef> {
                val bank = CycleBank(context, series.id)
                return bank.list().mapNotNull { cid ->
                    val pf = bank.prefs(cid)
                    if (pf.poolAbsCsv.isNotBlank()) CycleRef(series, cid) else null
                }
            }
            return collect(Series.CHALLENGER) + collect(Series.MASTER)
        }

        var allCycles by remember(showWelcome, welcomeRefresh) { mutableStateOf(listAllCycles()) }

        // -1 means “none selected yet”
        var selectedIndex by rememberSaveable(showWelcome, welcomeRefresh) { mutableStateOf(-1) }

        LaunchedEffect(allCycles, series, activeCycleId) {
            selectedIndex =
                allCycles.indexOfFirst { it.series == series && it.id == activeCycleId }
                    .takeIf { it >= 0 }
                    ?: if (allCycles.isNotEmpty()) 0 else -1
        }

        val selectedRef   = allCycles.getOrNull(selectedIndex)
        val selectedPrefs = selectedRef?.let { CycleBank(context, it.series.id).prefs(it.id) }
        val viewingPrefs = remember(selectedRef, welcomeRefresh) {
            selectedRef?.let { CycleBank(context, it.series.id).prefs(it.id) }
        }
        val selectedPoolNonEmpty = selectedPrefs?.poolAbsCsv?.isNotBlank() == true
        val currentPoolNonEmpty  = if (activeCycleId > 0)
            CycleBank(context, series.id).prefs(activeCycleId).poolAbsCsv.isNotBlank()
        else false

        // Keep status in sync with the active cycle
        LaunchedEffect(series, activeCycleId) {
            status = cycleCaptionFor(prefs)
        }


        val canContinue = selectedPoolNonEmpty || currentPoolNonEmpty



        // Default slider to viewing cycle size if available
        LaunchedEffect(viewingPrefs) {
            viewingPrefs?.let { pf ->
                slider = pf.size.takeIf { it in 25..cap } ?: slider
            }
        }

        // Populate themes (cached lists)
        LaunchedEffect(pick) {
            val quick = withContext(Dispatchers.IO) { listThemesInRawQuick(context, pick.rawRes) }
            themeOptions = listOf("All") + quick.distinct()
            val full = withContext(Dispatchers.IO) { listThemesCached(context, pick.rawRes) }
            themeOptions = listOf("All") + full.distinct()
        }

        // Auto-derive series for NEW cycles only
        LaunchedEffect(maxPreview) {
            pick = if (maxPreview <= 2099) Series.CHALLENGER else Series.MASTER
        }

        // ===== Manual, on-demand counter (no background loops) =====
        val counterScope = rememberCoroutineScope()
        var counterJob by remember { mutableStateOf<Job?>(null) }
        var showCounter by rememberSaveable { mutableStateOf(false) }  // only after a param edit
        var counting by remember { mutableStateOf(false) }
        var matchCount by remember { mutableStateOf<Int?>(null) }

        // a) Fixed to the big training PGN
        val pgnPoolCount = remember(Unit) {
            countGamesInResource(context, R.raw.train_all)
        }



        // tiny index caches (used only when you press "Check matches")
        var buckets by remember { mutableStateOf<List<RatingBucket>>(emptyList()) }
        var themesMap by remember { mutableStateOf<Map<String, IntArray>>(emptyMap()) }

        suspend fun ensureIndexes() {
            if (buckets.isEmpty()) {
                buckets = withContext(Dispatchers.IO) {
                    runCatching { loadRatingBuckets(context, R.raw.train_all_buckets) }.getOrElse { emptyList() }
                }
            }
            if (themesMap.isEmpty()) {
                themesMap = withContext(Dispatchers.IO) {
                    runCatching { loadThemeIndex(context, R.raw.train_all_themes) }.getOrElse { emptyMap() }
                }
            }
        }

        fun countMatchesQuick(theme: String, lo: Int?, hi: Int?): Int {
            if (buckets.isEmpty()) return 0
            fun floor25(r: Int) = (r / 25) * 25
            val bMin = lo?.let(::floor25)
            val bMax = hi?.let(::floor25)

            val ranges = buckets.filter { (bMin == null || it.floor >= bMin) && (bMax == null || it.floor <= bMax) }
                .map { it.startIdx..it.endIdx }
            if (ranges.isEmpty()) return 0
            if (theme.equals("all", true)) return ranges.sumOf { it.last - it.first + 1 }.coerceAtLeast(0)

            val ids = themesMap[theme.trim().lowercase()] ?: return 0
            val arr = ids.sortedArray()
            var i = 0; var j = 0; var c = 0
            while (i < arr.size && j < ranges.size) {
                val id = arr[i]; val r = ranges[j]
                when {
                    id < r.first -> i++
                    id > r.last  -> j++
                    else         -> { c++; i++ }
                }
            }
            return c
        }

        fun triggerCount(theme: String, lo: Int?, hi: Int?) {
            // cancel any in-flight job
            counterJob?.cancel()
            counting = true
            matchCount = null

            // launch a background job
            counterJob = counterScope.launch(Dispatchers.Default) {
                // hard cap total time so it never “hangs”
                val ok = withTimeoutOrNull(400) {
                    ensureIndexes()       // suspend; does IO with withContext(Dispatchers.IO)
                    true
                } ?: false

                val result = if (ok) {
                    // pure CPU in Default
                    countMatchesQuick(theme, lo, hi)
                } else 0

                // hop back to Main only to update state
                withContext(Dispatchers.Main) {
                    counting = false
                    matchCount = result
                }
            }
        }

        fun stopCounter() {
            counterJob?.cancel()
            counting = false
            showCounter = false
            matchCount = null
        }

        fun startSeriesNow(
            sel: Series,
            size: Int,
            theme: String = "All",
            minRating: Int? = null,
            maxRating: Int? = null,
            forceReset: Boolean = false
        ) {
            // Close the dialog and show the tiny spinner while we prepare
            showWelcome = false
            preparing = true
            loading = true

            GlobalScope.launch {
                // Switch series if needed
                if (sel != series) { series = sel; saveSeries(sel) }

                if (forceReset) {
                    prefs.resetStatsOnly()
                    prefs.poolCsv = ""
                }

                // Create a brand-new cycle and make it active
                val bank = CycleBank(context, sel.id)

                if (!proUnlocked && bank.size() >= 2) {
                    withContext(Dispatchers.Main) {
                        requirePro("You can save more than 2 training cycles in the full version of TrainerFish.")
                    }
                    loading = false
                    preparing = false
                    return@launch
                }

                val newId = bank.create()
                val pf = bank.prefs(newId)

                pf.lastMin   = (minRating ?: 0)
                pf.lastMax   = (maxRating ?: 0)
                pf.lastTheme = theme.ifBlank { "All" }
                pf.size      = size
                pf.resetStatsOnly()
                pf.poolCsv   = ""

                // Human-friendly auto-name, e.g. "All • 1800–2600 • 100"
                val autoName = defaultCycleLabel(pf.lastTheme, minRating, maxRating, size)
                bank.setLabel(newId, autoName)

                // ---------- Build pool once from indexes (NO suspend inside timers) ----------
                val tPickStart = SystemClock.elapsedRealtime()

                // Index data (suspending) OUTSIDE the timed section
                val buckets = withContext(Dispatchers.IO) {
                    runCatching { loadRatingBuckets(context, R.raw.train_all_buckets) }
                        .getOrElse { emptyList() }
                }
                val ids: List<Int> = if (theme.equals("all", true)) {
                    // pure CPU
                    sampleIdsFromBucketsQuick(
                        buckets = buckets,
                        limit   = size,
                        minRating = minRating,
                        maxRating = maxRating
                    )
                } else {
                    val themeMap = withContext(Dispatchers.IO) {
                        runCatching { loadThemeIndex(context, R.raw.train_all_themes) }
                            .getOrElse { emptyMap() }
                    }
                    val token = theme.trim().lowercase()
                    val themeIds = themeMap[token]
                    if (themeIds != null) {
                        sampleIdsFromThemeAndBucketsQuick(
                            themeIds = themeIds,
                            buckets  = buckets,
                            limit    = size,
                            minRating = minRating,
                            maxRating = maxRating
                        )
                    } else emptyList()
                }

                val tPick = SystemClock.elapsedRealtime() - tPickStart
                Log.d("CycleSpeed", "Pick pool: ${tPick}ms (theme=$theme, ids=${ids.size})")

                if (ids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        loading = false
                        preparing = false
                        showWelcome = true
                    }
                    return@launch
                }

                // Persist the exact pool
                pf.poolAbsCsv = ids.joinToString(",")

                // ---------- Load the selected games (suspending IO separated) ----------
                val tLoadStart = SystemClock.elapsedRealtime()

                val loaded: List<PgnGameInfo> = withContext(Dispatchers.IO) {
                    loadGamesByIndexes(context = context, resId = sel.rawRes, indexes = ids)
                }

                val tLoad = SystemClock.elapsedRealtime() - tLoadStart
                Log.d("CycleSpeed", "LOAD: ${tLoad}ms")

                withContext(Dispatchers.Main) {
                    if (loaded.isEmpty()) {
                        loading = false
                        preparing = false
                        showWelcome = true
                        return@withContext
                    }

                    // Make this new cycle the active one
                    activeCycleId = newId
                    prefs = pf

                    // Swap in the pool and jump to first puzzle
                    games = loaded
                    currentIndex = 0
                    loadGameAt(0)

                    loading = false
                    preparing = false
                }
            }
        }



        // ===== UI =====
        AlertDialog(
            onDismissRequest = { showWelcome = false },
            modifier = if (dense) Modifier.widthIn(max = 520.dp) else Modifier,
            properties = DialogProperties(usePlatformDefaultWidth = !dense),

            title = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Cycle Manager",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },

            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(maxHeight = maxH)
                        .verticalScroll(scroll)
                ) {
                    // Header row
                    val headerPad = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (nickname.isBlank()) {
                            TextButton(onClick = { showProfile = true }, contentPadding = headerPad) { Text("Enter Profile") }
                        } else {
                            Text(
                                text = "Hello, ${nickname.trim()}!",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showProfile = true }
                            )
                        }

                        // Continue the selected cycle
                        val canContinue = selectedRef?.let {
                            CycleBank(context, it.series.id).prefs(it.id).poolAbsCsv.isNotBlank()
                        } ?: false

                        Button(
                            enabled = canContinue,
                            onClick = {
                                stopCounter()

                                // Prefer the selected cycle; otherwise fall back to the currently active one
                                val ref = selectedRef ?: (if (currentPoolNonEmpty && activeCycleId > 0)
                                    CycleRef(series, activeCycleId) else null)

                                ref?.let {
                                    series = it.series
                                    saveSeries(series)
                                    activeCycleId = it.id
                                    prefs = CycleBank(context, it.series.id).prefs(it.id)
                                }
                                // Show both the tiny "preparing" flag and the full loading gallery
                                preparing = true
                                loading   = true

                                // Continue the existing cycle; when done, hide the gallery + preparing flag
                                continueCycleNow {
                                    preparing = false
                                    loading   = false
                                }
                            },
                            contentPadding = headerPad,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("Continue cycle") }

                    }

                    // ===== Current cycle summary =====
                    Divider()
                    Spacer(Modifier.height(if (dense) 6.dp else 10.dp))

                    // ---- unified cycle list (only cycles that actually have a pool) ----
                    data class CycleRef(val series: Series, val id: Int)

                    fun listAllCycles(): List<CycleRef> {
                        fun collect(s: Series): List<CycleRef> {
                            val bank = CycleBank(context, s.id)
                            return bank.list().mapNotNull { cid ->
                                val pf = bank.prefs(cid)
                                if (pf.poolAbsCsv.isNotBlank()) CycleRef(s, cid) else null
                            }
                        }
                        return collect(Series.CHALLENGER) + collect(Series.MASTER)
                    }

                    var allCycles by remember(showWelcome, welcomeRefresh) { mutableStateOf(listAllCycles()) }

                    // -1 means “none selected yet”
                    var selectedIndex by rememberSaveable(showWelcome, welcomeRefresh) { mutableStateOf(-1) }

                    // auto-select last active cycle (if exists), otherwise first; otherwise keep -1
                    LaunchedEffect(allCycles, series, activeCycleId) {
                        selectedIndex =
                            allCycles.indexOfFirst { it.series == series && it.id == activeCycleId }
                                .takeIf { it >= 0 }
                                ?: if (allCycles.isNotEmpty()) 0 else -1
                    }

                    val selectedRef = allCycles.getOrNull(selectedIndex)
                    val vPrefs = selectedRef?.let { CycleBank(context, it.series.id).prefs(it.id) }

                    // ---- summary of the selected (viewing) cycle ----
                    if (vPrefs != null) {
                        val vMin = vPrefs.lastMin.takeIf { it > 0 } ?: minStart
                        val vMax = vPrefs.lastMax.takeIf { it > 0 } ?: freeCap
                        val vTheme = vPrefs.lastTheme.ifBlank { "All" }
                        val poolCount = remember(vPrefs.poolCsv, vPrefs.size, welcomeRefresh) {
                            val n = vPrefs.poolCsv.split(',').count { it.isNotBlank() }
                            if (n > 0) n else vPrefs.size
                        }

                        // Use the actual solved list for this cycle to avoid any desync with solvedCount
                        val solvedNow = remember(vPrefs.solvedCsv, welcomeRefresh) {
                            vPrefs.solvedCsv.split(',').count { it.isNotBlank() }
                        }

                        val accNow = if (vPrefs.ptsTotal > 0) (vPrefs.ptsEarned * 100f / vPrefs.ptsTotal) else 0f
                        val avgMs = if (solvedNow > 0 && vPrefs.elapsedMs > 0L) (vPrefs.elapsedMs / solvedNow) else 0L


                        Text("${vTheme} • ${vMin}–${vMax} • ${(vPrefs.size.takeIf { it > 0 } ?: poolCount)}", fontWeight = FontWeight.SemiBold)
                        Text("Solved: $solvedNow/$poolCount,  Accuracy: ${"%.0f".format(accNow)}%")
                        Text("Average time per puzzle: ${formatHms(avgMs)}")

                    } else {
                        Text("No cycles yet. Define one below.", fontWeight = FontWeight.SemiBold)
                    }

                    // ---- navigator row ----
                    Spacer(Modifier.height(6.dp))
                    val totalCycles = allCycles.size
                    if (totalCycles > 0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Saved cycles: ${ (selectedIndex + 1).coerceIn(0, totalCycles) } / $totalCycles",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = {
                                    if (totalCycles > 0) {
                                        selectedIndex = (if (selectedIndex < 0) 0 else selectedIndex - 1 + totalCycles) % totalCycles
                                        stopCounter()
                                        allCycles.getOrNull(selectedIndex)?.let { ref ->
                                            series = ref.series; saveSeries(series)
                                            activeCycleId = ref.id
                                            prefs = CycleBank(context, ref.series.id).prefs(ref.id)
                                        }
                                        welcomeRefresh++
                                    }
                                }) { Text("Previous") }

                                TextButton(onClick = {
                                    val ref = selectedRef ?: return@TextButton
                                    val bank = CycleBank(context, ref.series.id)
                                    bank.delete(ref.id)
                                    if (activeCycleId == ref.id && series == ref.series) activeCycleId = 0
                                    stopCounter()
                                    allCycles = listAllCycles()
                                    selectedIndex = if (allCycles.isEmpty()) -1 else selectedIndex.coerceAtMost(allCycles.lastIndex)
                                    allCycles.getOrNull(selectedIndex)?.let { r ->
                                        series = r.series; saveSeries(series)
                                        activeCycleId = r.id
                                        prefs = CycleBank(context, r.series.id).prefs(r.id)
                                    }
                                    welcomeRefresh++
                                }) { Text("Delete") }

                                TextButton(onClick = {
                                    if (totalCycles > 0) {
                                        selectedIndex = (if (selectedIndex < 0) 0 else selectedIndex + 1) % totalCycles
                                        stopCounter()
                                        allCycles.getOrNull(selectedIndex)?.let { ref ->
                                            series = ref.series; saveSeries(series)
                                            activeCycleId = ref.id
                                            prefs = CycleBank(context, ref.series.id).prefs(ref.id)
                                        }
                                        welcomeRefresh++
                                    }
                                }) { Text("Next") }
                            }
                        }

                    }


                    Spacer(Modifier.height(4.dp))
                    Divider()
                    Spacer(Modifier.height(if (dense) 6.dp else 12.dp))

                    // ===== Redefine / Add another cycle =====
                    Text("Define another cycle")
                    Spacer(Modifier.height(if (dense) 6.dp else 12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { s ->
                                minText = s.filter { it.isDigit() }.take(4)
                                stopCounter() // cancel any pending count; keep last result
                            },
                            label = { Text("Min Rating ($minStart–$absoluteMax)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxText,
                            onValueChange = { s ->
                                val clean = s.filter { it.isDigit() }.take(4)
                                val value = clean.toIntOrNull()

                                if (!proUnlocked && value != null && value > freeCap) {
                                    // Clamp to freeCap and show paywall
                                    maxText = freeCap.toString()
                                    stopCounter()
                                    requirePro("Ratings above $freeCap are available in the full version of TrainerFish.")
                                } else {
                                    maxText = clean
                                    stopCounter()
                                }
                            },
                            label = {
                                Text("Max Rating (1800–3210)")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                    }

                    Spacer(Modifier.height(6.dp))
                    Text("${minPreview} – ${maxPreview}")

                    Spacer(Modifier.height(if (dense) 6.dp else 12.dp))

                    // ---------- On-demand counter (declare BEFORE using in sizeCap) ----------
                    val scope = rememberCoroutineScope()
                    var counting    by remember { mutableStateOf(false) }
                    var matchesNow  by rememberSaveable(showWelcome) { mutableStateOf<Int?>(null) } // last computed value
                    var counterJob  by remember { mutableStateOf<Job?>(null) }

                    // tiny index caches (loaded once)
                    var buckets  by remember { mutableStateOf<List<RatingBucket>>(emptyList()) }
                    var themeMap by remember { mutableStateOf<Map<String, IntArray>>(emptyMap()) }

                    LaunchedEffect(Unit) {
                        if (buckets.isEmpty()) {
                            buckets = withContext(Dispatchers.IO) {
                                runCatching { loadRatingBuckets(context, R.raw.train_all_buckets) }.getOrElse { emptyList() }
                            }
                        }
                        if (themeMap.isEmpty()) {
                            themeMap = withContext(Dispatchers.IO) {
                                runCatching { loadThemeIndex(context, R.raw.train_all_themes) }.getOrElse { emptyMap() }
                            }
                        }
                    }

                    // purely in-memory count from indexes
                    fun countMatchesQuick(theme: String, lo: Int?, hi: Int?): Int {
                        if (buckets.isEmpty()) return 0

                        fun floor25(r: Int) = (r / 25) * 25
                        val bMin = lo?.let(::floor25)
                        val bMax = hi?.let(::floor25)

                        // allowed ranges from buckets
                        val ranges = ArrayList<IntRange>()
                        for (b in buckets) {
                            if ((bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)) {
                                ranges += b.startIdx..b.endIdx
                            }
                        }
                        if (ranges.isEmpty()) return 0

                        // ALL themes: just sum the ranges
                        if (theme.equals("all", true)) {
                            var total = 0
                            for (r in ranges) total += (r.last - r.first + 1).coerceAtLeast(0)
                            return total
                        }

                        // specific theme: count ids that fall inside any allowed range
                        val ids = themeMap[theme.trim().lowercase()] ?: return 0
                        var i = 0; var j = 0; var cnt = 0
                        val sortedIds = ids.sortedArray() // ensure monotonic
                        while (i < sortedIds.size && j < ranges.size) {
                            val id = sortedIds[i]
                            val r  = ranges[j]
                            when {
                                id < r.first -> i++
                                id > r.last  -> j++
                                else         -> { cnt++; i++ }
                            }
                        }
                        return cnt
                    }

                    fun triggerCount(theme: String, lo: Int, hi: Int) {
                        counterJob?.cancel()
                        counting = true
                        matchesNow = null
                        counterJob = scope.launch(Dispatchers.Default) {
                            val res = withTimeoutOrNull(1500) { countMatchesQuick(theme, lo, hi) }
                            withContext(Dispatchers.Main) {
                                counting = false
                                matchesNow = res ?: 0
                            }
                        }
                    }

                    fun stopCounter() {
                        counterJob?.cancel()
                        counting = false
                        matchesNow = null // clear stale value whenever inputs change
                    }

                    // ---------- Cycle size (hard-capped by last computed matches) ----------
                    Text("Add/Decrease games in new cycle")

                    // How many games are actually available in this bucket
                    val effectiveMatches = matchesNow ?: cap
                    val fullSizeCap = max(25, min(cap, effectiveMatches))

                    // Lite users can only go up to 50 games per cycle
                    val freeSizeCap = min(fullSizeCap, 50)

                    LaunchedEffect(fullSizeCap, proUnlocked) {
                        val maxAllowed = if (proUnlocked) fullSizeCap else freeSizeCap
                        slider = slider.coerceIn(25, maxAllowed)
                    }

                    Slider(
                        value = slider.toFloat(),
                        onValueChange = { v ->
                            val snapped = ((v / 25f).roundToInt() * 25)
                                .coerceIn(25, fullSizeCap)

                            if (!proUnlocked && snapped > freeSizeCap) {
                                // Lite: clamp to 50 and show paywall
                                slider = freeSizeCap
                                requirePro("Cycles longer than $freeSizeCap games are available in the full version of TrainerFish.")
                            } else if (snapped != slider) {
                                slider = snapped
                            }
                        },
                        valueRange = 25f..fullSizeCap.toFloat(),
                        steps = ((fullSizeCap - 25) / 25 - 1).coerceAtLeast(0)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$slider of $fullSizeCap games")

                        // Right-side label:
                        // - default: show total puzzles in the full pool
                        // - after counting: show only the matching count
                        val matching = matchesNow
                        val rightLabel = if (matching != null) {
                            "Matching: " + String.format("%,d", matching)
                        } else {
                            "Total games: " + String.format("%,d", pgnPoolCount)
                        }

                        Text(
                            rightLabel,
                            maxLines = 1,
                            softWrap = false
                        )
                    }



                    // One-shot (re)count button
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                val lo = (minText.toIntOrNull() ?: minStart).coerceIn(minStart, absoluteMax)
                                val hi = (maxText.toIntOrNull() ?: freeCap).coerceIn(minStart, freeCap).coerceAtLeast(lo)
                                triggerCount(themeChoice, lo, hi)
                            }
                        ) {
                            Text(
                                when {
                                    counting          -> "Counting…"
                                    matchesNow != null -> "Recount matching games"
                                    else               -> "Count matching games"
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { themeMenuOpen = true; stopCounter() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Theme: $themeChoice", maxLines = 1, softWrap = false) }

                        DropdownMenu(
                            expanded = themeMenuOpen,
                            onDismissRequest = { themeMenuOpen = false }
                        ) {
                            themeOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        themeMenuOpen = false
                                        stopCounter()

                                        if (proUnlocked || opt.equals("All", ignoreCase = true)) {
                                            // Full users (or "All" in any version) can actually select
                                            themeChoice = opt
                                        } else {
                                            // Lite users clicking a specific theme: show paywall
                                            requirePro("Choosing specific themes is available in the full version of TrainerFish.")
                                        }
                                    }
                                )
                            }
                        }

                    }



                }
            },

            confirmButton = {
                // local padding (so btnPad2 is always in scope)
                val cfg2 = LocalConfiguration.current
                val dense2 = cfg2.screenWidthDp > cfg2.screenHeightDp
                val btnPad2 = if (dense2)
                    PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                else
                    ButtonDefaults.TextButtonContentPadding

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Reset stats (start over)
                        TextButton(
                            onClick = { showStatsConfirm = true },
                            contentPadding = btnPad2
                        ) { Text("Reset stats") }

                        // Show confirm dialog for "Reset stats"
                        if (showStatsConfirm) {
                            AlertDialog(
                                onDismissRequest = { showStatsConfirm = false },
                                title = { Text("Start over?") },
                                text = {
                                    Text(
                                        "This will ERASE all your training stats (accuracy, time, solved counters) " +
                                                "and DELETE all saved cycles. You’ll start fresh with zero cycles."
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showStatsConfirm = false

                                        // 1) Per-puzzle stats (Woodpecker)
                                        // (The store in your project exposes resetAll(); if you named it wipeAll(), use that.)
                                        runCatching { WoodpeckerStore(context).resetAll() }

                                        // 2) Remove all cycles for both series
                                        runCatching { CycleBank(context, Series.CHALLENGER.id).wipeAll() }
                                        runCatching { CycleBank(context, Series.MASTER.id).wipeAll() }

                                        // 3) Nudge UI back to a clean Welcome
                                        //    (clear in-memory state and force a refresh)
                                        // If you track these vars, keep them; else safely ignore lines that don't exist in your file.
                                        matchCount = null
                                        selectedIndex = 0
                                        allCycles = emptyList()
                                        welcomeRefresh++
                                    }) {
                                        Text("Yes, reset")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showStatsConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }


                        // PLAY CYCLE — creates a new cycle and closes Welcome
                        TextButton(
                            onClick = {
                                stopCounter()

                                val minStart = 1800
                                val absoluteMax = 3210
                                val freeCap = 3210 // change to 2099 for free builds

                                var lo = (minText.toIntOrNull() ?: minStart).coerceIn(minStart, absoluteMax)
                                var hi = (maxText.toIntOrNull() ?: freeCap).coerceIn(minStart, freeCap)
                                if (hi < lo) hi = lo

                                val reqSize = slider.coerceIn(25, CYCLE_MAX)
                                val sel = if (hi <= 2099) Series.CHALLENGER else Series.MASTER

                                startSeriesNow(
                                    sel = sel,
                                    size = reqSize,
                                    theme = themeChoice,
                                    minRating = lo,
                                    maxRating = hi,
                                    forceReset = false
                                )
                            },
                            contentPadding = btnPad2
                        ) { Text("Play cycle") }
                    }

                    Spacer(Modifier.height(if (dense2) 6.dp else 8.dp))

                    // Exit centered (optional)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { android.os.Process.killProcess(android.os.Process.myPid()) },
                            contentPadding = btnPad2
                        ) { Text("Exit") }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "© 2025 by Cliburn Anthony A. Orbe",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

            },

            dismissButton = {}
        )

        // Ensure counter stops when dialog closes
        LaunchedEffect(showWelcome) { if (!showWelcome) stopCounter() }


    }

// --- Pro paywall dialog (Lite → Pro) ---
    if (showProDialog && proDialogReason != null) {
        val ctx = LocalContext.current
        val activity = ctx as? Activity

        AlertDialog(
            onDismissRequest = {
                showProDialog = false
            },
            title = { Text("Unlock TrainerFish (full version)") },
            text = {
                Column {
                    Text(proDialogReason!!)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "The full version unlocks unlimited Woodpecker cycles, higher rating ranges, " +
                                "more saved training cycles, and all future TrainerFish features.",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showProDialog = false }) {
                    Text("Maybe later")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showProDialog = false
                        activity?.let { BillingManager.launchPurchase(it) }
                    }
                ) {
                    Text("Unlock full version")
                }
            }
        )
    }

    // --------------- Cycle complete ---------------
    ReplayCycleCompleteDialog(
        show = showCycleComplete,
        seriesTitle = series.title,
        cycleId = prefs.cycleId,
        cap = capFor(series),
        initialSize = prefs.size.coerceIn(25, capFor(series)),
        onDismiss = { showCycleComplete = false },
        onStartNewCycle = { newSize ->
            prefs.cycleId = prefs.cycleId + 1
            buildNewCyclePool(games.size, newSize)
            nextIndexFromPool()?.let { loadGameAt(it) }
            showCycleComplete = false
        },
        onRepeatCycle = {
            prefs.resetStatsOnly()
            prefs.solvedCsv = ""
            nextIndexFromPool()?.let { loadGameAt(it) }
            showCycleComplete = false
        }
    )


    // --------------- Overlays:
    ReplayExitDialog(
        show = showExitConfirm,
        onDismiss = { showExitConfirm = false },
        onConfirmExit = {
            showExitConfirm = false
            // Clean shutdown of engine/eval
            try { evalJob?.cancel() } catch (_: Throwable) {}
            evalJob = null
            stopEngineNow(setState = true)
            engineEnabled = false

            // Hard exit (same style you use in the Welcome dialog)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    )

    ReplayAboutDialog(
        show = showAbout,
        onDismiss = { showAbout = false }
    )



    if (showProfile) {
        NicknameDialog(
            current = nickname,
            onSave = {
                nickname = it
                profile.nickname = it
                showProfile = false
            },
            onClose = { showProfile = false }
        )
    }

    // --------------- Settings menu ---------------
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    // NEW: Profile entry
                    TextButton(
                        onClick = {
                            showSettings = false
                            showProfile = true       // open the profile dialog
                        }
                    ) { Text("Profile") }

                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showSettings = false
                            mode = TrainerMode.WOODPECKER
                            showWelcome = true
                            status = "Trainer — cycle manager"
                        }
                    ) { Text("Cycle manager") }

                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showSettings = false
                            showCelebrationDialog = true
                        }
                    ) { Text("Celebration") }

                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showSettings = false
                            showThemeDialog = true
                        }
                    ) { Text("Color theme") }

                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showSettings = false
                            showSoundDialog = true
                        }
                    ) { Text("Sound pack") }

                    // Chess clock
                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showSettings = false
                            showAbout = true
                        }
                    ) { Text("About / Legal") }

                    // Chess clock
                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showSettings = false
                            onClock()      // switches root screen to Clock
                        }
                    ) { Text("Chess clock") }


                    TextButton(
                        onClick = {
                            showSettings = false
                            onClock()      // switches root screen to Clock
                        }
                    ) { Text("Chess clock") }

                    // --- Developer / Testing -----------------------------------------
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Developer / Testing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Force Pro (testing only)",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = devProOverride,
                            onCheckedChange = { checked ->
                                devProOverride = checked
                                premiumPrefs.isPro = checked
                            }
                        )
                    }
                }



            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Close")
                }
            }
        )
    }

    ReplayProfileDialog(
        show = showProfile,
        nickname = nickname,
        poolTotal = 261_071,
        onDismiss = { showProfile = false },
        onSaveNickname = { newNick ->
            val trimmed = newNick.trim()
            nickname = trimmed
            profile.nickname = trimmed
            showProfile = false
        }
    )





    // --------------- Celebration submenu ---------------
    if (showCelebrationDialog) {
        AlertDialog(
            onDismissRequest = { showCelebrationDialog = false },
            title = { Text("Celebration") },
            text = {
                Column {
                    @Composable
                    fun option(id: String, label: String) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    setCelebration(id)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (celebration == id),
                                onClick = { setCelebration(id) }
                            )
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }

                    option("none", "None")
                    option("confetti", "Confetti")
                    option("gold", "Gold coins (Frank Marshall)")
                }
            },
            confirmButton = {
                TextButton(onClick = { showCelebrationDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // --------------- Color theme submenu ---------------
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Board color theme") },
            text = {
                Column {
                    @Composable
                    fun themeRow(id: String, label: String) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { equipBoardTheme(id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (boardTheme == id),
                                onClick = { equipBoardTheme(id) }
                            )
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }

                    themeRow("classic", "Classic (green)")
                    themeRow("wood", "Wood")
                    themeRow("night", "Night")
                    themeRow("blue",    "Blue")
                    themeRow("sand",    "Sand")
                    themeRow("forest",  "Forest")

                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // --------------- Sound pack submenu ---------------
    if (showSoundDialog) {
        AlertDialog(
            onDismissRequest = { showSoundDialog = false },
            title = { Text("Sound pack") },
            text = {
                Column {
                    @Composable
                    fun soundRow(id: String, label: String) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { equipSoundPack(id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (soundPack == id),
                                onClick = { equipSoundPack(id) }
                            )
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }

                    soundRow("wood", "Wood knock")
                    soundRow("digital", "Digital")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundDialog = false }) {
                    Text("Done")
                }
            }
        )
    }


    ReplayPosterDialog(
        show = showPoster,
        headline = posterHeadline,
        sub = posterSub,
        foot = posterFoot,
        onDismiss = { showPoster = false }
    )


}


// ---------- Overlays & helpers ----------
private fun posToIndex(pos: Offset, size: IntSize): Int? {
    if (size.width <= 0 || size.height <= 0) return null
    val sqW = size.width / 8f
    val sqH = size.height / 8f
    if (sqW <= 0f || sqH <= 0f) return null

    val fileF = pos.x / sqW
    val rankF = pos.y / sqH
    if (!fileF.isFinite() || !rankF.isFinite()) return null

    val file = kotlin.math.floor(fileF).toInt()
    val rankFromTop = kotlin.math.floor(rankF).toInt()
    if (file !in 0..7 || rankFromTop !in 0..7) return null

    val rank = 7 - rankFromTop
    return rank * 8 + file
}




@Composable
private fun BoxScope.drawPieceOverlay(piece: Piece?, center: Offset, boardSize: IntSize) {
    if (piece == null || boardSize.width == 0) return
    val sq = min(boardSize.width, boardSize.height) / 8f
    val half = (sq / 2f)
    val x = (center.x - half).roundToInt()
    val y = (center.y - half).roundToInt()
    Box(
        Modifier
            .offset { IntOffset(x, y) }
            .size(with(LocalDensity.current) { sq.toDp() }),
        contentAlignment = Alignment.Center
    ) {
        val resId = when (piece.type) {
            PieceType.KING   -> if (piece.isWhite) R.drawable.cburnett_wk else R.drawable.cburnett_bk
            PieceType.QUEEN  -> if (piece.isWhite) R.drawable.cburnett_wq else R.drawable.cburnett_bq
            PieceType.ROOK   -> if (piece.isWhite) R.drawable.cburnett_wr else R.drawable.cburnett_br
            PieceType.BISHOP -> if (piece.isWhite) R.drawable.cburnett_wb else R.drawable.cburnett_bb
            PieceType.KNIGHT -> if (piece.isWhite) R.drawable.cburnett_wn else R.drawable.cburnett_bn
            PieceType.PAWN   -> if (piece.isWhite) R.drawable.cburnett_wp else R.drawable.cburnett_bp
        }
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(piece.glyph, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

// Build a compact "Engine: …" label from a Stockfish info line
private fun buildEngineStatusFromInfo(
    line: String,
    showPv: Boolean = false,
    maxPvPlies: Int = 5
): String {
    val mate = Regex("""\bscore\s+mate\s+(-?\d+)""")
        .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val cp   = Regex("""\bscore\s+cp\s+(-?\d+)""")
        .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

    val base = when {
        mate != null && mate != 0 ->
            "TrainerFish: mate in ${kotlin.math.abs(mate)}"
        cp != null ->
            "TrainerFish: " + String.format("%+.2f", (cp / 100f))
        else ->
            "TrainerFish: …"
    }

    if (!showPv) return base

    // Try to extract the principal variation: "... pv e2e4 e7e5 g1f3 Nc6 ..."
    val pvMatch = Regex("""\bpv\s+(.+)$""").find(line)
    val pvPart = pvMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (pvPart.isBlank()) return base

    val pvTokens = pvPart.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (pvTokens.isEmpty()) return base

    // Show only the first [maxPvPlies] UCI moves to keep things compact
    val shortPv = pvTokens.take(maxPvPlies).joinToString(" ")

    return if (shortPv.isNotBlank()) {
        "$base  •  $shortPv"
    } else {
        base
    }
}

private fun uciToMoveOnBoard(
    board: com.github.bhlangonijr.chesslib.Board,
    uci: String
): com.github.bhlangonijr.chesslib.move.Move? {
    if (uci.length < 4) return null
    val from = com.github.bhlangonijr.chesslib.Square.fromValue(uci.substring(0, 2).uppercase())
    val to   = com.github.bhlangonijr.chesslib.Square.fromValue(uci.substring(2, 4).uppercase())
    return if (uci.length >= 5) {
        val side = board.sideToMove
        val promo = when (uci[4].uppercaseChar()) {
            'Q' -> if (side == com.github.bhlangonijr.chesslib.Side.WHITE) com.github.bhlangonijr.chesslib.Piece.WHITE_QUEEN else com.github.bhlangonijr.chesslib.Piece.BLACK_QUEEN
            'R' -> if (side == com.github.bhlangonijr.chesslib.Side.WHITE) com.github.bhlangonijr.chesslib.Piece.WHITE_ROOK  else com.github.bhlangonijr.chesslib.Piece.BLACK_ROOK
            'B' -> if (side == com.github.bhlangonijr.chesslib.Side.WHITE) com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP else com.github.bhlangonijr.chesslib.Piece.BLACK_BISHOP
            'N' -> if (side == com.github.bhlangonijr.chesslib.Side.WHITE) com.github.bhlangonijr.chesslib.Piece.WHITE_KNIGHT else com.github.bhlangonijr.chesslib.Piece.BLACK_KNIGHT
            else -> null
        } ?: return null
        com.github.bhlangonijr.chesslib.move.Move(from, to, promo)
    } else {
        com.github.bhlangonijr.chesslib.move.Move(from, to)
    }
}



private suspend fun bestMoveForFen(
    ctx: Context,
    fen: String,
    movetimeMs: Int = 5_000   // sensible default; caller can still override
): String? = withContext(Dispatchers.IO) {

    // Ensure the JNI engine is running; start() is idempotent.
    try {
        ProcEngine.start("inline")
    } catch (_: Throwable) {
        // If we can't start the engine, just bail out.
        return@withContext null
    }

    // Remember the last log line *before* we send the new command.
    val initialLast = ProcEngine.lines.value.lastOrNull()

    // Ask the shared engine to think on this FEN once.
    ProcEngine.evaluateFen(fen = fen, movetimeMs = movetimeMs)

    var bestFromBestmove: String? = null
    var fallbackFromPv: String? = null

    try {
        // Allow a little extra time beyond movetimeMs for the engine to respond.
        val timeoutMs = movetimeMs.toLong() + 2_000L

        withTimeout(timeoutMs) {
            while (bestFromBestmove == null) {
                val snapshot = ProcEngine.lines.value

                // Scan from newest to oldest; stop when we hit the "old" boundary.
                for (i in snapshot.size - 1 downTo 0) {
                    val line = snapshot[i]

                    // We've reached lines that were already present before this search.
                    if (initialLast != null && line == initialLast) {
                        break
                    }

                    // Ideal case: explicit bestmove
                    if (line.startsWith("bestmove")) {
                        val parts = line.split(" ")
                        val candidate = parts.getOrNull(1)
                        if (candidate != null &&
                            candidate.length >= 4 &&
                            candidate != "(none)" &&
                            candidate != "0000"
                        ) {
                            bestFromBestmove = candidate
                            break
                        }
                    }

                    // Fallback case: take the first move from the PV of an "info" line.
                    if (fallbackFromPv == null && line.startsWith("info ") && line.contains(" pv ")) {
                        val pvMatch = Regex("""\bpv\s+(.+)$""").find(line)
                        val pvPart = pvMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
                        if (pvPart.isNotBlank()) {
                            val token = pvPart.split(Regex("\\s+"))
                                .firstOrNull { it.length >= 4 }
                            if (token != null) {
                                fallbackFromPv = token
                                // We don't break here; we still prefer a true "bestmove" if it appears.
                            }
                        }
                    }
                }

                if (bestFromBestmove != null) break
                delay(50L)
            }
        }
    } catch (_: Throwable) {
        // timeout or cancellation -> we'll fall back below
    }

    // Primary: explicit bestmove; if missing, fall back to the PV move if we saw one.
    return@withContext bestFromBestmove ?: fallbackFromPv
}


private fun prettyFromUci(board: com.github.bhlangonijr.chesslib.Board, uci: String): String {
    // uci: e2e4 or e7e8q
    val from = uci.substring(0, 2)
    val to   = uci.substring(2, 4)
    val promo = if (uci.length >= 5) uci[4].lowercaseChar() else null

    val fromSq = com.github.bhlangonijr.chesslib.Square.valueOf(from.uppercase())
    val p = board.getPiece(fromSq)
    val letter = when (p?.pieceType) {
        com.github.bhlangonijr.chesslib.PieceType.KING   -> "K"
        com.github.bhlangonijr.chesslib.PieceType.QUEEN  -> "Q"
        com.github.bhlangonijr.chesslib.PieceType.ROOK   -> "R"
        com.github.bhlangonijr.chesslib.PieceType.BISHOP -> "B"
        com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> "N"
        else -> "" // pawns: no letter
    }

    val promoSuffix = promo?.let {
        val L = when (it) { 'q' -> "Q"; 'r' -> "R"; 'b' -> "B"; 'n' -> "N"; else -> null }
        L?.let { "=$it" } ?: ""
    } ?: ""

    return buildString {
        if (letter.isNotEmpty()) append(letter)
        append(from).append('-').append(to).append(promoSuffix)
    }
}

private fun promoMatches(mv: com.github.bhlangonijr.chesslib.move.Move, p: Int): Boolean {
    val promoPiece = mv.promotion ?: return p == 0
    return when (promoPiece) {
        com.github.bhlangonijr.chesslib.Piece.WHITE_QUEEN,
        com.github.bhlangonijr.chesslib.Piece.BLACK_QUEEN -> p == 4
        com.github.bhlangonijr.chesslib.Piece.WHITE_ROOK,
        com.github.bhlangonijr.chesslib.Piece.BLACK_ROOK -> p == 3
        com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP,
        com.github.bhlangonijr.chesslib.Piece.BLACK_BISHOP -> p == 2
        com.github.bhlangonijr.chesslib.Piece.WHITE_KNIGHT,
        com.github.bhlangonijr.chesslib.Piece.BLACK_KNIGHT -> p == 1
        else -> p == 0
    }
}

@Composable
private fun ReplayLoadingOverlay(loading: Boolean) {
    if (!loading) return

    LoadingGalleryDialog(
        show = true,
        images = listOf(
            R.drawable.loading_pos01, R.drawable.loading_pos02, R.drawable.loading_pos03,
            R.drawable.loading_pos04, R.drawable.loading_pos05, R.drawable.loading_pos06,
            R.drawable.loading_pos07, R.drawable.loading_pos08, R.drawable.loading_pos09,
            R.drawable.loading_pos10, R.drawable.loading_pos11, R.drawable.loading_pos12,
            R.drawable.loading_pos13, R.drawable.loading_pos14, R.drawable.loading_pos15,
            R.drawable.loading_pos16, R.drawable.loading_pos17, R.drawable.loading_pos18
        ),
        captions = emptyList(),
        message = "Preparing PGN cycle. This only takes a few seconds.",
        sampleCount = 18,
        switchMs = 10_000L,
        onDismiss = null
    )

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ReplayTopBar(
    mode: TrainerMode,
    iconOnlyTopBar: Boolean,
    soundOn: Boolean,
    engineEnabled: Boolean,
    endgameResult: EndgameResult?,
    engineStatus: String,
    goodJobNow: Boolean,
    nickname: String,
    onToggleSound: () -> Unit,
    onToggleEngine: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitRequested: () -> Unit
) {
    val isEndgame = (mode == TrainerMode.ENDGAME)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sound toggle
        PushButton(
            text = if (iconOnlyTopBar) "" else if (soundOn) "Mute" else "Unmute",
            leading = { Text(if (soundOn) "🔊" else "🔇") },
            onClick = onToggleSound,
            compact = true
        )

        if (isEndgame) {
            // In Endgame the engine is controlled automatically; the button is just a disabled icon.
            PushButton(
                text = if (iconOnlyTopBar) "" else "Engine",
                leading = { Text("🐟") },
                onClick = { /* disabled in Endgame */ },
                enabled = false,
                modifier = Modifier.alpha(0.5f),
                compact = true
            )
        } else {
            // Tactics / Opening: normal engine toggle
            PushButton(
                text = if (iconOnlyTopBar) "" else if (engineEnabled) "Engine: ON" else "Engine: OFF",
                leading = { Text("🐟") },
                onClick = onToggleEngine,
                compact = true
            )
        }

        // Center label
        val titleText = if (isEndgame) {
            when (endgameResult) {
                EndgameResult.WIN  -> "You Win!"
                EndgameResult.LOSS -> "Fish Wins"
                EndgameResult.DRAW -> "Draw"
                null               -> engineStatus
            }
        } else {
            if (goodJobNow) {
                val nick = nickname.trim()
                if (nick.isNotEmpty()) "Good Job, $nick!" else "Good Job!"
            } else ""
        }

        val titleColor = if (isEndgame) {
            when (endgameResult) {
                EndgameResult.WIN  -> MaterialTheme.colorScheme.error
                EndgameResult.LOSS -> MaterialTheme.colorScheme.error
                EndgameResult.DRAW -> MaterialTheme.colorScheme.primary
                null               -> MaterialTheme.colorScheme.primary
            }
        } else {
            MaterialTheme.colorScheme.error
        }

        Text(
            text = titleText,
            color = titleColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )

        // Right: Settings + Exit
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {

            // Settings menu (with Cycle Manager + cosmetics)
            PushButton(
                text = if (iconOnlyTopBar) "" else "Settings",
                leading = { Text("⚙") },
                onClick = onOpenSettings,
                compact = true
            )

            // Exit app (with confirmation)
            PushButton(
                text = if (iconOnlyTopBar) "" else "Exit",
                leading = { Text("🚪") },
                onClick = onExitRequested,
                compact = true
            )
        }
    }
}

@Composable
private fun ReplayModeChips(
    mode: TrainerMode,
    openingLoading: Boolean,
    onSelectTactics: () -> Unit,
    onSelectEndgame: () -> Unit,
    onSelectOpening: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = (mode == TrainerMode.WOODPECKER),
            onClick = onSelectTactics,
            label = { Text("Tactics") }
        )

        FilterChip(
            selected = (mode == TrainerMode.ENDGAME),
            onClick = onSelectEndgame,
            label = { Text("Endgame") }
        )

        FilterChip(
            selected = (mode == TrainerMode.OPENING),
            onClick = onSelectOpening,
            label = { Text("Opening") }
        )
    }

    if (mode == TrainerMode.OPENING && openingLoading) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Loading opening tree…")
        }
    }

}

@Composable
private fun ReplayOpeningBottomBar(
    showOpeningArrows: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onFlip: () -> Unit,
    onToggleArrows: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PushButton(
            text = "Back",
            onClick = onBack,
            compact = true
        )

        PushButton(
            text = "Reset",
            onClick = onReset,
            compact = true
        )

        PushButton(
            text = "Flip",
            onClick = onFlip,
            compact = true
        )

        Spacer(Modifier.weight(1f))

        FilterChip(
            selected = showOpeningArrows,
            onClick = onToggleArrows,
            label = { Text("Arrows") }
        )
    }
}

@Composable
private fun ReplayExitDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit TrainerFish?") },
        text = { Text("Are you sure you want to close the app?") },
        confirmButton = {
            TextButton(onClick = onConfirmExit) {
                Text("Exit")
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
private fun ReplayPosterDialog(
    show: Boolean,
    headline: String,
    sub: String,
    foot: String,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Milestone unlocked!") },
        text = {
            Column {
                if (headline.isNotBlank()) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (sub.isNotBlank()) {
                    Text(sub)
                    Spacer(Modifier.height(4.dp))
                }
                if (foot.isNotBlank()) {
                    Text(
                        foot,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ReplayProfileDialog(
    show: Boolean,
    nickname: String,
    poolTotal: Int,
    onDismiss: () -> Unit,
    onSaveNickname: (String) -> Unit
) {
    if (!show) return

    val ctx = LocalContext.current

    // ------- Aggregate stats across *all* cycles in both series -------
    var solvedTotal = 0
    var ptsEarnedTotal = 0
    var ptsPossibleTotal = 0
    var elapsedTotalMs = 0L
    var xpTotal = 0

    for (series in listOf(Series.CHALLENGER, Series.MASTER)) {
        val bank = CycleBank(ctx, series.id)
        for (cid in bank.list()) {
            val pf = bank.prefs(cid)

            if (pf.poolAbsCsv.isNotBlank()) {
                solvedTotal      += pf.solvedCount
                ptsEarnedTotal   += pf.ptsEarned
                ptsPossibleTotal += pf.ptsTotal
                elapsedTotalMs   += pf.elapsedMs
                xpTotal          += pf.xp
            }
        }
    }

    // ------- Accuracy -------
    val accuracyPct =
        if (ptsPossibleTotal > 0)
            100f * ptsEarnedTotal.toFloat() / ptsPossibleTotal.toFloat()
        else 0f

    // ------- Average time -------
    val avgMs =
        if (solvedTotal > 0) elapsedTotalMs / solvedTotal
        else 0L

    // ------- XP milestones -------
    val nextTarget = ((xpTotal / 1000) + 1) * 1000
    val xpToNext = (nextTarget - xpTotal).coerceAtLeast(0)

    // Nickname edit state
    var editNick by remember(show) { mutableStateOf(nickname.take(6)) }

    fun formatMs(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {

                // Nickname
                OutlinedTextField(
                    value = editNick,
                    onValueChange = { s -> editNick = s.take(6) },
                    label = { Text("Nickname (max 6 chars)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "This name appears on your milestone posters and in the header.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(16.dp))
                Text("Overall stats", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Text(
                    "Puzzles solved: " +
                            "${"%,d".format(solvedTotal)} / ${"%,d".format(poolTotal)}+"
                )
                Text("Accuracy: ${"%.1f".format(accuracyPct)}%")
                Text("Average time: ${formatMs(avgMs)} per puzzle")

                Spacer(Modifier.height(12.dp))
                Text("XP: ${"%,d".format(xpTotal)}")
                Text(
                    "XP to next milestone: " +
                            "${"%,d".format(xpToNext)} (at ${"%,d".format(nextTarget)} XP)"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveNickname(editNick) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ReplayCycleCompleteDialog(
    show: Boolean,
    seriesTitle: String,
    cycleId: Int,
    cap: Int,
    initialSize: Int,
    onDismiss: () -> Unit,
    onStartNewCycle: (Int) -> Unit,
    onRepeatCycle: () -> Unit
) {
    if (!show) return

    val cappedCap = cap.coerceAtLeast(25)
    var newSize by remember(cappedCap, initialSize) {
        mutableStateOf(initialSize.coerceIn(25, cappedCap))
    }

    // same step logic as before, but guard against weird caps
    val steps = if (cappedCap > 25) {
        (cappedCap - 25) / 25 - 1
    } else {
        0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cycle complete!") },
        text = {
            Column {
                Text("You finished $seriesTitle • Cycle #$cycleId.")
                Spacer(Modifier.height(12.dp))
                Text("Add/Decrease games in next cycle")
                Slider(
                    value = newSize.toFloat(),
                    onValueChange = { v ->
                        newSize = ((v / 25f).roundToInt() * 25)
                            .coerceIn(25, cappedCap)
                    },
                    valueRange = 25f..cappedCap.toFloat(),
                    steps = steps
                )
                Text("$newSize of $cappedCap games")
            }
        },
        confirmButton = {
            TextButton(onClick = { onStartNewCycle(newSize) }) {
                Text("Start new cycle")
            }
        },
        dismissButton = {
            TextButton(onClick = onRepeatCycle) {
                Text("Repeat cycle")
            }
        }
    )
}

@Composable
private fun ReplayAboutDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About / Legal", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "TrainerFish",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A chess training app for tactics, endgames, and openings.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Engine",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This app uses the Stockfish chess engine.\n\n" +
                            "Stockfish © 2004–2025 The Stockfish developers.\n" +
                            "Distributed under the GNU General Public License v3.0 (GPLv3).",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Engine source code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The complete source code for the Stockfish engine build used in this app is available at:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "https://github.com/tonorbe/trainerfish-stockfish-src",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "License",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stockfish is licensed under the GNU General Public License v3.0 (GPLv3).\n" +
                            "License text: https://www.gnu.org/licenses/gpl-3.0.html",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}















