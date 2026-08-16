package com.paddle.ocr.demo.plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * 健壮的本地图片加载器 (支持绝对路径、Uri、防 OOM 采样、EXIF 自动旋转)
 */
object ImageFileLoader {

    private const val TAG = "OcrPlugin"
    private const val SUB_TAG = "ImageLoader"

    /**
     * 安全加载 Bitmap
     * @param context Android 上下文
     * @param pathOrUri 文件绝对路径或 content:// / file:// 协议字符串
     * @param maxDimension 最大长宽限制 (默认 2560px，超大图自动下采样以防 OOM)
     */
    fun loadBitmap(context: Context, pathOrUri: String, maxDimension: Int = 2560): Bitmap? {
        val trimmed = pathOrUri.trim()
        if (trimmed.isEmpty()) {
            Log.e(TAG, "[$SUB_TAG] Image path is empty!")
            return null
        }

        Log.d(TAG, "[$SUB_TAG] Loading image from: $trimmed")

        return try {
            // 1. 测量图片原始尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInputStream(context, trimmed)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "[$SUB_TAG] Failed to decode image bounds (width=${options.outWidth}, height=${options.outHeight})")
                return null
            }

            // 2. 计算采样率 (inSampleSize)
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            Log.d(TAG, "[$SUB_TAG] Original size: ${options.outWidth}x${options.outHeight}, inSampleSize=${options.inSampleSize}")

            // 3. 解码 Bitmap
            var bitmap: Bitmap? = openInputStream(context, trimmed)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmap == null) {
                Log.e(TAG, "[$SUB_TAG] Failed to decode bitmap from stream")
                return null
            }

            // 4. 读取 EXIF 并自动旋转
            val orientation = getExifOrientation(context, trimmed)
            if (orientation != 0) {
                Log.d(TAG, "[$SUB_TAG] Applying EXIF rotation: ${orientation}°")
                bitmap = rotateBitmap(bitmap, orientation)
            }

            Log.d(TAG, "[$SUB_TAG] Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "[$SUB_TAG] Exception while loading image from $trimmed", e)
            null
        }
    }

    private fun openInputStream(context: Context, pathOrUri: String): InputStream? {
        val trimmed = pathOrUri.trim()
        return try {
            if (trimmed.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(trimmed))
            } else {
                val filePath = if (trimmed.startsWith("file://")) trimmed.substring(7) else trimmed
                val file = File(filePath)
                if (file.exists() && file.canRead()) {
                    java.io.FileInputStream(file)
                } else if (trimmed.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(trimmed))
                } else {
                    // Try open as file fallback
                    java.io.FileInputStream(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$SUB_TAG] openInputStream failed for $trimmed: ${e.message}")
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
            openInputStream(context, pathOrUri)?.use { stream ->
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
