package com.clipboardsync.app.util

import android.content.Context
import android.util.Log
import java.lang.reflect.Method

/**
 * 鸿蒙系统兼容性助手
 * 提供鸿蒙系统特有的功能检测和兼容性处理
 */
object HarmonyCompatibilityHelper {
    
    private const val TAG = "HarmonyCompatibility"
    
    /**
     * 检测是否为鸿蒙系统
     */
    fun isHarmonyOS(): Boolean {
        return try {
            val buildExClass = Class.forName("com.huawei.system.BuildEx")
            val getOsBrandMethod: Method = buildExClass.getMethod("getOsBrand")
            val osBrand = getOsBrandMethod.invoke(null) as? String
            Log.d(TAG, "检测到系统品牌: $osBrand")
            "harmony".equals(osBrand, ignoreCase = true)
        } catch (e: Exception) {
            Log.d(TAG, "非鸿蒙系统或检测失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取鸿蒙系统版本
     */
    fun getHarmonyVersion(): String? {
        return try {
            val buildExClass = Class.forName("com.huawei.system.BuildEx")
            val getOsVersionMethod: Method = buildExClass.getMethod("getOsVersion")
            getOsVersionMethod.invoke(null) as? String
        } catch (e: Exception) {
            Log.d(TAG, "无法获取鸿蒙版本: ${e.message}")
            null
        }
    }
    
    /**
     * 检查鸿蒙系统是否支持特定功能
     */
    fun checkHarmonyFeatureSupport(feature: HarmonyFeature): Boolean {
        if (!isHarmonyOS()) {
            return false
        }

        val version = getHarmonyVersion()
        Log.d(TAG, "检查鸿蒙功能支持: $feature, 版本: $version")

        // 如果无法获取版本，假设是较新的鸿蒙系统，启用兼容性功能
        if (version == null) {
            Log.w(TAG, "无法获取鸿蒙版本，启用默认兼容性功能")
            return when (feature) {
                HarmonyFeature.CLIPBOARD_ACCESS -> true
                HarmonyFeature.NOTIFICATION_ICON -> true  // 启用PNG图标兼容
                HarmonyFeature.BACKGROUND_SERVICE -> true
            }
        }

        return when (feature) {
            HarmonyFeature.CLIPBOARD_ACCESS -> {
                // 鸿蒙4.0+支持更好的剪切板访问
                checkVersionSupport(version, "4.0")
            }
            HarmonyFeature.NOTIFICATION_ICON -> {
                // 鸿蒙4.2+对通知图标有特殊要求
                checkVersionSupport(version, "4.2")
            }
            HarmonyFeature.BACKGROUND_SERVICE -> {
                // 鸿蒙3.0+支持后台服务
                checkVersionSupport(version, "3.0")
            }
        }
    }
    
    /**
     * 检查版本支持
     */
    private fun checkVersionSupport(currentVersion: String?, requiredVersion: String): Boolean {
        if (currentVersion == null) return false
        
        return try {
            val current = parseVersion(currentVersion)
            val required = parseVersion(requiredVersion)
            current >= required
        } catch (e: Exception) {
            Log.w(TAG, "版本比较失败: ${e.message}")
            false
        }
    }
    
    /**
     * 解析版本号
     */
    private fun parseVersion(version: String): Double {
        val cleanVersion = version.replace(Regex("[^0-9.]"), "")
        val parts = cleanVersion.split(".")
        return if (parts.size >= 2) {
            "${parts[0]}.${parts[1]}".toDoubleOrNull() ?: 0.0
        } else {
            parts[0].toDoubleOrNull() ?: 0.0
        }
    }
    
    /**
     * 获取鸿蒙系统的推荐配置
     */
    fun getHarmonyRecommendedConfig(): HarmonyConfig {
        if (!isHarmonyOS()) {
            // 非鸿蒙系统，返回默认配置
            return HarmonyConfig(
                useCompatibleNotificationIcon = false,
                enableClipboardRetry = false,
                clipboardRetryCount = 1,
                clipboardRetryDelay = 0L,
                useSystemFallbackIcon = false
            )
        }

        val version = getHarmonyVersion()
        val versionNum = version?.let { parseVersion(it) } ?: 0.0

        // 如果无法获取版本，假设是较新的鸿蒙系统，启用所有兼容性功能
        if (version == null) {
            Log.i(TAG, "无法获取鸿蒙版本，启用全部兼容性功能")
            return HarmonyConfig(
                useCompatibleNotificationIcon = true,  // 启用PNG图标
                enableClipboardRetry = true,
                clipboardRetryCount = 5,
                clipboardRetryDelay = 200L,
                useSystemFallbackIcon = true
            )
        }

        return HarmonyConfig(
            useCompatibleNotificationIcon = versionNum >= 4.2,
            enableClipboardRetry = versionNum >= 4.0,
            clipboardRetryCount = if (versionNum >= 4.2) 5 else 3,
            clipboardRetryDelay = if (versionNum >= 4.2) 200L else 100L,
            useSystemFallbackIcon = versionNum >= 4.2
        )
    }
    
    /**
     * 记录鸿蒙系统信息
     */
    fun logHarmonySystemInfo(context: Context) {
        if (!isHarmonyOS()) {
            Log.d(TAG, "当前系统不是鸿蒙系统")
            return
        }
        
        val version = getHarmonyVersion()
        val config = getHarmonyRecommendedConfig()
        
        Log.i(TAG, "=== 鸿蒙系统信息 ===")
        Log.i(TAG, "系统版本: $version")
        Log.i(TAG, "剪切板访问支持: ${checkHarmonyFeatureSupport(HarmonyFeature.CLIPBOARD_ACCESS)}")
        Log.i(TAG, "通知图标支持: ${checkHarmonyFeatureSupport(HarmonyFeature.NOTIFICATION_ICON)}")
        Log.i(TAG, "后台服务支持: ${checkHarmonyFeatureSupport(HarmonyFeature.BACKGROUND_SERVICE)}")
        Log.i(TAG, "推荐配置: $config")
        Log.i(TAG, "==================")
    }
}

/**
 * 鸿蒙系统功能枚举
 */
enum class HarmonyFeature {
    CLIPBOARD_ACCESS,    // 剪切板访问
    NOTIFICATION_ICON,   // 通知图标
    BACKGROUND_SERVICE   // 后台服务
}

/**
 * 鸿蒙系统推荐配置
 */
data class HarmonyConfig(
    val useCompatibleNotificationIcon: Boolean,  // 使用兼容的通知图标
    val enableClipboardRetry: Boolean,           // 启用剪切板重试
    val clipboardRetryCount: Int,                // 剪切板重试次数
    val clipboardRetryDelay: Long,               // 剪切板重试延迟(ms)
    val useSystemFallbackIcon: Boolean          // 使用系统备用图标
)
