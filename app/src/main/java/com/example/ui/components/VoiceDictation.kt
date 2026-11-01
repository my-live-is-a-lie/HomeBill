package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** True when the user has already allowed the app to use the microphone. */
fun hasMicrophonePermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
    PackageManager.PERMISSION_GRANTED

/**
 * Common face of the two voice-dictation engines (the Gemini live one and the built-in Android
 * one), so the screen can show the same recording bar for whichever is running.
 */
interface Dictation {
  /** True from [start] until listening has fully ended. */
  val isListening: Boolean

  /** Loudness of the voice, 0f..1f. */
  val level: Float

  /** Recent loudness values (oldest first) used to draw the waveform. */
  val levels: List<Float>

  /**
   * Starts listening. `onUpdate` receives the full text (what was there before + everything said
   * so far) every time it changes, `onFinished` receives the final text once listening ends, and
   * `onError` a short Arabic message. Returns false if this engine cannot start on this device.
   */
  fun start(
    baseText: String,
    onUpdate: (String) -> Unit,
    onFinished: (String) -> Unit,
    onError: (String) -> Unit,
  ): Boolean

  /** Ends listening and keeps what was said (delivered through `onFinished`). */
  fun stop()

  /** Ends listening immediately, without calling any callback. */
  fun cancel()
}

private fun joinText(a: String, b: String): String =
  when {
    a.isBlank() -> b
    b.isBlank() -> a
    else -> a.trimEnd() + " " + b
  }

/**
 * Dictation with the speech recognizer built into Android (no Gemini key needed).
 *
 * - [start] begins listening; every partial result is sent to the `onUpdate` callback as the full
 *   text (the text that was there before + everything said so far), so the input box fills in
 *   while the user speaks.
 * - Dictation keeps going across pauses: when the recognizer finishes a sentence the text is kept
 *   and listening restarts, until the user calls [stop] or about three silent rounds in a row
 *   pass.
 *
 * All calls must be made on the main thread.
 */
@Stable
class VoiceDictation internal constructor(context: Context) : Dictation {
  private val appContext = context.applicationContext
  private val handler = Handler(Looper.getMainLooper())

  override var isListening: Boolean by mutableStateOf(false)
    private set

  override var level: Float by mutableFloatStateOf(0f)
    private set

  override var levels: List<Float> by mutableStateOf(emptyList())
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

  override fun start(
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
    levels = emptyList()
    isListening = true
    beginRecognition()
    return true
  }

  override fun stop() {
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

  override fun cancel() {
    if (!isListening) return
    userStopped = true
    restartPending = false
    handler.removeCallbacksAndMessages(null)
    isListening = false
    level = 0f
    levels = emptyList()
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
    levels = emptyList()
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
        levels = (levels + level).takeLast(64)
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

/** Creates the built-in [VoiceDictation] for this screen and releases the microphone when it leaves. */
@Composable
fun rememberVoiceDictation(): VoiceDictation {
  val context = LocalContext.current
  val dictation = remember { VoiceDictation(context) }
  DisposableEffect(dictation) { onDispose { dictation.release() } }
  StopDictationInBackground(dictation)
  return dictation
}

/**
 * Ends the dictation (keeping what was said) when the app leaves the screen: Android cuts the
 * microphone off from apps in the background, so recording would only produce silence.
 */
@Composable
internal fun StopDictationInBackground(dictation: Dictation) {
  val context = LocalContext.current
  DisposableEffect(dictation, context) {
    val owner = context.findLifecycleOwner()
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) dictation.stop()
    }
    owner?.lifecycle?.addObserver(observer)
    onDispose { owner?.lifecycle?.removeObserver(observer) }
  }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
  var current: Context? = this
  while (current is ContextWrapper) {
    if (current is LifecycleOwner) return current
    current = current.baseContext
  }
  return null
}
