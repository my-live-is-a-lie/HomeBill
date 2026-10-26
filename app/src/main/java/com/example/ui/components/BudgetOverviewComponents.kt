package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.BudgetDashboardState
import com.example.domain.BudgetHealthStatus
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.DateAndFormatUtils
import kotlin.math.abs

@Composable
fun HouseholdBudgetHeroCard(
  dashboardState: BudgetDashboardState,
  onSelectMonth: (String) -> Unit,
  onEditBudgetClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalUtilityPalette.current
  val months = dashboardState.availableMonths
  val selectedIdx = months.indexOfFirst { it.yearMonth == dashboardState.selectedMonth }
  val canGoPrev = selectedIdx > 0
  val canGoNext = selectedIdx >= 0 && selectedIdx < months.lastIndex

  val animatedProgress by
    animateFloatAsState(
      targetValue = dashboardState.budgetProgressFraction,
      animationSpec = spring(stiffness = Spring.StiffnessMedium),
      label = "budgetHeroProgress",
    )

  val statusAccentColor =
    when (dashboardState.budgetStatus) {
      BudgetHealthStatus.OVER_BUDGET -> Color(0xFFFFB4AB)
      BudgetHealthStatus.APPROACHING_LIMIT -> Color(0xFFFCD34D)
      BudgetHealthStatus.WITHIN_BUDGET -> Color(0xFF6EE7B7)
      BudgetHealthStatus.UNSET -> MaterialTheme.colorScheme.onPrimary
    }

  Card(
    shape = MaterialTheme.shapes.extraLarge,
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = modifier.fillMaxWidth().testTag("household_budget_hero_card"),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.linearGradient(
              colors = listOf(palette.heroGradientStart, palette.heroGradientEnd)
            )
          )
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        // Header Row + Month Stepper
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AccountBalanceWallet,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.9f),
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.budget_hero_label),
              style = MaterialTheme.typography.labelLarge,
              color = Color.White.copy(alpha = 0.88f),
            )
          }

          if (months.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              // In RTL layout, KeyboardArrowRight points to previous (older) and Left to next
              IconButton(
                onClick = {
                  if (canGoPrev) onSelectMonth(months[selectedIdx - 1].yearMonth)
                },
                enabled = canGoPrev,
                modifier = Modifier.testTag("budget_prev_month_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                  contentDescription = stringResource(R.string.budget_prev_month_desc),
                  tint =
                    if (canGoPrev) Color.White else Color.White.copy(alpha = 0.35f),
                )
              }
              Text(
                text = dashboardState.selectedMonthShortLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              IconButton(
                onClick = {
                  if (canGoNext) onSelectMonth(months[selectedIdx + 1].yearMonth)
                },
                enabled = canGoNext,
                modifier = Modifier.testTag("budget_next_month_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                  contentDescription = stringResource(R.string.budget_next_month_desc),
                  tint =
                    if (canGoNext) Color.White else Color.White.copy(alpha = 0.35f),
                )
              }
            }
          }
        }

        // Quick Month Chips Strip
        if (months.size > 1) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          ) {
            months.forEach { m ->
              val selected = m.yearMonth == dashboardState.selectedMonth
              FilterChip(
                selected = selected,
                onClick = { onSelectMonth(m.yearMonth) },
                label = {
                  Text(
                    text = m.fullLabel,
                    color =
                      if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                      else Color.White,
                  )
                },
                modifier = Modifier.testTag("month_chip_${m.yearMonth}"),
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        } else {
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Selected Month Total Amount
        Text(
          text =
            stringResource(
              R.string.budget_selected_month_spent,
              dashboardState.selectedMonthFullLabel,
            ),
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.82f),
        )
        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              "${DateAndFormatUtils.formatMoney(dashboardState.selectedMonthTotal)} ${dashboardState.primaryCurrency}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.testTag("selected_month_total_text"),
          )
          Surface(
            color = Color.White.copy(alpha = 0.16f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Text(
              text =
                stringResource(
                  R.string.chart_bills_count_badge,
                  dashboardState.selectedMonthBillsCount,
                ),
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Budget Limit Progress vs Unset Prompt
        if (dashboardState.budgetStatus == BudgetHealthStatus.UNSET) {
          Surface(
            color = Color.White.copy(alpha = 0.13f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = stringResource(R.string.budget_unset_hint),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.92f),
              modifier = Modifier.padding(12.dp),
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = onEditBudgetClick,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
            shape = MaterialTheme.shapes.large,
            modifier =
              Modifier.fillMaxWidth()
                .height(48.dp)
                .testTag("edit_household_budget_button"),
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.budget_set_cta),
              style = MaterialTheme.typography.titleSmall,
            )
          }
        } else {
          val usedPct = dashboardState.budgetUsedPercent ?: 0.0
          val rem = dashboardState.remainingOrExceededAmount ?: 0.0
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                stringResource(
                  R.string.budget_target_label,
                  DateAndFormatUtils.formatMoney(dashboardState.monthlyBudgetLimit),
                  dashboardState.primaryCurrency,
                ),
              style = MaterialTheme.typography.labelLarge,
              color = Color.White.copy(alpha = 0.92f),
            )
            Text(
              text = stringResource(R.string.budget_used_percent_badge, usedPct),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = statusAccentColor,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Animated Progress Bar
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(statusAccentColor),
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          val statusMessage =
            when (dashboardState.budgetStatus) {
              BudgetHealthStatus.WITHIN_BUDGET ->
                stringResource(
                  R.string.budget_status_within,
                  DateAndFormatUtils.formatMoney(abs(rem)),
                  dashboardState.primaryCurrency,
                )
              BudgetHealthStatus.APPROACHING_LIMIT ->
                stringResource(
                  R.string.budget_status_approaching,
                  DateAndFormatUtils.formatMoney(abs(rem)),
                  dashboardState.primaryCurrency,
                  usedPct,
                )
              BudgetHealthStatus.OVER_BUDGET ->
                stringResource(
                  R.string.budget_status_over,
                  DateAndFormatUtils.formatMoney(abs(rem)),
                  dashboardState.primaryCurrency,
                  usedPct,
                )
              BudgetHealthStatus.UNSET -> ""
            }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector =
                  if (dashboardState.budgetStatus == BudgetHealthStatus.WITHIN_BUDGET) {
                    Icons.Default.CheckCircle
                  } else {
                    Icons.Default.WarningAmber
                  },
                contentDescription = null,
                tint = statusAccentColor,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.testTag("budget_status_message_text"),
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
              onClick = onEditBudgetClick,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color.White.copy(alpha = 0.18f),
                  contentColor = Color.White,
                ),
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.height(48.dp).testTag("edit_household_budget_button"),
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.budget_edit_cta),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeBudgetOverviewCard(
  dashboardState: BudgetDashboardState,
  onOpenChartsDashboard: () -> Unit,
  onEditBudgetClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalUtilityPalette.current

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier.fillMaxWidth().testTag("home_budget_overview_card"),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.home_budget_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = dashboardState.selectedMonthFullLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onEditBudgetClick,
          modifier = Modifier.testTag("home_edit_budget_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.budget_edit_cta),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Mini Multi-Category Stacked Distribution Bar
      if (dashboardState.categoryExpenses.isNotEmpty()) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(3.dp),
          modifier =
            Modifier.fillMaxWidth()
              .height(14.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
          dashboardState.categoryExpenses.forEach { cat ->
            val weightVal = cat.shareFraction.coerceAtLeast(0.03f)
            Box(
              modifier =
                Modifier.weight(weightVal)
                  .fillMaxHeight()
                  .background(categoryChartColor(cat.utilityType, cat.colorIndex)),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          dashboardState.categoryExpenses.take(4).forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier =
                  Modifier.size(9.dp)
                    .clip(CircleShape)
                    .background(categoryChartColor(cat.utilityType, cat.colorIndex)),
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text =
                  "${cat.categoryName}: ${DateAndFormatUtils.formatMoney(cat.totalAmount)} ${dashboardState.primaryCurrency}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }

      // Compact Budget Status Strip
      if (dashboardState.budgetStatus != BudgetHealthStatus.UNSET) {
        val rem = dashboardState.remainingOrExceededAmount ?: 0.0
        val usedPct = dashboardState.budgetUsedPercent ?: 0.0
        val (bgColor, fgColor, msg) =
          when (dashboardState.budgetStatus) {
            BudgetHealthStatus.WITHIN_BUDGET ->
              Triple(
                palette.savingsPositiveContainer,
                palette.savingsPositiveAccent,
                stringResource(
                  R.string.budget_status_within,
                  DateAndFormatUtils.formatMoney(abs(rem)),
                  dashboardState.primaryCurrency,
                ),
              )
            BudgetHealthStatus.APPROACHING_LIMIT ->
              Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondary,
                stringResource(
                  R.string.budget_status_approaching,
                  DateAndFormatUtils.formatMoney(abs(rem)),
                  dashboardState.primaryCurrency,
                  usedPct,
                ),
              )
            BudgetHealthStatus.OVER_BUDGET ->
              Triple(
                palette.spikeWarningContainer,
                palette.spikeWarningAccent,
                stringResource(
                  R.string.budget_status_over,
                  DateAndFormatUtils.formatMoney(abs(rem)),
                  dashboardState.primaryCurrency,
                  usedPct,
                ),
              )
            BudgetHealthStatus.UNSET -> Triple(Color.Transparent, Color.Transparent, "")
          }
        Surface(
          color = bgColor,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = msg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
      }

      FilledTonalButton(
        onClick = onOpenChartsDashboard,
        shape = MaterialTheme.shapes.large,
        modifier =
          Modifier.fillMaxWidth()
            .height(48.dp)
            .testTag("open_budget_dashboard_button"),
      ) {
        Icon(
          imageVector = Icons.Default.BarChart,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.home_open_charts_dashboard_cta),
          style = MaterialTheme.typography.titleSmall,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HouseholdBudgetDialog(
  initialLimit: Double,
  initialWarningPercent: Double,
  currency: String,
  onDismiss: () -> Unit,
  onSave: (monthlyLimit: Double, warningPercent: Double) -> Unit,
) {
  var limitInput by
    remember(initialLimit) {
      mutableStateOf(
        if (initialLimit > 0.0) DateAndFormatUtils.formatNumber(initialLimit) else ""
      )
    }
  var warningInput by
    remember(initialWarningPercent) {
      mutableStateOf(DateAndFormatUtils.formatNumber(initialWarningPercent))
    }

  val presets = listOf(1500.0, 2500.0, 3500.0, 5000.0, 7500.0)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.budget_dialog_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = stringResource(R.string.budget_dialog_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = stringResource(R.string.budget_quick_presets_label),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
          presets.forEach { preset ->
            AssistChip(
              onClick = { limitInput = preset.toInt().toString() },
              label = { Text("${DateAndFormatUtils.formatMoney(preset)} $currency") },
              modifier = Modifier.testTag("budget_preset_${preset.toInt()}"),
            )
          }
        }

        OutlinedTextField(
          value = limitInput,
          onValueChange = { limitInput = it },
          label = { Text(stringResource(R.string.budget_field_monthly_limit, currency)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("budget_limit_input"),
        )

        OutlinedTextField(
          value = warningInput,
          onValueChange = { warningInput = it },
          label = { Text(stringResource(R.string.budget_field_warning_percent)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("budget_warning_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedLimit = DateAndFormatUtils.parseFlexibleDouble(limitInput) ?: 0.0
          val parsedWarn =
            (DateAndFormatUtils.parseFlexibleDouble(warningInput) ?: 80.0).coerceIn(10.0, 100.0)
          onSave(parsedLimit.coerceAtLeast(0.0), parsedWarn)
        },
        modifier = Modifier.testTag("save_budget_confirm_button"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("cancel_budget_dialog_button"),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}
