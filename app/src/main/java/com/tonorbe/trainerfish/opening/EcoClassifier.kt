// app/src/main/java/com/example/puzzles/opening/EcoClassifier.kt
package com.tonorbe.trainerfish.opening

import android.content.Context
import android.util.JsonReader
import com.tonorbe.trainerfish.R
import java.io.BufferedInputStream
import java.io.InputStreamReader

data class EcoEntry(val code: String, val name: String)

object EcoClassifier {
    @Volatile
    private var loaded = false
    private val byFen: MutableMap<String, EcoEntry> = HashMap()

    /** Normalize FEN to the same 4-field key used by your Python script. */
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
                    while (jr.hasNext()) {
                        when (jr.nextName()) {
                            "format" -> {
                                // currently unused, but we can evolve the file format later
                                try { jr.nextInt() } catch (_: Throwable) { jr.skipValue() }
                            }
                            "entries" -> {
                                jr.beginArray()
                                while (jr.hasNext()) {
                                    jr.beginArray()
                                    val code = safeNextString(jr) ?: ""
                                    val name = safeNextString(jr) ?: ""
                                    val fen  = safeNextString(jr) ?: ""
                                    jr.endArray()
                                    if (code.isNotBlank() && fen.isNotBlank()) {
                                        byFen[fenKey(fen)] = EcoEntry(code, name)
                                    }
                                }
                                jr.endArray()
                            }
                            else -> jr.skipValue()
                        }
                    }
                    jr.endObject()
                }
            }

            loaded = true
        }
    }

    private fun safeNextString(jr: JsonReader): String? =
        try { jr.nextString() } catch (_: Throwable) { null }

    /** Look up ECO for the given FEN (may return null if unknown). */
    fun classify(context: Context, fen: String): EcoEntry? {
        ensureLoaded(context)
        return byFen[fenKey(fen)]
    }
}
