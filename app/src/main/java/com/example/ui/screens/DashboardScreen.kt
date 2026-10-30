package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UiState
import com.example.data.model.UtilityType
import com.example.domain.BillAnalyticsEngine
import com.example.domain.BillComparisonResult
import com.example.domain.BillSearchScope
import com.example.domain.BudgetDashboardState
import com.example.domain.MonthlyAnalyticsSummary
import com.example.domain.MonthlyBudgetDashboardEngine
import com.example.ui.components.BillComparisonModal
import com.example.ui.components.BillFormBottomSheet
import com.example.ui.components.BillSearchEmptyStateCard
import com.example.ui.components.BillSearchResultsHeader
import com.example.ui.components.BillSheetModal
import com.example.ui.components.HomeBudgetOverviewCard
import com.example.ui.components.HouseholdBudgetDialog
import com.example.ui.components.SearchedBillRecordCard
import com.example.ui.components.UtilityIconAvatar
import com.example.ui.components.VarianceAlertBannerCard
import com.example.ui.components.VarianceBadge
import com.example.ui.theme.LocalUtilityPalette
import com.example.util.DateAndFormatUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
  categoriesState: UiState<List<BillCategory>>,
  allBillsState: UiState<List<BillRecord>>,
  filteredBillsState: UiState<List<BillRecord>>,
  searchQuery: String,
  searchScope: BillSearchScope,
  analyticsSummary: MonthlyAnalyticsSummary,
  currentUserId: String,
  onSearchQueryChange: (String) -> Unit,
  onSearchScopeChange: (BillSearchScope) -> Unit,
  onClearSearch: () -> Unit,
  onSaveBill: (BillRecord, Double, List<BillRecord>) -> Unit,
  onOpenCategory: (String) -> Unit,
  onOpenScanner: () -> Unit,
  onOpenAssistant: () -> Unit,
  onCreateCategory: (String, UtilityType, String, Double) -> Unit,
  onSignOut: () -> Unit,
  budgetDashboardState: BudgetDashboardState? = null,
  onOpenAnalyticsDashboard: () -> Unit = {},
  onSaveHouseholdBudget: (Double, String, Double) -> Unit = { _, _, _ -> },
) {
  var showBudgetDialog by remember { mutableStateOf(false) }
  var activeComparison by remember { mutableStateOf<BillComparisonResult?>(null) }
  var activeSheetBill by remember { mutableStateOf<BillRecord?>(null) }
  var editingSearchBill by remember { mutableStateOf<BillRecord?>(null) }
  val utilityPalette = LocalUtilityPalette.current
  val context = LocalContext.current
  val allCategories = (categoriesState as? UiState.Success)?.data.orEmpty()
  val categoriesById = remember(allCategories) { allCategories.associateBy { it.id } }
  val allBills = (allBillsState as? UiState.Success)?.data.orEmpty()

  // Search is an icon next to the categories title; tapping it swaps the title for a search bar.
  var searchActive by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
  val searchFocusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val closeSearch: () -> Unit = {
    focusManager.clearFocus()
    onClearSearch()
    searchActive = false
  }
  BackHandler(enabled = searchActive) { closeSearch() }
  LaunchedEffect(searchActive) {
    if (searchActive) runCatching { searchFocusRequester.requestFocus() }
  }

  val emptyImageRequest =
    remember(context) {
      ImageRequest.Builder(context)
        .data(R.drawable.img_empty_bills)
        .crossfade(true)
        .build()
    }

  val effectiveBudgetState =
    budgetDashboardState
      ?: remember(allBills, allCategories) {
        MonthlyBudgetDashboardEngine.compute(allBills, allCategories, null)
      }

  if (showBudgetDialog) {
    HouseholdBudgetDialog(
      initialLimit = effectiveBudgetState.monthlyBudgetLimit,
      initialWarningPercent = effectiveBudgetState.warningPercent,
      currency = effectiveBudgetState.primaryCurrency,
      onDismiss = { showBudgetDialog = false },
      onSave = { newLimit, newWarn ->
        showBudgetDialog = false
        onSaveHouseholdBudget(newLimit, effectiveBudgetState.primaryCurrency, newWarn)
      },
    )
  }

  activeSheetBill?.let { sheetBill ->
    val cat = categoriesById[sheetBill.categoryId]
    val catBills =
      remember(allBills, sheetBill.categoryId) {
        allBills.filter { it.categoryId == sheetBill.categoryId }
      }
    val sheetComparison =
      remember(catBills, cat?.alertThresholdPercent, sheetBill.id) {
        BillAnalyticsEngine.buildCategoryComparisons(
          catBills,
          cat?.alertThresholdPercent ?: 25.0,
        )[sheetBill.id]
      }
    BillSheetModal(
      bill = sheetBill,
      comparison = sheetComparison,
      onEditBill = {
        editingSearchBill = sheetBill
      },
      onDismiss = { activeSheetBill = null },
    )
  }

  editingSearchBill?.let { billToEdit ->
    val resolvedCat =
      categoriesById[billToEdit.categoryId]
        ?: BillCategory(
          id = billToEdit.categoryId,
          userId = currentUserId,
          name = billToEdit.categoryName,
          utilityType = billToEdit.utilityType,
          defaultUnit = billToEdit.consumptionUnit,
          alertThresholdPercent = 25.0,
        )
    val catBills =
      remember(allBills, resolvedCat.id) {
        allBills.filter { it.categoryId == resolvedCat.id }
      }
    BillFormBottomSheet(
      category = resolvedCat,
      existingBill = billToEdit,
      existingCategoryBills = catBills,
      currentUserId = currentUserId,
      onDismiss = { editingSearchBill = null },
      onSaveBill = { updatedBill ->
        editingSearchBill = null
        onSaveBill(updatedBill, resolvedCat.alertThresholdPercent, catBills)
      },
    )
  }

  activeComparison?.let { comp ->
    BillComparisonModal(
      comparison = comp,
      onDismiss = { activeComparison = null },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
          )
        },
        actions = {
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Logout,
              contentDescription = stringResource(R.string.sign_out),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      ) {
        // Hero Banner Card (solid background, no decorative graphics)
        item {
          Card(
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth().background(utilityPalette.heroGradientStart)
            ) {
              Column(modifier = Modifier.padding(20.dp)) {
                Text(
                  text = stringResource(R.string.dashboard_greeting),
                  style = MaterialTheme.typography.labelLarge,
                  color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  verticalAlignment = Alignment.Bottom,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column {
                    Text(
                      text = stringResource(R.string.dashboard_total_current_month),
                      style = MaterialTheme.typography.bodySmall,
                      color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                      text =
                        "${DateAndFormatUtils.formatMoney(analyticsSummary.currentMonthTotal)} ${analyticsSummary.primaryCurrency}",
                      style = MaterialTheme.typography.displaySmall,
                      color = androidx.compose.ui.graphics.Color.White,
                    )
                  }
                  val catCount = (categoriesState as? UiState.Success)?.data?.size ?: 0
                  Surface(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
                    shape = MaterialTheme.shapes.medium,
                  ) {
                    Text(
                      text = "$catCount ${stringResource(R.string.dashboard_categories_count)}",
                      style = MaterialTheme.typography.labelLarge,
                      color = androidx.compose.ui.graphics.Color.White,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                  }
                }
              }
            }
          }
        }

        // Household Budget & Monthly Category Expenses Overview Card (hidden while searching)
        if (!searchActive) {
          item {
            HomeBudgetOverviewCard(
              dashboardState = effectiveBudgetState,
              onOpenChartsDashboard = onOpenAnalyticsDashboard,
              onEditBudgetClick = { showBudgetDialog = true },
            )
          }
        }

        // Active High-Variance Alerts Section (hidden while searching)
        if (!searchActive && analyticsSummary.activeAlerts.isNotEmpty()) {
          item {
            Text(
              text = stringResource(R.string.dashboard_alerts_title),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
          items(analyticsSummary.activeAlerts, key = { "alert_${it.bill.id}" }) { alertItem ->
            VarianceAlertBannerCard(
              alert = alertItem,
              onOpenComparison = { activeComparison = alertItem.comparison },
            )
          }
        }

        // Categories header: title + search icon, or the search bar that replaces the title.
        item(key = "categories_header") {
          if (searchActive) {
            DashboardSearchField(
              query = searchQuery,
              onQueryChange = onSearchQueryChange,
              onClose = closeSearch,
              onSearchAction = { focusManager.clearFocus() },
              focusRequester = searchFocusRequester,
            )
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = stringResource(R.string.dashboard_categories_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = {
                  // The simplified search always looks across store, date and category.
                  onSearchScopeChange(BillSearchScope.ALL)
                  searchActive = true
                },
                modifier = Modifier.testTag("dashboard_search_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = stringResource(R.string.dashboard_header_search_open_desc),
                )
              }
            }
          }
        }

        // Live Firestore Search Results Section when query is active
        if (searchQuery.isNotBlank()) {
          when (filteredBillsState) {
            is UiState.Loading -> {
              item {
                Box(
                  modifier = Modifier.fillMaxWidth().padding(28.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator()
                }
              }
            }
            is UiState.Error -> {
              item {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = MaterialTheme.shapes.medium,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = filteredBillsState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                  )
                }
              }
            }
            is UiState.Success -> {
              val matchedBills = filteredBillsState.data
              item {
                BillSearchResultsHeader(
                  resultsCount = matchedBills.size,
                  onClearSearch = closeSearch,
                )
              }
              if (matchedBills.isEmpty()) {
                item {
                  BillSearchEmptyStateCard(onClearSearch = closeSearch)
                }
              } else {
                items(matchedBills, key = { "search_bill_${it.id}" }) { bill ->
                  val billCategory = categoriesById[bill.categoryId]
                  val catBills =
                    remember(allBills, bill.categoryId) {
                      allBills.filter { it.categoryId == bill.categoryId }
                    }
                  val billComp =
                    remember(catBills, billCategory?.alertThresholdPercent, bill.id) {
                      BillAnalyticsEngine.buildCategoryComparisons(
                        catBills,
                        billCategory?.alertThresholdPercent ?: 25.0,
                      )[bill.id]
                    }
                  SearchedBillRecordCard(
                    bill = bill,
                    category = billCategory,
                    comparison = billComp,
                    onOpenSheet = { activeSheetBill = bill },
                    onOpenCategory = {
                      if (bill.categoryId.isNotBlank()) {
                        onOpenCategory(bill.categoryId)
                      }
                    },
                  )
                }
              }
            }
          }
        }

        when (categoriesState) {
          is UiState.Loading -> {
            item {
              Box(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }
          }
          is UiState.Error -> {
            item {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = categoriesState.message,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          }
          is UiState.Success -> {
            val categories = categoriesState.data
            if (categories.isEmpty()) {
              item {
                Card(
                  colors =
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  shape = MaterialTheme.shapes.extraLarge,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                  ) {
                    AsyncImage(
                      model = emptyImageRequest,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier =
                        Modifier.size(width = 220.dp, height = 155.dp)
                          .clip(MaterialTheme.shapes.large),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                      text = stringResource(R.string.empty_categories_title),
                      style = MaterialTheme.typography.titleLarge,
                      textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = stringResource(R.string.empty_categories_subtitle),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                      text = stringResource(R.string.quick_setup_label),
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      listOf(
                          UtilityType.GAS,
                          UtilityType.ELECTRICITY,
                          UtilityType.WATER,
                          UtilityType.INTERNET,
                          UtilityType.SHOPPING,
                        )
                        .forEach { preset ->
                          AssistChip(
                            onClick = {
                              onCreateCategory(
                                preset.defaultCategoryName,
                                preset,
                                preset.defaultUnit,
                                25.0,
                              )
                            },
                            leadingIcon = {
                              Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                              )
                            },
                            label = { Text(preset.defaultCategoryName) },
                            modifier = Modifier.testTag("quick_add_${preset.firestoreKey}"),
                          )
                        }
                    }
                  }
                }
              }
            } else {
              items(categories, key = { it.id }) { category ->
                val catBills =
                  remember(allBills, category.id) {
                    allBills.filter { it.categoryId == category.id }
                  }
                val comparisons =
                  remember(catBills, category.alertThresholdPercent) {
                    BillAnalyticsEngine.buildCategoryComparisons(
                      catBills,
                      category.alertThresholdPercent,
                    )
                  }
                val latestBill = catBills.maxByOrNull { it.billDateEpochMillis }
                val latestComp = latestBill?.let { comparisons[it.id] }

                CategoryCardItem(
                  category = category,
                  billsCount = catBills.size,
                  latestBill = latestBill,
                  latestComparison = latestComp,
                  onClick = { onOpenCategory(category.id) },
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Simple search bar that replaces the categories title while searching: a search icon, the
 * query field with the hint "البحث..." and a close button that ends the search.
 */
@Composable
private fun DashboardSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClose: () -> Unit,
  onSearchAction: () -> Unit,
  focusRequester: FocusRequester,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(28.dp),
    modifier = Modifier.fillMaxWidth().height(52.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 16.dp, end = 4.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(10.dp))
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle =
          MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
        decorationBox = { innerTextField ->
          Box(contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
              Text(
                text = stringResource(R.string.dashboard_header_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            innerTextField()
          }
        },
        modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("dashboard_search_field"),
      )
      IconButton(
        onClick = onClose,
        modifier = Modifier.testTag("dashboard_search_close_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(R.string.dashboard_header_search_close_desc),
        )
      }
    }
  }
}

@Composable
private fun CategoryCardItem(
  category: BillCategory,
  billsCount: Int,
  latestBill: BillRecord?,
  latestComparison: BillComparisonResult?,
  onClick: () -> Unit,
) {
  val utilityType = UtilityType.fromKey(category.utilityType)
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .testTag("category_card_${category.id}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
      UtilityIconAvatar(utilityType = utilityType, sizeDp = 50)
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = category.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          VarianceBadge(comparison = latestComparison)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text =
            if (latestBill != null) {
              stringResource(
                R.string.latest_bill_format,
                DateAndFormatUtils.formatMoney(latestBill.totalAmount),
                latestBill.currency,
                DateAndFormatUtils.formatIsoDateArabic(latestBill.billDateIso),
              )
            } else {
              stringResource(R.string.no_bills_yet)
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(R.string.bills_count_format, billsCount),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
