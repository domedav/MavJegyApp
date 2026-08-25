package com.domedav.mavjegy.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Képben lévő vonalkód dekódolása (a MÁV szerver jegyképéből).
 * Az eredeti app ezt a képet mutatja – mi a BENNE lévő kód adattartalmát
 * olvassuk ki, és azt kódoljuk újra lokális Aztec-be.
 */
object BarcodeImageDecoder {

    private val FORMATS = listOf(
        BarcodeFormat.AZTEC,
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.PDF_417,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39
    )

    /** Vonalkód szöveg kinyerése képből; forgatva és invertálva is próbálkozik. */
    fun decode(source: Bitmap): String? {
        var bmp = source
        // Nagy képek méretezése a dekódoláshoz
        val maxDim = 2000
        val largest = maxOf(bmp.width, bmp.height)
        if (largest > maxDim) {
            val scale = maxDim.toFloat() / largest
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }

        // 4 kísérlet: eredeti, 180°, invertált, 180°+invertált
        for (rotation in intArrayOf(0, 180)) {
            val rotated = if (rotation == 0) bmp else rotate(bmp, rotation.toFloat())
            decodeOnce(rotated, false)?.let { return it }
            decodeOnce(rotated, true)?.let { return it }
        }
        return null
    }

    private fun decodeOnce(bmp: Bitmap, invert: Boolean): String? {
        return try {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val lum = RGBLuminanceSource(w, h, pixels)
            val bin = BinaryBitmap(if (invert) HybridBinarizer(InvertedLuminance(lum)) else HybridBinarizer(lum))
            val reader = MultiFormatReader()
            val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to FORMATS)
            reader.setHints(hints)
            reader.decodeWithState(bin).text
        } catch (_: Exception) {
            null
        } finally {
            // nincs teendő
        }
    }

    private fun rotate(bmp: Bitmap, degrees: Float): Bitmap =
        android.graphics.Matrix().apply { postRotate(degrees) }
            .let { Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, it, true) }

    /** Fekete-fehér csere (világos kód sötét háttéren). */
    private class InvertedLuminance(private val wrapped: com.google.zxing.LuminanceSource) :
        com.google.zxing.LuminanceSource(wrapped.width, wrapped.height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val r = wrapped.getRow(y, row)
            for (i in r.indices) r[i] = (255 - r[i]).toByte()
            return r
        }

        override fun getMatrix(): ByteArray {
            val m = wrapped.matrix
            for (i in m.indices) m[i] = (255 - m[i]).toByte()
            return m
        }

        override fun isCropSupported(): Boolean = wrapped.isCropSupported
        override fun crop(left: Int, top: Int, width: Int, height: Int): com.google.zxing.LuminanceSource =
            InvertedLuminance(wrapped.crop(left, top, width, height))

        override fun isRotateSupported(): Boolean = wrapped.isRotateSupported
        override fun rotateCounterClockwise(): com.google.zxing.LuminanceSource =
            InvertedLuminance(wrapped.rotateCounterClockwise())
    }
}
