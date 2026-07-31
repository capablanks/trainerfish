from pathlib import Path
import re

ROOT = Path.cwd()
if not (ROOT / 'Replayscreen.kt').exists() and Path('/mnt/data/Replayscreen.kt').exists():
    ROOT = Path('/mnt/data')

replay_path = ROOT / 'Replayscreen.kt'
beat_path = ROOT / 'BeatFishScreen.kt'

if not replay_path.exists():
    raise SystemExit('Replayscreen.kt not found')
if not beat_path.exists():
    raise SystemExit('BeatFishScreen.kt not found')

# ---------------- Replayscreen.kt: keep Tactics menu item clickable ----------------
text = replay_path.read_text(encoding='utf-8')
backup = replay_path.with_suffix(replay_path.suffix + '.tactics_planner_menu_backup')
backup.write_text(text, encoding='utf-8')

select_pat = re.compile(
    r'''        val selectTactics: \(\) -> Unit = \{\n'''
    r'''            if \(mode != TrainerMode\.WOODPECKER\) \{\n'''
    r'''(?P<body>.*?)'''
    r'''            \}\n'''
    r'''        \}\n\n        val selectEndgame: \(\) -> Unit = \{''',
    re.S
)
match = select_pat.search(text)
if not match:
    raise SystemExit('selectTactics block not found')
body = match.group('body')
new_select = '''        val selectTactics: () -> Unit = {
            if (mode == TrainerMode.WOODPECKER) {
                // Already in Tactics: reopen the Cycle Manager / Training Planner
                // without rebuilding the screen or touching the current fast cycle state.
                loading = false
                preparing = false
                showWelcome = true
                status = "Trainer - choose a cycle or continue."
            } else {
''' + body + '''            }
        }

        val selectEndgame: () -> Unit = {'''
text = text[:match.start()] + new_select + text[match.end():]

old_menu = '''                ColorMenuItem(
                    icon = "T",
                    title = "Tactics",
                    description = "Woodpecker cycles, ratings, streaks, and XP.",
                    color = Color(0xFFEF4444),
                    enabled = mode != TrainerMode.WOODPECKER,
                    onClick = onSelectTactics
                )'''
new_menu = '''                ColorMenuItem(
                    icon = "T",
                    title = if (mode == TrainerMode.WOODPECKER) "Tactics Planner" else "Tactics",
                    description = if (mode == TrainerMode.WOODPECKER) {
                        "Open the cycle manager without leaving your current puzzle."
                    } else {
                        "Woodpecker cycles, ratings, streaks, and XP."
                    },
                    color = Color(0xFFEF4444),
                    enabled = true,
                    onClick = onSelectTactics
                )'''
if old_menu not in text:
    raise SystemExit('Tactics ColorMenuItem block not found')
text = text.replace(old_menu, new_menu, 1)
replay_path.write_text(text, encoding='utf-8')

# ---------------- BeatFishScreen.kt: make flipped taps/drag use display indices ----------------
text = beat_path.read_text(encoding='utf-8')
backup = beat_path.with_suffix(beat_path.suffix + '.flipped_click_mapping_backup')
backup.write_text(text, encoding='utf-8')

old_mapping = '''                            val whiteBottomNow = if (isSession) whiteBottomSession else setupWhiteBottom

                            // screenRankFromTop: 0 at top. Board rank: 0 = rank1 (a1..h1).
                            val boardFile = if (whiteBottomNow) screenFile else 7 - screenFile
                            val boardRank = if (whiteBottomNow) 7 - screenRankFromTop else screenRankFromTop
                            return boardRank * 8 + boardFile'''
new_mapping = '''                            // Return DISPLAY index, not logical board index.
                            // uiBoardForRender is already flipped when Black is at the bottom,
                            // and attemptPlayerMove() converts this display index back to UCI.
                            // Returning logical indices here caused double-flipping: e.g. a visual
                            // c7 click was interpreted as f2 in Beat-the-Fish / Recorder modes.
                            val displayRank = 7 - screenRankFromTop
                            return displayRank * 8 + screenFile'''
if old_mapping not in text:
    raise SystemExit('Primary drag squareAt mapping block not found')
text = text.replace(old_mapping, new_mapping, 1)

old_overlay_mapping = '''                                    val whiteBottomNow = if (isSession) whiteBottomSession else setupWhiteBottom
                                    val boardFile = if (whiteBottomNow) screenFile else 7 - screenFile
                                    val boardRank = if (whiteBottomNow) 7 - screenRankFromTop else screenRankFromTop
                                    return boardRank * 8 + boardFile'''
new_overlay_mapping = '''                                    // Return DISPLAY index, matching ChessBoard.onSquareClick.
                                    // The visible board array has already been oriented, so do not
                                    // flip here. attemptPlayerMove() performs the display->UCI conversion.
                                    val displayRank = 7 - screenRankFromTop
                                    return displayRank * 8 + screenFile'''
if old_overlay_mapping not in text:
    raise SystemExit('Fallback tap squareAt mapping block not found')
text = text.replace(old_overlay_mapping, new_overlay_mapping, 1)

beat_path.write_text(text, encoding='utf-8')

print('Patched:')
print(f' - {replay_path}')
print(f' - {beat_path}')
print('Backups:')
print(f' - {backup}')
print(f' - {replay_path.with_suffix(replay_path.suffix + ".tactics_planner_menu_backup")}')
