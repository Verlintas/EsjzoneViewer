package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Root presentation boundary for immersive reading. It deliberately owns only
 * the reader canvas and tap surface; Navigation 3 owns the route and back
 * stack outside this shell.
 */
@Composable
fun ReaderShell(
    background: Color,
    onReadingAreaTap: (xFraction: Float, yFraction: Float) -> Unit,
    horizontalSwipeEnabled: Boolean = false,
    onHorizontalSwipe: (forward: Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(horizontalSwipeEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var last = start
                    var childConsumed = down.isConsumed
                    var released: Offset? = null
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        childConsumed = childConsumed || change.isConsumed
                        last = change.position
                        if (!change.pressed) released = change.position
                    } while (event.changes.any { it.pressed })

                    val delta = last - start
                    val horizontalSwipe = horizontalSwipeEnabled &&
                        abs(delta.x) >= viewConfiguration.touchSlop * 4f &&
                        abs(delta.x) > abs(delta.y) * 1.35f
                    val release = released
                    when {
                        horizontalSwipe && !childConsumed -> onHorizontalSwipe(delta.x < 0f)
                        release != null && !childConsumed && delta.getDistance() < viewConfiguration.touchSlop -> {
                            onReadingAreaTap(
                                (release.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (release.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            },
        content = content
    )
}
