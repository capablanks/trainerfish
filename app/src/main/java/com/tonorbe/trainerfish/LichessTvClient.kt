package com.tonorbe.trainerfish

import com.tonorbe.trainerfish.pgn.parsePgnTreeFromChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal const val LICHESS_TV_START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

internal enum class LichessTvConnectionStatus {
    CONNECTING,
    LIVE,
    RECONNECTING,
    DETACHED,
    OFFLINE
}

internal data class LichessTvPlayer(
    val name: String = "Waiting for player",
    val title: String? = null,
    val rating: Int? = null,
    val seconds: Int? = null
) {
    val displayName: String
        get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "Anonymous" }
}

internal data class LichessTvState(
    val status: LichessTvConnectionStatus = LichessTvConnectionStatus.OFFLINE,
    val gameId: String? = null,
    val fen: String = LICHESS_TV_START_FEN,
    val startFen: String = LICHESS_TV_START_FEN,
    val white: LichessTvPlayer = LichessTvPlayer(),
    val black: LichessTvPlayer = LichessTvPlayer(),
    val orientationWhite: Boolean = true,
    val lastMoveUci: String? = null,
    val uciMoves: List<String> = emptyList(),
    val sanMoves: List<String> = emptyList(),
    val pgnLoaded: Boolean = false,
    val errorMessage: String? = null
)

/**
 * One unauthenticated connection to Lichess's official TV NDJSON feed.
 *
 * The feed supplies the featured position and each new move. A separate one-shot
 * PGN export fills the moves played before TrainerFish joined the live stream.
 */
internal class LichessTvClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LichessTvState())
    val state: StateFlow<LichessTvState> = _state.asStateFlow()

    @Volatile
    private var wantsLiveConnection = false
    @Volatile
    private var activeConnection: HttpURLConnection? = null
    private var streamJob: Job? = null
    private var pgnJob: Job? = null

    fun connect() {
        wantsLiveConnection = true
        if (streamJob?.isActive == true) return
        streamJob = scope.launch { streamLoop() }
    }

    fun detachForAnalysis() {
        wantsLiveConnection = false
        // Let the small one-shot PGN request finish if it is already in flight so
        // analysis mode can still receive the moves played before TrainerFish joined.
        streamJob?.cancel()
        streamJob = null
        closeActiveConnection()
        _state.update {
            it.copy(
                status = LichessTvConnectionStatus.DETACHED,
                errorMessage = null
            )
        }
    }

    fun close() {
        wantsLiveConnection = false
        pgnJob?.cancel()
        streamJob?.cancel()
        closeActiveConnection()
        scope.cancel()
    }

    private suspend fun streamLoop() {
        var firstAttempt = true
        var retryDelayMs = 2_000L

        while (scope.isActive && wantsLiveConnection) {
            _state.update {
                it.copy(
                    status = if (firstAttempt) {
                        LichessTvConnectionStatus.CONNECTING
                    } else {
                        LichessTvConnectionStatus.RECONNECTING
                    },
                    errorMessage = null
                )
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(TV_FEED_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 0
                    setRequestProperty("Accept", "application/x-ndjson")
                    setRequestProperty("User-Agent", USER_AGENT)
                    useCaches = false
                    doInput = true
                }
                activeConnection = connection
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Grandmaster Chess TV returned HTTP $code")
                }

                retryDelayMs = 2_000L
                _state.update {
                    it.copy(status = LichessTvConnectionStatus.LIVE, errorMessage = null)
                }

                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (scope.isActive && wantsLiveConnection) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) handleFeedLine(line)
                    }
                }

                if (wantsLiveConnection) {
                    throw IllegalStateException("The Grandmaster Chess TV stream ended")
                }
            } catch (error: Throwable) {
                if (!wantsLiveConnection || !scope.isActive) break
                _state.update {
                    it.copy(
                        status = LichessTvConnectionStatus.RECONNECTING,
                        errorMessage = error.message ?: "Grandmaster Chess TV connection lost"
                    )
                }
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(15_000L)
            } finally {
                if (activeConnection === connection) activeConnection = null
                runCatching { connection?.disconnect() }
            }
            firstAttempt = false
        }
    }

    private fun handleFeedLine(line: String) {
        val event = runCatching { JSONObject(line) }.getOrNull() ?: return
        val data = event.optJSONObject("d") ?: return
        when (event.optString("t")) {
            "featured" -> handleFeatured(data)
            "fen" -> handleFen(data)
        }
    }

    private fun handleFeatured(data: JSONObject) {
        val id = data.optString("id").trim()
        if (id.isBlank()) return

        val players = data.optJSONArray("players")
        var white = LichessTvPlayer(name = "White")
        var black = LichessTvPlayer(name = "Black")
        if (players != null) {
            for (index in 0 until players.length()) {
                val item = players.optJSONObject(index) ?: continue
                val player = parsePlayer(item)
                if (item.optString("color").equals("black", ignoreCase = true)) {
                    black = player
                } else {
                    white = player
                }
            }
        }

        val fen = data.optString("fen").trim().ifBlank { LICHESS_TV_START_FEN }
        val lastMove = data.optString("lastMove").trim().takeIf { it.isNotBlank() }
        _state.value = LichessTvState(
            status = LichessTvConnectionStatus.LIVE,
            gameId = id,
            fen = fen,
            startFen = fen,
            white = white,
            black = black,
            orientationWhite = !data.optString("orientation").equals("black", ignoreCase = true),
            lastMoveUci = lastMove,
            errorMessage = null
        )

        pgnJob?.cancel()
        pgnJob = scope.launch {
            fetchCurrentPgn(id)?.let { loaded -> applyFetchedPgn(id, loaded) }
        }
    }

    private fun handleFen(data: JSONObject) {
        val newFen = data.optString("fen").trim()
        if (newFen.isBlank()) return

        val moveUci = data.optString("lm").trim().takeIf { it.isNotBlank() }
        _state.update { current ->
            var uciMoves = current.uciMoves
            var sanMoves = current.sanMoves

            if (moveUci != null && current.lastMoveUci != moveUci && uciMoves.lastOrNull() != moveUci) {
                val before = runCatching {
                    com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(current.fen) }
                }.getOrNull()
                val move = before?.let { bfUciToMoveOnBoard(it, moveUci) }
                val san = if (before != null && move != null) {
                    runCatching {
                        bfPrettySan(
                            board = before,
                            mv = move,
                            isWhiteMove = before.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE
                        )
                    }.getOrNull()
                } else null

                uciMoves = uciMoves + moveUci
                sanMoves = sanMoves + (san ?: moveUci)
            }

            current.copy(
                status = LichessTvConnectionStatus.LIVE,
                fen = newFen,
                white = current.white.copy(seconds = data.optIntOrNull("wc") ?: current.white.seconds),
                black = current.black.copy(seconds = data.optIntOrNull("bc") ?: current.black.seconds),
                lastMoveUci = moveUci ?: current.lastMoveUci,
                uciMoves = uciMoves,
                sanMoves = sanMoves,
                errorMessage = null
            )
        }
    }

    private fun parsePlayer(item: JSONObject): LichessTvPlayer {
        val user = item.optJSONObject("user")
        val rawName = user?.optString("name").orEmpty()
            .ifBlank { item.optString("name") }
            .ifBlank { "Anonymous" }
        return LichessTvPlayer(
            name = rawName,
            title = user?.optString("title")?.trim()?.takeIf { it.isNotBlank() },
            rating = item.optIntOrNull("rating"),
            seconds = item.optIntOrNull("seconds")
        )
    }

    private suspend fun fetchCurrentPgn(gameId: String): LoadedTvPgn? {
        var connection: HttpURLConnection? = null
        return try {
            val url = "$GAME_EXPORT_BASE/$gameId?clocks=false&evals=false&literate=false&opening=true"
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/x-chess-pgn")
                setRequestProperty("User-Agent", USER_AGENT)
                useCaches = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val pgn = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(8_192)
                while (output.length < MAX_PGN_CHARS) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    output.append(buffer, 0, count.coerceAtMost(MAX_PGN_CHARS - output.length))
                }
                output.toString()
            }
            parseTvPgn(pgn)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun parseTvPgn(pgn: String): LoadedTvPgn? {
        if (pgn.isBlank()) return null
        val tree = runCatching { parsePgnTreeFromChunk(pgn) }.getOrNull() ?: return null
        val uci = ArrayList<String>()
        val san = ArrayList<String>()
        var nodeId = tree.nodes.getOrNull(tree.rootId)?.nextId
        while (nodeId != null) {
            val node = tree.nodes.getOrNull(nodeId) ?: break
            val moveUci = node.uci ?: break
            uci += moveUci
            san += node.san.ifBlank { moveUci }
            nodeId = node.nextId
        }
        return LoadedTvPgn(
            startFen = tree.startFen?.takeIf { it.isNotBlank() } ?: LICHESS_TV_START_FEN,
            uciMoves = uci,
            sanMoves = san
        )
    }

    private fun applyFetchedPgn(gameId: String, fetched: LoadedTvPgn) {
        _state.update { current ->
            if (current.gameId != gameId) return@update current

            val fetchedIsPrefix = current.uciMoves.size >= fetched.uciMoves.size &&
                current.uciMoves.take(fetched.uciMoves.size) == fetched.uciMoves
            val currentIsPrefix = fetched.uciMoves.size >= current.uciMoves.size &&
                fetched.uciMoves.take(current.uciMoves.size) == current.uciMoves

            when {
                fetchedIsPrefix -> current.copy(
                    startFen = fetched.startFen,
                    pgnLoaded = true
                )
                currentIsPrefix || current.uciMoves.isEmpty() -> current.copy(
                    startFen = fetched.startFen,
                    uciMoves = fetched.uciMoves,
                    sanMoves = fetched.sanMoves,
                    pgnLoaded = true
                )
                else -> current.copy(pgnLoaded = true)
            }
        }
    }

    @Synchronized
    private fun closeActiveConnection() {
        val connection = activeConnection
        activeConnection = null
        runCatching { connection?.inputStream?.close() }
        runCatching { connection?.disconnect() }
    }

    private data class LoadedTvPgn(
        val startFen: String,
        val uciMoves: List<String>,
        val sanMoves: List<String>
    )

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    companion object {
        private const val TV_FEED_URL = "https://lichess.org/api/tv/feed"
        private const val GAME_EXPORT_BASE = "https://lichess.org/game/export"
        private const val USER_AGENT = "TrainerFish/5.0 (com.tonorbe.trainerfish)"
        private const val MAX_PGN_CHARS = 512_000
    }
}
