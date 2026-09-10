package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrushType

/**
 * Clean, safe instrument stub bar for testing and redesign preparation.
 * Decouples the wheel UI while providing immediate, crash-free control over
 * active tool, active color, stroke width, opacity, and smoothing.
 */
@Composable
fun InstrumentStubBar(
    activeBrush: BrushType,
    activeColor: Int,
    activeSize: Float,
    activeOpacity: Float,
    activeSmoothing: Float,
    onSelectBrush: (BrushType) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSmoothingChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSliders by remember { mutableStateOf(false) }

    val quickColors = remember {
        listOf(
            android.graphics.Color.parseColor("#FFFFFF"),
            android.graphics.Color.parseColor("#CBD5E1"),
            android.graphics.Color.parseColor("#00E5FF"),
            android.graphics.Color.parseColor("#00F5D4"),
            android.graphics.Color.parseColor("#FFB703"),
            android.graphics.Color.parseColor("#FF4D6D"),
            android.graphics.Color.parseColor("#7209B7"),
            android.graphics.Color.parseColor("#2B2D42")
        )
    }

    val primaryBrushes = remember {
        listOf(
            BrushType.PEN,
            BrushType.SOFT_PENCIL,
            BrushType.HARD_PENCIL,
            BrushType.FOUNTAIN_PEN,
            BrushType.MARKER,
            BrushType.ERASER_HARD,
            BrushType.ERASER_MASK,
            BrushType.SLICE,
            BrushType.LASSO
        )
    }

    Column(
        modifier = modifier.testTag("instrument_stub_bar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xEE1E1F29),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tool selection chips
                primaryBrushes.forEach { brush ->
                    val isSelected = activeBrush == brush
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectBrush(brush) },
                        leadingIcon = {
                            Icon(
                                imageVector = getStubBrushIcon(brush),
                                contentDescription = brush.displayName,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                            )
                        },
                        label = {
                            Text(
                                text = brush.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2B3245),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF16171E),
                            labelColor = Color(0xFFCBD5E1)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                            selectedBorderColor = Color(0xFF00E5FF),
                            borderWidth = if (isSelected) 1.5.dp else 0.5.dp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x33FFFFFF)))
                Spacer(modifier = Modifier.width(4.dp))

                // Quick Color Swatches
                quickColors.forEach { colorInt ->
                    val isSelected = activeColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0x44FFFFFF),
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(colorInt) }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Toggle Sliders Button
                IconButton(
                    onClick = { showSliders = !showSliders },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Adjust brush size, opacity, smoothing",
                        tint = if (showSliders) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Animated Sliders Panel
        AnimatedVisibility(
            visible = showSliders,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xEE1E1F29),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                shadowElevation = 8.dp,
                modifier = Modifier.width(320.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${activeBrush.displayName} Controls",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Size slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text("${String.format("%.1f", activeSize)} pt", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = activeSize,
                        onValueChange = onSizeChange,
                        valueRange = 0.5f..60f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    // Opacity slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text("${(activeOpacity * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = activeOpacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    // Smoothing slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Smoothing", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text("${(activeSmoothing * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = activeSmoothing,
                        onValueChange = onSmoothingChange,
                        valueRange = 0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )
                }
            }
        }
    }
}

private fun getStubBrushIcon(type: BrushType): ImageVector {
    return when (type) {
        BrushType.PEN -> Icons.Default.Create
        BrushType.SOFT_PENCIL -> Icons.Default.Edit
        BrushType.HARD_PENCIL -> Icons.Default.Edit
        BrushType.FOUNTAIN_PEN -> Icons.Default.Brush
        BrushType.MARKER -> Icons.Default.Highlight
        BrushType.LASSO -> Icons.Default.CropFree
        BrushType.SLICE -> Icons.Default.ContentCut
        BrushType.NUDGE -> Icons.Default.AutoFixHigh
        BrushType.ERASER_HARD, BrushType.ERASER_MASK -> Icons.Default.ContentCut
        else -> Icons.Default.Brush
    }
}
