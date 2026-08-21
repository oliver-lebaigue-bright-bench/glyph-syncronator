package com.glyphix.app.logic.smartcapture

import android.content.Context
import android.util.Log
import com.glyphix.app.logic.AudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SmartCaptureOrchestrator(
    private val context: Context,
    private val playbackEngine: SmartCapturePlaybackEngine
) {
    private val pipedClient = PipedApiClient()
    private val downloader = AudioDownloader(context)
    private val analyzer = OfflineAudioAnalyzer()
    
    // In-memory cache for now (can be upgraded to disk/PocketBase later)
    private val sequenceCache = mutableMapOf<String, SongVisualSequence>()
    
    private var currentJob: Job? = null
    
    fun onSongChanged(
        artist: String, 
        title: String, 
        durationMs: Long, 
        startPositionMs: Long,
        config: AudioProcessor.VisualizerConfig
    ) {
        val songKey = "${artist.trim().lowercase()}_${title.trim().lowercase()}"
        
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            // 1. Cache Check
            if (sequenceCache.containsKey(songKey)) {
                Log.d("Orchestrator", "Cache hit for $songKey")
                val sequence = sequenceCache[songKey]!!
                playbackEngine.start(sequence, startPositionMs)
                return@launch
            }
            
            Log.d("Orchestrator", "Cache miss for $songKey. Starting pipeline.")
            
            // 2. Fetch Stream URL
            val streamUrl = pipedClient.getBestAudioStreamUrl(artist, title)
            if (streamUrl == null) {
                Log.e("Orchestrator", "Failed to find audio stream for $songKey")
                return@launch
            }
            
            // 3. Download Audio
            val audioFile = downloader.downloadAudio(streamUrl, songKey)
            if (audioFile == null || !audioFile.exists()) {
                Log.e("Orchestrator", "Failed to download audio for $songKey")
                return@launch
            }
            
            try {
                // 4. Generate Lightshow
                val sequence = analyzer.analyzeAudio(audioFile, songKey, durationMs, config)
                if (sequence != null) {
                    // Save to cache
                    sequenceCache[songKey] = sequence
                    
                    // 5. Play
                    playbackEngine.start(sequence, startPositionMs)
                } else {
                    Log.e("Orchestrator", "Failed to generate sequence for $songKey")
                }
            } finally {
                // IMPORTANT: Automatically delete the downloaded audio file to save space!
                if (audioFile.exists()) {
                    audioFile.delete()
                    Log.d("Orchestrator", "Deleted temporary audio file: ${audioFile.name}")
                }
            }
        }
    }
    
    fun updatePlaybackPosition(positionMs: Long) {
        playbackEngine.updatePosition(positionMs)
    }
    
    fun onPlaybackPaused() {
        playbackEngine.pause()
    }
    
    fun onPlaybackStopped() {
        playbackEngine.stop()
        currentJob?.cancel()
    }
}
