package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = OnEmeraldPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = OnAmberSecondaryLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = SlateTertiaryLight,
    onTertiary = OnSlateTertiaryLight,
    tertiaryContainer = SlateContainerLight,
    onTertiaryContainer = OnSlateContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberSecondaryDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = SlateTertiaryDark,
    onTertiary = OnSlateTertiaryDark,
    tertiaryContainer = SlateContainerDark,
    onTertiaryContainer = OnSlateContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
  )

val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

/**
 * App theme. [darkTheme] is the device's setting and is used only while the user's theme choice is
 * [ThemeMode.SYSTEM] (the default); an explicit light/dark/black choice and the accent color come
 * from [ThemeSettingsStore].
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  ThemeSettingsStore.ensureLoaded(context)
  val mode = ThemeSettingsStore.mode
  val accent = AccentPresets.byId(ThemeSettingsStore.accentId)

  val useDark =
    when (mode) {
      ThemeMode.SYSTEM -> darkTheme
      ThemeMode.LIGHT -> false
      ThemeMode.DARK,
      ThemeMode.BLACK -> true
    }
  val baseScheme = if (useDark) DarkColorScheme else LightColorScheme
  val colorScheme =
    (if (mode == ThemeMode.BLACK) baseScheme.asPureBlack() else baseScheme)
      .withAccent(accent, useDark)
  val utilityPalette =
    (if (useDark) DarkUtilityPalette else LightUtilityPalette).withAccent(accent, useDark)

  // Keep the status/navigation bar icons readable when the app theme differs from the system's.
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = view.context.findActivity()?.window ?: return@SideEffect
      val controller = WindowCompat.getInsetsController(window, view)
      controller.isAppearanceLightStatusBars = !useDark
      controller.isAppearanceLightNavigationBars = !useDark
    }
  }

  CompositionLocalProvider(
    LocalLayoutDirection provides LayoutDirection.Rtl,
    LocalUtilityPalette provides utilityPalette,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = AppShapes,
      content = content,
    )
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
