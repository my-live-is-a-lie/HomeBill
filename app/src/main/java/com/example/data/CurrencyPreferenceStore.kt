package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the user's preferred currency on the device only.
 *
 * The value is a short display string (for example "ج.م" or "ر.س"), not an
 * ISO code, and it is never sent to Firebase: it only seeds the currency of
 * newly created bills and the fallbacks shown when a category has no bills
 * yet. Bills already saved keep the currency stored on them.
 *
 * The factory default is the Egyptian pound. The user can change it in
 * Settings at any time; the change applies to future bills immediately
 * through [currencyFlow].
 *
 * Automatic detection: when a scan (image or voice) recognizes a currency
 * and the user has never picked one manually, [autoSetCurrency] adopts the
 * recognized currency as the new default. The moment the user picks a
 * currency in Settings, that manual choice locks the default: later
 * recognitions no longer change it. Choosing the automatic option in
 * Settings clears the lock and re-enables detection.
 */
object CurrencyPreferenceStore {
  const val DEFAULT_CURRENCY = "ج.م"
  const val MAX_CURRENCY_LENGTH = 16

  /** Quick-pick list shown in the Settings dialog, plus a free-text option. */
  val PRESET_CURRENCIES =
    listOf(
      "ج.م",
      "ر.س",
      "د.إ",
      "د.ك",
      "ر.ق",
      "د.ج",
      "د.م",
      "ل.ل",
      "د.ت",
      "$",
      "€",
      "£",
    )

  private const val PREFS_NAME = "homebill_prefs"
  private const val KEY_CURRENCY = "preferred_currency"
  private const val KEY_MANUAL = "preferred_currency_manual"

  @Volatile
  private var appContext: Context? = null

  private val _currencyFlow = MutableStateFlow(DEFAULT_CURRENCY)
  val currencyFlow: StateFlow<String> = _currencyFlow.asStateFlow()

  fun init(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
    _currencyFlow.value = readStored()
  }

  private fun prefs(context: Context? = null): SharedPreferences? {
    val ctx = context?.applicationContext ?: appContext ?: return null
    return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  private fun readStored(): String {
    val stored = prefs()?.getString(KEY_CURRENCY, null)?.trim().orEmpty()
    return stored.ifBlank { DEFAULT_CURRENCY }.take(MAX_CURRENCY_LENGTH)
  }

  fun getCurrency(context: Context? = null): String {
    if (context != null) {
      init(context)
    }
    return _currencyFlow.value
  }

  /** True once the user has picked a currency in Settings. */
  fun isManuallySet(context: Context? = null): Boolean {
    if (context != null) {
      init(context)
    }
    return prefs(context)?.getBoolean(KEY_MANUAL, false) ?: false
  }

  /**
   * Manual choice from Settings. This locks the default: later automatic
   * detections no longer change it until the user re-enables automatic mode.
   */
  fun setCurrency(value: String, context: Context? = null, manual: Boolean = true) {
    val cleaned = value.trim().take(MAX_CURRENCY_LENGTH)
    if (cleaned.isBlank()) return
    prefs(context)?.edit()?.putString(KEY_CURRENCY, cleaned)?.putBoolean(KEY_MANUAL, manual)?.apply()
    _currencyFlow.value = cleaned
  }

  /**
   * Adopts a currency recognized by a scan (image or voice) as the new
   * default, but only while the user has never picked one manually. A
   * manual choice always wins over recognition; this call is a no-op then.
   * It never rewrites any saved bill, only the default for future bills.
   */
  fun autoSetCurrency(value: String, context: Context? = null) {
    if (isManuallySet(context)) return
    val cleaned = value.trim().take(MAX_CURRENCY_LENGTH)
    if (cleaned.isBlank() || cleaned == _currencyFlow.value) return
    prefs(context)?.edit()?.putString(KEY_CURRENCY, cleaned)?.apply()
    _currencyFlow.value = cleaned
  }

  /** Re-enables automatic detection; the current value stays until the next recognition. */
  fun clearManualChoice(context: Context? = null) {
    prefs(context)?.edit()?.putBoolean(KEY_MANUAL, false)?.apply()
  }
}
