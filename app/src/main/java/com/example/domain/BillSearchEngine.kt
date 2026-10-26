package com.example.domain

import androidx.annotation.StringRes
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.util.DateAndFormatUtils
import java.util.Locale

enum class BillSearchScope(@param:StringRes val labelRes: Int) {
  ALL(R.string.bill_search_scope_all),
  STORE_NAME(R.string.bill_search_scope_store),
  DATE(R.string.bill_search_scope_date),
  CATEGORY(R.string.bill_search_scope_category),
}

object BillSearchEngine {

  private val arabicDiacriticsRegex = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
  private val multiSpaceRegex = Regex("\\s+")

  private val utilityKeywordsMap: Map<UtilityType, List<String>> =
    mapOf(
      UtilityType.GAS to listOf("الغاز", "غاز", "فاتورة الغاز", "gas"),
      UtilityType.ELECTRICITY to
        listOf("الكهرباء", "كهرباء", "فاتورة الكهرباء", "electricity", "power"),
      UtilityType.WATER to listOf("الماء", "ماء", "المياه", "مياه", "فاتورة الماء", "water"),
      UtilityType.INTERNET to
        listOf("الإنترنت", "انترنت", "فاتورة الإنترنت", "internet", "wifi", "fiber", "فايبر"),
      UtilityType.TELECOM to
        listOf("الاتصالات", "اتصالات", "جوال", "هاتف", "فاتورة الاتصالات", "telecom", "mobile"),
      UtilityType.SHOPPING to
        listOf(
          "تسوق ومقاضي",
          "تسوق",
          "مقاضي",
          "سوبرماركت",
          "تموينات",
          "بقالة",
          "فواتير التسوق",
          "shopping",
          "groceries",
          "supermarket",
        ),
      UtilityType.DINING to
        listOf(
          "مطاعم وأغذية",
          "مطاعم",
          "مطعم",
          "كافيه",
          "قهوة",
          "أغذية",
          "فواتير المطاعم",
          "dining",
          "restaurant",
          "cafe",
        ),
      UtilityType.MAINTENANCE to
        listOf("صيانة وخدمات", "صيانة", "خدمات", "إصلاح", "فواتير الصيانة", "maintenance", "repair"),
      UtilityType.FUEL to
        listOf("وقود ومواصلات", "وقود", "بنزين", "محطة", "مواصلات", "فواتير الوقود", "fuel", "petrol"),
      UtilityType.OTHER to listOf("أخرى", "اخرى", "فاتورة منزلية", "عامة", "other"),
    )

  /**
   * Filters Firestore [BillRecord] items by store/provider name, date, or expense category,
   * preserving their chronological descending sort order.
   */
  fun filterBills(
    bills: List<BillRecord>,
    query: String,
    scope: BillSearchScope = BillSearchScope.ALL,
    categoriesById: Map<String, BillCategory> = emptyMap(),
  ): List<BillRecord> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return bills

    val normalizedQuery = normalizeSearchText(trimmed)
    if (normalizedQuery.isEmpty()) return bills

    val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

    return bills.filter { bill ->
      matchesBill(
        bill = bill,
        normalizedFullQuery = normalizedQuery,
        queryTokens = tokens,
        scope = scope,
        categoriesById = categoriesById,
      )
    }
  }

  fun matchesBill(
    bill: BillRecord,
    normalizedFullQuery: String,
    queryTokens: List<String> = normalizedFullQuery.split(' ').filter { it.isNotBlank() },
    scope: BillSearchScope = BillSearchScope.ALL,
    categoriesById: Map<String, BillCategory> = emptyMap(),
  ): Boolean {
    if (normalizedFullQuery.isBlank()) return true

    return when (scope) {
      BillSearchScope.STORE_NAME ->
        matchesCandidateList(buildStoreCandidates(bill), normalizedFullQuery, queryTokens)
      BillSearchScope.DATE ->
        matchesDateCandidates(buildDateCandidates(bill), normalizedFullQuery, queryTokens)
      BillSearchScope.CATEGORY ->
        matchesCandidateList(
          buildCategoryCandidates(bill, categoriesById),
          normalizedFullQuery,
          queryTokens,
        )
      BillSearchScope.ALL -> {
        val storeCandidates = buildStoreCandidates(bill)
        val dateCandidates = buildDateCandidates(bill)
        val categoryCandidates = buildCategoryCandidates(bill, categoriesById)

        if (
          matchesCandidateList(storeCandidates, normalizedFullQuery, emptyList()) ||
            matchesDateCandidates(dateCandidates, normalizedFullQuery, emptyList()) ||
            matchesCandidateList(categoryCandidates, normalizedFullQuery, emptyList())
        ) {
          return true
        }

        // Support multi-word cross-field queries such as "التميمي أكتوبر" or "تسوق 2026-10"
        queryTokens.isNotEmpty() &&
          queryTokens.all { token ->
            val dateToken = normalizeDateSeparators(token)
            storeCandidates.any { it.contains(token) } ||
              dateCandidates.any { it.contains(token) || it.contains(dateToken) } ||
              categoryCandidates.any { it.contains(token) }
          }
      }
    }
  }

  fun normalizeSearchText(input: String): String {
    if (input.isBlank()) return ""
    val withoutDiacritics = arabicDiacriticsRegex.replace(input, "")
    val sb = StringBuilder(withoutDiacritics.length)
    for (ch in withoutDiacritics) {
      val mapped =
        when (ch) {
          in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
          in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
          'أ', 'إ', 'آ', 'ٱ' -> 'ا'
          'ة' -> 'ه'
          'ى' -> 'ي'
          'ؤ' -> 'و'
          'ئ' -> 'ي'
          else -> ch.lowercaseChar()
        }
      sb.append(mapped)
    }
    return multiSpaceRegex.replace(sb.toString().trim().lowercase(Locale.ROOT), " ")
  }

  private fun normalizeDateSeparators(input: String): String {
    return input.replace('/', '-').replace('.', '-').replace('\\', '-')
  }

  private fun buildStoreCandidates(bill: BillRecord): List<String> {
    val candidates = mutableListOf<String>()
    if (bill.providerName.isNotBlank()) {
      candidates.add(normalizeSearchText(bill.providerName))
    }
    // Check extracted sheet rows that store merchant/provider labels if present
    for (row in bill.sheetItems) {
      val normLabel = normalizeSearchText(row.label)
      if (
        row.value.isNotBlank() &&
          (normLabel.contains("متجر") ||
            normLabel.contains("جهه") ||
            normLabel.contains("شركه") ||
            normLabel.contains("store") ||
            normLabel.contains("merchant") ||
            normLabel.contains("provider"))
      ) {
        candidates.add(normalizeSearchText(row.value))
      }
    }
    return candidates.filter { it.isNotBlank() }
  }

  private fun buildDateCandidates(bill: BillRecord): List<String> {
    val candidates = mutableSetOf<String>()
    val iso = bill.billDateIso.trim()
    val month = bill.billingMonth.trim()

    if (iso.isNotBlank()) {
      val normIso = normalizeDateSeparators(normalizeSearchText(iso))
      candidates.add(normIso)
      candidates.add(normIso.replace('-', '/'))

      val parts = normIso.split('-')
      if (parts.size == 3) {
        val y = parts[0]
        val mPadded = parts[1]
        val dPadded = parts[2]
        val mUnpadded = mPadded.trimStart('0').ifEmpty { "0" }
        val dUnpadded = dPadded.trimStart('0').ifEmpty { "0" }

        // YYYY-M-D & DD-MM-YYYY & D-M-YYYY variants
        candidates.add("$y-$mUnpadded-$dUnpadded")
        candidates.add("$dPadded-$mPadded-$y")
        candidates.add("$dUnpadded-$mUnpadded-$y")
        candidates.add("$mPadded-$y")
        candidates.add("$mUnpadded-$y")
        candidates.add("$y-$mPadded")
        candidates.add("$y-$mUnpadded")
      }

      candidates.add(normalizeSearchText(DateAndFormatUtils.formatIsoDateArabic(iso)))
    }

    if (month.isNotBlank()) {
      val normMonth = normalizeDateSeparators(normalizeSearchText(month))
      candidates.add(normMonth)
      candidates.add(normMonth.replace('-', '/'))
      candidates.add(normalizeSearchText(DateAndFormatUtils.formatBillingMonthArabic(month)))
    }

    return candidates.filter { it.isNotBlank() }
  }

  private fun buildCategoryCandidates(
    bill: BillRecord,
    categoriesById: Map<String, BillCategory>,
  ): List<String> {
    val candidates = mutableSetOf<String>()
    if (bill.categoryName.isNotBlank()) {
      candidates.add(normalizeSearchText(bill.categoryName))
    }

    val linkedCategory = categoriesById[bill.categoryId]
    if (linkedCategory != null && linkedCategory.name.isNotBlank()) {
      candidates.add(normalizeSearchText(linkedCategory.name))
    }

    val billUtility = UtilityType.fromKey(bill.utilityType)
    val effectiveUtilities =
      buildSet {
        add(billUtility)
        if (linkedCategory != null) {
          add(UtilityType.fromKey(linkedCategory.utilityType))
        }
      }

    for (uType in effectiveUtilities) {
      candidates.add(normalizeSearchText(uType.firestoreKey))
      candidates.add(normalizeSearchText(uType.defaultCategoryName))
      utilityKeywordsMap[uType]?.forEach { kw -> candidates.add(normalizeSearchText(kw)) }
    }

    return candidates.filter { it.isNotBlank() }
  }

  private fun matchesCandidateList(
    candidates: List<String>,
    normalizedFullQuery: String,
    queryTokens: List<String>,
  ): Boolean {
    if (candidates.isEmpty()) return false
    if (candidates.any { it.contains(normalizedFullQuery) }) return true
    return queryTokens.isNotEmpty() &&
      queryTokens.all { token -> candidates.any { candidate -> candidate.contains(token) } }
  }

  private fun matchesDateCandidates(
    candidates: List<String>,
    normalizedFullQuery: String,
    queryTokens: List<String>,
  ): Boolean {
    if (candidates.isEmpty()) return false
    val dashNormalizedFull = normalizeDateSeparators(normalizedFullQuery)
    if (
      candidates.any {
        it.contains(normalizedFullQuery) || it.contains(dashNormalizedFull)
      }
    ) {
      return true
    }
    return queryTokens.isNotEmpty() &&
      queryTokens.all { token ->
        val dashToken = normalizeDateSeparators(token)
        candidates.any { candidate ->
          candidate.contains(token) || candidate.contains(dashToken)
        }
      }
  }
}
