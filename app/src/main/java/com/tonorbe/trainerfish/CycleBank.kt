package com.tonorbe.trainerfish

import android.content.Context
import android.content.SharedPreferences

class CycleBank(
    private val ctx: Context,
    private val seriesId: String
) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("gm_cycles_$seriesId", Context.MODE_PRIVATE)

    private fun idsCsv(): String = sp.getString("ids_csv", "") ?: ""
    private fun saveIds(ids: List<Int>) {
        sp.edit().putString("ids_csv", ids.joinToString(",")).apply()
    }

    private var nextId: Int
        get() = sp.getInt("next_id", 2) // 1 is reserved for legacy/first
        set(v) { sp.edit().putInt("next_id", v).apply() }

    var activeId: Int
        get() = sp.getInt("active_id", 0) // 0 = none selected
        set(v) { sp.edit().putInt("active_id", v).apply() }

    /** Keep only for legacy paths that truly need a bootstrap; prefer list() elsewhere. */
    fun ensureInit(): Int {
        var ids = list()
        if (ids.isEmpty()) {
            ids = listOf(1)                  // legacy single-cycle -> ID=1
            saveIds(ids)
            if (!sp.contains("active_id")) activeId = 1
            if (!sp.contains("next_id"))    nextId   = 2
        }
        if (activeId !in ids) activeId = ids.first()
        return activeId
    }

    fun list(): List<Int> =
        idsCsv().split(',').mapNotNull { it.toIntOrNull() }.filter { it >= 1 }

    fun size(): Int = list().size

    fun indexOf(id: Int): Int = list().indexOf(id)

    /** Create a brand-new cycle and make it active. */
    fun create(): Int {
        val ids = list().toMutableList()
        val id = nextId
        ids += id
        saveIds(ids)
        nextId = id + 1
        activeId = id
        return id
    }

    /** Strong delete: remove id, label, and the cycle's SharedPreferences. */
    fun delete(id: Int) {
        val ids = list().toMutableList()
        val idx = ids.indexOf(id)
        if (idx == -1) return

        // wipe stored prefs content (defensive) and remove the prefs file when possible
        runCatching { prefs(id).clearAll() }
        val prefName = keyFor(seriesId, id)
        runCatching { ctx.deleteSharedPreferences(prefName) }
            .onFailure {
                // fallback: just clear the file
                ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit().clear().apply()
            }

        // remove label and id
        sp.edit().remove("label_$id").apply()
        ids.removeAt(idx)
        saveIds(ids)

        // update active pointer
        if (ids.isEmpty()) {
            activeId = 0
        } else if (activeId == id) {
            val newIdx = idx.coerceAtMost(ids.lastIndex).coerceAtLeast(0)
            activeId = ids[newIdx]
        }
    }

    /** Hard wipe of the whole cycle table (ids + labels + per-cycle prefs). */
    fun wipeAll() {
        val ids = list()
        ids.forEach { id ->
            sp.edit().remove("label_$id").apply()
            val prefName = keyFor(seriesId, id)
            runCatching { ctx.deleteSharedPreferences(prefName) }
                .onFailure {
                    ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
        }
        sp.edit()
            .remove("ids_csv")
            .remove("active_id")
            .putInt("next_id", 2)
            .apply()
    }

    /** Get prefs for a specific cycle. */
    fun prefs(id: Int = activeId): CyclePrefs =
        CyclePrefs(ctx, keyFor(seriesId, id))

    /** Compose a CyclePrefs key; ID=1 uses legacy key for painless migration. */
    private fun keyFor(seriesId: String, id: Int): String =
        if (id == 1) seriesId else "${seriesId}_$id"

    // ---- Labels (stored next to the ids list) ----
    fun getLabel(id: Int): String? = sp.getString("label_$id", null)

    fun setLabel(id: Int, name: String) {
        sp.edit().putString("label_$id", name.trim()).apply()
    }
}

/** Helper used when wiping a removed cycle. Adjust fields if your CyclePrefs differs. */
private fun CyclePrefs.clearAll() {
    poolAbsCsv = ""
    lastMin = 0
    lastMax = 0
    lastTheme = "All"
    size = 25
    poolCsv = ""
    solvedCsv = ""
    ptsEarned = 0
    ptsTotal = 0
    elapsedMs = 0L
    solvedCount = 0
    cycleId = 1
    globalSeenCsv = ""
    soundOn = true
}

/** Default parameter-based label for a new cycle. */
fun defaultCycleLabel(theme: String, min: Int?, max: Int?, size: Int): String {
    val t = theme.ifBlank { "All" }
    val lo = min ?: 1800
    val hi = max ?: 3210
    return "$t - $lo-$hi - $size"
}
