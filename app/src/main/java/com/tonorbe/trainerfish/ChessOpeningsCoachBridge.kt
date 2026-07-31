package com.tonorbe.trainerfish

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

enum class CocTarget(val extraValue: String) {
    OPENING_EXPLORER("opening_explorer"),
    PGN_READER("pgn_reader")
}

private const val COC_PACKAGE = "com.tonorbe.chessopeningscoach"
private const val COC_EXTRA_TARGET = "trainerfish_coc_target"

fun Context.openChessOpeningsCoach(
    target: CocTarget,
    pgnUri: Uri? = null
): Boolean {
    val launchIntent = packageManager.getLaunchIntentForPackage(COC_PACKAGE)
        ?: return false

    launchIntent.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(COC_EXTRA_TARGET, target.extraValue)

        if (pgnUri != null) {
            data = pgnUri
            putExtra(Intent.EXTRA_STREAM, pgnUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    return runCatching {
        startActivity(launchIntent)
        true
    }.getOrDefault(false)
}

fun Context.openChessOpeningsCoachPlayStore() {
    val marketUri = Uri.parse("market://details?id=$COC_PACKAGE")
    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$COC_PACKAGE")

    val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(webIntent) }.onFailure {
            Toast.makeText(this, "Could not open Google Play.", Toast.LENGTH_LONG).show()
        }
    }
}