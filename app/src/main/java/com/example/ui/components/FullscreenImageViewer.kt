package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import java.io.File

/**
 * Full-screen image viewer: pinch to zoom, drag to pan, double tap to zoom in/out, and an X button
 * to close it.
 */
@Composable
fun FullscreenImageViewer(imageFile: File, onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
      AsyncImage(
        model = imageFile,
        contentDescription = stringResource(R.string.bill_image_content_desc),
        contentScale = ContentScale.Fit,
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 8f)
                scale = newScale
                if (newScale <= 1.01f) {
                  offsetX = 0f
                  offsetY = 0f
                } else {
                  val maxX = size.width * (newScale - 1f) / 2f
                  val maxY = size.height * (newScale - 1f) / 2f
                  offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                  offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                }
              }
            }
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  if (scale > 1.01f) {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                  } else {
                    scale = 2.5f
                  }
                }
              )
            }
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
              translationX = offsetX
              translationY = offsetY
            },
      )

      IconButton(
        onClick = onDismiss,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(12.dp)
            .background(Color(0x99000000), CircleShape)
            .testTag("fullscreen_image_close_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(R.string.bill_image_close_desc),
          tint = Color.White,
        )
      }
    }
  }
}
