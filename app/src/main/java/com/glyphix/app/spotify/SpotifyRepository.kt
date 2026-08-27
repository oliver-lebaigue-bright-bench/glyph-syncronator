package com.glyphix.app.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Repository for Spotify API operations and player state polling.
 */
class SpotifyRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SpotifyRepository"
        private const val BASE_URL = "https://api.spotify.com/"

        @Volatile
        private var INSTANCE: SpotifyRepository? = null

        fun getInstance(context: Context): SpotifyRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val authManager = SpotifyAuthManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val authInterceptor = Interceptor { chain ->
        var token = authManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            token = kotlinx.coroutines.runBlocking { authManager.refreshAccessToken() }
        }

        val request = if (!token.isNullOrEmpty()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        // If 401 Unauthorized, attempt refresh once
        if (response.code == 401) {
            response.close()
            val newToken = kotlinx.coroutines.runBlocking { authManager.refreshAccessToken() }
            if (!newToken.isNullOrEmpty()) {
                val newRequest = chain.request().newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return@Interceptor chain.proceed(newRequest)
            }
        }

        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: SpotifyWebApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyWebApi::class.java)

    // State Flows
    private val _userProfile = MutableStateFlow<SpotifyUserProfile?>(null)
    val userProfile: StateFlow<SpotifyUserProfile?> = _userProfile.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val recentlyPlayed: StateFlow<List<SpotifyTrack>> = _recentlyPlayed.asStateFlow()

    private val _topTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val topTracks: StateFlow<List<SpotifyTrack>> = _topTracks.asStateFlow()

    private val _searchTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val searchTracks: StateFlow<List<SpotifyTrack>> = _searchTracks.asStateFlow()

    private val _searchPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val searchPlaylists: StateFlow<List<SpotifyPlaylist>> = _searchPlaylists.asStateFlow()

    private val _playbackState = MutableStateFlow<SpotifyPlaybackState?>(null)
    val playbackState: StateFlow<SpotifyPlaybackState?> = _playbackState.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<SpotifyPlaylist?>(null)
    val selectedPlaylist: StateFlow<SpotifyPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<SpotifyTrack>> = _selectedPlaylistTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isPlaylistLoading = MutableStateFlow(false)
    val isPlaylistLoading: StateFlow<Boolean> = _isPlaylistLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null

    init {
        scope.launch {
            authManager.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    refreshAllData()
                    startPollingPlayback()
                } else {
                    stopPollingPlayback()
                    clearData()
                }
            }
        }
    }

    fun refreshAllData() {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val d1 = async { loadUserProfile() }
                val d2 = async { loadPlaylists() }
                val d3 = async { loadRecentlyPlayed() }
                val d4 = async { loadTopTracks() }
                val d5 = async { fetchPlaybackState() }
                awaitAll(d1, d2, d3, d4, d5)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing Spotify data", e)
                _errorMessage.value = "Failed to load Spotify data: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun loadUserProfile() {
        try {
            val response = api.getCurrentUserProfile()
            if (response.isSuccessful && response.body() != null) {
                _userProfile.value = response.body()
                Log.d(TAG, "Loaded profile: ${response.body()?.displayName} product: ${response.body()?.product}")
            } else {
                Log.w(TAG, "loadUserProfile failed: ${response.code()} error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load user profile", e)
        }
    }

    suspend fun loadPlaylists() {
        try {
            val response = api.getUserPlaylists(limit = 50)
            if (response.isSuccessful) {
                val list = response.body()?.items?.filterNotNull() ?: emptyList()
                _playlists.value = list
                Log.d(TAG, "Loaded ${list.size} playlists")
            } else {
                Log.w(TAG, "loadPlaylists failed: ${response.code()} error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load playlists", e)
        }
    }

    suspend fun loadRecentlyPlayed() {
        try {
            val response = api.getRecentlyPlayed(limit = 30)
            if (response.isSuccessful) {
                val tracks = response.body()?.items?.mapNotNull { it?.track } ?: emptyList()
                _recentlyPlayed.value = tracks
                Log.d(TAG, "Loaded ${tracks.size} recently played tracks")
            } else {
                Log.w(TAG, "loadRecentlyPlayed failed: ${response.code()} error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load recently played", e)
        }
    }

    suspend fun loadTopTracks() {
        try {
            val response = api.getTopTracks(limit = 30)
            if (response.isSuccessful) {
                val tracks = response.body()?.items?.filterNotNull() ?: emptyList()
                _topTracks.value = tracks
            } else {
                Log.w(TAG, "loadTopTracks failed: ${response.code()} error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load top tracks", e)
        }
    }

    private var searchJob: Job? = null

    fun search(query: String, debounceMs: Long = 300L) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _isSearching.value = false
            _searchTracks.value = emptyList()
            _searchPlaylists.value = emptyList()
            return
        }

        searchJob = scope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            _isSearching.value = true
            try {
                Log.d(TAG, "Executing search for: '$trimmed'")
                var foundTracks: List<SpotifyTrack> = emptyList()
                var foundPlaylists: List<SpotifyPlaylist> = emptyList()

                val trackJob = async {
                    try {
                        val trackResponse = api.search(query = trimmed, type = "track", limit = 10)
                        if (trackResponse.isSuccessful && trackResponse.body() != null) {
                            val items = trackResponse.body()?.tracks?.items?.filterNotNull()?.filter { !it.name.isNullOrBlank() } ?: emptyList()
                            if (items.isNotEmpty()) return@async items
                        } else {
                            Log.w(TAG, "Retrofit track search returned ${trackResponse.code()} error=${trackResponse.errorBody()?.string()}")
                        }

                        // Direct URL-encoded search fallback (capped at limit=10 for Dev Mode)
                        val token = authManager.getAccessToken()
                        val encodedQ = java.net.URLEncoder.encode(trimmed, "UTF-8")
                        val url = "https://api.spotify.com/v1/search?q=$encodedQ&type=track&limit=10"
                        val req = okhttp3.Request.Builder()
                            .url(url)
                            .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
                            .build()
                        val resp = okHttpClient.newCall(req).execute()
                        val bodyStr = resp.body?.string()
                        if (resp.isSuccessful && !bodyStr.isNullOrEmpty()) {
                            val parsed = com.google.gson.Gson().fromJson(bodyStr, SpotifySearchResponse::class.java)
                            parsed?.tracks?.items?.filterNotNull()?.filter { !it.name.isNullOrBlank() } ?: emptyList()
                        } else {
                            Log.w(TAG, "Direct search failed: ${resp.code} body=$bodyStr")
                            emptyList()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Track search failed", e)
                        emptyList()
                    }
                }

                val playlistJob = async {
                    try {
                        val playlistResponse = api.search(query = trimmed, type = "playlist", limit = 10)
                        if (playlistResponse.isSuccessful && playlistResponse.body() != null) {
                            playlistResponse.body()?.playlists?.items?.filterNotNull()?.filter { !it.name.isNullOrBlank() } ?: emptyList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                foundTracks = trackJob.await()
                foundPlaylists = playlistJob.await()

                _searchTracks.value = foundTracks
                _searchPlaylists.value = foundPlaylists
                Log.d(TAG, "Search '$trimmed' completed with ${foundTracks.size} tracks, ${foundPlaylists.size} playlists")
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for query: $trimmed", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    private fun parseTracksFromJson(jsonString: String): List<SpotifyTrack> {
        val list = mutableListOf<SpotifyTrack>()
        try {
            val jsonElement = com.google.gson.JsonParser.parseString(jsonString)
            if (!jsonElement.isJsonObject) return list
            val jsonObject = jsonElement.asJsonObject

            val itemsArray = when {
                jsonObject.has("items") && jsonObject.get("items").isJsonArray -> jsonObject.getAsJsonArray("items")
                jsonObject.has("tracks") && jsonObject.get("tracks").isJsonObject && jsonObject.getAsJsonObject("tracks").has("items") && jsonObject.getAsJsonObject("tracks").get("items").isJsonArray -> jsonObject.getAsJsonObject("tracks").getAsJsonArray("items")
                jsonObject.has("tracks") && jsonObject.get("tracks").isJsonArray -> jsonObject.getAsJsonArray("tracks")
                else -> null
            }
            if (itemsArray != null) {
                for (elem in itemsArray) {
                    if (!elem.isJsonObject) continue
                    val itemObj = elem.asJsonObject
                    val trackObj = when {
                        itemObj.has("track") && itemObj.get("track").isJsonObject -> itemObj.getAsJsonObject("track")
                        itemObj.has("item") && itemObj.get("item").isJsonObject -> itemObj.getAsJsonObject("item")
                        itemObj.has("name") && (itemObj.has("uri") || itemObj.has("id")) -> itemObj
                        else -> null
                    }
                    if (trackObj != null) {
                        val track = com.google.gson.Gson().fromJson(trackObj, SpotifyTrack::class.java)
                        if (track != null && !track.name.isNullOrBlank()) {
                            list.add(track)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing playlist tracks JSON", e)
        }
        return list
    }

    fun loadPlaylistTracks(playlist: SpotifyPlaylist) {
        val rawId = playlist.id ?: ""
        val cleanId = rawId.removePrefix("spotify:playlist:").split("?").firstOrNull()?.trim()
        if (cleanId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot load playlist: invalid ID '$rawId'")
            return
        }
        _selectedPlaylist.value = playlist
        _selectedPlaylistTracks.value = emptyList()
        scope.launch {
            _isPlaylistLoading.value = true
            try {
                Log.d(TAG, "Fetching tracks for playlist ID='$cleanId' (${playlist.displayName})")
                var tracks: List<SpotifyTrack> = emptyList()

                // Strategy 1: /v1/playlists/{id}/items (Official 2026 endpoint)
                val itemsResponse = api.getPlaylistItems(playlistId = cleanId, limit = 50)
                if (itemsResponse.isSuccessful && itemsResponse.body() != null) {
                    tracks = itemsResponse.body()?.items?.mapNotNull { it?.actualTrack ?: it?.track }?.filter { !it.name.isNullOrBlank() } ?: emptyList()
                    Log.d(TAG, "Strategy 1 (/items) returned ${tracks.size} tracks")
                } else {
                    Log.w(TAG, "Strategy 1 (/items) failed: ${itemsResponse.code()} error: ${itemsResponse.errorBody()?.string()}")
                }

                // Strategy 2: Direct OkHttp GET /v1/playlists/{id}/items
                if (tracks.isEmpty()) {
                    val token = authManager.getAccessToken()
                    val url = "https://api.spotify.com/v1/playlists/$cleanId/items?limit=50"
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    val bodyStr = resp.body?.string()
                    if (resp.isSuccessful && !bodyStr.isNullOrEmpty()) {
                        tracks = parseTracksFromJson(bodyStr)
                        Log.d(TAG, "Strategy 2 (direct /items) returned ${tracks.size} tracks")
                    } else {
                        Log.w(TAG, "Strategy 2 failed: ${resp.code} body=$bodyStr")
                    }
                }

                // Strategy 3: /v1/playlists/{id} full playlist object fallback
                if (tracks.isEmpty()) {
                    val token = authManager.getAccessToken()
                    val url = "https://api.spotify.com/v1/playlists/$cleanId"
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    val bodyStr = resp.body?.string()
                    if (resp.isSuccessful && !bodyStr.isNullOrEmpty()) {
                        tracks = parseTracksFromJson(bodyStr)
                        Log.d(TAG, "Strategy 3 (direct full playlist) returned ${tracks.size} tracks")
                    } else {
                        Log.w(TAG, "Strategy 3 failed: ${resp.code} body=$bodyStr")
                    }
                }

                // Strategy 4: Direct OkHttp GET /v1/playlists/{id}/tracks (legacy)
                if (tracks.isEmpty()) {
                    val token = authManager.getAccessToken()
                    val url = "https://api.spotify.com/v1/playlists/$cleanId/tracks?limit=50"
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    val bodyStr = resp.body?.string()
                    if (resp.isSuccessful && !bodyStr.isNullOrEmpty()) {
                        tracks = parseTracksFromJson(bodyStr)
                        Log.d(TAG, "Strategy 4 (direct /tracks) returned ${tracks.size} tracks")
                    } else {
                        Log.w(TAG, "Strategy 4 failed: ${resp.code} body=$bodyStr")
                    }
                }

                _selectedPlaylistTracks.value = tracks
                Log.d(TAG, "Loaded total ${tracks.size} tracks for playlist '${playlist.displayName}'")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load tracks for playlist: ${playlist.displayName}", e)
            } finally {
                _isPlaylistLoading.value = false
            }
        }
    }

    fun closePlaylistDetails() {
        _selectedPlaylist.value = null
        _selectedPlaylistTracks.value = emptyList()
    }

    // Playback Controls
    fun playTrack(trackUri: String) {
        scope.launch {
            try {
                val body = SpotifyPlayRequestBody(uris = listOf(trackUri))
                val response = api.startPlayback(body)
                if (!response.isSuccessful) {
                    Log.w(TAG, "playTrack Web API response code: ${response.code()}, falling back to native Spotify app intent")
                    launchSpotifyAppIntent(trackUri)
                } else {
                    delay(300)
                    fetchPlaybackState()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error playing track via Web API, falling back to native app intent", e)
                launchSpotifyAppIntent(trackUri)
            }
        }
    }

    fun playPlaylist(playlistUri: String, trackUri: String? = null) {
        scope.launch {
            try {
                val offset = if (!trackUri.isNullOrEmpty()) mapOf("uri" to trackUri) else null
                val body = SpotifyPlayRequestBody(context_uri = playlistUri, offset = offset)
                val response = api.startPlayback(body)
                if (!response.isSuccessful) {
                    Log.w(TAG, "playPlaylist Web API response code: ${response.code()}, falling back to native Spotify app intent")
                    launchSpotifyAppIntent(trackUri ?: playlistUri)
                } else {
                    delay(300)
                    fetchPlaybackState()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error playing playlist via Web API, falling back to native app intent", e)
                launchSpotifyAppIntent(trackUri ?: playlistUri)
            }
        }
    }

    private fun launchSpotifyAppIntent(spotifyUri: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
                setPackage("com.spotify.music")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch specific spotify package intent, falling back to general URI", e)
            try {
                val generalIntent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(generalIntent)
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause() {
        val current = _playbackState.value
        scope.launch {
            try {
                if (current?.is_playing == true) {
                    api.pausePlayback()
                } else {
                    api.startPlayback()
                }
                delay(250)
                fetchPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling play/pause", e)
            }
        }
    }

    fun skipNext() {
        scope.launch {
            try {
                api.skipToNext()
                delay(300)
                fetchPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error skipping next", e)
            }
        }
    }

    fun skipPrevious() {
        scope.launch {
            try {
                api.skipToPrevious()
                delay(300)
                fetchPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error skipping previous", e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            try {
                api.seekToPosition(positionMs)
                delay(150)
                fetchPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error seeking position: $positionMs", e)
            }
        }
    }

    fun toggleShuffle() {
        val current = _playbackState.value?.shuffle_state ?: false
        scope.launch {
            try {
                api.setShuffle(!current)
                delay(200)
                fetchPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling shuffle", e)
            }
        }
    }

    fun toggleRepeat() {
        val current = _playbackState.value?.repeat_state ?: "off"
        val nextMode = when (current) {
            "off" -> "context"
            "context" -> "track"
            else -> "off"
        }
        scope.launch {
            try {
                api.setRepeatMode(nextMode)
                delay(200)
                fetchPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling repeat", e)
            }
        }
    }

    suspend fun fetchPlaybackState() {
        try {
            val response = api.getPlaybackState()
            if (response.isSuccessful && response.body() != null) {
                _playbackState.value = response.body()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch playback state", e)
        }
    }

    private fun startPollingPlayback() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                fetchPlaybackState()
                delay(2000) // Poll every 2s while active
            }
        }
    }

    private fun stopPollingPlayback() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun clearData() {
        _userProfile.value = null
        _playlists.value = emptyList()
        _recentlyPlayed.value = emptyList()
        _topTracks.value = emptyList()
        _searchTracks.value = emptyList()
        _searchPlaylists.value = emptyList()
        _playbackState.value = null
        _selectedPlaylist.value = null
        _selectedPlaylistTracks.value = emptyList()
    }
}
