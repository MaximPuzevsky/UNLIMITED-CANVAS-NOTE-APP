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
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleLayers: () -> Unit,
    onImportDeviceMedia: () -> Unit,
    onImportSampleBlueprint: () -> Unit,
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit,
    onExportJson: () -> Unit,
    onOpenBlueprintSpecs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPrecisionDialog by remember { mutableStateOf(false) }
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
            // LEFT GROUP: Gallery back, Project Title, Zoom, Angle, Undo / Redo (>=48dp touch targets)
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

                Spacer(modifier = Modifier.width(4.dp))

                // Angle indicator with tap-to-zero (0°)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (Math.abs(rotationDeg) > 0.5f) Color(0xFF2A2E42) else Color(0xFF1E212E),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (Math.abs(rotationDeg) > 0.5f) Color(0xFF48CAE4) else Color(0xFF33384B)
                    ),
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
                            tint = if (Math.abs(rotationDeg) > 0.5f) Color(0xFF48CAE4) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${rotationDeg.toInt()}°",
                            color = if (Math.abs(rotationDeg) > 0.5f) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Precision & Grid Settings Trigger
                IconButton(
                    onClick = { showPrecisionDialog = true },
                    modifier = Modifier.size(48.dp).testTag("grid_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Precision & Grid Settings",
                        tint = if (activeGrid != GridType.NONE) Color(0xFF48CAE4) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Undo Button (Touch Target >= 48dp)
                IconButton(
                    onClick = onUndo,
                    modifier = Modifier.size(48.dp).testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo (2-finger tap)",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Redo Button (Touch Target >= 48dp)
                IconButton(
                    onClick = onRedo,
                    modifier = Modifier.size(48.dp).testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo (3-finger tap)",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // RIGHT GROUP: Touch Mode, Import, Layers Badge, Export, S Pen Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Touch Mode Toggle (Draw vs Pan)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (fingerMode == FingerMode.DRAW) Color(0xFF0077B6).copy(alpha = 0.35f) else Color(0xFF5A189A).copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (fingerMode == FingerMode.DRAW) Color(0xFF48CAE4) else Color(0xFFC77DFF)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleFingerMode() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("finger_mode_toggle")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (fingerMode == FingerMode.DRAW) Icons.Default.Edit else Icons.Default.PanTool,
                            contentDescription = "Toggle Touch Mode",
                            tint = if (fingerMode == FingerMode.DRAW) Color(0xFF48CAE4) else Color(0xFFC77DFF),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (fingerMode == FingerMode.DRAW) "Touch: Draw" else "Touch: Pan",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

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

                // Settings & Stylus Calibration Modal Trigger
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(48.dp).testTag("settings_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Stylus & Settings",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Architecture Specs Blueprint
                IconButton(
                    onClick = onOpenBlueprintSpecs,
                    modifier = Modifier.size(40.dp).testTag("blueprint_specs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Architecture Blueprint",
                        tint = Color(0xFFF4A261),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Precision & Grid Settings Dialog
    if (showPrecisionDialog) {
        PrecisionGridDialog(
            activeGrid = activeGrid,
            angleSnapping = angleSnapping,
            activeBgColor = canvasBgColor,
            onSelectGrid = onSelectGrid,
            onToggleAngleSnapping = onToggleAngleSnapping,
            onSelectBgColor = onSelectCanvasBgColor,
            onDismiss = { showPrecisionDialog = false }
        )
    }

    // Settings & S Pen Calibration Dialog
    if (showSettingsDialog) {
        StylusSettingsDialog(
            fingerMode = fingerMode,
            onToggleFingerMode = onToggleFingerMode,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * Precision & Grid Settings Popup (Grid Type, Snap to Grid, Canvas Background: Dark, White, Blueprint Blue, Sepia)
 */
@Composable
fun PrecisionGridDialog(
    activeGrid: GridType,
    angleSnapping: Boolean,
    activeBgColor: Int,
    onSelectGrid: (GridType) -> Unit,
    onToggleAngleSnapping: () -> Unit,
    onSelectBgColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val canvasColors = remember {
        listOf(
            android.graphics.Color.parseColor("#15161C") to "Dark Matte",
            android.graphics.Color.parseColor("#FAFAFA") to "White Paper",
            android.graphics.Color.parseColor("#1A365D") to "Blueprint Blue",
            android.graphics.Color.parseColor("#F4ECD8") to "Architectural Sepia"
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
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Precision & Grid Settings",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grid Type Selection
                Text("Grid Style", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
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
                                .padding(vertical = 8.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Snap to Grid / Angle Snapping
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Angle Snapping", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Snaps viewport to 0°, 45°, 90°, 180°", color = Color(0xFF94A3B8), fontSize = 10.sp)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Paper Background Color
                Text("Canvas Paper Background", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
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
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF48CAE4) else Color(0xFF474C62),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings & S Pen Calibration Dialog
 */
@Composable
fun StylusSettingsDialog(
    fingerMode: FingerMode,
    onToggleFingerMode: () -> Unit,
    onDismiss: () -> Unit
) {
    var pressureCurve by remember { mutableFloatStateOf(0.5f) }
    var selectedShortcut by remember { mutableStateOf("Eraser / Lasso Toggle") }

    val shortcuts = listOf("Eraser / Lasso Toggle", "Color Picker", "Pan Canvas Only")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF181A24),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353A4E)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stylus & Gesture Settings",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // S Pen Button Remapping
                Text("S Pen Side Button Action", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                for (sc in shortcuts) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedShortcut = sc }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedShortcut == sc),
                            onClick = { selectedShortcut = sc },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF48CAE4))
                        )
                        Text(text = sc, color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pressure Sensitivity Calibration
                Text("S Pen Pressure Calibration", color = Color(0xFF48CAE4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Soft", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text("Normal", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text("Firm", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
                Slider(
                    value = pressureCurve,
                    onValueChange = { pressureCurve = it },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF48CAE4)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Finger Touch Interaction
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Finger Action", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
            }
        }
    }
}
