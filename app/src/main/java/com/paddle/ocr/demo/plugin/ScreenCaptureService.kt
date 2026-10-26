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
    private var captureStartTime: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("=== onStartCommand ===")
        captureStartTime = System.currentTimeMillis()
        PluginStatusManager.notifyRunning("正在截屏并推理文字...")
        
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
        val isExactMatch = intent.getBooleanExtra("isExactMatch", false)
        val isIgnoreCase = intent.getBooleanExtra("isIgnoreCase", true)
        val restrictRegion = intent.getBooleanExtra("restrictRegion", false)
        val regionLeft = intent.getStringExtra("regionLeft") ?: "0.0"
        val regionTop = intent.getStringExtra("regionTop") ?: "0.0"
        val regionRight = intent.getStringExtra("regionRight") ?: "1.0"
        val regionBottom = intent.getStringExtra("regionBottom") ?: "1.0"
        val regionSpec = intent.getStringExtra("regionSpec") ?: ""
        val regionMode = intent.getStringExtra("regionMode") ?: TaskerPluginConstants.REGION_MODE_SINGLE
        val regionUsePercent = intent.getBooleanExtra("regionUsePercent", false)
        val regionVarLeft = intent.getStringExtra("regionVarLeft") ?: ""
        val regionVarTop = intent.getStringExtra("regionVarTop") ?: ""
        val regionVarRight = intent.getStringExtra("regionVarRight") ?: ""
        val regionVarBottom = intent.getStringExtra("regionVarBottom") ?: ""
        val regionVarX = intent.getStringExtra("regionVarX") ?: ""
        val regionVarY = intent.getStringExtra("regionVarY") ?: ""
        val regionVarW = intent.getStringExtra("regionVarW") ?: ""
        val regionVarH = intent.getStringExtra("regionVarH") ?: ""
        val pendingIntent: android.app.PendingIntent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("pendingIntent", android.app.PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("pendingIntent")
        }
        val fireIntent: Intent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("fireIntent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("fireIntent")
        }
        val isAppTest = intent.getBooleanExtra("isAppTest", false)

        log("resultCode=$resultCode")
        log("targetText=$targetText, isRegex=$isRegex, isExactMatch=$isExactMatch, isIgnoreCase=$isIgnoreCase, restrictRegion=$restrictRegion, isAppTest=$isAppTest, hasFireIntent=${fireIntent != null}")
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
        captureScreenAndOcr(
            targetText = targetText,
            isRegex = isRegex,
            isExactMatch = isExactMatch,
            isIgnoreCase = isIgnoreCase,
            restrictRegion = restrictRegion,
            regionLeft = regionLeft,
            regionTop = regionTop,
            regionRight = regionRight,
            regionBottom = regionBottom,
            regionSpec = regionSpec,
            regionMode = regionMode,
            regionUsePercent = regionUsePercent,
            regionVarLeft = regionVarLeft,
            regionVarTop = regionVarTop,
            regionVarRight = regionVarRight,
            regionVarBottom = regionVarBottom,
            regionVarX = regionVarX,
            regionVarY = regionVarY,
            regionVarW = regionVarW,
            regionVarH = regionVarH,
            fireIntent = fireIntent,
            pendingIntent = pendingIntent,
            isAppTest = isAppTest
        )

        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAndOcr(
        targetText: String,
        isRegex: Boolean,
        isExactMatch: Boolean,
        isIgnoreCase: Boolean,
        restrictRegion: Boolean,
        regionLeft: String,
        regionTop: String,
        regionRight: String,
        regionBottom: String,
        regionSpec: String,
        regionMode: String,
        regionUsePercent: Boolean,
        regionVarLeft: String,
        regionVarTop: String,
        regionVarRight: String,
        regionVarBottom: String,
        regionVarX: String,
        regionVarY: String,
        regionVarW: String,
        regionVarH: String,
        fireIntent: Intent?,
        pendingIntent: android.app.PendingIntent?,
        isAppTest: Boolean
    ) {
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
            val errBundle = OcrProcessResult.createErrorBundle("截屏超时：5秒内未能获取屏幕图像帧")
            signalTaskerFinish(fireIntent, pendingIntent, false, errBundle)
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
                if (croppedBitmap != bitmap) {
                    try { bitmap.recycle() } catch (ignore: Exception) {}
                }
                image.close()
                log("bitmap created: ${croppedBitmap.width}x${croppedBitmap.height}")

                virtualDisplay?.release()
                mediaProjection?.stop()
                log("virtualDisplay released, mediaProjection stopped")

                log("launching coroutine for OCR processing")
                scope.launch {
                    processOcr(
                        bitmap = croppedBitmap,
                        targetText = targetText,
                        isRegex = isRegex,
                        isExactMatch = isExactMatch,
                        isIgnoreCase = isIgnoreCase,
                        restrictRegion = restrictRegion,
                        regionLeft = regionLeft,
                        regionTop = regionTop,
                        regionRight = regionRight,
                        regionBottom = regionBottom,
                        regionSpec = regionSpec,
                        regionMode = regionMode,
                        regionUsePercent = regionUsePercent,
                        regionVarLeft = regionVarLeft,
                        regionVarTop = regionVarTop,
                        regionVarRight = regionVarRight,
                        regionVarBottom = regionVarBottom,
                        regionVarX = regionVarX,
                        regionVarY = regionVarY,
                        regionVarW = regionVarW,
                        regionVarH = regionVarH,
                        fireIntent = fireIntent,
                        pendingIntent = pendingIntent,
                        isAppTest = isAppTest
                    )
                    log("OCR processing done, calling stopSelf")
                    stopSelf()
                }
            } else {
                log("IMAGE IS NULL - screen capture failed!")
                PluginStatusManager.notifyFailed("截屏失败：未能获取屏幕图像")
                scope.launch {
                    val errBundle = OcrProcessResult.createErrorBundle("截屏失败：未能获取屏幕图像")
                    signalTaskerFinish(fireIntent, pendingIntent, false, errBundle)
                    stopSelf()
                }
            }
        }, null)
        log("onImageAvailable listener registered")
    }

    private suspend fun processOcr(
        bitmap: Bitmap,
        targetText: String,
        isRegex: Boolean,
        isExactMatch: Boolean,
        isIgnoreCase: Boolean,
        restrictRegion: Boolean,
        regionLeft: String,
        regionTop: String,
        regionRight: String,
        regionBottom: String,
        regionSpec: String,
        regionMode: String,
        regionUsePercent: Boolean,
        regionVarLeft: String,
        regionVarTop: String,
        regionVarRight: String,
        regionVarBottom: String,
        regionVarX: String,
        regionVarY: String,
        regionVarW: String,
        regionVarH: String,
        fireIntent: Intent?,
        pendingIntent: android.app.PendingIntent?,
        isAppTest: Boolean
    ) {
        log("=== processOcr (via OcrResultProcessor) ===")
        val result = OcrResultProcessor.process(
            bitmap = bitmap,
            targetText = targetText,
            isRegex = isRegex,
            isExactMatch = isExactMatch,
            isIgnoreCase = isIgnoreCase,
            restrictRegion = restrictRegion,
            regionLeft = regionLeft,
            regionTop = regionTop,
            regionRight = regionRight,
            regionBottom = regionBottom,
            regionSpec = regionSpec,
            regionMode = regionMode,
            regionUsePercent = regionUsePercent,
            regionVarLeft = regionVarLeft,
            regionVarTop = regionVarTop,
            regionVarRight = regionVarRight,
            regionVarBottom = regionVarBottom,
            regionVarX = regionVarX,
            regionVarY = regionVarY,
            regionVarW = regionVarW,
            regionVarH = regionVarH
        )
        log("OcrResultProcessor returned success=${result.success}, matchFound=${result.matchFound}, error=${result.errorMessage}")

        val durationMs = if (captureStartTime > 0) System.currentTimeMillis() - captureStartTime else 0
        if (result.success) {
            PluginStatusManager.notifySuccess(durationMs, targetText, result.matchFound)
        } else {
            PluginStatusManager.notifyFailed(result.errorMessage ?: "未知错误")
        }

        if (isAppTest) {
            log("isAppTest=true, emitting result for Demo app")
            val ocrModelResult = OCRApplication.instance.ocr?.recognize(bitmap)
            if (ocrModelResult != null) {
                OCRApplication.instance.appTestResult.emit(Pair(bitmap, ocrModelResult))
            }
        } else {
            try {
                if (!bitmap.isRecycled) bitmap.recycle()
            } catch (ignore: Exception) {}
            signalTaskerFinish(fireIntent, pendingIntent, result.success, result.toTaskerBundle())
        }
    }

    private fun signalTaskerFinish(fireIntent: Intent?, pendingIntent: android.app.PendingIntent?, success: Boolean, varsBundle: Bundle) {
        log("=== signalTaskerFinish ===")
        log("hasFireIntent=${fireIntent != null}, hasPendingIntent=${pendingIntent != null}, success=$success, varsBundleKeys=${varsBundle.keySet()}")

        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        log("resultCode=$resultCode")

        // 1. 直接通过 signalFinish 发送完成广播给 Tasker / MacroDroid (核心通道，同文件和无障碍模式)
        if (fireIntent != null) {
            val signaled = TaskerPlugin.Setting.signalFinish(this, fireIntent, resultCode, varsBundle)
            log("ScreenCaptureService direct signalFinish signaled: $signaled")
        } else {
            log("fireIntent is null, cannot direct signalFinish!")
        }

        // 2. 兜底尝试 pendingIntent
        if (pendingIntent != null) {
            val resultIntent = Intent().apply {
                putExtra(PluginResultsService.EXTRA_RESULT_CODE, resultCode)
                putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, varsBundle)
            }
            try {
                pendingIntent.send(this, 0, resultIntent)
                log("pendingIntent sent to PluginResultsService")
            } catch (e: Exception) {
                log("pendingIntent Exception: ${e.message}")
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