package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.remote.UserGeminiKeyStore
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

/** Joins two pieces of transcript with a single space (none before closing punctuation). */
private fun joinSegments(a: String, b: String): String {
  if (a.isBlank()) return b
  if (b.isBlank()) return a
  if (b.first() in ".,،؟?!:؛") return a.trimEnd() + b
  if (a.last().isWhitespace() || b.first().isWhitespace()) return a + b
  return "$a $b"
}

/**
 * Live voice dictation through Gemini's live transcription (model gemini-3.5-transcribe-live).
 *
 * The microphone is recorded continuously inside the app (16 kHz, 16-bit mono PCM) and streamed
 * over a WebSocket in 100 ms chunks, so no pause between sentences can drop words. The server
 * answers with interim hypotheses (shown at once, replaced as the speech goes on) and final
 * segments (appended). Audio recorded before the connection is ready is buffered and sent as soon
 * as it is, so the first words are not lost either. If the connection drops (a live session lasts
 * at most 10 minutes) it reconnects by itself and keeps the text so far.
 *
 * Uses the user's own Gemini key from [UserGeminiKeyStore]. All public calls must be made on the
 * main thread.
 */
@Stable
class GeminiLiveDictation internal constructor(context: Context) : Dictation {
  private val appContext = context.applicationContext
  private val main = Handler(Looper.getMainLooper())

  override var isListening: Boolean by mutableStateOf(false)
    private set

  override var level: Float by mutableFloatStateOf(0f)
    private set

  override var levels: List<Float> by mutableStateOf(emptyList())
    private set

  private var updateCallback: (String) -> Unit = {}
  private var finishedCallback: (String) -> Unit = {}
  private var errorCallback: (String) -> Unit = {}

  private var committed = "" // text before dictation + every final segment so far
  private var interim = "" // the live, still changing segment
  private var displayed = "" // what the input box currently shows

  // Bumped on every start/stop-for-good so late callbacks of an old session are ignored.
  @Volatile private var session = 0
  @Volatile private var socket: WebSocket? = null
  @Volatile private var ready = false
  @Volatile private var captureRunning = false
  @Volatile private var stopRequested = false
  private var stopping = false // main thread only
  private var failures = 0 // main thread only

  private val lock = Any()
  private val pending = ArrayDeque<ByteArray>() // audio waiting for the connection; guarded by lock
  private var endPending = false // "stream finished" to send once ready; guarded by lock

  private val finishRunnable = Runnable { finishNow() }
  private val connectTimeoutRunnable =
    Runnable {
      if (isListening && !ready) fail(CONNECT_ERROR)
    }

  override fun start(
    baseText: String,
    onUpdate: (String) -> Unit,
    onFinished: (String) -> Unit,
    onError: (String) -> Unit,
  ): Boolean {
    if (isListening) return true
    if (UserGeminiKeyStore.getKey(appContext).isBlank()) return false
    if (!hasMicrophonePermission(appContext)) return false
    val record = createAudioRecord() ?: return false

    updateCallback = onUpdate
    finishedCallback = onFinished
    errorCallback = onError
    committed = baseText
    interim = ""
    displayed = baseText
    stopping = false
    stopRequested = false
    failures = 0
    synchronized(lock) {
      pending.clear()
      endPending = false
      ready = false
    }
    session++
    val sid = session
    level = 0f
    levels = emptyList()
    isListening = true
    startCapture(sid, record)
    connect(sid)
    return true
  }

  override fun stop() {
    if (!isListening || stopping) return
    stopping = true
    stopRequested = true
    captureRunning = false // the capture thread sends the last audio and then "stream finished"
    // Safety net in case the server never answers; normally the final text ends it sooner.
    scheduleFinish(if (ready) 3500L else 7000L)
  }

  override fun cancel() {
    if (!isListening) return
    shutDown()
  }

  internal fun release() {
    cancel()
    main.removeCallbacksAndMessages(null)
  }

  // ---------------------------------------------------------------------------------------------
  // Recording
  // ---------------------------------------------------------------------------------------------

  @SuppressLint("MissingPermission") // checked in start() through hasMicrophonePermission()
  private fun createAudioRecord(): AudioRecord? {
    val minBuffer =
      AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBuffer <= 0) return null
    val bufferSize = max(minBuffer, CHUNK_BYTES * 4)
    val sources =
      intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
    for (source in sources) {
      try {
        val record =
          AudioRecord(
            source,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
          )
        if (record.state == AudioRecord.STATE_INITIALIZED) return record
        record.release()
      } catch (_: Exception) {}
    }
    return null
  }

  private fun startCapture(sid: Int, record: AudioRecord) {
    captureRunning = true
    Thread(
        {
          val buffer = ByteArray(CHUNK_BYTES)
          try {
            record.startRecording()
            while (captureRunning) {
              var read = 0
              while (read < CHUNK_BYTES && captureRunning) {
                val n = record.read(buffer, read, CHUNK_BYTES - read)
                if (n < 0) throw IllegalStateException("audio read error $n")
                read += n
              }
              // After stop() the last, shorter piece is still sent so the final word is not cut.
              if (read > 0) handleChunk(sid, buffer.copyOf(read))
            }
          } catch (e: Exception) {
            main.post { if (sid == session) fail("تعذّر الوصول إلى الميكروفون") }
          } finally {
            try {
              record.stop()
            } catch (_: Exception) {}
            record.release()
            if (stopRequested) sendStreamEnd(sid)
          }
        },
        "gemini-live-capture",
      )
      .start()
  }

  /** Runs on the capture thread: draws the waveform and sends (or buffers) the audio. */
  private fun handleChunk(sid: Int, chunk: ByteArray) {
    val half = (chunk.size / 2) and 1.inv()
    val first = levelOf(chunk, 0, half)
    val second = levelOf(chunk, half, chunk.size)
    main.post {
      if (sid == session && isListening) {
        level = max(first, second)
        levels = (levels + first + second).takeLast(MAX_LEVELS)
      }
    }
    synchronized(lock) {
      if (sid != session) return
      val ws = socket
      if (ready && ws != null) {
        ws.send(audioMessage(chunk))
      } else {
        pending.addLast(chunk)
        while (pending.size > MAX_PENDING_CHUNKS) pending.removeFirst()
      }
    }
  }

  private fun sendStreamEnd(sid: Int) {
    synchronized(lock) {
      if (sid != session) return
      val ws = socket
      if (ready && ws != null) ws.send(END_MESSAGE) else endPending = true
    }
  }

  private fun levelOf(pcm: ByteArray, from: Int, to: Int): Float {
    var sum = 0.0
    var count = 0
    var i = from
    while (i + 1 < to) {
      val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
      sum += sample.toDouble() * sample.toDouble()
      count++
      i += 2
    }
    if (count == 0) return 0f
    val rms = sqrt(sum / count) / 32768.0
    val db = 20.0 * log10(rms + 1e-6)
    return ((db + 50.0) / 38.0).toFloat().coerceIn(0f, 1f) // -50 dB = silence, -12 dB = loud
  }

  private fun audioMessage(chunk: ByteArray): String =
    "{\"realtimeInput\":{\"audio\":{\"data\":\"" +
      Base64.encodeToString(chunk, Base64.NO_WRAP) +
      "\",\"mimeType\":\"audio/pcm;rate=16000\"}}}"

  // ---------------------------------------------------------------------------------------------
  // Connection
  // ---------------------------------------------------------------------------------------------

  private fun setupMessage(): String {
    val transcription =
      JSONObject()
        .put("languageCodes", JSONArray().put("ar-EG"))
        .put("customVocabulary", JSONArray(VOCABULARY))
    val setup =
      JSONObject()
        .put("model", MODEL)
        .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
        .put("inputAudioTranscription", transcription)
    return JSONObject().put("setup", setup).toString()
  }

  private fun connect(sid: Int) {
    val key = UserGeminiKeyStore.getKey(appContext)
    if (key.isBlank()) {
      fail("لا يوجد مفتاح Gemini، أضفه من الإعدادات")
      return
    }
    synchronized(lock) { ready = false }
    val request = Request.Builder().url("$WS_URL?key=$key").build()
    socket = client.newWebSocket(request, SocketListener(sid))
    main.removeCallbacks(connectTimeoutRunnable)
    main.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)
  }

  private inner class SocketListener(private val sid: Int) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      if (sid != session) {
        webSocket.cancel()
        return
      }
      webSocket.send(setupMessage())
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      handleMessage(sid, webSocket, text)
    }

    // The Live API sends its JSON in binary frames.
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
      handleMessage(sid, webSocket, bytes.utf8())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      webSocket.close(1000, null)
      main.post { socketGone(sid, webSocket, code, reason, null) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      main.post { socketGone(sid, webSocket, code, reason, null) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      main.post { socketGone(sid, webSocket, -1, t.message.orEmpty(), response?.code) }
    }
  }

  /** Runs on the OkHttp thread. */
  private fun handleMessage(sid: Int, ws: WebSocket, raw: String) {
    val json =
      try {
        JSONObject(raw)
      } catch (_: Exception) {
        return
      }

    if (json.has("setupComplete") || json.has("setup_complete")) {
      synchronized(lock) {
        if (sid != session || ws !== socket) return
        ready = true
        // Everything recorded while connecting goes out first, in order.
        while (pending.isNotEmpty()) ws.send(audioMessage(pending.removeFirst()))
        if (endPending) {
          ws.send(END_MESSAGE)
          endPending = false
        }
      }
      main.post { if (sid == session) main.removeCallbacks(connectTimeoutRunnable) }
      return
    }

    val content = json.optJSONObject("serverContent") ?: json.optJSONObject("server_content") ?: return
    val interimText =
      (content.optJSONObject("interimInputTranscription")
          ?: content.optJSONObject("interim_input_transcription"))
        ?.optString("text")
        .orEmpty()
    val finalText =
      (content.optJSONObject("inputTranscription") ?: content.optJSONObject("input_transcription"))
        ?.optString("text")
        .orEmpty()
    if (interimText.isEmpty() && finalText.isEmpty()) return
    main.post { onTranscript(sid, interimText, finalText) }
  }

  // ---------------------------------------------------------------------------------------------
  // Text and lifecycle (main thread)
  // ---------------------------------------------------------------------------------------------

  private fun onTranscript(sid: Int, interimText: String, finalText: String) {
    if (sid != session || !isListening) return
    var changed = false
    val fin = finalText.trim()
    val itm = interimText.trim()
    if (fin.isNotEmpty()) {
      committed = joinSegments(committed, fin)
      interim = ""
      changed = true
    }
    // A late interim that only repeats the segment just finalized is ignored.
    if (itm.isNotEmpty() && !committed.trimEnd().endsWith(itm)) {
      interim = itm
      changed = true
    }
    if (changed) {
      displayed = if (interim.isEmpty()) committed else joinSegments(committed, interim)
      updateCallback(displayed)
    }
    // After the user stopped: finish once the server has gone quiet for a moment.
    if (stopping) scheduleFinish(FINISH_QUIET_MS)
  }

  private fun scheduleFinish(delayMillis: Long) {
    main.removeCallbacks(finishRunnable)
    main.postDelayed(finishRunnable, delayMillis)
  }

  /** Keeps the live segment as text when no final version of it is going to arrive. */
  private fun promoteInterim() {
    if (interim.isNotBlank()) {
      committed = joinSegments(committed, interim.trim())
      interim = ""
      displayed = committed
      updateCallback(displayed)
    }
  }

  private fun finishNow() {
    if (!isListening) return
    promoteInterim()
    shutDown()
    finishedCallback(displayed)
  }

  private fun fail(message: String) {
    if (!isListening) return
    promoteInterim()
    shutDown()
    finishedCallback(displayed)
    errorCallback(message)
  }

  /** Stops recording, closes the connection and resets every state; callbacks are not called. */
  private fun shutDown() {
    session++ // from here on, anything still coming from the old session is ignored
    captureRunning = false
    main.removeCallbacks(finishRunnable)
    main.removeCallbacks(connectTimeoutRunnable)
    val ws = socket
    socket = null
    synchronized(lock) {
      ready = false
      pending.clear()
      endPending = false
    }
    try {
      ws?.close(1000, null)
    } catch (_: Exception) {}
    stopping = false
    isListening = false
    level = 0f
    levels = emptyList()
  }

  private fun socketGone(sid: Int, ws: WebSocket, code: Int, reason: String, httpCode: Int?) {
    if (sid != session || !isListening || ws !== socket) return
    socket = null
    var wasReady = false
    synchronized(lock) {
      wasReady = ready
      ready = false
    }
    main.removeCallbacks(connectTimeoutRunnable)
    if (stopping) {
      finishNow()
      return
    }
    promoteInterim() // keep what has been heard even if the final version never comes
    if (wasReady) failures = 0 else failures++
    val fatalHttp = httpCode != null && httpCode in 400..499
    val fatalClose = !wasReady && code in 1002..1011
    if (fatalHttp || fatalClose || failures > 2) {
      fail(errorMessage(code, reason, httpCode))
      return
    }
    // A live session lasts at most 10 minutes (or the network blinked): reconnect and carry on.
    // The microphone keeps recording meanwhile and that audio is sent once ready again.
    main.postDelayed(
      { if (sid == session && isListening && !stopping) connect(sid) },
      RECONNECT_DELAY_MS,
    )
  }

  private fun errorMessage(code: Int, reason: String, httpCode: Int?): String =
    when {
      httpCode == 400 || httpCode == 401 || httpCode == 403 ->
        "مفتاح Gemini غير صالح أو لا يملك صلاحية التفريغ الصوتي المباشر"
      httpCode == 404 -> "خدمة التفريغ الصوتي المباشر غير متاحة لهذا المفتاح"
      httpCode == 429 -> "تم تجاوز حصة Gemini، حاول لاحقًا"
      code in 1002..1011 && reason.isNotBlank() -> "رفضت خدمة Gemini الطلب: ${reason.take(120)}"
      else -> CONNECT_ERROR
    }

  private companion object {
    const val SAMPLE_RATE = 16_000
    const val CHUNK_BYTES = 3_200 // 100 ms of 16-bit mono audio
    const val MAX_PENDING_CHUNKS = 100 // at most 10 s of audio kept while (re)connecting
    const val MAX_LEVELS = 64
    const val CONNECT_TIMEOUT_MS = 8_000L
    const val RECONNECT_DELAY_MS = 400L
    const val FINISH_QUIET_MS = 900L

    const val MODEL = "models/gemini-3.5-transcribe-live"
    const val WS_URL =
      "wss://generativelanguage.googleapis.com/ws/" +
        "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val END_MESSAGE = "{\"realtimeInput\":{\"audioStreamEnd\":true}}"
    const val CONNECT_ERROR = "تعذّر الاتصال بخدمة Gemini، تحقق من الإنترنت"

    // Words of the app, so the recognizer prefers them over similar-sounding ones.
    val VOCABULARY =
      listOf(
        "فاتورة",
        "فاتورة الكهرباء",
        "فاتورة المياه",
        "فاتورة الغاز",
        "فاتورة الإنترنت",
        "قراءة العداد",
        "المتأخرات",
        "جنيه",
      )

    val client: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // a live stream has no read deadline
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    }
  }
}

/** Creates the Gemini live [GeminiLiveDictation] for this screen and releases the microphone when it leaves. */
@Composable
fun rememberGeminiLiveDictation(): GeminiLiveDictation {
  val context = LocalContext.current
  val dictation = remember { GeminiLiveDictation(context) }
  DisposableEffect(dictation) { onDispose { dictation.release() } }
  StopDictationInBackground(dictation)
  return dictation
}
