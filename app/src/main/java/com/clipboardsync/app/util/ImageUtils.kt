package com.clipboardsync.app.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object ImageUtils {
    
    private const val TAG = "ImageUtils"
    
    /**
     * 将Base64字符串转换为Bitmap
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val cleanBase64 = base64String.replace("data:image/png;base64,", "")
                .replace("data:image/jpeg;base64,", "")
                .replace("data:image/jpg;base64,", "")
            
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting base64 to bitmap", e)
            null
        }
    }
    
    /**
     * 将Bitmap转换为Base64字符串
     */
    fun bitmapToBase64(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100): String? {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(format, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting bitmap to base64", e)
            null
        }
    }
    
    /**
     * 保存图片到相册
     */
    fun saveImageToGallery(context: Context, bitmap: Bitmap, fileName: String? = null): Uri? {
        val displayName = fileName ?: "clipboard_image_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGalleryQ(context, bitmap, displayName)
        } else {
            saveImageToGalleryLegacy(context, bitmap, displayName)
        }
    }
    
    /**
     * Android Q及以上版本保存图片到相册
     */
    private fun saveImageToGalleryQ(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipboardSync")
        }
        
        return try {
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                imageUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to gallery (Q+)", e)
            null
        }
    }
    
    /**
     * Android Q以下版本保存图片到相册
     */
    private fun saveImageToGalleryLegacy(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        return try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val clipboardDir = File(picturesDir, "ClipboardSync")
            if (!clipboardDir.exists()) {
                clipboardDir.mkdirs()
            }
            
            val imageFile = File(clipboardDir, displayName)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            
            // 通知媒体扫描器
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to gallery (Legacy)", e)
            null
        }
    }
    
    /**
     * 保存Base64图片到相册
     */
    fun saveBase64ImageToGallery(context: Context, base64String: String, fileName: String? = null): Uri? {
        val bitmap = base64ToBitmap(base64String) ?: return null
        return saveImageToGallery(context, bitmap, fileName)
    }
    
    /**
     * 从URI加载Bitmap
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }
    
    /**
     * 压缩Bitmap
     */
    fun compressBitmap(bitmap: Bitmap, maxWidth: Int = 1920, maxHeight: Int = 1080, @Suppress("UNUSED_PARAMETER") quality: Int = 80): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 获取图片文件大小（字节）
     */
    fun getBitmapSize(bitmap: Bitmap): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            bitmap.allocationByteCount.toLong()
        } else {
            bitmap.byteCount.toLong()
        }
    }
    
    /**
     * 检查Base64字符串是否为有效图片
     */
    fun isValidBase64Image(base64String: String): Boolean {
        return try {
            val bitmap = base64ToBitmap(base64String)
            bitmap != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取图片的MIME类型
     */
    fun getMimeTypeFromBase64(base64String: String): String {
        return when {
            base64String.startsWith("data:image/jpeg") || base64String.startsWith("data:image/jpg") -> "image/jpeg"
            base64String.startsWith("data:image/png") -> "image/png"
            base64String.startsWith("data:image/gif") -> "image/gif"
            base64String.startsWith("data:image/webp") -> "image/webp"
            else -> "image/png" // 默认为PNG
        }
    }
}
