package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.HouseholdBudget
import com.example.data.model.UtilityType
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.BillSearchEngine
import com.example.domain.BillSearchScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BillRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeCategories(userId: String): Flow<List<BillCategory>> {
    val path = CATEGORIES_COLLECTION
    return db
      .collection(CATEGORIES_COLLECTION)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(BillCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observeAllBills(userId: String): Flow<List<BillRecord>> {
    val path = BILLS_COLLECTION
    return db
      .collection(BILLS_COLLECTION)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(BillRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(
            compareByDescending<BillRecord> { it.billDateEpochMillis }
              .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
          )
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observeBillsByCategory(userId: String, categoryId: String): Flow<List<BillRecord>> {
    val path = BILLS_COLLECTION
    return db
      .collection(BILLS_COLLECTION)
      .whereEqualTo("userId", userId)
      .whereEqualTo("categoryId", categoryId)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(BillRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(
            compareByDescending<BillRecord> { it.billDateEpochMillis }
              .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
          )
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

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
    userId: String = requireUserId(),
    query: String,
    scope: BillSearchScope = BillSearchScope.ALL,
    categoriesById: Map<String, BillCategory> = emptyMap(),
  ): Result<List<BillRecord>> = runCatching {
    val path = BILLS_COLLECTION
    val allUserBills =
      try {
        db
          .collection(BILLS_COLLECTION)
          .whereEqualTo("userId", userId)
          .get()
          .await()
          .toObjects(BillRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(
            compareByDescending<BillRecord> { it.billDateEpochMillis }
              .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
          )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, path)
        throw e
      }

    BillSearchEngine.filterBills(
      bills = allUserBills,
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
  ): Result<BillCategory> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection(CATEGORIES_COLLECTION).document()
    val trimmedName = name.trim().take(80).ifBlank { utilityType.defaultCategoryName }
    val trimmedUnit = defaultUnit.trim().take(20).ifBlank { utilityType.defaultUnit }
    val clampedThreshold = alertThresholdPercent.coerceIn(5.0, 200.0)

    val category =
      BillCategory(
        id = docRef.id,
        userId = uid,
        name = trimmedName,
        utilityType = utilityType.firestoreKey,
        defaultUnit = trimmedUnit,
        alertThresholdPercent = clampedThreshold,
        createdAt = null,
        updatedAt = null,
      )
    try {
      docRef.set(category).await()
      category
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updateCategory(category: BillCategory): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(category.id.isNotBlank()) { "Category ID must not be blank" }
    requireNotNull(category.createdAt) { "Category createdAt must not be null for update" }
    val docRef = db.collection(CATEGORIES_COLLECTION).document(category.id)
    val sanitized =
      category.copy(
        userId = uid,
        name = category.name.trim().take(80).ifBlank { "فاتورة منزلية" },
        defaultUnit = category.defaultUnit.trim().take(20).ifBlank { "وحدة" },
        alertThresholdPercent = category.alertThresholdPercent.coerceIn(5.0, 200.0),
        updatedAt = null,
      )
    try {
      docRef.set(sanitized).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteCategoryAndBills(categoryId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val billsPath = BILLS_COLLECTION
    val billsSnapshot =
      try {
        db
          .collection(BILLS_COLLECTION)
          .whereEqualTo("userId", uid)
          .whereEqualTo("categoryId", categoryId)
          .get()
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, billsPath)
        throw e
      }

    for (billDoc in billsSnapshot.documents) {
      try {
        billDoc.reference.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, billDoc.reference.path)
        throw e
      }
    }

    val catRef = db.collection(CATEGORIES_COLLECTION).document(categoryId)
    try {
      catRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, catRef.path)
      throw e
    }
  }

  suspend fun createBill(bill: BillRecord): Result<String> = runCatching {
    val uid = requireUserId()
    require(bill.categoryId.isNotBlank()) { "categoryId is required" }
    val docRef = db.collection(BILLS_COLLECTION).document()
    val sanitizedSheet = sanitizeSheetItems(bill.sheetItems)
    val payload =
      bill.copy(
        id = docRef.id,
        userId = uid,
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
        sheetItems = sanitizedSheet,
        createdAt = null,
        updatedAt = null,
      )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updateBill(bill: BillRecord): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(bill.id.isNotBlank()) { "Bill ID must not be blank for update" }
    requireNotNull(bill.createdAt) { "Bill createdAt must not be null for update" }
    val docRef = db.collection(BILLS_COLLECTION).document(bill.id)
    val payload =
      bill.copy(
        userId = uid,
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
        updatedAt = null,
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteBill(billId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection(BILLS_COLLECTION).document(billId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun getBillById(billId: String): Result<BillRecord?> = runCatching {
    val docRef = db.collection(BILLS_COLLECTION).document(billId)
    try {
      docRef
        .get()
        .await()
        .toObject(BillRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  fun observeHouseholdBudget(userId: String): Flow<HouseholdBudget?> {
    val docRef = db.collection(BUDGETS_COLLECTION).document(userId)
    return docRef
      .snapshots()
      .map { snapshot ->
        if (!snapshot.exists()) {
          null
        } else {
          snapshot.toObject(
            HouseholdBudget::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  suspend fun getHouseholdBudget(userId: String): Result<HouseholdBudget?> = runCatching {
    val docRef = db.collection(BUDGETS_COLLECTION).document(userId)
    try {
      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        null
      } else {
        snapshot.toObject(
          HouseholdBudget::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun saveHouseholdBudget(
    monthlyLimit: Double,
    currency: String = "ر.س",
    warningPercent: Double = 80.0,
  ): Result<HouseholdBudget> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection(BUDGETS_COLLECTION).document(uid)
    val sanitizedLimit = monthlyLimit.coerceIn(0.0, 10_000_000.0)
    val sanitizedCurrency = currency.trim().take(16).ifBlank { "ر.س" }
    val sanitizedWarning = warningPercent.coerceIn(10.0, 100.0)

    try {
      val existingSnap = docRef.get().await()
      if (existingSnap.exists()) {
        docRef
          .update(
            mapOf(
              "monthlyLimit" to sanitizedLimit,
              "currency" to sanitizedCurrency,
              "warningPercent" to sanitizedWarning,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        val initialBudget =
          HouseholdBudget(
            id = uid,
            userId = uid,
            monthlyLimit = sanitizedLimit,
            currency = sanitizedCurrency,
            warningPercent = sanitizedWarning,
            createdAt = null,
            updatedAt = null,
          )
        docRef.set(initialBudget).await()
      }
      HouseholdBudget(
        id = uid,
        userId = uid,
        monthlyLimit = sanitizedLimit,
        currency = sanitizedCurrency,
        warningPercent = sanitizedWarning,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

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

  companion object {
    const val CATEGORIES_COLLECTION = "categories"
    const val BILLS_COLLECTION = "bills"
    const val BUDGETS_COLLECTION = "budgets"
  }
}
