package com.clipboardsync.app.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clipboardsync.app.R
import com.clipboardsync.app.ui.main.MainActivity
import kotlinx.coroutines.delay

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_ID = "clipboard_sync_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }
    
    private var retryCount = 0
    private var isNotificationEnabled = true
    
    /**
     * 尝试创建通知渠道，支持重试机制
     */
    suspend fun createNotificationChannelWithRetry(): Boolean {
        if (!isNotificationEnabled) {
            Log.w(TAG, "Notification disabled after max retries")
            return false
        }
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Attempting to create notification channel, attempt: ${attempt + 1}")
                
                if (createNotificationChannel()) {
                    Log.i(TAG, "Notification channel created successfully")
                    retryCount = 0
                    return true
                }
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.w(TAG, "Failed to create notification channel, retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel on attempt ${attempt + 1}", e)
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        Log.e(TAG, "Failed to create notification channel after $MAX_RETRY_ATTEMPTS attempts, disabling notifications")
        isNotificationEnabled = false
        retryCount = MAX_RETRY_ATTEMPTS
        return false
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // 检查是否已存在渠道
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel != null) {
                    Log.d(TAG, "Notification channel already exists")
                    return true
                }
                
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "剪切板同步服务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持剪切板同步服务运行"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                
                notificationManager.createNotificationChannel(channel)
                
                // 验证渠道是否创建成功
                val createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (createdChannel != null) {
                    Log.d(TAG, "Notification channel created and verified")
                    return true
                } else {
                    Log.e(TAG, "Notification channel creation failed - channel not found after creation")
                    return false
                }
            } else {
                // Android 8.0以下不需要渠道
                Log.d(TAG, "Android version < O, no channel needed")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating notification channel", e)
            false
        }
    }
    
    /**
     * 创建前台服务通知
     */
    fun createForegroundNotification(content: String): Notification? {
        if (!isNotificationEnabled) {
            Log.w(TAG, "Notifications disabled, returning null")
            return null
        }
        
        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("剪切板同步")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setSound(null)
                .setVibrate(null)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
            null
        }
    }
    
    /**
     * 检查通知权限
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    
    /**
     * 更新通知内容
     */
    fun updateNotification(content: String) {
        if (!isNotificationEnabled || !hasNotificationPermission()) {
            return
        }
        
        try {
            val notification = createForegroundNotification(content)
            if (notification != null) {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
    
    /**
     * 获取通知ID
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
    
    /**
     * 检查是否启用通知
     */
    fun isNotificationEnabled(): Boolean = isNotificationEnabled
    
    /**
     * 重置重试计数（用于重新启用通知）
     */
    fun resetRetryCount() {
        retryCount = 0
        isNotificationEnabled = true
    }
}
