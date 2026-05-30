package org.kasumi321.ushio.phitracker.utils

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.min

/**
 * Stack Blur algorithm based on Mario Klingemann's classic implementation.
 *
 * A fast approximate Gaussian blur with O(n) time complexity.
 * Visually nearly indistinguishable from true Gaussian blur.
 * Supports arbitrary radii (1..254) with no external dependencies.
 *
 * Original: http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
 */
fun stackBlur(source: Bitmap, radius: Int): Bitmap {
    require(radius in 1..254) { "Radius must be between 1 and 254, got $radius" }

    val w = source.width
    val h = source.height
    val pix = IntArray(w * h)
    source.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val div = radius + radius + 1

    val r = IntArray(w * h)
    val g = IntArray(w * h)
    val b = IntArray(w * h)

    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (i in dv.indices) {
        dv[i] = i / divsum
    }

    val stack = Array(div) { IntArray(3) }
    val r1 = radius + 1

    // --- Horizontal pass ---
    for (y in 0 until h) {
        var rsum = 0; var gsum = 0; var bsum = 0
        var rinsum = 0; var ginsum = 0; var binsum = 0
        var routsum = 0; var goutsum = 0; var boutsum = 0

        val yOffset = y * w

        for (i in -radius..radius) {
            val srcX = min(wm, maxOf(i, 0))
            val p = pix[yOffset + srcX]
            val sir = stack[i + radius]
            sir[0] = (p shr 16) and 0xff
            sir[1] = (p shr 8) and 0xff
            sir[2] = p and 0xff
            val rbs = r1 - abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            } else {
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            }
        }

        var stackpointer = radius

        for (x in 0 until w) {
            val idx = yOffset + x
            r[idx] = dv[rsum]
            g[idx] = dv[gsum]
            b[idx] = dv[bsum]

            rsum -= routsum; gsum -= goutsum; bsum -= boutsum

            val stackstart = (stackpointer - radius + div) % div
            val sir = stack[stackstart]

            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

            val nextX = min(x + radius + 1, wm)
            val p = pix[yOffset + nextX]
            sir[0] = (p shr 16) and 0xff
            sir[1] = (p shr 8) and 0xff
            sir[2] = p and 0xff

            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum

            stackpointer = (stackpointer + 1) % div
            val sirOut = stack[stackpointer]

            routsum += sirOut[0]; goutsum += sirOut[1]; boutsum += sirOut[2]
            rinsum -= sirOut[0]; ginsum -= sirOut[1]; binsum -= sirOut[2]
        }
    }

    // --- Vertical pass ---
    for (x in 0 until w) {
        var rsum = 0; var gsum = 0; var bsum = 0
        var rinsum = 0; var ginsum = 0; var binsum = 0
        var routsum = 0; var goutsum = 0; var boutsum = 0

        for (i in -radius..radius) {
            val srcY = min(hm, maxOf(i, 0))
            val idx = srcY * w + x
            val sir = stack[i + radius]
            sir[0] = r[idx]; sir[1] = g[idx]; sir[2] = b[idx]
            val rbs = r1 - abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            } else {
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            }
        }

        var stackpointer = radius

        for (y in 0 until h) {
            val idx = y * w + x
            pix[idx] = (pix[idx] and 0xff000000.toInt()) or
                    (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

            rsum -= routsum; gsum -= goutsum; bsum -= boutsum

            val stackstart = (stackpointer - radius + div) % div
            val sir = stack[stackstart]

            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

            val nextY = min(y + radius + 1, hm)
            val nIdx = nextY * w + x
            sir[0] = r[nIdx]; sir[1] = g[nIdx]; sir[2] = b[nIdx]

            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum

            stackpointer = (stackpointer + 1) % div
            val sirOut = stack[stackpointer]

            routsum += sirOut[0]; goutsum += sirOut[1]; boutsum += sirOut[2]
            rinsum -= sirOut[0]; ginsum -= sirOut[1]; binsum -= sirOut[2]
        }
    }

    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(pix, 0, w, 0, 0, w, h)
    return result
}
