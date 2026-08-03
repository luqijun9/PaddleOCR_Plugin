package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.paddle.ocr.demo.R

class ActionEditActivity : Activity() {

    private lateinit var editTargetText: EditText
    private lateinit var checkIsRegex: CheckBox
    private lateinit var spinnerCaptureMode: Spinner
    private lateinit var editFilePath: EditText
    
    private var lastSelectedModeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_edit)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        editTargetText = findViewById(R.id.editTargetText)
        checkIsRegex = findViewById(R.id.checkIsRegex)
        spinnerCaptureMode = findViewById(R.id.spinnerCaptureMode)
        editFilePath = findViewById(R.id.editFilePath)

        val modes = arrayOf("录屏权限 (会闪一下黑屏)", "无障碍服务 (静默无感)", "指定文件路径 (本地图片)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerCaptureMode.adapter = adapter

        spinnerCaptureMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == TaskerPluginConstants.MODE_ACCESSIBILITY) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Toast.makeText(this@ActionEditActivity, "无障碍截图仅支持 Android 11+", Toast.LENGTH_SHORT).show()
                        spinnerCaptureMode.setSelection(lastSelectedModeIndex)
                        return
                    }
                }
                
                lastSelectedModeIndex = position
                if (position == TaskerPluginConstants.MODE_FILE_PATH) {
                    editFilePath.visibility = View.VISIBLE
                } else {
                    editFilePath.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Read existing bundle if editing
        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                editTargetText.setText(bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, ""))
                checkIsRegex.isChecked = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
                val mode = bundle.getInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, 0)
                spinnerCaptureMode.setSelection(mode)
                editFilePath.setText(bundle.getString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, ""))
            }
        }
    }

    override fun finish() {
        val targetText = editTargetText.text.toString()
        val isRegex = checkIsRegex.isChecked
        val captureMode = spinnerCaptureMode.selectedItemPosition
        val filePath = editFilePath.text.toString()

        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
            putInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, captureMode)
            putString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, filePath)
        }

        // Set blurb (description shown in Tasker)
        val blurb = buildString {
            when (captureMode) {
                TaskerPluginConstants.MODE_MEDIA_PROJECTION -> append("录屏 ")
                TaskerPluginConstants.MODE_ACCESSIBILITY -> append("静默 ")
                TaskerPluginConstants.MODE_FILE_PATH -> append("文件 ")
            }
            if (targetText.isEmpty()) append("识别全部文字") else append("查找: $targetText")
        }
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_STRING_BLURB, blurb)
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, resultBundle)

        // Tell Tasker which variables this action will output
        val variables = arrayOf(
            "%ocr_full_text\n全量文本\n包含所有拼在一起的文本结果",
            "%ocr_json\nJSON格式结果\n包含每个文本块坐标的JSON数组",
            "%match_found\n是否找到目标文本\ntrue 或 false",
            "%match_center_x\n目标X坐标\n匹配文本的中心点X轴坐标",
            "%match_center_y\n目标Y坐标\n匹配文本的中心点Y轴坐标",
            "%ocr_error\n错误信息\n运行出错时的提示（如无障碍未开启）"
        )
        TaskerPlugin.addRelevantVariableList(resultIntent, variables)

        // Also tell Tasker to replace variables in our Target Text and File Path before sending it to us
        resultBundle.putStringArray(
            "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS", 
            arrayOf(
                TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT,
                TaskerPluginConstants.BUNDLE_KEY_FILE_PATH
            )
        ) // 请求宿主等待较长时间（120秒），因为需要用户点击授权对话框 + OCR 识别
        // TaskerPlugin API 要求单位是毫秒
        TaskerPlugin.Setting.requestTimeoutMS(resultIntent, 120000) // 120秒

        setResult(Activity.RESULT_OK, resultIntent)
        super.finish()
    }
}
