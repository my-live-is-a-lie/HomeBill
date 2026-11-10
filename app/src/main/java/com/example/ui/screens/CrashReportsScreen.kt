package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.CrashReport
import com.example.util.CrashReporter
import java.text.DateFormat
import java.util.Date

/**
 * Crash report viewer. With [initialReportId] it opens that report directly (used right after a
 * crash); with null it opens the history list (used from Settings). Reports never leave the
 * device unless the user taps Share.
 */
@Composable
fun CrashReportsScreen(initialReportId: String?, onClose: () -> Unit) {
  val context = LocalContext.current
  var reports by remember { mutableStateOf(CrashReporter.listReports(context)) }
  var selectedId by remember { mutableStateOf(initialReportId) }
  val openedDirectly = initialReportId != null
  val selected = selectedId?.let { id -> reports.firstOrNull { it.id == id } }

  fun refresh() {
    reports = CrashReporter.listReports(context)
  }

  fun back() {
    if (selected != null && !openedDirectly) selectedId = null else onClose()
  }

  BackHandler(onBack = ::back)

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = ::back, modifier = Modifier.testTag("crash_reports_back")) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.crash_reports_back),
          )
        }
        Text(
          text =
            stringResource(
              if (openedDirectly) R.string.crash_report_after_crash_title
              else R.string.crash_reports_title
            ),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(modifier = Modifier.size(8.dp))
      if (selected != null) {
        CrashReportDetail(
          report = selected,
          showAfterCrashHint = openedDirectly,
          onDeleted = {
            CrashReporter.deleteReport(context, selected.id)
            refresh()
            if (openedDirectly) onClose() else selectedId = null
          },
          onContinue = onClose,
        )
      } else {
        CrashReportList(
          reports = reports,
          onOpen = { selectedId = it.id },
          onDeleteAll = {
            CrashReporter.clearAll(context)
            refresh()
          },
        )
      }
    }
  }
}

@Composable
private fun CrashReportDetail(
  report: CrashReport,
  showAfterCrashHint: Boolean,
  onDeleted: () -> Unit,
  onContinue: () -> Unit,
) {
  val context = LocalContext.current
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (showAfterCrashHint) {
      Text(
        text = stringResource(R.string.crash_report_hint),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    Text(
      text = formatTime(report.timestampMillis),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // The report is technical English text, so it is always shown left-to-right.
    SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Text(
        text = report.text,
        style =
          MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textDirection = TextDirection.Ltr,
            textAlign = TextAlign.Left,
          ),
        modifier = Modifier.verticalScroll(rememberScrollState()).testTag("crash_report_text"),
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
        onClick = { copyReport(context, report.text) },
        modifier = Modifier.weight(1f).testTag("crash_report_copy"),
      ) {
        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
        Spacer(modifier = Modifier.size(6.dp))
        Text(stringResource(R.string.crash_report_copy))
      }
      OutlinedButton(
        onClick = { shareReport(context, report.text) },
        modifier = Modifier.weight(1f).testTag("crash_report_share"),
      ) {
        Icon(imageVector = Icons.Default.Share, contentDescription = null)
        Spacer(modifier = Modifier.size(6.dp))
        Text(stringResource(R.string.crash_report_share))
      }
      OutlinedButton(
        onClick = onDeleted,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.weight(1f).testTag("crash_report_clear"),
      ) {
        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
        Spacer(modifier = Modifier.size(6.dp))
        Text(stringResource(R.string.crash_report_clear))
      }
    }
    if (showAfterCrashHint) {
      Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().testTag("crash_report_continue"),
      ) {
        Text(stringResource(R.string.crash_report_continue))
      }
    }
  }
}

@Composable
private fun CrashReportList(
  reports: List<CrashReport>,
  onOpen: (CrashReport) -> Unit,
  onDeleteAll: () -> Unit,
) {
  if (reports.isEmpty()) {
    Text(
      text = stringResource(R.string.crash_reports_empty),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(8.dp),
    )
    return
  }
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(reports, key = { it.id }) { report ->
        Card(onClick = { onOpen(report) }, modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
              text = formatTime(report.timestampMillis),
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = report.title,
              style =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  textDirection = TextDirection.Ltr,
                  textAlign = TextAlign.Left,
                ),
              maxLines = 2,
            )
          }
        }
      }
    }
    TextButton(
      onClick = onDeleteAll,
      colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      modifier = Modifier.fillMaxWidth().testTag("crash_reports_delete_all"),
    ) {
      Icon(imageVector = Icons.Default.Delete, contentDescription = null)
      Spacer(modifier = Modifier.size(6.dp))
      Text(stringResource(R.string.crash_reports_delete_all))
    }
  }
}

private fun formatTime(millis: Long): String =
  DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(millis))

private fun copyReport(context: Context, text: String) {
  runCatching {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText("HomeBill crash report", text))
      Toast.makeText(context, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
    }
    .onFailure { Toast.makeText(context, R.string.crash_report_action_failed, Toast.LENGTH_SHORT).show() }
}

private fun shareReport(context: Context, text: String) {
  runCatching {
      val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, "HomeBill crash report")
          putExtra(Intent.EXTRA_TEXT, text)
        }
      context.startActivity(
        Intent.createChooser(send, context.getString(R.string.crash_report_share))
      )
    }
    .onFailure { Toast.makeText(context, R.string.crash_report_action_failed, Toast.LENGTH_SHORT).show() }
}
