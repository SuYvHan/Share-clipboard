package com.clipboardsync.app.di

import android.content.Context
import androidx.room.Room
import com.clipboardsync.app.data.local.dao.ClipboardDao
import com.clipboardsync.app.data.local.database.ClipboardDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideClipboardDatabase(
        @ApplicationContext context: Context
    ): ClipboardDatabase {
        return Room.databaseBuilder(
            context,
            ClipboardDatabase::class.java,
            ClipboardDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideClipboardDao(database: ClipboardDatabase): ClipboardDao {
        return database.clipboardDao()
    }
}
