package com.glyphix.app.logic.smartcapture

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.glyphix.app.logic.AudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SmartCaptureOrchestrator(
    private val context: Context,
    private val playbackEngine: SmartCapturePlaybackEngine
) {
    private val lightshowRepository = LightshowRepository()
    private val sequenceCache = mutableMapOf<String, SongVisualSequence>()
    
    private var currentJob: Job? = null
    
    // Live Recording State
    @Volatile private var isRecording = false
    private var recordingSongKey: String? = null
    private var recordingDurationMs: Long = 0L
    private val recordedFrames = mutableListOf<GlyphFrame>()
    
    private var playbackStartRealtimeMs: Long = 0L
    private var playbackOffsetMs: Long = 0L
    private var isPlaying = false
    
    private fun showToast(msg: String) {
        Log.d("SmartCapture", "Smart Capture status: $msg")
        val prefs = context.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        val devMode = prefs.getBoolean("developer_mode_v2", false)
        val debugToasts = prefs.getBoolean("smart_capture_debug_toasts", true)
        if (!devMode || !debugToasts) return

        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, "Smart Capture: $msg", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SmartCapture", "Failed to show toast: ${e.message}")
            }
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
    
    @Synchronized
    private fun finalizeRecording() {
        if (!isRecording || recordingSongKey == null) return
        
        val framesToSave = recordedFrames.toList()
        val key = recordingSongKey!!
        val dur = recordingDurationMs
        
        isRecording = false
        recordingSongKey = null
        recordedFrames.clear()
        
        // If we recorded more than 20 seconds, or more than 70% of the song, let's keep it
        val recordedLengthMs = if (framesToSave.isNotEmpty()) framesToSave.last().timestampMs - framesToSave.first().timestampMs else 0L
        if (recordedLengthMs > 20000L || (dur > 0 && recordedLengthMs > dur * 0.7f)) {
            Log.d("Orchestrator", "Finalized recording for $key: ${framesToSave.size} frames ($recordedLengthMs ms). Uploading...")
            val sequence = SongVisualSequence(key, dur, framesToSave)
            sequenceCache[key] = sequence
            
            CoroutineScope(Dispatchers.IO).launch {
                lightshowRepository.uploadLightshow(sequence)
                showToast("Live generation complete! Uploaded to db.glyphix.site.")
            }
        } else {
            Log.d("Orchestrator", "Discarded recording for $key: Only $recordedLengthMs ms recorded (needed 20s+).")
        }
    }
    
    fun onSongChanged(
        artist: String, 
        title: String, 
        durationMs: Long, 
        startPositionMs: Long,
        config: AudioProcessor.VisualizerConfig,
        deviceType: Int = com.glyphix.app.model.DeviceProfile.DEVICE_NP2,
        audioSessionId: Int = 0
    ) {
        val songKey = "${artist.trim().lowercase()}_${title.trim().lowercase()}"
        
        if (isRecording && recordingSongKey != songKey) {
            finalizeRecording()
        }
        
        val prefs = context.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean("smart_capture_wifi_only", true)

        showToast("Detected: $title by $artist")
        
        playbackStartRealtimeMs = SystemClock.elapsedRealtime()
        playbackOffsetMs = startPositionMs
        isPlaying = true
        
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            if (sequenceCache.containsKey(songKey)) {
                Log.d("Orchestrator", "Memory cache hit for $songKey")
                showToast("Memory cache hit! Playing sequence.")
                val sequence = sequenceCache[songKey]!!
                playbackEngine.start(sequence, startPositionMs)
                return@launch
            }

            if (wifiOnly && !isWifiConnected()) {
                Log.d("Orchestrator", "Not on Wi-Fi and Wi-Fi only option enabled. Fallback to live visualizer.")
                showToast("Wi-Fi only enabled. Using live visualizer.")
                return@launch
            }
            
            Log.d("Orchestrator", "Checking crowdsourced DB for $songKey")
            showToast("Checking db.glyphix.site...")
            val dbSequence = lightshowRepository.fetchLightshow(songKey)
            if (dbSequence != null && dbSequence.frames.isNotEmpty()) {
                Log.d("Orchestrator", "PocketBase DB hit for $songKey (${dbSequence.frames.size} frames)")
                showToast("Crowdsourced lightshow found! Playing.")
                sequenceCache[songKey] = dbSequence
                playbackEngine.start(dbSequence, startPositionMs)
                return@launch
            }
            
            Log.d("Orchestrator", "Cache miss for $songKey. Starting Live-Recording on session $audioSessionId.")
            showToast("Generating live on the fly... using targeted visualizer.")
            
            // Start recording live frames
            synchronized(this@SmartCaptureOrchestrator) {
                isRecording = true
                recordingSongKey = songKey
                recordingDurationMs = durationMs
                recordedFrames.clear()
            }
        }
    }
    
    /**
     * Called by AudioCaptureService on every processed frame when fallback visualizer is active.
     */
    fun recordLiveFrame(intensities: FloatArray) {
        if (!isRecording) return
        if (!isPlaying) return
        
        val now = SystemClock.elapsedRealtime()
        val currentPositionMs = playbackOffsetMs + (now - playbackStartRealtimeMs)
        
        synchronized(this) {
            if (isRecording) {
                recordedFrames.add(GlyphFrame(currentPositionMs, intensities.toList()))
            }
        }
    }
    
    fun updatePlaybackPosition(positionMs: Long) {
        playbackStartRealtimeMs = SystemClock.elapsedRealtime()
        playbackOffsetMs = positionMs
        playbackEngine.updatePosition(positionMs)
    }
    
    fun onPlaybackPaused() {
        isPlaying = false
        playbackEngine.pause()
    }
    
    fun onPlaybackStopped() {
        isPlaying = false
        finalizeRecording()
        playbackEngine.stop()
        currentJob?.cancel()
    }
}
