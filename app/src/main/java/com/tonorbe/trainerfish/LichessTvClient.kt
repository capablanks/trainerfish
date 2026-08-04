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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

internal const val LICHESS_TV_START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

internal enum class LichessTvConnectionStatus {
    CONNECTING,
    LIVE,
    RECONNECTING,
    FINISHED,
    DETACHED,
    OFFLINE
}

internal enum class LichessTvSource {
    TOP_GAME,
    WATCHED_PLAYER,
    BROADCAST_BOARD
}

internal data class LichessTvPlayer(
    val name: String = "Waiting for player",
    val title: String? = null,
    val rating: Int? = null,
    val seconds: Int? = null,
    val fideId: Long? = null,
    val federation: String? = null,
    val lichessUsername: String? = null
) {
    val displayName: String
        get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "Anonymous" }
}

internal data class LichessTvState(
    val status: LichessTvConnectionStatus = LichessTvConnectionStatus.OFFLINE,
    val source: LichessTvSource = LichessTvSource.TOP_GAME,
    val watchedUsername: String? = null,
    val watchedGameOngoing: Boolean = false,
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
    val result: String? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null
)

internal fun lichessTvResultOrNull(raw: String?): String? = when (
    raw?.trim()?.replace('\u2013', '-')?.replace('\u2014', '-')
) {
    "1-0" -> "1-0"
    "0-1" -> "0-1"
    "1/2-1/2", "\u00bd-\u00bd" -> "1/2-1/2"
    else -> null
}

internal fun lichessTvResultLabel(result: String?): String? = when (lichessTvResultOrNull(result)) {
    "1-0" -> "1\u20130"
    "0-1" -> "0\u20131"
    "1/2-1/2" -> "\u00bd\u2013\u00bd"
    else -> null
}

/**
 * One unauthenticated spectator connection to either Lichess's TV feed or the
 * public, delayed stream of a user-selected game. PGN exports fill moves played
 * before TrainerFish joined either stream.
 */
internal class LichessTvClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LichessTvState())
    val state: StateFlow<LichessTvState> = _state.asStateFlow()

    @Volatile
    private var wantsLiveConnection = false
    @Volatile
    private var connectionSerial = 0L
    @Volatile
    private var activeConnection: HttpURLConnection? = null
    private var streamJob: Job? = null
    private var pgnJob: Job? = null
    private var activeBroadcastSelection: LichessBroadcastSelection? = null

    fun connect() {
        if (
            streamJob?.isActive == true &&
            _state.value.source == LichessTvSource.TOP_GAME
        ) return
        connectTopGame()
    }

    fun connectTopGame(noticeMessage: String? = null) {
        activeBroadcastSelection = null
        startConnection(LichessTvSource.TOP_GAME, null, noticeMessage)
    }

    fun watchPlayer(username: String) {
        val normalized = username.trim().removePrefix("@").trim()
        if (normalized.isBlank()) return
        activeBroadcastSelection = null
        startConnection(LichessTvSource.WATCHED_PLAYER, normalized, null)
    }

    fun watchBroadcastBoard(selection: LichessBroadcastSelection) {
        activeBroadcastSelection = selection
        wantsLiveConnection = true
        val serial = ++connectionSerial
        pgnJob?.cancel()
        streamJob?.cancel()
        closeActiveConnection()
        _state.value = LichessTvState(
            status = if (selection.isOngoing) {
                LichessTvConnectionStatus.CONNECTING
            } else {
                LichessTvConnectionStatus.FINISHED
            },
            source = LichessTvSource.BROADCAST_BOARD,
            gameId = selection.gameId,
            fen = selection.fen,
            white = selection.white,
            black = selection.black,
            orientationWhite = true,
            lastMoveUci = selection.lastMoveUci,
            result = selection.result,
            noticeMessage = selection.notice
        )
        streamJob = scope.launch { broadcastBoardLoop(serial, selection) }
    }

    fun reconnect() {
        val current = _state.value
        when {
            current.source == LichessTvSource.WATCHED_PLAYER && !current.watchedUsername.isNullOrBlank() -> {
                watchPlayer(current.watchedUsername)
            }
            current.source == LichessTvSource.BROADCAST_BOARD && activeBroadcastSelection != null -> {
                watchBroadcastBoard(activeBroadcastSelection!!)
            }
            else -> connectTopGame()
        }
    }

    private fun startConnection(
        source: LichessTvSource,
        username: String?,
        noticeMessage: String?
    ) {
        wantsLiveConnection = true
        val serial = ++connectionSerial
        pgnJob?.cancel()
        streamJob?.cancel()
        closeActiveConnection()
        _state.value = LichessTvState(
            status = LichessTvConnectionStatus.CONNECTING,
            source = source,
            watchedUsername = username,
            watchedGameOngoing = source == LichessTvSource.WATCHED_PLAYER,
            noticeMessage = noticeMessage
        )
        streamJob = scope.launch {
            if (source == LichessTvSource.WATCHED_PLAYER && username != null) {
                watchedPlayerLoop(serial, username)
            } else {
                topGameLoop(serial, noticeMessage)
            }
        }
    }

    fun detachForAnalysis() {
        wantsLiveConnection = false
        connectionSerial++
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

    /** Pause the network feed while a modal source/game chooser covers the TV. */
    fun pauseForDialog() {
        detachForAnalysis()
    }

    fun close() {
        wantsLiveConnection = false
        connectionSerial++
        pgnJob?.cancel()
        streamJob?.cancel()
        closeActiveConnection()
        scope.cancel()
    }

    private suspend fun topGameLoop(serial: Long, initialNotice: String? = null) {
        var firstAttempt = true
        var retryDelayMs = 2_000L

        while (scope.isActive && isCurrent(serial)) {
            _state.update {
                it.copy(
                    source = LichessTvSource.TOP_GAME,
                    watchedUsername = null,
                    watchedGameOngoing = false,
                    status = if (firstAttempt) {
                        LichessTvConnectionStatus.CONNECTING
                    } else {
                        LichessTvConnectionStatus.RECONNECTING
                    },
                    errorMessage = null,
                    noticeMessage = it.noticeMessage ?: initialNotice
                )
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(TV_FEED_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = TOP_GAME_READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/x-ndjson")
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Cache-Control", "no-cache")
                    useCaches = false
                    doInput = true
                }
                activeConnection = connection
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Chess TV returned HTTP $code")
                }

                retryDelayMs = 2_000L
                _state.update {
                    it.copy(
                        status = LichessTvConnectionStatus.LIVE,
                        errorMessage = null,
                        noticeMessage = it.noticeMessage ?: initialNotice
                    )
                }

                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (scope.isActive && isCurrent(serial)) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) handleFeedLine(serial, line)
                    }
                }

                if (isCurrent(serial)) {
                    throw IllegalStateException("The Chess TV stream ended")
                }
            } catch (error: Throwable) {
                if (!isCurrent(serial) || !scope.isActive) break
                _state.update {
                    it.copy(
                        status = LichessTvConnectionStatus.RECONNECTING,
                        errorMessage = if (error is SocketTimeoutException) {
                            "Chess TV stream stalled — reconnecting automatically"
                        } else {
                            error.message ?: "Chess TV connection lost"
                        }
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

    private suspend fun watchedPlayerLoop(serial: Long, username: String) {
        val gameId = try {
            fetchPlayingGameId(username)
        } catch (_: Throwable) {
            if (!isCurrent(serial)) return
            fallbackToTopGame(
                serial,
                "Could not check player — showing top game."
            )
            return
        }

        if (!isCurrent(serial)) return
        val activeGameId = gameId?.takeIf { it.isNotBlank() }
        if (activeGameId == null) {
            fallbackToTopGame(serial, "Player not playing — showing top game.")
            return
        }

        _state.update { it.copy(gameId = activeGameId) }
        fetchPlayerCurrentPgn(username)?.let { loaded ->
            if (isCurrent(serial)) applyFetchedPgn(activeGameId, loaded)
        }
        if (!isCurrent(serial)) return

        var firstAttempt = true
        var retryDelayMs = 2_000L
        while (scope.isActive && isCurrent(serial)) {
            _state.update {
                it.copy(
                    status = if (firstAttempt) {
                        LichessTvConnectionStatus.CONNECTING
                    } else {
                        LichessTvConnectionStatus.RECONNECTING
                    },
                    source = LichessTvSource.WATCHED_PLAYER,
                    watchedUsername = username,
                    watchedGameOngoing = true,
                    gameId = activeGameId,
                    errorMessage = null,
                    noticeMessage = "Watching @$username • 3-move delay • engine off"
                )
            }

            var connection: HttpURLConnection? = null
            try {
                val encodedGameId = URLEncoder.encode(activeGameId, Charsets.UTF_8.name())
                connection = (URL("$GAME_STREAM_BASE/$encodedGameId").openConnection() as HttpURLConnection).apply {
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
                    throw IllegalStateException("Player game stream returned HTTP $code")
                }

                retryDelayMs = 2_000L
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (scope.isActive && isCurrent(serial)) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            handleWatchedGameLine(serial, username, activeGameId, line)
                        }
                    }
                }

                if (!isCurrent(serial)) break
                fetchCurrentPgn(activeGameId)?.let { loaded ->
                    if (isCurrent(serial)) applyFetchedPgn(activeGameId, loaded)
                }
                if (!_state.value.watchedGameOngoing || _state.value.result != null) break
                throw IllegalStateException("Player game stream ended")
            } catch (error: Throwable) {
                if (!isCurrent(serial) || !scope.isActive) break
                _state.update {
                    it.copy(
                        status = LichessTvConnectionStatus.RECONNECTING,
                        errorMessage = error.message ?: "Player game connection lost"
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

    private suspend fun broadcastBoardLoop(
        serial: Long,
        selection: LichessBroadcastSelection
    ) {
        var firstAttempt = true
        var retryDelayMs = 2_000L

        while (scope.isActive && isCurrent(serial)) {
            _state.update { current ->
                if (current.source != LichessTvSource.BROADCAST_BOARD) current else current.copy(
                    status = if (firstAttempt) {
                        LichessTvConnectionStatus.CONNECTING
                    } else {
                        LichessTvConnectionStatus.RECONNECTING
                    },
                    errorMessage = null,
                    noticeMessage = selection.notice
                )
            }

            var connection: HttpURLConnection? = null
            try {
                val encodedRoundId = URLEncoder.encode(selection.roundId, Charsets.UTF_8.name())
                val url = "$BROADCAST_STREAM_BASE/$encodedRoundId.pgn?clocks=true&comments=false"
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 0
                    setRequestProperty("Accept", "application/x-chess-pgn")
                    setRequestProperty("User-Agent", USER_AGENT)
                    useCaches = false
                    doInput = true
                }
                activeConnection = connection
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Tournament broadcast returned HTTP $code")
                }

                retryDelayMs = 2_000L
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    consumeBroadcastPgnStream(
                        reader = reader,
                        serial = serial,
                        selection = selection
                    )
                }

                if (!isCurrent(serial)) break
                if (_state.value.status == LichessTvConnectionStatus.FINISHED) break
                throw IllegalStateException("Tournament broadcast stream ended")
            } catch (error: Throwable) {
                if (!isCurrent(serial) || !scope.isActive) break
                _state.update { current ->
                    if (current.source != LichessTvSource.BROADCAST_BOARD) current else current.copy(
                        status = LichessTvConnectionStatus.RECONNECTING,
                        errorMessage = error.message ?: "Tournament broadcast connection lost"
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

    private fun consumeBroadcastPgnStream(
        reader: BufferedReader,
        serial: Long,
        selection: LichessBroadcastSelection
    ) {
        var block = StringBuilder()
        var sawMoveText = false
        var trailingBlankLines = 0

        fun flushBlock() {
            if (block.isNotBlank()) {
                handleBroadcastPgnBlock(serial, selection, block.toString())
            }
            block = StringBuilder()
            sawMoveText = false
            trailingBlankLines = 0
        }

        while (scope.isActive && isCurrent(serial)) {
            val line = reader.readLine() ?: break
            if (line.startsWith("[Event ") && block.isNotBlank() && sawMoveText) {
                flushBlock()
            }
            if (block.length + line.length + 1 <= MAX_PGN_CHARS) {
                block.append(line).append('\n')
            }

            when {
                line.isBlank() && sawMoveText -> {
                    trailingBlankLines += 1
                    if (trailingBlankLines >= 2) flushBlock()
                }
                line.isNotBlank() && !line.startsWith("[") -> {
                    sawMoveText = true
                    trailingBlankLines = 0
                }
                line.isNotBlank() -> trailingBlankLines = 0
            }
        }
        flushBlock()
    }

    private fun handleBroadcastPgnBlock(
        serial: Long,
        selection: LichessBroadcastSelection,
        pgn: String
    ) {
        if (!isCurrent(serial)) return
        val gameUrl = pgnTag(pgn, "GameURL") ?: pgnTag(pgn, "Site") ?: return
        val gameId = gameUrl.substringBefore('?').trimEnd('/').substringAfterLast('/')
        if (gameId != selection.gameId) return
        val loaded = parseTvPgn(pgn) ?: return
        val rawResult = pgnTag(pgn, "Result").orEmpty()
        val result = lichessTvResultOrNull(rawResult)
        val finished = rawResult.isNotBlank() && rawResult != "*"
        val finalFen = positionAfter(loaded.startFen, loaded.uciMoves) ?: _state.value.fen

        _state.update { current ->
            if (
                current.source != LichessTvSource.BROADCAST_BOARD ||
                current.gameId != selection.gameId
            ) return@update current
            current.copy(
                status = if (finished) {
                    LichessTvConnectionStatus.FINISHED
                } else {
                    LichessTvConnectionStatus.LIVE
                },
                fen = finalFen,
                startFen = loaded.startFen,
                lastMoveUci = loaded.uciMoves.lastOrNull() ?: current.lastMoveUci,
                uciMoves = loaded.uciMoves,
                sanMoves = loaded.sanMoves,
                white = current.white.copy(seconds = loaded.whiteSeconds ?: current.white.seconds),
                black = current.black.copy(seconds = loaded.blackSeconds ?: current.black.seconds),
                pgnLoaded = true,
                result = result ?: current.result,
                errorMessage = null,
                noticeMessage = selection.notice
            )
        }
    }

    private fun positionAfter(startFen: String, uciMoves: List<String>): String? {
        val board = runCatching {
            com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(startFen) }
        }.getOrNull() ?: return null
        for (uci in uciMoves) {
            val move = bfUciToMoveOnBoard(board, uci) ?: return null
            if (!board.doMove(move)) return null
        }
        return board.fen
    }

    private fun pgnTag(pgn: String, name: String): String? {
        val escapedName = Regex.escape(name)
        return Regex("(?m)^\\[$escapedName\\s+\"([^\"]*)\"\\]\\s*$")
            .find(pgn)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun fallbackToTopGame(serial: Long, message: String) {
        if (!isCurrent(serial)) return
        _state.value = LichessTvState(
            status = LichessTvConnectionStatus.CONNECTING,
            source = LichessTvSource.TOP_GAME,
            noticeMessage = message
        )
        topGameLoop(serial, message)
    }

    private fun fetchPlayingGameId(username: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val encodedUsername = URLEncoder.encode(username, Charsets.UTF_8.name())
            val url = "$USER_STATUS_URL?ids=$encodedUsername&withGameIds=true"
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                useCaches = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Player status returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val player = JSONArray(body).optJSONObject(0) ?: return null
            player.optString("playingId").trim().takeIf { it.isNotBlank() }
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun handleWatchedGameLine(
        serial: Long,
        username: String,
        expectedGameId: String,
        line: String
    ) {
        if (!isCurrent(serial)) return
        val data = runCatching { JSONObject(line) }.getOrNull() ?: return
        if (data.has("id") || data.has("players")) {
            handleWatchedGameDescription(serial, username, expectedGameId, data)
        } else {
            handleWatchedFen(serial, data)
        }
    }

    private fun handleWatchedGameDescription(
        serial: Long,
        username: String,
        expectedGameId: String,
        data: JSONObject
    ) {
        if (!isCurrent(serial)) return
        val id = data.optString("id").trim().ifBlank { expectedGameId }
        if (id != expectedGameId) return
        val players = data.optJSONObject("players")
        val white = parseStreamPlayer(players?.optJSONObject("white"), "White")
        val black = parseStreamPlayer(players?.optJSONObject("black"), "Black")
        val statusName = data.optJSONObject("status")?.optString("name").orEmpty()
            .ifBlank { data.optString("status") }.lowercase()
        val ongoing = statusName.isBlank() || statusName == "created" || statusName == "started"
        val startFen = data.optString("initialFen").trim().ifBlank { LICHESS_TV_START_FEN }
        val fen = normalizeStreamFen(data.optString("fen"), startFen)
        val lastMove = data.optString("lastMove").trim().takeIf { it.isNotBlank() }
        val existing = _state.value.takeIf { it.gameId == id }
        val result = lichessTvResultOrNull(data.optString("result"))
            ?: when (data.optString("winner").trim().lowercase()) {
                "white" -> "1-0"; "black" -> "0-1"; else -> null
            }
            ?: if (!ongoing && statusName in setOf(
                "draw", "stalemate", "repetition", "insufficientmaterial", "fiftymoves"
            )) "1/2-1/2" else existing?.result

        _state.value = LichessTvState(
            status = if (ongoing) LichessTvConnectionStatus.LIVE else LichessTvConnectionStatus.FINISHED,
            source = LichessTvSource.WATCHED_PLAYER,
            watchedUsername = username,
            watchedGameOngoing = ongoing,
            gameId = id,
            fen = fen,
            startFen = existing?.startFen ?: startFen,
            white = white,
            black = black,
            orientationWhite = white.name.equals(username, ignoreCase = true),
            lastMoveUci = lastMove ?: existing?.lastMoveUci,
            uciMoves = existing?.uciMoves.orEmpty(),
            sanMoves = existing?.sanMoves.orEmpty(),
            pgnLoaded = existing?.pgnLoaded ?: false,
            result = result,
            noticeMessage = if (ongoing) {
                "Watching @$username • 3-move delay • engine off"
            } else {
                "@$username finished • engine analysis available"
            }
        )
        pgnJob?.cancel()
        pgnJob = scope.launch {
            val loaded = if (ongoing) fetchPlayerCurrentPgn(username) else fetchCurrentPgn(id)
            loaded?.let { applyFetchedPgn(id, it) }
        }
    }

    private fun handleWatchedFen(serial: Long, data: JSONObject) {
        if (!isCurrent(serial)) return
        val moveUci = data.optString("lm").trim().takeIf { it.isNotBlank() }
        _state.update { current ->
            if (current.source != LichessTvSource.WATCHED_PLAYER) return@update current
            val before = runCatching {
                com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(current.fen) }
            }.getOrNull()
            val move = if (before != null && moveUci != null) bfUciToMoveOnBoard(before, moveUci) else null
            val san = if (before != null && move != null) runCatching {
                bfPrettySan(before, move, before.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE)
            }.getOrNull() else null
            val applied = before != null && move != null && runCatching { before.doMove(move) }.getOrDefault(false)
            val fen = when {
                applied -> before!!.fen
                moveUci == null -> normalizeStreamFen(data.optString("fen"), current.fen)
                else -> current.fen
            }
            val append = applied && moveUci != null && current.lastMoveUci != moveUci &&
                current.uciMoves.lastOrNull() != moveUci
            val finished = current.status == LichessTvConnectionStatus.FINISHED || current.result != null
            current.copy(
                status = if (finished) LichessTvConnectionStatus.FINISHED else LichessTvConnectionStatus.LIVE,
                watchedGameOngoing = !finished,
                fen = fen,
                white = current.white.copy(seconds = data.optIntOrNull("wc") ?: current.white.seconds),
                black = current.black.copy(seconds = data.optIntOrNull("bc") ?: current.black.seconds),
                lastMoveUci = if (applied) moveUci else current.lastMoveUci,
                uciMoves = if (append) current.uciMoves + moveUci else current.uciMoves,
                sanMoves = if (append) current.sanMoves + (san ?: moveUci) else current.sanMoves,
                errorMessage = null
            )
        }
    }

    private fun parseStreamPlayer(item: JSONObject?, fallbackName: String): LichessTvPlayer {
        val user = item?.optJSONObject("user")
        return LichessTvPlayer(
            name = user?.optString("name").orEmpty().ifBlank { fallbackName },
            title = user?.optString("title")?.trim()?.takeIf { it.isNotBlank() },
            rating = item?.optIntOrNull("rating"),
            seconds = item?.optIntOrNull("seconds"),
            lichessUsername = user?.optString("id")?.trim()?.takeIf { it.isNotBlank() }
                ?: user?.optString("name")?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun normalizeStreamFen(rawFen: String, fallback: String): String {
        val trimmed = rawFen.trim()
        if (trimmed.isBlank()) return fallback
        val fields = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            fields.size >= 6 -> fields.take(6).joinToString(" ")
            fields.size >= 2 -> "${fields[0]} ${fields[1]} - - 0 1"
            else -> fallback
        }
    }

    private fun isCurrent(serial: Long): Boolean =
        wantsLiveConnection && connectionSerial == serial

    private fun handleFeedLine(serial: Long, line: String) {
        if (!isCurrent(serial)) return
        val event = runCatching { JSONObject(line) }.getOrNull() ?: return
        val data = event.optJSONObject("d") ?: return
        when (event.optString("t")) {
            "featured" -> handleFeatured(serial, data)
            "fen" -> handleFen(serial, data)
        }
    }

    private fun handleFeatured(serial: Long, data: JSONObject) {
        if (!isCurrent(serial)) return
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
        val notice = _state.value.noticeMessage
        _state.value = LichessTvState(
            status = LichessTvConnectionStatus.LIVE,
            source = LichessTvSource.TOP_GAME,
            gameId = id,
            fen = fen,
            startFen = fen,
            white = white,
            black = black,
            orientationWhite = !data.optString("orientation").equals("black", ignoreCase = true),
            lastMoveUci = lastMove,
            errorMessage = null,
            noticeMessage = notice
        )

        pgnJob?.cancel()
        pgnJob = scope.launch {
            fetchCurrentPgn(id)?.let { loaded -> applyFetchedPgn(id, loaded) }
        }
    }

    private fun handleFen(serial: Long, data: JSONObject) {
        if (!isCurrent(serial)) return
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
            seconds = item.optIntOrNull("seconds"),
            lichessUsername = user?.optString("id")?.trim()?.takeIf { it.isNotBlank() }
                ?: user?.optString("name")?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun fetchCurrentPgn(gameId: String): LoadedTvPgn? {
        val encodedGameId = URLEncoder.encode(gameId, Charsets.UTF_8.name())
        val url = "$GAME_EXPORT_BASE/$encodedGameId?clocks=false&evals=false&literate=false&opening=true"
        return fetchPgn(url)
    }

    private suspend fun fetchPlayerCurrentPgn(username: String): LoadedTvPgn? {
        val encodedUsername = URLEncoder.encode(username, Charsets.UTF_8.name())
        val url = "$USER_CURRENT_GAME_BASE/$encodedUsername/current-game" +
            "?moves=true&clocks=false&evals=false&literate=false&opening=true"
        return fetchPgn(url)
    }

    private suspend fun fetchPgn(url: String): LoadedTvPgn? {
        var connection: HttpURLConnection? = null
        return try {
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
        var whiteSeconds: Int? = null
        var blackSeconds: Int? = null
        var ply = 0
        var nodeId = tree.nodes.getOrNull(tree.rootId)?.nextId
        while (nodeId != null) {
            val node = tree.nodes.getOrNull(nodeId) ?: break
            val moveUci = node.uci ?: break
            uci += moveUci
            san += node.san.ifBlank { moveUci }
            parsePgnClockSeconds(node.postComment ?: node.preComment)?.let { seconds ->
                if (ply % 2 == 0) whiteSeconds = seconds else blackSeconds = seconds
            }
            ply += 1
            nodeId = node.nextId
        }
        return LoadedTvPgn(
            startFen = tree.startFen?.takeIf { it.isNotBlank() } ?: LICHESS_TV_START_FEN,
            uciMoves = uci,
            sanMoves = san,
            whiteSeconds = whiteSeconds,
            blackSeconds = blackSeconds,
            result = lichessTvResultOrNull(pgnTag(pgn, "Result"))
        )
    }

    private fun parsePgnClockSeconds(comment: String?): Int? {
        val raw = comment?.let {
            Regex("%clk\\s+([0-9]+):([0-9]{1,2}):([0-9]{1,2}(?:\\.[0-9]+)?)")
                .find(it)
        } ?: return null
        val hours = raw.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minutes = raw.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val seconds = raw.groupValues.getOrNull(3)?.toDoubleOrNull() ?: return null
        return (hours * 3_600 + minutes * 60 + seconds).toInt().coerceAtLeast(0)
    }

    private fun applyFetchedPgn(gameId: String, fetched: LoadedTvPgn) {
        _state.update { current ->
            if (current.gameId != gameId) return@update current
            val currentFen = positionAfter(fetched.startFen, current.uciMoves)
            val extend = currentFen != null && current.uciMoves.size >= fetched.uciMoves.size &&
                current.uciMoves.take(fetched.uciMoves.size) == fetched.uciMoves
            val uci = if (extend) current.uciMoves else fetched.uciMoves
            val san = if (extend && current.sanMoves.size == current.uciMoves.size) current.sanMoves else fetched.sanMoves
            val finalFen = (if (extend) currentFen else positionAfter(fetched.startFen, fetched.uciMoves)) ?: current.fen
            val result = fetched.result ?: current.result
            val finished = result != null
            val watchedFinished = current.source == LichessTvSource.WATCHED_PLAYER && finished
            current.copy(
                status = if (finished) LichessTvConnectionStatus.FINISHED else current.status,
                watchedGameOngoing = if (watchedFinished) false else current.watchedGameOngoing,
                fen = finalFen,
                startFen = fetched.startFen,
                lastMoveUci = uci.lastOrNull() ?: current.lastMoveUci,
                uciMoves = uci,
                sanMoves = san,
                white = current.white.copy(seconds = fetched.whiteSeconds ?: current.white.seconds),
                black = current.black.copy(seconds = fetched.blackSeconds ?: current.black.seconds),
                pgnLoaded = true,
                result = result,
                errorMessage = null,
                noticeMessage = if (watchedFinished) {
                    current.watchedUsername?.let { "@$it finished • engine analysis available" } ?: current.noticeMessage
                } else current.noticeMessage
            )
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
        val sanMoves: List<String>,
        val whiteSeconds: Int?,
        val blackSeconds: Int?,
        val result: String?
    )

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    companion object {
        private const val TV_FEED_URL = "https://lichess.org/api/tv/feed"
        private const val TOP_GAME_READ_TIMEOUT_MS = 75_000
        private const val GAME_EXPORT_BASE = "https://lichess.org/game/export"
        private const val USER_CURRENT_GAME_BASE = "https://lichess.org/api/user"
        private const val USER_STATUS_URL = "https://lichess.org/api/users/status"
        private const val GAME_STREAM_BASE = "https://lichess.org/api/stream/game"
        private const val BROADCAST_STREAM_BASE = "https://lichess.org/api/stream/broadcast/round"
        private const val USER_AGENT = "TrainerFish/5.1 (com.tonorbe.trainerfish)"
        private const val MAX_PGN_CHARS = 512_000
    }
}
