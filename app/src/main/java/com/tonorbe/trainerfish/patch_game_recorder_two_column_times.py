from pathlib import Path

path = Path('BeatFishScreen.kt')
if not path.exists():
    path = Path('/mnt/data/BeatFishScreen.kt')
text = path.read_text(encoding='utf-8')
backup = path.with_suffix(path.suffix + '.two_column_times_backup')
backup.write_text(text, encoding='utf-8')

orig = text

old = '''    // Phone-safe compact recorder layout: # | White | Time | Black | Time.
    // Keep the time cells small so the final seconds digit never reaches the rounded edge.
    val moveNoColumnWidth = if (showTimeColumns) 26.dp else 34.dp
    val timeColumnWidth = if (showTimeColumns) 44.dp else 54.dp
    val compactTimeTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
'''
new = '''    // Recorder mode uses a simple phone-safe layout:
    // # | White move   time | Black move   time
    // No separate Time columns, so small phones cannot clip the seconds digit.
    val moveNoColumnWidth = if (showTimeColumns) 28.dp else 34.dp
    val timeColumnWidth = 54.dp
    val compactTimeTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
'''
if old in text:
    text = text.replace(old, new, 1)
else:
    print('note: layout comment block not found; continuing')

anchor = '''    @Composable
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
'''
insert = anchor + '''
    fun recorderMoveText(san: String, time: String?): String {
        val move = bfSanToFanDisplay(san)
        return if (showTimeColumns && !time.isNullOrBlank()) "$move   $time" else move
    }
'''
if anchor in text and 'fun recorderMoveText(san: String, time: String?)' not in text:
    text = text.replace(anchor, insert, 1)

# White move text
text = text.replace(
'''                            text = bestMoveAnnotatedText(bfSanToFanDisplay(san0), isBest),
                            color = baseColor,
''',
'''                            text = bestMoveAnnotatedText(recorderMoveText(san0, r.whiteTime), isBest),
                            color = baseColor,
''',
1)

# First black move text in normal same-row display
text = text.replace(
'''                            text = bestMoveAnnotatedText(bfSanToFanDisplay(san0), isBest),
                            color = baseColor,
                            modifier = Modifier
                                .weight(1f)
                                .let { base ->
                                    val ply = r.blackPly
''',
'''                            text = bestMoveAnnotatedText(recorderMoveText(san0, r.blackTime), isBest),
                            color = baseColor,
                            modifier = Modifier
                                .weight(1f)
                                .let { base ->
                                    val ply = r.blackPly
''',
1)

# Split black move text
text = text.replace(
'''                        text = bestMoveAnnotatedText(bfSanToFanDisplay(san0), isBest),
                        color = baseColor,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
''',
'''                        text = bestMoveAnnotatedText(recorderMoveText(san0, r.blackTime), isBest),
                        color = baseColor,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
''',
1)

# Remove white time column from main row; keep CP columns for analysis mode only.
old = '''                if (showTimeColumns) {
                    TimeCell(r.whiteTime.orEmpty())
                } else if (showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }
'''
new = '''                if (!showTimeColumns && showCpColumns) {
                    Text(
                        cpText(r.whiteCp),
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }
'''
if old not in text:
    raise SystemExit('white time column block not found')
text = text.replace(old, new, 1)

# Remove black time column from main row; keep CP columns for analysis mode only.
old = '''                if (showTimeColumns) {
                    TimeCell(if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "")
                } else if (showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }
'''
new = '''                if (!showTimeColumns && showCpColumns) {
                    Text(
                        if (!splitBlackAfterWhite) cpText(r.blackCp) else "...",
                        modifier = Modifier.width(54.dp),
                        color = cpColor
                    )
                }
'''
if old not in text:
    raise SystemExit('black time column block not found')
text = text.replace(old, new, 1)

# Split row: remove the old placeholder time-cell spacer.
old = '''                    if (showTimeColumns) {
                        Spacer(Modifier.width(timeColumnWidth))
                    } else if (showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }
'''
new = '''                    if (!showTimeColumns && showCpColumns) {
                        Spacer(Modifier.width(54.dp))
                    }
'''
if old not in text:
    raise SystemExit('split-row spacer time column block not found')
text = text.replace(old, new, 1)

# Split row: remove the old black time cell.
old = '''                    if (showTimeColumns) {
                        TimeCell(r.blackTime.orEmpty())
                    } else if (showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }
'''
new = '''                    if (!showTimeColumns && showCpColumns) {
                        Text(
                            cpText(r.blackCp),
                            modifier = Modifier.width(54.dp),
                            color = cpColor
                        )
                    }
'''
if old not in text:
    raise SystemExit('split-row black time cell block not found')
text = text.replace(old, new, 1)

# Header: remove separate Time headers.
old = '''                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
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
                            }
'''
new = '''                            Text("White", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)
                            if (!showTimeColumns && showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
                            Text(
                                "Black",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = headerTextColor
                            )
                            if (!showTimeColumns && showCpColumns) {
                                Text("CP", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = cpColor)
                            }
'''
if old not in text:
    raise SystemExit('header time columns block not found')
text = text.replace(old, new, 1)

if text == orig:
    raise SystemExit('No changes made')
path.write_text(text, encoding='utf-8')
print('Patched BeatFishScreen.kt: recorder move list now uses # | White | Black with move + three spaces + time.')
print(f'Backup: {backup.name}')
