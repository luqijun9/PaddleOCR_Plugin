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
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.paddle.ocr.demo.OCRApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.content.ComponentName

class ScreenCaptureService : Service() {

    companion object {
        const val TAG = "ScreenCaptureService"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "ScreenCaptureChannel"
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tasker OCR")
            .setContentText("正在截屏识别中...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    @SuppressLint("WrongConstant")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FileLogger.log(this, TAG, "onStartCommand called")
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra("resultCode", 0)
        val data: Intent = intent.getParcelableExtra("data") ?: return START_NOT_STICKY
        val targetText = intent.getStringExtra("targetText") ?: ""
        val isRegex = intent.getBooleanExtra("isRegex", false)
        val fireIntent: Intent? = intent.getParcelableExtra("fireIntent")
        val isAppTest = intent.getBooleanExtra("isAppTest", false)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        captureScreenAndOcr(targetText, isRegex, fireIntent, isAppTest)

        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAndOcr(targetText: String, isRegex: Boolean, fireIntent: Intent?, isAppTest: Boolean) {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                mediaProjection?.unregisterCallback(this)
            }
        }, null)

        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (image != null) {
                // Remove listener so we only get one frame
                imageReader?.setOnImageAvailableListener(null, null)

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                image.close()

                virtualDisplay?.release()
                mediaProjection?.stop()

                FileLogger.log(this@ScreenCaptureService, TAG, "Screen captured successfully. Starting OCR.")
                scope.launch {
                    processOcr(croppedBitmap, targetText, isRegex, fireIntent, isAppTest)
                    stopSelf()
                }
            } else {
                FileLogger.log(this@ScreenCaptureService, TAG, "Failed to capture image (image is null)")
            }
        }, null)
    }

    private suspend fun processOcr(bitmap: Bitmap, targetText: String, isRegex: Boolean, fireIntent: Intent?, isAppTest: Boolean) {
        val ocrEngine = OCRApplication.instance.ocr
        if (ocrEngine == null) {
            Log.e(TAG, "OCR Engine not initialized!")
            if (!isAppTest) signalTaskerFinish(fireIntent, false, Bundle())
            return
        }

        try {
            val result = ocrEngine.recognize(bitmap)
            
            val fullTextBuilder = StringBuilder()
            val jsonArray = JSONArray()
            var matchFound = false
            var matchCenterX = 0f
            var matchCenterY = 0f

            result.results.forEach { ocrResult ->
                fullTextBuilder.append(ocrResult.text).append("\n")
                
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
                        matchFound = true
                        // Calculate center point (average of 4 corners, or just tl and br)
                        val tl = ocrResult.box.points[0]
                        val br = ocrResult.box.points[2]
                        matchCenterX = (tl.x + br.x) / 2f
                        matchCenterY = (tl.y + br.y) / 2f
                    }
                }
            }

            val bundle = Bundle().apply {
                val fullTextStr = fullTextBuilder.toString().trimEnd()
                val jsonStr = jsonArray.toString()
                val matchStr = matchFound.toString()
                
                // with %
                putString("%ocr_full_text", fullTextStr)
                putString("%ocr_json", jsonStr)
                putString("%match_found", matchStr)
                
                // without % (Tasker docs actually say no %)
                putString("ocr_full_text", fullTextStr)
                putString("ocr_json", jsonStr)
                putString("match_found", matchStr)
                
                if (matchFound) {
                    val centerXStr = matchCenterX.toString()
                    val centerYStr = matchCenterY.toString()
                    putString("%match_center_x", centerXStr)
                    putString("%match_center_y", centerYStr)
                    putString("match_center_x", centerXStr)
                    putString("match_center_y", centerYStr)
                }
            }

            Log.d(TAG, "OCR finished. Result length: ${fullTextBuilder.length}, matchFound: $matchFound")
            FileLogger.log(this, TAG, "OCR finished. matchFound: $matchFound, isAppTest: $isAppTest")
            if (isAppTest) {
                OCRApplication.instance.appTestResult.emit(Pair(bitmap, result))
            } else {
                signalTaskerFinish(fireIntent, true, bundle)
            }

        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed", e)
            FileLogger.log(this, TAG, "OCR processing failed: ${e.message}")
            if (!isAppTest) signalTaskerFinish(fireIntent, false, Bundle())
        }
    }

    private fun signalTaskerFinish(fireIntent: Intent?, success: Boolean, varsBundle: Bundle) {
        if (fireIntent == null) return
        
        val completionIntentString = fireIntent.getStringExtra(TaskerPluginConstants.EXTRA_PLUGIN_COMPLETION_INTENT)
        if (completionIntentString != null) {
            try {
                val completionIntent = Intent.parseUri(completionIntentString, Intent.URI_INTENT_SCHEME)
                
                val resultCode = if (success) TaskerPluginConstants.RESULT_CODE_OK else TaskerPluginConstants.RESULT_CODE_FAILED
                completionIntent.putExtra(TaskerPluginConstants.EXTRA_RESULT_CODE, resultCode)
                
                if (success) {
                    completionIntent.putExtra(TaskerPluginConstants.EXTRA_VARIABLES, varsBundle)
                }
                
                // For Tasker Android 8+ background limits, we might need to start a service or broadcast
                val callServicePackage = completionIntent.getStringExtra("net.dinglisch.android.tasker.EXTRA_CALL_SERVICE_PACKAGE")
                val callService = completionIntent.getStringExtra("net.dinglisch.android.tasker.EXTRA_CALL_SERVICE")
                val foreground = completionIntent.getBooleanExtra("net.dinglisch.android.tasker.EXTRA_CALL_SERVICE_FOREGROUND", false)
                
                if (callServicePackage != null && callService != null) {
                    completionIntent.component = ComponentName(callServicePackage, callService)
                    FileLogger.log(this, TAG, "signaling Tasker via Service: $callServicePackage / $callService (foreground=$foreground)")
                    if (foreground && android.os.Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(completionIntent)
                    } else {
                        startService(completionIntent)
                    }
                } else {
                    FileLogger.log(this, TAG, "signaling Tasker via Broadcast")
                    completionIntent.setPackage("net.dinglisch.android.taskerm") // Force explicit broadcast for Android 8+ limits
                    sendBroadcast(completionIntent)
                }
                
                // Show a Toast so the user knows what happened
                Handler(Looper.getMainLooper()).post {
                    if (success) {
                        Toast.makeText(this, "OCR完成，已返回变量到Tasker", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "OCR失败或未匹配", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
