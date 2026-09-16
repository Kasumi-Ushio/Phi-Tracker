package org.kasumi321.ushio.phitracker.ui.suggest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode

data class SuggestUiState(
    val isLoading: Boolean = true,
    val hasSaveData: Boolean = false,
    val targetMode: SuggestTargetMode = SuggestTargetMode.PlayerDisplayRks,
    val targetInput: String = "",
    val targetError: String? = null,
    val items: List<SuggestItem> = emptyList()
)

class SuggestViewModel(
    private val repository: PhigrosRepository,
    private val getB30UseCase: GetB30UseCase,
    private val getSuggestUseCase: GetSuggestUseCase,
    private val songDataProvider: SongDataProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuggestUiState())
    val uiState: StateFlow<SuggestUiState> = _uiState.asStateFlow()

    private var suggestJob: Job? = null
    private var currentB30: List<BestRecord> = emptyList()
    private var currentRecords: Map<String, SongRecord>? = null

    init {
        viewModelScope.launch {
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()
            getB30UseCase(diffMap, nameMap).collect { (b30, _) ->
                currentB30 = b30
                currentRecords = repository.getCachedSave().first()?.gameRecord
                _uiState.update {
                    it.copy(isLoading = false, hasSaveData = currentRecords != null)
                }
                recalculateSuggestItems()
            }
        }
    }

    fun setTargetMode(mode: SuggestTargetMode) {
        _uiState.update { it.copy(targetMode = mode) }
        recalculateSuggestItems()
    }

    fun setTargetInput(input: String) {
        val normalized = input.replace('，', '.')
        _uiState.update { it.copy(targetInput = normalized) }
        recalculateSuggestItems()
    }

    private fun recalculateSuggestItems() {
        // Target input can change on every keystroke; cancel the in-flight (heavy)
        // recomputation so rapid edits don't pile up overlapping background work.
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            val records = currentRecords
            if (records == null) {
                _uiState.update { it.copy(items = emptyList(), targetError = null) }
                return@launch
            }
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()
            // The sweep below can resume on a background dispatcher long after
            // this launch started; capture the request here and only apply the
            // result if the user hasn't changed it in the meantime, so a stale
            // sweep never clobbers a fresher result.
            val modeAtStart = _uiState.value.targetMode
            val inputAtStart = _uiState.value.targetInput
            val result = buildSuggestItems(
                currentB30 = currentB30,
                records = records,
                difficulties = diffMap,
                songNames = nameMap,
                mode = modeAtStart,
                input = inputAtStart
            )
            _uiState.update { current ->
                if (current.targetMode == modeAtStart && current.targetInput == inputAtStart) {
                    current.copy(items = result.items, targetError = result.error)
                } else {
                    current
                }
            }
        }
    }

    private data class SuggestBuildResult(
        val items: List<SuggestItem>,
        val error: String?
    )

    private suspend fun buildSuggestItems(
        currentB30: List<BestRecord>,
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        mode: SuggestTargetMode,
        input: String
    ): SuggestBuildResult {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) {
            // Sweeping every game chart (and, for the final-RKS mode, binary-searching
            // each) is heavy enough to jank the UI thread, so keep it on Default.
            val items = withContext(Dispatchers.Default) {
                getSuggestUseCase(
                    currentB30 = currentB30,
                    records = records,
                    difficulties = difficulties,
                    songNames = songNames,
                    limit = 30
                )
            }
            return SuggestBuildResult(items = items, error = null)
        }

        val targetInputPattern = Regex("""\d+(\.\d{0,2})?""")
        if (!targetInputPattern.matches(normalizedInput)) {
            return SuggestBuildResult(emptyList(), "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数")
        }

        val targetRks = normalizedInput.toFloatOrNull()
        if (targetRks == null || targetRks !in 0f..17f) {
            return SuggestBuildResult(emptyList(), "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数")
        }

        val items = withContext(Dispatchers.Default) {
            getSuggestUseCase(
                currentB30 = currentB30,
                records = records,
                difficulties = difficulties,
                songNames = songNames,
                targetMode = mode,
                targetRks = targetRks,
                limit = 30
            )
        }
        val error = if (mode == SuggestTargetMode.PlayerDisplayRks && items.isEmpty()) {
            "当前数据下已达到目标，或没有可提升的谱面能帮助达成该目标"
        } else null
        return SuggestBuildResult(items, error)
    }
}
