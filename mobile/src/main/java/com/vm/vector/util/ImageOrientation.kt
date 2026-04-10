package com.vm.vector.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Decodes JPEG bytes, applies EXIF orientation so the image is stored in display orientation,
 * and re-encodes to JPEG. This prevents collection images from being saved rotated 90° (common
 * when the camera stores pixels in sensor orientation and sets EXIF orientation instead).
 *
 * @param inputStream stream of JPEG bytes (e.g. from ContentResolver.openInputStream(uri))
 * @return correctly oriented JPEG bytes, or null on failure
 */
fun applyExifOrientationToJpegStream(inputStream: InputStream): ByteArray? {
    val bytes = inputStream.readBytes()
    return applyExifOrientationToJpegBytes(bytes)
}

/**
 * Decodes JPEG bytes, applies EXIF orientation, and re-encodes to JPEG.
 *
 * @param bytes raw JPEG bytes from camera or file
 * @return correctly oriented JPEG bytes, or null on failure
 */
fun applyExifOrientationToJpegBytes(bytes: ByteArray): ByteArray? {
    if (bytes.isEmpty()) return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val orientation = try {
        java.io.ByteArrayInputStream(bytes).use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val degrees = exifOrientationToDegrees(orientation)
    val rotated = if (degrees != 0) {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    if (!rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)) return null
    if (rotated != bitmap) rotated.recycle()
    if (bitmap != rotated) bitmap.recycle()
    return out.toByteArray()
}

private fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}
