from pathlib import Path

ROOT = Path.cwd()
landing_path = ROOT / "TrainerFishLanding.kt"
notif_path = ROOT / "TrainerFishDailyNotifications.kt"

if not landing_path.exists():
    raise FileNotFoundError(f"Missing {landing_path}")
if not notif_path.exists():
    raise FileNotFoundError(f"Missing {notif_path}")

landing = landing_path.read_text(encoding="utf-8")
notif = notif_path.read_text(encoding="utf-8")

# 1) Add a small public helper to fire the existing receiver immediately.
helper = r'''
fun Context.showTrainerFishTrainingNotificationNowForScreenshot() {
    val appContext = applicationContext
    TrainerFishDailyNotificationScheduler.createNotificationChannel(appContext)

    val intent = Intent(appContext, TrainerFishDailyNotificationReceiver::class.java).apply {
        action = TF_ACTION_DAILY_NOTIFICATION
    }
    appContext.sendBroadcast(intent)
}
'''.strip()

if "showTrainerFishTrainingNotificationNowForScreenshot" not in notif:
    marker = "\nfun ComponentActivity.requestTrainerFishNotificationPermissionIfNeeded()"
    if marker not in notif:
        raise RuntimeError("Could not find insertion point in TrainerFishDailyNotifications.kt")
    notif = notif.replace(marker, "\n\n" + helper + "\n" + marker, 1)

# 2) Add a callback parameter to VisualStudioDialog.
old_sig = """private fun VisualStudioDialog(\n    currentPieceSet: String,\n    currentBoardTheme: String,\n    currentUiBoxTheme: String,\n    appThemeKey: String,\n    onPieceSet: (String) -> Unit,\n    onBoardTheme: (String) -> Unit,\n    onUiBoxTheme: (String) -> Unit,\n    onAppTheme: (String) -> Unit,\n    onClose: () -> Unit\n)"""
new_sig = """private fun VisualStudioDialog(\n    currentPieceSet: String,\n    currentBoardTheme: String,\n    currentUiBoxTheme: String,\n    appThemeKey: String,\n    onPieceSet: (String) -> Unit,\n    onBoardTheme: (String) -> Unit,\n    onUiBoxTheme: (String) -> Unit,\n    onAppTheme: (String) -> Unit,\n    onShowTrainingNotification: () -> Unit,\n    onClose: () -> Unit\n)"""
if "onShowTrainingNotification: () -> Unit" not in landing:
    if old_sig not in landing:
        raise RuntimeError("Could not patch VisualStudioDialog signature")
    landing = landing.replace(old_sig, new_sig, 1)

# 3) Pass the callback where VisualStudioDialog is called.
old_call_tail = """            onUiBoxTheme = { key ->\n                currentUiBoxTheme = key\n                cosSp.edit().putString(\"ui_box_theme\", key).apply()\n            },\n            onAppTheme = onChangeAppThemeKey,\n            onClose = { showVisualStudio = false }\n        )"""
new_call_tail = """            onUiBoxTheme = { key ->\n                currentUiBoxTheme = key\n                cosSp.edit().putString(\"ui_box_theme\", key).apply()\n            },\n            onAppTheme = onChangeAppThemeKey,\n            onShowTrainingNotification = { ctx.showTrainerFishTrainingNotificationNowForScreenshot() },\n            onClose = { showVisualStudio = false }\n        )"""
if "onShowTrainingNotification = { ctx.showTrainerFishTrainingNotificationNowForScreenshot() }" not in landing:
    if old_call_tail not in landing:
        raise RuntimeError("Could not patch VisualStudioDialog call")
    landing = landing.replace(old_call_tail, new_call_tail, 1)

# 4) Insert the screenshot-only button near the top of Visual Studio.
button_block = r'''
                OutlinedButton(
                    onClick = onShowTrainingNotification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show training notification")
                }

                Text(
                    text = "Temporary screenshot helper. Remove before publishing if you do not want this visible.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(14.dp))
'''.rstrip()

anchor = """                Text(\n                    text = \"Choose your pieces, board colors, pane colors, and app background.\",\n                    fontSize = 13.sp,\n                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)\n                )\n\n                Spacer(Modifier.height(14.dp))"""
replacement = """                Text(\n                    text = \"Choose your pieces, board colors, pane colors, and app background.\",\n                    fontSize = 13.sp,\n                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)\n                )\n\n""" + button_block

if "Show training notification" not in landing:
    if anchor not in landing:
        raise RuntimeError("Could not find top area of VisualStudioDialog")
    landing = landing.replace(anchor, replacement, 1)

landing_path.write_text(landing, encoding="utf-8")
notif_path.write_text(notif, encoding="utf-8")

print("Patched:")
print(f" - {landing_path}")
print(f" - {notif_path}")
print("\nOpen Visual Studio and tap: Show training notification")
