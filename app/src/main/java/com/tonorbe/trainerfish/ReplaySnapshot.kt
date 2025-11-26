package com.tonorbe.trainerfish

import com.tonorbe.trainerfish.pgn.PgnGameInfo
import com.tonorbe.trainerfish.pgn.PgnSession

object ReplaySnapshot {
    var has: Boolean = false
        private set

    var games: List<PgnGameInfo>? = null
        private set

    var session: PgnSession? = null
        private set

    var current: PgnGameInfo? = null
        private set

    var index: Int = -1
        private set

    var whiteBottom: Boolean = true
        private set

    // Which module this snapshot belongs to (for now: Tactics)
    var mode: TrainerMode = TrainerMode.WOODPECKER
        private set

    fun save(
        games: List<PgnGameInfo>,
        session: PgnSession?,
        current: PgnGameInfo?,
        index: Int,
        whiteBottom: Boolean,
        mode: TrainerMode = TrainerMode.WOODPECKER
    ) {
        this.has = true
        this.games = games
        this.session = session
        this.current = current
        this.index = index
        this.whiteBottom = whiteBottom
        this.mode = mode
    }

    fun clear() {
        has = false
        games = null
        session = null
        current = null
        index = -1
        whiteBottom = true
        mode = TrainerMode.WOODPECKER
    }
}
