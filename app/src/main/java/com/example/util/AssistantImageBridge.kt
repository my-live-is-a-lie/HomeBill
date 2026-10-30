package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A bill image that the user sent from a bill to the AI assistant. */
data class AssistantImageAttachment(
  val filePath: String,
  val label: String,
)

/**
 * Small in-memory bridge between the bill screen and the AI assistant: the bill sheet attaches its
 * image and asks the app to open the assistant; the assistant screen shows the attachment and the
 * chat service sends it to Gemini together with the user's questions.
 */
object AssistantImageBridge {
  private val _attachment = MutableStateFlow<AssistantImageAttachment?>(null)
  val attachment: StateFlow<AssistantImageAttachment?> = _attachment.asStateFlow()

  // No replay: only a live request navigates, never a stale one after the screen is recreated.
  private val _openRequests =
    MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val openRequests: SharedFlow<Unit> = _openRequests.asSharedFlow()

  fun attachAndOpen(file: File, label: String) {
    _attachment.value = AssistantImageAttachment(file.absolutePath, label)
    _openRequests.tryEmit(Unit)
  }

  fun clear() {
    _attachment.value = null
  }

  /** Downscaled JPEG (Base64) of the attached image, or null if there is none. Call off the main thread. */
  fun currentAttachmentBase64(): String? {
    val current = _attachment.value ?: return null
    return try {
      val file = File(current.filePath)
      if (!file.exists() || file.length() == 0L) return null
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      val longest = maxOf(bounds.outWidth, bounds.outHeight)
      if (longest <= 0) return null
      var sample = 1
      while (longest / (sample * 2) >= 1800) sample *= 2
      val bitmap =
        BitmapFactory.decodeFile(
          file.absolutePath,
          BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
      Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
      null
    }
  }
}
