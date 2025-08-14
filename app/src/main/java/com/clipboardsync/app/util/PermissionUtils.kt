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
            // 方法1：通过BuildEx检测（鸿蒙4.0+）
            val buildExClass = Class.forName("com.huawei.system.BuildEx")
            val osBrand = buildExClass.getMethod("getOsBrand").invoke(null) as? String
            val osVersion = try {
                buildExClass.getMethod("getOsVersion").invoke(null) as? String
            } catch (e: Exception) {
                null
            }

            android.util.Log.d("PermissionUtils", "BuildEx检测 - osBrand: $osBrand, osVersion: $osVersion")

            if (osBrand == "harmony" || osBrand == "HarmonyOS") {
                android.util.Log.d("PermissionUtils", "通过BuildEx确认为鸿蒙系统")
                return true
            }

            false
        } catch (e: Exception) {
            android.util.Log.d("PermissionUtils", "BuildEx检测失败，尝试其他方法: ${e.message}")

            // 方法2：检查系统属性（鸿蒙4.2特有）
            val harmonyVersion = System.getProperty("hw_sc.build.platform.version")
            val harmonyOSVersion = System.getProperty("ro.build.version.harmony")
            val harmonyAPI = System.getProperty("ro.build.hw_emui_api_level")

            android.util.Log.d("PermissionUtils", "系统属性检测 - harmonyVersion: $harmonyVersion, harmonyOSVersion: $harmonyOSVersion, harmonyAPI: $harmonyAPI")

            if (!harmonyVersion.isNullOrEmpty() || !harmonyOSVersion.isNullOrEmpty()) {
                android.util.Log.d("PermissionUtils", "通过系统属性确认为鸿蒙系统")
                return true
            }

            // 方法3：检查特有的类（鸿蒙4.2）
            try {
                Class.forName("ohos.app.Context")
                android.util.Log.d("PermissionUtils", "通过ohos.app.Context确认为鸿蒙系统")
                return true
            } catch (e2: ClassNotFoundException) {
                // 继续其他检测
            }

            // 方法4：检查Build信息（华为设备的鸿蒙特征）
            val isHuawei = Build.BRAND.equals("huawei", ignoreCase = true) ||
                          Build.MANUFACTURER.equals("huawei", ignoreCase = true)
            val hasHarmonyIndicator = Build.MODEL.contains("harmony", ignoreCase = true) ||
                                    Build.DISPLAY.contains("harmony", ignoreCase = true) ||
                                    Build.FINGERPRINT.contains("harmony", ignoreCase = true) ||
                                    Build.PRODUCT.contains("harmony", ignoreCase = true)

            android.util.Log.d("PermissionUtils", "Build信息检测 - isHuawei: $isHuawei, hasHarmonyIndicator: $hasHarmonyIndicator")
            android.util.Log.d("PermissionUtils", "Build详情 - BRAND: ${Build.BRAND}, MANUFACTURER: ${Build.MANUFACTURER}, MODEL: ${Build.MODEL}")

            if (isHuawei && hasHarmonyIndicator) {
                android.util.Log.d("PermissionUtils", "通过Build信息确认为鸿蒙系统")
                return true
            }

            android.util.Log.d("PermissionUtils", "未检测到鸿蒙系统特征")
            false
        }
    }

    /**
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        val isHarmony = isHarmonyOS()

        android.util.Log.d("PermissionUtils", "权限申请策略 - 系统类型: ${if (isHarmony) "鸿蒙" else "Android"}, API级别: ${Build.VERSION.SDK_INT}")

        // 1. 通知权限 - 只在Android 13+申请（鸿蒙跳过）
        if (!isHarmony && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            android.util.Log.d("PermissionUtils", "添加通知权限 (Android 13+)")
        } else if (!isHarmony) {
            android.util.Log.d("PermissionUtils", "跳过通知权限申请 (Android 12及以下)")
        }

        // 2. 存储权限 - 根据API级别选择策略
        when {
            // API 33+：使用READ_MEDIA_IMAGES（新的媒体权限）
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                android.util.Log.d("PermissionUtils", "API 33+，添加媒体权限")
            }
            // API 30+：Android 11+ 使用分区存储，不需要申请传统存储权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                android.util.Log.d("PermissionUtils", "API 30+，使用分区存储，跳过存储权限申请")
                // 不添加存储权限，使用分区存储
            }
            // API 23-29：需要申请WRITE_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                android.util.Log.d("PermissionUtils", "API 23-29，添加存储权限")
            }
            // API 22及以下：权限在安装时授予，无需申请
            else -> {
                android.util.Log.d("PermissionUtils", "API 22及以下，权限在安装时授予")
            }
        }

        android.util.Log.d("PermissionUtils", "权限列表: $permissions")
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

                android.util.Log.d("PermissionUtils", "Android 13+ 通知权限检查: $granted")
                granted
            }

            // Android 12及以下：检查通知管理器状态
            else -> {
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        notificationManager.areNotificationsEnabled()
                    } else {
                        true // Android N以下假设通知已启用
                    }
                    android.util.Log.d("PermissionUtils", "Android 12及以下 通知管理器状态: $enabled")
                    enabled
                } catch (e: Exception) {
                    android.util.Log.w("PermissionUtils", "检查通知状态失败: ${e.message}")
                    true // 检查失败时假设已启用
                }
            }
        }
    }
    
    /**
     * 检查是否已授予存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        val isHarmony = isHarmonyOS()

        android.util.Log.d("PermissionUtils", "存储权限检查 - 系统: ${if (isHarmony) "鸿蒙" else "Android"}, API: ${Build.VERSION.SDK_INT}")

        val granted = when {
            // API 33+：检查媒体权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val mediaPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED

                android.util.Log.d("PermissionUtils", "API 33+ - 媒体权限: $mediaPermission")
                mediaPermission
            }

            // API 30+：Android 11+ 使用分区存储，不需要传统存储权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ 默认使用分区存储，应用可以访问自己的目录
                // 对于图片保存，使用 MediaStore API 不需要权限
                android.util.Log.d("PermissionUtils", "API 30+ - 使用分区存储，无需传统存储权限")
                true
            }

            // API 23-29：检查存储权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val storagePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                android.util.Log.d("PermissionUtils", "API 23-29 - 存储权限: $storagePermission")
                storagePermission
            }

            // API 22及以下：权限在安装时授予
            else -> {
                android.util.Log.d("PermissionUtils", "API 22及以下 - 权限在安装时授予")
                true
            }
        }

        android.util.Log.d("PermissionUtils", "存储权限检查结果: $granted (API ${Build.VERSION.SDK_INT})")
        return granted
    }
    
    /**
     * 获取未授予的权限列表
     */
    fun getDeniedPermissions(context: Context): List<String> {
        val deniedPermissions = mutableListOf<String>()
        val requiredPermissions = getRequiredPermissions()

        android.util.Log.d("PermissionUtils", "检查被拒绝的权限，需要的权限: $requiredPermissions")

        for (permission in requiredPermissions) {
            val isGranted = when (permission) {
                Manifest.permission.POST_NOTIFICATIONS -> {
                    // 使用自定义的通知权限检查逻辑
                    hasNotificationPermission(context)
                }
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                    // 使用自定义的存储权限检查逻辑
                    hasStoragePermission(context)
                }
                else -> {
                    // 其他权限使用标准检查
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }

            if (!isGranted) {
                deniedPermissions.add(permission)
                android.util.Log.d("PermissionUtils", "权限被拒绝: $permission")
            } else {
                android.util.Log.d("PermissionUtils", "权限已授予: $permission")
            }
        }

        android.util.Log.d("PermissionUtils", "被拒绝的权限列表: $deniedPermissions")
        return deniedPermissions
    }

    /**
     * 获取真正需要申请的权限列表（排除不需要申请的权限）
     */
    fun getPermissionsToRequest(context: Context): List<String> {
        val permissionsToRequest = mutableListOf<String>()
        val requiredPermissions = getRequiredPermissions()
        val isHarmony = isHarmonyOS()

        android.util.Log.d("PermissionUtils", "检查需要申请的权限，候选权限: $requiredPermissions")

        for (permission in requiredPermissions) {
            val shouldRequest = when (permission) {
                Manifest.permission.POST_NOTIFICATIONS -> {
                    // 只在Android 13+且非鸿蒙系统上申请
                    val needsRequest = !isHarmony &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED

                    android.util.Log.d("PermissionUtils", "通知权限检查 - 需要申请: $needsRequest")
                    needsRequest
                }
                Manifest.permission.READ_MEDIA_IMAGES -> {
                    // Android 13+ 的媒体权限
                    val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED

                    android.util.Log.d("PermissionUtils", "媒体权限检查 - 需要申请: $needsRequest")
                    needsRequest
                }
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                    // 只在 Android 6-10 上申请存储权限
                    val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED

                    android.util.Log.d("PermissionUtils", "存储权限检查 - 需要申请: $needsRequest (API ${Build.VERSION.SDK_INT})")
                    needsRequest
                }
                else -> {
                    // 其他权限正常检查
                    val needsRequest = ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    android.util.Log.d("PermissionUtils", "其他权限 $permission - 需要申请: $needsRequest")
                    needsRequest
                }
            }

            if (shouldRequest) {
                permissionsToRequest.add(permission)
                android.util.Log.d("PermissionUtils", "添加到申请列表: $permission")
            }
        }

        android.util.Log.d("PermissionUtils", "最终需要申请的权限列表: $permissionsToRequest")
        return permissionsToRequest
    }

    /**
     * 获取权限的描述信息
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限：用于显示同步状态和服务运行状态"
            Manifest.permission.READ_MEDIA_IMAGES -> "媒体权限：用于保存和读取图片文件（Android 13+）"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "存储权限：用于保存和读取图片文件（包含读写功能）"
            else -> "未知权限"
        }
    }
    
    /**
     * 获取权限的简短名称
     */
    fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限"
            Manifest.permission.READ_MEDIA_IMAGES -> "媒体权限"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "存储权限"
            else -> "未知权限"
        }
    }

    /**
     * 获取详细的系统和权限状态信息
     */
    fun getDetailedPermissionStatus(context: Context): String {
        val isHarmony = isHarmonyOS()
        val requiredPermissions = getRequiredPermissions()
        val permissionsToRequest = getPermissionsToRequest(context)
        val storageGranted = hasStoragePermission(context)
        val notificationGranted = hasNotificationPermission(context)

        return buildString {
            append("=== 系统信息 ===\n")
            append("系统类型: ${if (isHarmony) "鸿蒙系统" else "Android系统"}\n")
            append("API级别: ${Build.VERSION.SDK_INT}\n")
            append("Android版本: ${Build.VERSION.RELEASE}\n")
            append("设备品牌: ${Build.BRAND} ${Build.MODEL}\n")
            append("\n")

            append("=== 权限策略 ===\n")
            append("候选权限: $requiredPermissions\n")
            append("需要申请: $permissionsToRequest\n")
            append("📝 策略说明:\n")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    append("  - Android 13+：申请通知权限和媒体权限\n")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    append("  - Android 11-12：使用分区存储，无需存储权限\n")
                    append("  - 通知权限：Android 12及以下自动启用\n")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    append("  - Android 6-10：需要申请存储权限\n")
                    append("  - 通知权限：自动启用\n")
                }
                else -> {
                    append("  - Android 5及以下：所有权限在安装时授予\n")
                }
            }
            if (isHarmony) {
                append("  - 鸿蒙系统：跳过通知权限申请\n")
            }
            append("\n")

            append("=== 权限状态 ===\n")
            append("存储权限: ${if (storageGranted) "✅ 已就绪" else "❌ 未就绪"}")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> append(" (媒体权限)")
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> append(" (分区存储)")
                else -> append(" (传统存储)")
            }
            append("\n")

            append("通知权限: ${if (notificationGranted) "✅ 已启用" else "❌ 未启用"}")
            when {
                isHarmony -> append(" (鸿蒙系统跳过)")
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> append(" (需申请)")
                else -> append(" (自动启用)")
            }
            append("\n")

            append("整体状态: ${if (hasAllPermissions(context)) "✅ 全部就绪" else "❌ 需要权限"}")

            if (permissionsToRequest.isEmpty() && !hasAllPermissions(context)) {
                append("\n\n⚠️ 注意：虽然显示需要权限，但在当前Android版本下应用可以正常工作")
            }
        }
    }

    /**
     * 检查鸿蒙系统的权限状态（保持向后兼容）
     */
    fun checkHarmonyPermissionStatus(context: Context): String {
        return getDetailedPermissionStatus(context)
    }
}
