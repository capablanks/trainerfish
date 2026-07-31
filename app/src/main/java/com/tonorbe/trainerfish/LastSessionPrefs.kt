package com.tonorbe.trainerfish

import android.content.Context
import android.content.SharedPreferences

class LastSessionPrefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("last_session_prefs", Context.MODE_PRIVATE)

    var hasResume: Boolean
        get() = sp.getBoolean("hasResume", false)
        set(v) = sp.edit().putBoolean("hasResume", v).apply()

    var seriesId: String
        get() = sp.getString("seriesId", "train_all") ?: "train_all"
        set(v) = sp.edit().putString("seriesId", v).apply()

    var modeName: String
        get() = sp.getString("modeName", TrainerMode.WOODPECKER.name) ?: TrainerMode.WOODPECKER.name
        set(v) = sp.edit().putString("modeName", v).apply()

    var activeCycleId: Int
        get() = sp.getInt("activeCycleId", -1)
        set(v) = sp.edit().putInt("activeCycleId", v).apply()

    // where we were in the pool (0..poolSize-1)
    var poolPos: Int
        get() = sp.getInt("poolPos", 0)
        set(v) = sp.edit().putInt("poolPos", v).apply()

    // optional: save last selected endgame/opening index
    var endgameIndex: Int
        get() = sp.getInt("endgameIndex", 0)
        set(v) = sp.edit().putInt("endgameIndex", v).apply()

    // ---- Endgame restore ("up to last user move") ----
    // Position AFTER the user's last move (Fish to move next).
    var endgameFen: String
        get() = sp.getString("endgameFen", "") ?: ""
        set(v) = sp.edit().putString("endgameFen", v).apply()

    // Move line (pretty SAN strings) up to the user's last move.
    var endgameSanJson: String
        get() = sp.getString("endgameSanJson", "[]") ?: "[]"
        set(v) = sp.edit().putString("endgameSanJson", v).apply()

    // ---- Beat the Fish restore ----
    // Last position seen/used (restored into setup/edit mode on cold start).
    var beatFishFen: String
        get() = sp.getString("beatFishFen", "") ?: ""
        set(v) = sp.edit().putString("beatFishFen", v).apply()

    // Last live board shown outside Beat the Fish. This is intentionally separate
    // from beatFishFen so visiting Beat the Fish never overwrites the position that
    // "Load from other modes" is expected to retrieve.
    var otherModeFen: String
        get() = sp.getString("otherModeFen", "") ?: ""
        set(v) = sp.edit().putString("otherModeFen", v).apply()

    var otherModeName: String
        get() = sp.getString("otherModeName", "") ?: ""
        set(v) = sp.edit().putString("otherModeName", v).apply()

    var openingStartFen: String
        get() = sp.getString("openingStartFen", "") ?: ""
        set(v) = sp.edit().putString("openingStartFen", v).apply()

    var openingMovesUci: String
        get() = sp.getString("openingMovesUci", "") ?: ""
        set(v) = sp.edit().putString("openingMovesUci", v).apply()

    var openingCursorPly: Int
        get() = sp.getInt("openingCursorPly", 0)
        set(v) = sp.edit().putInt("openingCursorPly", v).apply()

    var openingSanJson: String
        get() = sp.getString("openingSanJson", "[]") ?: "[]"
        set(v) = sp.edit().putString("openingSanJson", v).apply()

    var openingPly: Int
        get() = sp.getInt("openingPly", 0)
        set(v) = sp.edit().putInt("openingPly", v).apply()


}
