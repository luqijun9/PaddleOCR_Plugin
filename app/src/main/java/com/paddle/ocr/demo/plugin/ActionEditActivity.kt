package com.paddle.ocr.demo.plugin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.paddle.ocr.demo.R
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paddle.ocr.demo.ui.theme.PPOCRTheme

class ActionEditActivity : ComponentActivity() {
    private var regionResultFlow = kotlinx.coroutines.flow.MutableStateFlow<FloatArray?>(null)
    private lateinit var regionDrawLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var screenCaptureLauncherActivity: androidx.activity.result.ActivityResultLauncher<Intent>

    private var pendingRestrictRegion = false
    private var pendingRegionLeft = "0.0"
    private var pendingRegionTop = "0.0"
    private var pendingRegionRight = "1.0"
    private var pendingRegionBottom = "1.0"

    companion object {
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getFloatArrayExtra("REGION_RESULT")?.let {
            regionResultFlow.value = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理用户取消操作（按返回键）：返回 cancelled 状态给 Tasker
        onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // 用户取消编辑，通知 Tasker 返回 cancelled 状态
                    setResult(Activity.RESULT_CANCELED, Intent())
                    finish()
                }
            }
        )

        // 启动前台保活服务
        AppKeepAliveService.start(this)

        // Launchers must be registered before setContent
        regionDrawLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getFloatArrayExtra("REGION_RESULT")?.let {
                    regionResultFlow.value = it
                }
            }
        }

        screenCaptureLauncherActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, FloatingSelectionService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                // When screenshot is ready, launch RegionDrawActivity as a CHILD
                // of this Activity (same task stack). Data returns via setResult.
                FloatingSelectionService.screenshotCallback = {
                    runOnUiThread {
                        val intent = Intent(this, RegionDrawActivity::class.java).apply {
                            putExtra(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, pendingRestrictRegion)
                            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, pendingRegionLeft)
                            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, pendingRegionTop)
                            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, pendingRegionRight)
                            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, pendingRegionBottom)
                        }
                        regionDrawLauncher.launch(intent)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, getString(R.string.floating_service_started_toast), Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            }
        }

        var initialTargetText = ""
        var initialIsRegex = false
        var initialIsExactMatch = false
        var initialIsIgnoreCase = true
        var initialCaptureMode = TaskerPluginConstants.MODE_MEDIA_PROJECTION
        var initialFilePath = ""

        var initialRestrictRegion = false
        var initialRegionLeft = "0.0"
        var initialRegionTop = "0.0"
        var initialRegionRight = "1.0"
        var initialRegionBottom = "1.0"

        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                initialTargetText = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, "")
                initialIsRegex = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
                initialIsExactMatch = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, false)
                initialIsIgnoreCase = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, true)
                initialCaptureMode = bundle.getInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, TaskerPluginConstants.MODE_MEDIA_PROJECTION)
                initialFilePath = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, "")
                
                initialRestrictRegion = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, false)
                initialRegionLeft = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, "0.0")
                initialRegionTop = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, "0.0")
                initialRegionRight = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, "1.0")
                initialRegionBottom = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, "1.0")
            }
        }

        setContent {
            PPOCRTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActionEditScreen(
                        initialTargetText = initialTargetText,
                        initialIsRegex = initialIsRegex,
                        initialIsExactMatch = initialIsExactMatch,
                        initialIsIgnoreCase = initialIsIgnoreCase,
                        initialCaptureMode = initialCaptureMode,
                        initialFilePath = initialFilePath,
                        initialRestrictRegion = initialRestrictRegion,
                        initialRegionLeft = initialRegionLeft,
                        initialRegionTop = initialRegionTop,
                        initialRegionRight = initialRegionRight,
                        initialRegionBottom = initialRegionBottom,
                        regionResultFlow = regionResultFlow,
                        onLaunchScreenCapture = { restrict, left, top, right, bottom ->
                            pendingRestrictRegion = restrict
                            pendingRegionLeft = left
                            pendingRegionTop = top
                            pendingRegionRight = right
                            pendingRegionBottom = bottom

                            if (Settings.canDrawOverlays(this)) {
                                val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                                screenCaptureLauncherActivity.launch(pm.createScreenCaptureIntent())
                            } else {
                                Toast.makeText(this, getString(R.string.overlay_permission_needed), Toast.LENGTH_SHORT).show()
                                try {
                                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                } catch (e: Exception) {
                                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                }
                            }
                        },
                        onSave = { mode, text, regex, exact, ignoreCase, path, restrict, left, top, right, bottom ->
                            saveAndFinish(mode, text, regex, exact, ignoreCase, path, restrict, left, top, right, bottom)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppKeepAliveService.stop(this)
    }

    private fun saveAndFinish(captureMode: Int, targetText: String, isRegex: Boolean, isExactMatch: Boolean, isIgnoreCase: Boolean, filePath: String, restrictRegion: Boolean, regionLeft: String, regionTop: String, regionRight: String, regionBottom: String) {
        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, isExactMatch)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, isIgnoreCase)
            putInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, captureMode)
            putString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, filePath)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, restrictRegion)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, regionLeft)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, regionTop)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, regionRight)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, regionBottom)
        }

        val blurb = buildString {
            when (captureMode) {
                TaskerPluginConstants.MODE_MEDIA_PROJECTION -> append(getString(R.string.blurb_mode_screen))
                TaskerPluginConstants.MODE_ACCESSIBILITY -> append(getString(R.string.blurb_mode_acc))
                TaskerPluginConstants.MODE_FILE_PATH -> {
                    if (filePath.isEmpty()) {
                        append(getString(R.string.blurb_no_image))
                    } else {
                        val fileName = java.io.File(filePath).name
                        if (fileName.isNotEmpty()) append(getString(R.string.blurb_file_name, fileName)) else append(getString(R.string.blurb_no_image))
                    }
                }
            }
            if (targetText.isEmpty()) append(getString(R.string.blurb_no_search)) else append(getString(R.string.blurb_search) + targetText)
            if (restrictRegion) {
                append(" | Crop: $regionLeft, $regionTop, $regionRight, $regionBottom")
            }
        }
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_STRING_BLURB, blurb)
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, resultBundle)

        val variables = arrayOf(
            getString(R.string.var_full_text),
            getString(R.string.var_json),
            getString(R.string.var_match_found),
            getString(R.string.var_center_x),
            getString(R.string.var_center_y),
            getString(R.string.var_error)
        )
        TaskerPlugin.addRelevantVariableList(resultIntent, variables)

        // 使用 TaskerPlugin 官方 API 注册变量替换 key（替代硬编码方式）
        TaskerPlugin.Setting.setVariableReplaceKeys(
            resultBundle,
            arrayOf(
                TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT,
                TaskerPluginConstants.BUNDLE_KEY_FILE_PATH,
                TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT,
                TaskerPluginConstants.BUNDLE_KEY_REGION_TOP,
                TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT,
                TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM
            )
        )
        TaskerPlugin.Setting.requestTimeoutMS(resultIntent, 120000)

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}