package com.tonorbe.trainerfish.playgames

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import com.tonorbe.trainerfish.CycleBank
import com.tonorbe.trainerfish.ProfilePrefs
import com.tonorbe.trainerfish.R
import com.tonorbe.trainerfish.TacticsShard
import com.tonorbe.trainerfish.WoodpeckerStore
import com.tonorbe.trainerfish.billing.BillingManager
import java.util.concurrent.Executors
import kotlin.math.max

data class TrainerFishPlayGamesUiState(
    val isConfigured: Boolean = false,
    val isChecking: Boolean = false,
    val isAuthenticated: Boolean = false,
    val playerName: String? = null,
    val playerTitle: String? = null,
    val playerIconUri: Uri? = null,
    val statusMessage: String? = null
)

data class TrainerFishLeaderboardUpdate(
    val currentElo: Int,
    val puzzleId: String,
    val puzzleRating: Int,
    val isPerfect: Boolean,
    val isFirstCompletion: Boolean,
    val isFirstPerfectMastery: Boolean,
    val cycleCompleted: Boolean
)

object TrainerFishPlayGamesConfig {
    private val leaderboardIds = intArrayOf(
        R.string.leaderboard_highest_trainer_fish_elo,
        R.string.leaderboard_most_puzzles_solved,
        R.string.leaderboard_highestrated_puzzle_solved_perfectly,
        R.string.leaderboard_most_different_puzzles_solved,
        R.string.leaderboard_most_beginner_puzzles_mastered_perfectly,
        R.string.leaderboard_most_easy_puzzles_mastered_perfectly,
        R.string.leaderboard_most_intermediate_puzzles_mastered_perfectly,
        R.string.leaderboard_most_difficult_puzzles_mastered_perfectly,
        R.string.leaderboard_most_masterclass_puzzles_mastered_perfectly,
        R.string.leaderboard_most_grandmaster_puzzles_mastered_perfectly,
        R.string.leaderboard_longest_perfect_streak,
        R.string.leaderboard_most_woodpecker_cycles_completed
    )

    fun isConfigured(context: Context): Boolean {
        val projectId = context.getString(R.string.app_id).trim()
        val validProjectId = projectId.isNotBlank() &&
            projectId.all(Char::isDigit) &&
            projectId.any { it != '0' }

        return validProjectId && leaderboardIds.all { resId ->
            context.getString(resId).isNotBlank()
        }
    }
}

class TrainerFishPlayGamesController(
    private val activity: Activity
) {
    private val configured = TrainerFishPlayGamesConfig.isConfigured(activity)
    private val stats = TrainerFishLeaderboardStore(activity)
    private val statsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TrainerFishLeaderboardStats").apply { isDaemon = true }
    }
    @Volatile
    private var statsReady = false
    @Volatile
    private var proEntitled = false
    private var signInInProgress = false

    var uiState by mutableStateOf(
        TrainerFishPlayGamesUiState(
            isConfigured = configured,
            isChecking = configured,
            statusMessage = if (configured) null else CONFIGURATION_MESSAGE
        )
    )
        private set

    init {
        val currentElo = ProfilePrefs(activity).eloRating
        statsExecutor.execute {
            stats.bootstrapFromExistingProgress(currentElo)
            statsReady = true
            activity.runOnUiThread(::flushBestScores)
        }
    }

    fun refreshAuthentication(onAuthenticated: (() -> Unit)? = null) {
        if (!proEntitled || !BillingManager.isPro.value) {
            uiState = uiState.copy(
                isChecking = false,
                isAuthenticated = false,
                playerName = null,
                playerTitle = null,
                playerIconUri = null,
                statusMessage = null
            )
            return
        }
        if (!configured) {
            uiState = uiState.copy(
                isChecking = false,
                isAuthenticated = false,
                statusMessage = CONFIGURATION_MESSAGE
            )
            return
        }
        if (signInInProgress) return

        uiState = uiState.copy(isChecking = true, statusMessage = null)
        PlayGames.getGamesSignInClient(activity)
            .isAuthenticated()
            .addOnCompleteListener { task ->
                val authenticated =
                    task.isSuccessful && task.result?.isAuthenticated == true

                if (authenticated) {
                    loadPlayerProfile(onAuthenticated)
                } else {
                    uiState = uiState.copy(
                        isChecking = false,
                        isAuthenticated = false,
                        playerName = null,
                        playerTitle = null,
                        playerIconUri = null,
                        statusMessage = authenticationFailureMessage(task.exception, null)
                    )
                }
            }
    }

    fun recordAndSubmit(update: TrainerFishLeaderboardUpdate) {
        statsExecutor.execute {
            // Free users keep accumulating the same local statistics. If they later
            // unlock Pro, flushBestScores() publishes their best current totals.
            stats.record(update)
            activity.runOnUiThread { submitScoresChangedBy(update) }
        }
    }

    fun setProEntitlement(isPro: Boolean) {
        proEntitled = isPro
        // Leaderboard browsing and score publication are the app's full Pro gate.
        refreshAuthentication()
    }

    fun shutdown() {
        statsExecutor.shutdown()
    }

    fun showLeaderboards() {
        if (!proEntitled || !BillingManager.isPro.value) {
            uiState = uiState.copy(statusMessage = "TrainerFish Leaderboards require Pro")
            return
        }
        if (!configured) {
            uiState = uiState.copy(statusMessage = CONFIGURATION_MESSAGE)
            return
        }

        if (!uiState.isAuthenticated) {
            uiState = uiState.copy(statusMessage = "Sign in to view leaderboards")
            requestSignIn(::launchLeaderboards)
            return
        }

        flushBestScores()
        launchLeaderboards()
    }

    private fun requestSignIn(onAuthenticated: (() -> Unit)? = null) {
        if (!configured || signInInProgress) return

        signInInProgress = true
        uiState = uiState.copy(isChecking = true, statusMessage = null)

        PlayGames.getGamesSignInClient(activity)
            .signIn()
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.isAuthenticated == true) {
                    loadPlayerProfile(onAuthenticated)
                } else {
                    signInInProgress = false
                    uiState = uiState.copy(
                        isChecking = false,
                        isAuthenticated = false,
                        statusMessage = authenticationFailureMessage(
                            task.exception,
                            "Play Games sign-in was not completed"
                        )
                    )
                }
            }
    }

    private fun loadPlayerProfile(onAuthenticated: (() -> Unit)? = null) {
        PlayGames.getPlayersClient(activity)
            .getCurrentPlayer()
            .addOnSuccessListener { player ->
                signInInProgress = false
                uiState = TrainerFishPlayGamesUiState(
                    isConfigured = true,
                    isChecking = false,
                    isAuthenticated = true,
                    playerName = player.displayName,
                    playerTitle = player.title?.takeIf { it.isNotBlank() },
                    playerIconUri = player.iconImageUri
                )
                flushBestScores()
                onAuthenticated?.invoke()
            }
            .addOnFailureListener { error ->
                signInInProgress = false
                uiState = TrainerFishPlayGamesUiState(
                    isConfigured = true,
                    isChecking = false,
                    isAuthenticated = true,
                    statusMessage = error.localizedMessage
                        ?: "Signed in, but the player profile could not be loaded"
                )
                flushBestScores()
                onAuthenticated?.invoke()
            }
    }

    private fun launchLeaderboards() {
        PlayGames.getLeaderboardsClient(activity)
            .getAllLeaderboardsIntent()
            .addOnSuccessListener { intent ->
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, RC_LEADERBOARDS)
            }
            .addOnFailureListener { error ->
                uiState = uiState.copy(
                    statusMessage = error.localizedMessage
                        ?: "Leaderboards are temporarily unavailable"
                )
            }
    }

    private fun flushBestScores() {
        if (!proEntitled || !BillingManager.isPro.value || !configured || !uiState.isAuthenticated || !statsReady) return

        val snapshot = stats.snapshot()
        val client = PlayGames.getLeaderboardsClient(activity)

        submitConfiguredScore(
            client,
            R.string.leaderboard_highest_trainer_fish_elo,
            snapshot.highestElo.toLong()
        )
        submitConfiguredScore(
            client,
            R.string.leaderboard_most_puzzles_solved,
            snapshot.totalPuzzlesSolved
        )
        submitConfiguredScore(
            client,
            R.string.leaderboard_highestrated_puzzle_solved_perfectly,
            snapshot.highestPuzzleRating.toLong(),
            snapshot.highestPuzzleId
        )
        submitConfiguredScore(
            client,
            R.string.leaderboard_most_different_puzzles_solved,
            snapshot.differentPuzzlesSolved.toLong()
        )
        TacticsShard.values().forEach { difficulty ->
            submitConfiguredScore(
                client,
                difficulty.perfectMasteryLeaderboardResource(),
                snapshot.perfectPuzzleMasteries.getValue(difficulty).toLong()
            )
        }
        submitConfiguredScore(
            client,
            R.string.leaderboard_longest_perfect_streak,
            snapshot.longestPerfectStreak.toLong()
        )
        submitConfiguredScore(
            client,
            R.string.leaderboard_most_woodpecker_cycles_completed,
            snapshot.cyclesCompleted
        )
    }

    private fun submitScoresChangedBy(update: TrainerFishLeaderboardUpdate) {
        if (!proEntitled || !BillingManager.isPro.value || !configured || !uiState.isAuthenticated || !statsReady) return

        val snapshot = stats.snapshot()
        val client = PlayGames.getLeaderboardsClient(activity)

        submitConfiguredScore(
            client,
            R.string.leaderboard_most_puzzles_solved,
            snapshot.totalPuzzlesSolved
        )

        if (update.currentElo >= snapshot.highestElo) {
            submitConfiguredScore(
                client,
                R.string.leaderboard_highest_trainer_fish_elo,
                snapshot.highestElo.toLong()
            )
        }

        if (update.isFirstCompletion) {
            submitConfiguredScore(
                client,
                R.string.leaderboard_most_different_puzzles_solved,
                snapshot.differentPuzzlesSolved.toLong()
            )
        }

        if (update.isPerfect && update.puzzleRating >= snapshot.highestPuzzleRating) {
            submitConfiguredScore(
                client,
                R.string.leaderboard_highestrated_puzzle_solved_perfectly,
                snapshot.highestPuzzleRating.toLong(),
                snapshot.highestPuzzleId
            )
        }

        if (update.isFirstPerfectMastery) {
            val difficulty = TacticsShard.forRating(update.puzzleRating)
            submitConfiguredScore(
                client,
                difficulty.perfectMasteryLeaderboardResource(),
                snapshot.perfectPuzzleMasteries.getValue(difficulty).toLong()
            )
        }

        if (update.isPerfect) {
            submitConfiguredScore(
                client,
                R.string.leaderboard_longest_perfect_streak,
                snapshot.longestPerfectStreak.toLong()
            )
        }

        if (update.cycleCompleted) {
            submitConfiguredScore(
                client,
                R.string.leaderboard_most_woodpecker_cycles_completed,
                snapshot.cyclesCompleted
            )
        }
    }

    private fun submitConfiguredScore(
        client: LeaderboardsClient,
        @StringRes leaderboardIdRes: Int,
        score: Long,
        tag: String? = null
    ) {
        // Defense in depth: even a future caller cannot publish a Free user's score.
        if (!proEntitled || !BillingManager.isPro.value) return
        if (score <= 0L) return
        val leaderboardId = activity.getString(leaderboardIdRes).trim()
        if (leaderboardId.isBlank()) return

        val safeTag = tag
            ?.filter { it.isLetterOrDigit() || it in "-._~" }
            ?.take(MAX_SCORE_TAG_LENGTH)
            ?.takeIf { it.isNotBlank() }

        if (safeTag == null) {
            client.submitScore(leaderboardId, score)
        } else {
            client.submitScore(leaderboardId, score, safeTag)
        }
    }

    private fun authenticationFailureMessage(
        error: Exception?,
        fallback: String?
    ): String? {
        val detail = error?.localizedMessage?.takeIf { it.isNotBlank() }
        val statusCode = (error as? ApiException)?.statusCode

        return when {
            detail != null && statusCode != null -> "$detail (Play Games code $statusCode)"
            detail != null -> detail
            statusCode != null -> "Play Games sign-in failed (code $statusCode)"
            else -> fallback
        }
    }

    companion object {
        private const val MAX_SCORE_TAG_LENGTH = 64
        private const val RC_LEADERBOARDS = 9004
        private const val CONFIGURATION_MESSAGE =
            "Google Play Games setup is waiting for the TrainerFish project and leaderboard IDs."
    }
}

private data class TrainerFishLeaderboardSnapshot(
    val highestElo: Int,
    val totalPuzzlesSolved: Long,
    val highestPuzzleRating: Int,
    val highestPuzzleId: String?,
    val differentPuzzlesSolved: Int,
    val perfectPuzzleMasteries: Map<TacticsShard, Int>,
    val longestPerfectStreak: Int,
    val cyclesCompleted: Long
)

private class TrainerFishLeaderboardStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun bootstrapFromExistingProgress(currentElo: Int) {
        if (prefs.getBoolean(KEY_BOOTSTRAPPED, false)) {
            val editor = prefs.edit()
            if (currentElo > prefs.getInt(KEY_HIGHEST_ELO, 0)) {
                editor.putInt(KEY_HIGHEST_ELO, currentElo)
            }

            editor.putInt(
                KEY_DIFFERENT_PUZZLES_SOLVED,
                max(
                    prefs.getInt(LEGACY_KEY_UNIQUE_PUZZLES_SOLVED, 0),
                    prefs.getInt(KEY_DIFFERENT_PUZZLES_SOLVED, 0)
                )
            )

            if (!prefs.getBoolean(KEY_DIFFICULTY_MASTERIES_BOOTSTRAPPED, false)) {
                val history = WoodpeckerStore(appContext).leaderboardHistory()
                TacticsShard.values().forEach { difficulty ->
                    val key = difficulty.perfectMasteryPreferenceKey()
                    editor.putInt(
                        key,
                        max(history.perfectPuzzleMasteries.getValue(difficulty), prefs.getInt(key, 0))
                    )
                }
                if (history.highestPerfectPuzzleRating >
                    prefs.getInt(KEY_HIGHEST_PUZZLE_RATING, 0)
                ) {
                    editor
                        .putInt(KEY_HIGHEST_PUZZLE_RATING, history.highestPerfectPuzzleRating)
                        .putString(KEY_HIGHEST_PUZZLE_ID, history.highestPerfectPuzzleId)
                }
                editor.putBoolean(KEY_DIFFICULTY_MASTERIES_BOOTSTRAPPED, true)
            }

            if (!prefs.getBoolean(KEY_CYCLES_BOOTSTRAPPED, false)) {
                editor
                    .putLong(
                        KEY_CYCLES_COMPLETED,
                        max(
                            recoverCompletedWoodpeckerCycles(appContext),
                            prefs.getLong(KEY_CYCLES_COMPLETED, 0L)
                        )
                    )
                    .putBoolean(KEY_CYCLES_BOOTSTRAPPED, true)
            }

            editor.apply()
            return
        }

        val history = WoodpeckerStore(appContext).leaderboardHistory()
        val recordedCycles = recoverCompletedWoodpeckerCycles(appContext)
        val existingHighestRating = prefs.getInt(KEY_HIGHEST_PUZZLE_RATING, 0)

        val editor = prefs.edit()
            .putBoolean(KEY_BOOTSTRAPPED, true)
            .putBoolean(KEY_DIFFICULTY_MASTERIES_BOOTSTRAPPED, true)
            .putBoolean(KEY_CYCLES_BOOTSTRAPPED, true)
            .putInt(KEY_HIGHEST_ELO, max(currentElo, prefs.getInt(KEY_HIGHEST_ELO, 0)))
            .putLong(
                KEY_TOTAL_PUZZLES_SOLVED,
                max(history.totalRecordedSolves, prefs.getLong(KEY_TOTAL_PUZZLES_SOLVED, 0L))
            )
            .putInt(
                KEY_DIFFERENT_PUZZLES_SOLVED,
                max(
                    history.differentPuzzlesSolved,
                    max(
                        prefs.getInt(LEGACY_KEY_UNIQUE_PUZZLES_SOLVED, 0),
                        prefs.getInt(KEY_DIFFERENT_PUZZLES_SOLVED, 0)
                    )
                )
            )
            .putInt(
                KEY_HIGHEST_PUZZLE_RATING,
                max(history.highestPerfectPuzzleRating, existingHighestRating)
            )
            .putLong(
                KEY_CYCLES_COMPLETED,
                max(recordedCycles, prefs.getLong(KEY_CYCLES_COMPLETED, 0L))
            )

        if (history.highestPerfectPuzzleRating > existingHighestRating) {
            editor.putString(KEY_HIGHEST_PUZZLE_ID, history.highestPerfectPuzzleId)
        }

        TacticsShard.values().forEach { difficulty ->
            val key = difficulty.perfectMasteryPreferenceKey()
            editor.putInt(
                key,
                max(history.perfectPuzzleMasteries.getValue(difficulty), prefs.getInt(key, 0))
            )
        }

        editor.apply()
    }

    fun record(update: TrainerFishLeaderboardUpdate) {
        bootstrapFromExistingProgress(update.currentElo)

        val oldHighestRating = prefs.getInt(KEY_HIGHEST_PUZZLE_RATING, 0)
        val currentStreak = if (update.isPerfect) {
            prefs.getInt(KEY_CURRENT_PERFECT_STREAK, 0) + 1
        } else {
            0
        }

        val editor = prefs.edit()
            .putInt(KEY_HIGHEST_ELO, max(update.currentElo, prefs.getInt(KEY_HIGHEST_ELO, 0)))
            .putLong(
                KEY_TOTAL_PUZZLES_SOLVED,
                prefs.getLong(KEY_TOTAL_PUZZLES_SOLVED, 0L) + 1L
            )
            .putInt(KEY_CURRENT_PERFECT_STREAK, currentStreak)
            .putInt(
                KEY_LONGEST_PERFECT_STREAK,
                max(currentStreak, prefs.getInt(KEY_LONGEST_PERFECT_STREAK, 0))
            )

        if (update.isFirstCompletion) {
            editor.putInt(
                KEY_DIFFERENT_PUZZLES_SOLVED,
                prefs.getInt(KEY_DIFFERENT_PUZZLES_SOLVED, 0) + 1
            )
        }

        if (update.isFirstPerfectMastery) {
            val masteryKey = TacticsShard.forRating(update.puzzleRating)
                .perfectMasteryPreferenceKey()
            editor.putInt(masteryKey, prefs.getInt(masteryKey, 0) + 1)
        }

        if (update.isPerfect && update.puzzleRating > oldHighestRating) {
            editor
                .putInt(KEY_HIGHEST_PUZZLE_RATING, update.puzzleRating)
                .putString(KEY_HIGHEST_PUZZLE_ID, update.puzzleId)
        }

        if (update.cycleCompleted) {
            editor.putLong(
                KEY_CYCLES_COMPLETED,
                prefs.getLong(KEY_CYCLES_COMPLETED, 0L) + 1L
            )
        }

        editor.apply()
    }

    fun snapshot(): TrainerFishLeaderboardSnapshot = TrainerFishLeaderboardSnapshot(
        highestElo = prefs.getInt(KEY_HIGHEST_ELO, 0),
        totalPuzzlesSolved = prefs.getLong(KEY_TOTAL_PUZZLES_SOLVED, 0L),
        highestPuzzleRating = prefs.getInt(KEY_HIGHEST_PUZZLE_RATING, 0),
        highestPuzzleId = prefs.getString(KEY_HIGHEST_PUZZLE_ID, null),
        differentPuzzlesSolved = prefs.getInt(KEY_DIFFERENT_PUZZLES_SOLVED, 0),
        perfectPuzzleMasteries = TacticsShard.values().associateWith { difficulty ->
            prefs.getInt(difficulty.perfectMasteryPreferenceKey(), 0)
        },
        longestPerfectStreak = prefs.getInt(KEY_LONGEST_PERFECT_STREAK, 0),
        cyclesCompleted = prefs.getLong(KEY_CYCLES_COMPLETED, 0L)
    )

    companion object {
        private const val PREFS_NAME = "trainerfish_leaderboard_stats_v1"
        private const val KEY_BOOTSTRAPPED = "bootstrapped"
        private const val KEY_DIFFICULTY_MASTERIES_BOOTSTRAPPED =
            "difficulty_masteries_bootstrapped"
        private const val KEY_CYCLES_BOOTSTRAPPED = "cycles_bootstrapped"
        private const val KEY_HIGHEST_ELO = "highest_elo"
        private const val KEY_TOTAL_PUZZLES_SOLVED = "total_puzzles_solved"
        private const val KEY_HIGHEST_PUZZLE_RATING = "highest_puzzle_rating"
        private const val KEY_HIGHEST_PUZZLE_ID = "highest_puzzle_id"
        private const val KEY_DIFFERENT_PUZZLES_SOLVED = "different_puzzles_solved"
        private const val LEGACY_KEY_UNIQUE_PUZZLES_SOLVED = "unique_puzzles_solved"
        private const val KEY_CURRENT_PERFECT_STREAK = "current_perfect_streak"
        private const val KEY_LONGEST_PERFECT_STREAK = "longest_perfect_streak"
        private const val KEY_CYCLES_COMPLETED = "cycles_completed"
    }
}

private fun recoverCompletedWoodpeckerCycles(context: Context): Long {
    val cycleBank = CycleBank(context, "tactics")
    return cycleBank.list().sumOf { cycleId ->
        val cycle = cycleBank.prefs(cycleId)
        val completedEarlierPasses = (cycle.cycleId - 1).coerceAtLeast(0).toLong()
        val target = (cycle.size.takeIf { it > 0 } ?: 25).coerceIn(1, 500)
        val solvedInCurrentPass = cycle.solvedCsv
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .toSet()
            .size
        completedEarlierPasses + if (solvedInCurrentPass >= target) 1L else 0L
    }
}

@StringRes
private fun TacticsShard.perfectMasteryLeaderboardResource(): Int = when (this) {
    TacticsShard.BEGINNER -> R.string.leaderboard_most_beginner_puzzles_mastered_perfectly
    TacticsShard.EASY -> R.string.leaderboard_most_easy_puzzles_mastered_perfectly
    TacticsShard.MEDIUM -> R.string.leaderboard_most_intermediate_puzzles_mastered_perfectly
    TacticsShard.DIFFICULT -> R.string.leaderboard_most_difficult_puzzles_mastered_perfectly
    TacticsShard.MASTERCLASS -> R.string.leaderboard_most_masterclass_puzzles_mastered_perfectly
    TacticsShard.GRANDMASTER -> R.string.leaderboard_most_grandmaster_puzzles_mastered_perfectly
}

private fun TacticsShard.perfectMasteryPreferenceKey(): String = when (this) {
    TacticsShard.BEGINNER -> "perfect_masteries_beginner"
    TacticsShard.EASY -> "perfect_masteries_easy"
    TacticsShard.MEDIUM -> "perfect_masteries_intermediate"
    TacticsShard.DIFFICULT -> "perfect_masteries_difficult"
    TacticsShard.MASTERCLASS -> "perfect_masteries_masterclass"
    TacticsShard.GRANDMASTER -> "perfect_masteries_grandmaster"
}
