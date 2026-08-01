package com.tonorbe.trainerfish

import androidx.compose.foundation.Image
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tonorbe.trainerfish.playgames.TrainerFishPlayGamesUiState

private data class LandingTile(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val mode: TrainerMode?,
    val colors: List<Color>,
    val actionLabel: String = "Start"
)

private data class TrainerAppPromo(
    val title: String,
    val subtitle: String,
    val note: String,
    val assetName: String,
    val url: String,
    val colors: List<Color>
)

private data class VisualBoardOption(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color
)

private data class VisualUiBoxOption(
    val key: String,
    val label: String,
    val colors: List<Color>
)

private val LandingBoardOptions = listOf(
    VisualBoardOption("classic", "Classic", Color(0xFFEEEED2), Color(0xFF8FB06B)),
    VisualBoardOption("gray", "Gray", Color(0xFFBDBDBD), Color(0xFF777777)),
    VisualBoardOption("wood", "Wood", Color(0xFFEED8B0), Color(0xFFA98272)),
    VisualBoardOption("night", "Night", Color(0xFFB0BEC5), Color(0xFF607D8B)),
    VisualBoardOption("blue", "Blue", Color(0xFFBBDEFB), Color(0xFF4F8FEF)),
    VisualBoardOption("sand", "Sand", Color(0xFFF5E0C3), Color(0xFFD9A65C)),
    VisualBoardOption("forest", "Forest", Color(0xFFC8E6C9), Color(0xFF5FA463)),
    VisualBoardOption("purple", "Purple", Color(0xFFEDE9FE), Color(0xFF8B5CF6)),
    VisualBoardOption("coffee", "Coffee", Color(0xFFE7D3B0), Color(0xFF8B5E3C)),
    VisualBoardOption("olive", "Olive", Color(0xFFDDE5B6), Color(0xFF738A3D)),
    VisualBoardOption("ice", "Ice", Color(0xFFE0F7FA), Color(0xFF00ACC1)),
    VisualBoardOption("rosewood", "Rosewood", Color(0xFFFADADD), Color(0xFFB56576))
)

private val LandingUiBoxOptions = listOf(
    VisualUiBoxOption("sunset", "Sunset", listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFF7C3AED))),
    VisualUiBoxOption("ocean", "Ocean", listOf(Color(0xFF0F172A), Color(0xFF2563EB), Color(0xFF06B6D4))),
    VisualUiBoxOption("royal", "Royal", listOf(Color(0xFF312E81), Color(0xFF7C3AED), Color(0xFFDB2777))),
    VisualUiBoxOption("emerald", "Emerald", listOf(Color(0xFF064E3B), Color(0xFF059669), Color(0xFF14B8A6))),
    VisualUiBoxOption("slate", "Slate", listOf(Color(0xFF020617), Color(0xFF1E293B), Color(0xFF334155))),
    VisualUiBoxOption("rose", "Rose", listOf(Color(0xFF881337), Color(0xFFDB2777), Color(0xFF7C3AED))),
    VisualUiBoxOption("azure", "Azure", listOf(Color(0xFF0C4A6E), Color(0xFF0284C7), Color(0xFF38BDF8))),
    VisualUiBoxOption("walnut", "Walnut", listOf(Color(0xFF3F2A1D), Color(0xFF7C4A2D), Color(0xFFB7794A))),
    VisualUiBoxOption("moss", "Moss", listOf(Color(0xFF132A13), Color(0xFF31572C), Color(0xFF4F772D))),
    VisualUiBoxOption("graphite", "Graphite", listOf(Color(0xFF111827), Color(0xFF374151), Color(0xFF6B7280)))
)

@Composable
fun TrainerFishLandingScreen(
    appThemeKey: String,
    onChangeAppThemeKey: (String) -> Unit,
    onSelectMode: (TrainerMode) -> Unit,
    onClock: () -> Unit = {},
    playGamesState: TrainerFishPlayGamesUiState = TrainerFishPlayGamesUiState(),
    proUnlocked: Boolean = false,
    onOpenLeaderboards: () -> Unit = {},
    onWatchLichessTv: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val cosSp = remember { ctx.getSharedPreferences("gm_cosmetics", android.content.Context.MODE_PRIVATE) }

    var showVisualStudio by remember { mutableStateOf(false) }
    var selectedPieceSet by rememberSaveable {
        mutableStateOf(cosSp.getString("piece_set", "original") ?: "original")
    }
    var selectedBoardTheme by rememberSaveable {
        mutableStateOf(cosSp.getString("board_theme", "classic") ?: "classic")
    }
    var selectedUiBoxTheme by rememberSaveable {
        mutableStateOf(cosSp.getString("ui_box_theme", "sunset") ?: "sunset")
    }

    fun setPieceSet(key: String) {
        selectedPieceSet = key
        cosSp.edit().putString("piece_set", key).apply()
    }

    fun setBoardTheme(key: String) {
        selectedBoardTheme = key
        cosSp.edit().putString("board_theme", key).apply()
    }

    fun setUiBoxTheme(key: String) {
        selectedUiBoxTheme = key
        cosSp.edit().putString("ui_box_theme", key).apply()
    }

    val isDark = appThemeKey == AppThemeKeys.DARK
    val bg = if (isDark) {
        Brush.verticalGradient(
            listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF111827))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFE0F2FE), Color(0xFFF8FAFC), Color(0xFFFFF7ED))
        )
    }

    val tiles = remember {
        listOf(
            LandingTile(
                title = "Tactics",
                subtitle = "Woodpecker cycles and puzzle training",
                emoji = "\uD83C\uDFAF",
                mode = TrainerMode.WOODPECKER,
                colors = listOf(Color(0xFFEF4444), Color(0xFFF97316))
            ),
            LandingTile(
                title = "Endgame",
                subtitle = "Lucena, Philidor, technique drills",
                emoji = "🏁",
                mode = TrainerMode.ENDGAME,
                colors = listOf(Color(0xFF10B981), Color(0xFF059669))
            ),
            LandingTile(
                title = "Opening",
                subtitle = "Grandmaster tree + engine check",
                emoji = "📖",
                mode = TrainerMode.OPENING,
                colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
            ),
            LandingTile(
                title = "Beat the Fish",
                subtitle = "Play, annotate, analyze",
                emoji = "\uD83D\uDC1F",
                mode = TrainerMode.BEAT_FISH,
                colors = listOf(Color(0xFF06B6D4), Color(0xFF0F766E))
            ),
            LandingTile(
                title = "Grandmaster Chess TV",
                subtitle = "Live elite games with engine analysis",
                emoji = "📺",
                mode = null,
                colors = listOf(Color(0xFF629924), Color(0xFF2D5F16), Color(0xFF111827)),
                actionLabel = "Watch"
            ),
            LandingTile(
                title = "Game Recorder",
                subtitle = "Record OTB games, then annotate",
                emoji = "✍️",
                mode = TrainerMode.GAME_RECORDER,
                colors = listOf(Color(0xFFF59E0B), Color(0xFFB45309))
            ),
            LandingTile(
                title = "PGN Reader",
                subtitle = "Read books, games, comments, variations",
                emoji = "📚",
                mode = TrainerMode.PGN,
                colors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
            ),
            LandingTile(
                title = "Chess Clock",
                subtitle = "Tournament-ready time control",
                emoji = "⏱️",
                mode = null,
                colors = listOf(Color(0xFF475569), Color(0xFF111827)),
                actionLabel = "Open"
            ),
            LandingTile(
                title = "Visual Studio",
                subtitle = "Pieces, board colors, light/dark",
                emoji = "🎨",
                mode = null,
                colors = listOf(Color(0xFFEC4899), Color(0xFF7C3AED)),
                actionLabel = "Style"
            )
        )
    }

    val promos = remember {
        listOf(
            TrainerAppPromo(
                title = "Trainer Word",
                subtitle = "Chess meets Word Factory! Connect letters using chess moves to form words and check their definitions.",
                note = "It's completely free with no ads!",
                assetName = "tw.png",
                url = "https://play.google.com/store/apps/details?id=com.tonorbe.trainerword",
                colors = listOf(Color(0xFF111827), Color(0xFF27272A), Color(0xFFB45309))
            ),
            TrainerAppPromo(
                title = "Chess Openings Coach",
                subtitle = "14.5 million opening positions, 1 million games database, free to download.",
                note = "Free to download and use with 1 item for one-time purchase lifetime unlock.",
                assetName = "coc.png",
                url = "https://play.google.com/store/apps/details?id=com.tonorbe.chessopeningscoach",
                colors = listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFFCA8A04))
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            val twoColumns = maxWidth >= 360.dp
            val compact = maxWidth < 420.dp

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LandingHeader(
                    isDark = isDark,
                    onToggleTheme = {
                        onChangeAppThemeKey(if (isDark) AppThemeKeys.LIGHT else AppThemeKeys.DARK)
                    },
                    onClock = onClock
                )

                Spacer(Modifier.height(14.dp))

                TrainerFishLeaderboardCard(
                    state = playGamesState,
                    proUnlocked = proUnlocked,
                    onClick = onOpenLeaderboards,
                    modifier = Modifier.fillMaxWidth()
                )

                playGamesState.statusMessage?.let { message ->
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(18.dp))

                if (twoColumns) {
                    tiles.chunked(2).forEach { rowTiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowTiles.forEach { tile ->
                                LandingModeTile(
                                    tile = tile,
                                    compact = compact,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (tile.title) {
                                            "Chess Clock" -> onClock()
                                            "Visual Studio" -> showVisualStudio = true
                                            "Grandmaster Chess TV" -> onWatchLichessTv()
                                            else -> tile.mode?.let(onSelectMode)
                                        }
                                    }
                                )
                            }
                            if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    tiles.forEach { tile ->
                        LandingModeTile(
                            tile = tile,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                when (tile.title) {
                                    "Chess Clock" -> onClock()
                                    "Visual Studio" -> showVisualStudio = true
                                    "Grandmaster Chess TV" -> onWatchLichessTv()
                                    else -> tile.mode?.let(onSelectMode)
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Text(
                    text = "Choose a training room and start improving.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Text(
                    text = "Enjoy these other apps from Trainer Apps:",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp)
                )

                promos.forEach { promo ->
                    TrainerAppPromoCard(
                        promo = promo,
                        compact = compact,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (showVisualStudio) {
        VisualStudioDialog(
            currentPieceSet = selectedPieceSet,
            currentBoardTheme = selectedBoardTheme,
            currentUiBoxTheme = selectedUiBoxTheme,
            appThemeKey = appThemeKey,
            onPieceSet = ::setPieceSet,
            onBoardTheme = ::setBoardTheme,
            onUiBoxTheme = ::setUiBoxTheme,
            onAppTheme = onChangeAppThemeKey,
            onClose = { showVisualStudio = false }
        )
    }

}

@Composable
private fun TrainerFishLeaderboardCard(
    state: TrainerFishPlayGamesUiState,
    proUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = when {
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
        state.isAuthenticated -> {
            val player = state.playerName?.takeIf { it.isNotBlank() } ?: "Play Games player"
            "Signed in as $player • Compare your training records"
        }
        else -> "Sign in to compare ELO, puzzle records, streaks, and cycles"
    }
    val action = when {
        !state.isConfigured -> "Setup"
        !proUnlocked && state.isChecking -> "Wait"
        !proUnlocked -> "View"
        state.isChecking -> "Wait"
        state.isAuthenticated -> "View"
        else -> "Sign in"
    }

    Surface(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = !state.isChecking, onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFFF59E0B))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    if (state.playerIconUri != null) {
                        AsyncImage(
                            model = state.playerIconUri,
                            contentDescription = "Google Play Games player",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "🏆", fontSize = 28.sp)
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TrainerFish Leaderboards",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.84f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = action,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LandingHeader(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onClock: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = Color.White.copy(alpha = if (isDark) 0.10f else 0.90f),
                modifier = Modifier.size(62.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_fish),
                    contentDescription = "TrainerFish",
                    modifier = Modifier.padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = "TrainerFish",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Chess training academy",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallLandingButton(text = "⏱", onClick = onClock)
            SmallLandingButton(text = if (isDark) "☀" else "🌙", onClick = onToggleTheme)
        }
    }
}

@Composable
private fun SmallLandingButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 22.sp)
        }
    }
}

@Composable
private fun LandingModeTile(
    tile: LandingTile,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(if (compact) 142.dp else 158.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(tile.colors))
                .padding(14.dp)
        ) {
            Text(
                text = tile.emoji,
                fontSize = if (compact) 34.sp else 40.sp,
                modifier = Modifier.align(Alignment.TopStart)
            )

            Text(
                text = tile.actionLabel,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = tile.title,
                    color = Color.White,
                    fontSize = if (compact) 20.sp else 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tile.subtitle,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = if (compact) 15.sp else 17.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun TrainerAppPromoCard(
    promo: TrainerAppPromo,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    val veryCompact = compact

    Surface(
        modifier = modifier
            .height(if (veryCompact) 168.dp else 162.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(promo.url))
                    )
                }
            },
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(promo.colors))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(if (veryCompact) 54.dp else 78.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    shadowElevation = 6.dp
                ) {
                    AsyncImage(
                        model = "file:///android_asset/${promo.assetName}",
                        contentDescription = promo.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                Spacer(Modifier.width(if (veryCompact) 10.dp else 12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = promo.title,
                        color = Color.White,
                        fontSize = if (veryCompact) 18.sp else 24.sp,
                        lineHeight = if (veryCompact) 21.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = if (veryCompact) 2 else 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = promo.subtitle,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = if (veryCompact) 10.sp else 13.sp,
                        lineHeight = if (veryCompact) 13.sp else 16.sp,
                        maxLines = if (veryCompact) 3 else 3
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = promo.note,
                        color = Color(0xFFFFF7CC),
                        fontSize = if (veryCompact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = if (veryCompact) 13.sp else 15.sp,
                        maxLines = if (veryCompact) 3 else 2
                    )
                }

                if (!veryCompact) {
                    Text(
                        text = "Play Store ›",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Bottom)
                            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualStudioDialog(
    currentPieceSet: String,
    currentBoardTheme: String,
    currentUiBoxTheme: String,
    appThemeKey: String,
    onPieceSet: (String) -> Unit,
    onBoardTheme: (String) -> Unit,
    onUiBoxTheme: (String) -> Unit,
    onAppTheme: (String) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "Visual Studio",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Choose your pieces, board colors, pane colors, and app background.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(ctx) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show puzzle notification")
                }

                Spacer(Modifier.height(14.dp))

                VisualSectionTitle("Piece sets")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrainerFishPieceSets.forEach { option ->
                        PieceSetStudioCard(
                            keyName = option.key,
                            label = option.label,
                            selected = currentPieceSet == option.key,
                            onClick = { onPieceSet(option.key) }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                VisualSectionTitle("Board colors")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LandingBoardOptions.forEach { option ->
                        BoardThemeStudioCard(
                            option = option,
                            selected = currentBoardTheme == option.key,
                            pieceSetKey = currentPieceSet,
                            onClick = { onBoardTheme(option.key) }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                VisualSectionTitle("Pane colors")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LandingUiBoxOptions.forEach { option ->
                        UiBoxThemeStudioCard(
                            option = option,
                            selected = currentUiBoxTheme == option.key,
                            onClick = { onUiBoxTheme(option.key) }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                VisualSectionTitle("Background")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BackgroundStudioCard(
                        label = "Light",
                        emoji = "☀️",
                        selected = appThemeKey == AppThemeKeys.LIGHT,
                        colors = listOf(Color(0xFFE0F2FE), Color(0xFFFFF7ED)),
                        onClick = { onAppTheme(AppThemeKeys.LIGHT) }
                    )
                    BackgroundStudioCard(
                        label = "Dark",
                        emoji = "🌙",
                        selected = appThemeKey == AppThemeKeys.DARK,
                        colors = listOf(Color(0xFF020617), Color(0xFF111827)),
                        onClick = { onAppTheme(AppThemeKeys.DARK) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Done") }
        }
    )
}

@Composable
private fun VisualSectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun PieceSetStudioCard(
    keyName: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    StudioSelectableSurface(
        selected = selected,
        width = 132,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF8FAFC), Color(0xFFE5E7EB))
                    )
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioPieceSample(
                piece = Piece(PieceType.KING, true),
                pieceSetKey = keyName,
                onClick = onClick
            )
            StudioPieceSample(
                piece = Piece(PieceType.PAWN, false),
                pieceSetKey = keyName,
                onClick = onClick
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioPieceSample(
    piece: Piece,
    pieceSetKey: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.90f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            DrawPiece(
                p = piece,
                style = PieceStyle.Solid,
                pieceSetKey = pieceSetKey
            )
        }
    }
}

@Composable
private fun BoardThemeStudioCard(
    option: VisualBoardOption,
    selected: Boolean,
    pieceSetKey: String,
    onClick: () -> Unit
) {
    StudioSelectableSurface(
        selected = selected,
        width = 132,
        onClick = onClick
    ) {
        val preview = remember {
            Array<Piece?>(64) { null }.also { b ->
                b[4] = Piece(PieceType.KING, true)
                b[12] = Piece(PieceType.PAWN, true)
                b[60] = Piece(PieceType.KING, false)
                b[52] = Piece(PieceType.PAWN, false)
            }
        }
        ChessBoard(
            board = preview,
            selected = null,
            onSquareClick = { onClick() },
            light = option.light,
            dark = option.dark,
            pieceStyle = PieceStyle.Solid,
            pieceSetKey = pieceSetKey,
            whiteBottom = true
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UiBoxThemeStudioCard(
    option: VisualUiBoxOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    StudioSelectableSurface(
        selected = selected,
        width = 132,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(option.colors)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Pane",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BackgroundStudioCard(
    label: String,
    emoji: String,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit
) {
    StudioSelectableSurface(
        selected = selected,
        width = 132,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 34.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioSelectableSurface(
    selected: Boolean,
    width: Int,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Surface(
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.95f else 0.68f),
        tonalElevation = if (selected) 6.dp else 2.dp,
        shadowElevation = if (selected) 8.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}
