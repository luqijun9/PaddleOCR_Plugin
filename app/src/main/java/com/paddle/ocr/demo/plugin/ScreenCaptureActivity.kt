package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

class ScreenCaptureActivity : Activity() {

    companion object {
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        const val TAG = "OcrPlugin"
        const val SUB_TAG = "Activity"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var targetText: String = ""
    private var isRegex: Boolean = false
    private var isExactMatch: Boolean = false
    private var isIgnoreCase: Boolean = true
    private var pendingIntent: PendingIntent? = null
    private var isAppTest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        log("=== onCreate ===")
        log("savedInstanceState=${savedInstanceState != null}")
        log("callingIntent action=${intent.action}")
        log("callingIntent extras=${intent.extras?.keySet()}")

        targetText = intent.getStringExtra("targetText") ?: ""
        isRegex = intent.getBooleanExtra("isRegex", false)
        isExactMatch = intent.getBooleanExtra("isExactMatch", false)
        isIgnoreCase = intent.getBooleanExtra("isIgnoreCase", true)
        pendingIntent = intent.getParcelableExtra(TaskerPluginConstants.EXTRA_PENDING_INTENT)
        isAppTest = intent.getBooleanExtra("isAppTest", false)
        log("targetText=$targetText, isRegex=$isRegex, isExactMatch=$isExactMatch, isIgnoreCase=$isIgnoreCase, isAppTest=$isAppTest")
        log("pendingIntent=${pendingIntent != null}")

        // 请求 MediaProjection 权限
        log("requesting MediaProjection permission...")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
        log("permission dialog shown, waiting for user response")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        log("=== onActivityResult ===")
        log("requestCode=$requestCode, resultCode=$resultCode, data=${data != null}")

        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                log("SCREEN CAPTURE PERMISSION GRANTED")
                log("data action=${data.action}")
                log("data extras=${data.extras?.keySet()}")

                log("--- starting ScreenCaptureService ---")
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                    putExtra("targetText", targetText)
                    putExtra("isRegex", isRegex)
                    putExtra("isExactMatch", isExactMatch)
                    putExtra("isIgnoreCase", isIgnoreCase)
                    putExtra(TaskerPluginConstants.EXTRA_PENDING_INTENT, pendingIntent)
                    putExtra("isAppTest", isAppTest)
                    putExtra(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, intent.getBooleanExtra(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, false))
                    putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, intent.getStringExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT) ?: "0.0")
                    putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, intent.getStringExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP) ?: "0.0")
                    putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, intent.getStringExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT) ?: "1.0")
                    putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, intent.getStringExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM) ?: "1.0")
                }
                log("serviceIntent created, calling startForegroundService")
                startForegroundService(serviceIntent)
                log("startForegroundService called")
            } else {
                log("SCREEN CAPTURE PERMISSION DENIED")
                log("resultCode=$resultCode, data=${data != null}")
                signalTaskerFinish(false)
            }
        }
        log("finishing activity")
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("=== onDestroy ===")
    }

    private fun signalTaskerFinish(success: Boolean) {
        log("=== signalTaskerFinish (Activity) ===")
        if (pendingIntent == null) {
            log("pendingIntent is null, cannot signal!")
            return
        }

        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        log("calling pendingIntent.send() with resultCode=$resultCode")

        val resultIntent = Intent(this, PluginResultsService::class.java).apply {
            putExtra(PluginResultsService.EXTRA_PLUGIN_RESULT_BUNDLE, Bundle())
            putExtra(PluginResultsService.EXTRA_PLUGIN_RESULT_CODE, resultCode)
        }
        try {
            pendingIntent!!.send(this, 0, resultIntent)
            log("pendingIntent.send() completed")
        } catch (e: PendingIntent.CanceledException) {
            log("PendingIntent.send() failed: ${e.message}")
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}