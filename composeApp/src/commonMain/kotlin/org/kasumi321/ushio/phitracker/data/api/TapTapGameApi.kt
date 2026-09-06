package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo

/**
 * TapTap's unofficial public APK listing API (no auth), mirroring
 * phi-plugin's model/integrations/getInfoFromTap PgrUpdateInfo. The X-UA
 * value is passed as a single query parameter whose "&" separators are
 * left to Ktor's parameter encoding.
 */
class TapTapGameApi(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://api.taptapdada.com"
        const val PHIGROS_APP_ID = "165287"
        const val X_UA = "V=1&PN=TapTap&VN_CODE=283021001&LANG=zh_CN"
    }

    suspend fun fetchLatestUpdate(): GameUpdateInfo {
        val response: TapTapUpdateResponse = httpClient.get("$BASE_URL/apk/v1/list-by-app") {
            parameter("app_id", PHIGROS_APP_ID)
            parameter("X-UA", X_UA)
            parameter("from", 0)
            parameter("limit", 1)
        }.body()
        if (!response.success) throw IllegalStateException("TapTap update listing returned success=false")
        return response.data.list.firstOrNull()?.toDomain()
            ?: throw IllegalStateException("TapTap update listing returned an empty list")
    }
}

@Serializable
data class TapTapUpdateResponse(
    val success: Boolean,
    val data: TapTapUpdateData
)

@Serializable
data class TapTapUpdateData(
    val list: List<TapTapUpdateItem>
)

@Serializable
data class TapTapUpdateItem(
    @SerialName("version_label") val versionLabel: String,
    @SerialName("version_code") val versionCode: Long,
    @SerialName("update_date") val updateDateEpochSeconds: Long,
    val whatsnew: TapTapWhatsNew
)

@Serializable
data class TapTapWhatsNew(
    val text: String
)

fun TapTapUpdateItem.toDomain(): GameUpdateInfo = GameUpdateInfo(
    version = versionLabel,
    versionCode = versionCode,
    date = Instant.fromEpochSeconds(updateDateEpochSeconds)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString(),
    changelog = whatsnew.text.toPlainChangelog()
)

/**
 * TapTap whatsnew fragments are HTML: strip the div wrappers, turn <br>
 * into line breaks, then trim trailing whitespace and collapse runs of
 * blank lines.
 */
internal fun String.toPlainChangelog(): String =
    replace(Regex("(?i)</?div[^>]*>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
