package com.tonorbe.trainerfish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.tonorbe.trainerfish.pgn.PgnGameInfo
import com.tonorbe.trainerfish.pgn.PgnSession

class PuzzleViewModel : ViewModel() {
    var savedGames by mutableStateOf<List<PgnGameInfo>?>(null)
    var savedSession by mutableStateOf<PgnSession?>(null)
    var savedCurrent by mutableStateOf<PgnGameInfo?>(null)
    var savedIndex by mutableStateOf(-1)
    var savedWhiteBottom by mutableStateOf(true)
    var savedRemainingSec by mutableStateOf<Int?>(null)

    var hasSnapshot by mutableStateOf(false)
        private set

    fun saveSnapshot(
        games: List<PgnGameInfo>,
        session: PgnSession?,
        current: PgnGameInfo?,
        index: Int,
        whiteBottom: Boolean,
        remainingSec: Int?
    ) {
        savedGames = games
        savedSession = session
        savedCurrent = current
        savedIndex = index
        savedWhiteBottom = whiteBottom
        savedRemainingSec = remainingSec
        hasSnapshot = true
    }

    fun clearSnapshot() { hasSnapshot = false }
}
