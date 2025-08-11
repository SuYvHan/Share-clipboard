package com.clipboardsync.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    /**
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            // Android 12及以下
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        return permissions
    }
    
    /**
     * 检查是否已授予所有必要权限
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查是否已授予通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12及以下不需要运行时权限
        }
    }
    
    /**
     * 检查是否已授予存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
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
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限：用于显示同步状态和服务运行状态"
            Manifest.permission.READ_MEDIA_IMAGES -> "媒体权限：用于保存图片到相册"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "存储权限：用于保存图片到相册"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限：用于读取图片文件"
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
            Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限"
            else -> "未知权限"
        }
    }
}
