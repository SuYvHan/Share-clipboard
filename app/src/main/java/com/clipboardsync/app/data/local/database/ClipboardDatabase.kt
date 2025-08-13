package com.clipboardsync.app.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.clipboardsync.app.data.local.dao.ClipboardDao
import com.clipboardsync.app.data.local.entity.ClipboardEntity

@Database(
    entities = [ClipboardEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ClipboardDatabase : RoomDatabase() {
    
    abstract fun clipboardDao(): ClipboardDao
    
    companion object {
        const val DATABASE_NAME = "clipboard_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clipboard_items ADD COLUMN filePath TEXT")
            }
        }
    }
}
