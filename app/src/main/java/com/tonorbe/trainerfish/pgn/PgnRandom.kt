package com.tonorbe.trainerfish.pgn

import android.content.Context
import android.content.res.AssetFileDescriptor
import androidx.annotation.RawRes
import java.io.BufferedReader
import java.io.FileInputStream
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.util.Locale
import kotlin.random.Random

// Internal helpers in this file are prefixed with pr* to avoid any collisions.
private val prTAG_RE   = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+"([^"]*)"]\s*$""")
private val prFEN_RE   = Regex("""(?mi)^\s*\[FEN\s+"([^"]*)"]""")
private val prTHEME_RE = Regex("""(?mi)^\s*\[Theme\s+"([^"]*)"]""")

private fun prParseHeaders(chunk: String): Map<String, String> {
    if (chunk.isEmpty()) return emptyMap()
    val out = HashMap<String, String>(16)
    for (m in prTAG_RE.findAll(chunk)) out[m.groupValues[1]] = m.groupValues[2]
    return out
}

private fun prChunkMatchesTheme(chunk: String, theme: String?): Boolean {
    if (theme == null || theme.equals("All", true) || theme.isBlank()) return true
    val m = prTHEME_RE.find(chunk) ?: return false
    val t = m.groupValues[1].lowercase(Locale.ROOT)
    val want = theme.lowercase(Locale.ROOT)
    return t.split(',', ';').any { it.trim() == want || it.contains(want) }
}

private fun prOpenReaderAt(afd: AssetFileDescriptor, pos: Long): Pair<BufferedReader, java.nio.channels.FileChannel> {
    val fis = FileInputStream(afd.fileDescriptor)
    val ch = fis.channel
    ch.position(pos)
    val reader = Channels.newReader(ch, Charsets.UTF_8.newDecoder(), -1)
    return Pair(BufferedReader(reader, 64 * 1024), ch)
}

/**
 * Fast *near-uniform* first-puzzle picker:
 * - Seek to a random byte offset
 * - Snap to next "[Event"
 * - Scan until first match; if none, wrap and scan start->offset
 */
fun findRandomMatchingIndex(
    context: Context,
    @RawRes resId: Int,
    theme: String? = null,
    minRating: Int? = null,
    maxRating: Int? = null
): Int? {
    // Try fast random-seek path first (works only if resource is uncompressed)
    val afd = try { context.resources.openRawResourceFd(resId) } catch (_: Throwable) { null }
    if (afd != null) {
        afd.use { d ->
            val startOffset = d.startOffset
            val length = d.length
            if (length > 0L) {
                val randPos = startOffset + Random.nextLong(length)
                val p1 = prScanForMatchFromPosition(
                    afd = d,
                    fromPos = randPos,
                    untilPos = startOffset + length,
                    theme = theme, minRating = minRating, maxRating = maxRating
                )
                if (p1 != null) return p1
                val p2 = prScanForMatchFromPosition(
                    afd = d,
                    fromPos = startOffset,
                    untilPos = randPos,
                    theme = theme, minRating = minRating, maxRating = maxRating
                )
                if (p2 != null) return p2
            }
        }
        // fall through to reservoir fallback if nothing found
    }

    // Fallback: single-pass reservoir sampling (uniform) to pick exactly 1 index.
    var chosen: Int? = null
    var matches = 0
    var gameIdx = -1
    context.resources.openRawResource(resId).bufferedReader(Charset.forName("UTF-8")).useLines { lines ->
        val sb = StringBuilder()
        var inGame = false

        fun accept(chunk: String): Boolean {
            if (!prChunkMatchesTheme(chunk, theme)) return false
            val h = prParseHeaders(chunk)
            val rating = h["Rating"]?.toIntOrNull()
            if (minRating != null && maxRating != null) {
                if (rating == null || rating !in minRating..maxRating) return false
            }
            return true
        }

        fun consider() {
            if (!accept(sb.toString())) return
            matches++
            // pick with probability 1/matches
            if (Random.nextInt(matches) == 0) {
                chosen = gameIdx
            }
        }

        for (line in lines) {
            if (line.startsWith("[Event")) {
                if (inGame) consider()
                inGame = true
                gameIdx++
                sb.clear()
            }
            sb.append(line).append('\n')
        }
        if (inGame) consider()
    }
    return chosen
}

/**
 * Build a *uniform random* set of [limit] matching game indexes from the full file,
 * excluding any in [exclude] (streaming reservoir sampling; zero bias).
 */
fun buildRandomPoolExcluding(
    context: Context,
    @RawRes resId: Int,
    limit: Int,
    exclude: Set<Int>,
    theme: String? = null,
    minRating: Int? = null,
    maxRating: Int? = null
): List<Int> {
    if (limit <= 0) return emptyList()

    val reservoir = ArrayList<Int>(limit)
    var matchesSeen = 0
    var gameIdx = -1

    context.resources.openRawResource(resId).bufferedReader(Charset.forName("UTF-8")).useLines { lines ->
        val sb = StringBuilder()
        var inGame = false

        fun accept(chunk: String): Boolean {
            if (!prChunkMatchesTheme(chunk, theme)) return false
            val h = prParseHeaders(chunk)
            val rating = h["Rating"]?.toIntOrNull()
            if (minRating != null && maxRating != null) {
                if (rating == null || rating !in minRating..maxRating) return false
            }
            return true
        }

        fun consider() {
            if (!accept(sb.toString())) return
            if (exclude.contains(gameIdx)) return
            matchesSeen++
            if (reservoir.size < limit) {
                reservoir += gameIdx
            } else {
                val j = Random.nextInt(matchesSeen)
                if (j < limit) reservoir[j] = gameIdx
            }
        }

        for (line in lines) {
            if (line.startsWith("[Event")) {
                if (inGame) consider()
                inGame = true
                gameIdx++
                sb.clear()
            }
            sb.append(line).append('\n')
        }
        if (inGame) consider()
    }

    return reservoir.shuffled()
}

// ---- internal segmented scan for findRandomMatchingIndex ----
private fun prScanForMatchFromPosition(
    afd: AssetFileDescriptor,
    fromPos: Long,
    untilPos: Long,
    theme: String?,
    minRating: Int?,
    maxRating: Int?
): Int? {
    val start = fromPos.coerceIn(afd.startOffset, afd.startOffset + afd.length)
    val end = untilPos.coerceIn(afd.startOffset, afd.startOffset + afd.length)
    if (start >= end) return null

    val (br, ch) = prOpenReaderAt(afd, start)

    var gameIdx = -1
    var inGame = false
    val sb = StringBuilder()

    fun accept(chunk: String): Boolean {
        if (!prChunkMatchesTheme(chunk, theme)) return false
        val h = prParseHeaders(chunk)
        val rating = h["Rating"]?.toIntOrNull()
        if (minRating != null && maxRating != null) {
            if (rating == null || rating !in minRating..maxRating) return false
        }
        return true
    }

    // If we started mid-game, advance to next "[Event"
    while (true) {
        val line = br.readLine() ?: return null
        if (ch.position() > end) return null
        if (line.startsWith("[Event")) {
            inGame = true
            gameIdx++
            sb.clear()
            sb.append(line).append('\n')
            break
        }
    }

    while (true) {
        val line = br.readLine() ?: break
        if (ch.position() > end) break
        if (line.startsWith("[Event")) {
            if (inGame && accept(sb.toString())) return gameIdx
            inGame = true
            gameIdx++
            sb.clear()
            sb.append(line).append('\n')
        } else {
            sb.append(line).append('\n')
        }
    }

    if (inGame && sb.isNotEmpty() && accept(sb.toString())) return gameIdx
    return null
}
