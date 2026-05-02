package org.kasumi321.ushio.phitracker.data.parser

class BinaryReader(data: ByteArray) {
    private var pos = 0
    private val data = data

    val position: Int get() = pos
    val remaining: Int get() = data.size - pos

    fun readByte(): Int = data[pos++].toInt() and 0xFF

    fun readShort(): Int {
        val low = data[pos++].toInt() and 0xFF
        val high = data[pos++].toInt() and 0xFF
        return low or (high shl 8)
    }

    fun readInt(): Int {
        val b0 = data[pos++].toInt() and 0xFF
        val b1 = data[pos++].toInt() and 0xFF
        val b2 = data[pos++].toInt() and 0xFF
        val b3 = data[pos++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readBoolean(): Boolean = readByte() != 0

    fun readVarShort(): Int {
        val first = readByte()
        return if (first < 128) first else (first and 0x7F) or (readByte() shl 7)
    }

    fun readString(): String {
        val length = readVarShort()
        val bytes = data.copyOfRange(pos, pos + length)
        pos += length
        return bytes.decodeToString()
    }

    fun readStringTrimEnd(trimBytes: Int): String {
        val length = readVarShort()
        val bytes = data.copyOfRange(pos, pos + length - trimBytes)
        pos += length
        return bytes.decodeToString()
    }

    fun hasRemaining(): Boolean = pos < data.size

    fun skip(n: Int) {
        pos += n
    }
}
