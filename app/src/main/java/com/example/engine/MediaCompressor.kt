package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class CompressedMediaResult(
    val mediaType: String,
    val localPath: String,
    val thumbPath: String,
    val contentHash: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val syncId: String
)

/**
 * Safe media import for baby memories.
 * Copies source bytes to disk first, decodes from file (not stream), verifies output,
 * and stores high-quality JPEG for maximum device compatibility.
 */
object MediaCompressor {
    private const val TAG = "MediaCompressor"
    private const val MAX_LONG_EDGE = 2560
    private const val THUMB_LONG_EDGE = 480
    private const val PHOTO_QUALITY = 95
    const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L

    fun memoriesDir(context: Context): File =
        File(context.filesDir, "memories").also { if (!it.exists()) it.mkdirs() }

    fun thumbsDir(context: Context): File =
        File(memoriesDir(context), "thumbs").also { if (!it.exists()) it.mkdirs() }

    fun compressPhotoFromUri(context: Context, uri: Uri, syncId: String = UUID.randomUUID().toString()): CompressedMediaResult? {
        val staging = File(memoriesDir(context), "$syncId.import")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { buffered ->
                    FileOutputStream(staging).use { output -> buffered.copyTo(output) }
                }
            } ?: return null
            if (staging.length() == 0L) {
                staging.delete()
                return null
            }
            processImportedPhotoFile(context, staging, syncId).also { staging.delete() }
        } catch (e: Exception) {
            staging.delete()
            Log.e(TAG, "compressPhotoFromUri failed", e)
            null
        }
    }

    fun compressPhotoFromFile(context: Context, file: File, syncId: String = UUID.randomUUID().toString()): CompressedMediaResult? {
        return try {
            processImportedPhotoFile(context, file, syncId)
        } catch (e: Exception) {
            Log.e(TAG, "compressPhotoFromFile failed", e)
            null
        }
    }

    fun importVideoFromUri(context: Context, uri: Uri, syncId: String = UUID.randomUUID().toString()): CompressedMediaResult? {
        return try {
            val outFile = File(memoriesDir(context), "$syncId.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { buffered ->
                    FileOutputStream(outFile).use { output -> buffered.copyTo(output) }
                }
            } ?: return null
            if (outFile.length() > MAX_VIDEO_BYTES) {
                outFile.delete()
                Log.w(TAG, "Video exceeds ${MAX_VIDEO_BYTES / (1024 * 1024)}MB Nearby limit")
                return null
            }
            val thumb = createVideoPlaceholderThumb(context, syncId)
            val hash = sha256File(outFile)
            CompressedMediaResult(
                mediaType = com.example.data.model.MediaTypes.VIDEO,
                localPath = outFile.absolutePath,
                thumbPath = thumb?.absolutePath.orEmpty(),
                contentHash = hash,
                fileSizeBytes = outFile.length(),
                mimeType = "video/mp4",
                syncId = syncId
            )
        } catch (e: Exception) {
            Log.e(TAG, "importVideoFromUri failed", e)
            null
        }
    }

    /** Returns true when [file] exists and Android can decode its dimensions. */
    fun isValidImageFile(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun processImportedPhotoFile(context: Context, sourceFile: File, syncId: String): CompressedMediaResult? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "Unreadable image: ${sourceFile.name}")
            return null
        }

        val decoded = decodeSampledBitmap(sourceFile.absolutePath, MAX_LONG_EDGE) ?: run {
            Log.e(TAG, "Failed to decode ${sourceFile.name}")
            return null
        }
        val oriented = applyExifOrientation(sourceFile, decoded)
        return compressBitmap(context, oriented, syncId)
    }

    private fun compressBitmap(context: Context, bitmap: Bitmap, syncId: String): CompressedMediaResult? {
        val scaled = scaleToMaxEdge(bitmap, MAX_LONG_EDGE)
        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()

        val outFile = File(memoriesDir(context), "$syncId.jpg")
        FileOutputStream(outFile).use { fos ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, fos)) {
                Log.e(TAG, "JPEG compress failed for $syncId")
                outFile.delete()
                if (!scaled.isRecycled) scaled.recycle()
                return null
            }
        }

        if (!isValidImageFile(outFile)) {
            Log.e(TAG, "Saved photo failed validation for $syncId")
            outFile.delete()
            if (!scaled.isRecycled) scaled.recycle()
            return null
        }

        val thumbBmp = scaleToMaxEdge(scaled, THUMB_LONG_EDGE)
        val thumbFile = File(thumbsDir(context), "$syncId.jpg")
        FileOutputStream(thumbFile).use { fos ->
            thumbBmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        if (thumbBmp !== scaled && !thumbBmp.isRecycled) thumbBmp.recycle()
        if (!scaled.isRecycled) scaled.recycle()

        return CompressedMediaResult(
            mediaType = com.example.data.model.MediaTypes.PHOTO,
            localPath = outFile.absolutePath,
            thumbPath = thumbFile.absolutePath,
            contentHash = sha256File(outFile),
            fileSizeBytes = outFile.length(),
            mimeType = "image/jpeg",
            syncId = syncId
        )
    }

    fun createThumbFromExisting(context: Context, sourcePath: String, syncId: String): String {
        return try {
            if (!isValidImageFile(File(sourcePath))) return ""
            val bmp = decodeSampledBitmap(sourcePath, THUMB_LONG_EDGE * 2) ?: return ""
            val thumb = scaleToMaxEdge(bmp, THUMB_LONG_EDGE)
            if (thumb !== bmp && !bmp.isRecycled) bmp.recycle()
            val thumbFile = File(thumbsDir(context), "$syncId.jpg")
            FileOutputStream(thumbFile).use { fos ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            if (!thumb.isRecycled) thumb.recycle()
            thumbFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "createThumbFromExisting failed", e)
            ""
        }
    }

    private fun decodeSampledBitmap(path: String, maxLongEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxLongEdge) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun createVideoPlaceholderThumb(context: Context, syncId: String): File? {
        return try {
            val bmp = Bitmap.createBitmap(THUMB_LONG_EDGE, THUMB_LONG_EDGE * 3 / 4, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0xFF37474F.toInt())
            val thumbFile = File(thumbsDir(context), "$syncId.jpg")
            FileOutputStream(thumbFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            }
            bmp.recycle()
            thumbFile
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxEdge) return source
        val scale = maxEdge.toFloat() / longEdge
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            rotateForExif(bitmap, exif)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun rotateForExif(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } > 0) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
