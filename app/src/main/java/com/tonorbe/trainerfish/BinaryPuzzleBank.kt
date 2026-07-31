package com.tonorbe.trainerfish

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import com.github.bhlangonijr.chesslib.game.Game
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import com.tonorbe.trainerfish.pgn.PgnGameInfo
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

private const val TACTICS_BIN_VERSION = 1
private const val SHARD_KEY_STEP = 1_000_000
private const val NO_THEME_ID = 0xFFFF
private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

private const val MAGIC_PUZZLES = "TFPZBIN1"
private const val MAGIC_OFFSETS = "TFPZOFF1"
private const val MAGIC_BUCKETS = "TFPZBKT1"
private const val MAGIC_THEMES = "TFPZTHM1"
private const val MAGIC_THEME_INDEX = "TFPZTIX1"

private const val PGN_CACHE_PREFS = "tf_binary_puzzle_pgn_cache_v1"
private const val PGN_CACHE_ORDER = "order_csv"
private const val PGN_CACHE_MAX = 64
private const val STARTER_INDEX_NAME = "train_starter_puzzles.json"


data class TacticsPuzzleKey(
    val shard: TacticsShard,
    val localIndex: Int
) {
    val encoded: Int get() = shard.ordinal * SHARD_KEY_STEP + localIndex
}

data class TacticsBinRatingBucket(
    val floor: Int,
    val startIdx: Int,
    val endIdx: Int
)

data class TacticsBinRecord(
    val encodedId: Int,
    val localIndex: Int,
    val puzzleId: String,
    val rating: Int,
    val fen: String,
    val uciMoves: List<String>,
    val primaryTheme: String,
    val shard: TacticsShard
)

enum class TacticsShard(
    val folder: String,
    val label: String,
    val minRating: Int,
    val maxRating: Int
) {
    BEGINNER("beginner_1200_1499", "Beginner", 1200, 1499),
    EASY("easy_1500_1799", "Easy", 1500, 1799),
    MEDIUM("medium_1800_2099", "Medium", 1800, 2099),
    DIFFICULT("difficult_2100_2299", "Difficult", 2100, 2299),
    MASTERCLASS("masterclass_2300_2499", "Masterclass", 2300, 2499),
    GRANDMASTER("grandmaster_2500_up", "Grandmaster", 2500, 10000);

    companion object {
        fun forRating(rating: Int): TacticsShard = values().firstOrNull { rating in it.minRating..it.maxRating }
            ?: if (rating >= 2500) GRANDMASTER else BEGINNER
    }
}

object TacticsBinaryBank {
    private val offsetsCache = HashMap<TacticsShard, Pair<IntArray, IntArray>>()
    private val bucketsCache = HashMap<TacticsShard, List<TacticsBinRatingBucket>>()
    private val themesCache = HashMap<TacticsShard, Map<String, IntArray>>()
    private val themeNamesCache = HashMap<TacticsShard, List<String>>()
    private val themeIndexManifestCache = HashMap<TacticsShard, Map<String, String>>()
    private val themeIndexCache = HashMap<String, IntArray>()
    private val countIndexCache = HashMap<TacticsShard, JSONObject>()
    private val starterIndexCache = HashMap<TacticsShard, JSONObject>()

    private val starterWarmStarted = HashSet<String>()

    /**
     * Background-only helper: pre-decode a few starter puzzles per shard into the tiny
     * persisted PGN cache. This makes a later cold-start New Cycle able to open its
     * first puzzle from cache instead of touching the large binary shard.
     */
    fun warmStarterCache(context: Context, perShard: Int = 1) {
        val appCtx = context.applicationContext
        val want = perShard.coerceIn(1, 4)

        for (shard in TacticsShard.values()) {
            val warmKey = "${shard.folder}:$want"
            val shouldWarm = synchronized(starterWarmStarted) {
                if (starterWarmStarted.contains(warmKey)) false
                else {
                    starterWarmStarted.add(warmKey)
                    true
                }
            }
            if (!shouldWarm) continue

            runCatching {
                val hi: Int? = if (shard.maxRating >= 10000) null else shard.maxRating
                val ids = samplePuzzleIds(
                    context = appCtx,
                    theme = "All",
                    limit = want,
                    minRating = shard.minRating,
                    maxRating = hi
                )
                if (ids.isNotEmpty()) {
                    loadGames(appCtx, ids) // loadGames() saves each decoded puzzle into PGN cache.
                }
            }.onFailure { t ->
                Log.w("TacticsBinaryBank", "Starter cache warm failed for ${shard.folder}", t)
            }
        }
    }

    /**
     * Return already-cached decoded puzzle IDs matching the requested planner filter.
     * Used to make the first puzzle of a new cycle cold-start instantly when possible.
     */
    fun cachedPuzzleIdsMatching(
        context: Context,
        theme: String,
        minRating: Int?,
        maxRating: Int?,
        limit: Int = 16
    ): List<Int> {
        if (limit <= 0) return emptyList()

        val sp = context.getSharedPreferences(PGN_CACHE_PREFS, Context.MODE_PRIVATE)
        val token = normalizedThemeKey(theme)
        val order = sp.getString(PGN_CACHE_ORDER, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }

        if (order.isEmpty()) return emptyList()

        val out = ArrayList<Int>(limit)
        for (encoded in order) {
            val pgn = sp.getString("pgn_$encoded", null)?.takeIf { it.startsWith("[Event") } ?: continue
            val headers = runCatching { parsePgnHeaders(pgn) }.getOrNull() ?: continue

            val rating = headers["Rating"]?.toIntOrNull()
            if (rating != null) {
                if (minRating != null && rating < minRating) continue
                if (maxRating != null && rating > maxRating) continue
            } else {
                // Rating missing should be rare; at least ensure the encoded shard overlaps the range.
                val shard = decode(encoded)?.shard ?: continue
                if (minRating != null && shard.maxRating < minRating) continue
                if (maxRating != null && shard.minRating > maxRating) continue
            }

            if (token.isNotBlank() && token != "all") {
                val rawTheme = headers["Theme"].orEmpty()
                val hasTheme = rawTheme
                    .split(Regex("[,;\\s]+"))
                    .any { normalizedThemeKey(it) == token }
                if (!hasTheme) continue
            }

            out += encoded
            if (out.size >= limit) break
        }
        return out
    }


    /**
     * Ultra-fast first-puzzle path for Start New Cycle.
     * Reads a tiny shard-local train_starter_puzzles.json and parses one prebuilt PGN.
     * This avoids touching train_puzzles.bin / offsets / theme indexes before the board is shown.
     */
    fun loadStarterPuzzle(
        context: Context,
        theme: String,
        minRating: Int?,
        maxRating: Int?
    ): Pair<Int, PgnGameInfo>? {
        val token = normalizedThemeKey(theme)
        val candidates = ArrayList<Pair<Int, String>>(64)

        for (shard in shardsForRange(minRating, maxRating)) {
            val root = runCatching { loadStarterIndex(context, shard) }.getOrNull() ?: continue
            val source: JSONArray? = if (token.isBlank() || token == "all") {
                root.optJSONArray("all")
            } else {
                root.optJSONObject("themes")?.optJSONArray(token)
            }
            if (source == null || source.length() == 0) continue

            for (i in 0 until source.length()) {
                val o = source.optJSONObject(i) ?: continue
                val local = o.optInt("localIndex", -1)
                if (local < 0) continue
                val rating = o.optInt("rating", -1)
                if (rating >= 0) {
                    if (minRating != null && rating < minRating) continue
                    if (maxRating != null && rating > maxRating) continue
                }
                val pgn = o.optString("pgn", "")
                if (!pgn.startsWith("[Event")) continue
                candidates += encode(shard, local) to pgn
            }
        }

        if (candidates.isEmpty()) return null
        val (encoded, pgn) = candidates[Random.nextInt(candidates.size)]
        val info = starterPgnToGameInfo(
            context = context,
            pgn = pgn,
            fallbackTheme = theme.takeIf { it.isNotBlank() && !it.equals("all", true) } ?: ""
        ) ?: return null
        cacheStarterPuzzlePgn(context, encoded, pgn)
        return encoded to info
    }

    /**
     * Fast starter batch path used while a streamed cycle is still being filled.
     * Returns several ready-to-display puzzles from train_starter_puzzles.json,
     * excluding IDs already used in the cycle. This keeps Next responsive even if
     * the full random sampler is still working in the background.
     */
    fun loadStarterPuzzles(
        context: Context,
        theme: String,
        minRating: Int?,
        maxRating: Int?,
        exclude: Set<Int> = emptySet(),
        limit: Int = 8
    ): List<Pair<Int, PgnGameInfo>> {
        val want = limit.coerceIn(1, 64)
        val token = normalizedThemeKey(theme)
        val fallbackTheme = theme.takeIf { it.isNotBlank() && !it.equals("all", true) } ?: ""
        val candidates = ArrayList<Pair<Int, String>>(128)

        for (shard in shardsForRange(minRating, maxRating)) {
            val root = runCatching { loadStarterIndex(context, shard) }.getOrNull() ?: continue
            val source: JSONArray? = if (token.isBlank() || token == "all") {
                root.optJSONArray("all")
            } else {
                root.optJSONObject("themes")?.optJSONArray(token)
            }
            if (source == null || source.length() == 0) continue

            for (i in 0 until source.length()) {
                val o = source.optJSONObject(i) ?: continue
                val local = o.optInt("localIndex", -1)
                if (local < 0) continue
                val encoded = encode(shard, local)
                if (encoded in exclude) continue

                val rating = o.optInt("rating", -1)
                if (rating >= 0) {
                    if (minRating != null && rating < minRating) continue
                    if (maxRating != null && rating > maxRating) continue
                }

                val pgn = o.optString("pgn", "")
                if (!pgn.startsWith("[Event")) continue
                candidates += encoded to pgn
            }
        }

        if (candidates.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Int, PgnGameInfo>>(want)
        val seen = HashSet<Int>(exclude.size + want * 2)
        seen += exclude

        for ((encoded, pgn) in candidates.shuffled(Random(System.nanoTime()))) {
            if (!seen.add(encoded)) continue
            val info = starterPgnToGameInfo(
                context = context,
                pgn = pgn,
                fallbackTheme = fallbackTheme
            ) ?: continue
            cacheStarterPuzzlePgn(context, encoded, pgn)
            out += encoded to info
            if (out.size >= want) break
        }
        return out
    }

    fun encode(shard: TacticsShard, localIndex: Int): Int = shard.ordinal * SHARD_KEY_STEP + localIndex

    fun decode(encoded: Int): TacticsPuzzleKey? {
        if (encoded < 0) return null
        val shardOrdinal = encoded / SHARD_KEY_STEP
        val local = encoded % SHARD_KEY_STEP
        val shard = TacticsShard.values().getOrNull(shardOrdinal) ?: return null
        return TacticsPuzzleKey(shard, local)
    }

    fun totalPuzzleCount(context: Context): Int = TacticsShard.values().sumOf { shard ->
        countPuzzleCountFromIndex(context, shard)
            ?: runCatching {
                val manifest = ensureAssetCopied(context, shard, "train_manifest.json")
                val value = JSONObject(manifest.readText(Charsets.UTF_8)).optInt("puzzleCount", -1)
                value.takeIf { it >= 0 }
            }.getOrNull()
            ?: runCatching { loadOffsets(context, shard).first.size }.getOrDefault(0)
    }

    fun listThemes(context: Context): List<String> {
        val out = linkedSetOf<String>()
        for (shard in TacticsShard.values()) {
            // Prefer the new manifest because it is tiny and does not decode postings.
            runCatching { out += loadThemeIndexManifest(context, shard).keys }
                .onFailure {
                    // Legacy fallback: read names only from train_themes.bin, skipping postings.
                    runCatching { out += loadThemeNames(context, shard) }
                }
        }
        return out.sorted()
    }

    fun countMatches(
        context: Context,
        theme: String,
        minRating: Int?,
        maxRating: Int?
    ): Int {
        // Production fast path: difficulty is the shard band itself.
        // We no longer intersect with 25-Elo rating buckets here. The user-facing
        // planner uses Beginner/Easy/Medium/etc. bands; finer Pro bucket filtering
        // can return in a later update without touching cycle persistence.
        val token = normalizedThemeKey(theme)
        var total = 0

        for (shard in shardsForRange(minRating, maxRating)) {
            total += if (token.isBlank() || token == "all") {
                shardPuzzleCount(context, shard)
            } else {
                runCatching { loadThemeIndexIds(context, shard, token).size }
                    .getOrDefault(0)
            }
        }
        return total
    }

    fun samplePuzzleIds(
        context: Context,
        theme: String,
        limit: Int,
        minRating: Int?,
        maxRating: Int?
    ): List<Int> {
        if (limit <= 0) return emptyList()
        val token = normalizedThemeKey(theme)

        // Fast "All" path without rating buckets and without materializing every
        // local ID in a large shard. We only need shard counts, then choose a
        // random local row number inside the chosen shard.
        if (token.isBlank() || token == "all") {
            val sources = ArrayList<Pair<TacticsShard, Int>>()
            val prefix = ArrayList<Int>()
            var total = 0

            for (shard in shardsForRange(minRating, maxRating)) {
                val count = shardPuzzleCount(context, shard)
                if (count > 0) {
                    sources += shard to count
                    total += count
                    prefix += total
                }
            }

            if (total <= 0) return emptyList()
            val want = min(limit, total)
            val out = ArrayList<Int>(want)
            val seen = HashSet<Int>(want * 2)
            var attempts = 0
            val maxAttempts = want * 35

            while (out.size < want && attempts < maxAttempts) {
                attempts++
                val r = Random.nextInt(total)
                var shardPos = 0
                while (shardPos < prefix.size && r >= prefix[shardPos]) shardPos++
                val (shard, count) = sources[shardPos.coerceIn(0, sources.lastIndex)]
                val encoded = encode(shard, Random.nextInt(count))
                if (seen.add(encoded)) out += encoded
            }

            // Defensive sequential fill for tiny shards or near-exhaustive requests.
            if (out.size < want) {
                for ((shard, count) in sources) {
                    for (local in 0 until count) {
                        val encoded = encode(shard, local)
                        if (seen.add(encoded)) {
                            out += encoded
                            if (out.size >= want) return out
                        }
                    }
                }
            }
            return out
        }

        // Theme path: theme index already contains only local row numbers with this
        // theme inside the selected shard band. No rating-bucket intersection.
        val perShard = ArrayList<Pair<TacticsShard, IntArray>>()
        var total = 0

        for (shard in shardsForRange(minRating, maxRating)) {
            val localIds = runCatching { loadThemeIndexIds(context, shard, token) }
                .getOrDefault(IntArray(0))
            if (localIds.isNotEmpty()) {
                perShard += shard to localIds
                total += localIds.size
            }
        }

        if (total <= 0) return emptyList()
        val want = min(limit, total)
        val out = ArrayList<Int>(want)
        val seen = HashSet<Int>(want * 2)
        val prefix = IntArray(perShard.size)
        var acc = 0
        for (i in perShard.indices) {
            acc += perShard[i].second.size
            prefix[i] = acc
        }

        var attempts = 0
        val maxAttempts = want * 35
        while (out.size < want && attempts < maxAttempts) {
            attempts++
            val r = Random.nextInt(total)
            var shardPos = 0
            while (shardPos < prefix.size && r >= prefix[shardPos]) shardPos++
            val (shard, ids) = perShard[shardPos.coerceIn(0, perShard.lastIndex)]
            val local = ids[Random.nextInt(ids.size)]
            val encoded = encode(shard, local)
            if (seen.add(encoded)) out += encoded
        }

        // Defensive fill for small themes where repeated random picks hit duplicates.
        if (out.size < want) {
            for ((shard, ids) in perShard) {
                for (local in ids) {
                    val encoded = encode(shard, local)
                    if (seen.add(encoded)) {
                        out += encoded
                        if (out.size >= want) return out
                    }
                }
            }
        }

        return out
    }

    fun loadGames(context: Context, encodedIds: List<Int>): List<PgnGameInfo> {
        if (encodedIds.isEmpty()) return emptyList()
        val out = ArrayList<PgnGameInfo>(encodedIds.size)

        for (encoded in encodedIds) {
            val starterCached = runCatching {
                loadStarterCachedPgn(context, encoded)?.let { starterPgnToGameInfo(context, it) }
            }.getOrNull()
            if (starterCached != null) {
                out += starterCached
                continue
            }
            // Cold-start fast path: if this puzzle was already decoded in a previous app session,
            // rebuild the PgnGameInfo from the tiny cached PGN instead of opening the large binary shard.
            val cached = runCatching {
                loadCachedPuzzlePgn(context, encoded)?.let { cachedPgnToGameInfo(context, it) }
            }.onFailure {
                Log.w("TacticsBinaryBank", "Cached puzzle PGN failed for $encoded; falling back to binary", it)
            }.getOrNull()

            if (cached != null) {
                out += cached
                continue
            }

            val key = decode(encoded) ?: continue
            val rec = runCatching { readRecord(context, key) }
                .onFailure { Log.e("TacticsBinaryBank", "Failed to read puzzle $encoded", it) }
                .getOrNull()
                ?: continue

            val pgn = buildPgn(rec)
            val info = pgnToGameInfo(
                context = context,
                pgn = pgn,
                fallbackFen = rec.fen,
                fallbackEvent = rec.puzzleId,
                fallbackTheme = rec.primaryTheme,
                fallbackRating = rec.rating,
                fallbackNote = rec.shard.label
            ) ?: continue

            cachePuzzlePgn(context, encoded, pgn)
            out += info
        }

        return out
    }


    fun readRecord(context: Context, key: TacticsPuzzleKey): TacticsBinRecord {
        val (offsets, lengths) = loadOffsets(context, key.shard)
        if (key.localIndex !in offsets.indices) error("Puzzle index ${key.localIndex} out of range for ${key.shard.folder}")
        val puzzleFile = ensureAssetCopied(context, key.shard, "train_puzzles.bin")
        val themeNames = loadThemeNames(context, key.shard)
        RandomAccessFile(puzzleFile, "r").use { raf ->
            raf.seek(offsets[key.localIndex].toLong())
            val bytes = ByteArray(lengths[key.localIndex])
            raf.readFully(bytes)
            return parseRecord(bytes, key, themeNames)
        }
    }

    private fun recordToPgnGameInfo(context: Context, rec: TacticsBinRecord): PgnGameInfo? {
        val pgn = buildPgn(rec)
        return pgnToGameInfo(
            context = context,
            pgn = pgn,
            fallbackFen = rec.fen,
            fallbackEvent = rec.puzzleId,
            fallbackTheme = rec.primaryTheme,
            fallbackRating = rec.rating,
            fallbackNote = rec.shard.label
        )
    }

    private fun cachedPgnToGameInfo(context: Context, pgn: String): PgnGameInfo? =
        pgnToGameInfo(
            context = context,
            pgn = pgn,
            fallbackFen = null,
            fallbackEvent = "",
            fallbackTheme = "",
            fallbackRating = null,
            fallbackNote = "Cached puzzle"
        )

    private fun pgnToGameInfo(
        context: Context,
        pgn: String,
        fallbackFen: String?,
        fallbackEvent: String,
        fallbackTheme: String,
        fallbackRating: Int?,
        fallbackNote: String
    ): PgnGameInfo? {
        val headers = parsePgnHeaders(pgn)
        val tmp = File.createTempFile("tf_bin_puzzle_", ".pgn", context.cacheDir)
        return try {
            tmp.writeText(pgn, Charsets.UTF_8)
            val holder = PgnHolder(tmp.absolutePath)
            holder.loadPgn()
            val game: Game = holder.games.firstOrNull() ?: return null

            val event = headers["LichessID"]
                ?.takeIf { it.isNotBlank() }
                ?: headers["Event"]
                    ?.takeIf { it.isNotBlank() }
                ?: fallbackEvent

            PgnGameInfo(
                white = headers["White"].orEmpty(),
                black = headers["Black"].orEmpty(),
                startFen = headers["FEN"]?.takeIf { it.isNotBlank() } ?: fallbackFen,
                game = game,
                event = event,
                theme = headers["Theme"]?.takeIf { it.isNotBlank() } ?: fallbackTheme,
                rating = headers["Rating"]?.toIntOrNull() ?: fallbackRating,
                site = headers["Site"]?.takeIf { it.isNotBlank() } ?: "Lichess Puzzle Database",
                note = fallbackNote
            )
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun parsePgnHeaders(pgn: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val re = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+"([^"]*)"]\s*$""")
        for (m in re.findAll(pgn)) {
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }


    private fun loadStarterIndex(context: Context, shard: TacticsShard): JSONObject {
        starterIndexCache[shard]?.let { return it }
        val file = ensureAssetCopied(context, shard, STARTER_INDEX_NAME)
        val root = JSONObject(file.readText(Charsets.UTF_8))
        starterIndexCache[shard] = root
        return root
    }

    private fun starterPgnToGameInfo(
        context: Context,
        pgn: String,
        fallbackTheme: String = ""
    ): PgnGameInfo? {
        val headers = parseStarterPgnHeaders(pgn)
        val tmp = File.createTempFile("tf_starter_puzzle_", ".pgn", context.cacheDir)
        return try {
            tmp.writeText(pgn, Charsets.UTF_8)
            val holder = PgnHolder(tmp.absolutePath)
            holder.loadPgn()
            val game: Game = holder.games.firstOrNull() ?: return null
            PgnGameInfo(
                white = headers["White"].orEmpty(),
                black = headers["Black"].orEmpty(),
                startFen = headers["FEN"]?.takeIf { it.isNotBlank() },
                game = game,
                event = headers["LichessID"]?.takeIf { it.isNotBlank() }
                    ?: headers["Event"].orEmpty(),
                theme = headers["Theme"]?.takeIf { it.isNotBlank() } ?: fallbackTheme,
                rating = headers["Rating"]?.toIntOrNull(),
                site = headers["Site"]?.takeIf { it.isNotBlank() } ?: "Lichess Puzzle Database",
                note = "Starter puzzle"
            )
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun parseStarterPgnHeaders(pgn: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val re = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+\"([^\"]*)"]\s*$""")
        for (m in re.findAll(pgn)) {
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    private fun loadStarterCachedPgn(context: Context, encoded: Int): String? {
        val sp = context.getSharedPreferences("tf_starter_puzzle_pgn_cache_v1", Context.MODE_PRIVATE)
        val pgn = sp.getString("pgn_$encoded", null)?.takeIf { it.isNotBlank() } ?: return null
        return if (pgn.startsWith("[Event")) pgn else null
    }

    private fun cacheStarterPuzzlePgn(context: Context, encoded: Int, pgn: String) {
        if (!pgn.startsWith("[Event") || pgn.length > 24_000) return
        context.getSharedPreferences("tf_starter_puzzle_pgn_cache_v1", Context.MODE_PRIVATE)
            .edit()
            .putString("pgn_$encoded", pgn)
            .apply()
    }

    private fun loadCachedPuzzlePgn(context: Context, encoded: Int): String? {
        val sp = context.getSharedPreferences(PGN_CACHE_PREFS, Context.MODE_PRIVATE)
        val pgn = sp.getString("pgn_$encoded", null)?.takeIf { it.isNotBlank() } ?: return null
        return if (pgn.startsWith("[Event")) pgn else null
    }

    private fun cachePuzzlePgn(context: Context, encoded: Int, pgn: String) {
        if (pgn.isBlank()) return
        // Safety guard: puzzle PGNs are tiny. Do not let corrupt/huge text enter SharedPreferences.
        if (pgn.length > 24_000) return

        val sp = context.getSharedPreferences(PGN_CACHE_PREFS, Context.MODE_PRIVATE)
        val key = encoded.toString()
        val oldOrder = sp.getString(PGN_CACHE_ORDER, "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val newOrder = (listOf(key) + oldOrder.filterNot { it == key })
            .take(PGN_CACHE_MAX)

        val keep = newOrder.toSet()
        val edit = sp.edit()
            .putString("pgn_$key", pgn)
            .putString(PGN_CACHE_ORDER, newOrder.joinToString(","))

        for (old in oldOrder) {
            if (old !in keep) edit.remove("pgn_$old")
        }
        edit.apply()
    }


    private fun buildPgn(rec: TacticsBinRecord): String {
        val sb = StringBuilder()
        sb.appendLine("[Event \"${tagSafe(rec.puzzleId)}\"]")
        sb.appendLine("[Site \"Lichess Puzzle Database\"]")
        sb.appendLine("[Date \"????.??.??\"]")
        sb.appendLine("[Round \"-\"]")
        sb.appendLine("[White \"\"]")
        sb.appendLine("[Black \"\"]")
        sb.appendLine("[Result \"*\"]")
        sb.appendLine("[SetUp \"1\"]")
        sb.appendLine("[FEN \"${tagSafe(rec.fen)}\"]")
        if (rec.primaryTheme.isNotBlank()) sb.appendLine("[Theme \"${tagSafe(rec.primaryTheme)}\"]")
        sb.appendLine("[Rating \"${rec.rating}\"]")
        sb.appendLine("[LichessID \"${tagSafe(rec.puzzleId)}\"]")
        sb.appendLine()
        sb.appendLine(numberUciFromFen(rec.fen, rec.uciMoves))
        return sb.toString()
    }

    private fun tagSafe(s: String): String = s.replace('"', '\'').replace("\n", " ").trim()

    private fun numberUciFromFen(fen: String, moves: List<String>): String {
        val parts = fen.trim().split(Regex("\\s+"))
        var side = parts.getOrNull(1)?.lowercase(Locale.ROOT) ?: "w"
        var moveNo = parts.getOrNull(5)?.toIntOrNull() ?: 1
        val out = ArrayList<String>(moves.size * 2 + 4)
        var i = 0
        while (i < moves.size) {
            if (side == "w") {
                out += "$moveNo."
                out += moves[i]
                side = "b"
                i++
            } else {
                out += "$moveNo..."
                out += moves[i]
                side = "w"
                moveNo++
                i++
            }
        }
        out += "*"
        return out.joinToString(" ")
    }

    private fun parseRecord(bytes: ByteArray, key: TacticsPuzzleKey, themeNames: List<String>): TacticsBinRecord {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rating = bb.uShort()
        val primaryThemeId = bb.uShort()
        val moveCount = bb.uByte()
        bb.get() // reserved
        val idLen = bb.uShort()
        val fenLen = bb.uShort()
        val idBytes = ByteArray(idLen)
        bb.get(idBytes)
        val fenBytes = ByteArray(fenLen)
        bb.get(fenBytes)
        val moves = ArrayList<String>(moveCount)
        repeat(moveCount) { moves += unpackUci(bb.uShort()) }
        val themeName = if (primaryThemeId == NO_THEME_ID) "" else themeNames.getOrNull(primaryThemeId).orEmpty()
        return TacticsBinRecord(
            encodedId = key.encoded,
            localIndex = key.localIndex,
            puzzleId = idBytes.toString(Charsets.UTF_8),
            rating = rating,
            fen = fenBytes.toString(Charsets.UTF_8),
            uciMoves = moves,
            primaryTheme = themeName,
            shard = key.shard
        )
    }

    private fun unpackUci(packed: Int): String {
        val from = packed and 0x3F
        val to = (packed ushr 6) and 0x3F
        val promo = (packed ushr 12) and 0x07
        fun sq(v: Int): String {
            val file = (v % 8)
            val rank = (v / 8) + 1
            return "${('a'.code + file).toChar()}$rank"
        }
        val p = when (promo) {
            1 -> "q"
            2 -> "r"
            3 -> "b"
            4 -> "n"
            else -> ""
        }
        return sq(from) + sq(to) + p
    }

    private fun loadOffsets(context: Context, shard: TacticsShard): Pair<IntArray, IntArray> {
        offsetsCache[shard]?.let { return it }
        val file = ensureAssetCopied(context, shard, "train_puzzle_offsets.bin")
        RandomAccessFile(file, "r").use { raf ->
            require(raf.readMagic() == MAGIC_OFFSETS) { "Bad offsets magic for ${shard.folder}" }
            val version = raf.readIntLE()
            require(version == TACTICS_BIN_VERSION) { "Unsupported offsets version $version" }
            val count = raf.readIntLE()
            val offsets = IntArray(count)
            val lengths = IntArray(count)
            for (i in 0 until count) {
                offsets[i] = raf.readIntLE()
                lengths[i] = raf.readUShortLE()
            }
            val pair = offsets to lengths
            offsetsCache[shard] = pair
            return pair
        }
    }

    private fun shardPuzzleCount(context: Context, shard: TacticsShard): Int =
        countPuzzleCountFromIndex(context, shard)
            ?: runCatching { loadOffsets(context, shard).first.size }.getOrDefault(0)

    private fun loadBuckets(context: Context, shard: TacticsShard): List<TacticsBinRatingBucket> {
        bucketsCache[shard]?.let { return it }
        val file = ensureAssetCopied(context, shard, "train_rating_buckets.bin")
        RandomAccessFile(file, "r").use { raf ->
            require(raf.readMagic() == MAGIC_BUCKETS) { "Bad buckets magic for ${shard.folder}" }
            val version = raf.readIntLE()
            require(version == TACTICS_BIN_VERSION) { "Unsupported buckets version $version" }
            val count = raf.readIntLE()
            val out = ArrayList<TacticsBinRatingBucket>(count)
            repeat(count) {
                out += TacticsBinRatingBucket(
                    floor = raf.readUShortLE(),
                    startIdx = raf.readIntLE(),
                    endIdx = raf.readIntLE()
                )
            }
            val list = out.sortedBy { it.floor }
            bucketsCache[shard] = list
            return list
        }
    }

    private fun loadThemeNames(context: Context, shard: TacticsShard): List<String> {
        themeNamesCache[shard]?.let { return it }

        // Read only theme names from the legacy combined train_themes.bin.
        // Do not decode posting lists here; that was the hidden one-puzzle slowdown.
        val file = ensureAssetCopied(context, shard, "train_themes.bin")
        RandomAccessFile(file, "r").use { raf ->
            require(raf.readMagic() == MAGIC_THEMES) { "Bad themes magic for ${shard.folder}" }
            val version = raf.readIntLE()
            require(version == TACTICS_BIN_VERSION) { "Unsupported themes version $version" }
            val count = raf.readIntLE()
            val names = ArrayList<String>(count)
            repeat(count) {
                val nameLen = raf.readUShortLE()
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)
                val name = nameBytes.toString(Charsets.UTF_8).lowercase(Locale.ROOT)
                raf.readIntLE() // posting count
                val encodedLen = raf.readIntLE()
                if (encodedLen > 0) raf.seek(raf.filePointer + encodedLen)
                names += name
            }
            themeNamesCache[shard] = names
            return names
        }
    }

    private fun normalizedThemeKey(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun ratingFloor25(rating: Int): Int = (rating / 25) * 25

    private fun countPuzzleCountFromIndex(context: Context, shard: TacticsShard): Int? =
        runCatching {
            val value = loadCountIndex(context, shard).optInt("puzzleCount", -1)
            value.takeIf { it >= 0 }
        }.getOrNull()

    private fun countFromPrecomputedIndex(
        context: Context,
        shard: TacticsShard,
        token: String,
        minRating: Int?,
        maxRating: Int?
    ): Int? {
        val root = runCatching { loadCountIndex(context, shard) }.getOrNull() ?: return null
        val byFloor = if (token.isBlank() || token == "all") {
            root.optJSONObject("allByFloor")
        } else {
            root.optJSONObject("themes")
                ?.optJSONObject(token)
                ?.optJSONObject("byFloor")
        }
        return countFromFloorObject(byFloor, minRating, maxRating)
    }

    private fun countFromFloorObject(
        byFloor: JSONObject?,
        minRating: Int?,
        maxRating: Int?
    ): Int? {
        if (byFloor == null) return null
        val floorMin = minRating?.let { ratingFloor25(it) }
        val floorMax = maxRating?.let { ratingFloor25(it) }
        var total = 0
        val keys = byFloor.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val floor = key.toIntOrNull() ?: continue
            if ((floorMin == null || floor >= floorMin) &&
                (floorMax == null || floor <= floorMax)
            ) {
                total += byFloor.optInt(key, 0)
            }
        }
        return total
    }

    private fun loadCountIndex(context: Context, shard: TacticsShard): JSONObject {
        countIndexCache[shard]?.let { return it }
        val file = ensureAssetCopied(context, shard, "train_counts.json")
        val root = JSONObject(file.readText(Charsets.UTF_8))
        countIndexCache[shard] = root
        return root
    }

    private fun loadThemeIndexManifest(context: Context, shard: TacticsShard): Map<String, String> {
        themeIndexManifestCache[shard]?.let { return it }

        val file = ensureAssetCopied(context, shard, "train_theme_index_manifest.json")
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val themes = root.getJSONObject("themes")
        val out = HashMap<String, String>(themes.length())
        val keys = themes.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val entry = themes.getJSONObject(rawKey)
            val key = normalizedThemeKey(rawKey)
            val rel = entry.optString("file", "theme_indexes/$key.bin")
            if (key.isNotBlank() && rel.isNotBlank()) out[key] = rel
        }
        themeIndexManifestCache[shard] = out
        return out
    }

    private fun loadThemeIndexIds(context: Context, shard: TacticsShard, rawTheme: String): IntArray {
        val token = normalizedThemeKey(rawTheme)
        if (token.isBlank() || token == "all") return IntArray(0)

        val cacheKey = "${shard.folder}:$token"
        themeIndexCache[cacheKey]?.let { return it }

        val relFile = runCatching {
            loadThemeIndexManifest(context, shard)[token]
        }.getOrNull() ?: "theme_indexes/$token.bin"

        val ids = runCatching {
            val file = ensureAssetCopied(context, shard, relFile)
            RandomAccessFile(file, "r").use { raf ->
                require(raf.readMagic() == MAGIC_THEME_INDEX) { "Bad theme-index magic for ${shard.folder}/$token" }
                val version = raf.readIntLE()
                require(version == TACTICS_BIN_VERSION) { "Unsupported theme-index version $version" }
                val count = raf.readIntLE()
                IntArray(count) { raf.readIntLE() }
            }
        }.getOrElse { err ->
            // Legacy safety fallback only. New assets should use per-theme files.
            Log.w("TacticsBinaryBank", "Falling back to combined train_themes.bin for ${shard.folder}/$token", err)
            loadThemes(context, shard)[token] ?: IntArray(0)
        }

        themeIndexCache[cacheKey] = ids
        return ids
    }


    private fun loadThemes(context: Context, shard: TacticsShard): Map<String, IntArray> {
        themesCache[shard]?.let { return it }
        val file = ensureAssetCopied(context, shard, "train_themes.bin")
        RandomAccessFile(file, "r").use { raf ->
            require(raf.readMagic() == MAGIC_THEMES) { "Bad themes magic for ${shard.folder}" }
            val version = raf.readIntLE()
            require(version == TACTICS_BIN_VERSION) { "Unsupported themes version $version" }
            val count = raf.readIntLE()
            val names = ArrayList<String>(count)
            val map = HashMap<String, IntArray>(count)
            repeat(count) {
                val nameLen = raf.readUShortLE()
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)
                val name = nameBytes.toString(Charsets.UTF_8).lowercase(Locale.ROOT)
                val postingCount = raf.readIntLE()
                val encodedLen = raf.readIntLE()
                val encoded = ByteArray(encodedLen)
                raf.readFully(encoded)
                val postings = decodeDeltaVarints(encoded, postingCount)
                names += name
                map[name] = postings
            }
            themeNamesCache[shard] = names
            themesCache[shard] = map
            return map
        }
    }


    private fun RandomAccessFile.skipFullyByteCount(byteCount: Int) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skipBytes(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (read() < 0) throw java.io.EOFException()
                remaining -= 1
            }
        }
    }

    private fun decodeDeltaVarints(bytes: ByteArray, count: Int): IntArray {
        val out = IntArray(count)
        var pos = 0
        var prev = 0
        for (i in 0 until count) {
            var shift = 0
            var value = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                value = value or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            val actual = if (i == 0) value else prev + value
            out[i] = actual
            prev = actual
        }
        return out
    }

    private fun shardsForRange(minRating: Int?, maxRating: Int?): List<TacticsShard> =
        TacticsShard.values().filter { shard ->
            val lo = minRating ?: Int.MIN_VALUE
            val hi = maxRating ?: Int.MAX_VALUE
            shard.maxRating >= lo && shard.minRating <= hi
        }

    private fun matchingRanges(
        buckets: List<TacticsBinRatingBucket>,
        minRating: Int?,
        maxRating: Int?
    ): List<IntRange> {
        fun floor25(r: Int) = (r / 25) * 25
        val bMin = minRating?.let(::floor25)
        val bMax = maxRating?.let(::floor25)
        return buckets.filter { b ->
            (bMin == null || b.floor >= bMin) && (bMax == null || b.floor <= bMax)
        }.map { it.startIdx..it.endIdx }
    }

    private fun allIdsFromBuckets(
        buckets: List<TacticsBinRatingBucket>,
        minRating: Int?,
        maxRating: Int?
    ): IntArray {
        val ranges = matchingRanges(buckets, minRating, maxRating)
        val total = ranges.sumOf { (it.last - it.first + 1).coerceAtLeast(0) }
        val out = IntArray(total)
        var p = 0
        for (r in ranges) for (v in r) out[p++] = v
        return out
    }

    private fun idsInRanges(ids: IntArray, ranges: List<IntRange>): IntArray {
        if (ids.isEmpty() || ranges.isEmpty()) return IntArray(0)
        val out = ArrayList<Int>()
        var i = 0
        var j = 0
        while (i < ids.size && j < ranges.size) {
            val id = ids[i]
            val r = ranges[j]
            when {
                id < r.first -> i++
                id > r.last -> j++
                else -> { out += id; i++ }
            }
        }
        return out.toIntArray()
    }

    private fun countIdsInRanges(ids: IntArray, ranges: List<IntRange>): Int = idsInRanges(ids, ranges).size

    private fun ensureAssetCopied(context: Context, shard: TacticsShard, fileName: String): File {
        val dir = File(context.noBackupFilesDir, "puzzle_bins_v1/${shard.folder}")
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, fileName)
        out.parentFile?.mkdirs()
        if (out.exists() && out.length() > 0L) return out

        val assetPath = "puzzles/${shard.folder}/$fileName"
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun RandomAccessFile.readMagic(): String {
        val bytes = ByteArray(8)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if ((b0 or b1 or b2 or b3) < 0) throw java.io.EOFException()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
    }

    private fun RandomAccessFile.readUShortLE(): Int {
        val b0 = read()
        val b1 = read()
        if ((b0 or b1) < 0) throw java.io.EOFException()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
    }

    private fun ByteBuffer.uByte(): Int = get().toInt() and 0xFF
    private fun ByteBuffer.uShort(): Int = short.toInt() and 0xFFFF
}
