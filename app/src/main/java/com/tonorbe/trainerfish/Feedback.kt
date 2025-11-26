package com.tonorbe.trainerfish

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

private fun buildSoundPool(): SoundPool =
    SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

class Feedback(
    private val sp: SoundPool?,
    private val correctSample: Int,
    private val wrongSample: Int,
    private val applauseSample: Int,
    private val viewProvider: () -> View?
) {
    /** Controlled by UI mute switch */
    var soundsEnabled: Boolean = true

    // SoundPool loads asynchronously; mark when each sample is ready.
    private val ready = hashSetOf<Int>()

    init {
        sp?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready += sampleId
        }
    }

    private fun isReady(id: Int) = id != 0 && ready.contains(id)

    private fun tap()   { viewProvider()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun press() { viewProvider()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

    fun correct() {
        if (soundsEnabled && isReady(correctSample)) sp?.play(correctSample, 1f, 1f, 1, 0, 1f)
        tap()
    }

    fun wrong() {
        if (soundsEnabled && isReady(wrongSample))   sp?.play(wrongSample,   1f, 1f, 1, 0, 0.95f)
        press()
    }

    fun applause() {
        if (soundsEnabled && isReady(applauseSample)) sp?.play(applauseSample, 1f, 1f, 1, 0, 1f)
    }
}

@Composable
fun rememberFeedback(context: Context): Feedback {
    val view = LocalView.current
    val sp = remember { runCatching { buildSoundPool() }.getOrNull() }

    @SuppressLint("DiscouragedApi")
    fun rawId(name: String): Int = context.resources.getIdentifier(name, "raw", context.packageName)

    // Names to try (any one file will do). Keep your files as lowercase in res/raw.
    val correctId  = listOf("correct", "success", "right").firstNotNullOfOrNull { id ->
        rawId(id).takeIf { it != 0 }
    } ?: 0
    val wrongId    = listOf("incorrect", "wrong", "error").firstNotNullOfOrNull { id ->
        rawId(id).takeIf { it != 0 }
    } ?: 0
    val applauseId = listOf("applause", "clap", "cheer", "applause_short").firstNotNullOfOrNull { id ->
        rawId(id).takeIf { it != 0 }
    } ?: 0

    val correctSample  = remember(sp, correctId)  { if (sp != null && correctId  != 0) sp.load(context, correctId,  1) else 0 }
    val wrongSample    = remember(sp, wrongId)    { if (sp != null && wrongId    != 0) sp.load(context, wrongId,    1) else 0 }
    val applauseSample = remember(sp, applauseId) { if (sp != null && applauseId != 0) sp.load(context, applauseId, 1) else 0 }

    DisposableEffect(sp) { onDispose { sp?.release() } }

    return remember(sp, correctSample, wrongSample, applauseSample, view) {
        Feedback(sp, correctSample, wrongSample, applauseSample) { view }
    }
}
