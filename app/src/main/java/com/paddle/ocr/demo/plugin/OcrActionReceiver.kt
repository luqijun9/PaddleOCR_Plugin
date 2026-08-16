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
            val pendingResult = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val bitmap = ImageFileLoader.loadBitmap(context, imagePath)
                    val result = if (bitmap != null) {
                        OcrResultProcessor.process(bitmap, targetText, isRegex)
                    } else {
                        OcrProcessResult(
                            success = false,
                            errorMessage = "无法加载或解码指定路径的图片: $imagePath"
                        )
                    }

                    val finalResultCode = if (result.success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
                    val resultIntent = Intent().apply {
                        putExtra(PluginResultsService.EXTRA_RESULT_CODE, finalResultCode)
                        putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, result.toTaskerBundle())
                    }
                    pendingIntent.send(context, 0, resultIntent)
                    log("File mode OCR finished. Success=${result.success}, matchFound=${result.matchFound}")
                } catch (e: Throwable) {
                    log("File mode OCR exception: ${e.message}")
                    val errBundle = Bundle().apply {
                        putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, "图片识别异常: ${e.message ?: "未知错误"}")
                    }
                    val errIntent = Intent().apply {
                        putExtra(PluginResultsService.EXTRA_RESULT_CODE, TaskerPlugin.Setting.RESULT_CODE_FAILED)
                        putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, errBundle)
                    }
                    try {
                        pendingIntent.send(context, 0, errIntent)
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