package com.tonorbe.trainerfish

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable





@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeTour(
    modifier: Modifier = Modifier,
    onFinish: (dontShowAgain: Boolean) -> Unit
) {



    val ctx = LocalContext.current

    val pages = remember {
        listOf(
            // --- PAGE 1 : TrainerFish Icon + Intro ---
            TourPage(
                title = "Welcome to TrainerFish",
                body = "Your complete chess-training system: tactics, endgames, and opening mastery - all powered by Stockfish.",
                emoji = "\uD83D\uDC1F" // You can replace this with ImagePainter if you want the actual icon
            ),

            // --- PAGE 2 : Woodpecker (261k puzzles) ---
            TourPage(
                title = "666,715 Tactical Puzzles",
                body = "TrainerFish includes a Woodpecker-style training system built from 666,715 rated puzzles, indexed by tactical themes. Define as many training cycles as you like, each with its own themes and rating range.\n\nRepeat cycles to build permanent pattern recognition.",
                emoji = "\uD83C\uDFAF"
            ),


            // --- PAGE 3 : Endgame Course ---
            TourPage(
                title = "Endgame Course",
                body = "Practice the most important endgame positions with Stockfish as a silent sparring partner.\n\nEach position comes with an engine evaluation and a short explanation of the key endgame principles. Lucena, Tarrasch, Philidor, fortress positions, technique drills - all structured for deliberate practice.",
                emoji = "🏁"
            ),


            // --- PAGE 4 : Opening Explorer ---
            TourPage(
                title = "Grandmaster Opening Explorer",
                body = "Explore a complete ECO-indexed opening tree with over 8 million positions - built solely from master games played in the last 10 years. Watch the animated move sequence from your chosen ECO, then let TrainerFish and Stockfish analyze alongside the tree so every variation is verified and validated by the strongest chess machine on earth.",
                emoji = "📖"
            ),

            TourPage(
                title = "Freebies to Enjoy!",
                body = "TrainerFish is completely free in this build.\n\n" +
                        "🟢 PGN Reader (Free)\n" +
                        "Read the built-in Chess Fundamentals library right away.\n" +
                        "Opening your own PGN files is also free in this build.\n\n" +
                        "🟢 Play Mode (Beat the Fish) (Free)\n" +
                        "Play full games against Stockfish, analyze freely, and enjoy unlimited practice.\n\n" +
                        "🟢 Chess Clock (Free)\n" +
                        "A clean, tournament-ready chess clock - completely free, no restrictions.\n\n" +
                        "You can train, play, and compete right away. Enjoy the full TrainerFish feature set while this free build is active.",
                emoji = "🎁"
            ),



            // --- PAGE 5 : Community / Facebook group ---
            TourPage(
                title = "Join the TrainerFish Community",
                body = "Got questions, ideas, or wins to share? Join our Facebook group for tips, updates, and support.",
                emoji = "💬",
                linkLabel = "facebook.com/groups/trainerapps",
                linkUrl = "https://www.facebook.com/groups/trainerapps/"
            ),

            // --- PAGE 6 : Ready ---
            TourPage(
                title = "You're All Set",
                body = "Solve puzzles, drill your endgames, and explore openings - all in one clean interface.\n\nTap Get Started to begin your training.",
                emoji = "🚀"
            )

        )
    }


    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pages.size - 1
    // "Do not show this again" flag (persisted by caller when true)
    val dontShowAgain = rememberSaveable { mutableStateOf(false) }

    // Guard against spam-clicks causing multiple navigations / state races.
    // Once true, we ignore further clicks.
    val finishLocked = rememberSaveable { mutableStateOf(false) }
    val nextLocked = rememberSaveable { mutableStateOf(false) }


    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "TrainerFish",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(12.dp))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                TourCard(pages[page])
            }

            Spacer(Modifier.height(8.dp))

            // Dots indicator
            Row(
                Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { idx ->
                    val active = pagerState.currentPage == idx
                    Box(
                        Modifier
                            .size(if (active) 10.dp else 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // "Do not show this again" checkbox (only on the last page)
            if (pagerState.currentPage == last) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain.value,
                        onCheckedChange = { dontShowAgain.value = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Do not show this again",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(4.dp))
            }

            // Bottom buttons
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (!finishLocked.value) {
                            finishLocked.value = true
                            onFinish(false) // Skip never disables the tour
                        }
                    },
                    enabled = !finishLocked.value,
                    modifier = Modifier.weight(1f)
                ) { Text("Skip") }

                Spacer(Modifier.width(8.dp))

                val isLast = pagerState.currentPage == last
                Button(
                    onClick = {
                        if (isLast) {
                            // Pass whether the user ticked "Do not show this again"
                            if (!finishLocked.value) {
                                finishLocked.value = true
                                onFinish(dontShowAgain.value)
                            }
                        } else {
                            if (!nextLocked.value) {
                                nextLocked.value = true
                                scope.launch {
                                    try {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    } finally {
                                        nextLocked.value = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !finishLocked.value,
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text(if (isLast) "Get Started" else "Next")
                }
            }

        }
    }
}

@Composable
private fun TourCard(page: TourPage) {
    val isIntroPage = page.title == "Welcome to TrainerFish"

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isIntroPage) {
            // Show the fish logo on a dark circle so it stands out
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.Black, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_fish),
                    contentDescription = "TrainerFish",
                    modifier = Modifier.size(72.dp)
                )
            }
        } else {
            // Other pages keep using emoji
            Text(page.emoji, fontSize = 64.sp)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            page.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            page.body,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            textAlign = TextAlign.Center
        )

        // Optional link (e.g., Facebook group)
        if (!page.linkLabel.isNullOrBlank() && !page.linkUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))

            val context = LocalContext.current
            Text(
                text = page.linkLabel,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.Underline
                ),
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(page.linkUrl))
                    context.startActivity(intent)
                }
            )
        }
    }
}



private data class TourPage(
    val title: String,
    val body: String,
    val emoji: String,
    val linkLabel: String? = null,
    val linkUrl: String? = null
)
