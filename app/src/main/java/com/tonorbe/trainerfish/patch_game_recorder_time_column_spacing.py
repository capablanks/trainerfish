from pathlib import Path

path = Path('BeatFishScreen.kt')
if not path.exists():
    path = Path('/mnt/data/BeatFishScreen.kt')
text = path.read_text(encoding='utf-8')
backup = path.with_suffix(path.suffix + '.time_column_spacing_backup')
backup.write_text(text, encoding='utf-8')

changed = []

if 'import androidx.compose.ui.text.style.TextAlign' not in text:
    anchor = 'import androidx.compose.ui.text.input.KeyboardType\n'
    if anchor not in text:
        raise SystemExit('Import anchor not found')
    text = text.replace(anchor, anchor + 'import androidx.compose.ui.text.style.TextAlign\n', 1)
    changed.append('added TextAlign import')

anchor = '    val headerTextColor = subtleTextColor\n'
insert = '''    val headerTextColor = subtleTextColor

    // Recorder mode needs tighter, phone-safe move/time columns:
    // # | White | Time | Black | Time.  The time cells are a little wider
    // and right-aligned so the final digit is not clipped on small screens.
    val moveNoColumnWidth = if (showTimeColumns) 28.dp else 34.dp
    val timeColumnWidth = if (showTimeColumns) 56.dp else 54.dp
    val compactTimeTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
'''
if insert not in text:
    if anchor not in text:
        raise SystemExit('headerTextColor anchor not found')
    text = text.replace(anchor, insert, 1)
    changed.append('added compact recorder column sizes')

# Width constants used inside the move list.
repls = [
    ('modifier = Modifier.width(34.dp)', 'modifier = Modifier.width(moveNoColumnWidth)'),
    ('Spacer(Modifier.width(34.dp))', 'Spacer(Modifier.width(moveNoColumnWidth))'),
    ('modifier = Modifier.width(46.dp)', 'modifier = Modifier.width(timeColumnWidth)'),
    ('Spacer(Modifier.width(46.dp))', 'Spacer(Modifier.width(timeColumnWidth))'),
]
for old, new in repls:
    if old in text:
        text = text.replace(old, new)
        changed.append(f'{old} -> {new}')

# Time value style and alignment.
old = '''                        r.whiteTime.orEmpty(),
                        modifier = Modifier.width(timeColumnWidth),
                        color = subtleTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false
'''
new = '''                        r.whiteTime.orEmpty(),
                        modifier = Modifier.width(timeColumnWidth),
                        color = subtleTextColor,
                        style = compactTimeTextStyle,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
'''
if old in text:
    text = text.replace(old, new, 1)
    changed.append('right-aligned white time')

old = '''                        if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "",
                        modifier = Modifier.width(timeColumnWidth),
                        color = subtleTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false
'''
new = '''                        if (!splitBlackAfterWhite) r.blackTime.orEmpty() else "",
                        modifier = Modifier.width(timeColumnWidth),
                        color = subtleTextColor,
                        style = compactTimeTextStyle,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
'''
if old in text:
    text = text.replace(old, new, 1)
    changed.append('right-aligned black time')

old = '''                            r.blackTime.orEmpty(),
                            modifier = Modifier.width(timeColumnWidth),
                            color = subtleTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false
'''
new = '''                            r.blackTime.orEmpty(),
                            modifier = Modifier.width(timeColumnWidth),
                            color = subtleTextColor,
                            style = compactTimeTextStyle,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
'''
if old in text:
    text = text.replace(old, new, 1)
    changed.append('right-aligned split black time')

# Header Time cells: right aligned and compact.
old = 'Text("#", modifier = Modifier.width(moveNoColumnWidth), style = MaterialTheme.typography.bodySmall, color = headerTextColor)'
if old not in text:
    # This also verifies the moveNoColumnWidth replacement happened near the header.
    pass

header_old = 'Text("Time", modifier = Modifier.width(timeColumnWidth), style = MaterialTheme.typography.bodySmall, color = subtleTextColor)'
header_new = 'Text("Time", modifier = Modifier.width(timeColumnWidth), style = compactTimeTextStyle, color = subtleTextColor, textAlign = TextAlign.End)'
count = text.count(header_old)
if count:
    text = text.replace(header_old, header_new)
    changed.append(f'right-aligned {count} header time cells')

# Add a small gap before the Black column when the time column is showing, so "Time" and "Black" do not visually merge.
old = 'Text("Black", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = headerTextColor)'
new = '''Text(
                                "Black",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = if (showTimeColumns) 6.dp else 0.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = headerTextColor
                            )'''
if old in text:
    text = text.replace(old, new, 1)
    changed.append('added header gap before Black')

path.write_text(text, encoding='utf-8')
print('Patched', path)
print('Backup', backup)
for item in changed:
    print('-', item)
