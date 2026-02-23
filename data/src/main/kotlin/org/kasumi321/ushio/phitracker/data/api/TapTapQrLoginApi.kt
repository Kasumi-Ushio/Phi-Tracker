package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.kasumi321.ushio.phitracker.domain.model.Server
import timber.log.Timber
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TapTap QR 码登录 API 客户端
 *
 * 移植自 phi-plugin 的 lib/TapTap/ 实现:
 * - TapTapHelper.js  → requestDeviceCode, checkQrCodeResult, getProfile
 * - LCHelper.js      → exchangeForSessionToken
 */
@Singleton
class TapTapQrLoginApi @Inject constructor(
    private val httpClient: HttpClient
) {

    // ── Step 1: 请求设备码 (获取 QR 码 URL) ──────────────────────────

    /**
     * POST /oauth2/v1/device/code
     * @return DeviceCodeResponse 包含 qrcode_url, device_code, expires_in, interval
     */
    suspend fun requestDeviceCode(server: Server): DeviceCodeResponse {
        val deviceId = UUID.randomUUID().toString().replace("-", "")
        val clientId = TapTapConstants.lcClientId(server)

        val response = httpClient.submitForm(
            url = TapTapConstants.oauthCodeUrl(server),
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("response_type", "device_code")
                append("scope", "public_profile")
                append("version", "2.1")
                append("platform", "unity")
                append("info", """{"device_id":"$deviceId"}""")
            }
        )

        val text = response.bodyAsText()
        Timber.d("Device code raw response: $text")
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        // API 返回 {"data": {"device_code": ..., "qrcode_url": ..., ...}}
        val apiResponse = json.decodeFromString<DeviceCodeApiResponse>(text)
        val data = apiResponse.data
        Timber.d("Device code received: qrcode_url=${data.qrcodeUrl}, expires_in=${data.expiresIn}")
        return DeviceCodeResponse(data = data, deviceId = deviceId)
    }

    // ── Step 2: 轮询检查扫码结果 ─────────────────────────────────────

    /**
     * POST /oauth2/v1/token
     * @return QrCheckResult — success=true 时包含 token data, false 时包含 error status
     */
    suspend fun checkQrCodeResult(
        deviceCode: String,
        deviceId: String,
        server: Server
    ): QrCheckResult {
        val clientId = TapTapConstants.lcClientId(server)

        return try {
            val response = httpClient.submitForm(
                url = TapTapConstants.oauthTokenUrl(server),
                formParameters = Parameters.build {
                    append("grant_type", "device_token")
                    append("client_id", clientId)
                    append("secret_type", "hmac-sha-1")
                    append("code", deviceCode)
                    append("version", "1.0")
                    append("platform", "unity")
                    append("info", """{"device_id":"$deviceId"}""")
                }
            )

            val text = response.bodyAsText()
            Timber.d("QR check response: $text")

            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            // API 返回两种格式:
            // 失败: {"data": {"error": "authorization_pending", ...}}
            // 成功: {"success": true, "data": {"kid": ..., "access_token": ..., ...}}
            try {
                // 先检查是否有 success 字段
                val jsonObj = json.parseToJsonElement(text).jsonObject
                val success = jsonObj["success"]?.jsonPrimitive?.booleanOrNull == true
                val dataObj = jsonObj["data"]
                
                if (success && dataObj != null) {
                    val tokenData = json.decodeFromJsonElement<TapTapTokenData>(dataObj)
                    QrCheckResult(success = true, data = tokenData)
                } else if (dataObj != null) {
                    val errorObj = dataObj.jsonObject
                    val error = errorObj["error"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                    QrCheckResult(success = false, error = error)
                } else {
                    // 直接是 token 数据 (无 wrapper)
                    val tokenData = json.decodeFromString<TapTapTokenData>(text)
                    if (tokenData.kid != null && tokenData.accessToken != null) {
                        QrCheckResult(success = true, data = tokenData)
                    } else {
                        val errorData = json.decodeFromString<QrErrorData>(text)
                        QrCheckResult(success = false, error = errorData.error ?: "unknown")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse QR check response")
                QrCheckResult(success = false, error = "parse_error")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking QR code result")
            QrCheckResult(success = false, error = "network_error")
        }
    }

    // ── Step 3: 获取 TapTap 用户 Profile ─────────────────────────────

    /**
     * GET /account/profile/v1
     * 使用 MAC 认证头
     */
    suspend fun getProfile(tokenData: TapTapTokenData, server: Server): TapTapProfile {
        val url = TapTapConstants.profileUrl(server)
        val authorization = buildMacAuthorization(
            requestUrl = url,
            method = "GET",
            keyId = tokenData.kid!!,
            macKey = tokenData.macKey!!
        )

        Timber.d("Fetching profile with MAC auth")
        val response = httpClient.get(url) {
            header("Authorization", authorization)
        }
        return response.body()
    }

    // ── Step 4: 通过 LeanCloud 换取 sessionToken ─────────────────────

    /**
     * POST /1.1/users
     * 使用 authData 方式登录, 获取 sessionToken
     */
    suspend fun exchangeForSessionToken(
        profile: TapTapProfile,
        tokenData: TapTapTokenData,
        server: Server
    ): String {
        val clientId = TapTapConstants.lcClientId(server)
        val appKey = TapTapConstants.lcAppKey(server)

        // X-LC-Sign = md5(timestamp + appKey), timestamp
        val timestamp = System.currentTimeMillis() / 1000
        val signData = "$timestamp$appKey"
        val hash = java.security.MessageDigest.getInstance("MD5")
            .digest(signData.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val lcSign = "$hash,$timestamp"

        // 构建请求体: { authData: { taptap: { ...profile, ...tokenData } } }
        val body = buildJsonObject {
            putJsonObject("authData") {
                putJsonObject("taptap") {
                    // profile 数据
                    put("openid", profile.openid)
                    put("name", profile.name)
                    put("avatar", profile.avatar)
                    // token 数据
                    put("kid", tokenData.kid)
                    put("access_token", tokenData.accessToken)
                    put("token_type", tokenData.tokenType)
                    put("mac_key", tokenData.macKey)
                    put("mac_algorithm", tokenData.macAlgorithm)
                    put("scope", tokenData.scope)
                }
            }
        }

        Timber.d("Exchanging for sessionToken via LeanCloud")
        val response = httpClient.post(TapTapConstants.lcUsersUrl(server)) {
            header("X-LC-Id", clientId)
            header("X-LC-Sign", lcSign)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }

        val result: LcLoginResponse = response.body()
        Timber.d("Got sessionToken: ${result.sessionToken.take(8)}...")
        return result.sessionToken
    }

    // ── MAC 签名算法 ─────────────────────────────────────────────────

    /**
     * 构建 MAC Authorization 头, 移植自 phi-plugin TapTapHelper.js getAuthorization()
     */
    private fun buildMacAuthorization(
        requestUrl: String,
        method: String,
        keyId: String,
        macKey: String
    ): String {
        val url = URL(requestUrl)
        val time = (System.currentTimeMillis() / 1000).toString().padStart(10, '0')
        val nonce = generateRandomString(16)
        val host = url.host
        val uri = url.path + if (url.query != null) "?${url.query}" else ""
        val port = if (url.port > 0) url.port.toString()
        else if (url.protocol == "https") "443" else "80"

        val signatureBase = "$time\n$nonce\n$method\n$uri\n$host\n$port\n\n"

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(macKey.toByteArray(), "HmacSHA1"))
        val signature = Base64.getEncoder().encodeToString(mac.doFinal(signatureBase.toByteArray()))

        return """MAC id="$keyId", ts="$time", nonce="$nonce", mac="$signature""""
    }

    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}

// ── 数据类 ───────────────────────────────────────────────────────────

/** Step 1 API 响应包装 */
@Serializable
data class DeviceCodeApiResponse(
    val data: DeviceCodeData
)

/** Step 1 响应: 设备码数据 */
@Serializable
data class DeviceCodeData(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String = "",
    @SerialName("qrcode_url") val qrcodeUrl: String,
    @SerialName("verification_url") val verificationUrl: String = "",
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5
)

/** Step 1 完整响应 (含自生成的 deviceId) */
data class DeviceCodeResponse(
    val data: DeviceCodeData,
    val deviceId: String
)

/** Step 2 轮询错误数据 */
@Serializable
data class QrErrorData(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

/** Step 2 Token 数据 (扫码成功后) */
@Serializable
data class TapTapTokenData(
    val kid: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("mac_key") val macKey: String? = null,
    @SerialName("mac_algorithm") val macAlgorithm: String? = null,
    val scope: String? = null
)

/** Step 2 轮询结果 */
data class QrCheckResult(
    val success: Boolean,
    val data: TapTapTokenData? = null,
    val error: String? = null
)

/** Step 3 TapTap Profile */
@Serializable
data class TapTapProfile(
    val data: TapTapProfileData
) {
    val openid: String get() = data.openid
    val name: String get() = data.name
    val avatar: String get() = data.avatar
}

@Serializable
data class TapTapProfileData(
    val openid: String,
    val name: String,
    val avatar: String
)

/** Step 4 LeanCloud 登录响应 */
@Serializable
data class LcLoginResponse(
    val sessionToken: String,
    val objectId: String = ""
)
