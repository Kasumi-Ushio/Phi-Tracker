package org.kasumi321.ushio.phitracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import org.kasumi321.ushio.phitracker.domain.model.Server
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionToken 安全存储管理器
 * 使用 EncryptedSharedPreferences (AES-256)
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "phi_tracker_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String, server: Server) {
        prefs.edit()
            .putString(KEY_SESSION_TOKEN, token)
            .putString(KEY_SERVER, server.name)
            .apply()
    }

    fun getToken(): Pair<String, Server>? {
        val token = prefs.getString(KEY_SESSION_TOKEN, null) ?: return null
        val serverName = prefs.getString(KEY_SERVER, Server.CN.name) ?: Server.CN.name
        val server = try { Server.valueOf(serverName) } catch (_: Exception) { Server.CN }
        return token to server
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SERVER)
            .apply()
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SERVER = "server"
    }
}
