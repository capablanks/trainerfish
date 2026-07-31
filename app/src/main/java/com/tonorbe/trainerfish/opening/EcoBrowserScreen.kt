package com.tonorbe.trainerfish.opening

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * ECO Browser UI (Option C: Search + Filtered List).
 *
 * @param onLineSelected Called when user taps an ECO line.
 *                       Parent can then play entry.pgn on the board.
 */
@Composable
fun EcoBrowserScreen(
    modifier: Modifier = Modifier,
    selectedEcoCode: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCode: String? = null,
    onLineSelected: (EcoEntry) -> Unit
)
{
    val context = LocalContext.current

    // Load once and keep in memory
    val allEntries = remember {
        EcoClassifier.getAllEntries(context)
    }


    val filtered = remember(query, allEntries, selectedEcoCode) {
        val trimmed = query.trim()
        val base = if (trimmed.isEmpty()) {
            allEntries
        } else {
            val q = trimmed.lowercase()
            allEntries.filter { entry ->
                entry.code.lowercase().contains(q) ||
                        entry.name.lowercase().contains(q)
            }
        }

        // \uD83D\uDD25 Move last-opened ECO to the top if present
        if (selectedEcoCode.isNullOrEmpty()) {
            base
        } else {
            val (hit, rest) = base.partition { it.code == selectedEcoCode }
            hit + rest
        }
    }


    val pinned = remember(filtered, selectedCode) {
        if (selectedCode.isNullOrBlank()) return@remember filtered

        val idx = filtered.indexOfFirst { it.code == selectedCode }
        if (idx < 0) filtered
        else {
            val selected = filtered[idx]
            listOf(selected) + filtered.filterIndexed { i, _ -> i != idx }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Search box
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it) },
            label = { Text("Search ECO or name (e.g. \"D10\" or \"Slav\")") },
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Results: ${filtered.size}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Divider()

        // Scrollable list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            items(filtered) { entry ->
                EcoRow(
                    entry = entry,
                    selected = (entry.code == selectedCode),
                    onClick = { onLineSelected(entry) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun EcoRow(
    entry: EcoEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Text(
            text = "${entry.code} - ${entry.name}",
            color = fg,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        entry.pgn?.let { pgn ->
            Text(
                text = pgn,
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

