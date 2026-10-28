package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.UtilityType
import com.example.domain.BillAnalyticsEngine
import com.example.domain.BillComparisonResult
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.BillImageStore
import com.example.util.BillSheetExporter
import com.example.util.DateAndFormatUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSheetModal(
  bill: BillRecord,
  comparison: BillComparisonResult?,
  onEditBill: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val attachedImage =
    remember(bill.id) {
      BillImageStore.imageFile(context, bill.id).takeIf { it.exists() && it.length() > 0L }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.bill_sheet_modal_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${bill.categoryName} • ${DateAndFormatUtils.formatIsoDateArabic(bill.billDateIso)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row {
          IconButton(
            onClick = {
              onDismiss()
              onEditBill()
            },
            modifier = Modifier.testTag("sheet_edit_bill_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = stringResource(R.string.form_edit_bill_title),
            )
          }
          IconButton(
            onClick = { BillSheetExporter.shareSingleBillCsv(context, bill, comparison) },
            modifier = Modifier.testTag("sheet_export_csv_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = stringResource(R.string.export_single_sheet_csv),
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Summary KPI Strip
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
          ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(14.dp),
        ) {
          Column {
            Text(
              text = stringResource(R.string.metric_amount),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${DateAndFormatUtils.formatMoney(bill.totalAmount)} ${bill.currency}",
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = stringResource(R.string.metric_consumption),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${DateAndFormatUtils.formatNumber(bill.consumptionValue)} ${bill.consumptionUnit}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Original scanned bill image (stored on this phone) with a save-to-gallery action
      if (attachedImage != null) {
        val savedMessage = stringResource(R.string.bill_image_saved)
        val failedMessage = stringResource(R.string.bill_image_save_failed)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.bill_image_title),
          style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AsyncImage(
          model = attachedImage,
          contentDescription = stringResource(R.string.bill_image_content_desc),
          contentScale = ContentScale.Fit,
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(max = 520.dp)
              .clip(MaterialTheme.shapes.medium)
              .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
              .testTag("bill_attached_image"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = {
            val ok =
              BillImageStore.saveToGallery(
                context,
                bill.id,
                bill.providerName.ifBlank { bill.categoryName },
              )
            Toast.makeText(context, if (ok) savedMessage else failedMessage, Toast.LENGTH_LONG)
              .show()
          },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_bill_image_button"),
        ) {
          Icon(imageVector = Icons.Default.Download, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.bill_image_save_to_gallery))
        }

        // Digital replica of the bill (same size, text in the same places) with JPEG/PDF/Word export
        Spacer(modifier = Modifier.height(16.dp))
        BillReplicaSection(bill = bill)
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Structured Spreadsheet Table
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
          Text(
            text = stringResource(R.string.sheet_col_item),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1.2f),
          )
          Text(
            text = stringResource(R.string.sheet_col_value),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = stringResource(R.string.sheet_col_unit),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(0.55f),
          )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        val rowsToShow =
          bill.sheetItems.ifEmpty {
            buildList {
              if (bill.providerName.isNotBlank()) add(BillSheetRow("الجهة المصدرة", bill.providerName, ""))
              if (bill.invoiceNumber.isNotBlank()) add(BillSheetRow("رقم الفاتورة", bill.invoiceNumber, ""))
              add(BillSheetRow("التاريخ", bill.billDateIso, ""))
              if (bill.previousReading > 0) {
                add(
                  BillSheetRow(
                    "القراءة السابقة",
                    DateAndFormatUtils.formatNumber(bill.previousReading),
                    bill.consumptionUnit,
                  )
                )
              }
              if (bill.currentReading > 0) {
                add(
                  BillSheetRow(
                    "القراءة الحالية",
                    DateAndFormatUtils.formatNumber(bill.currentReading),
                    bill.consumptionUnit,
                  )
                )
              }
              add(
                BillSheetRow(
                  "كمية الاستهلاك",
                  DateAndFormatUtils.formatNumber(bill.consumptionValue),
                  bill.consumptionUnit,
                )
              )
              add(
                BillSheetRow(
                  "الإجمالي المستحق",
                  DateAndFormatUtils.formatMoney(bill.totalAmount),
                  bill.currency,
                )
              )
            }
          }

        rowsToShow.forEachIndexed { idx, row ->
          val rowBg =
            if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.surface
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth()
                .background(rowBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            Text(
              text = row.label,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1.2f),
            )
            Text(
              text = row.value,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = row.unit.ifBlank { "—" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(0.55f),
            )
          }
          if (idx < rowsToShow.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          }
        }
      }

      if (bill.rawExtractedText.isNotBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.extracted_raw_text_label),
          style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = bill.rawExtractedText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
      Button(
        onClick = { BillSheetExporter.shareSingleBillCsv(context, bill, comparison) },
        modifier = Modifier.fillMaxWidth().height(50.dp),
      ) {
        Icon(imageVector = Icons.Default.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.export_single_sheet_csv))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillComparisonModal(
  comparison: BillComparisonResult,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val palette = LocalUtilityPalette.current
  val current = comparison.currentBill
  val prev = comparison.previousBill

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp)
    ) {
      Text(
        text = stringResource(R.string.comparison_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = current.categoryName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (prev == null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = stringResource(R.string.no_previous_bill_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
          )
        }
      } else {
        if (comparison.hasSpike) {
          Surface(
            color = palette.spikeWarningContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.WarningAmber,
                  contentDescription = null,
                  tint = palette.spikeWarningAccent,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = stringResource(R.string.variance_spike_alert_title),
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.spikeWarningAccent,
                )
              }
              if (comparison.isFinancialSpike && comparison.amountChangePercent != null) {
                Text(
                  text =
                    stringResource(
                      R.string.variance_amount_up,
                      comparison.amountChangePercent,
                      comparison.amountDiff,
                      current.currency,
                    ),
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(top = 6.dp),
                )
              }
              if (comparison.isConsumptionSpike && comparison.consumptionChangePercent != null) {
                Text(
                  text =
                    stringResource(
                      R.string.variance_consumption_up,
                      comparison.consumptionChangePercent,
                      comparison.consumptionDiff,
                      current.consumptionUnit,
                    ),
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(top = 4.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
        }

        // Side-by-side comparison cards
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
              ),
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = stringResource(R.string.current_bill_label),
                style = MaterialTheme.typography.labelMedium,
              )
              Text(
                text = DateAndFormatUtils.formatIsoDateArabic(current.billDateIso),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "${DateAndFormatUtils.formatMoney(current.totalAmount)} ${current.currency}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text =
                  "${DateAndFormatUtils.formatNumber(current.consumptionValue)} ${current.consumptionUnit}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }

          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = stringResource(R.string.previous_bill_label),
                style = MaterialTheme.typography.labelMedium,
              )
              Text(
                text = DateAndFormatUtils.formatIsoDateArabic(prev.billDateIso),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "${DateAndFormatUtils.formatMoney(prev.totalAmount)} ${prev.currency}",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "${DateAndFormatUtils.formatNumber(prev.consumptionValue)} ${prev.consumptionUnit}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }

        if (comparison.sheetDeltas.isNotEmpty()) {
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = stringResource(R.string.sheet_rows_section_title),
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
          ) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .padding(10.dp)
            ) {
              Text(
                text = stringResource(R.string.sheet_col_item),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1.2f),
              )
              Text(
                text = stringResource(R.string.current_bill_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(0.9f),
              )
              Text(
                text = stringResource(R.string.previous_bill_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(0.9f),
              )
            }
            comparison.sheetDeltas.forEach { delta ->
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
              Row(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                  text = delta.label,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.weight(1.2f),
                )
                Text(
                  text = "${delta.currentValue} ${delta.unit}".trim(),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(0.9f),
                )
                Text(
                  text = "${delta.previousValue} ${delta.unit}".trim(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(0.9f),
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFormDialog(
  existing: BillCategory? = null,
  onDismiss: () -> Unit,
  onSave: (name: String, utilityType: UtilityType, unit: String, threshold: Double) -> Unit,
) {
  var selectedUtility by
    remember(existing) {
      mutableStateOf(UtilityType.fromKey(existing?.utilityType ?: UtilityType.GAS.firestoreKey))
    }
  var name by remember(existing) { mutableStateOf(existing?.name ?: selectedUtility.defaultCategoryName) }
  var unit by remember(existing) { mutableStateOf(existing?.defaultUnit ?: selectedUtility.defaultUnit) }
  var threshold by remember(existing) { mutableDoubleStateOf(existing?.alertThresholdPercent ?: 25.0) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          stringResource(
            if (existing == null) R.string.dialog_new_category_title
            else R.string.dialog_edit_category_title
          )
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = stringResource(R.string.field_utility_type),
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          UtilityType.entries.forEach { uType ->
            FilterChip(
              selected = selectedUtility == uType,
              onClick = {
                val wasDefaultName = name == selectedUtility.defaultCategoryName || name.isBlank()
                selectedUtility = uType
                if (existing == null && wasDefaultName) {
                  name = uType.defaultCategoryName
                  unit = uType.defaultUnit
                }
              },
              label = { Text(stringResource(uType.labelRes)) },
            )
          }
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.field_category_name)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("category_name_input"),
        )

        OutlinedTextField(
          value = unit,
          onValueChange = { unit = it },
          label = { Text(stringResource(R.string.field_default_unit)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("category_unit_input"),
        )

        Column {
          Text(
            text =
              "${stringResource(R.string.field_alert_threshold)}: +${threshold.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
          )
          Slider(
            value = threshold.toFloat(),
            onValueChange = { threshold = (it.toInt()).toDouble() },
            valueRange = 10f..100f,
            steps = 17,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(name, selectedUtility, unit, threshold) },
        modifier = Modifier.testTag("save_category_button"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillFormBottomSheet(
  category: BillCategory,
  existingBill: BillRecord? = null,
  existingCategoryBills: List<BillRecord>,
  currentUserId: String,
  onDismiss: () -> Unit,
  onSaveBill: (BillRecord) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var billDateIso by
    remember(existingBill) {
      mutableStateOf(
        existingBill?.billDateIso
          ?: DateAndFormatUtils.epochMillisToIsoDate(DateAndFormatUtils.todayEpochMillis())
      )
    }
  var showDatePicker by remember { mutableStateOf(false) }

  var totalAmountText by
    remember(existingBill) {
      mutableStateOf(existingBill?.totalAmount?.let { DateAndFormatUtils.formatNumber(it) } ?: "")
    }
  var currency by remember(existingBill) { mutableStateOf(existingBill?.currency ?: "ر.س") }
  var prevReadingText by
    remember(existingBill) {
      mutableStateOf(
        existingBill?.previousReading?.takeIf { it > 0 }?.let { DateAndFormatUtils.formatNumber(it) }
          ?: ""
      )
    }
  var currReadingText by
    remember(existingBill) {
      mutableStateOf(
        existingBill?.currentReading?.takeIf { it > 0 }?.let { DateAndFormatUtils.formatNumber(it) }
          ?: ""
      )
    }
  var consumptionText by
    remember(existingBill) {
      mutableStateOf(
        existingBill?.consumptionValue?.takeIf { it > 0 }?.let { DateAndFormatUtils.formatNumber(it) }
          ?: ""
      )
    }
  var consumptionUnit by
    remember(existingBill) { mutableStateOf(existingBill?.consumptionUnit ?: category.defaultUnit) }
  var invoiceNumber by remember(existingBill) { mutableStateOf(existingBill?.invoiceNumber ?: "") }
  var providerName by remember(existingBill) { mutableStateOf(existingBill?.providerName ?: "") }
  var notes by remember(existingBill) { mutableStateOf(existingBill?.notes ?: "") }

  val sheetRows =
    remember(existingBill) {
      mutableStateListOf<BillSheetRow>().apply {
        if (existingBill != null) addAll(existingBill.sheetItems)
      }
    }

  val parsedAmount = DateAndFormatUtils.parseFlexibleDouble(totalAmountText) ?: 0.0
  val parsedPrev = DateAndFormatUtils.parseFlexibleDouble(prevReadingText) ?: 0.0
  val parsedCurr = DateAndFormatUtils.parseFlexibleDouble(currReadingText) ?: 0.0
  val parsedCons =
    DateAndFormatUtils.parseFlexibleDouble(consumptionText)
      ?: if (parsedCurr > parsedPrev && parsedPrev > 0) (parsedCurr - parsedPrev) else 0.0

  val previewDraft =
    BillRecord(
      id = existingBill?.id ?: "",
      userId = currentUserId,
      categoryId = category.id,
      categoryName = category.name,
      utilityType = category.utilityType,
      billDateIso = billDateIso,
      billingMonth = DateAndFormatUtils.isoDateToBillingMonth(billDateIso),
      billDateEpochMillis = DateAndFormatUtils.isoDateToEpochMillis(billDateIso),
      totalAmount = parsedAmount,
      currency = currency,
      consumptionValue = parsedCons,
      consumptionUnit = consumptionUnit,
    )
  val liveComparison =
    remember(parsedAmount, parsedCons, billDateIso, existingCategoryBills) {
      BillAnalyticsEngine.previewDraftComparison(
        previewDraft,
        existingCategoryBills,
        category.alertThresholdPercent,
      )
    }

  if (showDatePicker) {
    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis = DateAndFormatUtils.isoDateToEpochMillis(billDateIso)
      )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              billDateIso = DateAndFormatUtils.epochMillisToIsoDate(millis)
            }
            showDatePicker = false
          }
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp),
    ) {
      Text(
        text =
          stringResource(
            if (existingBill == null) R.string.form_add_bill_title else R.string.form_edit_bill_title
          ),
        style = MaterialTheme.typography.titleLarge,
      )

      // Date Picker Row
      OutlinedButton(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_bill_date_button"),
      ) {
        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text =
            "${stringResource(R.string.field_bill_date)}: ${DateAndFormatUtils.formatIsoDateArabic(billDateIso)}"
        )
      }

      if (liveComparison.hasSpike && (parsedAmount > 0 || parsedCons > 0)) {
        val palette = LocalUtilityPalette.current
        Surface(
          color = palette.spikeWarningContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = palette.spikeWarningAccent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            val pct =
              liveComparison.amountChangePercent ?: liveComparison.consumptionChangePercent ?: 0.0
            Text(
              text =
                "تنبيه فوري: القيمة المدخلة أعلى بنسبة +${String.format(Locale.US, "%.1f", pct)}% من الفاتورة السابقة!",
              style = MaterialTheme.typography.bodySmall,
              color = palette.spikeWarningAccent,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = totalAmountText,
          onValueChange = { totalAmountText = it },
          label = { Text(stringResource(R.string.field_total_amount)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1.4f).testTag("bill_amount_input"),
        )
        OutlinedTextField(
          value = currency,
          onValueChange = { currency = it },
          label = { Text(stringResource(R.string.field_currency)) },
          singleLine = true,
          modifier = Modifier.weight(0.7f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = prevReadingText,
          onValueChange = {
            prevReadingText = it
            val p = DateAndFormatUtils.parseFlexibleDouble(it) ?: 0.0
            val c = DateAndFormatUtils.parseFlexibleDouble(currReadingText) ?: 0.0
            if (c > p && p > 0) consumptionText = DateAndFormatUtils.formatNumber(c - p)
          },
          label = { Text(stringResource(R.string.field_prev_reading)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = currReadingText,
          onValueChange = {
            currReadingText = it
            val p = DateAndFormatUtils.parseFlexibleDouble(prevReadingText) ?: 0.0
            val c = DateAndFormatUtils.parseFlexibleDouble(it) ?: 0.0
            if (c > p && p > 0) consumptionText = DateAndFormatUtils.formatNumber(c - p)
          },
          label = { Text(stringResource(R.string.field_curr_reading)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = consumptionText,
          onValueChange = { consumptionText = it },
          label = { Text(stringResource(R.string.field_consumption_value)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1.4f).testTag("bill_consumption_input"),
        )
        OutlinedTextField(
          value = consumptionUnit,
          onValueChange = { consumptionUnit = it },
          label = { Text(stringResource(R.string.field_consumption_unit)) },
          singleLine = true,
          modifier = Modifier.weight(0.7f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = invoiceNumber,
          onValueChange = { invoiceNumber = it },
          label = { Text(stringResource(R.string.field_invoice_number)) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = providerName,
          onValueChange = { providerName = it },
          label = { Text(stringResource(R.string.field_provider_name)) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text(stringResource(R.string.field_notes)) },
        modifier = Modifier.fillMaxWidth(),
      )

      // Sheet Rows Editor
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stringResource(R.string.sheet_rows_section_title),
          style = MaterialTheme.typography.titleSmall,
        )
        TextButton(
          onClick = {
            if (sheetRows.size < 25) sheetRows.add(BillSheetRow("", "", ""))
          }
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(stringResource(R.string.add_sheet_row))
        }
      }

      sheetRows.forEachIndexed { idx, row ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = row.label,
            onValueChange = { sheetRows[idx] = row.copy(label = it) },
            label = { Text(stringResource(R.string.field_row_label)) },
            singleLine = true,
            modifier = Modifier.weight(1.2f),
          )
          OutlinedTextField(
            value = row.value,
            onValueChange = { sheetRows[idx] = row.copy(value = it) },
            label = { Text(stringResource(R.string.field_row_value)) },
            singleLine = true,
            modifier = Modifier.weight(0.9f),
          )
          IconButton(onClick = { sheetRows.removeAt(idx) }) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = stringResource(R.string.action_delete),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = {
          val finalSheet =
            sheetRows.filter { it.label.isNotBlank() }.ifEmpty {
              buildList {
                if (providerName.isNotBlank()) add(BillSheetRow("الجهة المصدرة", providerName, ""))
                if (invoiceNumber.isNotBlank()) add(BillSheetRow("رقم الفاتورة", invoiceNumber, ""))
                add(BillSheetRow("تاريخ الفاتورة", billDateIso, ""))
                if (parsedPrev > 0) {
                  add(BillSheetRow("القراءة السابقة", DateAndFormatUtils.formatNumber(parsedPrev), consumptionUnit))
                }
                if (parsedCurr > 0) {
                  add(BillSheetRow("القراءة الحالية", DateAndFormatUtils.formatNumber(parsedCurr), consumptionUnit))
                }
                if (parsedCons > 0) {
                  add(BillSheetRow("كمية الاستهلاك", DateAndFormatUtils.formatNumber(parsedCons), consumptionUnit))
                }
                add(BillSheetRow("الإجمالي المستحق", DateAndFormatUtils.formatMoney(parsedAmount), currency))
              }
            }

          onSaveBill(
            BillRecord(
              id = existingBill?.id ?: "",
              userId = currentUserId,
              categoryId = category.id,
              categoryName = category.name,
              utilityType = category.utilityType,
              billDateIso = billDateIso,
              billingMonth = DateAndFormatUtils.isoDateToBillingMonth(billDateIso),
              billDateEpochMillis = DateAndFormatUtils.isoDateToEpochMillis(billDateIso),
              totalAmount = parsedAmount,
              currency = currency,
              consumptionValue = parsedCons,
              consumptionUnit = consumptionUnit,
              previousReading = parsedPrev,
              currentReading = parsedCurr,
              invoiceNumber = invoiceNumber,
              providerName = providerName,
              rawExtractedText = existingBill?.rawExtractedText ?: "",
              notes = notes,
              sourceType = existingBill?.sourceType ?: "manual",
              sheetItems = finalSheet,
              createdAt = existingBill?.createdAt,
              updatedAt = existingBill?.updatedAt,
            )
          )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_bill_button"),
      ) {
        Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
