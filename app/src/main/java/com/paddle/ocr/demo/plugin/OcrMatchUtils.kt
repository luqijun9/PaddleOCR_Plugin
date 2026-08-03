package com.paddle.ocr.demo.plugin

import android.os.Bundle
import android.util.Log
import com.paddle.ocr.model.OCRRunResult
import org.json.JSONArray
import org.json.JSONObject

object OcrMatchUtils {
    private const val TAG = "OcrMatchUtils"

    fun processOcrResultToBundle(
        result: OCRRunResult,
        targetText: String,
        isRegex: Boolean,
        isExactMatch: Boolean,
        isIgnoreCase: Boolean
    ): Bundle {
        val fullTextBuilder = StringBuilder()
        val jsonArray = JSONArray()
        var matchFound = false
        var matchCenterX = 0f
        var matchCenterY = 0f

        result.results.forEachIndexed { i, ocrResult ->
            fullTextBuilder.append(ocrResult.text).append("\n")
            Log.d(TAG, "result[$i]: text='${ocrResult.text}' confidence=${ocrResult.confidence}")

            val jsonObj = JSONObject()
            jsonObj.put("text", ocrResult.text)
            jsonObj.put("confidence", ocrResult.confidence)
            val tl = ocrResult.box.points[0]
            val br = ocrResult.box.points[2]
            
            jsonObj.put("startX", tl.x)
            jsonObj.put("startY", tl.y)
            jsonObj.put("endX", br.x)
            jsonObj.put("endY", br.y)
            jsonObj.put("centerX", (tl.x + br.x) / 2f)
            jsonObj.put("centerY", (tl.y + br.y) / 2f)
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
                    val tl = ocrResult.box.points[0]
                    val br = ocrResult.box.points[2]
                    matchCenterX = (tl.x + br.x) / 2f
                    matchCenterY = (tl.y + br.y) / 2f
                    Log.d(TAG, "  centerX=$matchCenterX, centerY=$matchCenterY")
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