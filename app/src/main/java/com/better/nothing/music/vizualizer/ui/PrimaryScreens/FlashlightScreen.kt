package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import com.better.nothing.music.vizualizer.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.model.TorchMode
import com.better.nothing.music.vizualizer.ui.AnimatedToggleCard
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveRangeSlider
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MorphingPolygon
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.invLerpLog
import com.better.nothing.music.vizualizer.ui.lerpLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
    flashlightMode: TorchMode,
    onFlashlightModeChanged: (TorchMode) -> Unit,
    flashlightFreqMin: Float,
    flashlightFreqMax: Float,
    onFlashlightFreqRangeChanged: (Float, Float) -> Unit,
    flashlightThreshold: Float,
    onFlashlightThresholdChanged: (Float) -> Unit,
    flashlightSpeedMs: Float,
    onFlashlightSpeedMsChanged: (Float) -> Unit,
    flashlightBeatSensitivity: Float,
    onFlashlightBeatSensitivityChanged: (Float) -> Unit,
    flashlightIntensityLevels: Int,
    flashlightCurrentLevel: Int,
    flashlightAmplitudeProvider: () -> Float,
    isBeatDetectedProvider: () -> Boolean,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val supportsMultiIntensity = flashlightIntensityLevels > 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(text = stringResource(R.string.flashlight_header))

        AnimatedToggleCard(
            title = stringResource(R.string.flashlight_sync_title),
            checked = flashlightEnabled,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onFlashlightEnabledChanged(enabled)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = flashlightEnabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 420),
                    initialOffsetY = { fullHeight -> fullHeight / 3 }
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 280),
                    targetOffsetY = { fullHeight -> fullHeight / 5 }
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.flashlight_intensity_label,
                            flashlightIntensityLevels
                        )
                    )
                    BodyText(
                        text = if (supportsMultiIntensity) {
                            "This torch can use multiple brightness levels. Current: $flashlightCurrentLevel / $flashlightIntensityLevels"
                        } else {
                            "This torch is binary: it can only be on or off."
                        },
                        size = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.flashlight_frequency_label,
                            flashlightFreqMin.toInt(),
                            flashlightFreqMax.toInt()
                        )
                    )

                    val currentRange = invLerpLog(flashlightFreqMin, 20f, 1000f)..invLerpLog(
                        flashlightFreqMax,
                        20f,
                        1000f
                    )

                    ExpressiveRangeSlider(
                        value = currentRange,
                        onValueChange = { newRange ->
                            val newMin = lerpLog(newRange.start, 20f, 1000f)
                            val newMax = lerpLog(newRange.endInclusive, 20f, 1000f)

                            if (newMax - newMin >= 10f) {
                                onFlashlightFreqRangeChanged(newMin, newMax)
                            }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    BodyText(
                        text = stringResource(R.string.flashlight_frequency_desc),
                        size = 12.sp
                    )
                }

                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(title = stringResource(R.string.flashlight_mode_label))
                    ExpressiveSplitButton(
                        items = TorchMode.entries,
                        selectedItem = flashlightMode,
                        onItemSelection = onFlashlightModeChanged,
                        labelProvider = { mode ->
                            stringResource(
                                when (mode) {
                                    TorchMode.AMPLITUDE -> R.string.flashlight_mode_amplitude
                                    TorchMode.BEAT_DETECTION -> R.string.flashlight_mode_beat
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (flashlightMode == TorchMode.AMPLITUDE) {
                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(
                            title = stringResource(
                                R.string.flashlight_threshold_label,
                                flashlightThreshold
                            )
                        )

                        ExpressiveSlider(
                            value = flashlightThreshold,
                            onValueChange = onFlashlightThresholdChanged,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        BodyText(
                            text = stringResource(R.string.flashlight_threshold_desc),
                            size = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                if (flashlightMode == TorchMode.BEAT_DETECTION) {
                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(
                            title = stringResource(
                                R.string.flashlight_beat_sensitivity_label,
                                flashlightBeatSensitivity
                            )
                        )
                        ExpressiveSlider(
                            value = flashlightBeatSensitivity,
                            onValueChange = onFlashlightBeatSensitivityChanged,
                            valueRange = 0.3f..6.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BodyText(
                            text = stringResource(R.string.flashlight_beat_sensitivity_desc),
                            size = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(
                            title = if (supportsMultiIntensity) {
                                "Fade out duration: ${flashlightSpeedMs.toInt()}ms"
                            } else {
                                stringResource(R.string.flashlight_speed_label, flashlightSpeedMs)
                            }
                        )
                        ExpressiveSlider(
                            value = flashlightSpeedMs,
                            onValueChange = onFlashlightSpeedMsChanged,
                            valueRange = if (supportsMultiIntensity) 150f..700f else 20f..150f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BodyText(
                            text = if (supportsMultiIntensity) {
                                "Adjust how long the flashlight takes to fade out after each beat."
                            } else {
                                stringResource(R.string.flashlight_speed_desc)
                            },
                            size = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                ExpressiveCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)
                ) {
                    CardHeader(title = stringResource(R.string.flashlight_monitor_label))

                    val flashlightAmplitude = flashlightAmplitudeProvider()
                    val isBeatDetected = isBeatDetectedProvider()
                    val flashColor by animateColorAsState(
                        targetValue = if (isBeatDetected) Color.White else Color.Yellow,
                        animationSpec = if (isBeatDetected) snap() else spring(stiffness = Spring.StiffnessVeryLow),
                        label = "flashColor"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        flashColor.copy(
                                            alpha = 0.08f * flashlightAmplitude.coerceIn(
                                                0f,
                                                1.2f
                                            )
                                        ),
                                        Color.Transparent
                                    ),
                                    radius = 350f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MorphingPolygon(
                            isBeatDetected = isBeatDetected,
                            amplitude = flashlightAmplitude,
                            color = flashColor,
                            modifier = Modifier.size(110.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}
