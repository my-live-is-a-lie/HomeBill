package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the end-user's own Gemini API key on the device only.
 *
 * The key is never sent to Firebase, never committed to the repo, and never
 * baked into the APK at build time. It lives in the app's private
 * SharedPreferences, which other apps cannot read. The developer's
 * BuildConfig key is intentionally NOT used as a fallback: AI features
 * require the user's own key, entered first in Settings.
 */
object UserGeminiKeyStore {
  private const val PREFS_NAME = "homebill_user_keys"
  private const val KEY_GEMINI_API_KEY = "gemini_api_key"

  @Volatile
  private var appContext: Context? = null

  fun init(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
  }

  private fun prefs(context: Context? = null): SharedPreferences? {
    val ctx = context?.applicationContext ?: appContext ?: return null
    return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun getKey(): String {
    return prefs()?.getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
  }

  fun getKey(context: Context): String {
    init(context)
    return getKey()
  }

  fun hasKey(context: Context? = null): Boolean {
    return if (context != null) getKey(context).isNotBlank() else getKey().isNotBlank()
  }

  fun saveKey(key: String, context: Context? = null) {
    val trimmed = key.trim()
    val editor = prefs(context)?.edit() ?: return
    if (trimmed.isBlank()) {
      editor.remove(KEY_GEMINI_API_KEY)
    } else {
      editor.putString(KEY_GEMINI_API_KEY, trimmed)
    }
    editor.apply()
  }

  fun clearKey(context: Context? = null) {
    prefs(context)?.edit()?.remove(KEY_GEMINI_API_KEY)?.apply()
  }

  fun maskedKey(): String {
    val key = getKey()
    if (key.length <= 4) return if (key.isBlank()) "" else "••••"
    return "••••" + key.takeLast(4)
  }
}
