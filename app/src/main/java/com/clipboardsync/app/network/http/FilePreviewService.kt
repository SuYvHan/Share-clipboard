package com.clipboardsync.app.network.http

import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilePreviewService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    
    private val tag = "FilePreviewService"
    
    /**
     * 获取文件预览数据
     * @param itemId 剪切板项的ID（响应中的id字段）
     * @param fileName 文件名（响应中的fileName字段）
     */
    suspend fun getFilePreview(config: AppConfig, itemId: String, fileName: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                // URL编码文件名以处理中文和特殊字符
                val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                val url = "${config.httpUrl}/api/files/preview?id=$itemId&name=$encodedFileName"
                Log.d(tag, "Fetching file preview from: $url")
                Log.d(tag, "Using itemId: $itemId, fileName: $fileName, encoded: $encodedFileName")
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("accept", "application/octet-stream")

                // 只有当authKey不为空时才添加认证头
                if (config.authKey.isNotEmpty()) {
                    requestBuilder.addHeader(config.authKey, config.authValue)
                }

                val request = requestBuilder.get().build()

                Log.d(tag, "Request headers: ${request.headers}")
                Log.d(tag, "Auth header: ${config.authKey} = ${config.authValue}")

                val response: Response = httpClient.newCall(request).execute()
                Log.d(tag, "Response code: ${response.code}, message: ${response.message}")
                Log.d(tag, "Response headers: ${response.headers}")
                
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        Log.d(tag, "Successfully fetched file preview, size: ${bytes.size} bytes")
                        Result.success(bytes)
                    } else {
                        val errorMsg = "响应体为空"
                        Log.w(tag, errorMsg)
                        Result.failure(Exception(errorMsg))
                    }
                } else {
                    val responseBody = try {
                        response.body?.string() ?: "无响应内容"
                    } catch (e: Exception) {
                        "读取响应内容失败: ${e.message}"
                    }

                    val errorMsg = buildString {
                        append("HTTP请求失败\n")
                        append("状态码: ${response.code}\n")
                        append("状态消息: ${response.message}\n")
                        append("请求URL: $url\n")
                        append("响应头: ${response.headers}\n")
                        append("响应内容: $responseBody")
                    }

                    Log.e(tag, "File preview request failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                val detailedError = buildString {
                    append("网络请求异常\n")
                    append("异常类型: ${e.javaClass.simpleName}\n")
                    append("异常消息: ${e.message}\n")
                    append("请求URL: ${config.httpUrl}/api/files/preview?id=$itemId&name=${URLEncoder.encode(fileName, "UTF-8")}\n")
                    append("服务器: ${config.serverHost}:${config.httpPort}\n")
                    append("协议: ${if (config.useSecureConnection) "HTTPS" else "HTTP"}\n")
                    append("认证: ${config.authKey}=${config.authValue}\n")
                    when (e) {
                        is java.net.ConnectException -> append("连接错误: 无法连接到服务器")
                        is java.net.SocketTimeoutException -> append("超时错误: 请求超时")
                        is java.net.UnknownHostException -> append("域名错误: 无法解析主机名")
                        is javax.net.ssl.SSLException -> append("SSL错误: 安全连接失败")
                        else -> append("其他网络错误")
                    }
                }
                Log.e(tag, "Error fetching file preview: $detailedError", e)
                Result.failure(Exception(detailedError, e))
            }
        }
    }
    
    /**
     * 获取图片预览数据
     * @param itemId 剪切板项的ID（响应中的id字段）
     * @param fileName 文件名（响应中的fileName字段）
     */
    suspend fun getImagePreview(config: AppConfig, itemId: String, fileName: String): Result<ByteArray> {
        return getFilePreview(config, itemId, fileName)
    }
}
