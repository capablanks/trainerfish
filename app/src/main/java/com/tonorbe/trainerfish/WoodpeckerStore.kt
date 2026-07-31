package com.tonorbe.trainerfish

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.min

/**
 * Woodpecker persistence: cycle order/cursor, elapsed time, and per-puzzle stats
 * (best/last time, best/last points).
 *
 * Cycle navigation (order/cursor) remains index-based inside the current games list.
 * Per-puzzle stats are now keyed by a stable puzzle identifier (Lichess ID),
 * which we take from the PGN [Event "..."] tag in Woodpecker mode.
 */
class WoodpeckerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("woodpecker", Context.MODE_PRIVATE)

    // ----- Cycle lifecycle -----

    fun ensureCycle(totalPuzzles: Int) {
        val order = prefs.getString(KEY_ORDER, null)
        val total = prefs.getInt(KEY_TOTAL, 0)
        if (order == null || total != totalPuzzles) {
            startNewCycle(totalPuzzles)
        }
    }

    fun resetAll() {
        // Clears everything: cycle state + per-puzzle stats.
        // This is acceptable when we change the underlying dataset (e.g., new PGN).
        prefs.edit().clear().apply()
    }

    fun startNewCycle(totalPuzzles: Int) {
        val newCycle = prefs.getInt(KEY_CYCLE, 0) + 1
        val shuffled = (0 until totalPuzzles).shuffled()
        prefs.edit()
            .putInt(KEY_CYCLE, newCycle)
            .putString(KEY_ORDER, shuffled.joinToString(","))
            .putInt(KEY_CURSOR, 0)
            .putInt(KEY_SOLVED, 0)
            .putInt(KEY_TOTAL, totalPuzzles)
            .putLong(KEY_ELAPSED_MS, 0L)
            .apply()
    }

    // ----- Cycle navigation -----

    fun currentIndex(): Int {
        val order = getOrder()
        val cursor = prefs.getInt(KEY_CURSOR, 0)
            .coerceIn(0, (order.size - 1).coerceAtLeast(0))
        return if (order.isEmpty()) 0 else order[cursor]
    }

    fun nextIndex(): Int {
        val order = getOrder()
        val nextCursor = min(
            prefs.getInt(KEY_CURSOR, 0) + 1,
            (order.size - 1).coerceAtLeast(0)
        )
        prefs.edit().putInt(KEY_CURSOR, nextCursor).apply()
        return if (order.isEmpty()) 0 else order[nextCursor]
    }

    fun isCycleComplete(): Boolean = solvedCount() >= total()

    // ----- Cycle stats -----

    fun solvedCount(): Int = prefs.getInt(KEY_SOLVED, 0)
    fun total(): Int = prefs.getInt(KEY_TOTAL, 0)
    fun cycleId(): Int = prefs.getInt(KEY_CYCLE, 1)
    fun elapsedMs(): Long = prefs.getLong(KEY_ELAPSED_MS, 0L)

    // ----- Per-puzzle stats (keyed by Lichess ID) -----

    /**
     * Record a solved attempt for a given puzzle.
     *
     * @param puzzleId Stable identifier, e.g. Lichess puzzle ID,
     *                 taken from the PGN [Event "..."] tag in Woodpecker mode.
     */
    fun markSolved(puzzleId: String, ms: Long, earnedPoints: Int, totalPoints: Int) {
        if (puzzleId.isBlank()) return

        val key = "p_id_$puzzleId"

        val bestTime = prefs.getLong("${key}_best_ms", Long.MAX_VALUE)
        val newBestTime = kotlin.math.min(bestTime, ms)

        val bestPts = prefs.getInt("${key}_best_pts", 0)
        val bestTot = prefs.getInt("${key}_best_tot", 0)
        val prevPct = if (bestTot > 0) bestPts.toFloat() / bestTot else -1f
        val thisPct = if (totalPoints > 0) earnedPoints.toFloat() / totalPoints else -1f

        val (newBestPts, newBestTot) =
            if (thisPct > prevPct || (thisPct == prevPct && earnedPoints > bestPts)) {
                earnedPoints to totalPoints
            } else {
                bestPts to bestTot
            }

        prefs.edit()
            .putInt("${key}_attempts", prefs.getInt("${key}_attempts", 0) + 1)
            .putInt("${key}_solved", prefs.getInt("${key}_solved", 0) + 1)
            .putLong("${key}_best_ms", newBestTime)
            .putLong("${key}_last_ms", ms)
            .putInt("${key}_best_pts", newBestPts)
            .putInt("${key}_best_tot", newBestTot)
            .putInt("${key}_last_pts", earnedPoints)
            .putInt("${key}_last_tot", totalPoints)
            // global counters
            .putInt(KEY_SOLVED, prefs.getInt(KEY_SOLVED, 0) + 1)
            .putLong(KEY_ELAPSED_MS, prefs.getLong(KEY_ELAPSED_MS, 0L) + ms)
            .apply()
    }

    fun puzzleBestMs(puzzleId: String): Long {
        if (puzzleId.isBlank()) return 0L
        val key = "p_id_$puzzleId"
        val v = prefs.getLong("${key}_best_ms", Long.MAX_VALUE)
        return if (v == Long.MAX_VALUE) 0L else v
    }

    fun puzzleLastMs(puzzleId: String): Long {
        if (puzzleId.isBlank()) return 0L
        val key = "p_id_$puzzleId"
        return prefs.getLong("${key}_last_ms", 0L)
    }

    fun puzzleBestPoints(puzzleId: String): Pair<Int, Int> {
        if (puzzleId.isBlank()) return 0 to 0
        val key = "p_id_$puzzleId"
        val bestPts = prefs.getInt("${key}_best_pts", 0)
        val bestTot = prefs.getInt("${key}_best_tot", 0)
        return bestPts to bestTot
    }

    fun puzzleLastPoints(puzzleId: String): Pair<Int, Int> {
        if (puzzleId.isBlank()) return 0 to 0
        val key = "p_id_$puzzleId"
        val lastPts = prefs.getInt("${key}_last_pts", 0)
        val lastTot = prefs.getInt("${key}_last_tot", 0)
        return lastPts to lastTot
    }

    /**
     * Return the set of all puzzle IDs that have any recorded "best_ms" entry.
     * Used for global profile stats where we want to aggregate over "seen" puzzles
     * without needing access to the full PGN index list.
     */
    fun allPuzzleIdsWithRecords(): Set<String> {
        val result = mutableSetOf<String>()
        for (key in prefs.all.keys) {
            if (key.startsWith("p_id_") && key.endsWith("_best_ms")) {
                val id = key.removePrefix("p_id_").removeSuffix("_best_ms")
                if (id.isNotEmpty()) result += id
            }
        }
        return result
    }

    // ----- Helpers -----

    private fun getOrder(): List<Int> {
        val s = prefs.getString(KEY_ORDER, "") ?: ""
        if (s.isBlank()) return emptyList()
        return s.split(",").mapNotNull { it.toIntOrNull() }
    }

    companion object {
        private const val KEY_CYCLE = "cycle_id"
        private const val KEY_ORDER = "order"
        private const val KEY_CURSOR = "cursor"
        private const val KEY_SOLVED = "solved"
        private const val KEY_TOTAL = "total"
        private const val KEY_ELAPSED_MS = "elapsed_ms"
    }
}
