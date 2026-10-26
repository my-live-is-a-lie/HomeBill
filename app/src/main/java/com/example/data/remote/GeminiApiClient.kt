package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class GenerateContentRequest(
  val contents: List<GeminiContent>,
  val generationConfig: GeminiGenerationConfig? = null,
  val systemInstruction: GeminiContent? = null,
)

@Serializable
data class GeminiContent(
  val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
  val text: String? = null,
  val inlineData: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
  val mimeType: String,
  val data: String,
)

@Serializable
data class GeminiGenerationConfig(
  val responseMimeType: String? = null,
  val responseSchema: JsonObject? = null,
  val temperature: Float? = null,
)

@Serializable
data class GenerateContentResponse(
  val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
  val content: GeminiContent? = null,
)

interface GeminiApiService {
  @POST("v1beta/models/{model}:generateContent")
  suspend fun generateContent(
    @Path("model") model: String,
    @Query("key") apiKey: String,
    @Body request: GenerateContentRequest,
  ): GenerateContentResponse
}

@Serializable
data class ExtractedSheetRowDto(
  val label: String = "",
  val value: String = "",
  val unit: String = "",
)

@Serializable
data class ExpenseClassificationDto(
  val expenseCategory: String = "other",
  val suggestedCategoryName: String = "فاتورة منزلية",
  val classificationReason: String = "",
  val defaultUnit: String = "وحدة",
)

/**
 * Structured JSON payload returned by Gemini for a captured bill or receipt image.
 * Includes core structured fields (`date`, `storeName`, `totalAmount`), automatic expense
 * classification (`expenseCategory`, `detectedUtilityType`, `classificationReason`),
 * tax, utility meter readings, OCR text, and itemized sheet rows.
 */
@Serializable
data class ExtractedBillDto(
  val date: String = "",
  val storeName: String = "",
  val totalAmount: Double = 0.0,
  val taxAmount: Double = 0.0,
  val subtotalAmount: Double = 0.0,
  val currency: String = "ر.س",
  val invoiceNumber: String = "",
  val expenseCategory: String = "",
  val detectedUtilityType: String = "other",
  val suggestedCategoryName: String = "فاتورة منزلية",
  val classificationReason: String = "",
  val consumptionValue: Double = 0.0,
  val consumptionUnit: String = "وحدة",
  val previousReading: Double = 0.0,
  val currentReading: Double = 0.0,
  val rawExtractedText: String = "",
  val sheetRows: List<ExtractedSheetRowDto> = emptyList(),
  // Optional backward-compatible fallback keys in case a plain-JSON fallback uses legacy aliases
  val billDateIso: String = "",
  val providerName: String = "",
) {
  val resolvedDateIso: String
    get() = date.trim().ifBlank { billDateIso.trim() }

  val resolvedStoreName: String
    get() = storeName.trim().ifBlank { providerName.trim() }

  val resolvedExpenseType: String
    get() = expenseCategory.trim().ifBlank { detectedUtilityType.trim() }.ifBlank { "other" }
}

data class ScannedBillJsonResult(
  val data: ExtractedBillDto,
  val formattedJson: String,
)

/**
 * Configured Gemini REST API client for multimodal image understanding and structured JSON output.
 */
class GeminiApiClient(
  private val service: GeminiApiService = defaultService,
  private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY },
) {

  /**
   * Accepts a captured [Bitmap] image of a bill/receipt and returns a structured JSON object
   * containing `date`, `storeName`, `totalAmount`, `taxAmount`, and itemized `sheetRows`.
   */
  suspend fun extractBillFromBitmap(bitmap: Bitmap): Result<ScannedBillJsonResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val apiKey = requireValidApiKey()
        val base64Jpeg = bitmap.toCompressedBase64()
        executeStructuredExtraction(apiKey = apiKey, base64Jpeg = base64Jpeg)
      }
    }

  /**
   * Accepts a captured image [Uri] (from CameraX or system gallery), normalizes EXIF orientation,
   * compresses to JPEG Base64, and returns structured bill JSON from Gemini.
   */
  suspend fun extractBillFromUri(context: Context, imageUri: Uri): Result<ScannedBillJsonResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val apiKey = requireValidApiKey()
        val orientedBitmap = decodeOrientedBitmapFromUri(context, imageUri)
        val base64Jpeg = orientedBitmap.toCompressedBase64()
        executeStructuredExtraction(apiKey = apiKey, base64Jpeg = base64Jpeg)
      }
    }

  /**
   * Automatically classifies the expense category (e.g., `electricity`, `water`, `shopping`,
   * `dining`, `maintenance`, `fuel`, `gas`, `internet`, `telecom`, `other`) using Gemini
   * based on the store/provider name and/or bill content.
   */
  suspend fun classifyExpenseFromStoreOrContent(
    storeName: String,
    contentText: String = "",
  ): Result<ExpenseClassificationDto> =
    withContext(Dispatchers.IO) {
      runCatching {
        val apiKey = requireValidApiKey()
        val userPrompt = buildString {
          appendLine("صنّف نوع المصروف لهذه الفاتورة بناءً على اسم المتجر/الجهة والمحتوى:")
          appendLine("اسم المتجر أو الجهة المصدرة: ${storeName.trim().ifBlank { "غير محدد" }}")
          if (contentText.isNotBlank()) {
            appendLine("تفاصيل وبنود الفاتورة: ${contentText.trim().take(2000)}")
          }
        }
        val request =
          GenerateContentRequest(
            systemInstruction =
              GeminiContent(
                parts = listOf(GeminiPart(text = EXPENSE_CLASSIFICATION_SYSTEM_PROMPT))
              ),
            contents =
              listOf(
                GeminiContent(
                  parts = listOf(GeminiPart(text = userPrompt))
                )
              ),
            generationConfig =
              GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f,
                responseSchema = buildExpenseClassificationSchema(),
              ),
          )
        val rawJson = executeVisionWithFailover(apiKey = apiKey, primaryRequest = request)
        val sanitizedJson = extractJsonObjectSubstring(rawJson)
        lenientJson.decodeFromString<ExpenseClassificationDto>(sanitizedJson)
      }
    }

  private suspend fun executeStructuredExtraction(
    apiKey: String,
    base64Jpeg: String,
  ): ScannedBillJsonResult {
    val schemaRequest =
      GenerateContentRequest(
        systemInstruction =
          GeminiContent(
            parts = listOf(GeminiPart(text = BILL_EXTRACTION_SYSTEM_PROMPT))
          ),
        contents =
          listOf(
            GeminiContent(
              parts =
                listOf(
                  GeminiPart(text = BILL_EXTRACTION_USER_PROMPT),
                  GeminiPart(
                    inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Jpeg)
                  ),
                )
            )
          ),
        generationConfig =
          GeminiGenerationConfig(
            responseMimeType = "application/json",
            temperature = 0.1f,
            responseSchema = buildStructuredBillSchema(),
          ),
      )

    val rawJson = executeVisionWithFailover(apiKey = apiKey, primaryRequest = schemaRequest)
    val sanitizedJson = extractJsonObjectSubstring(rawJson)
    val decoded = lenientJson.decodeFromString<ExtractedBillDto>(sanitizedJson)
    val effectiveExpense = decoded.resolvedExpenseType
    val normalizedDto =
      decoded.copy(
        date = decoded.resolvedDateIso,
        storeName = decoded.resolvedStoreName,
        expenseCategory = effectiveExpense,
        detectedUtilityType = effectiveExpense,
        classificationReason = decoded.classificationReason.trim(),
        billDateIso = decoded.resolvedDateIso,
        providerName = decoded.resolvedStoreName,
      )
    val pretty = buildCleanSummaryJson(normalizedDto)
    return ScannedBillJsonResult(data = normalizedDto, formattedJson = pretty)
  }

  private fun requireValidApiKey(): String {
    val key = apiKeyProvider().trim()
    if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
      throw IllegalStateException(
        "يرجى إضافة مفتاح GEMINI_API_KEY في لوحة Secrets داخل AI Studio لتفعيل قراءة الفواتير بالذكاء الاصطناعي."
      )
    }
    return key
  }

  private suspend fun executeVisionWithFailover(
    apiKey: String,
    primaryRequest: GenerateContentRequest,
  ): String {
    val sticky = lastHealthyVisionModel
    val candidates =
      if (sticky != null && sticky in SUPPORTED_VISION_MODELS) {
        listOf(sticky) + SUPPORTED_VISION_MODELS.filterNot { it == sticky }
      } else {
        SUPPORTED_VISION_MODELS
      }

    var lastError: Throwable? = null
    for ((index, modelId) in candidates.withIndex()) {
      try {
        val response =
          service.generateContent(model = modelId, apiKey = apiKey, request = primaryRequest)
        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        if (!text.isNullOrBlank()) {
          lastHealthyVisionModel = modelId
          return text
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (he: HttpException) {
        val code = he.code()
        Log.w(TAG, "Gemini vision model '$modelId' failed with HTTP $code", he)
        if (code == 401 || code == 403) {
          throw IllegalStateException(
            "تعذر التحقق من صلاحية مفتاح GEMINI_API_KEY (HTTP $code). يرجى مراجعة لوحة Secrets."
          )
        }
        lastError = he
        if (isRetryableStatus(code) && index < candidates.lastIndex) {
          delay(if (index == 0) 650L else 1100L)
          continue
        }
      } catch (ioe: IOException) {
        Log.w(TAG, "Gemini vision network IO error on '$modelId'", ioe)
        lastError = ioe
        if (index < candidates.lastIndex) {
          delay(650L)
          continue
        }
      }
    }

    // Secondary lightweight JSON-MIME fallback without FSM schema compilation if 503 occurred
    if (lastError is HttpException && isRetryableStatus((lastError as HttpException).code())) {
      val lightRequest =
        primaryRequest.copy(
          generationConfig =
            GeminiGenerationConfig(
              responseMimeType = "application/json",
              temperature = 0.1f,
              responseSchema = null,
            )
        )
      for (fallbackModel in listOf("gemini-flash-latest", "gemini-3.1-flash-lite-preview")) {
        try {
          delay(900L)
          val response =
            service.generateContent(model = fallbackModel, apiKey = apiKey, request = lightRequest)
          val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
          if (!text.isNullOrBlank()) {
            lastHealthyVisionModel = fallbackModel
            return text
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (t: Throwable) {
          lastError = t
        }
      }
    }

    if (lastError is HttpException && (lastError as HttpException).code() == 503) {
      throw IllegalStateException(
        "خدمة قراءة الفواتير تواجه ضغطاً مؤقتاً حالياً (503). يرجى الضغط على «إعادة المحاولة» بعد لحظات."
      )
    }
    if (lastError is HttpException && (lastError as HttpException).code() == 429) {
      throw IllegalStateException(
        "تم تجاوز الحد المؤقت للطلبات المتتالية (429). يرجى الانتظار ثواني قليلة ثم إعادة المحاولة."
      )
    }
    throw IllegalStateException(
      lastError?.localizedMessage ?: "لم يتم العثور على بيانات قابلة للقراءة في صورة الفاتورة."
    )
  }

  private fun isRetryableStatus(code: Int): Boolean =
    code == 503 || code == 429 || code == 500 || code == 502 || code == 504 || code == 404

  companion object {
    private const val TAG = "GeminiApiClient"
    const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val SUPPORTED_VISION_MODELS =
      listOf(
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-pro-preview",
      )

    @Volatile private var lastHealthyVisionModel: String? = null

    val lenientJson: Json = Json {
      ignoreUnknownKeys = true
      isLenient = true
      coerceInputValues = true
      encodeDefaults = true
    }

    private val prettyJson: Json = Json {
      prettyPrint = true
      encodeDefaults = true
    }

    val sharedOkHttpClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    }

    val defaultService: GeminiApiService by lazy {
      Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(sharedOkHttpClient)
        .addConverterFactory(lenientJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeminiApiService::class.java)
    }

    val default: GeminiApiClient by lazy { GeminiApiClient() }

    val EXPENSE_TYPE_KEYS =
      listOf(
        "gas",
        "electricity",
        "water",
        "internet",
        "telecom",
        "shopping",
        "dining",
        "maintenance",
        "fuel",
        "other",
      )

    private val BILL_EXTRACTION_SYSTEM_PROMPT =
      """
      أنت خبير متخصص في قراءة واستخراج البيانات المهيكلة (Structured JSON) والتصنيف التلقائي الذكي لنوع المصروف من صور الفواتير والإيصالات العربية والإنجليزية.
      قم بقراءة الصورة الملتقطة بدقة وإرجاع كائن JSON مهيكل يحتوي على:
      1) date: تاريخ إصدار الفاتورة بصيغة YYYY-MM-DD (أو نص فارغ إذا لم يظهر).
      2) storeName: اسم المتجر أو الشركة أو الجهة المصدرة للفاتورة كما يظهر في الترويسة أو الشعار أو الختم (مثل: الشركة السعودية للكهرباء، شركة المياه الوطنية، شركة الغاز والتصنيع الأهلية، أسواق التميمي، بنده، العثيم، جرير، إلخ).
      3) totalAmount: المبلغ المالي الإجمالي المستحق كرقم عشري (NUMBER)، مع taxAmount لقيمة ضريبة القيمة المضافة إن وجدت، وsubtotalAmount للمبلغ قبل الضريبة إن وجد، وcurrency للعملة (مثل ر.س أو SAR أو ج.م أو USD).
      4) invoiceNumber: رقم الفاتورة أو رقم الإيصال أو الحساب المشترك.
      5) التصنيف التلقائي لنوع المصروف (expenseCategory وdetectedUtilityType — ضع نفس القيمة في الحقلين) بناءً على اسم المتجر/الجهة (storeName) ومحتوى وبنود الفاتورة:
         - electricity: فواتير الكهرباء والطاقة وعدادات ك.و.س / kWh (مثل: الشركة السعودية للكهرباء، شركات توزيع الكهرباء).
         - water: فواتير المياه والصرف الصحي وعدادات م³ وصهاريج المياه (مثل: شركة المياه الوطنية، مصلحة المياه).
         - gas: فواتير الغاز الطبيعي أو تعبئة أسطوانات وخزانات الغاز المنزلي (مثل: غازكو، تاون جاس، ناتجاس).
         - internet: اشتراكات الإنترنت المنزلي والألياف البصرية Fiber/DSL والبيانات الثابتة.
         - telecom: فواتير الاتصالات والهاتف الجوال والباقات (مثل: STC، موبايلي، زين، فودافون، اتصالات، أورنج).
         - shopping: فواتير التسوق والمقاضي والسوبرماركت والهايبرماركت والمتاجر الإلكترونية والإلكترونيات والمكتبات والصيدليات والملابس (مثل: بنده، التميمي، العثيم، الدانوب، كارفور، لولو، جرير، إكسترا، ساكو، إيكيا، أمازون، نون، النهدي).
         - dining: فواتير المطاعم والمقاهي والمخابز والحلويات وتطبيقات توصيل الطعام (مثل: البيك، ستاربكس، دانكن، ماكدونالدز، هنقرستيشن، جاهز).
         - maintenance: فواتير الصيانة المنزلية والسباكة والتكييف والإصلاح وقطع الغيار والتنظيف.
         - fuel: فواتير محطات الوقود والبنزين والديزل وزيوت وغسيل السيارات والنقل والمواصلات (مثل: الدريس، ساسكو، أرامكو، نفط، بترومين، أوبر، كريم).
         - other: أي مصروف عام آخر لا يندرج تحت الفئات السابقة.
      6) suggestedCategoryName: اسم قسم الفاتورة المقترح بالعربية بما يناسب التصنيف (مثل: فاتورة الكهرباء، فاتورة الماء، فاتورة الغاز، فواتير التسوق، فواتير المطاعم، فواتير الصيانة، فواتير الوقود).
      7) classificationReason: جملة عربية قصيرة ومباشرة توضح سبب التصنيف التلقائي استناداً إلى اسم المتجر أو محتوى البنود (مثل: "تم التصنيف كـ تسوق بناءً على اسم المتجر وأصناف المشتريات").
      8) للمرافق المنزلية: كمية الاستهلاك consumptionValue ووحدتها consumptionUnit (مثل م³ أو ك.و.س) والقراءة السابقة previousReading والحالية currentReading (ضع 0 إذا لم تكن فاتورة عداد).
      9) rawExtractedText: النص الكامل المقروء من الفاتورة.
      10) sheetRows: مصفوفة بجميع البنود والتفاصيل والأصناف والرسوم والضرائب الظاهرة في الفاتورة (label، value، unit).
      """.trimIndent()

    private const val BILL_EXTRACTION_USER_PROMPT =
      "استخرج البيانات المهيكلة لهذه الفاتورة المصورة بصيغة JSON، وصنّف نوع المصروف تلقائياً (expenseCategory مثل: electricity أو water أو shopping أو dining أو maintenance أو fuel أو gas أو internet أو telecom) بناءً على اسم المتجر/الجهة (storeName) ومحتوى وبنود الفاتورة (sheetRows)، مع كتابة سبب التصنيف في classificationReason."

    private val EXPENSE_CLASSIFICATION_SYSTEM_PROMPT =
      """
      أنت مصنّف مالي ذكي للفواتير المنزلية وفواتير المتاجر.
      مهمتك تحديد نوع المصروف تلقائياً من بين القيم التالية فقط:
      [electricity, water, gas, internet, telecom, shopping, dining, maintenance, fuel, other]
      اعتماداً على اسم المتجر/الجهة المصدرة أو تفاصيل الأصناف والمحتوى، وإرجاع JSON يحتوي على:
      - expenseCategory: مفتاح التصنيف من القائمة أعلاه.
      - suggestedCategoryName: اسم القسم بالعربية (مثل: فاتورة الكهرباء، فاتورة الماء، فواتير التسوق، فواتير المطاعم، فواتير الصيانة، فواتير الوقود).
      - classificationReason: سبب التصنيف باختصار بالعربية.
      - defaultUnit: وحدة القياس المناسبة (مثل: ك.و.س، م³، صنف، طلب، خدمة، لتر، جيجا، وحدة).
      """.trimIndent()

    fun buildExpenseClassificationSchema(): JsonObject = buildJsonObject {
      put("type", "OBJECT")
      putJsonObject("properties") {
        putJsonObject("expenseCategory") {
          put("type", "STRING")
          putJsonArray("enum") { EXPENSE_TYPE_KEYS.forEach { add(it) } }
        }
        putJsonObject("suggestedCategoryName") { put("type", "STRING") }
        putJsonObject("classificationReason") { put("type", "STRING") }
        putJsonObject("defaultUnit") { put("type", "STRING") }
      }
      putJsonArray("required") {
        add("expenseCategory")
        add("suggestedCategoryName")
        add("classificationReason")
        add("defaultUnit")
      }
    }

    fun buildStructuredBillSchema(): JsonObject = buildJsonObject {
      put("type", "OBJECT")
      putJsonObject("properties") {
        putJsonObject("date") {
          put("type", "STRING")
          put("description", "Bill or receipt issue date in YYYY-MM-DD format.")
        }
        putJsonObject("storeName") {
          put("type", "STRING")
          put("description", "Store, merchant, or utility provider name shown on the bill.")
        }
        putJsonObject("totalAmount") {
          put("type", "NUMBER")
          put("description", "Final total amount payable on the scanned bill.")
        }
        putJsonObject("taxAmount") {
          put("type", "NUMBER")
          put("description", "VAT or tax amount if shown on the bill, otherwise 0.")
        }
        putJsonObject("subtotalAmount") {
          put("type", "NUMBER")
          put("description", "Subtotal amount before tax if shown, otherwise 0.")
        }
        putJsonObject("currency") {
          put("type", "STRING")
          put("description", "Currency symbol or ISO code such as ر.س or SAR.")
        }
        putJsonObject("invoiceNumber") {
          put("type", "STRING")
          put("description", "Invoice, receipt, or account number.")
        }
        putJsonObject("expenseCategory") {
          put("type", "STRING")
          put(
            "description",
            "Automatic expense classification based on storeName and bill content.",
          )
          putJsonArray("enum") { EXPENSE_TYPE_KEYS.forEach { add(it) } }
        }
        putJsonObject("detectedUtilityType") {
          put("type", "STRING")
          putJsonArray("enum") { EXPENSE_TYPE_KEYS.forEach { add(it) } }
        }
        putJsonObject("suggestedCategoryName") { put("type", "STRING") }
        putJsonObject("classificationReason") {
          put("type", "STRING")
          put("description", "Brief Arabic explanation for why this expense category was chosen.")
        }
        putJsonObject("consumptionValue") { put("type", "NUMBER") }
        putJsonObject("consumptionUnit") { put("type", "STRING") }
        putJsonObject("previousReading") { put("type", "NUMBER") }
        putJsonObject("currentReading") { put("type", "NUMBER") }
        putJsonObject("rawExtractedText") { put("type", "STRING") }
        putJsonObject("sheetRows") {
          put("type", "ARRAY")
          putJsonObject("items") {
            put("type", "OBJECT")
            putJsonObject("properties") {
              putJsonObject("label") { put("type", "STRING") }
              putJsonObject("value") { put("type", "STRING") }
              putJsonObject("unit") { put("type", "STRING") }
            }
            putJsonArray("required") {
              add("label")
              add("value")
              add("unit")
            }
          }
        }
      }
      putJsonArray("required") {
        add("date")
        add("storeName")
        add("totalAmount")
        add("taxAmount")
        add("subtotalAmount")
        add("currency")
        add("invoiceNumber")
        add("expenseCategory")
        add("detectedUtilityType")
        add("suggestedCategoryName")
        add("classificationReason")
        add("consumptionValue")
        add("consumptionUnit")
        add("previousReading")
        add("currentReading")
        add("rawExtractedText")
        add("sheetRows")
      }
    }

    fun extractJsonObjectSubstring(rawText: String): String {
      val unfenced =
        rawText
          .removePrefix("```json")
          .removePrefix("```JSON")
          .removePrefix("```")
          .removeSuffix("```")
          .trim()
      if (unfenced.startsWith("{") && unfenced.endsWith("}")) {
        return unfenced
      }
      val start = unfenced.indexOf('{')
      val end = unfenced.lastIndexOf('}')
      return if (start >= 0 && end > start) unfenced.substring(start, end + 1) else unfenced
    }

    fun Bitmap.toCompressedBase64(maxDimension: Int = 1600, quality: Int = 82): String {
      val scaled =
        if (width > maxDimension || height > maxDimension) {
          val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
          val targetW = (width * ratio).toInt().coerceAtLeast(1)
          val targetH = (height * ratio).toInt().coerceAtLeast(1)
          Bitmap.createScaledBitmap(this, targetW, targetH, true)
        } else {
          this
        }
      val outputStream = ByteArrayOutputStream()
      scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
      return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeOrientedBitmapFromUri(
      context: Context,
      uri: Uri,
      maxDimension: Int = 1600,
    ): Bitmap {
      val bytes =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: throw IllegalArgumentException("تعذر فتح ملف الصورة الملتقطة.")

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

      var sampleSize = 1
      while (
        bounds.outWidth / sampleSize > maxDimension ||
          bounds.outHeight / sampleSize > maxDimension
      ) {
        sampleSize *= 2
      }

      val decoded =
        BitmapFactory.decodeByteArray(
          bytes,
          0,
          bytes.size,
          BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IllegalArgumentException("صيغة الصورة الملتقطة غير مدعومة.")

      val rotationMatrix = readExifTransformMatrix(bytes) ?: return decoded
      return runCatching {
          Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, rotationMatrix, true)
        }
        .getOrDefault(decoded)
    }

    private fun readExifTransformMatrix(jpegBytes: ByteArray): Matrix? {
      val orientation =
        runCatching {
            ExifInterface(ByteArrayInputStream(jpegBytes))
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          }
          .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

      val matrix = Matrix()
      when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return null
      }
      return matrix
    }

    private fun buildCleanSummaryJson(dto: ExtractedBillDto): String {
      val compactObj = buildJsonObject {
        put("date", dto.resolvedDateIso)
        put("storeName", dto.resolvedStoreName)
        put("totalAmount", dto.totalAmount)
        put("taxAmount", dto.taxAmount)
        if (dto.subtotalAmount > 0.0) {
          put("subtotalAmount", dto.subtotalAmount)
        }
        put("currency", dto.currency)
        put("invoiceNumber", dto.invoiceNumber)
        put("expenseCategory", dto.resolvedExpenseType)
        put("detectedUtilityType", dto.resolvedExpenseType)
        put("suggestedCategoryName", dto.suggestedCategoryName)
        if (dto.classificationReason.isNotBlank()) {
          put("classificationReason", dto.classificationReason)
        }
        if (dto.consumptionValue > 0.0) {
          put("consumptionValue", dto.consumptionValue)
          put("consumptionUnit", dto.consumptionUnit)
        }
        putJsonArray("items") {
          dto.sheetRows.take(12).forEach { row ->
            add(
              buildJsonObject {
                put("label", row.label)
                put("value", row.value)
                if (row.unit.isNotBlank()) put("unit", row.unit)
              }
            )
          }
        }
      }
      return prettyJson.encodeToString(JsonObject.serializer(), compactObj)
    }
  }
}
