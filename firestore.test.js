const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildCategoryPayload(dbContext, userId, overrides = {}) {
  const serverTimestamp = dbContext.firestore.FieldValue
    ? dbContext.firestore.FieldValue.serverTimestamp()
    : new Date(Date.now() - 1000);
  return {
    userId,
    name: "فاتورة الغاز",
    utilityType: "gas",
    defaultUnit: "م³",
    alertThresholdPercent: 25,
    createdAt: serverTimestamp,
    updatedAt: serverTimestamp,
    ...overrides,
  };
}

function buildBillPayload(userId, categoryId, overrides = {}) {
  const ts = new Date(Date.now() - 1000);
  return {
    userId,
    categoryId,
    categoryName: "فاتورة الغاز",
    utilityType: "gas",
    billDateIso: "2026-10-15",
    billingMonth: "2026-10",
    billDateEpochMillis: 1792022400000,
    totalAmount: 145.5,
    currency: "ر.س",
    consumptionValue: 38.2,
    consumptionUnit: "م³",
    previousReading: 1200,
    currentReading: 1238.2,
    invoiceNumber: "INV-2026-10",
    providerName: "شركة الغاز الوطنية",
    rawExtractedText: "فاتورة غاز شهر أكتوبر الإجمالي 145.5",
    notes: "استهلاك معتاد",
    sourceType: "camera_ai",
    sheetItems: [
      { label: "القراءة السابقة", value: "1200", unit: "م³" },
      { label: "القراءة الحالية", value: "1238.2", unit: "م³" },
    ],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or list categories or bills", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("categories").get());
  await assertFails(unauthDb.collection("bills").get());
});

test("Authenticated owner can create category and bill with valid parent category", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceCtx.firestore();
  const ts = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("categories").doc("cat_gas_1").set({
      userId: ALICE_UID,
      name: "فاتورة الغاز",
      utilityType: "gas",
      defaultUnit: "م³",
      alertThresholdPercent: 25,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("bills")
      .doc("bill_oct_1")
      .set(buildBillPayload(ALICE_UID, "cat_gas_1"))
  );
});

test("Cross-user isolation: Alice cannot read or delete Bob's category or bill", async () => {
  const ts = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("categories").doc("bob_cat").set({
      userId: BOB_UID,
      name: "فاتورة الكهرباء",
      utilityType: "electricity",
      defaultUnit: "ك.و.س",
      alertThresholdPercent: 25,
      createdAt: ts,
      updatedAt: ts,
    });
    await adminDb
      .collection("bills")
      .doc("bob_bill")
      .set(buildBillPayload(BOB_UID, "bob_cat"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("categories").doc("bob_cat").get());
  await assertFails(aliceDb.collection("bills").doc("bob_bill").get());
  await assertFails(aliceDb.collection("bills").doc("bob_bill").delete());
});

test("Client query alignment: list succeeds with userId filter and fails without filter", async () => {
  const ts = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("categories").doc("alice_cat").set({
      userId: ALICE_UID,
      name: "فاتورة الماء",
      utilityType: "water",
      defaultUnit: "م³",
      alertThresholdPercent: 20,
      createdAt: ts,
      updatedAt: ts,
    });
    await adminDb
      .collection("bills")
      .doc("alice_bill")
      .set(buildBillPayload(ALICE_UID, "alice_cat"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("categories").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("bills").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("categories").get());
  await assertFails(aliceDb.collection("bills").get());
});

test("Orphaned bill & cross-tenant parent hijack rejected", async () => {
  const ts = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("categories").doc("bob_cat").set({
      userId: BOB_UID,
      name: "فاتورة الغاز",
      utilityType: "gas",
      defaultUnit: "م³",
      alertThresholdPercent: 25,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Non-existent category
  await assertFails(
    aliceDb
      .collection("bills")
      .doc("orphan_bill")
      .set(buildBillPayload(ALICE_UID, "missing_cat"))
  );
  // Bob's category
  await assertFails(
    aliceDb
      .collection("bills")
      .doc("hijack_bill")
      .set(buildBillPayload(ALICE_UID, "bob_cat"))
  );
});

test("Shadow fields, negative amounts, and immutable field tampering rejected", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("categories").doc("alice_cat").set({
      userId: ALICE_UID,
      name: "فاتورة الغاز",
      utilityType: "gas",
      defaultUnit: "م³",
      alertThresholdPercent: 25,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Shadow field on create
  await assertFails(
    aliceDb
      .collection("bills")
      .doc("shadow_bill")
      .set(buildBillPayload(ALICE_UID, "alice_cat", { isAdmin: true }))
  );

  // Negative amount on create
  await assertFails(
    aliceDb
      .collection("bills")
      .doc("neg_bill")
      .set(buildBillPayload(ALICE_UID, "alice_cat", { totalAmount: -10 }))
  );

  // Valid create followed by valid update & invalid immutable mutation
  await assertSucceeds(
    aliceDb
      .collection("bills")
      .doc("valid_bill")
      .set(buildBillPayload(ALICE_UID, "alice_cat", { createdAt: ts, updatedAt: ts }))
  );

  await assertSucceeds(
    aliceDb
      .collection("bills")
      .doc("valid_bill")
      .update({ totalAmount: 180.0, updatedAt: new Date(Date.now() - 500) })
  );

  // Tampering immutable categoryId
  await assertFails(
    aliceDb
      .collection("bills")
      .doc("valid_bill")
      .update({ categoryId: "other_cat", updatedAt: new Date(Date.now() - 500) })
  );
});

test("Automatic expense classification types (shopping, dining, maintenance, fuel) succeed and unknown types fail", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("categories").doc("cat_shopping_1").set({
      userId: ALICE_UID,
      name: "فواتير التسوق",
      utilityType: "shopping",
      defaultUnit: "صنف",
      alertThresholdPercent: 30,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("bills")
      .doc("bill_shopping_1")
      .set(
        buildBillPayload(ALICE_UID, "cat_shopping_1", {
          categoryName: "فواتير التسوق",
          utilityType: "shopping",
          consumptionUnit: "صنف",
          providerName: "أسواق التميمي",
        })
      )
  );

  await assertFails(
    aliceDb.collection("categories").doc("cat_invalid_type").set({
      userId: ALICE_UID,
      name: "غير معروف",
      utilityType: "crypto_unknown",
      defaultUnit: "وحدة",
      alertThresholdPercent: 25,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("HouseholdBudget rules: owner can get unset doc, create, update, and cross-user/invalid writes fail", async () => {
  const ts = new Date(Date.now() - 1000);
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Unauthenticated read fails
  await assertFails(unauthDb.collection("budgets").doc(ALICE_UID).get());

  // Authenticated owner can read non-existent budget doc
  await assertSucceeds(aliceDb.collection("budgets").doc(ALICE_UID).get());

  // Owner cannot create budget under another user's doc ID
  await assertFails(
    aliceDb.collection("budgets").doc(BOB_UID).set({
      userId: ALICE_UID,
      monthlyLimit: 3500,
      currency: "ر.س",
      warningPercent: 80,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner can create valid budget
  await assertSucceeds(
    aliceDb.collection("budgets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      monthlyLimit: 3500,
      currency: "ر.س",
      warningPercent: 80,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user Bob cannot read or update Alice's budget
  await assertFails(bobDb.collection("budgets").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("budgets").doc(ALICE_UID).update({
      monthlyLimit: 5000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner can update monthlyLimit and warningPercent while preserving createdAt
  await assertSucceeds(
    aliceDb.collection("budgets").doc(ALICE_UID).update({
      monthlyLimit: 4200,
      warningPercent: 85,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Negative monthlyLimit or out-of-range warningPercent rejected
  await assertFails(
    aliceDb.collection("budgets").doc(ALICE_UID).update({
      monthlyLimit: -100,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    aliceDb.collection("budgets").doc(ALICE_UID).update({
      warningPercent: 150,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

