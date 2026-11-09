package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BillDatabase
import com.example.data.local.LOCAL_USER_ID
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.UtilityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillRepositoryTest {

  private lateinit var db: BillDatabase
  private lateinit var repo: BillRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, BillDatabase::class.java).allowMainThreadQueries().build()
    repo = BillRepository(db.billDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun sampleBill(categoryId: String, total: Double = 165.0) =
    BillRecord(
      categoryId = categoryId,
      categoryName = "فاتورة الغاز",
      utilityType = "gas",
      billDateIso = "2026-10-20",
      billingMonth = "2026-10",
      billDateEpochMillis = 1792454400000L,
      totalAmount = total,
      sheetItems = listOf(BillSheetRow("استهلاك", "42.5", "م³"), BillSheetRow("رسوم", "10", "")),
    )

  @Test
  fun createCategoryAndBill_areObservedWithOwnerAndTimestamps() = runBlocking {
    val cat = repo.createCategory("فاتورة الغاز", UtilityType.GAS, "م³").getOrThrow()
    assertEquals(LOCAL_USER_ID, cat.userId)
    assertNotNull(cat.createdAt)

    val billId = repo.createBill(sampleBill(cat.id)).getOrThrow()
    val bills = repo.observeAllBills(LOCAL_USER_ID).first()
    assertEquals(1, bills.size)
    val saved = bills.single()
    assertEquals(billId, saved.id)
    assertEquals(LOCAL_USER_ID, saved.userId)
    assertNotNull(saved.createdAt)
    assertEquals(listOf("استهلاك", "رسوم"), saved.sheetItems.map { it.label })
    assertEquals("م³", saved.sheetItems.first().unit)
    assertEquals(listOf(cat), repo.observeCategories(LOCAL_USER_ID).first())
  }

  @Test
  fun updateBill_keepsCreatedAtAndDurationTimestamps() = runBlocking {
    val cat = repo.createCategory("غاز", UtilityType.GAS, "م³", trackDuration = true).getOrThrow()
    val id = repo.createBill(sampleBill(cat.id)).getOrThrow()
    val stored = repo.getBillById(id).getOrThrow()!!

    val started = 1_700_000_000_000L
    repo.updateBill(stored.copy(totalAmount = 300.0, startedAtEpochMillis = started)).getOrThrow()

    val updated = repo.getBillById(id).getOrThrow()!!
    assertEquals(300.0, updated.totalAmount, 0.001)
    assertEquals(started, updated.startedAtEpochMillis)
    assertNull(updated.endedAtEpochMillis)
    assertEquals(stored.createdAt, updated.createdAt)
  }

  @Test
  fun updateBill_withoutCreatedAt_fails() = runBlocking {
    val result = repo.updateBill(sampleBill("c1").copy(id = "b1"))
    assertTrue(result.isFailure)
  }

  @Test
  fun deleteCategoryAndBills_removesOnlyThatCategory() = runBlocking {
    val gas = repo.createCategory("غاز", UtilityType.GAS, "م³").getOrThrow()
    val water = repo.createCategory("ماء", UtilityType.WATER, "م³").getOrThrow()
    repo.createBill(sampleBill(gas.id)).getOrThrow()
    repo.createBill(sampleBill(gas.id, 200.0)).getOrThrow()
    repo.createBill(sampleBill(water.id)).getOrThrow()

    repo.deleteCategoryAndBills(gas.id).getOrThrow()

    assertEquals(listOf(water.id), repo.observeCategories(LOCAL_USER_ID).first().map { it.id })
    assertEquals(listOf(water.id), repo.observeAllBills(LOCAL_USER_ID).first().map { it.categoryId })
  }

  @Test
  fun deleteBill_removesIt() = runBlocking {
    val cat = repo.createCategory("غاز", UtilityType.GAS, "م³").getOrThrow()
    val id = repo.createBill(sampleBill(cat.id)).getOrThrow()
    repo.deleteBill(id).getOrThrow()
    assertTrue(repo.observeAllBills(LOCAL_USER_ID).first().isEmpty())
  }

  @Test
  fun householdBudget_savedAndUpdatedInPlace() = runBlocking {
    assertNull(repo.observeHouseholdBudget(LOCAL_USER_ID).first())
    val first = repo.saveHouseholdBudget(2500.0, "ج.م", 80.0).getOrThrow()
    repo.saveHouseholdBudget(3000.0, "ج.م", 90.0).getOrThrow()

    val stored = repo.observeHouseholdBudget(LOCAL_USER_ID).first()!!
    assertEquals(3000.0, stored.monthlyLimit, 0.001)
    assertEquals(90.0, stored.warningPercent, 0.001)
    assertEquals(first.createdAt, stored.createdAt)
  }
}
