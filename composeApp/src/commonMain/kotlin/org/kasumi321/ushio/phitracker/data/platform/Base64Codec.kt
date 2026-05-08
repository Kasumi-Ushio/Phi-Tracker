package org.kasumi321.ushio.phitracker.data.platform

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64Codec {
    fun decode(value: String): ByteArray = Base64.Default.decode(value)
}
