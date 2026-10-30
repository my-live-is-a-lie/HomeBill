package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * App theme choice.
 *  - [SYSTEM]: follow the device's light/dark setting (the behavior before this setting existed).
 *  - [LIGHT]: light theme.
 *  - [DARK]: the app's dark theme (dark green-gray surfaces).
 *  - [BLACK]: pure-black dark theme (useful on OLED screens).
 */
enum class ThemeMode(val key: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark"),
  BLACK("black");

  companion object {
    fun fromKey(key: String?): ThemeMode = values().firstOrNull { it.key == key } ?: SYSTEM
  }
}

/**
 * An accent ("لون التمييز") choice. Only the primary color is specified; its container and
 * "on" colors are derived. The default preset keeps the app's original emerald colors untouched.
 */
data class AccentPreset(
  val id: String,
  val nameAr: String,
  val lightPrimary: Color,
  val darkPrimary: Color,
) {
  val isDefault: Boolean
    get() = id == AccentPresets.DEFAULT_ID

  /** The color shown in the picker for the current brightness. */
  fun swatch(dark: Boolean): Color = if (dark) darkPrimary else lightPrimary
}

object AccentPresets {
  const val DEFAULT_ID = "emerald"

  val all: List<AccentPreset> =
    listOf(
      AccentPreset(DEFAULT_ID, "زمردي", EmeraldPrimaryLight, EmeraldPrimaryDark),
      AccentPreset("blue", "أزرق", Color(0xFF1D4ED8), Color(0xFF60A5FA)),
      AccentPreset("violet", "بنفسجي", Color(0xFF6D28D9), Color(0xFFA78BFA)),
      AccentPreset("pink", "وردي", Color(0xFFBE185D), Color(0xFFF472B6)),
      AccentPreset("red", "أحمر", Color(0xFFB91C1C), Color(0xFFF87171)),
      AccentPreset("orange", "برتقالي", Color(0xFFC2410C), Color(0xFFFB923C)),
      AccentPreset("gold", "ذهبي", Color(0xFFA16207), Color(0xFFFACC15)),
      AccentPreset("cyan", "سماوي", Color(0xFF0E7490), Color(0xFF22D3EE)),
    )

  fun byId(id: String?): AccentPreset = all.firstOrNull { it.id == id } ?: all.first()
}

/**
 * Persists the theme mode and accent choice and exposes them as Compose state, so changing one
 * recomposes [MyApplicationTheme] immediately. Values are stored in SharedPreferences and loaded
 * once, the first time the theme is composed.
 */
object ThemeSettingsStore {
  private const val PREFS_NAME = "homebill_theme_prefs"
  private const val KEY_MODE = "theme_mode"
  private const val KEY_ACCENT = "accent_id"

  private var loaded = false

  var mode by mutableStateOf(ThemeMode.SYSTEM)
    private set

  var accentId by mutableStateOf(AccentPresets.DEFAULT_ID)
    private set

  fun ensureLoaded(context: Context) {
    if (loaded) return
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    mode = ThemeMode.fromKey(prefs.getString(KEY_MODE, null))
    accentId = AccentPresets.byId(prefs.getString(KEY_ACCENT, null)).id
    loaded = true
  }

  fun setMode(context: Context, newMode: ThemeMode) {
    mode = newMode
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_MODE, newMode.key)
      .apply()
  }

  fun setAccent(context: Context, preset: AccentPreset) {
    accentId = preset.id
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_ACCENT, preset.id)
      .apply()
  }
}

/** Pure-black variant of the dark scheme: black background, near-black surfaces. */
internal fun ColorScheme.asPureBlack(): ColorScheme =
  copy(
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF1A1A1A),
    outline = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFB4B4B4),
  )

/** Replaces the primary color family with [accent]; the default accent returns the scheme as is. */
internal fun ColorScheme.withAccent(accent: AccentPreset, dark: Boolean): ColorScheme {
  if (accent.isDefault) return this
  return if (dark) {
    val p = accent.darkPrimary
    copy(
      primary = p,
      onPrimary = lerp(p, Color.Black, 0.85f),
      primaryContainer = lerp(p, Color.Black, 0.65f),
      onPrimaryContainer = lerp(p, Color.White, 0.8f),
    )
  } else {
    val p = accent.lightPrimary
    copy(
      primary = p,
      onPrimary = Color.White,
      primaryContainer = lerp(p, Color.White, 0.82f),
      onPrimaryContainer = lerp(p, Color.Black, 0.75f),
    )
  }
}

/** The hero card follows the accent: a deep shade of it. The default accent keeps its colors. */
internal fun UtilityColorPalette.withAccent(accent: AccentPreset, dark: Boolean): UtilityColorPalette {
  if (accent.isDefault) return this
  val p = accent.swatch(dark)
  return copy(
    heroGradientStart = lerp(p, Color.Black, if (dark) 0.7f else 0.35f),
    heroGradientEnd = lerp(p, Color.Black, if (dark) 0.55f else 0.2f),
  )
}
