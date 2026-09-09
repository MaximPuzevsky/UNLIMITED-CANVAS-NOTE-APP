/*
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.BrushType
import com.example.model.ToolSlot
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ToolWheelControlPopup {
    SIZE,
    OPACITY,
    SMOOTHING
}

@Composable
fun ToolWheel(
    toolSlots: List<ToolSlot>,
    activeSlotIndex: Int,
    activeColor: Int,
    activeBrush: BrushType,
    activeSize: Float,
    activeOpacity: Float,
    activeSmoothing: Float,
    onSelectSlot: (Int) -> Unit,
    onChangeBrushType: (BrushType) -> Unit,
    onOpenColorWheel: () -> Unit,
    onSelectColor: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSmoothingChange: (Float) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(16f) }
    var offsetY by remember { mutableFloatStateOf(120f) }

    var activePopup by remember { mutableStateOf<ToolWheelControlPopup?>(null) }
    var showBrushDialog by remember { mutableStateOf(false) }

    val spectrumColors = remember {
        listOf(
            Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
            Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF),
            Color(0xFF8B00FF), Color(0xFFFF0000)
        )
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .testTag("concepts_tool_wheel")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(290.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                            offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                        }
                    }
            ) {
                // 1. OUTER RING BACKDROP (Diameter 290dp)
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF13151F).copy(alpha = 0.95f),
                    shadowElevation = 18.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2C3144)),
                    modifier = Modifier.size(290.dp)
                ) {}

                // 2. MIDDLE RING BACKDROP (Diameter 196dp)
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF191C28).copy(alpha = 0.97f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33394D)),
                    modifier = Modifier.size(196.dp)
                ) {}

                // =========================================================================
                // POSITION CALCULATIONS: OUTER RING (Radius = 122dp)
                // Positioned midway between outer ring edge (145dp) and middle ring edge (98dp)
                // =========================================================================
                val outerRadius = 122f

                // UNDO (Upper-left)
                val undoAngleRad = Math.toRadians(210.0)
                val undoX = (outerRadius * cos(undoAngleRad)).toFloat()
                val undoY = (outerRadius * sin(undoAngleRad)).toFloat()

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF202330),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF3F465C)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .offset { IntOffset(undoX.roundToInt(), undoY.roundToInt()) }
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onUndo() }
                        .testTag("outer_slot_undo")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo Action",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // REDO (Lower-left)
                val redoAngleRad = Math.toRadians(150.0)
                val redoX = (outerRadius * cos(redoAngleRad)).toFloat()
                val redoY = (outerRadius * sin(redoAngleRad)).toFloat()

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF202330),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF3F465C)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .offset { IntOffset(redoX.roundToInt(), redoY.roundToInt()) }
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onRedo() }
                        .testTag("outer_slot_redo")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo Action",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tool Slots along Outer Ring
                val toolSlotAngles = listOf(-90.0, -45.0, 0.0, 30.0, 60.0, 90.0)

                for (idx in toolSlotAngles.indices) {
                    val slotIndex = idx.coerceAtMost(toolSlots.size - 1)
                    val slot = toolSlots[slotIndex]
                    val isSelected = (slotIndex == activeSlotIndex)
                    val angleRad = Math.toRadians(toolSlotAngles[idx])
                    val slotX = (outerRadius * cos(angleRad)).toFloat()
                    val slotY = (outerRadius * sin(angleRad)).toFloat()

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(slotX.roundToInt(), slotY.roundToInt()) }
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF2B3349) else Color(0xFF1E212E))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF3B4055),
                                shape = CircleShape
                            )
                            .pointerInput(slotIndex, isSelected) {
                                detectTapGestures(
                                    onTap = {
                                        if (isSelected) showBrushDialog = true
                                        else onSelectSlot(slotIndex)
                                    }
                                )
                            }
                    ) {
                        Icon(
                            imageVector = getBrushIcon(slot.brushType),
                            contentDescription = slot.brushType.displayName,
                            tint = if (slot.brushType.isUtility) Color(0xFFCBD5E1) else Color(slot.color),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // =========================================================================
                // POSITION CALCULATIONS: MIDDLE RING (Radius = 68dp)
                // Positioned midway between middle ring edge (98dp) and inner circle (48dp)
                // =========================================================================
                val middleRadius = 68f

                // Size Button (-40°)
                val sizeAngleRad = Math.toRadians(-40.0)
                val sizeX = (middleRadius * cos(sizeAngleRad)).toFloat()
                val sizeY = (middleRadius * sin(sizeAngleRad)).toFloat()
                val isSizeActive = (activePopup == ToolWheelControlPopup.SIZE)

                Surface(
                    shape = CircleShape,
                    color = if (isSizeActive) Color(0xFF273347) else Color(0xFF222635),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSizeActive) 1.5.dp else 1.dp,
                        color = if (isSizeActive) Color(0xFF48CAE4) else Color(0xFF394056)
                    ),
                    modifier = Modifier
                        .offset { IntOffset(sizeX.roundToInt(), sizeY.roundToInt()) }
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { activePopup = if (isSizeActive) null else ToolWheelControlPopup.SIZE }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SizeVisualIcon(isActive = isSizeActive, sizePt = activeSize, modifier = Modifier.size(20.dp))
                    }
                }

                // Opacity Button (10°)
                val opacityAngleRad = Math.toRadians(10.0)
                val opacityX = (middleRadius * cos(opacityAngleRad)).toFloat()
                val opacityY = (middleRadius * sin(opacityAngleRad)).toFloat()
                val isOpacityActive = (activePopup == ToolWheelControlPopup.OPACITY)

                Surface(
                    shape = CircleShape,
                    color = if (isOpacityActive) Color(0xFF273347) else Color(0xFF222635),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isOpacityActive) 1.5.dp else 1.dp,
                        color = if (isOpacityActive) Color(0xFF48CAE4) else Color(0xFF394056)
                    ),
                    modifier = Modifier
                        .offset { IntOffset(opacityX.roundToInt(), opacityY.roundToInt()) }
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { activePopup = if (isOpacityActive) null else ToolWheelControlPopup.OPACITY }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        OpacityVisualIcon(isActive = isOpacityActive, opacity = activeOpacity, modifier = Modifier.size(20.dp))
                    }
                }

                // Smoothing Button (60°)
                val smoothAngleRad = Math.toRadians(60.0)
                val smoothX = (middleRadius * cos(smoothAngleRad)).toFloat()
                val smoothY = (middleRadius * sin(smoothAngleRad)).toFloat()
                val isSmoothActive = (activePopup == ToolWheelControlPopup.SMOOTHING)

                Surface(
                    shape = CircleShape,
                    color = if (isSmoothActive) Color(0xFF273347) else Color(0xFF222635),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSmoothActive) 1.5.dp else 1.dp,
                        color = if (isSmoothActive) Color(0xFF48CAE4) else Color(0xFF394056)
                    ),
                    modifier = Modifier
                        .offset { IntOffset(smoothX.roundToInt(), smoothY.roundToInt()) }
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { activePopup = if (isSmoothActive) null else ToolWheelControlPopup.SMOOTHING }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SmoothingVisualIcon(isActive = isSmoothActive, modifier = Modifier.size(20.dp))
                    }
                }

                // =========================================================================
                // 3. INNER CENTER DISC (Diameter 96dp): Triggers Color Wheel Popup
                // =========================================================================
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clickable { onOpenColorWheel() }
                ) {
                    Canvas(modifier = Modifier.size(96.dp)) {
                        drawCircle(
                            brush = Brush.sweepGradient(spectrumColors),
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(activeColor))
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Color Wheel",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // POPUP SLIDER ANCHORED TO RIGHT
            AnimatedVisibility(
                visible = activePopup != null,
                enter = fadeIn() + slideInHorizontally { -20 },
                exit = fadeOut() + slideOutHorizontally { -20 }
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1B1D29).copy(alpha = 0.98f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF383F56)),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(210.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        when (activePopup) {
                            ToolWheelControlPopup.SIZE -> {
                                ModularSliderCard(
                                    title = "Stroke Size",
                                    valueDisplay = "${String.format(Locale.US, "%.1f", activeSize)} pt",
                                    value = activeSize,
                                    valueRange = 0.5f..48f,
                                    onValueChange = onSizeChange,
                                    activeColor = Color(0xFF48CAE4),
                                    presets = listOf(1f, 3.5f, 8f, 16f, 32f),
                                    presetFormatter = { "${it.roundToInt()}pt" },
                                    onSelectPreset = onSizeChange,
                                    onClose = { activePopup = null }
                                )
                            }
                            ToolWheelControlPopup.OPACITY -> {
                                ModularSliderCard(
                                    title = "Opacity",
                                    valueDisplay = "${(activeOpacity * 100).roundToInt()}%",
                                    value = activeOpacity,
                                    valueRange = 0.05f..1.0f,
                                    onValueChange = onOpacityChange,
                                    activeColor = Color(0xFF0077B6),
                                    presets = listOf(0.25f, 0.50f, 0.75f, 1.0f),
                                    presetFormatter = { "${(it * 100).roundToInt()}%" },
                                    onSelectPreset = onOpacityChange,
                                    onClose = { activePopup = null }
                                )
                            }
                            ToolWheelControlPopup.SMOOTHING -> {
                                ModularSliderCard(
                                    title = "Smoothing",
                                    valueDisplay = "${(activeSmoothing * 100).roundToInt()}%",
                                    value = activeSmoothing,
                                    valueRange = 0.0f..1.0f,
                                    onValueChange = onSmoothingChange,
                                    activeColor = Color(0xFF90E0EF),
                                    presets = listOf(0.0f, 0.20f, 0.50f, 1.0f),
                                    presetFormatter = { if (it == 0f) "Off" else "${(it * 100).roundToInt()}%" },
                                    onSelectPreset = onSmoothingChange,
                                    onClose = { activePopup = null }
                                )
                            }
                            null -> {}
                        }
                    }
                }
            }
        }

        if (showBrushDialog) {
            BrushSelectionDialog(
                currentBrush = activeBrush,
                onSelectBrush = {
                    onChangeBrushType(it)
                    showBrushDialog = false
                },
                onDismiss = { showBrushDialog = false }
            )
        }
    }
}

@Composable
fun SizeVisualIcon(isActive: Boolean, sizePt: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFCBD5E1)
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width / 2f - 2.dp.toPx()

        drawCircle(color = strokeColor, radius = maxRadius, center = center, style = Stroke(width = 1.6.dp.toPx()))
        val innerRadius = (maxRadius * (sizePt / 48f).coerceIn(0.25f, 0.85f))
        drawCircle(color = strokeColor, radius = innerRadius, center = center)
    }
}

@Composable
fun OpacityVisualIcon(isActive: Boolean, opacity: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ringColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFCBD5E1)
        val fillColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFE2E8F0)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f - 2.dp.toPx()

        drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 1.6.dp.toPx()))
        drawArc(
            color = fillColor.copy(alpha = opacity.coerceIn(0.2f, 1.0f)),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f)
        )
    }
}

@Composable
fun SmoothingVisualIcon(isActive: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFCBD5E1)
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.80f)
            cubicTo(w * 0.30f, h * 0.20f, w * 0.70f, h * 0.85f, w * 0.85f, h * 0.22f)
        }

        drawPath(path = path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = lineColor, radius = 1.8.dp.toPx(), center = Offset(w * 0.85f, h * 0.22f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModularSliderCard(
    title: String,
    valueDisplay: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    activeColor: Color,
    presets: List<Float>,
    presetFormatter: (Float) -> String,
    onSelectPreset: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = title, color = activeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(text = valueDisplay, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = activeColor, activeTrackColor = activeColor, inactiveTrackColor = Color(0xFF2E354A))
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                val isSelected = (value - preset).let { kotlin.math.abs(it) < 0.02f }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) activeColor else Color(0xFF262A3C),
                    modifier = Modifier.clickable { onSelectPreset(preset) }
                ) {
                    Text(
                        text = presetFormatter(preset),
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BrushSelectionDialog(
    currentBrush: BrushType,
    onSelectBrush: (BrushType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF181A26),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF333A50)),
            modifier = Modifier.width(320.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Select Instrument", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(BrushType.entries.toTypedArray()) { brush ->
                        val isSelected = (brush == currentBrush)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF273347) else Color(0xFF202332),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF2E3448)
                            ),
                            modifier = Modifier.clickable { onSelectBrush(brush) }.padding(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                                Icon(
                                    imageVector = getBrushIcon(brush),
                                    contentDescription = brush.displayName,
                                    tint = if (isSelected) Color(0xFF48CAE4) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = brush.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282E42)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

private fun getBrushIcon(brushType: BrushType): ImageVector {
    return when (brushType) {
        BrushType.PEN -> Icons.Default.Create
        BrushType.PENCIL -> Icons.Default.Edit
        BrushType.MARKER -> Icons.Default.ModeEdit
        BrushType.HIGHLIGHTER -> Icons.Default.Highlight
        BrushType.WATERCOLOR -> Icons.Default.WaterDrop
        BrushType.AIRBRUSH -> Icons.Default.Air
        BrushType.ERASER -> Icons.Default.RadioButtonUnchecked
        BrushType.LASSO -> Icons.Default.CropFree
        BrushType.SLICE -> Icons.Default.ContentCut
    }
}

*/