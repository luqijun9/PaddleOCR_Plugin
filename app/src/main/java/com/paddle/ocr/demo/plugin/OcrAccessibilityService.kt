package com.paddle.ocr.demo.plugin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.Display
import androidx.annotation.RequiresApi
import com.paddle.ocr.demo.OCRApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class OcrAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "OcrPlugin"
        var instance: OcrAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "OcrAccessibilityService onServiceConnected")
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "OcrAccessibilityService onUnbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "OcrAccessibilityService onDestroy")
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureAndRecognize(fireIntent: Intent, targetText: String, isRegex: Boolean) {
        Log.d(TAG, "OcrAccessibilityService captureAndRecognize triggered")
        
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                Log.d(TAG, "OcrAccessibilityService Screenshot onSuccess")
                val hardwareBuffer = screenshot.hardwareBuffer
                val colorSpace = screenshot.colorSpace
                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                
                if (bitmap != null) {
                    // Start OCR process
                    val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    performOcr(softwareBitmap, fireIntent, targetText, isRegex)
                } else {
                    hardwareBuffer.close()
                    signalError(fireIntent, "无法从 HardwareBuffer 转换 Bitmap")
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.d(TAG, "OcrAccessibilityService Screenshot onFailure: $errorCode")
                signalError(fireIntent, "截屏失败，错误码：$errorCode")
            }
        })
    }

    private fun performOcr(bitmap: Bitmap, fireIntent: Intent, targetText: String, isRegex: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val ocrEngine = OCRApplication.instance.ocr
                if (ocrEngine == null) {
                    signalError(fireIntent, "OCR引擎未初始化")
                    bitmap.recycle()
                    return@launch
                }
                
                val result = ocrEngine.recognize(bitmap)
                bitmap.recycle()
                
                val fullTextBuilder = StringBuilder()
                val jsonArray = JSONArray()

                var matchFound = false
                var matchCenterX = 0f
                var matchCenterY = 0f

                val regex = if (isRegex && targetText.isNotEmpty()) Regex(targetText) else null

                for (ocrResult in result.results) {
                    val text = ocrResult.text
                    val confidence = ocrResult.confidence
                    val pts = ocrResult.box.points

                    fullTextBuilder.append(text).append("\n")

                    val boxJson = JSONObject().apply {
                        put("text", text)
                        put("confidence", confidence.toDouble())
                        val ptsArray = JSONArray()
                        pts.forEach { pt ->
                            ptsArray.put(JSONObject().apply {
                                put("x", pt.x)
                                put("y", pt.y)
                            })
                        }
                        put("points", ptsArray)
                    }
                    jsonArray.put(boxJson)

                    // Check for target text
                    if (!matchFound && targetText.isNotEmpty()) {
                        val isMatch = if (isRegex && regex != null) {
                            regex.containsMatchIn(text)
                        } else {
                            text.contains(targetText)
                        }

                        if (isMatch) {
                            matchFound = true
                            val tl = pts[0]
                            val br = pts[2]
                            matchCenterX = (tl.x + br.x) / 2f
                            matchCenterY = (tl.y + br.y) / 2f
                        }
                    }
                }

                val varsBundle = Bundle().apply {
                    val fullTextStr = fullTextBuilder.toString().trimEnd()
                    val jsonStr = jsonArray.toString()
                    val matchStr = matchFound.toString()
                    
                    putString("%ocr_full_text", fullTextStr)
                    putString("%ocr_json", jsonStr)
                    putString("%match_found", matchStr)
                    
                    if (matchFound) {
                        putString("%match_center_x", matchCenterX.toString())
                        putString("%match_center_y", matchCenterY.toString())
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "OcrAccessibilityService OCR Complete, signaling Tasker")
                    val resultCode = TaskerPlugin.Setting.RESULT_CODE_OK
                    TaskerPlugin.Setting.signalFinish(this@OcrAccessibilityService, fireIntent, resultCode, varsBundle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "OcrAccessibilityService OCR Error: ${e.message}", e)
                signalError(fireIntent, "OCR 识别出错: ${e.message}")
            }
        }
    }

    fun signalError(fireIntent: Intent, errorMessage: String) {
        Handler(Looper.getMainLooper()).post {
            val varsBundle = Bundle().apply {
                putString("%ocr_error", errorMessage)
            }
            TaskerPlugin.Setting.signalFinish(this, fireIntent, TaskerPlugin.Setting.RESULT_CODE_FAILED, varsBundle)
        }
    }
}
