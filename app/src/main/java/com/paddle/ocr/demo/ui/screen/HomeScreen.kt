// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.demo.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paddle.ocr.demo.ui.component.*
import com.paddle.ocr.demo.ui.viewmodel.OCRViewModel

@Composable
fun HomeScreen(viewModel: OCRViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val timing by viewModel.timing.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is OCRViewModel.UIState.Loading -> {
                LoadingOverlay("Loading OCR models...")
            }
            is OCRViewModel.UIState.Ready -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OverlayPermissionBanner(context)
                    NotificationPermissionBanner(context)
                    Spacer(modifier = Modifier.height(16.dp))
                    ImagePicker(
                        onGalleryClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onScreenshotClick = { viewModel.startScreenshotTest(context) },
                        onSampleClick = { viewModel.onSampleImageClicked(it) },
                        sampleImages = emptyList(),
                    )
                }
            }
            is OCRViewModel.UIState.Processing -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ImagePreview(bitmap = s.bitmap, results = emptyList())
                    Spacer(modifier = Modifier.height(16.dp))
                    LoadingOverlay("Processing...")
                }
            }
            is OCRViewModel.UIState.Result -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ImagePreview(
                        bitmap = s.bitmap,
                        results = s.result.results,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (timing != null) {
                        TimingBar(
                            detectionMs = timing!!.detectionMs,
                            recognitionMs = timing!!.recognitionMs,
                            totalMs = timing!!.totalMs,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ResultList(
                        results = s.result.results,
                        onCopyAll = { viewModel.copyAllResults(s.result.results) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ImagePicker(
                        onGalleryClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onScreenshotClick = { viewModel.startScreenshotTest(context) },
                        onSampleClick = { viewModel.onSampleImageClicked(it) },
                        sampleImages = emptyList(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            is OCRViewModel.UIState.Error -> {
                LoadingOverlay("Error occurred")
                ErrorDialog(
                    message = s.message,
                    onRetry = { viewModel.retry() },
                    onDismiss = { viewModel.retry() },
                )
            }
        }
    }
}

@Composable
fun NotificationPermissionBanner(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

    fun checkNotificationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(checkNotificationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
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
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📢 建议开启通知权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "开启通知权限后，插件可常驻通知栏实时显示就绪状态、OCR 识别耗时与匹配结果，同时提升系统后台存活优先级。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text("一键开启通知权限")
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionBanner(context: Context) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚠️ 未开启后台弹出/悬浮窗权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "为了让 MacroDroid / Tasker 插件在后台能自动弹出录屏授权，请开启“显示在其他应用上层”（部分机型如小米/vivo还需在系统权限管理中开启“后台弹出界面”）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("一键前往授权")
                }
            }
        }
    }
}

