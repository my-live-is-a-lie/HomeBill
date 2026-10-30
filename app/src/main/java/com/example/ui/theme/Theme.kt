package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
 * [ThemeMode.SYSTEM] (the default). An explicit light/dark/black choice, the primary and secondary
 * accents, and the dynamic-color switch all come from [ThemeSettingsStore].
 *
 * With dynamic color on (Android 12+) the whole scheme is taken from the phone's wallpaper and the
 * accent choices are ignored.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  ThemeSettingsStore.ensureLoaded(context)
  val mode = ThemeSettingsStore.mode
  val primaryAccent = AccentPresets.byId(ThemeSettingsStore.accentId, AccentRole.PRIMARY)
  val secondaryAccent =
    AccentPresets.byId(ThemeSettingsStore.secondaryAccentId, AccentRole.SECONDARY)
  val dynamicRequested = ThemeSettingsStore.dynamicColor
  val useDynamic = dynamicRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  val useDark =
    when (mode) {
      ThemeMode.SYSTEM -> darkTheme
      ThemeMode.LIGHT -> false
      ThemeMode.DARK,
      ThemeMode.BLACK -> true
    }

  val baseScheme =
    if (dynamicRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (useDark) {
      DarkColorScheme
    } else {
      LightColorScheme
    }
  val modeScheme = if (mode == ThemeMode.BLACK) baseScheme.asPureBlack() else baseScheme
  val colorScheme =
    if (useDynamic) {
      modeScheme
    } else {
      modeScheme
        .withAccent(primaryAccent, AccentRole.PRIMARY, useDark)
        .withAccent(secondaryAccent, AccentRole.SECONDARY, useDark)
    }

  val basePalette = if (useDark) DarkUtilityPalette else LightUtilityPalette
  val utilityPalette =
    if (useDynamic || primaryAccent.id != AccentRole.PRIMARY.defaultId) {
      basePalette.withHeroFrom(colorScheme.primary, useDark)
    } else {
      basePalette
    }

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
