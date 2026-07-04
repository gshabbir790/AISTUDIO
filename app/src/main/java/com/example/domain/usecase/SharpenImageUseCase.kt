package com.example.domain.usecase

import android.graphics.Bitmap

class SharpenImageUseCase {
    operator fun invoke(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val k01 = -0.4f
        val k10 = -0.4f
        val k11 = 2.6f
        val k12 = -0.4f
        val k21 = -0.4f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x

                val n = (y - 1) * width + x
                val s = (y + 1) * width + x
                val w = y * width + (x - 1)
                val e = y * width + (x + 1)

                val pC = pixels[index]
                val rC = (pC shr 16) and 0xFF
                val gC = (pC shr 8) and 0xFF
                val bC = pC and 0xFF

                val pN = pixels[n]
                val pS = pixels[s]
                val pW = pixels[w]
                val pE = pixels[e]

                val r = (rC * k11 +
                        ((pN shr 16) and 0xFF) * k01 +
                        ((pW shr 16) and 0xFF) * k10 +
                        ((pE shr 16) and 0xFF) * k12 +
                        ((pS shr 16) and 0xFF) * k21).toInt().coerceIn(0, 255)

                val g = (gC * k11 +
                        ((pN shr 8) and 0xFF) * k01 +
                        ((pW shr 8) and 0xFF) * k10 +
                        ((pE shr 8) and 0xFF) * k12 +
                        ((pS shr 8) and 0xFF) * k21).toInt().coerceIn(0, 255)

                val b = (bC * k11 +
                        (pN and 0xFF) * k01 +
                        (pW and 0xFF) * k10 +
                        (pE and 0xFF) * k12 +
                        (pS and 0xFF) * k21).toInt().coerceIn(0, 255)

                val alpha = (pC shr 24) and 0xFF

                outPixels[index] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }
}
