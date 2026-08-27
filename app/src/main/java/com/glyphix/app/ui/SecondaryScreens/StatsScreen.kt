package com.glyphix.app.ui.SecondaryScreens

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.ui.*

/**
 * Stats Overview Screen replicating `Assets/stats.png` while adapting to
 * all original themes (Nothing, Glass, Music, Material You, Monster, etc.).
 */
@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val isGlass = LocalIsGlassTheme.current
    val totalTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val idleTime by viewModel.totalIdleTime.collectAsStateWithLifecycle()
    val activeTime by viewModel.totalActiveTime.collectAsStateWithLifecycle()
    val glyphTime by viewModel.totalGlyphTime.collectAsStateWithLifecycle()
    val hapticTime by viewModel.totalHapticTime.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    // Formatted real time calculations
    val totalHours = totalTime / (1000 * 60 * 60)
    val totalMins = (totalTime / (1000 * 60)) % 60
    val totalDisplay = "${totalHours}h ${totalMins}m"

    val totalActiveAndIdle = (activeTime + idleTime).coerceAtLeast(1L)
    val activePercent = if (activeTime > 0) (activeTime * 100 / totalActiveAndIdle).toInt() else 0
    val idlePercent = if (idleTime > 0) 100 - activePercent else 0

    val glyphHours = glyphTime / (1000 * 60 * 60)
    val glyphMins = (glyphTime / (1000 * 60)) % 60
    val glyphDisplay = "${glyphHours}h ${glyphMins}m"

    val hapticHours = hapticTime / (1000 * 60 * 60)
    val hapticMins = (hapticTime / (1000 * 60)) % 60
    val hapticDisplay = "${hapticHours}h ${hapticMins}m"

    val idleHours = idleTime / (1000 * 60 * 60)
    val idleMins = (idleTime / (1000 * 60)) % 60
    val idleDisplay = "${idleHours}h ${idleMins}m"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isGlass) {
            GlyphixBackground()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Floating Top Bar
            FloatingTopBar(
                title = "Stats",
                onMenuClick = { onDismiss() },
                onProfileClick = { onOpenProfile() },
                avatarUrl = userProfile?.profilePictureUrl,
                isProfileActive = false
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Hero Card: Total Visualization (Theme dynamic container)
                val heroBg = if (isGlass) {
                    Color.White.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                val heroTextColor = if (isGlass) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.graphicsLayer {
                                    renderEffect = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            } else {
                                Modifier.shadow(elevation = 2.dp, shape = RoundedCornerShape(26.dp))
                            }
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = heroBg,
                    border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "TOTAL VISUALIZATION",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 13.sp
                            ),
                            color = heroTextColor.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = totalDisplay,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 46.sp
                            ),
                            color = heroTextColor
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Lifetime",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = heroTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                // 2. Two Side-by-Side Metric Cards (Active Music % & Idle Pulse %)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Active Music Card
                    MockupCard(modifier = Modifier.weight(1f)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "ACTIVE MUSIC",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = mockupSubtextColor()
                            )
                            Text(
                                text = "${activePercent}%",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = mockupTextColor()
                            )
                        }
                    }

                    // Idle Pulse Card
                    MockupCard(modifier = Modifier.weight(1f)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "IDLE PULSE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = mockupSubtextColor()
                            )
                            Text(
                                text = "${idlePercent}%",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = mockupTextColor()
                            )
                        }
                    }
                }

                // 3. Breakdown Card
                MockupCard {
                    Text(
                        text = "Breakdown",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = mockupTextColor()
                    )
                    Spacer(Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BreakdownRow(label = "Glyph interface", value = glyphDisplay)
                        BreakdownRow(label = "Haptic feedback", value = hapticDisplay)
                        BreakdownRow(label = "Idle pulse", value = idleDisplay)
                    }
                }

                Spacer(Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = mockupSubtextColor()
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = mockupTextColor()
        )
    }
}
