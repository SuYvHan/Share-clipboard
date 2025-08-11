package com.clipboardsync.app.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.clipboardsync.app.data.local.dao.ClipboardDao
import com.clipboardsync.app.data.local.entity.ClipboardEntity

@Database(
    entities = [ClipboardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ClipboardDatabase : RoomDatabase() {
    
    abstract fun clipboardDao(): ClipboardDao
    
    companion object {
        const val DATABASE_NAME = "clipboard_database"
    }
}
