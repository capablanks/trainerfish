package com.tonorbe.trainerfish

import android.content.Context

class PremiumPrefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("trainerfish_premium", Context.MODE_PRIVATE)

    var isPro: Boolean
        get() = sp.getBoolean("is_pro", false)
        set(value) {
            sp.edit().putBoolean("is_pro", value).apply()
        }
}
