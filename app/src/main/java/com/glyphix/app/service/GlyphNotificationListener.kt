package com.glyphix.app.service

import android.app.Notification
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class GlyphNotificationListener : NotificationListenerService() {

    private var activeController: MediaController? = null
    
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            if (state == null) return
            
            val intent = Intent("com.glyphix.app.action.STATE_CHANGED")
            intent.setPackage(packageName)
            intent.putExtra("is_playing", state.state == PlaybackState.STATE_PLAYING)
            intent.putExtra("position", state.position)
            sendBroadcast(intent)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            if (metadata == null) return
            
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            // Some apps use METADATA_KEY_DURATION, some might omit it.
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val state = activeController?.playbackState
            val position = state?.position ?: 0L
            
            Log.d("GlyphNotif", "Song changed: $title - $artist")
            if (title != null && artist != null) {
                val intent = Intent("com.glyphix.app.action.SONG_CHANGED")
                intent.setPackage(packageName)
                intent.putExtra("title", title)
                intent.putExtra("artist", artist)
                intent.putExtra("duration", duration)
                intent.putExtra("position", position)
                sendBroadcast(intent)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.d("GlyphNotif", "Notification posted: ${sbn?.packageName}")
        val notification = sbn?.notification ?: return
        val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        
        if (token != null) {
            if (activeController?.sessionToken != token) {
                activeController?.unregisterCallback(callback)
                activeController = MediaController(this, token)
                activeController?.registerCallback(callback)
                
                // Trigger initial fetch
                callback.onMetadataChanged(activeController?.metadata)
                callback.onPlaybackStateChanged(activeController?.playbackState)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null && activeController?.sessionToken == token) {
            activeController?.unregisterCallback(callback)
            activeController = null
        }
    }
}
