package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared with the actual tab layout so the lens cannot drift when its geometry changes. */
internal object NavigationGlassMetrics {
    val horizontalPadding = 4.dp
    val bottomHeight = 60.dp
    val bottomCornerRadius = 30.dp
    val bottomVerticalPadding = 4.dp
    val bottomItemHeight = 52.dp
    val bottomItemMaxWidth = 76.dp
    val railWidth = 58.dp
    val railItemSize = 48.dp
    val railVerticalPadding = 8.dp
    val railItemGap = 8.dp
}

/**
 * Animated soft glowing pill indicator underneath active tab glyphs and hit targets.
 * Retains spring physics and layout placement without frame-by-frame recomposition.
 */
@Composable
internal fun NavigationSelectionLens(
    scene: AppGlassScene,
    position: State<Float>,
    targetFraction: Float,
    itemCount: Int,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    require(itemCount > 0)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val shape = remember {
        RoundedCornerShape(percent = 50)
    }
    // A slower follower stretches the moving lens, then settles back to its resting shape.
    // Both springs retain their current values on rapid retargeting and honor duration scale 0.
    val tail = animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 90f),
        label = "navigation_lens_tail"
    )

    Layout(
        modifier = modifier,
        content = {
            val pillFill = if (dark) {
                Color.White.copy(alpha = 0.10f)
            } else {
                Color.White.copy(alpha = 0.22f)
            }
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(pillFill)
            )
        }
    ) { measurables, constraints ->
        // This overlay is always installed with BoxScope.matchParentSize().
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val horizontalPadding = NavigationGlassMetrics.horizontalPadding.roundToPx()
        val slotWidth = ((width - horizontalPadding * 2).coerceAtLeast(0)).toFloat() / itemCount
        val lensWidth = if (vertical) {
            NavigationGlassMetrics.railItemSize.roundToPx().coerceAtMost(width)
        } else {
            slotWidth.roundToInt().coerceAtMost(NavigationGlassMetrics.bottomItemMaxWidth.roundToPx())
        }
        val lensHeight = if (vertical) {
            NavigationGlassMetrics.railItemSize.roundToPx().coerceAtMost(height)
        } else {
            val availableHeight = (height - NavigationGlassMetrics.bottomVerticalPadding.roundToPx() * 2)
                .coerceAtLeast(0)
            NavigationGlassMetrics.bottomItemHeight.roundToPx().coerceAtMost(availableHeight)
        }
        val lens = measurables.single().measure(Constraints.fixed(lensWidth, lensHeight))
        // Column rounds the item and the gap independently, so do the same here.
        val railStep = NavigationGlassMetrics.railItemSize.roundToPx() +
            NavigationGlassMetrics.railItemGap.roundToPx()
        val railPadding = NavigationGlassMetrics.railVerticalPadding.roundToPx()

        layout(width, height) {
            // Read animated state only in placement: no frame-by-frame recomposition/remeasure.
            val head = position.value
            val index = (head * itemCount - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())
            val stretch = (abs(head - tail.value) * itemCount * 0.16f).coerceIn(0f, 0.12f)
            val along = 1f + stretch
            val across = 1f / along
            val centerX = if (vertical) width / 2f else horizontalPadding + (index + 0.5f) * slotWidth
            val centerY = if (vertical) railPadding + lensHeight / 2f + index * railStep else height / 2f
            // Keep the stretched material inside the capsule even at either end of a jump.
            val halfWidth = lensWidth * (if (vertical) across else along) / 2f
            val halfHeight = lensHeight * (if (vertical) along else across) / 2f
            val safeHalfWidth = halfWidth.coerceAtMost(width / 2f)
            val safeHalfHeight = halfHeight.coerceAtMost(height / 2f)
            val x = centerX.coerceIn(safeHalfWidth, width - safeHalfWidth) - lensWidth / 2f
            val y = centerY.coerceIn(safeHalfHeight, height - safeHalfHeight) - lensHeight / 2f
            lens.placeRelativeWithLayer(x.roundToInt(), y.roundToInt()) {
                // Transform this empty material only; tab content lives in the sibling Row/Column.
                scaleX = if (vertical) across else along
                scaleY = if (vertical) along else across
            }
        }
    }
}
