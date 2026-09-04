package com.glyphix.app.ui

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.geometry.Rect
import coil3.compose.AsyncImage
import com.glyphix.app.R
import com.glyphix.app.service.AudioCaptureService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    onTitleLongClick: (() -> Unit)? = null,
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
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onTitleLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTitleLongClick()
                                }
                            )
                        } else Modifier
                    )
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
        TabItem(Tab.Glyphs, Icons.AutoMirrored.Filled.TrendingUp, "Glyphs"),
        TabItem(Tab.Visuals, Icons.Default.Info, "Visuals"),
        TabItem(Tab.Settings, Icons.Default.Settings, "Settings")
    )

    val navBgColor = if (isGlass) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color(0xFF0D0D0D).copy(alpha = 0.95f)
    }

    val borderStroke = if (isGlass) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    }

    // Animation orchestration for the "Collapse -> Bounce -> Expand" sequence
    val animActiveIndex = remember { Animatable(navItems.indexOfFirst { it.tab == selectedTab }.toFloat()) }
    val animExpansion = remember { Animatable(1f) }
    
    var targetIndex by remember { mutableIntStateOf(navItems.indexOfFirst { it.tab == selectedTab }) }
    var previousTab by remember { mutableStateOf(selectedTab) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != previousTab) {
            val newIndex = navItems.indexOfFirst { it.tab == selectedTab }
            targetIndex = newIndex
            
            // 1. QUICK COLLAPSE
            animExpansion.animateTo(0f, tween(100))
            
            // 2. BOUNCE OVER
            animActiveIndex.animateTo(newIndex.toFloat(), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            
            // 3. EXPAND
            animExpansion.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            
            previousTab = selectedTab
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main Navigation Pill
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .shadow(
                    elevation = if (isGlass) 0.dp else 8.dp,
                    shape = RoundedCornerShape(36.dp)
                ),
            shape = RoundedCornerShape(36.dp),
            color = navBgColor,
            border = borderStroke
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val extraWidthPx = with(LocalDensity.current) { 84.dp.toPx() }
                val expansion = animExpansion.value
                
                // Calculate current slot widths based on expansion
                val currentBaseWidthPx = (totalWidthPx - (extraWidthPx * expansion)) / 4f

                fun getCenterForIndex(idx: Float, isExpanded: Float): Float {
                    val base = (totalWidthPx - (extraWidthPx * isExpanded)) / 4f
                    val slotIndex = idx.roundToInt()

                    // Start position
                    var x = 0f
                    for (j in 0 until slotIndex) {
                        x += base + (if (j == targetIndex) extraWidthPx * isExpanded else 0f)
                    }
                    
                    // Current slot width
                    val currentSlotW = base + (if (slotIndex == targetIndex) extraWidthPx * isExpanded else 0f)
                    
                    // If we are sliding (isExpanded is near 0), idx is the slide position
                    return if (isExpanded < 0.1f) {
                        idx * (totalWidthPx / 4f) + (totalWidthPx / 8f)
                    } else {
                        x + currentSlotW / 2f
                    }
                }

                val pillWidth = with(LocalDensity.current) { (56f + 84f * expansion).dp }
                
                // The Selection Indicator: Bouncy and Precisely Calculated
                Box(
                    modifier = Modifier
                        .offset {
                            val centerX = getCenterForIndex(animActiveIndex.value, expansion)
                            IntOffset((centerX - pillWidth.toPx() / 2f).roundToInt(), 0)
                        }
                        .size(pillWidth, 56.dp)
                        .background(accentColor, RoundedCornerShape(28.dp))
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    navItems.forEachIndexed { i, item ->
                        val isSelected = selectedTab == item.tab
                        val currentSlotWidth = with(LocalDensity.current) { 
                            (currentBaseWidthPx + (if (i == targetIndex) extraWidthPx * expansion else 0f)).toDp() 
                        }

                        Box(
                            modifier = Modifier
                                .width(currentSlotWidth)
                                .fillMaxHeight()
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.wrapContentWidth(unbounded = true)
                            ) {
                                // Icon container
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            color = if (isSelected) Color.White.copy(alpha = expansion.coerceIn(0f, 1f) * 0.9f) else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val iconModifier = Modifier.size(24.dp)
                                    if (bananaMode && isSelected) {
                                        Icon(painterResource(R.drawable.banana), null, tint = Color.Unspecified, modifier = iconModifier)
                                    } else if (penisMode && isSelected) {
                                        Icon(painterResource(R.drawable.penis), null, tint = Color.Unspecified, modifier = iconModifier)
                                    } else {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = if (isSelected) {
                                                if (expansion > 0.5f) Color.Black else contentColor.copy(alpha = 0.7f)
                                            } else {
                                                contentColor.copy(alpha = 0.7f)
                                            },
                                            modifier = iconModifier
                                        )
                                    }
                                }

                                // Text Label: Never clipped, always sharp
                                if (isSelected && expansion > 0.3f) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.label,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = expansion),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val fabCornerRadius by animateDpAsState(
            targetValue = if (isFabMenuExpanded) 24.dp else 28.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "fabCornerRadius"
        )
        val fabRotation by animateFloatAsState(
            targetValue = if (isFabMenuExpanded) 90f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "fabRotation"
        )

        val fabBg = when {
            isRunning -> MaterialTheme.colorScheme.error
            isFabMenuExpanded -> accentColor
            isGlass -> Color.White.copy(alpha = 0.25f)
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
                .size(72.dp)
                .shadow(
                    elevation = if (isGlass) 0.dp else 8.dp,
                    shape = RoundedCornerShape(fabCornerRadius)
                ),
            shape = RoundedCornerShape(fabCornerRadius),
            color = fabBg,
            border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)) else borderStroke,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                if (isRunning) onToggleVisualizer() else onToggleFabMenu()
            }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        isRunning -> Icons.Default.Stop
                        isFabMenuExpanded -> Icons.Default.Close
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = fabIconTint,
                    modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = fabRotation }
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
        Triple("Microphone", Icons.Outlined.Mic, AudioCaptureService.CaptureSource.MIC),
        Triple("Spotify Player", Icons.Default.MusicNote, AudioCaptureService.CaptureSource.SPOTIFY),
        Triple("Desktop Companion (UDP)", Icons.Outlined.Wifi, AudioCaptureService.CaptureSource.NETWORK),
        Triple("Desktop Companion (BT)", Icons.Outlined.Bluetooth, AudioCaptureService.CaptureSource.BLUETOOTH)
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
            StaggeredEntranceColumn(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = modifier.padding(end = 16.dp, bottom = 8.dp)
            ) {
                sources.forEach { (name, icon, source) ->
                    AnimatedItem {
                        val itemBg = pillBg
                        val itemTextColor = pillTextColor

                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = itemBg,
                            border = if (isGlass) {
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
                            } else {
                                BorderStroke(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
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
    onSelectSpotify: () -> Unit = {},
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
                StaggeredEntranceColumn(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AnimatedItem {
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
                    }
                    AnimatedItem {
                        HorizontalDivider(
                            color = textPrimary.copy(alpha = 0.06f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }

                    AnimatedItem {
                        HamburgerMenuItem(
                            title = "Spotify Player",
                            subtitle = "Inbuilt music & search",
                            icon = Icons.Default.MusicNote,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelectSpotify()
                                onDismiss()
                            }
                        )
                    }
                    AnimatedItem {
                        HorizontalDivider(
                            color = textPrimary.copy(alpha = 0.06f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }

                    AnimatedItem {
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
                    }
                    AnimatedItem {
                        HorizontalDivider(
                            color = textPrimary.copy(alpha = 0.06f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }

                    AnimatedItem {
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
                    }
                    AnimatedItem {
                        HorizontalDivider(
                            color = textPrimary.copy(alpha = 0.06f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }

                    AnimatedItem {
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
