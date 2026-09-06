package com.glyphix.app.spotify

import com.google.gson.annotations.SerializedName

/**
 * Spotify OAuth Token Response
 */
data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("token_type") val tokenType: String = "",
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 3600L,
    @SerializedName("refresh_token") val refreshToken: String? = null
)

/**
 * Current User Profile
 */
data class SpotifyUserProfile(
    val id: String? = "",
    @SerializedName("display_name") val displayName: String? = null,
    val email: String? = null,
    val images: List<SpotifyImage>? = emptyList(),
    val product: String? = null, // "premium" or "free"
    val uri: String? = null
)

/**
 * Spotify Image
 */
data class SpotifyImage(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null
)

/**
 * Spotify Playlists Paged Response
 */
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylist?>? = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

/**
 * Spotify Playlist Item
 */
data class SpotifyPlaylist(
    val id: String? = "",
    val name: String? = "",
    val description: String? = null,
    val images: List<SpotifyImage>? = emptyList(),
    val uri: String? = "",
    val tracks: SpotifyPlaylistTracksRef? = null
) {
    val trackCount: Int get() = tracks?.total ?: 0
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Untitled Playlist"
    val safeUri: String get() = uri?.takeIf { it.isNotBlank() } ?: "spotify:playlist:${id.orEmpty()}"
}

data class SpotifyPlaylistTracksRef(
    val total: Int = 0,
    val href: String? = null
)

/**
 * Spotify Playlist Full Response (/v1/playlists/{id})
 */
data class SpotifyPlaylistFullResponse(
    val id: String? = "",
    val name: String? = "",
    val description: String? = null,
    val images: List<SpotifyImage>? = emptyList(),
    val uri: String? = "",
    val tracks: SpotifyPlaylistTracksResponse? = null
)

/**
 * Spotify Playlist Tracks Response
 */
data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrackWrapper?>? = emptyList(),
    val total: Int = 0
)

data class SpotifyPlaylistTrackWrapper(
    val track: SpotifyTrack? = null,
    val item: SpotifyTrack? = null
) {
    val actualTrack: SpotifyTrack?
        get() = track ?: item
}

/**
 * Spotify Track
 */
data class SpotifyTrack(
    val id: String? = "",
    val name: String? = "",
    val uri: String? = "",
    val duration_ms: Long = 0L,
    val explicit: Boolean = false,
    val artists: List<SpotifyArtist?>? = emptyList(),
    val album: SpotifyAlbum? = null
) {
    val durationMs: Long get() = duration_ms
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Unknown Track"
    val safeUri: String get() = uri?.takeIf { it.isNotBlank() } ?: "spotify:track:${id.orEmpty()}"
    val artistNames: String get() = artists?.filterNotNull()?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val imageUrl: String? get() = album?.images?.firstOrNull()?.url
}

/**
 * Spotify Artist
 */
data class SpotifyArtist(
    val id: String? = "",
    val name: String? = "",
    val uri: String? = null
)

/**
 * Spotify Album
 */
data class SpotifyAlbum(
    val id: String? = "",
    val name: String? = "",
    val images: List<SpotifyImage>? = emptyList(),
    val uri: String? = null
)

/**
 * Search Results
 */
data class SpotifySearchResponse(
    val tracks: SpotifyTracksPaged? = null,
    val playlists: SpotifyPlaylistsResponse? = null,
    val albums: SpotifyAlbumsPaged? = null
)

data class SpotifyTracksPaged(
    val items: List<SpotifyTrack?>? = emptyList(),
    val total: Int = 0
)

data class SpotifyAlbumsPaged(
    val items: List<SpotifyAlbum?>? = emptyList(),
    val total: Int = 0
)

/**
 * Recently Played Tracks
 */
data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyRecentlyPlayedItem?>? = emptyList()
)

data class SpotifyRecentlyPlayedItem(
    val track: SpotifyTrack? = null,
    val played_at: String? = null
)

/**
 * Current Playback State
 */
data class SpotifyPlaybackState(
    val is_playing: Boolean = false,
    val progress_ms: Long = 0,
    val item: SpotifyTrack? = null,
    val shuffle_state: Boolean = false,
    val repeat_state: String = "off",
    val device: SpotifyDevice? = null
)

data class SpotifyDevice(
    val id: String? = null,
    val name: String? = null,
    val is_active: Boolean = false,
    val is_restricted: Boolean = false,
    val type: String? = null,
    val volume_percent: Int? = 100
)

data class SpotifyDevicesResponse(
    val devices: List<SpotifyDevice>? = emptyList()
)

/**
 * Playback Request Body
 */
data class SpotifyPlayRequestBody(
    val context_uri: String? = null,
    val uris: List<String>? = null,
    val offset: Map<String, Any>? = null,
    val position_ms: Long? = null
)

/**
 * Transfer Playback Request Body
 */
data class SpotifyTransferRequestBody(
    @SerializedName("device_ids") val device_ids: List<String>,
    @SerializedName("play") val play: Boolean = true
)

