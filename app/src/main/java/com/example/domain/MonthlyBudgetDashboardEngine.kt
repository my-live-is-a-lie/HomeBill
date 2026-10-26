package com.example.domain

import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.HouseholdBudget
import com.example.data.model.UtilityType
import com.example.util.DateAndFormatUtils

enum class BudgetHealthStatus {
  UNSET,
  WITHIN_BUDGET,
  APPROACHING_LIMIT,
  OVER_BUDGET,
}

data class MonthOption(
  val yearMonth: String,
  val shortLabel: String,
  val fullLabel: String,
)

data class CategoryMonthlyExpense(
  val categoryKey: String,
  val categoryId: String?,
  val categoryName: String,
  val utilityType: UtilityType,
  val colorIndex: Int,
  val totalAmount: Double,
  val shareFraction: Float,
  val sharePercentage: Double,
  val billsCount: Int,
  val previousMonthAmount: Double,
  val changeFromPreviousPercent: Double?,
)

data class CategorySegmentAmount(
  val categoryKey: String,
  val categoryName: String,
  val utilityType: UtilityType,
  val colorIndex: Int,
  val amount: Double,
)

data class MonthlyCategoryBarPoint(
  val yearMonth: String,
  val shortLabel: String,
  val fullLabel: String,
  val totalAmount: Double,
  val segments: List<CategorySegmentAmount>,
) {
  fun amountForFilter(categoryKey: String?): Double {
    if (categoryKey.isNullOrBlank()) return totalAmount
    return segments.firstOrNull { it.categoryKey == categoryKey }?.amount ?: 0.0
  }
}

data class BudgetDashboardState(
  val availableMonths: List<MonthOption>,
  val selectedMonth: String,
  val selectedMonthShortLabel: String,
  val selectedMonthFullLabel: String,
  val selectedMonthTotal: Double,
  val selectedMonthBillsCount: Int,
  val previousMonthTotal: Double?,
  val monthOverMonthDiff: Double?,
  val monthOverMonthPercent: Double?,
  val monthlyBudgetLimit: Double,
  val warningPercent: Double,
  val budgetUsedPercent: Double?,
  val budgetProgressFraction: Float,
  val remainingOrExceededAmount: Double?,
  val budgetStatus: BudgetHealthStatus,
  val categoryExpenses: List<CategoryMonthlyExpense>,
  val legendCategories: List<CategorySegmentAmount>,
  val multiMonthSeries: List<MonthlyCategoryBarPoint>,
  val averageMonthlySpend: Double,
  val topCategory: CategoryMonthlyExpense?,
  val primaryCurrency: String,
)

object MonthlyBudgetDashboardEngine {

  private data class ResolvedCategoryMeta(
    val key: String,
    val categoryId: String?,
    val name: String,
    val utilityType: UtilityType,
    val colorIndex: Int,
  )

  fun compute(
    allBills: List<BillRecord>,
    categories: List<BillCategory>,
    householdBudget: HouseholdBudget?,
    requestedMonth: String? = null,
    fallbackCurrentMonth: String = DateAndFormatUtils.currentBillingMonth(),
  ): BudgetDashboardState {
    val categoryById = categories.associateBy { it.id }
    val primaryCurrency =
      householdBudget?.currency?.takeIf { it.isNotBlank() }
        ?: allBills.firstOrNull()?.currency?.takeIf { it.isNotBlank() }
        ?: "ر.س"

    // Build stable metadata map for every known category across categories + bills
    val metaMap = LinkedHashMap<String, ResolvedCategoryMeta>()
    var nextColorIdx = 0
    categories.forEach { cat ->
      val key = cat.id.ifBlank { UtilityType.fromKey(cat.utilityType).firestoreKey }
      if (!metaMap.containsKey(key)) {
        val uType = UtilityType.fromKey(cat.utilityType)
        metaMap[key] =
          ResolvedCategoryMeta(
            key = key,
            categoryId = cat.id.takeIf { it.isNotBlank() },
            name = cat.name.ifBlank { uType.defaultCategoryName },
            utilityType = uType,
            colorIndex = nextColorIdx++,
          )
      }
    }
    allBills.forEach { bill ->
      val uType = UtilityType.fromKey(bill.utilityType)
      val key = bill.categoryId.ifBlank { uType.firestoreKey }
      if (!metaMap.containsKey(key)) {
        val linkedCat = categoryById[bill.categoryId]
        metaMap[key] =
          ResolvedCategoryMeta(
            key = key,
            categoryId = bill.categoryId.takeIf { it.isNotBlank() },
            name =
              linkedCat?.name?.takeIf { it.isNotBlank() }
                ?: bill.categoryName.ifBlank { uType.defaultCategoryName },
            utilityType = uType,
            colorIndex = nextColorIdx++,
          )
      }
    }

    val groupedByMonth =
      allBills
        .filter { it.billingMonth.isNotBlank() }
        .groupBy { it.billingMonth.trim() }

    val sortedMonthKeys = groupedByMonth.keys.sorted()
    val availableMonths =
      sortedMonthKeys.map { ym ->
        MonthOption(
          yearMonth = ym,
          shortLabel = DateAndFormatUtils.formatBillingMonthArabic(ym, shortYear = true),
          fullLabel = DateAndFormatUtils.formatBillingMonthArabic(ym, shortYear = false),
        )
      }

    val selectedMonth =
      when {
        !requestedMonth.isNullOrBlank() && groupedByMonth.containsKey(requestedMonth) ->
          requestedMonth
        !requestedMonth.isNullOrBlank() && sortedMonthKeys.isEmpty() -> requestedMonth
        groupedByMonth.containsKey(fallbackCurrentMonth) -> fallbackCurrentMonth
        sortedMonthKeys.isNotEmpty() -> sortedMonthKeys.last()
        else -> fallbackCurrentMonth
      }

    val selectedMonthBills = groupedByMonth[selectedMonth].orEmpty()
    val selectedMonthTotal = selectedMonthBills.sumOf { it.totalAmount }

    // Identify preceding month in sortedMonthKeys for accurate MoM comparison
    val selectedIdx = sortedMonthKeys.indexOf(selectedMonth)
    val previousMonthKey =
      if (selectedIdx > 0) sortedMonthKeys[selectedIdx - 1] else null
    val previousMonthBills = previousMonthKey?.let { groupedByMonth[it] }.orEmpty()
    val previousMonthTotal =
      if (previousMonthKey != null) previousMonthBills.sumOf { it.totalAmount } else null

    val monthOverMonthDiff =
      previousMonthTotal?.let { prev -> selectedMonthTotal - prev }
    val monthOverMonthPercent =
      previousMonthTotal?.let { prev ->
        if (prev > 0.001) ((selectedMonthTotal - prev) / prev) * 100.0 else null
      }

    // Per-category breakdown for selectedMonth
    val prevAmountsByKey =
      previousMonthBills
        .groupBy { bill ->
          bill.categoryId.ifBlank { UtilityType.fromKey(bill.utilityType).firestoreKey }
        }
        .mapValues { (_, list) -> list.sumOf { it.totalAmount } }

    val categoryExpenses =
      selectedMonthBills
        .groupBy { bill ->
          bill.categoryId.ifBlank { UtilityType.fromKey(bill.utilityType).firestoreKey }
        }
        .mapNotNull { (key, billsInCat) ->
          val catSum = billsInCat.sumOf { it.totalAmount }
          if (catSum <= 0.0 && billsInCat.isEmpty()) return@mapNotNull null
          val meta =
            metaMap[key]
              ?: run {
                val uType = UtilityType.fromKey(billsInCat.first().utilityType)
                ResolvedCategoryMeta(
                  key = key,
                  categoryId = billsInCat.first().categoryId.takeIf { it.isNotBlank() },
                  name = billsInCat.first().categoryName.ifBlank { uType.defaultCategoryName },
                  utilityType = uType,
                  colorIndex = 0,
                )
              }
          val fraction =
            if (selectedMonthTotal > 0.001) {
              (catSum / selectedMonthTotal).toFloat().coerceIn(0f, 1f)
            } else {
              0f
            }
          val pct =
            if (selectedMonthTotal > 0.001) (catSum / selectedMonthTotal) * 100.0 else 0.0
          val prevCatSum = prevAmountsByKey[key] ?: 0.0
          val changePct =
            if (previousMonthKey != null && prevCatSum > 0.001) {
              ((catSum - prevCatSum) / prevCatSum) * 100.0
            } else {
              null
            }
          CategoryMonthlyExpense(
            categoryKey = key,
            categoryId = meta.categoryId,
            categoryName = meta.name,
            utilityType = meta.utilityType,
            colorIndex = meta.colorIndex,
            totalAmount = catSum,
            shareFraction = fraction,
            sharePercentage = pct,
            billsCount = billsInCat.size,
            previousMonthAmount = prevCatSum,
            changeFromPreviousPercent = changePct,
          )
        }
        .sortedByDescending { it.totalAmount }

    // Multi-month stacked series (up to last 8 months)
    val recentMonths = sortedMonthKeys.takeLast(8)
    val multiMonthSeries =
      recentMonths.map { ym ->
        val mBills = groupedByMonth[ym].orEmpty()
        val byCatKey =
          mBills.groupBy { bill ->
            bill.categoryId.ifBlank { UtilityType.fromKey(bill.utilityType).firestoreKey }
          }
        val segments =
          byCatKey
            .map { (key, list) ->
              val meta = metaMap[key]
              val uType = meta?.utilityType ?: UtilityType.fromKey(list.first().utilityType)
              CategorySegmentAmount(
                categoryKey = key,
                categoryName =
                  meta?.name ?: list.first().categoryName.ifBlank { uType.defaultCategoryName },
                utilityType = uType,
                colorIndex = meta?.colorIndex ?: 0,
                amount = list.sumOf { it.totalAmount },
              )
            }
            .filter { it.amount > 0.0 }
            .sortedBy { it.colorIndex }

        MonthlyCategoryBarPoint(
          yearMonth = ym,
          shortLabel = DateAndFormatUtils.formatBillingMonthArabic(ym, shortYear = true),
          fullLabel = DateAndFormatUtils.formatBillingMonthArabic(ym, shortYear = false),
          totalAmount = mBills.sumOf { it.totalAmount },
          segments = segments,
        )
      }

    // Aggregate legend categories across recent months sorted by total spend descending
    val legendTotals = LinkedHashMap<String, Double>()
    multiMonthSeries.forEach { pt ->
      pt.segments.forEach { seg ->
        legendTotals[seg.categoryKey] = (legendTotals[seg.categoryKey] ?: 0.0) + seg.amount
      }
    }
    val legendCategories =
      legendTotals.entries
        .sortedByDescending { it.value }
        .mapNotNull { (key, sum) ->
          val meta = metaMap[key] ?: return@mapNotNull null
          CategorySegmentAmount(
            categoryKey = key,
            categoryName = meta.name,
            utilityType = meta.utilityType,
            colorIndex = meta.colorIndex,
            amount = sum,
          )
        }

    val averageMonthlySpend =
      if (sortedMonthKeys.isNotEmpty()) {
        sortedMonthKeys.map { ym -> groupedByMonth[ym].orEmpty().sumOf { it.totalAmount } }.average()
      } else {
        0.0
      }

    // Budget status evaluation
    val limit = (householdBudget?.monthlyLimit ?: 0.0).coerceAtLeast(0.0)
    val warningPct = (householdBudget?.warningPercent ?: 80.0).coerceIn(10.0, 100.0)
    val usedPct = if (limit > 0.001) (selectedMonthTotal / limit) * 100.0 else null
    val progressFraction =
      if (limit > 0.001) (selectedMonthTotal / limit).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = if (limit > 0.001) limit - selectedMonthTotal else null
    val budgetStatus =
      when {
        limit <= 0.001 -> BudgetHealthStatus.UNSET
        selectedMonthTotal > limit -> BudgetHealthStatus.OVER_BUDGET
        (usedPct ?: 0.0) >= warningPct -> BudgetHealthStatus.APPROACHING_LIMIT
        else -> BudgetHealthStatus.WITHIN_BUDGET
      }

    return BudgetDashboardState(
      availableMonths = availableMonths,
      selectedMonth = selectedMonth,
      selectedMonthShortLabel =
        DateAndFormatUtils.formatBillingMonthArabic(selectedMonth, shortYear = true),
      selectedMonthFullLabel =
        DateAndFormatUtils.formatBillingMonthArabic(selectedMonth, shortYear = false),
      selectedMonthTotal = selectedMonthTotal,
      selectedMonthBillsCount = selectedMonthBills.size,
      previousMonthTotal = previousMonthTotal,
      monthOverMonthDiff = monthOverMonthDiff,
      monthOverMonthPercent = monthOverMonthPercent,
      monthlyBudgetLimit = limit,
      warningPercent = warningPct,
      budgetUsedPercent = usedPct,
      budgetProgressFraction = progressFraction,
      remainingOrExceededAmount = remaining,
      budgetStatus = budgetStatus,
      categoryExpenses = categoryExpenses,
      legendCategories = legendCategories,
      multiMonthSeries = multiMonthSeries,
      averageMonthlySpend = averageMonthlySpend,
      topCategory = categoryExpenses.firstOrNull(),
      primaryCurrency = primaryCurrency,
    )
  }
}
