package com.tonorbe.trainerfish

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.min

/**
 * Woodpecker persistence: cycle order/cursor, elapsed time, and per-puzzle stats
 * (best/last time, best/last points).
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
        // Clears all best-time / best-score entries.
        // If you store other fields here, remove only the keys you use for puzzle stats.
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
        val cursor = prefs.getInt(KEY_CURSOR, 0).coerceIn(0, (order.size - 1).coerceAtLeast(0))
        return if (order.isEmpty()) 0 else order[cursor]
    }

    fun nextIndex(): Int {
        val order = getOrder()
        val nextCursor = min(prefs.getInt(KEY_CURSOR, 0) + 1, (order.size - 1).coerceAtLeast(0))
        prefs.edit().putInt(KEY_CURSOR, nextCursor).apply()
        return if (order.isEmpty()) 0 else order[nextCursor]
    }

    fun isCycleComplete(): Boolean = solvedCount() >= total()

    // ----- Cycle stats -----

    fun solvedCount(): Int = prefs.getInt(KEY_SOLVED, 0)
    fun total(): Int = prefs.getInt(KEY_TOTAL, 0)
    fun cycleId(): Int = prefs.getInt(KEY_CYCLE, 1)
    fun elapsedMs(): Long = prefs.getLong(KEY_ELAPSED_MS, 0L)

    // ----- Per-puzzle stats -----

    fun markSolved(puzzleIdx: Int, ms: Long, earnedPoints: Int, totalPoints: Int) {
        val key = "p_$puzzleIdx"

        val bestTime = prefs.getLong("${key}_best_ms", Long.MAX_VALUE)
        val newBestTime = kotlin.math.min(bestTime, ms)

        val bestPts = prefs.getInt("${key}_best_pts", 0)
        val bestTot = prefs.getInt("${key}_best_tot", 0)
        val newBestPts: Int
        val newBestTot: Int
        // Improve best if percentage is higher, or if equal % but more points (edge equal totals)
        val prevPct = if (bestTot > 0) bestPts.toFloat() / bestTot else -1f
        val thisPct = if (totalPoints > 0) earnedPoints.toFloat() / totalPoints else -1f
        if (thisPct > prevPct || (thisPct == prevPct && earnedPoints > bestPts)) {
            newBestPts = earnedPoints
            newBestTot = totalPoints
        } else {
            newBestPts = bestPts
            newBestTot = bestTot
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
            .putInt(KEY_SOLVED, prefs.getInt(KEY_SOLVED, 0) + 1)
            .putLong(KEY_ELAPSED_MS, prefs.getLong(KEY_ELAPSED_MS, 0L) + ms)
            .apply()
    }

    fun puzzleBestMs(puzzleIdx: Int): Long {
        val v = prefs.getLong("p_${puzzleIdx}_best_ms", Long.MAX_VALUE)
        return if (v == Long.MAX_VALUE) 0L else v
    }

    fun puzzleLastMs(puzzleIdx: Int): Long = prefs.getLong("p_${puzzleIdx}_last_ms", 0L)

    fun puzzleBestPoints(puzzleIdx: Int): Pair<Int, Int> =
        prefs.getInt("p_${puzzleIdx}_best_pts", 0) to prefs.getInt("p_${puzzleIdx}_best_tot", 0)

    fun puzzleLastPoints(puzzleIdx: Int): Pair<Int, Int> =
        prefs.getInt("p_${puzzleIdx}_last_pts", 0) to prefs.getInt("p_${puzzleIdx}_last_tot", 0)

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
