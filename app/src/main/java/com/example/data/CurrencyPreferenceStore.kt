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
 * How the default is determined: there is no lock. The default is always
 * the last value determined, whether the user picked it in Settings or a
 * scan (image or voice) recognized one. A bill that carries its own
 * recognized currency always keeps it; a bill without one falls back to
 * this default. Saved bills are never rewritten by any of this.
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

  /**
   * Manual choice from Settings. This simply sets the current default; a
   * later recognition may determine a different default afterwards.
   */
  fun setCurrency(value: String, context: Context? = null) {
    val cleaned = value.trim().take(MAX_CURRENCY_LENGTH)
    if (cleaned.isBlank()) return
    prefs(context)?.edit()?.putString(KEY_CURRENCY, cleaned)?.apply()
    _currencyFlow.value = cleaned
  }

  /**
   * Adopts a currency recognized by a scan (image or voice) as the new
   * default for future bills. There is no lock: the default is always the
   * last value determined, whether by the user in Settings or by a
   * recognition. It never rewrites any saved bill, and a bill that carries
   * its own recognized currency always keeps it.
   */
  fun autoSetCurrency(value: String, context: Context? = null) {
    val cleaned = value.trim().take(MAX_CURRENCY_LENGTH)
    if (cleaned.isBlank() || cleaned == _currencyFlow.value) return
    prefs(context)?.edit()?.putString(KEY_CURRENCY, cleaned)?.apply()
    _currencyFlow.value = cleaned
  }
}
