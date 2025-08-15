package com.clipboardsync.app.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object ImageSaveUtils {

    private const val TAG = "ImageSaveUtils"

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
     * 方案一：直接从Bitmap保存到相册（推荐）
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "clipboard_image_${System.currentTimeMillis()}.jpg"
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val isHarmony = isHarmonyOS()
                Log.d(TAG, "开始保存图片到相册，系统类型: ${if (isHarmony) "鸿蒙" else "Android"}，API版本: ${Build.VERSION.SDK_INT}")

                // 鸿蒙4.2系统特殊处理
                if (isHarmony) {
                    return@withContext saveToHarmonyGallery(context, bitmap, fileName)
                }

                val contentResolver = context.contentResolver
                val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 使用分区存储
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipboardSync")
                        put(MediaStore.Images.Media.IS_PENDING, 1)

                        // Android 12+ 特殊处理
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // 确保文件可以被其他应用访问
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    } else {
                        // Android 9及以下使用传统路径
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val clipboardDir = File(picturesDir, "ClipboardSync")
                        if (!clipboardDir.exists()) {
                            clipboardDir.mkdirs()
                        }
                        put(MediaStore.Images.Media.DATA, File(clipboardDir, fileName).absolutePath)
                    }
                }

                Log.d(TAG, "创建MediaStore条目...")
                val imageUri = contentResolver.insert(imageCollection, contentValues)

                if (imageUri != null) {
                    Log.d(TAG, "成功创建MediaStore条目: $imageUri")

                    contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        Log.d(TAG, "图片压缩结果: $success")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // 完成写入，移除pending状态
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        val updateResult = contentResolver.update(imageUri, contentValues, null, null)
                        Log.d(TAG, "更新pending状态结果: $updateResult")
                    }

                    Log.d(TAG, "图片保存成功: $imageUri")
                    Result.success("图片已保存到相册")
                } else {
                    Log.e(TAG, "无法创建MediaStore条目")
                    Result.failure(Exception("无法创建图片文件"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存图片到相册失败", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 方案二：从URL下载并保存到相册（备用方案）
     */
    suspend fun downloadAndSaveToGallery(
        context: Context,
        imageUrl: String,
        authKey: String,
        authValue: String,
        httpClient: OkHttpClient,
        fileName: String = "clipboard_image_${System.currentTimeMillis()}.jpg"
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                Log.d(TAG, "Downloading image from URL: $imageUrl")
                
                // 1. 下载图片到临时文件
                val requestBuilder = Request.Builder()
                    .url(imageUrl)
                    .addHeader("accept", "application/octet-stream")

                // 只有当authKey不为空时才添加认证头
                if (authKey.isNotEmpty()) {
                    requestBuilder.addHeader(authKey, authValue)
                }

                val request = requestBuilder.build()
                
                val response = httpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载失败: HTTP ${response.code}"))
                }
                
                val imageBytes = response.body?.bytes()
                    ?: return@withContext Result.failure(Exception("响应体为空"))
                
                Log.d(TAG, "Downloaded ${imageBytes.size} bytes")
                
                // 2. 创建临时文件
                tempFile = File(context.cacheDir, "temp_$fileName")
                FileOutputStream(tempFile).use { fos ->
                    fos.write(imageBytes)
                }
                
                // 3. 保存到相册
                val result = saveFileToGallery(context, tempFile, fileName)
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading and saving image", e)
                Result.failure(e)
            } finally {
                // 4. 清理临时文件
                tempFile?.let { file ->
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(TAG, "Temp file deleted: $deleted")
                    }
                }
            }
        }
    }
    
    /**
     * 将文件保存到相册
     */
    private fun saveFileToGallery(
        context: Context,
        file: File,
        fileName: String
    ): Result<String> {
        return try {
            val contentResolver = context.contentResolver
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipboardSync")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val imageUri = contentResolver.insert(imageCollection, contentValues)
            
            if (imageUri != null) {
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(imageUri, contentValues, null, null)
                }
                
                Log.d(TAG, "File saved to gallery successfully: $imageUri")
                Result.success("图片已保存到相册")
            } else {
                Result.failure(Exception("无法创建图片文件"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file to gallery", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从AsyncImage的URL直接保存（尝试从Coil缓存获取）
     */
    suspend fun saveFromUrl(
        context: Context,
        imageUrl: String,
        authKey: String,
        authValue: String,
        httpClient: OkHttpClient
    ): Result<String> {
        return try {
            // 首先尝试从Coil的缓存中获取Bitmap
            // 如果缓存中没有，则使用下载方案
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "clipboard_sync_$timestamp.jpg"
            
            downloadAndSaveToGallery(context, imageUrl, authKey, authValue, httpClient, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving from URL", e)
            Result.failure(e)
        }
    }

    /**
     * 鸿蒙4.2系统专用的图片保存方法
     */
    private suspend fun saveToHarmonyGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Result<String> {
        return try {
            Log.d(TAG, "使用鸿蒙4.2专用方法保存图片")

            val contentResolver = context.contentResolver

            // 鸿蒙4.2使用更兼容的MediaStore方式
            val imageCollection = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    try {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } catch (e: Exception) {
                        Log.w(TAG, "鸿蒙4.2获取主存储失败，使用外部存储: ${e.message}")
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                }
                else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

                // 鸿蒙4.2特殊处理
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipboardSync")
                    put(MediaStore.Images.Media.IS_PENDING, 1)

                    // 鸿蒙4.2额外属性
                    try {
                        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    } catch (e: Exception) {
                        Log.w(TAG, "鸿蒙4.2设置时间属性失败: ${e.message}")
                    }
                } else {
                    // 鸿蒙3.x兼容模式
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val clipboardDir = File(picturesDir, "ClipboardSync")
                    if (!clipboardDir.exists()) {
                        clipboardDir.mkdirs()
                    }
                    put(MediaStore.Images.Media.DATA, File(clipboardDir, fileName).absolutePath)
                }
            }

            Log.d(TAG, "鸿蒙4.2创建MediaStore条目...")
            val imageUri = contentResolver.insert(imageCollection, contentValues)

            if (imageUri != null) {
                Log.d(TAG, "鸿蒙4.2成功创建MediaStore条目: $imageUri")

                // 使用更安全的输出流写入
                var outputStream: OutputStream? = null
                try {
                    outputStream = contentResolver.openOutputStream(imageUri)
                    if (outputStream != null) {
                        // 鸿蒙4.2使用较高质量保存
                        val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        Log.d(TAG, "鸿蒙4.2图片压缩结果: $success")

                        if (!success) {
                            throw Exception("鸿蒙4.2图片压缩失败")
                        }
                    } else {
                        throw Exception("鸿蒙4.2无法打开输出流")
                    }
                } finally {
                    outputStream?.close()
                }

                // 完成写入，移除pending状态
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        val updateResult = contentResolver.update(imageUri, contentValues, null, null)
                        Log.d(TAG, "鸿蒙4.2更新pending状态结果: $updateResult")
                    } catch (e: Exception) {
                        Log.w(TAG, "鸿蒙4.2更新pending状态失败: ${e.message}")
                        // 不影响主要功能，继续执行
                    }
                }

                Log.d(TAG, "鸿蒙4.2图片保存成功: $imageUri")
                Result.success("图片已保存到相册（鸿蒙4.2兼容模式）")
            } else {
                Log.e(TAG, "鸿蒙4.2无法创建MediaStore条目")
                Result.failure(Exception("鸿蒙4.2无法创建图片文件"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "鸿蒙4.2保存图片失败: ${e.message}", e)

            // 降级到传统文件保存方式
            try {
                Log.d(TAG, "鸿蒙4.2降级到传统文件保存")
                saveToTraditionalStorage(context, bitmap, fileName)
            } catch (e2: Exception) {
                Log.e(TAG, "鸿蒙4.2传统保存也失败: ${e2.message}", e2)
                Result.failure(Exception("鸿蒙4.2图片保存失败: ${e.message}"))
            }
        }
    }

    /**
     * 传统文件保存方式（鸿蒙兜底方案）
     */
    private fun saveToTraditionalStorage(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Result<String> {
        return try {
            Log.d(TAG, "使用传统文件保存方式")

            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val clipboardDir = File(picturesDir, "ClipboardSync")

            if (!clipboardDir.exists()) {
                clipboardDir.mkdirs()
            }

            val imageFile = File(clipboardDir, fileName)
            val outputStream = FileOutputStream(imageFile)

            val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()

            if (success) {
                // 通知媒体扫描器
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                Log.d(TAG, "传统方式保存成功: ${imageFile.absolutePath}")
                Result.success("图片已保存到相册（传统模式）")
            } else {
                Result.failure(Exception("传统方式图片压缩失败"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "传统方式保存失败: ${e.message}", e)
            Result.failure(e)
        }
    }
}
