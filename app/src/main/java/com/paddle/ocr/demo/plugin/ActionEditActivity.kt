package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import com.paddle.ocr.demo.R

class ActionEditActivity : Activity() {

    private lateinit var editTargetText: EditText
    private lateinit var checkIsRegex: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_edit)

        editTargetText = findViewById(R.id.editTargetText)
        checkIsRegex = findViewById(R.id.checkIsRegex)

        // Read existing bundle if editing
        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                editTargetText.setText(bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, ""))
                checkIsRegex.isChecked = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
            }
        }
    }

    override fun finish() {
        val targetText = editTargetText.text.toString()
        val isRegex = checkIsRegex.isChecked

        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
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
        // IMPORTANT: Tasker expects RELEVANT_VARIABLES on the resultIntent to show the UI
        if (TaskerPluginConstants.EXTRA_RELEVANT_VARIABLES.isNotEmpty()) {
            resultIntent.putExtra(TaskerPluginConstants.EXTRA_RELEVANT_VARIABLES, variables)
        }
        
        // Also tell Tasker to replace variables in our Target Text before sending it to us
        resultBundle.putStringArray(
            "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS", 
            arrayOf(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT)
        )

        // Tell Tasker we request a timeout UI, default to 10 seconds (10000 ms)
        // Tasker actually expects this value in seconds
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_REQUESTED_TIMEOUT, 10)

        setResult(Activity.RESULT_OK, resultIntent)
        super.finish()
    }
}
