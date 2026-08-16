package com.better.nothing.music.vizualizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.CommunityPresetsScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LeaderboardScreen
import kotlinx.coroutines.launch

@Composable
internal fun CommunityOverlays(
    viewModel: MainViewModel
) {
    val isShowingCommunity by viewModel.isShowingCommunity.collectAsStateWithLifecycle()
    val isShowingAnnouncementHistory by viewModel.showAnnouncementHistory.collectAsStateWithLifecycle()
    val isShowingLeaderboard by viewModel.isShowingLeaderboard.collectAsStateWithLifecycle()
    val latestAnnouncement by viewModel.latestAnnouncement.collectAsStateWithLifecycle()
    val showAnnouncementModal by viewModel.showAnnouncementModal.collectAsStateWithLifecycle()
    val showAnnouncementEditor by viewModel.showAnnouncementEditor.collectAsStateWithLifecycle()

    if (isShowingCommunity) {
        val userId by viewModel.userId.collectAsStateWithLifecycle()
        val presets by viewModel.communityRepository.getPresets().collectAsStateWithLifecycle(initialValue = null)
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        CommunityPresetsScreen(
            presets = presets,
            currentUserId = userId,
            error = null,
            onDownload = { preset ->
                scope.launch {
                    try {
                        viewModel.communityRepository.incrementDownloadCount(preset.id)
                    } catch (e: Exception) {
                        // Log or show error
                    }
                }
                // Add logic to actually apply/download the preset
            },
            onDelete = { preset -> viewModel.deleteCustomPreset(preset.id) },
            onDismiss = { viewModel.hideCommunity() }
        )
    }

    if (isShowingAnnouncementHistory) {
        val announcements by viewModel.announcementHistory.collectAsStateWithLifecycle()
        AnnouncementHistoryScreen(
            announcements = announcements,
            onDismiss = { viewModel.hideAnnouncementHistory() }
        )
    }

    if (isShowingLeaderboard) {
        val entries by viewModel.leaderboardEntries.collectAsStateWithLifecycle()
        LeaderboardScreen(
            entries = entries,
            onDismiss = { viewModel.hideLeaderboard() }
        )
    }

    if (showAnnouncementModal && latestAnnouncement != null) {
        AnnouncementModal(
            announcement = latestAnnouncement!!,
            onDismiss = { viewModel.dismissAnnouncement() }
        )
    }

    if (showAnnouncementEditor) {
        AnnouncementEditorScreen(
            onPost = { t, m, s, l, lt -> viewModel.postAnnouncement(t, m, s, l, lt) },
            onDismiss = { viewModel.hideAnnouncementEditor() }
        )
    }
}
