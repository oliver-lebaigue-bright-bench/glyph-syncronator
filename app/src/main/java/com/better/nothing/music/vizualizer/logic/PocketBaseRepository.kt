package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.util.Log
import com.better.nothing.music.vizualizer.model.SongVisualSequence
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Handles communication with the self-hosted PocketBase backend.
 */
class PocketBaseRepository(private val context: Context) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(ServerConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(ServerConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(ServerConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = ServerConfig.getPocketBaseUrl(context)

    suspend fun getSongVisualSequence(songKey: String): SongVisualSequence? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/collections/song_visuals/records?filter=(songKey='$songKey')"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "PocketBase fetch failed with status ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = json.getAsJsonArray("items") ?: return@withContext null
                if (items.size() == 0) return@withContext null

                val firstItem = items.get(0).asJsonObject
                return@withContext parseSequenceFromRecord(firstItem)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PocketBase connection error for $songKey: ${e.message}")
            return@withContext null
        }
    }

    suspend fun uploadSongVisualSequence(sequence: SongVisualSequence): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/collections/song_visuals/records"
        val recordJson = JsonObject().apply {
            addProperty("songKey", sequence.songKey)
            addProperty("title", sequence.title)
            addProperty("artist", sequence.artist)
            addProperty("durationMs", sequence.durationMs)
            add("framesJson", gson.toJsonTree(sequence.frames))
            addProperty("version", sequence.version)
            addProperty("downloads", sequence.downloads)
        }

        val requestBody = gson.toJson(recordJson).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully uploaded sequence to PocketBase: ${sequence.songKey}")
                    return@withContext true
                } else {
                    Log.w(TAG, "PocketBase upload failed with status ${response.code}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PocketBase upload connection error: ${e.message}")
            return@withContext false
        }
    }

    private fun parseSequenceFromRecord(record: JsonObject): SongVisualSequence {
        val songKey = record.get("songKey")?.asString ?: ""
        val title = record.get("title")?.asString ?: ""
        val artist = record.get("artist")?.asString ?: ""
        val durationMs = record.get("durationMs")?.asLong ?: 0L
        val version = record.get("version")?.asInt ?: 1
        val downloads = record.get("downloads")?.asInt ?: 0

        val framesElement = record.get("framesJson")
        val frames = if (framesElement != null && !framesElement.isJsonNull) {
            runCatching {
                gson.fromJson(framesElement, Array<com.better.nothing.music.vizualizer.model.GlyphFrame>::class.java).toList()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        return SongVisualSequence(
            songKey = songKey,
            title = title,
            artist = artist,
            durationMs = durationMs,
            frames = frames,
            version = version,
            downloads = downloads
        )
    }

    companion object {
        private const val TAG = "PocketBaseRepo"
    }
}
