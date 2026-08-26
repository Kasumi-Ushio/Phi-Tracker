package org.kasumi321.ushio.phitracker.domain.model

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val body: String? = null,
)
