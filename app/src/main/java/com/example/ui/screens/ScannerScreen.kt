package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextDirection
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
import com.example.ui.components.ExpressiveButton
import com.example.ui.components.ExpressiveFab
import com.example.ui.components.UtilityIconAvatar
import com.example.ui.components.VoiceBillEntrySheet
import com.example.ui.theme.LocalUtilityPalette
import com.example.ui.viewmodel.ScanUiState
import com.example.util.CrashReporter
import com.example.util.DateAndFormatUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.util.Locale

/** Walks up the [ContextWrapper] chain to find the hosting [Activity], or null if none. */
private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

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
  onCreateCategory: (String, UtilityType, String, Double, Boolean) -> Unit,
  // When set, the create-section dialog offers the template choice and reports it here.
  onCreateCategoryWithTemplate:
    ((String, UtilityType, String, Double, Boolean, String) -> Unit)? =
    null,
  // Called with the sentence the user dictated in the "add by voice" sheet.
  onVoiceBillText: (String) -> Unit = {},
) {
  val context = LocalContext.current
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
  var permissionDeniedNotice by rememberSaveable { mutableStateOf(false) }
  var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }
  var showCameraXScanner by remember { mutableStateOf(false) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var showVoiceSheet by remember { mutableStateOf(false) }
  // The reason the ML Kit scanner failed to open, shown in a dialog instead of silently
  // falling back to the CameraX dialog. Null when the scanner opened (or was not tried).
  var scannerFailure by remember { mutableStateOf<ScannerFailure?>(null) }
  // True while the scanner module is being downloaded after Play services reported it missing.
  var scannerModuleDownloading by remember { mutableStateOf(false) }

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

  // Last step of the camera chain: tell the user instead of crashing.
  fun showCameraFailure() {
    Toast.makeText(context, context.getString(R.string.camera_open_failed), Toast.LENGTH_LONG).show()
  }

  // Records a scanner failure in the local reports (Settings > crash reports) and shows its
  // reason on screen, instead of silently falling back to the CameraX dialog.
  fun reportScannerFailure(throwable: Throwable) {
    val detail = "${throwable.javaClass.simpleName}: ${throwable.message ?: "unknown reason"}"
    Log.w("ScannerScreen", "Document scanner failed to open: $detail", throwable)
    runCatching {
      CrashReporter.recordNonFatal(context, "Document scanner failed to open", throwable)
    }
    scannerFailure = ScannerFailure(detail)
  }

  // Last-resort capture path. Creating the file, building the FileProvider uri and launching the
  // system camera can all throw (storage full, no camera app, missing CAMERA permission).
  fun launchSystemCameraFallback() {
    try {
      val tempFile = File.createTempFile("bill_scan_", ".jpg", context.cacheDir)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
      pendingCameraUri = uri
      takePictureLauncher.launch(uri)
    } catch (e: Exception) {
      Log.w("ScannerScreen", "System camera could not be launched", e)
      showCameraFailure()
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onImageSelected(uri)
      }
    }

  // Second step of the camera chain: the CameraX dialog (needs the camera permission). If even
  // this step cannot start, the system camera is tried next.
  fun openLegacyCamera() {
    try {
      if (isCameraGranted) {
        showCameraXScanner = true
      } else {
        permissionRequestedOnce = true
        cameraPermissionState.launchPermissionRequest()
      }
    } catch (e: Exception) {
      Log.w("ScannerScreen", "CameraX dialog could not be opened", e)
      showCameraXScanner = false
      launchSystemCameraFallback()
    }
  }

  // ML Kit Document Scanner: detects the bill edges automatically, lets the user adjust the
  // crop, and returns only the cropped bill image (so Gemini gets less irrelevant content).
  val documentScannerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
      try {
        if (result.resultCode == Activity.RESULT_OK) {
          val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
          val croppedUri = scanResult?.pages?.firstOrNull()?.imageUri
          if (croppedUri != null) {
            onImageSelected(croppedUri)
          }
        } else if (
          result.data?.hasExtra(
            ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION
          ) == true
        ) {
          // The scanner's IntentSender could not be started; the launcher reports that as a
          // cancelled result carrying the exception, so show its reason instead of falling back
          // silently.
          val sendException = result.data?.readSendIntentException()
          reportScannerFailure(
            sendException ?: Exception("Document scanner IntentSender failed to start")
          )
        }
      } catch (e: Exception) {
        Log.w("ScannerScreen", "Document scanner result could not be read", e)
        reportScannerFailure(e)
      }
    }

  // Launches the scanner once its module is confirmed to be installed. Every failure is
  // recorded and its reason shown, instead of silently falling back to the CameraX dialog.
  fun startScanner(scanner: GmsDocumentScanner, activity: Activity) {
    try {
      scanner
        .getStartScanIntent(activity)
        .addOnSuccessListener { intentSender ->
          try {
            documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
          } catch (e: Exception) {
            reportScannerFailure(e)
          }
        }
        .addOnFailureListener { e ->
          reportScannerFailure(e)
        }
    } catch (e: Exception) {
      reportScannerFailure(e)
    } catch (e: LinkageError) {
      // The scanner classes could not be loaded at all.
      reportScannerFailure(e)
    }
  }

  // Asks Play services to download the scanner module, and tells the user to retry once it
  // is ready. The missing module is also recorded in the reports log.
  fun requestScannerModule(scanner: GmsDocumentScanner) {
    runCatching {
      CrashReporter.recordNonFatal(
        context,
        "Document scanner module not installed",
        IllegalStateException("Document scanner module is not installed"),
      )
    }
    ModuleInstall.getClient(context)
      .installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
      .addOnSuccessListener { scannerModuleDownloading = true }
      .addOnFailureListener { e -> reportScannerFailure(e) }
  }

  // First step of the camera chain: ML Kit document scanner. The scanner is an on-demand
  // Play services module (GmsDocumentScanner is an OptionalModuleApi), so confirm it is
  // installed before launching; launching while it is missing fails inside Play services.
  fun launchDocumentScanner() {
    try {
      val activity = context.findActivity()
      if (activity == null) {
        reportScannerFailure(
          IllegalStateException("No activity available to open the document scanner")
        )
        return
      }
      // SCANNER_MODE_FULL = crop/rotate + image filters (grayscale, auto-enhance, ...) +
      // ML-powered cleaning (removes stains / fingers), all available after the capture.
      val options =
        GmsDocumentScannerOptions.Builder()
          .setGalleryImportAllowed(true)
          .setPageLimit(1)
          .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
          .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
          .build()
      // Make sure ML Kit is initialized before creating the client (its auto-init provider
      // is disabled in the manifest). This is a no-op once ML Kit is already initialized.
      com.google.mlkit.common.MlKit.initialize(context.applicationContext)
      val scanner = GmsDocumentScanning.getClient(options)
      ModuleInstall.getClient(context)
        .areModulesAvailable(scanner)
        .addOnSuccessListener { response ->
          if (response.areModulesAvailable()) {
            startScanner(scanner, activity)
          } else {
            requestScannerModule(scanner)
          }
        }
        .addOnFailureListener {
          // The availability check itself failed: try the scanner directly, and let the
          // diagnostic dialog show the real reason if that fails too.
          startScanner(scanner, activity)
        }
    } catch (e: Exception) {
      reportScannerFailure(e)
    } catch (e: LinkageError) {
      // The scanner classes could not be loaded at all.
      reportScannerFailure(e)
    }
  }

  if (showCreateDialog) {
    CategoryFormDialog(
      onDismiss = { showCreateDialog = false },
      onSave = { name, uType, unit, threshold, trackDuration ->
        showCreateDialog = false
        onCreateCategory(name, uType, unit, threshold, trackDuration)
      },
      onSaveWithTemplate = onCreateCategoryWithTemplate?.let { createWithTemplate ->
        { name: String, uType: UtilityType, unit: String, threshold: Double, trackDuration: Boolean, templateMode: String ->
          showCreateDialog = false
          createWithTemplate(name, uType, unit, threshold, trackDuration, templateMode)
        }
      },
    )
  }

  if (showVoiceSheet) {
    VoiceBillEntrySheet(
      onDismiss = { showVoiceSheet = false },
      onTextReady = { spokenText ->
        showVoiceSheet = false
        onVoiceBillText(spokenText)
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
      onUnavailable = {
        // The live preview cannot start: close the dialog and use the system camera instead.
        showCameraXScanner = false
        launchSystemCameraFallback()
      },
    )
  }

  scannerFailure?.let { failure ->
    ScannerFailureDialog(
      detail = failure.detail,
      onRetry = {
        scannerFailure = null
        launchDocumentScanner()
      },
      onUseFallback = {
        scannerFailure = null
        openLegacyCamera()
      },
    )
  }

  if (scannerModuleDownloading) {
    ScannerModuleDownloadingDialog(
      onDismiss = { scannerModuleDownloading = false },
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
        // Extra bottom space so the last card can scroll up above the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      ) {
        when (scanState) {
          is ScanUiState.Idle,
          is ScanUiState.Error -> {
            // A little empty space above the card, so it sits lower on the screen.
            item { Spacer(modifier = Modifier.height(44.dp)) }
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
                  // Receipt mark in the app's accent color (it follows the chosen accent).
                  Box(
                    modifier =
                      Modifier.size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(34.dp),
                    )
                  }
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

                  if (!isCameraGranted && shouldShowCameraRationale) {
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
                  } else if (!isCameraGranted && (permissionDeniedNotice || permissionRequestedOnce)) {
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

                  ExpressiveButton(
                    onClick = { launchDocumentScanner() },
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

                  ExpressiveButton(
                    onClick = {
                      photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    tonal = true,
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
                  // A bill dictated by voice has no picture to show while it is being read.
                  if (scanState.imageUri != Uri.EMPTY) {
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
                  }
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
                fromVoice = scanState.fromVoice,
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
      }

      // "Add bill": a small rectangular button with a plus, floating at the bottom corner of the
      // screen. BottomStart is the right corner in this right-to-left layout.
      ExpressiveFab(
        onClick = { showCreateDialog = true },
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(16.dp)
            .size(width = 72.dp, height = 56.dp)
            .testTag("add_bill_from_scanner_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = stringResource(R.string.add_category_button),
        )
      }
      // "Add bill by voice": at the bottom end (the left corner in this right-to-left
      // layout), opposite the "+" button at BottomStart, so the two stay far apart.
      ExtendedFloatingActionButton(
        onClick = { showVoiceSheet = true },
        icon = { Icon(imageVector = Icons.Default.Mic, contentDescription = null) },
        text = { Text("إضافة بالصوت") },
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(16.dp)
            .testTag("scanner_voice_bill_button"),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExtractedSheetReviewCard(
  imageUri: Uri,
  fromVoice: Boolean,
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
        if (imageUri != Uri.EMPTY) {
          AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.large),
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.bill_image_will_be_attached),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(14.dp))
        }

        // A bill dictated by voice shows the sentence that was heard instead of a picture, so the
        // user can check what the numbers below were read from.
        if (fromVoice && draft.rawExtractedText.isNotBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("voice_bill_quote_card"),
          ) {
            Row(
              verticalAlignment = Alignment.Top,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "ما قلته للتطبيق — راجع الأرقام قبل الحفظ",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = draft.rawExtractedText,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
        }
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
            Text(if (fromVoice) "إلغاء" else stringResource(R.string.rescan_button))
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

        // For a dictated bill the sentence is already shown at the top of the card.
        if (draft.rawExtractedText.isNotBlank() && !fromVoice) {
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

// Reason the ML Kit document scanner failed to open, shown to the user and recorded locally.
private data class ScannerFailure(val detail: String)

// The framework reports a failed IntentSender start as a cancelled result carrying the
// exception; read it out so the real reason can be shown and recorded.
@Suppress("DEPRECATION")
private fun Intent.readSendIntentException(): IntentSender.SendIntentException? =
  getParcelableExtra(
    ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION
  )

@Composable
private fun ScannerFailureDialog(
  detail: String,
  onRetry: () -> Unit,
  onUseFallback: () -> Unit,
) {
  AlertDialog(
    // Dismissing the dialog falls back to the CameraX dialog, as before.
    onDismissRequest = onUseFallback,
    title = { Text(stringResource(R.string.scanner_failure_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.scanner_failure_message))
        // The reason is technical English text, so it is always shown left-to-right.
        Text(
          text = detail,
          style =
            MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
              textDirection = TextDirection.Ltr,
              textAlign = TextAlign.Left,
            ),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onRetry, modifier = Modifier.testTag("scanner_failure_retry")) {
        Text(stringResource(R.string.scanner_failure_retry))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onUseFallback,
        modifier = Modifier.testTag("scanner_failure_use_fallback"),
      ) {
        Text(stringResource(R.string.scanner_failure_use_fallback))
      }
    },
  )
}

@Composable
private fun ScannerModuleDownloadingDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.scanner_module_downloading_title)) },
    text = { Text(stringResource(R.string.scanner_module_downloading_message)) },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("scanner_module_downloading_ok"),
      ) {
        Text(stringResource(R.string.scanner_module_downloading_ok))
      }
    },
  )
}
