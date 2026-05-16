package org.kasumi321.ushio.phitracker

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.logging.AnrWatchDog
import org.kasumi321.ushio.phitracker.data.logging.AndroidLogContext
import org.kasumi321.ushio.phitracker.data.logging.LoggingState
import org.kasumi321.ushio.phitracker.data.logging.createLoggingState
import org.kasumi321.ushio.phitracker.data.platform.AndroidPlatformContext
import org.kasumi321.ushio.phitracker.data.platform.NotificationPermissionRequester
import org.kasumi321.ushio.phitracker.data.platform.SafTreeManager
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.di.initKoin

class MainActivity : ComponentActivity() {

    private var anrWatchDog: AnrWatchDog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.initialize(this)
        AndroidPlatformContext.setCurrentActivity(this)
        SafTreeManager.initialize(this)

        val isDebug = getAppMetadata().buildType == "Debug"
        AndroidLogContext.init(filesDir.absolutePath.toPath())

        val loggingState = createLoggingState(isDebug)

        val anr = AnrWatchDog(store = loggingState.store)
        anr.start()
        anrWatchDog = anr

        initKoin()

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidPlatformContext.setCurrentActivity(null)
        anrWatchDog?.stop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NotificationPermissionRequester.REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            NotificationPermissionRequester.handleResult(granted)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
