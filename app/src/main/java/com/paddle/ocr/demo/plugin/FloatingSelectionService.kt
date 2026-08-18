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
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.paddle.ocr.demo.R

class FloatingSelectionService : Service() {

    companion object {
        const val TAG = "FloatingSelectionService"
        var captureBitmap: Bitmap? = null
        // 由 ActionEditActivity 启动 Service 前设置，截图就绪后在主线程回调
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
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "FloatingSelectionChannel")
                .setContentTitle(getString(R.string.floating_service_notification_title))
                .setContentText(getString(R.string.floating_service_notification_desc))
                .setSmallIcon(android.R.drawable.ic_menu_crop)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.floating_service_notification_title))
                .setContentText(getString(R.string.floating_service_notification_desc))
                .setSmallIcon(android.R.drawable.ic_menu_crop)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(102, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(102, notification)
        }
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        // 现代化圆角胶囊悬浮容器
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(18f), dp(10f))

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24f).toFloat()
                setColor(android.graphics.Color.parseColor("#EE1E293B")) // 深色科技灰
                setStroke(dp(1.5f), android.graphics.Color.parseColor("#38BDF8")) // 科技天蓝高亮边框
            }
            background = shape
            elevation = dp(8f).toFloat()
        }

        // 截图图标
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setColorFilter(android.graphics.Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                marginEnd = dp(8f)
            }
        }
        container.addView(iconView)

        // 文本提示
        val textView = TextView(this).apply {
            text = getString(R.string.floating_service_capture_btn)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(textView)

        floatingView = container

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24f)
            y = dp(160f)
        }

        // 自由拖拽与点击判定
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        val touchSlop = dp(6f)

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    try {
                        windowManager?.updateViewLayout(container, layoutParams)
                    } catch (e: Exception) {
                        Log.w(TAG, "updateViewLayout notice: ${e.message}")
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dx < touchSlop && dy < touchSlop) {
                        captureAndOpenDrawActivity()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, layoutParams)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.floating_service_toast_hint),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun captureAndOpenDrawActivity() {
        floatingView?.visibility = View.GONE

        try {
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

                    val cb = screenshotCallback
                    screenshotCallback = null
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        cb?.invoke()
                    }

                    stopSelf()
                } else {
                    Log.e(TAG, "Acquired image was null in FloatingSelectionService")
                    stopSelf()
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error during captureAndOpenDrawActivity: ${e.message}", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    this,
                    "截屏失败: ${e.message ?: "未知异常"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "FloatingSelectionChannel",
                "OCR Region Selection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (ignore: Exception) {}
        }
        mediaProjection?.stop()
    }
}
