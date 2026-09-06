package com.glyphix.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.model.UserProfile
import com.glyphix.app.ui.SecondaryScreens.AboutScreen
import com.glyphix.app.ui.SecondaryScreens.CustomPresetEditorScreen
import com.glyphix.app.ui.SecondaryScreens.LicenseScreen
import com.glyphix.app.ui.SecondaryScreens.ProfileScreen
import com.glyphix.app.ui.SecondaryScreens.StatsScreen
import com.glyphix.app.ui.PrimaryScreens.GlyphsScreen
import com.glyphix.app.ui.PrimaryScreens.HapticsScreen
import com.glyphix.app.ui.PrimaryScreens.FlashlightScreen
import com.glyphix.app.ui.PrimaryScreens.SpotifyScreen
import com.glyphix.app.ui.PrimaryScreens.VisualsScreen
import com.glyphix.app.service.AudioCaptureService

@Composable
internal fun MainOverlays(
    viewModel: MainViewModel,
    selectedDevice: Int,
    onGoogleSignIn: () -> Unit = {},
    onOverlayPermissionRequest: () -> Unit = {}
) {
    val isShowingEditor by viewModel.isShowingEditor.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val isShowingProfile by viewModel.isShowingProfile.collectAsStateWithLifecycle()
    val isShowingProfileSetup by viewModel.isShowingProfileSetup.collectAsStateWithLifecycle()
    val isShowingGlyphs by viewModel.isShowingGlyphs.collectAsStateWithLifecycle()
    val isShowingHaptics by viewModel.isShowingHaptics.collectAsStateWithLifecycle()
    val isShowingFlashlight by viewModel.isShowingFlashlight.collectAsStateWithLifecycle()
    val isShowingSpotify by viewModel.isShowingSpotify.collectAsStateWithLifecycle()
    val isShowingVisuals by viewModel.isShowingVisuals.collectAsStateWithLifecycle()
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()

    val expansionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    AnimatedVisibility(
        visible = isShowingProfile,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.85f) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            ProfileScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.hideProfile() },
                onOpenLeaderboard = {
                    viewModel.hideProfile()
                    viewModel.showLeaderboard()
                },
                onGoogleSignIn = onGoogleSignIn
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingEditor,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.8f) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            val fftState by viewModel.fftState.collectAsStateWithLifecycle()
            CustomPresetEditorScreen(
                selectedDevice = selectedDevice,
                fftState = fftState,
                onDismiss = { viewModel.hideEditor() },
                onSave = { name, zones, key -> viewModel.saveCustomPreset(name, zones, key) },
                onShare = { name, author, zones -> /* Handle share */ }
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingLicense,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            LicenseScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.hideLicense() }
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingStats,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.8f) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            StatsScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.hideStats() },
                onOpenProfile = {
                    viewModel.hideStats()
                    viewModel.showProfile()
                }
            )
        }
    }
    if (isShowingProfileSetup) {
        val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
        ProfileSetupDialog(
            userProfile = userProfile,
            onSave = { 
                viewModel.updateProfile(it)
                viewModel.hideProfileSetup()
            },
            onPickImage = { viewModel.uploadProfilePicture(it) },
            onDismiss = { viewModel.hideProfileSetup() }
        )
    }

    AnimatedVisibility(
        visible = isShowingGlyphs,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
            val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
            val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
            val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
            val vizStateState by viewModel.visualizerState.collectAsStateWithLifecycle()

            GlyphsScreen(
                gammaValue = gammaValue,
                onGammaChanged = { viewModel.setGammaValue(it); viewModel.persistGamma(it) },
                maxBrightness = maxBrightness,
                onMaxBrightnessChanged = { viewModel.setMaxBrightness(it) },
                presets = presets,
                selectedPreset = selectedPreset,
                onPresetSelected = { viewModel.setSelectedPreset(it) },
                isRunning = isRunning,
                selectedDevice = selectedDevice,
                viewModel = viewModel,
                vizStateProvider = { vizStateState },
                onDismiss = { viewModel.hideGlyphs() }
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingHaptics,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            val hapticMotorEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
            val hapticMode by viewModel.hapticMode.collectAsStateWithLifecycle()
            val hapticFreqMin by viewModel.hapticFreqMin.collectAsStateWithLifecycle()
            val hapticFreqMax by viewModel.hapticFreqMax.collectAsStateWithLifecycle()
            val hapticMultiplier by viewModel.hapticMultiplier.collectAsStateWithLifecycle()
            val hapticAudioGain by viewModel.hapticAudioGain.collectAsStateWithLifecycle()
            val hapticGamma by viewModel.hapticGamma.collectAsStateWithLifecycle()
            val hapticBeatSensitivity by viewModel.hapticBeatSensitivity.collectAsStateWithLifecycle()
            val hapticBeatGamma by viewModel.hapticBeatGamma.collectAsStateWithLifecycle()
            val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()
            val hapticAmplitudeState by viewModel.hapticAmplitude.collectAsStateWithLifecycle()

            HapticsScreen(
                hapticMotorEnabled = hapticMotorEnabled,
                onHapticMotorEnabledChanged = { viewModel.setHapticMotorEnabled(it) },
                hapticMode = hapticMode,
                onHapticModeChanged = { viewModel.setHapticMode(it) },
                hapticFreqMin = hapticFreqMin,
                hapticFreqMax = hapticFreqMax,
                onHapticFreqRangeChanged = { min, max -> viewModel.setHapticFreqRange(min, max) },
                hapticMultiplier = hapticMultiplier,
                onHapticMultiplierChanged = { viewModel.setHapticMultiplier(it) },
                hapticAudioGain = hapticAudioGain,
                onHapticAudioGainChanged = { viewModel.setHapticAudioGain(it) },
                hapticGamma = hapticGamma,
                onHapticGammaChanged = { viewModel.setHapticGamma(it) },
                hapticBeatSensitivity = hapticBeatSensitivity,
                onHapticBeatSensitivityChanged = { viewModel.setHapticBeatSensitivity(it) },
                hapticBeatGamma = hapticBeatGamma,
                onHapticBeatGammaChanged = { viewModel.setHapticBeatGamma(it) },
                hapticAmplitudeProvider = { hapticAmplitudeState },
                isBeatDetectedProvider = { isBeatDetected },
                onDismiss = { viewModel.hideHaptics() }
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingFlashlight,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
            val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
            val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
            val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
            val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
            val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
            val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
            val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
            val flashlightLevel by viewModel.flashlightLevel.collectAsStateWithLifecycle()
            val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()
            val flashlightAmplitudeState by viewModel.flashlightAmplitude.collectAsStateWithLifecycle()

            FlashlightScreen(
                flashlightEnabled = flashlightEnabled,
                onFlashlightEnabledChanged = { viewModel.setFlashlightEnabled(it) },
                flashlightMode = flashlightMode,
                onFlashlightModeChanged = { viewModel.setFlashlightMode(it) },
                flashlightFreqMin = flashlightFreqMin,
                flashlightFreqMax = flashlightFreqMax,
                onFlashlightFreqRangeChanged = { min, max -> viewModel.setFlashlightFreqRange(min, max) },
                flashlightThreshold = flashlightThreshold,
                onFlashlightThresholdChanged = { viewModel.setFlashlightThreshold(it) },
                flashlightSpeedMs = flashlightSpeedMs,
                onFlashlightSpeedMsChanged = { viewModel.setFlashlightSpeedMs(it) },
                flashlightBeatSensitivity = flashlightBeatSensitivity,
                onFlashlightBeatSensitivityChanged = { viewModel.setFlashlightBeatSensitivity(it) },
                flashlightIntensityLevels = flashlightIntensityLevels,
                flashlightCurrentLevel = flashlightLevel,
                flashlightAmplitudeProvider = { flashlightAmplitudeState },
                isBeatDetectedProvider = { isFlashlightBeatDetected },
                onDismiss = { viewModel.hideFlashlight() }
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingSpotify,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            SpotifyScreen(
                spotifyRepo = viewModel.spotifyRepository,
                authManager = viewModel.spotifyAuthManager,
                onStartVisualizer = {
                    viewModel.setCaptureSource(AudioCaptureService.CaptureSource.SPOTIFY)
                    MainActivity.serviceStatic?.startCapture(0, null)
                },
                onActivateSpotifyInput = {
                    viewModel.setCaptureSource(AudioCaptureService.CaptureSource.SPOTIFY)
                },
                onDismiss = { viewModel.hideSpotify() }
            )
        }
    }

    AnimatedVisibility(
        visible = isShowingVisuals,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlyphixBackground()
            val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
            VisualsScreen(
                viewModel = viewModel,
                overlayEnabled = overlayEnabled,
                onOverlayEnabledChanged = { viewModel.setOverlayEnabled(it) },
                onOverlayPermissionRequest = onOverlayPermissionRequest,
                onDismiss = { viewModel.hideVisuals() }
            )
        }
    }
}
