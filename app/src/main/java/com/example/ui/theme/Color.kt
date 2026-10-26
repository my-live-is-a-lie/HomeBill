package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Theme Palette — Deep Emerald & Warm Amber Fintech
val EmeraldPrimaryLight = Color(0xFF0D5C54)
val OnEmeraldPrimaryLight = Color(0xFFFFFFFF)
val EmeraldContainerLight = Color(0xFFCCEBE6)
val OnEmeraldContainerLight = Color(0xFF052E2A)

val AmberSecondaryLight = Color(0xFFB45309)
val OnAmberSecondaryLight = Color(0xFFFFFFFF)
val AmberContainerLight = Color(0xFFFEF3C7)
val OnAmberContainerLight = Color(0xFF451A03)

val SlateTertiaryLight = Color(0xFF1E4E68)
val OnSlateTertiaryLight = Color(0xFFFFFFFF)
val SlateContainerLight = Color(0xFFD6ECF8)
val OnSlateContainerLight = Color(0xFF082738)

val BackgroundLight = Color(0xFFF4F8F7)
val OnBackgroundLight = Color(0xFF10201E)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF10201E)
val SurfaceVariantLight = Color(0xFFE6EFED)
val OnSurfaceVariantLight = Color(0xFF3F5551)
val OutlineLight = Color(0xFFB5C7C3)

val ErrorLight = Color(0xFFBA1A1A)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Dark Theme Palette
val EmeraldPrimaryDark = Color(0xFF5EEAD4)
val OnEmeraldPrimaryDark = Color(0xFF042F2B)
val EmeraldContainerDark = Color(0xFF114B45)
val OnEmeraldContainerDark = Color(0xFFCCFBF1)

val AmberSecondaryDark = Color(0xFFFBBF24)
val OnAmberSecondaryDark = Color(0xFF451A03)
val AmberContainerDark = Color(0xFF78350F)
val OnAmberContainerDark = Color(0xFFFEF3C7)

val SlateTertiaryDark = Color(0xFF7DD3FC)
val OnSlateTertiaryDark = Color(0xFF082F49)
val SlateContainerDark = Color(0xFF0C4A6E)
val OnSlateContainerDark = Color(0xFFE0F2FE)

val BackgroundDark = Color(0xFF0B1514)
val OnBackgroundDark = Color(0xFFE2ECEA)
val SurfaceDark = Color(0xFF12211F)
val OnSurfaceDark = Color(0xFFE2ECEA)
val SurfaceVariantDark = Color(0xFF1D312E)
val OnSurfaceVariantDark = Color(0xFFA8BEBA)
val OutlineDark = Color(0xFF35504C)

// Semantic Utility Accents
@Immutable
data class UtilityColorPalette(
  val gasAccent: Color,
  val gasContainer: Color,
  val electricityAccent: Color,
  val electricityContainer: Color,
  val waterAccent: Color,
  val waterContainer: Color,
  val internetAccent: Color,
  val internetContainer: Color,
  val telecomAccent: Color,
  val telecomContainer: Color,
  val shoppingAccent: Color,
  val shoppingContainer: Color,
  val diningAccent: Color,
  val diningContainer: Color,
  val maintenanceAccent: Color,
  val maintenanceContainer: Color,
  val fuelAccent: Color,
  val fuelContainer: Color,
  val otherAccent: Color,
  val otherContainer: Color,
  val spikeWarningAccent: Color,
  val spikeWarningContainer: Color,
  val savingsPositiveAccent: Color,
  val savingsPositiveContainer: Color,
  val heroGradientStart: Color,
  val heroGradientEnd: Color,
)

val LightUtilityPalette =
  UtilityColorPalette(
    gasAccent = Color(0xFFEA580C),
    gasContainer = Color(0xFFFFEDD5),
    electricityAccent = Color(0xFFD97706),
    electricityContainer = Color(0xFFFEF3C7),
    waterAccent = Color(0xFF0284C7),
    waterContainer = Color(0xFFE0F2FE),
    internetAccent = Color(0xFF4F46E5),
    internetContainer = Color(0xFFE0E7FF),
    telecomAccent = Color(0xFF7C3AED),
    telecomContainer = Color(0xFFEDE9FE),
    shoppingAccent = Color(0xFFDB2777),
    shoppingContainer = Color(0xFFFCE7F3),
    diningAccent = Color(0xFFE11D48),
    diningContainer = Color(0xFFFFE4E6),
    maintenanceAccent = Color(0xFF0F766E),
    maintenanceContainer = Color(0xFFCCFBF1),
    fuelAccent = Color(0xFF0369A1),
    fuelContainer = Color(0xFFE0F2FE),
    otherAccent = Color(0xFF0D5C54),
    otherContainer = Color(0xFFCCFBF1),
    spikeWarningAccent = Color(0xFFDC2626),
    spikeWarningContainer = Color(0xFFFEE2E2),
    savingsPositiveAccent = Color(0xFF059669),
    savingsPositiveContainer = Color(0xFFD1FAE5),
    heroGradientStart = Color(0xFF09423C),
    heroGradientEnd = Color(0xFF136F63),
  )

val DarkUtilityPalette =
  UtilityColorPalette(
    gasAccent = Color(0xFFFB923C),
    gasContainer = Color(0xFF431407),
    electricityAccent = Color(0xFFFBBF24),
    electricityContainer = Color(0xFF451A03),
    waterAccent = Color(0xFF38BDF8),
    waterContainer = Color(0xFF082F49),
    internetAccent = Color(0xFF818CF8),
    internetContainer = Color(0xFF1E1B4B),
    telecomAccent = Color(0xFFA78BFA),
    telecomContainer = Color(0xFF2E1065),
    shoppingAccent = Color(0xFFF472B6),
    shoppingContainer = Color(0xFF500724),
    diningAccent = Color(0xFFFB7185),
    diningContainer = Color(0xFF4C0519),
    maintenanceAccent = Color(0xFF2DD4BF),
    maintenanceContainer = Color(0xFF134E4A),
    fuelAccent = Color(0xFF7DD3FC),
    fuelContainer = Color(0xFF0C4A6E),
    otherAccent = Color(0xFF2DD4BF),
    otherContainer = Color(0xFF042F2E),
    spikeWarningAccent = Color(0xFFF87185),
    spikeWarningContainer = Color(0xFF450A0A),
    savingsPositiveAccent = Color(0xFF34D399),
    savingsPositiveContainer = Color(0xFF064E3B),
    heroGradientStart = Color(0xFF0D3D38),
    heroGradientEnd = Color(0xFF165B52),
  )

val LocalUtilityPalette = staticCompositionLocalOf { LightUtilityPalette }
