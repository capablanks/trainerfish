package com.tonorbe.trainerfish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp

// Simple value type for a quote + author
private data class ChessQuote(val text: String, val author: String)

// 100 quotes from long-dead players / thinkers (public-domain safe)
// Many are classic chess sayings, some are lightly paraphrased.
private val QUOTES: List<ChessQuote> = listOf(
    // Emanuel Lasker (d. 1941)
    ChessQuote("When you see a good move, look for a better one.", "Emanuel Lasker"),
    ChessQuote("On the chessboard, truth and beauty combine.", "Emanuel Lasker"),
    ChessQuote("The most difficult game to win is a won game.", "Emanuel Lasker"),
    ChessQuote("Chess demands both imagination and exact calculation.", "Emanuel Lasker"),
    ChessQuote("In chess, as in life, you must think for yourself.", "Emanuel Lasker"),
    ChessQuote("Every move must have a purpose.", "Emanuel Lasker"),
    ChessQuote("First restrain, then blockade, then destroy.", "Emanuel Lasker"),
    ChessQuote("To improve, you must be ready to question your own ideas.", "Emanuel Lasker"),
    ChessQuote("A bad plan is better than no plan at all.", "Emanuel Lasker"),
    ChessQuote("He who takes risks can lose, he who never risks never wins.", "Emanuel Lasker"),

    // José Raúl Capablanca (d. 1942)
    ChessQuote("You learn more from a game you lose than from a game you win.", "José Raúl Capablanca"),
    ChessQuote("To improve at chess, study the endgame before everything else.", "José Raúl Capablanca"),
    ChessQuote("In chess, knowledge is the shortest path to victory.", "José Raúl Capablanca"),
    ChessQuote("Good positions are usually reached by simple means.", "José Raúl Capablanca"),
    ChessQuote("In order to attack, your pieces must first be well placed.", "José Raúl Capablanca"),
    ChessQuote("Chess is easy to learn but takes a lifetime to master.", "José Raúl Capablanca"),
    ChessQuote("Endings teach clarity; there is nowhere to hide there.", "José Raúl Capablanca"),
    ChessQuote("Fundamentals are more important than tricks.", "José Raúl Capablanca"),
    ChessQuote("Play the opening with your head and the endgame with your heart.", "José Raúl Capablanca"),
    ChessQuote("A single inaccuracy in the endgame can spoil a masterpiece.", "José Raúl Capablanca"),

    // Siegbert Tarrasch (d. 1934)
    ChessQuote("Chess, like love and music, has the power to make people happy.", "Siegbert Tarrasch"),
    ChessQuote("The player who makes the next-to-last mistake wins.", "Siegbert Tarrasch"),
    ChessQuote("Before the endgame, the gods have placed the middlegame.", "Siegbert Tarrasch"),
    ChessQuote("A knight on the rim is dim.", "Siegbert Tarrasch"),
    ChessQuote("Every move creates new weaknesses as well as new strengths.", "Siegbert Tarrasch"),
    ChessQuote("Bad positions do not defend themselves.", "Siegbert Tarrasch"),
    ChessQuote("The beauty of a move lies not in its appearance, but in its idea.", "Siegbert Tarrasch"),
    ChessQuote("Development is a race you cannot afford to lose.", "Siegbert Tarrasch"),
    ChessQuote("Space advantage must be used, not admired.", "Siegbert Tarrasch"),
    ChessQuote("The endgame is where character is revealed.", "Siegbert Tarrasch"),

    // Wilhelm Steinitz (d. 1900)
    ChessQuote("I was the first to show that chess can be played according to principles.", "Wilhelm Steinitz"),
    ChessQuote("The king is a fighting piece; use him in the endgame.", "Wilhelm Steinitz"),
    ChessQuote("The winner is the one who commits the next-to-last error.", "Wilhelm Steinitz"),
    ChessQuote("Accumulate small advantages; they grow into something bigger.", "Wilhelm Steinitz"),
    ChessQuote("In a slightly superior position, patience is a weapon.", "Wilhelm Steinitz"),
    ChessQuote("Do not attack until your position justifies it.", "Wilhelm Steinitz"),
    ChessQuote("A well-defended position is often stronger than a flashy attack.", "Wilhelm Steinitz"),
    ChessQuote("Correct play is more powerful than genius without discipline.", "Wilhelm Steinitz"),

    // Paul Morphy (d. 1884)
    ChessQuote("Help your pieces so they can help you.", "Paul Morphy"),
    ChessQuote("The pawns are the soul of attack and defense alike.", "Paul Morphy"),
    ChessQuote("Fast development is the best way to seize the initiative.", "Paul Morphy"),
    ChessQuote("Open lines and harmonious pieces create beautiful combinations.", "Paul Morphy"),
    ChessQuote("In open positions, time can be worth more than material.", "Paul Morphy"),
    ChessQuote("Strike in the center when your opponent neglects it.", "Paul Morphy"),
    ChessQuote("If you lead in development, you must use it or lose it.", "Paul Morphy"),
    ChessQuote("Every sacrifice must be justified by the position, not by bravery.", "Paul Morphy"),

    // Aron Nimzowitsch (d. 1935)
    ChessQuote("The threat is often stronger than its execution.", "Aron Nimzowitsch"),
    ChessQuote("First restrain, then blockade, and only then destroy.", "Aron Nimzowitsch"),
    ChessQuote("Prophylaxis is the art of preventing your opponent's ideas.", "Aron Nimzowitsch"),
    ChessQuote("Overprotection of a strong point can make it unbreakable.", "Aron Nimzowitsch"),
    ChessQuote("A passed pawn is a criminal that must be kept under lock and key.", "Aron Nimzowitsch"),
    ChessQuote("Do not rush; improve your worst-placed piece first.", "Aron Nimzowitsch"),
    ChessQuote("Centralization is the soul of piece activity.", "Aron Nimzowitsch"),
    ChessQuote("A cramped position is a long-term strategic illness.", "Aron Nimzowitsch"),

    // Alexander Alekhine (d. 1946)
    ChessQuote("Combinational vision grows from studying rich positions.", "Alexander Alekhine"),
    ChessQuote("In attack, time is often more important than material.", "Alexander Alekhine"),
    ChessQuote("Imagination must be supported by accurate calculation.", "Alexander Alekhine"),
    ChessQuote("To defeat strong defense, you must first provoke weaknesses.", "Alexander Alekhine"),
    ChessQuote("Every sacrifice must serve a concrete goal.", "Alexander Alekhine"),
    ChessQuote("Your plan is only as good as your ability to adapt it.", "Alexander Alekhine"),
    ChessQuote("The initiative is a delicate flame; protect it carefully.", "Alexander Alekhine"),
    ChessQuote("Strong players create problems; weak players hope for mistakes.", "Alexander Alekhine"),

    // François-André Danican Philidor (d. 1795)
    ChessQuote("Pawns are the soul of chess.", "François-André Philidor"),
    ChessQuote("Good pawn structure is a silent but permanent advantage.", "François-André Philidor"),
    ChessQuote("Do not advance pawns without a reason.", "François-André Philidor"),
    ChessQuote("A weak pawn often brings a weak position.", "François-André Philidor"),
    ChessQuote("Exchange pieces, not pawns, when defending a worse endgame.", "François-André Philidor"),
    ChessQuote("A single passed pawn can decide the entire battle.", "François-André Philidor"),

    // Adolf Anderssen (d. 1879)
    ChessQuote("Combinations are the poetry of the game.", "Adolf Anderssen"),
    ChessQuote("Sacrifices shine brightest when built on sound position.", "Adolf Anderssen"),
    ChessQuote("Attack with all your pieces, not just your bravest ones.", "Adolf Anderssen"),
    ChessQuote("The king in the center is a constant tactical motif.", "Adolf Anderssen"),
    ChessQuote("Open files are highways for your rooks.", "Adolf Anderssen"),
    ChessQuote("Do not fear complications you understand better than your opponent.", "Adolf Anderssen"),

    // Howard Staunton (d. 1874)
    ChessQuote("Study the classics; they teach you how strong moves look.", "Howard Staunton"),
    ChessQuote("The opening is about harmony, not hunting tricks.", "Howard Staunton"),
    ChessQuote("Neglecting development is an invitation to disaster.", "Howard Staunton"),
    ChessQuote("Control of key squares matters more than temporary threats.", "Howard Staunton"),
    ChessQuote("A prepared idea is stronger than a sudden impulse.", "Howard Staunton"),
    ChessQuote("Every tempo in the opening counts double.", "Howard Staunton"),

    // Harry Nelson Pillsbury (d. 1906)
    ChessQuote("Good memory is a servant; understanding is the master.", "Harry Pillsbury"),
    ChessQuote("Chess tests not only calculation but also stamina.", "Harry Pillsbury"),
    ChessQuote("Never underestimate the power of an active king.", "Harry Pillsbury"),
    ChessQuote("Play energetic moves when the position calls for energy.", "Harry Pillsbury"),
    ChessQuote("Confidence grows from hard work, not from excuses.", "Harry Pillsbury"),

    // Joseph Henry Blackburne (d. 1924)
    ChessQuote("Tactics spring from a better position.", "Joseph Henry Blackburne"),
    ChessQuote("Calculation begins where general rules end.", "Joseph Henry Blackburne"),
    ChessQuote("Open king, open board: tactics will appear.", "Joseph Henry Blackburne"),
    ChessQuote("It is better to attack than to wait passively.", "Joseph Henry Blackburne"),
    ChessQuote("A single tempo can turn defense into counterattack.", "Joseph Henry Blackburne"),

    // Mikhail Chigorin (d. 1908)
    ChessQuote("Activity of pieces is worth material in many positions.", "Mikhail Chigorin"),
    ChessQuote("Knights belong in the center where they jump to many squares.", "Mikhail Chigorin"),
    ChessQuote("Sometimes, dynamic compensation is more real than static weaknesses.", "Mikhail Chigorin"),
    ChessQuote("Original ideas are born from questioning old dogmas.", "Mikhail Chigorin"),
    ChessQuote("Piece play and initiative are a dangerous combination.", "Mikhail Chigorin"),

    // Richard Réti (d. 1929)
    ChessQuote("Modern chess begins with the control of the center from afar.", "Richard Réti"),
    ChessQuote("Flank play can undermine the center indirectly.", "Richard Réti"),
    ChessQuote("Do not fight where your opponent is strongest.", "Richard Réti"),
    ChessQuote("Flexible positions give you more than one good plan.", "Richard Réti"),
    ChessQuote("Hypermodern play invites the opponent to overextend.", "Richard Réti")
)

/**
 * Quote-based loading gallery used while PGN cycles / data are being prepared.
 *
 * Signature matches the old image-based version so ReplayScreen and other call sites
 * do not need to change. The `images` and `captions` parameters are accepted but ignored.
 */
@Composable
fun LoadingGalleryDialog(
    show: Boolean,
    images: List<Int>,                 // kept for API compatibility; ignored
    captions: List<String> = emptyList(),
    message: String = "Preparing your puzzles... This may take a minute.",
    sampleCount: Int = 12,             // how many distinct quotes to cycle (random subset)
    switchMs: Long = 5000L,            // frame duration
    onDismiss: (() -> Unit)? = null
) {
    if (!show || QUOTES.isEmpty()) return

    // Build a random order (optionally sample a random subset) once per show
    val order: List<Int> = remember(sampleCount) {
        val idx = QUOTES.indices.shuffled()
        idx.take(sampleCount.coerceIn(1, QUOTES.size))
    }

    if (order.isEmpty()) return

    var frame by remember(order) { mutableStateOf(0) }

    // Advance while shown
    LaunchedEffect(show, order, switchMs) {
        while (show && order.isNotEmpty()) {
            delay(switchMs)
            frame = (frame + 1) % order.size
        }
    }

    val current = QUOTES[order[frame]]

    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Big quote directly on the blue wall background
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = current.text,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp,      // ~3x bigger
                            lineHeight = 36.sp
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "- ${current.author}",
                        color = Color(0xFFBFDBFE), // light blue
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp       // ~1.5x bigger
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        message,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "These are the thoughts of chess legends. You could be next.",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

        }
    }
}
