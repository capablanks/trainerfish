from pathlib import Path
import re

path = Path('BeatFishScreen.kt')
if not path.exists():
    path = Path('/mnt/data/BeatFishScreen.kt')

text = path.read_text(encoding='utf-8')
backup = path.with_suffix(path.suffix + '.time_columns_final_backup')
backup.write_text(text, encoding='utf-8')
changed = []

# TextAlign is used by the final TimeCell.
if 'import androidx.compose.ui.text.style.TextAlign' not in text:
    anchor = 'import androidx.compose.ui.text.input.KeyboardType\n'
    if anchor not in text:
        raise SystemExit('Import anchor not found')
    text = text.replace(anchor, anchor + 'import androidx.compose.ui.text.style.TextAlign\n', 1)
    changed.append('added TextAlign import')

# 1) Add compact recorder time parameters if this file is still before the first compact-time patch.
old_sig = '''    annotatedRibbon: String? = null,
    cpSeriesAfterWhite: List<Int?> = emptyList(),
    showCpColumns: Boolean = true,'''
new_sig = '''    annotatedRibbon: String? = null,
    cpSeriesAfterWhite: List<Int?> = emptyList(),
    moveTimesSec: List<Int> = emptyList(),
    showTimeColumns: Boolean = false,
    showCpColumns: Boolean = true,'''
if old_sig in text and 'moveTimesSec: List<Int> = emptyList()' not in text:
    text = text.replace(old_sig, new_sig, 1)
    changed.append('added MoveListBox time parameters')

# 2) Add / normalize phone-safe column constants after headerTextColor.
constants_re = re.compile(
    r'''    val headerTextColor = subtleTextColor\n\n(?:    // Recorder mode needs.*?\n)?(?:    // # \| White.*?\n)?    val moveNoColumnWidth = if \(showTimeColumns\) \d+\.dp else 34\.dp\n    val timeColumnWidth = if \(showTimeColumns\) \d+\.dp else 54\.dp\n    val compactTimeTextStyle = MaterialTheme\.typography\.bodySmall\.copy\(fontSize = \d+\.sp\)\n''',
    re.S,
)
constants_new = '''    val headerTextColor = subtleTextColor

    // Phone-safe compact recorder layout: # | White | Time | Black | Time.
    // Keep the time cells small so the final seconds digit never reaches the rounded edge.
    val moveNoColumnWidth = if (showTimeColumns) 26.dp else 34.dp
    val timeColumnWidth = if (showTimeColumns) 44.dp else 54.dp
    val compactTimeTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
'''
if constants_re.search(text):
    text = constants_re.sub(constants_new, text, count=1)
    changed.append('normalized compact column constants')
else:
    anchor = '    val headerTextColor = subtleTextColor\n'
    if anchor not in text:
        raise SystemExit('headerTextColor anchor not found')
    text = text.replace(anchor, constants_new, 1)
    changed.append('added compact column constants')

# 3) Add / normalize moveTimeText + TimeCell after cpText.
cp_anchor_old = '''    fun cpText(cp: Int?): String {
        if (cp == null) return "..."
        val v = cp / 100.0
        return if (v >= 0) String.format("+%.1f", v) else String.format("%.1f", v)
    }

    fun nagColorForSan(san: String?): Color? {'''
cp_anchor_new = '''    fun cpText(cp: Int?): String {
        if (cp == null) return "..."
        val v = cp / 100.0
        return if (v >= 0) String.format("+%.1f", v) else String.format("%.1f", v)
    }

    fun moveTimeText(ply: Int?): String {
        if (ply == null) return ""
        val sec = moveTimesSec.getOrNull(ply - 1) ?: return ""
        val safe = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    @Composable
    fun TimeCell(text: String) {
        Text(
            text = text,
            modifier = Modifier.width(timeColumnWidth),
            color = subtleTextColor,
            style = compactTimeTextStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }

    fun nagColorForSan(san: String?): Color? {'''

# Replace existing moveTimeText + TimeCell block if present.
time_block_re = re.compile(
    r'''    fun moveTimeText\(ply: Int\?\): String \{\n        if \(ply == null\) return ""\n        val sec = moveTimesSec\.getOrNull\(ply - 1\) \?: return ""\n        val safe = sec\.coerceAtLeast\(0\)\n        return String\.format\(Locale\.US, "%02d:%02d", safe / 60, safe % 60\)\n    \}\n\n(?:    @Composable\n    fun TimeCell\(text: String\) \{.*?\n    \}\n\n)?    fun nagColorForSan\(san: String\?\): Color\? \{''',
    re.S,
)
time_repl = '''    fun moveTimeText(ply: Int?): String {
        if (ply == null) return ""
        val sec = moveTimesSec.getOrNull(ply - 1) ?: return ""
        val safe = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    @Composable
    fun TimeCell(text: String) {
        Text(
            text = text,
            modifier = Modifier.width(timeColumnWidth),
            color = subtleTextColor,
            style = compactTimeTextStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }

    fun nagColorForSan(san: String?): Color? {'''
if time_block_re.search(text):
    text = time_block_re.sub(time_repl, text, count=1)
    changed.append('normalized TimeCell')
elif cp_anchor_old in text:
    text = text.replace(cp_anchor_old, cp_anchor_new, 1)
    changed.append('added moveTimeText and TimeCell')
else:
    raise SystemExit('cpText / moveTimeText anchor not found')

# 4) Add RowData time fields if needed.
old_rows_header = '''    data class RowData(
        val moveNo: Int,
        val whitePly: Int?, val whiteSan: String?, val whiteCp: Int?,
        val blackPly: Int?, val blackSan: String?, val blackCp: Int?
    )

    val rows = remember(moves.size, cpSeriesAfterWhite.size, mode) {'''
new_rows_header = '''    data class RowData(
        val moveNo: Int,
        val whitePly: Int?, val whiteSan: String?, val whiteCp: Int?, val whiteTime: String?,
        val blackPly: Int?, val blackSan: String?, val blackCp: Int?, val blackTime: String?
    )

    val rows = remember(moves.size, cpSeriesAfterWhite.size, mode, moveTimesSec.toList(), showTimeColumns) {'''
if old_rows_header in text:
    text = text.replace(old_rows_header, new_rows_header, 1)
    changed.append('extended RowData with time fields')
else:
    # Normalize remember keys if already patched.
    text = text.replace(
        '    val rows = remember(moves.size, cpSeriesAfterWhite.size, mode) {',
        '    val rows = remember(moves.size, cpSeriesAfterWhite.size, mode, moveTimesSec.toList(), showTimeColumns) {'
    )

old_add_white = '                out.add(RowData(moveNo, whitePly, whiteSan, whiteCp, blackPly, blackSan, blackCp))'
new_add_white = '''                out.add(RowData(
                    moveNo = moveNo,
                    whitePly = whitePly,
                    whiteSan = whiteSan,
                    whiteCp = whiteCp,
                    whiteTime = moveTimeText(whitePly),
                    blackPly = blackPly,
                    blackSan = blackSan,
                    blackCp = blackCp,
                    blackTime = moveTimeText(blackPly)
                ))'''
if old_add_white in text:
    text = text.replace(old_add_white, new_add_white, 1)
    changed.append('filled white/black row time fields')

old_add_black = '                out.add(RowData(moveNo, null, null, null, blackPly, m.san, cpSeriesAfterWhite.getOrNull(i)))'
new_add_black = '''                out.add(RowData(
                    moveNo = moveNo,
                    whitePly = null,
                    whiteSan = null,
                    whiteCp = null,
                    whiteTime = null,
                    blackPly = blackPly,
                    blackSan = m.san,
                    blackCp = cpSeriesAfterWhite.getOrNull(i),
                    blackTime = moveTimeText(blackPly)
                ))'''
if old_add_black in text:
    text = text.replace(old_add_black, new_add_black, 1)
    changed.append('filled black-only row time field')

# 5) Convert raw time Text blocks into TimeCell if they exist from earlier patches.
raw_time_blocks = {
'''                    Text(
                        r.whiteTime.orEmpty(),
                        modifier = Modifier.width(timeColumnWidth),
                        color = subtleTextColor,
                        style = compactTimeTextStyle,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
                    )''': '''                    TimeCell(r.whiteTime.orEmpty())''',
'''                    Text(
                        if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "",
                        modifier = Modifier.width(timeColumnWidth),
                        color = subtleTextColor,
                        style = compactTimeTextStyle,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
                    )''': '''                    TimeCell(if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "")''',
'''                        Text(
                            r.blackTime.orEmpty(),
                            modifier = Modifier.width(timeColumnWidth),
                            color = subtleTextColor,
                            style = compactTimeTextStyle,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )''': '''                        TimeCell(r.blackTime.orEmpty())''',
'''                                Text("Time", modifier = Modifier.width(timeColumnWidth), style = compactTimeTextStyle, color = subtleTextColor, textAlign = TextAlign.End)''': '''                                TimeCell("Time")''',
'''                                Text("Time", modifier = Modifier.width(46.dp), style = MaterialTheme.typography.bodySmall, color = subtleTextColor)''': '''                                TimeCell("Time")''',
'''                    Text(
                        r.whiteTime.orEmpty(),
                        modifier = Modifier.width(46.dp),
                        color = subtleTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false
                    )''': '''                    TimeCell(r.whiteTime.orEmpty())''',
'''                    Text(
                        if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "",
                        modifier = Modifier.width(46.dp),
                        color = subtleTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false
                    )''': '''                    TimeCell(if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "")''',
'''                        Text(
                            r.blackTime.orEmpty(),
                            modifier = Modifier.width(46.dp),
                            color = subtleTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false
                        )''': '''                        TimeCell(r.blackTime.orEmpty())''',
}
for old, new in raw_time_blocks.items():
    if old in text:
        text = text.replace(old, new)
        changed.append('converted raw time Text to TimeCell')

# 6) If the file is still pre-time-cell, add time branches around CP columns.
def replace_once(old, new, label):
    global text
    if old in text:
        text = text.replace(old, new, 1)
        changed.append(label)

replace_once('''                if (showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }''', '''                if (showTimeColumns) {
                    TimeCell(r.whiteTime.orEmpty())
                } else if (showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }''', 'added white time cell branch')

replace_once('''                if (showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }''', '''                if (showTimeColumns) {
                    TimeCell(if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "")
                } else if (showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }''', 'added black time cell branch')

replace_once('''                    if (showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }''', '''                    if (showTimeColumns) {
                        Spacer(Modifier.width(timeColumnWidth))
                    } else if (showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }''', 'added split-row time spacer')

replace_once('''                    if (showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }''', '''                    if (showTimeColumns) {
                        TimeCell(r.blackTime.orEmpty())
                    } else if (showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }''', 'added split-row black time cell')

# 7) Header: normalize Time labels to TimeCell and keep black label gap tiny.
header_old = '''                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                            Text("Black", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }'''
header_new = '''                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showTimeColumns) {
                                TimeCell("Time")
                            } else if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                            Text(
                                "Black",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = if (showTimeColumns) 2.dp else 0.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = headerTextColor
                            )
                            if (showTimeColumns) {
                                TimeCell("Time")
                            } else if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }'''
replace_once(header_old, header_new, 'added compact time header')

# Normalize an already-patched Black header padding from 6dp to 2dp.
text = text.replace('.padding(start = if (showTimeColumns) 6.dp else 0.dp)', '.padding(start = if (showTimeColumns) 2.dp else 0.dp)')

# 8) Use moveNoColumnWidth in move list rows/headers.
for old, new in [
    ('modifier = Modifier.width(34.dp)', 'modifier = Modifier.width(moveNoColumnWidth)'),
    ('Spacer(Modifier.width(34.dp))', 'Spacer(Modifier.width(moveNoColumnWidth))'),
]:
    if old in text:
        text = text.replace(old, new)
        changed.append(f'{old} -> {new}')

# 9) Wire caller: pass moveTimesSec and suppress inline {MM:SS} comments in the UI.
old_call = '''                    annotatedRibbon = if (recordGameMode) (annotatedRibbon ?: rebuildRecordRibbon(moves)) else annotatedRibbon,
                    cpSeriesAfterWhite = annotatedCpSeries,
                    showCpColumns = (mode == BeatFishMode.ANALYZE),'''
new_call = '''                    annotatedRibbon = if (recordGameMode) null else annotatedRibbon,
                    cpSeriesAfterWhite = annotatedCpSeries,
                    moveTimesSec = if (recordGameMode && mode == BeatFishMode.PLAY) recordMoveTimesSec.toList() else emptyList(),
                    showTimeColumns = recordGameMode && mode == BeatFishMode.PLAY,
                    showCpColumns = (mode == BeatFishMode.ANALYZE),'''
if old_call in text:
    text = text.replace(old_call, new_call, 1)
    changed.append('wired MoveListBox caller for moveTimesSec')
else:
    # If already wired, leave it alone.
    pass

# Sanity checks for the current bad state and the intended final state.
if 'showTimeColumns' not in text or 'moveTimesSec' not in text:
    raise SystemExit('Patch did not find/apply recorder time-column wiring')
if 'fun TimeCell(text: String)' not in text:
    raise SystemExit('Final TimeCell not present')

path.write_text(text, encoding='utf-8')
print(f'Patched {path}')
print(f'Backup written to {backup}')
if changed:
    print('Changes:')
    for item in changed:
        print(' - ' + item)
else:
    print('No text changes needed; file already looked patched.')
