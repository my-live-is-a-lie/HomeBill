package com.example.domain

import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.util.DateAndFormatUtils
import kotlin.math.abs

enum class AlertSeverity {
  NONE,
  ELEVATED,
  HIGH_SPIKE,
}

data class SheetRowDelta(
  val label: String,
  val currentValue: String,
  val previousValue: String,
  val unit: String,
)

data class BillComparisonResult(
  val currentBill: BillRecord,
  val previousBill: BillRecord?,
  val thresholdPercent: Double,
  val amountDiff: Double,
  val amountChangePercent: Double?,
  val consumptionDiff: Double,
  val consumptionChangePercent: Double?,
  val isFinancialSpike: Boolean,
  val isConsumptionSpike: Boolean,
  val severity: AlertSeverity,
  val sheetDeltas: List<SheetRowDelta>,
) {
  val hasSpike: Boolean
    get() = isFinancialSpike || isConsumptionSpike
}

data class VarianceAlertItem(
  val bill: BillRecord,
  val category: BillCategory,
  val comparison: BillComparisonResult,
)

data class MonthlyCostPoint(
  val yearMonth: String,
  val shortLabel: String,
  val fullLabel: String,
  val totalAmount: Double,
  val byUtility: Map<UtilityType, Double>,
)

data class UtilityShareSlice(
  val utilityType: UtilityType,
  val totalAmount: Double,
  val fraction: Float,
  val percentage: Double,
)

data class CategoryTrendPoint(
  val billId: String,
  val dateLabel: String,
  val isoDate: String,
  val amount: Double,
  val consumption: Double,
)

data class MonthlyAnalyticsSummary(
  val monthlyPoints: List<MonthlyCostPoint>,
  val utilityShares: List<UtilityShareSlice>,
  val currentMonthTotal: Double,
  val averageMonthlyCost: Double,
  val monthOverMonthChangePercent: Double?,
  val highestCategoryName: String,
  val activeAlerts: List<VarianceAlertItem>,
  val primaryCurrency: String,
)

object BillAnalyticsEngine {

  fun compareWithPrevious(
    current: BillRecord,
    previous: BillRecord?,
    thresholdPercent: Double = 25.0,
  ): BillComparisonResult {
    if (previous == null) {
      val baseRows =
        current.sheetItems.map { row ->
          SheetRowDelta(
            label = row.label,
            currentValue = row.value,
            previousValue = "—",
            unit = row.unit,
          )
        }
      return BillComparisonResult(
        currentBill = current,
        previousBill = null,
        thresholdPercent = thresholdPercent,
        amountDiff = 0.0,
        amountChangePercent = null,
        consumptionDiff = 0.0,
        consumptionChangePercent = null,
        isFinancialSpike = false,
        isConsumptionSpike = false,
        severity = AlertSeverity.NONE,
        sheetDeltas = baseRows,
      )
    }

    val amountDiff = current.totalAmount - previous.totalAmount
    val amountPct =
      if (previous.totalAmount > 0.001) {
        (amountDiff / previous.totalAmount) * 100.0
      } else {
        null
      }

    val consDiff = current.consumptionValue - previous.consumptionValue
    val consPct =
      if (previous.consumptionValue > 0.001) {
        (consDiff / previous.consumptionValue) * 100.0
      } else {
        null
      }

    val isFinancialSpike = amountPct != null && amountPct >= thresholdPercent
    val isConsumptionSpike = consPct != null && consPct >= thresholdPercent

    val maxIncreasePct = maxOf(amountPct ?: 0.0, consPct ?: 0.0)
    val severity =
      when {
        isFinancialSpike || isConsumptionSpike -> AlertSeverity.HIGH_SPIKE
        maxIncreasePct >= thresholdPercent * 0.65 -> AlertSeverity.ELEVATED
        else -> AlertSeverity.NONE
      }

    val prevMap = previous.sheetItems.associateBy { it.label.trim() }
    val allLabels = LinkedHashSet<String>()
    current.sheetItems.forEach { allLabels.add(it.label.trim()) }
    previous.sheetItems.forEach { allLabels.add(it.label.trim()) }

    val currMap = current.sheetItems.associateBy { it.label.trim() }
    val deltas =
      allLabels
        .filter { it.isNotBlank() }
        .map { label ->
          val cRow = currMap[label]
          val pRow = prevMap[label]
          SheetRowDelta(
            label = label,
            currentValue = cRow?.value ?: "—",
            previousValue = pRow?.value ?: "—",
            unit = cRow?.unit?.ifBlank { pRow?.unit.orEmpty() } ?: pRow?.unit.orEmpty(),
          )
        }

    return BillComparisonResult(
      currentBill = current,
      previousBill = previous,
      thresholdPercent = thresholdPercent,
      amountDiff = amountDiff,
      amountChangePercent = amountPct,
      consumptionDiff = consDiff,
      consumptionChangePercent = consPct,
      isFinancialSpike = isFinancialSpike,
      isConsumptionSpike = isConsumptionSpike,
      severity = severity,
      sheetDeltas = deltas,
    )
  }

  fun buildCategoryComparisons(
    billsInCategory: List<BillRecord>,
    thresholdPercent: Double,
  ): Map<String, BillComparisonResult> {
    if (billsInCategory.isEmpty()) return emptyMap()
    val chronological =
      billsInCategory.sortedWith(
        compareBy<BillRecord> { it.billDateEpochMillis }.thenBy { it.billDateIso }
      )
    val map = LinkedHashMap<String, BillComparisonResult>()
    for (i in chronological.indices) {
      val current = chronological[i]
      val previous = if (i > 0) chronological[i - 1] else null
      map[current.id] = compareWithPrevious(current, previous, thresholdPercent)
    }
    return map
  }

  fun previewDraftComparison(
    draftBill: BillRecord,
    existingCategoryBills: List<BillRecord>,
    thresholdPercent: Double,
  ): BillComparisonResult {
    val candidates =
      existingCategoryBills
        .filter { it.id != draftBill.id && it.billDateEpochMillis <= draftBill.billDateEpochMillis }
        .sortedByDescending { it.billDateEpochMillis }
    val previous =
      candidates.firstOrNull()
        ?: existingCategoryBills
          .filter { it.id != draftBill.id }
          .maxByOrNull { it.billDateEpochMillis }
    return compareWithPrevious(draftBill, previous, thresholdPercent)
  }

  fun buildCategoryTrend(billsInCategory: List<BillRecord>): List<CategoryTrendPoint> {
    return billsInCategory
      .sortedBy { it.billDateEpochMillis }
      .takeLast(12)
      .map { bill ->
        CategoryTrendPoint(
          billId = bill.id,
          dateLabel = DateAndFormatUtils.formatBillingMonthArabic(bill.billingMonth, shortYear = true),
          isoDate = bill.billDateIso,
          amount = bill.totalAmount,
          consumption = bill.consumptionValue,
        )
      }
  }

  fun computeMonthlyAnalytics(
    allBills: List<BillRecord>,
    categories: List<BillCategory>,
  ): MonthlyAnalyticsSummary {
    val categoryById = categories.associateBy { it.id }
    val primaryCurrency = allBills.firstOrNull()?.currency?.ifBlank { "ر.س" } ?: "ر.س"

    // Group bills by billingMonth (YYYY-MM)
    val groupedByMonth = allBills.groupBy { it.billingMonth }
    val sortedMonths = groupedByMonth.keys.sorted().takeLast(12)

    val monthlyPoints =
      sortedMonths.map { ym ->
        val monthBills = groupedByMonth[ym].orEmpty()
        val byUtility =
          monthBills
            .groupBy { UtilityType.fromKey(it.utilityType) }
            .mapValues { (_, list) -> list.sumOf { it.totalAmount } }
        MonthlyCostPoint(
          yearMonth = ym,
          shortLabel = DateAndFormatUtils.formatBillingMonthArabic(ym, shortYear = true),
          fullLabel = DateAndFormatUtils.formatBillingMonthArabic(ym, shortYear = false),
          totalAmount = monthBills.sumOf { it.totalAmount },
          byUtility = byUtility,
        )
      }

    val totalSpendAll = allBills.sumOf { it.totalAmount }
    val utilityShares =
      if (totalSpendAll > 0.001) {
        allBills
          .groupBy { UtilityType.fromKey(it.utilityType) }
          .map { (uType, list) ->
            val sum = list.sumOf { it.totalAmount }
            val pct = (sum / totalSpendAll) * 100.0
            UtilityShareSlice(
              utilityType = uType,
              totalAmount = sum,
              fraction = (sum / totalSpendAll).toFloat().coerceIn(0f, 1f),
              percentage = pct,
            )
          }
          .sortedByDescending { it.totalAmount }
      } else {
        emptyList()
      }

    val currentYm = DateAndFormatUtils.currentBillingMonth()
    val latestMonthPoint = monthlyPoints.lastOrNull()
    val currentMonthTotal =
      groupedByMonth[currentYm]?.sumOf { it.totalAmount } ?: (latestMonthPoint?.totalAmount ?: 0.0)

    val averageMonthlyCost =
      if (monthlyPoints.isNotEmpty()) {
        monthlyPoints.map { it.totalAmount }.average()
      } else {
        0.0
      }

    val momChangePct =
      if (monthlyPoints.size >= 2) {
        val last = monthlyPoints[monthlyPoints.size - 1].totalAmount
        val prev = monthlyPoints[monthlyPoints.size - 2].totalAmount
        if (prev > 0.001) ((last - prev) / prev) * 100.0 else null
      } else {
        null
      }

    val highestCategoryName =
      allBills
        .groupBy { it.categoryId }
        .maxByOrNull { (_, list) -> list.sumOf { it.totalAmount } }
        ?.let { (catId, list) -> categoryById[catId]?.name ?: list.firstOrNull()?.categoryName }
        .orEmpty()
        .ifBlank { "—" }

    // Active spike alerts from latest bill of each category
    val alerts = mutableListOf<VarianceAlertItem>()
    for (category in categories) {
      val catBills = allBills.filter { it.categoryId == category.id }
      if (catBills.size >= 2) {
        val comparisons = buildCategoryComparisons(catBills, category.alertThresholdPercent)
        val newestBill = catBills.maxByOrNull { it.billDateEpochMillis }
        val newestComp = newestBill?.let { comparisons[it.id] }
        if (newestBill != null && newestComp != null && newestComp.hasSpike) {
          alerts.add(
            VarianceAlertItem(
              bill = newestBill,
              category = category,
              comparison = newestComp,
            )
          )
        }
      }
    }
    alerts.sortByDescending {
      maxOf(
        abs(it.comparison.amountChangePercent ?: 0.0),
        abs(it.comparison.consumptionChangePercent ?: 0.0),
      )
    }

    return MonthlyAnalyticsSummary(
      monthlyPoints = monthlyPoints,
      utilityShares = utilityShares,
      currentMonthTotal = currentMonthTotal,
      averageMonthlyCost = averageMonthlyCost,
      monthOverMonthChangePercent = momChangePct,
      highestCategoryName = highestCategoryName,
      activeAlerts = alerts,
      primaryCurrency = primaryCurrency,
    )
  }
}
