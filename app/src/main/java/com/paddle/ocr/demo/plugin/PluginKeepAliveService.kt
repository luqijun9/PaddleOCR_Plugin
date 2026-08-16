package com.paddle.ocr.demo.plugin

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 插件常驻前台保活服务 (维持 oom_adj 优先级，保持模型在内存中热启动)
 */
class PluginKeepAliveService : Service() {

    companion object {
        private const val TAG = "OcrPlugin"
        private const val SUB_TAG = "KeepAlive"

        fun start(context: Context) {
            try {
                val intent = Intent(context, PluginKeepAliveService::class.java)
                ContextCompat.startForegroundService(context, intent)
                Log.d(TAG, "[$SUB_TAG] startForegroundService called")
            } catch (e: Exception) {
                Log.e(TAG, "[$SUB_TAG] Failed to start PluginKeepAliveService: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[$SUB_TAG] onCreate")
        PluginStatusManager.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[$SUB_TAG] onStartCommand")
        PluginStatusManager.init(applicationContext)

        val notification = PluginStatusManager.buildNotification(
            "PP-OCR 插件运行中",
            "模型就绪，等待触发"
        )
        startForeground(PluginStatusManager.NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "[$SUB_TAG] onDestroy")
        super.onDestroy()
    }
}
