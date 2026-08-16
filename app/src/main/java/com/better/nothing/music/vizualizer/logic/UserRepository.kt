package com.better.nothing.music.vizualizer.logic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.better.nothing.music.vizualizer.model.UserProfile
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class UserRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("users")

    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val snapshot = database.child(userId).get().await()
            snapshot.getValue(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching profile", e)
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        try {
            database.child(profile.userId).setValue(profile).await()
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving profile", e)
            throw e
        }
    }

    suspend fun uploadAvatarFromResource(userId: String, resourceId: Int, context: android.content.Context): String = withContext(Dispatchers.IO) {
        try {
            Log.d("UserRepository", "Encoding resource $resourceId to Base64")
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                ?: throw Exception("Could not decode resource")
            encodeBitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.e("UserRepository", "Resource encoding failed", e)
            throw e
        }
    }

    suspend fun uploadProfilePicture(userId: String, imageUri: Uri, context: android.content.Context): String = withContext(Dispatchers.IO) {
        try {
            Log.d("UserRepository", "Encoding Uri $imageUri to Base64")
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw Exception("Could not open input stream")
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw Exception("Could not decode stream")
            encodeBitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.e("UserRepository", "Uri encoding failed", e)
            throw e
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        // 1. Scale down to 200x200 to keep RTDB lightweight
        val scaled = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
        
        // 2. Compress to high-quality JPEG
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        
        // 3. Convert to Base64 Data URL
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}
