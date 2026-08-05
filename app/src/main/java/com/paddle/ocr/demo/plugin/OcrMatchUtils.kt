package com.paddle.ocr.demo.plugin

import android.os.Bundle
import android.util.Log
import com.paddle.ocr.model.OCRRunResult
import org.json.JSONArray
import org.json.JSONObject

data class CropResult(
    val croppedBitmap: android.graphics.Bitmap,
    val offsetX: Int,
    val offsetY: Int,
    val isCropped: Boolean
)

object OcrMatchUtils {
    private const val TAG = "OcrMatchUtils"

    fun cropBitmapIfNeeded(
        bitmap: android.graphics.Bitmap,
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
            val leftPct = regionLeft.toFloat().coerceIn(0f, 1f)
            val topPct = regionTop.toFloat().coerceIn(0f, 1f)
            val rightPct = regionRight.toFloat().coerceIn(0f, 1f)
            val bottomPct = regionBottom.toFloat().coerceIn(0f, 1f)

            val bW = bitmap.width
            val bH = bitmap.height

            val cropLeft = (leftPct * bW).toInt().coerceIn(0, bW)
            val cropTop = (topPct * bH).toInt().coerceIn(0, bH)
            val cropRight = (rightPct * bW).toInt().coerceIn(0, bW)
            val cropBottom = (bottomPct * bH).toInt().coerceIn(0, bH)

            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop

            if (cropWidth > 0 && cropHeight > 0) {
                val cropped = android.graphics.Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
                CropResult(cropped, cropLeft, cropTop, true)
            } else {
                CropResult(bitmap, 0, 0, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bitmap: ${e.message}", e)
            CropResult(bitmap, 0, 0, false)
        }
    }

    fun processOcrResultToBundle(
        result: OCRRunResult,
        targetText: String,
        isRegex: Boolean,
        isExactMatch: Boolean,
        isIgnoreCase: Boolean,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): Bundle {
        val fullTextBuilder = StringBuilder()
        val jsonArray = JSONArray()
        var matchFound = false
        var matchCenterX = 0
        var matchCenterY = 0

        result.results.forEachIndexed { i, ocrResult ->
            fullTextBuilder.append(ocrResult.text).append("\n")
            Log.d(TAG, "result[$i]: text='${ocrResult.text}' confidence=${ocrResult.confidence}")

            val jsonObj = JSONObject()
            jsonObj.put("text", ocrResult.text)
            jsonObj.put("confidence", ocrResult.confidence)
            val tl = ocrResult.box.points[0]
            val br = ocrResult.box.points[2]
            
            val startX = tl.x.toInt() + offsetX
            val startY = tl.y.toInt() + offsetY
            val endX = br.x.toInt() + offsetX
            val endY = br.y.toInt() + offsetY
            val centerX = ((tl.x + br.x) / 2f).toInt() + offsetX
            val centerY = ((tl.y + br.y) / 2f).toInt() + offsetY
            
            jsonObj.put("startX", startX)
            jsonObj.put("startY", startY)
            jsonObj.put("endX", endX)
            jsonObj.put("endY", endY)
            jsonObj.put("centerX", centerX)
            jsonObj.put("centerY", centerY)
            jsonObj.put("bounds", "($startX, $startY) - ($endX, $endY)")
            jsonArray.put(jsonObj)

            // Check for target text
            if (!matchFound && targetText.isNotEmpty()) {
                val isMatch = if (isRegex) {
                    val regexOptions = if (isIgnoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    if (isExactMatch) {
                        Regex(targetText, regexOptions).matches(ocrResult.text)
                    } else {
                        Regex(targetText, regexOptions).containsMatchIn(ocrResult.text)
                    }
                } else if (isExactMatch) {
                    ocrResult.text.equals(targetText, ignoreCase = isIgnoreCase)
                } else {
                    ocrResult.text.contains(targetText, ignoreCase = isIgnoreCase)
                }
                
                if (isMatch) {
                    Log.d(TAG, "MATCH FOUND in result[$i]!")
                    matchFound = true
                    val tlx = ocrResult.box.points[0].x.toInt()
                    val tly = ocrResult.box.points[0].y.toInt()
                    val brx = ocrResult.box.points[2].x.toInt()
                    val bry = ocrResult.box.points[2].y.toInt()
                    matchCenterX = ((tlx + brx) / 2) + offsetX
                    matchCenterY = ((tly + bry) / 2) + offsetY
                    Log.d(TAG, "  centerX=$matchCenterX, centerY=$matchCenterY (with offset: $offsetX, $offsetY)")
                }
            }
        }
        
        val fullTextStr = fullTextBuilder.toString().trimEnd()
        val jsonStr = jsonArray.toString()
        Log.d(TAG, "fullText length=${fullTextStr.length}, json length=${jsonStr.length}, matchFound=$matchFound")
        
        return Bundle().apply {
            putString("%ocr_error", "")
            putString("%ocr_full_text", fullTextStr)
            putString("%ocr_json", jsonStr)
            if (targetText.isEmpty()) {
                putString("%match_found", "")
                putString("%match_center_x", "")
                putString("%match_center_y", "")
            } else {
                putString("%match_found", matchFound.toString())
                if (matchFound) {
                    putString("%match_center_x", matchCenterX.toString())
                    putString("%match_center_y", matchCenterY.toString())
                } else {
                    putString("%match_center_x", "")
                    putString("%match_center_y", "")
                }
            }
        }
    }
}