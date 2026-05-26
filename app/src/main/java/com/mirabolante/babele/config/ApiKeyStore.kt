package com.mirabolante.babele.config

import android.content.Context
import com.mirabolante.babele.BuildConfig

/**
 * Stores the user's own Gemini API key (BYOK — bring your own key) in SharedPreferences.
 *
 * Pure BYOK: the key used for API calls is ALWAYS the user-entered one — there is no runtime
 * fallback to a baked BuildConfig key, so a release build can never accidentally ship and bill
 * the developer's key. The baked key is used only to pre-fill the input field on DEBUG builds.
 */
class ApiKeyStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun storedKey(): String = prefs.getString(KEY, "").orEmpty()

  fun setKey(key: String) {
    prefs.edit().putString(KEY, key.trim()).apply()
  }

  fun clear() {
    prefs.edit().remove(KEY).apply()
  }

  /** True once the user has saved their own key — drives the forced-setup gate. */
  fun hasUserKey(): Boolean = storedKey().isNotBlank()

  /** Key used for API calls: the user's key only. Empty until they set one. */
  fun effectiveKey(): String = storedKey()

  /** Pre-fill for the input field: the baked key on DEBUG builds only, empty on release. */
  fun prefillKey(): String = if (BuildConfig.DEBUG) BuildConfig.GEMINI_API_KEY else ""

  companion object {
    private const val PREFS = "babele_prefs"
    private const val KEY = "gemini_api_key"
  }
}
