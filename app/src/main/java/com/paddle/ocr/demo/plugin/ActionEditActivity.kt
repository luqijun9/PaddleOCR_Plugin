package com.paddle.ocr.demo.plugin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import com.paddle.ocr.demo.R
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var initialTargetText = ""
        var initialIsRegex = false
        var initialIsExactMatch = false
        var initialIsIgnoreCase = true
        var initialCaptureMode = TaskerPluginConstants.MODE_MEDIA_PROJECTION
        var initialFilePath = ""

        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                initialTargetText = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, "")
                initialIsRegex = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
                initialIsExactMatch = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, false)
                // Default to true if the key is not in the bundle
                initialIsIgnoreCase = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, true)
                initialCaptureMode = bundle.getInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, TaskerPluginConstants.MODE_MEDIA_PROJECTION)
                initialFilePath = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, "")
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
                        onSave = { mode, text, regex, exact, ignoreCase, path ->
                            saveAndFinish(mode, text, regex, exact, ignoreCase, path)
                        }
                    )
                }
            }
        }
    }

    private fun saveAndFinish(captureMode: Int, targetText: String, isRegex: Boolean, isExactMatch: Boolean, isIgnoreCase: Boolean, filePath: String) {
        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, isExactMatch)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, isIgnoreCase)
            putInt(TaskerPluginConstants.BUNDLE_KEY_CAPTURE_MODE, captureMode)
            putString(TaskerPluginConstants.BUNDLE_KEY_FILE_PATH, filePath)
        }

        val blurb = buildString {
            when (captureMode) {
                TaskerPluginConstants.MODE_MEDIA_PROJECTION -> append("录屏模式 ")
                TaskerPluginConstants.MODE_ACCESSIBILITY -> append("无障碍模式 ")
                TaskerPluginConstants.MODE_FILE_PATH -> {
                    if (filePath.isEmpty()) {
                        append("无图片 ")
                    } else {
                        val fileName = java.io.File(filePath).name
                        if (fileName.isNotEmpty()) append("$fileName ") else append("无图片 ")
                    }
                }
            }
            if (targetText.isEmpty()) append("无查找") else append("查找: $targetText")
        }
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_STRING_BLURB, blurb)
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, resultBundle)

        val variables = arrayOf(
            "%ocr_full_text\n全量文本\n包含所有拼在一起的文本结果",
            "%ocr_json\nJSON格式结果\n包含每个文本块坐标的JSON数组",
            "%match_found\n是否找到目标文本\ntrue 或 false",
            "%match_center_x\n目标X坐标\n匹配文本的中心点X轴坐标",
            "%match_center_y\n目标Y坐标\n匹配文本的中心点Y轴坐标",
            "%ocr_error\n错误信息\n运行出错时的提示（如无障碍未开启）"
        )
        TaskerPlugin.addRelevantVariableList(resultIntent, variables)

        resultBundle.putStringArray(
            "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS",
            arrayOf(
                TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT,
                TaskerPluginConstants.BUNDLE_KEY_FILE_PATH
            )
        )
        TaskerPlugin.Setting.requestTimeoutMS(resultIntent, 120000)

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionEditScreen(
    initialTargetText: String,
    initialIsRegex: Boolean,
    initialIsExactMatch: Boolean,
    initialIsIgnoreCase: Boolean,
    initialCaptureMode: Int,
    initialFilePath: String,
    onSave: (Int, String, Boolean, Boolean, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    var targetText by remember { mutableStateOf(initialTargetText) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }
    var isExactMatch by remember { mutableStateOf(initialIsExactMatch) }
    var isIgnoreCase by remember { mutableStateOf(initialIsIgnoreCase) }
    var captureMode by remember { mutableStateOf(initialCaptureMode) }
    var filePath by remember { mutableStateOf(initialFilePath) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val realPath = UriUtils.getPath(context, it)
            if (realPath != null) {
                filePath = realPath
            } else {
                Toast.makeText(context, "无法获取文件的绝对路径，请手动输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(context, "未授予存储权限，Tasker 可能无法读取本地图片文件", Toast.LENGTH_SHORT).show()
        }
        galleryLauncher.launch(arrayOf("image/*"))
    }

    val modeOptions = listOf("录屏权限 (需要授权)", "无障碍服务 (需要授权)", "指定文件路径 (本地图片)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置 OCR 插件") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = { onSave(captureMode, targetText, isRegex, isExactMatch, isIgnoreCase, filePath) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    Text("保存并返回 (Save & Exit)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 模式选择
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "图像获取方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(Modifier.selectableGroup()) {
                        modeOptions.forEachIndexed { index, text ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = (captureMode == index),
                                        onClick = {
                                            if (index == TaskerPluginConstants.MODE_ACCESSIBILITY && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                                Toast.makeText(context, "无障碍截图仅支持 Android 11+", Toast.LENGTH_SHORT).show()
                                            } else {
                                                captureMode = index
                                            }
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (captureMode == index),
                                    onClick = null 
                                )
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }

                    if (captureMode == TaskerPluginConstants.MODE_FILE_PATH) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = filePath,
                                onValueChange = { filePath = it },
                                label = { Text("图片文件绝对路径") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { 
                                    val permissionsToRequest = mutableListOf<String>()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                                        }
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                    }
                                    if (permissionsToRequest.isNotEmpty()) {
                                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                    } else {
                                        galleryLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_folder_outline),
                                    contentDescription = "选择文件"
                                )
                            }
                        }
                    }
                }
            }

            // 文本匹配配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "查找目标文本 (可选)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text("输入你想查找的文字") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "匹配范围",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val matchScopeOptions = listOf("包含", "完全匹配")
                    var selectedScopeIndex = if (isExactMatch) 1 else 0

                    Row(modifier = Modifier.fillMaxWidth()) {
                        matchScopeOptions.forEachIndexed { index, option ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .selectable(
                                        selected = selectedScopeIndex == index,
                                        onClick = {
                                            selectedScopeIndex = index
                                            isExactMatch = (index == 1)
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = selectedScopeIndex == index,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "匹配规则",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isRegex,
                                onClick = { isRegex = !isRegex },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isRegex,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "使用正则表达式匹配",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isIgnoreCase,
                                onClick = { isIgnoreCase = !isIgnoreCase },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isIgnoreCase,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "不区分大小写",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // 说明文字
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "说明",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "此插件将自动截取当前屏幕，并识别文字。如果找到了目标文本，会返回中心坐标。\n可以通过 Tasker 变量 %ocr_full_text, %match_found, %match_center_x, %match_center_y 获取结果。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                SelectionContainer {
                    Text(
                        text = "💡 录屏模式免弹窗截图：\n如果您有 Root 或 Shizuku，可通过执行以下 ADB 命令隐式授予录屏权限，从此使用“录屏权限”模式不再有确认弹窗：\nappops set com.paddle.ocr.demo PROJECT_MEDIA allow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
