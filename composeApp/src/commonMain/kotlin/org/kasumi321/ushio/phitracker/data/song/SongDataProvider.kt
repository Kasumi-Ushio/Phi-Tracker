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
        reader.readText("difficulty.csv").lineSequence().forEachIndexed { index, line ->
            if (index == 0 || line.isBlank()) return@forEachIndexed
            val parts = parseCsvLine(line)
            if (parts.size < 4) return@forEachIndexed
            val songId = parts[0] + ".0"
            val diffs = mutableMapOf<Difficulty, Float>()
            parts.getOrNull(1)?.toFloatOrNull()?.let { diffs[Difficulty.EZ] = it }
            parts.getOrNull(2)?.toFloatOrNull()?.let { diffs[Difficulty.HD] = it }
            parts.getOrNull(3)?.toFloatOrNull()?.let { diffs[Difficulty.IN] = it }
            parts.getOrNull(4)?.toFloatOrNull()?.let { diffs[Difficulty.AT] = it }
            if (diffs.isNotEmpty()) result[songId] = diffs
        }
        return result
    }

    private fun loadInfos(): Map<String, InfoCsvModel> {
        val result = mutableMapOf<String, InfoCsvModel>()
        reader.readText("info.csv").lineSequence().forEachIndexed { index, line ->
            if (index == 0 || line.isBlank()) return@forEachIndexed
            val parts = parseCsvLine(line)
            if (parts.size < 4) return@forEachIndexed
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

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                inQuotes && c == '"' -> inQuotes = false
                inQuotes -> current.append(c)
                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

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
