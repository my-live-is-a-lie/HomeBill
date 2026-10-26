package com.example.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.HouseholdBudget
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MonthlyBudgetDashboardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleCategories =
    listOf(
      BillCategory(
        id = "cat_elec",
        userId = "u1",
        name = "فاتورة الكهرباء",
        utilityType = "electricity",
        defaultUnit = "ك.و.س",
      ),
      BillCategory(
        id = "cat_water",
        userId = "u1",
        name = "فاتورة الماء",
        utilityType = "water",
        defaultUnit = "م³",
      ),
      BillCategory(
        id = "cat_shop",
        userId = "u1",
        name = "فواتير التسوق",
        utilityType = "shopping",
        defaultUnit = "صنف",
      ),
    )

  private val sampleBills =
    listOf(
      BillRecord(
        id = "b_sep_elec",
        userId = "u1",
        categoryId = "cat_elec",
        categoryName = "فاتورة الكهرباء",
        utilityType = "electricity",
        billDateIso = "2026-09-10",
        billingMonth = "2026-09",
        billDateEpochMillis = 1788998400000L,
        totalAmount = 400.0,
        currency = "ر.س",
      ),
      BillRecord(
        id = "b_sep_shop",
        userId = "u1",
        categoryId = "cat_shop",
        categoryName = "فواتير التسوق",
        utilityType = "shopping",
        billDateIso = "2026-09-20",
        billingMonth = "2026-09",
        billDateEpochMillis = 1789862400000L,
        totalAmount = 600.0,
        currency = "ر.س",
      ),
      BillRecord(
        id = "b_oct_elec",
        userId = "u1",
        categoryId = "cat_elec",
        categoryName = "فاتورة الكهرباء",
        utilityType = "electricity",
        billDateIso = "2026-10-10",
        billingMonth = "2026-10",
        billDateEpochMillis = 1791590400000L,
        totalAmount = 500.0,
        currency = "ر.س",
      ),
      BillRecord(
        id = "b_oct_water",
        userId = "u1",
        categoryId = "cat_water",
        categoryName = "فاتورة الماء",
        utilityType = "water",
        billDateIso = "2026-10-14",
        billingMonth = "2026-10",
        billDateEpochMillis = 1791936000000L,
        totalAmount = 150.0,
        currency = "ر.س",
      ),
      BillRecord(
        id = "b_oct_shop_1",
        userId = "u1",
        categoryId = "cat_shop",
        categoryName = "فواتير التسوق",
        utilityType = "shopping",
        billDateIso = "2026-10-18",
        billingMonth = "2026-10",
        billDateEpochMillis = 1792281600000L,
        totalAmount = 450.0,
        currency = "ر.س",
      ),
      BillRecord(
        id = "b_oct_shop_2",
        userId = "u1",
        categoryId = "cat_shop",
        categoryName = "فواتير التسوق",
        utilityType = "shopping",
        billDateIso = "2026-10-25",
        billingMonth = "2026-10",
        billDateEpochMillis = 1792886400000L,
        totalAmount = 400.0,
        currency = "ر.س",
      ),
    )

  @Test
  fun `compute aggregates monthly category totals and evaluates household budget status`() {
    // October total = 500 (elec) + 150 (water) + 850 (shopping) = 1,500 SAR
    // September total = 400 + 600 = 1,000 SAR -> MoM = +50.0%
    val withinState =
      MonthlyBudgetDashboardEngine.compute(
        allBills = sampleBills,
        categories = sampleCategories,
        householdBudget =
          HouseholdBudget(userId = "u1", monthlyLimit = 2500.0, warningPercent = 80.0),
        requestedMonth = "2026-10",
      )

    assertEquals("2026-10", withinState.selectedMonth)
    assertEquals(1500.0, withinState.selectedMonthTotal, 0.01)
    assertEquals(4, withinState.selectedMonthBillsCount)
    assertEquals(50.0, withinState.monthOverMonthPercent ?: 0.0, 0.01)
    assertEquals(BudgetHealthStatus.WITHIN_BUDGET, withinState.budgetStatus)
    assertEquals(1000.0, withinState.remainingOrExceededAmount ?: 0.0, 0.01)
    assertEquals(3, withinState.categoryExpenses.size)
    // Top category in October is Shopping (850 SAR, 2 bills)
    assertEquals("cat_shop", withinState.categoryExpenses[0].categoryKey)
    assertEquals(850.0, withinState.categoryExpenses[0].totalAmount, 0.01)
    assertEquals(2, withinState.categoryExpenses[0].billsCount)

    // Approaching limit when limit = 1700 (1500 / 1700 = 88.2% >= 80%)
    val approachingState =
      MonthlyBudgetDashboardEngine.compute(
        allBills = sampleBills,
        categories = sampleCategories,
        householdBudget =
          HouseholdBudget(userId = "u1", monthlyLimit = 1700.0, warningPercent = 80.0),
        requestedMonth = "2026-10",
      )
    assertEquals(BudgetHealthStatus.APPROACHING_LIMIT, approachingState.budgetStatus)

    // Over budget when limit = 1200 (1500 > 1200 -> -300 remaining)
    val overState =
      MonthlyBudgetDashboardEngine.compute(
        allBills = sampleBills,
        categories = sampleCategories,
        householdBudget =
          HouseholdBudget(userId = "u1", monthlyLimit = 1200.0, warningPercent = 80.0),
        requestedMonth = "2026-10",
      )
    assertEquals(BudgetHealthStatus.OVER_BUDGET, overState.budgetStatus)
    assertEquals(-300.0, overState.remainingOrExceededAmount ?: 0.0, 0.01)
  }

  @Test
  fun `AnalyticsScreen renders interactive monthly category charts and saves household budget`() {
    var selectedMonth by mutableStateOf("2026-10")
    var savedBudget by mutableStateOf<HouseholdBudget?>(null)
    val baseSummary = BillAnalyticsEngine.computeMonthlyAnalytics(sampleBills, sampleCategories)

    composeTestRule.setContent {
      MyApplicationTheme {
        val dashState =
          MonthlyBudgetDashboardEngine.compute(
            allBills = sampleBills,
            categories = sampleCategories,
            householdBudget = savedBudget,
            requestedMonth = selectedMonth,
          )
        AnalyticsScreen(
          categories = sampleCategories,
          allBills = sampleBills,
          summary = baseSummary,
          budgetDashboardState = dashState,
          onSelectMonth = { ym -> selectedMonth = ym },
          onSaveHouseholdBudget = { limit, curr, warn ->
            savedBudget =
              HouseholdBudget(
                userId = "u1",
                monthlyLimit = limit,
                currency = curr,
                warningPercent = warn,
              )
          },
        )
      }
    }

    composeTestRule.waitForIdle()

    // Verify Hero Card shows October 1,500 total
    composeTestRule.onNodeWithTag("household_budget_hero_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("selected_month_total_text").assertTextContains("1,500", substring = true)

    // Switch to September via month chip and verify total updates to 1,000
    composeTestRule.onNodeWithTag("month_chip_2026-09").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("selected_month_total_text").assertTextContains("1,000", substring = true)

    // Switch back to October and open Household Budget Dialog to set 3,500 SAR limit
    composeTestRule.onNodeWithTag("month_chip_2026-10").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("edit_household_budget_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("budget_preset_3500").performClick()
    composeTestRule.onNodeWithTag("save_budget_confirm_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(3500.0, savedBudget?.monthlyLimit ?: 0.0, 0.01)
    composeTestRule.onNodeWithTag("budget_status_message_text").assertTextContains("2,000", substring = true)

    // Scroll to Monthly Category Bar Chart and test category legend filter
    composeTestRule
      .onNodeWithTag("budget_dashboard_lazy_list")
      .performScrollToNode(hasTestTag("monthly_category_chart_card"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("recharts_monthly_bar_canvas").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("legend_filter_cat_elec").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("monthly_bar_tooltip_card")
      .performScrollTo()
      .assertTextContains("500", substring = true)

    // Scroll LazyColumn to breakdown card and toggle Donut mode
    composeTestRule
      .onNodeWithTag("budget_dashboard_lazy_list")
      .performScrollToNode(hasTestTag("selected_month_breakdown_card"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("category_expense_bar_cat_shop").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("chart_mode_donut_chip").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("recharts_category_donut_canvas").performScrollTo().assertIsDisplayed()
  }
}
