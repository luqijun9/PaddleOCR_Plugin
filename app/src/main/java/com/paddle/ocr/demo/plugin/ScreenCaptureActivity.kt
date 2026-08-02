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
        const val TAG = "ScreenCaptureActivity"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var targetText: String = ""
    private var isRegex: Boolean = false
    private var fireIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make activity transparent (needs theme in Manifest)
        
        targetText = intent.getStringExtra("targetText") ?: ""
        isRegex = intent.getBooleanExtra("isRegex", false)
        fireIntent = intent.getParcelableExtra("fireIntent")

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "Screen capture permission granted.")
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                    putExtra("targetText", targetText)
                    putExtra("isRegex", isRegex)
                    putExtra("fireIntent", fireIntent)
                }
                // Start foreground service
                startForegroundService(serviceIntent)
            } else {
                Log.e(TAG, "Screen capture permission denied.")
                // Should signal Tasker error
                signalTaskerFinish(false)
            }
        }
        finish()
    }
    
    private fun signalTaskerFinish(success: Boolean) {
        if (fireIntent == null) return
        val taskerActionId = fireIntent?.getByteArrayExtra("net.dinglisch.android.tasker.extras.PASS_THROUGH_MESSAGE_ID")
        
        val resultIntent = Intent("net.dinglisch.android.tasker.ACTION_EDIT_EVENT_SIGNAL_FINISH")
        resultIntent.putExtra("net.dinglisch.android.tasker.extras.PASS_THROUGH_MESSAGE_ID", taskerActionId)
        
        // Signal error to Tasker if denied
        if (!success) {
            resultIntent.putExtra("net.dinglisch.android.tasker.extras.SIGNAL_STATE", 2) // TaskerPlugin.Setting.RESULT_CODE_FAILED
        } else {
            resultIntent.putExtra("net.dinglisch.android.tasker.extras.SIGNAL_STATE", 1) // OK
        }
        sendBroadcast(resultIntent)
    }
}
