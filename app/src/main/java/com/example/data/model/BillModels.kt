package com.example.data.model

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.R

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

@Keep
data class BillSheetRow(
  val label: String = "",
  val value: String = "",
  val unit: String = "",
)

@Entity(tableName = "categories", indices = [Index("userId")])
@Keep
data class BillCategory(
  @PrimaryKey val id: String = "",
  val userId: String = "",
  val name: String = "",
  val utilityType: String = "gas",
  val defaultUnit: String = "م³",
  val alertThresholdPercent: Double = 25.0,
  // Epoch millis, set by the repository when the row is created or updated.
  val createdAt: Long? = null,
  val updatedAt: Long? = null,
  // When true, bills of this category show consumption start/end tracking in the bill sheet.
  val trackDuration: Boolean = false,
)

@Entity(tableName = "bills", indices = [Index("userId"), Index("categoryId")])
@Keep
data class BillRecord(
  @PrimaryKey val id: String = "",
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
  // Epoch millis, set by the repository when the row is created or updated.
  val createdAt: Long? = null,
  val updatedAt: Long? = null,
  // Consumption tracking timestamps (epoch millis). Null means the step has not happened yet:
  // startedAt null = consumption never started, endedAt null = consumption still running.
  val startedAtEpochMillis: Long? = null,
  val endedAtEpochMillis: Long? = null,
)

// One row per owner id; the row id is the owner id.
@Entity(tableName = "budgets")
@Keep
data class HouseholdBudget(
  @PrimaryKey val id: String = "",
  val userId: String = "",
  val monthlyLimit: Double = 0.0,
  val currency: String = "ر.س",
  val warningPercent: Double = 80.0,
  // Epoch millis, set by the repository when the row is created or updated.
  val createdAt: Long? = null,
  val updatedAt: Long? = null,
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}
