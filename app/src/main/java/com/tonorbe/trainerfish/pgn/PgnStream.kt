package com.tonorbe.trainerfish.pgn

import android.content.Context
import androidx.annotation.RawRes
import com.github.bhlangonijr.chesslib.game.Game
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import java.io.File
import java.util.Locale
import kotlin.math.min




internal fun sanitizeChunkForChesslib(chunk: String): String {
    fun ensureResultToken(moves: String): String {
        val s = moves.trim()
        if (s.isEmpty()) return "*"
        val ended = Regex("(1-0|0-1|1/2-1/2|\\*)\\s*$")
        return if (ended.containsMatchIn(s)) s else "$s *"
    }
    val parts = chunk.split("\n\n", limit = 2)
    return if (parts.size == 2) parts[0] + "\n\n" + ensureResultToken(parts[1]) + "\n"
    else ensureResultToken(chunk) + "\n"
}

// -----------------------------------------------------------------------------
// Regex helpers
// -----------------------------------------------------------------------------
private val TAG_RE        = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+"([^"]*)"]\s*$""")
private val FEN_RE        = Regex("""(?mi)^\s*\[FEN\s+"([^"]*)"]""")
private val THEME_RE      = Regex("""(?mi)^\s*\[Theme\s+"([^"]*)"]""")
private val RESULT_END_RE = Regex("""(1-0|0-1|1/2-1/2|\*)\s*$""")

private fun ensureResultToken(moves: String): String {
    val s = moves.trim()
    if (s.isEmpty()) return "*"
    return if (RESULT_END_RE.containsMatchIn(s)) s else "$s *"
}

private fun parseHeaders(chunk: String): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    TAG_RE.findAll(chunk).forEach { m -> map[m.groupValues[1]] = m.groupValues[2] }
    return map
}

private fun extractFen(chunk: String): String? = FEN_RE.find(chunk)?.groupValues?.getOrNull(1)

// Split ONLY on commas / semicolons, so multi-word themes stay intact.
// Extract distinct theme tokens from a PGN [Theme "..."] header
// We now split ONLY on commas / semicolons, so phrases like
// "anastasia's mate" stay intact.
// Split on commas, semicolons, and whitespace so each Lichess tag token
// (advancedpawn, fork, matein2, long, middlegame, ...) becomes its own theme.
private fun splitThemeTokens(theme: String?): Set<String> {
    return theme
        .orEmpty()
        .split(Regex("[,;\\s]+"))
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .toSet()
}


// Does this PGN chunk match the requested theme filter?
private fun chunkMatchesTheme(chunk: String, theme: String?): Boolean {
    if (theme == null || theme.equals("all", true)) return true

    val raw = THEME_RE.find(chunk)?.groupValues?.get(1).orEmpty()
    val tokens = splitThemeTokens(raw)

    return theme.trim().lowercase(Locale.ROOT) in tokens
}

// -----------------------------------------------------------------------------
// Themes: quick list + cached full list (distinct tokens)
// -----------------------------------------------------------------------------
private const val THEME_CACHE_SP = "gm_theme_cache"
private const val THEME_CACHE_VERSION = 3  // bump if format changes

/** Very quick pass: scan some of the file and collect theme TOKENS (distinct). */
fun listThemesInRawQuick(
    context: Context,
    @RawRes resId: Int,
    byteLimit: Long = 1_500_000,
    gameLimit: Int = 2000
): List<String> {
    val out = linkedSetOf<String>()
    var bytes = 0L
    var games = 0
    context.resources.openRawResource(resId)
        .bufferedReader(Charsets.UTF_8)
        .useLines { seq ->
            for (line in seq) {
                bytes += (line.length + 1)
                if (line.startsWith("[Event")) {
                    games++
                    if (games >= gameLimit || bytes >= byteLimit) break
                }
                if (line.startsWith("[Theme")) {
                    val firstQ = line.indexOf('"')
                    val lastQ  = line.lastIndexOf('"')
                    if (firstQ >= 0 && lastQ > firstQ) {
                        val raw = line.substring(firstQ + 1, lastQ)
                        out += splitThemeTokens(raw)
                    }
                }
            }
        }
    return out.sorted()
}

/** Full pass with cache keyed by resource length + version. Returns distinct, sorted tokens. */
fun listThemesCached(context: Context, @RawRes resId: Int): List<String> {
    // Try cache
    val sp = context.getSharedPreferences(THEME_CACHE_SP, Context.MODE_PRIVATE)
    val keyData = "themes_$resId"
    val keySig  = "sig_$resId"
    val wantSig = "${THEME_CACHE_VERSION}:${rawLength(context, resId)}"
    val haveSig = sp.getString(keySig, null)
    val cached  = sp.getString(keyData, null)
    if (haveSig == wantSig && !cached.isNullOrEmpty()) {
        return cached.split('|').filter { it.isNotBlank() }
    }

    // Full scan
    val out = linkedSetOf<String>()
    context.resources.openRawResource(resId)
        .bufferedReader(Charsets.UTF_8)
        .useLines { seq ->
            for (line in seq) {
                if (line.startsWith("[Theme")) {
                    val firstQ = line.indexOf('"')
                    val lastQ  = line.lastIndexOf('"')
                    if (firstQ >= 0 && lastQ > firstQ) {
                        val raw = line.substring(firstQ + 1, lastQ)
                        out += splitThemeTokens(raw)
                    }
                }
            }
        }

    val finalList = out.sorted()
    sp.edit()
        .putString(keySig, wantSig)
        .putString(keyData, finalList.joinToString("|"))
        .apply()
    return finalList
}

private fun rawLength(context: Context, @RawRes resId: Int): Long =
    try { context.resources.openRawResourceFd(resId).use { it.length } } catch (_: Throwable) { -1L }

// -----------------------------------------------------------------------------
// Woodpecker helpers: find absolute game indexes by filter, then load by index
// -----------------------------------------------------------------------------
fun findGameIndexesByFilter(
    context: Context,
    @RawRes resId: Int,
    limit: Int,
    theme: String? = null,
    minRating: Int? = null,
    maxRating: Int? = null
): List<Int> {
    if (limit <= 0) return emptyList()

    // Reservoir sampling over ALL matches so early-file games don't dominate.
    val reservoir = ArrayList<Int>(limit)
    var matchesSeen = 0

    context.resources.openRawResource(resId).bufferedReader(Charsets.UTF_8).useLines { lines ->
        val sb = StringBuilder()
        var inGame = false
        var gameIdx = -1

        fun accept(): Boolean {
            val chunk = sb.toString(); sb.clear()
            if (!chunkMatchesTheme(chunk, theme)) return false
            val h = parseHeaders(chunk)
            val rating = h["Rating"]?.toIntOrNull()
            if (minRating != null && maxRating != null) {
                if (rating == null || rating !in minRating..maxRating) return false
            }
            return true
        }

        fun considerCurrentGame() {
            if (!accept()) return
            matchesSeen++
            if (reservoir.size < limit) {
                // Fill the reservoir first
                reservoir += gameIdx
            } else {
                // Replace an existing element with probability limit/matchesSeen
                val j = kotlin.random.Random.nextInt(matchesSeen)
                if (j < limit) {
                    reservoir[j] = gameIdx
                }
            }
        }

        for (line in lines) {
            if (line.startsWith("[Event")) {
                if (inGame) considerCurrentGame()
                inGame = true
                gameIdx++
            }
            sb.append(line).append('\n')
        }
        // Trail: last buffered game
        if (inGame && sb.isNotEmpty()) considerCurrentGame()
    }

    // Optional: shuffle so the returned list itself is in random order
    return reservoir.shuffled(kotlin.random.Random)
}

fun loadGamesByIndexesWithOffsets(
    context: Context,
    @RawRes pgnResId: Int,
    index: PgnOffsetIndex,
    indexes: List<Int>
): List<PgnGameInfo> {
    if (indexes.isEmpty()) return emptyList()

    // Important: process in ascending order so we can stream-skip forward
    val sorted = indexes.distinct().sorted()

    val out = ArrayList<PgnGameInfo>(sorted.size)

    context.resources.openRawResource(pgnResId).use { input ->
        var curPos = 0L

        for (gameIdx in sorted) {
            val off = index.get(gameIdx) ?: continue
            val target = off.start.toLong()
            if (target < curPos) {
                // If out-of-order or mismatch, fall back by reopening stream (rare)
                // (But sorted should prevent this)
                continue
            }

            skipFully(input, target - curPos)
            curPos = target

            val bytes = readFully(input, off.length)
            curPos += off.length

            val chunk = bytes.toString(Charsets.UTF_8)
            val clean = sanitizeChunkForChesslib(chunk)

            // reuse your existing chunk->PgnGameInfo parsing logic
            val headers = parseHeaders(clean)
            val tmp = File.createTempFile("pgn_", ".pgn", context.cacheDir)
            try {
                tmp.writeText(clean, Charsets.UTF_8)
                val holder = PgnHolder(tmp.absolutePath)
                holder.loadPgn()
                val g: Game = holder.games.firstOrNull() ?: continue

                out += PgnGameInfo(
                    white = headers["White"].orEmpty(),
                    black = headers["Black"].orEmpty(),
                    startFen = extractFen(clean),
                    game = g,
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
    }

    return out
}


fun loadGamesByIndexes(
    context: Context,
    @RawRes resId: Int,
    indexes: List<Int>
): List<PgnGameInfo> {
    if (indexes.isEmpty()) return emptyList()
    val want = indexes.toHashSet()
    val out = ArrayList<PgnGameInfo>(indexes.size)

    context.resources.openRawResource(resId).bufferedReader(Charsets.UTF_8).useLines { lines ->
        val sb = StringBuilder()
        var inGame = false
        var gameIdx = -1

        fun maybeEmit() {
            if (gameIdx !in want) { sb.clear(); return }
            val chunk = sb.toString(); sb.clear()
            val clean = sanitizeChunkForChesslib(chunk)
            val tmp = File.createTempFile("one_", ".pgn", context.cacheDir)
            try {
                tmp.writeText(clean, Charsets.UTF_8)
                val holder = PgnHolder(tmp.absolutePath)
                holder.loadPgn()
                val g: Game = holder.games.firstOrNull() ?: return
                val h = parseHeaders(chunk)
                out += PgnGameInfo(
                    white = h["White"].orEmpty(),
                    black = h["Black"].orEmpty(),
                    startFen = extractFen(clean),
                    game = g,
                    event = h["Event"].orEmpty(),          // <- Event now *is* the Lichess ID for tactics
                    theme = h["Theme"].orEmpty(),
                    rating = h["Rating"]?.toIntOrNull(),
                    site = h["Site"].orEmpty(),
                    note = h["Note"].orEmpty()
                )


            } finally { runCatching { tmp.delete() } }
        }

        for (line in lines) {
            if (line.startsWith("[Event")) {
                if (inGame) maybeEmit()
                inGame = true; gameIdx++
            }
            sb.append(line).append('\n')
        }
        if (inGame && sb.isNotEmpty()) maybeEmit()
    }

    // Preserve requested order
    val map = HashMap<Int, PgnGameInfo>(out.size)
    var pos = 0
    for (idx in indexes) {
        if (pos < out.size) { map[idx] = out[pos]; pos++ }
    }
    return indexes.mapNotNull { map[it] }
}

// Count total games (lines that begin with [Event]) in a raw PGN resource.
fun countGamesInResource(
    context: Context,
    @RawRes resId: Int
): Int {
    var games = 0
    context.resources.openRawResource(resId)
        .bufferedReader(Charsets.UTF_8)
        .useLines { seq ->
            for (line in seq) if (line.startsWith("[Event")) games++
        }
    return games
}

private fun skipFully(input: java.io.InputStream, bytes: Long) {
    var remaining = bytes
    val scratch = ByteArray(64 * 1024)
    while (remaining > 0) {
        val toRead = min(scratch.size.toLong(), remaining).toInt()
        val r = input.read(scratch, 0, toRead)
        if (r < 0) throw java.io.EOFException("EOF while skipping")
        remaining -= r
    }
}

private fun readFully(input: java.io.InputStream, len: Int): ByteArray {
    val out = ByteArray(len)
    var off = 0
    while (off < len) {
        val r = input.read(out, off, len - off)
        if (r < 0) throw java.io.EOFException("EOF while reading chunk")
        off += r
    }
    return out
}



