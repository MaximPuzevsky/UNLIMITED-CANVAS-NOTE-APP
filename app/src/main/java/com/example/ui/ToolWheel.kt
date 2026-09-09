/* 
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Concepts 1:1 Signature 3-Ring Circular Tool Wheel:
 * 1. Inner Ring: Active tool preview, color chip, size (pt/px), opacity (%), smoothing (%). Tapping toggles sliders.
 * 2. Middle Ring: Tool slots radially arranged as wedges/segments with crisp icons and real-time color dots.
 * 3. Outer Ring: Quick COPIC color ring arranged in a circular arc.
 *
 * Fluid drag-to-reposition with snap-to-dock zones near corners.
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
    modifier: Modifier = Modifier
) {
    // Draggable position state
    var offsetX by remember { mutableFloatStateOf(16f) }
    var offsetY by remember { mutableFloatStateOf(120f) }
    var showSliders by remember { mutableStateOf(false) }
    var showBrushDialog by remember { mutableStateOf(false) }

    // Outer ring quick COPIC palette colors (12 curated vibrant & architectural tones)
    val quickCopicColors = remember {
        listOf(
            android.graphics.Color.parseColor("#000000"), // 100 Black
            android.graphics.Color.parseColor("#686E74"), // C-7 Cool Gray
            android.graphics.Color.parseColor("#CBD0D4"), // C-3 Light Cool Gray
            android.graphics.Color.parseColor("#FFFFFF"), // 0 White
            android.graphics.Color.parseColor("#E62B34"), // R29 Lipstick Red
            android.graphics.Color.parseColor("#FA8223"), // YR04 Chrome Orange
            android.graphics.Color.parseColor("#FBD429"), // Y15 Cadmium Yellow
            android.graphics.Color.parseColor("#74B238"), // YG17 Grass Green
            android.graphics.Color.parseColor("#008C4A"), // G17 Forest Green
            android.graphics.Color.parseColor("#3FB1E5"), // B05 Robin's Egg Blue
            android.graphics.Color.parseColor("#005DA4"), // B29 Night Blue
            android.graphics.Color.parseColor("#6A2A80"), // V09 Violet
            android.graphics.Color.parseColor("#6C4334"), // E29 Burnt Umber
            android.graphics.Color.parseColor("#DFB186")  // E33 Sand
        )
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .testTag("concepts_tool_wheel")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Main 3-Ring Wheel Container (Diameter: 270dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(270.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                            offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                        }
                    }
            ) {
                // Outer Ring Surface & Backdrop
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF161822).copy(alpha = 0.92f),
                    shadowElevation = 16.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF33384A)),
                    modifier = Modifier.size(270.dp)
                ) {}

                // --- 1. OUTER RING: Quick COPIC Color Arc (Radius ~ 118dp) ---
                val outerRadius = 116f
                for (i in quickCopicColors.indices) {
                    val colorInt = quickCopicColors[i]
                    // Distribute around top and sides (-160 to 160 deg)
                    val angleDeg = -170.0 + (i * (340.0 / (quickCopicColors.size - 1)))
                    val angleRad = Math.toRadians(angleDeg)
                    val x = (outerRadius * cos(angleRad)).toFloat()
                    val y = (outerRadius * sin(angleRad)).toFloat()

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .border(
                                width = if (activeColor == colorInt) 2.dp else 1.dp,
                                color = if (activeColor == colorInt) Color.White else Color(0xFF4A4E62),
                                shape = CircleShape
                            )
                            .clickable {
                                onSelectColor(colorInt)
                            }
                    )
                }

                // Middle Ring Backdrop Track
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E212D).copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383D52)),
                    modifier = Modifier.size(200.dp)
                ) {}

                // --- 2. MIDDLE RING: 8 Radial Tool Slots (Radius ~ 75dp) ---
                val middleRadius = 75f
                for (i in toolSlots.indices) {
                    val slot = toolSlots[i]
                    val isSelected = (i == activeSlotIndex)
                    val angleDeg = -90.0 + (i * (360.0 / toolSlots.size))
                    val angleRad = Math.toRadians(angleDeg)
                    val slotX = (middleRadius * cos(angleRad)).toFloat()
                    val slotY = (middleRadius * sin(angleRad)).toFloat()

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(slotX.roundToInt(), slotY.roundToInt()) }
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFF32374A) else Color(0xFF232736)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF474C62),
                                shape = CircleShape
                            )
                            .pointerInput(i, isSelected) {
                                detectTapGestures(
                                    onTap = {
                                        if (isSelected) {
                                            showBrushDialog = true
                                        } else {
                                            onSelectSlot(i)
                                        }
                                    },
                                    onDoubleTap = {
                                        onSelectSlot(i)
                                        showBrushDialog = true
                                    },
                                    onLongPress = {
                                        onSelectSlot(i)
                                        showBrushDialog = true
                                    }
                                )
                            }
                            .testTag("tool_slot_$i")
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

                // --- 3. INNER RING: Center Active Tool Core & Slider Trigger (Diameter 76dp) ---
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(activeColor).copy(alpha = 0.95f),
                                    Color(activeColor).copy(alpha = 0.60f),
                                    Color(0xFF141620)
                                )
                            )
                        )
                        .border(2.5.dp, Color(0xFFF1F5F9), CircleShape)
                        .clickable {
                            showSliders = !showSliders
                        }
                        .testTag("tool_wheel_center_core")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = getBrushIcon(activeBrush),
                            contentDescription = "Active Tool",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = String.format("%.1fpt", activeSize),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(activeOpacity * 100).roundToInt()}%",
                            color = Color(0xFFE2E8F0),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Quick Precision Adjustment Sliders Popup
            AnimatedVisibility(
                visible = showSliders,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1B1D28).copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383D54)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .width(230.dp)
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tool Adjustments",
                                color = Color(0xFF48CAE4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = onOpenColorWheel,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ColorLens,
                                    contentDescription = "Color Wheel",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Size Slider
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Size", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text("${String.format("%.1f", activeSize)} pt", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Slider(
                            value = activeSize,
                            onValueChange = onSizeChange,
                            valueRange = 0.5f..48f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF48CAE4)
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        // Opacity Slider
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Opacity", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text("${(activeOpacity * 100).roundToInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Slider(
                            value = activeOpacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0.05f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF0077B6)
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        // Smoothing Slider
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Smoothing", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text("${(activeSmoothing * 100).roundToInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Slider(
                            value = activeSmoothing,
                            onValueChange = onSmoothingChange,
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF90E0EF)
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        }

        // Comprehensive Brush Selection Dialog
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
 * Brush selection modal showcasing all genuine Concepts brushes with a live stroke preview card.
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

    // List of real Concepts Brushes (Wire explicitly excluded from default palette)
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

*/