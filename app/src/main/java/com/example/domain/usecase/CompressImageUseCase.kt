package com.example.domain.usecase

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class CompressImageUseCase {
    operator fun invoke(bitmap: Bitmap, targetKB: Double): Pair<ByteArray, Double> {
        var quality = 85
        var outputBytes = ByteArray(0)
        var sizeKB = 0.0

        while (quality >= 10) {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            outputBytes = bos.toByteArray()
            sizeKB = outputBytes.size / 1024.0

            if (sizeKB <= targetKB) {
                break
            }
            quality -= 5
        }

        return Pair(outputBytes, sizeKB)
    }
}
