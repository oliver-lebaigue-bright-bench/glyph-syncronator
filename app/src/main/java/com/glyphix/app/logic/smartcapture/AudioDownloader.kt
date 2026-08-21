package com.glyphix.app.logic.smartcapture

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.TimeUnit

class AudioDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAudio(url: String, songKey: String): File? = withContext(Dispatchers.IO) {
        try {
            val audioDir = File(context.cacheDir, "song_audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            val outputFile = File(audioDir, "$songKey.temp_audio")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e("AudioDownloader", "Failed to download audio: ${response.code}")
                return@withContext null
            }
            
            val body = response.body ?: return@withContext null
            
            outputFile.sink().buffer().use { sink ->
                sink.writeAll(body.source())
            }
            
            return@withContext outputFile
        } catch (e: Exception) {
            Log.e("AudioDownloader", "Error downloading audio: ${e.message}")
            return@withContext null
        }
    }
}
