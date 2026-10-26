package com.example.ui.components

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import java.io.File

@Composable
fun CameraXBillCaptureDialog(
  onImageCaptured: (Uri) -> Unit,
  onUseSystemCamera: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
  var boundCamera by remember { mutableStateOf<Camera?>(null) }
  var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
  var isTorchEnabled by remember { mutableStateOf(false) }
  var isCapturing by remember { mutableStateOf(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }

  val previewView = remember {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  val fallbackErrorText = stringResource(R.string.camerax_unavailable_error)

  LaunchedEffect(lifecycleOwner) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
      {
        try {
          val provider = providerFuture.get()
          cameraProviderRef = provider

          val previewUseCase =
            Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

          val captureUseCase =
            ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
              .build()

          val selector =
            when {
              provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                CameraSelector.DEFAULT_BACK_CAMERA
              provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                CameraSelector.DEFAULT_FRONT_CAMERA
              else -> null
            }

          if (selector == null) {
            cameraError = fallbackErrorText
          } else {
            provider.unbindAll()
            boundCamera =
              provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, captureUseCase)
            imageCapture = captureUseCase
            cameraError = null
          }
        } catch (e: Exception) {
          Log.w("CameraXBillCapture", "Failed to bind CameraX use cases", e)
          cameraError = fallbackErrorText
        }
      },
      ContextCompat.getMainExecutor(context),
    )
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        cameraProviderRef?.unbindAll()
      } catch (_: Exception) {}
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    BackHandler(onBack = onDismiss)

    val frameAccentColor = MaterialTheme.colorScheme.secondaryContainer
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim)
          .testTag("camerax_scanner_dialog")
    ) {
      if (cameraError == null) {
        AndroidView(
          factory = { previewView },
          modifier = Modifier.fillMaxSize(),
        )
        DocumentFramingOverlay(accentColor = frameAccentColor)
      } else {
        Column(
          modifier = Modifier.align(Alignment.Center).padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = cameraError.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(16.dp))
          OutlinedButton(
            onClick = {
              onDismiss()
              onUseSystemCamera()
            },
            modifier = Modifier.testTag("camerax_fallback_button"),
          ) {
            Text(
              text = stringResource(R.string.camerax_system_camera_fallback),
              color = MaterialTheme.colorScheme.inverseOnSurface,
            )
          }
        }
      }

      Column(
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // Top Action Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledIconButton(
            onClick = onDismiss,
            colors =
              IconButtonDefaults.filledIconButtonColors(
                containerColor = scrimColor,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              ),
            modifier = Modifier.size(48.dp).testTag("camerax_close_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.camerax_close_desc),
            )
          }

          Surface(
            color = scrimColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
          ) {
            Text(
              text = stringResource(R.string.camerax_guide_hint),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.inverseOnSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }

          val hasFlash = boundCamera?.cameraInfo?.hasFlashUnit() == true
          FilledIconButton(
            onClick = {
              if (hasFlash) {
                val next = !isTorchEnabled
                isTorchEnabled = next
                boundCamera?.cameraControl?.enableTorch(next)
              }
            },
            enabled = hasFlash,
            colors =
              IconButtonDefaults.filledIconButtonColors(
                containerColor = scrimColor,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              ),
            modifier = Modifier.size(48.dp).testTag("camerax_flash_button"),
          ) {
            Icon(
              imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
              contentDescription = stringResource(R.string.camerax_flash_desc),
            )
          }
        }

        // Bottom Capture Controls
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (cameraError == null) {
            FilledIconButton(
              onClick = {
                val capture = imageCapture ?: return@FilledIconButton
                if (isCapturing) return@FilledIconButton
                isCapturing = true
                val photoFile = File.createTempFile("bill_camerax_", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                capture.takePicture(
                  outputOptions,
                  ContextCompat.getMainExecutor(context),
                  object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                      isCapturing = false
                      val savedUri =
                        FileProvider.getUriForFile(
                          context,
                          "${context.packageName}.fileprovider",
                          photoFile,
                        )
                      onImageCaptured(savedUri)
                    }

                    override fun onError(exception: ImageCaptureException) {
                      Log.e("CameraXBillCapture", "Photo capture failed", exception)
                      isCapturing = false
                      cameraError = fallbackErrorText
                    }
                  },
                )
              },
              enabled = !isCapturing && imageCapture != null,
              shape = CircleShape,
              colors =
                IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier =
                Modifier.size(76.dp)
                  .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  .testTag("camerax_shutter_button"),
            ) {
              if (isCapturing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(28.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.5.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Default.CameraAlt,
                  contentDescription = stringResource(R.string.camerax_shutter_desc),
                  modifier = Modifier.size(34.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          TextButton(
            onClick = {
              onDismiss()
              onUseSystemCamera()
            },
            modifier = Modifier.testTag("camerax_use_system_camera_button"),
          ) {
            Text(
              text = stringResource(R.string.camerax_system_camera_fallback),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.inverseOnSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DocumentFramingOverlay(accentColor: Color) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val frameWidth = size.width * 0.82f
    val frameHeight = (size.height * 0.62f).coerceAtMost(frameWidth * 1.45f)
    val left = (size.width - frameWidth) / 2f
    val top = (size.height - frameHeight) / 2f
    val right = left + frameWidth
    val bottom = top + frameHeight
    val corner = (frameWidth * 0.1f).coerceIn(28f, 64f)
    val stroke = 4.dp.toPx()

    // Top-left corner
    drawLine(accentColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
    drawLine(accentColor, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)

    // Top-right corner
    drawLine(accentColor, Offset(right, top), Offset(right - corner, top), stroke, StrokeCap.Round)
    drawLine(accentColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)

    // Bottom-left corner
    drawLine(
      accentColor,
      Offset(left, bottom),
      Offset(left + corner, bottom),
      stroke,
      StrokeCap.Round,
    )
    drawLine(
      accentColor,
      Offset(left, bottom),
      Offset(left, bottom - corner),
      stroke,
      StrokeCap.Round,
    )

    // Bottom-right corner
    drawLine(
      accentColor,
      Offset(right, bottom),
      Offset(right - corner, bottom),
      stroke,
      StrokeCap.Round,
    )
    drawLine(
      accentColor,
      Offset(right, bottom),
      Offset(right, bottom - corner),
      stroke,
      StrokeCap.Round,
    )
  }
}
