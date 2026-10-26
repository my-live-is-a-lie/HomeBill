package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UtilityType
import com.example.domain.CategoryTrendPoint
import com.example.domain.MonthlyCostPoint
import com.example.domain.UtilityShareSlice
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.DateAndFormatUtils
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyStackedBarChart(
  points: List<MonthlyCostPoint>,
  averageCost: Double,
  currency: String,
  modifier: Modifier = Modifier,
) {
  if (points.isEmpty()) return

  var selectedIndex by remember(points.size) { mutableIntStateOf(points.lastIndex) }
  val safeIndex = selectedIndex.coerceIn(0, points.lastIndex)
  val selectedPoint = points[safeIndex]
  val palette = LocalUtilityPalette.current
  val maxTotal = (points.maxOfOrNull { it.totalAmount } ?: 1.0).coerceAtLeast(1.0)
  val avgLineColor = MaterialTheme.colorScheme.outline
  val highlightBorderColor = MaterialTheme.colorScheme.primary

  fun utilityColor(type: UtilityType): Color =
    when (type) {
      UtilityType.GAS -> palette.gasAccent
      UtilityType.ELECTRICITY -> palette.electricityAccent
      UtilityType.WATER -> palette.waterAccent
      UtilityType.INTERNET -> palette.internetAccent
      UtilityType.TELECOM -> palette.telecomAccent
      UtilityType.SHOPPING -> palette.shoppingAccent
      UtilityType.DINING -> palette.diningAccent
      UtilityType.MAINTENANCE -> palette.maintenanceAccent
      UtilityType.FUEL -> palette.fuelAccent
      UtilityType.OTHER -> palette.otherAccent
    }

  Column(modifier = modifier.fillMaxWidth()) {
    // Selected month summary strip
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        Text(
          text = selectedPoint.fullLabel,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "${DateAndFormatUtils.formatMoney(selectedPoint.totalAmount)} $currency",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
      val count = points.size
      val slotWidth = size.width / count.toFloat()
      val barWidth = (slotWidth * 0.54f).coerceIn(14.dp.toPx(), 42.dp.toPx())
      val chartHeight = size.height - 8.dp.toPx()

      // Dashed average line
      if (averageCost > 0.0) {
        val avgRatio = (averageCost / maxTotal).toFloat().coerceIn(0f, 1f)
        val avgY = chartHeight * (1f - avgRatio)
        drawLine(
          color = avgLineColor,
          start = Offset(0f, avgY),
          end = Offset(size.width, avgY),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
        )
      }

      points.forEachIndexed { idx, point ->
        val centerX = slotWidth * idx + slotWidth / 2f
        val left = centerX - barWidth / 2f
        var currentBottom = chartHeight

        val orderedSegments = point.byUtility.entries.sortedBy { it.key.ordinal }
        orderedSegments.forEach { (uType, amount) ->
          val segHeight = ((amount / maxTotal).toFloat() * chartHeight).coerceAtLeast(0f)
          val top = (currentBottom - segHeight).coerceAtLeast(0f)
          drawRoundRect(
            color = utilityColor(uType),
            topLeft = Offset(left, top),
            size = Size(barWidth, currentBottom - top),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
          )
          currentBottom = top
        }

        if (idx == safeIndex) {
          val totalBarH = ((point.totalAmount / maxTotal).toFloat() * chartHeight).coerceAtLeast(4f)
          drawRoundRect(
            color = highlightBorderColor,
            topLeft = Offset(left - 3.dp.toPx(), chartHeight - totalBarH - 3.dp.toPx()),
            size = Size(barWidth + 6.dp.toPx(), totalBarH + 6.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Interactive month selector pills
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
      points.forEachIndexed { idx, pt ->
        FilterChip(
          selected = idx == safeIndex,
          onClick = { selectedIndex = idx },
          label = {
            Text(
              text = pt.shortLabel,
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Breakdown of selected month by utility
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      selectedPoint.byUtility.entries
        .sortedByDescending { it.value }
        .forEach { (uType, amount) ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(10.dp)
                  .clip(CircleShape)
                  .background(utilityColor(uType))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "${stringResource(uType.labelRes)}: ${DateAndFormatUtils.formatMoney(amount)} $currency",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
    }
  }
}

@Composable
fun UtilityDonutChart(
  slices: List<UtilityShareSlice>,
  currency: String,
  modifier: Modifier = Modifier,
) {
  if (slices.isEmpty()) return
  val palette = LocalUtilityPalette.current

  fun sliceColor(type: UtilityType): Color =
    when (type) {
      UtilityType.GAS -> palette.gasAccent
      UtilityType.ELECTRICITY -> palette.electricityAccent
      UtilityType.WATER -> palette.waterAccent
      UtilityType.INTERNET -> palette.internetAccent
      UtilityType.TELECOM -> palette.telecomAccent
      UtilityType.SHOPPING -> palette.shoppingAccent
      UtilityType.DINING -> palette.diningAccent
      UtilityType.MAINTENANCE -> palette.maintenanceAccent
      UtilityType.FUEL -> palette.fuelAccent
      UtilityType.OTHER -> palette.otherAccent
    }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(20.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier.size(148.dp),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.size(136.dp)) {
        val strokeWidth = 24.dp.toPx()
        var startAngle = -90f
        slices.forEach { slice ->
          val sweep = (slice.fraction * 360f).coerceAtLeast(2f)
          drawArc(
            color = sliceColor(slice.utilityType),
            startAngle = startAngle,
            sweepAngle = (sweep - 2f).coerceAtLeast(1f),
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
          )
          startAngle += sweep
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val total = slices.sumOf { it.totalAmount }
        Text(
          text = DateAndFormatUtils.formatMoney(total),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = currency,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      slices.forEach { slice ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier =
                Modifier.size(10.dp)
                  .clip(CircleShape)
                  .background(sliceColor(slice.utilityType))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(slice.utilityType.labelRes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Text(
            text = String.format(Locale.US, "%.0f%%", slice.percentage),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
fun CategorySmoothTrendChart(
  points: List<CategoryTrendPoint>,
  showConsumption: Boolean,
  unitOrCurrency: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  if (points.isEmpty()) return
  val values = points.map { if (showConsumption) it.consumption else it.amount }
  val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
  val minVal = (values.minOrNull() ?: 0.0).coerceAtMost(maxVal * 0.85)
  val range = (maxVal - minVal).coerceAtLeast(1.0)

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
      val padX = 14.dp.toPx()
      val padY = 14.dp.toPx()
      val usableW = (size.width - padX * 2).coerceAtLeast(1f)
      val usableH = (size.height - padY * 2).coerceAtLeast(1f)

      val offsets =
        values.mapIndexed { index, v ->
          val x =
            if (values.size == 1) size.width / 2f
            else padX + (index.toFloat() / (values.size - 1).toFloat()) * usableW
          val norm = ((v - minVal) / range).toFloat().coerceIn(0f, 1f)
          val y = padY + usableH * (1f - norm)
          Offset(x, y)
        }

      if (offsets.size >= 2) {
        val strokePath =
          Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            for (i in 1 until offsets.size) {
              val prev = offsets[i - 1]
              val curr = offsets[i]
              val midX = (prev.x + curr.x) / 2f
              cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
          }

        val fillPath =
          Path().apply {
            addPath(strokePath)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
          }

        drawPath(
          path = fillPath,
          brush =
            Brush.verticalGradient(
              colors = listOf(accentColor.copy(alpha = 0.30f), accentColor.copy(alpha = 0.0f))
            ),
        )

        drawPath(
          path = strokePath,
          color = accentColor,
          style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      offsets.forEach { pt ->
        drawCircle(color = accentColor, radius = 5.dp.toPx(), center = pt)
        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = pt)
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      val first = points.first()
      val last = points.last()
      val firstVal = if (showConsumption) first.consumption else first.amount
      val lastVal = if (showConsumption) last.consumption else last.amount
      Text(
        text = "${first.dateLabel}: ${DateAndFormatUtils.formatNumber(firstVal)} $unitOrCurrency",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (points.size > 1) {
        Text(
          text = "${last.dateLabel}: ${DateAndFormatUtils.formatNumber(lastVal)} $unitOrCurrency",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = accentColor,
        )
      }
    }
  }
}
