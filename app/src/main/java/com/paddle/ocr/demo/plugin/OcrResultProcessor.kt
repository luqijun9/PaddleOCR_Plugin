package com.paddle.ocr.demo.plugin

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import com.paddle.ocr.demo.OCRApplication
import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一的 OCR 结果强类型数据模型
 */
data class OcrProcessResult(
    val success: Boolean,
    val fullText: String = "",
    val json: String = "",
    val hasTargetText: Boolean = false,
    val matchFound: Boolean = false,
    val matchCenterX: Int? = null,
    val matchCenterY: Int? = null,
    val errorMessage: String? = null
) {
    /**
     * 将处理结果打包为 Tasker/MacroDroid 认识的标准 Bundle 变量
     * 遵循 Tasker 规范：
     * - 未配置或无值时，显式赋空字符串 "" 以清除宿主对应变量
     * - 成功时 %errmsg 返回空字符串 ""
     * - 失败时 %ocr_full_text, %ocr_json, %match_found, %match_center_x/y 均返回空字符串 ""
     */
    fun toTaskerBundle(): Bundle {
        return Bundle().apply {
            if (success) {
                putString("%ocr_full_text", fullText)
                putString("%ocr_json", json)
                putString("%errmsg", "")
                putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, "")

                if (hasTargetText) {
                    putString("%match_found", matchFound.toString())
                    if (matchFound && matchCenterX != null && matchCenterY != null) {
                        putString("%match_center_x", matchCenterX.toString())
                        putString("%match_center_y", matchCenterY.toString())
                    } else {
                        putString("%match_center_x", "")
                        putString("%match_center_y", "")
                    }
                } else {
                    putString("%match_found", "")
                    putString("%match_center_x", "")
                    putString("%match_center_y", "")
                }
            } else {
                val err = errorMessage ?: "未知错误"
                putString("%ocr_full_text", "")
                putString("%ocr_json", "")
                putString("%match_found", "")
                putString("%match_center_x", "")
                putString("%match_center_y", "")
                putString("%errmsg", err)
                putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, err)
            }
        }
    }

    companion object {
        fun createErrorBundle(errorMessage: String): Bundle {
            return OcrProcessResult(
                success = false,
                errorMessage = errorMessage
            ).toTaskerBundle()
        }
    }
}

data class CropResult(
    val croppedBitmap: Bitmap,
    val offsetX: Int,
    val offsetY: Int,
    val isCropped: Boolean
)

/**
 * 通用 OCR 结果处理器 (纯业务与算法层，供截屏、文件图片等多数据源共享)
 */
object OcrResultProcessor {

    private const val TAG = "OcrPlugin"
    private const val SUB_TAG = "Processor"

    fun cropBitmapIfNeeded(
        bitmap: Bitmap,
        restrictRegion: Boolean,
        regionLeft: String,
        regionTop: String,
        regionRight: String,
        regionBottom: String
    ): CropResult {
        if (!restrictRegion) {
            return CropResult(bitmap, 0, 0, false)
        }
        return try {
            val leftPct = regionLeft.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
            val topPct = regionTop.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
            val rightPct = regionRight.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
            val bottomPct = regionBottom.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

            val bW = bitmap.width
            val bH = bitmap.height

            val cropLeft = (leftPct * bW).toInt().coerceIn(0, bW)
            val cropTop = (topPct * bH).toInt().coerceIn(0, bH)
            val cropRight = (rightPct * bW).toInt().coerceIn(0, bW)
            val cropBottom = (bottomPct * bH).toInt().coerceIn(0, bH)

            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop

            if (cropWidth > 0 && cropHeight > 0 && (cropWidth < bW || cropHeight < bH)) {
                val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
                CropResult(cropped, cropLeft, cropTop, true)
            } else {
                CropResult(bitmap, 0, 0, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$SUB_TAG] Failed to crop bitmap: ${e.message}", e)
            CropResult(bitmap, 0, 0, false)
        }
    }

    suspend fun process(
        bitmap: Bitmap,
        targetText: String,
        isRegex: Boolean = false,
        isExactMatch: Boolean = false,
        isIgnoreCase: Boolean = true,
        restrictRegion: Boolean = false,
        regionLeft: String = "0.0",
        regionTop: String = "0.0",
        regionRight: String = "1.0",
        regionBottom: String = "1.0"
    ): OcrProcessResult {
        val ocrEngine = OCRApplication.instance.ocr
        if (ocrEngine == null) {
            Log.e(TAG, "[$SUB_TAG] OCR Engine is NULL - not initialized!")
            return OcrProcessResult(
                success = false,
                errorMessage = "OCR引擎未初始化"
            )
        }

        val cropResult = cropBitmapIfNeeded(bitmap, restrictRegion, regionLeft, regionTop, regionRight, regionBottom)
        val inferenceBitmap = cropResult.croppedBitmap
        val offsetX = cropResult.offsetX
        val offsetY = cropResult.offsetY

        return try {
            Log.d(TAG, "[$SUB_TAG] Starting OCR recognition on bitmap: ${inferenceBitmap.width}x${inferenceBitmap.height} (offset: $offsetX, $offsetY)...")
            val result = ocrEngine.recognize(inferenceBitmap)
            Log.d(TAG, "[$SUB_TAG] recognize() returned ${result.results.size} text blocks")

            val fullTextBuilder = StringBuilder()
            val jsonArray = JSONArray()
            var matchFound = false
            var matchCenterX: Int? = null
            var matchCenterY: Int? = null

            result.results.forEachIndexed { i, ocrResult ->
                fullTextBuilder.append(ocrResult.text).append("\n")

                val localStartX = ocrResult.box.points.minOf { it.x }.toInt()
                val localStartY = ocrResult.box.points.minOf { it.y }.toInt()
                val localEndX = ocrResult.box.points.maxOf { it.x }.toInt()
                val localEndY = ocrResult.box.points.maxOf { it.y }.toInt()

                val startX = localStartX + offsetX
                val startY = localStartY + offsetY
                val endX = localEndX + offsetX
                val endY = localEndY + offsetY
                val centerX = (startX + endX) / 2
                val centerY = (startY + endY) / 2
                val bounds = "($startX, $startY) - ($endX, $endY)"

                val jsonObj = JSONObject().apply {
                    put("text", ocrResult.text)
                    put("confidence", ocrResult.confidence)
                    put("startX", startX)
                    put("startY", startY)
                    put("endX", endX)
                    put("endY", endY)
                    put("centerX", centerX)
                    put("centerY", centerY)
                    put("bounds", bounds)
                }
                jsonArray.put(jsonObj)

                // 目标匹配逻辑 (包含 / 完全匹配 / 正则 / 忽略大小写)
                if (!matchFound && targetText.isNotEmpty()) {
                    val isMatch = if (isRegex) {
                        try {
                            val regexOptions = if (isIgnoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                            if (isExactMatch) {
                                Regex(targetText, regexOptions).matches(ocrResult.text)
                            } else {
                                Regex(targetText, regexOptions).containsMatchIn(ocrResult.text)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[$SUB_TAG] Regex syntax error: ${e.message}")
                            false
                        }
                    } else if (isExactMatch) {
                        ocrResult.text.equals(targetText, ignoreCase = isIgnoreCase)
                    } else {
                        ocrResult.text.contains(targetText, ignoreCase = isIgnoreCase)
                    }

                    if (isMatch) {
                        matchFound = true
                        matchCenterX = centerX
                        matchCenterY = centerY
                        Log.d(TAG, "[$SUB_TAG] Match found in item[$i] at center ($matchCenterX, $matchCenterY)")
                    }
                }
            }

            val fullTextStr = fullTextBuilder.toString().trimEnd()
            val jsonStr = jsonArray.toString()

            OcrProcessResult(
                success = true,
                fullText = fullTextStr,
                json = jsonStr,
                hasTargetText = targetText.isNotEmpty(),
                matchFound = matchFound,
                matchCenterX = matchCenterX,
                matchCenterY = matchCenterY
            )
        } catch (e: Throwable) {
            Log.e(TAG, "[$SUB_TAG] OCR Processing exception", e)
            OcrProcessResult(
                success = false,
                errorMessage = "OCR识别异常: ${e.message ?: "未知错误"}"
            )
        } finally {
            if (cropResult.isCropped) {
                try {
                    if (!inferenceBitmap.isRecycled) {
                        inferenceBitmap.recycle()
                    }
                } catch (ignore: Exception) {}
            }
        }
    }
}
