package com.paddle.ocr.demo.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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

        val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
        log("bundle=$bundle")
        if (bundle != null) {
            log("bundle keys: ${bundle.keySet()}")
            val targetText = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, "")
            val isRegex = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
            val captureMode = bundle.getInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, TaskerPluginConstants.MODE_MEDIA_PROJECTION)
            val filePath = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, "")

            // IMPORTANT: For asynchronous Tasker plugins, we MUST return RESULT_CODE_PENDING (3)
            // in the BroadcastReceiver's resultCode, so Tasker knows to wait for the completion intent.
            if (isOrderedBroadcast) {
                resultCode = TaskerPlugin.Setting.RESULT_CODE_PENDING
            }

            when (captureMode) {
                TaskerPluginConstants.MODE_ACCESSIBILITY -> {
                    val accService = OcrAccessibilityService.instance
                    if (accService != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            accService.captureAndRecognize(intent, targetText, isRegex)
                        } else {
                            signalError(context, intent, "无障碍截图仅支持 Android 11+")
                        }
                    } else {
                        signalError(context, intent, "未开启无障碍服务")
                    }
                }
                TaskerPluginConstants.MODE_FILE_PATH -> {
                    if (filePath.isEmpty()) {
                        signalError(context, intent, "文件路径为空")
                        return
                    }
                    val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                        putExtra("fireIntent", intent)
                        putExtra("targetText", targetText)
                        putExtra("isRegex", isRegex)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, filePath)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                else -> {
                    // Default Mode: MediaProjection
                    val activityIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("fireIntent", intent)
                        putExtra("targetText", targetText)
                        putExtra("isRegex", isRegex)
                    }
                    context.startActivity(activityIntent)
                }
            }
        }
    }

    private fun signalError(context: Context, fireIntent: Intent, errorMessage: String) {
        val varsBundle = android.os.Bundle().apply {
            putString("%ocr_error", errorMessage)
            putString("%ocr_full_text", "")
            putString("%ocr_json", "")
            putString("%match_found", "")
            putString("%match_center_x", "")
            putString("%match_center_y", "")
        }
        TaskerPlugin.Setting.signalFinish(context, fireIntent, TaskerPlugin.Setting.RESULT_CODE_FAILED, varsBundle)
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}