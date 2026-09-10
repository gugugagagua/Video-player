package com.example.videoplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.videoplayer.data.model.LastWatched
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.data.model.VideoCount
import com.example.videoplayer.data.model.VideoSearchResult
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT collectionId, COUNT(*) AS count FROM videos GROUP BY collectionId")
    fun observeCounts(): Flow<List<VideoCount>>

    /** 各视频集「看到第几集」（取 lastVideoId 对应的排序下标） */
    @Query(
        "SELECT c.id AS collectionId, v.sortOrder AS sortOrder " +
            "FROM collections c INNER JOIN videos v ON v.id = c.lastVideoId"
    )
    fun observeLastWatched(): Flow<List<LastWatched>>

    /** 按视频文件名模糊搜索（带所属视频集名与进度） */
    @Query(
        "SELECT v.id AS id, v.fileName AS fileName, v.collectionId AS collectionId, " +
            "c.name AS collectionName, v.lastPosition AS lastPosition, v.duration AS duration " +
            "FROM videos v INNER JOIN collections c ON c.id = v.collectionId " +
            "WHERE v.fileName LIKE '%' || :query || '%' " +
            "ORDER BY c.name ASC, v.sortOrder ASC LIMIT 200"
    )
    suspend fun searchVideos(query: String): List<VideoSearchResult>

    /** 有观看记录但缺少时长的条目（用于补全进度百分比） */
    @Query(
        "SELECT * FROM videos WHERE collectionId = :collectionId " +
            "AND duration = 0 AND lastPosition > 0"
    )
    suspend fun getMissingDurations(collectionId: Long): List<Video>

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
