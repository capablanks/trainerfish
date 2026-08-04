package com.tonorbe.trainerfish

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val FAVORITE_ALERT_PREFS = "chess_tv_favorite_live_alerts"
private const val KEY_ALERTS_ENABLED = "enabled"
private const val KEY_NOTIFIED_GAME_IDS = "notified_game_ids"
private const val FAVORITE_ALERT_CHANNEL_ID = "trainerfish_favorite_live"
private const val FAVORITE_ALERT_CHANNEL_NAME = "Favorite players live"
private const val FAVORITE_ALERT_PERIODIC_WORK = "trainerfish_favorite_live_periodic"
private const val FAVORITE_ALERT_IMMEDIATE_WORK = "trainerfish_favorite_live_now"
private const val EXTRA_FAVORITE_SELECTION_JSON = "tf_favorite_live_selection_json"

internal object ChessTvFavoriteLiveNotificationScheduler {
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FAVORITE_ALERT_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERTS_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(FAVORITE_ALERT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERTS_ENABLED, enabled)
            .apply()
        sync(appContext, runImmediate = enabled)
    }

    fun sync(context: Context, runImmediate: Boolean = false) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        if (!isEnabled(appContext)) {
            workManager.cancelUniqueWork(FAVORITE_ALERT_PERIODIC_WORK)
            workManager.cancelUniqueWork(FAVORITE_ALERT_IMMEDIATE_WORK)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<ChessTvFavoriteLiveWorker>(
            15,
            TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(
            FAVORITE_ALERT_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        if (runImmediate) {
            val immediate = OneTimeWorkRequestBuilder<ChessTvFavoriteLiveWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(
                FAVORITE_ALERT_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                immediate
            )
        }
    }
}

class ChessTvFavoriteLiveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!ChessTvFavoriteLiveNotificationScheduler.isEnabled(applicationContext)) {
            return Result.success()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val profile = ChessTvFollowStore(applicationContext).load()
        if (profile.favorites.isEmpty()) return Result.success()

        val api = LichessBroadcastApi()
        val previews = try {
            api.loadLiveBroadcasts()
        } catch (_: Throwable) {
            return Result.retry()
        }

        val matches = mutableListOf<Pair<ChessTvFavoritePlayer, LichessBroadcastSelection>>()
        previews.forEach { preview ->
            val round = runCatching { api.loadRound(preview) }.getOrNull() ?: return@forEach
            round.boards.asSequence()
                .filter { it.isOngoing }
                .map(round::selectionFor)
                .forEach { selection ->
                    val favorite = profile.favorites.firstOrNull { candidate ->
                        selection.matchesFavorites(listOf(candidate))
                    }
                    if (favorite != null) matches += favorite to selection
                }
        }

        val uniqueMatches = matches.distinctBy { it.second.gameId }
        val activeIds = uniqueMatches.map { it.second.gameId }.toSet()
        val prefs = applicationContext.getSharedPreferences(
            FAVORITE_ALERT_PREFS,
            Context.MODE_PRIVATE
        )
        val previouslyNotified = prefs.getStringSet(KEY_NOTIFIED_GAME_IDS, emptySet())
            ?.toSet()
            .orEmpty()

        uniqueMatches
            .filter { (_, selection) -> selection.gameId !in previouslyNotified }
            .take(MAX_CHESS_TV_FAVORITES)
            .forEach { (favorite, selection) ->
                showFavoriteLiveNotification(favorite, selection)
            }

        prefs.edit().putStringSet(KEY_NOTIFIED_GAME_IDS, activeIds).apply()
        return Result.success()
    }

    private fun showFavoriteLiveNotification(
        favorite: ChessTvFavoritePlayer,
        selection: LichessBroadcastSelection
    ) {
        createFavoriteLiveChannel(applicationContext)
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_FAVORITE_SELECTION_JSON, selection.toFavoriteAlertJson())
        }
        val requestCode = selection.gameId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val matchup = "${selection.white.displayName} vs ${selection.black.displayName}"
        val notification = NotificationCompat.Builder(
            applicationContext,
            FAVORITE_ALERT_CHANNEL_ID
        )
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("${favorite.displayName} is playing live")
            .setContentText("$matchup • ${selection.tournamentName}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$matchup\n${selection.tournamentName} • ${selection.roundName} • Board ${selection.boardNumber}"
                )
            )
            .setContentIntent(pendingIntent)
            .addAction(applicationContext.applicationInfo.icon, "Watch live", pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(requestCode, notification)
    }
}

private fun createFavoriteLiveChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(FAVORITE_ALERT_CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            FAVORITE_ALERT_CHANNEL_ID,
            FAVORITE_ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when one of your saved Chess TV favorites appears in a live broadcast."
            setShowBadge(true)
        }
    )
}

internal fun Intent.chessTvFavoriteLiveSelectionOrNull(): LichessBroadcastSelection? =
    getStringExtra(EXTRA_FAVORITE_SELECTION_JSON)
        ?.takeIf { it.isNotBlank() }
        ?.let(::favoriteAlertSelectionFromJson)

private fun LichessBroadcastSelection.toFavoriteAlertJson(): String = JSONObject().apply {
    put("tournamentName", tournamentName)
    put("roundId", roundId)
    put("roundName", roundName)
    put("gameId", gameId)
    put("boardNumber", boardNumber)
    put("fen", fen)
    lastMoveUci?.let { put("lastMoveUci", it) }
    put("white", white.toFavoriteAlertJson())
    put("black", black.toFavoriteAlertJson())
    put("isOngoing", isOngoing)
    result?.let { put("result", it) }
}.toString()

private fun LichessTvPlayer.toFavoriteAlertJson(): JSONObject = JSONObject().apply {
    put("name", name)
    title?.let { put("title", it) }
    rating?.let { put("rating", it) }
    seconds?.let { put("seconds", it) }
    fideId?.let { put("fideId", it) }
    federation?.let { put("federation", it) }
    lichessUsername?.let { put("lichessUsername", it) }
}

private fun favoriteAlertSelectionFromJson(raw: String): LichessBroadcastSelection? = runCatching {
    val root = JSONObject(raw)
    LichessBroadcastSelection(
        tournamentName = root.getString("tournamentName"),
        roundId = root.getString("roundId"),
        roundName = root.getString("roundName"),
        gameId = root.getString("gameId"),
        boardNumber = root.optInt("boardNumber", 1),
        fen = root.optString("fen").ifBlank { LICHESS_TV_START_FEN },
        lastMoveUci = root.optString("lastMoveUci").takeIf { it.isNotBlank() },
        white = root.getJSONObject("white").favoriteAlertPlayer(),
        black = root.getJSONObject("black").favoriteAlertPlayer(),
        isOngoing = root.optBoolean("isOngoing", true),
        result = root.optString("result").takeIf { it.isNotBlank() }
    )
}.getOrNull()

private fun JSONObject.favoriteAlertPlayer(): LichessTvPlayer = LichessTvPlayer(
    name = optString("name").ifBlank { "Player" },
    title = optString("title").takeIf { it.isNotBlank() },
    rating = optIntOrNull("rating"),
    seconds = optIntOrNull("seconds"),
    fideId = optLongOrNull("fideId"),
    federation = optString("federation").takeIf { it.isNotBlank() },
    lichessUsername = optString("lichessUsername").takeIf { it.isNotBlank() }
)

private fun JSONObject.optIntOrNull(name: String): Int? =
    takeIf { has(name) && !isNull(name) }?.optInt(name)

private fun JSONObject.optLongOrNull(name: String): Long? =
    takeIf { has(name) && !isNull(name) }?.optLong(name)
