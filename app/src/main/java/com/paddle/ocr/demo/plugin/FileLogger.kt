package com.paddle.ocr.demo.plugin

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    fun log(context: Context, tag: String, message: String) {
        try {
            val file = File(context.getExternalFilesDir(null), "ocr_log.txt")
            val writer = FileWriter(file, true)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            writer.append("$time [$tag] $message\n")
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
