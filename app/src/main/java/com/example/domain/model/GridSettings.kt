package com.example.domain.model

data class GridSettings(
    val rows: Int = 5,
    val cols: Int = 4,
    val marginTop: Float = 0f,
    val marginBottom: Float = 0f,
    val marginLeft: Float = 0f,
    val marginRight: Float = 0f,
    val rowSpacing: Float = 0f,
    val colSpacing: Float = 0f
)
