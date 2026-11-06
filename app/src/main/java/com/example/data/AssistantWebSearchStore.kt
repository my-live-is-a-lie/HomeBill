package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the assistant may use Google Search (Gemini's built-in search tool) to answer questions
 * that need live information such as weather, news or prices. Off by default: every search the
 * model decides to run is billed to the user's own Gemini key and makes that reply slower, so the
 * user opts in from Settings.
 *
 * Stored on the device only. [init] is called from [CurrencyPreferenceStore.init], which already
 * runs at startup and whenever Settings opens, so the value is ready before the first chat turn.
 */
object AssistantWebSearchStore {
  private const val PREFS_NAME = "homebill_prefs"
  private const val KEY_ENABLED = "assistant_web_search_enabled"

  @Volatile private var appContext: Context? = null

  private val _enabledFlow = MutableStateFlow(false)
  val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

  fun init(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
    _enabledFlow.value = prefs()?.getBoolean(KEY_ENABLED, false) ?: false
  }

  private fun prefs(context: Context? = null): SharedPreferences? {
    val ctx = context?.applicationContext ?: appContext ?: return null
    return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun isEnabled(): Boolean = _enabledFlow.value

  fun setEnabled(enabled: Boolean, context: Context? = null) {
    prefs(context)?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    _enabledFlow.value = enabled
  }
}
