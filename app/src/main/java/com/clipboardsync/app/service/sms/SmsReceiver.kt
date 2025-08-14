package com.clipboardsync.app.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.clipboardsync.app.ClipboardSyncApplication
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.network.http.ClipboardHttpService
import com.clipboardsync.app.network.websocket.WebSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    private val tag = "SmsReceiver"
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // 验证码正则表达式模式
        private val CODE_PATTERNS = listOf(
            Pattern.compile("\\b\\d{4,8}\\b"), // 4-8位数字
            Pattern.compile("(?i)code[：:]*\\s*(\\d{4,8})"), // code: 123456
            Pattern.compile("(?i)验证码[：:]*\\s*(\\d{4,8})"), // 验证码: 123456
            Pattern.compile("(?i)动态码[：:]*\\s*(\\d{4,8})"), // 动态码: 123456
            Pattern.compile("(?i)校验码[：:]*\\s*(\\d{4,8})"), // 校验码: 123456
            Pattern.compile("(?i)验证码是[：:]*\\s*(\\d{4,8})") // 验证码是: 123456
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(tag, "收到短信广播")

        receiverScope.launch {
            try {
                // 使用Application的静态方法获取配置
                val config = ClipboardSyncApplication.getCurrentConfig()
                
                if (!config.autoUploadSms) {
                    Log.d(tag, "短信自动上传功能已关闭")
                    return@launch
                }

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) {
                    Log.d(tag, "未获取到短信内容")
                    return@launch
                }

                for (message in messages) {
                    processSmsMessage(message, config)
                }

            } catch (e: Exception) {
                Log.e(tag, "处理短信时发生错误", e)
            }
        }
    }

    private suspend fun processSmsMessage(message: SmsMessage, config: AppConfig) {
        try {
            val sender = message.originatingAddress ?: "未知发送方"
            val messageBody = message.messageBody ?: ""
            val timestamp = message.timestampMillis

            Log.d(tag, "处理短信 - 发送方: $sender, 内容: ${messageBody.take(50)}...")

            // 检查发送方过滤（如果启用了过滤且发送方不在信任列表中）
            if (config.smsFilterSender && !isTrustedSender(sender, config.trustedSenders)) {
                Log.d(tag, "发送方 $sender 不在信任列表中，跳过处理")
                return
            }

            // 如果未启用发送方过滤，则信任所有发送方
            if (!config.smsFilterSender) {
                Log.d(tag, "发送方过滤已关闭，信任所有发送方: $sender")
            }

            // 检查是否包含验证码关键词
            if (!containsVerificationKeywords(messageBody, config.smsKeywords)) {
                Log.d(tag, "短信内容不包含验证码关键词，跳过处理")
                return
            }

            // 提取验证码
            val verificationCode = extractVerificationCode(messageBody)
            if (verificationCode.isNullOrEmpty()) {
                Log.d(tag, "未能提取到验证码，跳过上传")
                return
            }

            Log.d(tag, "提取到验证码: $verificationCode，准备上传")

            // 创建剪切板项目（只上传验证码数字）
            val clipboardItem = createSmsClipboardItem(verificationCode, config)
            
            // 上传到服务器
            uploadSmsContent(clipboardItem, config)

            Log.i(tag, "成功提取并上传验证码: $verificationCode - 发送方: $sender")

        } catch (e: Exception) {
            Log.e(tag, "处理单条短信时发生错误", e)
        }
    }

    private fun isTrustedSender(sender: String, trustedSenders: List<String>): Boolean {
        return trustedSenders.any { trusted ->
            sender.contains(trusted, ignoreCase = true) || trusted.contains(sender, ignoreCase = true)
        }
    }

    private fun containsVerificationKeywords(content: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            content.contains(keyword, ignoreCase = true)
        }
    }

    private fun extractVerificationCode(content: String): String? {
        for (pattern in CODE_PATTERNS) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                val code = if (matcher.groupCount() > 0) {
                    matcher.group(1) // 获取捕获组中的验证码
                } else {
                    matcher.group() // 获取整个匹配
                }
                
                // 验证提取的验证码是否合理（4-8位数字）
                if (code != null && code.matches(Regex("\\d{4,8}"))) {
                    Log.d(tag, "提取到验证码: $code")
                    return code
                }
            }
        }
        return null
    }

    private fun createSmsClipboardItem(verificationCode: String, config: AppConfig): ClipboardItem {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        return ClipboardItem(
            id = UUID.randomUUID().toString(),
            type = ClipboardType.text,
            content = verificationCode, // 只包含验证码数字
            deviceId = config.deviceId,
            mimeType = "text/plain",
            createdAt = now,
            updatedAt = now
        )
    }

    private suspend fun uploadSmsContent(clipboardItem: ClipboardItem, config: AppConfig) {
        try {
            // 通过HTTP上传（简化版本，不使用WebSocket）
            uploadToHttpServer(clipboardItem, config)
            Log.i(tag, "验证码上传完成: ${clipboardItem.content}")

        } catch (e: Exception) {
            Log.e(tag, "上传验证码时发生错误", e)
        }
    }

    private suspend fun uploadToHttpServer(clipboardItem: ClipboardItem, config: AppConfig) {
        try {
            val httpClient = ClipboardSyncApplication.httpClient
            val url = "${config.httpUrl}/api/clipboard"

            // 构建正确的JSON请求体（匹配服务器期望的格式）
            val jsonBody = buildString {
                append("{")
                append("\"type\":\"text\",")  // 固定为text类型
                append("\"content\":\"${clipboardItem.content.replace("\"", "\\\"").replace("\n", "\\n")}\",")
                append("\"deviceId\":\"${clipboardItem.deviceId}\"")
                append("}")
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            // 添加认证头
            if (config.authKey.isNotEmpty() && config.authValue.isNotEmpty()) {
                requestBuilder.addHeader(config.authKey, config.authValue)
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Log.i(tag, "验证码HTTP上传成功: ${clipboardItem.content}")
            } else {
                Log.w(tag, "验证码HTTP上传失败: ${response.code}")
                // 打印响应内容以便调试
                try {
                    val responseBody = response.body?.string()
                    Log.w(tag, "服务器响应: $responseBody")
                } catch (e: Exception) {
                    Log.w(tag, "无法读取响应内容: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "HTTP上传错误", e)
        }
    }
}
