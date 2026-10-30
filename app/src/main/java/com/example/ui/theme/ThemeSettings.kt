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
 * Which color family an accent choice applies to. Both roles share the same palette of presets;
 * each role keeps its own saved choice. [defaultId] is the preset that matches the app's original
 * colors for that role (emerald for primary, amber for secondary), so choosing it changes nothing.
 */
enum class AccentRole(val defaultId: String) {
  PRIMARY("emerald"),
  SECONDARY("gold"),
}

/**
 * An accent ("لون التمييز") choice. Only one color per brightness is specified; the container and
 * "on" colors are derived from it.
 */
data class AccentPreset(
  val id: String,
  val nameAr: String,
  val lightPrimary: Color,
  val darkPrimary: Color,
) {
  /** The color shown in the picker for the current brightness. */
  fun swatch(dark: Boolean): Color = if (dark) darkPrimary else lightPrimary
}

object AccentPresets {
  val all: List<AccentPreset> =
    listOf(
      AccentPreset("emerald", "زمردي", EmeraldPrimaryLight, EmeraldPrimaryDark),
      AccentPreset("blue", "أزرق", Color(0xFF1D4ED8), Color(0xFF60A5FA)),
      AccentPreset("violet", "بنفسجي", Color(0xFF6D28D9), Color(0xFFA78BFA)),
      AccentPreset("pink", "وردي", Color(0xFFBE185D), Color(0xFFF472B6)),
      AccentPreset("red", "أحمر", Color(0xFFB91C1C), Color(0xFFF87171)),
      AccentPreset("orange", "برتقالي", Color(0xFFC2410C), Color(0xFFFB923C)),
      AccentPreset("gold", "ذهبي", AmberSecondaryLight, AmberSecondaryDark),
      AccentPreset("cyan", "سماوي", Color(0xFF0E7490), Color(0xFF22D3EE)),
    )

  /** The preset with [id], or the default preset of [role] when [id] is missing or unknown. */
  fun byId(id: String?, role: AccentRole): AccentPreset =
    all.firstOrNull { it.id == id } ?: all.first { it.id == role.defaultId }
}

/**
 * Persists the theme choices and exposes them as Compose state, so changing one recomposes
 * [MyApplicationTheme] immediately. Values are stored in SharedPreferences and loaded once, the
 * first time the theme is composed.
 */
object ThemeSettingsStore {
  private const val PREFS_NAME = "homebill_theme_prefs"
  private const val KEY_MODE = "theme_mode"
  private const val KEY_ACCENT = "accent_id"
  private const val KEY_SECONDARY_ACCENT = "secondary_accent_id"
  private const val KEY_DYNAMIC = "dynamic_color"

  private var loaded = false

  var mode by mutableStateOf(ThemeMode.SYSTEM)
    private set

  /** Primary accent preset id. */
  var accentId by mutableStateOf(AccentRole.PRIMARY.defaultId)
    private set

  var secondaryAccentId by mutableStateOf(AccentRole.SECONDARY.defaultId)
    private set

  /** Material You: take colors from the phone's wallpaper (Android 12+). */
  var dynamicColor by mutableStateOf(false)
    private set

  fun ensureLoaded(context: Context) {
    if (loaded) return
    val prefs = prefs(context)
    mode = ThemeMode.fromKey(prefs.getString(KEY_MODE, null))
    accentId = AccentPresets.byId(prefs.getString(KEY_ACCENT, null), AccentRole.PRIMARY).id
    secondaryAccentId =
      AccentPresets.byId(prefs.getString(KEY_SECONDARY_ACCENT, null), AccentRole.SECONDARY).id
    dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false)
    loaded = true
  }

  fun accentIdFor(role: AccentRole): String =
    when (role) {
      AccentRole.PRIMARY -> accentId
      AccentRole.SECONDARY -> secondaryAccentId
    }

  fun setMode(context: Context, newMode: ThemeMode) {
    mode = newMode
    prefs(context).edit().putString(KEY_MODE, newMode.key).apply()
  }

  fun setAccent(context: Context, role: AccentRole, preset: AccentPreset) {
    when (role) {
      AccentRole.PRIMARY -> {
        accentId = preset.id
        prefs(context).edit().putString(KEY_ACCENT, preset.id).apply()
      }
      AccentRole.SECONDARY -> {
        secondaryAccentId = preset.id
        prefs(context).edit().putString(KEY_SECONDARY_ACCENT, preset.id).apply()
      }
    }
  }

  fun setDynamicColor(context: Context, enabled: Boolean) {
    dynamicColor = enabled
    prefs(context).edit().putBoolean(KEY_DYNAMIC, enabled).apply()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

/**
 * Replaces the [role] color family (primary or secondary) with [accent]. When [accent] is the
 * role's default preset the scheme is returned untouched, so the original colors stay exact.
 */
internal fun ColorScheme.withAccent(accent: AccentPreset, role: AccentRole, dark: Boolean): ColorScheme {
  if (accent.id == role.defaultId) return this
  val tone = accent.swatch(dark)
  val on = if (dark) lerp(tone, Color.Black, 0.85f) else Color.White
  val container = if (dark) lerp(tone, Color.Black, 0.65f) else lerp(tone, Color.White, 0.82f)
  val onContainer = if (dark) lerp(tone, Color.White, 0.8f) else lerp(tone, Color.Black, 0.75f)
  return when (role) {
    AccentRole.PRIMARY ->
      copy(
        primary = tone,
        onPrimary = on,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
      )
    AccentRole.SECONDARY ->
      copy(
        secondary = tone,
        onSecondary = on,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
      )
  }
}

/** The hero card follows the primary color: a deep shade of it. */
internal fun UtilityColorPalette.withHeroFrom(primary: Color, dark: Boolean): UtilityColorPalette =
  copy(
    heroGradientStart = lerp(primary, Color.Black, if (dark) 0.7f else 0.35f),
    heroGradientEnd = lerp(primary, Color.Black, if (dark) 0.55f else 0.2f),
  )
