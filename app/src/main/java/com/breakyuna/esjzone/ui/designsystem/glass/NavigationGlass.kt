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
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
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
            refractionStrength = 0.85f,
            refractionDisplacement = if (vertical) 12.dp else 18.dp,
            refractionHeightFraction = 0.48f,
            refractionFoldStrength = 0.42f,
            edgeSoftness = 1.dp,
            specularIntensity = if (dark) 0.65f else 0.72f,
            ambientResponse = if (dark) 0.32f else 0.26f,
            specularExponent = 32f,
            fresnelExponent = 3f,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.03f,
            contrast = 0.04f,
            whitePoint = 0.06f,
            chromaMultiplier = 1.04f,
            contentNormalBlend = 0.04f,
            borderAlpha = 0f,
            borderWidth = 0.dp,
        )
    }

    AppGlassSurface(
        modifier = modifier.shadow(
            elevation = if (dark) 10.dp else 12.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.20f else 0.08f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.38f else 0.16f)
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
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
    borderWidth: Dp = 1.dp
): Modifier = drawWithCache {
    val strokeWidth = borderWidth.toPx()
    val halfStroke = strokeWidth / 2f
    val hairline = 0.75.dp.toPx()
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

    // Horizontal liquid caustic refraction beam across the lower curve (82% of height)
    val causticHeight = 4.5.dp.toPx()
    val causticY = size.height * 0.82f
    val causticBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.30f to Color.White.copy(alpha = if (dark) 0.35f else 0.55f),
        0.50f to Color.White.copy(alpha = if (dark) 0.55f else 0.80f),
        0.70f to Color.White.copy(alpha = if (dark) 0.35f else 0.55f),
        1f to Color.Transparent,
        startY = causticY - causticHeight / 2f,
        endY = causticY + causticHeight / 2f
    )

    // Subtle optical shadow shelf right beneath the caustic refraction band
    val causticShadowBrush = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = if (dark) 0.18f else 0.08f),
        1f to Color.Transparent,
        startY = causticY + causticHeight / 2f,
        endY = causticY + causticHeight / 2f + 2.dp.toPx()
    )

    // Sleek single-stroke gradient simulating light hitting the top bevel and glowing rim
    val outerBorderBrush = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.65f else 0.90f),
        0.30f to Color.White.copy(alpha = if (dark) 0.28f else 0.48f),
        0.70f to Color.White.copy(alpha = if (dark) 0.12f else 0.22f),
        1f to if (dark) Color.Black.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.30f),
        start = if (vertical) lightStart else Offset(0f, 0f),
        end = if (vertical) lightEnd else Offset(0f, size.height)
    )

    // Soft top inner reflection rim
    val innerGlowBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.25f else 0.40f),
        0.35f to Color.Transparent,
        1f to Color.Transparent,
        startY = 0f,
        endY = size.height
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

    // Determine corner radius: full half-circle capsule (percent = 50)
    val cornerRadiusPx = size.minDimension / 2f

    onDrawBehind {
        if (size.minDimension <= strokeWidth * 2f) return@onDrawBehind

        // 1. Ambient Volumetric Luminous Sheen (lifts the body brightness above background)
        drawRoundRect(
            brush = bodySheenBrush,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        // 2. Horizontal Liquid Caustic Refraction Beam & Optical Shelf (in horizontal mode)
        if (!vertical) {
            val causticInsetX = cornerRadiusPx * 0.40f
            val causticWidth = (size.width - causticInsetX * 2f).coerceAtLeast(0f)
            if (causticWidth > 0f) {
                // Bright caustic refraction streak
                drawRoundRect(
                    brush = causticBrush,
                    topLeft = Offset(causticInsetX, causticY - causticHeight / 2f),
                    size = Size(causticWidth, causticHeight),
                    cornerRadius = CornerRadius(causticHeight / 2f, causticHeight / 2f)
                )
                // Optical shadow shelf directly below the caustic ridge
                drawRoundRect(
                    brush = causticShadowBrush,
                    topLeft = Offset(causticInsetX, causticY + causticHeight / 2f),
                    size = Size(causticWidth, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.25.dp.toPx(), 1.25.dp.toPx())
                )
            }
        }

        // 3. Single refined outer border
        val outerRimSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val outerCornerRadius = (cornerRadiusPx - halfStroke).coerceAtLeast(0f)
        drawRoundRect(
            brush = outerBorderBrush,
            topLeft = Offset(halfStroke, halfStroke),
            size = outerRimSize,
            cornerRadius = CornerRadius(outerCornerRadius, outerCornerRadius),
            style = Stroke(strokeWidth)
        )

        // 4. Delicate top inner hairline
        val innerInset = strokeWidth + 0.5.dp.toPx()
        val innerRimSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)
        if (innerRimSize.minDimension > 0f) {
            val innerCornerRadius = (cornerRadiusPx - innerInset).coerceAtLeast(0f)
            drawRoundRect(
                brush = innerGlowBrush,
                topLeft = Offset(innerInset, innerInset),
                size = innerRimSize,
                cornerRadius = CornerRadius(innerCornerRadius, innerCornerRadius),
                style = Stroke(hairline)
            )
        }

        // 5. Dynamic traveling glint
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
