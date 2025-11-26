package com.tonorbe.trainerfish

import android.content.Context

class ClockPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gm_clock", Context.MODE_PRIVATE)

    var dndEnabled: Boolean
        get() = sp.getBoolean("dnd_enabled", false)
        set(v) { sp.edit().putBoolean("dnd_enabled", v).apply() }
}
