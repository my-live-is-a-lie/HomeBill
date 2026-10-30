package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps the scanned bill photo together with the bill. The image is stored privately in the app as
 * `files/bill_images/<billId>.jpg` (so no Firestore schema or security-rules change is needed) and
 * can be exported to the phone gallery under Pictures/HomeBill.
 */
object BillImageStore {
  private const val DIR_NAME = "bill_images"
  private const val GALLERY_FOLDER = "HomeBill"
  private const val MAX_EDGE_PX = 2200
  private const val JPEG_QUALITY = 90

  fun imageFile(context: Context, billId: String): File =
    File(File(context.applicationContext.filesDir, DIR_NAME), "$billId.jpg")

  fun hasImage(context: Context, billId: String): Boolean {
    if (billId.isBlank()) return false
    val file = imageFile(context, billId)
    return file.exists() && file.length() > 0L
  }

  /** Copies the scanned image (downscaled, EXIF-rotated) into private app storage. */
  fun saveForBill(context: Context, source: Uri, billId: String): Boolean {
    if (billId.isBlank() || source == Uri.EMPTY) return false
    return try {
      val resolver = context.applicationContext.contentResolver
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      val longest = maxOf(bounds.outWidth, bounds.outHeight)
      if (longest <= 0) return false

      var sample = 1
      while (longest / (sample * 2) >= MAX_EDGE_PX) sample *= 2
      val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
      val decoded =
        resolver.openInputStream(source)?.use {
          BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return false

      val rotationDegrees =
        try {
          resolver.openInputStream(source)?.use { stream ->
            when (
              ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
              ExifInterface.ORIENTATION_ROTATE_90 -> 90f
              ExifInterface.ORIENTATION_ROTATE_180 -> 180f
              ExifInterface.ORIENTATION_ROTATE_270 -> 270f
              else -> 0f
            }
          } ?: 0f
        } catch (e: Exception) {
          0f
        }

      var bitmap = decoded
      if (rotationDegrees != 0f) {
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
      }
      val maxSide = maxOf(bitmap.width, bitmap.height)
      if (maxSide > MAX_EDGE_PX) {
        val ratio = MAX_EDGE_PX.toFloat() / maxSide
        bitmap =
          Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
          )
      }

      val target = imageFile(context, billId)
      target.parentFile?.mkdirs()
      val written =
        FileOutputStream(target).use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
      if (!written) target.delete()
      written
    } catch (e: Exception) {
      false
    }
  }

  /** Deletes the stored image and its cached replica layout (if any). */
  fun deleteForBill(context: Context, billId: String) {
    if (billId.isBlank()) return
    try {
      val image = imageFile(context, billId)
      image.delete()
      File(image.parentFile, "$billId.layout.json").delete()
    } catch (e: Exception) {
      // Ignore: best-effort cleanup.
    }
  }

  /** Exports the stored bill image to the phone gallery (Pictures/HomeBill). */
  fun saveToGallery(context: Context, billId: String, label: String): Boolean {
    val source = imageFile(context, billId)
    if (!source.exists() || source.length() == 0L) return false
    val appContext = context.applicationContext
    val safeLabel =
      label
        .trim()
        .replace(' ', '_')
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(40)
        .ifBlank { "bill" }
    val fileName = "HomeBill_${safeLabel}_${System.currentTimeMillis()}.jpg"

    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = appContext.contentResolver
        val values =
          ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
              MediaStore.Images.Media.RELATIVE_PATH,
              "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
          }
        val uri =
          resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val copied =
          resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
            true
          } ?: false
        if (!copied) {
          resolver.delete(uri, null, null)
          return false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
      } else {
        @Suppress("DEPRECATION")
        val picturesDir =
          File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            GALLERY_FOLDER,
          )
        if (!picturesDir.exists() && !picturesDir.mkdirs()) return false
        val target = File(picturesDir, fileName)
        source.inputStream().use { input ->
          FileOutputStream(target).use { out -> input.copyTo(out) }
        }
        MediaScannerConnection.scanFile(
          appContext,
          arrayOf(target.absolutePath),
          arrayOf("image/jpeg"),
          null,
        )
        true
      }
    } catch (e: Exception) {
      false
    }
  }
}
