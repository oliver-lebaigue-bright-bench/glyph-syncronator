package com.glyphix.app.logic.smartcapture

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.glyphix.app.logic.AudioProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OfflineAudioAnalyzer {
    suspend fun analyzeAudio(
        audioFile: File,
        songKey: String,
        durationMs: Long,
        config: AudioProcessor.VisualizerConfig
    ): SongVisualSequence? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            
            if (audioTrackIndex < 0 || format == null) {
                Log.e("OfflineAudioAnalyzer", "No audio track found")
                return@withContext null
            }
            
            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            
            val processor = AudioProcessor()
            processor.updateFFTSize(sampleRate)
            
            val frames = mutableListOf<GlyphFrame>()
            var isEOS = false
            val bufferInfo = MediaCodec.BufferInfo()
            
            val hopSize = 1024
            var pcmBuffer = ShortArray(0)
            
            while (true) {
                if (!isEOS) {
                    val inIndex = codec.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val sampleSize = extractor.readSampleData(buffer!!, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val timeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }
                
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                when (outIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (isEOS) break
                    }
                    else -> {
                        if (outIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outIndex)
                            buffer?.position(bufferInfo.offset)
                            buffer?.limit(bufferInfo.offset + bufferInfo.size)
                            
                            if (buffer != null) {
                                val shortBuffer = buffer.asShortBuffer()
                                val shorts = ShortArray(shortBuffer.remaining())
                                shortBuffer.get(shorts)
                                
                                val monoShorts = if (channelCount == 2) {
                                    val mono = ShortArray(shorts.size / 2)
                                    for (i in mono.indices) {
                                        mono[i] = ((shorts[i * 2].toInt() + shorts[i * 2 + 1].toInt()) / 2).toShort()
                                    }
                                    mono
                                } else {
                                    shorts
                                }
                                
                                pcmBuffer += monoShorts
                                
                                while (pcmBuffer.size >= hopSize) {
                                    val hop = pcmBuffer.copyOfRange(0, hopSize)
                                    pcmBuffer = pcmBuffer.copyOfRange(hopSize, pcmBuffer.size)
                                    
                                    val result = processor.processAudioFrame(hop, config, null, null, false)
                                    if (result != null) {
                                        frames.add(GlyphFrame(bufferInfo.presentationTimeUs / 1000, result.uniqueMagnitudes))
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }
            
            codec.stop()
            codec.release()
            extractor.release()
            
            return@withContext SongVisualSequence(songKey, durationMs, frames)
        } catch (e: Exception) {
            Log.e("OfflineAudioAnalyzer", "Error analyzing audio", e)
            extractor.release()
            return@withContext null
        }
    }
}
