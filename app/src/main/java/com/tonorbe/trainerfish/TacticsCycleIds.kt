package com.tonorbe.trainerfish

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TF_CYCLE_MAX = 500

internal fun tfCycleSlotCsv(count: Int): String =
    (0 until count.coerceAtLeast(1)).joinToString(",")

internal fun tfReadCycleIds(pf: CyclePrefs): List<Int> =
    pf.poolAbsCsv
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it >= 0 }

internal fun tfCycleTargetSize(pf: CyclePrefs): Int =
    (pf.size.takeIf { it > 0 } ?: 25).coerceIn(1, TF_CYCLE_MAX)

/**
 * Build the full encoded puzzle-ID order for a tactics cycle.
 * This samples IDs only. It does not load/parse PGNs.
 */
internal suspend fun tfSampleCycleIds(
    context: Context,
    existing: List<Int>,
    theme: String,
    minRating: Int?,
    maxRating: Int?,
    targetSize: Int
): List<Int> = withContext(Dispatchers.IO) {
    val want = targetSize.coerceIn(1, TF_CYCLE_MAX)
    val out = linkedSetOf<Int>()
    existing.filter { it >= 0 }.forEach { out += it }

    var attempts = 0
    while (out.size < want && attempts < 12) {
        val need = want - out.size
        val ask = (need * 3).coerceAtLeast(16).coerceAtMost(TF_CYCLE_MAX)
        val before = out.size

        val batch = runCatching {
            TacticsBinaryBank.samplePuzzleIds(
                context = context,
                theme = theme,
                limit = ask,
                minRating = minRating,
                maxRating = maxRating
            )
        }.getOrElse { emptyList() }

        if (batch.isEmpty()) break
        for (id in batch) {
            if (id >= 0) out += id
            if (out.size >= want) break
        }

        attempts = if (out.size == before) attempts + 1 else 0
    }

    out.take(want)
}
