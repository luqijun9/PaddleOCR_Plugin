package com.paddle.ocr.demo.plugin

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.IBinder
import android.widget.Toast
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.paddle.ocr.demo.OCRApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ScreenCaptureService : Service() {

    companion object {
        const val TAG = "OcrPlugin"
        const val SUB_TAG = "Service"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "ScreenCaptureChannel"
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        log("=== onCreate ===")
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tasker OCR")
            .setContentText("正在截屏识别中...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        log("foreground notification started")
    }

    @SuppressLint("WrongConstant")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("=== onStartCommand ===")
        log("intent=$intent, flags=$flags, startId=$startId")
        
        if (intent == null) {
            log("intent is NULL, stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        val fireIntent: Intent? = intent.getParcelableExtra("fireIntent")
        val targetText = intent.getStringExtra("targetText") ?: ""
        val isRegex = intent.getBooleanExtra("isRegex", false)
        val filePath = intent.getStringExtra(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH)
        val isAppTest = intent.getBooleanExtra("isAppTest", false)

        log("fireIntent action=${fireIntent?.action}")
        log("targetText=$targetText, isRegex=$isRegex, filePath=$filePath")

        if (!filePath.isNullOrEmpty()) {
            log("Using file path mode: $filePath")
            val bitmap = android.graphics.BitmapFactory.decodeFile(filePath)
            if (bitmap != null) {
                CoroutineScope(Dispatchers.Default).launch {
                    processOcr(bitmap, targetText, isRegex, fireIntent, isAppTest)
                    stopSelf()
                }
            } else {
                log("Failed to decode file: $filePath")
                val varsBundle = Bundle().apply {
                    putString("%ocr_error", "无法读取图片文件: $filePath")
                }
                signalTaskerFinish(fireIntent, false, varsBundle)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra("resultCode", -1)
        val data: Intent? = intent.getParcelableExtra("data")

        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            log("MediaProjection token received, starting capture")
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            captureScreenAndOcr(targetText, isRegex, fireIntent, isAppTest)
        } else {
            log("No MediaProjection token, cannot capture screen")
            val varsBundle = Bundle().apply {
                putString("%ocr_error", "未获得录屏权限")
            }
            signalTaskerFinish(fireIntent, false, varsBundle)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAndOcr(targetText: String, isRegex: Boolean, fireIntent: Intent?, isAppTest: Boolean) {
        log("=== captureScreenAndOcr ===")
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        log("display: ${width}x${height} @ ${density}dpi")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        log("imageReader created")

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                log("MediaProjection onStop")
                super.onStop()
                mediaProjection?.unregisterCallback(this)
            }
        }, null)

        log("creating virtual display...")
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        log("virtualDisplay created: ${virtualDisplay != null}")

        imageReader?.setOnImageAvailableListener({ reader ->
            log("=== onImageAvailable ===")
            val image: Image? = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                log("acquireLatestImage exception: ${e.message}")
                null
            }
            
            if (image != null) {
                log("image acquired successfully")
                // Remove listener so we only get one frame
                imageReader?.setOnImageAvailableListener(null, null)

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                log("image planes: ${planes.size}, rowStride=$rowStride, pixelStride=$pixelStride")

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                image.close()
                log("bitmap created: ${croppedBitmap.width}x${croppedBitmap.height}")

                virtualDisplay?.release()
                mediaProjection?.stop()
                log("virtualDisplay released, mediaProjection stopped")

                log("launching coroutine for OCR processing")
                scope.launch {
                    processOcr(croppedBitmap, targetText, isRegex, fireIntent, isAppTest)
                    log("OCR processing done, calling stopSelf")
                    stopSelf()
                }
            } else {
                log("IMAGE IS NULL - screen capture failed!")
                scope.launch {
                    val bundle = Bundle().apply {
                        putString("%ocr_error", "截屏失败，未获取到有效图像数据")
                    }
                    signalTaskerFinish(fireIntent, false, bundle)
                    stopSelf()
                }
            }
        }, null)
        log("onImageAvailable listener registered")
    }

    private suspend fun processOcr(bitmap: Bitmap, targetText: String, isRegex: Boolean, fireIntent: Intent?, isAppTest: Boolean) {
        log("=== processOcr ===")
        val ocrEngine = OCRApplication.instance.ocr
        if (ocrEngine == null) {
            log("OCR ENGINE IS NULL - not initialized!")
            if (!isAppTest) {
                val bundle = Bundle().apply {
                    putString("%ocr_error", "OCR引擎未初始化")
                }
                signalTaskerFinish(fireIntent, false, bundle)
            }
            return
        }
        log("OCR engine available")

        try {
            log("calling ocrEngine.recognize()...")
            val result = ocrEngine.recognize(bitmap)
            log("recognize() returned. results count: ${result.results.size}")

            if (result.results.isEmpty()) {
                log("NO OCR RESULTS FOUND")
            }

            val fullTextBuilder = StringBuilder()
            val jsonArray = JSONArray()
            var matchFound = false
            var matchCenterX = 0f
            var matchCenterY = 0f

            result.results.forEachIndexed { i, ocrResult ->
                fullTextBuilder.append(ocrResult.text).append("\n")
                log("  result[$i]: text='${ocrResult.text}' confidence=${ocrResult.confidence}")

                val jsonObj = JSONObject()
                jsonObj.put("text", ocrResult.text)
                jsonObj.put("confidence", ocrResult.confidence)
                val boxArr = JSONArray()
                ocrResult.box.points.forEach { point ->
                    val pointObj = JSONObject()
                    pointObj.put("x", point.x)
                    pointObj.put("y", point.y)
                    boxArr.put(pointObj)
                }
                jsonObj.put("box", boxArr)
                jsonArray.put(jsonObj)

                // Check for target text
                if (!matchFound && targetText.isNotEmpty()) {
                    val isMatch = if (isRegex) {
                        Regex(targetText).containsMatchIn(ocrResult.text)
                    } else {
                        ocrResult.text.contains(targetText)
                    }
                    if (isMatch) {
                        log("MATCH FOUND in result[$i]!")
                        matchFound = true
                        val tl = ocrResult.box.points[0]
                        val br = ocrResult.box.points[2]
                        matchCenterX = (tl.x + br.x) / 2f
                        matchCenterY = (tl.y + br.y) / 2f
                        log("  centerX=$matchCenterX, centerY=$matchCenterY")
                    }
                }
            }

            val fullTextStr = fullTextBuilder.toString().trimEnd()
            val jsonStr = jsonArray.toString()
            log("fullText length=${fullTextStr.length}, json length=${jsonStr.length}, matchFound=$matchFound")

            val bundle = Bundle().apply {
                putString("%ocr_full_text", fullTextStr)
                putString("%ocr_json", jsonStr)
                putString("%match_found", matchFound.toString())
                if (matchFound) {
                    putString("%match_center_x", matchCenterX.toString())
                    putString("%match_center_y", matchCenterY.toString())
                }
            }
            log("varsBundle created with keys: ${bundle.keySet()}")

            if (isAppTest) {
                log("isAppTest=true, emitting result instead of signaling Tasker")
                OCRApplication.instance.appTestResult.emit(Pair(bitmap, result))
            } else {
                log("calling signalTaskerFinish with success=true")
                signalTaskerFinish(fireIntent, true, bundle)
            }

        } catch (e: Exception) {
            log("OCR EXCEPTION: ${e.message}")
            Log.e(TAG, "[$SUB_TAG] OCR processing failed", e)
            if (!isAppTest) {
                val bundle = Bundle().apply {
                    putString("%ocr_error", "OCR 识别出错: ${e.message}")
                }
                signalTaskerFinish(fireIntent, false, bundle)
            }
        }
    }

    private fun signalTaskerFinish(fireIntent: Intent?, success: Boolean, varsBundle: Bundle) {
        log("=== signalTaskerFinish ===")
        log("success=$success, varsBundleKeys=${varsBundle.keySet()}")
        
        if (fireIntent == null) {
            log("fireIntent is null, CANNOT signal Tasker!")
            return
        }

        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        
        val signaled = TaskerPlugin.Setting.signalFinish(this, fireIntent, resultCode, varsBundle)
        log("signalFinish signaled=$signaled")

        Handler(Looper.getMainLooper()).post {
            if (success) {
                Toast.makeText(this, "OCR完成，已返回变量到Tasker", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "OCR失败或未匹配", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        log("=== onDestroy ===")
        imageReader?.close()
        mediaProjection?.stop()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
        log("notification channel created")
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}