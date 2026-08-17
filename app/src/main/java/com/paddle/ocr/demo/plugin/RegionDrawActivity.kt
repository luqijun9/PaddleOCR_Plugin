package com.paddle.ocr.demo.plugin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paddle.ocr.demo.R
import com.paddle.ocr.demo.ui.theme.PPOCRTheme
import kotlin.math.max
import kotlin.math.min

enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    RIGHT_CENTER,
    BOTTOM_RIGHT,
    BOTTOM_CENTER,
    BOTTOM_LEFT,
    LEFT_CENTER,
    INSIDE_MOVE,
    OUTSIDE_NEW
}

class RegionDrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bitmap = FloatingSelectionService.captureBitmap
        if (bitmap == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            PPOCRTheme {
                var selectionRect by remember { mutableStateOf<Rect?>(null) }
                var containerSize by remember { mutableStateOf(Size.Zero) }

                var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
                var dragStartPos by remember { mutableStateOf(Offset.Zero) }
                var initialRectOnDrag by remember { mutableStateOf<Rect?>(null) }

                val density = LocalDensity.current
                val handleTouchRadiusPx = with(density) { 36.dp.toPx() }
                val minBoxSizePx = with(density) { 32.dp.toPx() }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { downPos ->
                                        dragStartPos = downPos
                                        val currentRect = selectionRect
                                        if (currentRect == null) {
                                            activeHandle = CropHandle.OUTSIDE_NEW
                                            selectionRect = Rect(downPos.x, downPos.y, downPos.x, downPos.y)
                                        } else {
                                            activeHandle = getHitHandle(downPos, currentRect, handleTouchRadiusPx)
                                            if (activeHandle == CropHandle.OUTSIDE_NEW) {
                                                selectionRect = Rect(downPos.x, downPos.y, downPos.x, downPos.y)
                                            } else {
                                                initialRectOnDrag = currentRect
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val width = containerSize.width
                                        val height = containerSize.height
                                        if (width <= 0 || height <= 0) return@detectDragGestures

                                        val currPos = change.position
                                        val initR = initialRectOnDrag

                                        when (activeHandle) {
                                            CropHandle.OUTSIDE_NEW -> {
                                                val left = min(dragStartPos.x, currPos.x).coerceIn(0f, width)
                                                val top = min(dragStartPos.y, currPos.y).coerceIn(0f, height)
                                                val right = max(dragStartPos.x, currPos.x).coerceIn(0f, width)
                                                val bottom = max(dragStartPos.y, currPos.y).coerceIn(0f, height)
                                                selectionRect = Rect(left, top, right, bottom)
                                            }
                                            CropHandle.INSIDE_MOVE -> {
                                                if (initR != null) {
                                                    val dx = currPos.x - dragStartPos.x
                                                    val dy = currPos.y - dragStartPos.y
                                                    val rectW = initR.width
                                                    val rectH = initR.height

                                                    var newLeft = initR.left + dx
                                                    var newTop = initR.top + dy

                                                    if (newLeft < 0f) newLeft = 0f
                                                    if (newTop < 0f) newTop = 0f
                                                    if (newLeft + rectW > width) newLeft = width - rectW
                                                    if (newTop + rectH > height) newTop = height - rectH

                                                    selectionRect = Rect(newLeft, newTop, newLeft + rectW, newTop + rectH)
                                                }
                                            }
                                            CropHandle.TOP_LEFT -> {
                                                if (initR != null) {
                                                    val newLeft = min(currPos.x, initR.right - minBoxSizePx).coerceIn(0f, width)
                                                    val newTop = min(currPos.y, initR.bottom - minBoxSizePx).coerceIn(0f, height)
                                                    selectionRect = Rect(newLeft, newTop, initR.right, initR.bottom)
                                                }
                                            }
                                            CropHandle.TOP_CENTER -> {
                                                if (initR != null) {
                                                    val newTop = min(currPos.y, initR.bottom - minBoxSizePx).coerceIn(0f, height)
                                                    selectionRect = Rect(initR.left, newTop, initR.right, initR.bottom)
                                                }
                                            }
                                            CropHandle.TOP_RIGHT -> {
                                                if (initR != null) {
                                                    val newRight = max(currPos.x, initR.left + minBoxSizePx).coerceIn(0f, width)
                                                    val newTop = min(currPos.y, initR.bottom - minBoxSizePx).coerceIn(0f, height)
                                                    selectionRect = Rect(initR.left, newTop, newRight, initR.bottom)
                                                }
                                            }
                                            CropHandle.RIGHT_CENTER -> {
                                                if (initR != null) {
                                                    val newRight = max(currPos.x, initR.left + minBoxSizePx).coerceIn(0f, width)
                                                    selectionRect = Rect(initR.left, initR.top, newRight, initR.bottom)
                                                }
                                            }
                                            CropHandle.BOTTOM_RIGHT -> {
                                                if (initR != null) {
                                                    val newRight = max(currPos.x, initR.left + minBoxSizePx).coerceIn(0f, width)
                                                    val newBottom = max(currPos.y, initR.top + minBoxSizePx).coerceIn(0f, height)
                                                    selectionRect = Rect(initR.left, initR.top, newRight, newBottom)
                                                }
                                            }
                                            CropHandle.BOTTOM_CENTER -> {
                                                if (initR != null) {
                                                    val newBottom = max(currPos.y, initR.top + minBoxSizePx).coerceIn(0f, height)
                                                    selectionRect = Rect(initR.left, initR.top, initR.right, newBottom)
                                                }
                                            }
                                            CropHandle.BOTTOM_LEFT -> {
                                                if (initR != null) {
                                                    val newLeft = min(currPos.x, initR.right - minBoxSizePx).coerceIn(0f, width)
                                                    val newBottom = max(currPos.y, initR.top + minBoxSizePx).coerceIn(0f, height)
                                                    selectionRect = Rect(newLeft, initR.top, initR.right, newBottom)
                                                }
                                            }
                                            CropHandle.LEFT_CENTER -> {
                                                if (initR != null) {
                                                    val newLeft = min(currPos.x, initR.right - minBoxSizePx).coerceIn(0f, width)
                                                    selectionRect = Rect(newLeft, initR.top, initR.right, initR.bottom)
                                                }
                                            }
                                            CropHandle.NONE -> {}
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandle = CropHandle.NONE
                                        initialRectOnDrag = null
                                    },
                                    onDragCancel = {
                                        activeHandle = CropHandle.NONE
                                        initialRectOnDrag = null
                                    }
                                )
                            }
                    ) {
                        containerSize = size

                        // 绘制截屏背景
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                        )

                        // 绘制半透明黑色遮罩 (EvenOdd 挖空选区)
                        val path = Path().apply {
                            addRect(Rect(0f, 0f, size.width, size.height))
                            val rect = selectionRect
                            if (rect != null && rect.width > 0 && rect.height > 0) {
                                addRect(rect)
                            }
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(path = path, color = Color(0x99000000))

                        // 绘制选区边框与 8 个控制手柄
                        val rect = selectionRect
                        if (rect != null && rect.width > 0 && rect.height > 0) {
                            drawRect(
                                color = Color(0xFF00E676),
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width, rect.height),
                                style = Stroke(width = 5f)
                            )

                            val midX = (rect.left + rect.right) / 2f
                            val midY = (rect.top + rect.bottom) / 2f

                            val handlePoints = listOf(
                                Offset(rect.left, rect.top),
                                Offset(midX, rect.top),
                                Offset(rect.right, rect.top),
                                Offset(rect.right, midY),
                                Offset(rect.right, rect.bottom),
                                Offset(midX, rect.bottom),
                                Offset(rect.left, rect.bottom),
                                Offset(rect.left, midY)
                            )

                            val handleRadius = 15f
                            for (pt in handlePoints) {
                                drawCircle(color = Color(0xFF00E676), radius = handleRadius, center = pt)
                                drawCircle(color = Color.White, radius = handleRadius - 4f, center = pt)
                            }
                        }
                    }

                    // 底部操作栏
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                FloatingSelectionService.captureBitmap = null
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xCC000000),
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.region_draw_cancel), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val rect = selectionRect
                                if (rect != null && containerSize.width > 0 && containerSize.height > 0 && rect.width > 0 && rect.height > 0) {
                                    val left = (rect.left / containerSize.width).coerceIn(0f, 1f)
                                    val top = (rect.top / containerSize.height).coerceIn(0f, 1f)
                                    val right = (rect.right / containerSize.width).coerceIn(0f, 1f)
                                    val bottom = (rect.bottom / containerSize.height).coerceIn(0f, 1f)

                                    val resultIntent = Intent().apply {
                                        putExtra("REGION_RESULT", floatArrayOf(left, top, right, bottom))
                                    }
                                    FloatingSelectionService.captureBitmap = null
                                    setResult(Activity.RESULT_OK, resultIntent)
                                } else {
                                    setResult(Activity.RESULT_CANCELED)
                                }
                                finish()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(stringResource(R.string.region_draw_confirm), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    private fun getHitHandle(touch: Offset, rect: Rect, radius: Float): CropHandle {
        val midX = (rect.left + rect.right) / 2f
        val midY = (rect.top + rect.bottom) / 2f

        if ((touch - Offset(rect.left, rect.top)).getDistance() <= radius) return CropHandle.TOP_LEFT
        if ((touch - Offset(midX, rect.top)).getDistance() <= radius) return CropHandle.TOP_CENTER
        if ((touch - Offset(rect.right, rect.top)).getDistance() <= radius) return CropHandle.TOP_RIGHT
        if ((touch - Offset(rect.right, midY)).getDistance() <= radius) return CropHandle.RIGHT_CENTER
        if ((touch - Offset(rect.right, rect.bottom)).getDistance() <= radius) return CropHandle.BOTTOM_RIGHT
        if ((touch - Offset(midX, rect.bottom)).getDistance() <= radius) return CropHandle.BOTTOM_CENTER
        if ((touch - Offset(rect.left, rect.bottom)).getDistance() <= radius) return CropHandle.BOTTOM_LEFT
        if ((touch - Offset(rect.left, midY)).getDistance() <= radius) return CropHandle.LEFT_CENTER

        if (rect.contains(touch)) return CropHandle.INSIDE_MOVE

        return CropHandle.OUTSIDE_NEW
    }
}
