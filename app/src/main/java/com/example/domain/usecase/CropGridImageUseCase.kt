package com.example.domain.usecase

import android.graphics.Bitmap
import com.example.domain.model.GridSettings

class CropGridImageUseCase {
    operator fun invoke(
        src: Bitmap,
        settings: GridSettings,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        val width = src.width
        val height = src.height

        val rows = settings.rows.coerceAtLeast(1)
        val cols = settings.cols.coerceAtLeast(1)

        val tM = settings.marginTop.coerceIn(0f, height.toFloat())
        val bM = settings.marginBottom.coerceIn(0f, height.toFloat())
        val lM = settings.marginLeft.coerceIn(0f, width.toFloat())
        val rM = settings.marginRight.coerceIn(0f, width.toFloat())
        val rS = settings.rowSpacing
        val cS = settings.colSpacing

        val activeWidth = (width - lM - rM).coerceAtLeast(1f)
        val activeHeight = (height - tM - bM).coerceAtLeast(1f)

        val cellW = ((activeWidth - (cols - 1) * cS) / cols).coerceAtLeast(1f)
        val cellH = ((activeHeight - (rows - 1) * rS) / rows).coerceAtLeast(1f)

        for (r in 0 until rows) {
            for (c in (cols - 1) downTo 0) {
                val x = (lM + c * (cellW + cS)).toInt().coerceIn(0, width - 1)
                val y = (tM + r * (cellH + rS)).toInt().coerceIn(0, height - 1)
                val w = cellW.toInt().coerceIn(1, width - x)
                val h = cellH.toInt().coerceIn(1, height - y)

                val cropped = Bitmap.createBitmap(src, x, y, w, h)
                val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
                if (cropped != scaled) {
                    cropped.recycle()
                }
                result.add(scaled)
            }
        }
        return result
    }
}
