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
    private var restrictRegion: Boolean = false
    private var regionLeft: String = "0.0"
    private var regionTop: String = "0.0"
    private var regionRight: String = "1.0"
    private var regionBottom: String = "1.0"
    private var regionSpec: String = ""
    private var regionMode: String = TaskerPluginConstants.REGION_MODE_SINGLE
    private var regionUsePercent: Boolean = false
    private var regionVarLeft: String = ""
    private var regionVarTop: String = ""
    private var regionVarRight: String = ""
    private var regionVarBottom: String = ""
    private var regionVarX: String = ""
    private var regionVarY: String = ""
    private var regionVarW: String = ""
    private var regionVarH: String = ""
    private var pendingIntent: android.app.PendingIntent? = null
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
        restrictRegion = intent.getBooleanExtra("restrictRegion", false)
        regionLeft = intent.getStringExtra("regionLeft") ?: "0.0"
        regionTop = intent.getStringExtra("regionTop") ?: "0.0"
        regionRight = intent.getStringExtra("regionRight") ?: "1.0"
        regionBottom = intent.getStringExtra("regionBottom") ?: "1.0"
        regionSpec = intent.getStringExtra("regionSpec") ?: ""
        regionMode = intent.getStringExtra("regionMode") ?: TaskerPluginConstants.REGION_MODE_SINGLE
        regionUsePercent = intent.getBooleanExtra("regionUsePercent", false)
        regionVarLeft = intent.getStringExtra("regionVarLeft") ?: ""
        regionVarTop = intent.getStringExtra("regionVarTop") ?: ""
        regionVarRight = intent.getStringExtra("regionVarRight") ?: ""
        regionVarBottom = intent.getStringExtra("regionVarBottom") ?: ""
        regionVarX = intent.getStringExtra("regionVarX") ?: ""
        regionVarY = intent.getStringExtra("regionVarY") ?: ""
        regionVarW = intent.getStringExtra("regionVarW") ?: ""
        regionVarH = intent.getStringExtra("regionVarH") ?: ""
        pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("pendingIntent", android.app.PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("pendingIntent")
        }
        fireIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("fireIntent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("fireIntent")
        }
        isAppTest = intent.getBooleanExtra("isAppTest", false)
        log("targetText=$targetText, isRegex=$isRegex, isExactMatch=$isExactMatch, isIgnoreCase=$isIgnoreCase, restrictRegion=$restrictRegion, isAppTest=$isAppTest, hasFireIntent=${fireIntent != null}")

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
                    putExtra("isExactMatch", isExactMatch)
                    putExtra("isIgnoreCase", isIgnoreCase)
                    putExtra("restrictRegion", restrictRegion)
                    putExtra("regionLeft", regionLeft)
                    putExtra("regionTop", regionTop)
                    putExtra("regionRight", regionRight)
                    putExtra("regionBottom", regionBottom)
                    putExtra("regionSpec", regionSpec)
                    putExtra("regionMode", regionMode)
                    putExtra("regionUsePercent", regionUsePercent)
                    putExtra("regionVarLeft", regionVarLeft)
                    putExtra("regionVarTop", regionVarTop)
                    putExtra("regionVarRight", regionVarRight)
                    putExtra("regionVarBottom", regionVarBottom)
                    putExtra("regionVarX", regionVarX)
                    putExtra("regionVarY", regionVarY)
                    putExtra("regionVarW", regionVarW)
                    putExtra("regionVarH", regionVarH)
                    putExtra("pendingIntent", pendingIntent)
                    putExtra("fireIntent", fireIntent)
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
        val resultCode = if (success) TaskerPlugin.Setting.RESULT_CODE_OK else TaskerPlugin.Setting.RESULT_CODE_FAILED
        val varsBundle = if (success) {
            Bundle().apply {
                putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, "")
                putString("%errmsg", "")
                putString("errmsg", "")
            }
        } else {
            OcrProcessResult.createErrorBundle(errorMessage ?: "用户取消或拒绝了录屏授权")
        }

        // 1. 直接通过 TaskerPlugin.Setting.signalFinish 发送完成广播给 Tasker / MacroDroid
        if (fireIntent != null) {
            val signaled = TaskerPlugin.Setting.signalFinish(this, fireIntent!!, resultCode, varsBundle)
            log("Activity direct signalFinish signaled: $signaled")
        } else {
            log("fireIntent is null in Activity, cannot direct signalFinish!")
        }

        // 2. 兜底尝试 pendingIntent
        if (pendingIntent != null) {
            log("sending pendingIntent with resultCode=$resultCode, vars=${varsBundle.keySet()}")
            val resultIntent = Intent().apply {
                putExtra(PluginResultsService.EXTRA_RESULT_CODE, resultCode)
                putExtra(PluginResultsService.EXTRA_RESULT_BUNDLE, varsBundle)
            }
            try {
                pendingIntent!!.send(this, 0, resultIntent)
                log("pendingIntent sent successfully")
            } catch (e: Exception) {
                log("pendingIntent Exception: ${e.message}")
            }
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}