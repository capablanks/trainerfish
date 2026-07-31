from pathlib import Path

path = Path('BeatFishScreen.kt')
if not path.exists():
    path = Path('/mnt/data/BeatFishScreen.kt')

text = path.read_text(encoding='utf-8')
backup = path.with_suffix(path.suffix + '.time_safe_inset_backup')
backup.write_text(text, encoding='utf-8')

# Keep the existing compact 4-column layout, but render each time value inside
# a fixed-width cell with an inner right inset. This pulls the final seconds
# digit away from the rounded card border on small phones.
needle = '''    fun moveTimeText(ply: Int?): String {
        if (ply == null) return ""
        val sec = moveTimesSec.getOrNull(ply - 1) ?: return ""
        val safe = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    fun nagColorForSan(san: String?): Color? {
'''
insert = '''    fun moveTimeText(ply: Int?): String {
        if (ply == null) return ""
        val sec = moveTimesSec.getOrNull(ply - 1) ?: return ""
        val safe = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    @Composable
    fun TimeCell(text: String) {
        Box(
            modifier = Modifier.width(timeColumnWidth),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(end = 7.dp),
                color = subtleTextColor,
                style = compactTimeTextStyle,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    fun nagColorForSan(san: String?): Color? {
'''
if needle not in text:
    if 'fun TimeCell(text: String)' not in text:
        raise SystemExit('Could not find moveTimeText() insertion point')
else:
    text = text.replace(needle, insert, 1)

replacements = {
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
}

for old, new in replacements.items():
    count = text.count(old)
    if count == 0:
        # idempotence: tolerate if already patched
        if new not in text:
            raise SystemExit('Expected block not found:\n' + old[:120])
    else:
        text = text.replace(old, new)

# If TextAlign was only used by the old time cells, remove the now-unused import.
if 'textAlign = TextAlign.' not in text:
    text = text.replace('import androidx.compose.ui.text.style.TextAlign\n', '')

path.write_text(text, encoding='utf-8')
print(f'Patched {path}')
print(f'Backup written to {backup}')
