package com.glyphix.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.model.UserProfile
import com.glyphix.app.ui.SecondaryScreens.AboutScreen
import com.glyphix.app.ui.SecondaryScreens.CustomPresetEditorScreen
import com.glyphix.app.ui.SecondaryScreens.LicenseScreen
import com.glyphix.app.ui.SecondaryScreens.ProfileScreen
import com.glyphix.app.ui.SecondaryScreens.StatsScreen

@Composable
internal fun MainOverlays(
    viewModel: MainViewModel,
    selectedDevice: Int,
    onGoogleSignIn: () -> Unit = {}
) {
    val isShowingEditor by viewModel.isShowingEditor.collectAsStateWithLifecycle()
    val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val isShowingStats by viewModel.isShowingStats.collectAsStateWithLifecycle()
    val isShowingProfile by viewModel.isShowingProfile.collectAsStateWithLifecycle()
    val isShowingProfileSetup by viewModel.isShowingProfileSetup.collectAsStateWithLifecycle()

    val expansionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    AnimatedVisibility(
        visible = isShowingProfile,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.85f) + fadeOut()
    ) {
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

    AnimatedVisibility(
        visible = isShowingEditor,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.8f) + fadeOut()
    ) {
        val fftState by viewModel.fftState.collectAsStateWithLifecycle()
        CustomPresetEditorScreen(
            selectedDevice = selectedDevice,
            fftState = fftState,
            onDismiss = { viewModel.hideEditor() },
            onSave = { name, zones, key -> viewModel.saveCustomPreset(name, zones, key) },
            onShare = { name, author, zones -> /* Handle share */ }
        )
    }

    AnimatedVisibility(
        visible = isShowingAbout,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.9f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.9f) + fadeOut()
    ) {
        AboutScreen(onDismiss = { viewModel.hideAbout() }, viewModel = viewModel)
    }

    AnimatedVisibility(
        visible = isShowingLicense,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        LicenseScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideLicense() }
        )
    }

    AnimatedVisibility(
        visible = isShowingStats,
        enter = scaleIn(animationSpec = expansionSpec, initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(animationSpec = expansionSpec, targetScale = 0.8f) + fadeOut()
    ) {
        StatsScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideStats() },
            onOpenProfile = {
                viewModel.hideStats()
                viewModel.showProfile()
            }
        )
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
}
