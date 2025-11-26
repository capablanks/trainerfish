package com.tonorbe.trainerfish

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeTour(
    modifier: Modifier = Modifier,
    onFinish: () -> Unit
) {


    val ctx = LocalContext.current

    val pages = remember {
        listOf(
            // --- PAGE 1 : TrainerFish Icon + Intro ---
            TourPage(
                title = "Welcome to TrainerFish",
                body = "Your complete chess-training system: tactics, endgames, and opening mastery — all powered by Stockfish.",
                emoji = "🐟" // You can replace this with ImagePainter if you want the actual icon
            ),

            // --- PAGE 2 : Woodpecker (261k puzzles) ---
            TourPage(
                title = "261,000+ Tactical Puzzles",
                body = "TrainerFish includes a Woodpecker-style training system built from more than 261,000 rated puzzles, indexed by tactical themes.\n\nRepeat cycles to build permanent pattern recognition.",
                emoji = "🎯"
            ),

            // --- PAGE 3 : Endgame Course ---
            TourPage(
                title = "Endgame Course",
                body = "Practice the most important endgame positions with Stockfish as a silent sparring partner.\n\nLucena, Tarrasch, Philidor, fortress positions, technique positions — all structured for deliberate practice.",
                emoji = "🏁"
            ),

            // --- PAGE 4 : Opening Explorer ---
            TourPage(
                title = "Grandmaster Opening Explorer",
                body = "Explore openings built from super-GM games plus grandmaster analysis of the most critical and popular lines.\n\nSee the best moves, frequencies, and ideas at every turn.",
                emoji = "📖"
            ),

            // --- PAGE 5 : Ready ---
            TourPage(
                title = "You're All Set",
                body = "Solve puzzles, drill your endgames, and explore openings — all in one clean interface.\n\nTap Get Started to begin your training.",
                emoji = "🚀"
            )
        )
    }


    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pages.size - 1

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

            // Bottom buttons
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f)
                ) { Text("Skip") }

                Spacer(Modifier.width(8.dp))

                val isLast = pagerState.currentPage == last
                Button(
                    onClick = {
                        if (isLast) onFinish()
                        else scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier.weight(1.4f)
                ) { Text(if (isLast) "Get Started" else "Next") }
            }
        }
    }
}

@Composable
private fun TourCard(page: TourPage) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(page.emoji, fontSize = 64.sp)
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
    }
}

private data class TourPage(
    val title: String,
    val body: String,
    val emoji: String
)
