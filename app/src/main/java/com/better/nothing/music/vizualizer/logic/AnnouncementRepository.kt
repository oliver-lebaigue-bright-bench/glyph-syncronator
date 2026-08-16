package com.better.nothing.music.vizualizer.logic

import android.util.Log
import com.better.nothing.music.vizualizer.model.Announcement
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AnnouncementRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("announcements")

    fun getLatestAnnouncement(): Flow<Announcement?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val announcement = snapshot.child("latest").getValue(Announcement::class.java)
                    trySend(announcement)
                } catch (e: Exception) {
                    Log.e("AnnouncementRepo", "Error parsing latest announcement", e)
                    // Don't close the flow, just wait for the next update or return null
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AnnouncementRepo", "onCancelled: ${error.message}")
                if (!isClosedForSend) {
                    close(error.toException())
                }
            }
        }
        try {
            database.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("AnnouncementRepo", "Failed to add listener", e)
            close(e)
        }
        awaitClose { 
            try {
                database.removeEventListener(listener)
            } catch (e: Exception) {
                Log.e("AnnouncementRepo", "Error removing listener", e)
            }
        }
    }

    fun getAnnouncementHistory(): Flow<List<Announcement>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val history = snapshot.child("history").children.mapNotNull {
                        try {
                            it.getValue(Announcement::class.java)
                        } catch (e: Exception) {
                            Log.e("AnnouncementRepo", "Error parsing announcement in history: ${it.key}", e)
                            null
                        }
                    }.sortedByDescending { it.timestamp }
                    trySend(history)
                } catch (e: Exception) {
                    Log.e("AnnouncementRepo", "Error processing announcement history", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AnnouncementRepo", "History onCancelled: ${error.message}")
                if (!isClosedForSend) {
                    close(error.toException())
                }
            }
        }
        try {
            database.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("AnnouncementRepo", "Failed to add history listener", e)
            close(e)
        }
        awaitClose { 
            try {
                database.removeEventListener(listener)
            } catch (e: Exception) {
                Log.e("AnnouncementRepo", "Error removing history listener", e)
            }
        }
    }

    suspend fun postAnnouncement(announcement: Announcement) {
        try {
            Log.d("AnnouncementRepo", "Posting announcement: ${announcement.id}")
            database.child("latest").setValue(announcement).await()
            // Also store in history
            database.child("history").child(announcement.id.toString()).setValue(announcement).await()
            Log.d("AnnouncementRepo", "Announcement posted successfully")
        } catch (e: Exception) {
            Log.e("AnnouncementRepo", "Post announcement failed for ID: ${announcement.id}", e)
            throw e
        }
    }
}
