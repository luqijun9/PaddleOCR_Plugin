package com.paddle.ocr.demo.plugin

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

class OcrActionReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "OcrPlugin"
        const val SUB_TAG = "Receiver"
        const val RESULT_CODE_PENDING = android.app.Activity.RESULT_FIRST_USER + 2

        /** 用于生成唯一 PendingIntent requestCode 的原子计数器 */
        private val nextRequestCode = AtomicInteger(10000)
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
            val isExactMatch = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, false)
            val isIgnoreCase = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, true)
            val captureMode = bundle.getInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, TaskerPluginConstants.MODE_MEDIA_PROJECTION)
            val filePath = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, "")
            val restrictRegion = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, false)
            val regionLeft = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, "0.0")
            val regionTop = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, "0.0")
            val regionRight = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, "1.0")
            val regionBottom = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, "1.0")

            // IMPORTANT: For asynchronous Tasker plugins, we MUST return RESULT_CODE_PENDING (3)
            // in the BroadcastReceiver's resultCode, so Tasker knows to wait for the completion intent.
            if (isOrderedBroadcast) {
                resultCode = TaskerPlugin.Setting.RESULT_CODE_PENDING

                // 注册可替换变量名到 resultExtras（Termux规范）
                // 让 Tasker 知道后续回传时哪些 bundle key 需要做变量替换
                val resultExtras = getResultExtras(true)
                if (resultExtras != null) {
                    val replaceKeys = arrayOf(
                        TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT,
                        TaskerPluginConstants.BUNDLE_KEY_FILE_PATH,
                        TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT,
                        TaskerPluginConstants.BUNDLE_KEY_REGION_TOP,
                        TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT,
                        TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM
                    )
                    TaskerPlugin.Setting.setVariableReplaceKeys(resultExtras, replaceKeys)
                }
            }

            // ============================================================
            // 引用传递（Reference-passing）模式：
            // 不再将 fireIntent 通过 putExtra 序列化传递给下游组件，
            // 而是将 fireIntent 保管在 PluginResultsService 的 Intent 中，
            // 用 PendingIntent 封装后放入下游 Intent 的 EXTRA_PENDING_INTENT 键。
            //
            // 下游组件处理完毕后，通过该 PendingIntent 将结果发回
            // PluginResultsService，由它提取原始 fireIntent 调用 signalFinish。
            // ============================================================
            val pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }

            when (captureMode) {
                TaskerPluginConstants.MODE_ACCESSIBILITY -> {
                    // Accessibility 模式：fireIntent → PendingIntent → 传给 Service 方法参数
                    val resultsIntent = Intent(context, PluginResultsService::class.java)
                    resultsIntent.putExtra(PluginResultsService.EXTRA_ORIGINAL_INTENT, intent)
                    val pendingIntent = PendingIntent.getService(
                        context, nextRequestCode.incrementAndGet(),
                        resultsIntent, pendingIntentFlags
                    )

                    val accService = OcrAccessibilityService.instance
                    if (accService != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            accService.captureAndRecognize(
                                pendingIntent, targetText, isRegex, isExactMatch, isIgnoreCase,
                                restrictRegion, regionLeft, regionTop, regionRight, regionBottom
                            )
                        } else {
                            signalError(context, intent, "无障碍截图仅支持 Android 11+")
                        }
                    } else {
                        signalError(context, intent, "未开启无障碍服务")
                    }
                }
                TaskerPluginConstants.MODE_FILE_PATH -> {
                    // File Path 模式：fireIntent → PendingIntent → 放入 Service Intent
                    if (filePath.isEmpty()) {
                        signalError(context, intent, "文件路径为空")
                        return
                    }

                    val resultsIntent = Intent(context, PluginResultsService::class.java)
                    resultsIntent.putExtra(PluginResultsService.EXTRA_ORIGINAL_INTENT, intent)
                    val pendingIntent = PendingIntent.getService(
                        context, nextRequestCode.incrementAndGet(),
                        resultsIntent, pendingIntentFlags
                    )

                    val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                        putExtra(TaskerPluginConstants.EXTRA_PENDING_INTENT, pendingIntent)
                        putExtra("targetText", targetText)
                        putExtra("isRegex", isRegex)
                        putExtra("isExactMatch", isExactMatch)
                        putExtra("isIgnoreCase", isIgnoreCase)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, filePath)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, restrictRegion)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, regionLeft)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, regionTop)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, regionRight)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, regionBottom)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                else -> {
                    // Default Mode: MediaProjection
                    // fireIntent → PendingIntent → 放入 Activity Intent
                    val resultsIntent = Intent(context, PluginResultsService::class.java)
                    resultsIntent.putExtra(PluginResultsService.EXTRA_ORIGINAL_INTENT, intent)
                    val pendingIntent = PendingIntent.getService(
                        context, nextRequestCode.incrementAndGet(),
                        resultsIntent, pendingIntentFlags
                    )

                    val activityIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(TaskerPluginConstants.EXTRA_PENDING_INTENT, pendingIntent)
                        putExtra("targetText", targetText)
                        putExtra("isRegex", isRegex)
                        putExtra("isExactMatch", isExactMatch)
                        putExtra("isIgnoreCase", isIgnoreCase)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, restrictRegion)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, regionLeft)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, regionTop)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, regionRight)
                        putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, regionBottom)
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

        // 检查宿主是否支持变量返回（Termux规范）
        val hostExtras = fireIntent.extras
        if (hostExtras != null && TaskerPlugin.Setting.hostSupportsVariableReturn(hostExtras)) {
            // 通过 addVariableBundle 写入变量到 getResultExtras(true)
            val resultExtras = getResultExtras(true)
            if (resultExtras != null) {
                TaskerPlugin.addVariableBundle(resultExtras, varsBundle)
            }
            // 注册可替换变量名
            val replaceKeys = arrayOf(
                TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT,
                TaskerPluginConstants.BUNDLE_KEY_FILE_PATH,
                TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT,
                TaskerPluginConstants.BUNDLE_KEY_REGION_TOP,
                TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT,
                TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM
            )
            TaskerPlugin.Setting.setVariableReplaceKeys(resultExtras, replaceKeys)
            resultCode = TaskerPlugin.Setting.RESULT_CODE_FAILED
        } else {
            TaskerPlugin.Setting.signalFinish(context, fireIntent, TaskerPlugin.Setting.RESULT_CODE_FAILED, varsBundle)
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, "[$SUB_TAG] $msg")
    }
}