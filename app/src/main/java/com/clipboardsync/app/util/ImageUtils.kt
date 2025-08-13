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
            if (base64String.isBlank()) {
                Log.w(TAG, "Base64 string is empty or blank")
                return null
            }

            Log.d(TAG, "Original base64 length: ${base64String.length}")
            Log.d(TAG, "Base64 prefix: ${base64String.take(50)}...")

            // 处理data URL格式的Base64字符串
            val cleanBase64 = if (base64String.startsWith("data:")) {
                // 移除data URL前缀，例如 "data:image/jpeg;base64,"
                val commaIndex = base64String.indexOf(",")
                if (commaIndex != -1) {
                    val result = base64String.substring(commaIndex + 1)
                    Log.d(TAG, "Removed data URL prefix, new length: ${result.length}")
                    result
                } else {
                    Log.w(TAG, "Data URL format but no comma found")
                    base64String
                }
            } else {
                base64String
            }

            // 清理Base64字符串，移除可能的空白字符
            val finalBase64 = cleanBase64.replace("\\s".toRegex(), "")
            Log.d(TAG, "Final base64 length after cleanup: ${finalBase64.length}")

            // 验证Base64格式并尝试修复
            val validBase64 = if (finalBase64.length % 4 != 0) {
                Log.w(TAG, "Invalid base64 length: ${finalBase64.length}, not divisible by 4")
                // 尝试添加填充
                val padding = 4 - (finalBase64.length % 4)
                val paddedBase64 = finalBase64 + "=".repeat(padding)
                Log.d(TAG, "Added padding, new length: ${paddedBase64.length}")
                paddedBase64
            } else {
                finalBase64
            }

            decodeBase64ToBitmap(validBase64)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting base64 to bitmap: ${e.message}", e)
            null
        }
    }

    private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            Log.d(TAG, "Attempting to decode base64 string of length: ${base64String.length}")

            // 尝试不同的Base64解码标志
            val decodedBytes = try {
                Base64.decode(base64String, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.w(TAG, "NO_WRAP decode failed, trying DEFAULT: ${e.message}")
                try {
                    Base64.decode(base64String, Base64.DEFAULT)
                } catch (e2: Exception) {
                    Log.w(TAG, "DEFAULT decode failed, trying URL_SAFE: ${e2.message}")
                    Base64.decode(base64String, Base64.URL_SAFE)
                }
            }

            Log.d(TAG, "Decoded bytes length: ${decodedBytes.size}")

            if (decodedBytes.isEmpty()) {
                Log.w(TAG, "Decoded bytes array is empty")
                return null
            }

            // 检查图片文件头
            val imageFormat = detectImageFormat(decodedBytes)
            Log.d(TAG, "Detected image format: $imageFormat")

            // 尝试解码为Bitmap
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            if (bitmap != null) {
                Log.d(TAG, "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
                return bitmap
            } else {
                Log.w(TAG, "BitmapFactory failed to decode bytes, trying with options")
                // 尝试使用不同的解码选项
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val retryBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                if (retryBitmap != null) {
                    Log.d(TAG, "Retry decode successful: ${retryBitmap.width}x${retryBitmap.height}")
                    return retryBitmap
                }

                Log.e(TAG, "All bitmap decode attempts failed")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in decodeBase64ToBitmap: ${e.message}", e)
            null
        }
    }

    fun detectImageFormat(bytes: ByteArray): String {
        if (bytes.size < 8) return "Unknown (too short)"

        return when {
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "PNG"

            // JPEG: FF D8 FF
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "JPEG"

            // GIF: 47 49 46 38
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() -> "GIF"

            // WebP: 52 49 46 46 ... 57 45 42 50
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes.size >= 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "WebP"

            else -> "Unknown (${bytes.take(8).joinToString(" ") { "%02X".format(it) }})"
        }
    }

    /**
     * 将字节数组转换为Bitmap（用于直接的图片数据）
     */
    fun bytesToBitmap(bytes: ByteArray): Bitmap? {
        return try {
            Log.d(TAG, "Converting bytes to bitmap, length: ${bytes.size}")

            if (bytes.isEmpty()) {
                Log.w(TAG, "Bytes array is empty")
                return null
            }

            // 检查图片文件头
            val imageFormat = detectImageFormat(bytes)
            Log.d(TAG, "Detected image format: $imageFormat")

            // 显示前几个字节用于调试
            val hexBytes = bytes.take(16).joinToString(" ") { "%02X".format(it) }
            Log.d(TAG, "First 16 bytes: $hexBytes")

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap != null) {
                Log.d(TAG, "Successfully converted bytes to bitmap: ${bitmap.width}x${bitmap.height}")
            } else {
                Log.w(TAG, "Failed to convert bytes to bitmap, trying with options...")
                // 尝试使用不同的解码选项
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val retryBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (retryBitmap != null) {
                    Log.d(TAG, "Retry bytes decode successful: ${retryBitmap.width}x${retryBitmap.height}")
                } else {
                    Log.e(TAG, "All bitmap decode attempts failed for format: $imageFormat")
                }
                return retryBitmap
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting bytes to bitmap: ${e.message}", e)
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
