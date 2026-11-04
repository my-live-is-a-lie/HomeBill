package com.example.data.remote

import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import java.util.Calendar
import java.util.Locale

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
 *
 * A question that names a period (a month, "last 3 months", "this year",
 * "summer"...) gets only the bills inside that period, optionally limited
 * to the category it names.
 */
object ChatContextSelector {

  private const val FULL_TYPING_LABEL = "جارٍ تحليل بيانات الفواتير وإعداد الإجابة..."
  private const val ANALYTICS_TYPING_LABEL = "جارٍ تحليل بيانات الفواتير وإعداد الإجابة..."
  private const val TARGETED_TYPING_LABEL = "جارٍ جلب الفاتورة المطلوبة..."
  private const val RANGE_TYPING_LABEL = "جارٍ جلب فواتير الفترة المطلوبة..."
  private const val LIST_TYPING_LABEL = "جارٍ جلب فواتيرك..."
  private const val CURRENCY_TYPING_LABEL = "جارٍ تجهيز إعداد العملة..."
  private const val MINIMAL_TYPING_LABEL = "جارٍ إعداد الإجابة..."

  /** Most bills printed for one period question (the service prints at most 40 anyway). */
  private const val MAX_RANGE_BILLS = 30

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

  /**
   * Words that show the question is about what is INSIDE a bill (its line items, readings, fees,
   * provider). Only then are the full per-bill details worth sending when many bills are listed.
   */
  private val ITEM_DETAIL_KEYWORDS =
    listOf(
      "بند",
      "بنود",
      "تفصيل",
      "تفاصيل",
      "رسوم",
      "ضريب",
      "غرام",
      "جهه",
      "مزود",
      "مصدر",
      "قراءه",
      "عداد",
      "شيت",
      "sheet",
    )

  private val CONFIRM_WORDS =
    setOf("نعم", "موافق", "اكيد", "تمام", "ايوه", "اي", "اوافق", "غيرها", "طبقها")

  private val REFUSE_WORDS =
    setOf("لا", "لا اريد", "مش موافق", "الغي", "الغاء", "لا تغير", "بلاش")

  // Month names (Gregorian: Egyptian/Gulf and Levantine spellings), normalized like the prompt.
  // Two-word names come first so "كانون الثاني" is never read as something shorter.
  private val MONTH_NAMES =
    listOf(
      "كانون الثاني" to 1,
      "كانون الاول" to 12,
      "تشرين الاول" to 10,
      "تشرين الثاني" to 11,
      "يناير" to 1,
      "فبراير" to 2,
      "مارس" to 3,
      "ابريل" to 4,
      "مايو" to 5,
      "يونيو" to 6,
      "يوليو" to 7,
      "اغسطس" to 8,
      "سبتمبر" to 9,
      "اكتوبر" to 10,
      "نوفمبر" to 11,
      "ديسمبر" to 12,
      "شباط" to 2,
      "اذار" to 3,
      "نيسان" to 4,
      "ايار" to 5,
      "حزيران" to 6,
      "تموز" to 7,
      "ايلول" to 9,
    )

  private val NUMBER_WORDS =
    mapOf(
      "ثلاثه" to 3,
      "ثلاث" to 3,
      "اربعه" to 4,
      "اربع" to 4,
      "خمسه" to 5,
      "خمس" to 5,
      "سته" to 6,
      "ست" to 6,
    )

  /** An inclusive period in ISO dates (yyyy-MM-dd), compared as text against a bill's billDateIso. */
  private data class DateRange(val fromIso: String, val toIso: String)

  /** Short explicit confirmations ("yes", "agreed") that answer a pending question. */
  fun isConfirmationPrompt(prompt: String): Boolean {
    val trimmed = normalize(prompt).trim()
    return trimmed in CONFIRM_WORDS ||
      (trimmed.length <= 30 && (trimmed.startsWith("نعم ") || trimmed.startsWith("موافق ")))
  }

  /**
   * Whether a message, sent while a currency proposal is pending, should
   * apply that proposal directly in the app instead of going to the model.
   * The first word must be a confirmation or an explicit add/set word
   * ("نعم", "أضفها", "عيّنها", ...). Word-matched, not substring, so
   * "التطبيق" never matches "طبّق". Guarded by: no bill words (adding a
   * bill is not confirming a currency), no refusal word, no question
   * mark, short text.
   */
  fun isCurrencyApplyIntent(prompt: String): Boolean {
    val cleaned = normalize(prompt).filter { it !in setOf('،', ',', '.', '؟', '?', '!', ':') }.trim()
    if (cleaned.isBlank() || cleaned.length > 40) return false
    if (BILL_TOKENS.any { cleaned.contains(it) }) return false
    val words = cleaned.split(" ").filter { it.isNotBlank() }
    if (words.any { it == "لا" }) return false
    val applyWords =
      setOf(
        "نعم", "موافق", "اكيد", "تمام", "ايوه", "اي", "اوافق", "حسنا", "طيب",
        "اضف", "اضفها", "عين", "عينها", "غير", "غيرها", "طبق", "طبقها",
        "نفذ", "نفذها", "ثبت", "ثبتها",
      )
    return words.firstOrNull() in applyWords
  }

  /** Whether model prose claims a currency change or addition was completed. */
  fun claimsCurrencyChange(replyText: String): Boolean {
    val normalized = normalize(replyText)
    return normalized.contains("تم تحديد") ||
      normalized.contains("تم تعيين") ||
      normalized.contains("تم تغيير") ||
      normalized.contains("تمت اضافه") ||
      normalized.contains("تم اضافه") ||
      normalized.contains("قمت باضافه") ||
      normalized.contains("قمت بتعيين") ||
      normalized.contains("قمت بتغيير")
  }

  /**
   * Whether a message looks like a country name given after the app asked
   * for one (for example "قطر" or "أقيم في مصر"): short, and not a bill
   * question, a statistic, a help question, or a confirmation/refusal.
   * Used only while the app is awaiting a country name; anything else
   * clears that wait and is classified normally.
   */
  fun isCountryLikeAnswer(prompt: String): Boolean {
    val normalized = normalize(prompt).trim()
    if (normalized.isBlank()) return false
    if (normalized.split(" ").size > 5) return false
    if (isConfirmationPrompt(normalized)) return false
    if (normalized in REFUSE_WORDS) return false
    if (BILL_TOKENS.any { normalized.contains(it) }) return false
    if (ANALYTICS_KEYWORDS.any { normalized.contains(it) }) return false
    if (HELP_KEYWORDS.any { normalized.contains(it) }) return false
    if (normalized.contains("فواتيري") || normalized.contains("كل الفواتير")) return false
    return true
  }

  /**
   * Whether the question is about what is inside a bill (items, readings, fees, provider). When
   * many bills are listed, only such questions get the full per-bill details; the others get a
   * short line per bill (date, category, amount, consumption).
   */
  fun needsItemDetails(prompt: String): Boolean {
    val normalized = normalize(prompt)
    return ITEM_DETAIL_KEYWORDS.any { normalized.contains(it) }
  }

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
      if (isConfirmationPrompt(prompt) || trimmed in REFUSE_WORDS) {
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

    val matchedCategory = matchCategory(prompt, categories)

    // A question about a period ("September", "last 3 months", "this year", "summer"): only the
    // bills inside it, limited to the named category when there is one. This comes before the
    // "latest bill" and statistics branches because "آخر 3 أشهر" is a period, not one bill,
    // and a statistic over a period needs those bills one by one.
    val range = detectDateRange(userPrompt)
    if (range != null) {
      val scopeBills =
        if (matchedCategory != null) allBills.filter { it.categoryId == matchedCategory.id } else allBills
      val inRange =
        scopeBills
          .filter { it.billDateIso >= range.fromIso && it.billDateIso <= range.toIso }
          .sortedByDescending { it.billDateEpochMillis }
          .take(MAX_RANGE_BILLS)
      return ChatContextSelection(
        ChatContextMode.TARGETED,
        if (matchedCategory != null) listOf(matchedCategory) else categories,
        scopeBills,
        inRange,
        RANGE_TYPING_LABEL,
      )
    }

    val isRecent = prompt.contains("اخر") || prompt.contains("احدث") || prompt.contains("جديد")
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

  /**
   * The period a question names, or null when it names none. Understood, in this order: a month
   * name (with an optional year), "last N months" (آخر 3 أشهر / آخر شهرين), this month, last
   * month, this year, last year, a bare year such as 2025, and summer. A month name without a year
   * means its most recent occurrence.
   */
  private fun detectDateRange(rawPrompt: String, now: Calendar = Calendar.getInstance()): DateRange? {
    val prompt = normalize(toAsciiDigits(rawPrompt))
    val curYear = now.get(Calendar.YEAR)
    val curMonth = now.get(Calendar.MONTH) + 1
    val explicitYear = Regex("20\\d\\d").find(prompt)?.value?.toIntOrNull()

    // 1. A named month.
    val named = MONTH_NAMES.firstOrNull { (name, _) -> prompt.contains(name) }
    if (named != null) {
      val month = named.second
      val year = explicitYear ?: if (month <= curMonth) curYear else curYear - 1
      return monthRange(year, month)
    }

    // 2. "Last N months": a number or number word followed by a month word.
    val lastN =
      Regex("اخر\\s+(?:(\\d+)\\s*(?:اشهر|شهور|شهر)|(شهرين)|(ثلاثه|ثلاث|اربعه|اربع|خمسه|خمس|سته|ست)\\s*(?:اشهر|شهور|شهر))")
        .find(prompt)
    if (lastN != null) {
      val digits = lastN.groupValues[1]
      val months =
        when {
          digits.isNotEmpty() -> digits.toIntOrNull()
          lastN.groupValues[2].isNotEmpty() -> 2
          else -> NUMBER_WORDS[lastN.groupValues[3]]
        }
      if (months != null && months in 1..24) {
        val start = (now.clone() as Calendar).apply { add(Calendar.MONTH, -months) }
        return DateRange(isoDate(start), isoDate(now))
      }
    }

    // 3. This month / last month.
    if (
      prompt.contains("الشهر الماضي") || prompt.contains("الشهر السابق") ||
        prompt.contains("الشهر اللي فات")
    ) {
      val year = if (curMonth == 1) curYear - 1 else curYear
      val month = if (curMonth == 1) 12 else curMonth - 1
      return monthRange(year, month)
    }
    if (
      prompt.contains("هذا الشهر") || prompt.contains("الشهر الحالي") ||
        prompt.contains("الشهر دا") || prompt.contains("الشهر ده")
    ) {
      return monthRange(curYear, curMonth)
    }

    // 4. Years.
    if (
      prompt.contains("السنه الماضيه") || prompt.contains("العام الماضي") ||
        prompt.contains("السنه السابقه") || prompt.contains("العام السابق")
    ) {
      return yearRange(curYear - 1)
    }
    if (
      prompt.contains("هذه السنه") || prompt.contains("هذا العام") ||
        prompt.contains("السنه دي") || prompt.contains("السنه الحاليه") ||
        prompt.contains("العام الحالي")
    ) {
      return yearRange(curYear)
    }
    if (explicitYear != null) {
      return yearRange(explicitYear)
    }

    // 5. Summer (June to August) of its most recent occurrence.
    if (prompt.contains("الصيف")) {
      val year = if (curMonth >= 6) curYear else curYear - 1
      return DateRange("$year-06-01", "$year-08-31")
    }

    return null
  }

  private fun monthRange(year: Int, month: Int): DateRange {
    val from = String.format(Locale.US, "%04d-%02d-01", year, month)
    // Day 31 is only an upper bound for the text comparison; no month has later dates.
    val to = String.format(Locale.US, "%04d-%02d-31", year, month)
    return DateRange(from, to)
  }

  private fun yearRange(year: Int): DateRange =
    DateRange(String.format(Locale.US, "%04d-01-01", year), String.format(Locale.US, "%04d-12-31", year))

  private fun isoDate(calendar: Calendar): String =
    String.format(
      Locale.US,
      "%04d-%02d-%02d",
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH) + 1,
      calendar.get(Calendar.DAY_OF_MONTH),
    )

  /** Arabic-Indic digits (٠-٩) to ASCII, so "٢٠٢٥" and "٣ أشهر" are understood. */
  private fun toAsciiDigits(text: String): String =
    buildString(text.length) {
      for (ch in text) {
        append(if (ch in '\u0660'..'\u0669') '0' + (ch - '\u0660') else ch)
      }
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
