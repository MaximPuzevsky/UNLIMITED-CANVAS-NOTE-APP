package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.FingerMode
import com.example.model.GridType

/**
 * Concepts-grade Minimalist Translucent Dark Top Precision Bar.
 * Meets all touch target requirements (>= 48dp for critical buttons).
 */
@Composable
fun TopPrecisionBar(
    projectTitle: String,
    zoomLevel: Float,
    rotationDeg: Float,
    activeGrid: GridType,
    angleSnapping: Boolean,
    activeLayerName: String,
    strokeCount: Int,
    fingerMode: FingerMode,
    canvasBgColor: Int,
    onBackToGallery: () -> Unit,
    onResetViewport: () -> Unit,
    onResetRotation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleFingerMode: () -> Unit,
    onSelectGrid: (GridType) -> Unit,
    onToggleAngleSnapping: () -> Unit,
    onSelectCanvasBgColor: (Int) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleLayers: () -> Unit,
    onImportDeviceMedia: () -> Unit,
    onImportSampleBlueprint: () -> Unit,
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit,
    onExportJson: () -> Unit,
    onOpenBlueprintSpecs: () -> Unit,
    pressureCurve: Float = 0.5f,
    onPressureCurveChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141620).copy(alpha = 0.92f),
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF2E3346)),
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("top_precision_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // LEFT GROUP: Gallery back, Project Title, Zoom In/Out, Dynamic Angle Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Back to Gallery Button (Touch Target >= 48dp)
                IconButton(
                    onClick = onBackToGallery,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("back_to_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Gallery Projects",
                        tint = Color(0xFF48CAE4),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = projectTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp, end = 8.dp)
                )

                // Zoom Out Button
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.size(36.dp).testTag("zoom_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Zoom percentage readout with 1-tap reset to 100%
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF202434),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onResetViewport() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("zoom_reset_badge")
                ) {
                    Text(
                        text = "${(zoomLevel * 100).toInt()}%",
                        color = Color(0xFF48CAE4),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Zoom In Button
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.size(36.dp).testTag("zoom_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Rotation Indicator: Dynamically set visibility to hidden/invisible whenever the angle is strictly 0
                if (kotlin.math.abs(rotationDeg) > 0.05f) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2A2E42),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF48CAE4)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onResetRotation() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("angle_reset_badge")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Reset Angle to 0°",
                                tint = Color(0xFF48CAE4),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${rotationDeg.toInt()}°",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // RIGHT GROUP: Import Menu, Layers Badge, Export Menu, Main Settings Menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Import Menu Trigger
                Box {
                    IconButton(
                        onClick = { showImportMenu = true },
                        modifier = Modifier.size(48.dp).testTag("import_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Import Images",
                            tint = Color(0xFF48CAE4),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pick from Device Storage") },
                            onClick = {
                                showImportMenu = false
                                onImportDeviceMedia()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Load Sample Architectural Template") },
                            onClick = {
                                showImportMenu = false
                                onImportSampleBlueprint()
                            }
                        )
                    }
                }

                // Layers Icon with Badge showing active layer and stroke count
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF202434),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353A4E)),
                    modifier = Modifier
                        .sizeIn(minHeight = 36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleLayers() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("toggle_layers_bar_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Layers",
                            tint = Color(0xFF48CAE4),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$activeLayerName ($strokeCount)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Export Options Dropdown
                Box {
                    IconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier.size(48.dp).testTag("export_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Vector / Image",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Vector SVG (W3C Standard)") },
                            onClick = {
                                showExportMenu = false
                                onExportSvg()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export High-Res PNG Image") },
                            onClick = {
                                showExportMenu = false
                                onExportPng()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Concepts Vector JSON") },
                            onClick = {
                                showExportMenu = false
                                onExportJson()
                            }
                        )
                    }
                }

                // Main Settings Menu Trigger (Contains Grid, Background, Touch Mode, S Pen, Blueprint)
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(48.dp).testTag("settings_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings & Preferences",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Main Settings & Preferences Dialog
    if (showSettingsDialog) {
        MainSettingsDialog(
            activeGrid = activeGrid,
            angleSnapping = angleSnapping,
            activeBgColor = canvasBgColor,
            fingerMode = fingerMode,
            onSelectGrid = onSelectGrid,
            onToggleAngleSnapping = onToggleAngleSnapping,
            onSelectBgColor = onSelectCanvasBgColor,
            onToggleFingerMode = onToggleFingerMode,
            onOpenBlueprintSpecs = {
                showSettingsDialog = false
                onOpenBlueprintSpecs()
            },
            pressureCurve = pressureCurve,
            onPressureCurveChange = onPressureCurveChange,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * Main Settings & Preferences Dialog
 * Integrates Precision Grid, Canvas Paper Background, Touch Pan vs Draw, Stylus Calibration, and Architecture Blueprint.
 */
@Composable
fun MainSettingsDialog(
    activeGrid: GridType,
    angleSnapping: Boolean,
    activeBgColor: Int,
    fingerMode: FingerMode,
    onSelectGrid: (GridType) -> Unit,
    onToggleAngleSnapping: () -> Unit,
    onSelectBgColor: (Int) -> Unit,
    onToggleFingerMode: () -> Unit,
    onOpenBlueprintSpecs: () -> Unit,
    pressureCurve: Float = 0.5f,
    onPressureCurveChange: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedShortcut by remember { mutableStateOf("Eraser / Lasso Toggle") }
    val shortcuts = listOf("Eraser / Lasso Toggle", "Color Picker", "Pan Canvas Only")

    val canvasColors = remember {
        listOf(
            android.graphics.Color.parseColor("#15161C") to "Dark Matte",
            android.graphics.Color.parseColor("#FAFAFA") to "White Paper",
            android.graphics.Color.parseColor("#1A365D") to "Blueprint Blue",
            android.graphics.Color.parseColor("#F4ECD8") to "Sepia"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF181A24),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353A4E)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Settings & Preferences",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Precision Grid Style
                Text("Precision Grid", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (grid in GridType.values()) {
                        val isSelected = (grid == activeGrid)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2B3248) else Color(0xFF1F2230),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF48CAE4) else Color(0xFF363B4F)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectGrid(grid) }
                                .padding(vertical = 7.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = grid.displayName,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Angle Snapping Toggle
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Angle Snapping", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Snaps rotation to 0°, 45°, 90°, 180°", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                    Switch(
                        checked = angleSnapping,
                        onCheckedChange = { onToggleAngleSnapping() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0077B6)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Canvas Paper Background Color
                Text("Canvas Paper Background", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for ((c, name) in canvasColors) {
                        val isSelected = (c == activeBgColor)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectBgColor(c) }
                                .padding(vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF474C62),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = name,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Finger Touch Mode (Pan vs Draw)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Finger Action", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (fingerMode == FingerMode.PAN) "Finger pans canvas (Recommended)" else "Finger draws ink",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = (fingerMode == FingerMode.DRAW),
                        onCheckedChange = { onToggleFingerMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0077B6)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // S Pen Pressure Calibration
                Text("S Pen Pressure Calibration", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Soft", color = Color(0xFF94A3B8), fontSize = 9.sp)
                    Text("Normal", color = Color(0xFF94A3B8), fontSize = 9.sp)
                    Text("Firm", color = Color(0xFF94A3B8), fontSize = 9.sp)
                }
                Slider(
                    value = pressureCurve,
                    onValueChange = { onPressureCurveChange(it) },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF48CAE4)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Concepts 1:1 Architecture Blueprint Spec link
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF232738),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF4A261).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenBlueprintSpecs() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Architecture Blueprint",
                            tint = Color(0xFFF4A261),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Concepts 1:1 Architecture Blueprint",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "View technical specs, math & hardware pipeline",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
