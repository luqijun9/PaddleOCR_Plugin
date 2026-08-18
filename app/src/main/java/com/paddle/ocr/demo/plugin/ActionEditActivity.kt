package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paddle.ocr.demo.R
import com.paddle.ocr.demo.ui.theme.PPOCRTheme
import java.io.File
import android.graphics.BitmapFactory
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ActionEditActivity : ComponentActivity() {

    private var initialImageSource: String = TaskerPluginConstants.IMAGE_SOURCE_SCREEN_CAPTURE
    private var initialImagePath: String = ""
    private var initialTargetText: String = ""
    private var initialIsRegex: Boolean = false
    private var initialIsExactMatch: Boolean = false
    private var initialIsIgnoreCase: Boolean = true
    private var initialRestrictRegion: Boolean = false
    private var initialRegionLeft: String = "0.0"
    private var initialRegionTop: String = "0.0"
    private var initialRegionRight: String = "1.0"
    private var initialRegionBottom: String = "1.0"
    private var initialRegionPreviewFile: String = ""
    private var instanceId: String = ""

    private val regionResultFlow = MutableStateFlow<FloatArray?>(null)
    private val regionPreviewFlow = MutableStateFlow<String?>(null)
    private var pendingRestrictRegion = false
    private var pendingRegionLeft = "0.0"
    private var pendingRegionTop = "0.0"
    private var pendingRegionRight = "1.0"
    private var pendingRegionBottom = "1.0"

    private val regionDrawLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val coords = result.data?.getFloatArrayExtra("REGION_RESULT")
            val previewFile = result.data?.getStringExtra("REGION_PREVIEW_FILE")
            if (coords != null && coords.size == 4) {
                regionResultFlow.value = coords
            }
            if (!previewFile.isNullOrEmpty()) {
                regionPreviewFlow.value = previewFile
            }
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
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
            val serviceIntent = Intent(this, FloatingSelectionService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        }
    }

    private fun requestRegionScreenCapture(
        restrict: Boolean,
        left: String,
        top: String,
        right: String,
        bottom: String
    ) {
        pendingRestrictRegion = restrict
        pendingRegionLeft = left
        pendingRegionTop = top
        pendingRegionRight = right
        pendingRegionBottom = bottom

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, getString(R.string.perm_overlay_title), Toast.LENGTH_LONG).show()
            return
        }

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun requestInPlaceRegionDraw(
        bitmap: Bitmap,
        restrict: Boolean,
        left: String,
        top: String,
        right: String,
        bottom: String
    ) {
        FloatingSelectionService.captureBitmap = bitmap
        val intent = Intent(this, RegionDrawActivity::class.java).apply {
            putExtra(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, restrict)
            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, left)
            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, top)
            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, right)
            putExtra(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, bottom)
        }
        regionDrawLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PluginKeepAliveService.start(this)

        // 读取已有配置
        if (intent.action == TaskerPluginConstants.ACTION_EDIT_SETTING) {
            val bundle = intent.getBundleExtra(TaskerPluginConstants.EXTRA_BUNDLE)
            if (bundle != null) {
                initialImageSource = bundle.getString(
                    TaskerPluginConstants.BUNDLE_KEY_IMAGE_SOURCE,
                    TaskerPluginConstants.IMAGE_SOURCE_SCREEN_CAPTURE
                )
                initialImagePath = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_IMAGE_PATH, "")
                initialTargetText = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, "")
                initialIsRegex = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, false)
                initialIsExactMatch = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, false)
                initialIsIgnoreCase = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, true)
                initialRestrictRegion = bundle.getBoolean(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, false)
                initialRegionLeft = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, "0.0")
                initialRegionTop = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, "0.0")
                initialRegionRight = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, "1.0")
                initialRegionBottom = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, "1.0")
                initialRegionPreviewFile = bundle.getString(TaskerPluginConstants.BUNDLE_KEY_REGION_PREVIEW_FILE, "")
                instanceId = bundle.getString("plugin_instance_id") ?: UUID.randomUUID().toString()
            }
        }
        if (instanceId.isEmpty()) {
            instanceId = UUID.randomUUID().toString()
        }

        setContent {
            PPOCRTheme {
                ActionEditScreen(
                    initialImageSource = initialImageSource,
                    initialImagePath = initialImagePath,
                    initialTargetText = initialTargetText,
                    initialIsRegex = initialIsRegex,
                    initialIsExactMatch = initialIsExactMatch,
                    initialIsIgnoreCase = initialIsIgnoreCase,
                    initialRestrictRegion = initialRestrictRegion,
                    initialRegionLeft = initialRegionLeft,
                    initialRegionTop = initialRegionTop,
                    initialRegionRight = initialRegionRight,
                    initialRegionBottom = initialRegionBottom,
                    initialRegionPreviewFile = initialRegionPreviewFile,
                    regionResultFlow = regionResultFlow,
                    regionPreviewFlow = regionPreviewFlow,
                    onLaunchScreenCapture = { restrict, left, top, right, bottom ->
                        requestRegionScreenCapture(restrict, left, top, right, bottom)
                    },
                    onLaunchInPlaceRegionDraw = { bitmap, restrict, left, top, right, bottom ->
                        requestInPlaceRegionDraw(bitmap, restrict, left, top, right, bottom)
                    },
                    onSave = { imageSource, imagePath, targetText, isRegex, isExactMatch, isIgnoreCase, restrictRegion, regionLeft, regionTop, regionRight, regionBottom, regionPreviewFile ->
                        saveAndFinish(
                            imageSource,
                            imagePath,
                            targetText,
                            isRegex,
                            isExactMatch,
                            isIgnoreCase,
                            restrictRegion,
                            regionLeft,
                            regionTop,
                            regionRight,
                            regionBottom,
                            regionPreviewFile
                        )
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun saveAndFinish(
        imageSource: String,
        imagePath: String,
        targetText: String,
        isRegex: Boolean,
        isExactMatch: Boolean,
        isIgnoreCase: Boolean,
        restrictRegion: Boolean,
        regionLeft: String,
        regionTop: String,
        regionRight: String,
        regionBottom: String,
        regionPreviewFile: String
    ) {
        val resultIntent = Intent()
        val resultBundle = Bundle().apply {
            putString(TaskerPluginConstants.BUNDLE_KEY_IMAGE_SOURCE, imageSource)
            putString(TaskerPluginConstants.BUNDLE_KEY_IMAGE_PATH, imagePath)
            putString(TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT, targetText)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_REGEX, isRegex)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_EXACT_MATCH, isExactMatch)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_IS_IGNORE_CASE, isIgnoreCase)
            putBoolean(TaskerPluginConstants.BUNDLE_KEY_RESTRICT_REGION, restrictRegion)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT, regionLeft)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_TOP, regionTop)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT, regionRight)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM, regionBottom)
            putString(TaskerPluginConstants.BUNDLE_KEY_REGION_PREVIEW_FILE, regionPreviewFile)
            putString("plugin_instance_id", instanceId)
        }

        // 设置在 Tasker/MacroDroid 动作列表里显示的摘要 (Blurb，全多语言自适应)
        val blurb = buildString {
            when (imageSource) {
                TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH -> {
                    val fileName = imagePath.substringAfterLast('/').ifEmpty { getString(R.string.source_local_file) }
                    if (targetText.isNotEmpty()) {
                        append(getString(R.string.blurb_file_find_fmt, fileName, targetText))
                    } else {
                        append(getString(R.string.blurb_file_ocr_fmt, fileName))
                    }
                }
                TaskerPluginConstants.IMAGE_SOURCE_ACCESSIBILITY -> {
                    if (targetText.isNotEmpty()) {
                        append(getString(R.string.blurb_a11y_find_fmt, targetText))
                    } else {
                        append(getString(R.string.blurb_a11y_ocr))
                    }
                }
                else -> {
                    if (targetText.isNotEmpty()) {
                        append(getString(R.string.blurb_screen_find_fmt, targetText))
                    } else {
                        append(getString(R.string.blurb_screen_ocr))
                    }
                }
            }
            if (restrictRegion) {
                append(" " + getString(R.string.blurb_region_suffix))
            }
        }
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_STRING_BLURB, blurb)
        resultIntent.putExtra(TaskerPluginConstants.EXTRA_BUNDLE, resultBundle)

        // 注册回传变量 (多语言动态本地化)
        val variables = arrayOf(
            "%ocr_full_text\n${getString(R.string.var_label_full_text)}\n${getString(R.string.var_desc_full_text)}",
            "%ocr_json\n${getString(R.string.var_label_json)}\n${getString(R.string.var_desc_json)}",
            "%match_found\n${getString(R.string.var_label_match_found)}\n${getString(R.string.var_desc_match_found)}",
            "%match_center_x\n${getString(R.string.var_label_match_center_x)}\n${getString(R.string.var_desc_match_center_x)}",
            "%match_center_y\n${getString(R.string.var_label_match_center_y)}\n${getString(R.string.var_desc_match_center_y)}",
            "%errmsg\n${getString(R.string.var_label_errmsg)}\n${getString(R.string.var_desc_errmsg)}"
        )
        TaskerPlugin.addRelevantVariableList(resultIntent, variables)

        // 注册变量替换 (若宿主支持)
        if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this)) {
            TaskerPlugin.Setting.setVariableReplaceKeys(
                resultBundle,
                arrayOf(
                    TaskerPluginConstants.BUNDLE_KEY_TARGET_TEXT,
                    TaskerPluginConstants.BUNDLE_KEY_IMAGE_PATH,
                    TaskerPluginConstants.BUNDLE_KEY_REGION_LEFT,
                    TaskerPluginConstants.BUNDLE_KEY_REGION_TOP,
                    TaskerPluginConstants.BUNDLE_KEY_REGION_RIGHT,
                    TaskerPluginConstants.BUNDLE_KEY_REGION_BOTTOM
                )
            )
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
    initialImageSource: String,
    initialImagePath: String,
    initialTargetText: String,
    initialIsRegex: Boolean,
    initialIsExactMatch: Boolean,
    initialIsIgnoreCase: Boolean,
    initialRestrictRegion: Boolean,
    initialRegionLeft: String,
    initialRegionTop: String,
    initialRegionRight: String,
    initialRegionBottom: String,
    initialRegionPreviewFile: String,
    regionResultFlow: StateFlow<FloatArray?>,
    regionPreviewFlow: StateFlow<String?>,
    onLaunchScreenCapture: (Boolean, String, String, String, String) -> Unit,
    onLaunchInPlaceRegionDraw: (Bitmap, Boolean, String, String, String, String) -> Unit,
    onSave: (String, String, String, Boolean, Boolean, Boolean, Boolean, String, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var imageSource by remember { mutableStateOf(initialImageSource) }
    var imagePath by remember { mutableStateOf(initialImagePath) }
    var targetText by remember { mutableStateOf(initialTargetText) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }
    var isExactMatch by remember { mutableStateOf(initialIsExactMatch) }
    var isIgnoreCase by remember { mutableStateOf(initialIsIgnoreCase) }
    var restrictRegion by remember { mutableStateOf(initialRestrictRegion) }
    var regionLeft by remember { mutableStateOf(initialRegionLeft) }
    var regionTop by remember { mutableStateOf(initialRegionTop) }
    var regionRight by remember { mutableStateOf(initialRegionRight) }
    var regionBottom by remember { mutableStateOf(initialRegionBottom) }
    var regionPreviewFile by remember { mutableStateOf(initialRegionPreviewFile) }

    val initialHasCustomRegion = initialRestrictRegion &&
            !(initialRegionLeft == "0.0" && initialRegionTop == "0.0" && initialRegionRight == "1.0" && initialRegionBottom == "1.0")
    var hasCustomRegion by remember { mutableStateOf(initialHasCustomRegion) }

    val regionResult by regionResultFlow.collectAsState()
    LaunchedEffect(regionResult) {
        regionResult?.let {
            regionLeft = String.format(java.util.Locale.US, "%.4f", it[0])
            regionTop = String.format(java.util.Locale.US, "%.4f", it[1])
            regionRight = String.format(java.util.Locale.US, "%.4f", it[2])
            regionBottom = String.format(java.util.Locale.US, "%.4f", it[3])
            hasCustomRegion = true
        }
    }

    val newPreviewFile by regionPreviewFlow.collectAsState()
    LaunchedEffect(newPreviewFile) {
        newPreviewFile?.let {
            if (it.isNotEmpty()) {
                regionPreviewFile = it
            }
        }
    }

    var localImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageSource, imagePath, restrictRegion) {
        if (restrictRegion && imageSource == TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH && imagePath.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val res = ImageFileLoader.loadBitmap(context, imagePath, maxDimension = 640)
                localImageBitmap = (res as? ImageLoadResult.Success)?.bitmap
            }
        } else {
            localImageBitmap = null
        }
    }

    val currentBitmap = remember(imageSource, restrictRegion, regionPreviewFile, localImageBitmap) {
        if (!restrictRegion) {
            null
        } else if (imageSource == TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH) {
            localImageBitmap
        } else if (regionPreviewFile.isNotEmpty()) {
            val file = File(context.filesDir, "previews/$regionPreviewFile")
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } else {
            null
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // 系统文件管理器 (DocumentsUI) 选择器 Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    android.util.Log.w("OcrPlugin", "takePersistableUriPermission notice: ${e.message}")
                }
                val realPath = UriPathUtils.getRealPathFromUri(context, uri)
                imagePath = realPath
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val res = ImageFileLoader.loadBitmap(context, realPath, maxDimension = 640)
                    localImageBitmap = (res as? ImageLoadResult.Success)?.bitmap
                }
            }
        }
    }

    // 返回键默认自动保存
    BackHandler {
        onSave(
            imageSource,
            imagePath,
            targetText,
            isRegex,
            isExactMatch,
            isIgnoreCase,
            restrictRegion,
            regionLeft,
            regionTop,
            regionRight,
            regionBottom,
            regionPreviewFile
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_ocr_plugin_config),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_cancel)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSave(
                            imageSource,
                            imagePath,
                            targetText,
                            isRegex,
                            isExactMatch,
                            isIgnoreCase,
                            restrictRegion,
                            regionLeft,
                            regionTop,
                            regionRight,
                            regionBottom,
                            regionPreviewFile
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.btn_save),
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
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Button(
                        onClick = {
                            onSave(
                                imageSource,
                                imagePath,
                                targetText,
                                isRegex,
                                isExactMatch,
                                isIgnoreCase,
                                restrictRegion,
                                regionLeft,
                                regionTop,
                                regionRight,
                                regionBottom,
                                regionPreviewFile
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
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
            // 模式选择卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_image_source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = imageSource == TaskerPluginConstants.IMAGE_SOURCE_SCREEN_CAPTURE,
                            onClick = { imageSource = TaskerPluginConstants.IMAGE_SOURCE_SCREEN_CAPTURE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text(stringResource(R.string.source_screen_capture))
                        }
                        SegmentedButton(
                            selected = imageSource == TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH,
                            onClick = { imageSource = TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text(stringResource(R.string.source_local_file))
                        }
                        val isAndroid11OrAbove = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                        SegmentedButton(
                            selected = imageSource == TaskerPluginConstants.IMAGE_SOURCE_ACCESSIBILITY,
                            onClick = {
                                if (isAndroid11OrAbove) {
                                    imageSource = TaskerPluginConstants.IMAGE_SOURCE_ACCESSIBILITY
                                }
                            },
                            enabled = isAndroid11OrAbove,
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text(if (isAndroid11OrAbove) stringResource(R.string.source_accessibility) else stringResource(R.string.source_accessibility_req_11))
                        }
                    }
                }
            }

            // 本地图片路径卡片 (仅本地图片模式显示)
            AnimatedVisibility(visible = imageSource == TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                text = stringResource(R.string.section_image_path),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = imagePath,
                            onValueChange = { imagePath = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.label_image_path)) },
                            placeholder = { Text(stringResource(R.string.placeholder_image_path)) },
                            supportingText = {
                                Text(stringResource(R.string.supporting_image_path))
                            },
                            trailingIcon = {
                                if (imagePath.isNotEmpty()) {
                                    IconButton(onClick = { imagePath = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.btn_clear)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf(
                                            "image/jpeg",
                                            "image/png",
                                            "image/webp",
                                            "image/bmp",
                                            "image/gif",
                                            "image/*"
                                        )
                                    )
                                }
                                filePickerLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.btn_browse_files))
                        }
                    }
                }
            }

            // 无障碍权限检测卡片 (仅无障碍模式显示)
            if (imageSource == TaskerPluginConstants.IMAGE_SOURCE_ACCESSIBILITY) {
                AccessibilityPermissionCheckCard(context)
            }

            // 存储权限检测卡片 (仅本地文件模式显示)
            if (imageSource == TaskerPluginConstants.IMAGE_SOURCE_FILE_PATH) {
                StoragePermissionCheckCard(context)
            }

            // 悬浮窗权限检测卡片 (仅截屏模式显示)
            if (imageSource == TaskerPluginConstants.IMAGE_SOURCE_SCREEN_CAPTURE) {
                OverlayPermissionCheckCard(context)
            }

            // 通知权限检测卡片
            NotificationPermissionCheckCard(context)

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
                            text = stringResource(R.string.section_target_text),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.label_target_text)) },
                        placeholder = { Text(stringResource(R.string.placeholder_target_text)) },
                        trailingIcon = {
                            if (targetText.isNotEmpty()) {
                                IconButton(onClick = { targetText = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.btn_clear)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 匹配范围 (包含 / 完全匹配)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.match_scope),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val scopeOptions = listOf(
                            stringResource(R.string.contains_match),
                            stringResource(R.string.exact_match)
                        )
                        val selectedScopeIndex = if (isExactMatch) 1 else 0
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            scopeOptions.forEachIndexed { index, label ->
                                SegmentedButton(
                                    selected = selectedScopeIndex == index,
                                    onClick = { isExactMatch = (index == 1) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scopeOptions.size)
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 选项 1: 正则表达式
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.switch_use_regex),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.desc_use_regex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRegex,
                            onCheckedChange = { isRegex = it }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 选项 2: 忽略大小写
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.switch_ignore_case),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.desc_ignore_case),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isIgnoreCase,
                            onCheckedChange = { isIgnoreCase = it }
                        )
                    }
                }
            }

            // 限制识别范围卡片
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.section_region_restrict),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.desc_restrict_region),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = restrictRegion,
                            onCheckedChange = { restrictRegion = it }
                        )
                    }

                    if (restrictRegion) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        val dm = context.resources.displayMetrics
                        val screenW = dm.widthPixels
                        val screenH = dm.heightPixels

                        val leftVal = regionLeft.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
                        val topVal = regionTop.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
                        val rightVal = regionRight.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                        val bottomVal = regionBottom.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

                        val baseW = if (currentBitmap != null && !currentBitmap.isRecycled) currentBitmap.width else screenW
                        val baseH = if (currentBitmap != null && !currentBitmap.isRecycled) currentBitmap.height else screenH

                        val leftPx = (leftVal * baseW).toInt()
                        val topPx = (topVal * baseH).toInt()
                        val rightPx = (rightVal * baseW).toInt()
                        val bottomPx = (bottomVal * baseH).toInt()

                        val widthPx = (rightPx - leftPx).coerceAtLeast(0)
                        val heightPx = (bottomPx - topPx).coerceAtLeast(0)

                        val screenRatio = if (currentBitmap != null && !currentBitmap.isRecycled) {
                            currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
                        } else if (screenH > 0) {
                            screenW.toFloat() / screenH.toFloat()
                        } else {
                            9f / 16f
                        }

                        val previewHeightDp = 220.dp
                        val previewWidthDp = previewHeightDp * screenRatio

                        // 迷你全屏示意缩略图
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(previewWidthDp)
                                    .height(previewHeightDp)
                                    .background(Color(0xFF202124), RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (currentBitmap != null && !currentBitmap.isRecycled) {
                                        drawImage(
                                            image = currentBitmap.asImageBitmap(),
                                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                        )
                                    } else {
                                        drawRect(color = Color(0xFF202124))
                                    }

                                    // 仅在用户实际画框或微调设定了局部选区后才绘制高亮绿框
                                    if (hasCustomRegion) {
                                        val l = leftVal * size.width
                                        val t = topVal * size.height
                                        val w = (rightVal - leftVal).coerceAtLeast(0f) * size.width
                                        val h = (bottomVal - topVal).coerceAtLeast(0f) * size.height

                                        if (w > 0f && h > 0f) {
                                            drawRect(
                                                color = Color(0xFF00E676).copy(alpha = 0.30f),
                                                topLeft = Offset(l, t),
                                                size = Size(w, h)
                                            )
                                            drawRect(
                                                color = Color(0xFF00E676),
                                                topLeft = Offset(l, t),
                                                size = Size(w, h),
                                                style = Stroke(width = 3f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (hasCustomRegion) {
                                Text(
                                    text = stringResource(R.string.region_pixel_range_fmt, leftPx, rightPx, topPx, bottomPx),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.region_pixel_size_fmt, widthPx, heightPx, baseW, baseH),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.region_not_configured),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.region_full_image_fmt, baseW, baseH),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 双按钮协同布局：在当前底图上重新框选 vs 截取新屏幕画框 (上下垂直排列)
                        if (currentBitmap != null && !currentBitmap.isRecycled) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onLaunchInPlaceRegionDraw(
                                            currentBitmap,
                                            restrictRegion,
                                            regionLeft,
                                            regionTop,
                                            regionRight,
                                            regionBottom
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.region_draw_current_img), fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onLaunchScreenCapture(
                                            restrictRegion,
                                            regionLeft,
                                            regionTop,
                                            regionRight,
                                            regionBottom
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.region_draw_new_screen), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    onLaunchScreenCapture(
                                        restrictRegion,
                                        regionLeft,
                                        regionTop,
                                        regionRight,
                                        regionBottom
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.region_draw), fontWeight = FontWeight.Bold)
                            }
                        }

                        // 可折叠高级像素手动微调
                        var showAdvancedInput by remember { mutableStateOf(false) }
                        var pxLeftText by remember(regionLeft, baseW) { mutableStateOf(leftPx.toString()) }
                        var pxTopText by remember(regionTop, baseH) { mutableStateOf(topPx.toString()) }
                        var pxRightText by remember(regionRight, baseW) { mutableStateOf(rightPx.toString()) }
                        var pxBottomText by remember(regionBottom, baseH) { mutableStateOf(bottomPx.toString()) }

                        TextButton(
                            onClick = { showAdvancedInput = !showAdvancedInput },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(if (showAdvancedInput) stringResource(R.string.region_advanced_hide_px) else stringResource(R.string.region_advanced_show_px))
                        }

                        AnimatedVisibility(visible = showAdvancedInput) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pxLeftText,
                                        onValueChange = { input ->
                                            pxLeftText = input
                                            input.toFloatOrNull()?.let { px ->
                                                if (baseW > 0) {
                                                    regionLeft = String.format(java.util.Locale.US, "%.4f", (px / baseW).coerceIn(0f, 1f))
                                                    hasCustomRegion = true
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.region_left_px)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = pxTopText,
                                        onValueChange = { input ->
                                            pxTopText = input
                                            input.toFloatOrNull()?.let { px ->
                                                if (baseH > 0) {
                                                    regionTop = String.format(java.util.Locale.US, "%.4f", (px / baseH).coerceIn(0f, 1f))
                                                    hasCustomRegion = true
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.region_top_px)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pxRightText,
                                        onValueChange = { input ->
                                            pxRightText = input
                                            input.toFloatOrNull()?.let { px ->
                                                if (baseW > 0) {
                                                    regionRight = String.format(java.util.Locale.US, "%.4f", (px / baseW).coerceIn(0f, 1f))
                                                    hasCustomRegion = true
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.region_right_px)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = pxBottomText,
                                        onValueChange = { input ->
                                            pxBottomText = input
                                            input.toFloatOrNull()?.let { px ->
                                                if (baseH > 0) {
                                                    regionBottom = String.format(java.util.Locale.US, "%.4f", (px / baseH).coerceIn(0f, 1f))
                                                    hasCustomRegion = true
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.region_bottom_px)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
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
                            text = stringResource(R.string.section_output_variables),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    VariableRow(name = "%ocr_full_text", desc = stringResource(R.string.var_desc_full_text))
                    VariableRow(name = "%ocr_json", desc = stringResource(R.string.var_desc_json))
                    VariableRow(name = "%match_found", desc = stringResource(R.string.var_desc_match_found))
                    VariableRow(name = "%match_center_x", desc = stringResource(R.string.var_desc_match_center_x))
                    VariableRow(name = "%match_center_y", desc = stringResource(R.string.var_desc_match_center_y))
                    VariableRow(name = "%errmsg", desc = stringResource(R.string.var_desc_errmsg))
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
                        text = stringResource(R.string.perm_overlay_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = stringResource(R.string.perm_overlay_desc),
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
                    Text(stringResource(R.string.perm_overlay_btn))
                }
            }
        }
    }
}

@Composable
fun StoragePermissionCheckCard(context: Context) {
    fun checkHasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    var hasPermission by remember { mutableStateOf(checkHasStoragePermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkHasStoragePermission()
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
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.perm_storage_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Text(
                    text = stringResource(R.string.perm_storage_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.perm_storage_btn))
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionCheckCard(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

    fun checkNotificationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(checkNotificationPermission()) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkNotificationPermission()
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
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.perm_notification_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Text(
                    text = stringResource(R.string.perm_notification_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.perm_notification_btn))
                }
            }
        }
    }
}

@Composable
fun AccessibilityPermissionCheckCard(context: Context) {
    var isRunning by remember { mutableStateOf(OcrAccessibilityService.isServiceRunning()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isRunning = OcrAccessibilityService.isServiceRunning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isRunning) {
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
                        text = stringResource(R.string.perm_accessibility_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = stringResource(R.string.perm_accessibility_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.perm_accessibility_btn))
                }
            }
        }
    }
}
