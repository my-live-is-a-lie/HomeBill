package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashReporterTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    CrashReporter.install(context)
    CrashReporter.clearAll(context)
  }

  private fun crash(message: String) =
    CrashReporter.recordCrash(
      Thread.currentThread(),
      RuntimeException(message, IllegalStateException("root cause")),
    )

  @Test
  fun recordCrash_savesFullReport() {
    crash("boom")

    val report = CrashReporter.listReports(context).single()
    assertTrue(report.text.contains("Time: "))
    assertTrue(report.text.contains("Thread: ${Thread.currentThread().name}"))
    assertTrue(report.text.contains("Exception: java.lang.RuntimeException: boom"))
    assertTrue(report.text.contains("App version: "))
    assertTrue(report.text.contains("Android: "))
    assertTrue(report.text.contains("Device: "))
    assertTrue(report.text.contains("Caused by: java.lang.IllegalStateException: root cause"))
    assertTrue(report.text.contains("CrashReporterTest"))
    assertEquals("java.lang.RuntimeException: boom", report.title)
  }

  @Test
  fun pendingReport_isReturnedOnlyOnce() {
    crash("first")
    val id = CrashReporter.consumePendingReportId(context)
    assertNotNull(id)
    assertNotNull(CrashReporter.getReport(context, id!!))
    assertNull(CrashReporter.consumePendingReportId(context))
    // Showing it once does not remove it from the history.
    assertEquals(1, CrashReporter.listReports(context).size)
  }

  @Test
  fun listReports_isNewestFirst_andDeleteAndClearWork() {
    crash("older")
    Thread.sleep(5)
    crash("newer")

    val reports = CrashReporter.listReports(context)
    assertEquals(2, reports.size)
    assertTrue(reports.first().title.endsWith("newer"))

    CrashReporter.deleteReport(context, reports.first().id)
    assertEquals(1, CrashReporter.listReports(context).size)

    CrashReporter.clearAll(context)
    assertTrue(CrashReporter.listReports(context).isEmpty())
    assertNull(CrashReporter.consumePendingReportId(context))
  }

  @Test
  fun deleteReport_ignoresPathTricks() {
    crash("keep me")
    CrashReporter.deleteReport(context, "../../databases/homebill.db")
    assertEquals(1, CrashReporter.listReports(context).size)
  }
}
