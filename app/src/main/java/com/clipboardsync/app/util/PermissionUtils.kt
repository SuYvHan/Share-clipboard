package com.clipboardsync.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * 检测是否为鸿蒙系统
     */
    private fun isHarmonyOS(): Boolean {
        return try {
            val buildExClass = Class.forName("com.huawei.system.BuildEx")
            val osBrand = buildExClass.getMethod("getOsBrand").invoke(null) as? String
            osBrand == "harmony" || osBrand == "HarmonyOS"
        } catch (e: Exception) {
            // 检查系统属性
            val harmonyVersion = System.getProperty("hw_sc.build.platform.version")
            val harmonyOSVersion = System.getProperty("ro.build.version.harmony")
            !harmonyVersion.isNullOrEmpty() || !harmonyOSVersion.isNullOrEmpty()
        }
    }

    /**
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        val isHarmony = isHarmonyOS()

        when {
            // 鸿蒙系统特殊处理：只申请存储权限
            isHarmony -> {
                android.util.Log.d("PermissionUtils", "鸿蒙系统检测到，只申请存储权限")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            // Android 13+ (API 33+)：通知权限 + 媒体权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                android.util.Log.d("PermissionUtils", "Android 13+，申请通知权限和媒体权限")
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            // Android 12 (API 31-32)：特殊处理存储权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                android.util.Log.d("PermissionUtils", "Android 12，特殊处理存储权限")
                // Android 12不再需要WRITE_EXTERNAL_STORAGE，但为了兼容性仍然申请
                // 主要依赖MediaStore API和分区存储
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            // Android 10-11 (API 29-30)：存储权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                android.util.Log.d("PermissionUtils", "Android 10-11，申请存储权限")
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            // Android 9 (API 28)：存储权限
            else -> {
                android.util.Log.d("PermissionUtils", "Android 9，申请存储权限")
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        android.util.Log.d("PermissionUtils", "最终权限列表: $permissions")
        return permissions
    }
    
    /**
     * 检查是否已授予所有必要权限
     */
    fun hasAllPermissions(context: Context): Boolean {
        val isHarmony = isHarmonyOS()

        // 检查存储权限
        val storageGranted = hasStoragePermission(context)

        // 检查通知权限（鸿蒙系统跳过）
        val notificationGranted = if (isHarmony) {
            true // 鸿蒙系统不检查通知权限
        } else {
            hasNotificationPermission(context)
        }

        val allGranted = storageGranted && notificationGranted

        android.util.Log.d("PermissionUtils", "权限检查结果 - 存储: $storageGranted, 通知: $notificationGranted, 全部: $allGranted")

        return allGranted
    }
    
    /**
     * 检查是否已授予通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        val isHarmony = isHarmonyOS()

        return when {
            // 鸿蒙系统：不检查通知权限，因为不会申请
            isHarmony -> {
                android.util.Log.d("PermissionUtils", "鸿蒙系统，跳过通知权限检查")
                true
            }

            // Android 13+：检查POST_NOTIFICATIONS权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                android.util.Log.d("PermissionUtils", "Android 13+ 通知权限: $granted")
                granted
            }

            // Android 12及以下：检查通知管理器状态
            else -> {
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        notificationManager.areNotificationsEnabled()
                    } else {
                        true
                    }
                    android.util.Log.d("PermissionUtils", "Android 12及以下 通知启用状态: $enabled")
                    enabled
                } catch (e: Exception) {
                    android.util.Log.w("PermissionUtils", "检查通知状态失败", e)
                    true
                }
            }
        }
    }
    
    /**
     * 检查是否已授予存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        val granted = when {
            // Android 13+：检查媒体权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            }

            // Android 12 (API 31-32)：特殊处理
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12中，即使没有WRITE_EXTERNAL_STORAGE权限，
                // 也可以通过MediaStore API保存到公共目录
                val writePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                // 检查是否可以写入媒体文件
                val canWriteMedia = try {
                    // 尝试检查MediaStore的可用性
                    val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.provider.MediaStore.Images.Media.getContentUri(
                            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                        )
                    } else {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    mediaUri != null
                } catch (e: Exception) {
                    false
                }

                android.util.Log.d("PermissionUtils", "Android 12 - writePermission: $writePermission, canWriteMedia: $canWriteMedia")
                writePermission || canWriteMedia
            }

            // Android 10-11：检查写入权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            // Android 9及以下：检查写入权限
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        android.util.Log.d("PermissionUtils", "存储权限检查结果: $granted (API ${Build.VERSION.SDK_INT})")
        return granted
    }
    
    /**
     * 获取未授予的权限列表
     */
    fun getDeniedPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 获取权限的描述信息
     */
    fun getPermissionDescription(permission: String): String {
        val isHarmony = isHarmonyOS()
        val systemInfo = if (isHarmony) "鸿蒙系统" else "Android系统"

        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限：用于显示同步状态和服务运行状态（$systemInfo）"
            Manifest.permission.READ_MEDIA_IMAGES -> "媒体权限：用于保存图片到相册（Android 13+）"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    "存储权限：用于保存图片到相册（Android 12，主要使用MediaStore API）"
                } else {
                    "存储权限：用于保存图片到相册（$systemInfo）"
                }
            }
            Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限：用于读取图片文件（Android 12及以下）"
            else -> "未知权限"
        }
    }
    
    /**
     * 获取权限的简短名称
     */
    fun getPermissionName(permission: String): String {
        val isHarmony = isHarmonyOS()
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限"
            Manifest.permission.READ_MEDIA_IMAGES -> if (isHarmony) "存储权限（鸿蒙）" else "媒体权限"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "存储权限"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限"
            else -> "未知权限"
        }
    }

    /**
     * 获取详细的系统和权限状态信息
     */
    fun getDetailedPermissionStatus(context: Context): String {
        val isHarmony = isHarmonyOS()
        val requiredPermissions = getRequiredPermissions()
        val storageGranted = hasStoragePermission(context)
        val notificationGranted = hasNotificationPermission(context)

        return buildString {
            append("=== 系统信息 ===\n")
            append("系统类型: ${if (isHarmony) "鸿蒙系统" else "Android系统"}\n")
            append("API级别: ${Build.VERSION.SDK_INT}\n")
            append("Android版本: ${Build.VERSION.RELEASE}\n")

            // Android 12特殊说明
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                append("⚠️ Android 12系统，使用分区存储\n")
            }
            append("\n")

            append("=== 权限申请策略 ===\n")
            append("需要申请的权限: $requiredPermissions\n")
            append("权限数量: ${requiredPermissions.size}\n")

            // Android 12特殊说明
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                append("📝 Android 12注意事项:\n")
                append("  - 主要依赖MediaStore API\n")
                append("  - 分区存储自动处理\n")
                append("  - 通知权限无需运行时申请\n")
            }
            append("\n")

            append("=== 权限状态 ===\n")
            append("存储权限: ${if (storageGranted) "✅ 已授予" else "❌ 未授予"}\n")
            append("通知权限: ${if (notificationGranted) "✅ 已启用" else "❌ 未启用"}")
            if (isHarmony) {
                append(" (鸿蒙系统不申请)")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                append(" (Android 12无需申请)")
            }
            append("\n")
            append("整体状态: ${if (hasAllPermissions(context)) "✅ 全部就绪" else "❌ 需要权限"}")
        }
    }

    /**
     * 检查鸿蒙系统的权限状态（保持向后兼容）
     */
    fun checkHarmonyPermissionStatus(context: Context): String {
        return getDetailedPermissionStatus(context)
    }
}
