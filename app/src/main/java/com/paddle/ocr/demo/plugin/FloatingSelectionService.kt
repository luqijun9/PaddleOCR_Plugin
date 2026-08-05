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
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button

class FloatingSelectionService : Service() {

    companion object {
        const val TAG = "FloatingSelectionService"
        var captureBitmap: Bitmap? = null
        // Set by ActionEditActivity before starting the service.
        // Invoked on main thread when screenshot is ready.
        var screenshotCallback: (() -> Unit)? = null
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var resultCode: Int = 0
    private var resultData: Intent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, "FloatingSelectionChannel")
            .setContentTitle("OCR Region Selection")
            .setContentText("Tap the floating button to capture screen")
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .build()
        startForeground(102, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InflateParams")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        resultCode = intent.getIntExtra("resultCode", -1)
        resultData = intent.getParcelableExtra("data")
        
        if (resultCode == android.app.Activity.RESULT_OK && resultData != null) {
            showFloatingWindow()
        } else {
            Log.e(TAG, "No MediaProjection data. ResultCode: $resultCode")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        floatingView = Button(this).apply {
            text = getString(com.paddle.ocr.demo.R.string.floating_service_capture_btn)
            textSize = 20f
            setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                captureAndOpenDrawActivity()
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager?.addView(floatingView, layoutParams)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, getString(com.paddle.ocr.demo.R.string.floating_service_toast_hint), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun captureAndOpenDrawActivity() {
        floatingView?.visibility = View.GONE

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData!!)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
            }
        }, null)

        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
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
                val croppedBitmap = if (rowPadding == 0) {
                    bitmap
                } else {
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    bitmap.recycle()
                    cropped
                }
                image.close()

                captureBitmap = croppedBitmap

                virtualDisplay?.release()
                mediaProjection?.stop()

                // Notify ActionEditActivity via callback instead of launching Activity
                // directly from a Service (which is unreliable across different task stacks).
                val cb = screenshotCallback
                screenshotCallback = null
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    cb?.invoke()
                }

                stopSelf()
            }
        }, null)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "FloatingSelectionChannel",
            "OCR Region Selection",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        mediaProjection?.stop()
        // We do NOT clear captureBitmap here because we need it in RegionDrawActivity
    }
}
