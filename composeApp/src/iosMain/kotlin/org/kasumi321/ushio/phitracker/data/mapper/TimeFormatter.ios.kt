package org.kasumi321.ushio.phitracker.data.mapper

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier

actual fun formatTimestamp(epochMillis: Long): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis.toDouble() / 1000.0)
    return formatter.stringFromDate(date)
}
