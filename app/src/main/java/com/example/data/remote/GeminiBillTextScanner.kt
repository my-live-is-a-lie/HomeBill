package com.example.data.remote

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Turns a sentence the user dictated ("دفعت فاتورة الكهرباء ٣٥٠ جنيه اليوم") into the same editable
 * [ScannedBillDraft] that a scanned bill produces, so the user reviews and confirms it in the same
 * review form before anything is saved.
 *
 * It reuses the structured-bill JSON schema and the draft mapping of the image scanner; only the
 * input is different (text instead of a picture).
 */
class GeminiBillTextScanner(
  private val scanner: GeminiBillScanner = GeminiBillScanner(),
  private val service: GeminiApiService = GeminiApiClient.defaultService,
  // The user's own key, entered in Settings.
  private val apiKeyProvider: () -> String = { UserGeminiKeyStore.getKey() },
) {

  /**
   * @param spokenText what the user said, as transcribed.
   * @param todayIso today's date as yyyy-MM-dd, so "اليوم" / "أمس" / "الأسبوع الماضي" can be resolved.
   * @param defaultCurrency currency to use when the user does not mention one.
   * @param categoryNames names of the user's existing bill categories, so the bill can be filed
   *   under one of them when it matches.
   */
  suspend fun scanBillText(
    spokenText: String,
    todayIso: String,
    defaultCurrency: String,
    categoryNames: List<String>,
  ): Result<ScannedBillDraft> =
    withContext(Dispatchers.IO) {
      runCatching {
        val text = spokenText.trim()
        if (text.isEmpty()) {
          throw IllegalArgumentException("لم يصل أي كلام لتحويله إلى فاتورة.")
        }
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
          throw IllegalStateException(
            "لم يتم إضافة مفتاح Gemini الخاص بك بعد. أضفه من تبويب الإعدادات قبل إضافة الفواتير بالصوت."
          )
        }

        val userPrompt = buildString {
          appendLine("تاريخ اليوم: $todayIso")
          appendLine("العملة الافتراضية: ${defaultCurrency.ifBlank { "ر.س" }}")
          appendLine(
            "أقسام المستخدم الحالية: " +
              categoryNames.filter { it.isNotBlank() }.take(40).joinToString("، ").ifBlank { "لا يوجد" }
          )
          appendLine("جملة المستخدم المنطوقة:")
          appendLine("\"\"\"")
          appendLine(text.take(1500))
          append("\"\"\"")
        }

        val request =
          GenerateContentRequest(
            systemInstruction =
              GeminiContent(parts = listOf(GeminiPart(text = VOICE_BILL_SYSTEM_PROMPT))),
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
            generationConfig =
              GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f,
                responseSchema = GeminiApiClient.buildStructuredBillSchema(),
              ),
          )

        val rawJson = generateWithFailover(apiKey, request)
        val decoded =
          GeminiApiClient.lenientJson.decodeFromString<ExtractedBillDto>(
            GeminiApiClient.extractJsonObjectSubstring(rawJson)
          )

        val expense = decoded.resolvedExpenseType
        val dateIso = decoded.resolvedDateIso.ifBlank { todayIso }
        val storeName = decoded.resolvedStoreName
        val normalized =
          decoded.copy(
            date = dateIso,
            storeName = storeName,
            expenseCategory = expense,
            detectedUtilityType = expense,
            classificationReason = decoded.classificationReason.trim(),
            currency = decoded.currency.trim().ifBlank { defaultCurrency },
            // The "raw text" of a voice bill is simply what was said.
            rawExtractedText = text,
            billDateIso = dateIso,
            providerName = storeName,
          )
        val pretty =
          prettyJson.encodeToString(ExtractedBillDto.serializer(), normalized)
        scanner.mapJsonResultToDraft(ScannedBillJsonResult(data = normalized, formattedJson = pretty))
      }
    }

  private suspend fun generateWithFailover(apiKey: String, request: GenerateContentRequest): String {
    // A short sentence does not need the slowest model: the first three are enough.
    val models = GeminiApiClient.SUPPORTED_VISION_MODELS.take(3)
    var lastError: Throwable? = null
    for ((index, modelId) in models.withIndex()) {
      try {
        val response = service.generateContent(model = modelId, apiKey = apiKey, request = request)
        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        if (!text.isNullOrBlank()) return text
        lastError = IllegalStateException("رد Gemini فارغ")
      } catch (ce: CancellationException) {
        throw ce
      } catch (he: HttpException) {
        val code = he.code()
        if (code == 401 || code == 403) {
          throw IllegalStateException(
            "تعذر التحقق من صلاحية مفتاح Gemini الخاص بك (HTTP $code). تحقق من المفتاح في تبويب الإعدادات."
          )
        }
        lastError = he
        if (index < models.lastIndex) {
          delay(600L)
          continue
        }
      } catch (ioe: IOException) {
        lastError = ioe
        if (index < models.lastIndex) {
          delay(600L)
          continue
        }
      }
    }
    when ((lastError as? HttpException)?.code()) {
      429 ->
        throw IllegalStateException(
          "تم تجاوز الحد المؤقت للطلبات (429). انتظر ثواني قليلة ثم أعد المحاولة."
        )
      500,
      502,
      503,
      504 ->
        throw IllegalStateException("خدمة Gemini مشغولة مؤقتًا. أعد المحاولة بعد لحظات.")
    }
    if (lastError is IOException) {
      throw IllegalStateException("تعذّر الاتصال بـ Gemini، تحقق من الإنترنت ثم أعد المحاولة.")
    }
    throw IllegalStateException(lastError?.localizedMessage ?: "تعذر فهم الجملة المنطوقة كفاتورة.")
  }

  private companion object {
    val prettyJson: Json = Json {
      prettyPrint = true
      encodeDefaults = true
    }

    val VOICE_BILL_SYSTEM_PROMPT =
      """
      أنت مساعد يسجّل الفواتير والمصروفات المنزلية من جملة قالها المستخدم بصوته (بالفصحى أو بالعامية المصرية أو الخليجية)، ثم تحوّلها إلى JSON مهيكل.
      قواعد صارمة:
      1) لا تخترع أي معلومة لم ترد في الكلام. إن لم يُذكر المبلغ فاجعل totalAmount = 0، وكذلك كل رقم لم يُذكر يكون 0، وكل نص لم يُذكر يبقى نصاً فارغاً.
      2) الأرقام: حوّل الأرقام المنطوقة بالحروف إلى أرقام (مثل: «ثلاثمية وخمسين» = 350، «ألف ومية» = 1100، «اتنين ونص» = 2.5، «خمسمية» = 500) وافهم الأرقام الهندية (٣٥٠) واللاتينية (350). اكتب الأرقام في حقول NUMBER كأرقام عشرية عادية.
      3) التاريخ date بصيغة YYYY-MM-DD. استخدم «تاريخ اليوم» المعطى في الرسالة لحساب: اليوم، أمس/إمبارح، أول أمس، قبل ٣ أيام، الأسبوع الماضي، الشهر الماضي، يوم السبت الماضي، إلخ. وإن لم يُذكر تاريخ فاستخدم تاريخ اليوم.
      4) currency: جنيه أو جنيه مصري = ج.م، ريال أو ريال سعودي = ر.س، درهم = د.إ، دينار كويتي = د.ك، دولار = $. وإن لم تُذكر عملة فاستخدم «العملة الافتراضية» المعطاة.
      5) storeName: الجهة أو المتجر المذكور في الكلام (مثل: شركة الكهرباء، فودافون، كارفور). إن لم تُذكر جهة محددة فاكتب اسم الخدمة كما قالها المستخدم (مثل: الكهرباء) أو اتركه فارغاً.
      6) التصنيف expenseCategory وdetectedUtilityType (ضع نفس القيمة في الحقلين) من هذه القيم فقط:
         electricity (كهرباء)، water (مياه وصرف)، gas (غاز)، internet (إنترنت منزلي)، telecom (هاتف وباقات ورصيد موبايل)، shopping (تسوق ومقاضي وسوبرماركت وصيدلية)، dining (مطاعم ومقاهي)، maintenance (صيانة وإصلاح)، fuel (بنزين ومواصلات)، other (أي شيء آخر).
      7) suggestedCategoryName: إن كان أحد «أقسام المستخدم الحالية» يطابق المعنى فاستخدم اسمه حرفياً، وإلا فاختر اسماً عربياً قصيراً مناسباً (فاتورة الكهرباء، فاتورة الماء، فواتير التسوق، إلخ).
      8) classificationReason: جملة عربية قصيرة توضح سبب التصنيف (مثل: «ذكرت فاتورة الكهرباء»).
      9) consumptionValue وconsumptionUnit وpreviousReading وcurrentReading: فقط إذا ذكرها المستخدم (مثل «استهلكت ١٢٠ كيلو» أو «القراءة ٤٥٢٠»)، وإلا فهي 0 والوحدة نص فارغ.
      10) taxAmount وsubtotalAmount وinvoiceNumber: فقط إذا ذُكرت، وإلا فهي 0 أو نص فارغ.
      11) rawExtractedText: نص كلام المستخدم كما وصلك دون تعديل.
      12) sheetRows: صفّ لكل معلومة ذُكرت فعلاً (المبلغ، التاريخ، الجهة، ملاحظات مثل «مدفوعة نقداً»)؛ اكتب القيمة نصاً والوحدة في unit.
      """.trimIndent()
  }
}
