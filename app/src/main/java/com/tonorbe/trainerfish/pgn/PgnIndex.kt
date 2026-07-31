package com.tonorbe.trainerfish.pgn

import android.content.Context
import androidx.annotation.RawRes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.random.Random
import java.util.Locale

// ---- Data ----
data class PgnIdx(
    val idx: Int,
    val start: Long,
    val end: Long,
    val rating: Int?,
    val theme: String?,
    val white: String,
    val black: String,
    val event: String,
    val fen: String?
)

data class RatingBucket(
    val floor: Int,
    val startIdx: Int,
    val endIdx: Int,
    val startByte: Long,
    val endByte: Long
)

// ---- JSON loaders ----
fun loadIndexJson(context: Context, @RawRes resId: Int): List<PgnIdx> {
    val text = context.resources.openRawResource(resId)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    val arr = JSONArray(text)
    val out = ArrayList<PgnIdx>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out += PgnIdx(
            idx = o.getInt("idx"),
            start = o.getLong("start"),
            end = o.getLong("end"),
            rating = if (o.isNull("rating")) null else o.getInt("rating"),
            theme  = if (o.isNull("theme"))  null else o.getString("theme"),
            white  = o.optString("white"),
            black  = o.optString("black"),
            event  = o.optString("event"),
            fen    = if (o.isNull("fen"))    null else o.getString("fen")
        )
    }
    return out.sortedBy { it.idx }
}

fun loadRatingBuckets(context: Context, @RawRes resId: Int): List<RatingBucket> {
    val text = context.resources.openRawResource(resId)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    val arr = JSONArray(text)
    val out = ArrayList<RatingBucket>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out += RatingBucket(
            floor = o.getInt("floor"),
            startIdx = o.getInt("startIdx"),
            endIdx = o.getInt("endIdx"),
            startByte = o.getLong("startByte"),
            endByte = o.getLong("endByte")
        )
    }
    return out.sortedBy { it.floor }
}

/** Load compact theme -> IDs table produced by the indexer (train_all_themes.json). */
fun loadThemeIndex(context: Context, @RawRes resId: Int): Map<String, IntArray> {
    val txt = context.resources.openRawResource(resId)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    val obj = JSONObject(txt)
    val out = HashMap<String, IntArray>(obj.length())
    val it = obj.keys()
    while (it.hasNext()) {
        val k = it.next()
        val arr = obj.getJSONArray(k)
        val a = IntArray(arr.length())
        for (i in 0 until arr.length()) a[i] = arr.getInt(i)
        out[k] = a
    }
    return out
}

// ---- Fast samplers (no full scan) ----

/** Super-fast pick for theme == "All": sample from rating buckets only. */
fun sampleIdsFromBucketsQuick(
    buckets: List<RatingBucket>,
    limit: Int,
    minRating: Int?,
    maxRating: Int?
): List<Int> {
    if (limit <= 0 || buckets.isEmpty()) return emptyList()

    fun floor25(r: Int) = (r / 25) * 25
    val bMin = minRating?.let { floor25(it) }
    val bMax = maxRating?.let { floor25(it) }

    val inRange = buckets.filter { b ->
        (bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)
    }
    if (inRange.isEmpty()) return emptyList()

    val sizes = IntArray(inRange.size)
    var total = 0
    for (i in inRange.indices) {
        val c = (inRange[i].endIdx - inRange[i].startIdx + 1).coerceAtLeast(0)
        sizes[i] = c
        total += c
    }
    if (total <= 0) return emptyList()

    // prefix sums for weighted sampling
    val prefix = IntArray(sizes.size)
    var acc = 0
    for (i in sizes.indices) { acc += sizes[i]; prefix[i] = acc }

    val want = min(limit, total)
    val out = ArrayList<Int>(want)
    val seen = HashSet<Int>(want * 2)

    fun pickOne(): Int {
        val r = Random.nextInt(total) // 0..total-1
        var lo = 0; var hi = prefix.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (r < prefix[mid]) hi = mid else lo = mid + 1
        }
        val b = inRange[lo]
        val base = b.startIdx
        val size = sizes[lo]
        val off = (if (lo == 0) r else r - prefix[lo - 1]).coerceIn(0, size - 1)
        return base + off
    }

    var attempts = 0
    val maxAttempts = want * 20
    while (out.size < want && attempts < maxAttempts) {
        val id = pickOne(); attempts++
        if (seen.add(id)) out += id
    }
    return out
}

/** Theme-specific fast pick: (theme IDs) ∩ (rating buckets). */
fun sampleIdsFromThemeAndBucketsQuick(
    themeIds: IntArray,
    buckets: List<RatingBucket>,
    limit: Int,
    minRating: Int?,
    maxRating: Int?
): List<Int> {
    if (limit <= 0 || themeIds.isEmpty() || buckets.isEmpty()) return emptyList()

    fun floor25(r: Int) = (r / 25) * 25
    val bMin = minRating?.let { floor25(it) }
    val bMax = maxRating?.let { floor25(it) }

    val ranges = buckets.filter { b ->
        (bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)
    }.map { it.startIdx to it.endIdx }
    if (ranges.isEmpty()) return emptyList()

    fun inRanges(id: Int): Boolean {
        for ((s, e) in ranges) if (id in s..e) return true
        return false
    }

    val allowed = ArrayList<Int>()
    for (id in themeIds) if (inRanges(id)) allowed += id
    if (allowed.isEmpty()) return emptyList()

    allowed.shuffle(Random)
    return allowed.take(min(limit, allowed.size))
}

// ---- Legacy pool builder (kept for fallback) ----
fun buildRandomPoolFromBuckets(
    index: List<PgnIdx>,
    buckets: List<RatingBucket>,
    limit: Int,
    minRating: Int?,
    maxRating: Int?,
    theme: String? = null
): List<Int> {
    if (limit <= 0) return emptyList()

    fun floor25(r: Int) = (r / 25) * 25
    val bMin = minRating?.let { floor25(it) }
    val bMax = maxRating?.let { floor25(it) }

    val inRange = buckets.filter { b ->
        (bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)
    }

    // Normalize theme filter (null or "all" means no filter)
    val themeFilter = theme
        ?.takeIf { it.isNotBlank() && !it.equals("all", true) }
        ?.trim()
        ?.lowercase(Locale.ROOT)

    // Match a single index entry against the theme filter.
// We split ONLY on commas/semicolons so phrases stay intact.
    fun matchesTheme(i: Int): Boolean {
        val f = themeFilter ?: return true

        val t = index[i].theme
            ?.lowercase(Locale.ROOT)
            ?: return false

        return t
            .split(Regex("[,;]+"))       // 🔴 split only on comma / semicolon
            .map { it.trim() }
            .any { it == f }
    }



    val candidates = ArrayList<Int>()
    for (b in inRange) {
        var i = b.startIdx
        while (i <= b.endIdx) {
            if (matchesTheme(i)) candidates += i
            i++
        }
    }
    if (candidates.isEmpty()) return emptyList()

    val out = ArrayList<Int>(limit)
    val seen = HashSet<Int>()
    val N = candidates.size
    while (out.size < limit && seen.size < N) {
        val pick = candidates[Random.nextInt(N)]
        if (seen.add(pick)) out += pick
    }
    return out
}

// ---- Batch loader (streaming, OOM-safe) ----
fun loadGamesByOffsetsBatch(
    context: Context,
    @RawRes pgnResId: Int,
    selected: List<PgnIdx>,
    batchSize: Int = 64
): List<PgnGameInfo> {
    if (selected.isEmpty()) return emptyList()

    val afd = context.resources.openRawResourceFd(pgnResId)
        ?: error("PGN must be uncompressed (see androidResources.noCompress)")
    val out = ArrayList<PgnGameInfo>(selected.size)

    FileInputStream(afd.fileDescriptor).channel.use { ch ->
        var i = 0
        while (i < selected.size) {
            val j = min(i + batchSize, selected.size)
            val batch = selected.subList(i, j)

            // stream each batch straight to a temp file; avoid building a giant String
            val tmp = File.createTempFile("cycle_", ".pgn", context.cacheDir)
            try {
                tmp.outputStream().buffered().use { os ->
                    for (e in batch) {
                        val size = (e.end - e.start).toInt()
                        val buf = ByteBuffer.allocate(size)
                        ch.position(afd.startOffset + e.start)
                        var read = 0
                        while (read < size) {
                            val r = ch.read(buf)
                            if (r <= 0) break
                            read += r
                        }
                        val s = String(buf.array(), Charsets.UTF_8)
                        val clean = sanitizeChunkForChesslib(s)
                        os.write(clean.toByteArray(Charsets.UTF_8))
                        os.write('\n'.code) // separator between games
                    }
                }

                val holder = com.github.bhlangonijr.chesslib.pgn.PgnHolder(tmp.absolutePath)
                holder.loadPgn()

                val games = holder.games
                val maxCount = min(games.size, batch.size)
                for (k in 0 until maxCount) {
                    val meta = batch[k]
                    out += PgnGameInfo(
                        white = meta.white,
                        black = meta.black,
                        startFen = meta.fen,
                        game = games[k],
                        event = meta.event,
                        theme = meta.theme ?: "",
                        rating = meta.rating
                    )
                }
            } finally {
                runCatching { tmp.delete() }
            }

            i = j
        }
    }

    return out
}

// ---------- Fast counters (index-only; no PGN scan) ----------

fun countGamesInBuckets(
    buckets: List<RatingBucket>,
    minRating: Int?,
    maxRating: Int?
): Int {
    if (buckets.isEmpty()) return 0
    fun floor25(r: Int) = (r / 25) * 25
    val bMin = minRating?.let(::floor25)
    val bMax = maxRating?.let(::floor25)

    var total = 0
    for (b in buckets) {
        if ((bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)) {
            total += (b.endIdx - b.startIdx + 1).coerceAtLeast(0)
        }
    }
    return total
}

fun countGamesForThemeInBuckets(
    themeIds: IntArray,                  // absolute game indexes for the theme
    buckets: List<RatingBucket>,
    minRating: Int?,
    maxRating: Int?
): Int {
    if (themeIds.isEmpty() || buckets.isEmpty()) return 0
    fun floor25(r: Int) = (r / 25) * 25
    val bMin = minRating?.let(::floor25)
    val bMax = maxRating?.let(::floor25)

    // Build allowed index ranges from buckets
    val ranges = ArrayList<IntRange>(buckets.size)
    for (b in buckets) {
        if ((bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)) {
            ranges += b.startIdx..b.endIdx
        }
    }
    if (ranges.isEmpty()) return 0

    // Count theme ids that fall into any allowed range
    var c = 0
    outer@ for (id in themeIds) {
        for (r in ranges) {
            if (id in r) { c++; continue@outer }
        }
    }
    return c
}

