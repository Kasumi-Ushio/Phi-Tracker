package org.kasumi321.ushio.phitracker.ui.update

import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo

fun Result<ReleaseInfo?>.toUpdateCheckState(): UpdateCheckState = fold(
    onSuccess = { release ->
        if (release == null) {
            UpdateCheckState.NoUpdate
        } else {
            UpdateCheckState.Available(
                version = release.tagName,
                htmlUrl = release.htmlUrl,
                body = release.body.orEmpty()
            )
        }
    },
    onFailure = { error -> UpdateCheckState.Error(error.message ?: "未知错误") }
)
