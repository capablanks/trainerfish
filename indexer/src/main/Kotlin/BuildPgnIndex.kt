import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

private data class GameIdx(
    val idx: Int,
    val start: Int,
    val end: Int,
    val rating: Int?,
    val theme: String?,
    val white: String,
    val black: String,
    val event: String,
    val fen: String?
)

private data class Bucket(
    val floor: Int,
    var startIdx: Int,
    var endIdx: Int,
    var startByte: Int,
    var endByte: Int
)

private val TAG = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+"([^"]*)"]\s*$""")
private val EVENT_SIG = "[Event".toByteArray(UTF_8)

fun main(args: Array<String>) {
    if (args.size != 4) {
        System.err.println("Usage: BuildPgnIndex <in.pgn> <out_idx.json> <out_buckets.json> <out_themes.json>")
        return
    }

    val inFile     = File(args[0])
    val outIdx     = File(args[1]).also { it.parentFile?.mkdirs() }
    val outBuckets = File(args[2]).also { it.parentFile?.mkdirs() }
    val outThemes  = File(args[3]).also { it.parentFile?.mkdirs() }

    val bytes = inFile.readBytes()
    val n = bytes.size

    // 1) Find all game starts by scanning for the literal "[Event"
    val starts = ArrayList<Int>()
    var i = 0
    while (i + EVENT_SIG.size <= n) {
        var ok = true
        for (k in EVENT_SIG.indices) {
            if (bytes[i + k] != EVENT_SIG[k]) { ok = false; break }
        }
        if (ok) starts += i
        i++
    }
    if (starts.isEmpty()) error("No [Event found in PGN")

    // 2) Per-game index (parse headers only)
    val entries = ArrayList<GameIdx>(starts.size)
    // Also collect theme -> ids (tokenized, lowercased)
    val themeMap = HashMap<String, MutableList<Int>>()  // token -> [ids]

    fun addThemeTokens(id: Int, raw: String?) {
        if (raw.isNullOrBlank()) return
        raw.lowercase().split(Regex("[,;\\s]+")).forEach { t ->
            val token = t.trim()
            if (token.isNotEmpty()) themeMap.getOrPut(token) { mutableListOf() }.add(id)
        }
    }

    for (g in starts.indices) {
        val s = starts[g]
        val e = if (g + 1 < starts.size) starts[g + 1] else n
        val chunk = String(bytes, s, e - s, UTF_8)

        var rating: Int? = null
        var theme: String? = null
        var white = ""; var black = ""; var event = ""; var fen: String? = null

        for (m in TAG.findAll(chunk)) {
            when (m.groupValues[1].lowercase()) {
                "rating" -> rating = m.groupValues[2].toIntOrNull()
                "theme"  -> theme  = m.groupValues[2]
                "white"  -> white  = m.groupValues[2]
                "black"  -> black  = m.groupValues[2]
                "event"  -> event  = m.groupValues[2]
                "fen"    -> fen    = m.groupValues[2]
            }
        }

        entries += GameIdx(g, s, e, rating, theme, white, black, event, fen)
        addThemeTokens(g, theme)
    }

    // 3) 25-pt rating buckets (PGN assumed sorted by rating)
    fun floor25(r: Int) = (r / 25) * 25
    val buckets = ArrayList<Bucket>()
    var cur: Bucket? = null
    for (e in entries) {
        val r = e.rating ?: continue
        val f = floor25(r)
        if (cur == null || cur!!.floor != f) {
            cur = Bucket(f, e.idx, e.idx, e.start, e.end)
            buckets += cur!!
        } else {
            cur!!.endIdx = e.idx
            cur!!.endByte = e.end
        }
    }

    // 4) Write files
    outIdx.writeText(toIdxJson(entries), UTF_8)
    outBuckets.writeText(toBucketJson(buckets), UTF_8)
    outThemes.writeText(toThemesJson(themeMap), UTF_8)

    println("Indexed ${entries.size} games ->")
    println("  ${outIdx.absolutePath}")
    println("  ${outBuckets.absolutePath}")
    println("  ${outThemes.absolutePath}")
}

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

private fun toIdxJson(list: List<GameIdx>): String =
    buildString {
        append('[')
        list.forEachIndexed { i, e ->
            if (i>0) append(',')
            append("{\"idx\":").append(e.idx)
            append(",\"start\":").append(e.start)
            append(",\"end\":").append(e.end)
            append(",\"rating\":").append(e.rating?.toString() ?: "null")
            append(",\"theme\":").append(e.theme?.let { "\"${esc(it)}\"" } ?: "null")
            append(",\"white\":\"").append(esc(e.white)).append('"')
            append(",\"black\":\"").append(esc(e.black)).append('"')
            append(",\"event\":\"").append(esc(e.event)).append('"')
            append(",\"fen\":").append(e.fen?.let { "\"${esc(it)}\"" } ?: "null")
            append('}')
        }
        append(']')
    }

private fun toBucketJson(list: List<Bucket>): String =
    buildString {
        append('[')
        list.forEachIndexed { i, b ->
            if (i>0) append(',')
            append("{\"floor\":").append(b.floor)
            append(",\"startIdx\":").append(b.startIdx)
            append(",\"endIdx\":").append(b.endIdx)
            append(",\"startByte\":").append(b.startByte)
            append(",\"endByte\":").append(b.endByte)
            append('}')
        }
        append(']')
    }

/** Compact themes table: { "fork":[1,7,10], "skewer":[2,9], ... } */
private fun toThemesJson(themes: Map<String, List<Int>>): String =
    buildString {
        append('{')
        var firstK = true
        for ((k, ids) in themes.entries.sortedBy { it.key }) {
            if (!firstK) append(',')
            firstK = false
            append('"').append(esc(k)).append('"').append(':')
            append('[')
            ids.forEachIndexed { i, id -> if (i>0) append(','); append(id) }
            append(']')
        }
        append('}')
    }
