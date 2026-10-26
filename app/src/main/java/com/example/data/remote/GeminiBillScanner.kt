package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.model.BillSheetRow
import com.example.data.model.UtilityType
import com.example.util.DateAndFormatUtils

data class ScannedBillDraft(
  val detectedUtilityType: UtilityType,
  val suggestedCategoryName: String,
  val classificationReason: String = "",
  val storeName: String,
  val billDateIso: String,
  val billingMonth: String,
  val billDateEpochMillis: Long,
  val totalAmount: Double,
  val taxAmount: Double = 0.0,
  val subtotalAmount: Double = 0.0,
  val currency: String,
  val consumptionValue: Double,
  val consumptionUnit: String,
  val previousReading: Double,
  val currentReading: Double,
  val invoiceNumber: String,
  val providerName: String,
  val rawExtractedText: String,
  val sheetRows: List<BillSheetRow>,
  val structuredJsonPreview: String = "",
)

/**
 * High-level bill scanner service backed by [GeminiApiClient].
 * Accepts a captured image ([Bitmap] or [Uri]) and maps the structured JSON response
 * (`date`, `storeName`, `totalAmount`, `expenseCategory`, `classificationReason`, and `sheetRows`)
 * into an automatically classified [ScannedBillDraft].
 */
class GeminiBillScanner(
  private val apiClient: GeminiApiClient = GeminiApiClient.default,
) {

  /**
   * Sends a captured [Bitmap] directly to Gemini and returns the raw structured JSON DTO.
   */
  suspend fun extractStructuredBillJson(bitmap: Bitmap): Result<ExtractedBillDto> =
    apiClient.extractBillFromBitmap(bitmap).map { it.data }

  /**
   * Sends a captured image [Uri] to Gemini and returns the raw structured JSON DTO.
   */
  suspend fun extractStructuredBillJson(context: Context, imageUri: Uri): Result<ExtractedBillDto> =
    apiClient.extractBillFromUri(context, imageUri).map { it.data }

  /**
   * Scans a captured [Bitmap] and maps the structured JSON result into an editable [ScannedBillDraft].
   */
  suspend fun scanBillBitmap(bitmap: Bitmap): Result<ScannedBillDraft> =
    apiClient.extractBillFromBitmap(bitmap).map { result ->
      mapJsonResultToDraft(result)
    }

  /**
   * Scans a captured or picked image [Uri] and maps the structured JSON result into an editable [ScannedBillDraft].
   */
  suspend fun scanBillImage(context: Context, imageUri: Uri): Result<ScannedBillDraft> =
    apiClient.extractBillFromUri(context, imageUri).map { result ->
      mapJsonResultToDraft(result)
    }

  /**
   * Re-classifies an existing [ScannedBillDraft] using Gemini based on its `storeName`
   * and extracted/edited content (`sheetRows` + `rawExtractedText`).
   */
  suspend fun classifyDraftExpense(draft: ScannedBillDraft): Result<ScannedBillDraft> {
    val storeSignal = draft.storeName.trim().ifBlank { draft.providerName.trim() }
    val contentSummary = buildString {
      if (draft.sheetRows.isNotEmpty()) {
        append(
          draft.sheetRows.joinToString(separator = " | ") { row ->
            listOf(row.label, row.value, row.unit).filter { it.isNotBlank() }.joinToString(" ")
          }
        )
      }
      if (draft.rawExtractedText.isNotBlank()) {
        if (isNotEmpty()) append(" — ")
        append(draft.rawExtractedText.take(1200))
      }
    }

    return apiClient
      .classifyExpenseFromStoreOrContent(storeName = storeSignal, contentText = contentSummary)
      .map { classification ->
        val resolvedType =
          UtilityType.fromKey(classification.expenseCategory).let { fromGemini ->
            if (fromGemini != UtilityType.OTHER) {
              fromGemini
            } else {
              inferExpenseTypeFromSignals(
                storeName = storeSignal,
                rawText = draft.rawExtractedText,
                rows = draft.sheetRows,
                consumptionUnit = draft.consumptionUnit,
              )?.first ?: UtilityType.OTHER
            }
          }

        val resolvedCategoryName =
          classification.suggestedCategoryName
            .trim()
            .takeIf { it.isNotBlank() && it != UtilityType.OTHER.defaultCategoryName }
            ?.take(80)
            ?: resolvedType.defaultCategoryName

        val previousDefaultUnit = draft.detectedUtilityType.defaultUnit
        val resolvedUnit =
          when {
            draft.consumptionUnit.isBlank() ||
              draft.consumptionUnit == "وحدة" ||
              draft.consumptionUnit == previousDefaultUnit ->
              classification.defaultUnit.trim().ifBlank { resolvedType.defaultUnit }
            else -> draft.consumptionUnit
          }

        draft.copy(
          detectedUtilityType = resolvedType,
          suggestedCategoryName = resolvedCategoryName,
          classificationReason = classification.classificationReason.trim(),
          consumptionUnit = resolvedUnit,
        )
      }
  }

  internal fun mapJsonResultToDraft(result: ScannedBillJsonResult): ScannedBillDraft {
    val dto = result.data
    val cleanStoreName = dto.resolvedStoreName.take(120)

    val prelimRows =
      dto.sheetRows
        .filter { it.label.isNotBlank() }
        .map {
          BillSheetRow(
            label = it.label.trim().take(120),
            value = it.value.trim().take(200),
            unit = it.unit.trim().take(30),
          )
        }

    val geminiType = UtilityType.fromKey(dto.resolvedExpenseType)
    val fallbackSignal =
      if (geminiType == UtilityType.OTHER) {
        inferExpenseTypeFromSignals(
          storeName = cleanStoreName,
          rawText = dto.rawExtractedText,
          rows = prelimRows,
          consumptionUnit = dto.consumptionUnit,
        )
      } else {
        null
      }

    val utilityType = fallbackSignal?.first ?: geminiType
    val resolvedReason =
      when {
        dto.classificationReason.trim().isNotBlank() -> dto.classificationReason.trim().take(200)
        fallbackSignal != null -> fallbackSignal.second
        utilityType != UtilityType.OTHER ->
          buildDefaultReason(utilityType = utilityType, storeName = cleanStoreName)
        else -> ""
      }

    val normalizedIso = DateAndFormatUtils.normalizeIsoDate(dto.resolvedDateIso)
    val epochMillis = DateAndFormatUtils.isoDateToEpochMillis(normalizedIso)
    val billingMonth = DateAndFormatUtils.isoDateToBillingMonth(normalizedIso)

    val rawSuggested = dto.suggestedCategoryName.trim()
    val resolvedSuggestedCategory =
      when {
        rawSuggested.isNotBlank() && rawSuggested != UtilityType.OTHER.defaultCategoryName ->
          rawSuggested.take(80)
        utilityType != UtilityType.OTHER -> utilityType.defaultCategoryName
        cleanStoreName.isNotBlank() -> "فاتورة ${cleanStoreName.take(40)}"
        else -> utilityType.defaultCategoryName
      }

    val computedConsumption =
      when {
        dto.consumptionValue > 0.0 -> dto.consumptionValue
        dto.currentReading > dto.previousReading && dto.previousReading > 0.0 ->
          dto.currentReading - dto.previousReading
        else -> 0.0
      }

    val rawUnit = dto.consumptionUnit.trim()
    val unit =
      if (rawUnit.isBlank() || (rawUnit == "وحدة" && utilityType != UtilityType.OTHER)) {
        utilityType.defaultUnit
      } else {
        rawUnit
      }
    val currency = dto.currency.trim().ifBlank { "ر.س" }
    val cleanTotal = dto.totalAmount.coerceAtLeast(0.0)
    val cleanTax = dto.taxAmount.coerceAtLeast(0.0)
    val cleanSubtotal = dto.subtotalAmount.coerceAtLeast(0.0)

    val extractedRows = prelimRows.toMutableList()

    // Ensure core structured fields (storeName, date, taxAmount, totalAmount) are always represented in the Sheet
    if (extractedRows.isEmpty()) {
      if (cleanStoreName.isNotBlank()) {
        extractedRows.add(BillSheetRow("المتجر / الجهة المصدرة", cleanStoreName, ""))
      }
      if (dto.invoiceNumber.isNotBlank()) {
        extractedRows.add(BillSheetRow("رقم الفاتورة / الإيصال", dto.invoiceNumber.trim(), ""))
      }
      extractedRows.add(BillSheetRow("تاريخ الفاتورة", normalizedIso, ""))
      if (dto.previousReading > 0.0) {
        extractedRows.add(
          BillSheetRow("القراءة السابقة", DateAndFormatUtils.formatNumber(dto.previousReading), unit)
        )
      }
      if (dto.currentReading > 0.0) {
        extractedRows.add(
          BillSheetRow("القراءة الحالية", DateAndFormatUtils.formatNumber(dto.currentReading), unit)
        )
      }
      if (computedConsumption > 0.0) {
        extractedRows.add(
          BillSheetRow("كمية الاستهلاك", DateAndFormatUtils.formatNumber(computedConsumption), unit)
        )
      }
      if (cleanSubtotal > 0.0) {
        extractedRows.add(
          BillSheetRow("المبلغ قبل الضريبة", DateAndFormatUtils.formatMoney(cleanSubtotal), currency)
        )
      }
      if (cleanTax > 0.0) {
        extractedRows.add(
          BillSheetRow("ضريبة القيمة المضافة", DateAndFormatUtils.formatMoney(cleanTax), currency)
        )
      }
      extractedRows.add(
        BillSheetRow("إجمالي المبلغ المستحق", DateAndFormatUtils.formatMoney(cleanTotal), currency)
      )
    }

    return ScannedBillDraft(
      detectedUtilityType = utilityType,
      suggestedCategoryName = resolvedSuggestedCategory,
      classificationReason = resolvedReason,
      storeName = cleanStoreName,
      billDateIso = normalizedIso,
      billingMonth = billingMonth,
      billDateEpochMillis = epochMillis,
      totalAmount = cleanTotal,
      taxAmount = cleanTax,
      subtotalAmount = cleanSubtotal,
      currency = currency,
      consumptionValue = computedConsumption.coerceAtLeast(0.0),
      consumptionUnit = unit,
      previousReading = dto.previousReading.coerceAtLeast(0.0),
      currentReading = dto.currentReading.coerceAtLeast(0.0),
      invoiceNumber = dto.invoiceNumber.trim(),
      providerName = cleanStoreName,
      rawExtractedText = dto.rawExtractedText.trim(),
      sheetRows = extractedRows.take(25),
      structuredJsonPreview = result.formattedJson,
    )
  }

  companion object {
    /**
     * Secondary signal analyzer used only when a fallback plain-JSON response returns `"other"`
     * despite unambiguous store-name or content indicators.
     */
    internal fun inferExpenseTypeFromSignals(
      storeName: String,
      rawText: String,
      rows: List<BillSheetRow>,
      consumptionUnit: String,
    ): Pair<UtilityType, String>? {
      val rowCorpus = rows.joinToString(" ") { "${it.label} ${it.value} ${it.unit}" }
      val corpus = "$storeName $rawText $rowCorpus $consumptionUnit".lowercase()

      fun matchesAny(vararg keywords: String): String? =
        keywords.firstOrNull { kw -> corpus.contains(kw.lowercase()) }

      matchesAny(
          "الشركة السعودية للكهرباء",
          "شركة الكهرباء",
          "saudi electricity",
          "ك.و.س",
          "kwh",
          "كيلوواط",
          "عداد الكهرباء",
        )
        ?.let { hit ->
          return UtilityType.ELECTRICITY to
            "تم التصنيف تلقائياً كـ كهرباء استناداً إلى «$hit» في بيانات الفاتورة"
        }

      matchesAny(
          "شركة المياه الوطنية",
          "مصلحة المياه",
          "national water",
          "عداد المياه",
          "الصرف الصحي",
          "صهريج مياه",
        )
        ?.let { hit ->
          return UtilityType.WATER to
            "تم التصنيف تلقائياً كـ مياه استناداً إلى «$hit» في بيانات الفاتورة"
        }

      matchesAny(
          "الغاز والتصنيع",
          "غازكو",
          "gasco",
          "تاون جاس",
          "ناتجاس",
          "أسطوانة غاز",
          "اسطوانة غاز",
          "خزان الغاز",
        )
        ?.let { hit ->
          return UtilityType.GAS to
            "تم التصنيف تلقائياً كـ غاز استناداً إلى «$hit» في بيانات الفاتورة"
        }

      matchesAny(
          "ألياف بصرية",
          "الياف بصرية",
          "فايبر",
          "fiber",
          "ftth",
          "اشتراك إنترنت",
          "اشتراك انترنت",
          "broadband",
        )
        ?.let { hit ->
          return UtilityType.INTERNET to
            "تم التصنيف تلقائياً كـ إنترنت استناداً إلى «$hit» في بيانات الفاتورة"
        }

      matchesAny(
          "stc",
          "موبايلي",
          "mobily",
          "زين",
          "zain",
          "فودافون",
          "vodafone",
          "أورنج",
          "orange",
          "باقة مفوتر",
          "شريحة اتصال",
        )
        ?.let { hit ->
          return UtilityType.TELECOM to
            "تم التصنيف تلقائياً كـ اتصالات استناداً إلى «$hit» في بيانات الفاتورة"
        }

      matchesAny(
          "الدريس",
          "ساسكو",
          "aldrees",
          "sasco",
          "بترومين",
          "petromin",
          "بنزين 91",
          "بنزين 95",
          "ديزل",
          "محطة وقود",
          "أوبر",
          "uber",
          "careem",
        )
        ?.let { hit ->
          return UtilityType.FUEL to
            "تم التصنيف تلقائياً كـ وقود ومواصلات استناداً إلى «$hit»"
        }

      matchesAny(
          "البيك",
          "albaik",
          "ستاربكس",
          "starbucks",
          "دانكن",
          "dunkin",
          "ماكدونالدز",
          "mcdonald",
          "برجر",
          "مطعم",
          "مقهى",
          "كافيه",
          "هنقرستيشن",
          "hungerstation",
          "جاهز",
          "jahez",
        )
        ?.let { hit ->
          return UtilityType.DINING to
            "تم التصنيف تلقائياً كـ مطاعم وأغذية استناداً إلى «$hit»"
        }

      matchesAny(
          "صيانة",
          "سباكة",
          "تكييف",
          "إصلاح",
          "اصلاح",
          "قطع غيار",
          "ورشة",
        )
        ?.let { hit ->
          return UtilityType.MAINTENANCE to
            "تم التصنيف تلقائياً كـ صيانة وخدمات استناداً إلى «$hit»"
        }

      matchesAny(
          "التميمي",
          "tamimi",
          "بنده",
          "panda",
          "العثيم",
          "othaim",
          "الدانوب",
          "danube",
          "كارفور",
          "carrefour",
          "لولو",
          "lulu",
          "جرير",
          "jarir",
          "إكسترا",
          "extra",
          "ساكو",
          "saco",
          "إيكيا",
          "ikea",
          "أمازون",
          "amazon",
          "نون",
          "noon",
          "النهدي",
          "nahdi",
          "سوبرماركت",
          "هايبر",
          "أسواق",
          "اسواق",
          "تموينات",
          "بقالة",
        )
        ?.let { hit ->
          return UtilityType.SHOPPING to
            "تم التصنيف تلقائياً كـ تسوق ومقاضي استناداً إلى اسم المتجر «${storeName.ifBlank { hit }}»"
        }

      return null
    }

    private fun buildDefaultReason(utilityType: UtilityType, storeName: String): String {
      return if (storeName.isNotBlank()) {
        "تم التصنيف تلقائياً بواسطة Gemini بناءً على اسم الجهة «$storeName» ومحتوى الفاتورة"
      } else {
        "تم التصنيف تلقائياً بواسطة Gemini (${utilityType.defaultCategoryName}) من محتوى الفاتورة"
      }
    }
  }
}
