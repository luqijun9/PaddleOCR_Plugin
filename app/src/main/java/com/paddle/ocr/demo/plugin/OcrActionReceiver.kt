package com.paddle.ocr.demo.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OcrActionReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "OcrPlugin"
        const val SUB_TAG = "Receiver"
        const val RESULT_CODE_PENDING = android.app.Activity.RESULT_FIRST_USER + 2
    }

    override fun onReceive(context: Context, intent: Intent) {
        log("=== onReceive ===")
        log("action=${intent.action}")
        log("isOrderedBroadcast=$isOrderedBroadcast")
        log("goAsync=$isOrderedBroadcast") // goAsync available

        if (intent.action != TaskerPluginConstants.ACTION_FIRE_SETTING) {
            log("WRONG action, returning")
            return
        }

        // 1. 检查所有关键 intent extras
        log("--- intent extras ---")
        val extras = intent.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                log("  extra: $key = ${extras.get(key)}")
            }
        } else {
            log("  extras is NULL!")
        }

        // 2. 检查 COMPLETION_INTENT
        val completionIntentStr = intent.getStringExtra(TaskerPluginConstants.EXTRA_PLUGIN_COMPLETION_INTENT)
        log("hasCompletionIntent=${completionIntentStr != null}")
        if (completionIntentStr != null) {
            log("completionIntentStr=$completionIntentStr")
        }

        // 3. 检查 BUNDLE
        val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
        log("bundle=$bundle")
        if (bundle != null) {
            log("bundle keys: ${bundle.keySet()}")
            log("targetText=${bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT)}")
            log("isRegex=${bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX)}")
        }

        val targetText = bundle?.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT) ?: ""
        val isRegex = bundle?.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX) ?: false
        val imageSource = bundle?.getString(TaskerPluginConstants.BUNDLE_KEY_IMAGE_SOURCE) ?: TaskerPluginConstants.IMAGE_SOURCE_SCREEN_CAPTURE
        val imagePath = bundle?.getString(TaskerPluginConstants.BUNDLE_KEY_IMAGE_PATH) ?: ""

        log("targetText='$targetText', isRegex=$isRegex, imageSource=$imageSource, imagePath='$imagePath'")

        // 4. 设置 RESULT_CODE_PENDING
        if (isOrderedBroadcast) {
            resultCode = RESULT_CODE_PENDING
            log("set resultCode = RESULT_CODE_PENDING ($RESULT_CODE_PENDING)")
        } else {
            log("NOT ordered broadcast, cannot set pending result!")
        }

        // 5. 创建 PendingIntent 指向 PluginResultsService
        val resultsServiceIntent = Intent(context, PluginResultsService::class.java).apply {
            putExtra(PluginResultsService.EXTRA_ORIGINAL_INTENT, intent)
        }
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_MUTABLE
        } else {
            android.app.PendingIntent.FLAG_ONE_SHOT
        }
        val pendingIntent = android.app.PendingIntent.getService(context, requestCode, resultsServiceIntent, pendingIntentFlags)

        // 6. 根据模式分流执行
        if (imageSource == TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH) {
            log("--- Routing to FILE_PATH mode (goAsync + Coroutine) ---")
            val startTime = System.currentTimeMillis()
            PluginStatusManager.notifyRunning("正在识别本地图片...")
            val pendingResult = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val result = when (val loadResult = ImageFileLoader.loadBitmap(context, imagePath)) {
                        is ImageLoadResult.Success -> {
                            val res = OcrResultProcessor.process(loadResult.bitmap, targetText, isRegex)
                            try {
                                if (!loadResult.bitmap.isRecycled) {
                                    loadResult.bitmap.recycle()
                                }
                            } catch (ignore: Exception) {}
                            res
                        }
                        is ImageLoadResult.Failure -> {
                            OcrProcessResult(
                                success = false,
                                errorMessage = loadResult.errorMessage
                            )
                        }
                    }

                    val durationMs = System.currentTimeMillis() - startTime
                    if (result.success) {
                        val detail = if (result.matchFound) "找到目标 [$targetText]" else "文件识别完成"
                        PluginStatusManager.notifySuccess(durationMs, detail)
                    } else {
                        PluginStatusManager.notifyFailed(result.errorMessage ?: "未知错误")
                    }

                    val finalResultCode = if (result.success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
                    val varsBundle = result.toTaskerBundle()

                    // 1. 直接通过 signalFinish 发送完成广播给 Tasker / MacroDroid
                    val signaled = TaskerPlugin.Setting.signalFinish(context, intent, finalResultCode, varsBundle)
                    log("File mode signalFinish directly returned: $signaled")

                    // 2. 兜底尝试 pendingIntent
                    try {
                        val resultIntent = Intent().apply {
                            putExtra(PluginResultsService.EXTRA_RESULT_CODE, finalResultCode)
                            putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, varsBundle)
                        }
                        pendingIntent.send(context, 0, resultIntent)
                    } catch (e: Exception) {
                        log("pendingIntent fallback send notice: ${e.message}")
                    }

                    log("File mode OCR finished. Success=${result.success}, matchFound=${result.matchFound}")
                } catch (e: Throwable) {
                    log("File mode OCR exception: ${e.message}")
                    val errMsg = "图片识别异常: ${e.message ?: "未知错误"}"
                    PluginStatusManager.notifyFailed(errMsg)
                    val errBundle = Bundle().apply {
                        putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, errMsg)
                    }
                    try {
                        TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, errBundle)
                    } catch (ignore: Exception) {}
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // 6. 无障碍静默截屏模式
        if (imageSource == TaskerPluginConstants.IMAGE_SOURCE_ACCESSIBILITY) {
            log("--- Routing to ACCESSIBILITY mode (goAsync + Coroutine) ---")
            val startTime = System.currentTimeMillis()
            PluginStatusManager.notifyRunning("正在无障碍静默截屏...")

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                val errMsg = "无障碍静默截屏需要 Android 11 (API 30) 及以上系统支持"
                PluginStatusManager.notifyFailed(errMsg)
                val errBundle = Bundle().apply {
                    putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, errMsg)
                }
                TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, errBundle)
                return
            }

            if (!OcrAccessibilityService.isServiceRunning()) {
                val errMsg = "无障碍服务未开启，请先在系统设置中启用 PP-OCR 无障碍服务"
                PluginStatusManager.notifyFailed(errMsg)
                val errBundle = Bundle().apply {
                    putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, errMsg)
                }
                TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, errBundle)
                return
            }

            val pendingResult = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val screenshotResult = OcrAccessibilityService.takeScreenshotSuspend(context)
                    val result = if (screenshotResult.isSuccess) {
                        val bitmap = screenshotResult.getOrThrow()
                        val res = OcrResultProcessor.process(bitmap, targetText, isRegex)
                        try {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        } catch (ignore: Exception) {}
                        res
                    } else {
                        val err = screenshotResult.exceptionOrNull()?.message ?: "无障碍截屏未知失败"
                        OcrProcessResult(success = false, errorMessage = err)
                    }

                    val durationMs = System.currentTimeMillis() - startTime
                    if (result.success) {
                        val detail = if (result.matchFound) "找到目标 [$targetText]" else "全屏识别完成"
                        PluginStatusManager.notifySuccess(durationMs, detail)
                    } else {
                        PluginStatusManager.notifyFailed(result.errorMessage ?: "未知错误")
                    }

                    val finalResultCode = if (result.success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
                    val varsBundle = result.toTaskerBundle()

                    // 1. 直接通过 signalFinish 发送完成广播给 Tasker / MacroDroid
                    val signaled = TaskerPlugin.Setting.signalFinish(context, intent, finalResultCode, varsBundle)
                    log("Accessibility mode signalFinish directly returned: $signaled")

                    // 2. 兜底尝试 pendingIntent
                    try {
                        val resultIntent = Intent().apply {
                            putExtra(PluginResultsService.EXTRA_RESULT_CODE, finalResultCode)
                            putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, varsBundle)
                        }
                        pendingIntent.send(context, 0, resultIntent)
                    } catch (e: Exception) {
                        log("pendingIntent fallback send notice: ${e.message}")
                    }

                    log("Accessibility mode OCR finished. Success=${result.success}, matchFound=${result.matchFound}")
                } catch (e: Throwable) {
                    log("Accessibility mode OCR exception: ${e.message}")
                    val errMsg = "无障碍识别异常: ${e.message ?: "未知错误"}"
                    PluginStatusManager.notifyFailed(errMsg)
                    val errBundle = Bundle().apply {
                        putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, errMsg)
                    }
                    try {
                        TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, errBundle)
                    } catch (ignore: Exception) {}
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // 7. 屏幕截图模式：启动 ScreenCaptureActivity
        log("--- starting ScreenCaptureActivity for SCREEN_CAPTURE mode ---")
        val captureIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("targetText", targetText)
            putExtra("isRegex", isRegex)
            putExtra("pendingIntent", pendingIntent)
        }
        log("captureIntent created")
        context.startActivity(captureIntent)
        log("startActivity called, onReceive returning")
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
        // Also write to file when context is available
    }
}