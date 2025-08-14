package com.clipboardsync.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val serverHost: String = "",
    val websocketPort: Int = 3002,
    val httpPort: Int = 80,
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
    val useSecureConnection: Boolean = false,
    val autoUploadSms: Boolean = true,
    val smsKeywords: List<String> = listOf("验证码", "验证", "code", "Code", "CODE", "验证码是", "动态码", "校验码"),
    val smsFilterSender: Boolean = true,
    val trustedSenders: List<String> = listOf("10086", "10010", "10000", "95533", "95588", "95599")
) {
    val websocketUrl: String
        get() = if (useSecureConnection) {
            "wss://$serverHost:$websocketPort/"
        } else {
            "ws://$serverHost:$websocketPort/"
        }

    val httpUrl: String
        get() = if (useSecureConnection) {
            if (httpPort == 443) {
                "https://$serverHost"
            } else {
                "https://$serverHost:$httpPort"
            }
        } else {
            if (httpPort == 80) {
                "http://$serverHost"
            } else {
                "http://$serverHost:$httpPort"
            }
        }

    val websocketUrlWithAuth: String
        get() {
            val baseUrl = websocketUrl
            val params = mutableListOf<String>()

            // 添加设备ID
            if (deviceId.isNotEmpty()) {
                params.add("deviceId=$deviceId")
            }

            // 添加认证参数
            if (authKey.isNotEmpty() && authValue.isNotEmpty()) {
                params.add("authKey=$authKey")
                params.add("authValue=$authValue")
            }

            return if (params.isNotEmpty()) {
                "$baseUrl?${params.joinToString("&")}"
            } else {
                baseUrl
            }
        }
}
