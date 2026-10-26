package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.domain.BillComparisonResult
import java.io.File

object BillSheetExporter {

  fun shareSingleBillCsv(
    context: Context,
    bill: BillRecord,
    comparison: BillComparisonResult?,
  ) {
    val sb = StringBuilder()
    // UTF-8 BOM so Microsoft Excel & Google Sheets open Arabic characters cleanly
    sb.append('\uFEFF')
    sb.appendLine("ورقة تفاصيل الفاتورة (Sheet),${escapeCsv(bill.categoryName)}")
    sb.appendLine("تاريخ الفاتورة,${escapeCsv(bill.billDateIso)}")
    sb.appendLine("شهر الفوترة,${escapeCsv(bill.billingMonth)}")
    sb.appendLine("القيمة الإجمالية,${DateAndFormatUtils.formatMoney(bill.totalAmount)},${escapeCsv(bill.currency)}")
    sb.appendLine("كمية الاستهلاك,${DateAndFormatUtils.formatNumber(bill.consumptionValue)},${escapeCsv(bill.consumptionUnit)}")
    if (bill.previousReading > 0.0 || bill.currentReading > 0.0) {
      sb.appendLine("القراءة السابقة,${DateAndFormatUtils.formatNumber(bill.previousReading)},${escapeCsv(bill.consumptionUnit)}")
      sb.appendLine("القراءة الحالية,${DateAndFormatUtils.formatNumber(bill.currentReading)},${escapeCsv(bill.consumptionUnit)}")
    }
    if (bill.invoiceNumber.isNotBlank()) {
      sb.appendLine("رقم الفاتورة,${escapeCsv(bill.invoiceNumber)}")
    }
    if (bill.providerName.isNotBlank()) {
      sb.appendLine("الجهة المصدرة,${escapeCsv(bill.providerName)}")
    }
    if (comparison?.previousBill != null) {
      val pctStr = comparison.amountChangePercent?.let { String.format(java.util.Locale.US, "%+.1f%%", it) } ?: "—"
      sb.appendLine("المقارنة مع الفاتورة السابقة (${comparison.previousBill.billDateIso}),$pctStr")
    }
    sb.appendLine()
    sb.appendLine("البند / التفصيل,القيمة,الوحدة")
    for (row in bill.sheetItems) {
      sb.appendLine("${escapeCsv(row.label)},${escapeCsv(row.value)},${escapeCsv(row.unit)}")
    }
    if (bill.rawExtractedText.isNotBlank()) {
      sb.appendLine()
      sb.appendLine("النص المستخرج من الفاتورة,${escapeCsv(bill.rawExtractedText)}")
    }

    val safeDate = bill.billDateIso.replace(Regex("[^0-9\\-]"), "")
    val fileName = "bill_sheet_${safeDate}.csv"
    writeAndShareCsv(context, fileName, sb.toString())
  }

  fun shareCategoryLedgerCsv(
    context: Context,
    category: BillCategory,
    bills: List<BillRecord>,
  ) {
    val sb = StringBuilder()
    sb.append('\uFEFF')
    sb.appendLine("كشف فواتير القسم,${escapeCsv(category.name)}")
    sb.appendLine("التاريخ,الشهر,المبلغ الإجمالي,العملة,الاستهلاك,الوحدة,القراءة السابقة,القراءة الحالية,رقم الفاتورة,الجهة المصدرة,ملاحظات")
    for (bill in bills.sortedByDescending { it.billDateEpochMillis }) {
      sb.appendLine(
        listOf(
          escapeCsv(bill.billDateIso),
          escapeCsv(bill.billingMonth),
          DateAndFormatUtils.formatMoney(bill.totalAmount),
          escapeCsv(bill.currency),
          DateAndFormatUtils.formatNumber(bill.consumptionValue),
          escapeCsv(bill.consumptionUnit),
          DateAndFormatUtils.formatNumber(bill.previousReading),
          DateAndFormatUtils.formatNumber(bill.currentReading),
          escapeCsv(bill.invoiceNumber),
          escapeCsv(bill.providerName),
          escapeCsv(bill.notes),
        ).joinToString(",")
      )
    }
    val fileName = "category_bills_${category.utilityType}.csv"
    writeAndShareCsv(context, fileName, sb.toString())
  }

  private fun writeAndShareCsv(context: Context, fileName: String, content: String) {
    val file = File(context.cacheDir, fileName)
    file.writeText(content, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent.createChooser(sendIntent, context.getString(R.string.share_sheet_chooser_title)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(chooser)
  }

  private fun escapeCsv(value: String): String {
    val cleaned = value.replace("\r\n", " ").replace("\n", " ")
    return if (cleaned.contains(',') || cleaned.contains('"')) {
      "\"${cleaned.replace("\"", "\"\"")}\""
    } else {
      cleaned
    }
  }
}
