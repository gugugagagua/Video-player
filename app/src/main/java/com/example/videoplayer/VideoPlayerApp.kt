package com.example.videoplayer

import android.app.Application
import androidx.room.Room
import com.example.videoplayer.data.SettingsStore
import com.example.videoplayer.data.db.AppDatabase

class VideoPlayerApp : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }
}
