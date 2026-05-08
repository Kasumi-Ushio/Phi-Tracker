package org.kasumi321.ushio.phitracker.data.mapper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatTimestamp(epochMillis: Long): String = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss",
    Locale.US
).format(Date(epochMillis))
