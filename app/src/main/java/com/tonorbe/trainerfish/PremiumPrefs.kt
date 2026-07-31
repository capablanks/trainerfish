package com.tonorbe.trainerfish

import android.content.Context

class PremiumPrefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("trainerfish_premium", Context.MODE_PRIVATE)

    /**
     * Local/testing flag only. Production Pro access must come from Play Billing.
     * Default must be false; otherwise fresh installs are treated as Pro.
     */
    var isPro: Boolean
        get() = sp.getBoolean("is_pro", false)
        set(value) {
            sp.edit().putBoolean("is_pro", value).apply()
        }
}
