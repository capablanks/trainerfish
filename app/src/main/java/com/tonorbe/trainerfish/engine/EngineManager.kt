package com.tonorbe.trainerfish.engine

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL


private const val TAG = "TrainerFish"

object EngineManager {
    private const val SUBDIR = "engines"

    private fun engineDir(ctx: Context): File =
        File(ctx.filesDir, SUBDIR).apply { mkdirs() }

    // Old location some OEMs mount as noexec. We migrate anything found here.
    private fun legacyDir(ctx: Context): File =
        File(ctx.codeCacheDir, SUBDIR)

    private fun arch(): String {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        return when {
            abis.any { it.contains("arm64") } -> "arm64"
            abis.any { it.contains("armeabi-v7a") || it.contains("armv7") } -> "armv7"
            else -> "unknown"
        }
    }

    private fun builtinName(): String = when (arch()) {
        "arm64" -> "stockfish17_arm64"
        "armv7" -> "stockfish17_armv7"
        else    -> "stockfish_unknown"
    }

    private fun fileBuiltin(ctx: Context) = File(engineDir(ctx), builtinName())
    private fun fileLatest(ctx: Context)  = File(engineDir(ctx), "stockfish_latest")

    /** Public: resolve path for the selected source, or null if unavailable. */
    suspend fun resolveSelectedPath(ctx: Context, prefs: EnginePrefs): String? =
        withContext(Dispatchers.IO) {
            when (prefs.source) {
                EngineSource.BUILTIN -> ensureBuiltin(ctx)?.absolutePath
                EngineSource.LATEST  -> ensureLatest(ctx, prefs)?.absolutePath
            }
        }

    /** Ensure bundled engine exists under filesDir/engines and is executable. */
    suspend fun ensureBuiltin(ctx: Context): File? = withContext(Dispatchers.IO) {
        migrateFromCodeCache(ctx)

        val out = fileBuiltin(ctx)
        if (!out.exists() || out.length() == 0L) {
            // Try copying from /assets first...
            val assetPath = "engines/${builtinName()}"
            val copiedFromAssets = runCatching {
                ctx.assets.open(assetPath).use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                }
                true
            }.getOrDefault(false)

            // ...if not in assets, try /res/raw with common names.
            if (!copiedFromAssets && (out.length() == 0L)) {
                val rawId = findRawForArch(ctx, arch()) ?: return@withContext null
                ctx.resources.openRawResource(rawId).use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                }
            }
        }

        makeExecutable(out)
        Log.d(TAG, "engine prepared at ${out.absolutePath}, size=${out.length()}, exec=${out.canExecute()}")
        out
    }

    /** Ensure downloaded engine exists under filesDir/engines and is executable. */
    suspend fun ensureLatest(ctx: Context, prefs: EnginePrefs): File? = withContext(Dispatchers.IO) {
        migrateFromCodeCache(ctx)

        val url = when (arch()) {
            "arm64" -> prefs.latestUrlArm64
            "armv7" -> prefs.latestUrlArmv7
            else    -> ""
        }
        if (url.isBlank()) return@withContext null

        val out = fileLatest(ctx)
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.inputStream.use { ins ->
                out.outputStream().use { outs -> ins.copyTo(outs) }
            }
            conn.disconnect()
            makeExecutable(out)
            Log.d(TAG, "engine downloaded to ${out.absolutePath}, size=${out.length()}, exec=${out.canExecute()}")
            out
        } catch (t: Throwable) {
            Log.e(TAG, "engine download failed: ${t.message}")
            null
        }
    }

    // ---------- helpers ----------

    private fun findRawForArch(ctx: Context, arch: String): Int? {
        fun id(n: String) = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
        val candidates = when (arch) {
            "arm64" -> listOf(
                "stockfish17_arm64", "stockfish17_arm64_pgo",
                "stockfish10_arm64_pgo", "stockfish10_arm64"
            )
            "armv7" -> listOf("stockfish17_armv7", "stockfish10_armv7")
            else -> emptyList()
        }
        for (name in candidates) {
            val r = id(name)
            if (r != 0) return r
        }
        return null
    }


    private fun makeExecutable(f: File) {
        try {
            // 0755 (octal) == 0x1ED (hex) == 493 (decimal)
            Os.chmod(f.absolutePath, 0x1ED)   // or use 493
        } catch (_: Throwable) {
            // Fallback if chmod isn't allowed
            f.setReadable(true, false)
            f.setWritable(true, true)
            f.setExecutable(true, false)
        }
    }



    /** Move any old engines from code_cache/engines -> files/engines (OPPO noexec fix). */
    private fun migrateFromCodeCache(ctx: Context) {
        val legacy = legacyDir(ctx)
        if (!legacy.exists()) return
        val dest = engineDir(ctx)
        legacy.listFiles()?.forEach { lf ->
            val df = File(dest, lf.name)
            if (!df.exists() || df.length() == 0L) {
                runCatching { lf.copyTo(df, overwrite = false) }
                    .onSuccess { makeExecutable(df) }
            }
        }
    }
}
