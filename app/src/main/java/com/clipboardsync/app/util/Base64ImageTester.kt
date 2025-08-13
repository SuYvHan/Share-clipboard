package com.clipboardsync.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log

object Base64ImageTester {
    
    private const val TAG = "Base64ImageTester"
    
    /**
     * 测试Base64字符串是否为有效的图片
     */
    fun testBase64Image(base64String: String): TestResult {
        val result = TestResult()
        
        try {
            result.originalLength = base64String.length
            result.hasDataUrlPrefix = base64String.startsWith("data:")
            
            // 清理Base64字符串
            val cleanBase64 = if (base64String.startsWith("data:")) {
                val commaIndex = base64String.indexOf(",")
                if (commaIndex != -1) {
                    base64String.substring(commaIndex + 1)
                } else {
                    base64String
                }
            } else {
                base64String
            }.replace("\\s".toRegex(), "")
            
            result.cleanedLength = cleanBase64.length
            result.isValidBase64Length = cleanBase64.length % 4 == 0

            // 检查Base64字符是否有效
            if (!cleanBase64.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) {
                result.error = "Invalid Base64 characters found"
                return result
            }

            // 尝试解码
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            result.decodedBytesLength = decodedBytes.size
            result.decodeSuccess = true
            
            // 检查图片头部
            result.imageFormat = detectImageFormat(decodedBytes)
            
            // 尝试创建Bitmap
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                result.bitmapCreated = true
                result.bitmapWidth = bitmap.width
                result.bitmapHeight = bitmap.height
                bitmap.recycle()
            }
            
        } catch (e: Exception) {
            result.error = e.message
            Log.e(TAG, "Error testing base64 image", e)
        }
        
        return result
    }
    
    private fun detectImageFormat(bytes: ByteArray): String? {
        if (bytes.size < 8) return null
        
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
            
            else -> "Unknown"
        }
    }
    
    data class TestResult(
        var originalLength: Int = 0,
        var hasDataUrlPrefix: Boolean = false,
        var cleanedLength: Int = 0,
        var isValidBase64Length: Boolean = false,
        var decodeSuccess: Boolean = false,
        var decodedBytesLength: Int = 0,
        var imageFormat: String? = null,
        var bitmapCreated: Boolean = false,
        var bitmapWidth: Int = 0,
        var bitmapHeight: Int = 0,
        var error: String? = null
    ) {
        fun isValid(): Boolean {
            return decodeSuccess && bitmapCreated && error == null
        }
        
        fun getReport(): String {
            return buildString {
                appendLine("=== Base64 Image Test Report ===")
                appendLine("Original length: $originalLength")
                appendLine("Has data URL prefix: $hasDataUrlPrefix")
                appendLine("Cleaned length: $cleanedLength")
                appendLine("Valid Base64 length: $isValidBase64Length")
                appendLine("Decode success: $decodeSuccess")
                appendLine("Decoded bytes length: $decodedBytesLength")
                appendLine("Image format: $imageFormat")
                appendLine("Bitmap created: $bitmapCreated")
                if (bitmapCreated) {
                    appendLine("Bitmap size: ${bitmapWidth}x${bitmapHeight}")
                }
                error?.let { appendLine("Error: $it") }
                appendLine("Overall valid: ${isValid()}")
            }
        }
    }
}
