package com.better.nothing.music.vizualizer.logic

import android.util.Log
import com.better.nothing.music.vizualizer.model.CommunityPreset
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommunityRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("community_presets")

    fun getPresets(): Flow<List<CommunityPreset>> = callbackFlow {
        Log.d("CommunityRepo", "Starting getPresets flow")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    Log.d("CommunityRepo", "onDataChange: ${snapshot.childrenCount} items")
                    val presets = snapshot.children.mapNotNull { 
                        try {
                            it.getValue(CommunityPreset::class.java)?.copy(id = it.key ?: "") 
                        } catch (e: Exception) {
                            Log.e("CommunityRepo", "Error parsing preset: ${it.key}", e)
                            null
                        }
                    }
                    trySend(presets.sortedByDescending { it.timestamp })
                } catch (e: Exception) {
                    Log.e("CommunityRepo", "Error processing presets snapshot", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommunityRepo", "onCancelled: ${error.message}")
                if (!isClosedForSend) {
                    close(error.toException())
                }
            }
        }
        try {
            database.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Failed to add presets listener", e)
            close(e)
        }
        awaitClose { 
            Log.d("CommunityRepo", "Closing getPresets flow")
            try {
                database.removeEventListener(listener)
            } catch (e: Exception) {
                Log.e("CommunityRepo", "Error removing presets listener", e)
            }
        }
    }

    suspend fun uploadPreset(preset: CommunityPreset) {
        try {
            val key = database.push().key ?: throw Exception("Could not generate key")
            Log.d("CommunityRepo", "Uploading preset with key: $key")
            database.child(key).setValue(preset.copy(id = key)).await()
            Log.d("CommunityRepo", "Upload successful")
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Upload failed", e)
            throw e
        }
    }

    suspend fun deletePreset(presetId: String) {
        try {
            Log.d("CommunityRepo", "Deleting preset: $presetId")
            database.child(presetId).removeValue().await()
            Log.d("CommunityRepo", "Delete successful")
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Delete failed", e)
            throw e
        }
    }
    
    suspend fun incrementDownloadCount(presetId: String) {
        try {
            Log.d("CommunityRepo", "Incrementing download count for: $presetId")
            val ref = database.child(presetId).child("downloads")
            val snapshot = ref.get().await()
            val current = snapshot.getValue(Int::class.java) ?: 0
            ref.setValue(current + 1).await()
            Log.d("CommunityRepo", "Download count incremented for: $presetId")
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Failed to increment download count for: $presetId", e)
            // We don't necessarily want to crash the app if download count fails
        }
    }
}
