package com.example.data.model

import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
enum class UtilityType(
  val firestoreKey: String,
  @param:StringRes val labelRes: Int,
  val defaultCategoryName: String,
  val defaultUnit: String,
) {
  GAS(
    firestoreKey = "gas",
    labelRes = R.string.utility_gas,
    defaultCategoryName = "فاتورة الغاز",
    defaultUnit = "م³",
  ),
  ELECTRICITY(
    firestoreKey = "electricity",
    labelRes = R.string.utility_electricity,
    defaultCategoryName = "فاتورة الكهرباء",
    defaultUnit = "ك.و.س",
  ),
  WATER(
    firestoreKey = "water",
    labelRes = R.string.utility_water,
    defaultCategoryName = "فاتورة الماء",
    defaultUnit = "م³",
  ),
  INTERNET(
    firestoreKey = "internet",
    labelRes = R.string.utility_internet,
    defaultCategoryName = "فاتورة الإنترنت",
    defaultUnit = "جيجا",
  ),
  TELECOM(
    firestoreKey = "telecom",
    labelRes = R.string.utility_telecom,
    defaultCategoryName = "فاتورة الاتصالات",
    defaultUnit = "وحدة",
  ),
  SHOPPING(
    firestoreKey = "shopping",
    labelRes = R.string.utility_shopping,
    defaultCategoryName = "فواتير التسوق",
    defaultUnit = "صنف",
  ),
  DINING(
    firestoreKey = "dining",
    labelRes = R.string.utility_dining,
    defaultCategoryName = "فواتير المطاعم",
    defaultUnit = "طلب",
  ),
  MAINTENANCE(
    firestoreKey = "maintenance",
    labelRes = R.string.utility_maintenance,
    defaultCategoryName = "فواتير الصيانة",
    defaultUnit = "خدمة",
  ),
  FUEL(
    firestoreKey = "fuel",
    labelRes = R.string.utility_fuel,
    defaultCategoryName = "فواتير الوقود",
    defaultUnit = "لتر",
  ),
  OTHER(
    firestoreKey = "other",
    labelRes = R.string.utility_other,
    defaultCategoryName = "فاتورة منزلية",
    defaultUnit = "وحدة",
  );

  companion object {
    fun fromKey(key: String?): UtilityType {
      val normalized = key?.trim()?.lowercase() ?: return OTHER
      entries.firstOrNull { it.firestoreKey == normalized }?.let { return it }
      return when {
        normalized in setOf("groceries", "grocery", "supermarket", "retail", "store", "market") ||
          normalized.contains("تسوق") ||
          normalized.contains("مقاضي") -> SHOPPING
        normalized in setOf("food", "restaurant", "cafe", "coffee", "delivery") ||
          normalized.contains("مطعم") ||
          normalized.contains("مطاعم") -> DINING
        normalized in setOf("repair", "service", "services", "plumbing", "hvac") ||
          normalized.contains("صيانة") -> MAINTENANCE
        normalized in setOf("petrol", "gasoline", "transport", "station") ||
          normalized.contains("وقود") ||
          normalized.contains("بنزين") -> FUEL
        normalized == "power" || normalized.contains("كهرباء") -> ELECTRICITY
        normalized.contains("مياه") || normalized.contains("ماء") -> WATER
        normalized.contains("غاز") -> GAS
        normalized in setOf("wifi", "fiber", "broadband") || normalized.contains("إنترنت") ||
          normalized.contains("انترنت") -> INTERNET
        normalized in setOf("phone", "mobile", "cellular") || normalized.contains("اتصالات") ->
          TELECOM
        else -> OTHER
      }
    }
  }
}

@IgnoreExtraProperties
@Keep
data class BillSheetRow(
  val label: String = "",
  val value: String = "",
  val unit: String = "",
)

/** How a section's bill form is built. Stored on the category as a plain string. */
object TemplateModes {
  // Sections created before templates existed: the full original form, unchanged.
  const val LEGACY = "legacy"
  // Ready-made fields and rows chosen by the section's utility type.
  const val PRESET = "preset"
  // No template: the form starts with only price and currency.
  const val CUSTOM = "custom"

  fun normalize(mode: String?): String =
    when (mode?.trim()?.lowercase()) {
      PRESET -> PRESET
      CUSTOM -> CUSTOM
      else -> LEGACY
    }
}

/** Which optional fixed fields a bill form shows. Bill date, total and currency are always shown. */
data class BillFormLayout(
  val showReadings: Boolean,
  val showConsumption: Boolean,
  val showInvoiceNumber: Boolean,
  val showProvider: Boolean,
  val showNotes: Boolean,
)

data class BillTemplate(val layout: BillFormLayout, val rows: List<BillSheetRow>)

object BillTemplates {
  const val MAX_REMEMBERED_ROWS = 40
  const val MAX_DISMISSED_ROWS = 40
  const val MAX_SHEET_ROWS = 25

  val PRICE_ONLY = BillFormLayout(
    showReadings = false,
    showConsumption = false,
    showInvoiceNumber = false,
    showProvider = false,
    showNotes = false,
  )

  val FULL = BillFormLayout(
    showReadings = true,
    showConsumption = true,
    showInvoiceNumber = true,
    showProvider = true,
    showNotes = true,
  )

  // Labels the bill form generates by itself when a bill has no rows. They must never be
  // remembered as user-added rows.
  private val reservedLabelKeys: Set<String> by lazy {
    setOf(
        "الجهة المصدرة",
        "رقم الفاتورة",
        "تاريخ الفاتورة",
        "القراءة السابقة",
        "القراءة الحالية",
        "كمية الاستهلاك",
        "الإجمالي المستحق",
        "إجمالي الفاتورة",
      )
      .map { normalizeLabel(it) }
      .toSet()
  }

  fun normalizeLabel(label: String): String = label.trim().replace(Regex("\\s+"), " ").lowercase()

  private fun row(label: String, unit: String = "") = BillSheetRow(label = label, value = "", unit = unit)

  fun forType(type: UtilityType): BillTemplate =
    when (type) {
      // Gas: just the price (plus an optional cylinder count / delivery fee row).
      UtilityType.GAS ->
        BillTemplate(PRICE_ONLY, listOf(row("عدد الأسطوانات", "أسطوانة"), row("رسوم التوصيل")))
      UtilityType.ELECTRICITY ->
        BillTemplate(
          FULL,
          listOf(
            row("قيمة الاستهلاك"),
            row("رسوم الخدمة"),
            row("رسوم العداد"),
            row("الضريبة"),
            row("المتأخرات"),
          ),
        )
      UtilityType.WATER ->
        BillTemplate(
          FULL,
          listOf(
            row("قيمة الاستهلاك"),
            row("رسوم الصرف"),
            row("رسوم الخدمة"),
            row("الضريبة"),
            row("المتأخرات"),
          ),
        )
      // Internet: no meter readings, but data usage is still useful.
      UtilityType.INTERNET ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = true,
            showInvoiceNumber = true,
            showProvider = true,
            showNotes = true,
          ),
          listOf(
            row("سعر الباقة"),
            row("ضريبة القيمة المضافة"),
            row("رسوم الراوتر"),
            row("رسوم إضافية"),
          ),
        )
      UtilityType.TELECOM ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = false,
            showInvoiceNumber = true,
            showProvider = true,
            showNotes = true,
          ),
          listOf(
            row("سعر الباقة"),
            row("رسوم الخدمة"),
            row("ضريبة القيمة المضافة"),
            row("رصيد إضافي"),
          ),
        )
      UtilityType.SHOPPING ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = false,
            showInvoiceNumber = false,
            showProvider = true,
            showNotes = true,
          ),
          listOf(row("الخصم"), row("رسوم التوصيل"), row("الضريبة")),
        )
      UtilityType.DINING ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = false,
            showInvoiceNumber = false,
            showProvider = true,
            showNotes = true,
          ),
          listOf(row("رسوم الخدمة"), row("الضريبة"), row("البقشيش"), row("رسوم التوصيل")),
        )
      UtilityType.MAINTENANCE ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = false,
            showInvoiceNumber = true,
            showProvider = true,
            showNotes = true,
          ),
          listOf(row("أجرة العامل"), row("قطع الغيار"), row("رسوم الزيارة")),
        )
      UtilityType.FUEL ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = true,
            showInvoiceNumber = false,
            showProvider = true,
            showNotes = true,
          ),
          listOf(row("سعر اللتر"), row("نوع الوقود")),
        )
      UtilityType.OTHER ->
        BillTemplate(
          BillFormLayout(
            showReadings = false,
            showConsumption = false,
            showInvoiceNumber = true,
            showProvider = true,
            showNotes = true,
          ),
          listOf(row("الضريبة"), row("رسوم إضافية")),
        )
    }

  /**
   * The fixed fields a bill form shows for this category. An existing bill can only ever add
   * fields on top of the template (a field that holds data is never hidden), so editing an old
   * bill still shows everything it had.
   */
  fun layoutFor(category: BillCategory, existingBill: BillRecord?): BillFormLayout {
    val base =
      when (TemplateModes.normalize(category.templateMode)) {
        TemplateModes.PRESET -> forType(UtilityType.fromKey(category.utilityType)).layout
        TemplateModes.CUSTOM -> PRICE_ONLY
        else -> FULL
      }
    if (existingBill == null) return base
    return BillFormLayout(
      showReadings =
        base.showReadings || existingBill.previousReading > 0 || existingBill.currentReading > 0,
      showConsumption = base.showConsumption || existingBill.consumptionValue > 0,
      showInvoiceNumber = base.showInvoiceNumber || existingBill.invoiceNumber.isNotBlank(),
      showProvider = base.showProvider || existingBill.providerName.isNotBlank(),
      showNotes = base.showNotes || existingBill.notes.isNotBlank(),
    )
  }

  /**
   * Rows seeded into the table of a new bill of this section: the template rows (preset mode only)
   * followed by the remembered rows, minus the rows the user dismissed. Values are always empty.
   * Legacy sections get none.
   */
  fun suggestedRows(category: BillCategory): List<BillSheetRow> {
    val dismissed = category.dismissedRows.map { normalizeLabel(it) }.toSet()
    return allSuggestedRows(category).filter { normalizeLabel(it.label) !in dismissed }
  }

  /**
   * Every row that can be suggested for this section, dismissed ones included. The form offers
   * these as tap-to-add chips so a dismissed row can always be restored.
   */
  fun allSuggestedRows(category: BillCategory): List<BillSheetRow> {
    val mode = TemplateModes.normalize(category.templateMode)
    if (mode == TemplateModes.LEGACY) return emptyList()
    val templateRows =
      if (mode == TemplateModes.PRESET) forType(UtilityType.fromKey(category.utilityType)).rows
      else emptyList()
    val seen = HashSet<String>()
    return (templateRows + category.rememberedRows.map { row(it.label, it.unit) })
      .filter { it.label.isNotBlank() && seen.add(normalizeLabel(it.label)) }
  }

  /**
   * Returns the category's remembered rows extended with the labels in [rows] that are not known
   * yet, or null when nothing new has to be stored.
   */
  fun mergeRemembered(category: BillCategory, rows: List<BillSheetRow>): List<BillSheetRow>? {
    val known = HashSet<String>()
    known += reservedLabelKeys
    known += category.rememberedRows.map { normalizeLabel(it.label) }
    if (TemplateModes.normalize(category.templateMode) == TemplateModes.PRESET) {
      known += forType(UtilityType.fromKey(category.utilityType)).rows.map { normalizeLabel(it.label) }
    }
    val additions = mutableListOf<BillSheetRow>()
    for (r in rows) {
      val label = r.label.trim().take(120)
      val key = normalizeLabel(label)
      if (key.isEmpty() || !known.add(key)) continue
      additions += row(label, r.unit.trim().take(30))
    }
    if (additions.isEmpty()) return null
    return (category.rememberedRows + additions).takeLast(MAX_REMEMBERED_ROWS)
  }

  /**
   * Returns the new dismissed-rows list after a bill was saved, or null when nothing changes.
   *
   * [seeded] are the rows that were pre-filled into the form of a new bill. Any of them missing
   * from [saved] (deleted, or left without a value) becomes dismissed. A saved row that has a
   * value and was not already on the bill before ([previousLabelKeys]) is un-dismissed. Rows the
   * user typed fresh are never in [seeded], so deleting them dismisses nothing.
   */
  fun mergeDismissed(
    current: List<String>,
    seeded: List<BillSheetRow>,
    saved: List<BillSheetRow>,
    previousLabelKeys: Set<String>,
  ): List<String>? {
    val result = LinkedHashSet<String>(current)
    val savedKeys = saved.map { normalizeLabel(it.label) }.filter { it.isNotEmpty() }.toSet()
    saved
      .filter { it.value.isNotBlank() }
      .map { normalizeLabel(it.label) }
      .filter { it.isNotEmpty() && it !in previousLabelKeys }
      .forEach { result.remove(it) }
    seeded
      .map { normalizeLabel(it.label) }
      .filter { it.isNotEmpty() && it !in savedKeys }
      .forEach { result.add(it) }
    val updated = result.toList().takeLast(MAX_DISMISSED_ROWS)
    return if (updated == current) null else updated
  }

  /** Clean-up applied before dismissed labels are written: normalized, unique, capped. */
  fun sanitizeDismissed(labels: List<String>): List<String> =
    labels
      .map { normalizeLabel(it).take(120) }
      .filter { it.isNotEmpty() }
      .distinct()
      .takeLast(MAX_DISMISSED_ROWS)

  /** Clean-up applied before remembered rows are written: trimmed, unique, capped, value-less. */
  fun sanitizeRemembered(rows: List<BillSheetRow>): List<BillSheetRow> {
    val seen = HashSet<String>()
    return rows
      .mapNotNull { r ->
        val label = r.label.trim().take(120)
        if (label.isEmpty() || !seen.add(normalizeLabel(label))) null
        else row(label, r.unit.trim().take(30))
      }
      .takeLast(MAX_REMEMBERED_ROWS)
  }

  /**
   * Rows to pre-remember for a brand-new template section: what the user already added in their
   * other sections of the same utility type, so the same extra rows are not typed again.
   */
  fun seedRowsFor(type: UtilityType, existingCategories: List<BillCategory>): List<BillSheetRow> {
    val templateKeys = forType(type).rows.map { normalizeLabel(it.label) }.toSet()
    val seen = HashSet<String>(templateKeys)
    return existingCategories
      .filter { UtilityType.fromKey(it.utilityType) == type }
      .flatMap { it.rememberedRows }
      .filter { it.label.isNotBlank() && seen.add(normalizeLabel(it.label)) }
      .let { sanitizeRemembered(it) }
  }
}

@IgnoreExtraProperties
@Keep
data class BillCategory(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val utilityType: String = "gas",
  val defaultUnit: String = "م³",
  val alertThresholdPercent: Double = 25.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
  // When true, bills of this category show consumption start/end tracking in the bill sheet.
  val trackDuration: Boolean = false,
  // One of TemplateModes. A missing value (documents from older app versions) means LEGACY.
  val templateMode: String = TemplateModes.LEGACY,
  // Rows the user added to bills of this section. Offered again, with empty values, in the next
  // bill form of the same section. Only label and unit are meaningful here.
  val rememberedRows: List<BillSheetRow> = emptyList(),
  // Normalized labels of suggested rows the user dismissed. These are not seeded into new bills
  // of this section any more, but stay available as tap-to-add suggestions.
  val dismissedRows: List<String> = emptyList(),
)

@IgnoreExtraProperties
@Keep
data class BillRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val categoryId: String = "",
  val categoryName: String = "",
  val utilityType: String = "gas",
  val billDateIso: String = "2026-10-01",
  val billingMonth: String = "2026-10",
  val billDateEpochMillis: Long = 1790812800000L,
  val totalAmount: Double = 0.0,
  val currency: String = "ر.س",
  val consumptionValue: Double = 0.0,
  val consumptionUnit: String = "م³",
  val previousReading: Double = 0.0,
  val currentReading: Double = 0.0,
  val invoiceNumber: String = "",
  val providerName: String = "",
  val rawExtractedText: String = "",
  val notes: String = "",
  val sourceType: String = "manual",
  val sheetItems: List<BillSheetRow> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
  // Consumption tracking timestamps (epoch millis). Null means the step has not happened yet:
  // startedAt null = consumption never started, endedAt null = consumption still running.
  val startedAtEpochMillis: Long? = null,
  val endedAtEpochMillis: Long? = null,
)

@IgnoreExtraProperties
@Keep
data class HouseholdBudget(
  @DocumentId val id: String = "",
  val userId: String = "",
  val monthlyLimit: Double = 0.0,
  val currency: String = "ر.س",
  val warningPercent: Double = 80.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}
