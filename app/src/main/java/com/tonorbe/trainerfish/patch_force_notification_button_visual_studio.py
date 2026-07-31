from pathlib import Path
import re

ROOT = Path.cwd()
landing = ROOT / 'TrainerFishLanding.kt'
daily = ROOT / 'TrainerFishDailyNotifications.kt'

if not landing.exists():
    raise SystemExit('TrainerFishLanding.kt not found. Run this in app/src/main/java/com/tonorbe/trainerfish')
if not daily.exists():
    raise SystemExit('TrainerFishDailyNotifications.kt not found. Run this in app/src/main/java/com/tonorbe/trainerfish')

text = landing.read_text(encoding='utf-8')
orig = text

# Backup once
backup = landing.with_suffix(landing.suffix + '.force_notification_button_backup')
if not backup.exists():
    backup.write_text(text, encoding='utf-8')

# Ensure imports needed by the temporary button.
if 'import androidx.compose.material3.OutlinedButton' not in text:
    marker = 'import androidx.compose.material3.MaterialTheme\n'
    if marker in text:
        text = text.replace(marker, marker + 'import androidx.compose.material3.OutlinedButton\n', 1)
    else:
        text = text.replace('import androidx.compose.material3.AlertDialog\n', 'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.OutlinedButton\n', 1)

if 'import android.widget.Toast' not in text:
    # Usually the file already imports many Android/Compose symbols. Put this after package imports safely.
    m = re.search(r'package\s+com\.tonorbe\.trainerfish\s*\n', text)
    if m:
        insert_at = m.end()
        text = text[:insert_at] + '\nimport android.widget.Toast\n' + text[insert_at:]
    else:
        text = 'import android.widget.Toast\n' + text

# Ensure VisualStudioDialog has LocalContext.
vs_match = re.search(r'(@Composable\s+private\s+fun\s+VisualStudioDialog\s*\([^)]*\)\s*\{)', text, re.S)
if not vs_match:
    raise SystemExit('Could not find VisualStudioDialog function')

body_start = vs_match.end()
body_preview = text[body_start:body_start+400]
if 'val ctx = LocalContext.current' not in body_preview:
    text = text[:body_start] + '\n    val ctx = LocalContext.current\n' + text[body_start:]

# Add the temporary button inside Visual Studio dialog, immediately after the intro text block.
button_block = '''

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(ctx)
                        Toast.makeText(ctx, "Puzzle notification sent", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show puzzle notification")
                }
'''

if 'Show puzzle notification' not in text:
    # Insert after the exact intro text Text() block in VisualStudioDialog.
    intro = '''                Text(
                    text = "Choose your pieces, board colors, pane colors, and app background.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
'''
    if intro in text:
        text = text.replace(intro, intro + button_block, 1)
    else:
        # Fallback: insert before Piece sets section.
        marker = '                VisualSectionTitle("Piece sets")'
        if marker not in text:
            raise SystemExit('Could not find insertion point for notification button')
        text = text.replace(marker, button_block + '\n' + marker, 1)
else:
    # Existing button may not show toast; upgrade it if simple one-liner exists.
    text = text.replace(
        'onClick = { TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(ctx) },',
        'onClick = {\n                        TrainerFishDailyNotificationScheduler.showTrainingNotificationNow(ctx)\n                        Toast.makeText(ctx, "Puzzle notification sent", Toast.LENGTH_SHORT).show()\n                    },'
    )

landing.write_text(text, encoding='utf-8')

# Sanity check daily notification public function exists.
daily_text = daily.read_text(encoding='utf-8')
if 'fun showTrainingNotificationNow(context: Context)' not in daily_text:
    raise SystemExit('TrainerFishDailyNotifications.kt does not contain showTrainingNotificationNow(context). Patch DailyNotifications first.')

print('OK: Visual Studio temporary notification button is present.')
print('Open TrainerFish > Visual Studio > Show puzzle notification.')
print('Reminder: remove this test button before publishing the final build.')
