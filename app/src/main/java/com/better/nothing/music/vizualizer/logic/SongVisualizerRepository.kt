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
 * Manages fetching, caching, generating, and uploading song visualizer timelines.
 */
class SongVisualizerRepository(private val context: Context) {

    private val gson = Gson()
    private val memoryCache = LruCache<String, SongVisualSequence>(20)
    private val firebaseRef by lazy {
        try {
            FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("song_visuals")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Database", e)
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

        // 3. Query Firebase Cloud
        val cloudSeq = fetchFromFirebase(key)
        if (cloudSeq != null) {
            Log.d(TAG, "Fetched sequence from Firebase for $key")
            saveToDisk(cloudSeq, persistent = false)
            memoryCache.put(key, cloudSeq)
            return@withContext cloudSeq
        }

        // 4. Fallback: Generate locally using Procedural Engine
        Log.d(TAG, "Generating procedural sequence for $key")
        val generatedSeq = ProceduralLightshowEngine.generateSequence(metadata)
        saveToDisk(generatedSeq, persistent = false)
        memoryCache.put(key, generatedSeq)

        // 5. Asynchronously upload generated sequence to Firebase for other devices to use
        uploadToFirebase(generatedSeq)

        return@withContext generatedSeq
    }

    private fun loadFromDisk(key: String): SongVisualSequence? {
        val persistentFile = File(persistentDir, "$key.json")
        if (persistentFile.exists()) {
            return runCatching { gson.fromJson(persistentFile.readText(), SongVisualSequence::class.java) }.getOrNull()
        }
        val tempFile = File(tempCacheDir, "$key.json")
        if (tempFile.exists()) {
            return runCatching { gson.fromJson(tempFile.readText(), SongVisualSequence::class.java) }.getOrNull()
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

    private suspend fun fetchFromFirebase(key: String): SongVisualSequence? {
        val ref = firebaseRef ?: return null
        return try {
            val snapshot = ref.child(key).get().await()
            if (snapshot.exists()) {
                snapshot.getValue(SongVisualSequence::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase fetch failed for key: $key", e)
            null
        }
    }

    private fun uploadToFirebase(sequence: SongVisualSequence) {
        val ref = firebaseRef ?: return
        ref.child(sequence.songKey).setValue(sequence)
            .addOnSuccessListener { Log.d(TAG, "Uploaded sequence to Firebase: ${sequence.songKey}") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to upload sequence to Firebase: ${sequence.songKey}", e) }
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
