package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import javax.inject.Inject

/**
 * 获取 B30 列表用例
 *
 * 需要曲目定数和名称数据 (由 SongDataProvider 提供, 在 ViewModel 中注入)
 */
class GetB30UseCase @Inject constructor(
    private val repository: PhigrosRepository
) {
    /**
     * @param difficulties 定数表 (songId -> { difficulty -> chartConstant })
     * @param songNames 歌名表 (songId -> songName)
     */
    operator fun invoke(
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): Flow<List<BestRecord>> {
        return repository.getCachedSave().map { save ->
            if (save == null) return@map emptyList()
            RksCalculator.getB30(save.gameRecord, difficulties, songNames)
        }
    }
}
