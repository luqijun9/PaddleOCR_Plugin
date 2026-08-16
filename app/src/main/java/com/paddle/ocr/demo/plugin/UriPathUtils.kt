package com.paddle.ocr.demo.plugin

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 将各类 Content Uri (DocumentsUI, MediaStore 等) 转换为绝对物理文件路径
 */
object UriPathUtils {

    private const val TAG = "OcrPlugin"

    fun getRealPathFromUri(context: Context, uri: Uri): String {
        try {
            // 1. 如果本身就是 file://
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path ?: uri.toString()
            }

            // 2. DocumentsContract (系统文件管理器 / DocumentsUI)
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)

                // ExternalStorageProvider (内部存储 / SD卡)
                if ("com.android.externalstorage.documents" == uri.authority) {
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        val path = Environment.getExternalStorageDirectory().toString() + "/" + if (split.size > 1) split[1] else ""
                        if (File(path).exists()) return path
                    } else {
                        // SD卡路径
                        val path = "/storage/$type/" + if (split.size > 1) split[1] else ""
                        if (File(path).exists()) return path
                    }
                }
                // MediaDocumentsProvider (MediaStore 图片/视频/音频)
                else if ("com.android.providers.media.documents" == uri.authority) {
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    if ("image" == type) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    val path = getDataColumn(context, contentUri, selection, selectionArgs)
                    if (!path.isNullOrEmpty() && File(path).exists()) return path
                }
                // DownloadsDocumentsProvider (下载目录)
                else if ("com.android.providers.downloads.documents" == uri.authority) {
                    if (docId.startsWith("raw:")) {
                        return docId.substring(4)
                    }
                    try {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            docId.toLong()
                        )
                        val path = getDataColumn(context, contentUri, null, null)
                        if (!path.isNullOrEmpty() && File(path).exists()) return path
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse download uri: ${e.message}")
                    }
                }
            }
            // 3. 普通 MediaStore content:// Uri
            else if ("content".equals(uri.scheme, ignoreCase = true)) {
                val path = getDataColumn(context, uri, null, null)
                if (!path.isNullOrEmpty() && File(path).exists()) return path
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve real path from uri: $uri, error: ${e.message}")
        }

        // 兜底返回 Uri 字符串
        return uri.toString()
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        if (uri == null) return null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getDataColumn failed: ${e.message}")
        }
        return null
    }
}
