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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
            depth = 0.20f,
            refractionStrength = 1.0f,
            refractionDisplacement = 48.dp,
            refractionHeightFraction = 0.50f,
            refractionFoldStrength = 0f,
            edgeSoftness = 0.5.dp,
            specularIntensity = 0f,
            ambientResponse = 0f,
            specularExponent = 24f,
            fresnelExponent = 2.4f,
            surfaceProfile = SurfaceProfile.Lip,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.08f,
            contrast = 0.04f,
            whitePoint = 0f,
            chromaMultiplier = 1.05f,
            contentNormalBlend = 0.06f,
            borderAlpha = 0f,
            borderWidth = 0.dp,
        )
    }

    AppGlassSurface(
        modifier = modifier.shadow(
            elevation = if (dark) 10.dp else 12.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.22f else 0.10f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.18f)
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

/** Cached geometry/brushes render a clean translucent liquid glass body sheen without edge self-illumination. */
internal fun Modifier.navigationCrystalBevel(
    dark: Boolean,
    vertical: Boolean,
    lightPosition: State<Float>,
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
    borderWidth: Dp = 1.dp
): Modifier = drawWithCache {
    val cornerRadiusPx = size.minDimension / 2f

    // Ambient volumetric sheen: gentle translucent lift across the glass body without bright edge outline
    val bodySheenBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.06f else 0.10f),
        0.50f to Color.White.copy(alpha = if (dark) 0.02f else 0.04f),
        1f to Color.Transparent,
        startY = 0f,
        endY = size.height
    )

    onDrawBehind {
        // Ambient volumetric translucent sheen across the glass body
        drawRoundRect(
            brush = bodySheenBrush,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
    }
}
