package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.BillDao
import com.example.data.local.BillDatabase
import com.example.data.local.LOCAL_USER_ID
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.HouseholdBudget
import com.example.data.model.UtilityType
import com.example.domain.BillSearchEngine
import com.example.domain.BillSearchScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bill, category and budget storage. Everything lives in the on-device Room database; nothing is
 * sent to a server. Every row is owned by [LOCAL_USER_ID].
 */
class BillRepository(private val dao: BillDao) {
  constructor(context: Context) : this(BillDatabase.getInstance(context).billDao())

  /** Runs a storage step and turns failures into a [Result] (cancellation is never swallowed). */
  private inline fun <T> safely(operation: String, block: () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Local storage error during $operation", e)
      Result.failure(e)
    }

  private fun newId(): String = UUID.randomUUID().toString()

  fun observeCategories(userId: String): Flow<List<BillCategory>> = dao.observeCategories(userId)

  fun observeAllBills(userId: String): Flow<List<BillRecord>> = dao.observeAllBills(userId)

  fun observeBillsByCategory(userId: String, categoryId: String): Flow<List<BillRecord>> =
    dao.observeBillsByCategory(userId, categoryId)

  fun observeSearchedBills(
    userId: String,
    query: String,
    scope: BillSearchScope = BillSearchScope.ALL,
    categoriesById: Map<String, BillCategory> = emptyMap(),
  ): Flow<List<BillRecord>> {
    return observeAllBills(userId).map { bills ->
      BillSearchEngine.filterBills(
        bills = bills,
        query = query,
        scope = scope,
        categoriesById = categoriesById,
      )
    }
  }

  suspend fun searchBills(
    userId: String = LOCAL_USER_ID,
    query: String,
    scope: BillSearchScope = BillSearchScope.ALL,
    categoriesById: Map<String, BillCategory> = emptyMap(),
  ): Result<List<BillRecord>> =
    safely("searchBills") {
      BillSearchEngine.filterBills(
        bills = dao.getAllBills(userId),
        query = query,
        scope = scope,
        categoriesById = categoriesById,
      )
    }

  suspend fun createCategory(
    name: String,
    utilityType: UtilityType,
    defaultUnit: String,
    alertThresholdPercent: Double = 25.0,
    trackDuration: Boolean = false,
  ): Result<BillCategory> =
    safely("createCategory") {
      val now = System.currentTimeMillis()
      val category =
        BillCategory(
          id = newId(),
          userId = LOCAL_USER_ID,
          name = name.trim().take(80).ifBlank { utilityType.defaultCategoryName },
          utilityType = utilityType.firestoreKey,
          defaultUnit = defaultUnit.trim().take(20).ifBlank { utilityType.defaultUnit },
          alertThresholdPercent = alertThresholdPercent.coerceIn(5.0, 200.0),
          createdAt = now,
          updatedAt = now,
          trackDuration = trackDuration,
        )
      dao.upsertCategory(category)
      category
    }

  suspend fun updateCategory(category: BillCategory): Result<Unit> =
    safely("updateCategory") {
      require(category.id.isNotBlank()) { "Category ID must not be blank" }
      requireNotNull(category.createdAt) { "Category createdAt must not be null for update" }
      dao.upsertCategory(
        category.copy(
          userId = LOCAL_USER_ID,
          name = category.name.trim().take(80).ifBlank { "فاتورة منزلية" },
          defaultUnit = category.defaultUnit.trim().take(20).ifBlank { "وحدة" },
          alertThresholdPercent = category.alertThresholdPercent.coerceIn(5.0, 200.0),
          updatedAt = System.currentTimeMillis(),
        )
      )
    }

  suspend fun deleteCategoryAndBills(categoryId: String): Result<Unit> =
    safely("deleteCategoryAndBills") { dao.deleteCategoryWithBills(LOCAL_USER_ID, categoryId) }

  suspend fun createBill(bill: BillRecord): Result<String> =
    safely("createBill") {
      require(bill.categoryId.isNotBlank()) { "categoryId is required" }
      val now = System.currentTimeMillis()
      val payload =
        sanitizeBill(bill)
          .copy(id = newId(), userId = LOCAL_USER_ID, createdAt = now, updatedAt = now)
      dao.upsertBill(payload)
      payload.id
    }

  suspend fun updateBill(bill: BillRecord): Result<Unit> =
    safely("updateBill") {
      require(bill.id.isNotBlank()) { "Bill ID must not be blank for update" }
      requireNotNull(bill.createdAt) { "Bill createdAt must not be null for update" }
      dao.upsertBill(
        sanitizeBill(bill).copy(userId = LOCAL_USER_ID, updatedAt = System.currentTimeMillis())
      )
    }

  suspend fun deleteBill(billId: String): Result<Unit> =
    safely("deleteBill") { dao.deleteBill(LOCAL_USER_ID, billId) }

  suspend fun getBillById(billId: String): Result<BillRecord?> =
    safely("getBillById") { dao.getBillById(billId) }

  fun observeHouseholdBudget(userId: String): Flow<HouseholdBudget?> = dao.observeBudget(userId)

  suspend fun getHouseholdBudget(userId: String): Result<HouseholdBudget?> =
    safely("getHouseholdBudget") { dao.getBudget(userId) }

  suspend fun saveHouseholdBudget(
    monthlyLimit: Double,
    currency: String = "ر.س",
    warningPercent: Double = 80.0,
  ): Result<HouseholdBudget> =
    safely("saveHouseholdBudget") {
      val now = System.currentTimeMillis()
      val existing = dao.getBudget(LOCAL_USER_ID)
      val budget =
        HouseholdBudget(
          id = LOCAL_USER_ID,
          userId = LOCAL_USER_ID,
          monthlyLimit = monthlyLimit.coerceIn(0.0, 10_000_000.0),
          currency = currency.trim().take(16).ifBlank { "ر.س" },
          warningPercent = warningPercent.coerceIn(10.0, 100.0),
          createdAt = existing?.createdAt ?: now,
          updatedAt = now,
        )
      dao.upsertBudget(budget)
      budget
    }

  // Field limits shared by create and update.
  private fun sanitizeBill(bill: BillRecord): BillRecord =
    bill.copy(
      categoryName = bill.categoryName.trim().take(80).ifBlank { "فاتورة" },
      utilityType = UtilityType.fromKey(bill.utilityType).firestoreKey,
      billDateIso = bill.billDateIso.trim().take(10),
      billingMonth = bill.billingMonth.trim().take(7),
      billDateEpochMillis = bill.billDateEpochMillis.coerceAtLeast(1000L),
      totalAmount = bill.totalAmount.coerceIn(0.0, 10_000_000.0),
      currency = bill.currency.trim().take(16).ifBlank { "ر.س" },
      consumptionValue = bill.consumptionValue.coerceIn(0.0, 10_000_000.0),
      consumptionUnit = bill.consumptionUnit.trim().take(20).ifBlank { "وحدة" },
      previousReading = bill.previousReading.coerceIn(0.0, 100_000_000.0),
      currentReading = bill.currentReading.coerceIn(0.0, 100_000_000.0),
      invoiceNumber = bill.invoiceNumber.trim().take(80),
      providerName = bill.providerName.trim().take(120),
      rawExtractedText = bill.rawExtractedText.trim().take(5000),
      notes = bill.notes.trim().take(1000),
      sourceType = if (bill.sourceType == "camera_ai") "camera_ai" else "manual",
      sheetItems = sanitizeSheetItems(bill.sheetItems),
    )

  private fun sanitizeSheetItems(items: List<BillSheetRow>): List<BillSheetRow> {
    return items
      .filter { it.label.isNotBlank() }
      .take(25)
      .map { row ->
        BillSheetRow(
          label = row.label.trim().take(120),
          value = row.value.trim().take(200),
          unit = row.unit.trim().take(30),
        )
      }
  }

  private companion object {
    const val TAG = "BillRepository"
  }
}
