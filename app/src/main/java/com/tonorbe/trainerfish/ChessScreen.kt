package com.tonorbe.trainerfish

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.tonorbe.trainerfish.opening.BinaryOpeningBook

@Composable
fun ChessScreen() {
    val context = LocalContext.current

    // Pre-load the unified opening book once when the app starts.
    LaunchedEffect(Unit) {
        try {
            BinaryOpeningBook.loadIfNeeded(
                context,
                R.raw.fullbook_d32_all_book_data,
                R.raw.fullbook_d32_all_book_index
            )
            Log.d("ChessScreen", "Unified opening book preloaded")
        } catch (t: Throwable) {
            Log.e("ChessScreen", "Failed to preload opening book", t)
        }
    }

    // Main trainer UI
    ReplayScreen(
        context = context,
        autoStart = true
    )
}
