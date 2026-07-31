package com.tonorbe.trainerfish.pgn

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.game.Game
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import java.io.File
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move


// Standard initial chess position
private const val START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * IMPORTANT: For replay/reader, we keep the chunk as close to original as possible.
 * Variations depend on parentheses structure, spacing, and comments.
 *
 * We only do one safe thing: ensure there's a trailing result token (* / 1-0 / 0-1 / 1/2-1/2),
 * because some parsers expect it.
 *
 * We do NOT remove:
 *  - (% lines)
 *  - @@
 *  - [%cal ...] / [%csl ...]
 *  - parentheses variations
 *  - comments
 */
internal fun sanitizeChunkForReplay(chunk: String): String {
    fun ensureResultToken(moves: String): String {
        val s = moves.trim()
        if (s.isEmpty()) return "*"
        val ended = Regex("""(1-0|0-1|1/2-1/2|\*)\s*$""")
        return if (ended.containsMatchIn(s)) s else "$s *"
    }

    val raw = chunk.replace("\r\n", "\n")
    val parts = raw.split("\n\n", limit = 2)

    return if (parts.size == 2) {
        val headers = parts[0].trimEnd()
        val moves = ensureResultToken(parts[1])
        headers + "\n\n" + moves + "\n"
    } else {
        ensureResultToken(raw) + "\n"
    }
}

// --- PGN annotations (brace/semicolon comments) ---
// Returns: introComment (before first move) + map of plyIndex(1-based) -> comment lines.
// Note: This helper is also used by PGN mode UI to show comments under the mainline move list.
private fun extractPgnComments(chunkRaw: String): Pair<String?, Map<Int, List<String>>> {
    // Only look at movetext portion (after headers). Headers end at blank line.
    val split = chunkRaw.split("\n\n", limit = 2)
    val movetext = if (split.size == 2) split[1] else chunkRaw

    // Remove variations "(...)" (nested) so comments in mainline still map reasonably.
    val sb = StringBuilder(movetext.length)
    var depth = 0
    var i = 0
    while (i < movetext.length) {
        val c = movetext[i]
        when (c) {
            '(' -> { depth++; i++; continue }
            ')' -> { if (depth > 0) depth--; i++; continue }
            else -> {
                if (depth == 0) sb.append(c)
                i++
            }
        }
    }
    val text = sb.toString()

    val intro = StringBuilder()
    val byPly = LinkedHashMap<Int, MutableList<String>>()

    fun addComment(ply: Int, s: String) {
        val clean = s
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return

        if (ply <= 0) {
            if (intro.isNotEmpty()) intro.append('\n')
            intro.append(clean)
        } else {
            byPly.getOrPut(ply) { ArrayList() }.add(clean)
        }
    }

    fun isResultToken(t: String): Boolean =
        t == "1-0" || t == "0-1" || t == "1/2-1/2" || t == "*"

    fun isMoveNumber(t: String): Boolean =
        t.matches(Regex("\\d+\\.+")) || t.matches(Regex("\\d+\\.\\.\\."))

    fun isNag(t: String): Boolean =
        t.startsWith('$') && t.drop(1).all { it.isDigit() }

    var ply = 0
    var lastMovePly = 0

    var j = 0
    while (j < text.length) {
        val ch = text[j]

        // Brace comment
        if (ch == '{') {
            val endIdx = text.indexOf('}', startIndex = j + 1)
            val body = if (endIdx >= 0) text.substring(j + 1, endIdx) else text.substring(j + 1)
            addComment(lastMovePly, body)
            j = if (endIdx >= 0) endIdx + 1 else text.length
            continue
        }

        // Semicolon comment (to end-of-line)
        if (ch == ';') {
            val nl = text.indexOf('\n', startIndex = j + 1)
            val endIdx = if (nl < 0) text.length else nl
            val body = text.substring(j + 1, endIdx)
            addComment(lastMovePly, body)
            j = endIdx
            continue
        }

        if (ch.isWhitespace()) { j++; continue }

        // Read token (stop at whitespace or start of a comment)
        val start = j
        while (j < text.length && !text[j].isWhitespace() && text[j] != '{' && text[j] != ';') {
            j++
        }
        val tok = text.substring(start, j).trim()
        if (tok.isBlank()) continue

        // Ignore non-move tokens
        if (isMoveNumber(tok) || isResultToken(tok) || isNag(tok)) continue

        // Treat as a move token (ply increments 1 per SAN/UCI token in mainline)
        ply += 1
        lastMovePly = ply
    }

    return intro.toString().trim().takeIf { it.isNotBlank() } to byPly
}

// ---------------- Data used by the UI ----------------

data class PgnGameInfo(
    val white: String,
    val black: String,
    val startFen: String?,
    val game: Game,
    // extra fields (safe defaults)
    val event: String = "",
    val theme: String = "",
    val rating: Int? = null,
    val site: String = "",
    val note: String = ""
)

// ---------------- PGN helpers ----------------

private fun normalize(text: String): String {
    var t = text.replace("\r\n", "\n")
    if (t.startsWith("\uFEFF")) t = t.drop(1) // strip BOM
    return t
}

// Tag lines like: [Key "Value"]
private val HEADER_RE = Regex("""(?mi)^\s*\[(\w+)\s+"([^"]*)"]\s*$""")
// Start a new game only on [Event ...]
private val EVENT_RE = Regex("""(?mi)^\s*\[Event\s+""")
// FEN tag extractor
private val FEN_TAG = Regex("""(?im)^\s*\[FEN\s+"([^"]+)"]""")

private fun splitIntoGameChunks(pgn: String): List<String> {
    val starts = EVENT_RE.findAll(pgn).map { it.range.first }.toList()
    if (starts.isEmpty()) return emptyList()
    val ends = starts.drop(1) + pgn.length
    return starts.indices.map { i -> pgn.substring(starts[i], ends[i]) }
}

private fun parseHeaders(chunk: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    HEADER_RE.findAll(chunk).forEach { m ->
        map[m.groupValues[1]] = m.groupValues[2]
    }
    return map
}

private fun extractFen(chunk: String): String? =
    FEN_TAG.find(chunk)?.groupValues?.get(1)?.trim()

private fun tempFile(context: Context, name: String, text: String): File =
    File(context.cacheDir, name).apply { writeText(text) }

// ---------------- Replay session ----------------

class PgnSession(val game: Game) {
    val board: Board = Board()
    private val moves = game.halfMoves ?: emptyList()
    private var startFenSaved: String? = null

    // PGN MODE ONLY: allow overriding the move list (for navigating variation lines)
    private var overrideUciLine: List<String>? = null

    /** Replace the active navigation line with a UCI move list (ply sequence). Pass null to use game mainline. */
    fun setUciLine(uciLine: List<String>?) {
        overrideUciLine = uciLine
        ply = 0
        lastError = null
    }

    /**
     * Replace the active navigation line but KEEP the current ply if possible.
     * This prevents nav buttons from "breaking" when the user taps a move in the movetext
     * (variation navigation): we don't want to reset back to ply 0.
     */
    fun setUciLineKeepPly(uciLine: List<String>?) {
        overrideUciLine = uciLine
        ply = ply.coerceIn(0, totalPly)
        lastError = null
    }

    /** Convenience: set navigation line and also seek to a specific ply. */
    fun setUciLineAndSeek(uciLine: List<String>?, targetPly: Int) {
        overrideUciLine = uciLine
        ply = targetPly.coerceIn(0, totalPly)
        lastError = null
    }

    private fun uciToMove(uci: String): com.github.bhlangonijr.chesslib.move.Move? {
        if (uci.length < 4) return null
        return try {
            val from = com.github.bhlangonijr.chesslib.Square.valueOf(uci.substring(0, 2).uppercase())
            val to = com.github.bhlangonijr.chesslib.Square.valueOf(uci.substring(2, 4).uppercase())
            val promo = if (uci.length >= 5) {
                when (uci[4].lowercaseChar()) {
                    'q' -> com.github.bhlangonijr.chesslib.Piece.WHITE_QUEEN
                    'r' -> com.github.bhlangonijr.chesslib.Piece.WHITE_ROOK
                    'b' -> com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP
                    'n' -> com.github.bhlangonijr.chesslib.Piece.WHITE_KNIGHT
                    else -> null
                }
            } else null
            if (promo != null) com.github.bhlangonijr.chesslib.move.Move(from, to, promo)
            else com.github.bhlangonijr.chesslib.move.Move(from, to)
        } catch (_: Throwable) {
            null
        }
    }

																													
									
							   
																							
	 

																		   
    fun peekNextMoveUci(): String? {
        val o = overrideUciLine
        return if (o != null) o.getOrNull(ply) else moves.getOrNull(ply)?.toString()
    }

    var ply: Int = 0; private set
    var lastError: String? = null; private set

    /** PGN-mode only: allow UI to set ply when jumping board directly (DroidFish-style). */
    fun setPlyDirect(newPly: Int) {
        ply = newPly.coerceAtLeast(0)
        lastError = null
    }

    fun reset(startFen: String?) {
        startFenSaved = startFen
        try {
            if (!startFen.isNullOrBlank()) board.loadFromFen(startFen.trim())
            else board.loadFromFen(START_FEN)
            ply = 0
            lastError = null
        } catch (e: Exception) {
            board.loadFromFen(START_FEN)
            ply = 0
            lastError = "Bad FEN: ${e.message}"
        }
    }

    /** Step forward one ply. */
    fun next(): Boolean {
        val o = overrideUciLine
        val max = if (o != null) o.size else moves.size
        if (max == 0 || ply >= max) return false
        return try {
            if (o != null) {
                val mv = uciToMove(o[ply]) ?: throw IllegalStateException("Bad UCI at ply ${ply + 1}")
                board.doMove(mv)
            } else {
                board.doMove(moves[ply])
            }
            ply += 1
            lastError = null
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Illegal move at ply ${ply + 1}"
            false
        }
    }

    /** Step backward one ply (rebuilds from the start quickly + deterministically). */
    fun prev(): Boolean {
        if (ply <= 0) return false
        val target = ply - 1
        val o = overrideUciLine
        return try {
            board.loadFromFen(startFenSaved?.trim() ?: START_FEN)
            if (o != null) {
                for (i in 0 until target) {
                    val mv = uciToMove(o[i]) ?: throw IllegalStateException("Bad UCI at ply ${i + 1}")
                    board.doMove(mv)
                }
            } else {
                for (i in 0 until target) board.doMove(moves[i])
            }
            ply = target
            lastError = null
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Cannot step back"
            false
        }
    }

    fun jumpToFen(fen: String, targetPly: Int) {
        try {
            board.loadFromFen(fen)
            ply = targetPly.coerceAtLeast(0)
            lastError = null
        } catch (e: Exception) {
            lastError = "Bad cached FEN: ${e.message}"
        }
    }


    val totalPly: Int get() = overrideUciLine?.size ?: moves.size
}

private val THEME_RE = Regex("""(?mi)^\s*\[Theme\s+"([^"]*)"]""")

private fun chunkMatchesTheme(chunk: String, theme: String?): Boolean {
    val t = THEME_RE.find(chunk)?.groupValues?.get(1).orEmpty()
    if (theme == null || theme.equals("all", true)) return true
    return t.split(',', ';').any { it.trim().equals(theme, ignoreCase = true) }
}

/**
 * Parse one PGN chunk into a chesslib Game and basic headers.
 * We keep movetext intact (including variations) by using sanitizeChunkForReplay()
 * which only ensures a trailing result token.
 */
internal fun parseChunkToInfo(context: Context, chunk: String): PgnGameInfo? {
    val clean = sanitizeChunkForReplay(chunk)
    val tmp = tempFile(context, "one_chunk.pgn", clean)

    val holder = PgnHolder(tmp.absolutePath)
    holder.loadPgn()
    val g = holder.games.firstOrNull() ?: return null

    val headers = parseHeaders(clean)
    val white = headers["White"].orEmpty()
    val black = headers["Black"].orEmpty()
    val fen = extractFen(clean)

    return PgnGameInfo(
        white = white,
        black = black,
        startFen = fen,
        game = g,
        event = headers["Event"].orEmpty(),
        theme = headers["Theme"].orEmpty(),
        rating = headers["Rating"]?.toIntOrNull(),
        site = headers["Site"].orEmpty(),
        note = headers["Note"].orEmpty()
    )
}

// Split "pin, skewer" or "pin; skewer" into neat, lowercased tokens
fun themeTokens(theme: String?): Set<String> =
    theme.orEmpty()
        .split(',', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

// All distinct themes present across games, sorted; first item is "All"
fun collectThemes(games: List<PgnGameInfo>): List<String> =
    listOf("All") + games
        .flatMap { themeTokens(it.theme).toList() }
        .toSet()
        .sorted()

// Filter by a chosen theme label; "All" returns everything
fun filterByTheme(games: List<PgnGameInfo>, chosen: String): List<PgnGameInfo> {
    if (chosen.equals("All", true)) return games
    val needle = chosen.lowercase()
    return games.filter { needle in themeTokens(it.theme) }
}


// ============================================================================
// PGN TREE (variations as a real game tree) - used ONLY by PGN mode viewer
// ============================================================================

/**
 * A lightweight PGN movetext tree.
 * - root node represents the starting position (before any move)
 * - each node represents ONE ply (one SAN move) with resolved UCI when possible
 * - mainline is linked by .next; side-variations are stored in parent.variations as heads
 */
data class PgnTreeNode(
    val id: Int,
    val parentId: Int?,
    var san: String = "",
    var uci: String? = null,
    var preComment: String? = null,   // comment before the move
    var postComment: String? = null,  // comment after the move
    var nags: MutableList<String> = mutableListOf(),
    var nextId: Int? = null,
    var variations: MutableList<Int> = mutableListOf()
)

data class PgnTree(
    val startFen: String?,
    val introComment: String?,
    val nodes: MutableList<PgnTreeNode>
) {
    val rootId: Int get() = 0
}

/** Build a PGN tree from a raw PGN chunk (headers + movetext). */
fun parsePgnTreeFromChunk(chunkRaw: String): PgnTree {
    // Delegate to the new unified token-based parser.
    // This makes the PGN tree semantics identical to the writer (PgnEmit).
    return parsePgnTreeFromChunkTokens(chunkRaw)
}

/** Return the node id list from root to the given node (inclusive). */
fun pgnTreePathTo(tree: PgnTree, nodeId: Int): List<Int> {
    if (nodeId <= 0 || nodeId >= tree.nodes.size) return listOf(0)
    val out = ArrayList<Int>()
    var cur: Int? = nodeId
    while (cur != null) {
        out.add(cur)
        cur = tree.nodes[cur].parentId
    }
    out.reverse()
    if (out.isEmpty() || out.first() != 0) out.add(0, 0)
    return out
}

/** Extract SAN list for a path (excluding root). */
fun pgnTreeSanForPath(tree: PgnTree, path: List<Int>): List<String> =
    path.drop(1).mapNotNull { id -> tree.nodes.getOrNull(id)?.san }

/** Extract UCI list for a path (excluding root); moves without UCI are skipped. */
fun pgnTreeUciForPath(tree: PgnTree, path: List<Int>): List<String> =
    path.drop(1).mapNotNull { id -> tree.nodes.getOrNull(id)?.uci }

// ---------------- Local SAN helpers (copied from ReplayScreen; PGN-only use) ----------------

private fun squareFromAlgebraLocal(algebra: String): com.github.bhlangonijr.chesslib.Square {
    return com.github.bhlangonijr.chesslib.Square.valueOf(algebra.uppercase())
}

private fun pieceTypeFromLetterLocal(ch: Char): com.github.bhlangonijr.chesslib.PieceType = when (ch) {
    'K' -> com.github.bhlangonijr.chesslib.PieceType.KING
    'Q' -> com.github.bhlangonijr.chesslib.PieceType.QUEEN
    'R' -> com.github.bhlangonijr.chesslib.PieceType.ROOK
    'B' -> com.github.bhlangonijr.chesslib.PieceType.BISHOP
    'N' -> com.github.bhlangonijr.chesslib.PieceType.KNIGHT
    else -> com.github.bhlangonijr.chesslib.PieceType.PAWN
}

private fun normalizeSanLocal(s: String) = s
    .replace("+","")
    .replace("#","")
    .replace("e.p.","")
    .trim()

private fun moveToUciLocal(m: com.github.bhlangonijr.chesslib.move.Move): String {
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

private fun sanToLegalMoveLocal(board: com.github.bhlangonijr.chesslib.Board, sanRaw: String): com.github.bhlangonijr.chesslib.move.Move? {
    var san = normalizeSanLocal(sanRaw)

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

    val rePromo = Regex("^([a-h])x?([a-h][18])=([QRBN])$")
    val rePawnCap = Regex("^([a-h])x([a-h][1-8])$")
    val rePawnPush = Regex("^([a-h][1-8])$")
    val rePiece = Regex("^([KQRBN])([a-h1-8]?)(x?)([a-h][1-8])$")

    rePromo.matchEntire(san)?.let { m ->
        val fromFile = m.groupValues[1][0]
        val to = squareFromAlgebraLocal(m.groupValues[2])
        val promoType = pieceTypeFromLetterLocal(m.groupValues[3][0])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN &&
                    mv.to == to &&
                    mv.promotion?.pieceType == promoType &&
                    mv.from.toString().lowercase()[0] == fromFile
        }
    }

    rePawnCap.matchEntire(san)?.let { m ->
        val fromFile = m.groupValues[1][0]
        val to = squareFromAlgebraLocal(m.groupValues[2])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN &&
                    mv.to == to &&
                    mv.from.toString().lowercase()[0] == fromFile
        }
    }

    rePawnPush.matchEntire(san)?.let { m ->
        val to = squareFromAlgebraLocal(m.groupValues[1])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            p.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN && mv.to == to
        }
    }

    rePiece.matchEntire(san)?.let { m ->
        val pt = pieceTypeFromLetterLocal(m.groupValues[1][0])
        val disamb = m.groupValues[2]
        val to = squareFromAlgebraLocal(m.groupValues[4])
        return com.github.bhlangonijr.chesslib.move.MoveGenerator.generateLegalMoves(board).firstOrNull { mv ->
            val p = board.getPiece(mv.from)
            if (p.pieceType != pt) return@firstOrNull false
            if (mv.to != to) return@firstOrNull false
            if (disamb.isNotEmpty()) {
                val ch = disamb[0]
                val fromStr = mv.from.toString().lowercase()
                if (ch in 'a'..'h' && fromStr[0] != ch) return@firstOrNull false
                if (ch in '1'..'8' && fromStr[1] != ch) return@firstOrNull false
            }
            true
        }
    }

    return null
}



// ============================================================================
// PGN TREE TEXT (AnnotatedString) - Droidfish-like clickable moves in mainline + variations
// ============================================================================

/**
 * Build a DroidFish-like movetext view (clickable moves + indented variations)
 * via the unified token emitter so parsing/rendering/writing share identical semantics.
 */
fun buildAnnotatedMovetextFromTree(
    tree: PgnTree,
    maxPlies: Int = 800,
    activePly: Int = -1,
    currentNodeIdOverride: Int? = null
): AnnotatedString {

    // If caller tells us the exact nodeId (best, works for variations), use it.
    // Otherwise fall back to "activePly mapped to mainline node id" (old behavior).
    val currentNodeId: Int? = currentNodeIdOverride ?: run {
        if (activePly <= 0) return@run null
        var id: Int? = tree.nodes.getOrNull(tree.rootId)?.nextId
        var ply = 0
        while (id != null && id > 0 && ply < activePly) {
            ply += 1
            if (ply == activePly) return@run id
            id = tree.nodes.getOrNull(id)?.nextId
        }
        null
    }

    val receiver = PgnComposeTextReceiver(
        fontSize = 14.sp,
        indentStep = 12.sp,
        moveColor = Color(0xFFE6E6E6),
        metaColor = Color(0xFF82C784),
        currentBg = Color(0x335A5A5A)
    )
    receiver.clear()
    receiver.setCurrent(currentNodeId)

    emitPgnTokensFromTree(
        tree = tree,
        out = object : PgnTokenSink<Int> {
            override fun clear() = Unit
            override fun setCurrent(currentNodeId: Int?) = Unit
            override fun process(nodeId: Int?, token: PgnToken) {
                for (t in pgnTokenToTok(token)) receiver.process(nodeId, t)
            }
        },
        options = PgnEmitOptions(maxPlies = maxPlies),
        currentNodeId = currentNodeId
    )

    receiver.finish()
    return receiver.getText()
}

private fun pgnTokenToTok(token: PgnToken): List<PgnTok> {
    return when (token) {
        is PgnToken.RavStart -> listOf(PgnTok.LParen)
        is PgnToken.RavEnd -> listOf(PgnTok.RParen)
        is PgnToken.BraceComment -> listOf(PgnTok.Comment(token.body))
        is PgnToken.LineComment -> listOf(PgnTok.Comment(token.body))
        is PgnToken.San -> listOf(PgnTok.Symbol(token.san))
        is PgnToken.NullMove -> listOf(PgnTok.Symbol(s = "--"))
        is PgnToken.Nag -> {
            val n = token.nag.removePrefix("$").toIntOrNull()
            if (n != null) listOf(PgnTok.Nag(n)) else listOf(PgnTok.Symbol(token.nag))
        }
        is PgnToken.NagSymbol -> listOf(PgnTok.Symbol(token.sym))
        is PgnToken.Result -> {
            if (token.value == "*") listOf(PgnTok.Asterisk) else listOf(PgnTok.Symbol(token.value))
        }
        is PgnToken.MoveNumber -> {
            val raw = token.raw.trim()
            val num = raw.takeWhile { it.isDigit() }.toIntOrNull()
            if (num == null) return listOf(PgnTok.Symbol(raw))
            val dots = raw.dropWhile { it.isDigit() }.count { it == '.' }.coerceAtLeast(1)
            val out = ArrayList<PgnTok>(1 + dots)
            out.add(PgnTok.Integer(num))
            repeat(dots.coerceAtMost(3)) { out.add(PgnTok.Period) }
            out
        }
        is PgnToken.Other -> listOf(PgnTok.Symbol(token.raw))
    }
}


fun mainlineNodeIds(tree: PgnTree): List<Int> {
    val out = ArrayList<Int>(512)
    var cur = tree.nodes.getOrNull(tree.rootId)?.nextId
    while (cur != null && cur > 0) {
        out.add(cur)
        cur = tree.nodes.getOrNull(cur)?.nextId
    }
    return out
}

fun mainlinePlyOfNode(tree: PgnTree, nodeId: Int): Int {
    val ids = mainlineNodeIds(tree)
    val idx = ids.indexOf(nodeId)
    return if (idx >= 0) idx + 1 else -1 // ply is 1-based
}

fun isOnMainline(tree: PgnTree, nodeId: Int): Boolean =
    mainlinePlyOfNode(tree, nodeId) > 0

fun buildFenCacheForTree(tree: PgnTree): Map<Int, String> {
    val cache = HashMap<Int, String>(tree.nodes.size * 2)

    val board = Board()
    runCatching {
        val fen = tree.startFen?.trim()
        if (!fen.isNullOrBlank()) board.loadFromFen(fen) else board.loadFromFen(START_FEN)
    }.onFailure {
        board.loadFromFen(START_FEN)
    }

    fun dfs(posNodeId: Int) {
        val posNode = tree.nodes.getOrNull(posNodeId) ?: return

        // Children moves available from this position:
        val children = ArrayList<Int>(1 + posNode.variations.size)
        posNode.nextId?.let { children.add(it) }
        children.addAll(posNode.variations)

        val beforeFen = board.fen

        for (childId in children) {
            val child = tree.nodes.getOrNull(childId) ?: continue

            // Prefer UCI (fast). If missing, fall back to SAN resolution (slower but fills cache).
            val mv: Move? = child.uci?.let { uciToMoveLocal(it) }
                ?: sanToLegalMoveLocal(board, sanRaw = child.san)

            if (mv == null) {
                // Can't resolve move at this position -> skip this branch, but keep board stable
                runCatching { board.loadFromFen(beforeFen) }
                continue
            }

            val ok = runCatching {
                board.doMove(mv)
                true
            }.getOrElse { false }

            if (!ok) {
                runCatching { board.loadFromFen(beforeFen) }
                continue
            }

            // Cache the position AFTER playing this node move
            cache[childId] = board.fen

            // Recurse
            dfs(childId)

            // Restore board for next sibling
            runCatching { board.loadFromFen(beforeFen) }
        }
    }

    dfs(tree.rootId)
    return cache
}

private fun uciToMoveLocal(uciRaw: String): Move? {
    val u = uciRaw.trim()
    if (u.length < 4) return null
    val from = runCatching { Square.valueOf(u.substring(0, 2).uppercase()) }.getOrNull() ?: return null
    val to   = runCatching { Square.valueOf(u.substring(2, 4).uppercase()) }.getOrNull() ?: return null

    // Promotion: e7e8q
    if (u.length >= 5) {
        val p = when (u[4].lowercaseChar()) {
            'q' -> Piece.WHITE_QUEEN
            'r' -> Piece.WHITE_ROOK
            'b' -> Piece.WHITE_BISHOP
            'n' -> Piece.WHITE_KNIGHT
            else -> null
        }
        // chesslib promotion piece color is inferred by side-to-move during doMove() anyway,
        // but constructor expects a Piece; using WHITE_* works fine.
        if (p != null) return Move(from, to, p)
    }
    return Move(from, to)
}






