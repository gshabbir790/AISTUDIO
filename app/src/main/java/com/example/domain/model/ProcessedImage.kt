package com.example.domain.model

import android.graphics.Bitmap

data class ProcessedImage(
    val id: String,
    val defaultName: String,
    val customName: String,
    val sizeText: String,
    val bitmap: Bitmap,
    val jpegBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProcessedImage

        if (id != other.id) return false
        if (defaultName != other.defaultName) return false
        if (customName != other.customName) return false
        if (sizeText != other.sizeText) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + defaultName.hashCode()
        result = 31 * result + customName.hashCode()
        result = 31 * result + sizeText.hashCode()
        return result
    }
}
