package com.example.videoplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.data.model.VideoCount
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT collectionId, COUNT(*) AS count FROM videos GROUP BY collectionId")
    fun observeCounts(): Flow<List<VideoCount>>

    @Query("SELECT * FROM videos WHERE collectionId = :collectionId ORDER BY sortOrder ASC, id ASC")
    fun observeByCollection(collectionId: Long): Flow<List<Video>>

    @Query("SELECT * FROM videos WHERE collectionId = :collectionId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByCollection(collectionId: Long): List<Video>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: Long): Video?

    @Query("SELECT * FROM videos WHERE fileUri = :fileUri")
    suspend fun getByFileUri(fileUri: String): Video?

    @Insert
    suspend fun insert(video: Video): Long

    @Insert
    suspend fun insertAll(videos: List<Video>)

    @Update
    suspend fun update(video: Video)

    @Delete
    suspend fun delete(video: Video)

    @Query("DELETE FROM videos WHERE collectionId = :collectionId")
    suspend fun deleteByCollection(collectionId: Long)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE videos SET lastPosition = :position WHERE id = :id")
    suspend fun updateLastPosition(id: Long, position: Long)

    @Query("UPDATE videos SET duration = :duration WHERE id = :id")
    suspend fun updateDuration(id: Long, duration: Long)

    @Query("UPDATE videos SET coverUri = :coverUri WHERE id = :id")
    suspend fun updateCover(id: Long, coverUri: String?)
}
