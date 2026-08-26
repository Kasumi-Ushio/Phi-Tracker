package org.kasumi321.ushio.phitracker.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean,
    val body: String? = null,
)

fun GitHubReleaseDto.toDomain(): ReleaseInfo = ReleaseInfo(
    tagName = tagName,
    htmlUrl = htmlUrl,
    prerelease = prerelease,
    body = body,
)
