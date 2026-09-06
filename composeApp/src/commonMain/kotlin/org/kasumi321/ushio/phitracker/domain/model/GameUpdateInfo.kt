package org.kasumi321.ushio.phitracker.domain.model

import kotlinx.serialization.Serializable

/**
 * Latest published Phigros game update as returned by TapTap's public
 * apk list endpoint. [changelog] is already converted from the HTML
 * whatsnew fragment to plain text.
 */
@Serializable
data class GameUpdateInfo(
    val version: String,
    val versionCode: Long,
    val date: String,
    val changelog: String
)
