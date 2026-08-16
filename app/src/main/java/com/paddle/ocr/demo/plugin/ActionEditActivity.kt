package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.paddle.ocr.demo.R

class ActionEditActivity : Activity() {

    private lateinit var editTargetText: EditText
    private lateinit var checkIsRegex: CheckBox
    private lateinit var layoutOverlayWarning: LinearLayout
    private lateinit var btnGrantOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_edit)

        editTargetText = findViewById(R.id.editTargetText)
        checkIsRegex = findViewById(R.id.checkIsRegex)
        layoutOverlayWarning = findViewById(R.id.layoutOverlayWarning)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)

        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        checkOverlayPermission()

        // Read existing bundle if editing
        var instanceId: String? = null
        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                editTargetText.setText(bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, ""))
                checkIsRegex.isChecked = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
                instanceId = bundle.getString("plugin_instance_id")
            }
        }
        
        if (instanceId == null) {
            instanceId = java.util.UUID.randomUUID().toString()
        }
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            layoutOverlayWarning.visibility = View.VISIBLE
        } else {
            layoutOverlayWarning.visibility = View.GONE
        }
    }

    override fun finish() {
        val targetText = editTargetText.text.toString()
        val isRegex = checkIsRegex.isChecked

        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
            putString("plugin_instance_id", intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)?.getString("plugin_instance_id") ?: java.util.UUID.randomUUID().toString())
        }

        // Set blurb (description shown in Tasker)
        val blurb = if (targetText.isEmpty()) "识别全部文字" else "查找: $targetText"
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_STRING_BLURB, blurb)
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, resultBundle)

        // Tell Tasker which variables this action will output
        val variables = arrayOf(
            "%ocr_full_text\n全量文本\n包含所有拼在一起的文本结果",
            "%ocr_json\nJSON格式结果\n包含每个文本块坐标的JSON数组",
            "%match_found\n是否找到目标文本\ntrue 或 false",
            "%match_center_x\n目标X坐标\n匹配文本的中心点X轴坐标",
            "%match_center_y\n目标Y坐标\n匹配文本的中心点Y轴坐标"
        )
        TaskerPlugin.addRelevantVariableList(resultIntent, variables)

        // Tell Tasker to replace variables in our Target Text before sending it to us
        TaskerPlugin.Setting.setVariableReplaceKeys(resultBundle, arrayOf(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT))

        // 请求宿主等待较长时间（120秒），因为需要用户点击授权对话框 + OCR 识别
        // TaskerPlugin API 要求单位是毫秒
        TaskerPlugin.Setting.requestTimeoutMS(resultIntent, 120000) // 120秒

        setResult(Activity.RESULT_OK, resultIntent)
        super.finish()
    }
}
