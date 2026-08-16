package com.better.nothing.music.vizualizer.logic

import android.util.Log
import com.better.nothing.music.vizualizer.model.LeaderboardEntry
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LeaderboardRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("leaderboard")

    fun getTopUsers(limit: Int = 100): Flow<List<LeaderboardEntry>> = callbackFlow {
        val query = database.orderByChild("totalTimeMs").limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val entries = snapshot.children.mapNotNull { 
                        try {
                            it.getValue(LeaderboardEntry::class.java)
                        } catch (e: Exception) {
                            Log.e("LeaderboardRepo", "Error parsing leaderboard entry", e)
                            null
                        }
                    }.reversed() // orderByChild is ascending
                    trySend(entries)
                } catch (e: Exception) {
                    Log.e("LeaderboardRepo", "Error processing leaderboard snapshot", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LeaderboardRepo", "onCancelled: ${error.message}")
                if (!isClosedForSend) {
                    close(error.toException())
                }
            }
        }
        try {
            query.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("LeaderboardRepo", "Failed to add leaderboard listener", e)
            close(e)
        }
        awaitClose { 
            try {
                query.removeEventListener(listener)
            } catch (e: Exception) {
                Log.e("LeaderboardRepo", "Error removing leaderboard listener", e)
            }
        }
    }

    suspend fun updateScore(entry: LeaderboardEntry) {
        try {
            Log.d("LeaderboardRepo", "Updating score for user: ${entry.userId}")
            database.child(entry.userId).setValue(entry).await()
            Log.d("LeaderboardRepo", "Score updated successfully")
        } catch (e: Exception) {
            Log.e("LeaderboardRepo", "Failed to update score for user: ${entry.userId}", e)
            throw e
        }
    }
}
