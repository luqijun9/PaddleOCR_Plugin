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
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra("resultCode", 0)
        val data: Intent = intent.getParcelableExtra("data") ?: return START_NOT_STICKY
        val targetText = intent.getStringExtra("targetText") ?: ""
        val isRegex = intent.getBooleanExtra("isRegex", false)
        val fireIntent: Intent? = intent.getParcelableExtra("fireIntent")

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        captureScreenAndOcr(targetText, isRegex, fireIntent)

        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAndOcr(targetText: String, isRegex: Boolean, fireIntent: Intent?) {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
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

                scope.launch {
                    processOcr(croppedBitmap, targetText, isRegex, fireIntent)
                    stopSelf()
                }
            }
        }, null)
    }

    private suspend fun processOcr(bitmap: Bitmap, targetText: String, isRegex: Boolean, fireIntent: Intent?) {
        val ocrEngine = OCRApplication.instance.ocr
        if (ocrEngine == null) {
            Log.e(TAG, "OCR Engine not initialized!")
            signalTaskerFinish(fireIntent, false, Bundle())
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
                putString("%ocr_full_text", fullTextBuilder.toString().trimEnd())
                putString("%ocr_json", jsonArray.toString())
                putBoolean("%match_found", matchFound)
                if (matchFound) {
                    putFloat("%match_center_x", matchCenterX)
                    putFloat("%match_center_y", matchCenterY)
                }
            }

            Log.d(TAG, "OCR finished. Result length: ${fullTextBuilder.length}, matchFound: $matchFound")
            signalTaskerFinish(fireIntent, true, bundle)

        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed", e)
            signalTaskerFinish(fireIntent, false, Bundle())
        }
    }

    private fun signalTaskerFinish(fireIntent: Intent?, success: Boolean, varsBundle: Bundle) {
        if (fireIntent == null) return
        val taskerActionId = fireIntent.getByteArrayExtra("net.dinglisch.android.tasker.extras.PASS_THROUGH_MESSAGE_ID")
        
        val resultIntent = Intent("net.dinglisch.android.tasker.ACTION_EDIT_EVENT_SIGNAL_FINISH")
        resultIntent.putExtra("net.dinglisch.android.tasker.extras.PASS_THROUGH_MESSAGE_ID", taskerActionId)
        
        if (!success) {
            resultIntent.putExtra("net.dinglisch.android.tasker.extras.SIGNAL_STATE", 2) // FAILED
        } else {
            resultIntent.putExtra("net.dinglisch.android.tasker.extras.SIGNAL_STATE", 1) // OK
            resultIntent.putExtra("net.dinglisch.android.tasker.extras.VARIABLES", varsBundle)
        }
        sendBroadcast(resultIntent)
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
