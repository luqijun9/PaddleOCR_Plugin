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

        // 6. 启动 ScreenCaptureActivity
        log("--- starting ScreenCaptureActivity ---")
        val captureIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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