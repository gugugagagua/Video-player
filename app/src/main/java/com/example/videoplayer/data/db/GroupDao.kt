package com.example.videoplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.videoplayer.data.model.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Group>>

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Group>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: Long): Group?

    @Insert
    suspend fun insert(group: Group): Long

    @Update
    suspend fun update(group: Group)

    @Delete
    suspend fun delete(group: Group)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE groups SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE groups SET coverUri = :coverUri WHERE id = :id")
    suspend fun updateCover(id: Long, coverUri: String?)
}
