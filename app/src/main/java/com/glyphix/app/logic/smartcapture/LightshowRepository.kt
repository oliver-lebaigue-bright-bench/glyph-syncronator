package com.glyphix.app.logic.smartcapture

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LightshowRepository {
    private val baseUrl = "https://db.glyphix.site"
    private val collectionName = "lightshows"
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun sanitizeKey(songKey: String): String {
        return songKey.lowercase().trim()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    suspend fun fetchLightshow(songKey: String): SongVisualSequence? = withContext(Dispatchers.IO) {
        val safeKey = sanitizeKey(songKey)
        if (safeKey.isEmpty()) return@withContext null
        try {
            val encodedFilter = URLEncoder.encode("songKey ~ '$safeKey'", "UTF-8")
            val url = "$baseUrl/api/collections/$collectionName/records?filter=$encodedFilter"
            Log.d("LightshowRepo", "Fetching lightshow from PocketBase ($url)")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Glyphix-Android/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d("LightshowRepo", "PocketBase returned HTTP ${response.code} for $safeKey")
                    return@withContext null
                }
                
                val bodyStr = response.body?.string() ?: return@withContext null
                val rootJson = gson.fromJson(bodyStr, JsonObject::class.java) ?: return@withContext null
                val items = rootJson.getAsJsonArray("items")
                
                if (items == null || items.size() == 0) {
                    Log.d("LightshowRepo", "No record found on PocketBase for $safeKey")
                    return@withContext null
                }

                val item = items[0].asJsonObject
                val durationMs = item.get("durationMs")?.asLong ?: 0L
                
                val framesElement = item.get("sequenceData") ?: item.get("frames")
                val frames: List<GlyphFrame> = when {
                    framesElement == null || framesElement.isJsonNull -> emptyList()
                    framesElement.isJsonArray -> {
                        val type = object : TypeToken<List<GlyphFrame>>() {}.type
                        gson.fromJson(framesElement, type) ?: emptyList()
                    }
                    framesElement.isJsonPrimitive -> {
                        val jsonStr = framesElement.asString
                        val type = object : TypeToken<List<GlyphFrame>>() {}.type
                        gson.fromJson(jsonStr, type) ?: emptyList()
                    }
                    else -> emptyList()
                }

                if (frames.isNotEmpty()) {
                    Log.d("LightshowRepo", "Successfully fetched PocketBase lightshow for $safeKey (${frames.size} frames)")
                    return@withContext SongVisualSequence(safeKey, durationMs, frames)
                }
            }
        } catch (e: Exception) {
            Log.e("LightshowRepo", "Error fetching lightshow from PocketBase for $safeKey", e)
        }
        return@withContext null
    }

    suspend fun uploadLightshow(sequence: SongVisualSequence) = withContext(Dispatchers.IO) {
        val safeKey = sanitizeKey(sequence.songKey)
        if (safeKey.isEmpty()) return@withContext
        try {
            val existing = fetchLightshow(safeKey)
            if (existing != null && existing.frames.isNotEmpty()) {
                Log.d("LightshowRepo", "Record $safeKey already exists on PocketBase, skipping upload")
                return@withContext
            }

            val jsonFrames = gson.toJson(sequence.frames)
            
            val payload = JsonObject().apply {
                addProperty("songKey", safeKey)
                addProperty("durationMs", sequence.durationMs)
                addProperty("sequenceData", jsonFrames)
                addProperty("frames", jsonFrames)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "$baseUrl/api/collections/$collectionName/records"

            Log.d("LightshowRepo", "Uploading lightshow to PocketBase ($url) for key: $safeKey")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Glyphix-Android/1.0")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("LightshowRepo", "PocketBase upload successful for $safeKey (HTTP ${response.code})")
                } else {
                    val errBody = response.body?.string()
                    Log.e("LightshowRepo", "PocketBase upload failed for $safeKey (HTTP ${response.code}): $errBody")
                }
            }
        } catch (e: Exception) {
            Log.e("LightshowRepo", "Failed to upload lightshow to PocketBase for $safeKey", e)
        }
    }
}
