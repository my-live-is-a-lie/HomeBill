package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** True when the user has already allowed the app to use the microphone. */
fun hasMicrophonePermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
    PackageManager.PERMISSION_GRANTED

private fun joinText(a: String, b: String): String =
  when {
    a.isBlank() -> b
    b.isBlank() -> a
    else -> a.trimEnd() + " " + b
  }

/**
 * Live voice dictation inside the app (no system dialog).
 *
 * - [start] begins listening; every partial result is sent to the `onUpdate` callback as the full
 *   text (the text that was there before + everything said so far), so the input box fills in
 *   word by word while the user speaks.
 * - Dictation keeps going across pauses: when the recognizer finishes a sentence the text is kept
 *   and listening restarts, until the user calls [stop] (second tap on the mic) or about three
 *   silent rounds in a row pass.
 * - [stop] ends listening and keeps what was said; [cancel] ends it silently without callbacks.
 *
 * All calls must be made on the main thread.
 */
@Stable
class VoiceDictation internal constructor(context: Context) {
  private val appContext = context.applicationContext
  private val handler = Handler(Looper.getMainLooper())

  /** True from [start] until listening has fully ended. */
  var isListening by mutableStateOf(false)
    private set

  /** Loudness of the voice, 0f..1f; drives the pulsing mic animation. */
  var level by mutableFloatStateOf(0f)
    private set

  private var recognizer: SpeechRecognizer? = null
  private var updateCallback: (String) -> Unit = {}
  private var finishedCallback: (String) -> Unit = {}
  private var errorCallback: (String) -> Unit = {}

  private var committed = "" // text before dictation + every sentence finished so far
  private var displayed = "" // what the input box currently shows
  private var userStopped = false
  private var emptyRounds = 0
  private var restartPending = false

  private val stopSafety = Runnable { if (isListening) finish() }
  private val restartRunnable =
    Runnable {
      restartPending = false
      if (isListening && !userStopped) beginRecognition()
    }

  /**
   * Starts listening. Returns false when this device has no speech-recognition service, so the
   * caller can fall back to something else.
   */
  fun start(
    baseText: String,
    onUpdate: (String) -> Unit,
    onFinished: (String) -> Unit,
    onError: (String) -> Unit,
  ): Boolean {
    if (isListening) return true
    if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return false
    updateCallback = onUpdate
    finishedCallback = onFinished
    errorCallback = onError
    committed = baseText
    displayed = baseText
    userStopped = false
    emptyRounds = 0
    restartPending = false
    isListening = true
    beginRecognition()
    return true
  }

  /** Ends listening and keeps what was said (delivered through `onFinished`). */
  fun stop() {
    if (!isListening) return
    userStopped = true
    handler.removeCallbacks(restartRunnable)
    if (restartPending) {
      // Between two rounds nothing is being recorded, so there is nothing left to wait for.
      finish()
      return
    }
    try {
      recognizer?.stopListening()
    } catch (_: Exception) {}
    // Some devices never report back after stopListening(); do not stay stuck in "listening".
    handler.postDelayed(stopSafety, 1500)
  }

  /** Ends listening immediately, without calling any callback. */
  fun cancel() {
    if (!isListening) return
    userStopped = true
    restartPending = false
    handler.removeCallbacksAndMessages(null)
    isListening = false
    level = 0f
    destroyRecognizer()
  }

  internal fun release() {
    cancel()
    handler.removeCallbacksAndMessages(null)
  }

  private fun beginRecognition() {
    try {
      val r =
        recognizer
          ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
          }
      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
          )
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
          putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
          // Hints to wait longer before deciding the sentence is over (not every engine obeys).
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
          putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            2500L,
          )
        }
      r.startListening(intent)
    } catch (e: Exception) {
      fail("تعذر بدء الإدخال الصوتي")
    }
  }

  private fun scheduleRestart(delayMillis: Long = 150L) {
    restartPending = true
    handler.removeCallbacks(restartRunnable)
    handler.postDelayed(restartRunnable, delayMillis)
  }

  private fun destroyRecognizer() {
    val r = recognizer
    recognizer = null
    if (r != null) {
      // Destroyed after the current callback returns: destroying from inside it can crash.
      handler.post {
        try {
          r.destroy()
        } catch (_: Exception) {}
      }
    }
  }

  private fun finish() {
    if (!isListening) return
    handler.removeCallbacks(stopSafety)
    handler.removeCallbacks(restartRunnable)
    restartPending = false
    isListening = false
    level = 0f
    destroyRecognizer()
    finishedCallback(displayed)
  }

  private fun fail(message: String) {
    if (!isListening) return
    finish()
    errorCallback(message)
  }

  private val listener =
    object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}

      override fun onBeginningOfSpeech() {}

      override fun onRmsChanged(rmsdB: Float) {
        val target = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        level = level * 0.6f + target * 0.4f
      }

      override fun onBufferReceived(buffer: ByteArray?) {}

      override fun onEndOfSpeech() {}

      override fun onEvent(eventType: Int, params: Bundle?) {}

      override fun onPartialResults(partialResults: Bundle?) {
        if (!isListening) return
        val partial =
          partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (partial.isNotEmpty()) {
          displayed = joinText(committed, partial)
          updateCallback(displayed)
        }
      }

      override fun onResults(results: Bundle?) {
        if (!isListening) return
        val text =
          results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotEmpty()) {
          committed = joinText(committed, text)
          displayed = committed
          updateCallback(committed)
          emptyRounds = 0
        } else {
          emptyRounds++
        }
        if (userStopped || emptyRounds > 2) finish() else scheduleRestart()
      }

      override fun onError(error: Int) {
        if (!isListening) return
        if (userStopped) {
          finish()
          return
        }
        when (error) {
          SpeechRecognizer.ERROR_NO_MATCH,
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
          SpeechRecognizer.ERROR_CLIENT -> {
            // Nothing was heard (or the engine was busy): try again, but give up after a few
            // silent rounds in a row so the mic does not stay open forever.
            emptyRounds++
            if (emptyRounds > 2) finish() else scheduleRestart(300L)
          }
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            fail("يلزم السماح باستخدام الميكروفون للإدخال الصوتي")
          SpeechRecognizer.ERROR_NETWORK,
          SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
          SpeechRecognizer.ERROR_SERVER ->
            fail("تعذّر الاتصال بخدمة التعرف على الكلام، تحقق من الإنترنت")
          SpeechRecognizer.ERROR_AUDIO -> fail("تعذّر الوصول إلى الميكروفون")
          else -> fail("تعذّر التعرف على الكلام")
        }
      }
    }
}

/** Creates the [VoiceDictation] for this screen and releases the microphone when it leaves. */
@Composable
fun rememberVoiceDictation(): VoiceDictation {
  val context = LocalContext.current
  val dictation = remember { VoiceDictation(context) }
  DisposableEffect(dictation) { onDispose { dictation.release() } }
  return dictation
}
