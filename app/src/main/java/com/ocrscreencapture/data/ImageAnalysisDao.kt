package com.ocrscreencapture.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageAnalysisDao {

    @Query("SELECT * FROM image_analysis_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ImageAnalysisItem>>

    @Insert
    suspend fun insert(item: ImageAnalysisItem)

    @Delete
    suspend fun delete(item: ImageAnalysisItem)

    @Query("DELETE FROM image_analysis_history")
    suspend fun deleteAll()
}
