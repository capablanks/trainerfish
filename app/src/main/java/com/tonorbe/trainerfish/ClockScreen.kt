package com.tonorbe.trainerfish

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import androidx.compose.material3.OutlinedButton


// --- theme bits used by the clock (unchanged timers/bars)
private val LCD_GREEN = Color(0xFF39FF14)
private val SCREEN_BLACK = Color(0xFF0B0B0D)
private val ACTIVE_GRAY = Color(0xFF2A2A2D)
private val ORANGE = Color(0xFFFF9800)

// UI
private val UI_GRAY_BG   = Color(0xFF2F2F33)
private val UI_GRAY_LINE = Color(0xFF3B3B40)
private val UI_BTN       = Color(0xFF4B4B50)
private val UI_ICON      = Color(0xFFCFCFD3)
private val UI_BAR_LIGHT = Color(0xFFB6B6BA)
private val UI_BAR_DARK  = Color(0xFF5C5C61)
private val UI_BTN_TOP    = Color(0xFF6B6B72)
private val UI_BTN_BOTTOM = Color(0xFF3A3A3F)

// Center button red
private val ACCENT_RED = Color(0xFFE53935)

// ---------- Theme / Font options ----------
enum class ColorTheme { Classic, IceBlue, Amber, Mint, Mono }
enum class TimeFont { LCD, Mono, Sans, Serif }   // NEW: Sans, Serif

data class FontSpec(val family: FontFamily, val scale: Float)


private fun fontSpecFor(tf: TimeFont): FontSpec = when (tf) {
    TimeFont.LCD   -> FontSpec(LCD, 1.00f)
    TimeFont.Mono  -> FontSpec(FontFamily.Monospace, 0.82f)   // slightly smaller
    TimeFont.Sans  -> FontSpec(FontFamily.SansSerif, 1.00f)
    TimeFont.Serif -> FontSpec(FontFamily.Serif, 1.02f)
}

private fun ensureDndAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 23) return false
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return if (nm.isNotificationPolicyAccessGranted) {
        true
    } else {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        false
    }
}




data class ClockPalette(
    val bg: Color,
    val active: Color,
    val digit: Color,
    val divider: Color,
    val barLight: Color,
    val barDark: Color,
    val button: Color
)



private fun paletteFor(theme: ColorTheme): ClockPalette = when (theme) {
    ColorTheme.Classic -> ClockPalette(
        bg = Color(0xFF0B0B0D),          // SCREEN_BLACK
        active = Color(0xFF2A2A2D),      // ACTIVE_GRAY
        digit = Color(0xFF39FF14),       // LCD_GREEN
        divider = Color(0xA639FF14),
        barLight = Color(0xFFB6B6BA),
        barDark  = Color(0xFF5C5C61),
        button   = Color(0xFFE53935)     // red
    )
    ColorTheme.IceBlue -> ClockPalette(
        bg = Color(0xFF0A0F1F),
        active = Color(0xFF15233A),
        digit = Color(0xFF48D1FF),
        divider = Color(0xA648D1FF),
        barLight = Color(0xFF9FD2FF),
        barDark  = Color(0xFF406A8A),
        button   = Color(0xFF2196F3)
    )
    ColorTheme.Amber -> ClockPalette(
        bg = Color(0xFF0D0A00),
        active = Color(0xFF2A1F00),
        digit = Color(0xFFFFC107),
        divider = Color(0xA6FFC107),
        barLight = Color(0xFFFFE082),
        barDark  = Color(0xFFFFB300),
        button   = Color(0xFFFF9800)
    )
    ColorTheme.Mint -> ClockPalette(
        bg = Color(0xFF0A1510),
        active = Color(0xFF1B2B22),
        digit = Color(0xFF00E676),
        divider = Color(0xA600E676),
        barLight = Color(0xFFA5D6A7),
        barDark  = Color(0xFF66BB6A),
        button   = Color(0xFF00C853)
    )
    ColorTheme.Mono -> ClockPalette(
        bg = Color(0xFF000000),
        active = Color(0xFF1E1E1E),
        digit = Color(0xFFFFFFFF),
        divider = Color(0x80FFFFFF),
        barLight = Color(0xFFD0D0D0),
        barDark  = Color(0xFF808080),
        button   = Color(0xFF9E9E9E)
    )
}



// LCD font (put lcd.ttf at res/font/lcd.ttf)
private val LCD = FontFamily(Font(R.font.lcd))

// tips pref
private const val PREF_TIPS_SEEN = "clock_tips_seen"
private fun getTipsSeen(ctx: Context) =
    ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE).getBoolean(PREF_TIPS_SEEN, false)
private fun setTipsSeen(ctx: Context, seen: Boolean) {
    ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean(PREF_TIPS_SEEN, seen).apply()
}



// ---------------- Data & Engine ----------------
enum class Side { White, Black }

data class ClockConfig(
    val whiteMinutes: Int,
    val whiteSeconds: Int = 0,
    val whiteIncrement: Int,
    val blackMinutes: Int,
    val blackSeconds: Int = 0,
    val blackIncrement: Int,
    val showTenthsBelowSeconds: Int = 10,
    val theme: ColorTheme = ColorTheme.Classic,   // NEW
    val font: TimeFont = TimeFont.LCD            // NEW
)
data class PlayerCell(
    val remaining: Duration,
    val inc: Duration,
    val moves: Int,
    val flagged: Boolean
)

data class ClockState(
    val white: PlayerCell,
    val black: PlayerCell,
    val activeSide: Side?
) {
    val anyFlagged: Boolean get() = white.flagged || black.flagged

    fun begin(startSide: Side): ClockState =
        if (activeSide != null) this else copy(activeSide = startSide)

    fun onMoveComplete(): ClockState = when (activeSide) {
        Side.White -> copy(
            white = white.copy(remaining = (white.remaining + white.inc), moves = white.moves + 1),
            activeSide = Side.Black
        )
        Side.Black -> copy(
            black = black.copy(remaining = (black.remaining + black.inc), moves = black.moves + 1),
            activeSide = Side.White
        )
        null -> this
    }

    fun tick(delta: Duration): ClockState {
        val a = activeSide ?: return this
        if (anyFlagged) return this

        fun dec(p: PlayerCell): PlayerCell {
            val newRem = p.remaining - delta
            return if (newRem <= Duration.ZERO) p.copy(remaining = Duration.ZERO, flagged = true)
            else p.copy(remaining = newRem)
        }
        return when (a) {
            Side.White -> copy(white = dec(white))
            Side.Black -> copy(black = dec(black))
        }
    }

    companion object {
        fun fromConfig(cfg: ClockConfig): ClockState {
            val w = PlayerCell(
                remaining = cfg.whiteMinutes.minutes + cfg.whiteSeconds.seconds,
                inc = cfg.whiteIncrement.seconds,
                moves = 0,
                flagged = false
            )
            val b = PlayerCell(
                remaining = cfg.blackMinutes.minutes + cfg.blackSeconds.seconds,
                inc = cfg.blackIncrement.seconds,
                moves = 0,
                flagged = false
            )
            return ClockState(white = w, black = b, activeSide = null)
        }
    }

}

// ---------------- Persistence ----------------
private fun loadConfig(ctx: Context): ClockConfig {
    val p = ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)
    return ClockConfig(
        whiteMinutes = p.getInt("wm", 10),
        whiteSeconds = p.getInt("ws", 0),
        whiteIncrement = p.getInt("wi", 0),
        blackMinutes = p.getInt("bm", 10),
        blackSeconds = p.getInt("bs", 0),
        blackIncrement = p.getInt("bi", 0),
        showTenthsBelowSeconds = p.getInt("tenths", 10),
        theme = ColorTheme.values().getOrNull(p.getInt("th", 0)) ?: ColorTheme.Classic,
        font  = TimeFont.values().getOrNull(p.getInt("ff", 0)) ?: TimeFont.LCD
    )
}

private fun saveConfig(ctx: Context, cfg: ClockConfig) {
    ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)
        .edit()
        .putInt("wm", cfg.whiteMinutes)
        .putInt("ws", cfg.whiteSeconds)
        .putInt("wi", cfg.whiteIncrement)
        .putInt("bm", cfg.blackMinutes)
        .putInt("bs", cfg.blackSeconds)
        .putInt("bi", cfg.blackIncrement)
        .putInt("tenths", cfg.showTenthsBelowSeconds)
        .putInt("th", cfg.theme.ordinal)   // NEW
        .putInt("ff", cfg.font.ordinal)    // NEW
        .apply()
}

// ---------------- Clock UI ----------------
@SuppressLint("ContextCastToActivity")
@Composable
fun ClockScreen(onExit: () -> Unit = {}) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val clockPrefs = remember { ClockPrefs(ctx) }

    var dndEnabled by rememberSaveable { mutableStateOf(clockPrefs.dndEnabled) }


    DisposableEffect(dndEnabled) {
        val activity = ctx as Activity
        val prevOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var previousFilter: Int? = null

        if (Build.VERSION.SDK_INT >= 23 && dndEnabled) {
            if (nm.isNotificationPolicyAccessGranted) {
                previousFilter = nm.currentInterruptionFilter
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            } else {
                // Ask the user to grant it (they can come back and toggle again)
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        onDispose {
            if (previousFilter != null) nm.setInterruptionFilter(previousFilter!!)
            activity.requestedOrientation = prevOrientation
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }


    var config by remember { mutableStateOf(loadConfig(ctx)) }
    var state by remember { mutableStateOf(ClockState.fromConfig(config)) }
    var isPaused by remember { mutableStateOf(true) }



    val (whiteBaseMs, blackBaseMs) = remember(
        config.whiteMinutes, config.whiteSeconds,
        config.blackMinutes, config.blackSeconds
    ) {
        (config.whiteMinutes.minutes + config.whiteSeconds.seconds).inWholeMilliseconds to
                (config.blackMinutes.minutes + config.blackSeconds.seconds).inWholeMilliseconds
    }

    var showTips by rememberSaveable { mutableStateOf(!getTipsSeen(ctx)) }
    var showSettings by remember { mutableStateOf(false) }
    var showContextHelp by rememberSaveable { mutableStateOf(false) }

    val palette = remember(config.theme) { paletteFor(config.theme) }
    val fontSpec = remember(config.font) { fontSpecFor(config.font) }           // NEW
    val digitFont = fontSpec.family                                            // NEW
    val digitScale = fontSpec.scale                                            // NEW


    // ticker
    LaunchedEffect(state.activeSide, isPaused, state.white.flagged, state.black.flagged, config) {
        var last = SystemClock.elapsedRealtime()
        while (state.activeSide != null && !isPaused && !state.anyFlagged) {
            val now = SystemClock.elapsedRealtime()
            val deltaMs = (now - last).coerceAtMost(1000L)
            last = now
            state = state.tick(deltaMs.milliseconds)
            delay(40L)
        }
    }

    fun startFromPress(sidePressed: Side) {
        if (state.activeSide == null) {
            val start = if (sidePressed == Side.White) Side.Black else Side.White
            state = state.begin(start)
            isPaused = false
        } else if (!isPaused && !state.anyFlagged && state.activeSide == sidePressed) {
            state = state.onMoveComplete()
        }
    }

    fun resetKeepConfig() {
        state = ClockState.fromConfig(config)
        isPaused = true
    }

    // ------- UI (timers/bars kept exactly like your original)
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        // TOP (Black)
        // TOP
        TimerPad(
            side = Side.Black,
            cell = state.black,
            opponent = state.white,
            showTenthsBelow = config.showTenthsBelowSeconds,
            isActive = state.activeSide == Side.Black && !isPaused && !state.anyFlagged,
            onTap = { startFromPress(Side.Black) },
            barsAtTop = false,
            userBaseMs = blackBaseMs,
            oppBaseMs  = whiteBaseMs,
            fontFamily = digitFont,          // NEW
            palette = palette,               // NEW
            fontScale = digitScale,            // NEW

            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .graphicsLayer(rotationZ = 180f)
        )

// BOTTOM
        TimerPad(
            side = Side.White,
            cell = state.white,
            opponent = state.black,
            showTenthsBelow = config.showTenthsBelowSeconds,
            isActive = state.activeSide == Side.White && !isPaused && !state.anyFlagged,
            onTap = { startFromPress(Side.White) },
            barsAtTop = false,
            userBaseMs = whiteBaseMs,
            oppBaseMs  = blackBaseMs,
            fontFamily = digitFont,          // NEW
            palette = palette,               // NEW
            fontScale = digitScale,                // NEW

            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.5f)
        )



        // Divider (kept green as in your original)
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(palette.divider.copy(alpha = 0.65f))
        )



        // ===== Center red button (short press = play/pause; long press = menu) =====
        var menuOpen by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .align(Alignment.Center), // <- exact center of screen
            contentAlignment = Alignment.Center
        ) {
            GlowPulse(color = palette.button, active = !isPaused && !state.anyFlagged)
            RoundButton(
                glyph = {
                    if (isPaused || state.activeSide == null) {
                        PlayGlyph(color = Color.White)
                    } else {
                        PauseGlyph(color = Color.White)
                    }
                },
                tint = palette.button,

                onTap = {
                    if (state.activeSide == null) {
                        // Start TOP clock when started via the center button
                        state = state.begin(Side.Black)
                        isPaused = false
                    } else if (!state.anyFlagged) {
                        isPaused = !isPaused
                    }
                },
                onDoubleTap = { /* no-op */ },
                onLongPress = { menuOpen = true },
                modifier = Modifier
            )

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        menuOpen = false
                        showSettings = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Restart game") },
                    onClick = {
                        menuOpen = false
                        resetKeepConfig()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Help / Quick guide") },
                    onClick = {
                        menuOpen = false
                        showContextHelp = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Exit") },
                    onClick = {
                        menuOpen = false
                        onExit()
                    }
                )
            }
        }
        // ========================================================================
    }

    if (showTips) {
        ClockTipsDialog(
            onDismiss = { dontShowAgain ->
                if (dontShowAgain) setTipsSeen(ctx, true)
                showTips = false
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            initial = config,
            dndEnabled = dndEnabled,
            onToggleDnd = { enabled ->
                // If turning on, prompt for permission if missing
                if (enabled) ensureDndAccess(ctx)
                dndEnabled = enabled
                clockPrefs.dndEnabled = enabled
            },
            onCancel = { showSettings = false },
            onLetsGo = { newCfg ->
                config = newCfg
                saveConfig(ctx, newCfg)
                state = ClockState.fromConfig(newCfg)
                isPaused = true
                showSettings = false
            },
            onPreset = { m, inc ->
                config = config.copy(
                    whiteMinutes = m, whiteSeconds = 0, whiteIncrement = inc,
                    blackMinutes = m, blackSeconds = 0, blackIncrement = inc
                )
            }
        )
    }

    TrainerFishHelpDialog(
        show = showContextHelp,
        initialTopic = TrainerHelpTopic.CHESS_CLOCK,
        onDismiss = { showContextHelp = false }
    )


}

// ---------------- Timer Pad (kept from your original) ----------------
@Composable
private fun TimerPad(
    side: Side,
    cell: PlayerCell,
    opponent: PlayerCell,
    showTenthsBelow: Int,
    isActive: Boolean,
    onTap: () -> Unit,
    barsAtTop: Boolean,
    userBaseMs: Long,
    oppBaseMs: Long,
    fontFamily: FontFamily,          // NEW
    palette: ClockPalette,           // NEW
    fontScale: Float,                 // <- NEW PARAM

    modifier: Modifier = Modifier
) {
    val baseBg = palette.bg
    val activeBg = UI_GRAY_BG
    val flagBg = MaterialTheme.colorScheme.errorContainer

    val bg by animateColorAsState(
        targetValue = when {
            cell.flagged -> flagBg
            isActive -> activeBg
            else -> baseBg
        },
        label = "pad-bg"
    )

    val timeText = remember(cell.remaining, showTenthsBelow) {
        formatTime(cell.remaining, showTenthsBelow)
    }

    val userBase = userBaseMs.coerceAtLeast(1L).toFloat()
    val oppBase  = oppBaseMs.coerceAtLeast(1L).toFloat()
    val userFill = (cell.remaining.inWholeMilliseconds.toFloat() / userBase).coerceIn(0f, 1f)
    val oppFill  = (opponent.remaining.inWholeMilliseconds.toFloat() / oppBase).coerceIn(0f, 1f)

    val showOppBanner = opponent.remaining < 60.seconds
    val oppTimeText = remember(opponent.remaining, showTenthsBelow) {
        formatTime(opponent.remaining, showTenthsBelow)
    }

    BoxWithConstraints(
        modifier = modifier
            .background(bg)
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        val density = LocalDensity.current
        val targetPx = min(
            with(density) { (maxWidth - 24.dp).toPx() },
            with(density) { (maxHeight - 42.dp).toPx() }
        )
        val fontSize = with(density) { (targetPx * 0.40f * fontScale).toSp() }   // scaled

        Box(Modifier.fillMaxSize()) {
            Text(
                text = timeText,
                color = palette.digit,                 // THEMED
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = fontFamily,          // THEMED
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.align(Alignment.Center)
            )

            val barHeight = 6.dp
            val spacer = 4.dp
            val edgeAlign = if (barsAtTop) Alignment.TopCenter else Alignment.BottomCenter

            Column(
                modifier = Modifier
                    .align(edgeAlign)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                if (showOppBanner) {
                    Text(
                        text = "Opponent's time: $oppTimeText",
                        color = palette.barLight,      // THEMED
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Box(
                    Modifier
                        .fillMaxWidth(userFill)
                        .height(barHeight)
                        .background(palette.barLight)  // THEMED
                )
                Spacer(Modifier.height(spacer))
                Box(
                    Modifier
                        .fillMaxWidth(oppFill)
                        .height(barHeight)
                        .background(palette.barDark)   // THEMED
                )
            }
        }
    }
}



// ---------------- Center Button & Glow ----------------
@Composable
private fun RoundButton(
    glyph: @Composable () -> Unit,
    tint: Color,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (tint == UI_BTN) UI_BTN_TOP else tint.copy(alpha = 1f),
                        tint,
                        if (tint == UI_BTN) UI_BTN_BOTTOM else tint.copy(alpha = 1f)
                    )
                ),
                shape = CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) { glyph() }
}

@Composable
private fun GlowPulse(color: Color, active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "glow")
    val alpha by infinite.animateFloat(
        initialValue = if (active) 0.35f else 0.18f,
        targetValue  = if (active) 0.60f else 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        Modifier
            .size(96.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0f))
                ),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            )
        }
    }
}


// ---------------- Glyphs ----------------
@Composable
private fun PlayGlyph(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension
        val r = s * 0.32f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val tri = Path().apply {
            moveTo(cx - r * 0.45f, cy - r * 0.70f)
            lineTo(cx - r * 0.45f, cy + r * 0.70f)
            lineTo(cx + r * 0.95f, cy)
            close()
        }
        drawPath(tri, color)
    }
}

@Composable
private fun PauseGlyph(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val s  = size.minDimension
        val r  = s * 0.32f
        val iconH = r * 1.20f
        val barW  = r * 0.22f
        val gap   = r * 0.28f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val top = cy - iconH / 2f
        drawRect(color, androidx.compose.ui.geometry.Offset(cx - gap / 2f - barW, top),
            androidx.compose.ui.geometry.Size(barW, iconH))
        drawRect(color, androidx.compose.ui.geometry.Offset(cx + gap / 2f, top),
            androidx.compose.ui.geometry.Size(barW, iconH))
    }
}

// ---------------- Settings ----------------
@Composable
private fun SettingsDialog(
    initial: ClockConfig,
    dndEnabled: Boolean,
    onToggleDnd: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onLetsGo: (ClockConfig) -> Unit,
    onPreset: (mins: Int, inc: Int) -> Unit
)
 {
    var themeSel by remember { mutableStateOf(initial.theme) }
    var fontSel  by remember { mutableStateOf(initial.font) }
    var themeMenu by remember { mutableStateOf(false) }
    var fontMenu  by remember { mutableStateOf(false) }

    var wMin by remember { mutableStateOf(initial.whiteMinutes.toString()) }
    var wSec by remember { mutableStateOf(initial.whiteSeconds.toString()) }
    var wInc by remember { mutableStateOf(initial.whiteIncrement.toString()) }
    var bMin by remember { mutableStateOf(initial.blackMinutes.toString()) }
    var bSec by remember { mutableStateOf(initial.blackSeconds.toString()) }
    var bInc by remember { mutableStateOf(initial.blackIncrement.toString()) }

    fun onlyDigits(s: String) = s.filter(Char::isDigit)
    fun clampSec(v: Int) = v.coerceIn(0, 59)

    fun setBoth(m: Int, inc: Int) {
        onPreset(m, inc)
        wMin = m.toString(); wSec = "0"; wInc = inc.toString()
        bMin = m.toString(); bSec = "0"; bInc = inc.toString()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Trainer Clock Settings") },
        text = {
            BoxWithConstraints {
                val compact = maxWidth < 360.dp
                val fieldPadding = if (compact) 6.dp else 10.dp
                val labelWidth = if (compact) 72.dp else 92.dp
                val fsTitle = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.85f)      // keep dialog within screen
                        .verticalScroll(rememberScrollState()),  // scroll when needed
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
                ) {
                    Text("Presets", style = fsTitle)

                    // scrollable chips so they never overflow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3 to 0, 3 to 2, 5 to 0, 5 to 2).forEach { (m, inc) ->
                            FilterChip(
                                selected = false,
                                onClick = { setBoth(m, inc) },
                                label = { Text("${m}|$inc") }
                            )
                        }
                    }

                    // --- Silence notifications (DND) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Do not Disturb?", modifier = Modifier.width(labelWidth))
                        androidx.compose.material3.Switch(
                            checked = dndEnabled,
                            onCheckedChange = { onToggleDnd(it) }
                        )
                    }


                    // --- Theme picker (row 1) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Theme", modifier = Modifier.width(labelWidth))
                        Box {
                            OutlinedButton(onClick = { themeMenu = true }) { Text(themeSel.name) }
                            DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                                ColorTheme.values().forEach { th ->
                                    DropdownMenuItem(
                                        text = { Text(th.name) },
                                        onClick = { themeSel = th; themeMenu = false }
                                    )
                                }
                            }
                        }
                    }

                    // --- Font picker (row 2) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font", modifier = Modifier.width(labelWidth))
                        Box {
                            OutlinedButton(onClick = { fontMenu = true }) { Text(fontSel.name) }
                            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                                listOf(TimeFont.LCD, TimeFont.Mono, TimeFont.Sans, TimeFont.Serif).forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(f.name) },
                                        onClick = { fontSel = f; fontMenu = false }
                                    )
                                }
                            }
                        }
                    }


                    Spacer(Modifier.height(4.dp))
                    Text("Custom (time odds supported)", style = fsTitle)

                    // WHITE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("White", modifier = Modifier.width(labelWidth))
                        OutlinedTextField(
                            value = wMin, onValueChange = { wMin = onlyDigits(it) },
                            label = { Text("min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = fieldPadding)
                                .defaultMinSize(minWidth = 68.dp)
                        )
                        OutlinedTextField(
                            value = wSec, onValueChange = { wSec = onlyDigits(it) },
                            label = { Text("sec") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = fieldPadding)
                                .defaultMinSize(minWidth = 68.dp)
                        )
                        OutlinedTextField(
                            value = wInc, onValueChange = { wInc = onlyDigits(it) },
                            label = { Text("inc") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 68.dp)
                        )
                    }

                    // BLACK
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Black", modifier = Modifier.width(labelWidth))
                        OutlinedTextField(
                            value = bMin, onValueChange = { bMin = onlyDigits(it) },
                            label = { Text("min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = fieldPadding)
                                .defaultMinSize(minWidth = 68.dp)
                        )
                        OutlinedTextField(
                            value = bSec, onValueChange = { bSec = onlyDigits(it) },
                            label = { Text("sec") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = fieldPadding)
                                .defaultMinSize(minWidth = 68.dp)
                        )
                        OutlinedTextField(
                            value = bInc, onValueChange = { bInc = onlyDigits(it) },
                            label = { Text("inc") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 68.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wm = wMin.toIntOrNull() ?: initial.whiteMinutes
                    val ws = clampSec(wSec.toIntOrNull() ?: initial.whiteSeconds)
                    val wi = wInc.toIntOrNull() ?: initial.whiteIncrement
                    val bm = bMin.toIntOrNull() ?: initial.blackMinutes
                    val bs = clampSec(bSec.toIntOrNull() ?: initial.blackSeconds)
                    val bi = bInc.toIntOrNull() ?: initial.blackIncrement

                    onLetsGo(
                        initial.copy(
                            whiteMinutes = wm, whiteSeconds = ws, whiteIncrement = wi,
                            blackMinutes = bm, blackSeconds = bs, blackIncrement = bi,
                            theme = themeSel,          // NEW
                            font  = fontSel            // NEW
                        )
                    )

                },
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE)
            ) { Text("Let's go!") }
            Spacer(Modifier.height(6.dp))
            CopyrightLine()

        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }


    )
}



// ---------------- helpers ----------------
private fun formatTime(rem: Duration, tenthsBelow: Int): String {
    val totalMs = rem.inWholeMilliseconds.coerceAtLeast(0)
    val totalSec = totalMs / 1000
    val m = (totalSec / 60).toInt()
    val s = (totalSec % 60).toInt()
    return if (totalSec < tenthsBelow) {
        val tenths = ((totalMs % 1000) / 100).toInt()
        "%d:%02d.%d".format(m, s, tenths)
    } else "%d:%02d".format(m, s)
}

@Composable
private fun ClockTipsDialog(onDismiss: (dontShowAgain: Boolean) -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        title = { Text("Chess Clock - Quick Tips", color = UI_ICON) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("- Tap a player's pad after each move to switch turns (increment is applied).", color = UI_ICON)
                Text("- Short-press the CENTER red button to Play/Pause.", color = UI_ICON)
                Text("- At the start, the center button starts the TOP clock.", color = UI_ICON)
                Text("- Long-press the CENTER red button for menu: Settings / Restart / Exit.", color = UI_ICON)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dontShow,
                        onCheckedChange = { dontShow = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = UI_BTN,
                            uncheckedColor = UI_ICON,
                            checkmarkColor = SCREEN_BLACK
                        )
                    )
                    Text("Don't show this again", color = UI_ICON)
                    Spacer(Modifier.height(6.dp))
                    CopyrightLine()

                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDismiss(dontShow) },
                colors = ButtonDefaults.buttonColors(containerColor = UI_BTN),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Got it", color = UI_ICON) }
        },
        dismissButton = { TextButton(onClick = { onDismiss(false) }) { Text("Close", color = UI_ICON) } },
        containerColor = UI_GRAY_BG,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun CopyrightLine(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = "© 2025 Cliburn Anthony A. Orbe",
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
