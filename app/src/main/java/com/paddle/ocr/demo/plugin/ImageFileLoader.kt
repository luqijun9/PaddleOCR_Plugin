package com.paddle.ocr.demo.plugin

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * 图片加载结果封装
 */
sealed class ImageLoadResult {
    data class Success(val bitmap: Bitmap) : ImageLoadResult()
    data class Failure(val errorMessage: String, val cause: Throwable? = null) : ImageLoadResult()
}

/**
 * 健壮的本地图片加载器 (支持绝对路径、Uri、防 OOM 采样、EXIF 自动旋转，输出具体系统级错误)
 */
object ImageFileLoader {

    private const val TAG = "OcrPlugin"
    private const val SUB_TAG = "ImageLoader"

    /**
     * 安全加载 Bitmap 并返回详细结果
     */
    fun loadBitmap(context: Context, pathOrUri: String, maxDimension: Int = 2560): ImageLoadResult {
        val trimmed = pathOrUri.trim()
        if (trimmed.isEmpty()) {
            Log.e(TAG, "[$SUB_TAG] Image path is empty!")
            return ImageLoadResult.Failure("图片路径为空")
        }

        Log.d(TAG, "[$SUB_TAG] Loading image from: $trimmed")

        return try {
            // 1. 尝试打开数据流并测量图片原始尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            var streamOpenError: String? = null
            val boundsStream = openInputStreamWithDetail(context, trimmed, onOpenError = { streamOpenError = it })
            if (boundsStream == null) {
                val detail = streamOpenError ?: "文件不存在、无法访问或未授予存储权限"
                return ImageLoadResult.Failure("无法打开图片数据流 [$detail]: $trimmed")
            }

            boundsStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "[$SUB_TAG] Failed to decode image bounds (width=${options.outWidth}, height=${options.outHeight})")
                return ImageLoadResult.Failure("无法识别图片格式或文件已损坏 (解析尺寸: ${options.outWidth}x${options.outHeight})")
            }

            // 2. 计算采样率 (inSampleSize)
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            Log.d(TAG, "[$SUB_TAG] Original size: ${options.outWidth}x${options.outHeight}, inSampleSize=${options.inSampleSize}")

            // 3. 实际解码 Bitmap
            val decodeStream = openInputStreamWithDetail(context, trimmed, onOpenError = {})
            if (decodeStream == null) {
                return ImageLoadResult.Failure("第二次打开图片流失败: $trimmed")
            }

            var bitmap: Bitmap? = decodeStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmap == null) {
                Log.e(TAG, "[$SUB_TAG] Failed to decode bitmap from stream")
                return ImageLoadResult.Failure("BitmapFactory 解码失败 (可能内存不足或文件数据不完整)")
            }

            // 4. 读取 EXIF 并自动旋转
            val orientation = getExifOrientation(context, trimmed)
            if (orientation != 0) {
                Log.d(TAG, "[$SUB_TAG] Applying EXIF rotation: ${orientation}°")
                bitmap = rotateBitmap(bitmap, orientation)
            }

            Log.d(TAG, "[$SUB_TAG] Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}")
            ImageLoadResult.Success(bitmap)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "[$SUB_TAG] OutOfMemoryError while decoding image from $trimmed", oom)
            ImageLoadResult.Failure("内存不足，图片过大导致解码失败 (OutOfMemoryError)")
        } catch (e: Throwable) {
            Log.e(TAG, "[$SUB_TAG] Exception while loading image from $trimmed", e)
            ImageLoadResult.Failure("读取图片发生异常 [${e.javaClass.simpleName}]: ${e.message ?: "未知错误"}", e)
        }
    }

    private fun openInputStreamWithDetail(
        context: Context,
        pathOrUri: String,
        onOpenError: (String) -> Unit
    ): InputStream? {
        val trimmed = pathOrUri.trim()
        val errorLogs = mutableListOf<String>()

        // 1. 如果是 content:// 协议
        if (trimmed.startsWith("content://")) {
            // A. 直接 ContentResolver 打开
            try {
                val directStream = context.contentResolver.openInputStream(Uri.parse(trimmed))
                if (directStream != null) return directStream
            } catch (e: Exception) {
                errorLogs.add("ContentResolver: ${e.javaClass.simpleName}(${e.message})")
            }

            // B. 若为 MediaDocumentsProvider，提取纯数字 ID 并走 MediaStore
            try {
                val idMatch = Regex("""image(?::|%3A)(\d+)""").find(trimmed)
                if (idMatch != null) {
                    val id = idMatch.groupValues[1].toLong()
                    val mediaStoreUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val mediaStream = context.contentResolver.openInputStream(mediaStoreUri)
                    if (mediaStream != null) return mediaStream
                }
            } catch (e: Exception) {
                errorLogs.add("MediaStore: ${e.javaClass.simpleName}(${e.message})")
            }

            // C. 尝试重新编码冒号
            try {
                val encodedUriStr = trimmed.replace("image:", "image%3A")
                if (encodedUriStr != trimmed) {
                    val reencodedStream = context.contentResolver.openInputStream(Uri.parse(encodedUriStr))
                    if (reencodedStream != null) return reencodedStream
                }
            } catch (e: Exception) {
                errorLogs.add("ReencodedUri: ${e.javaClass.simpleName}(${e.message})")
            }

            // D. 尝试从 UriPathUtils 解析物理路径
            try {
                val realPath = UriPathUtils.getRealPathFromUri(context, Uri.parse(trimmed))
                if (realPath.isNotEmpty() && !realPath.startsWith("content://")) {
                    val f = File(realPath)
                    if (f.exists() && f.canRead()) {
                        return java.io.FileInputStream(f)
                    } else {
                        errorLogs.add("ResolvedPath: 文件不可读或不存在($realPath)")
                    }
                }
            } catch (e: Exception) {
                errorLogs.add("ResolvedPath: ${e.javaClass.simpleName}(${e.message})")
            }

            onOpenError(errorLogs.joinToString("; "))
            return null
        }

        // 2. 如果是物理文件路径 (/storage/... 或 file://)
        val filePath = if (trimmed.startsWith("file://")) trimmed.substring(7) else trimmed
        val file = File(filePath)
        return try {
            if (!file.exists()) {
                onOpenError("文件不存在: $filePath (请检查路径拼写或宏变量传参)")
                null
            } else if (!file.canRead()) {
                onOpenError("文件无读取权限: $filePath (请授予本应用所有文件访问权限)")
                null
            } else {
                java.io.FileInputStream(file)
            }
        } catch (e: Exception) {
            onOpenError("FileInputStream: ${e.javaClass.simpleName}(${e.message})")
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDim || h > maxDim) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun getExifOrientation(context: Context, pathOrUri: String): Int {
        return try {
            openInputStreamWithDetail(context, pathOrUri, {})?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "[$SUB_TAG] Failed to read EXIF orientation: ${e.message}")
            0
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
