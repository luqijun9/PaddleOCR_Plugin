package com.paddle.ocr.demo.plugin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

class RegionDrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bitmap = FloatingSelectionService.captureBitmap
        if (bitmap == null) {
            finish()
            return
        }

        setContent {
            var startOffset by remember { mutableStateOf<Offset?>(null) }
            var endOffset by remember { mutableStateOf<Offset?>(null) }
            var containerSize by remember { mutableStateOf(Size.Zero) }

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startOffset = offset
                                    endOffset = offset
                                },
                                onDrag = { change, _ ->
                                    endOffset = change.position
                                }
                            )
                        }
                ) {
                    containerSize = size
                    
                    // Draw the captured screenshot
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )

                    // Draw semi-transparent dark overlay
                    drawRect(color = Color(0x88000000))

                    // Clear the selected region
                    if (startOffset != null && endOffset != null) {
                        val left = min(startOffset!!.x, endOffset!!.x)
                        val top = min(startOffset!!.y, endOffset!!.y)
                        val right = max(startOffset!!.x, endOffset!!.x)
                        val bottom = max(startOffset!!.y, endOffset!!.y)

                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            blendMode = BlendMode.Clear
                        )
                        
                        // Draw a border around it
                        drawRect(
                            color = Color.Green,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
                }

                // Bottom Buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { finish() }) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        if (startOffset != null && endOffset != null && containerSize.width > 0 && containerSize.height > 0) {
                            val left = min(startOffset!!.x, endOffset!!.x) / containerSize.width
                            val top = min(startOffset!!.y, endOffset!!.y) / containerSize.height
                            val right = max(startOffset!!.x, endOffset!!.x) / containerSize.width
                            val bottom = max(startOffset!!.y, endOffset!!.y) / containerSize.height

                            val intent = Intent(this@RegionDrawActivity, ActionEditActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                putExtra("REGION_RESULT", floatArrayOf(left, top, right, bottom))
                            }
                            startActivity(intent)
                        }
                        finish()
                    }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        FloatingSelectionService.captureBitmap = null
    }
}
