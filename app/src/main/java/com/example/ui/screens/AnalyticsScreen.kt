package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.domain.BillAnalyticsEngine
import com.example.domain.BillComparisonResult
import com.example.domain.BudgetDashboardState
import com.example.domain.MonthlyAnalyticsSummary
import com.example.domain.MonthlyBudgetDashboardEngine
import com.example.ui.components.BillComparisonModal
import com.example.ui.components.CategorySmoothTrendChart
import com.example.ui.components.HouseholdBudgetDialog
import com.example.ui.components.HouseholdBudgetHeroCard
import com.example.ui.components.RechartsCategoryBreakdownChart
import com.example.ui.components.RechartsMonthlyCategoryBarChart
import com.example.ui.components.VarianceAlertBannerCard
import com.example.ui.components.visualSpec
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.DateAndFormatUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
  categories: List<BillCategory>,
  allBills: List<BillRecord>,
  summary: MonthlyAnalyticsSummary,
  budgetDashboardState: BudgetDashboardState =
    remember(allBills, categories) {
      MonthlyBudgetDashboardEngine.compute(allBills, categories, null)
    },
  onSelectMonth: (String) -> Unit = {},
  onSaveHouseholdBudget: (monthlyLimit: Double, currency: String, warningPercent: Double) -> Unit =
    { _, _, _ ->
    },
  onOpenCategory: (String) -> Unit = {},
) {
  var selectedCategoryId by
    remember(categories) { mutableStateOf(categories.firstOrNull()?.id) }
  var showConsumptionTrend by remember { mutableStateOf(false) }
  var activeComparison by remember { mutableStateOf<BillComparisonResult?>(null) }
  var showBudgetDialog by remember { mutableStateOf(false) }
  val palette = LocalUtilityPalette.current

  activeComparison?.let { comp ->
    BillComparisonModal(
      comparison = comp,
      onDismiss = { activeComparison = null },
    )
  }

  if (showBudgetDialog) {
    HouseholdBudgetDialog(
      initialLimit = budgetDashboardState.monthlyBudgetLimit,
      initialWarningPercent = budgetDashboardState.warningPercent,
      currency = budgetDashboardState.primaryCurrency,
      onDismiss = { showBudgetDialog = false },
      onSave = { newLimit, newWarning ->
        showBudgetDialog = false
        onSaveHouseholdBudget(
          newLimit,
          budgetDashboardState.primaryCurrency,
          newWarning,
        )
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.budget_dashboard_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = Modifier.testTag("budget_analytics_screen"),
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
          Modifier.widthIn(max = 720.dp)
            .fillMaxWidth()
            .testTag("budget_dashboard_lazy_list"),
      ) {
        // 1. Household Budget Tracker Hero Card + Month Selector
        item {
          HouseholdBudgetHeroCard(
            dashboardState = budgetDashboardState,
            onSelectMonth = onSelectMonth,
            onEditBudgetClick = { showBudgetDialog = true },
          )
        }

        if (allBills.isEmpty()) {
          item {
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = MaterialTheme.shapes.extraLarge,
              modifier = Modifier.fillMaxWidth().testTag("analytics_empty_state_card"),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.BarChart,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = stringResource(R.string.analytics_empty_message),
                  style = MaterialTheme.typography.bodyLarge,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        } else {
          // 2. 4 KPI Cards (2x2 Grid) synchronized with selected month & overall trends
          item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                KpiStatCard(
                  label = stringResource(R.string.analytics_kpi_avg),
                  value =
                    "${DateAndFormatUtils.formatMoney(budgetDashboardState.averageMonthlySpend)} ${budgetDashboardState.primaryCurrency}",
                  modifier = Modifier.weight(1f),
                )
                val momPct = budgetDashboardState.monthOverMonthPercent
                val momText =
                  momPct?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—"
                val momColor =
                  when {
                    (momPct ?: 0.0) > 10.0 -> palette.spikeWarningAccent
                    (momPct ?: 0.0) < -5.0 -> palette.savingsPositiveAccent
                    else -> MaterialTheme.colorScheme.primary
                  }
                KpiStatCard(
                  label = stringResource(R.string.analytics_kpi_mom),
                  value = momText,
                  valueColor = momColor,
                  modifier = Modifier.weight(1f),
                )
              }

              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                KpiStatCard(
                  label = stringResource(R.string.analytics_kpi_highest),
                  value = budgetDashboardState.topCategory?.categoryName ?: summary.highestCategoryName,
                  modifier = Modifier.weight(1f),
                )
                KpiStatCard(
                  label = stringResource(R.string.analytics_kpi_alerts),
                  value = "${summary.activeAlerts.size}",
                  valueColor =
                    if (summary.activeAlerts.isNotEmpty()) palette.spikeWarningAccent
                    else MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }

          // 3. Recharts-Style Interactive Monthly Category Expenses Bar Chart
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = MaterialTheme.shapes.extraLarge,
              modifier = Modifier.fillMaxWidth().testTag("monthly_category_chart_card"),
            ) {
              Column(modifier = Modifier.padding(18.dp)) {
                Text(
                  text = stringResource(R.string.chart_monthly_by_category_title),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = stringResource(R.string.chart_monthly_by_category_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                RechartsMonthlyCategoryBarChart(
                  points = budgetDashboardState.multiMonthSeries,
                  legendCategories = budgetDashboardState.legendCategories,
                  selectedMonth = budgetDashboardState.selectedMonth,
                  monthlyBudgetLimit = budgetDashboardState.monthlyBudgetLimit,
                  averageMonthlySpend = budgetDashboardState.averageMonthlySpend,
                  currency = budgetDashboardState.primaryCurrency,
                  onSelectMonth = onSelectMonth,
                )
              }
            }
          }

          // 4. Selected Month Category Breakdown Chart (Bars & Interactive Donut)
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = MaterialTheme.shapes.extraLarge,
              modifier = Modifier.fillMaxWidth().testTag("selected_month_breakdown_card"),
            ) {
              Column(modifier = Modifier.padding(18.dp)) {
                Text(
                  text =
                    stringResource(
                      R.string.chart_selected_month_breakdown_title,
                      budgetDashboardState.selectedMonthFullLabel,
                    ),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                RechartsCategoryBreakdownChart(
                  categoryExpenses = budgetDashboardState.categoryExpenses,
                  selectedMonthTotal = budgetDashboardState.selectedMonthTotal,
                  currency = budgetDashboardState.primaryCurrency,
                  onCategoryClick = onOpenCategory,
                )
              }
            }
          }

          // 5. Category Consumption & Cost Trend Line
          if (categories.isNotEmpty()) {
            item {
              val activeCat =
                categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()
              val catBills =
                remember(allBills, activeCat.id) {
                  allBills.filter { it.categoryId == activeCat.id }
                }
              val trendPts =
                remember(catBills) { BillAnalyticsEngine.buildCategoryTrend(catBills) }
              val spec = UtilityType.fromKey(activeCat.utilityType).visualSpec()

              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Text(
                    text = stringResource(R.string.analytics_trend_line_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Spacer(modifier = Modifier.height(8.dp))

                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  ) {
                    categories.forEach { cat ->
                      FilterChip(
                        selected = cat.id == activeCat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.name) },
                      )
                    }
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                      selected = !showConsumptionTrend,
                      onClick = { showConsumptionTrend = false },
                      label = { Text(stringResource(R.string.metric_amount)) },
                    )
                    FilterChip(
                      selected = showConsumptionTrend,
                      onClick = { showConsumptionTrend = true },
                      label = { Text(stringResource(R.string.metric_consumption)) },
                    )
                  }

                  Spacer(modifier = Modifier.height(12.dp))
                  if (trendPts.isNotEmpty()) {
                    val unitLabel =
                      if (showConsumptionTrend) activeCat.defaultUnit else summary.primaryCurrency
                    CategorySmoothTrendChart(
                      points = trendPts,
                      showConsumption = showConsumptionTrend,
                      unitOrCurrency = unitLabel,
                      accentColor = spec.accentColor,
                    )
                  } else {
                    Text(
                      text = stringResource(R.string.no_bills_yet),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }

          // 6. Active Spike Alerts List
          if (summary.activeAlerts.isNotEmpty()) {
            item {
              Text(
                text = stringResource(R.string.dashboard_alerts_title),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            items(summary.activeAlerts, key = { "analytics_alert_${it.bill.id}" }) { alert ->
              VarianceAlertBannerCard(
                alert = alert,
                onOpenComparison = { activeComparison = alert.comparison },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun KpiStatCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = valueColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
