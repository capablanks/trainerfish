package com.tonorbe.trainerfish

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Shared TrainerFish board splitter.
 *
 * VERTICAL   = vertical red bar between a left board pane and a right info pane.
 * HORIZONTAL = horizontal red bar between a top board pane and a bottom info pane.
 */
enum class BoardResizeSplitterOrientation { VERTICAL, HORIZONTAL }

@Composable
fun BoardResizeSplitter(
    orientation: BoardResizeSplitterOrientation,
    totalPx: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Red,
    onDeltaFraction: (Float) -> Unit
) {
    val safeTotalPx = totalPx.coerceAtLeast(1f)

    Box(
        modifier = modifier
            .then(
                if (orientation == BoardResizeSplitterOrientation.VERTICAL) {
                    Modifier.fillMaxHeight().width(12.dp)
                } else {
                    Modifier.fillMaxWidth().height(12.dp)
                }
            )
            .pointerInput(orientation, safeTotalPx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = if (orientation == BoardResizeSplitterOrientation.VERTICAL) {
                        dragAmount.x / safeTotalPx
                    } else {
                        dragAmount.y / safeTotalPx
                    }
                    onDeltaFraction(delta)
                }
            }
    ) {
        if (orientation == BoardResizeSplitterOrientation.VERTICAL) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(color)
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(color)
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
