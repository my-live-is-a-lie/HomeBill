package com.example.data.remote

import android.util.Log
import com.example.data.CurrencyPreferenceStore
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.domain.MonthlyAnalyticsSummary
import com.example.util.AssistantImageBridge
import com.example.util.DateAndFormatUtils
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class GeminiChatTurn(
  val role: String? = null,
  val parts: List<GeminiPart>,
)

@Serializable
data class GeminiChatRequest(
  val contents: List<GeminiChatTurn>,
  val generationConfig: GeminiGenerationConfig? = null,
  val systemInstruction: GeminiChatTurn? = null,
)

@Serializable
data class GeminiChatCandidate(
  val content: GeminiChatTurn? = null,
)

@Serializable
data class GeminiChatResponse(
  val candidates: List<GeminiChatCandidate> = emptyList(),
)

@Serializable
data class ChatStatHighlight(
  val label: String = "",
  val value: String = "",
  val trend: String = "",
)

@Serializable
data class ChatChartBar(
  val label: String = "",
  val value: Double = 0.0,
  val unit: String = "",
)

@Serializable
data class BillChatAiReplyDto(
  val replyText: String = "",
  val highlights: List<ChatStatHighlight> = emptyList(),
  val chartTitle: String = "",
  val chartBars: List<ChatChartBar> = emptyList(),
  val followUpSuggestions: List<String> = emptyList(),
  // Currency-setting action requested by the model ("" = none). Only the
  // values in KNOWN_CURRENCY_ACTIONS are honoured; anything else is dropped
  // in parseReplySafely, so a hallucinated action can never change a setting.
  val currencyAction: String = "",
  // Currency the model determined, used with "propose_currency" only:
  // currencyLabel is the currency name and country (for example
  // "جنيه مصري"), currencySymbol is its symbol (for example "ج.م").
  val currencyLabel: String = "",
  val currencySymbol: String = "",
)

/**
 * A currency the assistant proposed as the new default: its name and
 * country plus its symbol. The model determines both from its own
 * knowledge; the app stores no country or currency table for this.
 */
data class CurrencyProposal(
  val currencyLabel: String,
  val symbol: String,
)

interface GeminiChatApiService {
  @POST("v1beta/models/{model}:generateContent")
  suspend fun generateChatReply(
    @Path("model") model: String,
    @Query("key") apiKey: String,
    @Body request: GeminiChatRequest,
  ): GeminiChatResponse
}

class GeminiBillChatService(
  // The user's own key, entered in Settings. No developer key fallback here.
  private val apiKeyProvider: () -> String = { UserGeminiKeyStore.getKey() },
) {
  private val json = GeminiApiClient.lenientJson

  private val api: GeminiChatApiService by lazy {
    Retrofit.Builder()
      .baseUrl(GeminiApiClient.BASE_URL)
      .client(GeminiApiClient.sharedOkHttpClient)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
      .create(GeminiChatApiService::class.java)
  }

  suspend fun sendChatTurn(
    conversationHistory: List<Pair<Boolean, String>>,
    userPrompt: String,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
    // Null = automatic (current failover order). A set value is tried first;
    // the existing failover still applies to transient errors.
    preferredModel: String? = null,
    // Null = the model's own default thinking level. Otherwise "LOW",
    // "MEDIUM" or "HIGH", as chosen per conversation in the model settings.
    thinkingLevel: String? = null,
    // A currency proposal awaiting the user's confirmation (null = none).
    // It is shown to the model so a later "yes" can be recognised as
    // confirmation of this proposal and nothing else.
    pendingCurrencyProposal: CurrencyProposal? = null,
    // How much bill data this turn needs (see ChatContextSelector). FULL
    // preserves the previous behaviour of sending everything.
    contextMode: ChatContextMode = ChatContextMode.FULL,
    // Bills printed bill by bill. Defaults to allBills; analytics and
    // currency turns pass an empty list so only aggregates (or nothing)
    // are sent. Aggregates in the categories block always use allBills.
    detailBills: List<BillRecord> = allBills,
  ): Result<BillChatAiReplyDto> =
    withContext(Dispatchers.IO) {
      runCatching {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
          throw IllegalStateException(
            "لم يتم إضافة مفتاح Gemini الخاص بك بعد. أضفه من تبويب الإعدادات قبل استخدام المساعد الذكي."
          )
        }

        // A bill image the user sent from a bill screen (null = normal text-only chat).
        val attachedImageBase64 = AssistantImageBridge.currentAttachmentBase64()

        val systemContext =
          buildSystemInstruction(
            categories,
            allBills,
            detailBills,
            analyticsSummary,
            CurrencyPreferenceStore.getCurrency(),
            pendingCurrencyProposal,
            contextMode,
          ) +
            if (attachedImageBase64 != null) IMAGE_ATTACHMENT_NOTE else ""

        // Keep the latest 14 turns to respect context window limits and keep latency fast
        val recentTurns =
          conversationHistory.takeLast(14).map { (isUser, text) ->
            GeminiChatTurn(
              role = if (isUser) "user" else "model",
              parts = listOf(GeminiPart(text = text)),
            )
          }

        val allTurns =
          recentTurns +
            GeminiChatTurn(
              role = "user",
              parts =
                buildList {
                  add(GeminiPart(text = userPrompt))
                  if (attachedImageBase64 != null) {
                    add(
                      GeminiPart(
                        inlineData =
                          GeminiInlineData(mimeType = "image/jpeg", data = attachedImageBase64)
                      )
                    )
                  }
                },
            )

        // Use lightweight JSON MIME mode without heavy FSM responseSchema compilation to avoid 503 overload.
        // JSON mode is kept for every context mode, including MINIMAL:
        // a null responseMimeType would serialize as an explicit null
        // (lenientJson keeps explicitNulls on), which the API may reject,
        // and the saving that matters here is the prompt size, not the
        // small reply envelope.
        val jsonRequest =
          GeminiChatRequest(
            systemInstruction =
              GeminiChatTurn(
                parts = listOf(GeminiPart(text = systemContext)),
              ),
            contents = allTurns,
            generationConfig =
              GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3f,
                thinkingConfig = thinkingLevel?.let { GeminiThinkingConfig(thinkingLevel = it) },
              ),
          )

        val candidateModels = buildOrderedCandidateModels(preferredModel)
        var lastError: Throwable? = null

        for ((index, modelId) in candidateModels.withIndex()) {
          try {
            val response = api.generateChatReply(model = modelId, apiKey = apiKey, request = jsonRequest)
            val rawText =
              response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (!rawText.isNullOrBlank()) {
              lastHealthyChatModel = modelId
              return@runCatching parseReplySafely(rawText)
            }
          } catch (ce: CancellationException) {
            throw ce
          } catch (he: HttpException) {
            val code = he.code()
            Log.w(TAG, "Gemini chat model '$modelId' failed with HTTP $code", he)
            if (code == 401 || code == 403) {
              throw IllegalStateException(
                "تعذر التحقق من صلاحية مفتاح Gemini الخاص بك (HTTP $code). تحقق من المفتاح في تبويب الإعدادات."
              )
            }
            lastError = he
            if (isRetryableHttpCode(code) && index < candidateModels.lastIndex) {
              delay(backoffMillisForAttempt(index))
              continue
            }
          } catch (ioe: IOException) {
            Log.w(TAG, "Gemini chat network timeout/IO on '$modelId'", ioe)
            lastError = ioe
            if (index < candidateModels.lastIndex) {
              delay(700L)
              continue
            }
          }
        }

        // Final ultra-lightweight plain-text fallback if JSON mode encountered transient 503 across endpoints
        for (fallbackModel in listOf("gemini-flash-latest", "gemini-3.1-flash-lite-preview")) {
          try {
            delay(1000L)
            val plainRequest =
              jsonRequest.copy(
                // Keep the JSON mime type and thinking level, only soften
                // the temperature for this last-resort attempt.
                generationConfig = jsonRequest.generationConfig?.copy(temperature = 0.35f),
              )
            val response =
              api.generateChatReply(model = fallbackModel, apiKey = apiKey, request = plainRequest)
            val rawText =
              response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (!rawText.isNullOrBlank()) {
              lastHealthyChatModel = fallbackModel
              return@runCatching parseReplySafely(rawText)
            }
          } catch (ce: CancellationException) {
            throw ce
          } catch (t: Throwable) {
            lastError = t
          }
        }

        throw translateGeminiError(lastError)
      }
    }

  private fun buildOrderedCandidateModels(preferredModel: String? = null): List<String> {
    if (preferredModel != null && preferredModel in SUPPORTED_CHAT_MODELS) {
      return listOf(preferredModel) + SUPPORTED_CHAT_MODELS.filterNot { it == preferredModel }
    }
    val sticky = lastHealthyChatModel
    return if (sticky != null && sticky in SUPPORTED_CHAT_MODELS) {
      listOf(sticky) + SUPPORTED_CHAT_MODELS.filterNot { it == sticky }
    } else {
      SUPPORTED_CHAT_MODELS
    }
  }

  private fun isRetryableHttpCode(code: Int): Boolean =
    code == 503 || code == 429 || code == 500 || code == 502 || code == 504 || code == 404

  private fun backoffMillisForAttempt(attemptIndex: Int): Long =
    when (attemptIndex) {
      0 -> 650L
      1 -> 1100L
      else -> 1600L
    }

  private fun translateGeminiError(error: Throwable?): Exception {
    if (error is HttpException) {
      return when (error.code()) {
        503 ->
          IllegalStateException(
            "خدمة الذكاء الاصطناعي تواجه ضغطاً مؤقتاً حالياً (503). يرجى الضغط على «إعادة المحاولة» بعد لحظات."
          )
        429 ->
          IllegalStateException(
            "تم الوصول إلى الحد المؤقت للطلبات المتتالية (429). يرجى الانتظار ثواني قليلة ثم إعادة المحاولة."
          )
        400 ->
          IllegalStateException(
            "تعذر معالجة الطلب الحالي. تأكد من ضبط مفتاح Gemini الخاص بك في تبويب الإعدادات."
          )
        else ->
          IllegalStateException(
            "تعذر الاتصال بخدمة المساعد الذكي مؤقتاً (رمز ${error.code()}). يرجى إعادة المحاولة."
          )
      }
    }
    if (error is IOException) {
      return IllegalStateException(
        "انتهت مهلة الاتصال بالإنترنت أو الخادم. تحقق من الشبكة واضغط على «إعادة المحاولة»."
      )
    }
    return IllegalStateException(
      error?.localizedMessage ?: "تعذر استلام رد من المساعد الذكي، يرجى إعادة المحاولة."
    )
  }

  private fun parseReplySafely(rawText: String): BillChatAiReplyDto {
    val unfenced =
      rawText
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    val jsonSubstring =
      if (unfenced.startsWith("{") && unfenced.endsWith("}")) {
        unfenced
      } else {
        val start = unfenced.indexOf('{')
        val end = unfenced.lastIndexOf('}')
        if (start >= 0 && end > start) unfenced.substring(start, end + 1) else unfenced
      }

    val parsed = runCatching { json.decodeFromString<BillChatAiReplyDto>(jsonSubstring) }.getOrNull()
    if (parsed != null && parsed.replyText.isNotBlank()) {
      return parsed.copy(
        replyText = cleanMarkdownNoise(parsed.replyText),
        highlights =
          parsed.highlights.filter { it.label.isNotBlank() && it.value.isNotBlank() }.take(4),
        chartTitle = parsed.chartTitle.trim(),
        chartBars = parsed.chartBars.filter { it.label.isNotBlank() && it.value > 0.0 }.take(8),
        followUpSuggestions =
          parsed.followUpSuggestions.map { it.trim() }.filter { it.isNotBlank() }.take(3),
        currencyAction =
          parsed.currencyAction.trim().takeIf { it in KNOWN_CURRENCY_ACTIONS } ?: "",
        currencyLabel = parsed.currencyLabel.trim().take(60),
        currencySymbol = parsed.currencySymbol.trim().take(CurrencyPreferenceStore.MAX_CURRENCY_LENGTH),
      )
    }

    return BillChatAiReplyDto(
      replyText = cleanMarkdownNoise(unfenced),
      followUpSuggestions =
        listOf(
          "اعمل لي إحصائية شهرية لفواتيري",
          "ما أعلى قسم استهلاكاً وتكلفة؟",
        ),
    )
  }

  private fun cleanMarkdownNoise(text: String): String {
    return text.replace("**", "").replace("### ", "").replace("## ", "").trim()
  }

  /**
   * The user's current date, time, time zone and country, read from the phone. The model has no
   * clock and no location of its own, so without this block it invents an answer to "what time is
   * it?" or "which country am I in?". The country is derived from the phone's time zone (more
   * reliable than the language setting), with the language region as a fallback.
   */
  private fun buildDeviceContext(): String {
    val now = System.currentTimeMillis()
    val zone = TimeZone.getDefault()
    val arabic = Locale.forLanguageTag("ar")

    val offsetMinutes = zone.getOffset(now) / 60_000
    val absOffset = abs(offsetMinutes)
    val utcOffset =
      String.format(
        Locale.US,
        "UTC%s%02d:%02d",
        if (offsetMinutes < 0) "-" else "+",
        absOffset / 60,
        absOffset % 60,
      )

    val dateText =
      SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(now))
    val dayName = SimpleDateFormat("EEEE", arabic).apply { timeZone = zone }.format(Date(now))

    val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
    val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    val period = if (hour24 < 12) "صباحاً" else "مساءً"
    val clock24 = String.format(Locale.US, "%02d:%02d", hour24, minute)
    val clock12 = String.format(Locale.US, "%d:%02d %s", hour12, minute, period)

    val region =
      runCatching { android.icu.util.TimeZone.getRegion(zone.id) }
        .getOrNull()
        ?.takeIf { it.length == 2 && it != "ZZ" }
        ?: Locale.getDefault().country.takeIf { it.length == 2 }
    val countryName =
      region
        ?.let { code ->
          runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(arabic) }
            .getOrNull()
        }
        ?.takeIf { it.isNotBlank() }

    return buildString {
      appendLine("=== الوقت والمكان الآن (مأخوذة من هاتف المستخدم، مرجع موثوق) ===")
      appendLine("- التاريخ: $dateText ($dayName)")
      appendLine("- الوقت المحلي الحالي: $clock24 (أي $clock12)")
      appendLine("- المنطقة الزمنية: ${zone.id} ($utcOffset)")
      if (countryName != null) {
        appendLine("- بلد المستخدم حسب منطقة هاتفه الزمنية: $countryName")
      }
      appendLine()
    }
  }

  private fun buildSystemInstruction(
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    detailBills: List<BillRecord>,
    summary: MonthlyAnalyticsSummary,
    currentDefaultCurrency: String,
    pendingCurrencyProposal: CurrencyProposal?,
    contextMode: ChatContextMode,
  ): String = buildString {
    appendLine("أنت «المساعد الذكي» (مدعوم بـ Gemini)، مستشار مالي وتحليلي خبير داخل تطبيق أندرويد «فواتيري» لإدارة الفواتير المنزلية.")
    appendLine("مهمتك الأساسية:")
    appendLine("1) الإجابة بالعربية الواضحة والمنظمة عن أي سؤال يخص فواتير ومصاريف المستخدم المسجلة (كهرباء، مياه، غاز، إنترنت، اتصالات، تسوق ومقاضي، مطاعم، صيانة، وقود، وغيرها).")
    appendLine("2) إعداد إحصائيات دقيقة ومقارنات شهرية وتحليل أسباب التفاوت في القيمة المالية أو كمية الاستهلاك بناءً على البيانات الفعلية أدناه فقط دون اختلاق أرقام غير موجودة.")
    appendLine("3) عندما يطلب المستخدم إحصائية أو مقارنة رقمية أو تحليلاً للبيانات، قم بتعبئة مصفوفة highlights (بأهم المؤشرات الرقمية) ومصفوفة chartBars مع chartTitle (برسم بياني مبسط للقيم المقارنة) إلى جانب الشرح النصي في replyText.")
    appendLine("4) مساعدة المستخدم في شرح مزايا التطبيق وكيفية استخدامها:")
    appendLine("   - تبويب «الفواتير»: إنشاء أقسام مخصصة باسم حر (مثل فاتورة الغاز أو فواتير التسوق) وتحديد وحدة القياس ونسبة تنبيه التفاوت (مثلاً 25%)، والدخول لأي قسم لإضافة فواتير شهرية بتاريخ مستقل أو تعديلها.")
    appendLine("   - تبويب «مسح فاتورة»: تصوير الفاتورة عبر ماسح CameraX المدمج أو اختيار صورة من المعرض؛ يقوم Gemini باستخراج النص الكامل والتصنيف التلقائي لنوع المصروف (كهرباء، مياه، تسوق، مطاعم، صيانة، وقود، غاز، إنترنت، اتصالات) بناءً على اسم المتجر والمحتوى، وبناء جدول تفاصيل متكامل (Sheet) وحفظه تلقائياً في القسم المناسب.")
    appendLine("   - ميزة الـ Sheet والتصدير: كل فاتورة تحتوي على جدول بنود تفصيلي (Sheet) يمكن استعراضه ومقارنته بنداً ببند مع الفاتورة السابقة وتصديره كملف CSV متوافق مع Excel وGoogle Sheets.")
    appendLine("   - تبويب «الإحصائيات»: يعرض مخطط الأعمدة الشهري المكدس، ومخطط التوزيع الدائري للمرافق، ومنحنى تتبع التكلفة والاستهلاك لكل فئة.")
    appendLine("5) أعد الإجابة دائماً ككائن JSON صالح واحد فقط بالهيكل التالي دون نص خارجي:")
    appendLine("""{"replyText":"الشرح بالعربية مع نقاط • واضحة وبدون **","highlights":[{"label":"عنوان المؤشر","value":"القيمة","trend":"+0%"}],"chartTitle":"عنوان الرسم البياني أو نص فارغ","chartBars":[{"label":"البند","value":120.5,"unit":"$currentDefaultCurrency"}],"followUpSuggestions":["سؤال مقترح 1","سؤال مقترح 2"],"currencyAction":"","currencyLabel":"","currencySymbol":""}""")
    appendLine()
    // Current date, time, time zone and country: always sent, whatever the context mode.
    appendLine(buildDeviceContext())
    appendLine("=== الأسئلة العامة (غير المتعلقة بالفواتير) ===")
    appendLine("- أجب عن الأسئلة العامة البسيطة (الوقت، التاريخ، اليوم، البلد، الحساب، المعلومات العامة) إجابة مباشرة وصحيحة وقصيرة، ثم اعرض المساعدة في الفواتير بجملة قصيرة فقط عند الحاجة دون تكرار تعريف نفسك أو دورك في كل رد.")
    appendLine("- في أسئلة الوقت والتاريخ والبلد اعتمد حصراً على كتلة «الوقت والمكان الآن» أعلاه، ولا تخمّن ولا تستنتج الوقت أو البلد من العملة أو من لغة المحادثة. وإذا تعارض ما قيل سابقاً في المحادثة مع هذه الكتلة فالكتلة هي الصحيحة.")
    appendLine("- إذا قال لك المستخدم إن معلومة خاطئة، أعد الحساب من الكتلة المذكورة وأجب بالقيمة الصحيحة مباشرة دون اعتذار مطوّل.")
    appendLine("- لا تملك وصولاً للإنترنت: لا تدّعِ معرفة الطقس أو الأسعار الحية أو الأخبار الحالية؛ قل ذلك بوضوح واقترح مصدراً مناسباً.")
    appendLine()
    if (contextMode != ChatContextMode.MINIMAL) {
      appendLine("=== تغيير العملة الافتراضية من المحادثة ===")
      appendLine("عندما يطلب المستخدم ضبط العملة الافتراضية لفواتيره بناءً على محل إقامته:")
      appendLine("- إذا لم يذكر دولة بعينها: أجب بجملة قصيرة أنك ستحددها من محل إقامته، واضبط currencyAction على \"resolve_by_residence\"، ولا تدّعِ أن الإعداد تغيّر.")
      appendLine("- إذا ذكر دولة بعينها، أو إذا أُعطيت لك دولة في الرسالة الحالية (مثل نتيجة تحديد الموقع من عنوان IP): حدّد عملتها المحلية من معرفتك أنت، واضبط currencyAction على \"propose_currency\"، واملأ currencyLabel باسم العملة والدولة وcurrencySymbol برمزها. مثال على الصيغة المطلوبة: (جنيه مصري ج.م).")
      appendLine("- لا تغيّر أي إعداد أبدًا من تلقاء نفسك، ولا تضبط currencyAction على \"apply_currency_change\" إلا عندما يكون هناك اقتراح معلّق مذكور أدناه ويوافق عليه المستخدم صراحة في رسالته الحالية (نعم، موافق، أكيد...).")
      appendLine("- عندما يرفض المستخدم الاقتراح المعلّق أو يذكر دولة مختلفة: اضبط currencyAction على \"cancel_currency_change\" (ومع دولة جديدة أعد \"propose_currency\").")
      appendLine("- عند ضبط أي currencyAction، اجعل replyText جملة قصيرة فقط ولا تدّعِ فيه أن الإعداد تغيّر؛ فالتطبيق هو من يطبّق التغيير ويؤكّده برسالة مستقلة.")
    }
    if (pendingCurrencyProposal != null && contextMode != ChatContextMode.MINIMAL) {
      appendLine()
      appendLine("=== اقتراح عملة معلّق بانتظار تأكيد المستخدم ===")
      appendLine("- العملة المقترحة: ${pendingCurrencyProposal.currencyLabel} (${pendingCurrencyProposal.symbol})")
      appendLine("- لا تطبّق هذا الاقتراح إلا إذا وافق المستخدم عليه صراحة في رسالته الحالية؛ أي رد آخر (سؤال، رفض، دولة مختلفة) ليس موافقة.")
    }
    appendLine()
    if (contextMode != ChatContextMode.MINIMAL) {
      appendLine("- العملة الافتراضية الحالية في الإعدادات: $currentDefaultCurrency")
    }

    if (contextMode == ChatContextMode.ANALYTICS || contextMode == ChatContextMode.FULL) {
      appendLine("=== ملخص بيانات المستخدم الحية في التطبيق الآن ===")
      appendLine("- العملة الأساسية: ${summary.primaryCurrency}")
    appendLine("- عدد الأقسام المسجلة: ${categories.size}")
    appendLine("- إجمالي عدد الفواتير المسجلة: ${allBills.size}")
    appendLine("- إجمالي الشهر الحالي/الأحدث: ${DateAndFormatUtils.formatMoney(summary.currentMonthTotal)} ${summary.primaryCurrency}")
    appendLine("- المتوسط الشهري العام: ${DateAndFormatUtils.formatMoney(summary.averageMonthlyCost)} ${summary.primaryCurrency}")
    summary.monthOverMonthChangePercent?.let { mom ->
      appendLine("- نسبة التغير عن الشهر السابق: ${String.format(Locale.US, "%+.1f%%", mom)}")
    }
    appendLine("- الفئة الأعلى تكلفة: ${summary.highestCategoryName}")
    appendLine("- عدد تنبيهات التفاوت النشطة: ${summary.activeAlerts.size}")

    if (summary.activeAlerts.isNotEmpty()) {
      appendLine()
      appendLine("=== تنبيهات التفاوت المرتفع النشطة ===")
      summary.activeAlerts.forEach { alert ->
        val amtPct =
          alert.comparison.amountChangePercent?.let { String.format(Locale.US, "%+.1f%%", it) }
            ?: "غير متاح"
        val consPct =
          alert.comparison.consumptionChangePercent?.let { String.format(Locale.US, "%+.1f%%", it) }
            ?: "غير متاح"
        appendLine(
          "• قسم «${alert.category.name}» بتاريخ ${alert.bill.billDateIso}: المبلغ ${DateAndFormatUtils.formatMoney(alert.bill.totalAmount)} ${alert.bill.currency} (تغير المبلغ: $amtPct، تغير الاستهلاك: $consPct)"
        )
      }
    }

    if (summary.monthlyPoints.isNotEmpty()) {
      appendLine()
      appendLine("=== المجاميع الشهرية المسجلة ===")
      summary.monthlyPoints.forEach { pt ->
        appendLine(
          "• شهر ${pt.yearMonth} (${pt.fullLabel}): ${DateAndFormatUtils.formatMoney(pt.totalAmount)} ${summary.primaryCurrency}"
        )
      }
    }
    }

    if (categories.isNotEmpty()) {
      appendLine()
      appendLine("=== الأقسام وفئات الفواتير ===")
      categories.forEach { cat ->
        val catBills = allBills.filter { it.categoryId == cat.id }
        val sumAmt = catBills.sumOf { it.totalAmount }
        val avgAmt = if (catBills.isNotEmpty()) sumAmt / catBills.size else 0.0
        val uType = UtilityType.fromKey(cat.utilityType).firestoreKey
        appendLine(
          "• القسم: «${cat.name}» | النوع: $uType | الوحدة: ${cat.defaultUnit} | حد التنبيه: ${cat.alertThresholdPercent.toInt()}% | عدد الفواتير: ${catBills.size} | الإجمالي: ${DateAndFormatUtils.formatMoney(sumAmt)} ${summary.primaryCurrency} | المتوسط: ${DateAndFormatUtils.formatMoney(avgAmt)} ${summary.primaryCurrency}"
        )
      }
    }

    if (
      (contextMode == ChatContextMode.FULL || contextMode == ChatContextMode.TARGETED) &&
        detailBills.isNotEmpty()
    ) {
      appendLine()
      appendLine("=== سجل الفواتير التفصيلي (الأحدث أولاً، حتى 40 فاتورة) ===")
      detailBills
        .sortedByDescending { it.billDateEpochMillis }
        .take(40)
        .forEach { bill ->
          val sheetPreview =
            if (bill.sheetItems.isNotEmpty()) {
              bill.sheetItems
                .take(8)
                .joinToString(" ، ") { "${it.label}: ${it.value} ${it.unit}".trim() }
            } else {
              "لا توجد بنود إضافية"
            }
          appendLine(
            "• [${bill.billDateIso}] قسم «${bill.categoryName}» | المبلغ: ${DateAndFormatUtils.formatMoney(bill.totalAmount)} ${bill.currency} | الاستهلاك: ${DateAndFormatUtils.formatNumber(bill.consumptionValue)} ${bill.consumptionUnit} | القراءات: (${DateAndFormatUtils.formatNumber(bill.previousReading)} -> ${DateAndFormatUtils.formatNumber(bill.currentReading)}) | الجهة: ${bill.providerName.ifBlank { "—" }} | بنود الـ Sheet: [$sheetPreview]"
          )
        }
    } else if (contextMode == ChatContextMode.FULL && allBills.isEmpty()) {
      appendLine()
      appendLine(
        "ملاحظة: المستخدم لم يسجل أي فواتير بعد؛ إذا سأل عن بياناته شجعه بلطف على إنشاء قسم أو مسح فاتورة بالكاميرا، مع الإجابة الكاملة عن أي استفسار حول التطبيق أو نصائح إدارة الفواتير."
      )
    }
  }

  companion object {
    private const val TAG = "GeminiBillChat"

    // The only currency actions the app honours from a model reply.
    private val KNOWN_CURRENCY_ACTIONS =
      setOf(
        "resolve_by_residence",
        "propose_currency",
        "apply_currency_change",
        "cancel_currency_change",
      )

    // Appended to the system instruction when the user sent a bill image to the assistant.
    private const val IMAGE_ATTACHMENT_NOTE =
      "\n\n=== صورة فاتورة مرفقة ===\nأرفق المستخدم صورة فاتورة مع رسالته الحالية. أجب عن أسئلته اعتمادًا على ما تراه فعليًا في الصورة (النصوص والأرقام والبنود)، واذكر بوضوح إن كان جزء من الصورة غير واضح أو لا يمكن قراءته بثقة بدل التخمين، وقارنها ببيانات فواتيره المسجلة أعلاه عند الحاجة. التزم بنفس هيكل JSON المطلوب."

    // Strictly approved Gemini models from gemini-api skill in priority failover order
    private val SUPPORTED_CHAT_MODELS =
      listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-pro-preview",
      )

    @Volatile private var lastHealthyChatModel: String? = null
  }
}
