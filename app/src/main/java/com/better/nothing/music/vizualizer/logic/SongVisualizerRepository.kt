package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.better.nothing.music.vizualizer.model.SongMetadata
import com.better.nothing.music.vizualizer.model.SongVisualSequence
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages fetching, caching, generating, and uploading song visualizer timelines
 * using PocketBase (Homeserver) as Primary, Firebase as Backup, and local generation as Fallback.
 */
class SongVisualizerRepository(private val context: Context) {

    private val gson = Gson()
    private val memoryCache = LruCache<String, SongVisualSequence>(20)
    private val pocketBaseRepo = PocketBaseRepository(context)

    private val firebaseRef by lazy {
        try {
            FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("song_visuals")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Database backup", e)
            null
        }
    }

    private val tempCacheDir: File
        get() = File(context.cacheDir, "song_visuals").apply { if (!exists()) mkdirs() }

    private val persistentDir: File
        get() = File(context.filesDir, "song_visuals").apply { if (!exists()) mkdirs() }

    suspend fun getOrGenerateSequence(metadata: SongMetadata): SongVisualSequence = withContext(Dispatchers.IO) {
        val key = metadata.normalizedKey

        // 1. Check RAM Cache
        memoryCache.get(key)?.let {
            Log.d(TAG, "Loaded sequence from RAM cache for $key")
            return@withContext it
        }

        // 2. Check Disk Cache (Persistent & Temp)
        loadFromDisk(key)?.let {
            memoryCache.put(key, it)
            Log.d(TAG, "Loaded sequence from Disk cache for $key")
            return@withContext it
        }

        // 3. Primary Cloud: PocketBase Homeserver
        val pbSeq = fetchFromPocketBase(key)
        if (pbSeq != null) {
            Log.d(TAG, "Fetched sequence from PocketBase Homeserver for $key")
            saveToDisk(pbSeq, persistent = false)
            memoryCache.put(key, pbSeq)
            return@withContext pbSeq
        }

        // 4. Backup Cloud: Firebase Database (Power Outage / Homeserver Offline Fallback)
        val fbSeq = fetchFromFirebaseBackup(key)
        if (fbSeq != null) {
            Log.d(TAG, "Fetched sequence from Firebase Backup for $key")
            saveToDisk(fbSeq, persistent = false)
            memoryCache.put(key, fbSeq)
            return@withContext fbSeq
        }

        // 5. Offline Fallback: Generate locally using Procedural Engine
        Log.d(TAG, "All cloud servers offline or song missing; generating procedural sequence for $key")
        val generatedSeq = ProceduralLightshowEngine.generateSequence(metadata)
        saveToDisk(generatedSeq, persistent = false)
        memoryCache.put(key, generatedSeq)

        // 6. Non-blocking upload to Homeserver
        uploadToPocketBase(generatedSeq)

        return@withContext generatedSeq
    }

    private fun loadFromDisk(key: String): SongVisualSequence? {
        val persistentFile = File(persistentDir, "$key.json")
        if (persistentFile.exists()) {
            return try { gson.fromJson(persistentFile.readText(), SongVisualSequence::class.java) } catch (e: Exception) { null }
        }
        val tempFile = File(tempCacheDir, "$key.json")
        if (tempFile.exists()) {
            return try { gson.fromJson(tempFile.readText(), SongVisualSequence::class.java) } catch (e: Exception) { null }
        }
        return null
    }

    private fun saveToDisk(sequence: SongVisualSequence, persistent: Boolean) {
        val targetDir = if (persistent) persistentDir else tempCacheDir
        val file = File(targetDir, "${sequence.songKey}.json")
        runCatching {
            file.writeText(gson.toJson(sequence))
        }.onFailure {
            Log.e(TAG, "Failed to save sequence to disk: ${sequence.songKey}", it)
        }
    }

    private suspend fun fetchFromPocketBase(key: String): SongVisualSequence? {
        return try {
            pocketBaseRepo.getSongVisualSequence(key)
        } catch (e: Exception) {
            Log.w(TAG, "PocketBase fetch exception: ${e.message}")
            null
        }
    }

    private suspend fun fetchFromFirebaseBackup(key: String): SongVisualSequence? {
        val ref = firebaseRef ?: return null
        return try {
            val snapshot = ref.child(key).get().await()
            if (snapshot.exists()) {
                snapshot.getValue(SongVisualSequence::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase backup fetch exception: ${e.message}")
            null
        }
    }

    private suspend fun uploadToPocketBase(sequence: SongVisualSequence) {
        try {
            pocketBaseRepo.uploadSongVisualSequence(sequence)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload to PocketBase: ${e.message}")
        }
    }

    /**
     * Persistently saves a song sequence to filesDir so it is never evicted by OS cache cleanup.
     */
    fun saveToPersistentStorage(sequence: SongVisualSequence) {
        saveToDisk(sequence, persistent = true)
    }

    companion object {
        private const val TAG = "SongVisualizerRepo"
    }
}
