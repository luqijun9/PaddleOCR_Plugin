package com.paddle.ocr.demo.plugin

import android.app.IntentService
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * PluginResultsService 接收下游组件通过 PendingIntent 发回的结果，
 * 从中提取原始 fireIntent，然后调用 signalFinish 回传给 Tasker 宿主。
 *
 * 参考 Termux:Tasker 的 PluginResultsService 模式实现引用传递。
 */
class PluginResultsService : IntentService("PluginResultsService") {

    companion object {
        const val TAG = "OcrPlugin"
        const val SUB_TAG = "ResultsService"

        /** Intent extra 键，存放原始 fireIntent */
        const val EXTRA_ORIGINAL_INTENT = "originalIntent"

        /** Intent extra 键，存放结果 Bundle */
        const val EXTRA_PLUGIN_RESULT_BUNDLE = "pluginResultBundle"

        /** Intent extra 键，存放结果 code（RESULT_CODE_OK / RESULT_CODE_FAILED） */
        const val EXTRA_PLUGIN_RESULT_CODE = "pluginResultCode"
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            log("Ignoring null intent")
            return
        }

        log("=== onHandleIntent ===")

        // 1. 提取原始 fireIntent
        val originalIntent: Intent? = intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT)
        if (originalIntent == null) {
            log("ERROR: originalIntent is null!")
            return
        }
        log("originalIntent action=${originalIntent.action}")

        // 2. 提取结果 bundle
        val resultBundle: Bundle? = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE)
        if (resultBundle == null) {
            log("ERROR: resultBundle is null!")
            return
        }
        log("resultBundle keys=${resultBundle.keySet()}")

        // 3. 提取结果 code
        val resultCode = intent.getIntExtra(EXTRA_PLUGIN_RESULT_CODE, TaskerPlugin.Setting.RESULT_CODE_OK)
        log("resultCode=$resultCode")

        // 4. 通过原始 fireIntent 调用 signalFinish 回传给 Tasker
        val signaled = TaskerPlugin.Setting.signalFinish(this, originalIntent, resultCode, resultBundle)
        log("signalFinish signaled=$signaled")
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}
