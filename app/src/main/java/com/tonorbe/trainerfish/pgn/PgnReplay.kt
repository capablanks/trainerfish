package com.tonorbe.trainerfish.pgn

import android.content.Context
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.game.Game
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import java.io.File



// Standard initial chess position
private const val START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

// ---------------- Data used by the UI ----------------

data class PgnGameInfo(
    val white: String,
    val black: String,
    val startFen: String?,
    val game: Game,
    // added fields (with safe defaults)
    val event: String = "",
    val theme: String = "",
    val rating: Int? = null
)


// ---------------- PGN helpers ----------------

private fun normalize(text: String): String {
    var t = text.replace("\r\n", "\n")
    if (t.startsWith("\uFEFF")) t = t.drop(1) // strip BOM
    return t
}

// Tag lines like: [Key "Value"]
private val HEADER_RE  = Regex("""(?mi)^\s*\[(\w+)\s+"([^"]*)"]\s*$""")
// Start a new game only on [Event ...]
private val EVENT_RE   = Regex("""(?mi)^\s*\[Event\s+""")
// FEN tag extractor
private val FEN_TAG    = Regex("""(?im)^\s*\[FEN\s+"([^"]+)"]""")
// Result token in movetext
private val RESULT_TOK = Regex("""\b(1-0|0-1|1/2-1/2|\*)\s*$""")

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

/**
 * Ensure each chunk has a minimal, well-formed header block + trailing result,
 * so chesslib can parse very simple PGNs.
 */

private fun tempFile(context: Context, name: String, text: String): File =
    File(context.cacheDir, name).apply { writeText(text) }

// ---------------- Public API ----------------


// ---------------- Replay session ----------------

class PgnSession(val game: Game) {
    val board: Board = Board()
    private val moves = game.halfMoves ?: emptyList()
    private var startFenSaved: String? = null

    fun peekNextMoveUci(): String? =
        if (ply in (game.halfMoves?.indices ?: IntRange(1, 0)))
            game.halfMoves!![ply].toString() else null

    var ply: Int = 0; private set
    var lastError: String? = null; private set

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
        if (moves.isEmpty() || ply >= moves.size) return false
        return try {
            board.doMove(moves[ply]); ply += 1; lastError = null; true
        } catch (e: Exception) {
            lastError = e.message ?: "Illegal move at ply ${ply + 1}"
            false
        }
    }

    /** Step backward one ply (rebuilds from the start quickly + deterministically). */
    fun prev(): Boolean {
        if (ply <= 0) return false
        val target = ply - 1
        return try {
            // reload start position, then replay to target
            board.loadFromFen(startFenSaved?.trim() ?: START_FEN)
            for (i in 0 until target) board.doMove(moves[i])
            ply = target
            lastError = null
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Cannot step back"
            false
        }
    }

    val totalPly: Int get() = moves.size
}

private val THEME_RE  = Regex("""(?mi)^\s*\[Theme\s+"([^"]*)"]""")
private val WHITE_RE  = Regex("""(?mi)^\s*\[White\s+"([^"]*)"]""")
private val BLACK_RE  = Regex("""(?mi)^\s*\[Black\s+"([^"]*)"]""")
private val RATING_RE = Regex("""(?mi)^\s*\[Rating\s+"(\d+)"]""")

private fun chunkMatchesTheme(chunk: String, theme: String?): Boolean {
    val t = THEME_RE.find(chunk)?.groupValues?.get(1).orEmpty()
    if (theme == null || theme.equals("all", true)) return true
    return t.split(',', ';').any { it.trim().equals(theme, ignoreCase = true) }
}

private fun parseChunkToInfo(context: Context, chunk: String): PgnGameInfo? {
    // Reuse the same flow as your existing loader: sanitize → chesslib parse → headers/FEN.
    val clean = sanitizeChunkForChesslib(chunk)
    val tmp = File(context.cacheDir, "one_chunk.pgn")
    tmp.writeText(clean)

    val holder = PgnHolder(tmp.absolutePath)
    holder.loadPgn()
    val g = holder.games.firstOrNull() ?: return null

    val headers = parseHeaders(clean)
    val white = headers["White"].orEmpty()
    val black = headers["Black"].orEmpty()
    val fen   = extractFen(clean)

    return PgnGameInfo(white = white, black = black, startFen = fen, game = g)
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

