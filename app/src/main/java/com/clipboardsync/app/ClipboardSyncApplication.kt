package com.clipboardsync.app

import android.app.Application
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.repository.ConfigRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ClipboardSyncApplication : Application() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var configRepository: ConfigRepository

    companion object {
        lateinit var instance: ClipboardSyncApplication
            private set

        val httpClient: OkHttpClient
            get() = instance.okHttpClient

        /**
         * 获取实际的用户配置（同步方法，仅用于紧急情况）
         * 建议在实际使用中通过依赖注入获取ConfigRepository
         */
        fun getCurrentConfig(): AppConfig {
            return try {
                runBlocking {
                    instance.configRepository.getConfig().first()
                }
            } catch (e: Exception) {
                // 如果获取配置失败，返回默认配置
                appConfig
            }
        }

        // 默认配置，实际使用时会从数据库加载
        // 注意：这只是临时配置，实际应用中应该从ConfigRepository获取用户设置
        val appConfig: AppConfig
            get() = AppConfig(
                deviceId = "default-device",
                serverHost = "47.239.194.151",
                httpPort = 3001,  // 用户可配置
                websocketPort = 3002,  // 用户可配置
                authKey = "X-API-Key",  // 用户可配置
                authValue = "Qw133133",  // 用户可配置
                autoSync = true,
                useSecureConnection = false  // 用户可配置：HTTP/HTTPS切换
            )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
