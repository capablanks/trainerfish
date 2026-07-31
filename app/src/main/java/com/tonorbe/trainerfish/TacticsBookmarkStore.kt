package com.tonorbe.trainerfish

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved tactics puzzle.
 *
 * absIndex is the absolute game index inside R.raw.train_all.
 * visibleFen/savedPly are optional v2 fields. Old saved bookmarks still load.
 */
data class TacticsBookmark(
    val absIndex: Int,
    val eventId: String = "",
    val title: String,
    val theme: String,
    val rating: Int,
    val startFen: String,
    val savedAt: Long,
    val visibleFen: String = "",
    val savedPly: Int = 0
)

class TacticsBookmarkStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tf_tactics_bookmarks", Context.MODE_PRIVATE)

    private fun readRaw(): String = sp.getString("items_json", "[]") ?: "[]"

    fun clearAll() {
        sp.edit().clear().apply()
    }

    fun list(): List<TacticsBookmark> {
        val arr = runCatching { JSONArray(readRaw()) }.getOrElse { JSONArray() }
        val out = ArrayList<TacticsBookmark>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val absIndex = o.optInt("absIndex", -1)
            if (absIndex < 0) continue
            out += TacticsBookmark(
                absIndex = absIndex,
                eventId = o.optString("eventId", ""),
                title = o.optString("title", "Puzzle #$absIndex"),
                theme = o.optString("theme", ""),
                rating = o.optInt("rating", 0),
                startFen = o.optString("startFen", ""),
                savedAt = o.optLong("savedAt", 0L),
                visibleFen = o.optString("visibleFen", ""),
                savedPly = o.optInt("savedPly", 0)
            )
        }
        return out.sortedWith(compareByDescending<TacticsBookmark> { it.savedAt }.thenBy { it.absIndex })
    }

    private fun bookmarkKey(item: TacticsBookmark): String {
        val event = item.eventId.trim().ifBlank { item.title.trim() }
        return if (event.isNotBlank()) "event:$event" else "abs:${item.absIndex}"
    }

    fun isBookmarked(absIndex: Int, eventId: String = ""): Boolean {
        val event = eventId.trim()
        return list().any { item ->
            if (event.isNotBlank()) item.eventId.trim() == event || item.title.trim() == event
            else item.absIndex == absIndex
        }
    }

    fun add(bookmark: TacticsBookmark) {
        val key = bookmarkKey(bookmark)
        val merged = list().filterNot { bookmarkKey(it) == key } + bookmark
        save(merged)
    }

    fun remove(absIndex: Int, eventId: String = "") {
        val event = eventId.trim()
        save(list().filterNot { item ->
            if (event.isNotBlank()) item.eventId.trim() == event || item.title.trim() == event
            else item.absIndex == absIndex
        })
    }

    /** @return true if now bookmarked, false if removed. */
    fun toggle(bookmark: TacticsBookmark): Boolean {
        val event = bookmark.eventId.trim().ifBlank { bookmark.title.trim() }
        return if (isBookmarked(bookmark.absIndex, event)) {
            remove(bookmark.absIndex, event)
            false
        } else {
            add(bookmark)
            true
        }
    }

    private fun save(items: List<TacticsBookmark>) {
        val arr = JSONArray()
        items.distinctBy { bookmarkKey(it) }.forEach { bm ->
            arr.put(
                JSONObject()
                    .put("absIndex", bm.absIndex)
                    .put("eventId", bm.eventId)
                    .put("title", bm.title)
                    .put("theme", bm.theme)
                    .put("rating", bm.rating)
                    .put("startFen", bm.startFen)
                    .put("visibleFen", bm.visibleFen)
                    .put("savedPly", bm.savedPly)
                    .put("savedAt", bm.savedAt)
            )
        }
        sp.edit().putString("items_json", arr.toString()).apply()
    }
}
