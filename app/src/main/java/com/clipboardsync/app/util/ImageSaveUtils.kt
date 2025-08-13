package com.clipboardsync.app.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object ImageSaveUtils {
    
    private const val TAG = "ImageSaveUtils"
    
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
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(imageUri, contentValues, null, null)
                    }
                    
                    Log.d(TAG, "Image saved to gallery successfully: $imageUri")
                    Result.success("图片已保存到相册")
                } else {
                    Result.failure(Exception("无法创建图片文件"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bitmap to gallery", e)
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
                val request = Request.Builder()
                    .url(imageUrl)
                    .addHeader("accept", "application/octet-stream")
                    .addHeader(authKey, authValue)
                    .build()
                
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
}
