package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.domain.AlertSeverity
import com.example.domain.BillAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Fawatiri`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("فواتيري", appName)
  }

  @Test
  fun `BillAnalyticsEngine detects high financial and consumption spike vs previous bill`() {
    val sepBill =
      BillRecord(
        id = "b1",
        categoryId = "cat_gas",
        categoryName = "فاتورة الغاز",
        utilityType = "gas",
        billDateIso = "2026-09-15",
        billingMonth = "2026-09",
        billDateEpochMillis = 1789430400000L,
        totalAmount = 100.0,
        consumptionValue = 25.0,
        sheetItems = listOf(BillSheetRow("استهلاك الغاز", "25", "م³")),
      )

    val octBill =
      BillRecord(
        id = "b2",
        categoryId = "cat_gas",
        categoryName = "فاتورة الغاز",
        utilityType = "gas",
        billDateIso = "2026-10-15",
        billingMonth = "2026-10",
        billDateEpochMillis = 1792022400000L,
        totalAmount = 148.0,
        consumptionValue = 36.0,
        sheetItems = listOf(BillSheetRow("استهلاك الغاز", "36", "م³")),
      )

    val comp = BillAnalyticsEngine.compareWithPrevious(octBill, sepBill, thresholdPercent = 25.0)
    assertTrue(comp.hasSpike)
    assertTrue(comp.isFinancialSpike)
    assertTrue(comp.isConsumptionSpike)
    assertEquals(AlertSeverity.HIGH_SPIKE, comp.severity)
    assertEquals(48.0, comp.amountChangePercent ?: 0.0, 0.01)

    val summary =
      BillAnalyticsEngine.computeMonthlyAnalytics(
        allBills = listOf(sepBill, octBill),
        categories =
          listOf(
            BillCategory(
              id = "cat_gas",
              name = "فاتورة الغاز",
              utilityType = "gas",
              alertThresholdPercent = 25.0,
            )
          ),
      )
    assertEquals(2, summary.monthlyPoints.size)
    assertEquals(1, summary.activeAlerts.size)
  }
}
