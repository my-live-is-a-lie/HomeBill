package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.HouseholdBudget
import org.json.JSONArray
import org.json.JSONObject

/** Stores the details table of a bill as one JSON array in a single column. */
class BillTypeConverters {

  @TypeConverter
  fun sheetRowsToJson(rows: List<BillSheetRow>?): String {
    val array = JSONArray()
    rows.orEmpty().forEach { row ->
      array.put(
        JSONObject().apply {
          put("label", row.label)
          put("value", row.value)
          put("unit", row.unit)
        }
      )
    }
    return array.toString()
  }

  @TypeConverter
  fun stringListToJson(values: List<String>?): String {
    val array = JSONArray()
    values.orEmpty().forEach { array.put(it) }
    return array.toString()
  }

  @TypeConverter
  fun jsonToStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index -> array.optString(index, "") }
      }
      .getOrDefault(emptyList())
  }

  @TypeConverter
  fun jsonToSheetRows(json: String?): List<BillSheetRow> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
          val obj = array.getJSONObject(index)
          BillSheetRow(
            label = obj.optString("label", ""),
            value = obj.optString("value", ""),
            unit = obj.optString("unit", ""),
          )
        }
      }
      .getOrDefault(emptyList())
  }
}

@Database(
  entities = [BillCategory::class, BillRecord::class, HouseholdBudget::class],
  version = 2,
  exportSchema = false,
)
@TypeConverters(BillTypeConverters::class)
abstract class BillDatabase : RoomDatabase() {

  abstract fun billDao(): BillDao

  companion object {
    private const val DATABASE_NAME = "homebill.db"

    // Version 1 shipped without the template columns; add them without losing stored bills.
    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE categories ADD COLUMN templateMode TEXT NOT NULL DEFAULT 'legacy'"
          )
          db.execSQL(
            "ALTER TABLE categories ADD COLUMN rememberedRows TEXT NOT NULL DEFAULT '[]'"
          )
          db.execSQL(
            "ALTER TABLE categories ADD COLUMN dismissedRows TEXT NOT NULL DEFAULT '[]'"
          )
        }
      }

    @Volatile private var instance: BillDatabase? = null

    fun getInstance(context: Context): BillDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                BillDatabase::class.java,
                DATABASE_NAME,
              )
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { instance = it }
        }
  }
}
