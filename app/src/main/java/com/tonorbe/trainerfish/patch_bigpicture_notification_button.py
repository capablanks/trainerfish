from pathlib import Path
import shutil

root = Path.cwd()
daily = root / "TrainerFishDailyNotifications.kt"
landing = root / "TrainerFishLanding.kt"

if not daily.exists():
    raise SystemExit("TrainerFishDailyNotifications.kt not found in current folder")
if not landing.exists():
    raise SystemExit("TrainerFishLanding.kt not found in current folder")

for p in (daily, landing):
    bak = p.with_suffix(p.suffix + ".bak_bigpicture_button")
    if not bak.exists():
        shutil.copy2(p, bak)

s = daily.read_text(encoding="utf-8")

# 1) Imports for the generated BigPicture bitmap.
if "import android.graphics.Bitmap" not in s:
    s = s.replace(
        "import android.content.Intent\n",
        "import android.content.Intent\n"
        "import android.graphics.Bitmap\n"
        "import android.graphics.Canvas\n"
        "import android.graphics.Paint\n"
        "import android.graphics.RectF\n"
        "import android.graphics.Typeface\n"
        "import android.graphics.Color as AndroidColor\n"
    )

# 2) Add public trigger in scheduler object.
helper = r'''    fun showTrainingNotificationNow(context: Context) {
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

'''
if "fun showTrainingNotificationNow(context: Context)" not in s:
    anchor = "    fun createNotificationChannel(context: Context) {\n"
    if anchor not in s:
        raise RuntimeError("Could not find createNotificationChannel anchor")
    s = s.replace(anchor, helper + anchor)

# 3) Add bitmap renderer before receiver class.
bitmap_fun = r'''private fun createTrainerFishPuzzleReminderBitmap(quote: String): Bitmap {
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

'''
if "private fun createTrainerFishPuzzleReminderBitmap" not in s:
    anchor = "class TrainerFishDailyNotificationReceiver : BroadcastReceiver() {\n"
    if anchor not in s:
        raise RuntimeError("Could not find receiver class anchor")
    s = s.replace(anchor, bitmap_fun + anchor)

# 4) Make the broadcast receiver use the BigPicture function instead of the old text-only builder.
if "TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(context)" not in s:
    start = s.find("        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)")
    end_marker = "        NotificationManagerCompat.from(context).notify(TF_DAILY_NOTIFICATION_ID, notification)"
    end = s.find(end_marker, start)
    if start == -1 or end == -1:
        raise RuntimeError("Could not find old notification block in receiver")
    end = s.find("\n", end + len(end_marker))
    s = s[:start] + "        TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(context)" + s[end:]

daily.write_text(s, encoding="utf-8")

# 5) Add button to Visual Studio.
s = landing.read_text(encoding="utf-8")
anchor = '''private fun VisualStudioDialog(
    currentPieceSet: String,
    currentBoardTheme: String,
    currentUiBoxTheme: String,
    appThemeKey: String,
    onPieceSet: (String) -> Unit,
    onBoardTheme: (String) -> Unit,
    onUiBoxTheme: (String) -> Unit,
    onAppTheme: (String) -> Unit,
    onClose: () -> Unit
) {
'''
if "val ctx = LocalContext.current\n\n    AlertDialog(" not in s:
    if anchor not in s:
        raise RuntimeError("Could not find VisualStudioDialog anchor")
    s = s.replace(anchor, anchor + "    val ctx = LocalContext.current\n\n")

desc = '''                Text(
                    text = "Choose your pieces, board colors, pane colors, and app background.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
'''
button = '''
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(ctx) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show puzzle notification")
                }
'''
if "Text(\"Show puzzle notification\")" not in s:
    if desc not in s:
        raise RuntimeError("Could not find Visual Studio description anchor")
    s = s.replace(desc, desc + button)

landing.write_text(s, encoding="utf-8")

print("Patched successfully.")
print("- TrainerFishDailyNotifications.kt now posts a BigPicture puzzle notification")
print("- TrainerFishLanding.kt now has Visual Studio > Show puzzle notification")
print("Backups created with .bak_bigpicture_button suffix")
