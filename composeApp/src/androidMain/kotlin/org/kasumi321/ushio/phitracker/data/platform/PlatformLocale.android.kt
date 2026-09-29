package org.kasumi321.ushio.phitracker.data.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

private const val PREFS_NAME = "phi_tracker_app_locale"
private const val KEY_LANGUAGE_TAG = "language_tag"

actual fun currentLanguageTag(): String {
    // Locale.getDefault() does not track per-app locale changes applied
    // mid-session; the current configuration always reflects them.
    val context = AndroidPlatformContext.currentActivity
        ?: AndroidPlatformContext.applicationContext
    val locales = context?.resources?.configuration?.locales
    val first = if (locales != null && !locales.isEmpty) locales[0] else null
    return (first ?: Locale.getDefault()).toLanguageTag()
}

actual fun setAppLocale(languageTag: String?): Boolean {
    val context = AndroidPlatformContext.applicationContext ?: return false
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LANGUAGE_TAG, languageTag)
        .apply()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        localeManager?.applicationLocales = if (languageTag.isNullOrBlank()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(languageTag)
        }
    } else {
        // No framework per-app locale below API 33; the stored tag is applied
        // by wrapWithAppLocale during attachBaseContext, so recreating the
        // foreground activity is enough to refresh the running UI.
        AndroidPlatformContext.currentActivity?.recreate()
    }
    return false
}

/**
 * Applies the stored in-app language to [base] on API 32 and below. Called
 * from Activity.attachBaseContext before super; a no-op on API 33+, where the
 * framework resolves per-app locales itself.
 */
fun wrapWithAppLocale(base: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
    val tag = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE_TAG, null)
    if (tag.isNullOrBlank()) return base
    val config = Configuration(base.resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(tag))
    return base.createConfigurationContext(config)
}
