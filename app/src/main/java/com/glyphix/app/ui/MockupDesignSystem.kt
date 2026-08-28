package com.glyphix.app.ui

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.glyphix.app.R
import com.glyphix.app.service.AudioCaptureService

/**
 * Dynamic theme helper functions that respect the active theme
 * (Nothing, Glass, Music, Material You, Monster Classic/Ultra, etc.).
 */
@Composable
fun mockupSurfaceColor(): Color {
    val isGlass = LocalIsGlassTheme.current
    return if (isGlass) {
        Color.White.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
}

@Composable
fun mockupTextColor(): Color {
    val isGlass = LocalIsGlassTheme.current
    return if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
}

@Composable
fun mockupSubtextColor(): Color {
    val isGlass = LocalIsGlassTheme.current
    return if (isGlass) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun mockupAccentColor(): Color {
    return MaterialTheme.colorScheme.primary
}

/**
 * Floating Pill Top App Bar replicating `page 3.png`, `profile.png`, `settings.png`, `stats.png`.
 * Floats directly over page content with no layout reservation.
 */
@Composable
fun FloatingTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    avatarUrl: String? = null,
    isProfileActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isGlass = LocalIsGlassTheme.current
    val haptics = LocalHapticFeedback.current
    val contentColor = mockupTextColor()
    val accentColor = mockupAccentColor()

    val surfaceColor = if (isGlass) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }

    val borderStroke = if (isGlass) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.40f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(56.dp)
            .shadow(
                elevation = if (isGlass) 0.dp else 4.dp,
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        color = surfaceColor,
        contentColor = contentColor,
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Menu Button
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onMenuClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center: Screen Title
            val titleFontFamily = LocalAppFontFamily.current
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = titleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = if (titleFontFamily == NDot55FontFamily) 1.2.sp else 0.4.sp
                ),
                color = contentColor,
                textAlign = TextAlign.Center
            )

            // Right: Profile Avatar Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (isProfileActive) {
                            Modifier.background(
                                color = accentColor.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                        } else Modifier
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onProfileClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isProfileActive) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle,
                        contentDescription = "Profile",
                        tint = if (isProfileActive) accentColor else contentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating Dual Bottom Bar: 4-Item Nav Pill + Squircle FAB Button (`page 3.png`, `settings.png`).
 * Floats directly over page content with zero layout reservation.
 */
@Composable
fun FloatingBottomBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    isRunning: Boolean,
    onToggleVisualizer: () -> Unit,
    isFabMenuExpanded: Boolean,
    onToggleFabMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = LocalIsGlassTheme.current
    val bananaMode = LocalBananaMode.current
    val penisMode = LocalPenisMode.current
    val haptics = LocalHapticFeedback.current
    val accentColor = mockupAccentColor()
    val contentColor = mockupTextColor()

    val navItems = listOf(
        TabItem(Tab.Audio, Icons.Default.MusicNote, "Audio"),
        TabItem(Tab.Stats, Icons.Default.BarChart, "Stats"),
        TabItem(Tab.Visuals, Icons.Default.Image, ""),
        TabItem(Tab.Settings, Icons.Default.Settings, "Settings")
    )

    val navBgColor = if (isGlass) {
        Color.White.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }

    val borderStroke = if (isGlass) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.40f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Main Navigation Pill
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .shadow(
                    elevation = if (isGlass) 0.dp else 4.dp,
                    shape = RoundedCornerShape(32.dp)
                ),
            shape = RoundedCornerShape(32.dp),
            color = navBgColor,
            border = borderStroke
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                navItems.forEach { item ->
                    val isSelected = selectedTab == item.tab
                    val activeBg = accentColor.copy(alpha = if (isGlass) 0.35f else 0.18f)
                    val activeIconTint = accentColor
                    val inactiveIconTint = contentColor.copy(alpha = 0.65f)

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected && item.label.isNotBlank()) Modifier.background(activeBg, CircleShape) else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onTabSelected(item.tab)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.label.isNotBlank()) {
                            val iconModifier = Modifier.size(24.dp)
                            if (bananaMode && isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.banana),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = iconModifier
                                )
                            } else if (penisMode && isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.penis),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = iconModifier
                                )
                            } else {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) activeIconTint else inactiveIconTint,
                                    modifier = iconModifier
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animated Shape & Rotation for Play -> X Morph
        val fabCornerRadius by animateDpAsState(
            targetValue = if (isFabMenuExpanded) 32.dp else 22.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "fabCornerRadius"
        )
        val fabRotation by animateFloatAsState(
            targetValue = if (isFabMenuExpanded) 90f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "fabRotation"
        )

        // Squircle Play/Stop FAB Button that morphs to Close 'X' when expanded
        val fabBg = when {
            isRunning -> MaterialTheme.colorScheme.error
            isFabMenuExpanded -> accentColor
            isGlass -> Color.White.copy(alpha = 0.30f)
            else -> accentColor
        }
        val fabIconTint = when {
            isRunning -> MaterialTheme.colorScheme.onError
            isFabMenuExpanded -> if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary
            isGlass -> Color.White
            else -> MaterialTheme.colorScheme.onPrimary
        }

        Surface(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = if (isGlass) 0.dp else 4.dp,
                    shape = RoundedCornerShape(fabCornerRadius)
                ),
            shape = RoundedCornerShape(fabCornerRadius),
            color = fabBg,
            border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.40f)) else null,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                if (isRunning) {
                    onToggleVisualizer()
                } else {
                    onToggleFabMenu()
                }
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isRunning -> Icons.Default.Stop
                        isFabMenuExpanded -> Icons.Default.Close
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        isRunning -> "Stop"
                        isFabMenuExpanded -> "Close Menu"
                        else -> "Start"
                    },
                    tint = fabIconTint,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer { rotationZ = fabRotation }
                )
            }
        }
    }
}

private data class TabItem(val tab: Tab, val icon: ImageVector, val label: String)

/**
 * Speed-Dial Capture Source Menu replicating `Assets/FAB menu.png`.
 * Sweeps in from the right edge with spring dynamics when the Play button is pressed.
 */
@Composable
fun SpeedDialFabMenu(
    isExpanded: Boolean,
    currentSource: AudioCaptureService.CaptureSource,
    onSelectSource: (AudioCaptureService.CaptureSource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isExpanded) return

    val isGlass = LocalIsGlassTheme.current
    val accentColor = mockupAccentColor()
    val pillBg = if (isGlass) {
        Color.White.copy(alpha = 0.20f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    }
    val pillTextColor = if (isGlass) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val haptics = LocalHapticFeedback.current

    val sources = listOf(
        Triple("Smart Capture", Icons.Outlined.StarOutline, AudioCaptureService.CaptureSource.INTERNAL),
        Triple("Screen Capture", Icons.Outlined.Image, AudioCaptureService.CaptureSource.INTERNAL),
        Triple("Android Visualizer", Icons.Outlined.MusicNote, AudioCaptureService.CaptureSource.VIZUALIZER),
        Triple("Microphone", Icons.Outlined.Mic, AudioCaptureService.CaptureSource.MIC)
    )

    val density = LocalDensity.current
    val xOffsetPx = with(density) { (-16).dp.roundToPx() }
    val yOffsetPx = with(density) { (-105).dp.roundToPx() }

    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(xOffsetPx, yOffsetPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth + 400 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth + 400 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = modifier.padding(end = 16.dp, bottom = 8.dp)
            ) {
                sources.forEach { (name, icon, source) ->
                    val itemBg = pillBg
                    val itemTextColor = pillTextColor

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = itemBg,
                        border = if (isGlass) {
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
                        } else {
                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        },
                        shadowElevation = if (isGlass) 0.dp else 4.dp,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelectSource(source)
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = name,
                                tint = itemTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = itemTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hamburger Quick Configuration Dropdown Menu replicating `Assets/Hamburger Menu.png`.
 * Perfectly aligned directly beneath the top bar hamburger button with 0 gap and spring scale animation.
 */
@Composable
fun HamburgerDropdownMenu(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSelectGlyphs: () -> Unit,
    onSelectHaptics: () -> Unit,
    onSelectOverlays: () -> Unit,
    onSelectTorch: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val isGlass = LocalIsGlassTheme.current
    val menuBg = if (isGlass) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
    val textPrimary = mockupTextColor()
    val textSecondary = mockupSubtextColor()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val xOffsetPx = with(density) { 16.dp.roundToPx() }
    val yOffsetPx = with(density) { 68.dp.roundToPx() }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(xOffsetPx, yOffsetPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = scaleIn(
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = 0.8f
            ) + fadeIn(animationSpec = tween(150)),
            exit = scaleOut(
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                targetScale = 0.8f
            ) + fadeOut(animationSpec = tween(100))
        ) {
            Surface(
                modifier = modifier
                    .width(230.dp)
                    .shadow(elevation = if (isGlass) 0.dp else 8.dp, shape = RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = menuBg,
                border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.40f)) else BorderStroke(0.5.dp, textPrimary.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HamburgerMenuItem(
                        title = "Glyphs Controls",
                        subtitle = "Zones, effects & calibration",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelectGlyphs()
                            onDismiss()
                        }
                    )
                    HorizontalDivider(
                        color = textPrimary.copy(alpha = 0.06f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    HamburgerMenuItem(
                        title = "Haptics",
                        subtitle = "Configure Haptic Viz",
                        icon = Icons.Outlined.GraphicEq,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelectHaptics()
                            onDismiss()
                        }
                    )
                    HorizontalDivider(
                        color = textPrimary.copy(alpha = 0.06f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    HamburgerMenuItem(
                        title = "Overlays",
                        subtitle = "Configure Overlays",
                        icon = Icons.Outlined.Layers,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelectOverlays()
                            onDismiss()
                        }
                    )
                    HorizontalDivider(
                        color = textPrimary.copy(alpha = 0.06f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    HamburgerMenuItem(
                        title = "Torch",
                        subtitle = "Configure Torch",
                        icon = Icons.Outlined.FlashlightOn,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelectTorch()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HamburgerMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val textPrimary = mockupTextColor()
    val textSecondary = mockupSubtextColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = mockupAccentColor(),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = textSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Standard Mockup Card Container adapting to Glass & dynamic themes.
 */
@Composable
fun MockupCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    containerColor: Color = mockupSurfaceColor(),
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = LocalIsGlassTheme.current

    if (isGlass) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                .clip(shape)
        ) {
            // Original Glass Blur & Gradient Layer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                        }
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            // Top specular edge highlight & Border
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        // Top edge highlight
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                val r = 24.dp.toPx()
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
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    .border(
                        BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)),
                        shape = shape
                    )
                    .padding(20.dp),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                .shadow(elevation = 1.dp, shape = shape),
            shape = shape,
            color = containerColor,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }
}

/**
 * Mockup Pill Button with theme colors and optional expandable chevron.
 */
@Composable
fun MockupPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    showChevron: Boolean = false
) {
    val isGlass = LocalIsGlassTheme.current
    val accentColor = mockupAccentColor()
    val pillBg = if (isExpanded) {
        if (isGlass) Color.White.copy(alpha = 0.35f) else accentColor
    } else {
        if (isGlass) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    }
    val pillTextColor = if (isExpanded) {
        if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary
    } else {
        if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    }
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pillBg,
        border = if (isGlass) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)) else null,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = pillTextColor
            )
            if (showChevron) {
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "chevron_rot"
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = pillTextColor,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

/**
 * Mockup Pill Toggle with theme colors.
 */
@Composable
fun MockupPillToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = LocalIsGlassTheme.current
    val accentColor = mockupAccentColor()
    val pillBg = if (checked) {
        if (isGlass) Color.White.copy(alpha = 0.35f) else accentColor
    } else {
        if (isGlass) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    }
    val pillTextColor = if (checked) {
        if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary
    } else {
        mockupSubtextColor()
    }
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pillBg,
        border = if (isGlass) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)) else null,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onCheckedChange(!checked)
        },
        modifier = modifier
    ) {
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = pillTextColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}
