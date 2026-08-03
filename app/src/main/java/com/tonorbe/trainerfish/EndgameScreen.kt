package com.tonorbe.trainerfish

import android.R.attr.maxHeight
import android.R.attr.maxWidth
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.tonorbe.trainerfish.engine.ProcEngine
import com.tonorbe.trainerfish.pgn.PgnGameInfo
import com.tonorbe.trainerfish.pgn.PgnSession
import com.tonorbe.trainerfish.pgn.loadGamesByIndexes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.random.Random
import com.github.bhlangonijr.chesslib.Board as LibBoard
import com.github.bhlangonijr.chesslib.Piece as LibPiece
import com.github.bhlangonijr.chesslib.move.Move as LibMove
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

private const val EG_START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
private const val EG_EMPTY_FEN = "8/8/8/8/8/8/8/8 w - - 0 1"
private const val EG_FISH_THINK_MS = 3000
private const val EG_MOVE_ANIM_MS = 400

private enum class EgRoom {
    COURSE,
    TACTICS,
    STUDIES
}

private data class EgChoice(
    val uci: String,
    val label: String,
    val correct: Boolean
)

private data class EgMoveLineItem(
    val ply: Int,
    val moveNo: Int,
    val isWhite: Boolean,
    val label: String,
    val uci: String = ""
)

private fun egRoomTitle(room: EgRoom): String = when (room) {
    EgRoom.COURSE -> "Endgame Course"
    EgRoom.TACTICS -> "Endgame Tactics"
    EgRoom.STUDIES -> "Endgame Studies"
}

private fun egRoomShortTitle(room: EgRoom): String = when (room) {
    EgRoom.COURSE -> "Course"
    EgRoom.TACTICS -> "Tactics"
    EgRoom.STUDIES -> "Studies"
}

private fun egRoomCount(room: EgRoom): Int = when (room) {
    EgRoom.COURSE -> 125
    EgRoom.TACTICS -> 2000
    EgRoom.STUDIES -> 1234
}

private fun egRoomDescription(room: EgRoom): String = when (room) {
    EgRoom.COURSE -> "Play the 125 essential endgame positions with Trainer Fish as your sparring partner."
    EgRoom.TACTICS -> "Solve 2,000 endgame exercises by choosing the best first move from four randomized choices."
    EgRoom.STUDIES -> "Explore 1,234 endgame studies as first-move multiple-choice challenges, then review with Fish."
}

private fun egRawRes(room: EgRoom): Int = when (room) {
    EgRoom.COURSE -> R.raw.endgames
    EgRoom.TACTICS -> R.raw.endgame_tactics
    EgRoom.STUDIES -> R.raw.endgame_studies
}

private fun egIsMultipleChoice(room: EgRoom): Boolean = room != EgRoom.COURSE

private fun egUciSquareToIdx(sq: String, whiteBottom: Boolean): Int {
    if (sq.length < 2) return -1
    val file = sq[0].lowercaseChar() - 'a'
    val rank = sq[1] - '1'
    if (file !in 0..7 || rank !in 0..7) return -1
    val logical = rank * 8 + file
    return if (whiteBottom) logical else 63 - logical
}

private fun egViewIdxToBoardIdx(viewIdx: Int, whiteBottom: Boolean): Int =
    if (whiteBottom) viewIdx else 63 - viewIdx

private fun egBoardIdxToUciSquare(idx: Int): String {
    val file = (idx and 7).coerceIn(0, 7)
    val rank = ((idx ushr 3) + 1).coerceIn(1, 8)
    return "${('a'.code + file).toChar()}$rank"
}

private fun egSideName(side: Side): String =
    if (side == Side.WHITE) "White" else "Black"

private fun egMoveToDisplaySquares(uci: String, whiteBottom: Boolean): Pair<Int?, Int?> {
    if (uci.length < 4) return null to null
    val from = egUciSquareToIdx(uci.substring(0, 2), whiteBottom).takeIf { it >= 0 }
    val to = egUciSquareToIdx(uci.substring(2, 4), whiteBottom).takeIf { it >= 0 }
    return from to to
}

private fun egSquareFromUci(s: String): Square? = runCatching {
    Square.valueOf(s.uppercase())
}.getOrNull()

private fun egLegalMoveForUciPrefix(
    board: LibBoard,
    prefix: String
): Pair<LibMove, String>? {
    val wanted = prefix.lowercase()
    val legal = runCatching { MoveGenerator.generateLegalMoves(board).toList() }.getOrNull() ?: return null
    for (mv in legal) {
        val u = mv.toString().lowercase()
        if (u.take(4) == wanted.take(4)) return mv to u
    }
    return null
}

private fun egLegalMoveFromBestUci(
    board: LibBoard,
    uci: String?
): Pair<LibMove, String>? {
    val clean = uci?.trim()?.lowercase().orEmpty()
    if (clean.length < 4 || clean == "0000" || clean == "(none)") return null
    return egLegalMoveForUciPrefix(board, clean.take(4))
}

private fun egPieceLetter(piece: LibPiece): String {
    val name = piece.name.uppercase()
    return when {
        name.contains("KING") -> "K"
        name.contains("QUEEN") -> "Q"
        name.contains("ROOK") -> "R"
        name.contains("BISHOP") -> "B"
        name.contains("KNIGHT") -> "N"
        else -> ""
    }
}

private fun egMoveLabel(board: LibBoard, move: LibMove, uciRaw: String = move.toString()): String {
    val uci = uciRaw.lowercase()
    if (uci.length < 4) return uciRaw
    val from = egSquareFromUci(uci.substring(0, 2))
    val to = egSquareFromUci(uci.substring(2, 4))
    val dest = uci.substring(2, 4)
    val piece = if (from != null) runCatching { board.getPiece(from) }.getOrDefault(LibPiece.NONE) else LibPiece.NONE
    val capture = if (to != null) runCatching { board.getPiece(to) != LibPiece.NONE }.getOrDefault(false) else false
    val prefix = egPieceLetter(piece)
    val pawnFrom = if (prefix.isBlank() && capture && uci.isNotEmpty()) uci[0].toString() else ""
    val promo = if (uci.length >= 5) "=${uci[4].uppercaseChar()}" else ""
    return egSanToFanDisplay("$prefix$pawnFrom${if (capture) "x" else ""}$dest$promo")
}


private fun egSanToFanDisplay(raw: String): String {
    if (raw.isBlank()) return raw

    fun convertToken(tokenRaw: String): String {
        if (tokenRaw.isBlank()) return tokenRaw

        val leading = tokenRaw.takeWhile { it == '(' || it == '[' || it == '{' }
        val trailing = tokenRaw.takeLastWhile { it == ')' || it == ']' || it == '}' }
        var token = tokenRaw
        if (leading.isNotEmpty()) token = token.drop(leading.length)
        if (trailing.isNotEmpty()) token = token.dropLast(trailing.length)

        if (token.matches(Regex("""^\d+\.{1,3}$""")) ||
            token == "1-0" || token == "0-1" || token == "1/2-1/2" || token == "*" ||
            token == "O-O" || token == "O-O-O" || token == "..."
        ) {
            return leading + token + trailing
        }

        var out = token
        if (out.isNotEmpty()) {
            val piece = when (out[0]) {
                'K' -> "♔"
                'Q' -> "♕"
                'R' -> "♖"
                'B' -> "♗"
                'N' -> "♘"
                else -> null
            }
            if (piece != null) out = piece + out.drop(1)
        }

        out = out
            .replace("=K", "=♔")
            .replace("=Q", "=♕")
            .replace("=R", "=♖")
            .replace("=B", "=♗")
            .replace("=N", "=♘")

        return leading + out + trailing
    }

    return raw.split(Regex("""\s+""")).joinToString(" ") { convertToken(it) }
}

private fun egBestLabelForUci(board: LibBoard, uci: String?): String {
    val pair = egLegalMoveFromBestUci(board, uci) ?: return egSanToFanDisplay(uci.orEmpty().ifBlank { "..." })
    return egMoveLabel(board, pair.first, pair.second)
}

private fun egBuildChoices(session: PgnSession?): List<EgChoice> {
    val s = session ?: return emptyList()
    val expected = s.peekNextMoveUci()?.lowercase().orEmpty()
    if (expected.length < 4) return emptyList()
    val legal = runCatching { MoveGenerator.generateLegalMoves(s.board).toList() }.getOrDefault(emptyList())
    if (legal.isEmpty()) return emptyList()

    val correctPair = egLegalMoveForUciPrefix(s.board, expected) ?: return emptyList()
    val correctUci = correctPair.second
    val used = linkedSetOf(correctUci.take(4))
    val out = ArrayList<EgChoice>(4)
    out += EgChoice(
        uci = correctUci,
        label = egMoveLabel(s.board, correctPair.first, correctUci),
        correct = true
    )

    for (mv in legal.shuffled(Random(System.nanoTime()))) {
        val u = mv.toString().lowercase()
        if (u.length < 4) continue
        if (!used.add(u.take(4))) continue
        out += EgChoice(
            uci = u,
            label = egMoveLabel(s.board, mv, u),
            correct = false
        )
        if (out.size >= 4) break
    }

    return out.shuffled(Random(System.nanoTime()))
}


private fun egMoveLineItems(info: PgnGameInfo?): List<EgMoveLineItem> {
    val game = info?.game ?: return emptyList()
    val startFen = info.startFen?.trim()?.takeIf { it.isNotBlank() } ?: EG_START_FEN
    val board = LibBoard().apply {
        runCatching { loadFromFen(startFen) }.onFailure { loadFromFen(EG_START_FEN) }
    }
    val moves = game.halfMoves ?: emptyList()
    val out = ArrayList<EgMoveLineItem>(moves.size)

    for ((idx, mv) in moves.withIndex()) {
        val uci = mv.toString().lowercase()
        val moveNo = board.fen.split(' ').getOrNull(5)?.toIntOrNull() ?: ((idx / 2) + 1)
        val isWhite = board.sideToMove == Side.WHITE
        val label = egLegalMoveForUciPrefix(board, uci)?.let { egMoveLabel(board, it.first, it.second) }
            ?: uci.ifBlank { "..." }
        out += EgMoveLineItem(
            ply = idx + 1,
            moveNo = moveNo,
            isWhite = isWhite,
            label = label,
            uci = uci
        )
        runCatching { board.doMove(mv) }.onFailure { return out }
    }
    return out
}

private fun egMoveUciAtPly(info: PgnGameInfo?, ply: Int): String? =
    info?.game?.halfMoves?.getOrNull(ply - 1)?.toString()?.lowercase()

private val EG_TAG_RE = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+"([^"]*)"]\s*$""")
private val EG_EVENT_RE = Regex("""^\s*\[Event\s+""")
private val EG_RESULT_RE = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")

private fun egParseHeaders(chunk: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    EG_TAG_RE.findAll(chunk).forEach { m -> out[m.groupValues[1]] = m.groupValues[2] }
    return out
}

private fun egChunkAtIndex(context: Context, resId: Int, targetIndex: Int): String? {
    if (targetIndex < 0) return null
    val sb = StringBuilder()
    var inGame = false
    var gameIdx = -1
    var found: String? = null

    fun emitIfTarget(): String? =
        if (inGame && gameIdx == targetIndex && sb.isNotBlank()) sb.toString() else null

    context.resources.openRawResource(resId).bufferedReader(Charsets.UTF_8).useLines { lines ->
        for (line in lines) {
            if (EG_EVENT_RE.containsMatchIn(line)) {
                val ready = emitIfTarget()
                if (ready != null) {
                    found = ready
                    break
                }
                sb.clear()
                inGame = true
                gameIdx++
            }
            if (inGame) sb.append(line).append('\n')
        }
    }
    return found ?: emitIfTarget()
}

private fun egMoveTextFromChunk(chunk: String): String {
    val normalized = chunk.replace("\r\n", "\n")
    val split = normalized.split(Regex("""\n\s*\n"""), limit = 2)
    if (split.size == 2) return split[1]
    return normalized.lines()
        .filterNot { it.trimStart().startsWith("[") }
        .joinToString(" ")
}

private fun egCleanMoveToken(raw: String): String {
    var t = raw.trim()
    if (t.isBlank()) return ""
    t = t.replace("…", "...")
    t = t.replace(Regex("""^\d+\.\.\."""), "")
    t = t.replace(Regex("""^\d+\."""), "")
    t = t.trim()
    if (t.isBlank() || EG_RESULT_RE.matches(t) || t.startsWith("$")) return ""
    t = t.trimEnd('!', '?', '+', '#')
    return t
}

private fun egMoveTokensFromChunk(chunk: String, headers: Map<String, String>): List<String> {
    val text = egMoveTextFromChunk(chunk)
        .replace(Regex("""\{[^}]*}"""), " ")
        .replace(Regex(""";[^\n]*"""), " ")
        .replace("(", " ")
        .replace(")", " ")

    val tokens = text.split(Regex("""\s+"""))
        .map { egCleanMoveToken(it) }
        .filter { it.isNotBlank() }
        .filterNot { EG_RESULT_RE.matches(it) }

    if (tokens.isNotEmpty()) return tokens

    // Some EPD test-suite files store the best move in the Black tag.
    return listOf(headers["Black"].orEmpty())
        .map { egCleanMoveToken(it) }
        .filter { it.isNotBlank() }
}

private fun egSanCore(raw: String): String = raw
    .trim()
    .replace('0', 'O')
    .replace("x", "")
    .replace("-", "")
    .replace("+", "")
    .replace("#", "")
    .replace("e.p.", "", ignoreCase = true)
    .trimEnd('!', '?')

private fun egPromotionChar(raw: String): Char? =
    Regex("""=([QRBNqrbn])""").find(raw)?.groupValues?.getOrNull(1)?.firstOrNull()?.uppercaseChar()

private fun egLegalMoveForSanLike(board: LibBoard, rawToken: String): Pair<LibMove, String>? {
    val token = egCleanMoveToken(rawToken)
    if (token.isBlank()) return null

    val tokenLower = token.lowercase()
    val legal = runCatching { MoveGenerator.generateLegalMoves(board).toList() }.getOrNull() ?: return null

    legal.firstOrNull { mv -> mv.toString().lowercase().take(4) == tokenLower.take(4) }?.let { mv ->
        return mv to mv.toString().lowercase()
    }

    val san = egSanCore(token)
    if (san.equals("OO", true) || san.equals("OOO", true)) {
        val castleKingSide = san.length == 2
        legal.firstOrNull { mv ->
            val u = mv.toString().lowercase()
            if (castleKingSide) u in setOf("e1g1", "e8g8") else u in setOf("e1c1", "e8c8")
        }?.let { mv -> return mv to mv.toString().lowercase() }
    }

    legal.firstOrNull { mv ->
        val u = mv.toString().lowercase()
        egSanCore(egMoveLabel(board, mv, u)).equals(san, ignoreCase = true)
    }?.let { mv -> return mv to mv.toString().lowercase() }

    val dest = Regex("""([a-h][1-8])""").findAll(san).lastOrNull()?.groupValues?.getOrNull(1)?.lowercase()
        ?: return null
    val wantedPiece = san.firstOrNull()?.takeIf { it in "KQRBN" }?.toString().orEmpty()
    val promo = egPromotionChar(san)
    val beforeDest = san.substringBeforeLast(dest)
    val disambig = (if (wantedPiece.isNotBlank()) beforeDest.drop(1) else beforeDest)
        .filter { it in 'a'..'h' || it in '1'..'8' }

    return legal.firstOrNull { mv ->
        val u = mv.toString().lowercase()
        if (u.length < 4 || u.substring(2, 4) != dest) return@firstOrNull false
        val piece = runCatching { board.getPiece(mv.from) }.getOrDefault(LibPiece.NONE)
        val pieceLetter = egPieceLetter(piece)
        if (pieceLetter != wantedPiece) return@firstOrNull false
        if (promo != null && u.getOrNull(4)?.uppercaseChar() != promo) return@firstOrNull false
        val from = u.substring(0, 2)
        disambig.all { ch -> ch == from[0] || ch == from[1] }
    }?.let { it to it.toString().lowercase() }
}

private fun egBuildSyntheticGame(moves: List<LibMove>): com.github.bhlangonijr.chesslib.game.Game {
    val game = com.github.bhlangonijr.chesslib.game.Game(
        "eg_synthetic_${System.nanoTime()}",
        null
    )
    val linked = java.util.LinkedList<LibMove>().apply { addAll(moves) }

    // Avoid relying on a compile-time Kotlin setter: chesslib versions differ.
    // Try the Java setter first, then the backing field as a last-resort fallback.
    runCatching {
        val setter = game.javaClass.methods.firstOrNull { m ->
            m.name == "setHalfMoves" && m.parameterTypes.size == 1
        } ?: error("setHalfMoves not found")
        setter.invoke(game, linked)
    }.onFailure {
        runCatching {
            val field = game.javaClass.getDeclaredField("halfMoves")
            field.isAccessible = true
            field.set(game, linked)
        }
    }
    return game
}

private fun egFenWithSideToMove(fen: String, side: Side): String {
    val parts = fen.trim().split(Regex("\\s+")).toMutableList()
    if (parts.size < 4) return fen
    while (parts.size < 6) parts += if (parts.size == 4) "0" else "1"
    parts[1] = if (side == Side.WHITE) "w" else "b"
    return parts.take(6).joinToString(" ")
}

private fun egMovesFromFenAndTokens(fen: String, tokens: List<String>): Pair<String, List<LibMove>>? {
    if (tokens.isEmpty()) return null
    val board = LibBoard().apply {
        runCatching { loadFromFen(fen) }.onFailure { loadFromFen(EG_START_FEN) }
    }

    val moves = ArrayList<LibMove>()
    for (tok in tokens) {
        val legal = egLegalMoveForSanLike(board, tok) ?: break
        moves += legal.first
        val moved = runCatching { board.doMove(legal.first) }.getOrDefault(false)
        if (!moved) break
    }
    return if (moves.isEmpty()) null else fen to moves
}

private fun egFallbackLoadGameByIndex(context: Context, resId: Int, index: Int): PgnGameInfo? {
    val chunk = egChunkAtIndex(context, resId, index) ?: return null
    val headers = egParseHeaders(chunk)
    val rawFen = headers["FEN"]?.trim()?.takeIf { it.isNotBlank() } ?: EG_START_FEN
    val moveTextTokens = egMoveTokensFromChunk(chunk, headers)
    val blackTagTokens = listOf(headers["Black"].orEmpty())
        .map { egCleanMoveToken(it) }
        .filter { it.isNotBlank() }

    // Endgame studies from EPD/test-suite exports commonly store the intended
    // answer both as movetext and in the Black tag. Try both token sources.
    val tokenCandidates = listOf(moveTextTokens, blackTagTokens)
        .filter { it.isNotEmpty() }
        .distinctBy { it.joinToString(" ") }

    // Try the FEN as written first. Some EPD study suites contain entries where
    // the side-to-move field is inconsistent with the solution token, so also try
    // the opposite side before giving up. Example: movetext says "1. c4 *" but
    // the legal move is Black's ...c4 from the displayed FEN.
    val initialSide = if (rawFen.split(Regex("\\s+")).getOrNull(1) == "b") Side.BLACK else Side.WHITE
    val fixedCandidates = listOf(
        rawFen,
        egFenWithSideToMove(rawFen, if (initialSide == Side.WHITE) Side.BLACK else Side.WHITE)
    ).distinct()

    val parsed = tokenCandidates.firstNotNullOfOrNull { tokens ->
        fixedCandidates.firstNotNullOfOrNull { fenCandidate ->
            egMovesFromFenAndTokens(fenCandidate, tokens)
        }
    } ?: return null

    val fen = parsed.first
    val moves = parsed.second
    val game = egBuildSyntheticGame(moves)
    return PgnGameInfo(
        white = headers["White"].orEmpty(),
        black = headers["Black"].orEmpty(),
        startFen = fen,
        game = game,
        event = headers["Event"].orEmpty(),
        theme = headers["Theme"].orEmpty(),
        rating = headers["Rating"]?.toIntOrNull(),
        site = headers["Site"].orEmpty(),
        note = headers["Note"].orEmpty()
    )
}

private fun egEnsureResultTokenInChunk(chunk: String): String {
    val normalized = chunk.replace("\r\n", "\n")
    val parts = normalized.split(Regex("""\n\s*\n"""), limit = 2)
    if (parts.size != 2) return normalized
    val headers = parts[0].trimEnd()
    val moves = parts[1].trim()
    val fixedMoves = if (moves.isBlank() || Regex("""(1-0|0-1|1/2-1/2|\*)\s*$""").containsMatchIn(moves)) {
        moves.ifBlank { "*" }
    } else {
        "$moves *"
    }
    return "$headers\n\n$fixedMoves\n"
}

private fun egLoadSingleChunkWithChesslib(context: Context, resId: Int, index: Int): PgnGameInfo? {
    val chunk = egChunkAtIndex(context, resId, index) ?: return null
    val headers = egParseHeaders(chunk)
    val clean = egEnsureResultTokenInChunk(chunk)
    val tmp = java.io.File.createTempFile("eg_single_", ".pgn", context.cacheDir)
    return try {
        tmp.writeText(clean, Charsets.UTF_8)
        val holder = com.github.bhlangonijr.chesslib.pgn.PgnHolder(tmp.absolutePath)
        holder.loadPgn()
        val game = holder.games.firstOrNull() ?: return null
        PgnGameInfo(
            white = headers["White"].orEmpty(),
            black = headers["Black"].orEmpty(),
            startFen = headers["FEN"]?.trim()?.takeIf { it.isNotBlank() },
            game = game,
            event = headers["Event"].orEmpty(),
            theme = headers["Theme"].orEmpty(),
            rating = headers["Rating"]?.toIntOrNull(),
            site = headers["Site"].orEmpty(),
            note = headers["Note"].orEmpty()
        )
    } finally {
        runCatching { tmp.delete() }
    }
}

private fun egLoadGameByIndex(context: Context, resId: Int, index: Int): PgnGameInfo? {
    // Use the exact [Event "..."] chunk first. The shared loadGamesByIndexes()
    // helper splits on line.startsWith("[Event"), which also matches [EventDate]
    // in endgame_studies.pgn and corrupts the chunk boundaries.
    return runCatching { egLoadSingleChunkWithChesslib(context, resId, index) }
        .onFailure { Log.e("EndgameScreen", "Exact PGN chunk load failed resId=$resId index=$index", it) }
        .getOrNull()
        ?: runCatching { egFallbackLoadGameByIndex(context, resId, index) }
            .onFailure { Log.e("EndgameScreen", "Fallback PGN load failed resId=$resId index=$index", it) }
            .getOrNull()
        ?: runCatching { loadGamesByIndexes(context, resId, listOf(index)).firstOrNull() }
            .onFailure { Log.e("EndgameScreen", "Shared PGN load failed resId=$resId index=$index", it) }
            .getOrNull()
}

private fun egListEventLabels(context: Context, resId: Int): List<String> {
    val out = ArrayList<String>()
    val re = Regex("""^\s*\[Event\s+"([^"]*)"]\s*$""")
    runCatching {
        context.resources.openRawResource(resId).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                re.find(line)?.groupValues?.getOrNull(1)?.let { label ->
                    out += label.ifBlank { "Endgame #${out.size + 1}" }
                }
            }
        }
    }.onFailure { Log.e("EndgameScreen", "Unable to list endgames", it) }
    return out
}


@Composable
private fun EgSquareButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color(0xFF334155),
    textColor: Color = Color.White,
    compact: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(if (compact) 40.dp else 48.dp)
            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        shape = RoundedCornerShape(0.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 6.dp else 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.75f),
            disabledContentColor = textColor.copy(alpha = 0.85f)
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 12.sp else 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EgMoveLineWindow(
    items: List<EgMoveLineItem>,
    currentPly: Int,
    enabled: Boolean,
    reviewText: String,
    onStart: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val moveLineListState = rememberLazyListState()

    LaunchedEffect(items.size, currentPly) {
        if (items.isNotEmpty()) {
            val target = (if (currentPly > 0) currentPly - 1 else items.lastIndex)
                .coerceIn(0, items.lastIndex)
            moveLineListState.animateScrollToItem(target)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF0B1220).copy(alpha = 0.98f)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Move line",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                EgSquareButton("|◀", onClick = onStart, enabled = enabled && currentPly > 0, backgroundColor = Color(0xFF1E293B))
                EgSquareButton("◀", onClick = onPrev, enabled = enabled && currentPly > 0, backgroundColor = Color(0xFF1E293B))
                EgSquareButton("▶", onClick = onNext, enabled = enabled && currentPly < items.size, backgroundColor = Color(0xFF1E293B))
                EgSquareButton("▶|", onClick = onEnd, enabled = enabled && currentPly < items.size, backgroundColor = Color(0xFF1E293B))
            }

            if (reviewText.isNotBlank()) {
                Text(
                    reviewText,
                    color = Color(0xFFBAE6FD),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (items.isEmpty()) {
                Text("No PGN move line for this position.", color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
            } else {
                LazyColumn(
                    state = moveLineListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                ) {
                    itemsIndexed(items) { _, item ->
                        val isCurrent = item.ply == currentPly
                        val alreadyPlayed = item.ply < currentPly
                        val prefix = if (item.isWhite) "${item.moveNo}." else "${item.moveNo}..."
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable(enabled = enabled) { onSelectPly(item.ply) },
                            shape = RoundedCornerShape(0.dp),
                            color = when {
                                isCurrent -> Color(0xFFF59E0B)
                                alreadyPlayed -> Color(0xFF1D4ED8)
                                else -> Color(0xFF1F2937)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(prefix, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(item.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
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
fun EndgameScreen(
    light: Color,
    dark: Color,
    pieceStyle: PieceStyle,
    pieceSetKey: String,
    onBackToTactics: () -> Unit,
    onPositionChanged: (String) -> Unit = {},
    headerContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var room by remember { mutableStateOf(EgRoom.COURSE) }
    var showRoomDialog by remember { mutableStateOf(true) }
    var labels by remember { mutableStateOf<List<String>>(emptyList()) }
    // selectedIndex = visible slot number in the current room order.
    // selectedRawIndex = actual PGN game index, needed for stable progress tracking.
    var selectedIndex by remember { mutableStateOf(0) }
    var selectedRawIndex by remember { mutableStateOf(0) }
    var roomOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var jumpText by remember { mutableStateOf("1") }
    var showJumpDialog by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<PgnGameInfo?>(null) }
    var session by remember { mutableStateOf<PgnSession?>(null) }
    var status by remember { mutableStateOf("Choose an endgame room.") }
    var loading by remember { mutableStateOf(false) }
    var whiteBottom by remember { mutableStateOf(true) }
    var userSide by remember { mutableStateOf(Side.WHITE) }
    var selectedViewSq by remember { mutableStateOf<Int?>(null) }
    var fishReplying by remember { mutableStateOf(false) }
    var plyTick by remember { mutableStateOf(0) }
    var lastFrom by remember { mutableStateOf<Int?>(null) }
    var lastTo by remember { mutableStateOf<Int?>(null) }
    var choices by remember { mutableStateOf<List<EgChoice>>(emptyList()) }
    var answeredChoice by remember { mutableStateOf<String?>(null) }
    var reviewMode by remember { mutableStateOf(false) }
    var fishReviewLine by remember { mutableStateOf("") }
    val engineMutex = remember { Mutex() }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var lastDragPos by remember { mutableStateOf<Offset?>(null) }
    var animFrom by remember { mutableStateOf<Int?>(null) }
    var animTo by remember { mutableStateOf<Int?>(null) }
    val animT = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var landscapeBoardFractionState by remember { mutableStateOf(-1f) }
    var portraitBoardFractionState by remember { mutableStateOf(-1f) }
    var playedLine by remember { mutableStateOf<List<EgMoveLineItem>>(emptyList()) }
    var positionHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var historyPly by remember { mutableStateOf(0) }

    val completedPrefs = remember(context) {
        context.getSharedPreferences("tf_endgame_progress_v1", Context.MODE_PRIVATE)
    }
    var completedEndgames by remember {
        mutableStateOf(completedPrefs.getStringSet("completed_keys", mutableSetOf<String>())?.toSet() ?: emptySet())
    }

    fun egCompletedKey(targetRoom: EgRoom = room, targetIndex: Int = selectedRawIndex): String =
        "${targetRoom.name}:$targetIndex"

    fun markCurrentEndgameCompleted() {
        val key = egCompletedKey()
        if (key in completedEndgames) return
        val next = completedEndgames + key
        completedEndgames = next
        completedPrefs.edit().putStringSet("completed_keys", next.toMutableSet()).apply()
    }

    fun promptForUserMove(): String =
        "Play ${egSideName(userSide)}. Make your move."

    fun setInitialOrientation(s: PgnSession?) {
        // Only decide orientation at the starting position. After the user moves,
        // keep the board stable and do not flip on Fish's turn.
        whiteBottom = s?.board?.sideToMove != Side.BLACK
    }

    fun hasPgnSolutionLine(): Boolean {
        val s = session ?: return false
        return s.totalPly > 0
    }

    fun applyLastMoveArrow(uci: String) {
        val (from, to) = egMoveToDisplaySquares(uci, whiteBottom)
        lastFrom = from
        lastTo = to
    }

    fun resetMoveHistory(startFen: String) {
        playedLine = emptyList()
        positionHistory = listOf(startFen)
        historyPly = 0
    }

    fun appendMoveLineFromBefore(boardBefore: LibBoard, mv: LibMove?, uciRaw: String) {
        val uci = uciRaw.lowercase()
        val moveNo = boardBefore.fen.split(' ').getOrNull(5)?.toIntOrNull() ?: ((historyPly / 2) + 1)
        val isWhite = boardBefore.sideToMove == Side.WHITE
        val label = if (mv != null) egMoveLabel(boardBefore, mv, uci) else uci.ifBlank { "..." }
        val item = EgMoveLineItem(
            ply = historyPly + 1,
            moveNo = moveNo,
            isWhite = isWhite,
            label = label,
            uci = uci
        )
        playedLine = playedLine.take(historyPly) + item
    }

    fun pushPositionAfterMove(fen: String) {
        val nextPly = historyPly + 1
        val nextHistory = if (positionHistory.size >= historyPly + 1) {
            positionHistory.take(historyPly + 1) + fen
        } else {
            positionHistory + fen
        }
        positionHistory = nextHistory
        historyPly = nextPly
    }

    fun markCompletedIfEndReached() {
        val s = session ?: return
        val reachedSolutionEnd = hasPgnSolutionLine() && s.ply >= s.totalPly
        val noLegalMoves = runCatching { MoveGenerator.generateLegalMoves(s.board).isEmpty() }.getOrDefault(false)
        if (reachedSolutionEnd || noLegalMoves) markCurrentEndgameCompleted()
    }

    fun applyLegalMove(mv: LibMove, uci: String): Boolean {
        val s = session ?: return false
        return runCatching {
            val before = LibBoard().apply { loadFromFen(s.board.fen) }
            appendMoveLineFromBefore(before, mv, uci)
            s.board.doMove(mv)
            pushPositionAfterMove(s.board.fen)
            markCompletedIfEndReached()
            applyLastMoveArrow(uci)
            selectedViewSq = null
            plyTick++
            true
        }.getOrDefault(false)
    }

    fun playNextPgnMove(): Boolean {
        val s = session ?: return false
        val uci = s.peekNextMoveUci()?.lowercase().orEmpty()
        val before = LibBoard().apply { loadFromFen(s.board.fen) }
        val legalForLabel = egLegalMoveForUciPrefix(before, uci)?.first
        if (!s.next()) return false
        appendMoveLineFromBefore(before, legalForLabel, uci)
        pushPositionAfterMove(s.board.fen)
        markCompletedIfEndReached()
        applyLastMoveArrow(uci)
        selectedViewSq = null
        plyTick++
        return true
    }

    fun egIdxCenter(idx: Int): Offset {
        val sidePx = min(boardSize.width, boardSize.height).toFloat().coerceAtLeast(1f)
        val cell = sidePx / 8f
        val file = idx % 8
        val rank = idx / 8
        return Offset((file + 0.5f) * cell, ((7 - rank) + 0.5f) * cell)
    }

    suspend fun animateThenApply(uci: String, applyBlock: () -> Boolean): Boolean {
        if (uci.length < 4) return applyBlock()

        var tries = 0
        while ((boardSize.width <= 0 || boardSize.height <= 0) && tries < 32) {
            delay(16)
            tries++
        }
        if (boardSize.width <= 0 || boardSize.height <= 0) return applyBlock()

        val from = egUciSquareToIdx(uci.substring(0, 2), whiteBottom).takeIf { it >= 0 }
        val to = egUciSquareToIdx(uci.substring(2, 4), whiteBottom).takeIf { it >= 0 }
        if (from == null || to == null) return applyBlock()

        lastFrom = from
        lastTo = to
        animFrom = from
        animTo = to
        isAnimating = true
        animT.snapTo(0f)

        return try {
            animT.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = EG_MOVE_ANIM_MS, easing = LinearEasing)
            )
            applyBlock()
        } catch (_: CancellationException) {
            applyBlock()
        } catch (_: Throwable) {
            applyBlock()
        } finally {
            animFrom = null
            animTo = null
            isAnimating = false
        }
    }

    suspend fun applyLegalMoveAnimated(mv: LibMove, uci: String): Boolean =
        animateThenApply(uci) { applyLegalMove(mv, uci) }

    suspend fun playNextPgnMoveAnimated(): Boolean {
        val s = session ?: return false
        val uci = s.peekNextMoveUci()?.lowercase().orEmpty()
        return animateThenApply(uci) { playNextPgnMove() }
    }

    fun finishOrPrompt() {
        val s = session ?: return
        if (reviewMode) {
            status = "Review mode. Make any valid move on the board."
            return
        }
        val noLegalMoves = runCatching { MoveGenerator.generateLegalMoves(s.board).isEmpty() }.getOrDefault(false)
        status = when {
            egIsMultipleChoice(room) && answeredChoice != null -> "Correct. Use Review with Fish or go to the next position."
            hasPgnSolutionLine() && s.ply >= s.totalPly -> "Solved. Use Next for another endgame."
            noLegalMoves -> "Game over. Use Next for another endgame."
            s.board.sideToMove == userSide -> promptForUserMove()
            else -> "Fish to move..."
        }
    }

    fun jumpToPly(targetPlyRaw: Int) {
        val info = current ?: return
        val fresh = PgnSession(info.game).apply { reset(info.startFen) }
        val targetPly = targetPlyRaw.coerceIn(0, fresh.totalPly)
        repeat(targetPly) { fresh.next() }
        session = fresh
        historyPly = targetPly
        playedLine = egMoveLineItems(info).take(targetPly)
        selectedViewSq = null
        dragFrom = null
        lastDragPos = null
        animFrom = null
        animTo = null
        lastFrom = null
        lastTo = null
        egMoveUciAtPly(info, targetPly)?.let { applyLastMoveArrow(it) }
        plyTick++
        choices = if (egIsMultipleChoice(room) && !reviewMode && targetPly == 0) egBuildChoices(fresh) else choices
        finishOrPrompt()
    }

    fun jumpToHistoryPly(targetPlyRaw: Int) {
        val s = session ?: return
        val targetPly = targetPlyRaw.coerceIn(0, playedLine.size)
        val fen = positionHistory.getOrNull(targetPly) ?: return
        runCatching { s.board.loadFromFen(fen) }.onFailure { return }
        historyPly = targetPly
        selectedViewSq = null
        dragFrom = null
        lastDragPos = null
        animFrom = null
        animTo = null
        lastFrom = null
        lastTo = null
        playedLine.getOrNull(targetPly - 1)?.uci?.let { applyLastMoveArrow(it) }
        plyTick++
        finishOrPrompt()
    }

    fun navigateMoveLineTo(targetPly: Int) {
        if (playedLine.isNotEmpty() || reviewMode || !hasPgnSolutionLine()) {
            jumpToHistoryPly(targetPly)
        } else {
            jumpToPly(targetPly)
        }
    }

    suspend fun bestMoveForCurrentFen(movetimeMs: Int = EG_FISH_THINK_MS): String? = withContext(Dispatchers.IO) {
        val fenNow = session?.board?.fen ?: return@withContext null
        engineMutex.withLock {
            runCatching { ProcEngine.start("endgame") }.getOrElse { return@withContext null }
            runCatching { ProcEngine.clearOutput() }
            runCatching { ProcEngine.evaluateFen(fen = fenNow, movetimeMs = movetimeMs) }
                .getOrElse { return@withContext null }

            var best: String? = null
            var fallback: String? = null
            withTimeoutOrNull(movetimeMs.toLong() + 1000L) {
                while (best == null) {
                    val snap = ProcEngine.lines.value
                    for (i in snap.size - 1 downTo 0) {
                        val line = snap[i]
                        if (line.startsWith("bestmove")) {
                            val candidate = line.split(Regex("\\s+")).getOrNull(1)
                            if (!candidate.isNullOrBlank() && candidate.length >= 4 && candidate != "(none)" && candidate != "0000") {
                                best = candidate.lowercase()
                                break
                            }
                        }
                        if (fallback == null && line.startsWith("info ") && line.contains(" pv ")) {
                            fallback = line.substringAfter(" pv ", "")
                                .split(Regex("\\s+"))
                                .firstOrNull { it.length >= 4 }
                                ?.lowercase()
                        }
                    }
                    if (best != null) break
                    delay(40L)
                }
            }
            best ?: fallback
        }
    }

    fun maybePlayFishReply() {
        val s = session ?: return
        if (reviewMode) {
            finishOrPrompt()
            return
        }
        if (egIsMultipleChoice(room) && !reviewMode) return
        if (!reviewMode && hasPgnSolutionLine() && s.ply >= s.totalPly) {
            finishOrPrompt()
            return
        }
        if (s.board.sideToMove == userSide) {
            finishOrPrompt()
            return
        }

        fishReplying = true
        status = "Fish thinking for 3 seconds..."
        scope.launch {
            val started = System.currentTimeMillis()
            if (!reviewMode && hasPgnSolutionLine() && s.ply < s.totalPly) {
                delay(EG_FISH_THINK_MS.toLong())
                playNextPgnMoveAnimated()
            } else {
                val best = bestMoveForCurrentFen(EG_FISH_THINK_MS)
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < EG_FISH_THINK_MS) delay(EG_FISH_THINK_MS - elapsed)
                val legal = egLegalMoveFromBestUci(s.board, best)
                    ?: runCatching { MoveGenerator.generateLegalMoves(s.board).firstOrNull() }
                        .getOrNull()
                        ?.let { it to it.toString().lowercase() }
                if (legal != null) applyLegalMoveAnimated(legal.first, legal.second)
            }
            fishReplying = false
            finishOrPrompt()
        }
    }

    fun attemptUserMove(fromViewIdx: Int, toViewIdx: Int, animate: Boolean = true) {
        val s = session ?: return
        if (loading || fishReplying || isAnimating) return
        if (egIsMultipleChoice(room) && !reviewMode) return

        if (!reviewMode && hasPgnSolutionLine() && s.ply >= s.totalPly) {
            status = "Solved. Use Next for another endgame."
            selectedViewSq = null
            return
        }

        if (!reviewMode && s.board.sideToMove != userSide) {
            status = "Fish to move..."
            selectedViewSq = null
            maybePlayFishReply()
            return
        }

        val fromIdx = egViewIdxToBoardIdx(fromViewIdx, whiteBottom)
        val toIdx = egViewIdxToBoardIdx(toViewIdx, whiteBottom)
        val attemptedPrefix = egBoardIdxToUciSquare(fromIdx) + egBoardIdxToUciSquare(toIdx)

        if (!reviewMode && hasPgnSolutionLine()) {
            val expected = s.peekNextMoveUci()?.lowercase().orEmpty()
            if (expected.take(4) == attemptedPrefix) {
                selectedViewSq = null
                if (animate) {
                    scope.launch {
                        if (playNextPgnMoveAnimated()) maybePlayFishReply() else finishOrPrompt()
                    }
                } else {
                    if (playNextPgnMove()) maybePlayFishReply() else finishOrPrompt()
                }
            } else {
                selectedViewSq = null
                status = "Try again. ${promptForUserMove()}"
            }
            return
        }

        val legal = egLegalMoveForUciPrefix(s.board, attemptedPrefix)
        if (legal != null) {
            selectedViewSq = null
            if (animate) {
                scope.launch {
                    if (applyLegalMoveAnimated(legal.first, legal.second)) {
                        if (reviewMode) finishOrPrompt() else maybePlayFishReply()
                    } else finishOrPrompt()
                }
            } else {
                if (applyLegalMove(legal.first, legal.second)) {
                    if (reviewMode) finishOrPrompt() else maybePlayFishReply()
                } else finishOrPrompt()
            }
        } else {
            selectedViewSq = null
            status = if (reviewMode) "Illegal move. Review mode accepts valid moves only."
            else "Illegal move. ${promptForUserMove()}"
        }
    }

    fun handleBoardTap(viewIdx: Int) {
        val s = session ?: return
        if (loading || fishReplying || isAnimating) return
        if (egIsMultipleChoice(room) && !reviewMode) return

        val logicalIdx = egViewIdxToBoardIdx(viewIdx, whiteBottom)
        val pieces = boardToUiPieces(s.board)
        val tappedPiece = pieces.getOrNull(logicalIdx)
        val selected = selectedViewSq

        if (selected == null) {
            val sideToPlay = if (reviewMode) s.board.sideToMove else userSide
            if (tappedPiece != null && ((tappedPiece.isWhite && sideToPlay == Side.WHITE) || (!tappedPiece.isWhite && sideToPlay == Side.BLACK))) {
                selectedViewSq = viewIdx
                status = if (reviewMode) "Review mode. ${egSideName(sideToPlay)} to move. Choose destination."
                else "${egSideName(userSide)} to move. Choose destination."
            }
            return
        }

        if (selected == viewIdx) {
            selectedViewSq = null
            status = promptForUserMove()
            return
        }

        val selectedPiece = pieces.getOrNull(egViewIdxToBoardIdx(selected, whiteBottom))
        if (selectedPiece != null && tappedPiece != null && selectedPiece.isWhite == tappedPiece.isWhite) {
            selectedViewSq = viewIdx
            return
        }

        attemptUserMove(selected, viewIdx)
    }

    fun loadRawIndex(rawIndexRaw: Int, visibleSlotHint: Int? = null) {
        if (loading || labels.isEmpty()) return
        val count = labels.size.coerceAtLeast(1)
        val rawIndex = rawIndexRaw.coerceIn(0, count - 1)
        val order = roomOrder.takeIf { it.size == count } ?: (0 until count).toList()
        val visibleSlot = visibleSlotHint
            ?: order.indexOf(rawIndex).takeIf { it >= 0 }
            ?: rawIndex
        val resId = egRawRes(room)
        loading = true
        status = "Loading ${egRoomTitle(room)}..."
        selectedViewSq = null
        fishReplying = false
        answeredChoice = null
        reviewMode = false
        fishReviewLine = ""
        choices = emptyList()
        // The input box always shows the real puzzle number, not the shuffled slot.
        jumpText = (rawIndex + 1).toString()

        scope.launch {
            val info = withContext(Dispatchers.IO) {
                egLoadGameByIndex(context, resId, rawIndex)
            }
            if (info == null) {
                status = "Failed to load endgame."
                loading = false
                return@launch
            }
            val s = PgnSession(info.game).apply { reset(info.startFen) }
            selectedIndex = visibleSlot
            selectedRawIndex = rawIndex
            current = info
            session = s
            resetMoveHistory(s.board.fen)
            userSide = s.board.sideToMove
            lastFrom = null
            lastTo = null
            setInitialOrientation(s)
            plyTick++
            choices = if (egIsMultipleChoice(room)) egBuildChoices(s) else emptyList()
            status = promptForUserMove()
            loading = false
        }
    }

    fun loadAt(indexRaw: Int) {
        if (loading || labels.isEmpty()) return
        val count = labels.size.coerceAtLeast(1)
        val index = indexRaw.floorMod(count)
        val order = roomOrder.takeIf { it.size == count } ?: (0 until count).toList()
        val rawIndex = order.getOrNull(index) ?: index
        loadRawIndex(rawIndex, visibleSlotHint = index)
    }

    fun loadRoom(newRoom: EgRoom) {
        room = newRoom
        showRoomDialog = false
        labels = emptyList()
        roomOrder = emptyList()
        selectedIndex = 0
        selectedRawIndex = 0
        jumpText = "1"
        current = null
        session = null
        choices = emptyList()
        answeredChoice = null
        reviewMode = false
        fishReviewLine = ""
        resetMoveHistory(EG_EMPTY_FEN)
        lastFrom = null
        lastTo = null
        status = "Loading ${egRoomTitle(newRoom)}..."
        loading = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { egListEventLabels(context, egRawRes(newRoom)) }
            labels = found
            roomOrder = if (egIsMultipleChoice(newRoom)) {
                found.indices.toList().shuffled(Random(System.nanoTime()))
            } else {
                found.indices.toList()
            }
            loading = false
            if (found.isEmpty()) {
                status = "No endgames found."
            } else {
                loadAt(0)
            }
        }
    }

    fun step(delta: Int) {
        if (isAnimating) return
        selectedViewSq = null
        val available = if (playedLine.isNotEmpty()) playedLine.size else (session?.totalPly ?: 0)
        val target = (historyPly + delta).coerceIn(0, available)
        if (target != historyPly) navigateMoveLineTo(target)
    }

    fun jumpToText() {
        val n = jumpText.trim().toIntOrNull() ?: return
        if (labels.isNotEmpty()) loadRawIndex((n - 1).coerceIn(0, labels.lastIndex))
    }

    fun chooseMove(choice: EgChoice) {
        val s = session ?: return
        if (!egIsMultipleChoice(room) || answeredChoice != null || reviewMode || loading) return
        answeredChoice = choice.uci
        if (choice.correct) {
            status = "Correct! You found ${choice.label}. Use Review with Fish or go Next."
            scope.launch { playNextPgnMoveAnimated() }
        } else {
            status = "Not quite. The best move is still hidden. Try another choice."
            answeredChoice = null
        }
    }

    fun startReview() {
        val info = current ?: return
        val s = PgnSession(info.game).apply { reset(info.startFen) }
        session = s
        resetMoveHistory(s.board.fen)
        userSide = s.board.sideToMove
        setInitialOrientation(s)
        selectedViewSq = null
        lastFrom = null
        lastTo = null
        reviewMode = true
        answeredChoice = null
        fishReviewLine = "Fish is analyzing..."

        // For Endgame Tactics/Studies, review should start from the solved first move,
        // then become a real sparring session instead of merely replaying the answer.
        if (egIsMultipleChoice(room) && s.totalPly > 0) {
            playNextPgnMove()
        } else {
            plyTick++
        }

        status = "Review mode. Make any valid move on the board."
        plyTick++
    }

    LaunchedEffect(Unit) {
        // Load the course in the background so the board is not empty behind the chooser.
        loadRoom(EgRoom.COURSE)
        showRoomDialog = true
    }

    // Endgame owns its PgnSession internally, so publish every displayed board
    // position for Beat the Fish's cross-mode handoff.
    val liveEndgameFen = session?.board?.fen.orEmpty()
    LaunchedEffect(liveEndgameFen, plyTick) {
        if (liveEndgameFen.isNotBlank() && liveEndgameFen != EG_EMPTY_FEN) {
            onPositionChanged(liveEndgameFen)
        }
    }

    val reviewFenKey = session?.board?.fen.orEmpty()
    LaunchedEffect(reviewMode, reviewFenKey, plyTick) {
        if (!reviewMode || session == null) return@LaunchedEffect
        fishReviewLine = "Fish is analyzing this position..."
        val boardSnapshot = LibBoard().apply { loadFromFen(session?.board?.fen ?: EG_EMPTY_FEN) }
        val best = bestMoveForCurrentFen(EG_FISH_THINK_MS)
        fishReviewLine = "Fish recommends: ${egBestLabelForUci(boardSnapshot, best)}"
    }

    val boardBaseForRender = remember(session, plyTick, whiteBottom) {
        val b = session?.board ?: LibBoard().apply { loadFromFen(EG_EMPTY_FEN) }
        val base = boardToUiPieces(b)
        if (whiteBottom) base else Array<Piece?>(64) { i -> base[63 - i] }
    }
    val boardForRender = remember(boardBaseForRender, dragFrom, animFrom) {
        boardBaseForRender.copyOf().also { visible ->
            dragFrom?.takeIf { it in 0..63 }?.let { visible[it] = null }
            animFrom?.takeIf { it in 0..63 }?.let { visible[it] = null }
        }
    }
    val moveLineItems = remember(current) { egMoveLineItems(current) }

    // Endgame Tactics / Studies are first-move multiple-choice drills.
    // Do not show the PGN solution line before Review mode, because it gives away the answer.
    // After the correct answer, the user first sees the Review button; clicking it reveals
    // the move line and enables analysis-board style review.
    val showMoveLineWindow = !egIsMultipleChoice(room) || reviewMode
    val moveLineDisplayItems = when {
        !showMoveLineWindow -> emptyList()
        playedLine.isNotEmpty() -> playedLine
        else -> moveLineItems
    }
    val moveLineDisplayPly = when {
        !showMoveLineWindow -> 0
        playedLine.isNotEmpty() || !hasPgnSolutionLine() -> historyPly
        else -> session?.ply ?: 0
    }
    val alreadyPlayedCurrent = egCompletedKey() in completedEndgames

    val bgBrush = Brush.verticalGradient(
        listOf(Color(0xFF020617), Color(0xFF07111F), Color(0xFF111827))
    )
    val titleText = current?.event
        ?.ifBlank { labels.getOrNull(selectedIndex).orEmpty() }
        .orEmpty()
        .ifBlank { egRoomTitle(room) }
    val subText = current?.site.orEmpty().ifBlank {
        if (egIsMultipleChoice(room)) "Choose the best first move." else "Spar with Trainer Fish."
    }
    val noteText = current?.note.orEmpty()
    val sidePrompt = if (reviewMode) "Review mode. $fishReviewLine" else status

    if (showRoomDialog) {
        AlertDialog(
            onDismissRequest = { showRoomDialog = false },
            title = {
                Text("Choose Endgame Mode", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(EgRoom.COURSE, EgRoom.TACTICS, EgRoom.STUDIES).forEach { option ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { loadRoom(option) },
                            shape = RoundedCornerShape(18.dp),
                            color = when (option) {
                                EgRoom.COURSE -> Color(0xFF064E3B)
                                EgRoom.TACTICS -> Color(0xFF7C2D12)
                                EgRoom.STUDIES -> Color(0xFF312E81)
                            }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "${egRoomTitle(option)} • ${egRoomCount(option)}",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    egRoomDescription(option),
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoomDialog = false }) { Text("Continue") }
            }
        )
    }


    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Go to puzzle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a puzzle number from 1 to ${labels.size.coerceAtLeast(egRoomCount(room))}.")
                    OutlinedTextField(
                        value = jumpText,
                        onValueChange = { jumpText = it.filter { ch -> ch.isDigit() }.take(5) },
                        singleLine = true,
                        label = { Text("Puzzle #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showJumpDialog = false
                    jumpToText()
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(if (maxWidth > maxHeight) 0.dp else 0.dp)
    ) {

        val parentMaxWidthPx = constraints.maxWidth.toFloat()
        val parentMaxHeightPx = constraints.maxHeight.toFloat()
        val isLandscape = maxWidth > maxHeight
        val compactPortrait = !isLandscape && maxWidth < 600.dp
        val compactLandscape = isLandscape && maxHeight < 500.dp
        val compactScreen = compactPortrait || compactLandscape || maxWidth < 700.dp

        val boardSide = if (isLandscape) {
            val infoPaneReserve = if (compactScreen) 220.dp else 360.dp
            minOf(
                maxHeight - if (compactScreen) 0.dp else 28.dp,
                maxWidth - infoPaneReserve
            ).coerceAtLeast(260.dp)
        } else {
            maxWidth.coerceAtMost(560.dp)
        }
        val splitterDensity = LocalDensity.current
        val defaultLandscapeBoardFraction = if (parentMaxWidthPx > 0f) {
            with(splitterDensity) { (boardSide.toPx() / parentMaxWidthPx).coerceIn(0.45f, 0.97f) }
        } else 0.68f
        val defaultPortraitBoardFraction = if (parentMaxHeightPx > 0f) {
            val titlePx = with(splitterDensity) { if (compactScreen) 0.dp.toPx() else 40.dp.toPx() }
            val neededPx = with(splitterDensity) { boardSide.toPx() } + titlePx
            (neededPx / parentMaxHeightPx).coerceIn(0.42f, 0.97f)
        } else 0.58f
        LaunchedEffect(parentMaxWidthPx, parentMaxHeightPx, defaultLandscapeBoardFraction, defaultPortraitBoardFraction) {
            if (landscapeBoardFractionState < 0f && parentMaxWidthPx > 0f) {
                landscapeBoardFractionState = defaultLandscapeBoardFraction
            }
            if (portraitBoardFractionState < 0f && parentMaxHeightPx > 0f) {
                portraitBoardFractionState = defaultPortraitBoardFraction
            }
        }

        @Composable
        fun BoardPane(modifier: Modifier = Modifier) {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!compactScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Endgame", fontSize = if (compactScreen) 20.sp else 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        if (!isLandscape) EgSquareButton(text = "Tactics", compact = true, onClick = onBackToTactics, backgroundColor = Color(0xFF1E293B))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val boardReservedTop = if (compactScreen) 0.dp else 50.dp
                    val localBoardSide = minOf(
                        maxWidth,
                        (maxHeight - boardReservedTop).coerceAtLeast(180.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier.size(localBoardSide)
                    ) {
                        Box(
                            Modifier
                                .padding(if (compactScreen) 0.dp else 3.dp)
                                .fillMaxSize()
                                .onSizeChanged { boardSize = it },
                            contentAlignment = Alignment.TopStart
                        ) {
                            ChessBoard(
                                board = boardForRender,
                                selected = selectedViewSq,
                                lastMoveFrom = lastFrom,
                                lastMoveTo = lastTo,
                                onSquareClick = {},
                                light = light,
                                dark = dark,
                                pieceStyle = pieceStyle,
                                pieceSetKey = pieceSetKey,
                                whiteBottom = whiteBottom
                            )

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(2f)
                                    .pointerInput(boardSize, whiteBottom, loading, fishReplying, isAnimating, room, reviewMode, plyTick) {
                                        detectTapGestures(
                                            onTap = { pos: Offset ->
                                                posToIndex(pos, boardSize)?.let { handleBoardTap(it) }
                                            }
                                        )
                                    }
                                    .pointerInput(boardSize, whiteBottom, loading, fishReplying, isAnimating, room, reviewMode, plyTick) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                if (loading || fishReplying || isAnimating) return@detectDragGestures
                                                if (egIsMultipleChoice(room) && !reviewMode) return@detectDragGestures
                                                val idx = posToIndex(pos, boardSize) ?: return@detectDragGestures
                                                val s = session ?: return@detectDragGestures
                                                if (!reviewMode && s.board.sideToMove != userSide) return@detectDragGestures
                                                val sideToPlay = if (reviewMode) s.board.sideToMove else userSide
                                                val logical = egViewIdxToBoardIdx(idx, whiteBottom)
                                                val p = boardToUiPieces(s.board).getOrNull(logical) ?: return@detectDragGestures
                                                val ownsPiece = (p.isWhite && sideToPlay == Side.WHITE) || (!p.isWhite && sideToPlay == Side.BLACK)
                                                if (!ownsPiece) return@detectDragGestures
                                                dragFrom = idx
                                                selectedViewSq = idx
                                                lastDragPos = pos
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (dragFrom != null) {
                                                    lastDragPos = lastDragPos?.plus(dragAmount) ?: change.position
                                                }
                                            },
                                            onDragEnd = {
                                                val from = dragFrom
                                                val to = lastDragPos?.let { posToIndex(it, boardSize) }
                                                dragFrom = null
                                                lastDragPos = null
                                                if (from != null && to != null && from != to) attemptUserMove(from, to, animate = false)
                                            },
                                            onDragCancel = {
                                                dragFrom = null
                                                lastDragPos = null
                                            }
                                        )
                                    }
                            )

                            lastDragPos?.let { pos ->
                                val fromIdx = dragFrom?.takeIf { it in 0..63 }
                                if (fromIdx != null) drawPieceOverlay(boardBaseForRender.getOrNull(fromIdx), pos, boardSize)
                            }

                            val af = animFrom?.takeIf { it in 0..63 }
                            val at = animTo?.takeIf { it in 0..63 }
                            if (af != null && at != null && boardSize.width > 0 && boardSize.height > 0) {
                                val fromC = egIdxCenter(af)
                                val toC = egIdxCenter(at)
                                val t = animT.value
                                val p = Offset(
                                    fromC.x + (toC.x - fromC.x) * t,
                                    fromC.y + (toC.y - fromC.y) * t
                                )
                                drawPieceOverlay(boardBaseForRender.getOrNull(af), p, boardSize)
                            }

                            if (loading || fishReplying) CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        @Composable
        fun InfoPane(modifier: Modifier = Modifier) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLandscape) headerContent()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(EgRoom.COURSE, EgRoom.TACTICS, EgRoom.STUDIES).forEach { option ->
                        EgSquareButton(
                            text = if (compactScreen) egRoomShortTitle(option) else egRoomTitle(option),
                            onClick = { if (!loading && room != option) loadRoom(option) },
                            enabled = !loading || room == option,
                            backgroundColor = if (room == option) Color(0xFF2563EB) else Color(0xFF1E293B),
                            compact = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.96f)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Puzzle No. ${selectedRawIndex + 1}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compactScreen) 18.sp else 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (alreadyPlayedCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(0.dp),
                                    color = Color(0xFF16A34A)
                                ) {
                                    Text(
                                        "ALREADY PLAYED",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = if (compactScreen) 11.sp else 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EgSquareButton(
                                text = "Prev",
                                onClick = { if (labels.isNotEmpty()) loadAt(selectedIndex - 1) },
                                enabled = !loading && labels.isNotEmpty(),
                                backgroundColor = Color(0xFF1E293B),
                                modifier = Modifier.width(if (compactPortrait) 58.dp else 70.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable(enabled = !loading) {
                                        jumpText = (selectedIndex + 1).toString()
                                        showJumpDialog = true
                                    },
                                shape = RoundedCornerShape(0.dp),
                                color = Color(0xFF111827)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "# ${selectedIndex + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = if (compactPortrait) 13.sp else 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "/ ${labels.size.coerceAtLeast(egRoomCount(room))}",
                                        color = Color(0xFFCBD5E1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (compactPortrait) 11.sp else 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            EgSquareButton(
                                text = "Go",
                                onClick = {
                                    jumpText = (selectedIndex + 1).toString()
                                    showJumpDialog = true
                                },
                                enabled = !loading,
                                backgroundColor = Color(0xFFF59E0B),
                                textColor = Color(0xFF111827),
                                modifier = Modifier.width(if (compactPortrait) 52.dp else 66.dp)
                            )
                            EgSquareButton(
                                text = "Next",
                                onClick = { if (labels.isNotEmpty()) loadAt(selectedIndex + 1) },
                                enabled = !loading && labels.isNotEmpty(),
                                backgroundColor = Color(0xFF1E293B),
                                modifier = Modifier.width(if (compactPortrait) 58.dp else 70.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(0.dp),
                            color = if (fishReplying) Color(0xFF7C2D12) else Color(0xFF14532D)
                        ) {
                            Text(
                                sidePrompt,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp),
                                fontSize = if (compactScreen) 12.sp else 14.sp
                            )
                        }

                        if (noteText.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(0.dp),
                                color = Color(0xFF422006)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("NOTE", color = Color(0xFFFDE68A), fontWeight = FontWeight.ExtraBold, fontSize = if (compactScreen) 11.sp else 12.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(noteText, color = Color.White, fontSize = if (compactScreen) 12.sp else 13.sp)
                                }
                            }
                        }
                    }
                }

                if (egIsMultipleChoice(room) && !reviewMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                        color = Color(0xFF1E1B4B).copy(alpha = 0.96f)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Choose the best move", color = Color.White, fontWeight = FontWeight.ExtraBold)
                            val choiceColors = listOf(
                                Color(0xFF7C3AED),
                                Color(0xFF2563EB),
                                Color(0xFF0F766E),
                                Color(0xFFB45309)
                            )
                            choices.chunked(2).forEachIndexed { rowIdx, rowChoices ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowChoices.forEachIndexed { colIdx, choice ->
                                        val idx = rowIdx * 2 + colIdx
                                        val picked = answeredChoice == choice.uci
                                        EgSquareButton(
                                            text = "${('A'.code + idx).toChar()}. ${choice.label}",
                                            onClick = { chooseMove(choice) },
                                            enabled = answeredChoice == null,
                                            modifier = Modifier.weight(1f),
                                            backgroundColor = when {
                                                picked && choice.correct -> Color(0xFF16A34A)
                                                picked -> Color(0xFFB91C1C)
                                                else -> choiceColors.getOrElse(idx) { Color(0xFF4C1D95) }
                                            },
                                            compact = false
                                        )
                                    }
                                    if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            if (answeredChoice != null) {
                                EgSquareButton(
                                    text = "Review with Fish",
                                    onClick = { startReview() },
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = Color(0xFF0EA5E9),
                                    compact = false
                                )
                                Text(
                                    "Review opens the move line and lets the engine analyze while you play on.",
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = if (compactScreen) 11.sp else 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (showMoveLineWindow) {
                    EgMoveLineWindow(
                        items = moveLineDisplayItems,
                        currentPly = moveLineDisplayPly,
                        enabled = !loading && !fishReplying && !isAnimating,
                        reviewText = if (reviewMode) fishReviewLine else "",
                        onStart = { navigateMoveLineTo(0) },
                        onPrev = { step(-1) },
                        onNext = { step(1) },
                        onEnd = { navigateMoveLineTo(moveLineDisplayItems.size) },
                        onSelectPly = { ply -> navigateMoveLineTo(ply) }
                    )
                }
            }
        }

        if (isLandscape) {
            val boardFrac = (landscapeBoardFractionState.takeIf { it > 0f } ?: defaultLandscapeBoardFraction).coerceIn(0.45f, 0.97f)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .weight(boardFrac)
                        .fillMaxHeight()
                        .padding(end = 0.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    BoardPane(modifier = Modifier.fillMaxSize())
                }
                BoardResizeSplitter(
                    orientation = BoardResizeSplitterOrientation.VERTICAL,
                    totalPx = parentMaxWidthPx,
                    color = Color(0xFFEF4444),
                    onDeltaFraction = { delta ->
                        val current = (landscapeBoardFractionState.takeIf { it > 0f } ?: boardFrac).coerceIn(0.45f, 0.97f)
                        landscapeBoardFractionState = (current + delta).coerceIn(0.45f, 0.97f)
                    }
                )
                InfoPane(
                    modifier = Modifier
                        .weight(1f - boardFrac)
                        .fillMaxHeight()
                        .padding(start = 8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        } else {
            val boardFrac = (portraitBoardFractionState.takeIf { it > 0f } ?: defaultPortraitBoardFraction).coerceIn(0.42f, 0.97f)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(boardFrac)
                        .fillMaxWidth()
                        .padding(bottom = if (compactScreen) 0.dp else 4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    BoardPane(modifier = Modifier.fillMaxSize())
                }
                BoardResizeSplitter(
                    orientation = BoardResizeSplitterOrientation.HORIZONTAL,
                    totalPx = parentMaxHeightPx,
                    color = Color(0xFFEF4444),
                    onDeltaFraction = { delta ->
                        val current = (portraitBoardFractionState.takeIf { it > 0f } ?: boardFrac).coerceIn(0.42f, 0.97f)
                        portraitBoardFractionState = (current + delta).coerceIn(0.42f, 0.97f)
                    }
                )
                InfoPane(
                    modifier = Modifier
                        .weight(1f - boardFrac)
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
