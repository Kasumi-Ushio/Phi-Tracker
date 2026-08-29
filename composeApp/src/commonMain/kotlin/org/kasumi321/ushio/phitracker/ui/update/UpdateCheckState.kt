package org.kasumi321.ushio.phitracker.ui.update

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Available(val version: String, val htmlUrl: String, val body: String) : UpdateCheckState
    data object NoUpdate : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}
