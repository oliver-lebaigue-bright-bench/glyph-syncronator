package com.better.nothing.music.vizualizer.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.better.nothing.music.vizualizer.model.SongMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlyphNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onControllersChanged(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initMediaSessionTracker()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupMediaSessionTracker()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Notification listener trigger (refreshes active media sessions)
        refreshActiveMediaSession()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveMediaSession()
    }

    private fun initMediaSessionTracker() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, GlyphNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            val initialControllers = mediaSessionManager?.getActiveSessions(componentName)
            onControllersChanged(initialControllers)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaSessionTracker", e)
        }
    }

    private fun cleanupMediaSessionTracker() {
        try {
            activeController?.unregisterCallback(controllerCallback)
            activeController = null
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up MediaSessionTracker", e)
        }
    }

    private fun refreshActiveMediaSession() {
        try {
            val componentName = ComponentName(this, GlyphNotificationListener::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            onControllersChanged(controllers)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing active media session", e)
        }
    }

    private fun onControllersChanged(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            _playbackStateFlow.value = CurrentMediaState()
            return
        }

        // Find playing controller or default to first
        val playingController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.first()

        if (activeController?.sessionToken != playingController.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = playingController
            activeController?.registerCallback(controllerCallback)
        }

        updateMetadata(activeController?.metadata)
        updatePlaybackState(activeController?.playbackState)
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata == null) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

        if (title.isNotEmpty()) {
            val songMeta = SongMetadata(title = title, artist = artist, durationMs = duration, album = album)
            _playbackStateFlow.value = _playbackStateFlow.value.copy(songMetadata = songMeta)
            Log.d(TAG, "Media metadata updated: ${songMeta.normalizedKey}")
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        if (state == null) return
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        val pos = state.position
        val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
        val lastUpdate = state.lastPositionUpdateTime

        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            isPlaying = isPlaying,
            positionMs = pos,
            playbackSpeed = speed,
            lastPositionUpdateTime = lastUpdate
        )
    }

    data class CurrentMediaState(
        val songMetadata: SongMetadata = SongMetadata(),
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val lastPositionUpdateTime: Long = SystemClock.elapsedRealtime()
    ) {
        /**
         * Calculates real-time millisecond song position accounting for elapsed time since last update.
         */
        fun getCurrentPositionMs(): Long {
            if (!isPlaying || positionMs <= 0) return positionMs
            val elapsed = SystemClock.elapsedRealtime() - lastPositionUpdateTime
            return (positionMs + elapsed * playbackSpeed).toLong()
        }
    }

    companion object {
        private const val TAG = "GlyphNotifListener"

        private val _playbackStateFlow = MutableStateFlow(CurrentMediaState())
        val playbackStateFlow: StateFlow<CurrentMediaState> = _playbackStateFlow.asStateFlow()

        var instance: GlyphNotificationListener? = null
            private set
    }
}
