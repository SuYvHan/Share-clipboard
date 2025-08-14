package com.clipboardsync.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object SmsPermissionHelper {
    
    const val READ_SMS_PERMISSION = Manifest.permission.READ_SMS
    const val RECEIVE_SMS_PERMISSION = Manifest.permission.RECEIVE_SMS
    
    /**
     * 检查是否已授予短信权限
     */
    fun hasSmsPermissions(context: Context): Boolean {
        return hasReadSmsPermission(context) && hasReceiveSmsPermission(context)
    }
    
    /**
     * 检查是否已授予读取短信权限
     */
    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            READ_SMS_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查是否已授予接收短信权限
     */
    fun hasReceiveSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            RECEIVE_SMS_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取需要申请的短信权限列表
     */
    fun getRequiredSmsPermissions(context: Context): List<String> {
        val permissions = mutableListOf<String>()
        
        if (!hasReadSmsPermission(context)) {
            permissions.add(READ_SMS_PERMISSION)
        }
        
        if (!hasReceiveSmsPermission(context)) {
            permissions.add(RECEIVE_SMS_PERMISSION)
        }
        
        return permissions
    }
    
    /**
     * 获取所有短信相关权限
     */
    fun getAllSmsPermissions(): Array<String> {
        return arrayOf(READ_SMS_PERMISSION, RECEIVE_SMS_PERMISSION)
    }
    
    /**
     * 检查权限是否被永久拒绝（需要在Activity中调用）
     */
    fun isPermissionPermanentlyDenied(
        context: Context,
        permission: String,
        shouldShowRequestPermissionRationale: (String) -> Boolean
    ): Boolean {
        return !shouldShowRequestPermissionRationale(permission) && 
               ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取权限说明文本
     */
    fun getPermissionExplanation(): String {
        return """
            为了自动上传短信验证码，应用需要以下权限：
            
            • 读取短信权限：用于读取收到的短信内容
            • 接收短信权限：用于监听新收到的短信
            
            这些权限仅用于检测和上传验证码短信，不会读取或上传其他个人短信内容。
            您可以在设置中随时关闭短信自动上传功能。
        """.trimIndent()
    }
    
    /**
     * 获取权限被拒绝时的说明文本
     */
    fun getPermissionDeniedExplanation(): String {
        return """
            短信权限被拒绝，无法自动上传验证码。
            
            如需使用此功能，请：
            1. 前往应用设置
            2. 找到权限管理
            3. 开启短信相关权限
            
            或者您可以在应用设置中关闭短信自动上传功能。
        """.trimIndent()
    }
}
