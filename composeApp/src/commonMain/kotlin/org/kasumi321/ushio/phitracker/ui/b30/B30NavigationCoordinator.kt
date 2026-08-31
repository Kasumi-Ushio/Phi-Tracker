package org.kasumi321.ushio.phitracker.ui.b30

internal interface B30NavigationGateway {
    fun navigateB30()
    fun navigateHomeReplacingLogin()
    fun navigateLoginReplacingHome()
    fun popCurrent()
    fun popToHome(): Boolean
    fun navigateLoginClearingGraph()
}

internal class B30NavigationCoordinator(
    private val gateway: B30NavigationGateway
) {
    var payload: B30ExportPayload? = null
        private set

    fun openB30(value: B30ExportPayload) {
        payload = value
        gateway.navigateB30()
    }

    fun toolbarBack() {
        clearPayload()
        gateway.popCurrent()
    }

    fun destinationDisposed() {
        clearPayload()
    }

    fun homeLogout() {
        clearPayload()
        gateway.navigateLoginReplacingHome()
    }

    fun settingsLogout() {
        clearPayload()
        gateway.navigateLoginReplacingHome()
    }

    fun loginSuccess() {
        clearPayload()
        gateway.navigateHomeReplacingLogin()
    }

    fun recoverMissingPayload(): B30MissingPayloadRecovery {
        clearPayload()
        return if (gateway.popToHome()) {
            B30MissingPayloadRecovery.HomePopped
        } else {
            gateway.navigateLoginClearingGraph()
            B30MissingPayloadRecovery.NavigateLogin
        }
    }

    private fun clearPayload() {
        payload = null
    }
}
