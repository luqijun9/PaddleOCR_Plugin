package com.paddle.ocr.demo.plugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.paddle.ocr.demo.MainActivity
import com.paddle.ocr.demo.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 插件常驻通知与识别状态管理器
 */
object PluginStatusManager {

    const val CHANNEL_ID = "ocr_plugin_status_channel"
    const val CHANNEL_NAME = "OCR 插件运行状态"
    const val NOTIFICATION_ID = 2001

    private var appContext: Context? = null
    private var isChannelCreated = false

    fun init(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (isChannelCreated) return
        val ctx = appContext ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = ctx.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        isChannelCreated = true
    }

    fun buildNotification(title: String, content: String): Notification {
        val ctx = appContext ?: throw IllegalStateException("PluginStatusManager not initialized!")
        createNotificationChannel()

        val openAppIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(ctx, 0, openAppIntent, flags)

        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * 待命状态
     */
    fun notifyIdle() {
        val ctx = appContext ?: return
        updateNotification(
            ctx.getString(R.string.notif_idle_title),
            ctx.getString(R.string.notif_idle_desc)
        )
    }

    /**
     * 识别进行中状态
     */
    fun notifyRunning(modeInfo: String? = null) {
        val ctx = appContext ?: return
        val title = ctx.getString(R.string.notif_running_title)
        val content = modeInfo ?: ctx.getString(R.string.notif_running_screen)
        updateNotification(title, content)
    }

    /**
     * 识别成功状态 (例如: 耗时 230ms · 找到目标 [验证码])
     */
    fun notifySuccess(durationMs: Long, detail: String) {
        val ctx = appContext ?: return
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val title = ctx.getString(R.string.notif_success_title, timeStr)
        val content = if (detail.isNotEmpty()) {
            ctx.getString(R.string.notif_success_target, durationMs, detail)
        } else {
            ctx.getString(R.string.notif_success_all, durationMs)
        }
        updateNotification(title, content)
    }

    /**
     * 识别失败状态
     */
    fun notifyFailed(errorMessage: String) {
        val ctx = appContext ?: return
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val title = ctx.getString(R.string.notif_failed_title, timeStr)
        val content = errorMessage.ifEmpty { "Error" }
        updateNotification(title, content)
    }

    private fun updateNotification(title: String, content: String) {
        val ctx = appContext ?: return
        try {
            val notification = buildNotification(title, content)
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.w("OcrPlugin", "updateNotification failed: ${e.message}")
        }
    }
}
