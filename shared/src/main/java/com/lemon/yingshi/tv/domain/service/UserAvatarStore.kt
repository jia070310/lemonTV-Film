package com.lemon.yingshi.tv.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class UserAvatarStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val avatarDir: File
        get() = File(context.filesDir, "user_profile").also { it.mkdirs() }

    private val avatarTargetFile: File
        get() = File(avatarDir, AVATAR_FILE_NAME)

    fun getAvatarFile(): File? = avatarTargetFile.takeIf { it.isFile && it.length() > 0L }

    fun hasCustomAvatar(): Boolean = getAvatarFile() != null

    fun saveFromUri(uri: Uri): Boolean {
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return false

        if (decoded.width <= 0 || decoded.height <= 0) {
            decoded.recycle()
            return false
        }

        val square = cropCenterSquare(decoded)
        val resized = resizeSquare(square, TARGET_SIZE)
        if (square !== decoded && !square.isRecycled) {
            square.recycle()
        }
        if (decoded !== square && decoded !== resized && !decoded.isRecycled) {
            decoded.recycle()
        }

        return try {
            FileOutputStream(avatarTargetFile).use { output ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save user avatar", e)
            avatarTargetFile.delete()
            false
        } finally {
            if (!resized.isRecycled) {
                resized.recycle()
            }
        }
    }

    fun clear() {
        avatarTargetFile.delete()
    }

    private fun cropCenterSquare(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        if (size <= 0) return source
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2
        if (left == 0 && top == 0 && size == source.width && size == source.height) {
            return source
        }
        return Bitmap.createBitmap(source, left, top, size, size)
    }

    private fun resizeSquare(source: Bitmap, targetSize: Int): Bitmap {
        if (source.width == targetSize && source.height == targetSize) {
            return source
        }
        return Bitmap.createScaledBitmap(source, targetSize, targetSize, true)
    }

    companion object {
        private const val TAG = "UserAvatarStore"
        private const val AVATAR_FILE_NAME = "avatar.jpg"
        private const val TARGET_SIZE = 256
        private const val JPEG_QUALITY = 85
    }
}
