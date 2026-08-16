package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.better.nothing.music.vizualizer.model.SongVisualSequence
import com.better.nothing.music.vizualizer.service.GlyphNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 60 FPS frame playback engine that synchronizes pre-generated Glyph sequences
 * with active song position provided by GlyphNotificationListener.
 */
class MediaSyncPlaybackEngine(
    private val context: Context,
    private val glyphRenderer: GlyphRenderer
) {
    private val repository = SongVisualizerRepository(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())

    private var currentSequence: SongVisualSequence? = null
    private var isEngineRunning = false
    private var observeJob: Job? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isEngineRunning) return
            renderCurrentFrame()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    fun start() {
        if (isEngineRunning) return
        isEngineRunning = true
        Log.d(TAG, "Starting MediaSyncPlaybackEngine")

        observeJob = scope.launch {
            GlyphNotificationListener.playbackStateFlow.collectLatest { mediaState ->
                val meta = mediaState.songMetadata
                if (meta.title.isNotEmpty() && currentSequence?.songKey != meta.normalizedKey) {
                    Log.d(TAG, "New song detected: ${meta.normalizedKey}")
                    currentSequence = repository.getOrGenerateSequence(meta)
                }
            }
        }

        handler.post(tickRunnable)
    }

    fun stop() {
        if (!isEngineRunning) return
        isEngineRunning = false
        Log.d(TAG, "Stopping MediaSyncPlaybackEngine")
        handler.removeCallbacks(tickRunnable)
        observeJob?.cancel()
        observeJob = null
        currentSequence = null
    }

    private fun renderCurrentFrame() {
        val seq = currentSequence ?: return
        val mediaState = GlyphNotificationListener.playbackStateFlow.value
        if (!mediaState.isPlaying || seq.frames.isEmpty()) return

        val posMs = mediaState.getCurrentPositionMs()
        if (posMs < 0 || posMs > seq.durationMs + 5000) return

        // Find frame nearest to posMs
        val frames = seq.frames
        var frameIndex = (posMs / 33L).toInt().coerceIn(0, frames.size - 1)
        val frame = frames[frameIndex]

        // Pass frame intensities to GlyphRenderer
        val floatArray = frame.zoneIntensities.toFloatArray()
        glyphRenderer.renderFrameFromIntensities(floatArray)
    }

    companion object {
        private const val TAG = "MediaSyncPlaybackEngine"
        private const val FRAME_INTERVAL_MS = 16L // ~60 FPS
    }
}
