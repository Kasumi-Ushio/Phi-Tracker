package org.kasumi321.ushio.phitracker.data.platform

import java.util.Locale

actual fun currentLanguageTag(): String = Locale.getDefault().toLanguageTag()
