package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.HouseholdBudget
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BillDao {

  // --- Categories ---

  @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY createdAt DESC")
  abstract fun observeCategories(userId: String): Flow<List<BillCategory>>

  @Query("SELECT * FROM categories WHERE id = :categoryId AND userId = :userId LIMIT 1")
  abstract suspend fun getCategoryById(userId: String, categoryId: String): BillCategory?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsertCategory(category: BillCategory)

  @Query("DELETE FROM categories WHERE id = :categoryId AND userId = :userId")
  abstract suspend fun deleteCategory(userId: String, categoryId: String)

  // --- Bills ---

  @Query(
    "SELECT * FROM bills WHERE userId = :userId " +
      "ORDER BY billDateEpochMillis DESC, createdAt DESC"
  )
  abstract fun observeAllBills(userId: String): Flow<List<BillRecord>>

  @Query(
    "SELECT * FROM bills WHERE userId = :userId AND categoryId = :categoryId " +
      "ORDER BY billDateEpochMillis DESC, createdAt DESC"
  )
  abstract fun observeBillsByCategory(userId: String, categoryId: String): Flow<List<BillRecord>>

  @Query(
    "SELECT * FROM bills WHERE userId = :userId " +
      "ORDER BY billDateEpochMillis DESC, createdAt DESC"
  )
  abstract suspend fun getAllBills(userId: String): List<BillRecord>

  @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
  abstract suspend fun getBillById(billId: String): BillRecord?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsertBill(bill: BillRecord)

  @Query("DELETE FROM bills WHERE id = :billId AND userId = :userId")
  abstract suspend fun deleteBill(userId: String, billId: String)

  @Query("DELETE FROM bills WHERE userId = :userId AND categoryId = :categoryId")
  abstract suspend fun deleteBillsOfCategory(userId: String, categoryId: String)

  /** Removes a category together with all of its bills in one atomic step. */
  @Transaction
  open suspend fun deleteCategoryWithBills(userId: String, categoryId: String) {
    deleteBillsOfCategory(userId, categoryId)
    deleteCategory(userId, categoryId)
  }

  // --- Household budget (one row per owner id) ---

  @Query("SELECT * FROM budgets WHERE id = :userId LIMIT 1")
  abstract fun observeBudget(userId: String): Flow<HouseholdBudget?>

  @Query("SELECT * FROM budgets WHERE id = :userId LIMIT 1")
  abstract suspend fun getBudget(userId: String): HouseholdBudget?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsertBudget(budget: HouseholdBudget)
}
