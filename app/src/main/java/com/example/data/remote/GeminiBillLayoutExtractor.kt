package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * One piece of text on the bill. The box is normalized to 0..1000 on both axes, relative to the
 * whole image: ymin/xmin = top-left corner, ymax/xmax = bottom-right corner.
 */
@Serializable
data class ReplicaTextBlockDto(
  val text: String = "",
  val ymin: Double = 0.0,
  val xmin: Double = 0.0,
  val ymax: Double = 0.0,
  val xmax: Double = 0.0,
  val bold: Boolean = false,
  val align: String = "right",
  val color: String = "#000000",
  val font: String = "sans",
)

/** A visible rule / separator / table border, in the same 0..1000 coordinates. */
@Serializable
data class ReplicaLineDto(
  val x1: Double = 0.0,
  val y1: Double = 0.0,
  val x2: Double = 0.0,
  val y2: Double = 0.0,
  val dashed: Boolean = false,
  val color: String = "#000000",
)

/** Layout description used to redraw a bill as a clean digital replica. */
@Serializable
data class ReplicaLayoutDto(
  val backgroundColor: String = "#FFFFFF",
  val textBlocks: List<ReplicaTextBlockDto> = emptyList(),
  val lines: List<ReplicaLineDto> = emptyList(),
)

@Serializable
data class ReplicaCorrectionDto(
  val index: Int = -1,
  val text: String = "",
)

@Serializable
data class ReplicaCorrectionsDto(
  val corrections: List<ReplicaCorrectionDto> = emptyList(),
)

/** Result of the optional AI proofreading pass. */
data class ReplicaVerifyResult(
  val layout: ReplicaLayoutDto,
  val changedCount: Int,
)

/** Real stages of a replica request, reported to the UI as they actually start. */
enum class ReplicaStage {
  PREPARING,
  ANALYZING,
}

/**
 * Asks Gemini to read a bill photo and return every text segment with its position, weight, color,
 * alignment and a font family hint, so the bill can be redrawn with the same layout. It can also
 * proofread an already extracted layout against the image (optional step for the user).
 */
object GeminiBillLayoutExtractor {
  private const val TAG = "GeminiBillLayout"

  // Accuracy first: the strongest model is tried first, the faster ones are the fallback.
  private val MODELS =
    listOf(
      "gemini-3.1-pro-preview",
      "gemini-3.5-flash",
      "gemini-flash-latest",
      "gemini-3.1-flash-lite-preview",
    )

  // The pro model can take longer than the shared client's 60 s read timeout.
  private val service: GeminiApiService by lazy {
    val client =
      GeminiApiClient.sharedOkHttpClient
        .newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build()
    Retrofit.Builder()
      .baseUrl(GeminiApiClient.BASE_URL)
      .client(client)
      .addConverterFactory(
        GeminiApiClient.lenientJson.asConverterFactory("application/json".toMediaType())
      )
      .build()
      .create(GeminiApiService::class.java)
  }

  suspend fun extract(
    context: Context,
    imageFile: File,
    onStage: (ReplicaStage) -> Unit = {},
  ): Result<ReplicaLayoutDto> =
    withContext(Dispatchers.IO) {
      runCatching {
        onStage(ReplicaStage.PREPARING)
        val apiKey = requireApiKey()
        val base64 = loadImageBase64(context, imageFile)
        onStage(ReplicaStage.ANALYZING)
        val rawJson =
          callWithFailover(
            apiKey = apiKey,
            schemaRequest = buildExtractRequest(base64, withSchema = true),
            lightRequest = buildExtractRequest(base64, withSchema = false),
          )
        val json = GeminiApiClient.extractJsonObjectSubstring(rawJson)
        val decoded =
          GeminiApiClient.lenientJson.decodeFromString(ReplicaLayoutDto.serializer(), json)

        val usable =
          decoded.textBlocks.filter {
            it.text.isNotBlank() && it.xmax > it.xmin && it.ymax > it.ymin
          }
        if (usable.isEmpty()) {
          throw IllegalStateException(
            "لم يتم التعرف على نصوص كافية لإعادة رسم الفاتورة. جرّب مرة أخرى."
          )
        }

        // Safety net: if the model answered in 0..1 coordinates, bring them to 0..1000.
        val maxCoord = usable.maxOf { maxOf(it.xmax, it.ymax) }
        if (maxCoord in 0.0..1.5) {
          decoded.copy(
            textBlocks =
              usable.map {
                it.copy(
                  xmin = it.xmin * 1000.0,
                  xmax = it.xmax * 1000.0,
                  ymin = it.ymin * 1000.0,
                  ymax = it.ymax * 1000.0,
                )
              },
            lines =
              decoded.lines.map {
                it.copy(
                  x1 = it.x1 * 1000.0,
                  y1 = it.y1 * 1000.0,
                  x2 = it.x2 * 1000.0,
                  y2 = it.y2 * 1000.0,
                )
              },
          )
        } else {
          decoded.copy(textBlocks = usable)
        }
      }
    }

  /**
   * Optional proofreading pass: the model compares the extracted texts with the image and returns
   * only the texts that are wrong. Positions and styles are never touched.
   */
  suspend fun verify(
    context: Context,
    imageFile: File,
    layout: ReplicaLayoutDto,
    onStage: (ReplicaStage) -> Unit = {},
  ): Result<ReplicaVerifyResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        onStage(ReplicaStage.PREPARING)
        val apiKey = requireApiKey()
        val base64 = loadImageBase64(context, imageFile)
        val listing =
          layout.textBlocks.mapIndexed { index, block -> "$index\t${block.text}" }.joinToString("\n")
        onStage(ReplicaStage.ANALYZING)
        val rawJson =
          callWithFailover(
            apiKey = apiKey,
            schemaRequest = buildVerifyRequest(base64, listing, withSchema = true),
            lightRequest = buildVerifyRequest(base64, listing, withSchema = false),
          )
        val json = GeminiApiClient.extractJsonObjectSubstring(rawJson)
        val decoded =
          GeminiApiClient.lenientJson.decodeFromString(ReplicaCorrectionsDto.serializer(), json)

        val corrections = HashMap<Int, String>()
        decoded.corrections.forEach { correction ->
          val fixed = correction.text.replace('\n', ' ').trim()
          if (
            correction.index in layout.textBlocks.indices &&
              fixed.isNotEmpty() &&
              fixed != layout.textBlocks[correction.index].text.trim()
          ) {
            corrections[correction.index] = fixed
          }
        }
        if (corrections.isEmpty()) {
          ReplicaVerifyResult(layout = layout, changedCount = 0)
        } else {
          ReplicaVerifyResult(
            layout =
              layout.copy(
                textBlocks =
                  layout.textBlocks.mapIndexed { index, block ->
                    val fixed = corrections[index]
                    if (fixed != null) block.copy(text = fixed) else block
                  }
              ),
            changedCount = corrections.size,
          )
        }
      }
    }

  private fun requireApiKey(): String {
    val apiKey = UserGeminiKeyStore.getKey().trim()
    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
      throw IllegalStateException(
        "لم يتم إضافة مفتاح Gemini الخاص بك بعد. أضفه من تبويب الإعدادات."
      )
    }
    return apiKey
  }

  /** Sends the stored image at (almost) full resolution for the most accurate reading. */
  private fun loadImageBase64(context: Context, imageFile: File): String {
    if (!imageFile.exists()) {
      throw IllegalStateException("ملف صورة الفاتورة غير موجود على هذا الجهاز.")
    }
    val bitmap =
      GeminiApiClient.decodeOrientedBitmapFromUri(
        context.applicationContext,
        Uri.fromFile(imageFile),
        2400,
      )
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }

  private suspend fun callWithFailover(
    apiKey: String,
    schemaRequest: GenerateContentRequest,
    lightRequest: GenerateContentRequest,
  ): String {
    val attempts = MODELS.map { it to schemaRequest } + listOf("gemini-flash-latest" to lightRequest)

    var lastError: Throwable? = null
    for ((index, attempt) in attempts.withIndex()) {
      val (modelId, request) = attempt
      try {
        val response = service.generateContent(model = modelId, apiKey = apiKey, request = request)
        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        if (!text.isNullOrBlank()) return text
      } catch (ce: CancellationException) {
        throw ce
      } catch (he: HttpException) {
        val code = he.code()
        Log.w(TAG, "Replica model '$modelId' failed with HTTP $code", he)
        if (code == 401 || code == 403) {
          throw IllegalStateException(
            "تعذر التحقق من صلاحية مفتاح Gemini الخاص بك (HTTP $code). تحقق من المفتاح في تبويب الإعدادات."
          )
        }
        lastError = he
        if (index < attempts.lastIndex) delay(700L)
      } catch (ioe: IOException) {
        Log.w(TAG, "Replica network error on '$modelId'", ioe)
        lastError = ioe
        if (index < attempts.lastIndex) delay(700L)
      }
    }
    throw IllegalStateException(
      lastError?.localizedMessage
        ?: "تعذر إكمال الطلب حالياً. أعد المحاولة بعد لحظات."
    )
  }

  private fun buildExtractRequest(base64Jpeg: String, withSchema: Boolean): GenerateContentRequest =
    GenerateContentRequest(
      systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = EXTRACT_SYSTEM_PROMPT))),
      contents =
        listOf(
          GeminiContent(
            parts =
              listOf(
                GeminiPart(text = EXTRACT_USER_PROMPT),
                GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Jpeg)),
              )
          )
        ),
      generationConfig =
        GeminiGenerationConfig(
          responseMimeType = "application/json",
          temperature = 0f,
          responseSchema = if (withSchema) buildExtractSchema() else null,
        ),
    )

  private fun buildVerifyRequest(
    base64Jpeg: String,
    listing: String,
    withSchema: Boolean,
  ): GenerateContentRequest =
    GenerateContentRequest(
      systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = VERIFY_SYSTEM_PROMPT))),
      contents =
        listOf(
          GeminiContent(
            parts =
              listOf(
                GeminiPart(text = "Extracted texts (index<TAB>text):\n$listing"),
                GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Jpeg)),
              )
          )
        ),
      generationConfig =
        GeminiGenerationConfig(
          responseMimeType = "application/json",
          temperature = 0f,
          responseSchema = if (withSchema) buildVerifySchema() else null,
        ),
    )

  private fun buildVerifySchema(): JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
      putJsonObject("corrections") {
        put("type", "ARRAY")
        putJsonObject("items") {
          put("type", "OBJECT")
          putJsonObject("properties") {
            putJsonObject("index") { put("type", "INTEGER") }
            putJsonObject("text") { put("type", "STRING") }
          }
          putJsonArray("required") {
            add("index")
            add("text")
          }
        }
      }
    }
    putJsonArray("required") { add("corrections") }
  }

  private fun buildExtractSchema(): JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
      putJsonObject("backgroundColor") { put("type", "STRING") }
      putJsonObject("textBlocks") {
        put("type", "ARRAY")
        putJsonObject("items") {
          put("type", "OBJECT")
          putJsonObject("properties") {
            putJsonObject("text") { put("type", "STRING") }
            putJsonObject("ymin") { put("type", "NUMBER") }
            putJsonObject("xmin") { put("type", "NUMBER") }
            putJsonObject("ymax") { put("type", "NUMBER") }
            putJsonObject("xmax") { put("type", "NUMBER") }
            putJsonObject("bold") { put("type", "BOOLEAN") }
            putJsonObject("align") {
              put("type", "STRING")
              putJsonArray("enum") {
                add("left")
                add("center")
                add("right")
              }
            }
            putJsonObject("color") { put("type", "STRING") }
            putJsonObject("font") {
              put("type", "STRING")
              putJsonArray("enum") {
                add("sans")
                add("serif")
                add("mono")
              }
            }
          }
          putJsonArray("required") {
            add("text")
            add("ymin")
            add("xmin")
            add("ymax")
            add("xmax")
            add("bold")
            add("align")
            add("color")
            add("font")
          }
        }
      }
      putJsonObject("lines") {
        put("type", "ARRAY")
        putJsonObject("items") {
          put("type", "OBJECT")
          putJsonObject("properties") {
            putJsonObject("x1") { put("type", "NUMBER") }
            putJsonObject("y1") { put("type", "NUMBER") }
            putJsonObject("x2") { put("type", "NUMBER") }
            putJsonObject("y2") { put("type", "NUMBER") }
            putJsonObject("dashed") { put("type", "BOOLEAN") }
            putJsonObject("color") { put("type", "STRING") }
          }
          putJsonArray("required") {
            add("x1")
            add("y1")
            add("x2")
            add("y2")
            add("dashed")
            add("color")
          }
        }
      }
    }
    putJsonArray("required") {
      add("backgroundColor")
      add("textBlocks")
      add("lines")
    }
  }

  private val EXTRACT_SYSTEM_PROMPT =
    """
    You are a document layout reconstruction engine. You receive a photo or scan of a receipt or bill
    (Arabic and/or English) and return JSON that lets an app redraw it as a clean digital replica
    with the same layout.

    Rules:
    - backgroundColor: the dominant color of the paper itself, as a hex string like #F4F1EA.
    - textBlocks: every visible piece of text, in reading order. One block = one single-line text
      segment (a word group or one table cell). Never merge different columns into one block and
      never put several lines into one block. Copy the text EXACTLY as printed: keep Arabic as
      Arabic, keep digits as shown, do not translate, correct or reorder it.
    - ACCURACY OF DIGITS AND LETTERS IS CRITICAL. Faded thermal printing makes some characters easy
      to confuse (3 and 8, 0 and 6, 1 and 7, 5 and 6, Arabic dots and similar letter shapes). Look at
      every digit and letter individually before writing it, read each Arabic word letter by letter
      with its correct dots, and cross-check values that appear more than once on the bill.
    - ymin, xmin, ymax, xmax: the tight bounding box of that text, normalized to 0..1000 relative to
      the whole image (0,0 = top-left corner; 1000,1000 = bottom-right corner).
    - bold: true if the text looks heavier or larger than the surrounding text (titles, totals).
    - align: how the text sits inside its box: "right" for normal Arabic text, "left" for
      left-aligned Latin text or numbers, "center" for centered text.
    - color: ink color as a hex string.
    - font: "sans" by default, "serif" if the letterforms have serifs, "mono" for dot-matrix,
      typewriter or monospaced receipt printing.
    - lines: visible horizontal or vertical rules, separators, dashed lines and table borders as
      segments (x1,y1,x2,y2 in 0..1000), with dashed=true for dashed or dotted rules, and a color.
    - Ignore anything outside the paper (table, hands, shadows).
    """
      .trimIndent()

  private const val EXTRACT_USER_PROMPT =
    "Return the layout JSON for this bill image: every text segment with its exact text (double-check every digit and Arabic letter), bounding box (0..1000), bold, align, color and font, plus the visible lines and the paper background color."

  private val VERIFY_SYSTEM_PROMPT =
    """
    You are a meticulous proofreader for OCR of printed receipts and bills (Arabic and/or English).
    You receive the bill image and a numbered list of texts that were extracted from it, one per
    line, in the format: index<TAB>text.

    Compare every item with what is actually printed on the image. Return ONLY the items that are
    wrong: misread digits or letters, wrong Arabic letters or dots, missing or extra words, wrong
    spelling compared with the print. For each wrong item return its index and the corrected text,
    exactly as printed.

    Rules:
    - Do not return items that are already correct. If everything is correct, return an empty list.
    - Do not improve, normalize, translate or re-spell correct items; keep the printed wording even
      if it looks unusual.
    - Pay extra attention to digits: faded thermal printing makes 3/8, 0/6, 1/7 and 5/6 easy to
      confuse. Decide each digit by looking at it closely and cross-check numbers that appear twice
      (for example a total and the amount written in words).
    """
      .trimIndent()
}
