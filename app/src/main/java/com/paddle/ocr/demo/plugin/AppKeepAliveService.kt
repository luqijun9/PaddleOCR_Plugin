package com.paddle.ocr.demo.plugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.paddle.ocr.demo.R

/**
 * 前台保活服务：只要 App 的 Activity 在前台，此服务就运行，
 * 确保 App 进程不会被系统轻易杀死。
 *
 * 设计原则：
 * - 轻量无逻辑：只负责保活，不处理任何业务
 * - 生命周期绑定：由 Activity 的 onCreate/onDestroy 控制 start/stop
 * - 通知低优先级：IMPORTANCE_MIN 避免打扰用户
 * - Android 15 兼容：使用正确的 ServiceInfo.FOREGROUND_SERVICE_TYPE_*
 */
class AppKeepAliveService : Service() {

    companion object {
        const val TAG = "AppKeepAlive"
        const val NOTIFICATION_ID = 200
        const val CHANNEL_ID = "AppKeepAliveChannel"

        /**
         * 启动保活前台服务（幂等）。
         */
        fun start(context: Context) {
            val intent = Intent(context, AppKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止保活前台服务。
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, AppKeepAliveService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== onCreate ===")
        createNotificationChannel()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== onStartCommand ===")
        // 如果前台通知因某种原因被移除，此可以确保重新显示
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy ===")
    }

    /**
     * 创建前台通知并 startForeground。
     * 兼容 Android 15（API 35）的 ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE。
     */
    private fun startForegroundNotification() {
        val notification = buildNotification()

        @Suppress("DEPRECATION")
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 使用 SPECIAL_USE 类型，适用于不需要特定类型的保活场景
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ 需要声明 foregroundServiceType
                // 但由于我们没有特定功能，使用空类型 + manifest 声明 specialUse
                0
            } else {
                0
            }
        } else {
            0
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 14+ (API 34+): startForeground 需要第二个参数指定 foregroundServiceType
                @Suppress("NewApi")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13: startForeground 传 ServiceInfo
                @Suppress("NewApi")
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                // Android 8-9
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "startForeground succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = CHANNEL_ID
        // Android 13+ 如果用户拒绝了通知权限，这里不会崩溃，只是通知不显示
        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_keep_alive_notification_title))
            .setContentText(getString(R.string.app_keep_alive_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_keep_alive_channel_name),
            NotificationManager.IMPORTANCE_MIN  // 最低优先级，不发出声音/震动
        ).apply {
            description = getString(R.string.app_keep_alive_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
        Log.d(TAG, "notification channel created")
    }
}
