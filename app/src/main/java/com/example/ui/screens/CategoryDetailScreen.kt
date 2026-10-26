package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.domain.BillAnalyticsEngine
import com.example.domain.BillComparisonResult
import com.example.domain.BillSearchEngine
import com.example.domain.BillSearchScope
import com.example.ui.components.BillComparisonModal
import com.example.ui.components.BillFormBottomSheet
import com.example.ui.components.BillSearchEmptyStateCard
import com.example.ui.components.BillSheetModal
import com.example.ui.components.CategoryFormDialog
import com.example.ui.components.CategorySmoothTrendChart
import com.example.ui.components.CompactCategoryBillSearchField
import com.example.ui.components.UtilityIconAvatar
import com.example.ui.components.VarianceBadge
import com.example.ui.components.visualSpec
import com.example.util.BillSheetExporter
import com.example.util.DateAndFormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
  category: BillCategory?,
  categoryBills: List<BillRecord>,
  currentUserId: String,
  onNavigateBack: () -> Unit,
  onScanForCategory: (String) -> Unit,
  onSaveBill: (BillRecord) -> Unit,
  onDeleteBill: (String) -> Unit,
  onUpdateCategory: (BillCategory) -> Unit,
  onDeleteCategory: (String) -> Unit,
) {
  val context = LocalContext.current
  var showAddOrEditBill by remember { mutableStateOf(false) }
  var editingBill by remember { mutableStateOf<BillRecord?>(null) }
  var activeSheetBill by remember { mutableStateOf<BillRecord?>(null) }
  var activeComparison by remember { mutableStateOf<BillComparisonResult?>(null) }
  var showEditCategoryDialog by remember { mutableStateOf(false) }
  var showDeleteCategoryConfirm by remember { mutableStateOf(false) }
  var chartShowConsumption by remember { mutableStateOf(false) }
  var categorySearchQuery by remember { mutableStateOf("") }

  if (category == null) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.app_name)) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
              )
            }
          },
        )
      }
    ) { pad ->
      Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.no_bills_yet))
      }
    }
    return
  }

  val comparisons =
    remember(categoryBills, category.alertThresholdPercent) {
      BillAnalyticsEngine.buildCategoryComparisons(categoryBills, category.alertThresholdPercent)
    }
  val filteredCategoryBills =
    remember(categoryBills, categorySearchQuery, category) {
      BillSearchEngine.filterBills(
        bills = categoryBills,
        query = categorySearchQuery,
        scope = BillSearchScope.ALL,
        categoriesById = mapOf(category.id to category),
      )
    }
  val trendPoints = remember(categoryBills) { BillAnalyticsEngine.buildCategoryTrend(categoryBills) }
  val utilityType = UtilityType.fromKey(category.utilityType)
  val visualSpec = utilityType.visualSpec()

  if (showAddOrEditBill) {
    BillFormBottomSheet(
      category = category,
      existingBill = editingBill,
      existingCategoryBills = categoryBills,
      currentUserId = currentUserId,
      onDismiss = {
        showAddOrEditBill = false
        editingBill = null
      },
      onSaveBill = { bill ->
        showAddOrEditBill = false
        editingBill = null
        onSaveBill(bill)
      },
    )
  }

  activeSheetBill?.let { sheetBill ->
    BillSheetModal(
      bill = sheetBill,
      comparison = comparisons[sheetBill.id],
      onEditBill = {
        editingBill = sheetBill
        showAddOrEditBill = true
      },
      onDismiss = { activeSheetBill = null },
    )
  }

  activeComparison?.let { comp ->
    BillComparisonModal(
      comparison = comp,
      onDismiss = { activeComparison = null },
    )
  }

  if (showEditCategoryDialog) {
    CategoryFormDialog(
      existing = category,
      onDismiss = { showEditCategoryDialog = false },
      onSave = { name, uType, unit, threshold ->
        showEditCategoryDialog = false
        onUpdateCategory(
          category.copy(
            name = name,
            utilityType = uType.firestoreKey,
            defaultUnit = unit,
            alertThresholdPercent = threshold,
          )
        )
      },
    )
  }

  if (showDeleteCategoryConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteCategoryConfirm = false },
      title = { Text(stringResource(R.string.delete_category_confirm_title)) },
      text = { Text(stringResource(R.string.delete_category_confirm_body)) },
      confirmButton = {
        Button(
          onClick = {
            showDeleteCategoryConfirm = false
            onDeleteCategory(category.id)
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteCategoryConfirm = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(category.name) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("category_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        actions = {
          if (categoryBills.isNotEmpty()) {
            IconButton(
              onClick = {
                BillSheetExporter.shareCategoryLedgerCsv(context, category, categoryBills)
              },
              modifier = Modifier.testTag("export_category_csv_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.export_category_sheet),
              )
            }
          }
          IconButton(onClick = { showEditCategoryDialog = true }) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = stringResource(R.string.dialog_edit_category_title),
            )
          }
          IconButton(onClick = { showDeleteCategoryConfirm = true }) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = stringResource(R.string.action_delete),
            )
          }
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      ) {
        // Category Summary & Actions Card
        item {
          val avgAmount =
            if (categoryBills.isNotEmpty()) categoryBills.map { it.totalAmount }.average() else 0.0
          val latest = categoryBills.firstOrNull()
          val currency = latest?.currency ?: "ر.س"

          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  UtilityIconAvatar(utilityType = utilityType, sizeDp = 48)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(
                      text = stringResource(utilityType.labelRes),
                      style = MaterialTheme.typography.labelLarge,
                      color = visualSpec.accentColor,
                    )
                    Text(
                      text =
                        stringResource(
                          R.string.category_threshold_badge,
                          category.alertThresholdPercent.toInt(),
                        ),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = stringResource(R.string.category_avg_monthly),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "${DateAndFormatUtils.formatMoney(avgAmount)} $currency",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Button(
                  onClick = {
                    editingBill = null
                    showAddOrEditBill = true
                  },
                  modifier =
                    Modifier.weight(1f)
                      .height(48.dp)
                      .testTag("add_monthly_bill_button"),
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.add_monthly_bill))
                }

                FilledTonalButton(
                  onClick = { onScanForCategory(category.id) },
                  modifier =
                    Modifier.weight(1f)
                      .height(48.dp)
                      .testTag("scan_for_category_button"),
                ) {
                  Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.scan_for_category))
                }
              }
            }
          }
        }

        // Category Trend Chart if >= 2 bills exist
        if (trendPoints.size >= 2) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = stringResource(R.string.analytics_trend_line_title),
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                      selected = !chartShowConsumption,
                      onClick = { chartShowConsumption = false },
                      label = { Text(stringResource(R.string.metric_amount)) },
                    )
                    FilterChip(
                      selected = chartShowConsumption,
                      onClick = { chartShowConsumption = true },
                      label = { Text(stringResource(R.string.metric_consumption)) },
                    )
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val unitOrCurr =
                  if (chartShowConsumption) category.defaultUnit
                  else (categoryBills.firstOrNull()?.currency ?: "ر.س")
                CategorySmoothTrendChart(
                  points = trendPoints,
                  showConsumption = chartShowConsumption,
                  unitOrCurrency = unitOrCurr,
                  accentColor = visualSpec.accentColor,
                )
              }
            }
          }
        }

        if (categoryBills.isEmpty()) {
          item {
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp),
              ) {
                Text(
                  text = stringResource(R.string.empty_category_bills_title),
                  style = MaterialTheme.typography.titleMedium,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = stringResource(R.string.empty_category_bills_subtitle),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        } else {
          item {
            CompactCategoryBillSearchField(
              query = categorySearchQuery,
              onQueryChange = { categorySearchQuery = it },
              onClear = { categorySearchQuery = "" },
            )
          }
          if (filteredCategoryBills.isEmpty()) {
            item {
              BillSearchEmptyStateCard(
                onClearSearch = { categorySearchQuery = "" },
              )
            }
          } else {
            items(filteredCategoryBills, key = { it.id }) { bill ->
              val comp = comparisons[bill.id]
              MonthlyBillCard(
                bill = bill,
                comparison = comp,
                onOpenSheet = { activeSheetBill = bill },
                onOpenComparison = { if (comp != null) activeComparison = comp },
                onDelete = { onDeleteBill(bill.id) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MonthlyBillCard(
  bill: BillRecord,
  comparison: BillComparisonResult?,
  onOpenSheet: () -> Unit,
  onOpenComparison: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().testTag("bill_card_${bill.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = DateAndFormatUtils.formatIsoDateArabic(bill.billDateIso),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            VarianceBadge(comparison = comparison)
          }
          if (bill.providerName.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = bill.providerName,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text =
              "${stringResource(R.string.metric_consumption)}: ${DateAndFormatUtils.formatNumber(bill.consumptionValue)} ${bill.consumptionUnit}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "${DateAndFormatUtils.formatMoney(bill.totalAmount)} ${bill.currency}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp).testTag("delete_bill_${bill.id}"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = stringResource(R.string.action_delete),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        FilledTonalButton(
          onClick = onOpenSheet,
          modifier = Modifier.weight(1f).height(48.dp).testTag("open_sheet_${bill.id}"),
        ) {
          Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.view_bill_sheet))
        }

        OutlinedButton(
          onClick = onOpenComparison,
          modifier = Modifier.weight(1f).height(48.dp).testTag("compare_bill_${bill.id}"),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.compare_previous_bill))
        }
      }
    }
  }
}
