package com.example.videoplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.videoplayer.data.model.VideoCollection
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, id DESC")
    fun observeAll(): Flow<List<VideoCollection>>

    @Query(
        "SELECT * FROM collections WHERE (:groupId IS NULL AND groupId IS NULL) OR groupId = :groupId " +
            "ORDER BY sortOrder ASC, id DESC"
    )
    fun observeByGroup(groupId: Long?): Flow<List<VideoCollection>>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeById(id: Long): Flow<VideoCollection?>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): VideoCollection?

    @Query("SELECT * FROM collections WHERE folderUri = :folderUri")
    suspend fun getByFolderUri(folderUri: String): VideoCollection?

    @Insert
    suspend fun insert(collection: VideoCollection): Long

    @Update
    suspend fun update(collection: VideoCollection)

    @Delete
    suspend fun delete(collection: VideoCollection)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE collections SET groupId = :groupId WHERE id = :id")
    suspend fun moveToGroup(id: Long, groupId: Long?)

    @Query("UPDATE collections SET lastVideoId = :videoId WHERE id = :collectionId")
    suspend fun updateLastVideo(collectionId: Long, videoId: Long?)
}
