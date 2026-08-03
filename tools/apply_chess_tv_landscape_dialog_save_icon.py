from pathlib import Path
import re

path = Path("app/src/main/java/com/tonorbe/trainerfish/LichessTvScreen.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import androidx.compose.material.icons.filled.PowerSettingsNew\n",
    "import androidx.compose.material.icons.filled.PowerSettingsNew\nimport androidx.compose.material.icons.filled.Save\n",
    "Save icon import",
)
replace_once(
    "import androidx.compose.ui.platform.LocalContext\n",
    "import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\n",
    "LocalConfiguration import",
)
replace_once(
    "import androidx.compose.ui.unit.sp\n",
    "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.window.DialogProperties\n",
    "DialogProperties import",
)

new_dialog = r'''@Composable
private fun LichessBroadcastDialog(
    loading: Boolean,
    errorMessage: String?,
    broadcasts: List<LichessBroadcastPreview>,
    selectedRound: LichessBroadcastRound?,
    browserMode: LichessBroadcastBrowserMode,
    followProfile: ChessTvFollowProfile,
    personalizedGames: List<LichessBroadcastSelection>,
    personalizedScanProgress: Pair<Int, Int>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSelectBroadcast: (LichessBroadcastPreview) -> Unit,
    onOpenCountry: () -> Unit,
    onOpenFavorites: () -> Unit,
    onEditFollows: () -> Unit,
    selectedGames: List<LichessBroadcastSelection>,
    selectionMessage: String?,
    onToggleBoard: (LichessBroadcastBoard) -> Unit,
    onToggleSelection: (LichessBroadcastSelection) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    val dialogScrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val selectedTournamentCount = selectedGames.map { it.tournamentName }.distinct().size
    val country = followProfile.country
    val personalizedMode = selectedRound == null && browserMode != LichessBroadcastBrowserMode.TOURNAMENTS
    val title = when {
        selectedRound != null -> selectedRound.preview.tournamentName
        browserMode == LichessBroadcastBrowserMode.COUNTRY ->
            "Players from ${country?.name ?: "your country"}"
        browserMode == LichessBroadcastBrowserMode.FAVORITES -> "Favorite players"
        else -> "Live Tournament Broadcasts"
    }
    val dialogModifier = if (landscape) {
        Modifier.fillMaxWidth(0.74f).fillMaxHeight(0.94f)
    } else {
        Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.90f)
    }

    AlertDialog(
        modifier = dialogModifier,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = if (landscape) 20.sp else 22.sp,
                lineHeight = if (landscape) 23.sp else 26.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            // One scroll container owns the complete dialog body. This avoids the
            // near-zero-height nested list that occurred on landscape screens.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(dialogScrollState),
                verticalArrangement = Arrangement.spacedBy(if (landscape) 5.dp else 7.dp)
            ) {
                if (selectedRound != null) {
                    Text(
                        "${selectedRound.preview.roundName} • ${selectedGames.size}/$MAX_BROADCAST_GAMES selected",
                        color = Color(0xFF5D4632),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap boards, then Done. Use Tournaments to add another event; swipe the live board to switch games.",
                        color = Color(0xFF5D4632),
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                    selectionMessage?.let { message ->
                        Text(
                            message,
                            color = Color(0xFFB91C1C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        if (personalizedMode) {
                            "Choose matching games, then Done. The $MAX_BROADCAST_GAMES-game limit covers all lists."
                        } else {
                            "Choose up to $MAX_BROADCAST_GAMES games across broadcasts."
                        },
                        color = Color(0xFF5D4632),
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                    if (selectedGames.isNotEmpty()) {
                        Text(
                            "${selectedGames.size}/$MAX_BROADCAST_GAMES selected across $selectedTournamentCount tournament${if (selectedTournamentCount == 1) "" else "s"}.",
                            color = Color(0xFF166534),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!personalizedMode) {
                        TextButton(
                            onClick = onEditFollows,
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Text("Edit country & favorites", fontSize = 11.sp)
                        }
                    }
                }

                errorMessage?.let {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFFFFE4E6)
                    ) {
                        Text(
                            it,
                            color = Color(0xFF9F1239),
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(7.dp)
                        )
                    }
                }

                when {
                    loading && !personalizedMode -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF2F6B1F))
                        }
                    }

                    selectedRound != null -> {
                        if (selectedRound.boards.isEmpty() && errorMessage == null) {
                            Text(
                                "No boards are available in this round yet.",
                                color = Color(0xFF5D4632),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            selectedRound.boards.forEach { board ->
                                LichessBroadcastBoardRow(
                                    board = board,
                                    selected = selectedGames.any {
                                        it.roundId == selectedRound.preview.roundId &&
                                            it.gameId == board.gameId
                                    },
                                    onClick = { onToggleBoard(board) }
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    personalizedMode -> {
                        val (checked, total) = personalizedScanProgress
                        if (loading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF2F6B1F),
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    "Checking $checked/$total • ${personalizedGames.size} found",
                                    color = Color(0xFF5D4632),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (personalizedGames.isEmpty() && !loading && errorMessage == null) {
                            Text(
                                when (browserMode) {
                                    LichessBroadcastBrowserMode.COUNTRY ->
                                        "No live boards featuring players from ${country?.name ?: "your country"} were found."
                                    LichessBroadcastBrowserMode.FAVORITES ->
                                        "None of your favorite players has a live broadcast board right now."
                                    LichessBroadcastBrowserMode.TOURNAMENTS -> "No matching games were found."
                                },
                                color = Color(0xFF5D4632),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            personalizedGames.forEach { selection ->
                                LichessBroadcastSelectionRow(
                                    selection = selection,
                                    selected = selectedGames.any {
                                        it.roundId == selection.roundId && it.gameId == selection.gameId
                                    },
                                    onClick = { onToggleSelection(selection) }
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    broadcasts.isEmpty() && errorMessage == null -> {
                        Text(
                            "No tournament rounds are being broadcast live right now.",
                            color = Color(0xFF5D4632),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    else -> {
                        val countrySelectedCount = country?.let { selectedCountry ->
                            selectedGames.count { it.matchesCountry(selectedCountry) }
                        } ?: 0
                        LichessBroadcastCategoryRow(
                            number = 1,
                            title = country?.let { "Players from ${it.name}" }
                                ?: "Players from your country",
                            subtitle = country?.let {
                                "Live boards featuring ${it.label} players"
                            } ?: "Choose a country to enable this list",
                            selectedCount = countrySelectedCount,
                            onClick = if (country == null) onEditFollows else onOpenCountry
                        )
                        Spacer(Modifier.height(4.dp))

                        val favoriteSelectedCount = selectedGames.count {
                            it.matchesFavorites(followProfile.favorites)
                        }
                        LichessBroadcastCategoryRow(
                            number = 2,
                            title = "Favorite players",
                            subtitle = if (followProfile.favorites.isEmpty()) {
                                "Choose up to $MAX_CHESS_TV_FAVORITES players to enable this list"
                            } else {
                                followProfile.favorites.joinToString { it.displayName }
                            },
                            selectedCount = favoriteSelectedCount,
                            onClick = if (followProfile.favorites.isEmpty()) onEditFollows else onOpenFavorites
                        )
                        Spacer(Modifier.height(5.dp))

                        Text(
                            "3. Live tournaments",
                            color = Color(0xFF4E3B2A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                        broadcasts.forEach { broadcast ->
                            Spacer(Modifier.height(4.dp))
                            LichessBroadcastTournamentRow(
                                broadcast = broadcast,
                                selectedCount = selectedGames.count { it.roundId == broadcast.roundId },
                                onClick = { onSelectBroadcast(broadcast) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = selectedGames.isNotEmpty(),
                    onClick = onClear
                ) {
                    Text("Clear", color = if (selectedGames.isNotEmpty()) Color(0xFFB91C1C) else Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                if (selectedRound != null || personalizedMode) {
                    TextButton(onClick = onBack) { Text("← Tournaments") }
                } else {
                    TextButton(enabled = !loading, onClick = onRefresh) { Text("Refresh") }
                }
                if (selectedGames.isNotEmpty()) {
                    TextButton(onClick = onDone) { Text("Done (${selectedGames.size})") }
                } else if (selectedRound == null && !personalizedMode) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
        dismissButton = {}
    )
}

'''

pattern = re.compile(
    r"@Composable\nprivate fun LichessBroadcastDialog\(.*?\n\}\n\n@Composable\nprivate fun LichessBroadcastCategoryRow\(",
    re.S,
)
replacement = new_dialog + "@Composable\nprivate fun LichessBroadcastCategoryRow("
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"broadcast dialog replacement: expected one match, found {count}")

replace_once(
    '''                IconButton(
                    onClick = onFlip,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip board",
                        tint = Color(0xFF084E9E),
                        modifier = Modifier.size(23.dp)
                    )
                }
''',
    '''                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = onSavePgn,
                        enabled = canSavePgn,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save game to PGN",
                            tint = if (canSavePgn) Color(0xFF084E9E) else Color.Gray,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(
                        onClick = onFlip,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip board",
                            tint = Color(0xFF084E9E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
''',
    "PGN save icon beside flip",
)

path.write_text(text, encoding="utf-8")
print("Applied landscape broadcast dialog and PGN save icon patch")
