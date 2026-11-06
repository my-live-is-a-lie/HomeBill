package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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
 *
 * The picker list holds only currencies the user added in Settings. It
 * starts empty: each entry carries a display name (currency and country,
 * for example "جنيه مصري") plus its symbol (for example "ج.م"). The list
 * is stored on the device as a JSON array; a corrupt value simply reads
 * back as an empty list.
 */

/** A currency the user added in Settings: display name and symbol. */
data class UserCurrency(val label: String, val symbol: String)

object CurrencyPreferenceStore {
  const val DEFAULT_CURRENCY = "ج.م"
  const val MAX_CURRENCY_LENGTH = 16
  const val MAX_LABEL_LENGTH = 60

  private const val PREFS_NAME = "homebill_prefs"
  private const val KEY_CURRENCY = "preferred_currency"
  private const val KEY_CURRENCIES = "user_currencies"

  @Volatile
  private var appContext: Context? = null

  private val _currencyFlow = MutableStateFlow(DEFAULT_CURRENCY)
  val currencyFlow: StateFlow<String> = _currencyFlow.asStateFlow()

  fun init(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
    _currencyFlow.value = readStored()
    // Other small on-device settings that must be ready at startup share this entry point.
    AssistantWebSearchStore.init(context)
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

  /** Currencies the user added in Settings, in the order they were added. */
  fun getCurrencies(context: Context? = null): List<UserCurrency> {
    if (context != null) {
      init(context)
    }
    val stored = prefs(context)?.getString(KEY_CURRENCIES, null)?.trim().orEmpty()
    if (stored.isBlank()) return emptyList()
    return runCatching {
      val array = JSONArray(stored)
      (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val label = item.optString("label", "").trim()
        val symbol = item.optString("symbol", "").trim()
        if (label.isBlank() || symbol.isBlank()) null else UserCurrency(label, symbol)
      }
    }.getOrDefault(emptyList())
  }

  private fun saveCurrencies(currencies: List<UserCurrency>, context: Context? = null) {
    val array = JSONArray()
    currencies.forEach { currency ->
      array.put(JSONObject().put("label", currency.label).put("symbol", currency.symbol))
    }
    prefs(context)?.edit()?.putString(KEY_CURRENCIES, array.toString())?.apply()
  }

  /**
   * Adds a user currency. Returns false when the label or symbol is blank,
   * over its length limit, or its symbol is already in the list.
   */
  fun addCurrency(label: String, symbol: String, context: Context? = null): Boolean {
    val cleanLabel = label.trim().take(MAX_LABEL_LENGTH)
    val cleanSymbol = symbol.trim().take(MAX_CURRENCY_LENGTH)
    if (cleanLabel.isBlank() || cleanSymbol.isBlank()) return false
    val current = getCurrencies(context)
    if (current.any { it.symbol == cleanSymbol }) return false
    saveCurrencies(current + UserCurrency(cleanLabel, cleanSymbol), context)
    return true
  }

  /**
   * Removes a user currency by symbol. Returns false when it was not in the
   * list. Removing it does not change the current default: the default
   * stays a valid symbol string even when it is no longer listed.
   */
  fun removeCurrency(symbol: String, context: Context? = null): Boolean {
    val current = getCurrencies(context)
    val updated = current.filterNot { it.symbol == symbol.trim() }
    if (updated.size == current.size) return false
    saveCurrencies(updated, context)
    return true
  }

  /** Display form for a currency symbol: "label symbol" when it is a saved currency, else the symbol. */
  fun displayCurrency(symbol: String, context: Context? = null): String {
    val saved = getCurrencies(context).firstOrNull { it.symbol == symbol.trim() }
    return if (saved != null) "${saved.label} ${saved.symbol}" else symbol
  }
}
