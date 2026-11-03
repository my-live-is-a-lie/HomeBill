package com.example.data.remote

import com.example.data.model.BillCategory
import com.example.data.model.BillRecord

/** How much bill data the next assistant turn needs. */
enum class ChatContextMode {
  /** General chat: no bill data at all (the reply stays JSON, safely). */
  MINIMAL,

  /** Currency setting: no bill data, but JSON mode for the action fields. */
  CURRENCY,

  /** One category only, with a few of its most recent bills. */
  TARGETED,

  /** Statistics: summary plus category aggregates, no bill details. */
  ANALYTICS,

  /** Full context (previous behaviour), used when the request is unclear. */
  FULL,
}

/**
 * What the service sends for one turn. [bills] is the aggregate source:
 * it may be large at zero token cost because only its sums and counts are
 * printed. [detailBills] is the list actually printed bill by bill, so it
 * is the list that must stay small.
 */
data class ChatContextSelection(
  val mode: ChatContextMode,
  val categories: List<BillCategory>,
  val bills: List<BillRecord>,
  val detailBills: List<BillRecord>,
  /** Waiting text that describes what this turn is actually doing. */
  val typingLabel: String,
)

/**
 * Picks the smallest bill context that can still answer the user's
 * question, so ordinary questions stop paying for (and waiting on) the
 * full 40-bill prompt. Safety rule: anything the selector cannot classify
 * confidently falls back to FULL — a slightly wasteful correct answer
 * beats a cheap hallucinated one.
 *
 * Matching is done on a normalized prompt (hamza/taa-marbuta/ya variants
 * unified, diacritics stripped) so spelling differences do not break
 * category detection.
 */
object ChatContextSelector {

  private const val FULL_TYPING_LABEL = "جارٍ تحليل بيانات الفواتير وإعداد الإجابة..."
  private const val ANALYTICS_TYPING_LABEL = "جارٍ تحليل بيانات الفواتير وإعداد الإجابة..."
  private const val TARGETED_TYPING_LABEL = "جارٍ جلب الفاتورة المطلوبة..."
  private const val LIST_TYPING_LABEL = "جارٍ جلب فواتيرك..."
  private const val CURRENCY_TYPING_LABEL = "جارٍ تجهيز إعداد العملة..."
  private const val MINIMAL_TYPING_LABEL = "جارٍ إعداد الإجابة..."

  // Utility-type key -> Arabic words that name it in a question. Mirrors the
  // vocabulary UtilityType.fromKey already recognises.
  private val TYPE_KEYWORDS =
    mapOf(
      "غاز" to "gas",
      "انبوبه" to "gas",
      "انابيب" to "gas",
      "كهرباء" to "electricity",
      "كهربا" to "electricity",
      "مياه" to "water",
      "ماء" to "water",
      "انترنت" to "internet",
      "النت" to "internet",
      "واي فاي" to "internet",
      "راوتر" to "internet",
      "اتصالات" to "telecom",
      "موبايل" to "telecom",
      "هاتف" to "telecom",
      "تسوق" to "shopping",
      "مقاضي" to "shopping",
      "سوبر ماركت" to "shopping",
      "مطعم" to "dining",
      "مطاعم" to "dining",
      "صيانه" to "maintenance",
      "تصليح" to "maintenance",
      "وقود" to "fuel",
      "بنزين" to "fuel",
      "سولار" to "fuel",
    )

  private val ANALYTICS_KEYWORDS =
    listOf(
      "احصائ",
      "مقارن",
      "متوسط",
      "اعلي",
      "اكثر",
      "اقل",
      "مجموع",
      "اجمال",
      "تفاوت",
      "ارتفاع",
      "انخفاض",
      "شهري",
      "رسم",
      "مخطط",
      "عدد",
      "كم",
      "توزيع",
      "تحليل",
      "تقرير",
    )

  private val BILL_TOKENS =
    listOf("فاتوره", "فواتير", "قسم", "اقسام", "استهلاك", "قراءه", "مبلغ", "تكلفه", "مصاريف")

  private val HELP_KEYWORDS = listOf("كيف", "طريقه", "شرح", "ازاي", "خطوات")

  private val CONFIRM_WORDS =
    setOf("نعم", "موافق", "اكيد", "تمام", "ايوه", "اي", "اوافق", "غيرها", "طبقها")

  private val REFUSE_WORDS =
    setOf("لا", "لا اريد", "مش موافق", "الغي", "الغاء", "لا تغير", "بلاش")

  fun select(
    userPrompt: String,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    pendingCurrencyProposal: CurrencyProposal?,
  ): ChatContextSelection {
    val prompt = normalize(userPrompt)

    // A pending currency proposal turns short confirmations and refusals
    // into currency turns, so the model can return the action fields.
    if (pendingCurrencyProposal != null) {
      val trimmed = prompt.trim()
      if (trimmed in CONFIRM_WORDS || trimmed in REFUSE_WORDS ||
        (trimmed.length <= 30 && (trimmed.startsWith("نعم ") || trimmed.startsWith("موافق ")))
      ) {
        return currencySelection()
      }
    }

    // Currency-setting requests never need bill data.
    if (
      prompt.contains("عمله") || prompt.contains("محل اقام") || prompt.contains("مقيم") ||
        prompt.contains("افتراضيه")
    ) {
      return currencySelection()
    }

    val isRecent = prompt.contains("اخر") || prompt.contains("احدث") || prompt.contains("جديد")
    val matchedCategory = matchCategory(prompt, categories)
    val isAnalytics = ANALYTICS_KEYWORDS.any { prompt.contains(it) }

    // "Latest bill" questions: print exactly one bill, never the whole category.
    if (isRecent && (matchedCategory != null || BILL_TOKENS.any { prompt.contains(it) })) {
      val latest = allBills.maxByOrNull { it.billDateEpochMillis }
      if (latest != null) {
        val owner = categories.firstOrNull { it.id == latest.categoryId }
        val ownerList = if (owner != null) listOf(owner) else emptyList()
        val ownerBills = if (owner != null) allBills.filter { it.categoryId == owner.id } else emptyList()
        return ChatContextSelection(
          ChatContextMode.TARGETED,
          ownerList,
          ownerBills,
          listOf(latest),
          TARGETED_TYPING_LABEL,
        )
      }
      if (matchedCategory != null) {
        val matchedBills = allBills.filter { it.categoryId == matchedCategory.id }
        return ChatContextSelection(
          ChatContextMode.TARGETED,
          listOf(matchedCategory),
          matchedBills,
          emptyList(),
          TARGETED_TYPING_LABEL,
        )
      }
    }

    if (matchedCategory != null) {
      val matchedBills = allBills.filter { it.categoryId == matchedCategory.id }
      if (isAnalytics) {
        // Statistics: aggregates only, no bill details.
        return ChatContextSelection(
          ChatContextMode.ANALYTICS,
          categories,
          allBills,
          emptyList(),
          ANALYTICS_TYPING_LABEL,
        )
      }
      val recent = recentBills(allBills, matchedCategory.id, limit = 5)
      return ChatContextSelection(
        ChatContextMode.TARGETED,
        listOf(matchedCategory),
        matchedBills,
        recent,
        TARGETED_TYPING_LABEL,
      )
    }

    if (isAnalytics) {
      return ChatContextSelection(
        ChatContextMode.ANALYTICS,
        categories,
        allBills,
        emptyList(),
        ANALYTICS_TYPING_LABEL,
      )
    }

    if (prompt.contains("فواتيري") || prompt.contains("كل الفواتير") || prompt.contains("قائمه الفواتير")) {
      val recent = allBills.sortedByDescending { it.billDateEpochMillis }.take(10)
      return ChatContextSelection(ChatContextMode.FULL, categories, allBills, recent, LIST_TYPING_LABEL)
    }

    // App-help questions need the role section only, not bill data.
    if (HELP_KEYWORDS.any { prompt.contains(it) }) {
      return minimalOrCurrency(pendingCurrencyProposal)
    }

    // Anything mentioning bills that was not classified above keeps full
    // context: a correct answer matters more than the tokens saved.
    if (BILL_TOKENS.any { prompt.contains(it) }) {
      return ChatContextSelection(ChatContextMode.FULL, categories, allBills, allBills, FULL_TYPING_LABEL)
    }

    return minimalOrCurrency(pendingCurrencyProposal)
  }

  private fun currencySelection(): ChatContextSelection {
    return ChatContextSelection(ChatContextMode.CURRENCY, emptyList(), emptyList(), emptyList(), CURRENCY_TYPING_LABEL)
  }

  private fun minimalOrCurrency(pendingCurrencyProposal: CurrencyProposal?): ChatContextSelection {
    // With a proposal outstanding, the model must still see it, so a plain
    // minimal turn (which drops the pending section) is never used.
    return if (pendingCurrencyProposal != null) {
      currencySelection()
    } else {
      ChatContextSelection(ChatContextMode.MINIMAL, emptyList(), emptyList(), emptyList(), MINIMAL_TYPING_LABEL)
    }
  }

  private fun matchCategory(prompt: String, categories: List<BillCategory>): BillCategory? {
    for (category in categories) {
      val name = normalize(category.name)
      if (name.isNotBlank() && prompt.contains(name)) {
        return category
      }
    }
    val typeKey = TYPE_KEYWORDS.entries.firstOrNull { (word, _) -> prompt.contains(word) }?.value
    if (typeKey != null) {
      return categories.firstOrNull { it.utilityType == typeKey }
    }
    return null
  }

  private fun recentBills(
    allBills: List<BillRecord>,
    categoryId: String,
    limit: Int,
  ): List<BillRecord> {
    return allBills
      .filter { it.categoryId == categoryId }
      .sortedByDescending { it.billDateEpochMillis }
      .take(limit)
  }

  private fun normalize(text: String): String {
    return text
      .lowercase()
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .filter { it !in '\u064B'..'\u0652' && it != '\u0670' }
      .split("\\s+".toRegex())
      .filter { it.isNotBlank() }
      .joinToString(" ")
  }
}
