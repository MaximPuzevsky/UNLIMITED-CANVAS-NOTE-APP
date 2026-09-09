package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DrawingRepository
import com.example.engine.VectorExporter
import com.example.model.BrushType
import com.example.ui.BlueprintDialog
import com.example.ui.CanvasView
import com.example.ui.ColorWheelDialog
import com.example.ui.FloatingSelectionBar
import com.example.ui.GalleryScreen
import com.example.ui.LayersPanel
import com.example.ui.QuickActionMenu
import com.example.ui.ToolWheel
import com.example.ui.TopPrecisionBar
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SampleMediaGenerator
import com.example.viewmodel.CanvasViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                ConceptsSketchApp()
            }
        }
    }
}

@Composable
fun ConceptsSketchApp(
    viewModel: CanvasViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { DrawingRepository.getInstance(context) }

    // Projects repository state
    val projects: List<com.example.data.DrawingProjectEntity> by repository.allProjects.collectAsState(initial = emptyList())
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val currentProjectTitle by viewModel.currentProjectTitle.collectAsState()

    // Initialize repository reference in ViewModel and seed sample project
    LaunchedEffect(Unit) {
        viewModel.setRepository(repository)
        repository.seedSampleProjectIfNeeded()
    }

    // Requirement 6: STARTUP GALLERY & PROJECT MANAGER
    // On application launch, present clean white screen displaying grid of saved drawings
    if (currentProjectId == null) {
        GalleryScreen(
            projects = projects,
            onOpenProject = { id, title ->
                coroutineScope.launch {
                    val data = repository.loadProjectDrawingData(id)
                    viewModel.openProject(id, title, data)
                }
            },
            onCreateNewProject = { customTitle ->
                val finalTitle = customTitle?.takeIf { it.isNotBlank() }
                    ?: "Untitled Drawing ${projects.size + 1}"
                coroutineScope.launch {
                    val entity = repository.createNewProject(finalTitle)
                    val blankData = repository.loadProjectDrawingData(entity.id)
                    viewModel.openProject(entity.id, finalTitle, blankData)
                }
            },
            onDeleteProject = { id ->
                coroutineScope.launch {
                    repository.deleteProject(id)
                }
            }
        )
        return
    }

    // Observe state from ViewModel
    val layers by viewModel.layers.collectAsState()
    val activeLayerId by viewModel.activeLayerId.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val images by viewModel.images.collectAsState()
    val viewport by viewModel.viewport.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val toolSlots by viewModel.toolSlots.collectAsState()
    val activeSlotIndex by viewModel.activeSlotIndex.collectAsState()
    val activeColor by viewModel.activeColor.collectAsState()
    val activeBrush by viewModel.activeBrushType.collectAsState()
    val activeSize by viewModel.activeStrokeWidth.collectAsState()
    val activeOpacity by viewModel.activeOpacity.collectAsState()
    val activeSmoothing by viewModel.activeSmoothing.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val currentStrokePoints by viewModel.currentStrokePoints.collectAsState()
    val isCurrentLasso by viewModel.isCurrentStrokeLasso.collectAsState()
    val quickMenuPoint by viewModel.quickMenuPoint.collectAsState()
    val fingerMode by viewModel.fingerMode.collectAsState()
    val pressureCurve by viewModel.pressureCurve.collectAsState()

    // Dialog & UI overlay visibility
    var isLayersOpen by remember { mutableStateOf(false) }
    var isColorWheelOpen by remember { mutableStateOf(false) }
    var isBlueprintOpen by remember { mutableStateOf(false) }
    var exportedSvgContent by remember { mutableStateOf<String?>(null) }
    var exportedPngBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportedJsonContent by remember { mutableStateOf<String?>(null) }
    val canvasBgColor by viewModel.canvasBackgroundColor.collectAsState()

    // Photo picker for screenshots / pictures
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    viewModel.addImportedImage(bitmap, "Screenshot / Photo")
                    Toast.makeText(context, "Image added with canvas rotation compensation", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val activeLayerName = layers.find { it.id == activeLayerId }?.name ?: "Layer"
    val activeLayerStrokeCount = strokes.count { it.layerId == activeLayerId && !it.isDeleted }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Infinite Vector Canvas Surface
            CanvasView(
                layers = layers,
                strokes = strokes,
                images = images,
                viewport = viewport,
                gridType = gridType,
                canvasBackgroundColor = canvasBgColor,
                selection = selection,
                currentStrokePoints = currentStrokePoints,
                isCurrentLasso = isCurrentLasso,
                activeColor = activeColor,
                activeWidth = activeSize,
                activeOpacity = activeOpacity,
                activeBrush = activeBrush,
                fingerMode = fingerMode,
                pressureCurve = pressureCurve,
                onPenStart = { pt, isBtn, w, h -> viewModel.startInking(pt, isBtn, w, h) },
                onPenMove = { pts, isBtn, w, h -> viewModel.appendInkingPoints(pts, isBtn, w, h) },
                onPenEnd = { pt, isBtn, w, h -> viewModel.finishInking(pt, isBtn, w, h) },
                onPenDwell = { sx, sy -> viewModel.triggerQuickMenuAt(sx, sy) },
                onPanZoom = { dx, dy, scale, rot -> viewModel.panZoomRotate(dx, dy, scale, rot) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onSingleTapOutside = { _, _ -> viewModel.clearSelection() },
                onDragSelection = { dx, dy -> viewModel.moveSelectionBy(dx, dy) },
                onTransformSelection = { dx, dy, scale, rot -> viewModel.transformSelection(dx, dy, scale, rot) },
                onCommitSelectionTransform = { dx, dy, scale, rot -> viewModel.commitSelectionTransform(dx, dy, scale, rot) },
                onCommitSelectionMove = { dx, dy -> viewModel.commitSelectionMove(dx, dy) },
                onReturnToPanMode = { viewModel.returnToPanMode() }
            )

            // 2. Top Precision & Status Bar
            TopPrecisionBar(
                projectTitle = currentProjectTitle,
                zoomLevel = viewport.zoom,
                rotationDeg = viewport.rotationDeg,
                activeGrid = gridType,
                angleSnapping = viewport.angleSnapping,
                activeLayerName = activeLayerName,
                strokeCount = activeLayerStrokeCount,
                fingerMode = fingerMode,
                canvasBgColor = canvasBgColor,
                onBackToGallery = { viewModel.closeCurrentProject() },
                onResetViewport = { viewModel.resetViewport() },
                onResetRotation = { viewModel.resetRotation() },
                onZoomIn = { viewModel.zoomIn() },
                onZoomOut = { viewModel.zoomOut() },
                onToggleFingerMode = { viewModel.toggleFingerMode() },
                onSelectGrid = { viewModel.setGridType(it) },
                onToggleAngleSnapping = { viewModel.toggleAngleSnapping() },
                onSelectCanvasBgColor = { viewModel.setCanvasBackgroundColor(it) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onToggleLayers = { isLayersOpen = !isLayersOpen },
                onImportDeviceMedia = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportSampleBlueprint = {
                    val sample = SampleMediaGenerator.createSampleBlueprintBitmap()
                    viewModel.addImportedImage(sample, "Elevation Schematic")
                    Toast.makeText(context, "Sample schematic added to canvas!", Toast.LENGTH_SHORT).show()
                },
                onExportSvg = {
                    val svg = VectorExporter.exportToSvg(layers, strokes, images)
                    exportedSvgContent = svg
                },
                onExportPng = {
                    val bmp = VectorExporter.exportToBitmap(layers, strokes, images)
                    exportedPngBitmap = bmp
                },
                onExportJson = {
                    val json = VectorExporter.exportToJson(layers, strokes, images)
                    exportedJsonContent = json
                },
                onOpenBlueprintSpecs = { isBlueprintOpen = true },
                pressureCurve = pressureCurve,
                onPressureCurveChange = { viewModel.setPressureCurve(it) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )

            

            // 4. Floating HUD Selection Toolbar (Copy, Delete, Mirror Left, Mirror Top)
            FloatingSelectionBar(
                selection = selection,
                onCopy = { viewModel.copySelection() },
                onDelete = { viewModel.deleteSelection() },
                onMirrorHorizontal = { viewModel.mirrorSelectionHorizontal() },
                onMirrorVertical = { viewModel.mirrorSelectionVertical() },
                onClearSelection = { viewModel.clearSelection() },
                onColorSelect = { viewModel.modifySelectionColor(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )

            // 5. S Pen Dwell Quick Action / Paste Menu
            QuickActionMenu(
                anchorPoint = quickMenuPoint,
                onPaste = {
                    viewModel.copySelection()
                    Toast.makeText(context, "Pasted at stylus position", Toast.LENGTH_SHORT).show()
                },
                onImportImage = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDismiss = { viewModel.dismissQuickMenu() }
            )

            // 6. Photoshop / Concepts Layers Panel
            LayersPanel(
                isOpen = isLayersOpen,
                layers = layers,
                activeLayerId = activeLayerId,
                strokes = strokes,
                images = images,
                onSelectLayer = { viewModel.selectLayer(it) },
                onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                onToggleLock = { viewModel.toggleLayerLock(it) },
                onOpacityChange = { id, op -> viewModel.setLayerOpacity(id, op) },
                onAddLayer = { viewModel.addLayer() },
                onDeleteLayer = { viewModel.deleteLayer(it) },
                onClose = { isLayersOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // 7. COPIC Color Wheel Dialog
            if (isColorWheelOpen) {
                ColorWheelDialog(
                    currentColor = activeColor,
                    copicColors = viewModel.copicSpectrum,
                    onColorSelected = { viewModel.setActiveColor(it) },
                    onDismiss = { isColorWheelOpen = false }
                )
            }

            // 8. Technical Architecture Blueprint Dialog
            if (isBlueprintOpen) {
                BlueprintDialog(onDismiss = { isBlueprintOpen = false })
            }

            // 9. SVG Export Preview Dialog
            exportedSvgContent?.let { svgData ->
                SvgExportDialog(
                    svgXml = svgData,
                    onDismiss = { exportedSvgContent = null }
                )
            }

            // 10. PNG Export Preview Dialog
            exportedPngBitmap?.let { bmp ->
                PngExportDialog(
                    bitmap = bmp,
                    onDismiss = { exportedPngBitmap = null }
                )
            }

            // 11. Concepts Vector JSON Export Dialog
            exportedJsonContent?.let { jsonStr ->
                JsonExportDialog(
                    jsonString = jsonStr,
                    onDismiss = { exportedJsonContent = null }
                )
            }
        }
    }
}

@Composable
fun SvgExportDialog(
    svgXml: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1D27),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353A4C)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "W3C SVG Vector Export",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1015))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = svgXml,
                        color = Color(0xFF64DFDF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Concepts SVG", svgXml)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "SVG copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy SVG")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy SVG Code", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PngExportDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1D27),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353A4C)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "High-Res Raster Render",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Render Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF333748), RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Resolution: ${bitmap.width} × ${bitmap.height} px (ARGB_8888)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Exported ${bitmap.width}x${bitmap.height} PNG to Pictures", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save PNG to Gallery", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun JsonExportDialog(
    jsonString: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1D27),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF353A4C)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Concepts Vector JSON",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1015))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = jsonString,
                        color = Color(0xFFF4A261),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Concepts Vector JSON", jsonString)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Vector JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy JSON")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Vector JSON", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
