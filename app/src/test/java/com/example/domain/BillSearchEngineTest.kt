package com.example.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.ui.components.BillHistorySearchBarCard
import com.example.ui.components.BillSearchEmptyStateCard
import com.example.ui.components.SearchedBillRecordCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillSearchEngineTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val categoriesById =
    mapOf(
      "cat_shop" to
        BillCategory(
          id = "cat_shop",
          userId = "u1",
          name = "مشتريات البيت الأسبوعية",
          utilityType = "shopping",
          defaultUnit = "صنف",
        ),
      "cat_elec" to
        BillCategory(
          id = "cat_elec",
          userId = "u1",
          name = "فاتورة الكهرباء",
          utilityType = "electricity",
          defaultUnit = "ك.و.س",
        ),
      "cat_water" to
        BillCategory(
          id = "cat_water",
          userId = "u1",
          name = "فاتورة الماء",
          utilityType = "water",
          defaultUnit = "م³",
        ),
    )

  private val sampleBills =
    listOf(
      BillRecord(
        id = "bill_tamimi",
        userId = "u1",
        categoryId = "cat_shop",
        categoryName = "فواتير التسوق",
        utilityType = "shopping",
        billDateIso = "2026-10-18",
        billingMonth = "2026-10",
        billDateEpochMillis = 1792281600000L,
        totalAmount = 345.50,
        providerName = "أسواق التميمي",
      ),
      BillRecord(
        id = "bill_elec",
        userId = "u1",
        categoryId = "cat_elec",
        categoryName = "فاتورة الكهرباء",
        utilityType = "electricity",
        billDateIso = "2026-09-05",
        billingMonth = "2026-09",
        billDateEpochMillis = 1788566400000L,
        totalAmount = 420.00,
        providerName = "الشركة السعودية للكهرباء",
      ),
      BillRecord(
        id = "bill_water",
        userId = "u1",
        categoryId = "cat_water",
        categoryName = "فاتورة الماء",
        utilityType = "water",
        billDateIso = "2026-08-12",
        billingMonth = "2026-08",
        billDateEpochMillis = 1786492800000L,
        totalAmount = 95.00,
        providerName = "",
        sheetItems =
          listOf(
            BillSheetRow(label = "اسم المتجر / الجهة", value = "شركة المياه الوطنية", unit = ""),
          ),
      ),
    )

  @Test
  fun `filterBills matches store name with Arabic letter normalization and sheet fallback`() {
    // Alef hamza & ya/alef-maqsura normalization
    val tamimiResults =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "اسواق التميمى",
        scope = BillSearchScope.STORE_NAME,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_tamimi"), tamimiResults.map { it.id })

    // Sheet row fallback when providerName is empty
    val waterResults =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "شركه المياه",
        scope = BillSearchScope.STORE_NAME,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_water"), waterResults.map { it.id })
  }

  @Test
  fun `filterBills matches ISO date, slash DMY date, Eastern Arabic digits, and Arabic month name`() {
    val isoMatch =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "2026-10-18",
        scope = BillSearchScope.DATE,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_tamimi"), isoMatch.map { it.id })

    val arabicDigitsSlashMatch =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "١٨/١٠/٢٠٢٦",
        scope = BillSearchScope.DATE,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_tamimi"), arabicDigitsSlashMatch.map { it.id })

    val arabicMonthMatch =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "سبتمبر",
        scope = BillSearchScope.DATE,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_elec"), arabicMonthMatch.map { it.id })
  }

  @Test
  fun `filterBills matches expense category by utility synonym, key, and linked category name`() {
    val shoppingSynonym =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "مقاضي",
        scope = BillSearchScope.CATEGORY,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_tamimi"), shoppingSynonym.map { it.id })

    val linkedCategoryRename =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "مشتريات البيت",
        scope = BillSearchScope.CATEGORY,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_tamimi"), linkedCategoryRename.map { it.id })

    val electricityArabic =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "كهرباء",
        scope = BillSearchScope.CATEGORY,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_elec"), electricityArabic.map { it.id })
  }

  @Test
  fun `filterBills enforces scope isolation and supports cross-field queries in ALL scope`() {
    // Searching date inside STORE_NAME scope should return empty
    val dateInStoreScope =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "2026-10-18",
        scope = BillSearchScope.STORE_NAME,
        categoriesById = categoriesById,
      )
    assertTrue(dateInStoreScope.isEmpty())

    // Cross-field query in ALL scope ("التميمي اكتوبر") matches bill_tamimi
    val crossFieldAll =
      BillSearchEngine.filterBills(
        bills = sampleBills,
        query = "التميمي اكتوبر",
        scope = BillSearchScope.ALL,
        categoriesById = categoriesById,
      )
    assertEquals(listOf("bill_tamimi"), crossFieldAll.map { it.id })
  }

  @Test
  fun `BillHistorySearchBarCard filters results interactively and clears search`() {
    var query by mutableStateOf("")
    var scope by mutableStateOf(BillSearchScope.ALL)

    composeTestRule.setContent {
      MyApplicationTheme {
        val filtered =
          BillSearchEngine.filterBills(
            bills = sampleBills,
            query = query,
            scope = scope,
            categoriesById = categoriesById,
          )
        androidx.compose.foundation.layout.Column {
          BillHistorySearchBarCard(
            searchQuery = query,
            searchScope = scope,
            onSearchQueryChange = { query = it },
            onSearchScopeChange = { scope = it },
            onClearSearch = {
              query = ""
              scope = BillSearchScope.ALL
            },
          )
          if (query.isNotBlank()) {
            if (filtered.isEmpty()) {
              BillSearchEmptyStateCard(
                onClearSearch = {
                  query = ""
                  scope = BillSearchScope.ALL
                }
              )
            } else {
              filtered.forEach { bill ->
                SearchedBillRecordCard(
                  bill = bill,
                  category = categoriesById[bill.categoryId],
                  comparison = null,
                  onOpenSheet = {},
                  onOpenCategory = {},
                )
              }
            }
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("bill_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("bill_search_input").performTextReplacement("التميمي")
    composeTestRule.onNodeWithTag("search_result_bill_bill_tamimi").assertIsDisplayed()

    // Non-matching query displays empty state
    composeTestRule.onNodeWithTag("bill_search_input").performTextReplacement("متجر غير موجود")
    composeTestRule.onNodeWithTag("bill_search_empty_state").assertIsDisplayed()

    // Clear button resets query
    composeTestRule.onNodeWithTag("bill_search_clear_button").performClick()
    assertEquals("", query)
  }
}
