package com.clipboardsync.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val serverHost: String = "",
    val websocketPort: Int = 3002,
    val deviceId: String = "",
    val autoSync: Boolean = true,
    val syncImages: Boolean = true,
    val syncFiles: Boolean = true,
    val maxHistoryItems: Int = 100,
    val autoCleanupDays: Int = 30,
    val enableNotifications: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val authKey: String = "",
    val authValue: String = "",
    val useSecureConnection: Boolean = false
) {
    val websocketUrl: String
        get() = if (useSecureConnection) {
            "wss://$serverHost:$websocketPort/"
        } else {
            "ws://$serverHost:$websocketPort/"
        }
}
