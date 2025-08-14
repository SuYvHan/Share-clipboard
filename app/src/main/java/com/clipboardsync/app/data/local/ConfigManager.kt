package com.clipboardsync.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clipboardsync.app.domain.model.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

@Singleton
class ConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ConfigManager"
    private val dataStore = context.dataStore
    
    companion object {
        private val SERVER_HOST = stringPreferencesKey("server_host")
        private val WEBSOCKET_PORT = intPreferencesKey("websocket_port")
        private val HTTP_PORT = intPreferencesKey("http_port")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        private val SYNC_IMAGES = booleanPreferencesKey("sync_images")
        private val SYNC_FILES = booleanPreferencesKey("sync_files")
        private val MAX_HISTORY_ITEMS = intPreferencesKey("max_history_items")
        private val AUTO_CLEANUP_DAYS = intPreferencesKey("auto_cleanup_days")
        private val ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications")
        private val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        private val AUTH_KEY = stringPreferencesKey("auth_key")
        private val AUTH_VALUE = stringPreferencesKey("auth_value")
        private val USE_SECURE_CONNECTION = booleanPreferencesKey("use_secure_connection")

        // 短信相关配置
        private val AUTO_UPLOAD_SMS = booleanPreferencesKey("auto_upload_sms")
        private val SMS_KEYWORDS = stringSetPreferencesKey("sms_keywords")
        private val SMS_FILTER_SENDER = booleanPreferencesKey("sms_filter_sender")
        private val TRUSTED_SENDERS = stringSetPreferencesKey("trusted_senders")
    }
    
    val configFlow: Flow<AppConfig> = dataStore.data.map { preferences ->
        val deviceId = preferences[DEVICE_ID] ?: ""
        val authKey = preferences[AUTH_KEY] ?: ""
        val authValue = preferences[AUTH_VALUE] ?: ""
        Log.d(tag, "Reading from DataStore - deviceId: '$deviceId', authKey: '$authKey', authValue: '$authValue'")

        AppConfig(
            serverHost = preferences[SERVER_HOST] ?: "47.239.194.151",
            websocketPort = preferences[WEBSOCKET_PORT] ?: 3002,
            httpPort = preferences[HTTP_PORT] ?: 80,
            deviceId = deviceId,
            autoSync = preferences[AUTO_SYNC] ?: true,
            syncImages = preferences[SYNC_IMAGES] ?: true,
            syncFiles = preferences[SYNC_FILES] ?: true,
            maxHistoryItems = preferences[MAX_HISTORY_ITEMS] ?: 100,
            autoCleanupDays = preferences[AUTO_CLEANUP_DAYS] ?: 30,
            enableNotifications = preferences[ENABLE_NOTIFICATIONS] ?: true,
            autoStartOnBoot = preferences[AUTO_START_ON_BOOT] ?: true,
            authKey = authKey,
            authValue = authValue,
            useSecureConnection = preferences[USE_SECURE_CONNECTION] ?: false,
            autoUploadSms = preferences[AUTO_UPLOAD_SMS] ?: true,
            smsKeywords = preferences[SMS_KEYWORDS]?.toList() ?: listOf("验证码", "验证", "code", "Code", "CODE", "验证码是", "动态码", "校验码"),
            smsFilterSender = preferences[SMS_FILTER_SENDER] ?: true,
            trustedSenders = preferences[TRUSTED_SENDERS]?.toList() ?: listOf("10086", "10010", "10000", "95533", "95588", "95599")
        )
    }
    
    suspend fun updateConfig(config: AppConfig) {
        dataStore.edit { preferences ->
            preferences[SERVER_HOST] = config.serverHost
            preferences[WEBSOCKET_PORT] = config.websocketPort
            preferences[HTTP_PORT] = config.httpPort
            preferences[DEVICE_ID] = config.deviceId.ifEmpty { generateDeviceId() }
            preferences[AUTO_SYNC] = config.autoSync
            preferences[SYNC_IMAGES] = config.syncImages
            preferences[SYNC_FILES] = config.syncFiles
            preferences[MAX_HISTORY_ITEMS] = config.maxHistoryItems
            preferences[AUTO_CLEANUP_DAYS] = config.autoCleanupDays
            preferences[ENABLE_NOTIFICATIONS] = config.enableNotifications
            preferences[AUTO_START_ON_BOOT] = config.autoStartOnBoot
            preferences[AUTH_KEY] = config.authKey
            preferences[AUTH_VALUE] = config.authValue
            preferences[USE_SECURE_CONNECTION] = config.useSecureConnection

            // 保存短信相关配置
            preferences[AUTO_UPLOAD_SMS] = config.autoUploadSms
            preferences[SMS_KEYWORDS] = config.smsKeywords.toSet()
            preferences[SMS_FILTER_SENDER] = config.smsFilterSender
            preferences[TRUSTED_SENDERS] = config.trustedSenders.toSet()
        }
    }
    
    suspend fun updateServerConfig(host: String, websocketPort: Int, httpPort: Int = 80, deviceId: String = "", authKey: String = "", authValue: String = "") {
        Log.d(tag, "Saving to DataStore - deviceId: '$deviceId', authKey: '$authKey', authValue: '$authValue', httpPort: $httpPort")
        dataStore.edit { preferences ->
            preferences[SERVER_HOST] = host
            preferences[WEBSOCKET_PORT] = websocketPort
            preferences[HTTP_PORT] = httpPort
            // 如果设备ID为空，生成一个新的；否则使用提供的ID
            val finalDeviceId = if (deviceId.isBlank()) {
                // 检查是否已经有设备ID，如果没有则生成新的
                preferences[DEVICE_ID] ?: generateDeviceId()
            } else {
                deviceId
            }
            preferences[DEVICE_ID] = finalDeviceId
            preferences[AUTH_KEY] = authKey
            preferences[AUTH_VALUE] = authValue
            Log.d(tag, "Final deviceId saved: '$finalDeviceId'")
        }
        Log.d(tag, "DataStore save completed")
    }
    
    suspend fun updateDeviceId(deviceId: String) {
        dataStore.edit { preferences ->
            preferences[DEVICE_ID] = deviceId.ifEmpty { generateDeviceId() }
        }
    }
    
    suspend fun updateSyncSettings(autoSync: Boolean, syncImages: Boolean, syncFiles: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_SYNC] = autoSync
            preferences[SYNC_IMAGES] = syncImages
            preferences[SYNC_FILES] = syncFiles
        }
    }
    
    private fun generateDeviceId(): String {
        return "device_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }
}
