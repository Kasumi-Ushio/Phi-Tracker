package org.kasumi321.ushio.phitracker.data.parser

import kotlin.math.min

class BinaryReader(
    private val data: ByteArray
) {
    var position: Int = 0
        private set

    val remaining: Int get() = data.size - position

    fun readByte(): Int = data[position++].toInt() and 0xFF

    fun readShort(): Int = readByte() or (readByte() shl 8)

    fun readInt(): Int = readByte() or (readByte() shl 8) or (readByte() shl 16) or (readByte() shl 24)

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readBoolean(): Boolean = readByte() != 0

    fun readVarShort(): Int {
        val first = readByte()
        return if (first < 128) first else (first and 0x7F) or (readByte() shl 7)
    }

    fun readString(): String {
        val length = readVarShort()
        val bytes = data.copyOfRange(position, position + length)
        position += length
        return bytes.decodeToString()
    }

    fun readStringTrimEnd(trimBytes: Int): String {
        val length = readVarShort()
        val end = position + min(length, length - trimBytes)
        val bytes = data.copyOfRange(position, end)
        position += length
        return bytes.decodeToString()
    }

    fun hasRemaining(): Boolean = remaining > 0

    fun skip(n: Int) {
        position += n
    }
}
