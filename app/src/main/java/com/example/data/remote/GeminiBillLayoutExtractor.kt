package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
import retrofit2.HttpException

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

/**
 * Asks Gemini to read a bill photo and return every text segment with its position, weight, color,
 * alignment and a font family hint, so the bill can be redrawn with the same layout.
 */
object GeminiBillLayoutExtractor {
  private const val TAG = "GeminiBillLayout"

  suspend fun extract(context: Context, imageFile: File): Result<ReplicaLayoutDto> =
    withContext(Dispatchers.IO) {
      runCatching {
        val apiKey = UserGeminiKeyStore.getKey().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
          throw IllegalStateException(
            "لم يتم إضافة مفتاح Gemini الخاص بك بعد. أضفه من تبويب الإعدادات."
          )
        }
        if (!imageFile.exists()) {
          throw IllegalStateException("ملف صورة الفاتورة غير موجود على هذا الجهاز.")
        }

        val bitmap =
          GeminiApiClient.decodeOrientedBitmapFromUri(
            context.applicationContext,
            Uri.fromFile(imageFile),
            1800,
          )
        val rawJson = callWithFailover(apiKey, bitmap.toJpegBase64())
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

  private suspend fun callWithFailover(apiKey: String, base64Jpeg: String): String {
    val service = GeminiApiClient.defaultService
    val schemaRequest = buildRequest(base64Jpeg, withSchema = true)
    val lightRequest = buildRequest(base64Jpeg, withSchema = false)
    val attempts =
      GeminiApiClient.SUPPORTED_VISION_MODELS.map { it to schemaRequest } +
        listOf("gemini-flash-latest" to lightRequest)

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
        Log.w(TAG, "Layout model '$modelId' failed with HTTP $code", he)
        if (code == 401 || code == 403) {
          throw IllegalStateException(
            "تعذر التحقق من صلاحية مفتاح Gemini الخاص بك (HTTP $code). تحقق من المفتاح في تبويب الإعدادات."
          )
        }
        lastError = he
        if (index < attempts.lastIndex) delay(700L)
      } catch (ioe: IOException) {
        Log.w(TAG, "Layout network error on '$modelId'", ioe)
        lastError = ioe
        if (index < attempts.lastIndex) delay(700L)
      }
    }
    throw IllegalStateException(
      lastError?.localizedMessage
        ?: "تعذر إعادة رسم الفاتورة حالياً. أعد المحاولة بعد لحظات."
    )
  }

  private fun buildRequest(base64Jpeg: String, withSchema: Boolean): GenerateContentRequest =
    GenerateContentRequest(
      systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = SYSTEM_PROMPT))),
      contents =
        listOf(
          GeminiContent(
            parts =
              listOf(
                GeminiPart(text = USER_PROMPT),
                GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Jpeg)),
              )
          )
        ),
      generationConfig =
        GeminiGenerationConfig(
          responseMimeType = "application/json",
          temperature = 0.1f,
          responseSchema = if (withSchema) buildSchema() else null,
        ),
    )

  private fun Bitmap.toJpegBase64(quality: Int = 85): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }

  private fun buildSchema(): JsonObject = buildJsonObject {
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

  private val SYSTEM_PROMPT =
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

  private const val USER_PROMPT =
    "Return the layout JSON for this bill image: every text segment with its exact text, bounding box (0..1000), bold, align, color and font, plus the visible lines and the paper background color."
}
