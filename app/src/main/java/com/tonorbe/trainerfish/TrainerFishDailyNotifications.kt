package com.tonorbe.trainerfish

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import kotlin.random.Random

private const val TF_NOTIFICATION_CHANNEL_ID = "trainerfish_daily_training"
private const val TF_NOTIFICATION_CHANNEL_NAME = "Daily chess training"
private const val TF_DAILY_NOTIFICATION_ID = 12064
private const val TF_DAILY_NOTIFICATION_REQUEST = 12065
private const val TF_OPEN_APP_REQUEST = 12066
private const val TF_ACTION_DAILY_NOTIFICATION = "com.tonorbe.trainerfish.DAILY_TRAINING_NOTIFICATION"

private val TRAINERFISH_DAILY_QUOTES = listOf(
    "A little chess every day is how calculation becomes instinct.",
    "One puzzle today is one blind spot removed tomorrow.",
    "Champions are built one position at a time.",
    "Train the pattern now. Recognize it instantly later.",
    "Strong players do not guess. They calculate.",
    "Five focused minutes can sharpen your whole chess day.",
    "Solve slowly, learn deeply, climb steadily.",
    "Every tactic you master becomes a weapon over the board.",
    "The board rewards discipline. Train today.",
    "Improve your worst move before chasing your best move.",
    "Good habits win games before the tactics appear.",
    "Do not wait for motivation. Make one move toward mastery.",
    "A missed tactic in training is a saved point in tournament play.",
    "Pattern recognition is earned through repetition.",
    "TrainerFish is ready. Your next puzzle is waiting."
)

object TrainerFishDailyNotificationScheduler {

    fun scheduleDailyNoon(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)

        val intent = Intent(appContext, TrainerFishDailyNotificationReceiver::class.java).apply {
            action = TF_ACTION_DAILY_NOTIFICATION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            TF_DAILY_NOTIFICATION_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextNoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Inexact repeating is battery-friendly and does not require Android 12 exact-alarm permission.
        // Android may deliver it a little after noon depending on Doze/battery state.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextNoon,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, TrainerFishDailyNotificationReceiver::class.java).apply {
            action = TF_ACTION_DAILY_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            TF_DAILY_NOTIFICATION_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    fun showTrainingNotificationNow(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: Intent(appContext, MainActivity::class.java)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.putExtra("tf_open_tactics", true)
        launchIntent.putExtra("tf_auto_continue_cycle", true)

        val openAppIntent = PendingIntent.getActivity(
            appContext,
            TF_OPEN_APP_REQUEST,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quote = TRAINERFISH_DAILY_QUOTES.random(Random(System.currentTimeMillis()))
        val bigPicture = createTrainerFishPuzzleReminderBitmap(quote)

        val notification = NotificationCompat.Builder(appContext, TF_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(appContext.applicationInfo.icon)
            .setContentTitle("Solve this next puzzle")
            .setContentText(quote)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    .setBigContentTitle("Solve this next puzzle")
                    .setSummaryText("Tap to continue your current tactics cycle.")
            )
            .setContentIntent(openAppIntent)
            .addAction(appContext.applicationInfo.icon, "Solve next puzzle", openAppIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(appContext).notify(TF_DAILY_NOTIFICATION_ID, notification)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(TF_NOTIFICATION_CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            TF_NOTIFICATION_CHANNEL_ID,
            TF_NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "A once-a-day chess training reminder from TrainerFish."
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}

private fun createTrainerFishPuzzleReminderBitmap(quote: String): Bitmap {
    val width = 960
    val height = 540
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(8, 12, 20) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(70, 0, 215, 255) }
    canvas.drawCircle(812f, 88f, 210f, glowPaint)
    glowPaint.color = AndroidColor.argb(70, 255, 186, 72)
    canvas.drawCircle(120f, 455f, 230f, glowPaint)

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(25, 33, 48) }
    canvas.drawRoundRect(RectF(28f, 28f, 932f, 512f), 34f, 34f, cardPaint)

    val boardLeft = 52f
    val boardTop = 92f
    val boardSize = 356f
    val sq = boardSize / 8f
    val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(238, 213, 165) }
    val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(170, 125, 103) }
    for (r in 0 until 8) {
        for (c in 0 until 8) {
            canvas.drawRect(
                boardLeft + c * sq,
                boardTop + r * sq,
                boardLeft + (c + 1) * sq,
                boardTop + (r + 1) * sq,
                if ((r + c) % 2 == 0) light else dark
            )
        }
    }

    val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 38f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    fun piece(file: Int, rankFromTop: Int, text: String, white: Boolean) {
        piecePaint.color = if (white) AndroidColor.WHITE else AndroidColor.BLACK
        canvas.drawText(text, boardLeft + file * sq + sq / 2f, boardTop + rankFromTop * sq + sq * 0.72f, piecePaint)
    }
    piece(0, 0, "♜", false); piece(5, 0, "♜", false); piece(7, 1, "♚", false)
    piece(1, 1, "♝", false); piece(4, 1, "♝", false); piece(4, 4, "♞", false)
    piece(7, 3, "♖", true); piece(2, 4, "♗", true); piece(6, 5, "♘", true)
    piece(6, 7, "♔", true); piece(0, 7, "♖", true); piece(2, 7, "♗", true)

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(170, 0, 225, 255)
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(boardLeft + 7.5f * sq, boardTop + 4.18f * sq, boardLeft + 7.5f * sq, boardTop + 2.75f * sq, arrowPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 50f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText("Solve this next puzzle", 450f, 126f, titlePaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(126, 224, 255)
        textSize = 34f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText("Mate in 3  •  Elo 1207", 450f, 178f, subPaint)

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(218, 226, 238)
        textSize = 29f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val cleanQuote = quote.take(42)
    canvas.drawText(cleanQuote, 450f, 248f, bodyPaint)
    canvas.drawText("Tap to continue your current tactics cycle.", 450f, 294f, bodyPaint)

    val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(0, 110, 235) }
    canvas.drawRoundRect(RectF(450f, 352f, 744f, 424f), 22f, 22f, buttonPaint)
    val buttonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 31f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText("Solve next puzzle", 597f, 398f, buttonText)

    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 200, 80)
        textSize = 24f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText("TRAINER FISH 5.0", 450f, 478f, footerPaint)

    return bitmap
}

class TrainerFishDailyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TrainerFishDailyNotificationScheduler.scheduleDailyNoon(context)
            return
        }

        if (action != TF_ACTION_DAILY_NOTIFICATION) return

        TrainerFishDailyNotificationScheduler.createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(context)
    }
}

fun ComponentActivity.requestTrainerFishNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val granted = ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (!granted) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            12067
        )
    }
}
