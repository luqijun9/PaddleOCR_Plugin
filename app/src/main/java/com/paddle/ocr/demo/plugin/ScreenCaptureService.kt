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

        val resultCode = intent.getIntExtra("resultCode", -1)
        val data: Intent = intent.getParcelableExtra("data") ?: run {
            log("data is NULL, stopping self")
            return START_NOT_STICKY
        }
        val targetText = intent.getStringExtra("targetText") ?: ""
        val isRegex = intent.getBooleanExtra("isRegex", false)
        val pendingIntent: android.app.PendingIntent? = intent.getParcelableExtra("pendingIntent")
        val isAppTest = intent.getBooleanExtra("isAppTest", false)

        log("resultCode=$resultCode")
        log("targetText=$targetText, isRegex=$isRegex, isAppTest=$isAppTest")
        log("data action=${data.action}")

        if (pendingIntent != null) {
            log("pendingIntent is present")
        } else {
            log("pendingIntent is NULL!")
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        log("mediaProjection created: ${mediaProjection != null}")

        log("calling captureScreenAndOcr")
        captureScreenAndOcr(targetText, isRegex, pendingIntent, isAppTest)

        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAndOcr(targetText: String, isRegex: Boolean, pendingIntent: android.app.PendingIntent?, isAppTest: Boolean) {
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

        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            log("SCREEN CAPTURE TIMEOUT (5s) - no image frame received")
            imageReader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.release()
            mediaProjection?.stop()
            val errBundle = Bundle().apply {
                putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, "截屏超时：5秒内未能获取屏幕图像帧")
            }
            signalTaskerFinish(pendingIntent, false, errBundle)
            stopSelf()
        }
        timeoutHandler.postDelayed(timeoutRunnable, 5000)

        imageReader?.setOnImageAvailableListener({ reader ->
            log("=== onImageAvailable ===")
            timeoutHandler.removeCallbacks(timeoutRunnable)

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
                    processOcr(croppedBitmap, targetText, isRegex, pendingIntent, isAppTest)
                    log("OCR processing done, calling stopSelf")
                    stopSelf()
                }
            } else {
                log("IMAGE IS NULL - screen capture failed!")
                scope.launch {
                    val errBundle = Bundle().apply {
                        putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, "截屏失败：未能获取屏幕图像")
                    }
                    signalTaskerFinish(pendingIntent, false, errBundle)
                    stopSelf()
                }
            }
        }, null)
        log("onImageAvailable listener registered")
    }

    private suspend fun processOcr(bitmap: Bitmap, targetText: String, isRegex: Boolean, pendingIntent: android.app.PendingIntent?, isAppTest: Boolean) {
        log("=== processOcr (via OcrResultProcessor) ===")
        val result = OcrResultProcessor.process(bitmap, targetText, isRegex)
        log("OcrResultProcessor returned success=${result.success}, matchFound=${result.matchFound}, error=${result.errorMessage}")

        if (isAppTest) {
            log("isAppTest=true, emitting result for Demo app")
            val ocrModelResult = OCRApplication.instance.ocr?.recognize(bitmap)
            if (ocrModelResult != null) {
                OCRApplication.instance.appTestResult.emit(Pair(bitmap, ocrModelResult))
            }
        } else {
            signalTaskerFinish(pendingIntent, result.success, result.toTaskerBundle())
        }
    }

    private fun signalTaskerFinish(pendingIntent: android.app.PendingIntent?, success: Boolean, varsBundle: Bundle) {
        log("=== signalTaskerFinish ===")
        log("pendingIntent=$pendingIntent, success=$success, varsBundleKeys=${varsBundle.keySet()}")
        
        if (pendingIntent == null) {
            log("pendingIntent is null, CANNOT signal Tasker!")
            return
        }

        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        log("resultCode=$resultCode")

        val resultIntent = Intent().apply {
            putExtra(PluginResultsService.EXTRA_RESULT_CODE, resultCode)
            putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, varsBundle)
        }

        try {
            pendingIntent.send(this, 0, resultIntent)
            log("pendingIntent sent successfully to PluginResultsService")
        } catch (e: android.app.PendingIntent.CanceledException) {
            log("pendingIntent CanceledException: ${e.message}")
            e.printStackTrace()
        }

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