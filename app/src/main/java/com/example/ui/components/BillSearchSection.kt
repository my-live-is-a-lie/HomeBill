package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.UtilityType
import com.example.domain.BillComparisonResult
import com.example.domain.BillSearchScope
import com.example.util.DateAndFormatUtils

@Composable
fun BillHistorySearchBarCard(
  searchQuery: String,
  searchScope: BillSearchScope,
  onSearchQueryChange: (String) -> Unit,
  onSearchScopeChange: (BillSearchScope) -> Unit,
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val placeholderRes =
    when (searchScope) {
      BillSearchScope.ALL -> R.string.bill_search_placeholder
      BillSearchScope.STORE_NAME -> R.string.bill_search_placeholder_store
      BillSearchScope.DATE -> R.string.bill_search_placeholder_date
      BillSearchScope.CATEGORY -> R.string.bill_search_placeholder_category
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier.fillMaxWidth().testTag("bill_search_card"),
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        placeholder = {
          Text(
            text = stringResource(placeholderRes),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.bill_search_section_title),
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = {
                onClearSearch()
                focusManager.clearFocus()
              },
              modifier = Modifier.size(48.dp).testTag("bill_search_clear_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = stringResource(R.string.bill_search_clear_desc),
              )
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
          ),
        modifier = Modifier.fillMaxWidth().testTag("bill_search_input"),
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      ) {
        SearchScopeChip(
          scope = BillSearchScope.ALL,
          selected = searchScope == BillSearchScope.ALL,
          icon = Icons.Default.Search,
          tag = "bill_search_scope_all",
          onClick = { onSearchScopeChange(BillSearchScope.ALL) },
        )
        SearchScopeChip(
          scope = BillSearchScope.STORE_NAME,
          selected = searchScope == BillSearchScope.STORE_NAME,
          icon = Icons.Default.Storefront,
          tag = "bill_search_scope_store",
          onClick = { onSearchScopeChange(BillSearchScope.STORE_NAME) },
        )
        SearchScopeChip(
          scope = BillSearchScope.DATE,
          selected = searchScope == BillSearchScope.DATE,
          icon = Icons.Default.CalendarMonth,
          tag = "bill_search_scope_date",
          onClick = { onSearchScopeChange(BillSearchScope.DATE) },
        )
        SearchScopeChip(
          scope = BillSearchScope.CATEGORY,
          selected = searchScope == BillSearchScope.CATEGORY,
          icon = Icons.Default.Category,
          tag = "bill_search_scope_category",
          onClick = { onSearchScopeChange(BillSearchScope.CATEGORY) },
        )
      }
    }
  }
}

@Composable
private fun SearchScopeChip(
  scope: BillSearchScope,
  selected: Boolean,
  icon: ImageVector,
  tag: String,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    leadingIcon = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
    },
    label = { Text(stringResource(scope.labelRes)) },
    modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
  )
}

@Composable
fun BillSearchResultsHeader(
  resultsCount: Int,
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier.fillMaxWidth(),
  ) {
    Text(
      text = stringResource(R.string.bill_search_results_count, resultsCount),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    TextButton(
      onClick = onClearSearch,
      modifier = Modifier.heightIn(min = 48.dp).testTag("bill_search_reset_button"),
    ) {
      Text(stringResource(R.string.bill_search_reset_action))
    }
  }
}

@Composable
fun SearchedBillRecordCard(
  bill: BillRecord,
  category: BillCategory?,
  comparison: BillComparisonResult?,
  onOpenSheet: () -> Unit,
  onOpenCategory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val utilityType = UtilityType.fromKey(category?.utilityType ?: bill.utilityType)
  val visualSpec = utilityType.visualSpec()
  val resolvedCategoryName =
    category?.name?.takeIf { it.isNotBlank() }
      ?: bill.categoryName.ifBlank { stringResource(utilityType.labelRes) }
  val headlineTitle = bill.providerName.ifBlank { resolvedCategoryName }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    shape = MaterialTheme.shapes.large,
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onOpenSheet)
        .testTag("search_result_bill_${bill.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          UtilityIconAvatar(utilityType = utilityType, sizeDp = 46)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = headlineTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              VarianceBadge(comparison = comparison)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Surface(
                color = visualSpec.containerColor,
                shape = CircleShape,
              ) {
                Text(
                  text = resolvedCategoryName,
                  style = MaterialTheme.typography.labelSmall,
                  color = visualSpec.accentColor,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
              Text(
                text =
                  "${DateAndFormatUtils.formatIsoDateArabic(bill.billDateIso)} (${bill.billDateIso})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
          text = "${DateAndFormatUtils.formatMoney(bill.totalAmount)} ${bill.currency}",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        FilledTonalButton(
          onClick = onOpenSheet,
          modifier =
            Modifier.weight(1f)
              .height(48.dp)
              .testTag("search_open_sheet_${bill.id}"),
        ) {
          Icon(
            imageVector = Icons.Default.TableChart,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.view_bill_sheet))
        }

        if (bill.categoryId.isNotBlank()) {
          OutlinedButton(
            onClick = onOpenCategory,
            modifier =
              Modifier.weight(1f)
                .height(48.dp)
                .testTag("search_open_category_${bill.id}"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.bill_search_open_category))
          }
        }
      }
    }
  }
}

@Composable
fun BillSearchEmptyStateCard(
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier.fillMaxWidth().testTag("bill_search_empty_state"),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().padding(28.dp),
    ) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.SearchOff,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(28.dp),
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = stringResource(R.string.bill_search_empty_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.bill_search_empty_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(16.dp))
      FilledTonalButton(
        onClick = onClearSearch,
        modifier = Modifier.height(48.dp).testTag("bill_search_empty_clear_button"),
      ) {
        Text(stringResource(R.string.bill_search_reset_action))
      }
    }
  }
}

@Composable
fun CompactCategoryBillSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    shape = MaterialTheme.shapes.large,
    placeholder = {
      Text(
        text = stringResource(R.string.category_bills_search_placeholder),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = stringResource(R.string.bill_search_section_title),
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(
          onClick = {
            onClear()
            focusManager.clearFocus()
          },
          modifier = Modifier.size(48.dp).testTag("category_bill_search_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = stringResource(R.string.bill_search_clear_desc),
          )
        }
      }
    },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
      ),
    modifier = modifier.fillMaxWidth().testTag("category_bill_search_input"),
  )
}
