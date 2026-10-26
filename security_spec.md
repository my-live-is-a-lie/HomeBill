# Security Specification — Fawatiri (فواتيري)

## 1. Data Invariants
1. **Strict Multi-Tenant Ownership**: Every `categories/{categoryId}` and `bills/{billId}` document belongs exclusively to `userId == request.auth.uid`. Cross-user reads, queries, updates, and deletes are strictly forbidden.
2. **Relational Parent Integrity**: A `bills/{billId}` document cannot be created unless `/categories/{categoryId}` exists AND `get(/databases/$(database)/documents/categories/$(categoryId)).data.userId == request.auth.uid`.
3. **Immutable Ownership & Temporal Anchors**: On `update`, `userId` and `createdAt` (and `categoryId` on `bills`) are strictly immutable.
4. **Query Filter Enforcement**: `allow list` on both `/categories` and `/bills` enforces `resource.data.userId == request.auth.uid`, rejecting unfiltered collection scans.
5. **Bounded Payload & Sheet Array Guard**: Every string has an explicit `.size() <= MAX` bound, document IDs match `^[a-zA-Z0-9_\-]+$` up to 128 chars, and `sheetItems` is strictly bounded to `<= 25` structured row maps.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Scan**: Unauthenticated `list` on `/bills` or `/categories` -> `PERMISSION_DENIED`.
2. **Cross-User Read (`get`)**: Alice (`alice_123`) reads Bob's (`bob_456`) bill `/bills/bob_bill_1` -> `PERMISSION_DENIED`.
3. **Unfiltered Query (`list` without `where("userId", "==", uid)`)**: Alice queries `db.collection("bills").get()` -> `PERMISSION_DENIED`.
4. **Identity Spoofing on Create**: Alice creates `/categories/cat_spoof` with `userId: "bob_456"` -> `PERMISSION_DENIED`.
5. **Orphaned Bill Creation**: Alice creates `/bills/bill_orphan` pointing to non-existent `categoryId: "missing_cat"` -> `PERMISSION_DENIED`.
6. **Cross-Tenant Parent Hijack**: Alice creates `/bills/bill_hijack` pointing to Bob's `categoryId: "bob_cat_1"` -> `PERMISSION_DENIED`.
7. **Shadow Field Injection on Create**: Alice creates `/categories/cat_shadow` including extra field `isAdmin: true` -> `PERMISSION_DENIED`.
8. **Shadow Field Injection on Update**: Alice updates her own bill adding `verifiedBySystem: true` -> `PERMISSION_DENIED`.
9. **Immutable Field Tampering (`userId` / `createdAt` / `categoryId`)**: Alice updates `categoryId` or `createdAt` on an existing bill -> `PERMISSION_DENIED`.
10. **Negative / Overflow Amount Poisoning**: Alice creates a bill with `totalAmount: -100` or `consumptionValue: -5` -> `PERMISSION_DENIED`.
11. **Resource Exhaustion (Oversized String / Sheet List)**: Alice writes a 6000-char `rawExtractedText` or 30 `sheetItems` -> `PERMISSION_DENIED`.
12. **ID Poisoning**: Alice creates a document with invalid characters or >128 char ID -> `PERMISSION_DENIED`.
