package org.kasumi321.ushio.phitracker.data.song

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createFileThenAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createTextAssetReader
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.NoteCount
import org.kasumi321.ushio.phitracker.domain.model.SongInfo

class SongDataProvider(
    private val assetReader: TextAssetReader = createTextAssetReader(),
    private val paths: PlatformPaths? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private var songs: Map<String, SongInfo>? = null

    private val reader: TextAssetReader = paths?.let { createFileThenAssetReader(assetReader, it) } ?: assetReader

    fun getSongs(): Map<String, SongInfo> {
        songs?.let { return it }
        val difficulties = loadDifficulties()
        val infos = loadInfos()
        val additionalInfo = runCatching { loadAdditionalInfo() }.getOrDefault(emptyMap())
        val notesInfo = runCatching { loadNotesInfo() }.getOrDefault(emptyMap())
        val loaded = mutableMapOf<String, SongInfo>()
        for ((songId, diffs) in difficulties) {
            val info = infos[songId]
            val rawId = songId.removeSuffix(".0")
            val addInfo = additionalInfo[rawId]
            val noteInfo = notesInfo[rawId]
            val noteCounts = diffs.keys.associateWith { difficulty ->
                val tArray = noteInfo?.get(difficulty.name)?.t ?: emptyList()
                if (tArray.size >= 4) NoteCount(tArray[0], tArray[1], tArray[2], tArray[3]) else NoteCount()
            }.filter { it.value.total > 0 }
            loaded[songId] = SongInfo(
                id = songId,
                name = info?.name ?: songId,
                composer = info?.composer ?: "",
                illustrator = info?.illustrator ?: "",
                difficulties = diffs,
                bpm = addInfo?.bpm ?: "",
                chapter = addInfo?.chapter ?: "",
                length = addInfo?.length ?: "",
                charters = info?.charters ?: emptyMap(),
                noteCounts = noteCounts
            )
        }
        songs = loaded
        return loaded
    }

    fun invalidateCache() {
        songs = null
    }

    fun getDifficultyMap(): Map<String, Map<Difficulty, Float>> = getSongs().mapValues { it.value.difficulties }
    fun getSongNameMap(): Map<String, String> = getSongs().mapValues { it.value.name }

    private fun loadDifficulties(): Map<String, Map<Difficulty, Float>> {
        val result = mutableMapOf<String, Map<Difficulty, Float>>()
        // Upstream folded the difficulty constants into info.csv (removing the
        // separate difficulty.csv). info.csv is TAB-separated with columns:
        // id, song, composer, illustrator, EZC, HDC, INC, ATC (charters), EZ, HD, IN, AT (constants).
        reader.readText("info.csv").lineSequence().forEachIndexed { index, line ->
            if (index == 0 || line.isBlank()) return@forEachIndexed
            val parts = line.split('\t')
            if (parts.size < 8) return@forEachIndexed
            val songId = parts[0] + ".0"
            val diffs = mutableMapOf<Difficulty, Float>()
            parts.getOrNull(8)?.trim()?.toFloatOrNull()?.let { diffs[Difficulty.EZ] = it }
            parts.getOrNull(9)?.trim()?.toFloatOrNull()?.let { diffs[Difficulty.HD] = it }
            parts.getOrNull(10)?.trim()?.toFloatOrNull()?.let { diffs[Difficulty.IN] = it }
            parts.getOrNull(11)?.trim()?.toFloatOrNull()?.let { diffs[Difficulty.AT] = it }
            if (diffs.isNotEmpty()) result[songId] = diffs
        }
        return result
    }

    private fun loadInfos(): Map<String, InfoCsvModel> {
        val result = mutableMapOf<String, InfoCsvModel>()
        // TAB-separated; per-difficulty charters live in columns EZC/HDC/INC/ATC (4..7).
        reader.readText("info.csv").lineSequence().forEachIndexed { index, line ->
            if (index == 0 || line.isBlank()) return@forEachIndexed
            val parts = line.split('\t')
            if (parts.size < 8) return@forEachIndexed
            val charters = mutableMapOf<Difficulty, String>()
            parts.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { charters[Difficulty.EZ] = it }
            parts.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { charters[Difficulty.HD] = it }
            parts.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { charters[Difficulty.IN] = it }
            parts.getOrNull(7)?.takeIf { it.isNotBlank() }?.let { charters[Difficulty.AT] = it }
            result[parts[0] + ".0"] = InfoCsvModel(parts[1], parts[2], parts[3], charters)
        }
        return result
    }

    private fun loadAdditionalInfo(): Map<String, InfoListEntry> = json.decodeFromString(reader.readText("infolist.json"))
    private fun loadNotesInfo(): Map<String, Map<String, NotesInfoDifficulty>> = json.decodeFromString(reader.readText("notesInfo.json"))

    private data class InfoCsvModel(
        val name: String,
        val composer: String,
        val illustrator: String,
        val charters: Map<Difficulty, String>
    )

    @Serializable
    private data class InfoListEntry(val bpm: String = "", val length: String = "", val chapter: String = "")

    @Serializable
    private data class NotesInfoDifficulty(val t: List<Int> = emptyList())
}
