package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.VectorStroke

@Composable
fun LayersPanel(
    isOpen: Boolean,
    layers: List<CanvasLayer>,
    activeLayerId: String,
    strokes: List<VectorStroke>,
    images: List<CanvasImageElement>,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
            color = Color(0xFF191B24).copy(alpha = 0.97f),
            shadowElevation = 20.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333748)),
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(0.82f)
                .testTag("concepts_layers_panel")
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Layers",
                            tint = Color(0xFF48CAE4),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Layers Stack",
                            color = Color(0xFFF1F5F9),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Row {
                        IconButton(
                            onClick = onAddLayer,
                            modifier = Modifier.size(28.dp).testTag("add_layer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Layer",
                                tint = Color(0xFF48CAE4)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Layers",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Layers List (Photoshop / Concepts style stack)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(layers.sortedByDescending { it.orderIndex }, key = { it.id }) { layer ->
                        val isActive = (layer.id == activeLayerId)
                        val strokeCount = strokes.count { it.layerId == layer.id && !it.isDeleted }
                        val imageCount = images.count { it.layerId == layer.id }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) Color(0xFF262B3A) else Color(0xFF1E212D),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isActive) 1.5.dp else 1.dp,
                                if (isActive) Color(0xFF48CAE4) else Color(0xFF323646)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectLayer(layer.id) }
                                .testTag("layer_item_${layer.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = layer.name,
                                            color = if (isActive) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 13.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = "$strokeCount strokes • $imageCount images",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Action icons: Visibility, Lock, Delete
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onToggleVisibility(layer.id) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle Visibility",
                                                tint = if (layer.isVisible) Color(0xFFE2E8F0) else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onToggleLock(layer.id) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = "Toggle Lock",
                                                tint = if (layer.isLocked) Color(0xFFEF4444) else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (layers.size > 1) {
                                            IconButton(
                                                onClick = { onDeleteLayer(layer.id) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Layer",
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Layer Opacity slider
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "${(layer.opacity * 100).toInt()}%",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    Slider(
                                        value = layer.opacity,
                                        onValueChange = { onOpacityChange(layer.id, it) },
                                        valueRange = 0.05f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color(0xFF48CAE4)
                                        ),
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
