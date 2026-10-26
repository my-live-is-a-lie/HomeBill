package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.UtilityType
import com.example.data.remote.ScannedBillDraft
import com.example.domain.BillAnalyticsEngine
import com.example.ui.components.CameraXBillCaptureDialog
import com.example.ui.components.CategoryFormDialog
import com.example.ui.components.UtilityIconAvatar
import com.example.ui.theme.LocalUtilityPalette
import com.example.ui.viewmodel.ScanUiState
import com.example.util.DateAndFormatUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.io.File
import java.util.Locale

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalLayoutApi::class,
  ExperimentalPermissionsApi::class,
)
@Composable
fun ScannerScreen(
  scanState: ScanUiState,
  categories: List<BillCategory>,
  allBills: List<BillRecord>,
  preselectedCategoryId: String?,
  isSaving: Boolean,
  isReclassifying: Boolean = false,
  onImageSelected: (Uri) -> Unit,
  onUpdateDraft: (ScannedBillDraft) -> Unit,
  onReclassifyDraft: () -> Unit = {},
  onSaveScannedBill: (ScannedBillDraft, String?) -> Unit,
  onResetScan: () -> Unit,
  onCreateCategory: (String, UtilityType, String, Double) -> Unit,
) {
  val context = LocalContext.current
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
  var permissionDeniedNotice by rememberSaveable { mutableStateOf(false) }
  var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }
  var showCameraXScanner by remember { mutableStateOf(false) }
  var showCreateDialog by remember { mutableStateOf(false) }

  val cameraPermissionState =
    rememberPermissionState(permission = Manifest.permission.CAMERA) { isGranted ->
      if (isGranted) {
        permissionDeniedNotice = false
        showCameraXScanner = true
      } else {
        permissionDeniedNotice = true
      }
    }

  val isCameraGranted = cameraPermissionState.status.isGranted
  val shouldShowCameraRationale = cameraPermissionState.status.shouldShowRationale

  fun openAppPermissionSettings() {
    runCatching {
      val settingsIntent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
          )
          .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      context.startActivity(settingsIntent)
    }
  }

  val takePictureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
      if (success) {
        pendingCameraUri?.let(onImageSelected)
      }
    }

  fun launchSystemCameraFallback() {
    val tempFile = File.createTempFile("bill_scan_", ".jpg", context.cacheDir)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    pendingCameraUri = uri
    takePictureLauncher.launch(uri)
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onImageSelected(uri)
      }
    }

  if (showCreateDialog) {
    CategoryFormDialog(
      onDismiss = { showCreateDialog = false },
      onSave = { name, uType, unit, threshold ->
        showCreateDialog = false
        onCreateCategory(name, uType, unit, threshold)
      },
    )
  }

  if (showCameraXScanner) {
    CameraXBillCaptureDialog(
      onImageCaptured = { capturedUri ->
        showCameraXScanner = false
        onImageSelected(capturedUri)
      },
      onUseSystemCamera = { launchSystemCameraFallback() },
      onDismiss = { showCameraXScanner = false },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.scanner_title)) },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      ) {
        when (scanState) {
          is ScanUiState.Idle,
          is ScanUiState.Error -> {
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.padding(24.dp),
                ) {
                  UtilityIconAvatar(utilityType = UtilityType.OTHER, sizeDp = 64)
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = stringResource(R.string.scanner_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = stringResource(R.string.scanner_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                  )

                  if (isCameraGranted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                      color = MaterialTheme.colorScheme.primaryContainer,
                      shape = MaterialTheme.shapes.large,
                      modifier = Modifier.testTag("camera_permission_ready_badge"),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Default.CheckCircle,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = stringResource(R.string.camera_permission_ready),
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                  } else if (shouldShowCameraRationale) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = MaterialTheme.shapes.large,
                      modifier =
                        Modifier.fillMaxWidth().testTag("camera_permission_rationale_card"),
                    ) {
                      Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(
                            text = stringResource(R.string.camera_permission_rationale_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                          )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                          text = stringResource(R.string.camera_permission_rationale_body),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                          onClick = {
                            permissionRequestedOnce = true
                            cameraPermissionState.launchPermissionRequest()
                          },
                          modifier =
                            Modifier.fillMaxWidth()
                              .height(48.dp)
                              .testTag("grant_camera_permission_button"),
                        ) {
                          Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(stringResource(R.string.camera_permission_grant_action))
                        }
                      }
                    }
                  } else if (permissionDeniedNotice || permissionRequestedOnce) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = MaterialTheme.shapes.large,
                      modifier = Modifier.fillMaxWidth().testTag("camera_permission_denied_card"),
                    ) {
                      Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                          text = stringResource(R.string.camera_permission_denied),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                          onClick = { openAppPermissionSettings() },
                          modifier =
                            Modifier.fillMaxWidth()
                              .height(48.dp)
                              .testTag("open_app_settings_button"),
                        ) {
                          Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(stringResource(R.string.camera_permission_open_settings))
                        }
                      }
                    }
                  }

                  if (scanState is ScanUiState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                      color = MaterialTheme.colorScheme.errorContainer,
                      shape = MaterialTheme.shapes.medium,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                          text = scanState.message,
                          color = MaterialTheme.colorScheme.onErrorContainer,
                          style = MaterialTheme.typography.bodySmall,
                        )
                        if (scanState.lastUri != null) {
                          Spacer(modifier = Modifier.height(10.dp))
                          OutlinedButton(
                            onClick = { onImageSelected(scanState.lastUri) },
                            modifier =
                              Modifier.fillMaxWidth()
                                .height(48.dp)
                                .testTag("retry_scan_button"),
                          ) {
                            Icon(
                              imageVector = Icons.Default.Refresh,
                              contentDescription = null,
                              modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.retry_scan_button))
                          }
                        }
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(22.dp))

                  Button(
                    onClick = {
                      if (isCameraGranted) {
                        showCameraXScanner = true
                      } else {
                        permissionRequestedOnce = true
                        cameraPermissionState.launchPermissionRequest()
                      }
                    },
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(52.dp)
                        .testTag("capture_camera_button"),
                  ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = stringResource(R.string.take_photo_camera),
                      style = MaterialTheme.typography.titleMedium,
                    )
                  }

                  Spacer(modifier = Modifier.height(10.dp))

                  FilledTonalButton(
                    onClick = {
                      photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(52.dp)
                        .testTag("pick_gallery_button"),
                  ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = stringResource(R.string.pick_photo_gallery),
                      style = MaterialTheme.typography.titleMedium,
                    )
                  }
                }
              }
            }
          }

          is ScanUiState.Scanning -> {
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.padding(28.dp),
                ) {
                  AsyncImage(
                    model = scanState.imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.large),
                  )
                  Spacer(modifier = Modifier.height(20.dp))
                  CircularProgressIndicator()
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = stringResource(R.string.scanning_in_progress),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }

          is ScanUiState.Extracted -> {
            item {
              ExtractedSheetReviewCard(
                imageUri = scanState.imageUri,
                draft = scanState.draft,
                categories = categories,
                allBills = allBills,
                initialCategoryId = scanState.preselectedCategoryId ?: preselectedCategoryId,
                isSaving = isSaving,
                isReclassifying = isReclassifying,
                onUpdateDraft = onUpdateDraft,
                onReclassifyDraft = onReclassifyDraft,
                onConfirmSave = onSaveScannedBill,
                onRescan = onResetScan,
              )
            }
          }
        }

        // Separate "add bill" button below the scan card.
        item {
          Button(
            onClick = { showCreateDialog = true },
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .testTag("add_bill_from_scanner_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.add_category_button),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExtractedSheetReviewCard(
  imageUri: Uri,
  draft: ScannedBillDraft,
  categories: List<BillCategory>,
  allBills: List<BillRecord>,
  initialCategoryId: String?,
  isSaving: Boolean,
  isReclassifying: Boolean,
  onUpdateDraft: (ScannedBillDraft) -> Unit,
  onReclassifyDraft: () -> Unit,
  onConfirmSave: (ScannedBillDraft, String?) -> Unit,
  onRescan: () -> Unit,
) {
  val palette = LocalUtilityPalette.current
  val autoMatchedCategory =
    remember(categories, draft.detectedUtilityType, draft.suggestedCategoryName, initialCategoryId) {
      if (!initialCategoryId.isNullOrBlank()) {
        categories.firstOrNull { it.id == initialCategoryId }
      } else {
        categories.firstOrNull { it.utilityType == draft.detectedUtilityType.firestoreKey }
          ?: categories.firstOrNull {
            it.name.trim().equals(draft.suggestedCategoryName.trim(), ignoreCase = true)
          }
      }
    }

  var chosenCategoryId by remember(autoMatchedCategory) { mutableStateOf(autoMatchedCategory?.id) }
  var showDatePicker by remember { mutableStateOf(false) }

  val targetCategory = categories.firstOrNull { it.id == chosenCategoryId }
  val existingCatBills =
    remember(allBills, chosenCategoryId) {
      if (chosenCategoryId == null) emptyList()
      else allBills.filter { it.categoryId == chosenCategoryId }
    }

  val previewComparison =
    remember(draft, existingCatBills, targetCategory) {
      val dummyRecord =
        BillRecord(
          billDateIso = draft.billDateIso,
          billingMonth = draft.billingMonth,
          billDateEpochMillis = draft.billDateEpochMillis,
          totalAmount = draft.totalAmount,
          currency = draft.currency,
          consumptionValue = draft.consumptionValue,
          consumptionUnit = draft.consumptionUnit,
        )
      BillAnalyticsEngine.previewDraftComparison(
        draftBill = dummyRecord,
        existingCategoryBills = existingCatBills,
        thresholdPercent = targetCategory?.alertThresholdPercent ?: 25.0,
      )
    }

  if (showDatePicker) {
    val dpState = rememberDatePickerState(initialSelectedDateMillis = draft.billDateEpochMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            dpState.selectedDateMillis?.let { millis ->
              val iso = DateAndFormatUtils.epochMillisToIsoDate(millis)
              onUpdateDraft(
                draft.copy(
                  billDateIso = iso,
                  billingMonth = DateAndFormatUtils.isoDateToBillingMonth(iso),
                  billDateEpochMillis = millis,
                )
              )
            }
            showDatePicker = false
          }
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = dpState)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    // Captured Photo + Detected Utility Banner
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = MaterialTheme.shapes.extraLarge,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        AsyncImage(
          model = imageUri,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.fillMaxWidth()
              .height(160.dp)
              .clip(MaterialTheme.shapes.large),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          ) {
            UtilityIconAvatar(utilityType = draft.detectedUtilityType, sizeDp = 44)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = stringResource(R.string.detected_category_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text =
                  "${stringResource(draft.detectedUtilityType.labelRes)} (${draft.suggestedCategoryName})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          OutlinedButton(onClick = onRescan) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.rescan_button))
          }
        }

        if (draft.classificationReason.isNotBlank()) {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("classification_reason_badge"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = draft.classificationReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.expense_classification_section),
          style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          UtilityType.entries.forEach { expenseType ->
            FilterChip(
              selected = draft.detectedUtilityType == expenseType,
              onClick = {
                val previousDefaultUnit = draft.detectedUtilityType.defaultUnit
                val previousDefaultCategory = draft.detectedUtilityType.defaultCategoryName
                val updatedUnit =
                  if (
                    draft.consumptionUnit.isBlank() ||
                      draft.consumptionUnit == "وحدة" ||
                      draft.consumptionUnit == previousDefaultUnit
                  ) {
                    expenseType.defaultUnit
                  } else {
                    draft.consumptionUnit
                  }
                val updatedCategoryName =
                  if (
                    draft.suggestedCategoryName.isBlank() ||
                      draft.suggestedCategoryName == previousDefaultCategory ||
                      draft.suggestedCategoryName == UtilityType.OTHER.defaultCategoryName
                  ) {
                    expenseType.defaultCategoryName
                  } else {
                    draft.suggestedCategoryName
                  }
                onUpdateDraft(
                  draft.copy(
                    detectedUtilityType = expenseType,
                    suggestedCategoryName = updatedCategoryName,
                    consumptionUnit = updatedUnit,
                  )
                )
              },
              label = { Text(stringResource(expenseType.labelRes)) },
              modifier = Modifier.testTag("expense_type_chip_${expenseType.firestoreKey}"),
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onReclassifyDraft,
          enabled = !isReclassifying && !isSaving,
          modifier =
            Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag("reclassify_expense_button"),
        ) {
          if (isReclassifying) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.reclassifying_in_progress))
          } else {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.reclassify_with_gemini))
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.target_category_label),
          style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          categories.forEach { cat ->
            FilterChip(
              selected = chosenCategoryId == cat.id,
              onClick = { chosenCategoryId = cat.id },
              label = { Text(cat.name) },
            )
          }
          FilterChip(
            selected = chosenCategoryId == null,
            onClick = { chosenCategoryId = null },
            label = {
              Text("${draft.suggestedCategoryName} (${stringResource(R.string.auto_create_category_badge)})")
            },
          )
        }
      }
    }

    // Immediate Variance Warning vs Previous Bill
    if (previewComparison.hasSpike) {
      Surface(
        color = palette.spikeWarningContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = palette.spikeWarningAccent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.variance_spike_alert_title),
              style = MaterialTheme.typography.titleSmall,
              color = palette.spikeWarningAccent,
            )
          }
          previewComparison.amountChangePercent?.takeIf { previewComparison.isFinancialSpike }?.let { pct ->
            Text(
              text =
                stringResource(
                  R.string.variance_amount_up,
                  pct,
                  previewComparison.amountDiff,
                  draft.currency,
                ),
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 6.dp),
            )
          }
          previewComparison.consumptionChangePercent
            ?.takeIf { previewComparison.isConsumptionSpike }
            ?.let { pct ->
              Text(
                text =
                  stringResource(
                    R.string.variance_consumption_up,
                    pct,
                    previewComparison.consumptionDiff,
                    draft.consumptionUnit,
                  ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
        }
      }
    }

    // Editable Extracted Sheet Card
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = MaterialTheme.shapes.extraLarge,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(18.dp),
      ) {
        Text(
          text = stringResource(R.string.scan_result_sheet_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
        )

        val context = LocalContext.current
        var showStructuredJson by rememberSaveable { mutableStateOf(false) }

        OutlinedButton(
          onClick = { showDatePicker = true },
          modifier =
            Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag("scanned_date_button"),
        ) {
          Icon(Icons.Default.CalendarMonth, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            "${stringResource(R.string.field_bill_date)}: ${DateAndFormatUtils.formatIsoDateArabic(draft.billDateIso)}"
          )
        }

        OutlinedTextField(
          value = draft.storeName.ifBlank { draft.providerName },
          onValueChange = { updatedStore ->
            onUpdateDraft(draft.copy(storeName = updatedStore, providerName = updatedStore))
          },
          label = { Text(stringResource(R.string.field_store_name)) },
          leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("scanned_store_name_input"),
        )

        OutlinedTextField(
          value = draft.invoiceNumber,
          onValueChange = { onUpdateDraft(draft.copy(invoiceNumber = it)) },
          label = { Text(stringResource(R.string.field_invoice_number)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("scanned_invoice_number_input"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          var amountText by
            remember(draft.totalAmount) {
              mutableStateOf(DateAndFormatUtils.formatNumber(draft.totalAmount))
            }
          OutlinedTextField(
            value = amountText,
            onValueChange = {
              amountText = it
              DateAndFormatUtils.parseFlexibleDouble(it)?.let { v ->
                onUpdateDraft(draft.copy(totalAmount = v))
              }
            },
            label = { Text(stringResource(R.string.field_total_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1.2f).testTag("scanned_total_amount_input"),
          )

          var taxText by
            remember(draft.taxAmount) {
              mutableStateOf(DateAndFormatUtils.formatNumber(draft.taxAmount))
            }
          OutlinedTextField(
            value = taxText,
            onValueChange = {
              taxText = it
              DateAndFormatUtils.parseFlexibleDouble(it)?.let { v ->
                onUpdateDraft(draft.copy(taxAmount = v))
              }
            },
            label = { Text(stringResource(R.string.field_tax_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(0.95f).testTag("scanned_tax_amount_input"),
          )

          OutlinedTextField(
            value = draft.currency,
            onValueChange = { onUpdateDraft(draft.copy(currency = it)) },
            label = { Text(stringResource(R.string.field_currency)) },
            singleLine = true,
            modifier = Modifier.weight(0.65f),
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          var consText by
            remember(draft.consumptionValue) {
              mutableStateOf(DateAndFormatUtils.formatNumber(draft.consumptionValue))
            }
          OutlinedTextField(
            value = consText,
            onValueChange = {
              consText = it
              DateAndFormatUtils.parseFlexibleDouble(it)?.let { v ->
                onUpdateDraft(draft.copy(consumptionValue = v))
              }
            },
            label = { Text(stringResource(R.string.field_consumption_value)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1.3f),
          )
          OutlinedTextField(
            value = draft.consumptionUnit,
            onValueChange = { onUpdateDraft(draft.copy(consumptionUnit = it)) },
            label = { Text(stringResource(R.string.field_consumption_unit)) },
            singleLine = true,
            modifier = Modifier.weight(0.7f),
          )
        }

        if (draft.structuredJsonPreview.isNotBlank()) {
          OutlinedButton(
            onClick = { showStructuredJson = !showStructuredJson },
            modifier =
              Modifier.fillMaxWidth()
                .height(48.dp)
                .testTag("toggle_structured_json_button"),
          ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text =
                stringResource(
                  if (showStructuredJson) R.string.structured_json_hide_toggle
                  else R.string.structured_json_preview_toggle
                )
            )
          }

          if (showStructuredJson) {
            val copiedNotice = stringResource(R.string.structured_json_copied)
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth().testTag("structured_json_preview_card"),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = "Gemini Structured JSON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                  )
                  IconButton(
                    onClick = {
                      val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                      clipboard?.setPrimaryClip(
                        ClipData.newPlainText("ScannedBillJson", draft.structuredJsonPreview)
                      )
                      Toast.makeText(context, copiedNotice, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp).testTag("copy_structured_json_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.ContentCopy,
                      contentDescription = stringResource(R.string.assistant_copy_desc),
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                  Text(
                    text = draft.structuredJsonPreview,
                    style =
                      MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                      ),
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
            }
          }
        }

        // Full Structured Sheet Table
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = stringResource(R.string.sheet_rows_section_title),
            style = MaterialTheme.typography.titleSmall,
          )
          TextButton(
            onClick = {
              if (draft.sheetRows.size < 25) {
                onUpdateDraft(draft.copy(sheetRows = draft.sheetRows + BillSheetRow("", "", "")))
              }
            }
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.add_sheet_row))
          }
        }

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
        ) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp)
          ) {
            Text(
              stringResource(R.string.sheet_col_item),
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1.2f),
            )
            Text(
              stringResource(R.string.sheet_col_value),
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(36.dp))
          }

          draft.sheetRows.forEachIndexed { idx, row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
              OutlinedTextField(
                value = row.label,
                onValueChange = { newLabel ->
                  val updatedList = draft.sheetRows.toMutableList()
                  updatedList[idx] = row.copy(label = newLabel)
                  onUpdateDraft(draft.copy(sheetRows = updatedList))
                },
                singleLine = true,
                modifier = Modifier.weight(1.2f),
              )
              OutlinedTextField(
                value = row.value,
                onValueChange = { newVal ->
                  val updatedList = draft.sheetRows.toMutableList()
                  updatedList[idx] = row.copy(value = newVal)
                  onUpdateDraft(draft.copy(sheetRows = updatedList))
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = {
                  val updatedList = draft.sheetRows.toMutableList().apply { removeAt(idx) }
                  onUpdateDraft(draft.copy(sheetRows = updatedList))
                },
                modifier = Modifier.size(36.dp),
              ) {
                Icon(
                  Icons.Default.DeleteOutline,
                  contentDescription = stringResource(R.string.action_delete),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }

        if (draft.rawExtractedText.isNotBlank()) {
          Text(
            text = stringResource(R.string.extracted_raw_text_label),
            style = MaterialTheme.typography.labelLarge,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = draft.rawExtractedText,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = { onConfirmSave(draft, chosenCategoryId) },
          enabled = !isSaving,
          modifier =
            Modifier.fillMaxWidth()
              .height(54.dp)
              .testTag("confirm_save_scanned_bill_button"),
        ) {
          if (isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.confirm_save_scanned_bill),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }
  }
}
