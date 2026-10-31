package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom outlined icons for the assistant screen, drawn as stroke-only vectors so they pick up
 * the tint passed to Icon(...). Both match the Gemini app: a rounded funnel for the filter and
 * two equal horizontal bars for the side drawer button.
 */
object AssistantIcons {
  /** Two equal, rounded horizontal bars (drawer / sidebar button). */
  val Sidebar: ImageVector by lazy {
    ImageVector.Builder(
        name = "SidebarTwoLines",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(5f, 9.25f)
        lineTo(19f, 9.25f)
        moveTo(5f, 14.75f)
        lineTo(19f, 14.75f)
      }
      .build()
  }

  /** Outlined funnel with a rounded stem (sort / filter button). */
  val Filter: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilterFunnel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(5f, 5.5f)
        lineTo(19f, 5.5f)
        lineTo(14f, 12f)
        lineTo(14f, 17.5f)
        arcTo(2f, 2f, 0f, false, true, 10f, 17.5f)
        lineTo(10f, 12f)
        close()
      }
      .build()
  }
}
