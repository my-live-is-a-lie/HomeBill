package com.example.data.remote

import android.util.Log
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.domain.MonthlyAnalyticsSummary
import com.example.util.DateAndFormatUtils
import java.io.IOException
import java.util.Locale
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
  ): Result<BillChatAiReplyDto> =
    withContext(Dispatchers.IO) {
      runCatching {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
          throw IllegalStateException(
            "لم يتم إضافة مفتاح Gemini الخاص بك بعد. أضفه من تبويب الإعدادات قبل استخدام المساعد الذكي."
          )
        }

        val systemContext = buildSystemInstruction(categories, allBills, analyticsSummary)

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
              parts = listOf(GeminiPart(text = userPrompt)),
            )

        // Use lightweight JSON MIME mode without heavy FSM responseSchema compilation to avoid 503 overload
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
              ),
          )

        val candidateModels = buildOrderedCandidateModels()
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
                generationConfig = GeminiGenerationConfig(temperature = 0.35f),
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

  private fun buildOrderedCandidateModels(): List<String> {
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

  private fun buildSystemInstruction(
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    summary: MonthlyAnalyticsSummary,
  ): String = buildString {
    appendLine("أنت «مساعد فواتيري الذكي» (مدعوم بـ Gemini)، مستشار مالي وتحليلي خبير داخل تطبيق أندرويد «فواتيري» لإدارة الفواتير المنزلية.")
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
    appendLine("""{"replyText":"الشرح بالعربية مع نقاط • واضحة وبدون **","highlights":[{"label":"عنوان المؤشر","value":"القيمة","trend":"+0%"}],"chartTitle":"عنوان الرسم البياني أو نص فارغ","chartBars":[{"label":"البند","value":120.5,"unit":"ر.س"}],"followUpSuggestions":["سؤال مقترح 1","سؤال مقترح 2"]}""")
    appendLine()

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

    if (allBills.isNotEmpty()) {
      appendLine()
      appendLine("=== سجل الفواتير التفصيلي (الأحدث أولاً، حتى 40 فاتورة) ===")
      allBills
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
    } else {
      appendLine()
      appendLine(
        "ملاحظة: المستخدم لم يسجل أي فواتير بعد؛ إذا سأل عن بياناته شجعه بلطف على إنشاء قسم أو مسح فاتورة بالكاميرا، مع الإجابة الكاملة عن أي استفسار حول التطبيق أو نصائح إدارة الفواتير."
      )
    }
  }

  companion object {
    private const val TAG = "GeminiBillChat"

    // Strictly approved Gemini models from gemini-api skill in priority failover order
    private val SUPPORTED_CHAT_MODELS =
      listOf(
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-pro-preview",
      )

    @Volatile private var lastHealthyChatModel: String? = null
  }
}
