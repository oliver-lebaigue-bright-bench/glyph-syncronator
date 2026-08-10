@file:OptIn(ExperimentalMaterial3Api::class)

package com.better.nothing.music.visualizer.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.better.nothing.music.visualizer.R
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bolt
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.LayerGroup
import compose.icons.fontawesomeicons.solid.MobileAlt
import compose.icons.fontawesomeicons.solid.Music
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.blur
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Path as AndroidPath
import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.Shader

// Linear position (0..1) to Logarithmic Frequency (20..2000)
fun lerpLog(value: Float, min: Float, max: Float): Float {
    val logMin = ln(min)
    val logMax = ln(max)
    return exp(logMin + (logMax - logMin) * value)
}

// Logarithmic Frequency (20..2000) back to Linear position (0..1)
fun invLerpLog(freq: Float, min: Float, max: Float): Float {
    val logMin = ln(min)
    val logMax = ln(max)
    return (ln(freq) - logMin) / (logMax - logMin)
}

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)),
        color = containerColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = LocalIsGlassTheme.current
    val shape = MaterialTheme.shapes.extraLarge
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
            .clip(shape)
    ) {
        // Background layer with blur
        if (isGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (isGlass) Color.White.copy(alpha = 0.1f) 
                        else MaterialTheme.colorScheme.surface
                    )
            )
        }

        // Content layer
        Column(
            modifier = Modifier
                .padding(20.dp)
                .drawWithContent {
                    drawContent()
                    if (isGlass) {
                        // Top edge highlight
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                val r = 32.dp.toPx()
                                moveTo(0f, r)
                                quadraticTo(0f, 0f, r, 0f)
                                lineTo(size.width - r, 0f)
                                quadraticTo(size.width, 0f, size.width, r)
                            },
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.0f),
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.0f)
                                )
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                .border(
                    BorderStroke(0.5.dp, Color.White.copy(alpha = if (isGlass) 0.3f else 0.1f)),
                    shape = shape
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun ModernSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun DashboardTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isGlass = LocalIsGlassTheme.current
    val bananaMode = LocalBananaMode.current
    
    val backgroundColor by animateColorAsState(
        if (active) {
            if (isGlass) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary
        } else {
            if (isGlass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "tileBackground"
    )
    val contentColor by animateColorAsState(
        if (active) {
            if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary
        } else {
            if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
        },
        label = "tileContent"
    )

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        modifier = modifier
            .height(140.dp),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Box {
            // Background Layer
            if (isGlass) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.graphicsLayer {
                                    renderEffect = RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            } else Modifier
                        )
                        .background(backgroundColor.copy(alpha = 0.35f), MaterialTheme.shapes.large)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), MaterialTheme.shapes.large)
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(backgroundColor, MaterialTheme.shapes.large)
                )
            }

            // Content Layer
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (bananaMode) {
                    Text("🍌", fontSize = 32.sp)
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun MorphingPolygon(
    isBeatDetected: Boolean,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "polygonRotation")

    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "baseRotation"
    )

    val polygonBase = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.85f,
            rounding = CornerRounding(0.2f)
        )
    }

    var sourcePoly by remember { mutableStateOf(polygonBase) }
    var targetPoly by remember { mutableStateOf(polygonBase) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(isBeatDetected) {
        if (isBeatDetected) {
            sourcePoly = targetPoly
            targetPoly = RoundedPolygon.star(
                numVerticesPerRadius = (3..24).random(),
                innerRadius = (25..85).random() / 100f,
                rounding = CornerRounding((4..20).random() / 100f)
            )
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Smooth amplitude to avoid jitter, but kept responsive
    val animatedAmplitude by animateFloatAsState(
        targetValue = (amplitude * 2.5f).coerceAtMost(1.2f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "animatedAmplitude"
    )

    val morph = remember(sourcePoly, targetPoly) {
        Morph(sourcePoly, targetPoly)
    }

    val path = remember { AndroidPath() }
    val composePath = remember { AndroidPath().asComposePath() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier) {
        val size = size.minDimension
        // Base scale 0.15 + up to 0.85 from amplitude
        val scale = size * (0.15f + (animatedAmplitude * 0.7f))
        
        path.reset()
        matrix.reset()
        matrix.scale(scale, scale)
        matrix.translate(size / (2 * scale), size / (2 * scale))
        
        morph.toPath(progress.value, path)
        // Note: asComposePath() usually wraps the same underlying object, 
        // but we need to ensure the transformation is applied correctly.
        val currentComposePath = path.asComposePath()
        currentComposePath.transform(matrix)

        rotate(baseRotation) {
            drawPath(
                path = currentComposePath,
                color = color,
                style = Fill
            )
        }
    }
}

@Composable
fun ExpressiveSplitButton(
    modifier: Modifier = Modifier,
    primaryText: String,
    primaryIcon: ImageVector,
    onPrimaryClick: () -> Unit,
    secondaryText: String,
    secondaryIcon: ImageVector,
    onSecondaryClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Primary Action
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onPrimaryClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(2f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(primaryIcon, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(primaryText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        // Secondary Action
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSecondaryClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(secondaryIcon, null, modifier = Modifier.size(20.dp))
                if (secondaryText.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(secondaryText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowScope.OptionTile(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    // This state controls the weight expansion explicitly
    var isWeightExpanded by remember { mutableStateOf(false) }

    // Guaranteeing a minimum 120ms animation window
    LaunchedEffect(interactionSource) {
        var pressStartTime = 0L

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressStartTime = SystemClock.elapsedRealtime()
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    isWeightExpanded = true
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val elapsed = SystemClock.elapsedRealtime() - pressStartTime
                    val remainingFloorDelay = 150L - elapsed

                    // If the finger was released before 120ms, hold it open
                    if (remainingFloorDelay > 0) {
                        delay(remainingFloorDelay.milliseconds)
                    }
                    isWeightExpanded = false
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
        }
    }

    // Color States - Base them on selection OR active expansion animation
    val isGlass = LocalIsGlassTheme.current
    val isEffectivelySelected = (isSelected || isWeightExpanded) && enabled
    val backgroundColor by animateColorAsState(
        if (!enabled) {
            if (isGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else if (isEffectivelySelected) {
            if (isGlass) {
                // Highly obvious glass selection
                if (label.contains("Glass", ignoreCase = true)) Color.White.copy(alpha = 0.4f)
                else Color.White.copy(alpha = 0.3f)
            } else MaterialTheme.colorScheme.primary
        } else {
            if (isGlass) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        },
        label = "backgroundColor"
    )
    val contentColor by animateColorAsState(
        if (!enabled) {
            if (isGlass) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        } else if (isEffectivelySelected) {
            if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary
        } else {
            if (isGlass) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "contentColor"
    )

    // Corner Radius Animation
    val m3eEnabled = LocalM3EEnabled.current
    val targetRadius = if (isSelected && enabled) 32.dp else 20.dp
    val animatedRadius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = if (m3eEnabled) {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(stiffness = Spring.StiffnessMedium)
        },
        label = "cornerRadius"
    )

    // Weight Animation using the managed isWeightExpanded state
    val targetWeight = if (isWeightExpanded && enabled) 1.2f else 1f
    val uiAmp = LocalUIAmplitude.current
    val animatedWeight by animateFloatAsState(
        targetValue = targetWeight * uiAmp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "weight"
    )

    Box(
        modifier = modifier
            .weight(animatedWeight)
            .height(64.dp)
            .clip(RoundedCornerShape(animatedRadius))
            .combinedClickable(
                onClick = if (enabled) onClick else ({}),
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null // REMOVED GRAY BOX
            )
    ) {
        Box {
            // Background Layer
            if (isGlass) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.graphicsLayer {
                                    renderEffect = RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            } else Modifier
                        )
                        .background(backgroundColor.copy(alpha = if (isEffectivelySelected) 0.45f else 0.2f))
                        .border(
                            if (isEffectivelySelected) 2.dp else 0.5.dp, 
                            if (isEffectivelySelected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.25f), 
                            RoundedCornerShape(animatedRadius)
                        )
                        .drawWithContent {
                            drawContent()
                            if (isEffectivelySelected) {
                                // Add a subtle inner glow for selected glass tile
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                                        center = center,
                                        radius = size.maxDimension / 2
                                    ),
                                    blendMode = BlendMode.Overlay
                                )
                            }
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(backgroundColor, RoundedCornerShape(animatedRadius))
                )
            }

            // Content Layer
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val bananaMode = LocalBananaMode.current
                if (bananaMode && isSelected) {
                    Text("🍌", fontSize = 20.sp)
                } else {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = if (isSelected && enabled) FontWeight.Bold else FontWeight.Medium,
                    maxLines = maxLines
                )
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .padding(bottom = 8.dp)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-1).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GlyphSyncronatorBackground(modifier: Modifier = Modifier) {
    val isGlass = LocalIsGlassTheme.current
    val uiAmp = LocalUIAmplitude.current
    val isDark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background

    Box(modifier = modifier
        .fillMaxSize()
        .background(background)) {

        if (LocalBananaMode.current) {
            val infiniteTransition = rememberInfiniteTransition(label = "banana_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            // Static bananas in corners + pulsing center banana
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("🍌", fontSize = 24.sp, modifier = Modifier.align(Alignment.TopStart).graphicsLayer { rotationZ = -45f })
                Text("🍌", fontSize = 24.sp, modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { rotationZ = 45f })
                Text("🍌", fontSize = 24.sp, modifier = Modifier.align(Alignment.BottomStart).graphicsLayer { rotationZ = -135f })
                
                Text("🍌", fontSize = 60.sp, modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.15f // Very subtle
                    }
                )
            }
        }
        
        if (isGlass) {
            val infiniteTransition = rememberInfiniteTransition(label = "glass_bg")
            
    // Speed significantly increased for a highly dynamic liquid look
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), 
        label = "p1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), 
        label = "p2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), repeatMode = RepeatMode.Reverse), 
        label = "p3"
    )

    // Extremely heavy smoothing for the background reaction
    val smoothedAmp by animateFloatAsState(
        targetValue = uiAmp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "smoothedBackgroundAmp"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseAmp = (1.0f + (smoothedAmp - 1.0f) * 1.1f).coerceAtMost(1.25f)
        
        val t1 = phase1 * Math.PI.toFloat() * 2
        val t2 = phase2 * Math.PI.toFloat() * 2
        val t3 = phase3 * Math.PI.toFloat() * 2

        val orbAlphaMultiplier = if (isDark) 1.0f else 0.35f

        // Cyan Orb
        val x1 = size.width * (0.35f + 0.25f * kotlin.math.sin(t1.toDouble()).toFloat())
        val y1 = size.height * (0.3f + 0.2f * kotlin.math.cos(t2.toDouble() * 0.5).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00FBFF).copy(alpha = 0.5f * baseAmp * orbAlphaMultiplier),
                    Color(0xFF00FBFF).copy(alpha = 0.15f * baseAmp * orbAlphaMultiplier),
                    Color.Transparent
                ),
                center = Offset(x1, y1),
                radius = size.width * 1.1f * baseAmp
            ),
            radius = size.width * 1.1f * baseAmp,
            center = Offset(x1, y1)
        )

        // Magenta Orb
        val x2 = size.width * (0.65f + 0.2f * kotlin.math.cos(t2.toDouble()).toFloat())
        val y2 = size.height * (0.7f + 0.2f * kotlin.math.sin(t3.toDouble() * 0.5).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF00C8).copy(alpha = 0.45f * baseAmp * orbAlphaMultiplier),
                    Color(0xFFFF00C8).copy(alpha = 0.12f * baseAmp * orbAlphaMultiplier),
                    Color.Transparent
                ),
                center = Offset(x2, y2),
                radius = size.width * 1.0f * baseAmp
            ),
            radius = size.width * 1.0f * baseAmp,
            center = Offset(x2, y2)
        )
        
        // Purple Orb
        val x3 = size.width * (0.25f + 0.3f * kotlin.math.sin(t3.toDouble()).toFloat())
        val y3 = size.height * (0.8f + 0.15f * kotlin.math.cos(t1.toDouble() * 0.5).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6200EE).copy(alpha = 0.4f * baseAmp * orbAlphaMultiplier),
                    Color(0xFF6200EE).copy(alpha = 0.1f * baseAmp * orbAlphaMultiplier),
                    Color.Transparent
                ),
                center = Offset(x3, y3),
                radius = size.width * 0.9f * baseAmp
            ),
            radius = size.width * 0.9f * baseAmp,
            center = Offset(x3, y3)
        )

        // Yellow Orb
        val x4 = size.width * (0.75f + 0.15f * kotlin.math.sin(t1.toDouble() * 0.7).toFloat())
        val y4 = size.height * (0.25f + 0.25f * kotlin.math.cos(t2.toDouble() * 0.4).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD600).copy(alpha = 0.35f * baseAmp * orbAlphaMultiplier),
                    Color(0xFFFFD600).copy(alpha = 0.08f * baseAmp * orbAlphaMultiplier),
                    Color.Transparent
                ),
                center = Offset(x4, y4),
                radius = size.width * 0.8f * baseAmp
            ),
            radius = size.width * 0.8f * baseAmp,
            center = Offset(x4, y4)
        )
    }
        }
    }
}

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = LocalIsGlassTheme.current

    Card(
        modifier = modifier
            .padding(vertical = 0.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null
    ) {
        Box {
            // Background Layer
            if (isGlass) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.graphicsLayer {
                                    renderEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            } else Modifier
                        )
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), shape)
                        .drawWithContent {
                            drawContent()
                            // Inner highlight at top
                            drawPath(
                                path = androidx.compose.ui.graphics.Path().apply {
                                    val sizePx = size
                                    val r = 32.dp.toPx()
                                    moveTo(0f, r)
                                    quadraticTo(0f, 0f, r, 0f)
                                    lineTo(sizePx.width - r, 0f)
                                    quadraticTo(sizePx.width, 0f, sizePx.width, r)
                                },
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.0f)),
                                ),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                            )
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(containerColor, shape)
                        .then(if (border != null) Modifier.border(border, shape) else Modifier)
                )
            }

            // Content Layer
            Column(
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(7.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        if (trailingContent != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent()
            }
        }
    }
    Spacer(modifier = Modifier.height(LocalAppSpacing.current.between))
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val bananaMode = LocalBananaMode.current
    Text(
        text = if (bananaMode) "🍌 $text 🍌" else text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    lineHeight: TextUnit = 24.sp,
) {
    Text(
        text  = text,
        // Hoist TextStyle out of every recomposition; only reallocated when
        // size or lineHeight actually changes.
        style = remember(size, lineHeight) {
            TextStyle(
                fontSize   = size,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Normal,
            )
        },
        color    = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
fun StartStopButton(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()
    val haptics           = LocalHapticFeedback.current
    val uiAmp             = LocalUIAmplitude.current
    val isGlass           = LocalIsGlassTheme.current
    val bananaMode        = LocalBananaMode.current

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    val buttonHeight = (60 + (uiAmp - 1) * 50).dp
    val buttonWidthMin = (130 + (uiAmp - 1) * 50).dp
    val cornerRadius = (18 + (uiAmp - 1) * 50).dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onClick()
            },
            interactionSource = interactionSource,
            shape = RoundedCornerShape(cornerRadius),
            color = if (isGlass) Color.Transparent else if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (isGlass) Color.White else if (running) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .height(buttonHeight)
                .widthIn(min = buttonWidthMin)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background Layer
                if (isGlass) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.graphicsLayer {
                                        renderEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                                    }
                                } else Modifier
                            )
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                )
                            )
                            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(cornerRadius))
                    )
                }

                // Content Layer
                Row(
                    modifier             = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState  = running,
                        transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
                        label        = "iconTransition"
                    ) { isRunning ->
                        if (bananaMode) {
                            Text("🍌", fontSize = 24.sp)
                        } else {
                            Icon(
                                imageVector     = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier        = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text  = stringResource(if (running) R.string.stop_visualizer else R.string.start_visualizer).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun NativeBottomBar(
    selectedTab: Tab,
    visibleTabs: List<Tab>,
    onTabSelected: (Tab) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val isGlass = LocalIsGlassTheme.current
    val bananaMode = LocalBananaMode.current
    val uiAmp = LocalUIAmplitude.current

    if (isGlass) {
        // ULTIMATE REDO: Modern Floating Glass Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
        ) {
            // Layer 1: The Glass Base (Blur + Tint)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                35f, 35f, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    },
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.1f))
                )),
                shadowElevation = 0.dp
            ) {}

            // Layer 2: Interactive Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                visibleTabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.22f else 1.0f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                        label = "nav_scale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onTabSelected(tab)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val iconScale = scale + (if (isSelected) (uiAmp - 1f) * 0.45f else 0f)
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f)
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                // Active tab background pulse/glow
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(contentColor.copy(alpha = 0.3f), Color.Transparent)
                                        ),
                                        radius = size.width * 0.8f
                                    )
                                }
                            }
                            
                            val iconModifier = Modifier.size(26.dp)
                            if (bananaMode && isSelected) {
                                Text("🍌", fontSize = 20.sp)
                            } else {
                                when (tab) {
                                    Tab.Audio -> Icon(FontAwesomeIcons.Solid.Music, null, iconModifier, tint = contentColor)
                                    Tab.Glyphs -> Icon(painterResource(R.drawable.ic_nav_glyphs), null, iconModifier, tint = contentColor)
                                    Tab.Visuals -> Icon(FontAwesomeIcons.Solid.LayerGroup, null, iconModifier, tint = contentColor)
                                    Tab.Haptics -> Icon(FontAwesomeIcons.Solid.MobileAlt, null, iconModifier, tint = contentColor)
                                    Tab.Flashlight -> Icon(FontAwesomeIcons.Solid.Bolt, null, iconModifier, tint = contentColor)
                                    Tab.Settings -> Icon(FontAwesomeIcons.Solid.Cog, null, iconModifier, tint = contentColor)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(2.dp))
                        
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.2.sp
                            ),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // Standard Redo
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            visibleTabs.forEach { tab ->
                val isSelected = tab == selectedTab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTabSelected(tab)
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    icon = {
                        val iconModifier = Modifier.size(24.dp)
                        if (bananaMode && isSelected) {
                            Text("🍌", fontSize = 18.sp)
                        } else {
                            when (tab) {
                                Tab.Audio -> Icon(FontAwesomeIcons.Solid.Music, null, iconModifier)
                                Tab.Glyphs -> Icon(painterResource(R.drawable.ic_nav_glyphs), null, iconModifier)
                                Tab.Visuals -> Icon(FontAwesomeIcons.Solid.LayerGroup, null, iconModifier)
                                Tab.Haptics -> Icon(FontAwesomeIcons.Solid.MobileAlt, null, iconModifier)
                                Tab.Flashlight -> Icon(FontAwesomeIcons.Solid.Bolt, null, iconModifier)
                                Tab.Settings -> Icon(FontAwesomeIcons.Solid.Cog, null, iconModifier)
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveSplitButton(
    items: List<T>,
    selectedItem: T,
    onItemSelection: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val uiAmp = LocalUIAmplitude.current

    // 1. Resolve Composable labels into plain strings safely in the Composable pipeline
    val resolvedLabels = items.associateWith { labelProvider(it) }

    // 2. Chunk items into rows using the resolved plain string map
    val chunkedRows = remember(items, resolvedLabels) {
        if (items.size <= 3) {
            listOf(items)
        } else {
            val rows = mutableListOf<MutableList<T>>()
            var currentRow = mutableListOf<T>()
            var currentCharacterCount = 0

            // Threshold budget limit per row
            val maxCharactersPerRow = 26

            items.forEach { item ->
                val labelText = resolvedLabels[item].orEmpty()
                val textLength = labelText.length

                if (currentCharacterCount + textLength > maxCharactersPerRow && currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                    currentCharacterCount = 0
                }
                currentRow.add(item)
                currentCharacterCount += textLength
            }
            if (currentRow.isNotEmpty()) {
                rows.add(currentRow)
            }
            rows
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chunkedRows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEachIndexed { itemIndex, item ->
                    val isSelected = item == selectedItem
                    var isPressed by remember { mutableStateOf(false) }

                    val bouncySpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    val dpBouncySpec = spring<androidx.compose.ui.unit.Dp>(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )

                    val baseWeight by animateFloatAsState(
                        targetValue = if (isPressed) 0.89f 
                                      else if (isSelected) 1.2f 
                                      else 1.0f,
                        animationSpec = bouncySpec,
                        label = "ExpressiveWeightAnimationBase"
                    )
                    
                    val animatedWeight = if (isSelected) {
                        baseWeight * uiAmp
                    } else {
                        baseWeight
                    }

                    // Color transitions
                    val targetContainerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }

                    val targetContentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val containerColor by animateColorAsState(
                        targetValue = targetContainerColor,
                        animationSpec = tween(durationMillis = 250),
                        label = "ContainerColorAnimation"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = targetContentColor,
                        animationSpec = tween(durationMillis = 250),
                        label = "ContentColorAnimation"
                    )

                    // Edge rounding physics logic
                    val fullyRounded = 20.dp
                    val innerRounded = 8.dp // Unified - truly sharp inner edges for the box look

                    val isFirstRow = rowIndex == 0
                    val isLastRow = rowIndex == chunkedRows.size - 1
                    val isFirstInRow = itemIndex == 0
                    val isLastInRow = itemIndex == rowItems.size - 1

                    val targetTopStart = if (isSelected || (isFirstRow && isFirstInRow)) fullyRounded else innerRounded
                    val targetTopEnd = if (isSelected || (isFirstRow && isLastInRow)) fullyRounded else innerRounded
                    val targetBottomStart = if (isSelected || (isLastRow && isFirstInRow)) fullyRounded else innerRounded
                    val targetBottomEnd = if (isSelected || (isLastRow && isLastInRow)) fullyRounded else innerRounded

                    val topStart by animateDpAsState(targetValue = targetTopStart, animationSpec = dpBouncySpec, label = "TopStart")
                    val bottomStart by animateDpAsState(targetValue = targetBottomStart, animationSpec = dpBouncySpec, label = "BottomStart")
                    val topEnd by animateDpAsState(targetValue = targetTopEnd, animationSpec = dpBouncySpec, label = "TopEnd")
                    val bottomEnd by animateDpAsState(targetValue = targetBottomEnd, animationSpec = dpBouncySpec, label = "BottomEnd")

                    val dynamicButtonShape = RoundedCornerShape(
                        topStart = topStart.coerceAtLeast(0.dp),
                        bottomStart = bottomStart.coerceAtLeast(0.dp),
                        topEnd = topEnd.coerceAtLeast(0.dp),
                        bottomEnd = bottomEnd.coerceAtLeast(0.dp)
                    )

                    Surface(
                        color = containerColor,
                        contentColor = contentColor,
                        shape = dynamicButtonShape,
                        modifier = Modifier
                            .weight(animatedWeight)
                            .pointerInput(item, isSelected) {
                                detectTapGestures(
                                    onPress = {
                                        val startTime = System.currentTimeMillis()
                                        isPressed = true
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)

                                        try {
                                            awaitRelease()
                                        } finally {
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                            val elapsedTime = System.currentTimeMillis() - startTime
                                            val remainingTime = 100L - elapsedTime

                                            scope.launch {
                                                if (remainingTime > 0) {
                                                    delay(remainingTime.milliseconds)
                                                }
                                                isPressed = false
                                                if (!isSelected) {
                                                    onItemSelection(item)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bananaMode = LocalBananaMode.current
                            Text(
                                text = if (bananaMode && isSelected) "🍌 ${resolvedLabels[item]}" else resolvedLabels[item] ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val uiAmp = LocalUIAmplitude.current

    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isActive = isPressed || isDragged

    val wasActive = remember { mutableStateOf(false) }

    // Trigger haptic on Press/Release (skip initial state)
    LaunchedEffect(isActive) {
        if (!wasActive.value && isActive) {
            // Trigger on press (transition from false to true)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (wasActive.value && !isActive) {
            // Trigger on release (transition from true to false)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        wasActive.value = isActive
    }

    // The "Expressive" factor (1.0 to 1.8)
    val animationFactor by animateFloatAsState(
        targetValue = if (isActive && LocalM3EEnabled.current) 2.1f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressive_bounce"
    )

    val view = LocalView.current
    Slider(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue)
        },
        valueRange = valueRange,
        steps = steps,
        interactionSource = interactionSource,
        modifier = modifier
            .height(56.dp)
            .pointerInput(isActive) {
                if (isActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            },
        thumb = {
            val bananaMode = LocalBananaMode.current
            if (bananaMode) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    Text("🍌", fontSize = 24.sp)
                }
            } else {
                // THUMB: Gets THINNER as animationFactor increases
                // Width: 4dp -> 2dp | Height: 44dp -> 48dp
                val thumbWidth = 4.dp / animationFactor

                Box(
                    modifier = Modifier
                        .size(width = thumbWidth, height = 44.dp * (animationFactor * 0.8f).coerceAtLeast(1f))
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp) // Keeps same corner radius
                        )
                )
            }
        },
        track = { sliderState ->
            // TRACK: Gets THICKER
            // Radius: We want it to look like a pill when thin, but less rounded when thick
            val trackHeight = 16.dp * animationFactor * uiAmp

            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier
                    .height(trackHeight),
                thumbTrackGapSize = 4.dp,
                trackInsideCornerSize = 2.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

@Composable
fun ExpressiveRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val uiAmp = LocalUIAmplitude.current

    val startActive by startInteractionSource.collectIsPressedAsState()
    val startDragged by startInteractionSource.collectIsDraggedAsState()
    val endActive by endInteractionSource.collectIsPressedAsState()
    val endDragged by endInteractionSource.collectIsDraggedAsState()

    val isAnyActive = startActive || startDragged || endActive || endDragged
    val wasActive = remember { mutableStateOf(false) }

    // Trigger haptic on Press/Release (skip initial state)
    LaunchedEffect(isAnyActive) {
        if (!wasActive.value && isAnyActive) {
            // Trigger on press (transition from false to true)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (wasActive.value && !isAnyActive) {
            // Trigger on release (transition from true to false)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        wasActive.value = isAnyActive
    }

    // Animation and Haptic logic remains the same...
    val animationFactor by animateFloatAsState(
        targetValue = if (isAnyActive && LocalM3EEnabled.current) 2.1f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "track_bloom"
    )

    val startThumbFactor by animateFloatAsState(if ((startActive || startDragged) && LocalM3EEnabled.current) 2.1f else 1.0f)
    val endThumbFactor by animateFloatAsState(if ((endActive || endDragged) && LocalM3EEnabled.current) 2.1f else 1.0f)

    val view = LocalView.current
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        startInteractionSource = startInteractionSource,
        endInteractionSource = endInteractionSource,
        modifier = modifier
            .height(64.dp)
            .pointerInput(isAnyActive) {
                if (isAnyActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            },
        startThumb = { ExpressiveThumb(factor = startThumbFactor) },
        endThumb = { ExpressiveThumb(factor = endThumbFactor) },
        track = { rangeSliderState ->
            val trackHeight = 12.dp * animationFactor * uiAmp
            SliderDefaults.Track(
                rangeSliderState = rangeSliderState,
                modifier = Modifier.height(trackHeight),
                thumbTrackGapSize = 4.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

@Composable
private fun ExpressiveThumb(factor: Float) {
    val bananaMode = LocalBananaMode.current
    if (bananaMode) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
            Text("🍌", fontSize = 24.sp)
        }
    } else {
        // The thumb gets thinner and taller when grabbed
        val thumbWidth = 4.dp / factor
        val thumbHeight = 40.dp * (factor * 0.8f).coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .size(width = thumbWidth, height = thumbHeight)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun AnimatedToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle? = null,
    titleColor: Color? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: CardColors? = null,
    contentPadding: Dp = 12.dp,
    disabledTopSpacerFraction: Float = 0.3f,
    disabledTitleScaleFactor: Float = 1.15f,
    disabledSwitchScaleFactor: Float = 1.4f,
    disabledTitleSpacing: Dp = 28.dp,
    animationDurationMs: Int = 500,
) {
    // Tweak these defaults here when tuning the shared motion/scale behavior.
    val motionDurationMs = animationDurationMs
    val offTitleScale = disabledTitleScaleFactor
    val offSwitchScale = disabledSwitchScaleFactor
    val offTitleSpacing = disabledTitleSpacing
    val defaultTitleStyle = MaterialTheme.typography.headlineMedium.let { style ->
        style.copy(
            fontSize = style.fontSize * 0.9f,
            lineHeight = style.lineHeight * 0.9f
        )
    }
    val resolvedTitleStyle = titleStyle ?: defaultTitleStyle

    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            Color.White
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = animationDurationMs),
        label = "card_container_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) {
            Color.White
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = animationDurationMs),
        label = "card_border_color"
    )

    val resolvedTitleColor = titleColor ?: if (checked) Color.Black else MaterialTheme.colorScheme.onBackground
    val resolvedColors = colors ?: CardDefaults.cardColors(containerColor = containerColor)

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(
            durationMillis = motionDurationMs,
            easing = FastOutSlowInEasing
        ),
        label = "toggle_card_progress"
    )
    val titleScale = offTitleScale - ((offTitleScale - 1f) * progress)
    val switchScale = offSwitchScale - ((offSwitchScale - 1f) * progress)
    val titleSpacing = lerp(offTitleSpacing, 0.dp, progress)
    val topSpacer = lerp(screenHeight * disabledTopSpacerFraction, 0.dp, progress)

    Spacer(modifier = Modifier.height(topSpacer))

    Card(
        shape = shape,
        colors = resolvedColors,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedToggleCardLayout(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            titleStyle = resolvedTitleStyle,
            titleColor = resolvedTitleColor,
            progress = progress,
            titleScale = titleScale,
            switchScale = switchScale,
            titleToSwitchSpacing = titleSpacing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        )
    }
}

@Composable
private fun AnimatedToggleCardLayout(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleStyle: TextStyle,
    titleColor: Color,
    progress: Float,
    titleScale: Float,
    switchScale: Float,
    titleToSwitchSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val spacingPx = with(LocalDensity.current) {
        titleToSwitchSpacing.roundToPx()
    }

    Layout(
        modifier = modifier,
        content = {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.Black,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        val switchPlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )

        val width = constraints.maxWidth
        val scaledTextWidth = (textPlaceable.width * titleScale).roundToInt()
        val scaledTextHeight = (textPlaceable.height * titleScale).roundToInt()
        val scaledSwitchWidth = (switchPlaceable.width * switchScale).roundToInt()
        val scaledSwitchHeight = (switchPlaceable.height * switchScale).roundToInt()
        val enabledHeight = maxOf(scaledTextHeight, scaledSwitchHeight)

        val disabledTextX = ((width - scaledTextWidth) / 2f).roundToInt()
        val disabledTextY = 0
        val enabledTextX = 0
        val enabledTextY = ((enabledHeight - scaledTextHeight) / 2f).roundToInt()

        val disabledSwitchX = ((width - scaledSwitchWidth) / 2f).roundToInt()
        val disabledSwitchY = scaledTextHeight + spacingPx
        val enabledSwitchX = width - scaledSwitchWidth
        val enabledSwitchY = ((enabledHeight - scaledSwitchHeight) / 2f).roundToInt()

        val textVisualX = lerpInt(disabledTextX, enabledTextX, progress)
        val textVisualY = lerpInt(disabledTextY, enabledTextY, progress)
        val switchX = lerpInt(disabledSwitchX, enabledSwitchX, progress)
        val switchY = lerpInt(disabledSwitchY, enabledSwitchY, progress)

        val textPlacementX = textVisualX + ((scaledTextWidth - textPlaceable.width) / 2f).roundToInt()
        val textPlacementY = textVisualY + ((scaledTextHeight - textPlaceable.height) / 2f).roundToInt()
        val switchPlacementX = switchX + ((scaledSwitchWidth - switchPlaceable.width) / 2f).roundToInt()
        val switchPlacementY = switchY + ((scaledSwitchHeight - switchPlaceable.height) / 2f).roundToInt()
        val layoutHeight = maxOf(
            textVisualY + scaledTextHeight,
            switchY + scaledSwitchHeight
        ).coerceAtLeast(enabledHeight)

        layout(width, layoutHeight) {
            textPlaceable.placeWithLayer(textPlacementX, textPlacementY) {
                scaleX = titleScale *.95f
                scaleY = titleScale *.95f
                transformOrigin = TransformOrigin.Center
            }
            switchPlaceable.placeWithLayer(switchPlacementX, switchPlacementY) {
                scaleX = switchScale
                scaleY = switchScale
                transformOrigin = TransformOrigin.Center
            }
        }
    }
}

private fun lerpInt(start: Int, end: Int, progress: Float): Int {
    return (start + (end - start) * progress).roundToInt()
}
