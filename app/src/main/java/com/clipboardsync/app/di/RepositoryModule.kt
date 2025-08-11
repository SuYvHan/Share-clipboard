package com.clipboardsync.app.di

import com.clipboardsync.app.data.repository.ConfigRepositoryImpl
import com.clipboardsync.app.data.repository.ClipboardRepositoryImpl
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.domain.repository.ClipboardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConfigRepository(
        configRepositoryImpl: ConfigRepositoryImpl
    ): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindClipboardRepository(
        clipboardRepositoryImpl: ClipboardRepositoryImpl
    ): ClipboardRepository
}
