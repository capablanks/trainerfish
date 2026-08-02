package com.tonorbe.trainerfish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TrainerHelpTopic(
    val shortTitle: String,
    val title: String,
    val emoji: String
) {
    HOME("Home", "Getting started", "🐟"),
    TACTICS("Tactics", "Tactics and Woodpecker cycles", "🎯"),
    ENDGAME("Endgame", "Endgame training", "🏁"),
    BEAT_FISH("Beat Fish", "Beat the Fish", "🐠"),
    GAME_RECORDER("Recorder", "Game Recorder", "✍️"),
    CHESS_TV("Chess TV", "Live games and broadcasts", "📺"),
    CHESS_CLOCK("Clock", "Chess Clock", "⏱️"),
    VISUAL_STUDIO("Visuals", "Visual Studio", "🎨")
}

private data class TrainerHelpSection(
    val title: String,
    val body: String
)

internal fun trainerHelpTopicFor(mode: TrainerMode): TrainerHelpTopic = when (mode) {
    TrainerMode.WOODPECKER -> TrainerHelpTopic.TACTICS
    TrainerMode.ENDGAME -> TrainerHelpTopic.ENDGAME
    TrainerMode.BEAT_FISH -> TrainerHelpTopic.BEAT_FISH
    TrainerMode.GAME_RECORDER -> TrainerHelpTopic.GAME_RECORDER
    TrainerMode.OPENING, TrainerMode.PGN -> TrainerHelpTopic.HOME
}

private fun helpIntro(topic: TrainerHelpTopic): String = when (topic) {
    TrainerHelpTopic.HOME ->
        "Choose a room from Home. TrainerFish remembers your progress and recent positions, so you can train in short sessions and continue later."
    TrainerHelpTopic.TACTICS ->
        "Build pattern recognition by solving rated puzzles in repeated Woodpecker cycles. The aim is to become both accurate and fast."
    TrainerHelpTopic.ENDGAME ->
        "Learn essential positions, solve endgame tactics, and explore studies. The board starts from the side to move and stays in that orientation."
    TrainerHelpTopic.BEAT_FISH ->
        "Play a complete game or continue from a position you last viewed in another TrainerFish training room."
    TrainerHelpTopic.GAME_RECORDER ->
        "Record an over-the-board game move by move, then review, annotate, save, or analyze it after play."
    TrainerHelpTopic.CHESS_TV ->
        "Follow the current Lichess TV game, a player, or up to eight boards chosen from any combination of live tournament broadcasts."
    TrainerHelpTopic.CHESS_CLOCK ->
        "A full-screen two-player chess clock with increments, independent starting times, presets, themes, and optional Do Not Disturb."
    TrainerHelpTopic.VISUAL_STUDIO ->
        "Personalize TrainerFish. Piece sets, board colors, pane colors, and the app background update as soon as you choose them."
}

private fun helpSections(topic: TrainerHelpTopic): List<TrainerHelpSection> = when (topic) {
    TrainerHelpTopic.HOME -> listOf(
        TrainerHelpSection(
            "Choose a room",
            "Tactics develops pattern recognition; Endgame teaches technique; Beat the Fish provides practical play; Game Recorder captures OTB games; Chess TV follows live master games; and Chess Clock is ready for tournament use."
        ),
        TrainerHelpSection(
            "Opening and PGN study",
            "Opening Explorer and the full PGN Reader now open in the separate Trainer Chess Openings Coach app. TrainerFish remains focused on tactics, endgames, play, recording, and live viewing."
        ),
        TrainerHelpSection(
            "Settings and appearance",
            "Open Visual Studio for pieces and colors. Inside a training room, use Settings for your profile, board, sounds, orientation, purchases, and help for the mode currently on screen."
        ),
        TrainerHelpSection(
            "Board-first design",
            "TrainerFish gives the board priority. Rotate the device when useful, and drag a red splitter in screens that provide one to resize the board and information pane."
        )
    )

    TrainerHelpTopic.TACTICS -> listOf(
        TrainerHelpSection(
            "1. Create or choose a cycle",
            "Use the Tactics Planner to select difficulty, rating range, themes, and cycle size. You may keep several cycles and continue any unfinished one."
        ),
        TrainerHelpSection(
            "2. Solve the position",
            "Play the best move on the board. TrainerFish replies with the puzzle line. Use Give up only when you want the solution; doing so affects the attempt result."
        ),
        TrainerHelpSection(
            "3. Review and learn",
            "After the puzzle, use the large Back and Next controls to replay the line. You can move pieces during review, inspect alternatives with the engine, bookmark the puzzle, or share the solved-puzzle card."
        ),
        TrainerHelpSection(
            "4. Repeat the cycle",
            "Finish every puzzle, then repeat the same set. Try to improve accuracy first and speed second. Your Tactics Elo and cycle records are updated automatically."
        )
    )

    TrainerHelpTopic.ENDGAME -> listOf(
        TrainerHelpSection(
            "Choose a room",
            "Endgame Course contains essential sparring positions. Endgame Tactics asks for the critical first move. Endgame Studies presents composed positions and ideas."
        ),
        TrainerHelpSection(
            "Read the note first",
            "The title and Note card explain the position's objective. In Course positions, play your side while Trainer Fish defends after a short thinking delay."
        ),
        TrainerHelpSection(
            "Navigate and review",
            "Use Previous, Next, or the position number to move through the collection. Review the played line with the navigation controls without changing the board orientation."
        )
    )

    TrainerHelpTopic.BEAT_FISH -> listOf(
        TrainerHelpSection(
            "Start a game",
            "Choose a Trainer Fish opponent, side, and time control. You can also load the most recent position retained from Tactics or Endgame and play from there."
        ),
        TrainerHelpSection(
            "During play",
            "Move by drag or tap. The live clock is authoritative. Navigation is for reviewing positions; use Play from here when you intentionally want to branch from a reviewed position."
        ),
        TrainerHelpSection(
            "After the game",
            "Open analysis to inspect the move list and engine lines, add annotations, save the PGN, or share the game. The last game is retained for continuation."
        )
    )

    TrainerHelpTopic.GAME_RECORDER -> listOf(
        TrainerHelpSection(
            "Before recording",
            "Enter the player and event details, choose board orientation, then place the board at the game's starting position."
        ),
        TrainerHelpSection(
            "Record moves",
            "Enter each move on the board as it is played. Use the navigation controls to verify the score and correct an entry before continuing."
        ),
        TrainerHelpSection(
            "Finish and export",
            "When the game ends, enter the result, save the PGN, and open analysis if desired. Engine help is intended for post-game review, not while an OTB game is in progress."
        )
    )

    TrainerHelpTopic.CHESS_TV -> listOf(
        TrainerHelpSection(
            "Choose what to watch",
            "Use the Home and Help icons in the header. Open the blue hamburger beside PGN moves to watch the TV top game, follow a Lichess player, or open Live broadcasts. In the broadcast chooser, select up to eight games across any number of tournaments, then tap Done."
        ),
        TrainerHelpSection(
            "Switch broadcast boards",
            "Swipe the board left for the next selected game or right for the previous one, or use ⏮ and ⏭ below the PGN. Only the displayed game is streamed, so an eight-game list remains lightweight. Use Clear list in the chooser to start a new selection."
        ),
        TrainerHelpSection(
            "Live and analysis",
            "Tap the LIVE badge or choose Analyze game to pause the stream and inspect the position. Tap the badge again or choose Reconnect live to return to the same game and preserved watch list."
        ),
        TrainerHelpSection(
            "Moves and engine",
            "Tap a move or use the four inner navigation controls to detach and review. The flip icon to the right of PGN moves reverses the board. Tap the large power button beside the evaluation to turn Stockfish on or off; green means on and red means off. Broadcast analysis searches to depth 50. Engine analysis is locked only while following a specific player's game."
        )
    )

    TrainerHelpTopic.CHESS_CLOCK -> listOf(
        TrainerHelpSection(
            "Start and make moves",
            "Tap a player's pad to start the opponent's clock. After every move, tap the pad belonging to the player who just moved; the increment is added automatically."
        ),
        TrainerHelpSection(
            "Center control",
            "Tap the center button to pause or resume. Long-press it for Settings, Restart, Help, or Exit. At the initial position, tapping the center button starts the top clock."
        ),
        TrainerHelpSection(
            "Tournament setup",
            "Settings provides presets, separate times and increments, low-time tenths, color themes, fonts, and Do Not Disturb. The screen stays awake while the clock is open."
        )
    )

    TrainerHelpTopic.VISUAL_STUDIO -> listOf(
        TrainerHelpSection(
            "Piece sets",
            "Tap a King-and-Pawn preview to apply that piece design throughout TrainerFish."
        ),
        TrainerHelpSection(
            "Board and pane colors",
            "Choose the board first, then select a pane palette that complements it. Changes are saved immediately and remain after restarting the app."
        ),
        TrainerHelpSection(
            "Background and notifications",
            "Choose Light or Dark for the app background. Show puzzle notification lets you preview the daily training reminder without waiting for its schedule."
        )
    )
}

@Composable
internal fun TrainerFishHelpDialog(
    show: Boolean,
    initialTopic: TrainerHelpTopic,
    onDismiss: () -> Unit
) {
    if (!show) return

    var topic by remember(show, initialTopic) { mutableStateOf(initialTopic) }
    val topics = remember { TrainerHelpTopic.entries.toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(topic.emoji, fontSize = 28.sp)
                Column {
                    Text(
                        "Contextual Help",
                        color = Color(0xFF1565C0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(topic.title, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    topics.forEach { candidate ->
                        val selected = candidate == topic
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) Color(0xFF1565C0) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { topic = candidate }
                        ) {
                            Text(
                                "${candidate.emoji} ${candidate.shortTitle}",
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        helpIntro(topic),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    helpSections(topic).forEach { section ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                section.title,
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                section.body,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
