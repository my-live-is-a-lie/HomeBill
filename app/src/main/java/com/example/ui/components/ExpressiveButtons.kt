package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/*
 * Buttons with the press feel of Material 3 Expressive: when a finger goes down the button shrinks
 * a little and its corners change shape (a pill becomes a softer rounded rectangle), and on
 * release it springs back with a small bounce. Built on the regular Material 3 buttons, so colors,
 * ripple and accessibility are unchanged; no experimental Material API is needed.
 */

/**
 * 0 when released, 1 while pressed, animated with a springy overshoot so the release bounces.
 */
@Composable
private fun pressFraction(source: MutableInteractionSource): Float {
  val pressed by source.collectIsPressedAsState()
  val fraction by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
      label = "expressive_press",
    )
  return fraction
}

/**
 * A filled button (or a tonal one when [tonal] is true) with the expressive press animation.
 * [restingCorner] is the corner radius at rest: half of the button's height gives a pill.
 */
@Composable
fun ExpressiveButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tonal: Boolean = false,
  restingCorner: Dp = 26.dp,
  pressedCorner: Dp = 12.dp,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  content: @Composable RowScope.() -> Unit,
) {
  val source = remember { MutableInteractionSource() }
  val fraction = pressFraction(source)
  val animatedModifier =
    modifier.graphicsLayer {
      val scale = 1f - 0.05f * fraction
      scaleX = scale
      scaleY = scale
    }
  val shape = RoundedCornerShape(lerp(restingCorner, pressedCorner, fraction).coerceAtLeast(0.dp))
  if (tonal) {
    FilledTonalButton(
      onClick = onClick,
      modifier = animatedModifier,
      enabled = enabled,
      shape = shape,
      contentPadding = contentPadding,
      interactionSource = source,
      content = content,
    )
  } else {
    Button(
      onClick = onClick,
      modifier = animatedModifier,
      enabled = enabled,
      shape = shape,
      contentPadding = contentPadding,
      interactionSource = source,
      content = content,
    )
  }
}

/**
 * A floating action button with the expressive press animation: it shrinks and its corners round
 * off while pressed, then springs back.
 */
@Composable
fun ExpressiveFab(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.primary,
  contentColor: Color = MaterialTheme.colorScheme.onPrimary,
  restingCorner: Dp = 16.dp,
  pressedCorner: Dp = 28.dp,
  content: @Composable () -> Unit,
) {
  val source = remember { MutableInteractionSource() }
  val fraction = pressFraction(source)
  FloatingActionButton(
    onClick = onClick,
    modifier =
      modifier.graphicsLayer {
        val scale = 1f - 0.08f * fraction
        scaleX = scale
        scaleY = scale
      },
    shape = RoundedCornerShape(lerp(restingCorner, pressedCorner, fraction).coerceAtLeast(0.dp)),
    containerColor = containerColor,
    contentColor = contentColor,
    interactionSource = source,
    content = content,
  )
}
