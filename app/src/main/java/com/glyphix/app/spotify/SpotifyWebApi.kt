package com.glyphix.app.spotify

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Spotify Web API endpoints.
 */
interface SpotifyWebApi {

    @GET("v1/me")
    suspend fun getCurrentUserProfile(): Response<SpotifyUserProfile>

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistsResponse>

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistItems(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistTracksResponse>

    @GET("v1/playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Path("playlist_id") playlistId: String
    ): Response<SpotifyPlaylistFullResponse>

    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistTracksResponse>

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 10
    ): Response<SpotifySearchResponse>

    @GET("v1/me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Query("limit") limit: Int = 30
    ): Response<SpotifyRecentlyPlayedResponse>

    @GET("v1/me/top/tracks")
    suspend fun getTopTracks(
        @Query("limit") limit: Int = 30,
        @Query("time_range") timeRange: String = "short_term"
    ): Response<SpotifyTracksPaged>

    @GET("v1/me/player")
    suspend fun getPlaybackState(): Response<SpotifyPlaybackState>

    @GET("v1/me/player/devices")
    suspend fun getDevices(): Response<SpotifyDevicesResponse>

    @PUT("v1/me/player/play")
    suspend fun startPlayback(
        @Query("device_id") deviceId: String? = null,
        @Body body: SpotifyPlayRequestBody = SpotifyPlayRequestBody()
    ): Response<Unit>

    @PUT("v1/me/player/pause")
    suspend fun pausePlayback(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @PUT("v1/me/player")
    suspend fun transferPlayback(
        @Body body: SpotifyTransferRequestBody
    ): Response<Unit>

    @POST("v1/me/player/next")
    suspend fun skipToNext(): Response<Unit>

    @POST("v1/me/player/previous")
    suspend fun skipToPrevious(): Response<Unit>

    @PUT("v1/me/player/seek")
    suspend fun seekToPosition(
        @Query("position_ms") positionMs: Long
    ): Response<Unit>

    @PUT("v1/me/player/shuffle")
    suspend fun setShuffle(
        @Query("state") state: Boolean
    ): Response<Unit>

    @PUT("v1/me/player/repeat")
    suspend fun setRepeatMode(
        @Query("state") state: String // "track", "context", "off"
    ): Response<Unit>
}
