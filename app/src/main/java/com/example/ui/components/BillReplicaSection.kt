package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/**
 * Redraws the stored bill image as a clean digital replica (same size, text in the same places,
 * closest fonts) and lets the user download it as JPEG, PDF or Word.
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
  var errorText by remember(bill.id) { mutableStateOf<String?>(null) }

  val label = bill.providerName.ifBlank { bill.categoryName }
  val savedImageMessage = stringResource(R.string.bill_replica_saved_images)
  val savedDocumentMessage = stringResource(R.string.bill_replica_saved_docs)
  val saveFailedMessage = stringResource(R.string.bill_replica_save_failed)
  val generateFailedMessage = stringResource(R.string.bill_replica_generate_failed)

  fun generate() {
    if (isGenerating) return
    isGenerating = true
    errorText = null
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
        produceState<android.graphics.Bitmap?>(null, currentLayout, imageSize) {
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
        Image(
          bitmap = shownBitmap.asImageBitmap(),
          contentDescription = stringResource(R.string.bill_replica_title),
          contentScale = ContentScale.Fit,
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(max = 520.dp)
              .clip(MaterialTheme.shapes.medium)
              .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
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

      TextButton(
        onClick = { generate() },
        enabled = !isGenerating,
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
