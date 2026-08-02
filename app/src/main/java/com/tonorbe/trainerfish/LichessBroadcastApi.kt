package com.tonorbe.trainerfish

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class LichessBroadcastPreview(
    val tournamentId: String,
    val tournamentName: String,
    val roundId: String,
    val roundName: String,
    val tier: Int,
    val location: String?,
    val playersSummary: String?
)

internal data class LichessBroadcastBoard(
    val gameId: String,
    val boardNumber: Int,
    val fen: String,
    val lastMoveUci: String?,
    val white: LichessTvPlayer,
    val black: LichessTvPlayer,
    val status: String
) {
    val isOngoing: Boolean
        get() = status.isBlank() || status == "*"

    val result: String?
        get() = lichessTvResultOrNull(status)
}

internal data class LichessBroadcastRound(
    val preview: LichessBroadcastPreview,
    val boards: List<LichessBroadcastBoard>
)

internal data class LichessBroadcastSelection(
    val tournamentName: String,
    val roundId: String,
    val roundName: String,
    val gameId: String,
    val boardNumber: Int,
    val fen: String,
    val lastMoveUci: String?,
    val white: LichessTvPlayer,
    val black: LichessTvPlayer,
    val isOngoing: Boolean,
    val result: String?
) {
    val notice: String
        get() = "$tournamentName • $roundName • Board $boardNumber"
}

internal fun LichessBroadcastRound.selectionFor(board: LichessBroadcastBoard) =
    LichessBroadcastSelection(
        tournamentName = preview.tournamentName,
        roundId = preview.roundId,
        roundName = preview.roundName,
        gameId = board.gameId,
        boardNumber = board.boardNumber,
        fen = board.fen,
        lastMoveUci = board.lastMoveUci,
        white = board.white,
        black = board.black,
        isOngoing = board.isOngoing,
        result = board.result
    )

/**
 * Public, unauthenticated reads from Lichess's broadcast API. A tournament
 * relayed by Trainer Tournament Manager uses this same contract, so Trainer
 * Fish does not need a separate viewer for Trainer-hosted events.
 */
internal class LichessBroadcastApi {
    suspend fun loadLiveBroadcasts(): List<LichessBroadcastPreview> = withContext(Dispatchers.IO) {
        val root = getJson(TOP_BROADCASTS_URL)
        val active = root.optJSONArray("active") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until active.length()) {
                val item = active.optJSONObject(index) ?: continue
                val tour = item.optJSONObject("tour") ?: continue
                val round = item.optJSONObject("round") ?: continue
                if (!round.optBoolean("ongoing", false)) continue

                val tournamentId = tour.optString("id").trim()
                val tournamentName = tour.optString("name").trim()
                val roundId = round.optString("id").trim()
                if (tournamentId.isBlank() || tournamentName.isBlank() || roundId.isBlank()) continue

                val info = tour.optJSONObject("info")
                add(
                    LichessBroadcastPreview(
                        tournamentId = tournamentId,
                        tournamentName = tournamentName,
                        roundId = roundId,
                        roundName = round.optString("name").trim().ifBlank { "Current round" },
                        tier = tour.optInt("tier", 0),
                        location = info?.optString("location")?.trim()?.takeIf { it.isNotBlank() },
                        playersSummary = info?.optString("players")?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }.distinctBy { it.roundId }
            .sortedWith(compareByDescending<LichessBroadcastPreview> { it.tier }.thenBy { it.tournamentName })
    }

    suspend fun loadRound(preview: LichessBroadcastPreview): LichessBroadcastRound =
        withContext(Dispatchers.IO) {
            val root = getJson("$ROUND_DETAILS_BASE/${preview.roundId}")
            val games = root.optJSONArray("games")
                ?: return@withContext LichessBroadcastRound(preview, emptyList())
            val boards = buildList {
                for (index in 0 until games.length()) {
                    val game = games.optJSONObject(index) ?: continue
                    val gameId = game.optString("id").trim()
                    if (gameId.isBlank()) continue
                    val players = game.optJSONArray("players")
                    add(
                        LichessBroadcastBoard(
                            gameId = gameId,
                            boardNumber = index + 1,
                            fen = game.optString("fen").trim().ifBlank { LICHESS_TV_START_FEN },
                            lastMoveUci = game.optString("lastMove").trim().takeIf { it.isNotBlank() },
                            white = parsePlayer(players?.optJSONObject(0), "White"),
                            black = parsePlayer(players?.optJSONObject(1), "Black"),
                            status = game.optString("status").trim().ifBlank { "*" }
                        )
                    )
                }
            }
            LichessBroadcastRound(preview, boards)
        }

    private fun parsePlayer(item: JSONObject?, fallbackName: String): LichessTvPlayer =
        LichessTvPlayer(
            name = item?.optString("name")?.trim().orEmpty().ifBlank { fallbackName },
            title = item?.optString("title")?.trim()?.takeIf { it.isNotBlank() },
            rating = item.optIntOrNull("rating"),
            // Broadcast clocks are represented in centiseconds.
            seconds = item.optIntOrNull("clock")?.div(100),
            fideId = item.optLongOrNull("fideId"),
            federation = item?.optString("fed")?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            lichessUsername = item?.optString("username")?.trim()?.takeIf { it.isNotBlank() }
        )

    private fun getJson(url: String): JSONObject {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                useCaches = false
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Broadcast directory returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun JSONObject?.optIntOrNull(name: String): Int? =
        this?.takeIf { it.has(name) && !it.isNull(name) }?.optInt(name)

    private fun JSONObject?.optLongOrNull(name: String): Long? =
        this?.takeIf { it.has(name) && !it.isNull(name) }?.optLong(name)

    private companion object {
        const val TOP_BROADCASTS_URL = "https://lichess.org/api/broadcast/top"
        const val ROUND_DETAILS_BASE = "https://lichess.org/api/broadcast/-/-"
        const val USER_AGENT = "TrainerFish/5.0 (com.tonorbe.trainerfish)"
    }
}
