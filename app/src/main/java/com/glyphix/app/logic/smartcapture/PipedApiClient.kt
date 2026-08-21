package com.glyphix.app.logic.smartcapture

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PipedApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val instances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.lunar.icu",
        "https://piped-api.garudalinux.org"
    )

    suspend fun getBestAudioStreamUrl(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("$artist $title", "UTF-8")
        
        for (baseUrl in instances) {
            try {
                val searchUrl = "$baseUrl/search?q=$query&filter=music_songs"
                val searchRequest = Request.Builder().url(searchUrl).build()
                
                val searchResponse = client.newCall(searchRequest).execute()
                if (!searchResponse.isSuccessful) continue
                
                val searchBody = searchResponse.body?.string() ?: continue
                val searchJson = JSONObject(searchBody)
                val items = searchJson.optJSONArray("items") ?: continue
                
                if (items.length() == 0) continue
                
                val firstItem = items.getJSONObject(0)
                val urlPath = firstItem.optString("url")
                val videoId = Uri.parse("https://youtube.com$urlPath").getQueryParameter("v") ?: continue
                
                val streamUrl = "$baseUrl/streams/$videoId"
                val streamRequest = Request.Builder().url(streamUrl).build()
                
                val streamResponse = client.newCall(streamRequest).execute()
                if (!streamResponse.isSuccessful) continue
                
                val streamBody = streamResponse.body?.string() ?: continue
                val streamJson = JSONObject(streamBody)
                val audioStreams = streamJson.optJSONArray("audioStreams") ?: continue
                
                var bestUrl: String? = null
                var highestBitrate = 0
                
                for (i in 0 until audioStreams.length()) {
                    val stream = audioStreams.getJSONObject(i)
                    val bitrate = stream.optInt("bitrate", 0)
                    val url = stream.optString("url")
                    
                    if (url.isNotEmpty() && bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        bestUrl = url
                    }
                }
                
                if (bestUrl != null) {
                    return@withContext bestUrl
                }
            } catch (e: Exception) {
                Log.e("PipedApiClient", "Error fetching from $baseUrl: ${e.message}")
            }
        }
        return@withContext null
    }
}
