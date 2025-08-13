package com.clipboardsync.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.InputStream

object FileUploadUtils {
    
    private const val TAG = "FileUploadUtils"
    
    /**
     * 将文件转换为Base64编码
     */
    fun fileToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting file to base64", e)
            null
        }
    }
    
    /**
     * 将图片转换为Base64编码（包含data URL前缀）
     */
    fun imageToBase64WithDataUrl(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                val mimeType = getMimeType(context, uri) ?: "image/jpeg"
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mimeType;base64,$base64"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to base64", e)
            null
        }
    }
    
    /**
     * 获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        it.getString(nameIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file name", e)
            null
        }
    }
    
    /**
     * 获取文件大小
     */
    fun getFileSize(context: Context, uri: Uri): Long? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        it.getLong(sizeIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            null
        }
    }
    
    /**
     * 获取MIME类型
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                mimeType
            } else {
                // 如果无法从ContentResolver获取，尝试从文件扩展名获取
                val fileName = getFileName(context, uri)
                fileName?.let {
                    val extension = it.substringAfterLast('.', "")
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting mime type", e)
            null
        }
    }
    
    /**
     * 检查是否为图片文件
     */
    fun isImageFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType?.startsWith("image/") == true
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
