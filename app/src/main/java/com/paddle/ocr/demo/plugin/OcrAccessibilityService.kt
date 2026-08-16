package com.paddle.ocr.demo.plugin

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * PP-OCR 无障碍服务 (用于 Android 11+ 实现免弹窗静默截屏)
 */
class OcrAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OcrPlugin"
        private const val SUB_TAG = "Accessibility"

        @Volatile
        var instance: OcrAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean {
            return instance != null
        }

        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName = "${context.packageName}/${OcrAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServicesSetting.split(':').any {
                it.equals(expectedComponentName, ignoreCase = true) ||
                        it.contains(context.packageName, ignoreCase = true)
            }
        }

        /**
         * 协程异步静默截屏 (需 Android 11+)
         */
        suspend fun takeScreenshotSuspend(context: Context): Result<Bitmap> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return Result.failure(IllegalStateException("无障碍静默截屏需要 Android 11 (API 30) 及以上系统支持"))
            }

            val service = instance ?: return Result.failure(
                IllegalStateException("无障碍服务未开启，请先在系统设置中启用 PP-OCR 无障碍服务")
            )

            return suspendCancellableCoroutine { continuation ->
                val executor = ContextCompat.getMainExecutor(service)
                val callback = @RequiresApi(Build.VERSION_CODES.R) object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()

                            if (hwBitmap != null) {
                                // 转换为可读写的标准软件 Bitmap (ARGB_8888) 以便 OCR 引擎处理
                                val softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                hwBitmap.recycle()

                                if (softwareBitmap != null) {
                                    Log.d(TAG, "[$SUB_TAG] Screenshot captured: ${softwareBitmap.width}x${softwareBitmap.height}")
                                    continuation.resume(Result.success(softwareBitmap))
                                } else {
                                    continuation.resume(Result.failure(IllegalStateException("无法将硬件缓冲转换为软件 Bitmap")))
                                }
                            } else {
                                continuation.resume(Result.failure(IllegalStateException("wrapHardwareBuffer 返回空图像")))
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "[$SUB_TAG] Exception during screenshot bitmap conversion: ${t.message}", t)
                            continuation.resume(Result.failure(t))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val reason = when (errorCode) {
                            1 -> "系统内部错误 (INTERNAL_ERROR)"
                            2 -> "无障碍服务未就绪 (NO_ACCESSIBILITY_ACCESS)"
                            3 -> "截屏频率过高 (INTERVAL_TIME_SHORT)"
                            4 -> "无效的显示屏幕 (INVALID_DISPLAY)"
                            5 -> "无效的窗口 (INVALID_WINDOW)"
                            else -> "系统错误代码: $errorCode"
                        }
                        Log.e(TAG, "[$SUB_TAG] takeScreenshot onFailure: $reason")
                        continuation.resume(Result.failure(IllegalStateException("无障碍截屏失败: $reason")))
                    }
                }

                try {
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
                } catch (e: Exception) {
                    Log.e(TAG, "[$SUB_TAG] takeScreenshot call failed: ${e.message}", e)
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "[$SUB_TAG] onServiceConnected: Accessibility Service is ACTIVE")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 静默截屏无需监听事件流
    }

    override fun onInterrupt() {
        Log.w(TAG, "[$SUB_TAG] onInterrupt")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.d(TAG, "[$SUB_TAG] onUnbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "[$SUB_TAG] onDestroy")
        super.onDestroy()
    }
}
