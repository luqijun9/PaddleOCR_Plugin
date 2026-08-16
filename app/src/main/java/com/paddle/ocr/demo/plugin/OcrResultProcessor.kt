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
    val matchCenterX: Float? = null,
    val matchCenterY: Float? = null,
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
        isRegex: Boolean
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
            var matchCenterX: Float? = null
            var matchCenterY: Float? = null

            result.results.forEachIndexed { i, ocrResult ->
                fullTextBuilder.append(ocrResult.text).append("\n")

                val jsonObj = JSONObject().apply {
                    put("text", ocrResult.text)
                    put("confidence", ocrResult.confidence)
                    val boxArr = JSONArray()
                    ocrResult.box.points.forEach { point ->
                        val pointObj = JSONObject().apply {
                            put("x", point.x)
                            put("y", point.y)
                        }
                        boxArr.put(pointObj)
                    }
                    put("box", boxArr)
                }
                jsonArray.put(jsonObj)

                // 正则或关键字匹配
                if (!matchFound && targetText.isNotEmpty()) {
                    val isMatch = if (isRegex) {
                        try {
                            Regex(targetText).containsMatchIn(ocrResult.text)
                        } catch (e: Exception) {
                            Log.w(TAG, "[$SUB_TAG] Regex syntax error: ${e.message}")
                            false
                        }
                    } else {
                        ocrResult.text.contains(targetText)
                    }

                    if (isMatch) {
                        matchFound = true
                        val tl = ocrResult.box.points[0]
                        val br = ocrResult.box.points[2]
                        matchCenterX = (tl.x + br.x) / 2f
                        matchCenterY = (tl.y + br.y) / 2f
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
