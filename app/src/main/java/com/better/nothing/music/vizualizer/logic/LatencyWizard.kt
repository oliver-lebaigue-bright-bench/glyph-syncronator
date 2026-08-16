package com.better.nothing.music.vizualizer.logic

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

class LatencyWizard {
    companion object {
        private const val TAG = "LatencyWizard"
        private const val SAMPLE_RATE = 44100
        private const val PRE_WARMUP_MS = 500
        private const val PULSE_DURATION_MS = 150
        private const val RECORD_DURATION_MS = 2500
        private const val FREQUENCY = 1000.0
    }

    sealed class State {
        object Idle : State()
        object Preparing : State()
        object Recording : State()
        object Analyzing : State()
        data class Success(val latencyMs: Int) : State()
        data class Error(val message: String) : State()
    }

    @SuppressLint("MissingPermission")
    suspend fun measureLatency(
        audioManager: AudioManager? = null,
        onStateChanged: (State) -> Unit = {}
    ): State = withContext(Dispatchers.Default) {
        onStateChanged(State.Preparing)
        Log.d(TAG, "Starting latency measurement...")
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize <= 0) return@withContext State.Error("Failed to get min buffer size")

        val recorder = try {
            // Use standard MIC source which is more likely to be shareable
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 4
            )
        } catch (e: Exception) {
            return@withContext State.Error("AudioRecord Init Exception: ${e.message}")
        }

        // Try to force built-in microphone
        try {
            audioManager?.let { am ->
                val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val builtInMic = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                if (builtInMic != null) {
                    val success = recorder.setPreferredDevice(builtInMic)
                    Log.d(TAG, "Forced built-in microphone (${builtInMic.productName}), success: $success")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set preferred device: ${e.message}")
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext State.Error("AudioRecord failed to initialize. Is another app using the mic?")
        }

        // Prepare audio data with warmup
        val warmupSamples = (SAMPLE_RATE * PRE_WARMUP_MS / 1000)
        val pulseSamples = (SAMPLE_RATE * PULSE_DURATION_MS / 1000)
        val totalSamples = warmupSamples + pulseSamples
        val audioData = ShortArray(totalSamples)
        
        // Warmup: very low volume white noise to wake up Bluetooth/DAC
        for (i in 0 until warmupSamples) {
            audioData[i] = (Math.random() * 200 - 100).toInt().toShort()
        }
        
        // Beep
        for (i in 0 until pulseSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            audioData[warmupSamples + i] = (sin(2.0 * PI * FREQUENCY * t) * Short.MAX_VALUE * 0.95).toInt().toShort()
        }

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(audioData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            recorder.release()
            return@withContext State.Error("AudioTrack Init Exception: ${e.message}")
        }

        audioTrack.write(audioData, 0, audioData.size)

        var originalVolume = -1
        try {
            if (audioManager != null) {
                originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * 0.85).toInt(), 0)
                Log.d(TAG, "Volume set to 85% (from $originalVolume)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust volume: ${e.message}")
        }

        try {
            onStateChanged(State.Recording)
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                return@withContext State.Error("AudioRecord failed to start.")
            }
            
            // Wait for mic to settle
            delay(400)

            Log.d(TAG, "Playing pulse...")
            audioTrack.play()

            val recordBuffer = ShortArray(SAMPLE_RATE * RECORD_DURATION_MS / 1000)
            var totalRead = 0
            val recordingStartTime = System.currentTimeMillis()
            
            while (totalRead < recordBuffer.size && (System.currentTimeMillis() - recordingStartTime) < RECORD_DURATION_MS + 500) {
                val read = recorder.read(recordBuffer, totalRead, (recordBuffer.size - totalRead).coerceAtMost(bufferSize))
                if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
                totalRead += read
            }

            onStateChanged(State.Analyzing)
            Log.d(TAG, "Analyzing $totalRead samples...")
            
            var maxVal = 0
            for (i in 0 until totalRead) {
                val v = Math.abs(recordBuffer[i].toInt())
                if (v > maxVal) maxVal = v
            }
            Log.d(TAG, "Recording finished. Max absolute value: $maxVal")

            // 1. Calculate ambient level more robustly
            val ambientStart = (SAMPLE_RATE * 100 / 1000)
            val ambientEnd = (SAMPLE_RATE * 300 / 1000)
            var sumAbs = 0.0
            var count = 0
            for (i in ambientStart until ambientEnd.coerceAtMost(totalRead)) {
                sumAbs += Math.abs(recordBuffer[i].toInt())
                count++
            }
            val avgAmbient = if (count > 0) sumAbs / count else 0.0
            
            var maxAmbient = 0f
            for (i in ambientStart until ambientEnd.coerceAtMost(totalRead)) {
                val absVal = Math.abs(recordBuffer[i].toInt()).toFloat()
                if (absVal > maxAmbient) maxAmbient = absVal
            }

            Log.d(TAG, "Ambient Analysis: Avg=$avgAmbient, Max=$maxAmbient")

            // 2. Detection with sliding window
            val searchStart = (SAMPLE_RATE * 600 / 1000)
            val windowSize = (SAMPLE_RATE * 5 / 1000) 
            
            // Lower threshold significantly for better compatibility
            val threshold = (maxAmbient * 1.8f).coerceAtLeast(avgAmbient.toFloat() * 5f).coerceAtLeast(1000f)
            Log.d(TAG, "Detection threshold: $threshold")

            var detectedIndex = -1
            for (i in searchStart until (totalRead - windowSize)) {
                var windowSum = 0.0
                for (j in 0 until windowSize) {
                    windowSum += Math.abs(recordBuffer[i + j].toInt())
                }
                val windowAvg = windowSum / windowSize
                
                if (windowAvg > threshold) {
                    detectedIndex = i
                    break
                }
            }

            if (detectedIndex != -1) {
                val measuredTimeMs = (detectedIndex.toDouble() / SAMPLE_RATE * 1000).toInt()
                val finalLatency = measuredTimeMs - 900
                val correctedLatency = (finalLatency - 40).coerceIn(0, 500)
                
                Log.d(TAG, "Detected at: ${measuredTimeMs}ms. Raw Latency: ${finalLatency}ms. Corrected: ${correctedLatency}ms")
                return@withContext State.Success(correctedLatency)
            } else {
                Log.w(TAG, "No pulse detected. Max ambient: $maxAmbient, Max signal: $maxVal, Threshold: $threshold")
                return@withContext State.Error("Beep not detected (Signal: $maxVal, Thr: ${threshold.toInt()}). Try increasing speaker volume or moving the phone closer.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during measurement", e)
            return@withContext State.Error(e.message ?: "Unknown error during measurement")
        } finally {
            try {
                if (originalVolume != -1 && audioManager != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore volume")
            }
            
            try { recorder.stop() } catch (e: Exception) {}
            recorder.release()
            try { audioTrack.stop() } catch (e: Exception) {}
            audioTrack.release()
        }
    }
}
