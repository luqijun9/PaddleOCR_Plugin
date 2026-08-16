package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paddle.ocr.demo.ui.theme.PPOCRTheme
import java.util.UUID

class ActionEditActivity : ComponentActivity() {

    private var initialTargetText: String = ""
    private var initialIsRegex: Boolean = false
    private var instanceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 读取已有配置
        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                initialTargetText = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, "")
                initialIsRegex = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
                instanceId = bundle.getString("plugin_instance_id") ?: UUID.randomUUID().toString()
            }
        }
        if (instanceId.isEmpty()) {
            instanceId = UUID.randomUUID().toString()
        }

        setContent {
            PPOCRTheme {
                ActionEditScreen(
                    initialTargetText = initialTargetText,
                    initialIsRegex = initialIsRegex,
                    onSave = { targetText, isRegex ->
                        saveAndFinish(targetText, isRegex)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun saveAndFinish(targetText: String, isRegex: Boolean) {
        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
            putString("plugin_instance_id", instanceId)
        }

        // 设置在 Tasker/MacroDroid 动作列表里显示的摘要 (Blurb)
        val blurb = if (targetText.isEmpty()) "识别全屏文字" else "查找: $targetText"
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_STRING_BLURB, blurb)
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, resultBundle)

        // 注册回传变量
        val variables = arrayOf(
            "%ocr_full_text\n全量文本\n包含所有拼在一起的文本结果",
            "%ocr_json\nJSON格式结果\n包含每个文本块坐标的JSON数组",
            "%match_found\n是否找到目标文本\ntrue 或 false",
            "%match_center_x\n目标X坐标\n匹配文本的中心点X轴坐标",
            "%match_center_y\n目标Y坐标\n匹配文本的中心点Y轴坐标",
            "%errmsg\n错误信息\n执行失败或异常时的错误描述"
        )
        TaskerPlugin.addRelevantVariableList(resultIntent, variables)

        // 注册变量替换 (若宿主支持)
        if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this)) {
            TaskerPlugin.Setting.setVariableReplaceKeys(resultBundle, arrayOf(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT))
        }

        // 请求宿主等待较长时间 (120秒)
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
    onSave: (String, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var targetText by remember { mutableStateOf(initialTargetText) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }

    // 返回键默认自动保存
    BackHandler {
        onSave(targetText, isRegex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OCR 屏幕识别配置",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(targetText, isRegex) }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = { onSave(targetText, isRegex) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存配置", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限检测卡片
            OverlayPermissionCheckCard(context)

            // 目标文本配置卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "查找设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("目标查找文本 (可选)") },
                        placeholder = { Text("留空则提取全屏所有文字") },
                        supportingText = {
                            Text("支持 Tasker/MacroDroid 变量 (如 %search 或 [clipboard])")
                        },
                        trailingIcon = {
                            if (targetText.isNotEmpty()) {
                                IconButton(onClick = { targetText = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清空"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "使用正则表达式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "开启后支持正则匹配（例如 \\d{4,6} 匹配验证码）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRegex,
                            onCheckedChange = { isRegex = it }
                        )
                    }
                }
            }

            // 返回变量说明卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "输出变量说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    VariableRow(name = "%ocr_full_text", desc = "全屏识别出的所有文本拼接")
                    VariableRow(name = "%ocr_json", desc = "带文字框坐标与置信度的 JSON 数组")
                    VariableRow(name = "%match_found", desc = "是否匹配到目标文字 (true / false)")
                    VariableRow(name = "%match_center_x", desc = "匹配文字中心点 X 轴坐标 (像素)")
                    VariableRow(name = "%match_center_y", desc = "匹配文字中心点 Y 轴坐标 (像素)")
                    VariableRow(name = "%errmsg", desc = "失败或异常时的具体错误描述")
                }
            }
        }
    }
}

@Composable
fun VariableRow(name: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun OverlayPermissionCheckCard(context: Context) {
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasPermission) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "未开启后台弹出/悬浮窗权限",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = "后台触发自动截屏需要“显示在其他应用上层”（部分机型如小米/vivo还需开启“后台弹出界面”）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("一键前往开启权限")
                }
            }
        }
    }
}

