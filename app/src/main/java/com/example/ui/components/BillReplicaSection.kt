package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BillRecord
import com.example.data.remote.GeminiBillLayoutExtractor
import com.example.data.remote.ReplicaLayoutDto
import com.example.util.BillImageStore
import com.example.util.BillReplicaExporter
import com.example.util.BillReplicaRenderer
import com.example.util.BillReplicaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ReplicaFormat {
  JPEG,
  PDF,
  DOCX,
}

/** Finds the text block under a tap, in the 0..1000 normalized image coordinates. */
private fun findBlockIndexAt(layout: ReplicaLayoutDto, nx: Double, ny: Double): Int? {
  val margin = 6.0
  var bestIndex: Int? = null
  var bestArea = Double.MAX_VALUE
  layout.textBlocks.forEachIndexed { index, block ->
    val left = minOf(block.xmin, block.xmax) - margin
    val right = maxOf(block.xmin, block.xmax) + margin
    val top = minOf(block.ymin, block.ymax) - margin
    val bottom = maxOf(block.ymin, block.ymax) + margin
    if (nx in left..right && ny in top..bottom) {
      val area = (right - left) * (bottom - top)
      if (area < bestArea) {
        bestArea = area
        bestIndex = index
      }
    }
  }
  return bestIndex
}

/**
 * Redraws the stored bill image as a clean digital replica (same size, text in the same places,
 * closest fonts) and lets the user fix texts by tapping them, optionally proofread the texts with
 * AI, and download the result as JPEG, PDF or Word.
 */
@Composable
fun BillReplicaSection(bill: BillRecord) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val imageFile = remember(bill.id) { BillImageStore.imageFile(context, bill.id) }
  val imageSize = remember(bill.id) { BillReplicaRenderer.imageSize(imageFile) }
  var layout by
    remember(bill.id) { mutableStateOf<ReplicaLayoutDto?>(BillReplicaStore.load(context, bill.id)) }
  var isGenerating by remember(bill.id) { mutableStateOf(false) }
  var isVerifying by remember(bill.id) { mutableStateOf(false) }
  var errorText by remember(bill.id) { mutableStateOf<String?>(null) }
  var statusNote by remember(bill.id) { mutableStateOf<String?>(null) }
  var editingIndex by remember(bill.id) { mutableStateOf<Int?>(null) }
  var editText by remember(bill.id) { mutableStateOf("") }

  val label = bill.providerName.ifBlank { bill.categoryName }
  val savedImageMessage = stringResource(R.string.bill_replica_saved_images)
  val savedDocumentMessage = stringResource(R.string.bill_replica_saved_docs)
  val saveFailedMessage = stringResource(R.string.bill_replica_save_failed)
  val generateFailedMessage = stringResource(R.string.bill_replica_generate_failed)
  val verifyCleanMessage = stringResource(R.string.bill_replica_verify_clean)

  fun generate() {
    if (isGenerating || isVerifying) return
    isGenerating = true
    errorText = null
    statusNote = null
    scope.launch {
      GeminiBillLayoutExtractor.extract(context, imageFile)
        .onSuccess { newLayout ->
          withContext(Dispatchers.IO) { BillReplicaStore.save(context, bill.id, newLayout) }
          layout = newLayout
        }
        .onFailure { error -> errorText = error.localizedMessage ?: generateFailedMessage }
      isGenerating = false
    }
  }

  fun verify() {
    val currentLayout = layout ?: return
    if (isGenerating || isVerifying) return
    isVerifying = true
    errorText = null
    statusNote = null
    scope.launch {
      GeminiBillLayoutExtractor.verify(context, imageFile, currentLayout)
        .onSuccess { result ->
          if (result.changedCount > 0) {
            withContext(Dispatchers.IO) { BillReplicaStore.save(context, bill.id, result.layout) }
            layout = result.layout
            statusNote =
              context.getString(R.string.bill_replica_verify_done, result.changedCount)
          } else {
            statusNote = verifyCleanMessage
          }
        }
        .onFailure { error -> errorText = error.localizedMessage ?: generateFailedMessage }
      isVerifying = false
    }
  }

  fun export(format: ReplicaFormat) {
    val currentLayout = layout ?: return
    val size = imageSize ?: return
    scope.launch {
      val ok =
        withContext(Dispatchers.IO) {
          when (format) {
            ReplicaFormat.JPEG ->
              BillReplicaExporter.saveJpeg(context, currentLayout, size.first, size.second, label)
            ReplicaFormat.PDF ->
              BillReplicaExporter.savePdf(context, currentLayout, size.first, size.second, label)
            ReplicaFormat.DOCX ->
              BillReplicaExporter.saveDocx(context, currentLayout, size.first, size.second, label)
          }
        }
      val message =
        when {
          !ok -> saveFailedMessage
          format == ReplicaFormat.JPEG -> savedImageMessage
          else -> savedDocumentMessage
        }
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
  }

  if (imageSize == null) return

  // Dialog to fix one text by hand (opened by tapping a text in the replica).
  val dialogIndex = editingIndex
  val dialogLayout = layout
  if (dialogIndex != null && dialogLayout != null && dialogIndex in dialogLayout.textBlocks.indices) {
    AlertDialog(
      onDismissRequest = { editingIndex = null },
      title = { Text(text = stringResource(R.string.bill_replica_edit_title)) },
      text = {
        OutlinedTextField(
          value = editText,
          onValueChange = { editText = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("replica_edit_text_input"),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            val fixedText = editText.replace('\n', ' ').trim()
            if (fixedText.isNotEmpty()) {
              val updated =
                dialogLayout.copy(
                  textBlocks =
                    dialogLayout.textBlocks.mapIndexed { index, block ->
                      if (index == dialogIndex) block.copy(text = fixedText) else block
                    }
                )
              layout = updated
              scope.launch {
                withContext(Dispatchers.IO) { BillReplicaStore.save(context, bill.id, updated) }
              }
            }
            editingIndex = null
          },
          modifier = Modifier.testTag("replica_edit_save_button"),
        ) {
          Text(text = stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { editingIndex = null }) {
          Text(text = stringResource(R.string.action_cancel))
        }
      },
    )
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = stringResource(R.string.bill_replica_title),
      style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = stringResource(R.string.bill_replica_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Always-visible warning: the replica can contain mistakes.
    Surface(
      color = MaterialTheme.colorScheme.errorContainer,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().testTag("bill_replica_warning"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(12.dp),
      ) {
        Icon(
          imageVector = Icons.Default.WarningAmber,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.bill_replica_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
    Spacer(modifier = Modifier.height(10.dp))

    val currentLayout = layout
    if (currentLayout == null) {
      OutlinedButton(
        onClick = { generate() },
        enabled = !isGenerating,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_replica_button"),
      ) {
        if (isGenerating) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.bill_replica_generating))
        } else {
          Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.bill_replica_generate))
        }
      }
    } else {
      val replicaBitmap by
        produceState<Bitmap?>(null, currentLayout, imageSize) {
          value =
            withContext(Dispatchers.Default) {
              BillReplicaRenderer.renderBitmap(currentLayout, imageSize.first, imageSize.second)
            }
        }
      val shownBitmap = replicaBitmap
      if (shownBitmap == null) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else {
        Text(
          text = stringResource(R.string.bill_replica_tap_hint),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Full width with the original aspect ratio: a tap maps directly to image coordinates.
        Image(
          bitmap = shownBitmap.asImageBitmap(),
          contentDescription = stringResource(R.string.bill_replica_title),
          contentScale = ContentScale.FillBounds,
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(imageSize.first.toFloat() / imageSize.second.toFloat())
              .clip(MaterialTheme.shapes.medium)
              .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
              .pointerInput(currentLayout) {
                detectTapGestures { tap ->
                  val boxWidth = size.width.toFloat()
                  val boxHeight = size.height.toFloat()
                  if (boxWidth > 0f && boxHeight > 0f) {
                    val nx = (tap.x / boxWidth * 1000f).toDouble()
                    val ny = (tap.y / boxHeight * 1000f).toDouble()
                    val hit = findBlockIndexAt(currentLayout, nx, ny)
                    if (hit != null) {
                      editText = currentLayout.textBlocks[hit].text
                      editingIndex = hit
                    }
                  }
                }
              }
              .testTag("bill_replica_image"),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = stringResource(R.string.bill_replica_download_as),
        style = MaterialTheme.typography.labelLarge,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = { export(ReplicaFormat.JPEG) },
          modifier = Modifier.weight(1f).height(48.dp).testTag("export_replica_jpeg_button"),
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "JPEG")
        }
        OutlinedButton(
          onClick = { export(ReplicaFormat.PDF) },
          modifier = Modifier.weight(1f).height(48.dp).testTag("export_replica_pdf_button"),
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "PDF")
        }
        OutlinedButton(
          onClick = { export(ReplicaFormat.DOCX) },
          modifier = Modifier.weight(1f).height(48.dp).testTag("export_replica_docx_button"),
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Word")
        }
      }

      // Optional AI proofreading of the extracted texts.
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = stringResource(R.string.bill_replica_verify_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedButton(
        onClick = { verify() },
        enabled = !isGenerating && !isVerifying,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verify_replica_button"),
      ) {
        if (isVerifying) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.bill_replica_verifying))
        } else {
          Icon(imageVector = Icons.Default.Spellcheck, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.bill_replica_verify))
        }
      }

      TextButton(
        onClick = { generate() },
        enabled = !isGenerating && !isVerifying,
        modifier = Modifier.testTag("regenerate_replica_button"),
      ) {
        if (isGenerating) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = stringResource(R.string.bill_replica_generating))
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = stringResource(R.string.bill_replica_regenerate))
        }
      }
    }

    statusNote?.let { note ->
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 2.dp).testTag("bill_replica_status_note"),
      )
    }

    errorText?.let { message ->
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 2.dp),
      )
    }
  }
}
