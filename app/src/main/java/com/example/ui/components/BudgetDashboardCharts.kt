package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UtilityType
import com.example.domain.CategoryMonthlyExpense
import com.example.domain.CategorySegmentAmount
import com.example.domain.MonthlyCategoryBarPoint
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.DateAndFormatUtils
import java.util.Locale

@Composable
fun categoryChartColor(utilityType: UtilityType, colorIndex: Int = 0): Color {
  val palette = LocalUtilityPalette.current
  return when (utilityType) {
    UtilityType.GAS -> palette.gasAccent
    UtilityType.ELECTRICITY -> palette.electricityAccent
    UtilityType.WATER -> palette.waterAccent
    UtilityType.INTERNET -> palette.internetAccent
    UtilityType.TELECOM -> palette.telecomAccent
    UtilityType.SHOPPING -> palette.shoppingAccent
    UtilityType.DINING -> palette.diningAccent
    UtilityType.MAINTENANCE -> palette.maintenanceAccent
    UtilityType.FUEL -> palette.fuelAccent
    UtilityType.OTHER -> {
      val fallbackCycle =
        listOf(
          palette.otherAccent,
          palette.telecomAccent,
          palette.internetAccent,
          palette.maintenanceAccent,
          palette.fuelAccent,
        )
      fallbackCycle[colorIndex.mod(fallbackCycle.size)]
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RechartsMonthlyCategoryBarChart(
  points: List<MonthlyCategoryBarPoint>,
  legendCategories: List<CategorySegmentAmount>,
  selectedMonth: String,
  monthlyBudgetLimit: Double,
  averageMonthlySpend: Double,
  currency: String,
  onSelectMonth: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (points.isEmpty()) return

  var filteredCategoryKey by remember { mutableStateOf<String?>(null) }
  val activeLegendCat =
    remember(filteredCategoryKey, legendCategories) {
      legendCategories.firstOrNull { it.categoryKey == filteredCategoryKey }
    }
  val effectiveFilterKey = activeLegendCat?.categoryKey

  val selectedIdx =
    remember(points, selectedMonth) {
      val found = points.indexOfFirst { it.yearMonth == selectedMonth }
      if (found >= 0) found else points.lastIndex
    }
  val inspectedPoint = points[selectedIdx]
  val inspectedAmount = inspectedPoint.amountForFilter(effectiveFilterKey)

  val maxBarAmount =
    remember(points, effectiveFilterKey, monthlyBudgetLimit) {
      val barsMax = points.maxOfOrNull { it.amountForFilter(effectiveFilterKey) } ?: 1.0
      val refCeiling =
        if (effectiveFilterKey == null && monthlyBudgetLimit > 0.0) {
          maxOf(barsMax, monthlyBudgetLimit * 1.08)
        } else {
          barsMax * 1.08
        }
      refCeiling.coerceAtLeast(1.0)
    }

  val animProgress by
    animateFloatAsState(
      targetValue = 1f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "barChartProgress",
    )

  val palette = LocalUtilityPalette.current
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  val cursorBandColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
  val activeOutlineColor = MaterialTheme.colorScheme.primary
  val avgLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
  val budgetLineColor =
    if (inspectedPoint.totalAmount > monthlyBudgetLimit && monthlyBudgetLimit > 0.0) {
      palette.spikeWarningAccent
    } else {
      palette.savingsPositiveAccent
    }

  val layoutDir = LocalLayoutDirection.current
  val isRtl = layoutDir == LayoutDirection.Rtl

  val segmentColors =
    legendCategories.associate { seg ->
      seg.categoryKey to categoryChartColor(seg.utilityType, seg.colorIndex)
    }

  Column(modifier = modifier.fillMaxWidth()) {
    // Recharts-style Active Tooltip Card
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
      shape = MaterialTheme.shapes.medium,
      modifier =
        Modifier.fillMaxWidth()
          .semantics(mergeDescendants = true) {}
          .testTag("monthly_bar_tooltip_card"),
    ) {
      Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = inspectedPoint.fullLabel,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (activeLegendCat != null) {
              Text(
                text = activeLegendCat.categoryName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          Text(
            text = "${DateAndFormatUtils.formatMoney(inspectedAmount)} $currency",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        if (effectiveFilterKey == null && inspectedPoint.segments.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            inspectedPoint.segments
              .sortedByDescending { it.amount }
              .take(4)
              .forEach { seg ->
                val dotColor =
                  segmentColors[seg.categoryKey]
                    ?: categoryChartColor(seg.utilityType, seg.colorIndex)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "${seg.categoryName}: ${DateAndFormatUtils.formatMoney(seg.amount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Reference Line Indicators (Budget Ceiling & Average)
    if (effectiveFilterKey == null && (monthlyBudgetLimit > 0.0 || averageMonthlySpend > 0.0)) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (monthlyBudgetLimit > 0.0) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.width(16.dp)
                  .height(3.dp)
                  .clip(RoundedCornerShape(2.dp))
                  .background(budgetLineColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                stringResource(
                  R.string.chart_budget_reference_legend,
                  DateAndFormatUtils.formatMoney(monthlyBudgetLimit),
                  currency,
                ),
              style = MaterialTheme.typography.labelSmall,
              color = budgetLineColor,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
        if (averageMonthlySpend > 0.0) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.width(16.dp)
                  .height(2.5.dp)
                  .clip(RoundedCornerShape(2.dp))
                  .background(avgLineColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                stringResource(
                  R.string.chart_avg_reference_legend,
                  DateAndFormatUtils.formatMoney(averageMonthlySpend),
                  currency,
                ),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }

    // Cartesian Plot Area: Y-Axis Ticks + Interactive Stacked/Filtered Bar Canvas
    val chartDesc = stringResource(R.string.chart_monthly_by_category_title)
    Row(
      verticalAlignment = Alignment.Top,
      modifier = Modifier.fillMaxWidth().height(206.dp),
    ) {
      // Y-Axis Ticks Column
      Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxHeight().padding(bottom = 6.dp, end = 8.dp),
      ) {
        listOf(1.0, 0.75, 0.5, 0.25, 0.0).forEach { ratio ->
          val tickVal = maxBarAmount * ratio
          Text(
            text = DateAndFormatUtils.formatMoney(tickVal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }

      // Main Cartesian Canvas
      Canvas(
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = chartDesc }
            .testTag("recharts_monthly_bar_canvas")
            .pointerInput(points, isRtl) {
              detectTapGestures { offset ->
                if (points.isNotEmpty() && size.width > 0) {
                  val slotW = size.width.toFloat() / points.size.toFloat()
                  val rawSlot = (offset.x / slotW).toInt().coerceIn(0, points.lastIndex)
                  val tappedIndex = if (isRtl) (points.lastIndex - rawSlot) else rawSlot
                  onSelectMonth(points[tappedIndex].yearMonth)
                }
              }
            },
      ) {
        val chartHeight = (size.height - 6.dp.toPx()).coerceAtLeast(1f)
        val count = points.size
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.56f).coerceIn(14.dp.toPx(), 44.dp.toPx())

        // 1. Horizontal Cartesian Grid Lines (5 ticks: 100%, 75%, 50%, 25%, 0%)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
          val y = chartHeight * frac
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect =
              if (frac < 1f) PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) else null,
          )
        }

        // 2. Active Month Cursor Highlight Band (Recharts Tooltip Cursor)
        val activeCenterX =
          if (isRtl) {
            size.width - (slotWidth * selectedIdx + slotWidth / 2f)
          } else {
            slotWidth * selectedIdx + slotWidth / 2f
          }
        drawRoundRect(
          color = cursorBandColor,
          topLeft = Offset(activeCenterX - slotWidth * 0.42f, 0f),
          size = Size(slotWidth * 0.84f, chartHeight),
          cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        )

        // 3. Stacked / Filtered Category Bars per Month
        points.forEachIndexed { idx, point ->
          val centerX =
            if (isRtl) {
              size.width - (slotWidth * idx + slotWidth / 2f)
            } else {
              slotWidth * idx + slotWidth / 2f
            }
          val left = centerX - barWidth / 2f
          var currentBottom = chartHeight

          val visibleSegments =
            if (effectiveFilterKey == null) {
              point.segments
            } else {
              point.segments.filter { it.categoryKey == effectiveFilterKey }
            }

          visibleSegments.forEach { seg ->
            val segHeight =
              ((seg.amount / maxBarAmount).toFloat() * chartHeight * animProgress).coerceAtLeast(0f)
            val top = (currentBottom - segHeight).coerceAtLeast(0f)
            val segColor = segmentColors[seg.categoryKey] ?: palette.otherAccent
            if (currentBottom - top > 0.5f) {
              drawRoundRect(
                color = segColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, currentBottom - top),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
              )
            }
            currentBottom = top
          }

          if (idx == selectedIdx) {
            val shownTotal = point.amountForFilter(effectiveFilterKey)
            val totalBarH =
              ((shownTotal / maxBarAmount).toFloat() * chartHeight * animProgress).coerceAtLeast(4f)
            drawRoundRect(
              color = activeOutlineColor,
              topLeft = Offset(left - 2.5.dp.toPx(), (chartHeight - totalBarH - 2.5.dp.toPx()).coerceAtLeast(0f)),
              size = Size(barWidth + 5.dp.toPx(), totalBarH + 5.dp.toPx()),
              cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
              style = Stroke(width = 2.dp.toPx()),
            )
          }
        }

        // 4. Average Monthly Spend Reference Line
        if (effectiveFilterKey == null && averageMonthlySpend > 0.0) {
          val avgRatio = (averageMonthlySpend / maxBarAmount).toFloat().coerceIn(0f, 1f)
          val avgY = chartHeight * (1f - avgRatio)
          drawLine(
            color = avgLineColor,
            start = Offset(0f, avgY),
            end = Offset(size.width, avgY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
          )
        }

        // 5. Household Monthly Budget Ceiling Reference Line
        if (effectiveFilterKey == null && monthlyBudgetLimit > 0.0) {
          val budgetRatio = (monthlyBudgetLimit / maxBarAmount).toFloat().coerceIn(0f, 1f)
          val budgetY = chartHeight * (1f - budgetRatio)
          drawLine(
            color = budgetLineColor,
            start = Offset(0f, budgetY),
            end = Offset(size.width, budgetY),
            strokeWidth = 2.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 6f), 0f),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Aligned X-Axis Month Labels Row (clickable for accessibility)
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      points.forEachIndexed { idx, pt ->
        val isSelected = idx == selectedIdx
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(8.dp))
              .clickable { onSelectMonth(pt.yearMonth) }
              .testTag("bar_month_axis_${pt.yearMonth}"),
        ) {
          Text(
            text = pt.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Interactive Recharts Legend Chips (Filter by Category)
    if (legendCategories.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        FilterChip(
          selected = effectiveFilterKey == null,
          onClick = { filteredCategoryKey = null },
          label = {
            Text(
              text = stringResource(R.string.chart_filter_all_categories),
              style = MaterialTheme.typography.labelMedium,
            )
          },
          modifier = Modifier.testTag("legend_filter_all"),
        )

        legendCategories.forEach { cat ->
          val dotColor =
            segmentColors[cat.categoryKey]
              ?: categoryChartColor(cat.utilityType, cat.colorIndex)
          val isSelected = effectiveFilterKey == cat.categoryKey
          FilterChip(
            selected = isSelected,
            onClick = {
              filteredCategoryKey = if (isSelected) null else cat.categoryKey
            },
            leadingIcon = {
              Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor),
              )
            },
            label = {
              Text(
                text = cat.categoryName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier.testTag("legend_filter_${cat.categoryKey}"),
          )
        }
      }
    }
  }
}

@Composable
fun RechartsCategoryBreakdownChart(
  categoryExpenses: List<CategoryMonthlyExpense>,
  selectedMonthTotal: Double,
  currency: String,
  onCategoryClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDonutMode by remember { mutableStateOf(false) }
  var highlightedKey by
    remember(categoryExpenses) { mutableStateOf(categoryExpenses.firstOrNull()?.categoryKey) }

  Column(modifier = modifier.fillMaxWidth()) {
    // Mode Switcher (Category Bars vs Interactive Donut)
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      FilterChip(
        selected = !showDonutMode,
        onClick = { showDonutMode = false },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        },
        label = { Text(stringResource(R.string.chart_mode_bars)) },
        modifier = Modifier.testTag("chart_mode_bars_chip"),
      )
      FilterChip(
        selected = showDonutMode,
        onClick = { showDonutMode = true },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.DonutLarge,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        },
        label = { Text(stringResource(R.string.chart_mode_donut)) },
        modifier = Modifier.testTag("chart_mode_donut_chip"),
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (categoryExpenses.isEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stringResource(R.string.chart_no_expenses_in_month),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(24.dp),
        )
      }
      return@Column
    }

    if (showDonutMode) {
      InteractiveCategoryDonutSection(
        items = categoryExpenses,
        totalMonthAmount = selectedMonthTotal,
        currency = currency,
        highlightedKey = highlightedKey,
        onSelectKey = { key -> highlightedKey = key },
        onOpenCategory = onCategoryClick,
      )
    } else {
      CategoryHorizontalBarList(
        items = categoryExpenses,
        currency = currency,
        onCategoryClick = onCategoryClick,
      )
    }
  }
}

@Composable
private fun CategoryHorizontalBarList(
  items: List<CategoryMonthlyExpense>,
  currency: String,
  onCategoryClick: (String) -> Unit,
) {
  val palette = LocalUtilityPalette.current
  val maxCatAmount = (items.maxOfOrNull { it.totalAmount } ?: 1.0).coerceAtLeast(1.0)

  Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
    items.forEach { item ->
      val barColor = categoryChartColor(item.utilityType, item.colorIndex)
      val ratioTarget = (item.totalAmount / maxCatAmount).toFloat().coerceIn(0.04f, 1f)
      val animatedRatio by
        animateFloatAsState(
          targetValue = ratioTarget,
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          label = "catBar_${item.categoryKey}",
        )

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.large,
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = !item.categoryId.isNullOrBlank()) {
              item.categoryId?.let(onCategoryClick)
            }
            .testTag("category_expense_bar_${item.categoryKey}"),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              UtilityIconAvatar(utilityType = item.utilityType, sizeDp = 40)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = item.categoryName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    "${stringResource(R.string.chart_bills_count_badge, item.billsCount)} • ${
                      stringResource(R.string.chart_share_of_month, item.sharePercentage)
                    }",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "${DateAndFormatUtils.formatMoney(item.totalAmount)} $currency",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                item.changeFromPreviousPercent?.let { deltaPct ->
                  val isUp = deltaPct > 2.0
                  val isDown = deltaPct < -2.0
                  val deltaColor =
                    when {
                      isUp -> palette.spikeWarningAccent
                      isDown -> palette.savingsPositiveAccent
                      else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector =
                        if (deltaPct >= 0) Icons.AutoMirrored.Filled.TrendingUp
                        else Icons.AutoMirrored.Filled.TrendingDown,
                      contentDescription = null,
                      tint = deltaColor,
                      modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                      text = String.format(Locale.US, "%+.1f%%", deltaPct),
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = deltaColor,
                    )
                  }
                }
              }
              if (!item.categoryId.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Proportional Recharts Horizontal Bar Track
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth(animatedRatio)
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(barColor),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InteractiveCategoryDonutSection(
  items: List<CategoryMonthlyExpense>,
  totalMonthAmount: Double,
  currency: String,
  highlightedKey: String?,
  onSelectKey: (String) -> Unit,
  onOpenCategory: (String) -> Unit,
) {
  val activeItem =
    items.firstOrNull { it.categoryKey == highlightedKey } ?: items.first()
  val sliceColors =
    items.associate { it.categoryKey to categoryChartColor(it.utilityType, it.colorIndex) }
  val donutDesc = stringResource(R.string.analytics_category_share_title)
  val activeStroke by
    animateDpAsState(targetValue = 30.dp, label = "activeDonutStroke")

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(196.dp),
    ) {
      Canvas(
        modifier =
          Modifier.size(168.dp)
            .semantics { contentDescription = donutDesc }
            .testTag("recharts_category_donut_canvas"),
      ) {
        val baseStroke = 23.dp.toPx()
        val highlightedStroke = activeStroke.toPx()
        var startAngle = -90f
        items.forEach { slice ->
          val sweep = (slice.shareFraction * 360f).coerceAtLeast(3f)
          val isHighlighted = slice.categoryKey == activeItem.categoryKey
          drawArc(
            color = sliceColors[slice.categoryKey] ?: Color.Gray,
            startAngle = startAngle,
            sweepAngle = (sweep - 2.5f).coerceAtLeast(1.5f),
            useCenter = false,
            style =
              Stroke(
                width = if (isHighlighted) highlightedStroke else baseStroke,
                cap = StrokeCap.Butt,
              ),
          )
          startAngle += sweep
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp),
      ) {
        Text(
          text = activeItem.categoryName,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = DateAndFormatUtils.formatMoney(activeItem.totalAmount),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = String.format(Locale.US, "%.1f%% • %s", activeItem.sharePercentage, currency),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      items.forEach { item ->
        val isSelected = item.categoryKey == activeItem.categoryKey
        val dotColor = sliceColors[item.categoryKey] ?: Color.Gray
        Surface(
          color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
          shape = MaterialTheme.shapes.medium,
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 48.dp)
              .clip(MaterialTheme.shapes.medium)
              .clickable {
                if (isSelected && !item.categoryId.isNullOrBlank()) {
                  onOpenCategory(item.categoryId)
                } else {
                  onSelectKey(item.categoryKey)
                }
              }
              .testTag("donut_legend_row_${item.categoryKey}"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = item.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text =
                "${DateAndFormatUtils.formatMoney(item.totalAmount)} $currency (${
                  String.format(Locale.US, "%.0f%%", item.sharePercentage)
                })",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color =
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
