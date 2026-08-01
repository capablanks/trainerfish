// BeatFishScreen.kt (DROP-IN REPLACEMENT)
package com.tonorbe.trainerfish

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.game.Game
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import com.tonorbe.trainerfish.engine.ProcEngine
import com.tonorbe.trainerfish.opening.BinaryOpeningBook
import com.tonorbe.trainerfish.opening.EcoClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.bhlangonijr.chesslib.Board as LibBoard
import com.github.bhlangonijr.chesslib.PieceType as LibPieceType
import com.github.bhlangonijr.chesslib.move.Move as LibMove
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

private const val START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


private fun whiteToMoveFen(fenStr: String?): Boolean {
    val parts = fenStr?.trim()?.split(Regex("\\s+")) ?: return true
    return parts.getOrNull(1) == "w"
}

private fun bfFenWithSideToMove(fenStr: String, side: Side): String {
    val parts = fenStr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
    if (parts.size < 2) return fenStr
    while (parts.size < 6) parts += if (parts.size == 4) "0" else "1"
    parts[1] = if (side == Side.WHITE) "w" else "b"
    return parts.take(6).joinToString(" ")
}

private fun normalizeEngineFenOrNull(raw: String?): String? {
    val compact = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    if (compact.isBlank()) return null

    val original = compact.split(" ").filter { it.isNotBlank() }
    if (original.size < 4) return null

    val parts = when (original.size) {
        4 -> original + listOf("0", "1")
        5 -> original + "1"
        else -> original.take(6)
    }

    val boardPart = parts[0]
    val side = parts[1]
    val castling = parts[2]
    val ep = parts[3]
    val halfMove = parts[4].toIntOrNull() ?: return null
    val fullMove = parts[5].toIntOrNull() ?: return null

    if (side != "w" && side != "b") return null
    if (halfMove < 0 || fullMove <= 0) return null
    if (castling != "-" && !castling.matches(Regex("[KQkq]+"))) return null
    if (castling != "-" && castling.toSet().size != castling.length) return null
    if (ep != "-" && !ep.matches(Regex("[a-h][36]"))) return null

    val ranks = boardPart.split('/')
    if (ranks.size != 8) return null

    var whiteKings = 0
    var blackKings = 0
    for ((rankIndexFromTop, rankText) in ranks.withIndex()) {
        var count = 0
        for (ch in rankText) {
            when {
                ch in '1'..'8' -> count += ch.digitToInt()
                ch in "PNBRQKpnbrqk" -> {
                    count += 1
                    if (ch == 'K') whiteKings++
                    if (ch == 'k') blackKings++
                    // FEN ranks are 8..1 from top to bottom. Pawns on rank 8 or rank 1
                    // are illegal and can upset native engine position setup.
                    if ((rankIndexFromTop == 0 || rankIndexFromTop == 7) && (ch == 'P' || ch == 'p')) return null
                }
                else -> return null
            }
        }
        if (count != 8) return null
    }

    if (whiteKings != 1 || blackKings != 1) return null

    val fen = parts.joinToString(" ")
    return runCatching {
        LibBoard().apply { loadFromFen(fen) }.fen
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun evaluateFenSafely(fen: String?, movetimeMs: Int): Boolean {
    val safeFen = normalizeEngineFenOrNull(fen) ?: return false
    return runCatching {
        ProcEngine.evaluateFen(fen = safeFen, movetimeMs = movetimeMs)
        true
    }.getOrDefault(false)
}


// ----- Master PGN auto-append (user-chosen file) -----
private const val DEFAULT_MASTER_PGN_NAME = "trainer_fish_annotated_games.pgn"
private const val MASTER_PGN_PREFS = "trainerfish_master_pgn"
private const val MASTER_PGN_URI_KEY = "master_pgn_uri"

private fun getMasterPgnUri(context: Context): Uri? {
    val s = context.getSharedPreferences(MASTER_PGN_PREFS, Context.MODE_PRIVATE)
        .getString(MASTER_PGN_URI_KEY, null)
        ?.trim()
        .orEmpty()
    return if (s.isBlank()) null else runCatching { Uri.parse(s) }.getOrNull()
}

private data class LoadedPgnGameData(
    val startFen: String,
    val moves: List<BFMove>,
    val positions: List<String>,
    val rawMoveText: String
)

private fun loadGameDataFromPgnGame(game: Game): LoadedPgnGameData? {
    return runCatching {
        val startFen = game.fen?.takeIf { it.isNotBlank() } ?: START_FEN
        val board = LibBoard().apply { loadFromFen(startFen) }

        val outMoves = mutableListOf<BFMove>()
        val outPositions = mutableListOf<String>()
        outPositions += board.fen

        var moveNo = board.fen.split(" ").getOrNull(5)?.toIntOrNull() ?: 1

        val halfMoves = game.halfMoves ?: emptyList()
        for (mv in halfMoves) {
            val before = LibBoard().apply { loadFromFen(board.fen) }
            val isWhiteMove = (board.sideToMove == Side.WHITE)
            val san = bfPrettySan(before, mv, isWhiteMove = isWhiteMove)
            val uci = bfmoveToUci(mv)

            outMoves += BFMove(
                moveNumber = moveNo,
                uci = uci,
                san = san,
                isWhite = isWhiteMove
            )

            board.doMove(mv)
            outPositions += board.fen

            if (!isWhiteMove) moveNo++
        }

        LoadedPgnGameData(
            startFen = startFen,
            moves = outMoves,
            positions = outPositions,
            rawMoveText = buildBeatFishMoveRibbon(outMoves)
        )
    }.getOrNull()
}

private fun setMasterPgnUri(context: Context, uri: Uri?) {
    val e = context.getSharedPreferences(MASTER_PGN_PREFS, Context.MODE_PRIVATE).edit()
    if (uri == null) e.remove(MASTER_PGN_URI_KEY) else e.putString(MASTER_PGN_URI_KEY, uri.toString())
    e.apply()
}

/**
 * Append [gamePgn] into [uri]. Tries true append first; falls back to read+rewrite for providers
 * that don't support append mode.
 */
private suspend fun appendPgnGameToMasterUri(
    context: Context,
    uri: Uri,
    gamePgn: String
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val pgnToAppend = gamePgn.trim().let { if (it.isBlank()) "" else it + "\n\n" }
        if (pgnToAppend.isBlank()) return@runCatching

        val cr = context.contentResolver

        // Try true append
        runCatching {
            cr.openOutputStream(uri, "wa")?.use { os ->
                os.bufferedWriter(Charsets.UTF_8).use { bw ->
                    bw.write(pgnToAppend)
                    bw.flush()
                }
            } ?: error("Unable to open output stream.")
        }.onSuccess { return@runCatching }

        // Fallback: read then rewrite
        val existing = cr.openInputStream(uri)?.use { ins ->
            ins.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()

        val combined = existing.trimEnd().let { base ->
            if (base.isBlank()) pgnToAppend.trim()
            else base + "\n\n" + pgnToAppend.trim()
        } + "\n"

        cr.openOutputStream(uri, "wt")?.use { os ->
            os.bufferedWriter(Charsets.UTF_8).use { bw ->
                bw.write(combined)
                bw.flush()
            }
        } ?: error("Unable to open output stream.")
    }
}

private fun buildBeatFishPgnText(
    startFen: String,
    ribbon: String,
    result: String = "*",
    whiteName: String = "You",
    blackName: String = "Fish"
): String {
    val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
    val sb = StringBuilder()
    sb.appendLine("[Event \"TrainerFish Beat the Fish\"]")
    sb.appendLine("[Site \"TrainerFish\"]")
    sb.appendLine("[Date \"$date\"]")
    sb.appendLine("[Round \"-\"]")
    sb.appendLine("[White \"$whiteName\"]")
    sb.appendLine("[Black \"$blackName\"]")
    sb.appendLine("[Result \"$result\"]")

    // If not starting from the standard initial position, include SetUp/FEN tags.
    val fenTrim = startFen.trim()
    if (fenTrim.isNotBlank() && fenTrim != START_FEN) {
        sb.appendLine("[SetUp \"1\"]")
        sb.appendLine("[FEN \"$fenTrim\"]")
    }
    sb.appendLine()

    val body = ribbon.trim().replace(Regex("\\s+"), " ")
    val bodyWithResult = when {
        body.endsWith(" 1-0") || body.endsWith(" 0-1") || body.endsWith(" 1/2-1/2") || body.endsWith(" *") -> body
        body.isBlank() -> result
        else -> "$body $result"
    }
    sb.appendLine(bodyWithResult)
    return sb.toString()
}

private fun extractLeadingBraceCommentsFromRibbon(ribbon: String): List<String> {
    val out = mutableListOf<String>()
    val moveNoRe = Regex("""^\d+\.{1,3}$""")
    var i = 0
    var startedMoves = false

    while (i < ribbon.length) {
        val ch = ribbon[i]

        if (ch.isWhitespace()) {
            i++
            continue
        }

        if (ch == '{') {
            val start = i + 1
            var j = start
            while (j < ribbon.length && ribbon[j] != '}') j++
            if (!startedMoves) {
                val c = ribbon.substring(start, j).trim()
                if (c.isNotBlank()) out += c
            }
            i = if (j < ribbon.length) j + 1 else ribbon.length
            continue
        }

        val start = i
        var end = i
        while (
            end < ribbon.length &&
            !ribbon[end].isWhitespace() &&
            ribbon[end] != '{' &&
            ribbon[end] != '}'
        ) end++

        val tok = ribbon.substring(start, end)
        if (moveNoRe.matches(tok)) {
            startedMoves = true
            break
        }

        i = end
    }

    return out
}

private data class ExportVarNode(
    val uci: String,
    val children: LinkedHashMap<String, ExportVarNode> = LinkedHashMap()
)

private fun buildExportVarForest(lines: List<List<String>>): List<ExportVarNode> {
    val roots = LinkedHashMap<String, ExportVarNode>()

    for (line in lines) {
        if (line.isEmpty()) continue

        var curMap = roots
        for (uci in line) {
            val key = uci.lowercase(Locale.ROOT)
            val next = curMap.getOrPut(key) { ExportVarNode(key) }
            curMap = next.children
        }
    }

    return roots.values.toList()
}

private fun renderExportMoveToken(
    boardBefore: LibBoard,
    uci: String,
    forceMoveNumber: Boolean = false
): Pair<String, LibBoard>? {
    val mv = bfUciToMoveOnBoard(boardBefore, uci) ?: return null

    val before = LibBoard().apply { loadFromFen(boardBefore.fen) }
    val isWhiteMove = (boardBefore.sideToMove == Side.WHITE)
    val moveNo = boardBefore.fen.split(' ').getOrNull(5)?.toIntOrNull() ?: 1
    val san = bfPrettySan(before, mv, isWhiteMove = isWhiteMove)

    val tok = when {
        isWhiteMove -> "$moveNo. $san"
        forceMoveNumber -> "$moveNo... $san"
        else -> san
    }

    val after = LibBoard().apply { loadFromFen(boardBefore.fen) }
    after.doMove(mv)

    return tok to after
}

private fun renderExportLine(
    boardBefore: LibBoard,
    line: List<String>,
    forceFirstMoveNumber: Boolean
): String {
    if (line.isEmpty()) return ""

    val bb = LibBoard().apply { loadFromFen(boardBefore.fen) }
    val parts = mutableListOf<String>()

    line.forEachIndexed { idx, uci ->
        val step = renderExportMoveToken(
            boardBefore = bb,
            uci = uci,
            forceMoveNumber = (idx == 0 && forceFirstMoveNumber)
        ) ?: return@forEachIndexed

        parts += step.first
        bb.loadFromFen(step.second.fen)
    }

    return parts.joinToString(" ").trim()
}

private fun buildExportVariationBlocks(baseFen: String, lines: List<List<String>>): String {
    if (lines.isEmpty()) return ""

    val baseBoard = LibBoard().apply {
        runCatching { loadFromFen(baseFen) }.onFailure { loadFromFen(START_FEN) }
    }

    // Group by first move from the branch point.
    val byFirst = linkedMapOf<String, MutableList<List<String>>>()
    for (line in lines.map { it.map { u -> u.lowercase(Locale.ROOT) } }.filter { it.isNotEmpty() }) {
        byFirst.getOrPut(line.first()) { mutableListOf() }.add(line)
    }

    val out = mutableListOf<String>()

    for ((firstUci, grouped) in byFirst) {
        val firstStep = renderExportMoveToken(
            boardBefore = baseBoard,
            uci = firstUci,
            forceMoveNumber = true
        ) ?: continue

        val firstTok = firstStep.first
        val afterFirst = firstStep.second

        val suffixes = grouped.map { it.drop(1) }.filter { it.isNotEmpty() }

        if (suffixes.isEmpty()) {
            out += "($firstTok)"
            continue
        }

        // Build a tree only for continuations AFTER the first move.
        val forest = buildExportVarForest(suffixes)

        // Main continuation = first child tree rendered as the body
        val mainRoot = forest.firstOrNull()
        if (mainRoot == null) {
            out += "($firstTok)"
            continue
        }

        val mainLine = renderExportLine(
            boardBefore = afterFirst,
            line = collectMainLine(mainRoot),
            forceFirstMoveNumber = false
        )

        val sb = StringBuilder()
        sb.append(firstTok)
        if (mainLine.isNotBlank()) sb.append(' ').append(mainLine)

        // Nested alternatives at the same node after first move
        val nestedAtFirst = forest.drop(1)
        for (alt in nestedAtFirst) {
            val altText = renderExportLine(
                boardBefore = afterFirst,
                line = collectWholeLine(alt),
                forceFirstMoveNumber = true
            )
            if (altText.isNotBlank()) {
                sb.append(' ').append('(').append(altText).append(')')
            }
        }

        // Nested alternatives deeper inside the chosen main branch
        appendNestedAlternatives(
            sb = sb,
            boardBefore = afterFirst,
            node = mainRoot
        )

        out += "(${sb.toString().trim()})"
    }

    return out.joinToString(" ")
}

private fun collectMainLine(node: ExportVarNode): List<String> {
    val out = mutableListOf<String>()
    var cur: ExportVarNode? = node
    while (cur != null) {
        out += cur.uci
        cur = cur.children.values.firstOrNull()
    }
    return out
}

private fun collectWholeLine(node: ExportVarNode): List<String> {
    val out = mutableListOf<String>()
    var cur: ExportVarNode? = node
    while (cur != null) {
        out += cur.uci
        cur = cur.children.values.firstOrNull()
    }
    return out
}

private fun appendNestedAlternatives(
    sb: StringBuilder,
    boardBefore: LibBoard,
    node: ExportVarNode
) {
    val mainChildren = node.children.values.toList()
    if (mainChildren.isEmpty()) return

    val bb = LibBoard().apply { loadFromFen(boardBefore.fen) }

    // Walk the chosen main child path one move at a time
    val chosen = mainChildren.first()

    val chosenStep = renderExportMoveToken(
        boardBefore = bb,
        uci = chosen.uci,
        forceMoveNumber = false
    ) ?: return

    // Alternatives to chosen.uci from the same position must be nested here
    for (alt in mainChildren.drop(1)) {
        val altText = renderExportLine(
            boardBefore = bb,
            line = collectWholeLine(alt),
            forceFirstMoveNumber = true
        )
        if (altText.isNotBlank()) {
            sb.append(' ').append('(').append(altText).append(')')
        }
    }

    appendNestedAlternatives(
        sb = sb,
        boardBefore = chosenStep.second,
        node = chosen
    )
}

private fun buildBeatFishExportRibbon(
    startFen: String,
    moves: List<BFMove>,
    ribbon: String,
    userVarByBasePly: Map<Int, List<List<String>>>,
    pvByStartPly: Map<Int, Map<String, BeatPvData>>,
    result: String = "*"
): String {
    val commentsByPly = extractPlyCommentsFromRibbon(ribbon)
    val moveTokensByPly = extractPlyMoveTokensFromRibbon(ribbon)
    val leadingComments = extractLeadingBraceCommentsFromRibbon(ribbon)

    val sb = StringBuilder()
    if (leadingComments.isNotEmpty()) {
        sb.append(leadingComments.joinToString(" ") { "{$it}" })
    }

    val mainUci = moves.map { it.uci.lowercase(Locale.ROOT) }

    fun autoPvLinesForPly(ply1: Int): List<List<String>> {
        val startPly0 = (ply1 - 1).coerceAtLeast(0)
        val groups = pvByStartPly[startPly0].orEmpty()

        val orderedKinds = listOf("BEST", "BOOK", "LINE", "CUSTOM")
        val out = mutableListOf<List<String>>()

        for (kind in orderedKinds) {
            val pv = groups[kind] ?: continue
            val line = pv.uciMoves
                .map { it.lowercase(Locale.ROOT) }
                .filter { it.length >= 4 }
            if (line.isNotEmpty()) out += line
        }

        for ((kind, pv) in groups) {
            if (kind in orderedKinds) continue
            val line = pv.uciMoves
                .map { it.lowercase(Locale.ROOT) }
                .filter { it.length >= 4 }
            if (line.isNotEmpty()) out += line
        }

        return out
    }

    fun stripBestMoveText(comment: String): String {
        return comment
            .replace(Regex("""(Best:\s*)([^}\s][^}]*)"""), "$1")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    for (idx in moves.indices) {
        val ply1 = idx + 1

        val token = moveTokensByPly[ply1]?.replace("§", "") ?: run {
            val m = moves[idx]
            if (m.isWhite) "${m.moveNumber}. ${m.san}" else m.san
        }

        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(token)

        val mergedVars = buildList {
            addAll(userVarByBasePly[ply1].orEmpty().filter { it.isNotEmpty() })
            addAll(autoPvLinesForPly(ply1))
        }
            .map { line -> line.map { it.lowercase(Locale.ROOT) } }
            .distinctBy { it.joinToString(" ") }

        if (mergedVars.isNotEmpty()) {
            val baseFen = fenAfterUciPlies(startFen, mainUci, ply1 - 1) ?: startFen
            val blocks = buildExportVariationBlocks(baseFen, mergedVars)
            if (blocks.isNotBlank()) sb.append(' ').append(blocks)
        }

        val comments = commentsByPly[ply1].orEmpty()
        for (c in comments) {
            if (c.isBlank()) continue

            val cleaned = if (autoPvLinesForPly(ply1).isNotEmpty()) {
                stripBestMoveText(c)
            } else {
                c
            }

            if (cleaned.isNotBlank()) {
                sb.append(' ').append('{').append(cleaned).append('}')
            }
        }
    }

    if (sb.isEmpty()) return result
    return sb.toString().trim().replace(Regex("""\s+"""), " ")
}

data class BFMove(
    val moveNumber: Int,
    val uci: String,
    val san: String,
    val isWhite: Boolean
)

private enum class BeatFishStage {MENU, SETUP, READY, SESSION}
private enum class BeatFishMode { ANALYZE, PLAY }

private enum class BeatFishAnalyzeContext { GAME_ANALYSIS, ANALYSIS_BOARD }

// Launcher / Play UI phase
private enum class PlayPhase { Idle, Playing }

private data class BeatPvLine(
    val multiPv: Int,
    val evalText: String,
    val uciMoves: List<String>
)

@Composable
private fun BeatFishSquareButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = true,
    backgroundColor: Color = Color(0xFF1F2937),
    textColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 34.dp else 42.dp),
        shape = RoundedCornerShape(0.dp),
        contentPadding = PaddingValues(
            horizontal = if (compact) 10.dp else 14.dp,
            vertical = if (compact) 6.dp else 10.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = Color(0xFF1F2937).copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = text,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun BeatFishThinkingPanel(
    statusText: String,
    thinkSeconds: Int,
    moveNowRequested: Boolean,
    onMoveNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText.ifBlank { "Fish thinking..." },
                    color = Color(0xFFE0F2FE),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Target think time: ${thinkSeconds.coerceIn(2, 60)}s",
                    color = Color(0xFFBAE6FD),
                    fontSize = 12.sp
                )
            }
            BeatFishSquareButton(
                text = if (moveNowRequested) "Stopping" else "Move Now",
                onClick = onMoveNow,
                enabled = !moveNowRequested,
                compact = true,
                backgroundColor = Color(0xFFF59E0B),
                textColor = Color.Black
            )
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF59E0B),
            trackColor = Color(0xFF1E293B)
        )
    }
}



fun viewIdxToBoardIdx(viewIdx: Int, whiteBottom: Boolean): Int {
    return if (whiteBottom) viewIdx else 63 - viewIdx
}

fun boardIdxToViewIdx(boardIdx: Int, whiteBottom: Boolean): Int {
    return if (whiteBottom) boardIdx else 63 - boardIdx
}

// PV (best line) payload used to make moves inside "Best:" comments clickable.
data class BeatPvData(
    val startFen: String,
    val uciMoves: List<String>
)

private data class PendingBeatPromotion(
    val move: LibMove,
    val uci: String,
    val label: String
)

private fun bfSanTokenToFanDisplay(tokenRaw: String): String {
    if (tokenRaw.isBlank()) return tokenRaw

    val bestPrefix = if (tokenRaw.startsWith("§")) "§" else ""
    var token = tokenRaw.removePrefix("§")

    val leading = token.takeWhile { it == '(' || it == '[' || it == '{' }
    val trailing = token.takeLastWhile { it == ')' || it == ']' || it == '}' }
    if (leading.isNotEmpty()) token = token.drop(leading.length)
    if (trailing.isNotEmpty()) token = token.dropLast(trailing.length)

    if (token.matches(Regex("""^\d+\.{1,3}$""")) ||
        token == "1-0" || token == "0-1" || token == "1/2-1/2" || token == "*" ||
        token == "O-O" || token == "O-O-O"
    ) {
        return bestPrefix + leading + token + trailing
    }

    var out = token
    if (out.isNotEmpty()) {
        val first = when (out[0]) {
            'K' -> "♔"
            'Q' -> "♕"
            'R' -> "♖"
            'B' -> "♗"
            'N' -> "♘"
            else -> null
        }
        if (first != null) out = first + out.drop(1)
    }

    out = out
        .replace("=K", "=♔")
        .replace("=Q", "=♕")
        .replace("=R", "=♖")
        .replace("=B", "=♗")
        .replace("=N", "=♘")

    return bestPrefix + leading + out + trailing
}

private fun bfSanToFanDisplay(raw: String): String =
    raw.split(Regex("""\s+""")).joinToString(" ") { bfSanTokenToFanDisplay(it) }

/**
 * Convert a PV list of UCI moves into a numbered SAN/LAN-ish line for display.
 * This is meant to be readable like the Opening/Play move list.
 */
fun pvToSanNumbered(startFen: String, uciMoves: List<String>, maxPlies: Int = 8): String {
    val b = LibBoard().apply { runCatching { loadFromFen(startFen) } }
    val parts = startFen.split(' ')
    var moveNo = parts.getOrNull(5)?.toIntOrNull() ?: 1
    val out = ArrayList<String>(maxPlies + 2)

    for (uci in uciMoves.take(maxPlies)) {
        val mv = bfUciToMoveOnBoard(b, uci) ?: break
        val before = LibBoard().apply { loadFromFen(b.fen) }
        val isWhiteMove = (b.sideToMove == Side.WHITE)

        val san = bfPrettySan(before, mv, isWhiteMove = isWhiteMove)
        val displaySan = bfSanToFanDisplay(san)

        if (isWhiteMove) {
            out += "${moveNo}. $displaySan"
        } else {
            out += displaySan
            moveNo++
        }

        b.doMove(mv)
    }
    return out.joinToString(" ")
}





@Composable
private fun drawPieceOverlay(piece: Piece?, center: Offset, boardSize: IntSize) {
    val p = piece ?: return
    val sidePx = minOf(boardSize.width, boardSize.height).toFloat()
    if (sidePx <= 0f) return

    val cellPx = (sidePx / 8f).coerceAtLeast(1f)
    val density = LocalDensity.current
    val cellDp = with(density) { cellPx.toDp() }
    val fontSp = with(density) { (cellPx * 0.78f).toSp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - cellPx / 2f).toInt(),
                    (center.y - cellPx / 2f).toInt()
                )
            }
            .size(cellDp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = p.glyph,
            color = if (p.isWhite) Color.White else Color.Black,
            fontSize = fontSp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun BeatFishScreen(
    fen: String,
    lastFen: String?,
    showIntro: Boolean,
    onIntroDismiss: () -> Unit,
    onStartFromStart: () -> Unit,
    onStartFromLast: () -> Unit,
    light: Color,
    dark: Color,
    pieceStyle: PieceStyle,
    whiteBottom: Boolean,
    pieceSetKey: String = "cburnett",
    initialRecordGame: Boolean = false,
    headerContent: @Composable () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()




    // --- PGN export: auto-append to ONE master PGN file (user chooses once) ---
    var pendingMasterPgnGame by remember { mutableStateOf<String?>(null) }

    var beatFishImportPgnChunk by remember { mutableStateOf<String?>(null) }

    var isFishTurn by remember { mutableStateOf(false) }
    var fishSearchActive by remember { mutableStateOf(false) } // prevents double-start / stuck on move 1
    var statusText by remember { mutableStateOf("Ready") }
    var gameOverMessage by remember { mutableStateOf<String?>(null) }

    // Game result tag for PGN + to freeze play when the game has ended.
    var gameResultTag by remember { mutableStateOf<String?>(null) }
    var showGameOverDialog by remember { mutableStateOf(false) }

    var selectedSquare by remember { mutableStateOf<Int?>(null) }
    var pendingPromotionChoices by remember { mutableStateOf<List<PendingBeatPromotion>>(emptyList()) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var lastDragPos by remember { mutableStateOf<Offset?>(null) }

    var lastFromIdx by remember { mutableStateOf<Int?>(null) }
    var lastToIdx by remember { mutableStateOf<Int?>(null) }

    var legalityError by remember { mutableStateOf<String?>(null) }

    var playPhase by remember { mutableStateOf(PlayPhase.Idle) }
    var showLevelChooser by remember { mutableStateOf(false) }

    // --- Play difficulty (3 levels) ---
    // Level 1: "balance" move selection for 40 fish moves, then best move
    // Level 2: "balance" move selection for 20 fish moves, then best move
    // Level 3: best move always (current behavior)
    // Default is Level 1 unless user chooses otherwise.
    var playLevel by remember { mutableStateOf(1) }
    var playAsBlack by remember { mutableStateOf(false) } // if true: you play Black, fish plays White
    // --- Record Game (OTB recorder) ---
    var recordGameMode by remember { mutableStateOf(false) }
    var showRecordGameFairPlayNotice by remember { mutableStateOf(false) }
    var showRecordGameResetNotice by remember { mutableStateOf(false) }

    // Per-ply time spent by the player who made the move (seconds). Index 0 = ply 1.
    val recordMoveTimesSec = remember { mutableStateListOf<Int>() }

    // Ascending clocks (seconds) for each side while recording.
    var recordWhiteClockSec by rememberSaveable { mutableStateOf(0) }
    var recordBlackClockSec by rememberSaveable { mutableStateOf(0) }

    // Track current side-to-move "thinking" segment start (ms). Used to compute per-move duration.
    var recordTurnStartMs by rememberSaveable { mutableStateOf<Long?>(null) }

    // --- Fish move think time (seconds) ---
    val playPrefs = remember { ctx.getSharedPreferences("bf_play_prefs", Context.MODE_PRIVATE) }
    var fishThinkSecondsText by rememberSaveable {
        mutableStateOf((playPrefs.getInt("fish_think_seconds", 5)).toString())
    }

    LaunchedEffect(fishThinkSecondsText) {
        val v = fishThinkSecondsText.toIntOrNull()?.coerceIn(2, 60) ?: 5
        playPrefs.edit().putInt("fish_think_seconds", v).apply()
    }


    val fishThinkSeconds: Int = fishThinkSecondsText.toIntOrNull()?.coerceIn(2, 60) ?: 5
    val fishMoveTimeMs: Int = fishThinkSeconds * 1000

    var showAnnotateChoiceDialog by remember { mutableStateOf(false) }

    var moveNowRequested by remember { mutableStateOf(false) }

    fun formatMmSs(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        val mm = s / 60
        val ss = s % 60
        return String.format("%02d:%02d", mm, ss)
    }

    // Inject per-ply "{MM:SS}" comments right after each SAN move token in a movetext ribbon.
    // Best-effort: does not try to be a full PGN parser, but works well with ribbons generated by this screen.
    fun injectMoveTimesIntoRibbon(ribbon: String, timesSec: List<Int>): String {
        if (timesSec.isEmpty()) return ribbon
        val moveNoRe = Regex("""^\d+\.{1,3}$""")
        val resultRe = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")
        val out = StringBuilder(ribbon.length + timesSec.size * 10)

        var ply = 0
        var i = 0
        while (i < ribbon.length) {
            val ch = ribbon[i]
            if (ch == '{') {
                // Copy existing comment verbatim.
                val start = i
                var j = i + 1
                while (j < ribbon.length && ribbon[j] != '}') j++
                val end = if (j < ribbon.length) j + 1 else ribbon.length
                out.append(ribbon.substring(start, end))
                i = end
                continue
            }
            if (ch.isWhitespace()) {
                out.append(ch)
                i++
                continue
            }

            // Token
            val start = i
            var end = i
            while (end < ribbon.length && !ribbon[end].isWhitespace() && ribbon[end] != '{' && ribbon[end] != '}') end++
            val tok = ribbon.substring(start, end)
            out.append(tok)

            if (!moveNoRe.matches(tok) && !resultRe.matches(tok)) {
                ply++
                val t = timesSec.getOrNull(ply - 1)
                if (t != null) {
                    out.append(" {").append(formatMmSs(t)).append("}")
                }
            }

            i = end
        }
        return out.toString().replace(Regex("""\s+"""), " ").trim()
    }

    fun rebuildRecordRibbon(movesNow: List<BFMove>): String {
        // Build base ribbon from SAN + inject per-move time comments.
        val base = buildBeatFishMoveRibbon(movesNow)
        return injectMoveTimesIntoRibbon(base, recordMoveTimesSec.toList())
    }

    // --- Fish turn synchronization + "punish blunder then go back to equality" ---
    val fishTurnMutex = remember { kotlinx.coroutines.sync.Mutex() }



    val startingFen = remember(fen) { fen.ifBlank { START_FEN } }
    var effectiveFen by remember(startingFen) { mutableStateOf(startingFen) }

    var setupWhiteBottom by remember(effectiveFen) { mutableStateOf(true) }
    var setupBlackToMove by remember(effectiveFen) {
        mutableStateOf((effectiveFen.split(" ").getOrNull(1) ?: "w") == "b")
    }

    val initialUiPieces = remember(effectiveFen) {
        val b = LibBoard().apply {
            runCatching { loadFromFen(effectiveFen) }.onFailure { loadFromFen(START_FEN) }
        }
        boardToUiPieces(b)
    }
    val editablePieces = remember(effectiveFen) { mutableStateListOf(*initialUiPieces) }


    // Session (initialize a playable session immediately so the user can move right away)
    var isSession by remember { mutableStateOf(true) }
    var sessionBoard by remember(effectiveFen) {
        mutableStateOf<LibBoard?>(LibBoard().apply {
            runCatching { loadFromFen(effectiveFen) }.onFailure { loadFromFen(START_FEN) }
        })
    }
    var sessionStartFen by remember(effectiveFen) { mutableStateOf<String?>(sessionBoard?.fen ?: effectiveFen) }
    var playingAsWhite by remember { mutableStateOf(true) }
    var whiteBottomSession by remember { mutableStateOf(true) }

    // Keep derived session-side flags in sync with the user's choice (Play as Black).
    LaunchedEffect(playAsBlack, isSession, recordGameMode) {
        if (recordGameMode) {
            // In recorder mode, both sides are played manually. Keep a stable, simple orientation.
            playingAsWhite = true
            if (isSession) whiteBottomSession = true
            return@LaunchedEffect
        }
        playingAsWhite = !playAsBlack
        if (isSession) whiteBottomSession = playingAsWhite
    }



    val masterPgnCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-chess-pgn")
    ) { uri: Uri? ->
        val gamePgn = pendingMasterPgnGame
        pendingMasterPgnGame = null
        if (uri == null || gamePgn.isNullOrBlank()) return@rememberLauncherForActivityResult

        // Persist permission so we can keep appending later without asking again.
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
        }

        setMasterPgnUri(ctx, uri)

        scope.launch {
            appendPgnGameToMasterUri(context = ctx, uri = uri, gamePgn = gamePgn)
                .onSuccess {
                    Toast.makeText(ctx, "Saved to master PGN", Toast.LENGTH_SHORT).show()
                }
                .onFailure { t ->
                    Toast.makeText(ctx, "Save failed: ${t.message ?: "error"}", Toast.LENGTH_LONG).show()
                }
        }
    }


    LaunchedEffect(showIntro) {
        if (showIntro) onIntroDismiss()
    }

    val uciLines by ProcEngine.lines.collectAsState(initial = emptyList())
    val rawScoreCp by ProcEngine.scoreCp.collectAsState(initial = null)
    val activeEngineFen by ProcEngine.activeFen.collectAsState(initial = null)
    val engineLock = remember { Mutex() }
    // ===================== ENGINE (MERGED) =====================

    // Eval bar (White POV)
    var bfEvalCp by remember { mutableStateOf<Int?>(null) }


    // FEN we last asked the engine to evaluate
    var bfEngineFen by remember { mutableStateOf<String?>(null) }

    var pvLines by remember { mutableStateOf<List<BeatPvLine>>(emptyList()) }


    LaunchedEffect(recordGameMode) {
        if (recordGameMode) {
            runCatching { ProcEngine.send("stop") }
            runCatching { ProcEngine.clearOutput() }
            bfEvalCp = null
            bfEngineFen = null
            pvLines = emptyList()
        }
    }

    if (showRecordGameFairPlayNotice) {
        AlertDialog(
            onDismissRequest = { showRecordGameFairPlayNotice = false },
            title = { Text("Record Game fair-play notice") },
            text = {
                Text(
                    "Record Game is for entering moves without engine assistance. " +
                            "If you leave TrainerFish while a recorded game is in progress, " +
                            "the current record will reset to the starting position and the move list will be lost."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRecordGameFairPlayNotice = false }) {
                    Text("I understand")
                }
            }
        )
    }

    if (showRecordGameResetNotice) {
        AlertDialog(
            onDismissRequest = { showRecordGameResetNotice = false },
            title = { Text("Recorded game reset") },
            text = {
                Text(
                    "TrainerFish lost focus while Record Game was in progress. " +
                            "To keep Record Game clean and engine-free, the recorded moves were discarded " +
                            "and the board was reset to the starting position."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRecordGameResetNotice = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Analyze (MultiPV) UI state
    var multiPv by remember { mutableStateOf(1) }          // 1..4 (fixed to 1 for cleaner UI)


    var analysisAnchor by remember { mutableStateOf(0) }   // uciLines index marker (legacy)
    var analyzeRequestAnchor by remember { mutableStateOf(0) } // [OK] anchor for current Analyze search (stable)

    fun whiteToMoveFen(fenStr: String?): Boolean {
        val parts = fenStr?.split(' ') ?: return true
        return parts.getOrNull(1) == "w"
    }

    fun evalTextFromInfo(infoLine: String): String {
        val mate = Regex("""\bscore\s+mate\s+(-?\d+)""")
            .find(infoLine)?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (mate != null) return "#$mate"

        val cp = Regex("""\bscore\s+cp\s+(-?\d+)""")
            .find(infoLine)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return if (cp != null) {
            val v = cp / 100.0
            if (v >= 0) String.format("+%.2f", v) else String.format("%.2f", v)
        } else {
            "..."
        }
    }

    /**
     * Convert a PV list of UCI moves into a numbered SAN/LAN-ish line for display.
     * This is meant to be readable like the Opening/Play move list.
     */
    fun pvToSanNumbered(startFen: String, uciMoves: List<String>, maxPlies: Int = 8): String {
        val b = LibBoard().apply { runCatching { loadFromFen(startFen) } }
        val parts = startFen.split(' ')
        var moveNo = parts.getOrNull(5)?.toIntOrNull() ?: 1
        val out = ArrayList<String>(maxPlies + 2)

        for (uci in uciMoves.take(maxPlies)) {
            val mv = bfUciToMoveOnBoard(b, uci) ?: break
            val before = LibBoard().apply { loadFromFen(b.fen) }
            val isWhiteMove = (b.sideToMove == Side.WHITE)

            val san = bfPrettySan(before, mv, isWhiteMove = isWhiteMove)
            val displaySan = bfSanToFanDisplay(san)

            if (isWhiteMove) {
                out += "${moveNo}. $displaySan"
            } else {
                out += displaySan
                moveNo++
            }

            b.doMove(mv)
        }
        return out.joinToString(" ")
    }

    // ---------------- Annotation helpers ----------------

    fun idxToSq(idx0: Int): String {
        val file = idx0 and 7
        val rank = (idx0 ushr 3) + 1
        val f = ('a'.code + file).toChar()
        return "" + f + rank
    }

    fun bookMoveToUci(mv: com.tonorbe.trainerfish.opening.BookMove): String {
        val from = idxToSq(mv.from)
        val to = idxToSq(mv.to)
        val promo = when (mv.promo) {
            1 -> "q"
            2 -> "r"
            3 -> "b"
            4 -> "n"
            else -> ""
        }
        return from + to + promo
    }

    suspend fun bestLineForFenWhitePov(
        fenNow: String,
        movetimeMs: Int,
        pvPlies: Int
    ): Pair<Int?, List<String>> = withContext(Dispatchers.IO) {
        engineLock.withLock {
            runCatching { ProcEngine.start("annotate") }.onFailure { return@withContext (null to emptyList()) }
            runCatching { ProcEngine.setMultiPv(1) }

            // IMPORTANT: ProcEngine.lines is capped (200 lines). If we anchor by list size,
            // it can stop growing once the cap is hit, making us miss 'bestmove' during long annotations.
            // So we clear the captured output before each eval.
            ProcEngine.clearOutput()
            val anchor = 0
            evaluateFenSafely(fenNow, movetimeMs)

            // Force a minimum thinking time even if the engine returns early.
            delay(movetimeMs.toLong())

            // Wait a bit for a bestmove (best effort).
            runCatching {
                withTimeout(movetimeMs.toLong() + 350L) {
                    while (true) {
                        val snap = ProcEngine.lines.value
                        if (snap.size > anchor && snap.subList(anchor, snap.size).any { it.startsWith("bestmove") }) break
                        delay(35L)
                    }
                }
            }

            val snap = ProcEngine.lines.value
            val info = snap.drop(anchor).asReversed().firstOrNull { ln ->
                // Some positions emit score lines without a PV at very low depth; score is enough for annotation.
                ln.startsWith("info ") &&
                        ln.contains(" score ") &&
                        (!ln.contains(" multipv ") || ln.contains(" multipv 1 "))
            }

            val cp = info?.let { Regex("\\bscore\\s+cp\\s+(-?\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            val mate = info?.let { Regex("\\bscore\\s+mate\\s+(-?\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            val signed = when {
                cp != null -> cp
                mate != null && mate > 0 -> 900
                mate != null && mate < 0 -> -900
                else -> null
            }

            val wtm = whiteToMoveFen(fenNow)
            val cpWhite = signed?.let { s -> if (wtm) s else -s }

            val pvPart = info?.substringAfter(" pv ", "") ?: ""
            val pv = pvPart.split(' ').filter { it.length >= 4 }.take(pvPlies)

            cpWhite to pv
        }
    }


    // ---------------- Annotation helpers ----------------

    data class AnnotationResult(
        val annotatedRibbon: String,
        val summary: String,
        // White POV eval-after for each ply (null if unavailable). Stored for future line-graph.
        val cpSeriesAfterWhite: List<Int?> = emptyList(),
        // PV (best line) data keyed by the START ply (0-based) where the PV begins.
        // For a comment attached after ply N, the PV typically begins from ply (N-1).
        val pvByStartPly: Map<Int, Map<String, BeatPvData>> = emptyMap()
    )

    fun nagFor(sev: String): String = when (sev) {
        "dubious" -> "?!"
        "mistake" -> "?"
        "blunder" -> "??"
        else -> ""
    }

    fun phraseFor(sev: String, plyIndex0: Int): String {
        val map = mapOf(
            "dubious" to listOf(
                "This is dubious.",
                "Questionable move.",
                "That looks a bit off.",
                "Dubious choice.",
                "Not the best idea."
            ),
            "mistake" to listOf(
                "This is a mistake.",
                "I wouldn't play this.",
                "That throws away advantage.",
                "That's inaccurate.",
                "A clear mistake."
            ),
            "blunder" to listOf(
                "This is a blunder!",
                "Big mistake.",
                "That loses material/position.",
                "A serious blunder.",
                "Oops - that collapses."
            )
        )
        val arr = map[sev] ?: listOf("Inaccuracy.")
        return arr[(plyIndex0.coerceAtLeast(0)) % arr.size]
    }



    fun formatCpWhite(cpWhite: Int?): String {
        if (cpWhite == null) return "?"
        val v = cpWhite / 100.0
        return if (v >= 0) String.format("+%.2f", v) else String.format("%.2f", v)
    }

    fun parseMultiPv(lines: List<String>, fromIndex: Int, want: Int): List<BeatPvLine> {
        val slice = lines.drop(fromIndex)
        val infos = slice.filter { it.startsWith("info ") && it.contains(" multipv ") && it.contains(" pv ") }

        val bestByIdx = mutableMapOf<Int, BeatPvLine>()

        for (ln in infos) {
            val mp = Regex("""\bmultipv\s+(\d+)""").find(ln)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (mp < 1 || mp > want) continue

            val evalText = evalTextFromInfoWhitePov(ln, fen)



            // [OK] Convert engine PV (UCI moves) into SAN sequence for display (like Play mode)
            fun pvToSan(startFen: String, uciMoves: List<String>, maxPlies: Int = 12): String {
                val b = LibBoard().apply { runCatching { loadFromFen(startFen) } }
                val out = ArrayList<String>(maxPlies)
                for (uci in uciMoves.take(maxPlies)) {
                    val mv = bfUciToMoveOnBoard(b, uci) ?: break
                    val before = LibBoard().apply { loadFromFen(b.fen) }
                    val isWhiteMove = (b.sideToMove == Side.WHITE)
                    b.doMove(mv)
                    out += bfPrettySan(before, mv, isWhiteMove = isWhiteMove)
                }
                return out.joinToString(" ")
            }



            val pvPart = ln.substringAfter(" pv ", "")
            val moves = pvPart.split(' ').filter { it.length >= 4 }

            if (moves.isNotEmpty()) {
                bestByIdx[mp] = BeatPvLine(mp, evalText, moves)
            }
        }

        return (1..want).mapNotNull { bestByIdx[it] }
    }

    // Start in READY with an interactive board; launcher menu is shown separately.
    var stage by remember { mutableStateOf(BeatFishStage.READY) }
    var mode by remember { mutableStateOf(BeatFishMode.PLAY) }
    var setupReturnMode by remember { mutableStateOf(BeatFishMode.PLAY) }

    // Distinguish between post-game review (saved CPs) and free analysis (live engine CPs)
    var analyzeContext by remember { mutableStateOf(BeatFishAnalyzeContext.ANALYSIS_BOARD) }

    // --- Launcher menu + progressive UI ---
    var showLauncherMenu by remember { mutableStateOf(true) }
    var launcherDetail by remember { mutableStateOf<String?>(null) }

    // ----- Master PGN browser (Open saved annotated games) -----
    var showMasterPgnPicker by remember { mutableStateOf(false) }
    var isLoadingMasterPgn by remember { mutableStateOf(false) }
    var masterPgnError by remember { mutableStateOf<String?>(null) }
    var masterPgnEntries by remember { mutableStateOf<List<MasterPgnGameEntry>>(emptyList()) }

    // Keep the editor list in sync if effectiveFen is replaced (e.g., after loading a saved game)
    fun ensureEditorFromEffectiveFen() {
        val b = LibBoard().apply {
            runCatching { loadFromFen(effectiveFen) }.onFailure { loadFromFen(START_FEN) }
        }
        val ui = boardToUiPieces(b)
        if (editablePieces.size != 64) {
            editablePieces.clear()
            editablePieces.addAll(ui)
        } else {
            for (i in 0 until 64) editablePieces[i] = ui[i]
        }
    }

    // [OK] palette selection (top row = BLACK, bottom row = WHITE)
    var pieceToAdd by remember { mutableStateOf<Piece?>(null) }

    val fixedMultiPv = 2
    var evalAnchor by remember { mutableStateOf(0) }   // [OK] NEW: isolate eval to this screen's requests

    // Current position being analyzed in ANALYZE mode
    var analysisFen by remember { mutableStateOf<String?>(null) }
    // ECO classification shown in ANALYZE header
    var analysisEcoText by remember { mutableStateOf<String?>(null) }

    fun stopBeatFishEngineForMenu() {
        // One shared engine powers Analysis and Beat-the-Fish play. When the user opens
        // the hamburger launcher, stop any perpetual analysis/search first so the next
        // selected mode starts with a clean engine pipe.
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        fishSearchActive = false
        isFishTurn = false
        bfEvalCp = null
        pvLines = emptyList()
        analysisFen = null
        bfEngineFen = sessionBoard?.fen ?: effectiveFen
        selectedSquare = null
        pendingPromotionChoices = emptyList()
    }

    fun openBeatFishLauncherMenu() {
        stopBeatFishEngineForMenu()
        showLauncherMenu = true
        launcherDetail = null
        showLevelChooser = false
        playPhase = PlayPhase.Idle
        statusText = if (recordGameMode) "Record Game paused." else "Ready"
    }

    // ---------------- Annotations (ANALYZE) ----------------
    var showAnnotateDialog by remember { mutableStateOf(false) }
    var annotateText by remember { mutableStateOf("") }
    var isAnnotating by remember { mutableStateOf(false) }
    var annotatedRibbon by remember { mutableStateOf<String?>(null) }
    // Stored White-POV evals per ply from Annotate (for future line graph)
    var annotatedCpSeries by remember { mutableStateOf<List<Int?>>(emptyList()) }
    // PV data for clickable "Best:" lines inside comments
    var annotatedPvByStartPly by remember { mutableStateOf<Map<Int, Map<String, BeatPvData>>>(emptyMap()) }

    // Live CP series for Analysis Board (index = position index; 0 = start). Filled from realtime engine eval.
    val analysisLiveCpByPos = remember { mutableStateListOf<Int?>() }

    // ---------------- Background annotation (PLAY) ----------------
    // We pre-build annotations while the game is progressing, so post-game analysis opens immediately.
    var bgAnnotationRes by remember { mutableStateOf<AnnotationResult?>(null) }
    var bgAnnotationPlyCount by remember { mutableStateOf(0) }
    var bgAnnotRunning by remember { mutableStateOf(false) }
    var lastMoveAtMs by remember { mutableStateOf(0L) }




// ---------------- User-added variations (Analyze mode) ----------------
// Key: base ply (0-based index into navPositions, where the variation starts)
// Value: list of UCI-lines (each is a list of UCI moves from that base ply)
    var userVarByBasePly by remember { mutableStateOf<Map<Int, List<List<String>>>>(emptyMap()) }

// When the user starts making moves from a historical position (navMode), we treat it as editing a line.
    var editBasePly by remember { mutableStateOf<Int?>(null) }
    val editLineUci = remember { mutableStateListOf<String>() }

    fun resetEditLine() {
        editBasePly = null
        editLineUci.clear()
    }

    fun upsertUserVariation(basePly: Int, lineUci: List<String>) {
        if (basePly <= 0 || lineUci.isEmpty()) return

        val normalized = lineUci.map { it.lowercase(Locale.ROOT) }
        val cur = userVarByBasePly.toMutableMap()
        val list = (cur[basePly] ?: emptyList()).toMutableList()

        fun isSame(a: List<String>, b: List<String>): Boolean {
            if (a.size != b.size) return false
            return a.indices.all { a[it].lowercase(Locale.ROOT) == b[it].lowercase(Locale.ROOT) }
        }

        fun isPrefix(prefix: List<String>, full: List<String>): Boolean {
            if (prefix.size > full.size) return false
            return prefix.indices.all {
                prefix[it].lowercase(Locale.ROOT) == full[it].lowercase(Locale.ROOT)
            }
        }

        val exactIdx = list.indexOfFirst { isSame(it, normalized) }
        if (exactIdx >= 0) {
            list[exactIdx] = normalized
            cur[basePly] = list
            userVarByBasePly = cur
            return
        }

        // Only replace the currently-extended line if it is a strict prefix of the new one.
        // Keep sibling and ancestor lines intact so true nesting can still be rendered.
        val replaceIdx = list
            .mapIndexedNotNull { i, old ->
                if (isPrefix(old, normalized)) i else null
            }
            .maxByOrNull { list[it].size }

        if (replaceIdx != null) {
            list[replaceIdx] = normalized
        } else {
            list.add(normalized)
        }

        list.sortWith(compareBy<List<String>> { it.joinToString(" ") })
        cur[basePly] = list
        userVarByBasePly = cur
    }


    // Small helper: update analysis fen (and eval bar source) from a board
    fun setAnalysisFromBoard(board: LibBoard) {
        val fenNow = board.fen
        analysisFen = fenNow
        bfEngineFen = fenNow
    }



    var navMode by remember { mutableStateOf(false) }
    var navPositions by remember { mutableStateOf<List<String>>(emptyList()) }
    var navIndex by remember { mutableStateOf(0) }

    val moves = remember { mutableStateListOf<BFMove>() }

    var moveCounter by remember { mutableStateOf(1) }

    var autosaveJob by remember { mutableStateOf<Job?>(null) }

    fun isUnfinishedGame(): Boolean {
        return !recordGameMode &&
                isSession &&
                sessionStartFen != null &&
                moves.isNotEmpty() &&
                gameResultTag == null
    }

    fun requestAutosave(reason: String) {
        if (!isUnfinishedGame()) return

        // debounce writes (fast move sequences / engine replies)
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(350L)
            val startFen = sessionStartFen ?: return@launch
            val thinkSec = fishThinkSeconds // already clamped min=2 in your code
            withContext(Dispatchers.IO) {
                saveBeatFishResumePgn(
                    ctx = ctx,
                    startFen = startFen,
                    moves = moves.toList(),
                    playAsBlack = playAsBlack,
                    playLevel = playLevel,
                    thinkSec = thinkSec
                )
            }
        }
    }


// Compute the game's opening classification (ECO + name) for the ANALYZE header.
// EcoClassifier matches by a normalized 4-field FEN key, so to get a stable opening label
// (even deep into the middlegame) we replay the game from the session start FEN and keep the
// LAST non-null match.
    LaunchedEffect(mode, isSession, sessionStartFen, moves.size) {
        if (!isSession || mode != BeatFishMode.ANALYZE) {
            analysisEcoText = null
            return@LaunchedEffect
        }

        val startFen = sessionStartFen ?: START_FEN
        val b = LibBoard().apply {
            runCatching { loadFromFen(startFen) }.onFailure { loadFromFen(START_FEN) }
        }

        // Warmup: ensures eco_book.json is loaded.
        runCatching { EcoClassifier.getAllEntries(ctx) }

        var best: com.tonorbe.trainerfish.opening.EcoEntry? = EcoClassifier.classify(ctx, b.fen)
        val snap = moves.toList()
        val maxPlies = minOf(snap.size, 60)

        for (i in 0 until maxPlies) {
            val uci = snap[i].uci
            val mm = bfUciToMoveOnBoard(b, uci) ?: break
            b.doMove(mm)
            val e = EcoClassifier.classify(ctx, b.fen)
            if (e != null && e.code.isNotBlank()) best = e
        }

        analysisEcoText = if (best != null && best!!.code.isNotBlank()) {
            buildString {
                append("ECO ")
                append(best!!.code)
                if (best!!.name.isNotBlank()) {
                    append(" - ")
                    append(best!!.name)
                }
            }
        } else null
    }

    // Master PGN picker dialog (opens the saved master PGN and lets user choose a game)
    if (showMasterPgnPicker) {
        AlertDialog(
            onDismissRequest = { showMasterPgnPicker = false },
            title = { Text("Open saved game") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    if (isLoadingMasterPgn) {
                        Text("Loading...", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (masterPgnError != null) {
                        Text(
                            text = masterPgnError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (masterPgnEntries.isEmpty()) {
                        Text("No games found in master PGN.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(masterPgnEntries) { e ->


                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val data = loadGameDataFromPgnChunk(context = ctx, chunk = e.chunk)

                                            if (data == null) {
                                                statusText = "Could not load that game."
                                            } else {
                                                // Load into analysis board
                                                sessionStartFen = data.startFen
                                                moves.clear()
                                                moves.addAll(data.moves)

                                                navMode = true
                                                resetEditLine()
                                                userVarByBasePly = emptyMap()

                                                navPositions = data.positions
                                                navIndex = data.positions.lastIndex

                                                val fenNav = data.positions.lastOrNull() ?: data.startFen
                                                val bb = LibBoard().apply { loadFromFen(fenNav) }
                                                sessionBoard = bb

                                                isSession = true
                                                isFishTurn = false
                                                stage = BeatFishStage.READY
                                                mode = BeatFishMode.ANALYZE
                                                showLauncherMenu = false
                                                // When opening a saved PGN game, preserve the original movetext (including {comments} and (variations))
                                                // so the analysis move list can render it exactly like external PGN readers.
                                                bgAnnotationRes = null
                                                annotatedRibbon = data.rawMoveText.takeIf { it.isNotBlank() }
                                                // No engine-generated annotations loaded here; those can be regenerated via Annotate.
                                                annotatedCpSeries = emptyList()
                                                annotatedPvByStartPly = emptyMap()
                                                analysisLiveCpByPos.clear()

                                                // Sync analysis/eval sources
                                                analysisFen = fenNav
                                                bfEngineFen = fenNav
                                                val a = uciLines.size
                                                analysisAnchor = a
                                                analyzeRequestAnchor = a
                                                evalAnchor = a

                                                statusText = "Loaded saved game."
                                            }
                                            showMasterPgnPicker = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 6.dp)
                                ) {


                                    val firstTag = e.chunk.lineSequence().firstOrNull { it.startsWith("[") } ?: "[Saved Game]"
                                    val previewMoves = e.chunk.lineSequence()
                                        .firstOrNull { it.isNotBlank() && !it.startsWith("[") }
                                        ?.trim()
                                        .orEmpty()
                                        .take(80)

                                    Text(
                                        text = firstTag,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (previewMoves.isNotBlank()) {
                                        Text(
                                            text = previewMoves,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }


                                }
                                Divider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMasterPgnPicker = false }) { Text("Close") }
            }
        )
    }

// Deep CP snapshots by ply (index = ply count; 0 = start position).
// We continuously overwrite the CURRENT ply's slot while the engine thinks,
// so when a move is made we already have the deepest available eval for the position just played.
    val cpSnapshotsWhiteByPly = remember { mutableStateListOf<Int?>() }

// Keep the snapshot list sized to (moves.size + 1): start position + one entry per ply.
    LaunchedEffect(moves.size) {
        val need = moves.size + 1
        while (cpSnapshotsWhiteByPly.size < need) cpSnapshotsWhiteByPly.add(null)
        while (cpSnapshotsWhiteByPly.size > need) cpSnapshotsWhiteByPly.removeAt(cpSnapshotsWhiteByPly.lastIndex)
    }

// Keep the live ANALYSIS series sized to (moves.size + 1) when in Analysis Board.
// In Game analysis we don't mutate this; the graph uses saved series instead.
    LaunchedEffect(moves.size, analyzeContext) {
        if (analyzeContext != BeatFishAnalyzeContext.ANALYSIS_BOARD) return@LaunchedEffect
        val need = moves.size + 1
        while (analysisLiveCpByPos.size < need) analysisLiveCpByPos.add(null)
        while (analysisLiveCpByPos.size > need) analysisLiveCpByPos.removeAt(analysisLiveCpByPos.lastIndex)
    }


// Save the latest (deepening) eval into the current ply slot.
// IMPORTANT: only snapshot when the engine result actually belongs to the *current* displayed FEN.
// Otherwise, when we append a move, moves.size changes first while the engine is still returning
// the old position's eval - which would incorrectly write the "before" cp into the "after" slot.
    LaunchedEffect(bfEvalCp, activeEngineFen, isSession, mode, navMode) {
        if (!isSession) return@LaunchedEffect
        if (mode != BeatFishMode.PLAY) return@LaunchedEffect
        if (recordGameMode) return@LaunchedEffect

        val fenNow = sessionBoard?.fen ?: return@LaunchedEffect
        val fenEval = activeEngineFen ?: return@LaunchedEffect
        if (fenEval != fenNow) return@LaunchedEffect

        if (cpSnapshotsWhiteByPly.isNotEmpty()) {
            val idx = moves.size.coerceIn(0, cpSnapshotsWhiteByPly.lastIndex)
            cpSnapshotsWhiteByPly[idx] = bfEvalCp
        }
    }

// Save the latest realtime eval into the current POSITION slot while in Analysis Board.
// We write into the slot that corresponds to the currently displayed position:
// - navMode: navIndex (0..last)
// - otherwise: current end position = moves.size
    LaunchedEffect(bfEvalCp, activeEngineFen, isSession, mode, analyzeContext, navMode, navIndex, moves.size) {
        if (!isSession) return@LaunchedEffect
        if (mode != BeatFishMode.ANALYZE) return@LaunchedEffect
        if (analyzeContext != BeatFishAnalyzeContext.ANALYSIS_BOARD) return@LaunchedEffect

        val fenNow = sessionBoard?.fen ?: return@LaunchedEffect
        val fenEval = activeEngineFen ?: return@LaunchedEffect
        if (fenEval != fenNow) return@LaunchedEffect

        val idx = (if (navMode) navIndex else moves.size).coerceAtLeast(0)
        if (analysisLiveCpByPos.isNotEmpty()) {
            val safe = idx.coerceIn(0, analysisLiveCpByPos.lastIndex)
            analysisLiveCpByPos[safe] = bfEvalCp
        }
    }




    // ---------------- Game end helpers (PLAY) ----------------
    fun fenRepetitionKey(fenStr: String): String {
        // Repetition is based on: piece placement, side to move, castling rights, en-passant target.
        val p = fenStr.trim().split(" ")
        return listOf(
            p.getOrNull(0).orEmpty(),
            p.getOrNull(1).orEmpty(),
            p.getOrNull(2).orEmpty(),
            p.getOrNull(3).orEmpty()
        ).joinToString(" ")
    }

    fun isThreefoldClaimable(): Boolean {
        val curFen = sessionBoard?.fen ?: return false
        val key = fenRepetitionKey(curFen)
        val n = navPositions.count { fenRepetitionKey(it) == key }
        return n >= 3
    }

    fun isFiftyMoveClaimable(): Boolean {
        val curFen = sessionBoard?.fen ?: return false
        val parts = curFen.trim().split(" ")
        val halfMoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0
        return halfMoveClock >= 100 // 50 moves each side without pawn move/capture
    }

    fun finalVerdict(cpWhite: Int?, finalBoard: LibBoard? = null): String {
        finalBoard?.let { b ->
            if (b.isMated) {
                val winner = if (b.sideToMove == Side.WHITE) Side.BLACK else Side.WHITE
                val side = if (winner == Side.WHITE) "White" else "Black"
                return "Final evaluation: checkmate. $side wins."
            }
            if (b.isStaleMate) return "Final evaluation: draw by stalemate."
            if (b.isInsufficientMaterial) return "Final evaluation: draw by insufficient material."
            if (b.isDraw) {
                return when {
                    isThreefoldClaimable() -> "Final evaluation: draw by threefold repetition."
                    isFiftyMoveClaimable() -> "Final evaluation: draw by 50-move rule."
                    else -> "Final evaluation: draw."
                }
            }
        }

        if (cpWhite == null) return "Final evaluation: unclear."

        val pawns = cpWhite / 100.0
        val abs = kotlin.math.abs(cpWhite)
        val side = if (cpWhite >= 0) "White" else "Black"
        return when {
            abs < 50 -> "Final evaluation: roughly equal."
            abs < 100 -> "Final evaluation: $side is slightly better (${String.format("%.2f", pawns)})."
            abs < 200 -> "Final evaluation: $side is much better (${String.format("%.2f", pawns)})."
            else -> "Final evaluation: $side is winning (${String.format("%.2f", pawns)})."
        }
    }

    suspend fun buildAnnotationsOneShot(
        startFen: String,
        movesNow: List<BFMove>,
        cpSnapshotsWhiteByPly: List<Int?>? = null,
        perMoveMs: Long = 1000L,
        onProgress: suspend (cur: Int, total: Int) -> Unit = { _, _ -> }
    ): AnnotationResult = withContext(Dispatchers.IO) {

        // Local opening book removed from Trainer Fish base build.
        // Beat Fish is engine-only for now. Later we can query CoC through a provider.

        val total = movesNow.size.coerceAtLeast(0)
        val extras = ArrayList<String?>(total).apply { repeat(total) { add(null) } }

        // ---------------- Opening phase: detect TRUE book exit ----------------
        val bBook = LibBoard().apply {
            runCatching { loadFromFen(startFen) }.onFailure { loadFromFen(START_FEN) }
        }

        var lastInBookFen = bBook.fen
        var lastInBookPly = 0 // number of plies played that are still in book (0 = none)
        var outOfBookFen: String? = null
        var outOfBookDetected = false

        for (i in movesNow.indices) {
            onProgress(i + 1, total)

            val fenBefore = bBook.fen
            val node = if (BinaryOpeningBook.isLoaded) BinaryOpeningBook.lookup(fenBefore) else null
            val hasMoves = node != null && node.moves.isNotEmpty()

            if (!hasMoves) {
                // TRUE out-of-book: the current position is not in the tree (or has no continuations).
                // lastInBookFen/lastInBookPly already represent the LAST known in-book position.
                outOfBookDetected = true
                outOfBookFen = fenBefore
                break
            }

            val playedUci = movesNow[i].uci.lowercase(Locale.ROOT)
            val played4 = playedUci.take(4)

            // Some book moves include promotion suffix (e.g., e7e8q). Compare using the first 4 chars,
            // and accept either exact match or 4-char match.
            val inNode = node!!.moves.any { bm ->
                val buci = bookMoveToUci(bm).lowercase(Locale.ROOT)
                buci == playedUci || buci.take(4) == played4
            }

            if (!inNode) {
                // TRUE out-of-book: the played move is not present in the book for this position.
                outOfBookDetected = true
                outOfBookFen = fenBefore // position BEFORE the out-of-book move
                break
            }

            // Still in book, and the played move exists in this node.
            val mv = movesNow[i]
            val mm = bfUciToMoveOnBoard(bBook, mv.uci)
            if (mm == null) {
                // Can't replay further; treat as out of book at this point.
                outOfBookDetected = true
                outOfBookFen = fenBefore
                break
            }
            bBook.doMove(mm)
            // Track the last position that is still in book (AFTER applying the move)
            lastInBookFen = bBook.fen
            lastInBookPly = i + 1

        }
        // If we never hit out-of-book while replaying, we stayed "in book" through the end.
        if (!outOfBookDetected) {
            outOfBookFen = bBook.fen
        }

        // Build a main-line continuation from the last in-book position (top by count), up to 10 plies (or until book ends).
        fun mainLineFromFen(fen: String, maxPlies: Int = 10): List<String> {
            val uciLine = ArrayList<String>()
            var fenTmp = fen
            var depth = 0
            while (depth < maxPlies) {
                val p = (if (BinaryOpeningBook.isLoaded) BinaryOpeningBook.lookup(fenTmp) else null) ?: break
                if (p.moves.isEmpty()) break
                val tm = p.moves.maxByOrNull { it.count } ?: break
                val u = bookMoveToUci(tm)
                uciLine.add(u)

                val bb = LibBoard().apply { loadFromFen(fenTmp) }
                val mm = bfUciToMoveOnBoard(bb, u) ?: break
                bb.doMove(mm)
                fenTmp = bb.fen
                depth++
            }
            return uciLine
        }

        // ECO classification based on the LAST in-book position (move just before out-of-book).


        runCatching { EcoClassifier.getAllEntries(ctx) } // warm-up ECO table (best-effort)


        val ecoEntry = runCatching { EcoClassifier.classify(ctx, lastInBookFen) }.getOrNull()

        // Build an opening header (shown at the very start) based on the LAST in-book position
        // Opening classification (based on LAST in-book position: the move just before out-of-book).
        // NOTE: This is a plain header line (NOT a {comment}) so the UI won't accidentally strip it.
        val openingHeaderLine: String? =
            if (ecoEntry != null && !ecoEntry.code.isNullOrBlank()) {
                buildString {
                    append("ECO ${ecoEntry.code}")
                    if (!ecoEntry.name.isNullOrBlank()) {
                        append(" - ")
                        append(ecoEntry.name)
                    }
                }
            } else null

        // ---------------- Engine phase ----------------
        // We still detect the true book-exit ply for opening commentary,
        // but we now record CP snapshots / annotations from move 1 (ply 0) onward.
        val outOfBookIdx0 = lastInBookPly.coerceAtLeast(0) // first out-of-book ply index
        val startEvalIdx0 = 0

        // Opening exit comment: show ONLY when we actually go out of book.
        // We also include:
        //  - Book main line: the book's top-count continuation from the last in-book position
        //  - Line played: the actual continuation that happened after leaving book

        // PV (best line) data keyed by start ply (0-based position index) for making "Best:" lines clickable.
        // For a comment attached AFTER ply N, its PV typically begins from the position BEFORE that move,
        // i.e., start ply (N-1).
        val pvByStartPly: MutableMap<Int, MutableMap<String, BeatPvData>> = mutableMapOf()

        val openingComment = buildString {
            append("Out of book.")

            val bookLineUci = mainLineFromFen(lastInBookFen, maxPlies = 10)
            if (bookLineUci.isNotEmpty()) {
                val san = pvToSanNumbered(startFen = lastInBookFen, uciMoves = bookLineUci, maxPlies = 10)
                append(" Book main line: $san")

                // Make BOOK moves clickable inside the comment.
                val key = lastInBookPly.coerceAtLeast(0)
                pvByStartPly.getOrPut(key) { mutableMapOf<String, BeatPvData>() }["BOOK"] = BeatPvData(
                    startFen = lastInBookFen,
                    uciMoves = bookLineUci
                )
            }

            // The actual moves that were played after leaving book (cap to 10 plies for readability)
            val linePlayedUci = movesNow
                .drop(outOfBookIdx0)
                .take(10)
                .map { it.uci }

            if (linePlayedUci.isNotEmpty()) {
                val san = pvToSanNumbered(startFen = lastInBookFen, uciMoves = linePlayedUci, maxPlies = 10)
                append(" Line played: $san")

                // Make LINE moves clickable inside the comment.
                val key = lastInBookPly.coerceAtLeast(0)
                pvByStartPly.getOrPut(key) { mutableMapOf<String, BeatPvData>() }["LINE"] = BeatPvData(
                    startFen = lastInBookFen,
                    uciMoves = linePlayedUci
                )
            }
        }


        // Attach openingHeaderComment to the very beginning of the ribbon.
        // Attach openingComment only at the true book exit moment.
        var leadingComment: String? = null
        if (lastInBookPly <= 0) {
            leadingComment = listOfNotNull(leadingComment, "{${openingComment}}").joinToString(" ")
        } else {
            val idx = lastInBookPly.coerceIn(0, extras.lastIndex.coerceAtLeast(0))
            if (extras.isNotEmpty()) {
                extras[idx] = "{${openingComment}}"
            }
        }



        val b = LibBoard().apply {
            runCatching { loadFromFen(startFen) }.onFailure { loadFromFen(START_FEN) }
        }

        // Advance board to the first move after the last book move.
        for (i in 0 until startEvalIdx0.coerceAtMost(movesNow.size)) {
            val mv = movesNow[i]
            val mm = bfUciToMoveOnBoard(b, mv.uci) ?: break
            b.doMove(mm)
        }

        // Evaluate remaining moves for inaccuracies/blunders.
        // Simplified logic:
        //   - For the first out-of-book position we evaluate the *position before the move* (2000ms)
        //   - For each out-of-book move we evaluate the *position after the move* (2000ms)
        //   - Compare eval(after) vs eval(before) from the mover's POV and apply thresholds.
        // We also store eval-after-per-move for later graphing.

        val evalAfterCpWhite = ArrayList<Int?>(movesNow.size).apply { repeat(movesNow.size) { add(null) } }
        val bestMoveFlags = BooleanArray(movesNow.size) { false }



        var prevCpWhite: Int? = null // evaluation of the position BEFORE the current move (White POV)

        for (i in startEvalIdx0 until movesNow.size) {
            onProgress(i + 1, total)

            val mv = movesNow[i]
            val fenBefore = b.fen
            val moverWhite = (b.sideToMove == Side.WHITE)

            // Prefer deep snapshots (these are the whole point of PLAY-time thinking).
            val snapBefore = cpSnapshotsWhiteByPly?.getOrNull(i)
            val snapAfterPlanned = cpSnapshotsWhiteByPly?.getOrNull(i + 1)

            // Prefer snapshot for the position BEFORE this move; else use the last known eval.
            // If still missing, do a tiny fallback eval so we don't propagate stale values.
            var beforeCpWhiteBase: Int? = snapBefore ?: prevCpWhite
            if (beforeCpWhiteBase == null) {
                beforeCpWhiteBase = bestLineForFenWhitePov(
                    fenNow = fenBefore,
                    movetimeMs = perMoveMs.toInt(),
                    pvPlies = 1
                ).first
            }

            // Apply the move to get fenAfter (needed for snapshot lookup and fallback eval)
            val mm = bfUciToMoveOnBoard(b, mv.uci) ?: break
            b.doMove(mm)
            val fenAfter = b.fen

            // IMPORTANT: if we don't have a snapshot for the AFTER position, DO NOT reuse "before".
            // That would shift/bleed values (and is exactly what causes graph/annotation desync).
            var afterCpWhite: Int? = snapAfterPlanned
            if (afterCpWhite == null) {
                afterCpWhite = bestLineForFenWhitePov(
                    fenNow = fenAfter,
                    movetimeMs = perMoveMs.toInt(),
                    pvPlies = 1
                ).first
            }

            if (i in evalAfterCpWhite.indices) evalAfterCpWhite[i] = afterCpWhite

            // Compute loss from mover's POV using CP deltas.
            val loss = if (beforeCpWhiteBase != null && afterCpWhite != null) {
                if (moverWhite) (beforeCpWhiteBase - afterCpWhite) else (afterCpWhite - beforeCpWhiteBase)
            } else null

            val sev = when {
                loss == null -> null
                loss >= 150 -> "blunder"
                loss >= 80  -> "mistake"
                loss >= 40  -> "dubious"
                else -> null
            }

            // Only now (if marked) do we ask engine for PV/best line.
            var pvBefore: List<String> = emptyList()
            if (sev != null) {
                val (_, pv) = bestLineForFenWhitePov(
                    fenNow = fenBefore,
                    movetimeMs = perMoveMs.toInt(),
                    pvPlies = 10
                )

                pvBefore = pv
                if (pvBefore.isNotEmpty()) {
                    pvByStartPly.getOrPut(i) { mutableMapOf() }["BEST"] =
                        BeatPvData(startFen = fenBefore, uciMoves = pvBefore)
                }

                val playedUci = movesNow[i].uci.lowercase(Locale.ROOT)
                val bestUci = pvBefore.firstOrNull()?.lowercase(Locale.ROOT)

                if (bestUci != null && bestUci == playedUci) {
                    bestMoveFlags[i] = true
                    prevCpWhite = afterCpWhite ?: beforeCpWhiteBase
                    continue
                }

                val phrase = phraseFor(sev, i)
                val beforeTxt = formatCpWhite(beforeCpWhiteBase)
                val afterTxt = formatCpWhite(afterCpWhite)

                val bestLine = if (pvBefore.isNotEmpty()) {
                    pvToSanNumbered(startFen = fenBefore, uciMoves = pvBefore, maxPlies = 10)
                } else ""

                val comment = buildString {
                    append(phrase)
                    append(" Eval: ")
                    append(beforeTxt)
                    append(" - ")
                    append(afterTxt)
                    if (bestLine.isNotBlank()) {
                        append(" Best: ")
                        append(bestLine)
                    }
                }

                val nag = nagFor(sev)
                val existing = extras[i]
                val add = "$nag {${comment}}"
                extras[i] = if (existing.isNullOrBlank()) add else (add + " " + existing)
            }

            // Carry forward
            prevCpWhite = afterCpWhite ?: beforeCpWhiteBase
        }


        val finalCpWhite = when {
            b.isMated -> {
                // Board is AFTER the last move. If side to move is mated, the other side has won.
                if (b.sideToMove == Side.WHITE) -100000 else 100000
            }
            b.isStaleMate || b.isInsufficientMaterial || b.isDraw -> 0
            else -> cpSnapshotsWhiteByPly?.getOrNull(movesNow.size)
                ?: bestLineForFenWhitePov(
                    fenNow = b.fen,
                    movetimeMs = perMoveMs.toInt(),
                    pvPlies = 1
                ).first
        }

        // Ensure the final plotted point reflects mate/draw instead of dropping back to null/zero.
        if (movesNow.isNotEmpty()) {
            evalAfterCpWhite[evalAfterCpWhite.lastIndex] = finalCpWhite
        }

        val verdict = finalVerdict(finalCpWhite, b)

        if (movesNow.isEmpty()) {
            leadingComment = listOfNotNull(leadingComment, "{${verdict}}").joinToString(" ")
        } else {
            val li = extras.lastIndex
            val cur = extras[li]
            extras[li] = when {
                cur.isNullOrBlank() -> "{${verdict}}"
                else -> cur + " {${verdict}}"
            }
        }

        // Build ribbon with PGN-style comments/NAGs.
        val sb = StringBuilder()
        if (!openingHeaderLine.isNullOrBlank()) {
            sb.append(openingHeaderLine).append('\n')
        }
        if (!leadingComment.isNullOrBlank()) {
            sb.append(leadingComment).append(' ')
        }

        for (idx in movesNow.indices) {
            val m = movesNow[idx]
            val extra = extras.getOrNull(idx)
            val markBest = (idx in bestMoveFlags.indices && bestMoveFlags[idx])

            if (m.isWhite) {
                if (sb.isNotEmpty()) sb.append(' ')
                if (markBest) sb.append("${m.moveNumber}. §${m.san}") else sb.append("${m.moveNumber}. ${m.san}")
            } else {
                sb.append(' ')
                if (markBest) sb.append("§${m.san}") else sb.append(m.san)
            }

            if (!extra.isNullOrBlank()) {
                // extra can start with NAG like "?? {comment}"
                // If it starts with ? or ?!, attach it to SAN first.
                val trimmed = extra.trim()
                val nag = when {
                    trimmed.startsWith("??") -> "??"
                    trimmed.startsWith("?!") -> "?!"
                    trimmed.startsWith("?") -> "?"
                    else -> ""
                }
                val commentPart = trimmed.removePrefix(nag).trim()
                if (nag.isNotBlank()) {
                    sb.append(nag)
                }
                if (commentPart.isNotBlank()) {
                    sb.append(' ')
                    sb.append(commentPart)
                }
            }
        }

        AnnotationResult(
            annotatedRibbon = sb.toString().trim(),
            summary = "Annotation complete.",
            cpSeriesAfterWhite = evalAfterCpWhite.toList(),
            pvByStartPly = pvByStartPly.mapValues { it.value.toMap() }
        )
    }

    data class GameEnd(
        val title: String,
        val message: String,
        val resultTag: String
    )

    fun userSide(): Side = if (playingAsWhite) Side.WHITE else Side.BLACK
    fun fishSide(): Side = if (playingAsWhite) Side.BLACK else Side.WHITE

    fun resultTagForWinnerSide(winner: Side): String = if (winner == Side.WHITE) "1-0" else "0-1"

    fun computeGameEnd(board: LibBoard): GameEnd? {
        if (board.isMated) {
            val winner = if (board.sideToMove == Side.WHITE) Side.BLACK else Side.WHITE
            val winnerName = if (winner == userSide()) "You" else "Fish"
            return GameEnd(
                title = "Checkmate",
                message = "$winnerName wins by checkmate.",
                resultTag = resultTagForWinnerSide(winner)
            )
        }
        if (board.isStaleMate) return GameEnd("Draw", "Stalemate.", "1/2-1/2")
        if (board.isInsufficientMaterial) return GameEnd("Draw", "Draw by insufficient material.", "1/2-1/2")

        if (board.isDraw) {
            val msg = when {
                isThreefoldClaimable() -> "Draw by threefold repetition."
                isFiftyMoveClaimable() -> "Draw by 50-move rule."
                else -> "Draw."
            }
            return GameEnd("Draw", msg, "1/2-1/2")
        }
        return null
    }

    fun setGameOver(end: GameEnd) {
        autosaveJob?.cancel()
        autosaveJob = null
        gameOverMessage = end.message
        gameResultTag = end.resultTag
        statusText = "Game over."
        isFishTurn = false
        showGameOverDialog = true

        // Completed Beat-the-Fish games should not be resumable. Recorder mode
        // never owns this file, so it must not erase a separate unfinished game.
        if (!recordGameMode) runCatching { clearBeatFishResumeGame(ctx) }
    }


    // Eval bar normalization:
    // ProcEngine.scoreCp is from Stockfish POV (side-to-move). We normalize to White POV.
    // IMPORTANT: we must use ProcEngine.activeFen (the FEN that the current search belongs to),
    // otherwise the sign can temporarily flip while UI state is still catching up.
    LaunchedEffect(uciLines, activeEngineFen, mode, evalAnchor, analyzeRequestAnchor) {
        val fenForEval = activeEngineFen
        if (fenForEval.isNullOrBlank()) {
            bfEvalCp = null
            return@LaunchedEffect
        }

        fun latestBestCpWhite(lines: List<String>, fromIdx: Int, fen: String): Int? {
            val wtm = whiteToMoveFen(fenStr = fen)
            val startIdx = fromIdx.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            for (k in lines.size - 1 downTo startIdx) {
                val ln = lines[k]
                if (!ln.startsWith("info ") || !ln.contains(" score ") ) continue
                // Only accept multipv 1 (or lines without multipv).
                if (ln.contains(" multipv ") && !ln.contains(" multipv 1 ") ) continue
                val cp = Regex("\\bscore\\s+cp\\s+(-?\\d+)").find(ln)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val mate = Regex("\\bscore\\s+mate\\s+(-?\\d+)").find(ln)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val signed = when {
                    cp != null -> cp
                    mate != null && mate > 0 -> 900
                    mate != null && mate < 0 -> -900
                    else -> null
                } ?: continue
                return if (wtm) signed else -signed
            }
            return null
        }

        val anchor = if (mode == BeatFishMode.ANALYZE) analyzeRequestAnchor else evalAnchor
        bfEvalCp = latestBestCpWhite(uciLines, anchor, fenForEval)
    }



// BeatFish screen appears: do NOT hard-teardown the global engine.
// Just politely stop any current search and clear stale output.
    LaunchedEffect(Unit) {
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
    }

// If we leave ANALYZE and go to PLAY, do not hard-stop the engine.
// Just stop the current search so PLAY can reuse the engine cleanly.
    LaunchedEffect(mode) {
        if (mode == BeatFishMode.PLAY) {
            runCatching { ProcEngine.send("stop") }
            runCatching { ProcEngine.clearOutput() }
        }
    }

// ANALYZE mode: Opening-style deep think (1 hour) that only stops when the position changes.
// No periodic "re-go" loop - we simply re-issue a new long search when analysisFen changes.
    LaunchedEffect(mode, analysisFen, multiPv, showLauncherMenu) {
        // Do not keep the perpetual analysis search alive while the Beat Fish launcher
        // menu is open. The menu is a mode-switching surface; leaving analysis running
        // here can collide with starting Archer/Barracuda/Megalodon on the same engine.
        if (mode != BeatFishMode.ANALYZE || showLauncherMenu) return@LaunchedEffect

        val fenNow = analysisFen ?: bfEngineFen ?: sessionBoard?.fen ?: effectiveFen
        analysisFen = fenNow
        bfEngineFen = fenNow

        // Stop only the current search so we don't get interleaved lines.
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }

        // Mark where this Analyze search starts (used for PV + eval; must stay stable)
        analyzeRequestAnchor = ProcEngine.lines.value.size
        pvLines = emptyList()
        bfEvalCp = null

        runCatching { ProcEngine.start("beatfish_analyze") }
        runCatching { ProcEngine.setMultiPv(multiPv.coerceIn(1, 4)) }
        runCatching { evaluateFenSafely(fenNow, 300_000) }
    }

// HOTFIX: disable continuous PLAY-mode live eval for now.
// It fights the fish move search on the same engine.
    LaunchedEffect(
        mode,
        isSession,
        navMode,
        gameResultTag,
        sessionBoard?.fen,
        moves.size,
        isFishTurn,
        bgAnnotRunning,
        bgAnnotationPlyCount,
        recordGameMode
    ) {
        if (mode != BeatFishMode.PLAY) return@LaunchedEffect
        bfEvalCp = null
        pvLines = emptyList()
    }

// ---------------- Background annotation while PLAY is running ----------------
// HOTFIX: disable background annotation during active PLAY sessions.
// We want fish move search to be the only engine work in PLAY for now.
    LaunchedEffect(moves.size, moves.lastOrNull()?.uci) {
        lastMoveAtMs = System.currentTimeMillis()
    }

    LaunchedEffect(mode, isSession, navMode, gameResultTag, recordGameMode) {
        if (mode != BeatFishMode.PLAY) return@LaunchedEffect
        bgAnnotRunning = false
    }

// Parse latest MultiPV lines while the engine is thinking (no flicker back to "Analyzing..." once we have PVs)
    LaunchedEffect(uciLines, mode, analysisFen, multiPv, analysisAnchor) {
        if (mode != BeatFishMode.ANALYZE) return@LaunchedEffect
        if (analysisFen.isNullOrBlank()) return@LaunchedEffect

        val safeFrom = analyzeRequestAnchor.coerceIn(0, uciLines.size)
        val slice = uciLines.drop(safeFrom)

        val infos = slice.filter { it.startsWith("info ") && it.contains(" score ") && it.contains(" pv ") }
        if (infos.isEmpty()) return@LaunchedEffect

        val cpRegex      = Regex("""\bscore\s+cp\s+(-?\d+)""")
        val mateRegex    = Regex("""\bscore\s+mate\s+(-?\d+)""")
        val multipvRegex = Regex("""\bmultipv\s+(\d+)""")

        data class LineInfo(val mp: Int, val cpSort: Int, val raw: String)

        val map = mutableMapOf<Int, LineInfo>()
        for (raw in infos) {
            val mp   = multipvRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val mate = mateRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val cp   = cpRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val cpSort = when {
                mate != null && mate > 0 ->  100000 - mate
                mate != null && mate < 0 -> -100000 - mate
                cp != null               -> cp
                else                     -> 0
            }

            map[mp] = LineInfo(mp, cpSort, raw)
        }

        if (map.isEmpty()) return@LaunchedEffect

        val want = multiPv.coerceIn(1, 4)
        val ordered = (1..want).mapNotNull { mp -> map[mp] }


        // Build BeatPvLine list
        val newPv = ordered.mapNotNull { li ->
            val raw = li.raw
            val mate = mateRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val cp   = cpRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()




            val pvPart = raw.substringAfter(" pv ", "")
            val pvMoves = pvPart.split(' ').filter { it.length >= 4 }

            if (pvMoves.isEmpty()) null else BeatPvLine(
                multiPv = li.mp,
                evalText = evalTextFromInfoWhitePov(raw, analysisFen!!),
                uciMoves = pvMoves
            )

        }

        if (newPv.isNotEmpty()) {
            pvLines = newPv
        }
    }


    suspend fun bestMoveForFen(fenNow: String, movetimeMs: Int = 5_000): String? = withContext(Dispatchers.IO) {
        engineLock.withLock {
            try {
                ProcEngine.start("inline")
            } catch (_: Throwable) {
                return@withContext null
            }

            // IMPORTANT: ProcEngine.lines is capped (200 lines). If we anchor by list size,
            // it can stop growing once the cap is hit, making us miss 'bestmove' during long annotations.
            // So we clear the captured output before each eval.
            ProcEngine.clearOutput()
            val anchor = 0
            evaluateFenSafely(fenNow, movetimeMs)

            var best: String? = null
            var fallback: String? = null

            try {
                val timeoutMs = movetimeMs.toLong() + 350L
                withTimeout(timeoutMs) {
                    while (best == null) {
                        val snap = ProcEngine.lines.value
                        for (i in snap.size - 1 downTo anchor) {
                            val line = snap[i]

                            if (line.startsWith("bestmove")) {
                                val candidate = line.split(" ").getOrNull(1)
                                if (!candidate.isNullOrBlank() && candidate.length >= 4 && candidate != "(none)" && candidate != "0000") {
                                    best = candidate
                                    break
                                }
                            }

                            if (fallback == null && line.startsWith("info ") && line.contains(" pv ")) {
                                val pvPart = Regex("""\bpv\s+(.+)$""").find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                fallback = pvPart.split(Regex("\\s+")).firstOrNull { it.length >= 4 }
                            }
                        }
                        if (best != null) break
                        delay(50L)
                    }
                }
            } catch (_: Throwable) {}

            best ?: fallback
        }
    }


    /**
     * "Balance" move picker: choose among MultiPV lines the move whose evaluation is closest to 0.00.
     *
     * Notes:
     * - We only consider the top N engine candidates (MultiPV 4 by default) to keep this fast.
     * - Scores are taken from Stockfish's POV (side-to-move). Minimizing |score| tends to steer toward equality.
     * - If we can't extract candidates, we fall back to bestMoveForFen().
     */
    /**
     * "Balance" move picker for Play Lv 1/2:
     * 1) Ask Stockfish for MultiPV (up to 4) and pick the line whose |score| is closest to 0.00
     * 2) If all candidate lines are still far from equality (e.g. user blundered and engine wants to punish),
     *    do a short, time-bounded scan of a subset of legal moves and pick the move that brings evaluation closest to 0.
     *
     * IMPORTANT: This must NEVER stall Play mode. If anything fails, the caller will fall back to bestMoveForFen().
     */
     data class BeatFishCandidate(
        val multiPv: Int,
        val uci: String,
        val cpWhite: Int?
    )

    suspend fun multiPvCandidatesForFenWhitePov(
        fenNow: String,
        movetimeMs: Int,
        wantPv: Int = 4
    ): List<BeatFishCandidate> = withContext(Dispatchers.IO) {
        engineLock.withLock {
            try {
                ProcEngine.start("inline")
            } catch (_: Throwable) {
                return@withContext emptyList()
            }

            val want = wantPv.coerceIn(1, 4)

            ProcEngine.clearOutput()
            runCatching { ProcEngine.setMultiPv(want) }
            evaluateFenSafely(fenNow, movetimeMs)

            try {
                withTimeout(movetimeMs.toLong() + 500L) {
                    while (true) {
                        val snap = ProcEngine.lines.value
                        if (snap.any { it.startsWith("bestmove") }) break
                        delay(50L)
                    }
                }
            } catch (_: Throwable) {
            }

            val snap = ProcEngine.lines.value

            val cpRegex = Regex("""\bscore\s+cp\s+(-?\d+)""")
            val mateRegex = Regex("""\bscore\s+mate\s+(-?\d+)""")
            val mpRegex = Regex("""\bmultipv\s+(\d+)""")
            val pvRegex = Regex("""\bpv\s+(.+)$""")

            val latestByMp = linkedMapOf<Int, BeatFishCandidate>()

            for (line in snap) {
                if (!line.startsWith("info ")) continue
                if (!line.contains(" pv ")) continue
                if (!line.contains(" score ")) continue

                val mp = mpRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                if (mp !in 1..want) continue

                val cp = cpRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val mate = mateRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

                val signed = when {
                    cp != null -> cp
                    mate != null && mate > 0 -> 900
                    mate != null && mate < 0 -> -900
                    else -> null
                }

                val cpWhite = signed?.let { s ->
                    if (whiteToMoveFen(fenNow)) s else -s
                }

                val pvPart = pvRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val firstMove = pvPart.split(Regex("\\s+")).firstOrNull { it.length >= 4 } ?: continue

                latestByMp[mp] = BeatFishCandidate(
                    multiPv = mp,
                    uci = firstMove,
                    cpWhite = cpWhite
                )
            }

            (1..want).mapNotNull { latestByMp[it] }
        }
    }


    suspend fun pickBookMoveForFish(
        fenNow: String,
        fishMovesPlayedSoFar: Int
    ): String? {
        // Local opening book removed from Trainer Fish base build.
        // Fish now chooses from the engine only. Later, this can query CoC.
        return null
    }



    // Returns evaluation in CENTIPAWNS from WHITE's point of view.
// Positive = good for White, Negative = good for Black.
// Mate is mapped to large +/- values so deltas still work.
    suspend fun evalWhiteCpForFen(fenNow: String, movetimeMs: Int = 350): Int? = withContext(Dispatchers.IO) {
        engineLock.withLock {
            try {
                ProcEngine.start("inline")
            } catch (_: Throwable) {
                return@withContext null
            }

            // IMPORTANT: ProcEngine.lines is capped (200 lines). If we anchor by list size,
            // it can stop growing once the cap is hit, making us miss 'bestmove' during long annotations.
            // So we clear the captured output before each eval.
            ProcEngine.clearOutput()
            val anchor = 0
            evaluateFenSafely(fenNow, movetimeMs)

            // Wait until bestmove arrives (or timeout) so we have a stable last score line.
            val timeoutMs = movetimeMs.toLong() + 350L
            withTimeoutOrNull(timeoutMs) {
                while (true) {
                    val snap = ProcEngine.lines.value
                    if (snap.size > anchor && snap.subList(anchor, snap.size).any { it.startsWith("bestmove") }) {
                        return@withTimeoutOrNull
                    }
                    delay(40L)
                }
            }

            val snap = ProcEngine.lines.value
            val wtm = whiteToMoveFen(fenNow)

            // Scan backwards for the last "info ... score ..." line.
            for (i in snap.size - 1 downTo anchor) {
                val ln = snap[i]
                if (!ln.startsWith("info ")) continue
                // MultiPV safety: only accept multipv 1 (or lines without multipv).
                if (ln.contains(" multipv ") && !ln.contains(" multipv 1 ")) continue

                val mate = Regex("""\bscore\s+mate\s+(-?\d+)""").find(ln)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (mate != null) {
                    // Mate score is from side-to-move POV; convert to White POV by flipping when Black to move.
                    val whiteMate = if (wtm) mate else -mate
                    // Map to large scale so it dominates cp deltas
                    return@withContext if (whiteMate > 0) (100000 - whiteMate) else (-100000 - whiteMate)
                }

                val cp = Regex("""\bscore\s+cp\s+(-?\d+)""").find(ln)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (cp != null) {
                    // CP is from side-to-move POV; convert to White POV by flipping when Black to move.
                    val whiteCp = if (wtm) cp else -cp
                    return@withContext whiteCp
                }
            }

            null
        }
    }


    // Records a move (main line) and advances the move counter.
    fun recordAndAdvance(before: LibBoard, mv: LibMove, uci: String, isWhiteMove: Boolean) {
        val san = bfPrettySan(before, mv, isWhiteMove = isWhiteMove)

        if (recordGameMode) {
            val now = System.currentTimeMillis()
            val start = recordTurnStartMs ?: now
            val deltaSec = ((now - start) / 1000L).toInt().coerceAtLeast(0)
            recordMoveTimesSec.add(deltaSec)
            recordTurnStartMs = now
        }
        moves += BFMove(moveCounter, uci, san, isWhite = isWhiteMove)
        if (!isWhiteMove) moveCounter++


    }




    fun ensureNavTimelineForBoard() {

        val b = sessionBoard ?: return
        if (navPositions.isEmpty()) {
            navPositions = listOf(b.fen)
            navIndex = 0
        }
    }

    fun showNavPosition(idx: Int) {
        val rawFenNav = navPositions.getOrNull(idx) ?: return
        val userSide = if (playAsBlack) Side.BLACK else Side.WHITE
        val fenNav = if (mode == BeatFishMode.PLAY && !recordGameMode) {
            bfFenWithSideToMove(rawFenNav, userSide)
        } else {
            rawFenNav
        }
        val bb = LibBoard().apply { loadFromFen(fenNav) }
        sessionBoard = bb

        // In PLAY, navigating always puts the USER to move at the shown position.
        if (mode == BeatFishMode.PLAY) {
            /* keep user color constant while navigating */
            whiteBottomSession = !playAsBlack

            // Navigation is now "play from here". Stop any pending Fish search
            // and make the displayed position immediately interactive; the first
            // user move from this position will trim the old mainline.
            runCatching { ProcEngine.send("stop") }
            runCatching { ProcEngine.clearOutput() }
            fishSearchActive = false
            isFishTurn = false
            moveNowRequested = false
            gameOverMessage = null
            gameResultTag = null
            showGameOverDialog = false
            statusText = "Your move from here."

            // Keep eval/search anchored to the displayed position
            bfEngineFen = bb.fen
        }

        if (mode == BeatFishMode.ANALYZE) {
            analysisFen = bb.fen
            bfEngineFen = bb.fen
        }

        selectedSquare = null
        lastFromIdx = null
        lastToIdx = null
    }

    fun startFishTurn() {
        if (recordGameMode) return
        val b0 = sessionBoard ?: return
        if (navMode) return
        if (fishSearchActive) return
        if (!isSession) return
        if (mode != BeatFishMode.PLAY) return
        if (gameResultTag != null) return

        val fishSide = if (playAsBlack) {
            com.github.bhlangonijr.chesslib.Side.WHITE
        } else {
            com.github.bhlangonijr.chesslib.Side.BLACK
        }

        isFishTurn = (sessionBoard?.sideToMove == fishSide)
        if (!isFishTurn) {
            statusText = "Your move."
            fishSearchActive = false
            return
        }

        statusText = "Fish thinking..."

        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }

        bfEngineFen = b0.fen
        evalAnchor = ProcEngine.lines.value.size
        fishSearchActive = true
        moveNowRequested = false

        scope.launch {
            fishTurnMutex.withLock {
                val fishSideInner = if (playAsBlack) Side.WHITE else Side.BLACK

                try {
                    val turnStartMs = System.currentTimeMillis()
                    val budgetMs = fishMoveTimeMs.toLong().coerceIn(0L, 60_000L)
                    val deadlineMs = turnStartMs + budgetMs

                    fun remainingMs(): Int {
                        if (moveNowRequested) return 0
                        return (deadlineMs - System.currentTimeMillis()).toInt().coerceAtLeast(0)
                    }

                    suspend fun waitOutBudget() {
                        if (moveNowRequested) return
                        val r = remainingMs()
                        if (r > 0) delay(r.toLong())
                    }

                    fun fishCp(candidate: BeatFishCandidate): Int {
                        val cpWhite = candidate.cpWhite ?: return Int.MIN_VALUE / 4
                        return if (fishSideInner == Side.WHITE) cpWhite else -cpWhite
                    }

                    fun chooseWeakestNonNegativeCandidate(
                        candidates: List<BeatFishCandidate>
                    ): BeatFishCandidate? {
                        if (candidates.isEmpty()) return null

                        for (c in candidates.asReversed()) {
                            if (fishCp(c) >= 0) return c
                        }

                        return candidates.firstOrNull()
                    }

                    val boardNow = sessionBoard ?: return@withLock
                    val currentFullMove = boardNow.fen
                        .split(" ")
                        .getOrNull(5)
                        ?.toIntOrNull()
                        ?: 1

                    val handicapUntilMove = when (playLevel) {
                        1 -> 40
                        2 -> 20
                        else -> 0
                    }

                    val stillUsingWeakerEngine =
                        playLevel != 3 && currentFullMove <= handicapUntilMove

                    data class SelectedFishMove(
                        val uci: String,
                        val cpWhite: Int?
                    )

                    val chosen: SelectedFishMove? = run {
                        // 1) Opening phase: no engine, use opening tree only.
                        val fishMovesPlayedSoFar = moves.count { it.isWhite == (fishSideInner == Side.WHITE) }

                        val bookMove = pickBookMoveForFish(
                            fenNow = boardNow.fen,
                            fishMovesPlayedSoFar = fishMovesPlayedSoFar
                        )
                        if (!bookMove.isNullOrBlank()) {
                            waitOutBudget()
                            return@run SelectedFishMove(bookMove, null)
                        }

                        // 2) Engine phase
                        val ms = remainingMs().coerceIn(300, fishMoveTimeMs)

                        val candidates = withTimeoutOrNull((ms + 500).toLong()) {
                            multiPvCandidatesForFenWhitePov(
                                fenNow = boardNow.fen,
                                movetimeMs = ms,
                                wantPv = if (stillUsingWeakerEngine) 4 else 1
                            )
                        }.orEmpty()

                        val pickedCandidate =
                            if (stillUsingWeakerEngine) {
                                chooseWeakestNonNegativeCandidate(candidates)
                            } else {
                                candidates.firstOrNull()
                            }

                        val picked = if (pickedCandidate != null) {
                            SelectedFishMove(
                                uci = pickedCandidate.uci,
                                cpWhite = pickedCandidate.cpWhite
                            )
                        } else {
                            val best = withTimeoutOrNull((ms + 300).toLong()) {
                                bestMoveForFen(boardNow.fen, movetimeMs = ms)
                            }
                            if (best.isNullOrBlank()) null else SelectedFishMove(best, null)
                        }

                        waitOutBudget()
                        picked
                    }

                    if (chosen?.uci.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            gameOverMessage = "Engine error - no move found."
                            statusText = "Engine error"
                            val cur = sessionBoard
                            isFishTurn = (cur?.sideToMove == fishSideInner) && (gameResultTag == null)
                        }
                        return@withLock
                    }

                    val bm = chosen!!.uci

                    // --- APPLY ENGINE MOVE ---
                    val bApply = LibBoard().apply { loadFromFen(boardNow.fen) }

                    val emv: LibMove? = bfUciToMoveOnBoard(bApply, bm)
                    val legal = MoveGenerator.generateLegalMoves(bApply)
                    val isLegal = (emv != null) && legal.any { it == emv }

                    if (!isLegal || emv == null) {
                        withContext(Dispatchers.Main) {
                            gameOverMessage = "Engine failed to find a legal move."
                            statusText = "Engine error"
                            val cur = sessionBoard
                            isFishTurn = (cur?.sideToMove == fishSideInner) && (gameResultTag == null)
                        }
                        return@withLock
                    }

                    val before = LibBoard().apply { loadFromFen(bApply.fen) }
                    bApply.doMove(emv)
                    val newBoard = LibBoard().apply { loadFromFen(bApply.fen) }

                    withContext(Dispatchers.Main) {
                        if (bm.length >= 4) {
                            lastFromIdx = bfUciSquareToIdx(bm.substring(0, 2), whiteBottomSession)
                            lastToIdx = bfUciSquareToIdx(bm.substring(2, 4), whiteBottomSession)
                        }

                        sessionBoard = newBoard
                        bfEngineFen = newBoard.fen

                        recordAndAdvance(before, emv, bm, isWhiteMove = (before.sideToMove == Side.WHITE))

                        // Cache the CP of the ACTUAL fish move just played as the eval of the new position.
                        // Snapshot index == current ply count after the move has been recorded.
                        val snapshotIdx = moves.size
                        while (cpSnapshotsWhiteByPly.size <= snapshotIdx) {
                            cpSnapshotsWhiteByPly.add(null)
                        }
                        if (chosen.cpWhite != null) {
                            cpSnapshotsWhiteByPly[snapshotIdx] = chosen.cpWhite
                        }

                        if (!navMode) {
                            navPositions = navPositions + newBoard.fen
                            navIndex = navPositions.lastIndex
                        }

                        val end = computeGameEnd(newBoard)
                        if (end != null) setGameOver(end)

                        isFishTurn = (end == null) && (newBoard.sideToMove == fishSideInner)
                        statusText = if (end != null) {
                            statusText
                        } else {
                            if (isFishTurn) "Fish thinking..." else "Fish moved. Your move."
                        }
                    }

                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        gameOverMessage = "Engine error: ${t.message ?: t.javaClass.simpleName}"
                        statusText = "Fish stopped"
                        val cur = sessionBoard
                        isFishTurn = (cur?.sideToMove == fishSideInner) && (gameResultTag == null)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        moveNowRequested = false
                        fishSearchActive = false

                        if (isFishTurn && gameResultTag == null && statusText.contains("thinking", ignoreCase = true)) {
                            statusText = "Fish stalled - tap Move! or try again."
                        }
                    }
                }
            }
        }
    }

    fun applyMoveOnSession(mv: LibMove, uci: String) {
        legalityError = null
        val b = sessionBoard ?: return
        // In ANALYZE, allow moves even if the game already ended; those are variations.
        if (gameResultTag != null && mode == BeatFishMode.PLAY) return


        // In PLAY, if the user navigated to a historical position, we auto-commit that point as the new mainline
        // (no "Resume" button needed). The user is always to move after navigation.
        if (navMode && mode == BeatFishMode.PLAY) {
            val rawFenNow = navPositions.getOrNull(navIndex) ?: b.fen
            val userSide = if (playAsBlack) Side.BLACK else Side.WHITE
            val fenNow = if (!recordGameMode) bfFenWithSideToMove(rawFenNow, userSide) else rawFenNow
            val bb = LibBoard().apply { loadFromFen(fenNow) }
            sessionBoard = bb

            // User plays the side-to-move at this point
            /* keep user color constant while navigating */
            whiteBottomSession = !playAsBlack

            // Trim mainline to the current navIndex
            val keepCount = navIndex.coerceIn(0, moves.size)
            while (moves.size > keepCount) moves.removeAt(moves.lastIndex)

            // Trim navigation history to here
            navPositions = navPositions.take(navIndex + 1)
            navIndex = navPositions.lastIndex

            // Restore move counter from FEN
            val parts = fenNow.split(" ")
            moveCounter = parts.getOrNull(5)?.toIntOrNull() ?: moveCounter

            lastFromIdx = null
            lastToIdx = null
            navMode = false

            fishSearchActive = false
            isFishTurn = false
            statusText = "Your move from here."
            gameOverMessage = null
            gameResultTag = null
            showGameOverDialog = false

            bfEngineFen = bb.fen
        }

        // In ANALYZE (Game analysis / Analysis board), any user move creates/extends a variation line
        // anchored at the current navigation cursor (navIndex).
        val isAnalyzeEdit = (mode == BeatFishMode.ANALYZE)

        if (!isAnalyzeEdit) {
            // PLAY: prevent editing historical positions; also ignore input while fish is thinking.
            if (navMode) return
            if (fishSearchActive) return
        } else {
            // ANALYZE: allow moves (as variations) even while browsing; still don't accept input while the engine is in a forced search action.
            if (fishSearchActive) return
        }
// Capture the user's move pre/post FEN so Play mode can decide whether to "punish" a blunder.
        // IMPORTANT: Store in White POV evaluation space (handled later by evalWhiteCpForFen).
        val preFenForDelta = b.fen

        val before = LibBoard().apply { loadFromFen(b.fen) }
        val isWhiteMove = (b.sideToMove == Side.WHITE)

        b.doMove(mv)
        sessionBoard = LibBoard().apply { loadFromFen(b.fen) }

        // Post-move FEN (after the user's move).
        val postFenForDelta = b.fen

        if (uci.length >= 4) {
            lastFromIdx = bfUciSquareToIdx(uci.substring(0, 2), whiteBottomSession)
            lastToIdx = bfUciSquareToIdx(uci.substring(2, 4), whiteBottomSession)
        }

        if (isAnalyzeEdit) {
            // --------- Variation editing (does NOT alter the mainline game) ----------
            val basePly1 = editBasePly ?: ((if (navIndex >= moves.size) moves.size else (navIndex + 1)).coerceAtLeast(1))

            // If user clicked into a variation, keep extending that exact prefix.
            // If not, start a fresh branch from the current mainline nav point.
            if (editBasePly == null) {
                editBasePly = basePly1
                editLineUci.clear()
            }

            editLineUci.add(uci.lowercase(Locale.ROOT))
            upsertUserVariation(basePly1, editLineUci.toList())

            // Keep board analysis synced to the edited position
            runCatching { ProcEngine.send("stop") }
            runCatching { ProcEngine.clearOutput() }
            val a = uciLines.size
            analyzeRequestAnchor = a
            analysisAnchor = a
            bfEvalCp = null
            pvLines = emptyList()
            analysisFen = b.fen
            bfEngineFen = b.fen

            selectedSquare = null
            statusText = "Analyzing..."
            return
        }

        // --------- Normal session move (mainline) ----------
        // Keep eval bar in sync in PLAY too.
        if (mode == BeatFishMode.PLAY) bfEngineFen = b.fen

        recordAndAdvance(before, mv, uci, isWhiteMove)

        if (!navMode) {
            navPositions = navPositions + b.fen
            navIndex = navPositions.lastIndex
        }

        // Detect game end immediately after the move (before starting fish turn).
        val endNow = computeGameEnd(sessionBoard ?: b)
        if (endNow != null) {
            setGameOver(endNow)
            selectedSquare = null
            return
        }

        gameOverMessage = null

        gameResultTag = null

        showGameOverDialog = false
        selectedSquare = null
        statusText = if (mode == BeatFishMode.PLAY) "Your move." else "Analyzing..."

        if (mode == BeatFishMode.ANALYZE) {
            // Interrupt current long search and start a fresh one for the new position
            runCatching { ProcEngine.send("stop") }
            runCatching { ProcEngine.clearOutput() }
            val a = uciLines.size
            analyzeRequestAnchor = a
            analysisAnchor = a
            bfEvalCp = null
            pvLines = emptyList()
            analysisFen = b.fen
            bfEngineFen = b.fen
        }

        if (mode == BeatFishMode.PLAY) {
            if (recordGameMode) {
                // Recorder: both sides are played manually. No fish turn, no book, no live engine.
                isFishTurn = false
                statusText = if (b.sideToMove == Side.WHITE) "White to move." else "Black to move."
                requestAutosave("move")
                return
            }

            val fishShouldMove = (if (playAsBlack) (b.sideToMove == Side.WHITE) else (b.sideToMove == Side.BLACK))


            // Autosave unfinished game state after any move
            requestAutosave("move")

            if (fishShouldMove) startFishTurn()
        }
    }

    fun attemptPlayerMove(fromIdx: Int, toIdx: Int) {
        if (!isSession) return
        if (fishSearchActive) return
        // Allow moves in ANALYZE even if the game already ended; those are variations.
        if (gameResultTag != null && mode == BeatFishMode.PLAY) return
        // In PLAY, navMode is no longer read-only. The first legal move
        // from the shown position automatically becomes the new mainline.

        val b = sessionBoard ?: return

        fun promoSuffix(mv: LibMove): String = when (mv.promotion?.pieceType) {
            LibPieceType.QUEEN -> "q"
            LibPieceType.ROOK -> "r"
            LibPieceType.BISHOP -> "b"
            LibPieceType.KNIGHT -> "n"
            else -> ""
        }

        fun promoLabel(mv: LibMove): String = when (mv.promotion?.pieceType) {
            LibPieceType.QUEEN -> "Queen"
            LibPieceType.ROOK -> "Rook"
            LibPieceType.BISHOP -> "Bishop"
            LibPieceType.KNIGHT -> "Knight"
            else -> "Move"
        }

        val tryFlags = listOf(whiteBottomSession, !whiteBottomSession).distinct()
        var chosenMove: LibMove? = null
        var chosenUci: String? = null

        val legal = MoveGenerator.generateLegalMoves(b)

        for (flag in tryFlags) {
            val fromUci = bfIdxToUci(fromIdx, flag)
            val toUci = bfIdxToUci(toIdx, flag)

            val fromSq = runCatching { Square.valueOf(fromUci.uppercase()) }.getOrNull()
            val toSq = runCatching { Square.valueOf(toUci.uppercase()) }.getOrNull()

            val matches = if (fromSq != null && toSq != null) {
                legal.filter { it.from == fromSq && it.to == toSq }
            } else emptyList()

            if (matches.isEmpty()) continue

            val promotionMoves = matches.filter { it.promotion != null }
            if (promotionMoves.size > 1) {
                val order = listOf(LibPieceType.QUEEN, LibPieceType.ROOK, LibPieceType.BISHOP, LibPieceType.KNIGHT)
                pendingPromotionChoices = promotionMoves
                    .sortedBy { mv -> order.indexOf(mv.promotion?.pieceType).let { if (it < 0) 99 else it } }
                    .map { mv ->
                        PendingBeatPromotion(
                            move = mv,
                            uci = fromUci + toUci + promoSuffix(mv),
                            label = promoLabel(mv)
                        )
                    }
                return
            }

            val attempted = matches.first()
            chosenMove = attempted
            chosenUci = fromUci + toUci + promoSuffix(attempted)
            break
        }

        if (chosenMove != null && chosenUci != null) {
            legalityError = null
            applyMoveOnSession(chosenMove!!, chosenUci!!)
        } else {
            legalityError = "Illegal move"
            statusText = "Illegal move."
            selectedSquare = null
        }
    }

    if (pendingPromotionChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                pendingPromotionChoices = emptyList()
                selectedSquare = null
            },
            title = { Text("Promote pawn") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingPromotionChoices.chunked(2).forEachIndexed { rowIndex, rowChoices ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowChoices.forEachIndexed { colIndex, choice ->
                                val promoText = when (choice.label) {
                                    "Queen" -> "♕ Queen"
                                    "Rook" -> "♖ Rook"
                                    "Bishop" -> "♗ Bishop"
                                    "Knight" -> "♘ Knight"
                                    else -> choice.label
                                }
                                val promoColor = when (rowIndex * 2 + colIndex) {
                                    0 -> Color(0xFF2563EB)
                                    1 -> Color(0xFF059669)
                                    2 -> Color(0xFF7C3AED)
                                    else -> Color(0xFFB45309)
                                }
                                BeatFishSquareButton(
                                    text = promoText,
                                    onClick = {
                                        val mv = choice.move
                                        val uci = choice.uci
                                        pendingPromotionChoices = emptyList()
                                        applyMoveOnSession(mv, uci)
                                    },
                                    modifier = Modifier.weight(1f),
                                    compact = false,
                                    backgroundColor = promoColor,
                                    textColor = Color.White
                                )
                            }
                            if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    pendingPromotionChoices = emptyList()
                    selectedSquare = null
                }) { Text("Cancel") }
            }
        )
    }

    fun resetSessionState() {
        autosaveJob?.cancel()
        autosaveJob = null
        isSession = false
        sessionBoard = null
        sessionStartFen = null
        moves.clear()
        navMode = false
        navPositions = emptyList()
        navIndex = 0
        lastFromIdx = null
        lastToIdx = null
        selectedSquare = null
        dragFrom = null
        lastDragPos = null
        isFishTurn = false
        gameOverMessage = null
        gameResultTag = null
        showGameOverDialog = false
        statusText = "Ready"
        bfEngineFen = null
        bfEvalCp = null
        pvLines = emptyList()

        // Clear any previous annotation/analysis artifacts so a new game starts clean.
        annotatedRibbon = null
        annotatedCpSeries = emptyList()
        annotatedPvByStartPly = emptyMap()
        annotateText = ""
    }

    fun ensureLandingPlaySession() {
        // Ensure a playable session exists (used by launcher actions and first-move start)
        if (!isSession || sessionBoard == null) {
            val bb = LibBoard().apply {
                runCatching { loadFromFen(effectiveFen) }.onFailure { loadFromFen(START_FEN) }
            }
            isSession = true
            sessionBoard = bb
            sessionStartFen = bb.fen
            moves.clear()
            // New session: clear any previous annotation artifacts.
            annotatedRibbon = null
            annotatedCpSeries = emptyList()
            annotatedPvByStartPly = emptyMap()
            annotateText = ""
            navPositions = listOf(bb.fen)
            navIndex = 0
            navMode = false
            lastFromIdx = null
            lastToIdx = null
            selectedSquare = null
            // Fish plays White if you chose 'Play Black'
            val fishSide = if (playAsBlack) com.github.bhlangonijr.chesslib.Side.WHITE else com.github.bhlangonijr.chesslib.Side.BLACK
            isFishTurn = (bb.sideToMove == fishSide)
        }

        // User color is fixed by the Play-as-Black toggle (NOT by side-to-move).
        val cur = sessionBoard
        if (cur != null) {
            playingAsWhite = !playAsBlack
            whiteBottomSession = playingAsWhite
            moveCounter = cur.fen.split(" ").getOrNull(5)?.toIntOrNull() ?: 1
        }
    }

    fun loadFenIntoAnalysisBoard(fenToLoad: String, label: String = "Analyzing position from other modes."): Boolean {
        val bb = runCatching {
            LibBoard().apply { loadFromFen(fenToLoad.trim()) }
        }.getOrNull() ?: return false

        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        fishSearchActive = false
        isFishTurn = false

        resetSessionState()

        effectiveFen = bb.fen
        sessionBoard = bb
        sessionStartFen = bb.fen
        isSession = true

        editablePieces.clear()
        editablePieces.addAll(boardToUiPieces(bb))

        moves.clear()
        navPositions = listOf(bb.fen)
        navIndex = 0
        navMode = false
        moveCounter = bb.fen.split(' ').getOrNull(5)?.toIntOrNull() ?: 1

        whiteBottomSession = true
        playingAsWhite = (bb.sideToMove == Side.WHITE)

        stage = BeatFishStage.READY
        mode = BeatFishMode.ANALYZE
        analyzeContext = BeatFishAnalyzeContext.ANALYSIS_BOARD
        showLauncherMenu = false
        showLevelChooser = false
        playPhase = PlayPhase.Idle

        annotatedRibbon = null
        annotatedCpSeries = emptyList()
        annotatedPvByStartPly = emptyMap()
        analysisLiveCpByPos.clear()
        resetEditLine()
        userVarByBasePly = emptyMap()

        val a = ProcEngine.lines.value.size
        analysisAnchor = a
        analyzeRequestAnchor = a
        evalAnchor = a
        bfEvalCp = null
        pvLines = emptyList()
        analysisFen = bb.fen
        bfEngineFen = bb.fen
        selectedSquare = null
        pendingPromotionChoices = emptyList()
        statusText = label
        return true
    }

    fun enterAnalysisCustomPositionSetup() {
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        showLauncherMenu = false
        playPhase = PlayPhase.Idle
        showLevelChooser = false
        stage = BeatFishStage.SETUP
        setupReturnMode = BeatFishMode.ANALYZE
        mode = BeatFishMode.ANALYZE
        analyzeContext = BeatFishAnalyzeContext.ANALYSIS_BOARD
        isSession = false
        ensureEditorFromEffectiveFen()
        setupBlackToMove = (effectiveFen.split(" ").getOrNull(1) ?: "w") == "b"
        pieceToAdd = null
        selectedSquare = null
        pendingPromotionChoices = emptyList()
        statusText = "Set up a custom analysis position."
    }

    fun startPlayUiNow() {
        // PLAY mode must own the engine. Stop any previous analysis/eval search first.
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        fishSearchActive = false
        isFishTurn = false
        bfEvalCp = null
        pvLines = emptyList()
        analysisFen = null

        ensureLandingPlaySession()
        // Orientation + who plays which side
        whiteBottomSession = !playAsBlack  // you are at the bottom
        // Initialize balance budget

        mode = BeatFishMode.PLAY
        stage = BeatFishStage.READY
        navMode = false
        showLauncherMenu = false
        playPhase = PlayPhase.Playing
        showLevelChooser = false

        // Ensure timeline contains current position
        ensureNavTimelineForBoard()
        // Recompute fish turn from board side-to-move + chosen color
        sessionBoard?.let { bb ->
            val fishSide = if (playAsBlack) com.github.bhlangonijr.chesslib.Side.WHITE else com.github.bhlangonijr.chesslib.Side.BLACK
            isFishTurn = (bb.sideToMove == fishSide)
        }
        statusText = if (isFishTurn) "Fish thinking..." else "Your move."
    }

    fun pastePgnFromClipboard() {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(ctx, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val rawText = clip.getItemAt(0).coerceToText(ctx)?.toString()?.trim().orEmpty()
        if (rawText.isBlank()) {
            Toast.makeText(ctx, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        // Very light PGN sanity check
        val looksLikePgn =
            rawText.contains("[Event") ||
                    rawText.contains("1.") ||
                    rawText.contains("1...")

        if (!looksLikePgn) {
            Toast.makeText(ctx, "Clipboard does not contain PGN text.", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = File(ctx.cacheDir, "clipboard_import.pgn")
                    FileOutputStream(tempFile).use { out ->
                        out.write(rawText.toByteArray(Charsets.UTF_8))
                    }

                    val holder = PgnHolder(tempFile.absolutePath)
                    holder.loadPgn()

                    val game = holder.games.firstOrNull()
                        ?: error("No PGN game found in clipboard.")

                    val data = loadGameDataFromPgnGame(game)
                        ?: error("Could not parse PGN game.")

                    data
                }
            }

            loaded.onSuccess { data ->
                sessionStartFen = data.startFen

                moves.clear()
                moves.addAll(data.moves)

                navMode = true
                resetEditLine()
                userVarByBasePly = emptyMap()

                navPositions = data.positions
                navIndex = data.positions.lastIndex

                val fenNav = data.positions.lastOrNull() ?: data.startFen
                val bb = LibBoard().apply { loadFromFen(fenNav) }
                sessionBoard = bb

                isSession = true
                isFishTurn = false
                stage = BeatFishStage.READY
                mode = BeatFishMode.ANALYZE
                analyzeContext = BeatFishAnalyzeContext.GAME_ANALYSIS
                showLauncherMenu = false

                annotatedRibbon = data.rawMoveText.takeIf { it.isNotBlank() }
                annotatedCpSeries = emptyList()
                annotatedPvByStartPly = emptyMap()
                analysisLiveCpByPos.clear()

                analysisFen = fenNav
                bfEngineFen = fenNav
                val a = uciLines.size
                analysisAnchor = a
                analyzeRequestAnchor = a
                evalAnchor = a

                statusText = "Game loaded from clipboard."
                Toast.makeText(ctx, "PGN loaded from clipboard.", Toast.LENGTH_SHORT).show()
            }.onFailure { t ->
                Toast.makeText(
                    ctx,
                    "Clipboard PGN import failed: ${t.message ?: "error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    fun startRecordGameNow() {
        // Fresh OTB recorder session from start position.
        autosaveJob?.cancel()
        autosaveJob = null
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }


        fishSearchActive = false
        isFishTurn = false

        recordGameMode = true
        recordMoveTimesSec.clear()
        recordWhiteClockSec = 0
        recordBlackClockSec = 0
        recordTurnStartMs = System.currentTimeMillis()

        effectiveFen = START_FEN
        resetSessionState()

        val bb = LibBoard().apply { loadFromFen(START_FEN) }
        isSession = true
        sessionBoard = bb
        sessionStartFen = bb.fen

        moves.clear()
        // New session: clear any previous annotation artifacts.
        annotatedRibbon = null
        annotatedCpSeries = emptyList()
        annotatedPvByStartPly = emptyMap()
        annotateText = ""

        navPositions = listOf(bb.fen)
        navIndex = 0
        navMode = false

        lastFromIdx = null
        lastToIdx = null
        selectedSquare = null

        moveCounter = 1
        playingAsWhite = true
        whiteBottomSession = true

        mode = BeatFishMode.PLAY
        stage = BeatFishStage.READY
        showLauncherMenu = false
        playPhase = PlayPhase.Playing
        showLevelChooser = false

        statusText = "White to move."
        showRecordGameFairPlayNotice = true
    }

    LaunchedEffect(initialRecordGame) {
        if (initialRecordGame) {
            startRecordGameNow()
        }
    }

    fun exitRecordGameToMenu() {
        recordGameMode = false
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }

        // clean return to launcher menu
        resetSessionState()
        effectiveFen = START_FEN
        isSession = false
        sessionBoard = null

        showLauncherMenu = true
        playPhase = PlayPhase.Idle
        showLevelChooser = false
        statusText = "Ready"
    }

    fun resetRecordGameAfterFocusLoss() {
        // Fair-play guard: Record Game must not continue after the user leaves
        // TrainerFish mid-game, because another app could provide engine help.
        autosaveJob?.cancel()
        autosaveJob = null
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        fishSearchActive = false
        isFishTurn = false
        selectedSquare = null
        pendingPromotionChoices = emptyList()

        recordMoveTimesSec.clear()
        recordWhiteClockSec = 0
        recordBlackClockSec = 0
        recordTurnStartMs = System.currentTimeMillis()

        effectiveFen = START_FEN
        resetSessionState()

        val bb = LibBoard().apply { loadFromFen(START_FEN) }
        isSession = true
        sessionBoard = bb
        sessionStartFen = bb.fen

        moves.clear()
        annotatedRibbon = null
        annotatedCpSeries = emptyList()
        annotatedPvByStartPly = emptyMap()
        annotateText = ""

        navPositions = listOf(bb.fen)
        navIndex = 0
        navMode = false
        lastFromIdx = null
        lastToIdx = null

        moveCounter = 1
        playingAsWhite = true
        whiteBottomSession = true

        recordGameMode = true
        mode = BeatFishMode.PLAY
        stage = BeatFishStage.READY
        showLauncherMenu = false
        showLevelChooser = false
        playPhase = PlayPhase.Playing

        statusText = "Recorded game reset - White to move."
        showRecordGameResetNotice = true
    }

    fun resumeRecordGameFromMenu() {
        if (!recordGameMode || !isSession || sessionBoard == null) {
            statusText = "No active recorded game."
            return
        }

        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        fishSearchActive = false
        isFishTurn = false
        selectedSquare = null
        pendingPromotionChoices = emptyList()

        mode = BeatFishMode.PLAY
        stage = BeatFishStage.READY
        navMode = false
        showLauncherMenu = false
        showLevelChooser = false
        playPhase = PlayPhase.Playing
        recordTurnStartMs = System.currentTimeMillis()

        statusText = if (sessionBoard?.sideToMove == Side.WHITE) "White to move." else "Black to move."
    }


    // Auto-start fish move when entering PLAY and it's fish to move (important when fish plays White on move 1).
    LaunchedEffect(mode, playAsBlack, sessionBoard?.fen, navMode, gameResultTag, showLevelChooser, showLauncherMenu, recordGameMode) {
        if (recordGameMode) return@LaunchedEffect
        if (mode == BeatFishMode.PLAY && isSession && !navMode && gameResultTag == null && !showLevelChooser && !showLauncherMenu) {
            val bb = sessionBoard
            if (bb != null) {
                val fishSide = if (playAsBlack) com.github.bhlangonijr.chesslib.Side.WHITE else com.github.bhlangonijr.chesslib.Side.BLACK
                if (bb.sideToMove == fishSide && !fishSearchActive) {
                    startFishTurn()
                }
            }
        }
    }


    // Record Game: tick the side-to-move clock once per second while recording (ascending clocks).
    LaunchedEffect(recordGameMode, mode, isSession, navMode, gameResultTag, showLauncherMenu, sessionBoard?.fen) {
        if (!recordGameMode) return@LaunchedEffect
        if (mode != BeatFishMode.PLAY) return@LaunchedEffect
        if (!isSession) return@LaunchedEffect
        if (navMode) return@LaunchedEffect
        if (gameResultTag != null) return@LaunchedEffect
        if (showLauncherMenu) return@LaunchedEffect

        if (recordTurnStartMs == null) recordTurnStartMs = System.currentTimeMillis()

        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(1000)
            val b = sessionBoard ?: continue
            if (b.sideToMove == Side.WHITE) recordWhiteClockSec += 1 else recordBlackClockSec += 1
        }
    }



    LaunchedEffect(mode, isSession, gameResultTag, moves.size) {
        if (isSession && moves.isNotEmpty() && gameResultTag == null) {
            // If user switches modes mid-game, keep a resume snapshot
            requestAutosave("mode-change")
        }
    }


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Record Game fair-play guard: if the user leaves TrainerFish while
                // a recorded game has moves, discard that record and reset to start.
                if (recordGameMode && isSession && moves.isNotEmpty() && gameResultTag == null) {
                    resetRecordGameAfterFocusLoss()
                    return@LifecycleEventObserver
                }

                // App background / user leaves: autosave unfinished Beat-the-Fish play games only.
                if (!recordGameMode && isSession && moves.isNotEmpty() && gameResultTag == null) {
                    val startFen = sessionStartFen
                    if (startFen != null) {
                        val thinkSec = fishThinkSeconds
                        scope.launch(Dispatchers.IO) {
                            saveBeatFishResumePgn(
                                ctx = ctx,
                                startFen = startFen,
                                moves = moves.toList(),
                                playAsBlack = playAsBlack,
                                playLevel = playLevel,
                                thinkSec = thinkSec
                            )
                        }
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    val uiBoardForRender: Array<Piece?> = run {
        val b = sessionBoard
        if (isSession && b != null) {
            val base = boardToUiPieces(b)
            if (whiteBottomSession) base else Array(64) { i -> base[63 - i] }
        } else {
            editablePieces.toTypedArray()
        }
    }

    val boardForRender = remember(isSession, uiBoardForRender, dragFrom) {
        uiBoardForRender.copyOf().also { arr ->
            dragFrom?.takeIf { it in 0..63 }?.let { idx -> arr[idx] = null }
        }
    }

    @Composable
    fun BoardEvalPane(modifier: Modifier, landscape: Boolean) {
        BoxWithConstraints(modifier = modifier) {
            val evalT = 6.dp
            val gap = 4.dp
            val boardSide = if (landscape) {
                minOf(maxHeight, (maxWidth - evalT - gap).coerceAtLeast(0.dp))
            } else {
                minOf(maxWidth, (maxHeight - evalT - gap).coerceAtLeast(0.dp))
            }

            val whiteBottom = if (isSession) whiteBottomSession else setupWhiteBottom
            val barCp: Int? = if (isSession) bfEvalCp else null

            @Composable
            fun BoardOnly() {
            Box(
                Modifier
                    .size(boardSide)
                    .onSizeChanged { boardSize = it }
                    .pointerInput(isSession, navMode, isFishTurn, boardSize) {
                        fun squareAt(pos: Offset): Int? {
                            val side = kotlin.math.min(size.width, size.height)
                            if (side <= 0f) return null

                            // Board is usually centered inside this box. Adjust for letterboxing.
                            val left = (size.width - side) / 2f
                            val top = (size.height - side) / 2f
                            val x = pos.x - left
                            val y = pos.y - top
                            if (x < 0f || y < 0f || x >= side || y >= side) return null

                            val cell = (side / 8f).coerceAtLeast(1f)
                            val screenFile = (x / cell).toInt()
                            val screenRankFromTop = (y / cell).toInt()
                            if (screenFile !in 0..7 || screenRankFromTop !in 0..7) return null

                            // Return DISPLAY index, not logical board index.
                            // uiBoardForRender is already flipped when Black is at the bottom,
                            // and attemptPlayerMove() converts this display index back to UCI.
                            // Returning logical indices here caused double-flipping: e.g. a visual
                            // c7 click was interpreted as f2 in Beat-the-Fish / Recorder modes.
                            val displayRank = 7 - screenRankFromTop
                            return displayRank * 8 + screenFile
                        }

                        detectDragGestures(
                            onDragStart = { pos ->
                                if (!isSession || (!recordGameMode && isFishTurn)) return@detectDragGestures
                                val startIdx = squareAt(pos)
                                val uiPiece = startIdx?.let { uiBoardForRender.getOrNull(it) }
                                val ok = uiPiece != null && when (mode) {
                                    BeatFishMode.ANALYZE -> true
                                    BeatFishMode.PLAY -> if (recordGameMode) true else (uiPiece.isWhite == playingAsWhite)
                                }
                                if (!ok) {
                                    dragFrom = null
                                    lastDragPos = null
                                    selectedSquare = null
                                    return@detectDragGestures
                                }
                                dragFrom = startIdx
                                lastDragPos = pos
                                selectedSquare = startIdx
                            },
                            onDrag = { change, _ ->
                                if (!isSession || (!recordGameMode && isFishTurn)) return@detectDragGestures
                                lastDragPos = change.position
                            },
                            onDragEnd = {
                                if (isSession && (recordGameMode || !isFishTurn)) {
                                    val from = dragFrom
                                    val to = lastDragPos?.let { squareAt(it) }
                                    if (from != null && to != null && from != to) {
                                        // First real move from the landing screen hides the launcher menu and reveals move list/nav.
                                        if (!recordGameMode && showLauncherMenu && playPhase == PlayPhase.Idle && mode == BeatFishMode.PLAY) {
                                            startPlayUiNow()
                                        }
                                        attemptPlayerMove(from, to)
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
                ChessBoard(
                    board = boardForRender,
                    selected = selectedSquare,
                    lastMoveFrom = if (isSession) lastFromIdx else null,
                    lastMoveTo = if (isSession) lastToIdx else null,
                    onSquareClick = { idx ->
                        // [OK] SETUP editor logic only in SETUP
                        if (!isSession) {
                            if (stage != BeatFishStage.SETUP) return@ChessBoard

                            val a = idx

                            pieceToAdd?.let { p ->
                                editablePieces[a] = p
                                return@ChessBoard
                            }

                            val prev = selectedSquare
                            if (prev == null) {
                                if (editablePieces[a] != null) selectedSquare = a
                            } else {
                                if (prev == a) {
                                    editablePieces[a] = null
                                } else {
                                    val moving = editablePieces[prev]
                                    editablePieces[prev] = null
                                    editablePieces[a] = moving
                                }
                                selectedSquare = null
                            }
                            return@ChessBoard
                        }

                        if (isFishTurn) return@ChessBoard

                        if (selectedSquare == null) {
                            val uiPiece = uiBoardForRender.getOrNull(idx) ?: return@ChessBoard
                            val ok = when (mode) {
                                BeatFishMode.ANALYZE -> true
                                BeatFishMode.PLAY -> uiPiece.isWhite == playingAsWhite
                            }
                            if (ok) selectedSquare = idx
                            return@ChessBoard
                        }

                        val fromIdx = selectedSquare!!
                        val toIdx = idx
                        if (fromIdx == toIdx) {
                            selectedSquare = null
                            return@ChessBoard
                        }
                        if (showLauncherMenu && playPhase == PlayPhase.Idle && mode == BeatFishMode.PLAY) {
                            startPlayUiNow()
                        }
                        attemptPlayerMove(fromIdx, toIdx)
                    },
                    light = light,
                    dark = dark,
                    pieceStyle = pieceStyle,
                    pieceSetKey = pieceSetKey,
                    whiteBottom = if (isSession) whiteBottomSession else setupWhiteBottom
                )

                // 2-tap fallback overlay
// Fixes missed taps on ranks 3-8 in BeatFishScreen
                run {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(
                                isSession,
                                stage,
                                navMode,
                                isFishTurn,
                                boardSize,
                                mode,
                                playingAsWhite,
                                whiteBottomSession,
                                setupWhiteBottom
                            ) {
                                fun squareAt(pos: Offset): Int? {
                                    val side = kotlin.math.min(size.width.toFloat(), size.height.toFloat())
                                    if (side <= 0f) return null
                                    val cell = (side / 8f).coerceAtLeast(1f)
                                    val screenFile = (pos.x / cell).toInt()
                                    val screenRankFromTop = (pos.y / cell).toInt()
                                    if (screenFile !in 0..7 || screenRankFromTop !in 0..7) return null

                                    // Return DISPLAY index, matching ChessBoard.onSquareClick.
                                    // The visible board array has already been oriented, so do not
                                    // flip here. attemptPlayerMove() performs the display->UCI conversion.
                                    val displayRank = 7 - screenRankFromTop
                                    return displayRank * 8 + screenFile
                                }

                                detectTapGestures(
                                    onTap = { pos ->
                                        val idx = squareAt(pos) ?: return@detectTapGestures

                                        // ---------- SETUP MODE ----------
                                        if (!isSession) {
                                            if (stage != BeatFishStage.SETUP) return@detectTapGestures

                                            pieceToAdd?.let { p ->
                                                editablePieces[idx] = p
                                                return@detectTapGestures
                                            }

                                            val prev = selectedSquare
                                            if (prev == null) {
                                                if (editablePieces[idx] != null) {
                                                    selectedSquare = idx
                                                }
                                            } else {
                                                if (prev == idx) {
                                                    editablePieces[idx] = null
                                                } else {
                                                    val moving = editablePieces[prev]
                                                    editablePieces[prev] = null
                                                    editablePieces[idx] = moving
                                                }
                                                selectedSquare = null
                                            }
                                            return@detectTapGestures
                                        }

                                        // ---------- SESSION MODE ----------
                                        if (!recordGameMode && isFishTurn) return@detectTapGestures

                                        if (selectedSquare == null) {
                                            val uiPiece = uiBoardForRender.getOrNull(idx) ?: return@detectTapGestures
                                            val ok = when (mode) {
                                                BeatFishMode.ANALYZE -> true
                                                BeatFishMode.PLAY -> if (recordGameMode) true else (uiPiece.isWhite == playingAsWhite)
                                            }
                                            if (ok) selectedSquare = idx
                                            return@detectTapGestures
                                        }

                                        val fromIdx = selectedSquare!!
                                        val toIdx = idx
                                        if (fromIdx == toIdx) {
                                            selectedSquare = null
                                            return@detectTapGestures
                                        }

                                        if (!recordGameMode && showLauncherMenu && playPhase == PlayPhase.Idle && mode == BeatFishMode.PLAY) {
                                            startPlayUiNow()
                                        }

                                        attemptPlayerMove(fromIdx, toIdx)
                                    }
                                )
                            }
                    )
                }

                lastDragPos?.let { pos ->
                    val fromIdx = dragFrom?.takeIf { it in 0..63 }
                    if (fromIdx != null) {
                        drawPieceOverlay(uiBoardForRender.getOrNull(fromIdx), pos, boardSize)
                    }
                }
            }
            }

            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoardOnly()
                    EvalBar(
                        scoreCp = barCp,
                        modifier = Modifier
                            .height(boardSide)
                            .width(evalT),
                        whiteOnTop = !whiteBottom
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    BoardOnly()
                    EvalBar(
                        scoreCp = barCp,
                        modifier = Modifier
                            .width(boardSide)
                            .height(evalT),
                        whiteOnTop = !whiteBottom
                    )
                }
            }
        }
    }



    @Composable
    fun BeatFishMenuCard(
        title: String,
        subtitle: String,
        emoji: String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(22.dp)
        val bg = if (enabled) {
            Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                )
            )
        } else {
            Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bg)
                .border(
                    width = 1.dp,
                    color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    shape = shape
                )
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.07f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }

                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                )
            }
        }
    }

    @Composable
    fun LauncherDetailHeader(title: String, subtitle: String? = null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { launcherDetail = null }) {
                Text("<- Back to Beat the Fish menu")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun RightPane(modifier: Modifier, showHeader: Boolean) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            if (showHeader) {
                headerContent()
                Spacer(Modifier.height(8.dp))

                if (!showLauncherMenu && mode == BeatFishMode.ANALYZE) {
                    val title = if (analyzeContext == BeatFishAnalyzeContext.GAME_ANALYSIS) "Game analysis" else "Analysis board"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

            }


            // Launcher menu (scrollable). The top level is now card-based. Selecting a card
            // replaces the menu with that card's details, keeping the launcher calmer and classier.
            if (showLauncherMenu) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (launcherDetail) {
                        null -> {
                            Text(
                                text = "Beat the Fish",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose a training room.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            BeatFishMenuCard(
                                title = if (recordGameMode) "Record Game in progress" else "Choose a Level",
                                subtitle = if (recordGameMode) "Resume or exit the active recorder session" else "Play Archer Fish, Barracuda, or Megalodon",
                                emoji = if (recordGameMode) "✍️" else "\uD83D\uDC1F",
                                onClick = { launcherDetail = if (recordGameMode) "record" else "level" }
                            )

                            if (!recordGameMode) {
                                BeatFishMenuCard(
                                    title = "Record Game",
                                    subtitle = "Enter both sides' moves, then analyze afterward",
                                    emoji = "✍️",
                                    onClick = { launcherDetail = "record" }
                                )
                                BeatFishMenuCard(
                                    title = "Set Up Position",
                                    subtitle = "Build a custom position before playing",
                                    emoji = "♟️",
                                    onClick = { launcherDetail = "setup" }
                                )
                                BeatFishMenuCard(
                                    title = "Load Position",
                                    subtitle = "Use another trainer position or resume last game",
                                    emoji = "📥",
                                    onClick = { launcherDetail = "load" }
                                )
                                BeatFishMenuCard(
                                    title = "Saved Games",
                                    subtitle = "Open your master PGN of saved games",
                                    emoji = "📚",
                                    onClick = { launcherDetail = "saved" }
                                )
                                BeatFishMenuCard(
                                    title = "Analysis Board",
                                    subtitle = "Analyze freely without playing against the fish",
                                    emoji = "🔎",
                                    onClick = { launcherDetail = "analysis" }
                                )
                                BeatFishMenuCard(
                                    title = "Paste PGN",
                                    subtitle = "Analyze a PGN copied to the clipboard",
                                    emoji = "📋",
                                    onClick = { launcherDetail = "clipboard" }
                                )
                            }
                        }

                        "level" -> {
                            LauncherDetailHeader(
                                title = "Choose a level",
                                subtitle = "Pick the fish strength and thinking time. The other menu cards stay hidden until you go back."
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = fishThinkSecondsText,
                                    onValueChange = { new ->
                                        val digits = new.filter { it.isDigit() }.take(2)
                                        val n = digits.toIntOrNull()
                                        fishThinkSecondsText = when {
                                            digits.isBlank() -> ""
                                            n == null -> digits
                                            n < 2 -> "2"
                                            n > 60 -> "60"
                                            else -> n.toString()
                                        }
                                    },
                                    enabled = !recordGameMode,
                                    label = { Text("Time - min 2") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(140.dp)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Play Black", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(8.dp))
                                    Checkbox(
                                        checked = playAsBlack,
                                        onCheckedChange = { playAsBlack = it },
                                        enabled = !recordGameMode
                                    )
                                }
                            }

                            val levelNames = mapOf(
                                1 to "Archer Fish",
                                2 to "Barracuda",
                                3 to "Megalodon"
                            )
                            val levelSubtitles = mapOf(
                                1 to "Friendly sparring partner",
                                2 to "Stronger club-level fish",
                                3 to "Full-strength best move mode"
                            )

                            listOf(1, 2, 3).forEach { lvl ->
                                BeatFishMenuCard(
                                    title = levelNames[lvl] ?: "Level $lvl",
                                    subtitle = levelSubtitles[lvl] ?: "Start a new game",
                                    emoji = when (lvl) { 1 -> "🏹"; 2 -> "🦈"; else -> "🐲" },
                                    enabled = !recordGameMode,
                                    onClick = {
                                        stopBeatFishEngineForMenu()

                                        effectiveFen = START_FEN
                                        resetSessionState()

                                        playLevel = lvl
                                        launcherDetail = null
                                        startPlayUiNow()
                                    }
                                )
                            }
                        }

                        "record" -> {
                            LauncherDetailHeader(
                                title = "Record Game",
                                subtitle = if (recordGameMode)
                                    "Recorder mode is paused in this menu. Return without losing moves, or exit and reset."
                                else
                                    "Record an over-the-board game with no engine assistance, then run analysis afterward."
                            )

                            if (recordGameMode) {
                                BeatFishMenuCard(
                                    title = "Back to Record Game",
                                    subtitle = "Resume the current move record without resetting",
                                    emoji = "↩️",
                                    onClick = {
                                        launcherDetail = null
                                        resumeRecordGameFromMenu()
                                    }
                                )
                                BeatFishMenuCard(
                                    title = "Exit Record Game",
                                    subtitle = "Discard the current recorder session and return to menu",
                                    emoji = "\uD83D\uDEAA",
                                    onClick = {
                                        launcherDetail = null
                                        exitRecordGameToMenu()
                                    }
                                )
                            } else {
                                Text(
                                    text = "Fair-play notice: if you leave TrainerFish while a recorded game is in progress, the current move record will reset.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                BeatFishMenuCard(
                                    title = "Start Record Game",
                                    subtitle = "Begin from the starting position and track move times",
                                    emoji = "✍️",
                                    onClick = {
                                        launcherDetail = null
                                        startRecordGameNow()
                                    }
                                )
                            }
                        }

                        "setup" -> {
                            LauncherDetailHeader(
                                title = "Set up position",
                                subtitle = "Create a custom position, then play it against the fish."
                            )
                            BeatFishMenuCard(
                                title = "Open Position Editor",
                                subtitle = "Place pieces manually and choose side to move",
                                emoji = "♟️",
                                enabled = !recordGameMode,
                                onClick = {
                                    stopBeatFishEngineForMenu()
                                    launcherDetail = null
                                    showLauncherMenu = false
                                    playPhase = PlayPhase.Idle
                                    showLevelChooser = false
                                    stage = BeatFishStage.SETUP
                                    setupReturnMode = BeatFishMode.PLAY
                                    isSession = false
                                    ensureEditorFromEffectiveFen()
                                    pieceToAdd = null
                                    selectedSquare = null
                                }
                            )
                        }

                        "load" -> {
                            LauncherDetailHeader(
                                title = "Load position",
                                subtitle = "Continue from another trainer position or resume your last unfinished game."
                            )
                            BeatFishMenuCard(
                                title = "Load from other modes",
                                subtitle = "Use the latest Tactics, Endgame, or Opening position",
                                emoji = "📥",
                                enabled = !recordGameMode,
                                onClick = {
                                    val fenToLoad = lastFen?.trim().orEmpty()
                                    if (fenToLoad.isNotBlank()) {
                                        val bb = runCatching {
                                            LibBoard().apply { loadFromFen(fenToLoad) }
                                        }.getOrNull()

                                        if (bb == null) {
                                            statusText = "Could not load that position."
                                        } else {
                                            runCatching { ProcEngine.send("stop") }
                                            runCatching { ProcEngine.clearOutput() }
                                            fishSearchActive = false
                                            isFishTurn = false

                                            resetSessionState()

                                            effectiveFen = bb.fen
                                            sessionBoard = bb
                                            sessionStartFen = bb.fen
                                            isSession = true

                                            editablePieces.clear()
                                            editablePieces.addAll(boardToUiPieces(bb))

                                            moves.clear()
                                            navPositions = listOf(bb.fen)
                                            navIndex = 0
                                            navMode = false

                                            moveCounter = bb.fen.split(' ').getOrNull(5)?.toIntOrNull() ?: 1

                                            // Continue as the side that was actually to move in
                                            // the imported trainer position. Otherwise a black-to-
                                            // move position immediately triggers an unwanted Fish move.
                                            val userIsBlack = bb.sideToMove == Side.BLACK
                                            playAsBlack = userIsBlack
                                            playingAsWhite = !userIsBlack
                                            whiteBottomSession = !userIsBlack

                                            stage = BeatFishStage.READY
                                            mode = BeatFishMode.PLAY
                                            showLauncherMenu = false
                                            launcherDetail = null
                                            showLevelChooser = false
                                            playPhase = PlayPhase.Idle

                                            bfEngineFen = bb.fen
                                            analysisFen = bb.fen
                                            statusText = "Loaded position from other modes."

                                            onStartFromLast()
                                        }
                                    } else {
                                        statusText = "No position available from other modes."
                                    }
                                }
                            )

                            BeatFishMenuCard(
                                title = "Load last played",
                                subtitle = "Resume your unfinished Beat the Fish game",
                                emoji = "⏮️",
                                enabled = !recordGameMode,
                                onClick = {
                                    val loaded = loadBeatFishResumeGame(ctx)
                                    if (loaded == null) {
                                        statusText = "No unfinished game to resume."
                                    } else {
                                        val resumedPlayAsBlack = loaded.playAsBlack ?: playAsBlack
                                        playAsBlack = resumedPlayAsBlack
                                        loaded.playLevel?.let { playLevel = it.coerceIn(1, 3) }
                                        loaded.thinkSec?.let {
                                            fishThinkSecondsText = it.coerceIn(2, 60).toString()
                                        }

                                        sessionStartFen = loaded.startFen

                                        moves.clear()
                                        moves.addAll(loaded.moves)

                                        navPositions = loaded.positions
                                        navIndex = loaded.positions.lastIndex
                                        navMode = false

                                        val fenNow = loaded.positions.lastOrNull() ?: loaded.startFen
                                        val bb = LibBoard().apply { loadFromFen(fenNow) }
                                        sessionBoard = bb
                                        bfEngineFen = bb.fen

                                        val last = loaded.moves.lastOrNull()
                                        moveCounter = when {
                                            last == null -> 1
                                            last.isWhite -> last.moveNumber
                                            else -> last.moveNumber + 1
                                        }

                                        playingAsWhite = !resumedPlayAsBlack
                                        whiteBottomSession = !resumedPlayAsBlack

                                        lastFromIdx = null
                                        lastToIdx = null
                                        selectedSquare = null

                                        isSession = true
                                        gameOverMessage = null
                                        gameResultTag = null
                                        showGameOverDialog = false
                                        stage = BeatFishStage.READY
                                        mode = BeatFishMode.PLAY

                                        showLauncherMenu = false
                                        launcherDetail = null
                                        playPhase = PlayPhase.Playing
                                        showLevelChooser = false

                                        val fishSide = if (resumedPlayAsBlack) Side.WHITE else Side.BLACK
                                        isFishTurn = (bb.sideToMove == fishSide)
                                        statusText = if (isFishTurn) "Fish thinking..." else "Your move."

                                        if (isFishTurn) startFishTurn()
                                    }
                                }
                            )
                        }

                        "saved" -> {
                            LauncherDetailHeader(
                                title = "Saved games",
                                subtitle = "Open the master PGN where TrainerFish appends annotated games."
                            )
                            BeatFishMenuCard(
                                title = if (isLoadingMasterPgn) "Opening master PGN..." else "Open saved PGN (master)",
                                subtitle = "Browse saved annotated games",
                                emoji = "📚",
                                onClick = {
                                    val masterUri = getMasterPgnUri(ctx)
                                    if (masterUri == null) {
                                        Toast.makeText(ctx, "No master PGN set yet. Tap Save first.", Toast.LENGTH_LONG).show()
                                        statusText = "No master PGN set yet. Save a game first."
                                    } else {
                                        isLoadingMasterPgn = true
                                        masterPgnError = null
                                        masterPgnEntries = emptyList()

                                        scope.launch {
                                            val raw = withContext(Dispatchers.IO) {
                                                runCatching { readTextFromUri(ctx, masterUri) }
                                            }
                                            val text = raw.getOrNull()
                                            if (text.isNullOrBlank()) {
                                                isLoadingMasterPgn = false
                                                masterPgnError = raw.exceptionOrNull()?.message ?: "Could not read PGN."
                                                return@launch
                                            }

                                            val chunks = withContext(Dispatchers.Default) {
                                                splitMasterPgnIntoChunks(text)
                                            }

                                            if (chunks.isEmpty()) {
                                                isLoadingMasterPgn = false
                                                masterPgnError = "No games found in that PGN."
                                                return@launch
                                            }

                                            val maxGames = 250
                                            val tail = if (chunks.size > maxGames) chunks.takeLast(maxGames) else chunks
                                            val entries = tail.mapIndexedNotNull { i, ch ->
                                                parseMasterPgnEntry(idx = i, chunk = ch)
                                            }.asReversed()

                                            masterPgnEntries = entries
                                            isLoadingMasterPgn = false
                                            showMasterPgnPicker = true
                                        }
                                    }
                                }
                            )
                        }

                        "analysis" -> {
                            LauncherDetailHeader(
                                title = "Analysis board",
                                subtitle = "Study a position with TrainerFish without playing a fish game."
                            )
                            BeatFishMenuCard(
                                title = "Analysis Board",
                                subtitle = "Analyze the current board position",
                                emoji = "🔎",
                                enabled = !recordGameMode,
                                onClick = {
                                    stopBeatFishEngineForMenu()
                                    showLauncherMenu = false
                                    launcherDetail = null
                                    playPhase = PlayPhase.Idle
                                    showLevelChooser = false
                                    stage = BeatFishStage.READY
                                    mode = BeatFishMode.ANALYZE
                                    analyzeContext = BeatFishAnalyzeContext.ANALYSIS_BOARD
                                    annotatedRibbon = null
                                    annotatedCpSeries = emptyList()
                                    annotatedPvByStartPly = emptyMap()
                                    ensureLandingPlaySession()
                                    val b = sessionBoard
                                    if (b == null) {
                                        statusText = "No board position to analyze."
                                    } else {
                                        whiteBottomSession = !playAsBlack
                                        ensureNavTimelineForBoard()
                                        statusText = "Analyzing..."
                                        analysisFen = b.fen
                                        bfEngineFen = b.fen
                                        evalAnchor = ProcEngine.lines.value.size
                                    }
                                }
                            )
                            BeatFishMenuCard(
                                title = "Analyze position from other modes",
                                subtitle = "Bring in the latest Tactics, Endgame, or Opening position",
                                emoji = "🧭",
                                enabled = !recordGameMode,
                                onClick = {
                                    val fenToLoad = lastFen?.trim().orEmpty()
                                    if (fenToLoad.isBlank()) {
                                        statusText = "No position available from other modes."
                                    } else if (!loadFenIntoAnalysisBoard(fenToLoad, "Analyzing position from other modes.")) {
                                        statusText = "Could not load that position."
                                    } else {
                                        launcherDetail = null
                                    }
                                }
                            )
                            BeatFishMenuCard(
                                title = "Create custom analysis position",
                                subtitle = "Set up a position and analyze it immediately",
                                emoji = "🛠️",
                                enabled = !recordGameMode,
                                onClick = {
                                    launcherDetail = null
                                    enterAnalysisCustomPositionSetup()
                                }
                            )
                        }

                        "clipboard" -> {
                            LauncherDetailHeader(
                                title = "Paste PGN",
                                subtitle = "Paste a complete PGN from the clipboard and load it into Game Analysis."
                            )
                            BeatFishMenuCard(
                                title = "Paste PGN from Clipboard",
                                subtitle = "Load copied PGN text into analysis",
                                emoji = "📋",
                                enabled = !recordGameMode,
                                onClick = {
                                    launcherDetail = null
                                    pastePgnFromClipboard()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            } else {
                // When not showing the launcher, expose a way to bring it back.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Analyze-only: bring back Annotate button here (it was removed with the old mode buttons).
                    if (mode == BeatFishMode.ANALYZE) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    if (!isSession || moves.isEmpty() || isAnnotating || isFishTurn) return@TextButton
                                    showAnnotateChoiceDialog = true
                                },
                                enabled = isSession && moves.isNotEmpty() && !isFishTurn
                            ) {
                                Text("Annotate")
                            }

                            TextButton(
                                onClick = {
                                    if (!isSession || moves.isEmpty()) return@TextButton

                                    val start = sessionStartFen ?: (sessionBoard?.fen ?: START_FEN)
                                    val baseRibbon =
                                        if (recordGameMode) {
                                            annotatedRibbon ?: rebuildRecordRibbon(moves.toList())
                                        } else {
                                            annotatedRibbon ?: buildBeatFishMoveRibbon(moves.toList())
                                        }

                                    val exportRibbon = buildBeatFishExportRibbon(
                                        startFen = start,
                                        moves = moves.toList(),
                                        ribbon = baseRibbon,
                                        userVarByBasePly = userVarByBasePly,
                                        pvByStartPly = annotatedPvByStartPly,
                                        result = gameResultTag ?: "*"
                                    )

                                    val resultTag = gameResultTag ?: "*"

                                    val pgn = buildBeatFishPgnText(
                                        startFen = start,
                                        ribbon = exportRibbon,
                                        result = resultTag
                                    )



                                    pendingMasterPgnGame = pgn
                                    val masterUri = getMasterPgnUri(ctx)
                                    if (masterUri == null) {
                                        masterPgnCreateLauncher.launch(DEFAULT_MASTER_PGN_NAME)
                                    } else {
                                        scope.launch {
                                            appendPgnGameToMasterUri(context = ctx, uri = masterUri, gamePgn = pgn)
                                                .onSuccess {
                                                    Toast.makeText(ctx, "Saved to master PGN", Toast.LENGTH_SHORT).show()
                                                }
                                                .onFailure { t ->
                                                    setMasterPgnUri(ctx, null)
                                                    Toast.makeText(
                                                        ctx,
                                                        "Save failed: ${t.message ?: "error"}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                        }
                                    }
                                },
                                enabled = isSession && moves.isNotEmpty()
                            ) {
                                Text("Save Game")
                            }
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    BeatFishSquareButton(
                        text = "Flip Board",
                        onClick = {
                            if (!isSession) return@BeatFishSquareButton
                            whiteBottomSession = !whiteBottomSession
                            selectedSquare = null
                            dragFrom = null
                            lastDragPos = null
                            lastFromIdx = null
                            lastToIdx = null
                        },
                        enabled = isSession,
                        compact = true
                    )

                    if (mode != BeatFishMode.ANALYZE && !recordGameMode) {
                        TextButton(
                            onClick = {
                                val startFen = sessionStartFen
                                if (startFen != null && moves.isNotEmpty() && gameResultTag == null) {
                                    scope.launch(Dispatchers.IO) {
                                        val ok = saveBeatFishResumePgn(
                                            ctx = ctx,
                                            startFen = startFen,
                                            moves = moves.toList(),
                                            playAsBlack = playAsBlack,
                                            playLevel = playLevel,
                                            thinkSec = fishThinkSeconds
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                ctx,
                                                if (ok) "Game saved (resume)" else "Save failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = isSession && moves.isNotEmpty() && gameResultTag == null
                        ) {
                            Text("Save Game")
                        }
                    }

                    TextButton(onClick = {
                        openBeatFishLauncherMenu()
                    }) {
                        Text("☰")
                    }

                }
            }

            if (!showLauncherMenu && mode == BeatFishMode.ANALYZE && stage != BeatFishStage.SETUP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val fenToLoad = lastFen?.trim().orEmpty()
                            if (fenToLoad.isBlank()) {
                                statusText = "No position available from other modes."
                            } else if (!loadFenIntoAnalysisBoard(fenToLoad, "Analyzing position from other modes.")) {
                                statusText = "Could not load that position."
                            }
                        },
                        enabled = !recordGameMode,
                        modifier = Modifier.weight(1f)
                    ) { Text("Load from modes") }

                    OutlinedButton(
                        onClick = { enterAnalysisCustomPositionSetup() },
                        enabled = !recordGameMode,
                        modifier = Modifier.weight(1f)
                    ) { Text("Custom position") }
                }
            }

            // [OK] SETUP MODE
            if (stage == BeatFishStage.SETUP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    BeatFishSquareButton(
                        text = "Clear Board",
                        onClick = {
                            // Keep kings if they exist
                            val wkIdx = editablePieces.indexOfFirst { it?.type == PieceType.KING && it.isWhite }
                            val bkIdx = editablePieces.indexOfFirst { it?.type == PieceType.KING && !it.isWhite }

                            val wk = if (wkIdx >= 0) editablePieces[wkIdx] else null
                            val bk = if (bkIdx >= 0) editablePieces[bkIdx] else null

                            for (i in 0 until 64) editablePieces[i] = null

                            // Restore kings to their previous squares (or default squares if missing)
                            if (wk != null) editablePieces[wkIdx] = wk else editablePieces[4] = Piece(PieceType.KING, true)   // e1
                            if (bk != null) editablePieces[bkIdx] = bk else editablePieces[60] = Piece(PieceType.KING, false) // e8

                            selectedSquare = null
                            statusText = "Board cleared (kings kept)."
                        },
                        compact = true
                    )


                    Button(onClick = {
                        val err = beatFishValidatePosition(editablePieces)
                        if (err != null) {
                            legalityError = err
                        } else {
                            val baseCastling = effectiveFen.split(" ").getOrNull(2) ?: "-"

                            // In Setup the user can choose who is to move.
                            val whiteToMove = !setupBlackToMove

                            // For Beat-the-Fish PLAY setup, the user plays the side to move initially.
                            playingAsWhite = whiteToMove
                            whiteBottomSession = if (setupReturnMode == BeatFishMode.ANALYZE) true else playingAsWhite

                            val newFen = buildFenFromEditable(editablePieces, whiteToMove, baseCastling)
                            effectiveFen = newFen
                            stage = BeatFishStage.READY
                            // Switch back to a live session using the newly built FEN.
                            resetSessionState()
                            val bb = LibBoard().apply { loadFromFen(newFen) }
                            isSession = true
                            sessionBoard = bb
                            sessionStartFen = bb.fen
                            navPositions = listOf(bb.fen)
                            navIndex = 0
                            navMode = false
                            isFishTurn = false

                            showLauncherMenu = false
                            playPhase = PlayPhase.Idle
                            showLevelChooser = false
                            mode = setupReturnMode

                            if (setupReturnMode == BeatFishMode.ANALYZE) {
                                analyzeContext = BeatFishAnalyzeContext.ANALYSIS_BOARD
                                annotatedRibbon = null
                                annotatedCpSeries = emptyList()
                                annotatedPvByStartPly = emptyMap()
                                analysisLiveCpByPos.clear()
                                resetEditLine()
                                userVarByBasePly = emptyMap()
                                val a = ProcEngine.lines.value.size
                                analysisAnchor = a
                                analyzeRequestAnchor = a
                                evalAnchor = a
                                analysisFen = bb.fen
                                bfEngineFen = bb.fen
                                bfEvalCp = null
                                pvLines = emptyList()
                                statusText = "Analyzing custom position."
                            } else {
                                statusText = "Setup ready."
                            }

                            pieceToAdd = null
                            selectedSquare = null
                            pendingPromotionChoices = emptyList()
                        }
                    }) { Text("Done Setup") }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = setupBlackToMove,
                        onCheckedChange = { setupBlackToMove = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Black to move? (unchecked = White)")
                }

                Text(
                    text = "Tap a piece below, then tap a square to place it. Tap same square twice to remove. Two-tap to move.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                PiecePalette(
                    selected = pieceToAdd,
                    onSelected = { pieceToAdd = it }
                )

                return@Column
            }

            // [OK] READY: Analyze / Play buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {

            }


            // [OK] MultiPV (Analyze only)
            if (isSession && mode == BeatFishMode.ANALYZE) {
                Spacer(Modifier.height(2.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                }

                Spacer(Modifier.height(2.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 110.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    if (analysisEcoText != null) {
                        Text(
                            text = analysisEcoText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    if (pvLines.isEmpty()) {
                        Text("Analyzing...", style = MaterialTheme.typography.bodySmall)
                    } else {
                        pvLines.take(1).forEach { pv ->
                            val first = pv.uciMoves.firstOrNull()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = first != null && isSession && !navMode && !isFishTurn,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        val uci = first ?: return@clickable
                                        val b = sessionBoard ?: return@clickable
                                        val mv = bfUciToMoveOnBoard(b, uci) ?: return@clickable
                                        applyMoveOnSession(mv, uci)
                                        val a = uciLines.size
                                        analysisAnchor = a
                                        analyzeRequestAnchor = a
                                        evalAnchor = a

                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(text = pv.evalText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(text = pvToSanNumbered(startFen = analysisFen ?: START_FEN, uciMoves = pv.uciMoves, maxPlies = 8), style = MaterialTheme.typography.bodySmall)
                            }
                            Divider()
                        }
                    }
                }

            }

            // [OK] Move list + nav appear only after the launcher is dismissed.
            if (isSession && !showLauncherMenu && stage != BeatFishStage.SETUP) {
                val fenForTree = sessionBoard?.fen ?: (sessionStartFen ?: START_FEN)

                val bookTreeMovesForPlay = remember(fenForTree, mode, navMode) {
                    if (mode != BeatFishMode.PLAY || navMode || recordGameMode) return@remember emptyList<BookTreeMove>()
                    if (!BinaryOpeningBook.isLoaded) return@remember emptyList<BookTreeMove>()

                    runCatching {
                        val node = BinaryOpeningBook.lookup(fenForTree) ?: return@runCatching emptyList<BookTreeMove>()
                        if (node.moves.isEmpty()) return@runCatching emptyList<BookTreeMove>()

                        node.moves
                            .sortedByDescending { it.count }
                            .take(24)
                            .mapNotNull { bm ->
                                val uci = bookMoveToUci(bm).lowercase(Locale.ROOT)
                                val b0 = LibBoard().apply { loadFromFen(fenForTree) }
                                val mv = bfUciToMoveOnBoard(b0, uci) ?: return@mapNotNull null
                                val san = bfPrettySan(b0, mv, isWhiteMove = (b0.sideToMove == Side.WHITE))
                                BookTreeMove(
                                    san = san,
                                    uci = uci,
                                    count = bm.count,
                                    total = node.totalCount
                                )
                            }
                    }.getOrDefault(emptyList())
                }


                // Analyze-only: evaluation line graph (White POV), clamped to ±4.0
                if (mode == BeatFishMode.ANALYZE) {
                    val seriesSrc: List<Int?> = when (analyzeContext) {
                        BeatFishAnalyzeContext.GAME_ANALYSIS ->
                            if (annotatedCpSeries.isNotEmpty()) annotatedCpSeries else cpSnapshotsWhiteByPly.toList()
                        BeatFishAnalyzeContext.ANALYSIS_BOARD ->
                            analysisLiveCpByPos.toList()
                    }

                    // IMPORTANT: keep series length aligned to POSITION indices (0 = start).
                    // Do NOT drop nulls - null means "no review eval yet" (e.g., still in-book) and must render as 0.0.
                    val expectedPositions = moves.size + 1
                    val series: List<Int> = when {
                        seriesSrc.size == expectedPositions -> seriesSrc.map { it ?: 0 }
                        seriesSrc.size == moves.size -> listOf(0) + seriesSrc.map { it ?: 0 }
                        seriesSrc.size > expectedPositions -> seriesSrc.take(expectedPositions).map { it ?: 0 }
                        else -> seriesSrc.map { it ?: 0 } + List(expectedPositions - seriesSrc.size) { 0 }
                    }

                    if (series.size >= 2) {
                        // Pointer follows the currently displayed POSITION on the board.
                        val posIndex = if (navMode) navIndex else moves.size
                        val pointerIndex = posIndex.coerceIn(0, series.lastIndex)

                        EvalLineGraph(
                            evals = series,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            currentIndex = pointerIndex
                        )
                    }
                }


                if (recordGameMode && mode == BeatFishMode.PLAY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "White ${formatMmSs(recordWhiteClockSec)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Black ${formatMmSs(recordBlackClockSec)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (mode == BeatFishMode.PLAY && isFishTurn && fishSearchActive && gameResultTag == null) {
                    BeatFishThinkingPanel(
                        statusText = statusText,
                        thinkSeconds = fishThinkSeconds,
                        moveNowRequested = moveNowRequested,
                        onMoveNow = {
                            moveNowRequested = true
                            runCatching { ProcEngine.send("stop") }
                            statusText = "Fish moving..."
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                MoveListBox(
                    moves = moves,
                    mode = mode,
                    annotatedRibbon = if (recordGameMode) null else annotatedRibbon,
                    cpSeriesAfterWhite = annotatedCpSeries,
                    moveTimesSec = if (recordGameMode && mode == BeatFishMode.PLAY) recordMoveTimesSec.toList() else emptyList(),
                    showTimeColumns = recordGameMode && mode == BeatFishMode.PLAY,
                    showCpColumns = (mode == BeatFishMode.ANALYZE),
                    bookTreeMoves = bookTreeMovesForPlay,
                    userVarByBasePly = userVarByBasePly,
                    startFen = sessionStartFen ?: START_FEN,
                    pvByStartPly = annotatedPvByStartPly,
                    highlightedPly = if (navMode && navIndex > 0) navIndex else null,
                    moveListContainerColor = Color(0xFFF4ECD8),
                    moveListContentColor = Color(0xFF4E3B2A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    topRowContent = {
                        val status = if (mode == BeatFishMode.PLAY) {
                            when {
                                gameResultTag != null -> "Game over"
                                recordGameMode -> {
                                    val stm = sessionBoard?.sideToMove
                                    if (stm == Side.BLACK) "Black to move" else "White to move"
                                }
                                isFishTurn && fishSearchActive -> "Fish thinking..."
                                isFishTurn -> "Fish to move"
                                statusText.contains("Illegal", ignoreCase = true) -> "Illegal move"
                                else -> "Your move"
                            }
                        } else null
                        if (!status.isNullOrBlank()) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // Push the menu to the far right; keep status on the left (same line).
                            Spacer(Modifier.weight(1f))
                        }

                    },
                    navRowContent = if (isSession && navPositions.isNotEmpty()) ({
                        val lastIndex = navPositions.lastIndex

                        fun enterNavModeHeader() {
                            if (!navMode) {
                                navMode = true
                                navIndex = lastIndex
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: simple nav buttons. First/last were removed to keep
                            // both big-screen and phone layouts clean.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BeatFishSquareButton("◀", onClick = {
                                    enterNavModeHeader()
                                    if (navIndex > 0) {
                                        navIndex -= 1
                                        showNavPosition(navIndex)
                                    }
                                }, enabled = lastIndex > 0, compact = true)

                                BeatFishSquareButton("▶", onClick = {
                                    enterNavModeHeader()
                                    if (navIndex < lastIndex) {
                                        navIndex += 1
                                        showNavPosition(navIndex)
                                    }
                                }, enabled = lastIndex > 0, compact = true)
                            }

                            // Right side
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {

                                // Move Now is shown in the dedicated Fish-thinking panel above.

                                // Existing right-side play buttons stay in PLAY mode
                                if (mode == BeatFishMode.PLAY) {
                                    val canInteract = isSession && !isFishTurn && (gameResultTag == null)

                                    BeatFishSquareButton("1/2", onClick = {
                                        if (!canInteract) return@BeatFishSquareButton
                                        val threefold = isThreefoldClaimable()
                                        val fifty = isFiftyMoveClaimable()
                                        if (threefold || fifty) {
                                            val reason = when {
                                                threefold -> "Draw by threefold repetition."
                                                fifty -> "Draw by 50-move rule."
                                                else -> "Draw."
                                            }
                                            setGameOver(GameEnd("Draw", reason, "1/2-1/2"))
                                        } else {
                                            Toast.makeText(ctx, "No draw claim available yet.", Toast.LENGTH_SHORT).show()
                                        }
                                    }, enabled = canInteract, compact = true)

                                    BeatFishSquareButton("R", onClick = {
                                        if (!canInteract) return@BeatFishSquareButton
                                        val winner = fishSide()
                                        val res = resultTagForWinnerSide(winner)
                                        setGameOver(GameEnd("Resign", "You resigned. Fish wins.", res))
                                    }, enabled = canInteract, compact = true)
                                }
                            }
                        }
                    }) else null,
                    rightStatus = null,
                    headerTrailing = null,
                    onSelectPly = { ply ->
                        // ply is 1-based (after start position at index 0)
                        val idx = ply.coerceIn(0, navPositions.lastIndex.coerceAtLeast(0))
                        if (navPositions.isNotEmpty()) {
                            navMode = true
                            navIndex = idx
                            resetEditLine()
                            showNavPosition(navIndex)
                        }
                    },
                    onSelectPv = { startPly, kind, pvPlies ->
                        val pvData = annotatedPvByStartPly[startPly]?.get(kind) ?: return@MoveListBox
                        val fenTarget = fenAfterUciPlies(pvData.startFen, pvData.uciMoves, pvPlies) ?: return@MoveListBox
                        val bb = LibBoard().apply { loadFromFen(fenTarget) }

                        // Jump board to PV position (engine stays active in ANALYZE).
                        sessionBoard = bb
                        navMode = true
                        if (mode == BeatFishMode.ANALYZE) {
                            analysisFen = bb.fen
                            bfEngineFen = bb.fen
                        }
                        selectedSquare = null
                        lastFromIdx = null
                        lastToIdx = null
                    },
                    onSelectUserVar = { basePly0: Int, uciLine: List<String>, plies: Int ->
                        val mainStartFen = sessionStartFen ?: START_FEN
                        val baseFen = fenAfterUciPlies(mainStartFen, moves.map { it.uci }, basePly0)
                            ?: mainStartFen

                        val bb = LibBoard().apply { loadFromFen(baseFen) }
                        val chosenPrefix = uciLine.take(plies)

                        for (uci in chosenPrefix) {
                            val mv = bfUciToMoveOnBoard(bb, uci) ?: break
                            bb.doMove(mv)
                        }

                        sessionBoard = LibBoard().apply { loadFromFen(bb.fen) }

                        // Stay in nav mode, but DO NOT replace the mainline navigation timeline.
                        navMode = true
                        navIndex = basePly0.coerceIn(0, navPositions.lastIndex.coerceAtLeast(0))

                        // This is the true variation edit anchor.
                        // userVarByBasePly is 1-based from the caller side.
                        editBasePly = basePly0 + 1
                        editLineUci.clear()
                        editLineUci.addAll(chosenPrefix.map { it.lowercase(Locale.ROOT) })

                        if (mode == BeatFishMode.ANALYZE) {
                            analysisFen = bb.fen
                            bfEngineFen = bb.fen
                        }

                        selectedSquare = null
                        lastFromIdx = null
                        lastToIdx = null
                    }
                    ,
                    onSelectBookMove = onSelectBookMove@{ uci ->
                        val canInteract = isSession && mode == BeatFishMode.PLAY && !isFishTurn && (gameResultTag == null)
                        if (!canInteract) return@onSelectBookMove
                        val b = sessionBoard ?: return@onSelectBookMove
                        val mv = bfUciToMoveOnBoard(b, uci) ?: return@onSelectBookMove
                        applyMoveOnSession(mv, uci)
                    }

                )
            }

            if (isSession && navPositions.isNotEmpty() && mode == BeatFishMode.PLAY) {
                /* Nav bar moved into MoveListBox header */

            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        var landscapeFracState by rememberSaveable { mutableStateOf(-1f) }
        var portraitFracState by rememberSaveable { mutableStateOf(-1f) }
        var rootSize by remember { mutableStateOf(IntSize.Zero) }
        val splitterDensity = LocalDensity.current

        if (isLandscape) {
            val minFrac = 0.30f
            val maxFrac = 0.97f
            val defaultLandscapeFrac = if (rootSize.width > 0 && rootSize.height > 0) {
                val neededPx = rootSize.height.toFloat() + with(splitterDensity) { 6.dp.toPx() + 4.dp.toPx() }
                (neededPx / rootSize.width.toFloat()).coerceIn(minFrac, maxFrac)
            } else 0.60f
            LaunchedEffect(rootSize.width, rootSize.height, defaultLandscapeFrac) {
                if (landscapeFracState < 0f && rootSize.width > 0 && rootSize.height > 0) {
                    landscapeFracState = defaultLandscapeFrac
                }
            }
            val landscapeFrac = (landscapeFracState.takeIf { it > 0f } ?: defaultLandscapeFrac).coerceIn(minFrac, maxFrac)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootSize = it },
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.Top
            ) {
                BoardEvalPane(
                    modifier = Modifier
                        .weight(landscapeFrac)
                        .fillMaxHeight()
                        .padding(end = 0.dp),
                    landscape = true
                )

                BoardResizeSplitter(
                    orientation = BoardResizeSplitterOrientation.VERTICAL,
                    totalPx = rootSize.width.toFloat(),
                    color = Color(0xFFFF4444)
                ) { delta ->
                    val current = (landscapeFracState.takeIf { it > 0f } ?: landscapeFrac).coerceIn(minFrac, maxFrac)
                    landscapeFracState = (current + delta).coerceIn(minFrac, maxFrac)
                }

                RightPane(
                    modifier = Modifier
                        .weight(1f - landscapeFrac)
                        .fillMaxHeight()
                        .padding(start = 6.dp),
                    showHeader = true
                )
            }
        } else {
            val minFrac = 0.30f
            val maxFrac = 0.97f
            val defaultPortraitFrac = if (rootSize.width > 0 && rootSize.height > 0) {
                val neededPx = rootSize.width.toFloat() + with(splitterDensity) { 6.dp.toPx() + 4.dp.toPx() }
                (neededPx / rootSize.height.toFloat()).coerceIn(minFrac, maxFrac)
            } else 0.52f
            LaunchedEffect(rootSize.width, rootSize.height, defaultPortraitFrac) {
                if (portraitFracState < 0f && rootSize.width > 0 && rootSize.height > 0) {
                    portraitFracState = defaultPortraitFrac
                }
            }
            val portraitFrac = (portraitFracState.takeIf { it > 0f } ?: defaultPortraitFrac).coerceIn(minFrac, maxFrac)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootSize = it },
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                BoardEvalPane(
                    modifier = Modifier
                        .weight(portraitFrac)
                        .fillMaxWidth(),
                    landscape = false
                )

                BoardResizeSplitter(
                    orientation = BoardResizeSplitterOrientation.HORIZONTAL,
                    totalPx = rootSize.height.toFloat(),
                    color = Color(0xFFFF4444)
                ) { delta ->
                    val current = (portraitFracState.takeIf { it > 0f } ?: portraitFrac).coerceIn(minFrac, maxFrac)
                    portraitFracState = (current + delta).coerceIn(minFrac, maxFrac)
                }

                RightPane(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - portraitFrac),
                    showHeader = false
                )
            }
        }
    }

    if (legalityError != null) {
        AlertDialog(
            onDismissRequest = { legalityError = null },
            title = { Text("Invalid position") },
            text = { Text(legalityError!!) },
            confirmButton = { TextButton(onClick = { legalityError = null }) { Text("OK") } }
        )
    }
    if (showAnnotateDialog) {
        AlertDialog(
            onDismissRequest = { showAnnotateDialog = false },
            title = { Text("Annotations") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(annotateText)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnnotateDialog = false }) { Text("Close") }
            }
        )
    }

    fun saveCurrentBeatFishGameToMaster() {
        if (!isSession || moves.isEmpty()) return

        val start = sessionStartFen ?: (sessionBoard?.fen ?: START_FEN)
        val baseRibbon = if (recordGameMode) {
            annotatedRibbon ?: rebuildRecordRibbon(moves.toList())
        } else {
            annotatedRibbon ?: buildBeatFishMoveRibbon(moves.toList())
        }

        val exportRibbon = buildBeatFishExportRibbon(
            startFen = start,
            moves = moves.toList(),
            ribbon = baseRibbon,
            userVarByBasePly = userVarByBasePly,
            pvByStartPly = annotatedPvByStartPly,
            result = gameResultTag ?: "*"
        )

        val pgn = buildBeatFishPgnText(
            startFen = start,
            ribbon = exportRibbon,
            result = gameResultTag ?: "*"
        )

        pendingMasterPgnGame = pgn
        val masterUri = getMasterPgnUri(ctx)
        if (masterUri == null) {
            masterPgnCreateLauncher.launch(DEFAULT_MASTER_PGN_NAME)
        } else {
            scope.launch {
                appendPgnGameToMasterUri(context = ctx, uri = masterUri, gamePgn = pgn)
                    .onSuccess {
                        Toast.makeText(ctx, "Saved to master PGN", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { t ->
                        setMasterPgnUri(ctx, null)
                        Toast.makeText(
                            ctx,
                            "Save failed: ${t.message ?: "error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    fun openPostGameAnalysisFromGameOver() {
        showGameOverDialog = false

        mode = BeatFishMode.ANALYZE
        analyzeContext = BeatFishAnalyzeContext.GAME_ANALYSIS
        analysisFen = sessionBoard?.fen ?: bfEngineFen ?: effectiveFen

        val start = sessionStartFen ?: (sessionBoard?.fen ?: START_FEN)
        val snap = moves.toList()

        val bg = bgAnnotationRes
        if (bg != null && bgAnnotationPlyCount == snap.size) {
            annotatedRibbon = if (recordGameMode) injectMoveTimesIntoRibbon(bg.annotatedRibbon, recordMoveTimesSec.toList()) else bg.annotatedRibbon
            annotatedCpSeries = bg.cpSeriesAfterWhite
            annotatedPvByStartPly = bg.pvByStartPly
            annotateText = bg.summary
            isAnnotating = false
            showAnnotateDialog = true
        } else {
            isAnnotating = true
            annotateText = if (bgAnnotRunning) "Finishing annotation..." else "Annotating..."
            showAnnotateDialog = true

            scope.launch {
                val res = runCatching {
                    buildAnnotationsOneShot(
                        startFen = start,
                        movesNow = snap,
                        cpSnapshotsWhiteByPly = cpSnapshotsWhiteByPly.toList(),
                        perMoveMs = 1000L
                    ) { cur, total ->
                        withContext(Dispatchers.Main) {
                            annotateText = "Annotating move $cur/$total..."
                        }
                    }
                }.getOrElse { tt ->
                    AnnotationResult(
                        annotatedRibbon = if (recordGameMode) injectMoveTimesIntoRibbon(buildBeatFishMoveRibbon(snap), recordMoveTimesSec.toList()) else buildBeatFishMoveRibbon(snap),
                        summary = "Annotation failed: ${tt.message ?: tt.toString()}"
                    )
                }

                annotatedRibbon = if (recordGameMode) injectMoveTimesIntoRibbon(res.annotatedRibbon, recordMoveTimesSec.toList()) else res.annotatedRibbon
                annotatedCpSeries = res.cpSeriesAfterWhite
                annotatedPvByStartPly = res.pvByStartPly
                annotateText = res.summary
                isAnnotating = false
            }
        }
    }

    fun startNewBeatFishGameFromGameOver() {
        showGameOverDialog = false
        runCatching { ProcEngine.send("stop") }
        runCatching { ProcEngine.clearOutput() }
        fishSearchActive = false
        isFishTurn = false
        effectiveFen = START_FEN
        resetSessionState()
        onStartFromStart()
        startPlayUiNow()
    }

    if (showGameOverDialog && gameResultTag != null && gameOverMessage != null) {
        AlertDialog(
            onDismissRequest = { showGameOverDialog = false },
            title = { Text("Game Over (${gameResultTag})") },
            text = { Text(gameOverMessage!!) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BeatFishSquareButton(
                            text = "New Game",
                            onClick = { startNewBeatFishGameFromGameOver() },
                            modifier = Modifier.weight(1f),
                            compact = false,
                            backgroundColor = Color(0xFF2563EB),
                            textColor = Color.White
                        )
                        BeatFishSquareButton(
                            text = "Analyze Game",
                            onClick = { openPostGameAnalysisFromGameOver() },
                            modifier = Modifier.weight(1f),
                            compact = false,
                            backgroundColor = Color(0xFF059669),
                            textColor = Color.White
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BeatFishSquareButton(
                            text = "Save PGN",
                            onClick = { saveCurrentBeatFishGameToMaster() },
                            enabled = moves.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            compact = false,
                            backgroundColor = Color(0xFFF59E0B),
                            textColor = Color.Black
                        )
                        BeatFishSquareButton(
                            text = "Menu",
                            onClick = {
                                showGameOverDialog = false
                                openBeatFishLauncherMenu()
                            },
                            modifier = Modifier.weight(1f),
                            compact = false,
                            backgroundColor = Color(0xFF334155),
                            textColor = Color.White
                        )
                    }
                }
            },
            dismissButton = {}
        )
    }




    fun runAnnotation(deep: Boolean) {
        if (!isSession) return
        if (isAnnotating) return

        val start = sessionStartFen ?: (sessionBoard?.fen ?: START_FEN)
        val moveSnap = moves.toList()

        isAnnotating = true
        annotateText = if (deep) "Deep annotating..." else "Annotating..."
        showAnnotateDialog = true

        scope.launch {
            val res = runCatching {
                buildAnnotationsOneShot(
                    startFen = start,
                    movesNow = moveSnap,
                    cpSnapshotsWhiteByPly = if (analyzeContext == BeatFishAnalyzeContext.GAME_ANALYSIS) cpSnapshotsWhiteByPly.toList() else null,
                    perMoveMs = if (deep) 3000L else 1000L

                ) { cur, total ->
                    withContext(Dispatchers.Main) {
                        annotateText =
                            if (deep) "Deep annotating move $cur/$total..."
                            else "Annotating move $cur/$total..."
                    }
                }
            }.getOrElse { tt ->
                AnnotationResult(
                    annotatedRibbon = if (recordGameMode) {
                        injectMoveTimesIntoRibbon(buildBeatFishMoveRibbon(moveSnap), recordMoveTimesSec.toList())
                    } else {
                        buildBeatFishMoveRibbon(moveSnap)
                    },
                    summary = "Annotation failed: ${tt.message ?: tt.toString()}"
                )
            }

            annotatedRibbon = if (recordGameMode) {
                injectMoveTimesIntoRibbon(res.annotatedRibbon, recordMoveTimesSec.toList())
            } else {
                res.annotatedRibbon
            }
            annotatedCpSeries = res.cpSeriesAfterWhite
            annotatedPvByStartPly = res.pvByStartPly
            annotateText = res.summary
            isAnnotating = false
        }
    }

    if (showAnnotateChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showAnnotateChoiceDialog = false },
            title = { Text("Annotation mode") },
            text = { Text("Choose analysis depth.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showAnnotateChoiceDialog = false
                            runAnnotation(deep = false)
                        }
                    ) {
                        Text("Fast")
                    }
                    TextButton(
                        onClick = {
                            showAnnotateChoiceDialog = false
                            runAnnotation(deep = true)
                        }
                    ) {
                        Text("Deep")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnnotateChoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}



fun evalTextFromInfoWhitePov(infoLine: String, fenForEval: String): String {
    val wtm = whiteToMoveFen(fenForEval)

    // Mate score is also from side-to-move POV; convert to White POV by flipping when Black is to move.
    val mate = Regex("""\bscore\s+mate\s+(-?\d+)""")
        .find(infoLine)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    if (mate != null) {
        val wMate = if (wtm) mate else -mate
        return if (wMate > 0) "M$wMate" else "-M${kotlin.math.abs(wMate)}"
    }

    val cp = Regex("""\bscore\s+cp\s+(-?\d+)""")
        .find(infoLine)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0

    val wCp = if (wtm) cp else -cp
    return String.format(java.util.Locale.US, "%+.2f", wCp / 100.0)
}




@Composable
private fun PiecePalette(
    selected: Piece?,
    onSelected: (Piece?) -> Unit
) {
    // [OK] TOP ROW = BLACK, BOTTOM ROW = WHITE
    val blackPieces = listOf(
        Piece(PieceType.QUEEN, false),
        Piece(PieceType.ROOK, false),
        Piece(PieceType.BISHOP, false),
        Piece(PieceType.KNIGHT, false),
        Piece(PieceType.PAWN, false)
    )
    val whitePieces = listOf(
        Piece(PieceType.QUEEN, true),
        Piece(PieceType.ROOK, true),
        Piece(PieceType.BISHOP, true),
        Piece(PieceType.KNIGHT, true),
        Piece(PieceType.PAWN, true)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        @Composable
        fun RowScope.PieceButton(p: Piece) {
            val sel = (p == selected)
            val label = if (sel) "* ${p.glyph}" else p.glyph
            val textColor = if (p.isWhite) Color.White else Color.Black
            val bg = if (p.isWhite) Color(0xFF1B1B1B) else Color(0xFFE6E6E6)

            OutlinedButton(
                onClick = { onSelected(if (selected == p) null else p) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = bg,
                    contentColor = textColor
                )
            ) {
                Text(text = label)
            }
        }


        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            blackPieces.forEach { PieceButton(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            whitePieces.forEach { PieceButton(it) }
        }
    }
}


private data class BookTreeMove(
    val san: String,
    val uci: String,
    val count: Int,
    val total: Int
)



@Composable
private fun MoveListBox(
    moves: List<BFMove>,
    mode: BeatFishMode,
    annotatedRibbon: String? = null,
    cpSeriesAfterWhite: List<Int?> = emptyList(),
    moveTimesSec: List<Int> = emptyList(),
    showTimeColumns: Boolean = false,
    showCpColumns: Boolean = true,
    bookTreeMoves: List<BookTreeMove> = emptyList(),
    userVarByBasePly: Map<Int, List<List<String>>> = emptyMap(),
    startFen: String = START_FEN,
    pvByStartPly: Map<Int, Map<String, BeatPvData>> = emptyMap(),
    modifier: Modifier = Modifier,
    moveListContainerColor: Color = MaterialTheme.colorScheme.surface,
    moveListContentColor: Color = MaterialTheme.colorScheme.onSurface,
    rightStatus: String? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    topRowContent: (@Composable RowScope.() -> Unit)? = null,
    navRowContent: (@Composable RowScope.() -> Unit)? = null,
    highlightedPly: Int? = null,
    onSelectPly: (Int) -> Unit = {},
    onSelectPv: (startPly: Int, kind: String, pvPlies: Int) -> Unit = { _, _, _ -> },
    onSelectUserVar: (basePly: Int, uciLine: List<String>, plies: Int) -> Unit = { _, _, _ -> },
    onSelectBookMove: (uci: String) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val bringMap = remember { mutableMapOf<Int, BringIntoViewRequester>() }
    val ribbon = annotatedRibbon ?: buildBeatFishMoveRibbon(moves)

    val isSepia = moveListContainerColor == Color(0xFFF4ECD8)

    val outlineColor = if (isSepia) {
        Color(0xFF8B6F47).copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }

    val subtleTextColor = if (isSepia) {
        Color(0xFF7A5C3A)
    } else {
        moveListContentColor.copy(alpha = 0.72f)
    }

    val cpColor = if (isSepia) {
        Color(0xFF9C5A1A)
    } else {
        subtleTextColor
    }

    val annColor = if (isSepia) {
        Color(0xFF6E7F3F)
    } else {
        Color(0xFF4CAF50)
    }

    val bestStarColor = if (isSepia) {
        Color(0xFFB26A1F)
    } else {
        annColor
    }

    val headerTextColor = subtleTextColor

    // Recorder mode uses a simple phone-safe layout:
    // # | White move   time | Black move   time
    // No separate Time columns, so small phones cannot clip the seconds digit.
    val moveNoColumnWidth = if (showTimeColumns) 28.dp else 34.dp
    val timeColumnWidth = 54.dp
    val compactTimeTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)

    val annByPly = remember(ribbon) {
        extractPlyCommentsFromRibbon(ribbon)
    }

    val moveTokByPly = remember(ribbon) {
        extractPlyMoveTokensFromRibbon(ribbon)
    }

    LaunchedEffect(moves.size, ribbon, userVarByBasePly, highlightedPly) {
        delay(80) // let the move-list measure before scrolling to the new bottom
        val ply = highlightedPly
        val req = if (ply != null) bringMap[ply] else null
        if (req != null) {
            runCatching { req.bringIntoView() }
        } else {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun cpText(cp: Int?): String {
        if (cp == null) return "..."
        val v = cp / 100.0
        return if (v >= 0) String.format("+%.1f", v) else String.format("%.1f", v)
    }

    fun moveTimeText(ply: Int?): String {
        if (ply == null) return ""
        val sec = moveTimesSec.getOrNull(ply - 1) ?: return ""
        val safe = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    @Composable
    fun TimeCell(text: String) {
        Text(
            text = text,
            modifier = Modifier.width(timeColumnWidth),
            color = subtleTextColor,
            style = compactTimeTextStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }

    fun recorderMoveAnnotatedText(
        san: String,
        time: String?
    ): AnnotatedString {
        return buildAnnotatedString {
            append(bfSanToFanDisplay(san))

            if (showTimeColumns && !time.isNullOrBlank()) {
                append("   ")
                withStyle(SpanStyle(color = Color.Red)) {
                    append(time)
                }
            }
        }
    }

    fun nagColorForSan(san: String?): Color? {
        if (san.isNullOrBlank()) return null
        return when {
            san.endsWith("??") -> Color(0xFFFF5252)
            san.endsWith("?!") -> cpColor
            san.endsWith("?") -> cpColor
            else -> null
        }
    }

    fun bestMoveAnnotatedText(san: String, isBest: Boolean): AnnotatedString {
        return buildAnnotatedString {
            append(san)
            if (isBest) {
                withStyle(SpanStyle(color = bestStarColor)) { append("★") }
            }
        }
    }

    data class RowData(
        val moveNo: Int,
        val whitePly: Int?, val whiteSan: String?, val whiteCp: Int?, val whiteTime: String?,
        val blackPly: Int?, val blackSan: String?, val blackCp: Int?, val blackTime: String?
    )

    val rows = remember(moves.size, cpSeriesAfterWhite.size, mode, moveTimesSec.toList(), showTimeColumns) {
        val out = ArrayList<RowData>()

        fun tagSanForPly(plyIndex0: Int, baseSan: String?): String? {
            if (baseSan.isNullOrBlank()) return baseSan
            if (mode != BeatFishMode.PLAY) return baseSan
            if (
                baseSan.endsWith("??") ||
                baseSan.endsWith("?!") ||
                baseSan.endsWith("?") ||
                baseSan.endsWith("!")
            ) return baseSan

            val after = cpSeriesAfterWhite.getOrNull(plyIndex0) ?: return baseSan
            val before = cpSeriesAfterWhite.getOrNull(plyIndex0 - 1) ?: return baseSan

            val moverWhite = (plyIndex0 % 2 == 0)
            val loss = if (moverWhite) (before - after) else (after - before)
            val gain = -loss

            val tag = when {
                loss >= 150 -> "??"
                loss >= 80 -> "?"
                loss >= 40 -> "?!"
                gain >= 90 -> "!"
                else -> ""
            }
            return if (tag.isEmpty()) baseSan else (baseSan + tag)
        }

        var i = 0
        while (i < moves.size) {
            val m = moves[i]
            val moveNo = m.moveNumber
            if (m.isWhite) {
                val whitePly = i + 1
                val whiteSan = tagSanForPly(i, m.san)
                val whiteCp = cpSeriesAfterWhite.getOrNull(i)

                val black = moves.getOrNull(i + 1)?.takeIf { !it.isWhite && it.moveNumber == moveNo }
                val blackPly = if (black != null) i + 2 else null
                val blackSan = tagSanForPly(i + 1, black?.san)
                val blackCp = if (black != null) cpSeriesAfterWhite.getOrNull(i + 1) else null

                out.add(RowData(
                    moveNo = moveNo,
                    whitePly = whitePly,
                    whiteSan = whiteSan,
                    whiteCp = whiteCp,
                    whiteTime = moveTimeText(whitePly),
                    blackPly = blackPly,
                    blackSan = blackSan,
                    blackCp = blackCp,
                    blackTime = moveTimeText(blackPly)
                ))
                i += if (black != null) 2 else 1
            } else {
                val blackPly = i + 1
                out.add(RowData(
                    moveNo = moveNo,
                    whitePly = null,
                    whiteSan = null,
                    whiteCp = null,
                    whiteTime = null,
                    blackPly = blackPly,
                    blackSan = m.san,
                    blackCp = cpSeriesAfterWhite.getOrNull(i),
                    blackTime = moveTimeText(blackPly)
                ))
                i += 1
            }
        }
        out
    }

    @Composable
    fun renderUserVarsAfterPly(ply: Int?) {
        if (ply == null) return
        val lines = userVarByBasePly[ply].orEmpty()
        if (lines.isEmpty()) return

        Spacer(Modifier.height(if (isSepia) 6.dp else 4.dp))

        val baseFen = fenAfterUciPlies(
            startFen,
            moves.map { it.uci },
            (ply - 1).coerceAtLeast(0)
        ) ?: startFen

        val annotated = buildUserVarTreeAnnotatedString(
            startFen = baseFen,
            lines = lines,
            color = annColor
        )

        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodySmall.copy(
                color = annColor,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.12f
            ),
            modifier = Modifier.padding(start = 34.dp),
            onClick = { off ->
                val seq = annotated.getStringAnnotations(tag = "UVARSEQ", start = off, end = off)
                    .firstOrNull()
                    ?.item

                if (!seq.isNullOrBlank()) {
                    val prefix = seq.split("\u0001").filter { it.isNotBlank() }
                    if (prefix.isNotEmpty()) {
                        onSelectUserVar(ply - 1, prefix, prefix.size)
                    }
                }
            }
        )
    }

    @Composable
    fun renderAnnotationsForPly(ply: Int?) {
        if (ply == null) return
        val list = annByPly[ply].orEmpty()
        if (list.isEmpty()) return

        Spacer(Modifier.height(if (isSepia) 6.dp else 4.dp))
        list.forEach { raw ->
            val annotated = buildCommentAnnotatedString(
                comment = raw,
                plyJustPlayed = ply,
                annotationColor = annColor,
                pvByStartPly = pvByStartPly
            )

            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = annColor,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.12f
                ),
                modifier = Modifier.padding(start = 34.dp),
                onClick = { off ->
                    annotated.getStringAnnotations(tag = "PV", start = off, end = off)
                        .firstOrNull()
                        ?.item
                        ?.split(':')
                        ?.takeIf { it.size == 3 }
                        ?.let { parts ->
                            val startPly = parts[0].toIntOrNull()
                            val kind = parts[1]
                            val pvPlies = parts[2].toIntOrNull()
                            if (startPly != null && pvPlies != null) {
                                onSelectPv(startPly, kind, pvPlies)
                            }
                        }
                }
            )
        }
    }

    @Composable
    fun MoveRowBlock(r: RowData) {
        val hasWhiteAnn = (r.whitePly != null && annByPly[r.whitePly].orEmpty().isNotEmpty())
        val hasWhiteVars = (r.whitePly != null && userVarByBasePly[r.whitePly].orEmpty().isNotEmpty())
        val splitBlackAfterWhite = (r.blackPly != null && (hasWhiteAnn || hasWhiteVars))

        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${r.moveNo}.",
                    modifier = Modifier.width(moveNoColumnWidth),
                    color = moveListContentColor
                )

                if (r.whitePly != null) {
                    val tok = moveTokByPly[r.whitePly] ?: r.whiteSan
                    if (!tok.isNullOrBlank()) {
                        val isBest = tok.startsWith("§")
                        val san0 = tok.removePrefix("§")
                        val baseColor = nagColorForSan(san0) ?: moveListContentColor
                        Text(
                            text = recorderMoveAnnotatedText(san0, r.whiteTime),
                            color = baseColor,
                            modifier = Modifier
                                .weight(1f)
                                .let { base ->
                                    val ply = r.whitePly
                                    if (ply != null) {
                                        val req = remember(ply) { BringIntoViewRequester() }
                                        LaunchedEffect(ply, req) {
                                            bringMap[ply] = req
                                        }
                                        val hilite = (highlightedPly != null && highlightedPly == ply)
                                        base
                                            .bringIntoViewRequester(req)
                                            .background(
                                                when {
                                                    !hilite -> Color.Transparent
                                                    isSepia -> Color(0xFFE8D7B8).copy(alpha = 0.95f)
                                                    else -> Color(0xFF8A8A8A).copy(alpha = 0.22f)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                width = if (hilite) 1.dp else 0.dp,
                                                color = if (hilite) {
                                                    if (isSepia) Color(0xFFB08A57)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    } else base
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelectPly(r.whitePly) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (!showTimeColumns && showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }

                if (!splitBlackAfterWhite && r.blackPly != null) {
                    val tok = moveTokByPly[r.blackPly] ?: r.blackSan
                    if (!tok.isNullOrBlank()) {
                        val isBest = tok.startsWith("§")
                        val san0 = tok.removePrefix("§")
                        val baseColor = nagColorForSan(san0) ?: moveListContentColor
                        Text(
                            text = recorderMoveAnnotatedText(san0, r.blackTime),
                            color = baseColor,
                            modifier = Modifier
                                .weight(1f)
                                .let { base ->
                                    val ply = r.blackPly
                                    if (ply != null) {
                                        val req = remember(ply) { BringIntoViewRequester() }
                                        LaunchedEffect(ply, req) {
                                            bringMap[ply] = req
                                        }
                                        val hilite = (highlightedPly != null && highlightedPly == ply)
                                        base
                                            .bringIntoViewRequester(req)
                                            .background(
                                                when {
                                                    !hilite -> Color.Transparent
                                                    isSepia -> Color(0xFFE8D7B8).copy(alpha = 0.95f)
                                                    else -> Color(0xFF8A8A8A).copy(alpha = 0.22f)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                width = if (hilite) 1.dp else 0.dp,
                                                color = if (hilite) {
                                                    if (isSepia) Color(0xFFB08A57)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    } else base
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelectPly(r.blackPly) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                } else if (r.blackPly != null) {
                    Text(
                        text = "...",
                        modifier = Modifier.weight(1f),
                        color = subtleTextColor
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (!showTimeColumns && showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }
            }

            renderUserVarsAfterPly(r.whitePly)
            renderAnnotationsForPly(r.whitePly)

            if (splitBlackAfterWhite && r.blackPly != null && r.blackSan != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(moveNoColumnWidth))
                    Text(
                        text = "...",
                        modifier = Modifier.weight(1f),
                        color = subtleTextColor
                    )
                    if (!showTimeColumns && showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }

                    val tok = moveTokByPly[r.blackPly] ?: r.blackSan
                    val isBest = tok?.startsWith("§") == true
                    val san0 = tok?.removePrefix("§") ?: ""
                    val baseColor = nagColorForSan(san0) ?: moveListContentColor
                    Text(
                        text = recorderMoveAnnotatedText(san0, r.blackTime),
                        color = baseColor,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectPly(r.blackPly) }
                    )
                    if (!showTimeColumns && showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }
                }
            }

            renderUserVarsAfterPly(r.blackPly)
            renderAnnotationsForPly(r.blackPly)
        }
    }

    Column(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (topRowContent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topRowContent()
                }
            }

            if (navRowContent != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navRowContent()
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(moveListContainerColor)
                .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            if (rows.isEmpty()) {
                Text(
                    "No moves yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = moveListContentColor
                )
            } else {
                if (bookTreeMoves.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#", modifier = Modifier.width(moveNoColumnWidth), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (!showTimeColumns && showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                            Text(
                                "Black",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = headerTextColor
                            )
                            if (!showTimeColumns && showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                        }
                        Divider(color = outlineColor)

                        rows.forEach { r ->
                            MoveRowBlock(r)
                        }

                        val tailVars = userVarByBasePly[moves.size].orEmpty()
                        if (tailVars.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Divider(color = outlineColor)
                            Spacer(Modifier.height(6.dp))

                            val baseFen = fenAfterUciPlies(startFen, moves.map { it.uci }, moves.size) ?: startFen
                            val annotated = buildUserVarTreeAnnotatedString(
                                startFen = baseFen,
                                lines = tailVars,
                                color = annColor
                            )

                            ClickableText(
                                text = annotated,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = annColor,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.12f
                                ),
                                modifier = Modifier.padding(start = 34.dp),
                                onClick = { off ->
                                    val seq = annotated.getStringAnnotations(tag = "UVARSEQ", start = off, end = off)
                                        .firstOrNull()
                                        ?.item

                                    if (!seq.isNullOrBlank()) {
                                        val prefix = seq.split("\u0001").filter { it.isNotBlank() }
                                        if (prefix.isNotEmpty()) {
                                            onSelectUserVar(moves.size, prefix, prefix.size)
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.42f)
                                .padding(end = 2.dp)
                        ) {
                            Text(
                                text = "Book",
                                style = MaterialTheme.typography.bodySmall,
                                color = headerTextColor
                            )
                            Spacer(Modifier.height(4.dp))

                            val total = bookTreeMoves.firstOrNull()?.total?.coerceAtLeast(1) ?: 1
                            bookTreeMoves.forEach { bm ->
                                val pct = (bm.count * 100f / total.toFloat()).coerceIn(0f, 100f)
                                val pctInt = (pct + 0.5f).toInt()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onSelectBookMove(bm.uci) }
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bfSanToFanDisplay(bm.san),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        softWrap = false,
                                        color = moveListContentColor
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${pctInt}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cpColor
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(outlineColor.copy(alpha = 0.6f))
                        )

                        Column(
                            modifier = Modifier
                                .weight(0.58f)
                                .padding(start = 2.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#", modifier = Modifier.width(moveNoColumnWidth), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                                    Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                                    if (showCpColumns) {
                                        Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                                    }
                                    Text("Black", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                                    if (showCpColumns) {
                                        Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                                    }
                                }
                                Divider(color = outlineColor)

                                rows.forEach { r ->
                                    MoveRowBlock(r)
                                }

                                val tailVars = userVarByBasePly[moves.size].orEmpty()
                                if (tailVars.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Divider(color = outlineColor)
                                    Spacer(Modifier.height(6.dp))
                                    tailVars.forEach { line ->
                                        val sanLine = pvToSanNumbered(
                                            startFen = fenAfterUciPlies(startFen, moves.map { it.uci }, moves.size) ?: startFen,
                                            uciMoves = line,
                                            maxPlies = 16
                                        )
                                        Text(
                                            text = "(${sanLine})",
                                            color = annColor,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .padding(start = 34.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    onSelectUserVar(moves.size, line, line.size)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildBeatFishMoveRibbon(moves: List<BFMove>): String {
    val sb = StringBuilder()
    for (m in moves) {
        if (m.isWhite) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append("${m.moveNumber}. ${m.san}")
        } else {
            sb.append(' ')
            sb.append(m.san)
        }
    }
    return sb.toString()
}

// Extract PGN comments (in braces) and attach each to the ply number (1-based)
// of the last move token seen before the comment.
private fun extractPlyCommentsFromRibbon(ribbon: String): Map<Int, List<String>> {
    val out = LinkedHashMap<Int, MutableList<String>>()

    val moveNoRe = Regex("""^\d+\.{1,3}$""") // 1. or 1...
    val resultRe = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")

    var ply = 0
    var i = 0
    while (i < ribbon.length) {
        val ch = ribbon[i]
        if (ch.isWhitespace()) {
            i++
            continue
        }

        if (ch == '{') {
            val start = i + 1
            var j = start
            while (j < ribbon.length && ribbon[j] != '}') j++
            val comment = ribbon.substring(start, j).trim()
            if (comment.isNotEmpty() && ply > 0) {
                out.getOrPut(ply) { ArrayList() }.add(comment)
            }
            i = if (j < ribbon.length) j + 1 else ribbon.length
            continue
        }

        // Token (until whitespace or '{')
        val start = i
        var end = i
        while (end < ribbon.length && !ribbon[end].isWhitespace() && ribbon[end] != '{' && ribbon[end] != '}') end++
        val tok = ribbon.substring(start, end)

        if (!moveNoRe.matches(tok) && !resultRe.matches(tok)) {
            // Treat as a SAN move token.
            ply++
        }
        i = end
    }

    return out
}

// Extract SAN move tokens (including NAG suffixes like ??, ?, ?! and the best-move marker §)
// and attach each to its ply number (1-based). We only start counting plies once we see the
// first move number token (e.g., "1." or "1...") so opening headers like "ECO ..." are ignored.
private fun extractPlyMoveTokensFromRibbon(ribbon: String): Map<Int, String> {
    val out = LinkedHashMap<Int, String>()

    val moveNoRe = Regex("""^\d+\.{1,3}$""") // 1. or 1...
    val resultRe = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")

    var startedMoves = false
    var ply = 0
    var i = 0
    while (i < ribbon.length) {
        val ch = ribbon[i]
        if (ch.isWhitespace()) {
            i++
            continue
        }

        if (ch == '{') {
            // Skip comment block
            var j = i + 1
            while (j < ribbon.length && ribbon[j] != '}') j++
            i = if (j < ribbon.length) j + 1 else ribbon.length
            continue
        }

        // Token (until whitespace or '{' / '}')
        val start = i
        var end = i
        while (end < ribbon.length && !ribbon[end].isWhitespace() && ribbon[end] != '{' && ribbon[end] != '}') end++
        val tok = ribbon.substring(start, end)

        if (moveNoRe.matches(tok)) {
            startedMoves = true
        } else if (startedMoves && !resultRe.matches(tok)) {
            // Treat as a move token (SAN), including possible § prefix and NAG suffix.
            ply++
            out[ply] = tok
        }

        i = end
    }

    return out
}


private fun buildCommentAnnotatedString(
    comment: String,
    plyJustPlayed: Int,
    annotationColor: Color,
    pvByStartPly: Map<Int, Map<String, BeatPvData>>
): AnnotatedString {
    val b = AnnotatedString.Builder()
    b.pushStyle(SpanStyle(color = annotationColor))

    val startPly0 = (plyJustPlayed - 1).coerceAtLeast(0)
    val pvGroups = pvByStartPly[startPly0]

    val moveNoRe = Regex("""^\d+\.{1,3}$""")
    val resultRe = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")
    val hasMoveGlyphRe = Regex("""[a-hKQRBN0O]""")

    data class Marker(val label: String, val kind: String)
    val markers = listOf(
        Marker("Best:", "BEST"),
        Marker("Book main line:", "BOOK"),
        Marker("Line played:", "LINE"),
        Marker("Your line:", "LINE"),
        Marker("Custom:", "LINE")
    )

    fun appendClickableSegment(seg: String, pvKind: String) {
        val pvData = pvGroups?.get(pvKind)
        if (pvData == null) {
            b.append(seg)
            return
        }

        var pvPly = 0
        var k = 0
        while (k < seg.length) {
            val cc = seg[k]
            if (cc.isWhitespace()) {
                b.append(cc)
                k++
                continue
            }

            val tkStart = k
            var tkEnd = k
            while (tkEnd < seg.length && !seg[tkEnd].isWhitespace()) tkEnd++
            val tok = seg.substring(tkStart, tkEnd)

            val isPvMoveToken =
                tok.isNotBlank() &&
                        !moveNoRe.matches(tok) &&
                        !resultRe.matches(tok) &&
                        hasMoveGlyphRe.containsMatchIn(tok)

            if (isPvMoveToken) {
                val pvPlies = (pvPly + 1).coerceAtMost(pvData.uciMoves.size)
                b.pushStringAnnotation(
                    tag = "PV",
                    annotation = "${startPly0}:${pvKind}:${pvPlies}"
                )
                b.append(tok)
                b.pop()
                pvPly++
            } else {
                b.append(tok)
            }
            k = tkEnd
        }
    }

    // Walk through comment text; whenever we detect a marker label, we switch PV kind.
    var idx = 0
    var currentKind: String? = null
    while (idx < comment.length) {
        val next = markers
            .mapNotNull { m ->
                val at = comment.indexOf(m.label, startIndex = idx)
                if (at >= 0) at to m else null
            }
            .minByOrNull { it.first }

        if (next == null) {
            val tail = comment.substring(idx)
            if (currentKind == null) b.append(tail) else appendClickableSegment(tail, currentKind!!)
            idx = comment.length
        } else {
            val at = next.first
            val marker = next.second
            val before = comment.substring(idx, at)
            if (before.isNotEmpty()) {
                if (currentKind == null) b.append(before) else appendClickableSegment(before, currentKind!!)
            }
            b.append(marker.label)
            currentKind = marker.kind
            idx = at + marker.label.length
        }
    }

    b.pop()
    return b.toAnnotatedString()
}

private data class UserVarTreeNode(
    val uci: String,
    val children: LinkedHashMap<String, UserVarTreeNode> = LinkedHashMap()
)

private fun buildUserVarForest(lines: List<List<String>>): List<UserVarTreeNode> {
    val roots = LinkedHashMap<String, UserVarTreeNode>()

    for (raw in lines) {
        val line = raw.map { it.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }
        if (line.isEmpty()) continue

        var curMap = roots
        for (uci in line) {
            val node = curMap.getOrPut(uci) { UserVarTreeNode(uci) }
            curMap = node.children
        }
    }

    return roots.values.toList()
}

private fun userVarTreeMaxDepth(node: UserVarTreeNode): Int {
    if (node.children.isEmpty()) return 1
    return 1 + (node.children.values.maxOfOrNull { userVarTreeMaxDepth(it) } ?: 0)
}

private fun orderedUserVarChildren(node: UserVarTreeNode): List<UserVarTreeNode> {
    return node.children.values.sortedWith(
        compareByDescending<UserVarTreeNode> { userVarTreeMaxDepth(it) }
            .thenBy { it.uci }
    )
}

private fun userVarMoveToken(
    boardBefore: LibBoard,
    uci: String,
    forceMoveNumber: Boolean
): Pair<String, LibBoard>? {
    val mv = bfUciToMoveOnBoard(boardBefore, uci) ?: return null

    val before = LibBoard().apply { loadFromFen(boardBefore.fen) }
    val isWhiteMove = (boardBefore.sideToMove == Side.WHITE)
    val moveNo = boardBefore.fen.split(' ').getOrNull(5)?.toIntOrNull() ?: 1
    val san = bfPrettySan(before, mv, isWhiteMove = isWhiteMove)
    val displaySan = bfSanToFanDisplay(san)

    val tok = when {
        isWhiteMove -> "$moveNo. $displaySan"
        forceMoveNumber -> "$moveNo... $displaySan"
        else -> displaySan
    }

    val after = LibBoard().apply { loadFromFen(boardBefore.fen) }
    after.doMove(mv)

    return tok to after
}

private fun appendUserVarNodeAnnotated(
    b: AnnotatedString.Builder,
    boardBefore: LibBoard,
    node: UserVarTreeNode,
    prefixBefore: List<String>,
    color: Color,
    forceMoveNumber: Boolean
) {
    val step = userVarMoveToken(
        boardBefore = boardBefore,
        uci = node.uci,
        forceMoveNumber = forceMoveNumber
    ) ?: return

    val tok = step.first
    val after = step.second
    val prefixHere = prefixBefore + node.uci.lowercase(Locale.ROOT)

    b.pushStyle(SpanStyle(color = color))
    b.pushStringAnnotation(
        tag = "UVARSEQ",
        annotation = prefixHere.joinToString("\u0001")
    )
    b.append(tok)
    b.pop()
    b.pop()

    val kids = orderedUserVarChildren(node)
    if (kids.isEmpty()) return

    val main = kids.first()

    // FIRST continue the parent/main line
    b.append(" ")
    appendUserVarNodeAnnotated(
        b = b,
        boardBefore = after,
        node = main,
        prefixBefore = prefixHere,
        color = color,
        forceMoveNumber = false
    )

    // THEN show sibling alternatives from this exact node
    for (alt in kids.drop(1)) {
        b.append(" (")
        appendUserVarNodeAnnotated(
            b = b,
            boardBefore = after,
            node = alt,
            prefixBefore = prefixHere,
            color = color,
            forceMoveNumber = true
        )
        b.append(")")
    }
}

private fun buildUserVarTreeAnnotatedString(
    startFen: String,
    lines: List<List<String>>,
    color: Color
): AnnotatedString {
    val b = AnnotatedString.Builder()
    val forest = buildUserVarForest(lines).sortedWith(
        compareByDescending<UserVarTreeNode> { userVarTreeMaxDepth(it) }
            .thenBy { it.uci }
    )

    forest.forEachIndexed { idx, root ->
        if (idx > 0) b.append("\n")

        val baseBoard = LibBoard().apply {
            runCatching { loadFromFen(startFen) }.onFailure { loadFromFen(START_FEN) }
        }

        b.pushStyle(SpanStyle(color = color))
        b.append("(")
        b.pop()

        appendUserVarNodeAnnotated(
            b = b,
            boardBefore = baseBoard,
            node = root,
            prefixBefore = emptyList(),
            color = color,
            forceMoveNumber = true
        )

        b.pushStyle(SpanStyle(color = color))
        b.append(")")
        b.pop()
    }

    return b.toAnnotatedString()
}

private fun buildMovesAnnotatedString(
    text: String,
    annotationColor: Color,
    pvByStartPly: Map<Int, Map<String, BeatPvData>> = emptyMap()
): AnnotatedString {
    // Color conventions:
    // - PGN comments in braces { ... } are green (annotationColor)
    // - Moves marked with NAG suffix are colored:
    //      ??  -> blunder (red)
    //      ?   -> mistake (orange)
    //      ?!  -> dubious (light orange)
    val blunderColor = Color(0xFFFF5252)
    val mistakeColor = Color(0xFFFFA726)
    val dubiousColor = Color(0xFF8B6F47)
    val bestMoveColor = Color(0xFFFFFFFF) // best move token text stays white; star rendered separately in Moves list

    val b = AnnotatedString.Builder()
    var plyCount = 0
    var i = 0
    var inComment = false
    var startedMoves = false

    val moveNoRe = Regex("""^\d+\.{1,3}$""") // 1. or 1...
    val resultRe = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")
    val hasMoveGlyphRe = Regex("""[a-hKQRBN0O]""")

    fun nagColor(token: String): Color? {
        return when {
            token.endsWith("??") -> blunderColor
            token.endsWith("?!") -> dubiousColor
            // Single '?' but not '?!' and not '??'
            token.endsWith("?") -> mistakeColor
            else -> null
        }
    }

    while (i < text.length) {
        val ch = text[i]

        if (ch == '{') {
            // Consume until matching '}' (or end)
            val start = i
            var j = i + 1
            while (j < text.length && text[j] != '}') j++
            if (j < text.length) j++ // include '}'

            val chunk = text.substring(start, j) // includes {...}
            val startPlyForPv = (plyCount - 1).coerceAtLeast(0)
            val pvGroups = pvByStartPly[startPlyForPv]

            b.pushStyle(SpanStyle(color = annotationColor))

            // Comments can contain multiple move-lines (Best, Book main line, Line played, etc.).
            // Each label uses its own PV list (if available) so tokens become clickable.
            val inner = chunk.removePrefix("{").removeSuffix("}")
            b.append('{')

            data class Marker(val label: String, val kind: String)
            val markers = listOf(
                Marker("Best:", "BEST"),
                Marker("Book main line:", "BOOK"),
                Marker("Line played:", "LINE"),
                Marker("Your line:", "LINE"),
                Marker("Custom:", "LINE")
            )

            fun appendClickableSegment(seg: String, pvKind: String) {
                val pvData = pvGroups?.get(pvKind)
                if (pvData == null) {
                    b.append(seg)
                    return
                }

                var pvPly = 0
                var k = 0
                while (k < seg.length) {
                    val cc = seg[k]
                    if (cc.isWhitespace()) {
                        b.append(cc)
                        k++
                        continue
                    }

                    val tkStart = k
                    var tkEnd = k
                    while (tkEnd < seg.length && !seg[tkEnd].isWhitespace()) tkEnd++
                    val tok = seg.substring(tkStart, tkEnd)

                    val isPvMoveToken =
                        tok.isNotBlank() &&
                                !moveNoRe.matches(tok) &&
                                !resultRe.matches(tok) &&
                                hasMoveGlyphRe.containsMatchIn(tok)

                    if (isPvMoveToken) pvPly += 1

                    if (isPvMoveToken && pvPly <= pvData.uciMoves.size) {
                        b.pushStringAnnotation(tag = "PV", annotation = "${startPlyForPv}:${pvKind}:${pvPly}")
                        b.append(tok)
                        b.pop()
                    } else {
                        b.append(tok)
                    }

                    k = tkEnd
                }
            }

            var pos = 0
            while (pos < inner.length) {
                var nextIdx = -1
                var nextMarker: Marker? = null
                for (m in markers) {
                    val idx = inner.indexOf(m.label, startIndex = pos)
                    if (idx >= 0 && (nextIdx == -1 || idx < nextIdx)) {
                        nextIdx = idx
                        nextMarker = m
                    }
                }

                if (nextIdx < 0 || nextMarker == null) {
                    b.append(inner.substring(pos))
                    break
                }

                // Append text before marker
                if (nextIdx > pos) {
                    b.append(inner.substring(pos, nextIdx))
                }

                // Append marker label itself
                val labelEnd = nextIdx + nextMarker.label.length
                b.append(nextMarker.label)

                // Segment after marker until next marker/end
                var segEnd = inner.length
                for (m in markers) {
                    val idx2 = inner.indexOf(m.label, startIndex = labelEnd)
                    if (idx2 >= 0 && idx2 < segEnd) segEnd = idx2
                }

                val seg = inner.substring(labelEnd, segEnd)
                appendClickableSegment(seg, nextMarker.kind)

                pos = segEnd
            }

            b.append('}')
            b.pop()
            i = j
            continue
        }

        if (ch.isWhitespace()) {
            b.append(ch)
            i++
            continue
        }

        // token (outside comments)
        val start = i
        var j = i
        while (j < text.length && !text[j].isWhitespace() && text[j] != '{') j++
        var token = text.substring(start, j)

        // Best-move marker: we prefix the SAN token with '§' during annotation build,
        // and strip it here while rendering the move in pink.
        val isBest = token.startsWith("§")
        if (isBest) token = token.removePrefix("§")

        val nagC = if (!inComment) nagColor(token) else null
        val c = when {
            nagC != null -> nagC
            isBest -> bestMoveColor
            else -> null
        }

        // Mark the moment movetext begins (first move number like "1.").
        if (!startedMoves && moveNoRe.matches(token)) startedMoves = true

        // Clickable ply navigation:
        // We count each SAN token as a ply AFTER movetext begins.
        val isMoveToken = startedMoves &&
                !inComment &&
                token.isNotBlank() &&
                !moveNoRe.matches(token) &&
                !resultRe.matches(token) &&
                hasMoveGlyphRe.containsMatchIn(token)

        if (isMoveToken) plyCount += 1

        if (isMoveToken) b.pushStringAnnotation(tag = "PLY", annotation = plyCount.toString())

        if (c != null) {
            b.pushStyle(SpanStyle(color = c))
            b.append(token)
            b.pop()
        } else {
            b.append(token)
        }

        if (isMoveToken) b.pop()
        i = j
    }

    return b.toAnnotatedString()
}

private fun fenAfterUciPlies(startFen: String, uciMoves: List<String>, plies: Int): String? {
    val b = LibBoard().apply {
        runCatching { loadFromFen(startFen) }.onFailure { loadFromFen(START_FEN) }
    }
    val n = plies.coerceIn(0, uciMoves.size)
    for (i in 0 until n) {
        val mm = bfUciToMoveOnBoard(b, uciMoves[i]) ?: return null
        b.doMove(mm)
    }
    return b.fen
}



// =================== Core helpers ===================

internal fun bfPrettySan(board: LibBoard, mv: LibMove, isWhiteMove: Boolean): String {
    val from = mv.from
    val to = mv.to
    val piece = board.getPiece(from)

    // NOTE: We must emit *valid* SAN so external PGN readers can parse it.
    // The previous implementation did not disambiguate ambiguous piece moves
    // (e.g. Nd7 when both knights can go to d7). This version adds the standard
    // SAN disambiguation (file/rank/both) using legal-move generation.

    // 1) Castling
    if (piece.pieceType == com.github.bhlangonijr.chesslib.PieceType.KING) {
        if ((from == Square.E1 && to == Square.G1) || (from == Square.E8 && to == Square.G8)) return "O-O"
        if ((from == Square.E1 && to == Square.C1) || (from == Square.E8 && to == Square.C8)) return "O-O-O"
    }

    fun sqFile(sq: Square): Char = sq.toString().lowercase(Locale.US)[0]
    fun sqRank(sq: Square): Char = sq.toString().lowercase(Locale.US)[1]

    val pieceChar = when (piece.pieceType) {
        com.github.bhlangonijr.chesslib.PieceType.KING -> "K"
        com.github.bhlangonijr.chesslib.PieceType.QUEEN -> "Q"
        com.github.bhlangonijr.chesslib.PieceType.ROOK -> "R"
        com.github.bhlangonijr.chesslib.PieceType.BISHOP -> "B"
        com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> "N"
        com.github.bhlangonijr.chesslib.PieceType.PAWN -> ""
        else -> ""
    }

    // 2) Capture detection (includes en-passant)
    val targetPiece = board.getPiece(to)
    val isPawn = piece.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN
    val isEnPassantLike =
        isPawn &&
                targetPiece == com.github.bhlangonijr.chesslib.Piece.NONE &&
                sqFile(from) != sqFile(to)

    val isCapture = targetPiece != com.github.bhlangonijr.chesslib.Piece.NONE || isEnPassantLike
    val captureSymbol = if (isCapture) "x" else ""

    // Pawn capture prefix is the origin file letter
    val pawnPrefix =
        if (isPawn && isCapture) sqFile(from).toString() else ""

    // 3) Disambiguation (for pieces other than pawns and kings)
    val disambig = if (!isPawn && piece.pieceType != com.github.bhlangonijr.chesslib.PieceType.KING) {
        val fromFile = sqFile(from)
        val fromRank = sqRank(from)

        val legal = MoveGenerator.generateLegalMoves(board)
        val ambiguousFromSquares = legal.asSequence()
            .filter { it.to == to && it != mv }
            .filter {
                val p = board.getPiece(it.from)
                p != com.github.bhlangonijr.chesslib.Piece.NONE &&
                        p.pieceType == piece.pieceType &&
                        p.pieceSide == piece.pieceSide
            }
            .map { it.from }
            .toList()

        if (ambiguousFromSquares.isNotEmpty()) {
            val sameFileExists = ambiguousFromSquares.any { sqFile(it) == fromFile }
            val sameRankExists = ambiguousFromSquares.any { sqRank(it) == fromRank }

            when {
                !sameFileExists -> fromFile.toString()
                !sameRankExists -> fromRank.toString()
                else -> "$fromFile$fromRank"
            }
        } else ""
    } else ""

    // 4) Promotion
    val promo = mv.promotion?.pieceType
    val promoSuffix = when (promo) {
        com.github.bhlangonijr.chesslib.PieceType.QUEEN -> "=Q"
        com.github.bhlangonijr.chesslib.PieceType.ROOK -> "=R"
        com.github.bhlangonijr.chesslib.PieceType.BISHOP -> "=B"
        com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> "=N"
        else -> ""
    }

    // 5) Check / mate suffix
    val clone = LibBoard().apply { loadFromFen(board.fen) }
    clone.doMove(mv)
    val suffix = when {
        clone.isMated -> "#"
        clone.isKingAttacked -> "+"
        else -> ""
    }

    val toSq = to.toString().lowercase(Locale.US)

    // SAN:
    // pawn: e4 / exd5
    // piece: Nf3 / Nbd7 / R1e1 / Qxe4+
    return pawnPrefix + pieceChar + disambig + captureSymbol + toSq + promoSuffix + suffix
}


private fun bfIdxToUci(idx: Int, whiteBottom: Boolean): String {
    val base = viewIdxToBoardIdx(idx, whiteBottom)

    val file = "abcdefgh"[base % 8]
    val rank = '1' + (base / 8)
    return "$file$rank"
}

private fun buildFenFromEditable(
    editable: List<Piece?>,
    whiteToMove: Boolean,
    baseCastling: String
): String {
    val sb = StringBuilder()
    for (rank in 7 downTo 0) {
        var empty = 0
        for (file in 0..7) {
            val idx = rank * 8 + file
            val p = editable[idx]
            if (p == null) {
                empty++
            } else {
                if (empty > 0) {
                    sb.append(empty)
                    empty = 0
                }
                sb.append(
                    when (p.type) {
                        PieceType.KING -> if (p.isWhite) 'K' else 'k'
                        PieceType.QUEEN -> if (p.isWhite) 'Q' else 'q'
                        PieceType.ROOK -> if (p.isWhite) 'R' else 'r'
                        PieceType.BISHOP -> if (p.isWhite) 'B' else 'b'
                        PieceType.KNIGHT -> if (p.isWhite) 'N' else 'n'
                        PieceType.PAWN -> if (p.isWhite) 'P' else 'p'
                    }
                )
            }
        }
        if (empty > 0) sb.append(empty)
        if (rank > 0) sb.append('/')
    }

    val side = if (whiteToMove) "w" else "b"

    var castling = baseCastling
    if (castling != "-" && castling.isNotEmpty()) {
        fun hasPiece(type: PieceType, isWhite: Boolean, idx: Int): Boolean =
            editable.getOrNull(idx)?.let { it.type == type && it.isWhite == isWhite } == true

        val whiteKingOnE1 = hasPiece(PieceType.KING, true, 4)
        val whiteRookA1 = hasPiece(PieceType.ROOK, true, 0)
        val whiteRookH1 = hasPiece(PieceType.ROOK, true, 7)

        val blackKingOnE8 = hasPiece(PieceType.KING, false, 60)
        val blackRookA8 = hasPiece(PieceType.ROOK, false, 56)
        val blackRookH8 = hasPiece(PieceType.ROOK, false, 63)

        if (!whiteKingOnE1) {
            castling = castling.replace("K", "").replace("Q", "")
        } else {
            if (!whiteRookH1) castling = castling.replace("K", "")
            if (!whiteRookA1) castling = castling.replace("Q", "")
        }

        if (!blackKingOnE8) {
            castling = castling.replace("k", "").replace("q", "")
        } else {
            if (!blackRookH8) castling = castling.replace("k", "")
            if (!blackRookA8) castling = castling.replace("q", "")
        }

        if (castling.isEmpty()) castling = "-"
    }

    return "$sb $side $castling - 0 1"
}

internal fun bfUciToMoveOnBoard(board: LibBoard, uci: String): LibMove? {
    if (uci.length < 4) return null

    val fromSq = Square.fromValue(uci.substring(0, 2).uppercase())
    val toSq = Square.fromValue(uci.substring(2, 4).uppercase())

    val promoType: LibPieceType? = if (uci.length >= 5) {
        when (uci[4].uppercaseChar()) {
            'Q' -> LibPieceType.QUEEN
            'R' -> LibPieceType.ROOK
            'B' -> LibPieceType.BISHOP
            'N' -> LibPieceType.KNIGHT
            else -> null
        }
    } else null

    val legal = MoveGenerator.generateLegalMoves(board)
    return legal.firstOrNull { mv ->
        mv.from == fromSq && mv.to == toSq && (promoType == null || mv.promotion?.pieceType == promoType)
    }
}

private fun beatFishValidatePosition(board: List<Piece?>): String? {
    var whiteKing = -1
    var blackKing = -1

    for (i in 0 until 64) {
        val p = board[i] ?: continue
        when (p.type) {
            PieceType.KING -> {
                if (p.isWhite) {
                    if (whiteKing != -1) return "White has more than one king."
                    whiteKing = i
                } else {
                    if (blackKing != -1) return "Black has more than one king."
                    blackKing = i
                }
            }
            PieceType.PAWN -> {
                val r = rankOf(i)
                if (r == 0 || r == 7) return "Pawns cannot be on rank 1 or 8."
            }
            else -> {}
        }
    }

    if (whiteKing == -1 || blackKing == -1) return "Both sides must have exactly one king."

    val df = kotlin.math.abs(fileOf(whiteKing) - fileOf(blackKing))
    val dr = kotlin.math.abs(rankOf(whiteKing) - rankOf(blackKing))
    if (df <= 1 && dr <= 1) return "Kings cannot be adjacent."

    return null
}

// ====== PGN saving + loading helpers ======

private const val BEATFISH_RESUME_FILE = "beat_the_fish_resume.pgn"
private const val LEGACY_BEATFISH_RESUME_FILE = "beatfish_resume.pgn"

private fun saveBeatFishResumePgn(
    ctx: Context,
    startFen: String,
    moves: List<BFMove>,
    playAsBlack: Boolean,
    playLevel: Int,
    thinkSec: Int
): Boolean {
    return try {
        val base = buildBeatFishPgn(startFen, moves)

        // Add extra tags (harmless for other PGN readers; our parser ignores unknown tags)
        val stamp = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).format(Date())
        val tags =
            """[TrainerFishResume "1"]
[TrainerFishPlayAsBlack "${if (playAsBlack) 1 else 0}"]
[TrainerFishLevel "$playLevel"]
[TrainerFishThinkSec "$thinkSec"]
[TrainerFishSavedAt "$stamp"]
"""

        // Insert tags after the FEN header area (simple + safe: inject after the [FEN "..."] line if present)
        val injected = run {
            val fenIdx = base.indexOf("[FEN \"")
            if (fenIdx >= 0) {
                val eol = base.indexOf('\n', startIndex = fenIdx)
                if (eol >= 0) base.substring(0, eol + 1) + tags + base.substring(eol + 1)
                else base + "\n" + tags
            } else {
                tags + base
            }
        }

        val docsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return false
        if (!docsDir.exists()) docsDir.mkdirs()
        val file = File(docsDir, BEATFISH_RESUME_FILE)
        file.writeText(injected.trim() + "\n\n")
        true
    } catch (_: Throwable) {
        false
    }
}

private fun buildBeatFishPgn(startFen: String, moves: List<BFMove>): String {
    val sb = StringBuilder()
    val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
    val uciSeq = moves.joinToString(" ") { it.uci }

    sb.appendLine("""[Event "Beat the Fish"]""")
    sb.appendLine("""[Site "?"]""")
    sb.appendLine("""[Date "$dateStr"]""")
    sb.appendLine("""[Round "-"]""")
    sb.appendLine("""[White "Player"]""")
    sb.appendLine("""[Black "TrainerFish"]""")
    sb.appendLine("""[Result "*"]""")
    sb.appendLine("""[SetUp "1"]""")
    sb.appendLine("""[FEN "$startFen"]""")
    sb.appendLine("""[TrainerFishUCI "$uciSeq"]""")
    sb.appendLine()

    val movesSb = StringBuilder()
    for (m in moves) {
        if (m.isWhite) {
            if (movesSb.isNotEmpty()) movesSb.append(' ')
            movesSb.append("${m.moveNumber}. ${m.san}")
        } else {
            movesSb.append(' ')
            movesSb.append(m.san)
        }
    }

    if (movesSb.isNotEmpty()) {
        sb.append(movesSb.toString())
        sb.append(" *")
    } else {
        sb.append("*")
    }

    return sb.toString()
}

private fun saveBeatFishPgn(ctx: Context, startFen: String, moves: List<BFMove>): Boolean {
    return try {
        val pgnText = buildBeatFishPgn(startFen, moves)
        val docsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return false
        if (!docsDir.exists()) docsDir.mkdirs()
        val file = File(docsDir, "beat_the_fish.pgn")
        file.appendText(pgnText + "\n\n")
        true
    } catch (_: Throwable) {
        false
    }
}



// --- Unfinished game (resume) save/load ---
// Manual Save, autosave, and Load last played all use the same metadata-rich file.
private fun clearBeatFishResumeGame(ctx: Context) {
    runCatching {
        val docsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        listOf(BEATFISH_RESUME_FILE, LEGACY_BEATFISH_RESUME_FILE).forEach { name ->
            val file = File(docsDir, name)
            if (file.exists()) file.delete()
        }
    }
}

private fun loadBeatFishResumeGame(ctx: Context): LoadedGameData? {
    val docsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
    val candidates = listOf(BEATFISH_RESUME_FILE, LEGACY_BEATFISH_RESUME_FILE)
        .map { File(docsDir, it) }
        .filter { it.exists() }
        .sortedByDescending { it.lastModified() }

    for (file in candidates) {
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) continue

        // Each resume file contains one overwritten game. Parse it whole: the
        // normal blank line between PGN headers and movetext must not separate
        // FEN/metadata from moves. Checking both names preserves existing saves.
        parseBeatFishChunk(text.trim())?.let { return it }
    }
    return null
}



private fun parseSanMoves(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return text.split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filter { token ->
            !token.matches(Regex("""\d+\.{1,3}""")) &&
                    token !in setOf("1-0", "0-1", "1/2-1/2", "*")
        }
}

private fun bfmoveToUci(mv: LibMove): String {
    val from = mv.from.toString().lowercase()
    val to = mv.to.toString().lowercase()
    val promo = mv.promotion?.pieceType
    val promoChar = when (promo) {
        com.github.bhlangonijr.chesslib.PieceType.QUEEN -> "q"
        com.github.bhlangonijr.chesslib.PieceType.ROOK -> "r"
        com.github.bhlangonijr.chesslib.PieceType.BISHOP -> "b"
        com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> "n"
        else -> ""
    }
    return from + to + promoChar
}

private fun loadLastBeatFishGame(ctx: Context): LoadedGameData? {
    val docsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
    val file = File(docsDir, "beat_the_fish.pgn")
    if (!file.exists()) return null

    val text = file.readText()

    val chunks = splitMasterPgnIntoChunks(text)

    if (chunks.isEmpty()) return null

    for (i in chunks.indices.reversed()) {
        val parsed = parseBeatFishChunk(chunks[i])
        if (parsed != null) return parsed
    }
    return null
}

private fun parseBeatFishChunk(chunk: String): LoadedGameData? {
    val lines = chunk.lines()

    var startFen: String? = null
    var uciHeader: String? = null
    var savedPlayAsBlack: Boolean? = null
    var savedPlayLevel: Int? = null
    var savedThinkSec: Int? = null
    val moveLines = mutableListOf<String>()

    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue

        if (line.startsWith("[")) {
            val fenMatch = Regex("""\[FEN\s+"([^"]+)"""").find(line)
            if (fenMatch != null) startFen = fenMatch.groupValues[1]

            val uciMatch = Regex("""\[TrainerFishUCI\s+"([^"]*)"""").find(line)
            if (uciMatch != null) uciHeader = uciMatch.groupValues[1]

            Regex("""\[TrainerFishPlayAsBlack\s+"([01])"""")
                .find(line)?.groupValues?.getOrNull(1)?.let {
                    savedPlayAsBlack = it == "1"
                }
            Regex("""\[TrainerFishLevel\s+"(\d+)"""")
                .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                    savedPlayLevel = it.coerceIn(1, 3)
                }
            Regex("""\[TrainerFishThinkSec\s+"(\d+)"""")
                .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                    savedThinkSec = it.coerceIn(2, 60)
                }
        } else {
            moveLines += line
        }
    }

    val fen = startFen ?: START_FEN
    val sanText = moveLines.joinToString(" ").trim()
    val uciList = uciHeader?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))

    val board = LibBoard().apply { loadFromFen(fen) }
    val positions = mutableListOf<String>()
    positions += board.fen
    val bfMoves = mutableListOf<BFMove>()

    val fenParts = fen.split(" ")
    var moveNumber = fenParts.getOrNull(5)?.toIntOrNull() ?: 1

    if (uciList != null && uciList.isNotEmpty()) {
        for (uci in uciList) {
            val mv = bfUciToMoveOnBoard(board, uci) ?: break
            val isWhite = board.sideToMove == Side.WHITE
            val san = bfPrettySan(board, mv, isWhiteMove = isWhite)

            bfMoves += BFMove(moveNumber, uci, san, isWhite = isWhite)
            board.doMove(mv)

            positions += board.fen
            if (!isWhite) moveNumber++
        }
    } else {
        val sanTokens = parseSanMoves(sanText)
        for (san in sanTokens) {
            val isWhite = board.sideToMove == Side.WHITE
            val legal = MoveGenerator.generateLegalMoves(board)
            val mv = legal.firstOrNull { cand -> bfPrettySan(board, cand, isWhiteMove = isWhite) == san } ?: break
            val uci = bfmoveToUci(mv)

            bfMoves += BFMove(moveNumber, uci, san, isWhite = isWhite)
            board.doMove(mv)

            positions += board.fen
            if (!isWhite) moveNumber++
        }
    }

    if (bfMoves.isEmpty() && positions.size <= 1) return null

    val rawMoveText = extractRawMoveTextFromPgnChunk(chunk)

    return LoadedGameData(
        startFen = fen,
        positions = positions,
        moves = bfMoves,
        rawMoveText = rawMoveText,
        playAsBlack = savedPlayAsBlack,
        playLevel = savedPlayLevel,
        thinkSec = savedThinkSec
    )
}


// ---------------- Eval line graph (Analyze mode) ----------------

private fun clampEvalPawnsWhite(cp: Int, maxAbs: Float = 2f): Float {
    val v = cp / 100f
    return v.coerceIn(-maxAbs, maxAbs)
}

private fun evalToNormY(cp: Int, maxAbs: Float = 2f): Float {
    val v = clampEvalPawnsWhite(cp, maxAbs)
    return (maxAbs - v) / (2f * maxAbs)
}

private fun evalBandColor(cp: Int): Color {
    val absCp = kotlin.math.abs(cp)
    return when {
        absCp < 50  -> Color(0xFF4CAF50) // green
        absCp < 100 -> Color(0xFFFFC107) // yellow
        absCp < 150 -> Color(0xFFFF9800) // orange
        else        -> Color(0xFFF44336) // red
    }
}

private fun guideLineColor(cp: Int): Color {
    return when (kotlin.math.abs(cp)) {
        0   -> Color.White.copy(alpha = 0.40f)
        50  -> Color(0xFFFFC107).copy(alpha = 0.35f)
        100 -> Color(0xFFFF9800).copy(alpha = 0.35f)
        150, 200 -> Color(0xFFF44336).copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.10f)
    }
}

@Composable
private fun EvalLineGraph(
    evals: List<Int>,
    modifier: Modifier = Modifier,
    maxAbsPawns: Float = 2f,
    currentIndex: Int? = null
) {
    if (evals.size < 2) return

    Canvas(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x11000000))
            .padding(8.dp)
    ) {
        val w = size.width
        val h = size.height
        val lineThin = 1.dp.toPx()
        val strokePx = 2.5.dp.toPx()
        val pointerRadius = 4.dp.toPx()

        fun xFor(i: Int): Float =
            if (evals.size <= 1) 0f else (i.toFloat() / evals.lastIndex.toFloat()) * w

        fun yFor(cp: Int): Float = evalToNormY(cp, maxAbsPawns) * h

        // Guides synced to the same scale as the graph
        val guideCps = listOf(-200, -150, -100, -50, 0, 50, 100, 150, 200)
        for (cp in guideCps) {
            val y = yFor(cp)
            drawLine(
                color = guideLineColor(cp),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (cp == 0) lineThin * 1.4f else lineThin
            )
        }

        val pts = evals.mapIndexed { i, cp -> Offset(xFor(i), yFor(cp)) }

        // Fill the ENTIRE region below the line, not only half.
        for (i in 0 until pts.lastIndex) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val segColor = evalBandColor(evals[i + 1])

            val fillPath = Path().apply {
                moveTo(p1.x, h)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p2.x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        segColor.copy(alpha = 0.26f),
                        segColor.copy(alpha = 0.10f)
                    ),
                    startY = kotlin.math.min(p1.y, p2.y),
                    endY = h
                )
            )
        }

        // Draw line segments on top, same band color as fill
        for (i in 0 until pts.lastIndex) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val segColor = evalBandColor(evals[i + 1])

            drawLine(
                color = segColor,
                start = p1,
                end = p2,
                strokeWidth = strokePx
            )
        }

        // Pointer
        val idx = currentIndex?.coerceIn(0, evals.lastIndex)
        if (idx != null) {
            val pt = pts[idx]
            val pointColor = evalBandColor(evals[idx])

            drawCircle(
                color = Color.Black.copy(alpha = 0.30f),
                radius = pointerRadius + 2.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = pointColor,
                radius = pointerRadius,
                center = pt
            )
        }
    }
}

private fun bfUciSquareToIdx(sq: String, whiteBottom: Boolean): Int {
    if (sq.length < 2) return -1
    val files = "abcdefgh"
    val file = files.indexOf(sq[0])
    val rank = (sq[1] - '1').coerceIn(0, 7)
    if (file !in 0..7 || rank !in 0..7) return -1
    val base = rank * 8 + file
    return if (whiteBottom) base else 63 - base
}
private data class MasterPgnGameEntry(
    val idx: Int,
    val white: String,
    val black: String,
    val date: String,
    val result: String,
    val event: String,
    val startFen: String?,
    val moveTextPreview: String,
    val chunk: String
)

private data class LoadedGameData(
    val startFen: String,
    val positions: List<String>,
    val moves: List<BFMove>,
    // Raw movetext (including comments and variations) as read from the PGN chunk.
    val rawMoveText: String,
    val playAsBlack: Boolean? = null,
    val playLevel: Int? = null,
    val thinkSec: Int? = null
)


private fun readTextFromUri(context: Context, uri: Uri): String {
    context.contentResolver.openInputStream(uri)?.use { ins ->
        return ins.bufferedReader(Charsets.UTF_8).readText()
    }
    error("Unable to open PGN")
}

private val MASTER_EVENT_RE = Regex("""(?mi)^\s*\[Event\s+""")
private val MASTER_ANYTAG_RE = Regex("""(?mi)^\s*\[""")

private fun splitMasterPgnIntoChunks(raw: String): List<String> {
    val s = raw.trim().replace("\r\n", "\n")
    if (s.isEmpty()) return emptyList()

    // Prefer splitting by [Event ...] tag (most PGNs have it).
    val starts = MASTER_EVENT_RE.findAll(s).map { it.range.first }.toList().toMutableList()
    if (starts.isEmpty()) {
        val any = MASTER_ANYTAG_RE.findAll(s).map { it.range.first }.toList()
        if (any.isNotEmpty()) starts.addAll(any) else starts.add(0)
    }
    starts.sort()
    val chunks = ArrayList<String>(starts.size)
    for (i in starts.indices) {
        val a = starts[i]
        val b = if (i + 1 < starts.size) starts[i + 1] else s.length
        val chunk = s.substring(a, b).trim()
        if (chunk.isNotBlank()) chunks.add(chunk + "\n")
    }
    return chunks
}

private fun extractMovetextFromPgn(chunk: String): String {
    val parts = chunk.replace("\r\n", "\n").split("\n\n", limit = 2)
    return if (parts.size == 2) parts[1].trim() else ""
}

private fun parseMasterPgnEntry(idx: Int, chunk: String): MasterPgnGameEntry? {
    fun tag(name: String): String? {
        val re = Regex("""(?mi)^\s*\[$name\s+"([^"]*)"]\s*$""")
        return re.find(chunk)?.groupValues?.getOrNull(1)
    }

    val white = tag("White") ?: "White"
    val black = tag("Black") ?: "Black"
    val date = tag("Date") ?: ""
    val result = tag("Result") ?: "*"
    val event = tag("Event") ?: ""
    val startFen = tag("FEN")

    val moveText = extractMovetextFromPgn(chunk)
        .replace(Regex("""\s+"""), " ")
        .trim()

    val preview = moveText.take(120)

    return MasterPgnGameEntry(
        idx = idx,
        white = white,
        black = black,
        date = date,
        result = result,
        event = event,
        startFen = startFen,
        moveTextPreview = preview,
        chunk = chunk
    )
}

private fun ensureResultToken(moves: String): String {
    val s = moves.trim()
    if (s.isEmpty()) return "*"
    val ended = Regex("""(1-0|0-1|1/2-1/2|\*)\s*$""")
    return if (ended.containsMatchIn(s)) s else "$s *"
}

private fun loadGameDataFromPgnChunk(context: Context, chunk: String): LoadedGameData? {
    return runCatching {
        val norm = chunk.replace("\r\n", "\n")
        val parts = norm.split("\n\n", limit = 2)
        val headers = parts.getOrNull(0) ?: ""
        val moveTextRaw = parts.getOrNull(1) ?: ""
        // Keep comments/variations; only ensure there is a result token at the end.
        val movesTextForParse = ensureResultToken(moveTextRaw)
        val sanitized = headers + "\n\n" + movesTextForParse + "\n"

        val tmp = File.createTempFile("master_game_", ".pgn", context.cacheDir)
        tmp.writeText(sanitized, Charsets.UTF_8)

        val holder = PgnHolder(tmp.absolutePath)
        holder.loadPgn()
        val game: Game = holder.games.firstOrNull() ?: return null

        // Parse FEN tag from headers (don't rely on chesslib tag API variants)
        val fenTag = Regex("""(?mi)^\s*\[FEN\s+"([^"]+)"\]""")
            .find(headers)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        val startFen = if (!fenTag.isNullOrBlank()) fenTag else START_FEN

        val b = LibBoard().apply { loadFromFen(startFen) }
        val outMoves = ArrayList<BFMove>()
        val positions = ArrayList<String>()
        positions.add(b.fen)

        var ply = 0
        // Use the FEN move number as the starting move number if present; else 1.
        var moveNo = runCatching { startFen.trim().split(Regex("\\s+")).getOrNull(5)?.toIntOrNull() ?: 1 }.getOrDefault(1)
        for (hm in game.halfMoves) {
            val uci = hm.toString().lowercase(Locale.ROOT)

            // Determine side-to-move from the board, not from ply parity (handles black-to-move FENs).
            val isWhite = (b.sideToMove == Side.WHITE)

            val libMove = bfUciToMoveOnBoard(board = b, uci = uci) ?: break
            val san = bfPrettySan(board = b, mv = libMove, isWhiteMove = isWhite)
            outMoves.add(BFMove(moveNumber = moveNo, uci = uci, san = san, isWhite = isWhite))

            b.doMove(libMove)
            positions.add(b.fen)

            // Increment move number after black's move.
            if (!isWhite) moveNo++
            ply++
        }

        LoadedGameData(
            startFen = startFen,
            positions = positions,
            moves = outMoves,
            rawMoveText = movesTextForParse.trim()
        )
    }.getOrNull()
}

private fun extractRawMoveTextFromPgnChunk(chunk: String): String {
    // Keep everything after the PGN tag section (this preserves {comments} and (variations))
    val lines = chunk.lineSequence().toList()
    val sb = StringBuilder()
    var i = 0

    // Skip tag pairs [ ... ]
    while (i < lines.size) {
        val t = lines[i].trim()
        if (t.startsWith("[")) {
            i++
            continue
        }
        // Typically there is a blank line after tags
        if (t.isBlank()) {
            i++
            break
        }
        // First non-tag non-blank line => movetext begins
        break
    }

    // Join the remainder as movetext (keep braces/parentheses intact)
    while (i < lines.size) {
        val t = lines[i].trim()
        if (t.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(t)
        }
        i++
    }

    return sb.toString().trim()
}
