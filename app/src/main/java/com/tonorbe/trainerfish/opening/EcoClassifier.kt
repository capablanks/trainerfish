// app/src/main/java/com/tonorbe/trainerfish/opening/EcoClassifier.kt
package com.tonorbe.trainerfish.opening

import android.content.Context
import android.util.JsonReader
import com.tonorbe.trainerfish.R
import java.io.BufferedInputStream
import java.io.InputStreamReader

/**
 * One ECO entry.
 *
 * @param code ECO code, e.g. "D10"
 * @param name Human-readable name, e.g. "Slav Defense"
 * @param pgn  Canonical line (may be null if using format=1 JSON)
 */
data class EcoEntry(
    val code: String,
    val name: String,
    val pgn: String? = null,
    val fen: String? = null
)


object EcoClassifier {

    @Volatile
    private var loaded = false

    /** FEN (first 4 fields) -> ECO entry. */
    private val byFen: MutableMap<String, EcoEntry> = HashMap()

    /** ECO code (e.g. "D10") -> representative ECO entry. */
    private val byCode: MutableMap<String, EcoEntry> = HashMap()

    /** All entries (for UI lists / search). */
    private val allEntriesInternal: MutableList<EcoEntry> = ArrayList()

    /** Normalize a FEN to the same 4-field form used in eco_book.json. */
    private fun fenKey(fen: String): String {
        val parts = fen.trim().split(Regex("\\s+"))
        return if (parts.size >= 4) {
            parts.subList(0, 4).joinToString(" ")
        } else {
            fen.trim()
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return

        synchronized(this) {
            if (loaded) return

            context.resources.openRawResource(R.raw.eco_book).use { ins ->
                val bis = BufferedInputStream(ins)
                JsonReader(InputStreamReader(bis, Charsets.UTF_8)).use { jr ->
                    jr.beginObject()

                    var formatVersion = 1

                    while (jr.hasNext()) {
                        when (jr.nextName()) {
                            "format" -> {
                                // 1 -> [code, name, fen]
                                // 2 -> [code, name, pgn, fen]
                                formatVersion = try {
                                    jr.nextInt()
                                } catch (_: Throwable) {
                                    // If something weird, just skip
                                    1
                                }
                            }

                            "entries" -> {
                                jr.beginArray()
                                while (jr.hasNext()) {
                                    jr.beginArray()

                                    val code = safeNextString(jr) ?: ""
                                    val name = safeNextString(jr) ?: ""

                                    var pgn: String? = null
                                    val fen: String

                                    if (formatVersion <= 1) {
                                        // Old format: [code, name, fen]
                                        fen = safeNextString(jr) ?: ""
                                    } else {
                                        // New format: [code, name, pgn, fen]
                                        pgn = safeNextString(jr)
                                        fen = safeNextString(jr) ?: ""
                                    }

                                    jr.endArray()

                                    if (code.isBlank() || fen.isBlank()) {
                                        continue
                                    }

                                    val entry = EcoEntry(code, name, pgn, fen)


                                    // FEN -> ECO
                                    val key = fenKey(fen)
                                    if (!byFen.containsKey(key)) {
                                        byFen[key] = entry
                                    }

                                    // ECO code -> first representative entry
                                    if (!byCode.containsKey(code)) {
                                        byCode[code] = entry
                                    }

                                    allEntriesInternal.add(entry)
                                }
                                jr.endArray()
                            }

                            else -> jr.skipValue()
                        }
                    }

                    jr.endObject()
                }
            }

            // Sort once for stable UI lists (by code then name)
            allEntriesInternal.sortWith(
                compareBy<EcoEntry> { it.code }.thenBy { it.name }
            )

            loaded = true
        }
    }

    private fun safeNextString(jr: JsonReader): String? =
        try {
            jr.nextString()
        } catch (_: Throwable) {
            null
        }

    /** Look up ECO for the given FEN (may return null if unknown). */
    fun classify(context: Context, fen: String): EcoEntry? {
        ensureLoaded(context)
        return byFen[fenKey(fen)]
    }

    /** Look up ECO entry directly by its code, e.g. "D10". */
    fun fromCode(context: Context, ecoCode: String): EcoEntry? {
        ensureLoaded(context)
        return byCode[ecoCode]
    }

    /** Return all ECO entries (for ECO browser UI). */
    fun getAllEntries(context: Context): List<EcoEntry> {
        ensureLoaded(context)
        // defensive copy so callers can't mutate internal list
        return ArrayList(allEntriesInternal)
    }
}
