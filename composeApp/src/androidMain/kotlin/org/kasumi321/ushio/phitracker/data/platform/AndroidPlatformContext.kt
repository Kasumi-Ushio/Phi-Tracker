package org.kasumi321.ushio.phitracker.data.platform

import android.content.Context

object AndroidPlatformContext {
    var applicationContext: Context? = null
        private set

    fun initialize(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }
}
