package org.kasumi321.ushio.phitracker.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BindRequest(
    val platform: String,
    @SerialName("platform_id")
    val platformId: String,
    val token: String,
    @SerialName("api_user_id")
    val apiUserId: String? = null,
    val isGlobal: Boolean? = null
)
