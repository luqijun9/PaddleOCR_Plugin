package com.paddle.ocr.demo.plugin

import android.app.Activity
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
    private var pendingIntent: android.app.PendingIntent? = null
    private var isAppTest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("=== onCreate ===")
        log("savedInstanceState=${savedInstanceState != null}")
        log("callingIntent action=${intent.action}")
        log("callingIntent extras=${intent.extras?.keySet()}")

        targetText = intent.getStringExtra("targetText") ?: ""
        isRegex = intent.getBooleanExtra("isRegex", false)
        pendingIntent = intent.getParcelableExtra("pendingIntent")
        isAppTest = intent.getBooleanExtra("isAppTest", false)
        log("targetText=$targetText, isRegex=$isRegex, isAppTest=$isAppTest")

        if (pendingIntent == null) {
            log("pendingIntent is NULL!")
        }

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
                    putExtra("pendingIntent", pendingIntent)
                    putExtra("isAppTest", isAppTest)
                }
                log("serviceIntent created, calling startForegroundService")
                startForegroundService(serviceIntent)
                log("startForegroundService called")
            } else {
                log("SCREEN CAPTURE PERMISSION DENIED")
                log("resultCode=$resultCode, data=${data != null}")
                signalTaskerFinish(false, "用户取消或拒绝了录屏授权")
            }
        }
        log("finishing activity")
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        log("=== onDestroy ===")
    }

    private fun signalTaskerFinish(success: Boolean, errorMessage: String? = null) {
        log("=== signalTaskerFinish (Activity) ===")
        if (pendingIntent == null) {
            log("pendingIntent is null, cannot signal!")
            return
        }

        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        val varsBundle = Bundle().apply {
            if (!success && !errorMessage.isNullOrEmpty()) {
                putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, errorMessage)
            }
        }
        log("sending pendingIntent with resultCode=$resultCode, vars=${varsBundle.keySet()}")
        val resultIntent = Intent().apply {
            putExtra(PluginResultsService.EXTRA_RESULT_CODE, resultCode)
            putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, varsBundle)
        }
        try {
            pendingIntent!!.send(this, 0, resultIntent)
            log("pendingIntent sent successfully")
        } catch (e: android.app.PendingIntent.CanceledException) {
            log("pendingIntent CanceledException: ${e.message}")
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}