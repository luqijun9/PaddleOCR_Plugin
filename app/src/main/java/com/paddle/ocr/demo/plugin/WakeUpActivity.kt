package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * 极速 1 像素透明唤醒 Activity
 * 用于在应用被系统彻底强行停止（Force Stop）后，由 Tasker / MacroDroid / 快捷方式无感拉起进程。
 * 在 onCreate 阶段拉起前台保活服务并预热 OCR 模型，耗时 < 10ms 即刻 finish() 自毁，屏幕上肉眼完全不可见。
 */
class WakeUpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("OcrPlugin", "WakeUpActivity invoked - waking up plugin process silently")
            PluginKeepAliveService.start(this)
        } catch (e: Exception) {
            Log.e("OcrPlugin", "Failed to start keep alive service in WakeUpActivity: ${e.message}")
        }
        finish()
    }
}
