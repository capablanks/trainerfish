package com.tonorbe.trainerfish.engine

import android.content.Context
import android.content.SharedPreferences

enum class EngineSource { BUILTIN, LATEST }

class EnginePrefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("engine_prefs", Context.MODE_PRIVATE)

    // UI toggle: remember whether the user wants the engine ON
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(value) { sp.edit().putBoolean("enabled", value).apply() }

    // Which engine to use (built-in vs downloaded)
    var source: EngineSource
        get() = runCatching {
            EngineSource.valueOf(sp.getString("source", "BUILTIN")!!)
        }.getOrDefault(EngineSource.BUILTIN)
        set(value) { sp.edit().putString("source", value.name).apply() }

    // Download URLs (raw ELF binaries)
    var latestUrlArm64: String
        get() = sp.getString("latest_url_arm64", "") ?: ""
        set(value) { sp.edit().putString("latest_url_arm64", value).apply() }

    var latestUrlArmv7: String
        get() = sp.getString("latest_url_armv7", "") ?: ""
        set(value) { sp.edit().putString("latest_url_armv7", value).apply() }
}
