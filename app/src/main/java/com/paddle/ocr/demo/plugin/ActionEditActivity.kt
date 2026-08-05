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
import androidx.compose.ui.res.stringResource
import com.paddle.ocr.demo.R
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
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
    private var regionResultFlow = kotlinx.coroutines.flow.MutableStateFlow<FloatArray?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getFloatArrayExtra("REGION_RESULT")?.let {
            regionResultFlow.value = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                        onSave = { mode, text, regex, exact, ignoreCase, path, restrict, left, top, right, bottom ->
                            saveAndFinish(mode, text, regex, exact, ignoreCase, path, restrict, left, top, right, bottom)
                        }
                    )
                }
            }
        }
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

        resultBundle.putStringArray(
            "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionEditScreen(
    initialTargetText: String,
    initialIsRegex: Boolean,
    initialIsExactMatch: Boolean,
    initialIsIgnoreCase: Boolean,
    initialCaptureMode: Int,
    initialFilePath: String,
    initialRestrictRegion: Boolean,
    initialRegionLeft: String,
    initialRegionTop: String,
    initialRegionRight: String,
    initialRegionBottom: String,
    regionResultFlow: kotlinx.coroutines.flow.StateFlow<FloatArray?>,
    onSave: (Int, String, Boolean, Boolean, Boolean, String, Boolean, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var targetText by remember { mutableStateOf(initialTargetText) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }
    var isExactMatch by remember { mutableStateOf(initialIsExactMatch) }
    var isIgnoreCase by remember { mutableStateOf(initialIsIgnoreCase) }
    var captureMode by remember { mutableStateOf(initialCaptureMode) }
    var filePath by remember { mutableStateOf(initialFilePath) }
    
    var restrictRegion by remember { mutableStateOf(initialRestrictRegion) }
    var regionLeft by remember { mutableStateOf(initialRegionLeft) }
    var regionTop by remember { mutableStateOf(initialRegionTop) }
    var regionRight by remember { mutableStateOf(initialRegionRight) }
    var regionBottom by remember { mutableStateOf(initialRegionBottom) }

    val regionResult by regionResultFlow.collectAsState()
    LaunchedEffect(regionResult) {
        regionResult?.let {
            regionLeft = String.format("%.2f", it[0])
            regionTop = String.format("%.2f", it[1])
            regionRight = String.format("%.2f", it[2])
            regionBottom = String.format("%.2f", it[3])
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val realPath = UriUtils.getPath(context, it)
            if (realPath != null) {
                filePath = realPath
            } else {
                Toast.makeText(context, context.getString(R.string.path_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(context, context.getString(R.string.permission_denied_storage), Toast.LENGTH_SHORT).show()
        }
        galleryLauncher.launch(arrayOf("image/*"))
    }

    val modeOptions = listOf(stringResource(R.string.mode_screen_record), stringResource(R.string.mode_accessibility), stringResource(R.string.mode_file_path))

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, FloatingSelectionService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Toast.makeText(context, "悬浮窗已启动，请切换到目标应用进行框选", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_edit_title)) },
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
                    onClick = { onSave(captureMode, targetText, isRegex, isExactMatch, isIgnoreCase, filePath, restrictRegion, regionLeft, regionTop, regionRight, regionBottom) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    Text(stringResource(R.string.save_and_exit), fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        text = stringResource(R.string.image_source_title),
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
                                                Toast.makeText(context, context.getString(R.string.accessibility_not_supported), Toast.LENGTH_SHORT).show()
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
                                label = { Text(stringResource(R.string.file_path_hint)) },
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
                                    contentDescription = stringResource(R.string.select_file_btn)
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
                        text = stringResource(R.string.target_text_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text(stringResource(R.string.target_text_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.match_scope),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val matchScopeOptions = listOf(stringResource(R.string.contains_match), stringResource(R.string.exact_match))
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
                        text = stringResource(R.string.match_rule),
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
                            text = stringResource(R.string.regex_match),
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
                            text = stringResource(R.string.ignore_case),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Region Restriction Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = restrictRegion,
                                onClick = { restrictRegion = !restrictRegion },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = restrictRegion,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.region_restrict),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (restrictRegion) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = regionLeft,
                                onValueChange = { regionLeft = it },
                                label = { Text(stringResource(R.string.region_left)) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = regionTop,
                                onValueChange = { regionTop = it },
                                label = { Text(stringResource(R.string.region_top)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = regionRight,
                                onValueChange = { regionRight = it },
                                label = { Text(stringResource(R.string.region_right)) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = regionBottom,
                                onValueChange = { regionBottom = it },
                                label = { Text(stringResource(R.string.region_bottom)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (Settings.canDrawOverlays(context)) {
                                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                                } else {
                                    Toast.makeText(context, context.getString(R.string.overlay_permission_needed), Toast.LENGTH_SHORT).show()
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.region_draw))
                        }
                    }
                }
            }

            // 说明文字
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = stringResource(R.string.instruction_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = context.getString(R.string.instruction_desc, "%ocr_full_text", "%match_found", "%match_center_x", "%match_center_y"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                SelectionContainer {
                    Text(
                        text = stringResource(R.string.appops_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
