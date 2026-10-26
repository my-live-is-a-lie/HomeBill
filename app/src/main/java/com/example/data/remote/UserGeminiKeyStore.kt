package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the end-user's own Gemini API keys on the device only.
 *
 * Keys are never sent to Firebase, never committed to the repo, and never
 * baked into the APK at build time. They live in the app's private
 * SharedPreferences, which other apps cannot read. The developer's
 * BuildConfig key is intentionally NOT used as a fallback: AI features
 * require the user's own key, entered first in Settings.
 *
 * Storage format: keys joined by a newline in "gemini_keys" (API keys
 * never contain a newline), plus "gemini_active_index". A legacy single
 * key stored under "gemini_api_key" by the first version of this feature
 * is migrated into the list on first read.
 */
object UserGeminiKeyStore {
  private const val PREFS_NAME = "homebill_user_keys"
  private const val KEY_LEGACY_SINGLE = "gemini_api_key"
  private const val KEY_LIST = "gemini_keys"
  private const val KEY_ACTIVE_INDEX = "gemini_active_index"

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

  fun getKeys(): List<String> {
    val stored = prefs()?.getString(KEY_LIST, "").orEmpty()
      .split("\n").map { it.trim() }.filter { it.isNotBlank() }
    if (stored.isNotEmpty()) return stored
    val legacy = prefs()?.getString(KEY_LEGACY_SINGLE, "")?.trim().orEmpty()
    return if (legacy.isNotBlank()) listOf(legacy) else emptyList()
  }

  fun getKeys(context: Context): List<String> {
    init(context)
    return getKeys()
  }

  fun activeIndex(): Int {
    val keys = getKeys()
    if (keys.isEmpty()) return -1
    val stored = prefs()?.getInt(KEY_ACTIVE_INDEX, 0) ?: 0
    return stored.coerceIn(0, keys.lastIndex)
  }

  fun activeIndex(context: Context): Int {
    init(context)
    return activeIndex()
  }

  /** The key currently used by all AI features, or "" if none is set. */
  fun getKey(): String {
    val keys = getKeys()
    val index = activeIndex()
    return if (index in keys.indices) keys[index] else ""
  }

  fun getKey(context: Context): String {
    init(context)
    return getKey()
  }

  fun hasKey(context: Context? = null): Boolean {
    return if (context != null) getKey(context).isNotBlank() else getKey().isNotBlank()
  }

  private fun writeKeys(keys: List<String>, active: Int, context: Context? = null) {
    val editor = prefs(context)?.edit() ?: return
    editor.putString(KEY_LIST, keys.joinToString("\n"))
    editor.putInt(KEY_ACTIVE_INDEX, if (keys.isEmpty()) 0 else active.coerceIn(0, keys.lastIndex))
    editor.remove(KEY_LEGACY_SINGLE)
    editor.apply()
  }

  /** Adds a key unless it is blank or already saved. Returns true if added. */
  fun addKey(key: String, context: Context? = null): Boolean {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return false
    val keys = getKeys().toMutableList()
    if (keys.contains(trimmed)) return false
    keys.add(trimmed)
    val active = if (keys.size == 1) 0 else activeIndex()
    writeKeys(keys, active, context)
    return true
  }

  /** Legacy single-key save: adds the key and makes it the active one. */
  fun saveKey(key: String, context: Context? = null) {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return
    val keys = getKeys().toMutableList()
    if (!keys.contains(trimmed)) keys.add(trimmed)
    writeKeys(keys, keys.indexOf(trimmed), context)
  }

  fun setActiveKey(index: Int, context: Context? = null) {
    val keys = getKeys()
    if (index in keys.indices) writeKeys(keys, index, context)
  }

  fun removeKey(index: Int, context: Context? = null) {
    val keys = getKeys().toMutableList()
    if (index !in keys.indices) return
    val wasActive = activeIndex()
    keys.removeAt(index)
    val active = when {
      keys.isEmpty() -> 0
      index < wasActive -> wasActive - 1
      index == wasActive -> 0
      else -> wasActive
    }
    writeKeys(keys, active, context)
  }

  fun clearKey(context: Context? = null) {
    prefs(context)?.edit()?.clear()?.apply()
  }

  fun maskedKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length <= 4) return if (trimmed.isBlank()) "" else "••••"
    return "••••" + trimmed.takeLast(4)
  }

  fun maskedKey(): String = maskedKey(getKey())
}
