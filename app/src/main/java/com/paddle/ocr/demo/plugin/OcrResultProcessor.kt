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
    val json: String = "[]",
    val matchFound: Boolean = false,
    val matchCenterX: Int? = null,
    val matchCenterY: Int? = null,
    val errorMessage: String? = null
) {
    /**
     * 将处理结果打包为 Tasker/MacroDroid 认识的标准 Bundle 变量
     */
    fun toTaskerBundle(): Bundle {
        return Bundle().apply {
            if (success) {
                putString("%ocr_full_text", fullText)
                putString("%ocr_json", json)
                putString("%match_found", matchFound.toString())
                if (matchFound && matchCenterX != null && matchCenterY != null) {
                    putString("%match_center_x", matchCenterX.toString())
                    putString("%match_center_y", matchCenterY.toString())
                }
            } else {
                putString(
                    TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE,
                    errorMessage ?: "未知错误"
                )
            }
        }
    }
}

/**
 * 通用 OCR 结果处理器 (纯业务与算法层，供截屏、文件图片等多数据源共享)
 */
object OcrResultProcessor {

    private const val TAG = "OcrPlugin"
    private const val SUB_TAG = "Processor"

    suspend fun process(
        bitmap: Bitmap,
        targetText: String,
        isRegex: Boolean = false,
        isExactMatch: Boolean = false,
        isIgnoreCase: Boolean = true
    ): OcrProcessResult {
        val ocrEngine = OCRApplication.instance.ocr
        if (ocrEngine == null) {
            Log.e(TAG, "[$SUB_TAG] OCR Engine is NULL - not initialized!")
            return OcrProcessResult(
                success = false,
                errorMessage = "OCR引擎未初始化"
            )
        }

        return try {
            Log.d(TAG, "[$SUB_TAG] Starting OCR recognition on bitmap: ${bitmap.width}x${bitmap.height}...")
            val result = ocrEngine.recognize(bitmap)
            Log.d(TAG, "[$SUB_TAG] recognize() returned ${result.results.size} text blocks")

            val fullTextBuilder = StringBuilder()
            val jsonArray = JSONArray()
            var matchFound = false
            var matchCenterX: Int? = null
            var matchCenterY: Int? = null

            result.results.forEachIndexed { i, ocrResult ->
                fullTextBuilder.append(ocrResult.text).append("\n")

                val startX = ocrResult.box.points.minOf { it.x }.toInt()
                val startY = ocrResult.box.points.minOf { it.y }.toInt()
                val endX = ocrResult.box.points.maxOf { it.x }.toInt()
                val endY = ocrResult.box.points.maxOf { it.y }.toInt()
                // 整数除法直接向下截断小数，不四舍五入
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
        }
    }
}
