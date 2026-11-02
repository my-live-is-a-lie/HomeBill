package com.example.ui.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.remote.UserGeminiKeyStore

/**
 * The bar shown while dictating: ✕ (cancel and discard), the live waveform, and ✓ (finish and keep),
 * like in Grok's voice input. In the right-to-left layout the first child sits on the right.
 * [confirmBusy] swaps the ✓ for a small spinner while the dictation is being wrapped up.
 */
@Composable
fun VoiceRecordingBar(
  levels: List<Float>,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
  confirmBusy: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FilledIconButton(
      onClick = onCancel,
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
      modifier = Modifier.size(44.dp).testTag("voice_cancel_button"),
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "إلغاء الإدخال الصوتي وحذف ما كُتب",
      )
    }
    VoiceWaveform(
      levels = levels,
      modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(40.dp),
    )
    FilledIconButton(
      onClick = onConfirm,
      enabled = !confirmBusy,
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.onSurface,
          contentColor = MaterialTheme.colorScheme.surface,
          disabledContainerColor = MaterialTheme.colorScheme.onSurface,
          disabledContentColor = MaterialTheme.colorScheme.surface,
        ),
      modifier = Modifier.size(44.dp).testTag("voice_confirm_button"),
    ) {
      if (confirmBusy) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.surface,
        )
      } else {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "إنهاء الإدخال الصوتي وإبقاء النص",
        )
      }
    }
  }
}

/** Scrolling bars that follow the loudness of the voice: newest on the right, quiet moments as dots. */
@Composable
fun VoiceWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
  val barColor = MaterialTheme.colorScheme.onSurface
  Canvas(modifier = modifier) {
    val barWidth = 3.dp.toPx()
    val step = barWidth + 3.dp.toPx()
    val minHeight = 4.dp.toPx()
    val count = (size.width / step).toInt().coerceAtLeast(1)
    for (position in 0 until count) {
      val index = levels.size - count + position
      val level = if (index >= 0) levels[index] else 0f
      val barHeight = minHeight + (size.height - minHeight) * level
      val x = size.width - (count - position) * step
      drawRoundRect(
        color = barColor.copy(alpha = if (level < 0.05f) 0.4f else 1f),
        topLeft = Offset(x, (size.height - barHeight) / 2f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
      )
    }
  }
}

/**
 * Bottom sheet for adding a bill by voice: it starts listening at once, shows the words as they are
 * said, and when the user taps ✓ it hands the sentence to [onTextReady] (the caller turns it into a
 * bill draft and opens the review form). ✕, swiping the sheet away or going back cancels.
 *
 * Uses the live Gemini dictation when a Gemini key exists, the recognizer built into Android
 * otherwise. If dictation ends by itself (silence, an error, the app going to the background) the
 * sheet keeps the text and offers "re-record" and "convert to a bill".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBillEntrySheet(onDismiss: () -> Unit, onTextReady: (String) -> Unit) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val liveVoice = rememberGeminiLiveDictation()
  val builtinVoice = rememberVoiceDictation()
  val voice: Dictation = if (liveVoice.isListening) liveVoice else builtinVoice

  var transcript by remember { mutableStateOf("") }
  // True after the user tapped ✓: the next end of dictation means "convert this to a bill".
  var confirming by remember { mutableStateOf(false) }

  val latestOnDismiss by rememberUpdatedState(onDismiss)
  val latestOnTextReady by rememberUpdatedState(onTextReady)

  val cancelAndClose: () -> Unit = {
    liveVoice.cancel()
    builtinVoice.cancel()
    latestOnDismiss()
  }

  val begin: () -> Unit = {
    confirming = false
    transcript = ""
    val onUpdate: (String) -> Unit = { text -> transcript = text }
    val onFinished: (String) -> Unit = { text ->
      transcript = text
      if (confirming) {
        val spoken = text.trim()
        if (spoken.isEmpty()) {
          confirming = false
          Toast.makeText(context, "لم أسمع شيئًا، حاول مرة أخرى", Toast.LENGTH_SHORT).show()
        } else {
          latestOnTextReady(spoken)
        }
      }
    }
    val onError: (String) -> Unit = { message ->
      confirming = false
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    var started = false
    if (UserGeminiKeyStore.hasKey(context)) {
      started = liveVoice.start("", onUpdate, onFinished, onError)
    }
    if (!started) started = builtinVoice.start("", onUpdate, onFinished, onError)
    if (!started) {
      Toast.makeText(context, "التعرف على الكلام غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show()
      latestOnDismiss()
    }
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        begin()
      } else {
        Toast.makeText(
            context,
            "يلزم السماح باستخدام الميكروفون للإدخال الصوتي",
            Toast.LENGTH_SHORT,
          )
          .show()
        latestOnDismiss()
      }
    }

  LaunchedEffect(Unit) {
    if (hasMicrophonePermission(context)) {
      begin()
    } else {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  ModalBottomSheet(
    onDismissRequest = cancelAndClose,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp)
          .testTag("voice_bill_sheet"),
    ) {
      Text(
        text = "إضافة فاتورة بالصوت",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "قل مثلًا: «دفعت فاتورة الكهرباء ٣٥٠ جنيه اليوم» — ستراجع النتيجة قبل الحفظ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(16.dp))

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 220.dp),
      ) {
        val blank = transcript.isBlank()
        Text(
          text =
            when {
              !blank -> transcript
              voice.isListening -> "جارٍ الاستماع…"
              else -> ""
            },
          style = MaterialTheme.typography.bodyLarge,
          color =
            if (blank) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        )
      }
      Spacer(modifier = Modifier.height(16.dp))

      if (voice.isListening) {
        VoiceRecordingBar(
          levels = voice.levels,
          onCancel = cancelAndClose,
          onConfirm = {
            confirming = true
            voice.stop()
          },
          confirmBusy = confirming,
        )
      } else {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = begin,
            modifier = Modifier.weight(1f).height(48.dp).testTag("voice_retry_button"),
          ) {
            Text("إعادة التسجيل")
          }
          Button(
            onClick = { latestOnTextReady(transcript.trim()) },
            enabled = transcript.isNotBlank(),
            modifier = Modifier.weight(1f).height(48.dp).testTag("voice_convert_button"),
          ) {
            Text("تحويل إلى فاتورة")
          }
        }
      }
    }
  }
}
