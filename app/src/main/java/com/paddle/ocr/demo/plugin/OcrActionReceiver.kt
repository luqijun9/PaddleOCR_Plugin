package com.paddle.ocr.demo.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class OcrActionReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "OcrActionReceiver"
        const val RESULT_CODE_PENDING = 17
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerPluginConstants.ACTION_FIRE_SETTING) return
        
        Log.d(TAG, "Received FIRE_SETTING from Tasker")

        val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
        val targetText = bundle?.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT) ?: ""
        val isRegex = bundle?.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX) ?: false

        // Tell Tasker to wait for an asynchronous result
        if (isOrderedBroadcast) {
            resultCode = RESULT_CODE_PENDING
        }

        // Start the ScreenCaptureActivity to request MediaProjection
        // The activity will then start the service, capture screen, run OCR, and signal Tasker finish
        val captureIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("targetText", targetText)
            putExtra("isRegex", isRegex)
            // Pass the original intent so the service knows how to signal finish
            putExtra("fireIntent", intent)
        }
        context.startActivity(captureIntent)
    }
}
