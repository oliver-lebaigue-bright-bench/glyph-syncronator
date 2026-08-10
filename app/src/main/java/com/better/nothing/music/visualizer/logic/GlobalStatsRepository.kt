package com.better.nothing.music.visualizer.logic

import android.util.Log
import com.better.nothing.music.visualizer.model.GlobalStats
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GlobalStatsRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("global_stats")

    fun getGlobalStats(): Flow<GlobalStats> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val stats = snapshot.getValue(GlobalStats::class.java) ?: GlobalStats()
                    trySend(stats)
                } catch (e: Exception) {
                    Log.e("GlobalStatsRepo", "Error parsing global stats", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GlobalStatsRepo", "onCancelled: ${error.message}")
                close(error.toException())
            }
        }
        database.addValueEventListener(listener)
        awaitClose { database.removeEventListener(listener) }
    }

    suspend fun incrementStats(
        timeMs: Long = 0,
        activeMs: Long = 0,
        idleMs: Long = 0,
        glyphMs: Long = 0,
        hapticMs: Long = 0,
        flashlightMs: Long = 0,
        sessions: Long = 0,
        beats: Long = 0
    ) {
        val updates = mutableMapOf<String, Any>()
        if (timeMs != 0L) updates["totalVisualizedTimeMs"] = ServerValue.increment(timeMs)
        if (activeMs != 0L) updates["totalActiveTimeMs"] = ServerValue.increment(activeMs)
        if (idleMs != 0L) updates["totalIdleTimeMs"] = ServerValue.increment(idleMs)
        if (glyphMs != 0L) updates["totalGlyphTimeMs"] = ServerValue.increment(glyphMs)
        if (hapticMs != 0L) updates["totalHapticTimeMs"] = ServerValue.increment(hapticMs)
        if (flashlightMs != 0L) updates["totalFlashlightTimeMs"] = ServerValue.increment(flashlightMs)
        if (sessions != 0L) updates["totalSessions"] = ServerValue.increment(sessions)
        if (beats != 0L) updates["totalBeatsDetected"] = ServerValue.increment(beats)

        if (updates.isNotEmpty()) {
            try {
                database.updateChildren(updates).await()
            } catch (e: Exception) {
                Log.e("GlobalStatsRepo", "Failed to increment global stats", e)
            }
        }
    }
    
    suspend fun incrementUserCount() {
        try {
            database.child("userCount").setValue(ServerValue.increment(1)).await()
        } catch (e: Exception) {
            Log.e("GlobalStatsRepo", "Failed to increment user count", e)
        }
    }
}
