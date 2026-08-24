package com.domedav.mavjegy.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object BarcodeGenerator {

    enum class Type { AZTEC, CODE128 }

    fun generate(content: String, type: Type, width: Int, height: Int): ImageBitmap {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        if (type == Type.AZTEC) {
            hints[EncodeHintType.ERROR_CORRECTION] = 50
        }
        val format = when (type) {
            Type.AZTEC -> BarcodeFormat.AZTEC
            Type.CODE128 -> BarcodeFormat.CODE_128
        }
        val matrix: BitMatrix = MultiFormatWriter().encode(content, format, width, height, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }
}
