package com.clipboardsync.app

import android.app.Application
import com.clipboardsync.app.domain.model.AppConfig
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ClipboardSyncApplication : Application() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    companion object {
        lateinit var instance: ClipboardSyncApplication
            private set

        val httpClient: OkHttpClient
            get() = instance.okHttpClient

        // 默认配置，实际使用时会从数据库加载
        val appConfig: AppConfig
            get() = AppConfig(
                deviceId = "default-device",
                serverHost = "47.239.194.151",
                httpPort = 80,
                websocketPort = 3002,
                authKey = "X-API-Key",
                authValue = "Qw133133",
                autoSync = true
            )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
