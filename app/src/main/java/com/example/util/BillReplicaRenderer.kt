package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.remote.GeminiApiClient
import com.example.data.remote.ReplicaLayoutDto
import com.example.data.remote.ReplicaTextBlockDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** Caches the extracted layout of a bill next to its image (files/bill_images/<billId>.layout.json). */
object BillReplicaStore {
  private fun layoutFile(context: Context, billId: String): File =
    File(File(context.applicationContext.filesDir, "bill_images"), "$billId.layout.json")

  fun load(context: Context, billId: String): ReplicaLayoutDto? {
    if (billId.isBlank()) return null
    return try {
      val file = layoutFile(context, billId)
      if (!file.exists()) return null
      GeminiApiClient.lenientJson.decodeFromString(ReplicaLayoutDto.serializer(), file.readText())
    } catch (e: Exception) {
      null
    }
  }

  fun save(context: Context, billId: String, layout: ReplicaLayoutDto) {
    if (billId.isBlank()) return
    try {
      val file = layoutFile(context, billId)
      file.parentFile?.mkdirs()
      file.writeText(
        GeminiApiClient.lenientJson.encodeToString(ReplicaLayoutDto.serializer(), layout)
      )
    } catch (e: Exception) {
      // Best effort cache only.
    }
  }
}

/**
 * Redraws a bill from a [ReplicaLayoutDto]: same pixel size as the original image, text placed in
 * the same boxes with the closest available fonts. The same drawing code feeds the on-screen
 * bitmap, the JPEG export and the PDF export (vector text).
 */
object BillReplicaRenderer {
  private const val MIN_TEXT_PX = 6f

  fun imageSize(file: File): Pair<Int, Int>? {
    return try {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, options)
      if (options.outWidth > 0 && options.outHeight > 0) {
        Pair(options.outWidth, options.outHeight)
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  fun renderBitmap(layout: ReplicaLayoutDto, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    draw(Canvas(bitmap), layout, width, height)
    return bitmap
  }

  fun draw(canvas: Canvas, layout: ReplicaLayoutDto, width: Int, height: Int) {
    canvas.drawColor(parseColor(layout.backgroundColor, Color.WHITE))

    val linePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, width * 0.0016f)
      }
    val dash = maxOf(4f, width * 0.008f)
    layout.lines.forEach { line ->
      linePaint.color = parseColor(line.color, Color.DKGRAY)
      linePaint.pathEffect = if (line.dashed) DashPathEffect(floatArrayOf(dash, dash), 0f) else null
      canvas.drawLine(
        (line.x1.coerceIn(0.0, 1000.0) / 1000.0 * width).toFloat(),
        (line.y1.coerceIn(0.0, 1000.0) / 1000.0 * height).toFloat(),
        (line.x2.coerceIn(0.0, 1000.0) / 1000.0 * width).toFloat(),
        (line.y2.coerceIn(0.0, 1000.0) / 1000.0 * height).toFloat(),
        linePaint,
      )
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    layout.textBlocks.forEach { block ->
      val text = cleanText(block.text)
      if (text.isEmpty()) return@forEach
      val box = boxPx(block, width, height)
      if (box.width() <= 0f || box.height() <= 0f) return@forEach

      applyBlockStyle(textPaint, block, text, box.width(), box.height())
      textPaint.color = parseColor(block.color, Color.BLACK)
      val x: Float
      when (block.align.lowercase(Locale.US)) {
        "left" -> {
          textPaint.textAlign = Paint.Align.LEFT
          x = box.left
        }
        "center" -> {
          textPaint.textAlign = Paint.Align.CENTER
          x = (box.left + box.right) / 2f
        }
        else -> {
          textPaint.textAlign = Paint.Align.RIGHT
          x = box.right
        }
      }
      val metrics = textPaint.fontMetrics
      val baseline = box.top + (box.height() - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
      canvas.drawText(text, x, baseline, textPaint)
    }
  }

  /** PDF page with the A4 width (595 pt) and the same aspect ratio as the original image. */
  fun buildPdf(layout: ReplicaLayoutDto, width: Int, height: Int): ByteArray {
    val pageWidth = 595
    val scale = pageWidth.toFloat() / width
    val pageHeight = (height * scale).toInt().coerceAtLeast(1)
    val document = PdfDocument()
    try {
      val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
      page.canvas.scale(scale, scale)
      draw(page.canvas, layout, width, height)
      document.finishPage(page)
      val out = ByteArrayOutputStream()
      document.writeTo(out)
      return out.toByteArray()
    } finally {
      document.close()
    }
  }

  /**
   * Word document with the same aspect ratio as the image. Every text block becomes an absolutely
   * positioned frame (page-anchored) with the mapped font, size, weight and alignment.
   */
  fun buildDocx(layout: ReplicaLayoutDto, width: Int, height: Int): ByteArray {
    // 96 dpi: 1 px = 15 twips. Word pages are limited to 22 in (31680 twips) per side.
    val widthTwips = width * 15.0
    val heightTwips = height * 15.0
    val pageScale = minOf(1.0, 31000.0 / maxOf(widthTwips, heightTwips))
    val pageW = (widthTwips * pageScale).toInt().coerceAtLeast(1000)
    val pageH = (heightTwips * pageScale).toInt().coerceAtLeast(1000)
    val twipsPerPx = pageW.toDouble() / width

    val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val body = StringBuilder()
    layout.textBlocks.forEach { block ->
      val text = cleanText(block.text)
      if (text.isEmpty()) return@forEach
      val box = boxPx(block, width, height)
      if (box.width() <= 0f || box.height() <= 0f) return@forEach

      val sizePx = applyBlockStyle(measurePaint, block, text, box.width(), box.height())
      val halfPoints = (sizePx * twipsPerPx / 10.0).roundToInt().coerceIn(6, 400)
      val frameX = (box.left * twipsPerPx).roundToInt()
      val frameY = (box.top * twipsPerPx).roundToInt()
      val frameW = (box.width() * twipsPerPx).roundToInt().coerceAtLeast(60)
      val frameH = (box.height() * twipsPerPx).roundToInt().coerceAtLeast(60)
      val jc =
        when (block.align.lowercase(Locale.US)) {
          "left" -> "left"
          "center" -> "center"
          else -> "right"
        }
      val fontName =
        when (block.font.lowercase(Locale.US)) {
          "serif" -> "Times New Roman"
          "mono" -> "Courier New"
          else -> "Arial"
        }
      val colorHex = String.format(Locale.US, "%06X", parseColor(block.color, Color.BLACK) and 0xFFFFFF)
      val boldXml = if (block.bold) "<w:b/><w:bCs/>" else ""
      val rtlXml = if (hasArabic(text)) "<w:rtl/>" else ""

      body.append(
        """<w:p><w:pPr><w:framePr w:w="$frameW" w:h="$frameH" w:hRule="exact" w:hSpace="0" w:vSpace="0" w:wrap="around" w:vAnchor="page" w:hAnchor="page" w:x="$frameX" w:y="$frameY"/><w:spacing w:before="0" w:after="0" w:line="$frameH" w:lineRule="exact"/><w:jc w:val="$jc"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/>$boldXml<w:color w:val="$colorHex"/><w:sz w:val="$halfPoints"/><w:szCs w:val="$halfPoints"/>$rtlXml</w:rPr><w:t xml:space="preserve">${xmlEscape(text)}</w:t></w:r></w:p>"""
      )
    }

    val backgroundHex =
      String.format(Locale.US, "%06X", parseColor(layout.backgroundColor, Color.WHITE) and 0xFFFFFF)

    val documentXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:background w:color="$backgroundHex"/><w:body>$body<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="20" w:lineRule="exact"/></w:pPr></w:p><w:sectPr><w:pgSz w:w="$pageW" w:h="$pageH"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>"""

    val contentTypes =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/></Types>"""
    val rootRels =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
    val documentRels =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/></Relationships>"""
    val settingsXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:displayBackgroundShape/></w:settings>"""

    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      fun addEntry(name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }
      addEntry("[Content_Types].xml", contentTypes)
      addEntry("_rels/.rels", rootRels)
      addEntry("word/document.xml", documentXml)
      addEntry("word/_rels/document.xml.rels", documentRels)
      addEntry("word/settings.xml", settingsXml)
    }
    return bytes.toByteArray()
  }

  // --- helpers -------------------------------------------------------------------------------

  private fun boxPx(block: ReplicaTextBlockDto, width: Int, height: Int): RectF {
    val x1 = block.xmin.coerceIn(0.0, 1000.0)
    val x2 = block.xmax.coerceIn(0.0, 1000.0)
    val y1 = block.ymin.coerceIn(0.0, 1000.0)
    val y2 = block.ymax.coerceIn(0.0, 1000.0)
    return RectF(
      (minOf(x1, x2) / 1000.0 * width).toFloat(),
      (minOf(y1, y2) / 1000.0 * height).toFloat(),
      (maxOf(x1, x2) / 1000.0 * width).toFloat(),
      (maxOf(y1, y2) / 1000.0 * height).toFloat(),
    )
  }

  /** Picks the closest font and a size that fits the text into its box. Returns the size in px. */
  private fun applyBlockStyle(
    paint: Paint,
    block: ReplicaTextBlockDto,
    text: String,
    boxWidth: Float,
    boxHeight: Float,
  ): Float {
    val family =
      when (block.font.lowercase(Locale.US)) {
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        else -> Typeface.SANS_SERIF
      }
    paint.typeface = Typeface.create(family, if (block.bold) Typeface.BOLD else Typeface.NORMAL)
    var size = (boxHeight * 0.78f).coerceAtLeast(MIN_TEXT_PX)
    paint.textSize = size
    val measured = paint.measureText(text)
    if (measured > boxWidth && measured > 0f) {
      size = (size * boxWidth / measured).coerceAtLeast(MIN_TEXT_PX)
      paint.textSize = size
    }
    return size
  }

  private fun cleanText(raw: String): String = raw.replace('\n', ' ').replace('\r', ' ').trim()

  private fun parseColor(hex: String, fallback: Int): Int {
    val value = hex.trim()
    if (value.isEmpty()) return fallback
    return try {
      Color.parseColor(if (value.startsWith("#")) value else "#$value")
    } catch (e: Exception) {
      fallback
    }
  }

  private fun hasArabic(text: String): Boolean =
    text.any {
      it.code in 0x0600..0x06FF ||
        it.code in 0x0750..0x077F ||
        it.code in 0xFB50..0xFDFF ||
        it.code in 0xFE70..0xFEFF
    }

  private fun xmlEscape(text: String): String =
    buildString {
      for (ch in text) {
        when (ch) {
          '&' -> append("&amp;")
          '<' -> append("&lt;")
          '>' -> append("&gt;")
          '"' -> append("&quot;")
          '\'' -> append("&apos;")
          else -> if (ch.code >= 0x20) append(ch)
        }
      }
    }
}

/** Saves replica files into the phone: JPEG to Pictures/HomeBill, PDF and Word to Download/HomeBill. */
object BillReplicaExporter {
  private const val FOLDER = "HomeBill"
  private const val MIME_DOCX =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

  fun saveJpeg(context: Context, layout: ReplicaLayoutDto, width: Int, height: Int, label: String): Boolean =
    try {
      val bitmap = BillReplicaRenderer.renderBitmap(layout, width, height)
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
      saveBytes(context, out.toByteArray(), fileName(label, "jpg"), "image/jpeg", isImage = true)
    } catch (e: Exception) {
      false
    }

  fun savePdf(context: Context, layout: ReplicaLayoutDto, width: Int, height: Int, label: String): Boolean =
    try {
      val bytes = BillReplicaRenderer.buildPdf(layout, width, height)
      saveBytes(context, bytes, fileName(label, "pdf"), "application/pdf", isImage = false)
    } catch (e: Exception) {
      false
    }

  fun saveDocx(context: Context, layout: ReplicaLayoutDto, width: Int, height: Int, label: String): Boolean =
    try {
      val bytes = BillReplicaRenderer.buildDocx(layout, width, height)
      saveBytes(context, bytes, fileName(label, "docx"), MIME_DOCX, isImage = false)
    } catch (e: Exception) {
      false
    }

  private fun fileName(label: String, extension: String): String {
    val safeLabel =
      label
        .trim()
        .replace(' ', '_')
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(40)
        .ifBlank { "bill" }
    return "HomeBill_${safeLabel}_replica_${System.currentTimeMillis()}.$extension"
  }

  private fun saveBytes(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
    isImage: Boolean,
  ): Boolean {
    val appContext = context.applicationContext
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = appContext.contentResolver
        val baseDir =
          if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        val values =
          ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
          }
        val collection =
          if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
          else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return false
        val written =
          resolver.openOutputStream(uri)?.use { stream ->
            stream.write(bytes)
            true
          } ?: false
        if (!written) {
          resolver.delete(uri, null, null)
          return false
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
      } else {
        @Suppress("DEPRECATION")
        val publicDir =
          Environment.getExternalStoragePublicDirectory(
            if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
          )
        val dir = File(publicDir, FOLDER)
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = File(dir, fileName)
        FileOutputStream(target).use { it.write(bytes) }
        MediaScannerConnection.scanFile(
          appContext,
          arrayOf(target.absolutePath),
          arrayOf(mimeType),
          null,
        )
        true
      }
    } catch (e: Exception) {
      false
    }
  }
}
