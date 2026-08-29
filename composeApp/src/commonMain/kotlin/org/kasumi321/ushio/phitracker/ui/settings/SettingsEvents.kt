package org.kasumi321.ushio.phitracker.ui.settings

import kotlinx.coroutines.flow.update
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState

fun SettingsViewModel.dismissUpdateResult() = mutableUiState.update { it.copy(updateCheckState = UpdateCheckState.Idle) }
fun SettingsViewModel.dismissUpdateDataError() = mutableUiState.update { it.copy(updateDataError = null) }

fun SettingsViewModel.logout() {
    launchSetting {
        repository.clearData()
        eventChannel.send(SettingsEvent.LoggedOut)
    }
}
