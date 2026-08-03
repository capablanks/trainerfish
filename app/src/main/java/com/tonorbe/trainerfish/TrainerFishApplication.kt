package com.tonorbe.trainerfish

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk
import com.tonorbe.trainerfish.playgames.TrainerFishPlayGamesConfig

class TrainerFishApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (TrainerFishPlayGamesConfig.isConfigured(this)) {
            PlayGamesSdk.initialize(this)
        }
    }
}
