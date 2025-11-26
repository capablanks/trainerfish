package com.tonorbe.trainerfish

import android.content.Context

/** Minimal first-run flag (v2 key so we can re-show after upgrades if needed). */
class FirstRunPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("first_run_prefs", Context.MODE_PRIVATE)

    var seenWelcome: Boolean
        get() = sp.getBoolean("seen_welcome_v2", false)
        set(v) { sp.edit().putBoolean("seen_welcome_v2", v).apply() }
}
