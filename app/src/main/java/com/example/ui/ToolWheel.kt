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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.InvertColors
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

/**
 * Control popup enum to ensure strictly ONE modular control popup is open at a time.
 */
enum class ToolWheelControlPopup {
    SIZE,
    OPACITY,
    SMOOTHING
}

/**
 * Concepts 1:1 Architectural 3-Ring Concentric Floating Instrument Wheel:
 *
 * 1. OUTER RING (Diameter 290dp):
 *    - All drawing/editing tool slots sit strictly in the outer ring band (radius ~120dp).
 *    - Guaranteed NO overlap or trapping into the middle ring.
 *    - Leftmost Slot Overrides: Undo (Back Arrow) and Redo (Forward Arrow) are placed specifically
 *      in the two leftmost slots of the outer circle.
 *
 * 2. MIDDLE RING (Diameter 196dp):
 *    - Tactile visual icons (Concentric circle for Size, Half-filled circle for Opacity, Curved streamline for Smoothing).
 *    - Centered precisely in the middle ring segment (radius ~74dp).
 *    - Tapping an icon opens ONLY its respective modular slider popup anchored immediately to the RIGHT of the wheel.
 *
 * 3. INNER CIRCLE (Diameter 96dp):
 *    - Active color disc and spectrum border for the selected tool slot.
 *    - Tapping opens the multi-tiered infinite-rotating COPIC radial color wheel.
 */
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
    // Draggable position state
    var offsetX by remember { mutableFloatStateOf(16f) }
    var offsetY by remember { mutableFloatStateOf(120f) }

    // Modular popup control (Only ONE open at a time)
    var activePopup by remember { mutableStateOf<ToolWheelControlPopup?>(null) }
    var showBrushDialog by remember { mutableStateOf(false) }

    val spectrumColors = remember {
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFF7F00),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFF8B00FF),
            Color(0xFFFF0000)
        )
    }

    // Outer container with draggable offset
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .testTag("concepts_tool_wheel")
    ) {
        // Horizontal row so modular slider popup is anchored immediately to the RIGHT of the wheel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
        ) {
            // =========================================================================
            // 3-RING CONCENTRIC TOOL WHEEL (Diameter 290dp)
            // =========================================================================
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
                // ---------------------------------------------------------------------
                // 1. OUTER RING BACKDROP (Diameter 290dp)
                // ---------------------------------------------------------------------
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF13151F).copy(alpha = 0.95f),
                    shadowElevation = 18.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2C3144)),
                    modifier = Modifier.size(290.dp)
                ) {}

                // ---------------------------------------------------------------------
                // 1. OUTER RING: Radial Slots (Radius = 120dp)
                // Guaranteed fully inside outer band (radius 98dp to 145dp).
                // Leftmost two slots: UNDO (upper-left, ~215°) & REDO (lower-left, ~145°).
                // Remaining 6 slots: Active tool instruments.
                // ---------------------------------------------------------------------
                val outerRadius = 120f

                // Leftmost Slot 1: UNDO (Upper-left, 215°)
                val undoAngleRad = Math.toRadians(215.0)
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

                // Leftmost Slot 2: REDO (Lower-left, 145°)
                val redoAngleRad = Math.toRadians(145.0)
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

                // Remaining 6 outer slots for Drawing/Editing Tools
                // Distributed around the remaining perimeter: -90° (Top), -45°, 0° (Right), 45°, 90° (Bottom), 270°/etc.
                val toolSlotAngles = listOf(
                    -90.0, // Top
                    -45.0, // Top-Right
                    0.0,   // Right
                    45.0,  // Bottom-Right
                    90.0,  // Bottom
                    -135.0 // Top-Far-Left
                )

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
                            .background(
                                if (isSelected) Color(0xFF2B3349) else Color(0xFF1E212E)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF3B4055),
                                shape = CircleShape
                            )
                            .pointerInput(slotIndex, isSelected) {
                                detectTapGestures(
                                    onTap = {
                                        if (isSelected) {
                                            showBrushDialog = true
                                        } else {
                                            onSelectSlot(slotIndex)
                                        }
                                    },
                                    onDoubleTap = {
                                        onSelectSlot(slotIndex)
                                        showBrushDialog = true
                                    },
                                    onLongPress = {
                                        onSelectSlot(slotIndex)
                                        showBrushDialog = true
                                    }
                                )
                            }
                            .testTag("tool_slot_$slotIndex")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getBrushIcon(slot.brushType),
                                contentDescription = slot.brushType.displayName,
                                tint = if (slot.brushType.isUtility) Color(0xFFCBD5E1) else Color(slot.color),
                                modifier = Modifier.size(18.dp)
                            )
                            // Real-time active color dot indicator below icon
                            if (!slot.brushType.isUtility) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(slot.color))
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------------
                // 2. MIDDLE RING BACKDROP (Diameter 196dp)
                // ---------------------------------------------------------------------
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF191C28).copy(alpha = 0.97f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33394D)),
                    modifier = Modifier.size(196.dp)
                ) {}

                // ---------------------------------------------------------------------
                // 2. MIDDLE RING: Minimal Visual Icons (Radius = 74dp)
                // Text removed; replaced with pure minimalist tactile visual icons:
                // - Size Icon: Concentric stroke/circle indicator (-45°, Top-Right)
                // - Opacity Icon: Half-filled opacity circle (0°, Right)
                // - Smoothing Icon: Curved streamline line icon (45°, Bottom-Right)
                // ---------------------------------------------------------------------
                val middleRadius = 74f

                // Size Icon Button (-45°)
                val sizeAngleRad = Math.toRadians(-45.0)
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            activePopup = if (isSizeActive) null else ToolWheelControlPopup.SIZE
                        }
                        .testTag("param_control_SIZE")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SizeVisualIcon(
                            isActive = isSizeActive,
                            sizePt = activeSize,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Opacity Icon Button (0°, Right)
                val opacityAngleRad = Math.toRadians(0.0)
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            activePopup = if (isOpacityActive) null else ToolWheelControlPopup.OPACITY
                        }
                        .testTag("param_control_OPACITY")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        OpacityVisualIcon(
                            isActive = isOpacityActive,
                            opacity = activeOpacity,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Smoothing Icon Button (45°, Bottom-Right)
                val smoothAngleRad = Math.toRadians(45.0)
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            activePopup = if (isSmoothActive) null else ToolWheelControlPopup.SMOOTHING
                        }
                        .testTag("param_control_SMOOTH")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SmoothingVisualIcon(
                            isActive = isSmoothActive,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // ---------------------------------------------------------------------
                // 3. INNER CIRCLE (Diameter 96dp): Active Color Disc & Spectrum Ring
                // Tapping opens the multi-tiered infinite-rotating COPIC radial color wheel
                // ---------------------------------------------------------------------
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clickable { onOpenColorWheel() }
                        .testTag("tool_wheel_center_color")
                ) {
                    // Multi-hue continuous spectrum ring perimeter
                    Canvas(modifier = Modifier.size(96.dp)) {
                        drawCircle(
                            brush = Brush.sweepGradient(spectrumColors),
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    }

                    // Active solid color disc
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(activeColor))
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    ) {
                        val isLightColor = Color(activeColor).run { (red * 0.299 + green * 0.587 + blue * 0.114) > 0.6 }
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Open Radial COPIC Color Wheel",
                            tint = if (isLightColor) Color.Black.copy(alpha = 0.85f) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // MODULAR POPUP CONTROLS ANCHORED TO THE RIGHT OF WHEEL
            // Opens ONLY ONE slider popup at a time (Size, Opacity, or Smoothing)
            // =========================================================================
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
                        .testTag("modular_slider_popup")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
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

        // =========================================================================
        // BRUSH SELECTION DIALOG WITH VISUAL PRESSURE TAPER PREVIEWS
        // =========================================================================
        if (showBrushDialog) {
            BrushSelectionDialog(
                currentBrush = activeBrush,
                currentColor = activeColor,
                currentSize = activeSize,
                onSelectBrush = {
                    onChangeBrushType(it)
                    showBrushDialog = false
                },
                onDismiss = { showBrushDialog = false }
            )
        }
    }
}

/**
 * Minimalist Visual Icon: Concentric stroke/circle indicator for Size.
 */
@Composable
fun SizeVisualIcon(
    isActive: Boolean,
    sizePt: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFCBD5E1)
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width / 2f - 2.dp.toPx()

        // Outer concentric ring
        drawCircle(
            color = strokeColor,
            radius = maxRadius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
        )

        // Dynamic inner filled indicator based on current size
        val innerRadius = (maxRadius * (sizePt / 48f).coerceIn(0.25f, 0.85f))
        drawCircle(
            color = strokeColor,
            radius = innerRadius,
            center = center
        )
    }
}

/**
 * Minimalist Visual Icon: Half-filled opacity circle indicator.
 */
@Composable
fun OpacityVisualIcon(
    isActive: Boolean,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val ringColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFCBD5E1)
        val fillColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFE2E8F0)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f - 2.dp.toPx()

        // Outer circle outline
        drawCircle(
            color = ringColor,
            radius = radius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
        )

        // Right half filled arc
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

/**
 * Minimalist Visual Icon: Curved streamline line icon for Smoothing.
 */
@Composable
fun SmoothingVisualIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val lineColor = if (isActive) Color(0xFF48CAE4) else Color(0xFFCBD5E1)
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.80f)
            cubicTo(
                w * 0.30f, h * 0.20f,
                w * 0.70f, h * 0.85f,
                w * 0.85f, h * 0.22f
            )
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Small end tangent guide point
        drawCircle(
            color = lineColor,
            radius = 1.8.dp.toPx(),
            center = Offset(w * 0.85f, h * 0.22f)
        )
    }
}

/**
 * Modular slider popup card showing only the single active parameter.
 */
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
        // Header
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = activeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Numerical readout
        Text(
            text = valueDisplay,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Continuous Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = activeColor
            ),
            modifier = Modifier.height(26.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Preset Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                val isSelected = (Math.abs(preset - value) < 0.05f)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) activeColor.copy(alpha = 0.25f) else Color(0xFF232736),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) activeColor else Color(0xFF373E52)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectPreset(preset) }
                ) {
                    Text(
                        text = presetFormatter(preset),
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Brush selection modal showcasing all genuine Concepts brushes with visual pressure-to-size
 * taper preview images so users instantly see how each brush responds to S Pen dynamics.
 */
@Composable
fun BrushSelectionDialog(
    currentBrush: BrushType,
    currentColor: Int,
    currentSize: Float,
    onSelectBrush: (BrushType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBrush by remember { mutableStateOf(currentBrush) }

    val conceptsBrushes = listOf(
        BrushType.PEN,
        BrushType.SOFT_PENCIL,
        BrushType.HARD_PENCIL,
        BrushType.FOUNTAIN_PEN,
        BrushType.MARKER,
        BrushType.WATERCOLOR,
        BrushType.AIRBRUSH,
        BrushType.WIRE,
        BrushType.LASSO,
        BrushType.ERASER_HARD,
        BrushType.ERASER_SOFT,
        BrushType.SLICE,
        BrushType.NUDGE
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF161822),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353B4E)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("brush_selection_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Concepts Brushes & Tools",
                            color = Color(0xFFF1F5F9),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "S Pen hardware pressure-taper dynamics",
                            color = Color(0xFF48CAE4),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hero Pressure Taper Preview Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F1118),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282C3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        BrushPressureStrokePreview(
                            brushType = selectedBrush,
                            color = if (selectedBrush.isUtility) Color(0xFF48CAE4) else Color(currentColor),
                            maxSize = currentSize,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Light Pressure (Thin)", color = Color(0xFF64748B), fontSize = 8.5.sp)
                            Text("Max Pressure (Full Size)", color = Color(0xFF64748B), fontSize = 8.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Brushes Grid with Mini Pressure Taper Previews
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(260.dp)
                ) {
                    items(conceptsBrushes) { brush ->
                        val isSelected = (brush == selectedBrush)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF272F43) else Color(0xFF1C1F2B),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF33384B)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedBrush = brush }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = getBrushIcon(brush),
                                        contentDescription = brush.displayName,
                                        tint = if (isSelected) Color(0xFF48CAE4) else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = brush.displayName,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Mini pressure stroke preview in every brush card
                                BrushPressureStrokePreview(
                                    brushType = brush,
                                    color = if (brush.isUtility) Color(0xFF48CAE4) else Color(currentColor),
                                    maxSize = 14f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onSelectBrush(selectedBrush) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select ${selectedBrush.displayName}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Canvas that accurately renders an S Pen pressure-to-size tapering stroke from light (left)
 * to heavy pressure (right), communicating dynamic brush characteristics.
 */
@Composable
fun BrushPressureStrokePreview(
    brushType: BrushType,
    color: Color,
    maxSize: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val startX = w * 0.08f
        val endX = w * 0.92f
        val cy = h / 2f
        val totalLength = endX - startX

        // Wire maintains uniform fixed stroke width and butt caps
        if (brushType == BrushType.WIRE) {
            drawLine(
                color = color,
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = (maxSize * 0.6f).coerceIn(2f, 10f),
                cap = StrokeCap.Butt
            )
            return@Canvas
        }

        val minRatio = when (brushType) {
            BrushType.FOUNTAIN_PEN -> 0.15f
            BrushType.WATERCOLOR -> 0.25f
            BrushType.AIRBRUSH -> 0.30f
            BrushType.SOFT_PENCIL -> 0.40f
            BrushType.MARKER -> 0.50f
            BrushType.HARD_PENCIL -> 0.65f
            BrushType.PEN -> 0.72f
            BrushType.ERASER_SOFT -> 0.45f
            BrushType.ERASER_HARD -> 0.85f
            else -> 0.60f
        }

        val minAlpha = when (brushType) {
            BrushType.AIRBRUSH -> 0.25f
            BrushType.WATERCOLOR -> 0.35f
            BrushType.SOFT_PENCIL -> 0.45f
            BrushType.FOUNTAIN_PEN -> 0.55f
            BrushType.MARKER -> 0.55f
            else -> 0.85f
        }

        val cap = if (brushType == BrushType.MARKER) StrokeCap.Square else StrokeCap.Round
        val effectiveMaxW = (maxSize * 1.4f).coerceIn(3f, 26f)

        // Draw segmented tapering curve from left (light pressure) to right (full pressure)
        val steps = 28
        var prevX = startX
        var prevY = cy

        for (i in 1..steps) {
            val progress = i / steps.toFloat()
            val x = startX + totalLength * progress
            // Subtle wave curve for visual organic feel
            val y = cy + (sin(progress * Math.PI.toFloat()) * 3.5f)

            // Pressure scales smoothly from 0.05f to 1.0f
            val pressure = (0.05f + 0.95f * progress)
            val segW = effectiveMaxW * (minRatio + (1.0f - minRatio) * pressure)
            val segAlpha = (minAlpha + (1.0f - minAlpha) * pressure) * color.alpha

            drawLine(
                color = color.copy(alpha = segAlpha.coerceIn(0.1f, 1.0f)),
                start = Offset(prevX, prevY),
                end = Offset(x, y),
                strokeWidth = segW,
                cap = cap
            )

            prevX = x
            prevY = y
        }
    }
}

fun getBrushIcon(type: BrushType): ImageVector {
    return when (type) {
        BrushType.PEN -> Icons.Default.Edit
        BrushType.SOFT_PENCIL -> Icons.Default.Create
        BrushType.HARD_PENCIL -> Icons.Default.ModeEdit
        BrushType.FOUNTAIN_PEN -> Icons.Default.Brush
        BrushType.WIRE -> Icons.Default.RadioButtonUnchecked
        BrushType.MARKER -> Icons.Default.Highlight
        BrushType.WATERCOLOR -> Icons.Default.WaterDrop
        BrushType.AIRBRUSH -> Icons.Default.Air
        BrushType.SLICE -> Icons.Default.ContentCut
        BrushType.NUDGE -> Icons.Default.AutoFixHigh
        BrushType.ERASER_HARD -> Icons.Default.CropFree
        BrushType.ERASER_SOFT -> Icons.Default.InvertColors
        BrushType.ERASER_MASK -> Icons.Default.CropFree
        BrushType.LASSO -> Icons.Default.CropFree
    }
}
