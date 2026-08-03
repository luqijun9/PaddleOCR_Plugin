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
    private var isExactMatch: Boolean = false
    private var isIgnoreCase: Boolean = true
    private var fireIntent: Intent? = null
    private var isAppTest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("=== onCreate ===")
        log("savedInstanceState=${savedInstanceState != null}")
        log("callingIntent action=${intent.action}")
        log("callingIntent extras=${intent.extras?.keySet()}")

        targetText = intent.getStringExtra("targetText") ?: ""
        isRegex = intent.getBooleanExtra("isRegex", false)
        isExactMatch = intent.getBooleanExtra("isExactMatch", false)
        isIgnoreCase = intent.getBooleanExtra("isIgnoreCase", true)
        fireIntent = intent.getParcelableExtra("fireIntent")
        isAppTest = intent.getBooleanExtra("isAppTest", false)
        log("targetText=$targetText, isRegex=$isRegex, isExactMatch=$isExactMatch, isIgnoreCase=$isIgnoreCase, isAppTest=$isAppTest")

        // 检查 fireIntent 是否完整
        if (fireIntent != null) {
            log("fireIntent action=${fireIntent!!.action}")
            log("fireIntent extras=${fireIntent!!.extras?.keySet()}")
            val completionStr = fireIntent!!.getStringExtra(TaskerPluginConstants.EXTRA_PLUGIN_COMPLETION_INTENT)
            log("fireIntent hasCompletionIntent=${completionStr != null}")
            if (completionStr != null) {
                log("fireIntent completionIntentStr=$completionStr")
            }
        } else {
            log("fireIntent is NULL!")
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
                    putExtra("isExactMatch", isExactMatch)
                    putExtra("isIgnoreCase", isIgnoreCase)
                    putExtra("fireIntent", fireIntent)
                    putExtra("isAppTest", isAppTest)
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

    override fun onDestroy() {
        super.onDestroy()
        log("=== onDestroy ===")
    }

    private fun signalTaskerFinish(success: Boolean) {
        log("=== signalTaskerFinish (Activity) ===")
        if (fireIntent == null) {
            log("fireIntent is null, cannot signal!")
            return
        }

        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        log("calling signalFinish with resultCode=$resultCode")
        val signaled = TaskerPlugin.Setting.signalFinish(this, fireIntent!!, resultCode, Bundle())
        log("signalFinish returned: signaled=$signaled")
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}