package com.tonorbe.trainerfish

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.github.bhlangonijr.chesslib.PieceType as LibPieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move as LibMove
import com.tonorbe.trainerfish.engine.ProcEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val MAX_BROADCAST_GAMES = 8

@Composable
internal fun LichessTvScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val client = remember { LichessTvClient() }
    val broadcastApi = remember { LichessBroadcastApi() }
    val screenScope = rememberCoroutineScope()
    val tv by client.state.collectAsState()
    val engineCpRaw by ProcEngine.scoreCp.collectAsState()
    val engineLines by ProcEngine.lines.collectAsState()

    var detached by rememberSaveable { mutableStateOf(false) }
    var engineEnabled by rememberSaveable { mutableStateOf(true) }
    var whiteBottom by rememberSaveable { mutableStateOf(true) }
    var showWatchPlayerDialog by rememberSaveable { mutableStateOf(false) }
    var playerUsername by rememberSaveable { mutableStateOf("") }
    var showBroadcastDialog by rememberSaveable { mutableStateOf(false) }
    var resumeLiveAfterBroadcastDialog by rememberSaveable { mutableStateOf(false) }
    var broadcastLoading by remember { mutableStateOf(false) }
    var broadcastError by remember { mutableStateOf<String?>(null) }
    var liveBroadcasts by remember { mutableStateOf<List<LichessBroadcastPreview>>(emptyList()) }
    var selectedBroadcastRound by remember { mutableStateOf<LichessBroadcastRound?>(null) }
    var selectedBroadcastGames by remember {
        mutableStateOf<List<LichessBroadcastSelection>>(emptyList())
    }
    var activeBroadcastIndex by remember { mutableStateOf(0) }
    var pendingBroadcastGames by remember {
        mutableStateOf<List<LichessBroadcastSelection>>(emptyList())
    }
    var broadcastSelectionMessage by remember { mutableStateOf<String?>(null) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var resumeLiveAfterHelpDialog by rememberSaveable { mutableStateOf(false) }
    var analysisStartFen by remember { mutableStateOf(LICHESS_TV_START_FEN) }
    var analysisFen by remember { mutableStateOf(LICHESS_TV_START_FEN) }
    var analysisUciMoves by remember { mutableStateOf<List<String>>(emptyList()) }
    var analysisSanMoves by remember { mutableStateOf<List<String>>(emptyList()) }
    var analysisPly by remember { mutableStateOf(0) }
    var analysisSourceGameId by remember { mutableStateOf<String?>(null) }
    var analysisWasEdited by remember { mutableStateOf(false) }
    var selectedSquare by remember { mutableStateOf<Int?>(null) }
    var promotionChoices by remember { mutableStateOf<List<LibMove>>(emptyList()) }

    val cosmetics = remember {
        context.getSharedPreferences("gm_cosmetics", android.content.Context.MODE_PRIVATE)
    }
    val pieceSet = cosmetics.getString("piece_set", "original") ?: "original"
    val boardTheme = cosmetics.getString("board_theme", "classic") ?: "classic"
    val (lightSquare, darkSquare) = remember(boardTheme) { lichessTvBoardColors(boardTheme) }

    fun positionAt(startFen: String, moves: List<String>, ply: Int): String? {
        val board = runCatching { Board().apply { loadFromFen(startFen) } }.getOrNull() ?: return null
        for (moveUci in moves.take(ply.coerceIn(0, moves.size))) {
            val move = bfUciToMoveOnBoard(board, moveUci) ?: return null
            board.doMove(move)
        }
        return board.fen
    }

    fun enterAnalysis(targetPly: Int = tv.uciMoves.size) {
        client.detachForAnalysis()
        detached = true
        selectedSquare = null
        analysisSourceGameId = tv.gameId
        analysisWasEdited = false
        analysisStartFen = tv.startFen
        analysisUciMoves = tv.uciMoves
        analysisSanMoves = tv.sanMoves
        analysisPly = targetPly.coerceIn(0, tv.uciMoves.size)
        analysisFen = positionAt(tv.startFen, tv.uciMoves, analysisPly)
            ?: if (analysisPly == tv.uciMoves.size) tv.fen else tv.startFen
    }

    fun navigateToPly(targetPly: Int) {
        if (!detached) {
            enterAnalysis(targetPly)
            return
        }
        val safePly = targetPly.coerceIn(0, analysisUciMoves.size)
        val fen = positionAt(analysisStartFen, analysisUciMoves, safePly) ?: return
        analysisPly = safePly
        analysisFen = fen
        selectedSquare = null
    }

    fun reconnect() {
        detached = false
        selectedSquare = null
        promotionChoices = emptyList()
        client.reconnect()
    }

    fun watchPlayer() {
        val username = playerUsername.trim().removePrefix("@").trim()
        if (username.isBlank()) return
        detached = false
        selectedSquare = null
        promotionChoices = emptyList()
        showWatchPlayerDialog = false
        client.watchPlayer(username)
    }

    fun showTopGame() {
        detached = false
        selectedSquare = null
        promotionChoices = emptyList()
        client.connectTopGame()
    }

    fun refreshBroadcasts() {
        selectedBroadcastRound = null
        broadcastSelectionMessage = null
        broadcastLoading = true
        broadcastError = null
        screenScope.launch {
            runCatching { broadcastApi.loadLiveBroadcasts() }
                .onSuccess { liveBroadcasts = it }
                .onFailure {
                    liveBroadcasts = emptyList()
                    broadcastError = it.message ?: "Could not load live tournament broadcasts."
                }
            broadcastLoading = false
        }
    }

    fun openBroadcasts() {
        resumeLiveAfterBroadcastDialog = !detached
        if (resumeLiveAfterBroadcastDialog) client.pauseForDialog()
        // Stop immediately; the engine effect below also waits for bestmove
        // before clearing output. No live position can change behind the dialog.
        ProcEngine.send("stop")
        pendingBroadcastGames = selectedBroadcastGames
        showBroadcastDialog = true
        refreshBroadcasts()
    }

    fun closeBroadcasts() {
        showBroadcastDialog = false
        val shouldResumeLive = resumeLiveAfterBroadcastDialog
        resumeLiveAfterBroadcastDialog = false
        if (shouldResumeLive) client.reconnect()
    }

    fun openHelp() {
        resumeLiveAfterHelpDialog = !detached
        if (resumeLiveAfterHelpDialog) client.pauseForDialog()
        ProcEngine.send("stop")
        showHelpDialog = true
    }

    fun closeHelp() {
        showHelpDialog = false
        val shouldResumeLive = resumeLiveAfterHelpDialog
        resumeLiveAfterHelpDialog = false
        if (shouldResumeLive) client.reconnect()
    }

    fun openBroadcastRound(preview: LichessBroadcastPreview) {
        broadcastLoading = true
        broadcastError = null
        screenScope.launch {
            runCatching { broadcastApi.loadRound(preview) }
                .onSuccess { round ->
                    selectedBroadcastRound = round
                    broadcastSelectionMessage = null
                }
                .onFailure {
                    broadcastError = it.message ?: "Could not load the tournament boards."
                }
            broadcastLoading = false
        }
    }

    fun toggleBroadcastBoard(board: LichessBroadcastBoard) {
        val round = selectedBroadcastRound ?: return
        val existingIndex = pendingBroadcastGames.indexOfFirst {
            it.roundId == round.preview.roundId && it.gameId == board.gameId
        }
        pendingBroadcastGames = when {
            existingIndex >= 0 -> {
                broadcastSelectionMessage = null
                pendingBroadcastGames.filterIndexed { index, _ -> index != existingIndex }
            }
            pendingBroadcastGames.size < MAX_BROADCAST_GAMES -> {
                broadcastSelectionMessage = null
                pendingBroadcastGames + round.selectionFor(board)
            }
            else -> {
                broadcastSelectionMessage = "You can choose up to $MAX_BROADCAST_GAMES games."
                pendingBroadcastGames
            }
        }
    }

    fun watchSelectedBroadcasts() {
        val selections = pendingBroadcastGames
        if (selections.isEmpty()) return

        val currentGameId = tv.gameId.takeIf { tv.source == LichessTvSource.BROADCAST_BOARD }
        val restoredIndex = selections.indexOfFirst { it.gameId == currentGameId }
        activeBroadcastIndex = restoredIndex.takeIf { it >= 0 } ?: 0
        selectedBroadcastGames = selections
        detached = false
        selectedSquare = null
        promotionChoices = emptyList()
        resumeLiveAfterBroadcastDialog = false
        showBroadcastDialog = false
        client.watchBroadcastBoard(selections[activeBroadcastIndex])
    }

    fun switchBroadcastGame(direction: Int) {
        if (
            detached ||
            tv.source != LichessTvSource.BROADCAST_BOARD ||
            selectedBroadcastGames.size < 2
        ) return

        val nextIndex = Math.floorMod(
            activeBroadcastIndex + direction,
            selectedBroadcastGames.size
        )
        if (nextIndex == activeBroadcastIndex) return
        activeBroadcastIndex = nextIndex
        selectedSquare = null
        promotionChoices = emptyList()
        client.watchBroadcastBoard(selectedBroadcastGames[nextIndex])
    }

    fun applyAnalysisMove(move: LibMove) {
        val board = runCatching { Board().apply { loadFromFen(analysisFen) } }.getOrNull() ?: return
        val isWhiteMove = board.sideToMove == Side.WHITE
        val san = runCatching { bfPrettySan(board, move, isWhiteMove) }
            .getOrElse { lichessTvMoveToUci(move) }
        val uci = lichessTvMoveToUci(move)
        if (!board.doMove(move)) return

        analysisUciMoves = analysisUciMoves.take(analysisPly) + uci
        analysisSanMoves = analysisSanMoves.take(analysisPly) + san
        analysisWasEdited = true
        analysisPly += 1
        analysisFen = board.fen
        selectedSquare = null
        promotionChoices = emptyList()
    }

    fun onBoardSquare(displayIndex: Int) {
        if (!detached) {
            enterAnalysis(tv.uciMoves.size)
            return
        }

        val logicalIndex = if (whiteBottom) displayIndex else 63 - displayIndex
        val board = runCatching { Board().apply { loadFromFen(analysisFen) } }.getOrNull() ?: return
        val selected = selectedSquare
        if (selected == null) {
            val square = lichessTvIndexToSquare(logicalIndex)
            val piece = board.getPiece(square)
            if (piece.pieceSide == board.sideToMove) selectedSquare = logicalIndex
            return
        }

        val from = lichessTvIndexToSquare(selected)
        val to = lichessTvIndexToSquare(logicalIndex)
        val candidates = MoveGenerator.generateLegalMoves(board).filter { it.from == from && it.to == to }
        when {
            candidates.size == 1 -> applyAnalysisMove(candidates.first())
            candidates.size > 1 -> promotionChoices = candidates
            else -> {
                val clickedPiece = board.getPiece(to)
                selectedSquare = if (clickedPiece.pieceSide == board.sideToMove) logicalIndex else null
            }
        }
    }

    DisposableEffect(client) {
        client.connect()
        onDispose {
            client.close()
            ProcEngine.send("stop")
            ProcEngine.clearOutput()
        }
    }

    LaunchedEffect(tv.gameId) {
        if (!detached && tv.gameId != null) whiteBottom = tv.orientationWhite
    }

    LaunchedEffect(detached, tv.pgnLoaded, tv.gameId, tv.uciMoves) {
        if (
            detached &&
            !analysisWasEdited &&
            tv.pgnLoaded &&
            tv.gameId == analysisSourceGameId &&
            tv.uciMoves.isNotEmpty() &&
            tv.uciMoves.size >= analysisUciMoves.size
        ) {
            analysisStartFen = tv.startFen
            analysisUciMoves = tv.uciMoves
            analysisSanMoves = tv.sanMoves
            analysisPly = tv.uciMoves.size
            analysisFen = positionAt(tv.startFen, tv.uciMoves, tv.uciMoves.size) ?: analysisFen
        }
    }

    val watchedPlayerEngineLocked =
        tv.source == LichessTvSource.WATCHED_PLAYER && tv.watchedGameOngoing
    val effectiveEngineEnabled =
        engineEnabled && !watchedPlayerEngineLocked && !showBroadcastDialog && !showHelpDialog
    val useDeepBroadcastAnalysis = tv.source == LichessTvSource.BROADCAST_BOARD
    val displayedFen = if (detached) analysisFen else tv.fen
    LaunchedEffect(effectiveEngineEnabled, useDeepBroadcastAnalysis, displayedFen) {
        if (!effectiveEngineEnabled) {
            ProcEngine.stopSearchAndWait()
            ProcEngine.clearOutput()
            return@LaunchedEffect
        }
        runCatching { ProcEngine.start("lichess-tv") }
        delay(80L)
        if (useDeepBroadcastAnalysis) {
            ProcEngine.evaluateFenDepthSafely(displayedFen, 50, multiPv = 1)
        } else {
            ProcEngine.evaluateFenSafely(displayedFen, 1_200, multiPv = 1)
        }
    }

    val whiteToMove = displayedFen.split(' ').getOrNull(1) != "b"
    val engineCpWhite = engineCpRaw?.let { if (whiteToMove) it else -it }
    val latestInfo = engineLines.asReversed().firstOrNull { line ->
        line.startsWith("info ") && line.contains(" score ") &&
            (!line.contains(" multipv ") || line.contains(" multipv 1 "))
    }
    val engineEvaluation = when {
        watchedPlayerEngineLocked -> "Locked"
        effectiveEngineEnabled -> lichessTvEvaluationText(latestInfo, whiteToMove)
        else -> "Off"
    }
    val enginePv = when {
        watchedPlayerEngineLocked -> "Engine disabled while watching a specific live player."
        effectiveEngineEnabled -> lichessTvPvText(displayedFen, latestInfo)
        else -> "Engine analysis is off."
    }

    val basePieces = remember(displayedFen) {
        runCatching {
            Board().apply { loadFromFen(displayedFen) }.let(::boardToUiPieces)
        }.getOrElse { arrayOfNulls<Piece>(64) }
    }
    val displayPieces = remember(basePieces, whiteBottom) {
        if (whiteBottom) basePieces else Array(64) { index -> basePieces[63 - index] }
    }
    val lastMove = (if (detached) analysisUciMoves.getOrNull(analysisPly - 1) else tv.lastMoveUci)
    val lastFromLogical = lastMove?.takeIf { it.length >= 4 }?.substring(0, 2)?.let(::lichessTvSquareIndex)
    val lastToLogical = lastMove?.takeIf { it.length >= 4 }?.substring(2, 4)?.let(::lichessTvSquareIndex)
    val displaySelected = selectedSquare?.let { if (whiteBottom) it else 63 - it }
    val displayLastFrom = lastFromLogical?.let { if (whiteBottom) it else 63 - it }
    val displayLastTo = lastToLogical?.let { if (whiteBottom) it else 63 - it }

    val shownUciMoves = if (detached) analysisUciMoves else tv.uciMoves
    val shownSanMoves = if (detached) analysisSanMoves else tv.sanMoves
    val shownPly = if (detached) analysisPly else shownUciMoves.size
    val broadcastPositionLabel = if (
        tv.source == LichessTvSource.BROADCAST_BOARD && selectedBroadcastGames.size > 1
    ) {
        "Game ${activeBroadcastIndex + 1}/${selectedBroadcastGames.size}"
    } else {
        null
    }

    BackHandler(onBack = onHome)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07140A), Color(0xFF102A15), Color(0xFF111827))
                )
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenMaxWidth = maxWidth
            val screenMaxHeight = maxHeight
            val landscape = screenMaxWidth > screenMaxHeight * 1.12f
            val landscapeEvalSpace = 26.dp
            val portraitEvalSpace = 16.dp

            @Composable
            fun BoardPane(modifier: Modifier) {
                val broadcastSwipeModifier = if (
                    !detached &&
                    tv.source == LichessTvSource.BROADCAST_BOARD &&
                    selectedBroadcastGames.size > 1
                ) {
                    Modifier.pointerInput(activeBroadcastIndex, selectedBroadcastGames.size) {
                        var horizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                            onDragCancel = { horizontalDrag = 0f },
                            onDragEnd = {
                                val threshold = size.width * 0.16f
                                when {
                                    horizontalDrag <= -threshold -> switchBroadcastGame(1)
                                    horizontalDrag >= threshold -> switchBroadcastGame(-1)
                                }
                                horizontalDrag = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }
                LichessTvBoardPane(
                    board = displayPieces,
                    selected = displaySelected,
                    lastFrom = displayLastFrom,
                    lastTo = displayLastTo,
                    whiteBottom = whiteBottom,
                    pieceSet = pieceSet,
                    lightSquare = lightSquare,
                    darkSquare = darkSquare,
                    evaluationCpWhite = engineCpWhite,
                    evaluationText = engineEvaluation,
                    engineEnabled = effectiveEngineEnabled,
                    onSquareClick = ::onBoardSquare,
                    landscape = landscape,
                    modifier = modifier.then(broadcastSwipeModifier)
                )
            }

            @Composable
            fun StudyPanel(modifier: Modifier) {
                LichessTvStudyPanel(
                    detached = detached,
                    engineEnabled = effectiveEngineEnabled,
                    engineLocked = watchedPlayerEngineLocked,
                    watchingAlternateGame = tv.source != LichessTvSource.TOP_GAME,
                    evaluationText = engineEvaluation,
                    enginePv = enginePv,
                    uciMoves = shownUciMoves,
                    sanMoves = shownSanMoves,
                    currentPly = shownPly,
                    pgnLoaded = tv.pgnLoaded,
                    onEngineToggle = { engineEnabled = !engineEnabled },
                    onAnalyze = { enterAnalysis(tv.uciMoves.size) },
                    onReconnect = ::reconnect,
                    onWatchPlayer = {
                        playerUsername = tv.watchedUsername ?: playerUsername
                        showWatchPlayerDialog = true
                    },
                    onBrowseBroadcasts = ::openBroadcasts,
                    onTopGame = ::showTopGame,
                    onFlip = { whiteBottom = !whiteBottom },
                    onHelp = ::openHelp,
                    onNavigate = ::navigateToPly,
                    modifier = modifier
                )
            }

            if (landscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // The board owns the full screen height. The evaluation rail extends
                    // beside it without taking a single pixel from the board itself.
                    BoardPane(
                        Modifier
                            .width(screenMaxHeight + landscapeEvalSpace)
                            .fillMaxHeight()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 5.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        LichessTvHeader(
                            state = tv,
                            detached = detached,
                            broadcastPositionLabel = broadcastPositionLabel,
                            onHome = onHome,
                            onLiveToggle = {
                                if (detached) reconnect() else enterAnalysis(tv.uciMoves.size)
                            }
                        )
                        Spacer(Modifier.height(5.dp))
                        LichessTvPlayers(white = tv.white, black = tv.black)
                        Spacer(Modifier.height(7.dp))
                        StudyPanel(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        LichessTvHeader(
                            state = tv,
                            detached = detached,
                            broadcastPositionLabel = broadcastPositionLabel,
                            onHome = onHome,
                            onLiveToggle = {
                                if (detached) reconnect() else enterAnalysis(tv.uciMoves.size)
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        LichessTvPlayers(white = tv.white, black = tv.black)
                    }

                    // Fix the pane height from the screen width instead of assigning a
                    // weight. This guarantees a wall-to-wall board on every portrait
                    // screen and prevents spare height from centering it in empty space.
                    BoardPane(
                        Modifier
                            .fillMaxWidth()
                            .height((screenMaxWidth + portraitEvalSpace).coerceAtLeast(136.dp))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 6.dp, top = 5.dp, end = 6.dp, bottom = 6.dp)
                    ) {
                        StudyPanel(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showWatchPlayerDialog) {
        AlertDialog(
            onDismissRequest = { showWatchPlayerDialog = false },
            title = { Text("Watch a player") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a Lichess username. The engine stays off while that player's game is live.",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = playerUsername,
                        onValueChange = { playerUsername = it },
                        label = { Text("Lichess username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = playerUsername.trim().removePrefix("@").isNotBlank(),
                    onClick = ::watchPlayer
                ) { Text("Watch") }
            },
            dismissButton = {
                TextButton(onClick = { showWatchPlayerDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBroadcastDialog) {
        LichessBroadcastDialog(
            loading = broadcastLoading,
            errorMessage = broadcastError,
            broadcasts = liveBroadcasts,
            selectedRound = selectedBroadcastRound,
            onDismiss = ::closeBroadcasts,
            onRefresh = ::refreshBroadcasts,
            onBack = {
                selectedBroadcastRound = null
                broadcastSelectionMessage = null
                broadcastError = null
            },
            onSelectBroadcast = ::openBroadcastRound,
            selectedGames = pendingBroadcastGames,
            selectionMessage = broadcastSelectionMessage,
            onToggleBoard = ::toggleBroadcastBoard,
            onClear = {
                pendingBroadcastGames = emptyList()
                broadcastSelectionMessage = null
            },
            onDone = ::watchSelectedBroadcasts
        )
    }

    TrainerFishHelpDialog(
        show = showHelpDialog,
        initialTopic = TrainerHelpTopic.CHESS_TV,
        onDismiss = ::closeHelp
    )

    if (promotionChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { promotionChoices = emptyList() },
            title = { Text("Choose promotion") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        LibPieceType.QUEEN to "Queen",
                        LibPieceType.ROOK to "Rook",
                        LibPieceType.BISHOP to "Bishop",
                        LibPieceType.KNIGHT to "Knight"
                    ).forEach { (type, label) ->
                        val move = promotionChoices.firstOrNull { it.promotion?.pieceType == type }
                        TextButton(enabled = move != null, onClick = { move?.let(::applyAnalysisMove) }) {
                            Text(label.take(1), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { promotionChoices = emptyList() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LichessBroadcastDialog(
    loading: Boolean,
    errorMessage: String?,
    broadcasts: List<LichessBroadcastPreview>,
    selectedRound: LichessBroadcastRound?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSelectBroadcast: (LichessBroadcastPreview) -> Unit,
    selectedGames: List<LichessBroadcastSelection>,
    selectionMessage: String?,
    onToggleBoard: (LichessBroadcastBoard) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    val instructionScrollState = rememberScrollState()
    val selectedTournamentCount = selectedGames.map { it.tournamentName }.distinct().size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                selectedRound?.preview?.tournamentName ?: "Live Tournament Broadcasts",
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedRound?.let {
                    Text(
                        "${it.preview.roundName} • ${selectedGames.size}/$MAX_BROADCAST_GAMES selected total",
                        color = Color(0xFF5D4632),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Select boards here, return to Tournaments to add games from another event, then tap Done. Swipe the live board left or right to switch games.",
                        color = Color(0xFF5D4632),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(43.dp)
                            .verticalScroll(instructionScrollState)
                    )
                    selectionMessage?.let { message ->
                        Text(
                            message,
                            color = Color(0xFFB91C1C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } ?: Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Choose up to $MAX_BROADCAST_GAMES games from one or several tournaments.",
                        color = Color(0xFF5D4632),
                        fontSize = 12.sp
                    )
                    if (selectedGames.isNotEmpty()) {
                        Text(
                            "${selectedGames.size}/$MAX_BROADCAST_GAMES selected across $selectedTournamentCount tournament${if (selectedTournamentCount == 1) "" else "s"}.",
                            color = Color(0xFF166534),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2F6B1F))
                    }
                } else {
                    errorMessage?.let {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = Color(0xFFFFE4E6)
                        ) {
                            Text(
                                it,
                                color = Color(0xFF9F1239),
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(9.dp)
                            )
                        }
                    }

                    if (selectedRound != null) {
                        if (selectedRound.boards.isEmpty() && errorMessage == null) {
                            Text(
                                "No boards are available in this round yet.",
                                color = Color(0xFF5D4632),
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 430.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                itemsIndexed(
                                    items = selectedRound.boards,
                                    key = { _, board -> board.gameId }
                                ) { _, board ->
                                    LichessBroadcastBoardRow(
                                        board = board,
                                        selected = selectedGames.any {
                                            it.roundId == selectedRound.preview.roundId &&
                                                it.gameId == board.gameId
                                        },
                                        onClick = { onToggleBoard(board) }
                                    )
                                }
                            }
                        }
                    } else {
                        if (broadcasts.isEmpty() && errorMessage == null) {
                            Text(
                                "No tournament rounds are being broadcast live right now.",
                                color = Color(0xFF5D4632),
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 430.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                items(broadcasts, key = { it.roundId }) { broadcast ->
                                    LichessBroadcastTournamentRow(
                                        broadcast = broadcast,
                                        selectedCount = selectedGames.count { it.roundId == broadcast.roundId },
                                        onClick = { onSelectBroadcast(broadcast) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = selectedGames.isNotEmpty(),
                    onClick = onClear
                ) {
                    Text("Clear list", color = if (selectedGames.isNotEmpty()) Color(0xFFB91C1C) else Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                if (selectedRound != null) {
                    TextButton(onClick = onBack) { Text("← Tournaments") }
                } else {
                    TextButton(enabled = !loading, onClick = onRefresh) { Text("Refresh") }
                }
                if (selectedGames.isNotEmpty()) {
                    TextButton(onClick = onDone) { Text("Done (${selectedGames.size})") }
                } else if (selectedRound == null) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun LichessBroadcastTournamentRow(
    broadcast: LichessBroadcastPreview,
    selectedCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = Color(0xFFE8F5E9),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(shape = RoundedCornerShape(7.dp), color = Color(0xFF166534)) {
                Text(
                    "LIVE",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    broadcast.tournamentName,
                    color = Color(0xFF142117),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    maxLines = 2
                )
                Text(
                    listOfNotNull(broadcast.roundName, broadcast.location).joinToString(" • "),
                    color = Color(0xFF3F5D45),
                    fontSize = 11.sp,
                    maxLines = 1
                )
                broadcast.playersSummary?.let {
                    Text(it, color = Color(0xFF617366), fontSize = 10.sp, maxLines = 1)
                }
            }
            if (selectedCount > 0) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFDCFCE7)) {
                    Text(
                        "$selectedCount selected",
                        color = Color(0xFF166534),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            Text("›", color = Color(0xFF166534), fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LichessBroadcastBoardRow(
    board: LichessBroadcastBoard,
    selected: Boolean,
    onClick: () -> Unit
) {
    val statusColor = if (board.isOngoing) Color(0xFFB91C1C) else Color(0xFF334155)
    val statusText = if (board.isOngoing) "LIVE" else board.status
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = when {
            selected -> Color(0xFFDCFCE7)
            board.isOngoing -> Color(0xFFFFF7ED)
            else -> Color(0xFFF1F5F9)
        },
        border = if (selected) BorderStroke(2.dp, Color(0xFF15803D)) else null,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BOARD", color = Color(0xFF64748B), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    board.boardNumber.toString(),
                    color = Color(0xFF0F172A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${board.white.displayName}  ${board.white.rating ?: "—"}",
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Text(
                    "${board.black.displayName}  ${board.black.rating ?: "—"}",
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (selected) {
                    Text("✓ SELECTED", color = Color(0xFF15803D), fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LichessTvHeader(
    state: LichessTvState,
    detached: Boolean,
    broadcastPositionLabel: String?,
    onHome: () -> Unit,
    onLiveToggle: () -> Unit
) {
    val (statusText, statusColor) = when {
        detached -> "ANALYSIS • STREAM PAUSED" to Color(0xFFF59E0B)
        state.status == LichessTvConnectionStatus.FINISHED -> "GAME FINISHED" to Color(0xFF93C5FD)
        state.source == LichessTvSource.BROADCAST_BOARD &&
            state.status == LichessTvConnectionStatus.LIVE -> "BROADCAST LIVE" to Color(0xFF67E8F9)
        state.source == LichessTvSource.WATCHED_PLAYER &&
            state.status == LichessTvConnectionStatus.LIVE -> "PLAYER LIVE" to Color(0xFFFACC15)
        state.status == LichessTvConnectionStatus.LIVE -> "LIVE" to Color(0xFF4ADE80)
        state.status == LichessTvConnectionStatus.RECONNECTING -> "RECONNECTING" to Color(0xFFFACC15)
        state.status == LichessTvConnectionStatus.CONNECTING -> "CONNECTING" to Color(0xFF93C5FD)
        else -> "OFFLINE" to Color(0xFFF87171)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = onHome) {
            Text("← Home", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Grandmaster Chess TV",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            val sourceDetail = state.errorMessage ?: state.noticeMessage ?: state.watchedUsername?.let {
                "Watching @$it"
            }
            val detail = if (broadcastPositionLabel != null && state.errorMessage == null) {
                listOfNotNull(broadcastPositionLabel, sourceDetail).joinToString(" • ")
            } else {
                sourceDetail
            }
            detail?.let {
                Text(
                    it,
                    color = if (state.errorMessage != null) Color(0xFFFCA5A5) else Color(0xFFFDE68A),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        Surface(
            modifier = Modifier.clickable(onClick = onLiveToggle),
            shape = RoundedCornerShape(10.dp),
            color = statusColor.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
        ) {
            Text(
                statusText,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun LichessTvPlayers(white: LichessTvPlayer, black: LichessTvPlayer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LichessTvPlayerCard("White", white, Color(0xFFF8FAFC), Color(0xFF111827), Modifier.weight(1f))
        LichessTvPlayerCard("Black", black, Color(0xFF111827), Color.White, Modifier.weight(1f))
    }
}

@Composable
private fun LichessTvPlayerCard(
    side: String,
    player: LichessTvPlayer,
    background: Color,
    foreground: Color,
    modifier: Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(side.uppercase(Locale.US), color = foreground.copy(alpha = 0.76f), fontSize = 9.sp)
                Text(player.displayName, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(player.rating?.toString() ?: "—", color = foreground, fontWeight = FontWeight.Black)
                player.seconds?.let {
                    Text(lichessTvClock(it), color = foreground.copy(alpha = 0.90f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LichessTvBoardPane(
    board: Array<Piece?>,
    selected: Int?,
    lastFrom: Int?,
    lastTo: Int?,
    whiteBottom: Boolean,
    pieceSet: String,
    lightSquare: Color,
    darkSquare: Color,
    evaluationCpWhite: Int?,
    evaluationText: String,
    engineEnabled: Boolean,
    onSquareClick: (Int) -> Unit,
    landscape: Boolean,
    modifier: Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopStart) {
        val landscapeEvalSpace = 26.dp
        val portraitEvalSpace = 16.dp
        val boardSize = if (landscape) {
            minOf(maxWidth - landscapeEvalSpace, maxHeight)
        } else {
            minOf(maxWidth, maxHeight - portraitEvalSpace)
        }.coerceAtLeast(120.dp)

        @Composable
        fun BoardOnly() {
            Box(modifier = Modifier.size(boardSize)) {
                ChessBoard(
                    board = board,
                    selected = selected,
                    lastMoveFrom = lastFrom,
                    lastMoveTo = lastTo,
                    onSquareClick = onSquareClick,
                    light = lightSquare,
                    dark = darkSquare,
                    pieceSetKey = pieceSet,
                    whiteBottom = whiteBottom
                )
            }
        }

        if (landscape) {
            Row(
                modifier = Modifier
                    .width(boardSize + landscapeEvalSpace)
                    .height(boardSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoardOnly()
                Spacer(Modifier.width(2.dp))
                LichessTvEvalBar(
                    cpWhite = evaluationCpWhite,
                    text = evaluationText,
                    enabled = engineEnabled,
                    horizontal = false,
                    modifier = Modifier.width(24.dp).fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .width(boardSize)
                    .height(boardSize + portraitEvalSpace),
                horizontalAlignment = Alignment.Start
            ) {
                BoardOnly()
                Spacer(Modifier.height(2.dp))
                LichessTvEvalBar(
                    cpWhite = evaluationCpWhite,
                    text = evaluationText,
                    enabled = engineEnabled,
                    horizontal = true,
                    modifier = Modifier.fillMaxWidth().height(14.dp)
                )
            }
        }
    }
}

@Composable
private fun LichessTvEvalBar(
    cpWhite: Int?,
    text: String,
    enabled: Boolean,
    horizontal: Boolean,
    modifier: Modifier
) {
    val whiteShare = if (!enabled || cpWhite == null) 0.5f else {
        (0.5f + cpWhite.coerceIn(-1_200, 1_200) / 2_400f).coerceIn(0.06f, 0.94f)
    }
    Box(modifier = modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFF111827))) {
        if (horizontal) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().weight(1f - whiteShare).background(Color(0xFF151515)))
                Box(Modifier.fillMaxHeight().weight(whiteShare).background(Color(0xFFF3F4F6)))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f - whiteShare).background(Color(0xFF151515)))
                Box(Modifier.fillMaxWidth().weight(whiteShare).background(Color(0xFFF3F4F6)))
            }
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xE6111827), RoundedCornerShape(4.dp))
                .padding(horizontal = 2.dp, vertical = if (horizontal) 0.dp else 2.dp)
        )
    }
}

@Composable
private fun LichessTvStudyPanel(
    detached: Boolean,
    engineEnabled: Boolean,
    engineLocked: Boolean,
    watchingAlternateGame: Boolean,
    evaluationText: String,
    enginePv: String,
    uciMoves: List<String>,
    sanMoves: List<String>,
    currentPly: Int,
    pgnLoaded: Boolean,
    onEngineToggle: () -> Unit,
    onAnalyze: () -> Unit,
    onReconnect: () -> Unit,
    onWatchPlayer: () -> Unit,
    onBrowseBroadcasts: () -> Unit,
    onTopGame: () -> Unit,
    onFlip: () -> Unit,
    onHelp: () -> Unit,
    onNavigate: (Int) -> Unit,
    modifier: Modifier
) {
    var controlsMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF4ECD8),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFF1565C0),
                        shadowElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = { controlsMenuExpanded = true },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Grandmaster Chess TV controls",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = controlsMenuExpanded,
                        onDismissRequest = { controlsMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (detached) "Reconnect live" else "Analyze game") },
                            onClick = {
                                controlsMenuExpanded = false
                                if (detached) onReconnect() else onAnalyze()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when {
                                        engineLocked -> "Engine locked"
                                        engineEnabled -> "Turn engine off"
                                        else -> "Turn engine on"
                                    }
                                )
                            },
                            enabled = !engineLocked,
                            onClick = {
                                controlsMenuExpanded = false
                                onEngineToggle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Watch player") },
                            onClick = {
                                controlsMenuExpanded = false
                                onWatchPlayer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Live broadcasts") },
                            onClick = {
                                controlsMenuExpanded = false
                                onBrowseBroadcasts()
                            }
                        )
                        if (watchingAlternateGame) {
                            DropdownMenuItem(
                                text = { Text("Show top game") },
                                onClick = {
                                    controlsMenuExpanded = false
                                    onTopGame()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Flip board") },
                            onClick = {
                                controlsMenuExpanded = false
                                onFlip()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Help for Chess TV") },
                            onClick = {
                                controlsMenuExpanded = false
                                onHelp()
                            }
                        )
                    }
                }
                Text(
                    "PGN moves",
                    color = Color(0xFF4E3B2A),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    if (pgnLoaded || detached) "$currentPly / ${uciMoves.size}" else "Loading…",
                    color = Color(0xFF7C5A3A),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(Modifier.height(4.dp))
            LichessTvMoveList(
                sanMoves = sanMoves,
                currentPly = currentPly,
                onNavigate = onNavigate,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LichessTvNavButton("|◀", enabled = currentPly > 0) { onNavigate(0) }
                LichessTvNavButton("◀", enabled = currentPly > 0) { onNavigate(currentPly - 1) }
                LichessTvNavButton("▶", enabled = currentPly < uciMoves.size) { onNavigate(currentPly + 1) }
                LichessTvNavButton("▶|", enabled = currentPly < uciMoves.size) { onNavigate(uciMoves.size) }
            }

            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF2C2118)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Engine $evaluationText",
                        color = Color(0xFFFFD166),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Text(
                        enginePv,
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun LichessTvMoveList(
    sanMoves: List<String>,
    currentPly: Int,
    onNavigate: (Int) -> Unit,
    modifier: Modifier
) {
    if (sanMoves.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Waiting for moves…", color = Color(0xFF5D4632), fontSize = 12.sp)
        }
        return
    }

    val paragraph = remember(sanMoves, currentPly) {
        buildAnnotatedString {
            sanMoves.forEachIndexed { index, san ->
                val ply = index + 1
                if (index > 0) append(" ")
                if (index % 2 == 0) append("${index / 2 + 1}. ")
                pushStringAnnotation(tag = "ply", annotation = ply.toString())
                pushStyle(
                    SpanStyle(
                        color = if (currentPly == ply) Color.White else Color(0xFF2C2118),
                        background = if (currentPly == ply) Color(0xFF1D4ED8) else Color.Transparent,
                        fontWeight = if (currentPly == ply) FontWeight.Black else FontWeight.Medium
                    )
                )
                append(san)
                pop()
                pop()
            }
        }
    }
    val moveScrollState = rememberScrollState()
    LaunchedEffect(sanMoves.size, currentPly) {
        if (currentPly == sanMoves.size) {
            delay(32L)
            moveScrollState.scrollTo(moveScrollState.maxValue)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, Color(0xFFC8B99F))
    ) {
        ClickableText(
            text = paragraph,
            style = TextStyle(
                color = Color(0xFF2C2118),
                fontSize = 13.sp,
                lineHeight = 21.sp
            ),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(moveScrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            onClick = { offset ->
                paragraph.getStringAnnotations(tag = "ply", start = offset, end = offset)
                    .firstOrNull()
                    ?.item
                    ?.toIntOrNull()
                    ?.let(onNavigate)
            }
        )
    }
}

@Composable
private fun LichessTvNavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = Color(0xFF084E9E),
            disabledContentColor = Color(0xFF776C60)
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

private fun lichessTvBoardColors(key: String): Pair<Color, Color> = when (key.trim().lowercase()) {
    "gray" -> Color(0xFFBDBDBD) to Color(0xFF777777)
    "wood" -> Color(0xFFEED8B0) to Color(0xFFA98272)
    "night" -> Color(0xFFB0BEC5) to Color(0xFF607D8B)
    "blue" -> Color(0xFFBBDEFB) to Color(0xFF4F8FEF)
    "sand" -> Color(0xFFF5E0C3) to Color(0xFFD9A65C)
    "forest" -> Color(0xFFC8E6C9) to Color(0xFF5FA463)
    "purple" -> Color(0xFFEDE9FE) to Color(0xFF8B5CF6)
    "coffee" -> Color(0xFFE7D3B0) to Color(0xFF8B5E3C)
    "olive" -> Color(0xFFDDE5B6) to Color(0xFF738A3D)
    "ice" -> Color(0xFFE0F7FA) to Color(0xFF00ACC1)
    "rosewood" -> Color(0xFFFADADD) to Color(0xFFB56576)
    else -> Color(0xFFEEEED2) to Color(0xFF8FB06B)
}

private fun lichessTvIndexToSquare(index: Int): com.github.bhlangonijr.chesslib.Square {
    val file = "ABCDEFGH"[index.coerceIn(0, 63) % 8]
    val rank = index.coerceIn(0, 63) / 8 + 1
    return com.github.bhlangonijr.chesslib.Square.valueOf("$file$rank")
}

private fun lichessTvSquareIndex(square: String): Int? {
    if (square.length != 2) return null
    val file = square[0].lowercaseChar() - 'a'
    val rank = square[1] - '1'
    if (file !in 0..7 || rank !in 0..7) return null
    return rank * 8 + file
}

private fun lichessTvMoveToUci(move: LibMove): String {
    val promotion = when (move.promotion?.pieceType) {
        LibPieceType.QUEEN -> "q"
        LibPieceType.ROOK -> "r"
        LibPieceType.BISHOP -> "b"
        LibPieceType.KNIGHT -> "n"
        else -> ""
    }
    return move.from.toString().lowercase(Locale.US) +
        move.to.toString().lowercase(Locale.US) + promotion
}

private fun lichessTvEvaluationText(infoLine: String?, whiteToMove: Boolean): String {
    if (infoLine == null) return "…"
    val mate = Regex("\\bscore\\s+mate\\s+(-?\\d+)")
        .find(infoLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (mate != null) {
        val whiteMate = if (whiteToMove) mate else -mate
        return if (whiteMate >= 0) "#${whiteMate}" else "#${whiteMate}"
    }
    val cp = Regex("\\bscore\\s+cp\\s+(-?\\d+)")
        .find(infoLine)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return "…"
    val whiteCp = if (whiteToMove) cp else -cp
    return String.format(Locale.US, "%+.2f", whiteCp / 100.0)
}

private fun lichessTvPvText(fen: String, infoLine: String?): String {
    val raw = infoLine?.substringAfter(" pv ", "")?.trim().orEmpty()
    if (raw.isBlank()) return "Calculating best line…"
    val board = runCatching { Board().apply { loadFromFen(fen) } }.getOrNull()
        ?: return raw.split(' ').take(8).joinToString(" ")
    val output = ArrayList<String>()
    var moveNumber = fen.split(' ').getOrNull(5)?.toIntOrNull() ?: 1
    raw.split(' ').filter { it.length >= 4 }.take(10).forEach { uci ->
        val move = bfUciToMoveOnBoard(board, uci) ?: return@forEach
        val white = board.sideToMove == Side.WHITE
        val san = runCatching { bfPrettySan(board, move, white) }.getOrElse { uci }
        output += if (white) "$moveNumber. $san" else san
        board.doMove(move)
        if (!white) moveNumber++
    }
    return output.joinToString(" ").ifBlank { "Calculating best line…" }
}

private fun lichessTvClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, safe / 60, safe % 60)
}
