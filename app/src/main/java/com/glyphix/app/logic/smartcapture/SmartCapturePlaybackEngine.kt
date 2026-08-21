package com.glyphix.app.logic.smartcapture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class SmartCapturePlaybackEngine(
    private val pushFrameCallback: (FloatArray) -> Unit
) {
    private var isPlaying = false
    private var currentSequence: SongVisualSequence? = null
    
    // Time tracking
    private var playbackStartTimeMs: Long = 0
    private var startOffsetMs: Long = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            
            tick()
            // ~60 FPS
            handler.postDelayed(this, 16)
        }
    }

    fun start(sequence: SongVisualSequence, startPositionMs: Long) {
        currentSequence = sequence
        startOffsetMs = startPositionMs
        playbackStartTimeMs = SystemClock.elapsedRealtime()
        
        if (!isPlaying) {
            isPlaying = true
            handler.post(tickRunnable)
        }
    }
    
    fun updatePosition(positionMs: Long) {
        startOffsetMs = positionMs
        playbackStartTimeMs = SystemClock.elapsedRealtime()
    }

    fun pause() {
        isPlaying = false
        handler.removeCallbacks(tickRunnable)
    }

    fun stop() {
        isPlaying = false
        currentSequence = null
        handler.removeCallbacks(tickRunnable)
    }

    private fun tick() {
        val seq = currentSequence ?: return
        
        val elapsedSinceStart = SystemClock.elapsedRealtime() - playbackStartTimeMs
        val currentPositionMs = startOffsetMs + elapsedSinceStart
        
        if (currentPositionMs > seq.durationMs) {
            stop()
            return
        }
        
        // Find the closest frame
        val frames = seq.frames
        if (frames.isEmpty()) return
        
        // Binary search or simple linear scan if not too big. 
        // For efficiency, we assume frames are sorted by timestampMs.
        // We find the frame whose timestamp is closest to currentPositionMs.
        
        var closestFrame = frames.first()
        for (frame in frames) {
            if (frame.timestampMs > currentPositionMs) {
                break
            }
            closestFrame = frame
        }
        
        pushFrameCallback(closestFrame.intensities)
    }
}
