package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.BrushType
import com.example.model.ToolSlot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private data class OuterWedgeData(
    val id: Int,
    val startAngle: Float,
    val sweepAngle: Float = 36f,
    val midAngle: Float,
    val isUndo: Boolean = false,
    val isRedo: Boolean = false,
    val toolIndex: Int = -1
)

private data class MiddleWedgeData(
    val setting: String,
    val label: String,
    val startAngle: Float,
    val sweepAngle: Float = 120f,
    val midAngle: Float
)

/**
 * Draws a clean annular sector (pie-slice / wedge of a ring).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnularSector(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDeg: Float,
    sweepAngleDeg: Float,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float = 1.8f
) {
    val path = Path().apply {
        val startRad = Math.toRadians(startAngleDeg.toDouble())
        val endRad = Math.toRadians((startAngleDeg + sweepAngleDeg).toDouble())

        val outerStartX = center.x + outerRadius * cos(startRad).toFloat()
        val outerStartY = center.y + outerRadius * sin(startRad).toFloat()
        val innerEndX = center.x + innerRadius * cos(endRad).toFloat()
        val innerEndY = center.y + innerRadius * sin(endRad).toFloat()

        moveTo(outerStartX, outerStartY)
        arcTo(
            rect = Rect(center.x - outerRadius, center.y - outerRadius, center.x + outerRadius, center.y + outerRadius),
            startAngleDegrees = startAngleDeg,
            sweepAngleDegrees = sweepAngleDeg,
            forceMoveTo = false
        )
        lineTo(innerEndX, innerEndY)
        arcTo(
            rect = Rect(center.x - innerRadius, center.y - innerRadius, center.x + innerRadius, center.y + innerRadius),
            startAngleDegrees = startAngleDeg + sweepAngleDeg,
            sweepAngleDegrees = -sweepAngleDeg,
            forceMoveTo = false
        )
        close()
    }

    drawPath(path, color = fillColor)
    if (strokeWidth > 0f) {
        drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
    }
}

/**
 * Concepts-Inspired Multi-Layered Radial Wheel System:
 *
 * 1. Central Core (Active Color Indicator & Ring Trigger):
 *    - Center circle fill dynamically reflects active instrument color.
 *    - Tapping expands / closes a large 2D torus (donut-shaped) spectrum ring.
 *
 * 2. Middle Ring (3-Part Division into Annular Sector Wedges):
 *    - Integrated labels and numerical readouts directly on the ring segments (SIZE, OPAC, SMOOTH).
 *    - Direct inline drag / tap adjustment on each full wedge.
 *
 * 3. Outer Ring (Full-Ring Wedge Buttons / Pie Slices):
 *    - Entire ring is segmented into distinct large wedge buttons with radial dividing lines.
 *    - 8 tool wedges + Undo & Redo wedges for large, comfortable touch hit-boxes.
 *
 * 4. Canvas Visibility & Minimize Support:
 *    - Collapse button docks the wheel into a sleek floating 52dp pill so drawings/images are never blocked.
 */
@Composable
fun ConceptsWheelSystem(
    toolSlots: List<ToolSlot>,
    activeSlotIndex: Int,
    activeColor: Int,
    activeBrush: BrushType,
    activeSize: Float,
    activeOpacity: Float,
    activeSmoothing: Float,
    onSelectSlot: (Int) -> Unit,
    onChangeBrushType: (BrushType) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSmoothingChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    isColorTorusOpen: Boolean,
    onToggleColorTorus: () -> Unit,
    onCloseColorTorus: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Floating position
    var offsetX by remember { mutableFloatStateOf(16f) }
    var offsetY by remember { mutableFloatStateOf(120f) }

    // Canvas & Overlay Visibility: Minimize / Collapse State
    var isWheelCollapsed by remember { mutableStateOf(false) }

    // Brush selection dialog state
    var showBrushDialog by remember { mutableStateOf(false) }

    // Infinite Rotary Angle for Color Torus (in degrees)
    var torusRotationAngle by remember { mutableFloatStateOf(0f) }

    // Active drag indicator for pen control settings
    var activeAdjustingSetting by remember { mutableStateOf<String?>(null) } // "size", "opacity", "smoothing"

    // Stable states for uninterrupted inline dragging
    val currentSize by rememberUpdatedState(activeSize)
    val currentOpacity by rememberUpdatedState(activeOpacity)
    val currentSmoothing by rememberUpdatedState(activeSmoothing)
    val onSizeChangeState by rememberUpdatedState(onSizeChange)
    val onOpacityChangeState by rememberUpdatedState(onOpacityChange)
    val onSmoothingChangeState by rememberUpdatedState(onSmoothingChange)

    // Base Radial Dimensions
    val wheelSize = 270.dp
    val rCore = 30.dp
    val rMidInner = 32.dp
    val rMidOuter = 78.dp
    val rOutInner = 80.dp
    val rOutOuter = 135.dp
    val torusDiameter = 420.dp

    // Outer 10 Wedges (each 36 degrees, fully contiguous)
    val outerWedges = remember {
        listOf(
            OuterWedgeData(0, 216f, 36f, 234f, isUndo = false, isRedo = false, toolIndex = 0),
            OuterWedgeData(1, 252f, 36f, 270f, isUndo = false, isRedo = false, toolIndex = 1),
            OuterWedgeData(2, 288f, 36f, 306f, isUndo = false, isRedo = false, toolIndex = 2),
            OuterWedgeData(3, 324f, 36f, 342f, isUndo = false, isRedo = false, toolIndex = 3),
            OuterWedgeData(4, 0f, 36f, 18f, isUndo = false, isRedo = false, toolIndex = 4),
            OuterWedgeData(5, 36f, 36f, 54f, isUndo = false, isRedo = false, toolIndex = 5),
            OuterWedgeData(6, 72f, 36f, 90f, isUndo = false, isRedo = false, toolIndex = 6),
            OuterWedgeData(7, 108f, 36f, 126f, isUndo = false, isRedo = false, toolIndex = 7),
            OuterWedgeData(8, 144f, 36f, 162f, isUndo = true, isRedo = false, toolIndex = -1),
            OuterWedgeData(9, 180f, 36f, 198f, isUndo = false, isRedo = true, toolIndex = -1)
        )
    }

    // Middle 3 Wedges (each 120 degrees: Size, Opacity, Smoothing)
    val middleWedges = remember {
        listOf(
            MiddleWedgeData("size", "SIZE", 210f, 120f, 270f),
            MiddleWedgeData("opacity", "OPAC", 330f, 120f, 30f),
            MiddleWedgeData("smoothing", "SMOOTH", 90f, 120f, 150f)
        )
    }

    if (isWheelCollapsed) {
        // Collapsed floating dock pill (guarantees unobstructed canvas view)
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF191B24))
                .border(2.dp, Color(0xFF48CAE4), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                        offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                    }
                }
                .clickable { isWheelCollapsed = false }
                .testTag("collapsed_wheel_dock_pill")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(activeColor))
                    .border(1.5.dp, Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = getBrushIcon(activeBrush),
                    contentDescription = "Expand Wheel",
                    tint = if (isColorBright(activeColor)) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        return
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .testTag("concepts_multi_ring_wheel_system")
    ) {
        // =========================================================================
        // EXPANDED 2D TORUS (DONUT-SHAPED) COLOR SPECTRUM RING
        // =========================================================================
        AnimatedVisibility(
            visible = isColorTorusOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            ColorTorusSpectrumRing(
                activeColor = activeColor,
                rotationAngle = torusRotationAngle,
                torusDiameter = torusDiameter,
                onRotateDelta = { deltaAngle ->
                    torusRotationAngle = (torusRotationAngle + deltaAngle) % 360f
                },
                onSelectColor = { selectedColor ->
                    onSelectColor(selectedColor)
                },
                onClose = onCloseColorTorus
            )
        }

        // =========================================================================
        // FULL CONCENTRIC MULTI-RING WHEEL SYSTEM
        // =========================================================================
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(wheelSize)
        ) {
            val density = LocalDensity.current
            val rCorePx = with(density) { rCore.toPx() }
            val rMidInnerPx = with(density) { rMidInner.toPx() }
            val rMidOuterPx = with(density) { rMidOuter.toPx() }
            val rOutInnerPx = with(density) { rOutInner.toPx() }
            val rOutOuterPx = with(density) { rOutOuter.toPx() }

            // 1. Master Canvas Drawing: Full-Ring Annular Sector Wedges with Radial Dividing Lines
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            val centerPx = Offset(size.width / 2f, size.height / 2f)
                            val dx = change.position.x - centerPx.x
                            val dy = change.position.y - centerPx.y
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist > rOutOuterPx) {
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                                offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // Outer Disc Backdrop
                drawCircle(
                    color = Color(0xFF10121A),
                    radius = rOutOuterPx,
                    center = center
                )

                // A. Render Outer Ring Full-Ring Wedges (Tools + Undo/Redo)
                for (w in outerWedges) {
                    val isSelected = (w.toolIndex == activeSlotIndex)
                    val fillColor = when {
                        isSelected -> Color(0xFF26334D)
                        w.isUndo || w.isRedo -> Color(0xFF191D2B)
                        else -> Color(0xFF141722)
                    }
                    val strokeColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E344A)
                    val strokeWidth = if (isSelected) 2.2.dp.toPx() else 1.5.dp.toPx()

                    drawAnnularSector(
                        center = center,
                        innerRadius = rOutInnerPx,
                        outerRadius = rOutOuterPx,
                        startAngleDeg = w.startAngle,
                        sweepAngleDeg = w.sweepAngle,
                        fillColor = fillColor,
                        strokeColor = strokeColor,
                        strokeWidth = strokeWidth
                    )
                }

                // Dividing circle between Outer Ring and Middle Ring
                drawCircle(
                    color = Color(0xFF383E54),
                    radius = rOutInnerPx,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // B. Render Middle Ring Full-Ring Wedges (3 Pen Controls: Size, Opacity, Smoothing)
                for (m in middleWedges) {
                    val isAdjusting = (activeAdjustingSetting == m.setting)
                    val fillColor = if (isAdjusting) Color(0xFF0284C7).copy(alpha = 0.85f) else Color(0xFF1B1E2C)
                    val strokeColor = if (isAdjusting) Color(0xFF38BDF8) else Color(0xFF383E54)
                    val strokeWidth = if (isAdjusting) 2.2.dp.toPx() else 1.5.dp.toPx()

                    drawAnnularSector(
                        center = center,
                        innerRadius = rMidInnerPx,
                        outerRadius = rMidOuterPx,
                        startAngleDeg = m.startAngle,
                        sweepAngleDeg = m.sweepAngle,
                        fillColor = fillColor,
                        strokeColor = strokeColor,
                        strokeWidth = strokeWidth
                    )
                }

                // Dividing circle between Middle Ring and Core
                drawCircle(
                    color = Color(0xFF383E54),
                    radius = rMidInnerPx,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // C. Central Core Circle Background
                drawCircle(
                    color = Color(activeColor),
                    radius = rCorePx,
                    center = center
                )
                drawCircle(
                    color = Color.White,
                    radius = rCorePx,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 2. Interactive Outer Ring Wedge Buttons (Full-Ring Touch Targets)
            val rOutCenterDp = (rOutInner + rOutOuter) / 2
            for (w in outerWedges) {
                val isSelected = (w.toolIndex == activeSlotIndex)
                val rad = Math.toRadians(w.midAngle.toDouble())
                val xOffDp = rOutCenterDp * cos(rad).toFloat()
                val yOffDp = rOutCenterDp * sin(rad).toFloat()

                if (w.isUndo) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(with(density) { xOffDp.toPx() }.roundToInt(), with(density) { yOffDp.toPx() }.roundToInt()) }
                            .size(46.dp)
                            .clickable { onUndo() }
                            .testTag("wheel_undo_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "UNDO",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else if (w.isRedo) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(with(density) { xOffDp.toPx() }.roundToInt(), with(density) { yOffDp.toPx() }.roundToInt()) }
                            .size(46.dp)
                            .clickable { onRedo() }
                            .testTag("wheel_redo_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "REDO",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else if (w.toolIndex in toolSlots.indices) {
                    val slot = toolSlots[w.toolIndex]
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(with(density) { xOffDp.toPx() }.roundToInt(), with(density) { yOffDp.toPx() }.roundToInt()) }
                            .size(48.dp)
                            .clickable {
                                if (isSelected) {
                                    showBrushDialog = true
                                } else {
                                    onSelectSlot(w.toolIndex)
                                }
                            }
                            .testTag("tool_slot_${w.toolIndex}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getBrushIcon(slot.brushType),
                                contentDescription = slot.brushType.displayName,
                                tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Slot color indicator dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(slot.color))
                                    .border(0.75.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            // 3. Interactive Middle Ring Segments (3-Part Division: Labels & Numerical Readouts on Ring)
            val rMidCenterDp = (rMidInner + rMidOuter) / 2

            // 3A. SIZE Segment (Top: 270 degrees)
            val sizeRad = Math.toRadians(270.0)
            val sizeX = rMidCenterDp * cos(sizeRad).toFloat()
            val sizeY = rMidCenterDp * sin(sizeRad).toFloat()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(with(density) { sizeX.toPx() }.roundToInt(), with(density) { sizeY.toPx() }.roundToInt()) }
                    .size(54.dp, 36.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeAdjustingSetting = "size" },
                            onDragEnd = { activeAdjustingSetting = null },
                            onDragCancel = { activeAdjustingSetting = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val delta = (dragAmount.x - dragAmount.y) * 0.25f
                                val newSize = (currentSize + delta).coerceIn(0.5f, 50f)
                                onSizeChangeState(newSize)
                            }
                        )
                    }
                    .clickable {
                        val next = if (currentSize >= 20f) 2f else currentSize + 2f
                        onSizeChangeState(next)
                    }
                    .testTag("pen_control_size")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SIZE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeAdjustingSetting == "size") Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = String.format("%.1f", activeSize),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // 3B. OPACITY Segment (Bottom-Right: 30 degrees)
            val opacRad = Math.toRadians(30.0)
            val opacX = rMidCenterDp * cos(opacRad).toFloat()
            val opacY = rMidCenterDp * sin(opacRad).toFloat()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(with(density) { opacX.toPx() }.roundToInt(), with(density) { opacY.toPx() }.roundToInt()) }
                    .size(54.dp, 36.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeAdjustingSetting = "opacity" },
                            onDragEnd = { activeAdjustingSetting = null },
                            onDragCancel = { activeAdjustingSetting = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val delta = (dragAmount.x - dragAmount.y) * 0.005f
                                val newOpacity = (currentOpacity + delta).coerceIn(0.05f, 1.0f)
                                onOpacityChangeState(newOpacity)
                            }
                        )
                    }
                    .clickable {
                        val next = if (currentOpacity <= 0.3f) 1.0f else currentOpacity - 0.2f
                        onOpacityChangeState(next)
                    }
                    .testTag("pen_control_opacity")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "OPAC",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeAdjustingSetting == "opacity") Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "${(activeOpacity * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // 3C. SMOOTHING Segment (Bottom-Left: 150 degrees)
            val smoothRad = Math.toRadians(150.0)
            val smoothX = rMidCenterDp * cos(smoothRad).toFloat()
            val smoothY = rMidCenterDp * sin(smoothRad).toFloat()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(with(density) { smoothX.toPx() }.roundToInt(), with(density) { smoothY.toPx() }.roundToInt()) }
                    .size(54.dp, 36.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeAdjustingSetting = "smoothing" },
                            onDragEnd = { activeAdjustingSetting = null },
                            onDragCancel = { activeAdjustingSetting = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val delta = (dragAmount.x - dragAmount.y) * 0.005f
                                val newSmoothing = (currentSmoothing + delta).coerceIn(0.0f, 1.0f)
                                onSmoothingChangeState(newSmoothing)
                            }
                        )
                    }
                    .clickable {
                        val next = if (currentSmoothing >= 0.8f) 0.0f else currentSmoothing + 0.25f
                        onSmoothingChangeState(next)
                    }
                    .testTag("pen_control_smoothing")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SMOOTH",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeAdjustingSetting == "smoothing") Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "${(activeSmoothing * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // 4. Central Core: Active Color Indicator & Torus Expansion Trigger
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(rCore * 2)
                    .clip(CircleShape)
                    .clickable { onToggleColorTorus() }
                    .testTag("center_color_circle_trigger")
            ) {
                Icon(
                    imageVector = getBrushIcon(activeBrush),
                    contentDescription = "Active Tool",
                    tint = if (isColorBright(activeColor)) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 5. Canvas Visibility / Minimize Button (Top-Right corner of wheel)
            IconButton(
                onClick = { isWheelCollapsed = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2232))
                    .border(1.dp, Color(0xFF3E4660), CircleShape)
                    .testTag("wheel_collapse_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimize Wheel",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    // Modal Brush Selector Dialog
    if (showBrushDialog) {
        BrushPickerDialog(
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

/**
 * Large 2D Torus (Donut-shaped) Color Spectrum Ring with Infinite Motor Rotation.
 *
 * - Outer Diameter: 420dp (largest outermost ring)
 * - Inner Radius: 135dp, Outer Radius: 205dp
 * - Displays color families organized from Warm (left/top) to Cool (right/bottom).
 * - Smooth shade bands from bright/light (inner) to deep/dark (outer).
 * - Rotates infinitely by dragging anywhere along the ring.
 * - Clicking any segment selects that color immediately.
 */
@Composable
fun ColorTorusSpectrumRing(
    activeColor: Int,
    rotationAngle: Float,
    torusDiameter: Dp = 400.dp,
    onRotateDelta: (Float) -> Unit,
    onSelectColor: (Int) -> Unit,
    onClose: () -> Unit
) {
    val families = ConceptsSpectrumPalette.families
    val totalFamilies = families.size
    val currentRotationAngle by rememberUpdatedState(rotationAngle)
    val onRotateDeltaState by rememberUpdatedState(onRotateDelta)
    val onSelectColorState by rememberUpdatedState(onSelectColor)
    val onCloseState by rememberUpdatedState(onClose)

    // Current hovered or selected family name for feedback badge
    var activeFamilyName by remember { mutableStateOf("") }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(torusDiameter)
            .testTag("color_torus_spectrum_ring")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val centerPx = Offset(size.width / 2f, size.height / 2f)
                        val rInnerPx = size.width * 0.32f
                        val rOuterPx = size.width * 0.49f

                        val dx = down.position.x - centerPx.x
                        val dy = down.position.y - centerPx.y
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist in (rInnerPx * 0.85f)..(rOuterPx * 1.15f)) {
                            // Touch is on the torus ring!
                            var prevAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))

                            // Calculate touched shade and family
                            val normDist = ((dist - rInnerPx) / (rOuterPx - rInnerPx)).coerceIn(0f, 0.99f)
                            var shadeIdx = (normDist * 7).toInt().coerceIn(0, 6)

                            var tapAngleDeg = (prevAngle - currentRotationAngle) % 360.0
                            if (tapAngleDeg < 0) tapAngleDeg += 360.0
                            val sweepPerFamily = 360.0 / totalFamilies
                            val famIdx = (tapAngleDeg / sweepPerFamily).toInt().coerceIn(0, totalFamilies - 1)
                            val family = families[famIdx]
                            activeFamilyName = family.name
                            onSelectColorState(family.shades[shadeIdx])

                            // Motor rotation drag loop
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()

                                val curX = change.position.x - centerPx.x
                                val curY = change.position.y - centerPx.y
                                val curDist = sqrt(curX * curX + curY * curY)
                                val curAngle = Math.toDegrees(atan2(curY.toDouble(), curX.toDouble()))

                                var delta = curAngle - prevAngle
                                if (delta > 180) delta -= 360
                                if (delta < -180) delta += 360
                                prevAngle = curAngle

                                // Infinite motor rotation
                                onRotateDeltaState(delta.toFloat())

                                // Radial movement changes shade band
                                if (curDist in (rInnerPx * 0.8f)..(rOuterPx * 1.2f)) {
                                    val d = ((curDist - rInnerPx) / (rOuterPx - rInnerPx)).coerceIn(0f, 0.99f)
                                    shadeIdx = (d * 7).toInt().coerceIn(0, 6)
                                }

                                // Shift active color dynamically as ring rotates under finger
                                val newRot = currentRotationAngle + delta
                                var dynAngle = (curAngle - newRot) % 360.0
                                if (dynAngle < 0) dynAngle += 360.0
                                val curFamIdx = (dynAngle / sweepPerFamily).toInt().coerceIn(0, totalFamilies - 1)
                                val curFam = families[curFamIdx]
                                activeFamilyName = curFam.name
                                val newColor = curFam.shades[shadeIdx]
                                onSelectColorState(newColor)
                            }
                        } else if (dist < rInnerPx * 0.75f) {
                            // Tapped inside the inner core/instruments area of the torus
                            onCloseState()
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rInner = size.width * 0.32f
            val rOuter = size.width * 0.49f
            val totalBands = 7
            val bandWidth = (rOuter - rInner) / totalBands
            val sweepPerFamily = 360f / totalFamilies

            // Draw dark background backdrop for donut
            drawCircle(
                color = Color(0xFF0F1118).copy(alpha = 0.97f),
                radius = rOuter + 4f,
                center = center
            )

            // Draw each family sector and shade band
            for (f in 0 until totalFamilies) {
                val family = families[f]
                val baseAngle = (f * sweepPerFamily + rotationAngle) % 360f

                for (s in 0 until family.shades.size) {
                    val colorInt = family.shades[s]
                    val bandInnerR = rInner + (s * bandWidth)
                    val bandStroke = bandWidth - 1.2f
                    val bandCenterR = bandInnerR + (bandStroke / 2f)

                    val isSelected = (colorInt == activeColor)

                    drawArc(
                        color = Color(colorInt),
                        startAngle = baseAngle + 0.6f,
                        sweepAngle = sweepPerFamily - 1.2f,
                        useCenter = false,
                        topLeft = Offset(center.x - bandCenterR, center.y - bandCenterR),
                        size = androidx.compose.ui.geometry.Size(bandCenterR * 2f, bandCenterR * 2f),
                        style = Stroke(
                            width = bandStroke,
                            cap = StrokeCap.Butt
                        )
                    )

                    // Draw selection highlight ring if current color matches
                    if (isSelected) {
                        drawArc(
                            color = Color.White,
                            startAngle = baseAngle + 0.3f,
                            sweepAngle = sweepPerFamily - 0.6f,
                            useCenter = false,
                            topLeft = Offset(center.x - bandCenterR, center.y - bandCenterR),
                            size = androidx.compose.ui.geometry.Size(bandCenterR * 2f, bandCenterR * 2f),
                            style = Stroke(
                                width = 3.5f,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }

            // Outer border of torus
            drawCircle(
                color = Color(0xFF475170),
                radius = rOuter + 2f,
                center = center,
                style = Stroke(width = 2f)
            )

            // Inner border of torus
            drawCircle(
                color = Color(0xFF475170),
                radius = rInner - 2f,
                center = center,
                style = Stroke(width = 2f)
            )

            // Reference notch indicator at top (12 o'clock / 270 degrees)
            val topIndicatorAngleRad = Math.toRadians(270.0)
            val notchR = rOuter + 2f
            val notchTipX = center.x + (notchR * cos(topIndicatorAngleRad)).toFloat()
            val notchTipY = center.y + (notchR * sin(topIndicatorAngleRad)).toFloat()

            val notchPath = Path().apply {
                moveTo(notchTipX, notchTipY)
                lineTo(notchTipX - 7f, notchTipY - 12f)
                lineTo(notchTipX + 7f, notchTipY - 12f)
                close()
            }
            drawPath(notchPath, color = Color.White)
        }

        // Active Color Family Name Badge floating at top
        if (activeFamilyName.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-14).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2230).copy(alpha = 0.95f))
                    .border(1.dp, Color(0xFF475170), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = activeFamilyName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Checks if a color is perceptually bright to choose contrasting text/icon tint.
 */
private fun isColorBright(colorInt: Int): Boolean {
    val r = android.graphics.Color.red(colorInt)
    val g = android.graphics.Color.green(colorInt)
    val b = android.graphics.Color.blue(colorInt)
    // Perceived luminance formula
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
    return luminance > 0.6
}

@Composable
fun BrushPickerDialog(
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
        BrushType.LASSO,
        BrushType.ERASER_HARD,
        BrushType.ERASER_SOFT,
        BrushType.SLICE,
        BrushType.NUDGE
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF181A24),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF363B4E)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("brush_selection_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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
                            text = "Select parametric vector inking tool",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Stroke Preview Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10121A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282C3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(20f, h / 2f + 10f)
                                cubicTo(
                                    w * 0.25f, h * 0.15f,
                                    w * 0.70f, h * 0.85f,
                                    w - 20f, h / 2f - 10f
                                )
                            }
                            drawPath(
                                path = path,
                                color = if (selectedBrush.isUtility) Color(0xFF48CAE4) else Color(currentColor),
                                style = Stroke(
                                    width = (currentSize * 1.5f).coerceIn(3f, 32f),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        Text(
                            text = "${selectedBrush.displayName} Preview",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Brushes Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(conceptsBrushes) { brush ->
                        val isSelected = (brush == selectedBrush)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF2A3146) else Color(0xFF1E212E),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF34394E)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedBrush = brush }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = getBrushIcon(brush),
                                    contentDescription = brush.displayName,
                                    tint = if (isSelected) Color(0xFF48CAE4) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = brush.displayName,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
