package com.example.videoplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.videoplayer.data.model.Group
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.data.model.VideoCollection

@Database(
    entities = [Group::class, VideoCollection::class, Video::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun collectionDao(): CollectionDao
    abstract fun videoDao(): VideoDao

    companion object {
        const val NAME = "video_player.db"

        /** v1 → v2：groups 表新增 coverUri 列（分组封面） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN coverUri TEXT")
            }
        }
    }
}
