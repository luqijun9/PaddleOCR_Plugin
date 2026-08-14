package com.paddle.ocr.demo.plugin

import android.app.IntentService
import android.content.Intent
import android.os.Bundle
import android.util.Log

class PluginResultsService : IntentService("PluginResultsService") {

    companion object {
        private const val TAG = "OcrPlugin"
        private const val SUB_TAG = "ResultsService"

        const val EXTRA_ORIGINAL_INTENT = "originalIntent"
        const val EXTRA_RESULT_BUNDLE = "resultBundle"
        const val EXTRA_RESULT_CODE = "resultCode"
    }

    override fun onHandleIntent(intent: Intent?) {
        log("=== onHandleIntent ===")
        if (intent == null) {
            log("intent is null, returning")
            return
        }

        val originalIntent = intent.getParcelableExtra<Intent>(EXTRA_ORIGINAL_INTENT)
        if (originalIntent == null) {
            log("originalIntent is missing, cannot signal finish")
            return
        }

        val resultBundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: Bundle()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, TaskerPlugin.Setting.RESULT_CODE_OK)

        log("signaling finish with resultCode=$resultCode, vars=${resultBundle.keySet()}")
        val signaled = TaskerPlugin.Setting.signalFinish(this, originalIntent, resultCode, resultBundle)
        log("signalFinish returned: $signaled")
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}
