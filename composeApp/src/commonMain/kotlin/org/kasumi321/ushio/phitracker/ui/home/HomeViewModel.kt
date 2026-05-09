package org.kasumi321.ushio.phitracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

class HomeViewModel(
    private val repository: PhigrosRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            repository.clearData()
            _uiState.update { it.copy(isLoggedOut = true, isLoggingOut = false) }
        }
    }
}

data class HomeUiState(
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false
)
