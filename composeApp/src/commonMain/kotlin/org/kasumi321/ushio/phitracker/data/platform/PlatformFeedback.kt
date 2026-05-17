package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

expect fun showPlatformMessage(message: String)

expect fun showPlatformAlert(title: String, message: String)

data class PlatformAlertContent(
    val id: Long,
    val title: String,
    val message: String
)

object PlatformAlertController {
    private var nextId = 0L
    private val _alert = MutableStateFlow<PlatformAlertContent?>(null)
    val alert: StateFlow<PlatformAlertContent?> = _alert.asStateFlow()

    fun show(title: String, message: String) {
        _alert.value = PlatformAlertContent(
            id = nextId++,
            title = title,
            message = message
        )
    }

    fun dismiss(id: Long) {
        if (_alert.value?.id == id) {
            _alert.value = null
        }
    }
}
