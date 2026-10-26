package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.UtilityType
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BillRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createCategoryAndBill_authenticatedOwner_emitsInRealtimeFlow() = runBlocking {
    val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
    val repo = BillRepository(firestore, auth)

    val catResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createCategory(
          name = "فاتورة الغاز",
          utilityType = UtilityType.GAS,
          defaultUnit = "م³",
          alertThresholdPercent = 25.0,
        )
      }
    assertTrue("Expected category creation to succeed: ${catResult.exceptionOrNull()}", catResult.isSuccess)
    val createdCat = catResult.getOrThrow()

    val billResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createBill(
          BillRecord(
            userId = uid,
            categoryId = createdCat.id,
            categoryName = createdCat.name,
            utilityType = "gas",
            billDateIso = "2026-10-20",
            billingMonth = "2026-10",
            billDateEpochMillis = 1792454400000L,
            totalAmount = 165.0,
            currency = "ر.س",
            consumptionValue = 42.5,
            consumptionUnit = "م³",
            previousReading = 800.0,
            currentReading = 842.5,
            invoiceNumber = "GAS-991",
            providerName = "شركة الغاز",
            rawExtractedText = "إجمالي فاتورة الغاز 165.0",
            notes = "فاتورة شهر أكتوبر",
            sourceType = "camera_ai",
            sheetItems =
              listOf(
                BillSheetRow(label = "القراءة السابقة", value = "800", unit = "م³"),
                BillSheetRow(label = "القراءة الحالية", value = "842.5", unit = "م³"),
              ),
          )
        )
      }
    assertTrue("Expected bill creation to succeed: ${billResult.exceptionOrNull()}", billResult.isSuccess)
    val billId = billResult.getOrThrow()

    val emittedBills =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeBillsByCategory(uid, createdCat.id).first { list -> list.any { it.id == billId } }
      }
    assertEquals(1, emittedBills.size)
    assertEquals(165.0, emittedBills.first().totalAmount, 0.01)
    assertEquals(2, emittedBills.first().sheetItems.size)
  }

  @Test
  fun getBillById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
    val aliceRepo = BillRepository(firestore, auth)

    val cat =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.createCategory("فاتورة الكهرباء", UtilityType.ELECTRICITY, "ك.و.س").getOrThrow()
      }
    val billId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createBill(
            BillRecord(
              userId = aliceUid,
              categoryId = cat.id,
              categoryName = cat.name,
              utilityType = "electricity",
              billDateIso = "2026-10-10",
              billingMonth = "2026-10",
              billDateEpochMillis = 1791590400000L,
              totalAmount = 320.0,
              currency = "ر.س",
              consumptionValue = 950.0,
              consumptionUnit = "ك.و.س",
            )
          )
          .getOrThrow()
      }

    // Switch to Bob and verify cross-user read is blocked
    signInTestUser("bob_${UUID.randomUUID()}@test.com")
    val bobRepo = BillRepository(firestore, auth)
    val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getBillById(billId) }
    assertTrue(bobReadResult.isFailure)
    val fsEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException", fsEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  @Test
  fun observeAllBills_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = BillRepository(firestore, auth)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAllBills("unauth_uid").first()
      }
      fail("Expected unauthenticated query to fail with PERMISSION_DENIED")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }
  }

  @Test
  fun createShoppingCategoryAndBill_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser("shopper_${UUID.randomUUID()}@test.com")
    val repo = BillRepository(firestore, auth)

    val catResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createCategory(
          name = "فواتير التسوق",
          utilityType = UtilityType.SHOPPING,
          defaultUnit = "صنف",
          alertThresholdPercent = 25.0,
        )
      }
    assertTrue("Expected shopping category creation to succeed: ${catResult.exceptionOrNull()}", catResult.isSuccess)
    val shoppingCat = catResult.getOrThrow()

    val billResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createBill(
          BillRecord(
            userId = uid,
            categoryId = shoppingCat.id,
            categoryName = shoppingCat.name,
            utilityType = "shopping",
            billDateIso = "2026-10-25",
            billingMonth = "2026-10",
            billDateEpochMillis = 1792886400000L,
            totalAmount = 245.50,
            currency = "ر.س",
            consumptionValue = 6.0,
            consumptionUnit = "صنف",
            invoiceNumber = "POS-7712",
            providerName = "أسواق التميمي",
            rawExtractedText = "أسواق التميمي - المجموع 245.50 ر.س",
            sourceType = "camera_ai",
            sheetItems =
              listOf(
                BillSheetRow(label = "مواد غذائية", value = "213.48", unit = "ر.س"),
                BillSheetRow(label = "ضريبة القيمة المضافة", value = "32.02", unit = "ر.س"),
              ),
          )
        )
      }
    assertTrue("Expected shopping bill creation to succeed: ${billResult.exceptionOrNull()}", billResult.isSuccess)
  }

  @Test
  fun saveAndObserveHouseholdBudget_authenticatedOwnerSucceeds_crossUserFails() = runBlocking {
    val aliceUid = signInTestUser("budget_alice_${UUID.randomUUID()}@test.com")
    val aliceRepo = BillRepository(firestore, auth)

    // Unset budget emits null initially
    val initialBudget =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeHouseholdBudget(aliceUid).first()
      }
    assertEquals(null, initialBudget)

    // Create household budget
    val createRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.saveHouseholdBudget(monthlyLimit = 3500.0, currency = "ر.س", warningPercent = 80.0)
      }
    assertTrue("Expected budget create to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)

    // Update household budget
    val updateRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.saveHouseholdBudget(monthlyLimit = 4200.0, currency = "ر.س", warningPercent = 85.0)
      }
    assertTrue("Expected budget update to succeed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeHouseholdBudget(aliceUid).first { it != null && it.monthlyLimit == 4200.0 }
      }
    assertNotNull(observed)
    assertEquals(4200.0, observed!!.monthlyLimit, 0.01)
    assertEquals(85.0, observed.warningPercent, 0.01)

    // Cross-user Bob cannot read Alice's household budget
    signInTestUser("budget_bob_${UUID.randomUUID()}@test.com")
    val bobRepo = BillRepository(firestore, auth)
    val bobReadRes = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getHouseholdBudget(aliceUid) }
    assertTrue(bobReadRes.isFailure)
    val fsEx = bobReadRes.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException", fsEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
