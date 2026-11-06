package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "The answer is being prepared" indicator for the assistant chat, in the style of modern AI apps
 * instead of a plain spinner:
 *  - a gradient four-point sparkle that pulses and twists, with two small stars twinkling around
 *    it and a soft glow behind;
 *  - a status label with a light sweep running across the text;
 *  - three placeholder lines that shimmer one after another, as a preview of the coming answer.
 *
 * Everything runs from infinite transitions, so it costs nothing while it is not on screen.
 */
@Composable
fun AiThinkingIndicator(label: String, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "ai_thinking")

  // One sweep drives the label and the three placeholder lines (the lines are phase-shifted).
  val sweep by
    transition.animateFloat(
      initialValue = -500f,
      targetValue = 1500f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1900, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "shimmer_sweep",
    )

  val labelBase = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
  val labelHighlight = MaterialTheme.colorScheme.onSurface
  val labelBrush =
    Brush.linearGradient(
      colors = listOf(labelBase, labelHighlight, labelBase),
      start = Offset(sweep, 0f),
      end = Offset(sweep + 420f, 0f),
    )

  val barBase = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
  val barGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

  Row(
    verticalAlignment = Alignment.Top,
    modifier = modifier.testTag("typing_indicator"),
  ) {
    AiSparkleLoader(loaderSize = 36.dp)
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(brush = labelBrush),
        modifier = Modifier.padding(top = 8.dp),
      )
      Spacer(modifier = Modifier.height(12.dp))
      ShimmerBar(widthFraction = 1f, sweep = sweep, phaseShift = 0f, base = barBase, glow = barGlow)
      Spacer(modifier = Modifier.height(8.dp))
      ShimmerBar(widthFraction = 0.82f, sweep = sweep, phaseShift = 160f, base = barBase, glow = barGlow)
      Spacer(modifier = Modifier.height(8.dp))
      ShimmerBar(widthFraction = 0.5f, sweep = sweep, phaseShift = 320f, base = barBase, glow = barGlow)
    }
  }
}

/** One placeholder line whose light sweep starts [phaseShift] px later than the previous line. */
@Composable
private fun ShimmerBar(
  widthFraction: Float,
  sweep: Float,
  phaseShift: Float,
  base: Color,
  glow: Color,
) {
  val brush =
    Brush.linearGradient(
      colors = listOf(base, glow, base),
      start = Offset(sweep - phaseShift, 0f),
      end = Offset(sweep - phaseShift + 420f, 0f),
    )
  Spacer(
    modifier =
      Modifier.fillMaxWidth(widthFraction)
        .height(10.dp)
        .background(brush = brush, shape = RoundedCornerShape(5.dp))
  )
}

/**
 * The animated mark: a gradient four-point star that breathes and twists a quarter turn (a star
 * looks the same every 90 degrees, so the loop has no visible seam), two small stars that twinkle
 * out of step, and a glow that pulses with the star. It never spins like a progress circle.
 */
@Composable
private fun AiSparkleLoader(loaderSize: Dp, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "sparkle_loader")
  val twist by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 90f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "sparkle_twist",
    )
  val pulse by
    transition.animateFloat(
      initialValue = 0.82f,
      targetValue = 1.08f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "sparkle_pulse",
    )
  val twinkleA by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "sparkle_twinkle_a",
    )
  val twinkleB by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
          initialStartOffset = StartOffset(450),
        ),
      label = "sparkle_twinkle_b",
    )

  val colorA = MaterialTheme.colorScheme.primary
  val colorB = MaterialTheme.colorScheme.tertiary
  val colorC = MaterialTheme.colorScheme.secondary

  Canvas(modifier = modifier.size(loaderSize)) {
    val s = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)

    // Soft glow behind the star.
    drawCircle(
      brush =
        Brush.radialGradient(
          colors = listOf(colorA.copy(alpha = 0.32f * pulse), Color.Transparent),
          center = center,
          radius = s * 0.5f,
        ),
      radius = s * 0.5f,
      center = center,
    )

    // Main star: breathes and twists a quarter turn per loop.
    val mainRadius = s * 0.30f * pulse
    rotate(degrees = twist, pivot = center) {
      drawPath(
        path = sparklePath(center, mainRadius),
        brush =
          Brush.linearGradient(
            colors = listOf(colorA, colorB, colorC),
            start = Offset(center.x - mainRadius, center.y - mainRadius),
            end = Offset(center.x + mainRadius, center.y + mainRadius),
          ),
      )
    }

    // Two small stars twinkling out of step.
    drawPath(
      path = sparklePath(Offset(center.x + s * 0.34f, center.y - s * 0.34f), s * 0.13f * twinkleA),
      color = colorB.copy(alpha = twinkleA),
    )
    drawPath(
      path = sparklePath(Offset(center.x - s * 0.36f, center.y + s * 0.32f), s * 0.10f * twinkleB),
      color = colorC.copy(alpha = twinkleB),
    )
  }
}

/**
 * A four-point star with concave sides. The curve control points sit a little off the center
 * ([fullness] of the radius) so the star is slender but not razor thin.
 */
private fun sparklePath(center: Offset, radius: Float, fullness: Float = 0.12f): Path {
  val d = radius * fullness
  return Path().apply {
    moveTo(center.x, center.y - radius)
    cubicTo(
      center.x + d, center.y - d,
      center.x + d, center.y - d,
      center.x + radius, center.y,
    )
    cubicTo(
      center.x + d, center.y + d,
      center.x + d, center.y + d,
      center.x, center.y + radius,
    )
    cubicTo(
      center.x - d, center.y + d,
      center.x - d, center.y + d,
      center.x - radius, center.y,
    )
    cubicTo(
      center.x - d, center.y - d,
      center.x - d, center.y - d,
      center.x, center.y - radius,
    )
    close()
  }
}
