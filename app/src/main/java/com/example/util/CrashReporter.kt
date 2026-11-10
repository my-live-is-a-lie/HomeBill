package com.example.util

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/** One saved crash report. [id] is the report's file name inside the private reports folder. */
data class CrashReport(
  val id: String,
  val timestampMillis: Long,
  // First line of the exception, for list rows.
  val title: String,
  // Full report text: time, thread, exception, device info and the whole stack trace.
  val text: String,
)

/**
 * Local-only crash reporting. An uncaught exception is written to the app's private storage and
 * the app then dies as usual; nothing is sent anywhere. On the next launch the newest report is
 * shown once (see [consumePendingReportId]) and every report stays in the history until deleted.
 */
object CrashReporter {
  private const val DIR_NAME = "crash_reports"
  private const val PENDING_FILE = "pending_report.txt"
  private const val FILE_PREFIX = "crash_"
  private const val FILE_SUFFIX = ".txt"
  private const val MAX_REPORTS = 30
  private const val MAX_TRACE_CHARS = 60_000

  @Volatile private var reportsDir: File? = null
  @Volatile private var appVersion: String = "unknown"
  private var handlerInstalled = false

  /** Safe to call more than once; the handler itself is only registered the first time. */
  @Synchronized
  fun install(context: Context) {
    val appContext = context.applicationContext ?: context
    reportsDir = File(appContext.filesDir, DIR_NAME)
    appVersion = readAppVersion(appContext)
    if (handlerInstalled) return
    handlerInstalled = true
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      // Never let the reporter itself throw: the original handler must always run.
      try {
        recordCrash(thread, throwable)
      } catch (_: Throwable) {}
      if (previous != null) {
        previous.uncaughtException(thread, throwable)
      } else {
        Process.killProcess(Process.myPid())
        exitProcess(10)
      }
    }
  }

  /** Writes a report for [throwable] and marks it as the one to show on the next launch. */
  fun recordCrash(thread: Thread, throwable: Throwable) {
    val dir = reportsDir ?: return
    dir.mkdirs()
    val now = System.currentTimeMillis()
    var file = File(dir, "$FILE_PREFIX$now$FILE_SUFFIX")
    var attempt = 1
    while (file.exists()) {
      file = File(dir, "$FILE_PREFIX${now}_$attempt$FILE_SUFFIX")
      attempt++
    }
    file.writeText(buildReportText(now, thread, throwable))
    File(dir, PENDING_FILE).writeText(file.name)
    pruneOldReports(dir)
  }

  /**
   * Records a failure that did not crash the app (for example a document scanner that failed
   * to open). The report appears in the history list, but it is NOT shown automatically on the
   * next launch, so a non-fatal failure never blocks opening the app.
   */
  fun recordNonFatal(context: Context, label: String, throwable: Throwable) {
    val dir = dirFor(context)
    dir.mkdirs()
    val now = System.currentTimeMillis()
    var file = File(dir, "$FILE_PREFIX$now$FILE_SUFFIX")
    var attempt = 1
    while (file.exists()) {
      file = File(dir, "$FILE_PREFIX${now}_$attempt$FILE_SUFFIX")
      attempt++
    }
    file.writeText(buildReportText(now, Thread.currentThread(), throwable, failureLabel = label))
    pruneOldReports(dir)
  }

  /** Returns the id of a report that has not been shown yet, and clears the marker. */
  fun consumePendingReportId(context: Context): String? =
    runCatching {
        val dir = dirFor(context)
        val marker = File(dir, PENDING_FILE)
        if (!marker.exists()) return@runCatching null
        val id = marker.readText().trim()
        marker.delete()
        if (id.isNotEmpty() && File(dir, File(id).name).exists()) File(id).name else null
      }
      .getOrNull()

  /** All saved reports, newest first. */
  fun listReports(context: Context): List<CrashReport> =
    runCatching {
        val dir = dirFor(context)
        dir
          .listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX) }
          .orEmpty()
          .mapNotNull { readReport(it) }
          .sortedByDescending { it.timestampMillis }
      }
      .getOrDefault(emptyList())

  fun getReport(context: Context, id: String): CrashReport? =
    runCatching { readReport(File(dirFor(context), File(id).name)) }.getOrNull()

  fun deleteReport(context: Context, id: String) {
    runCatching {
      val dir = dirFor(context)
      File(dir, File(id).name).delete()
      val marker = File(dir, PENDING_FILE)
      if (marker.exists() && marker.readText().trim() == File(id).name) marker.delete()
    }
  }

  fun clearAll(context: Context) {
    runCatching { dirFor(context).listFiles()?.forEach { it.delete() } }
  }

  private fun dirFor(context: Context): File =
    File((context.applicationContext ?: context).filesDir, DIR_NAME)

  private fun readReport(file: File): CrashReport? {
    if (!file.isFile) return null
    val text = file.readText()
    val stamp =
      file.name
        .removePrefix(FILE_PREFIX)
        .removeSuffix(FILE_SUFFIX)
        .substringBefore('_')
        .toLongOrNull() ?: file.lastModified()
    val exception =
      text.lineSequence().firstOrNull { it.startsWith("Exception: ") }?.removePrefix("Exception: ")
    val failure =
      text.lineSequence().firstOrNull { it.startsWith("Failure: ") }?.removePrefix("Failure: ")
    val title =
      when {
        failure != null && exception != null -> "$failure: $exception"
        exception != null -> exception
        else -> file.name
      }
    return CrashReport(id = file.name, timestampMillis = stamp, title = title, text = text)
  }

  private fun pruneOldReports(dir: File) {
    val files =
      dir
        .listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .sortedByDescending { it.lastModified() }
    files.drop(MAX_REPORTS).forEach { it.delete() }
  }

  private fun buildReportText(
    timeMillis: Long,
    thread: Thread,
    throwable: Throwable,
    failureLabel: String? = null,
  ): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(timeMillis))
    val trace =
      StringWriter()
        .also { writer -> PrintWriter(writer).use { throwable.printStackTrace(it) } }
        .toString()
        .take(MAX_TRACE_CHARS)
    return buildString {
      appendLine(if (failureLabel == null) "HomeBill crash report" else "HomeBill failure report")
      appendLine("Time: $time")
      if (failureLabel != null) appendLine("Failure: $failureLabel")
      appendLine("Thread: ${thread.name}")
      appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
      appendLine("App version: $appVersion")
      appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
      appendLine()
      appendLine("Stack trace:")
      append(trace)
    }
  }

  @Suppress("DEPRECATION")
  private fun readAppVersion(context: Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
          else info.versionCode.toLong()
        "${info.versionName} ($code)"
      }
      .getOrDefault("unknown")
}
