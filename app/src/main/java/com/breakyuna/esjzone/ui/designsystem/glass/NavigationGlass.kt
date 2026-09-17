@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.SurfaceProfile

/**
 * A modern liquid frosted glass dock surface with authentic backdrop blur,
 * delicate top-lit specular highlight rim, and translucent surface wash.
 */
@Composable
fun AppNavigationGlassSurface(
    scene: AppGlassScene,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    selectedFraction: Float = 0.125f,
    itemCount: Int = 4,
    shape: RoundedCornerShape = if (vertical) RoundedCornerShape(percent = 50) else RoundedCornerShape(NavigationGlassMetrics.bottomCornerRadius),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    // Finite, selection-driven motion only. Compose respects the system duration scale.
    // Read in the draw phase, so each frame does not rebuild the Haze style/capture.
    val lightPosition = animateFloatAsState(
        targetValue = selectedFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "navigation_glass_light"
    )
    val spec = remember(colors, dark, shape, vertical) {
        AppGlassSpec(
            shape = shape,
            material = AppGlassMaterial.REGULAR,
            tint = if (dark) colors.surfaceContainer else colors.surfaceContainerLow,
            // Transparent glass with light tint to allow background refraction to show through
            alpha = 1f,
            tintAlpha = if (dark) 0.12f else 0.16f,
            fallbackAlpha = 0.96f,
            blurRadius = 8.dp,
            depth = 0.18f,
            refractionStrength = 0.95f,
            refractionDisplacement = if (vertical) 14.dp else 22.dp,
            refractionHeightFraction = 0.50f,
            refractionFoldStrength = 0.45f,
            edgeSoftness = 1.2.dp,
            specularIntensity = if (dark) 0.70f else 0.80f,
            ambientResponse = if (dark) 0.35f else 0.30f,
            specularExponent = 28f,
            fresnelExponent = 2.6f,
            surfaceProfile = SurfaceProfile.Circle,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.05f,
            contrast = 0.04f,
            whitePoint = 0.06f,
            chromaMultiplier = 1.04f,
            contentNormalBlend = 0.06f,
            borderAlpha = 0f,
            borderWidth = 0.dp,
        )
    }

    AppGlassSurface(
        modifier = modifier.shadow(
            elevation = if (dark) 8.dp else 10.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.16f else 0.06f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.26f else 0.12f)
        ),
        spec = spec,
        scene = scene
    ) {
        // BoxScope member: must not participate in measuring the wrap-content side rail.
        Box(
            Modifier
                .matchParentSize()
                .navigationCrystalBevel(
                    dark = dark,
                    vertical = vertical,
                    lightPosition = lightPosition,
                    shape = shape
                )
        )
        NavigationSelectionLens(
            scene = scene,
            position = lightPosition,
            targetFraction = selectedFraction,
            itemCount = itemCount,
            vertical = vertical,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

/** Cached geometry/brushes render a clean, high-end liquid glass border with dynamic specular highlight. */
internal fun Modifier.navigationCrystalBevel(
    dark: Boolean,
    vertical: Boolean,
    lightPosition: State<Float>,
    shape: RoundedCornerShape = if (vertical) RoundedCornerShape(percent = 50) else RoundedCornerShape(NavigationGlassMetrics.bottomCornerRadius),
    borderWidth: Dp = 1.dp
): Modifier = drawWithCache {
    val strokeWidth = borderWidth.toPx()
    val halfStroke = strokeWidth / 2f
    val rtl = layoutDirection == LayoutDirection.Rtl
    val lightStart = Offset(if (rtl) size.width else 0f, 0f)
    val lightEnd = Offset(if (rtl) 0f else size.width, size.height)

    // Ambient volumetric sheen: delicate, subtle lift so the background remains transparent
    val bodySheenBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.08f else 0.14f),
        0.50f to Color.White.copy(alpha = if (dark) 0.03f else 0.06f),
        1f to Color.Transparent,
        startY = 0f,
        endY = size.height
    )

    // Sleek border gradient illuminating top rim and wrapping gracefully around rounded corner curves
    val lightAngleStart = if (vertical) lightStart else Offset(0f, 0f)
    val lightAngleEnd = if (vertical) lightEnd else Offset(size.width * 0.70f, size.height)
    val outerBorderBrush = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.72f else 0.92f),
        0.20f to Color.White.copy(alpha = if (dark) 0.40f else 0.65f),
        0.50f to Color.White.copy(alpha = if (dark) 0.20f else 0.35f),
        0.80f to Color.White.copy(alpha = if (dark) 0.30f else 0.45f),
        1f to if (dark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.25f),
        start = lightAngleStart,
        end = lightAngleEnd
    )

    // Determine corner radius matching the exact dock shape
    val cornerRadiusPx = if (vertical) size.minDimension / 2f else NavigationGlassMetrics.bottomCornerRadius.toPx().coerceAtMost(size.minDimension / 2f)

    // Dedicated optical refraction fringe along the curved rounded glass corners
    val cornerLensFringeBrush = Brush.horizontalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.38f else 0.55f),
        (cornerRadiusPx / size.width).coerceIn(0f, 1f) to Color.Transparent,
        ((size.width - cornerRadiusPx) / size.width).coerceIn(0f, 1f) to Color.Transparent,
        1f to Color.White.copy(alpha = if (dark) 0.30f else 0.45f)
    )

    // A reflected strip light that travels along the rim when the tab changes
    val glintRadius = (size.minDimension * 0.80f).coerceAtLeast(1f)
    val glintBrush = Brush.radialGradient(
        0f to Color.White.copy(alpha = if (dark) 0.40f else 0.55f),
        0.40f to Color.White.copy(alpha = if (dark) 0.18f else 0.22f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = glintRadius
    )
    val glintCompression = strokeWidth * 2.5f / glintRadius

    onDrawBehind {
        if (size.minDimension <= strokeWidth * 2f) return@onDrawBehind

        // 1. Ambient Volumetric Luminous Sheen (lifts the body brightness above background)
        drawRoundRect(
            brush = bodySheenBrush,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        // 2. Optical curved lens refraction edge along the rounded corners
        if (!vertical && cornerRadiusPx > 0f) {
            val lensRimStroke = 2.dp.toPx()
            val lensRimInset = strokeWidth + 0.5.dp.toPx()
            val lensRimHeight = (size.height - lensRimInset * 2f).coerceAtLeast(0f)
            if (lensRimHeight > 0f) {
                val lensRimRadius = (cornerRadiusPx - lensRimInset).coerceAtLeast(0f)
                drawRoundRect(
                    brush = cornerLensFringeBrush,
                    topLeft = Offset(lensRimInset, lensRimInset),
                    size = Size(size.width - lensRimInset * 2f, lensRimHeight),
                    cornerRadius = CornerRadius(lensRimRadius, lensRimRadius),
                    style = Stroke(lensRimStroke)
                )
            }
        }

        // 3. Single refined outer border with angled highlight wrapping around the curves
        val outerRimSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val outerCornerRadius = (cornerRadiusPx - halfStroke).coerceAtLeast(0f)
        drawRoundRect(
            brush = outerBorderBrush,
            topLeft = Offset(halfStroke, halfStroke),
            size = outerRimSize,
            cornerRadius = CornerRadius(outerCornerRadius, outerCornerRadius),
            style = Stroke(strokeWidth)
        )

        // 4. Dynamic traveling glint
        val progress = lightPosition.value.coerceIn(0f, 1f)
        val glintCenter = if (vertical) {
            Offset(if (rtl) size.width else 0f, size.height * progress)
        } else {
            Offset(size.width * (if (rtl) 1f - progress else progress), 0f)
        }
        translate(left = glintCenter.x, top = glintCenter.y) {
            scale(
                scaleX = if (vertical) glintCompression else 1f,
                scaleY = if (vertical) 1f else glintCompression,
                pivot = Offset.Zero
            ) {
                drawCircle(brush = glintBrush, radius = glintRadius, center = Offset.Zero)
            }
        }
    }
}
