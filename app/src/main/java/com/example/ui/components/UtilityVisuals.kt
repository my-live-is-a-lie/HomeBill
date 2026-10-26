package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UtilityType
import com.example.domain.AlertSeverity
import com.example.domain.BillComparisonResult
import com.example.domain.VarianceAlertItem
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.DateAndFormatUtils
import java.util.Locale

data class UtilityVisualSpec(
  val icon: ImageVector,
  val accentColor: Color,
  val containerColor: Color,
)

@Composable
fun UtilityType.visualSpec(): UtilityVisualSpec {
  val p = LocalUtilityPalette.current
  return when (this) {
    UtilityType.GAS ->
      UtilityVisualSpec(Icons.Default.LocalFireDepartment, p.gasAccent, p.gasContainer)
    UtilityType.ELECTRICITY ->
      UtilityVisualSpec(Icons.Default.Bolt, p.electricityAccent, p.electricityContainer)
    UtilityType.WATER ->
      UtilityVisualSpec(Icons.Default.WaterDrop, p.waterAccent, p.waterContainer)
    UtilityType.INTERNET ->
      UtilityVisualSpec(Icons.Default.Wifi, p.internetAccent, p.internetContainer)
    UtilityType.TELECOM ->
      UtilityVisualSpec(Icons.Default.PhoneInTalk, p.telecomAccent, p.telecomContainer)
    UtilityType.SHOPPING ->
      UtilityVisualSpec(Icons.Default.ShoppingCart, p.shoppingAccent, p.shoppingContainer)
    UtilityType.DINING ->
      UtilityVisualSpec(Icons.Default.Restaurant, p.diningAccent, p.diningContainer)
    UtilityType.MAINTENANCE ->
      UtilityVisualSpec(Icons.Default.Build, p.maintenanceAccent, p.maintenanceContainer)
    UtilityType.FUEL ->
      UtilityVisualSpec(Icons.Default.LocalGasStation, p.fuelAccent, p.fuelContainer)
    UtilityType.OTHER ->
      UtilityVisualSpec(Icons.AutoMirrored.Filled.ReceiptLong, p.otherAccent, p.otherContainer)
  }
}

@Composable
fun UtilityIconAvatar(
  utilityType: UtilityType,
  modifier: Modifier = Modifier,
  sizeDp: Int = 46,
) {
  val spec = utilityType.visualSpec()
  Box(
    modifier =
      modifier
        .size(sizeDp.dp)
        .clip(CircleShape)
        .background(spec.containerColor),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = spec.icon,
      contentDescription = stringResource(utilityType.labelRes),
      tint = spec.accentColor,
      modifier = Modifier.size((sizeDp * 0.52f).dp),
    )
  }
}

@Composable
fun VarianceBadge(
  comparison: BillComparisonResult?,
  modifier: Modifier = Modifier,
) {
  val comp = comparison ?: return
  val pct = comp.amountChangePercent ?: comp.consumptionChangePercent ?: return
  val palette = LocalUtilityPalette.current

  val (bgColor, fgColor, icon) =
    when {
      comp.severity == AlertSeverity.HIGH_SPIKE ->
        Triple(
          palette.spikeWarningContainer,
          palette.spikeWarningAccent,
          Icons.Default.WarningAmber,
        )
      pct > 2.0 ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.secondary,
          Icons.AutoMirrored.Filled.TrendingUp,
        )
      pct < -2.0 ->
        Triple(
          palette.savingsPositiveContainer,
          palette.savingsPositiveAccent,
          Icons.AutoMirrored.Filled.TrendingDown,
        )
      else ->
        Triple(
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
          Icons.AutoMirrored.Filled.TrendingUp,
        )
    }

  Surface(
    color = bgColor,
    shape = CircleShape,
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fgColor,
        modifier = Modifier.size(14.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = String.format(Locale.US, "%+.1f%%", pct),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = fgColor,
      )
    }
  }
}

@Composable
fun VarianceAlertBannerCard(
  alert: VarianceAlertItem,
  onOpenComparison: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalUtilityPalette.current
  val comp = alert.comparison
  Card(
    modifier = modifier.fillMaxWidth().clickable(onClick = onOpenComparison),
    colors = CardDefaults.cardColors(containerColor = palette.spikeWarningContainer),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = stringResource(R.string.variance_spike_alert_title),
            tint = palette.spikeWarningAccent,
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "${stringResource(R.string.variance_spike_alert_title)} • ${alert.category.name}",
              style = MaterialTheme.typography.titleSmall,
              color = palette.spikeWarningAccent,
            )
            Text(
              text = DateAndFormatUtils.formatIsoDateArabic(alert.bill.billDateIso),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        TextButton(onClick = onOpenComparison) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = null,
            tint = palette.spikeWarningAccent,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.compare_previous_bill),
            color = palette.spikeWarningAccent,
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }

      if (comp.isFinancialSpike && comp.amountChangePercent != null) {
        Text(
          text =
            stringResource(
              R.string.variance_amount_up,
              comp.amountChangePercent,
              comp.amountDiff,
              alert.bill.currency,
            ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
      if (comp.isConsumptionSpike && comp.consumptionChangePercent != null) {
        Text(
          text =
            stringResource(
              R.string.variance_consumption_up,
              comp.consumptionChangePercent,
              comp.consumptionDiff,
              alert.bill.consumptionUnit,
            ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}
