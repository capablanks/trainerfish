// ChessScreen.kt (DROP-IN REPLACEMENT)
package com.tonorbe.trainerfish

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val PUZZLE_DATASET_VERSION = 4

@Composable
fun ChessScreen() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Heavy one-time work off the main thread so UI becomes responsive ASAP.
        launch(Dispatchers.IO) {
            runCatching { migratePuzzleDatasetIfNeeded(context) }
                .onFailure { t -> Log.e("ChessScreen", "Dataset migration failed", t) }

            // Do NOT warm decoded starter puzzles here.
            // Start New Cycle now reads one prebuilt PGN from train_starter_puzzles.json,
            // so background binary loading would only compete with the user's first click.
        }

        // Do NOT preload the opening book here.
        // Let Opening Explorer / Beat-the-Fish load it lazily only when needed.
        // This prevents startup/background loading from competing with tactics JSON/index loads.
    }

    ReplayScreen(
        context = context,
        autoStart = true
    )
}

private fun migratePuzzleDatasetIfNeeded(context: Context) {
    val sp = context.getSharedPreferences("gm_dataset", Context.MODE_PRIVATE)
    val current = sp.getInt("puzzle_dataset_version", 0)
    if (current >= PUZZLE_DATASET_VERSION) return

    try {
        WoodpeckerStore(context).resetAll()
    } catch (t: Throwable) {
        Log.e("ChessScreen", "Failed to reset WoodpeckerStore", t)
    }

    try {
        for (series in listOf(Series.TACTICS)) {
            CycleBank(context, series.id).wipeAll()
        }
    } catch (t: Throwable) {
        Log.e("ChessScreen", "Failed to wipe CycleBank", t)
    }

    try {
        TacticsBookmarkStore(context).clearAll()
    } catch (t: Throwable) {
        Log.e("ChessScreen", "Failed to wipe tactics bookmarks", t)
    }

    sp.edit().putInt("puzzle_dataset_version", PUZZLE_DATASET_VERSION).apply()
    Log.d("ChessScreen", "Puzzle dataset migration to version $PUZZLE_DATASET_VERSION completed")
}
