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

    private fun cropBitmapByPixelBounds(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): CropResult {
        return try {
            val bW = bitmap.width
            val bH = bitmap.height
            val cropLeft = left.coerceIn(0, bW)
            val cropTop = top.coerceIn(0, bH)
            val cropRight = right.coerceIn(0, bW)
            val cropBottom = bottom.coerceIn(0, bH)

            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop

            if (cropWidth > 0 && cropHeight > 0 && (cropWidth < bW || cropHeight < bH)) {
                val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
                CropResult(cropped, cropLeft, cropTop, true)
            } else {
                CropResult(bitmap, 0, 0, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$SUB_TAG] Failed to crop bitmap by pixel bounds: ${e.message}", e)
            CropResult(bitmap, 0, 0, false)
        }
    }

    /**
     * 解析 "左,上,右,下" 区域字符串。
     * usePercent=true 时要求 0-100 的整数百分比，否则要求实际像素整数。
     * 格式非法或区域为空时返回 null。
     */
    fun parseRegionSpec(spec: String, usePercent: Boolean): IntArray? {
        val parts = spec.split(Regex("[,，;；\\s]+")).filter { it.isNotEmpty() }
        return parseRegionValues(parts, usePercent)
    }

    /**
     * 解析分开传入的四项边界值，任一项缺失或非法时返回 null。
     */
    fun parseRegionValues(values: List<String>, usePercent: Boolean): IntArray? {
        if (values.size != 4) return null
        val nums = IntArray(4)
        for (i in 0..3) {
            val v = values[i].trim().toIntOrNull() ?: return null
            if (v < 0) return null
            if (usePercent && v > 100) return null
            nums[i] = v
        }
        if (nums[2] <= nums[0] || nums[3] <= nums[1]) return null
        return nums
    }

    /**
     * 将变量区域（整数百分比或实际像素）换算为当前截图的像素边界；越界或空选区返回 null。
     */
    private fun toPixelBounds(values: IntArray, bitmap: Bitmap, usePercent: Boolean): IntArray? {
        val bW = bitmap.width
        val bH = bitmap.height
        if (bW <= 0 || bH <= 0) return null

        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        if (usePercent) {
            left = values[0] * bW / 100
            top = values[1] * bH / 100
            right = values[2] * bW / 100
            bottom = values[3] * bH / 100
        } else {
            left = values[0]
            top = values[1]
            right = values[2]
            bottom = values[3]
        }

        if (left < 0 || top < 0 || right > bW || bottom > bH) return null
        if (right <= left || bottom <= top) return null
        return intArrayOf(left, top, right, bottom)
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
        regionBottom: String = "1.0",
        regionSpec: String = "",
        regionMode: String = TaskerPluginConstants.REGION_MODE_SINGLE,
        regionUsePercent: Boolean = false,
        regionVarLeft: String = "",
        regionVarTop: String = "",
        regionVarRight: String = "",
        regionVarBottom: String = ""
    ): OcrProcessResult {
        // 区域变量优先于固定边界（宿主运行时会把 Tasker/MacroDroid 变量替换为实际数值）
        var pixelBounds: IntArray? = null
        if (restrictRegion) {
            val parsed: IntArray? = if (regionMode == TaskerPluginConstants.REGION_MODE_SEPARATE) {
                val values = listOf(regionVarLeft, regionVarTop, regionVarRight, regionVarBottom)
                if (values.any { it.isNotBlank() }) {
                    parseRegionValues(values, regionUsePercent) ?: return OcrProcessResult(
                        success = false,
                        errorMessage = if (regionUsePercent)
                            "分开范围变量格式无效: 左/上/右/下 需为 0-100 的整数百分比，当前: ${values.joinToString(",")}"
                        else
                            "分开范围变量格式无效: 左/上/右/下 需为实际像素整数，当前: ${values.joinToString(",")}"
                    )
                } else {
                    null
                }
            } else if (regionSpec.isNotBlank()) {
                parseRegionSpec(regionSpec, regionUsePercent) ?: return OcrProcessResult(
                    success = false,
                    errorMessage = if (regionUsePercent)
                        "范围变量格式无效: \"$regionSpec\"，应为 左,上,右,下 四个 0-100 的整数百分比（如 0,25,100,75）"
                    else
                        "范围变量格式无效: \"$regionSpec\"，应为 左,上,右,下 四个实际像素整数（如 100,300,900,1200）"
                )
            } else {
                null
            }

            if (parsed != null) {
                val bounds = toPixelBounds(parsed, bitmap, regionUsePercent)
                if (bounds == null) {
                    return OcrProcessResult(
                        success = false,
                        errorMessage = if (regionUsePercent)
                            "范围变量换算后选区无效: ${parsed.joinToString(",")}（百分比），截图尺寸: ${bitmap.width}x${bitmap.height}"
                        else
                            "范围变量超出截图尺寸: ${parsed.joinToString(",")}（像素），截图尺寸: ${bitmap.width}x${bitmap.height}"
                    )
                }
                pixelBounds = bounds
                Log.d(TAG, "[$SUB_TAG] Region variables resolved to pixels: ${bounds.joinToString(",")} (${if (regionUsePercent) "percent" else "pixel"} input)")
            }
        }

        val ocrEngine = OCRApplication.instance.awaitModelReady(timeoutMs = 10000)
        if (ocrEngine == null) {
            Log.e(TAG, "[$SUB_TAG] OCR Engine is NULL - initialization timed out or failed!")
            return OcrProcessResult(
                success = false,
                errorMessage = "OCR引擎初始化超时或失败"
            )
        }

        val resolvedPixels = pixelBounds
        val cropResult = if (resolvedPixels != null) {
            cropBitmapByPixelBounds(bitmap, resolvedPixels[0], resolvedPixels[1], resolvedPixels[2], resolvedPixels[3])
        } else {
            cropBitmapIfNeeded(bitmap, restrictRegion, regionLeft, regionTop, regionRight, regionBottom)
        }
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
