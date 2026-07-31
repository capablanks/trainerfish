from pathlib import Path
import re

path = Path('BeatFishScreen.kt')
if not path.exists():
    path = Path('/mnt/data/BeatFishScreen.kt')
text = path.read_text(encoding='utf-8')
backup = path.with_suffix(path.suffix + '.compact_recorder_move_times_backup')
backup.write_text(text, encoding='utf-8')

# 1) Add MoveListBox parameters for compact recorder time columns.
old_sig = '''    annotatedRibbon: String? = null,
    cpSeriesAfterWhite: List<Int?> = emptyList(),
    showCpColumns: Boolean = true,'''
new_sig = '''    annotatedRibbon: String? = null,
    cpSeriesAfterWhite: List<Int?> = emptyList(),
    moveTimesSec: List<Int> = emptyList(),
    showTimeColumns: Boolean = false,
    showCpColumns: Boolean = true,'''
if old_sig not in text:
    raise SystemExit('MoveListBox signature anchor not found')
text = text.replace(old_sig, new_sig, 1)

# 2) Add a local time formatter after cpText().
old_cp = '''    fun cpText(cp: Int?): String {
        if (cp == null) return "..."
        val v = cp / 100.0
        return if (v >= 0) String.format("+%.1f", v) else String.format("%.1f", v)
    }

    fun nagColorForSan(san: String?): Color? {'''
new_cp = '''    fun cpText(cp: Int?): String {
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

    fun nagColorForSan(san: String?): Color? {'''
if old_cp not in text:
    raise SystemExit('cpText anchor not found')
text = text.replace(old_cp, new_cp, 1)

# 3) Extend RowData and remember keys.
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
if old_rows_header not in text:
    raise SystemExit('RowData block not found')
text = text.replace(old_rows_header, new_rows_header, 1)

old_add_white = '''                out.add(RowData(moveNo, whitePly, whiteSan, whiteCp, blackPly, blackSan, blackCp))'''
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
if old_add_white not in text:
    raise SystemExit('white RowData add not found')
text = text.replace(old_add_white, new_add_white, 1)

old_add_black = '''                out.add(RowData(moveNo, null, null, null, blackPly, m.san, cpSeriesAfterWhite.getOrNull(i)))'''
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
if old_add_black not in text:
    raise SystemExit('black RowData add not found')
text = text.replace(old_add_black, new_add_black, 1)

# 4) Swap CP cell rendering to Time cell rendering when requested.
old_white_cell = '''                if (showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }'''
new_white_cell = '''                if (showTimeColumns) {
                    Text(
                        r.whiteTime.orEmpty(),
                        modifier = Modifier.width(46.dp),
                        color = subtleTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false
                    )
                } else if (showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }'''
if old_white_cell not in text:
    raise SystemExit('white CP cell block not found')
text = text.replace(old_white_cell, new_white_cell, 1)

old_black_cell = '''                if (showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }'''
new_black_cell = '''                if (showTimeColumns) {
                    Text(
                        if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "",
                        modifier = Modifier.width(46.dp),
                        color = subtleTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false
                    )
                } else if (showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }'''
if old_black_cell not in text:
    raise SystemExit('black CP cell block not found')
text = text.replace(old_black_cell, new_black_cell, 1)

old_split_spacer = '''                    if (showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }'''
new_split_spacer = '''                    if (showTimeColumns) {
                        Spacer(Modifier.width(46.dp))
                    } else if (showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }'''
if old_split_spacer not in text:
    raise SystemExit('split spacer block not found')
text = text.replace(old_split_spacer, new_split_spacer, 1)

old_split_black_cp = '''                    if (showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }'''
new_split_black_cp = '''                    if (showTimeColumns) {
                        Text(
                            r.blackTime.orEmpty(),
                            modifier = Modifier.width(46.dp),
                            color = subtleTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else if (showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }'''
if old_split_black_cp not in text:
    raise SystemExit('split black CP block not found')
text = text.replace(old_split_black_cp, new_split_black_cp, 1)

# 5) Header labels: White | Time | Black | Time when in recorder compact mode.
old_header = '''                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                            Text("Black", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }'''
new_header = '''                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showTimeColumns) {
                                Text("Time", modifier = Modifier.width(46.dp), style = MaterialTheme.typography.bodySmall, color = subtleTextColor)
                            } else if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                            Text("Black", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (showTimeColumns) {
                                Text("Time", modifier = Modifier.width(46.dp), style = MaterialTheme.typography.bodySmall, color = subtleTextColor)
                            } else if (showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }'''
if old_header not in text:
    raise SystemExit('main header block not found')
text = text.replace(old_header, new_header, 1)

# 6) Wire caller: do not inject time comments for UI; pass times into compact columns.
old_call = '''                    annotatedRibbon = if (recordGameMode) (annotatedRibbon ?: rebuildRecordRibbon(moves)) else annotatedRibbon,
                    cpSeriesAfterWhite = annotatedCpSeries,
                    showCpColumns = (mode == BeatFishMode.ANALYZE),'''
new_call = '''                    annotatedRibbon = if (recordGameMode) null else annotatedRibbon,
                    cpSeriesAfterWhite = annotatedCpSeries,
                    moveTimesSec = if (recordGameMode && mode == BeatFishMode.PLAY) recordMoveTimesSec.toList() else emptyList(),
                    showTimeColumns = recordGameMode && mode == BeatFishMode.PLAY,
                    showCpColumns = (mode == BeatFishMode.ANALYZE),'''
if old_call not in text:
    raise SystemExit('MoveListBox call block not found')
text = text.replace(old_call, new_call, 1)

path.write_text(text, encoding='utf-8')
print('Patched BeatFishScreen.kt')
print(f'Backup: {backup}')
