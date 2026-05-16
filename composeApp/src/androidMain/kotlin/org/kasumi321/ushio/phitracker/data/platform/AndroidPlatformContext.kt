package org.kasumi321.ushio.phitracker.data.platform

import android.app.Activity
import android.content.Context

object AndroidPlatformContext {
    var applicationContext: Context? = null
        private set
    var currentActivity: Activity? = null
        private set

    fun initialize(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
        if (context is Activity) {
            currentActivity = context
        }
    }

    fun setCurrentActivity(activity: Activity?) {
        currentActivity = activity
    }
}
