package com.tonorbe.trainerfish

/**
 * Process-session memory for Chess TV. Country and favorite-player choices are
 * persisted separately by ChessTvFollowStore; selected broadcast boards live
 * only for the current app process, as intended.
 */
internal data class ChessTvSessionSnapshot(
    val selectedGames: List<LichessBroadcastSelection> = emptyList(),
    val activeIndex: Int = 0,
    val lastSource: LichessTvSource = LichessTvSource.TOP_GAME
)

internal object ChessTvSessionStore {
    private val lock = Any()
    private var selectedGames: List<LichessBroadcastSelection> = emptyList()
    private var activeIndex: Int = 0
    private var lastSource: LichessTvSource = LichessTvSource.TOP_GAME

    fun snapshot(): ChessTvSessionSnapshot = synchronized(lock) {
        ChessTvSessionSnapshot(
            selectedGames = selectedGames.toList(),
            activeIndex = activeIndex.coerceIn(0, (selectedGames.size - 1).coerceAtLeast(0)),
            lastSource = lastSource
        )
    }

    fun saveGames(games: List<LichessBroadcastSelection>, index: Int) = synchronized(lock) {
        selectedGames = games.distinctBy { it.roundId to it.gameId }
        activeIndex = index.coerceIn(0, (selectedGames.size - 1).coerceAtLeast(0))
    }

    fun markSource(source: LichessTvSource) = synchronized(lock) {
        lastSource = source
    }

    fun replaceWith(selection: LichessBroadcastSelection) = synchronized(lock) {
        selectedGames = listOf(selection)
        activeIndex = 0
        lastSource = LichessTvSource.BROADCAST_BOARD
    }
}
