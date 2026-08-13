package com.ocrscreencapture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_analysis_history")
data class ImageAnalysisItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val description: String = "",
    val keywords: String = "",
    val detectedText: String = "",
    val analysis: String = "",
    val websites: String = "",
    val rawResponse: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
