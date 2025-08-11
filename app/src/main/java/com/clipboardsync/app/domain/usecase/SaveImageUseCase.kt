package com.clipboardsync.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.clipboardsync.app.util.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * 保存Base64图片到相册
     */
    suspend fun saveBase64Image(base64String: String, fileName: String? = null): Result<Uri> {
        return try {
            val uri = ImageUtils.saveBase64ImageToGallery(context, base64String, fileName)
            if (uri != null) {
                Result.success(uri)
            } else {
                Result.failure(Exception("Failed to save image to gallery"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 检查Base64字符串是否为有效图片
     */
    fun isValidImage(base64String: String): Boolean {
        return ImageUtils.isValidBase64Image(base64String)
    }
    
    /**
     * 获取图片的MIME类型
     */
    fun getImageMimeType(base64String: String): String {
        return ImageUtils.getMimeTypeFromBase64(base64String)
    }
}
