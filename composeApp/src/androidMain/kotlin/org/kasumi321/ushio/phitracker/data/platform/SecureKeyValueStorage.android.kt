package org.kasumi321.ushio.phitracker.data.platform

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

actual fun createSecureKeyValueStorage(name: String): SecureKeyValueStorage {
    val context = AndroidPlatformContext.applicationContext
        ?: throw IllegalStateException(
            "AndroidPlatformContext.applicationContext not initialized. " +
                "Call AndroidPlatformContext.initialize(context) in MainActivity.onCreate before App()."
        )
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        name,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    return EncryptedPrefsStorage(prefs)
}

private class EncryptedPrefsStorage(
    private val prefs: SharedPreferences
) : SecureKeyValueStorage {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        check(prefs.edit().putString(key, value).commit()) { "Unable to persist secure key: $key" }
    }

    override fun remove(key: String) {
        check(prefs.edit().remove(key).commit()) { "Unable to remove secure key: $key" }
    }
}
