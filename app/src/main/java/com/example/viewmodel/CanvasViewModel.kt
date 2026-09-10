package com.example.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConceptFileManager
import com.example.data.ConceptFilePayload
import com.example.data.DrawingRepository
import com.example.data.ProjectDrawingData
import com.example.data.UserSettingsManager
import com.example.engine.GeometryMath
import com.example.model.BrushType
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.CanvasTextBlock
import com.example.model.FingerMode
import com.example.model.GridType
import com.example.model.RawPoint
import com.example.model.SelectionState
import com.example.model.ToolSlot
import com.example.model.VectorStroke
import com.example.model.ViewportState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

data class CanvasSnapshot(
    val strokes: List<VectorStroke>,
    val images: List<CanvasImageElement>,
    val textBlocks: List<CanvasTextBlock> = emptyList(),
    val layers: List<CanvasLayer>
)

class CanvasViewModel(
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private var repository: DrawingRepository? = null
    private var userSettings: UserSettingsManager? = null

    fun setRepository(repo: DrawingRepository) {
        this.repository = repo
    }

    fun setUserSettings(settings: UserSettingsManager) {
        this.userSettings = settings
        // Restore persistent configurations
        val savedSlots = settings.getToolSlots(initialSlots)
        _toolSlots.value = savedSlots
        val slotIdx = settings.getActiveSlotIndex().coerceIn(0, savedSlots.size - 1)
        _activeSlotIndex.value = slotIdx
        val activeSlot = savedSlots[slotIdx]
        _activeBrushType.value = activeSlot.brushType
        _activeColor.value = activeSlot.color
        _activeStrokeWidth.value = activeSlot.strokeWidth
        _activeOpacity.value = activeSlot.opacity
        _activeSmoothing.value = activeSlot.smoothing
        _gridType.value = settings.getGridType()
        _canvasBackgroundColor.value = settings.getBackgroundColor()
        _fingerMode.value = settings.getFingerMode()
        val angleSnapping = settings.getAngleSnapping()
        _viewport.value = _viewport.value.copy(angleSnapping = angleSnapping)
    }

    private val _currentProjectId = MutableStateFlow<String?>(null)
    val currentProjectId: StateFlow<String?> = _currentProjectId.asStateFlow()

    private val _currentProjectTitle = MutableStateFlow("Untitled Drawing")
    val currentProjectTitle: StateFlow<String> = _currentProjectTitle.asStateFlow()

    // Layers
    private val defaultLayerId = UUID.randomUUID().toString()
    private val _layers = MutableStateFlow(
        listOf(
            CanvasLayer(id = defaultLayerId, name = "Sketches", orderIndex = 0),
            CanvasLayer(id = UUID.randomUUID().toString(), name = "References", orderIndex = 1)
        )
    )
    val layers: StateFlow<List<CanvasLayer>> = _layers.asStateFlow()

    private val _activeLayerId = MutableStateFlow(defaultLayerId)
    val activeLayerId: StateFlow<String> = _activeLayerId.asStateFlow()

    // Vector elements
    private val _strokes = MutableStateFlow<List<VectorStroke>>(emptyList())
    val strokes: StateFlow<List<VectorStroke>> = _strokes.asStateFlow()

    private val _images = MutableStateFlow<List<CanvasImageElement>>(emptyList())
    val images: StateFlow<List<CanvasImageElement>> = _images.asStateFlow()

    // Text elements
    private val _textBlocks = MutableStateFlow<List<CanvasTextBlock>>(emptyList())
    val textBlocks: StateFlow<List<CanvasTextBlock>> = _textBlocks.asStateFlow()

    // Viewport
    private val _viewport = MutableStateFlow(ViewportState(zoom = 1.0f))
    val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()

    // Background Grid
    private val _gridType = MutableStateFlow(GridType.DOT)
    val gridType: StateFlow<GridType> = _gridType.asStateFlow()

    // Canvas Background Color (Matte Dark #15161C by default)
    private val _canvasBackgroundColor = MutableStateFlow(Color.parseColor("#15161C"))
    val canvasBackgroundColor: StateFlow<Int> = _canvasBackgroundColor.asStateFlow()

    // Requirement 1: DEFAULT INTERACTION MODE
    // Default state of the canvas must be Pan/Canvas Navigation (0-touch or 1-finger drag pans canvas)
    private val _fingerMode = MutableStateFlow(FingerMode.PAN)
    val fingerMode: StateFlow<FingerMode> = _fingerMode.asStateFlow()

    // Tool Slots (Concepts Tool Wheel) - Expressive tools without Wire
    private val initialSlots = listOf(
        ToolSlot(0, BrushType.PEN, Color.parseColor("#FFFFFF"), 3.5f, 1.0f, 0.45f),
        ToolSlot(1, BrushType.SOFT_PENCIL, Color.parseColor("#CBD5E1"), 2.8f, 0.85f, 0.20f),
        ToolSlot(2, BrushType.HARD_PENCIL, Color.parseColor("#F8F9FA"), 1.4f, 0.95f, 0.10f),
        ToolSlot(3, BrushType.FOUNTAIN_PEN, Color.parseColor("#00E5FF"), 4.5f, 1.0f, 0.60f),
        ToolSlot(4, BrushType.LASSO, Color.parseColor("#00F5D4"), 2.0f, 1.0f, 0.0f),
        ToolSlot(5, BrushType.MARKER, Color.parseColor("#FFB703"), 22.0f, 0.50f, 0.25f),
        ToolSlot(6, BrushType.SLICE, Color.parseColor("#FF4D6D"), 2.5f, 1.0f, 0.0f),
        ToolSlot(7, BrushType.ERASER_HARD, Color.TRANSPARENT, 24.0f, 1.0f, 0.0f)
    )

    private val _toolSlots = MutableStateFlow(initialSlots)
    val toolSlots: StateFlow<List<ToolSlot>> = _toolSlots.asStateFlow()

    private val _activeSlotIndex = MutableStateFlow(0)
    val activeSlotIndex: StateFlow<Int> = _activeSlotIndex.asStateFlow()

    // Active tool attributes
    private val _activeColor = MutableStateFlow(initialSlots[0].color)
    val activeColor: StateFlow<Int> = _activeColor.asStateFlow()

    private val _activeBrushType = MutableStateFlow(initialSlots[0].brushType)
    val activeBrushType: StateFlow<BrushType> = _activeBrushType.asStateFlow()

    private val _activeStrokeWidth = MutableStateFlow(initialSlots[0].strokeWidth)
    val activeStrokeWidth: StateFlow<Float> = _activeStrokeWidth.asStateFlow()

    private val _activeOpacity = MutableStateFlow(initialSlots[0].opacity)
    val activeOpacity: StateFlow<Float> = _activeOpacity.asStateFlow()

    private val _activeSmoothing = MutableStateFlow(initialSlots[0].smoothing)
    val activeSmoothing: StateFlow<Float> = _activeSmoothing.asStateFlow()

    // Selection State
    private val _selection = MutableStateFlow(SelectionState())
    val selection: StateFlow<SelectionState> = _selection.asStateFlow()

    // S Pen / Touch current inking path
    private val _currentStrokePoints = MutableStateFlow<List<RawPoint>>(emptyList())
    val currentStrokePoints: StateFlow<List<RawPoint>> = _currentStrokePoints.asStateFlow()
    private val inFlightStrokePoints = mutableListOf<RawPoint>()

    private val _isCurrentStrokeLasso = MutableStateFlow(false)
    val isCurrentStrokeLasso: StateFlow<Boolean> = _isCurrentStrokeLasso.asStateFlow()

    // S Pen Long-press Dwell / Paste menu
    private val _quickMenuPoint = MutableStateFlow<PointF?>(null)
    val quickMenuPoint: StateFlow<PointF?> = _quickMenuPoint.asStateFlow()

    // Clipboard for copy-paste (supports smart filtering of micro-dots and centered pasting)
    private var clipboardStrokes: List<VectorStroke> = emptyList()
    private var clipboardImages: List<CanvasImageElement> = emptyList()
    private var clipboardTextBlocks: List<CanvasTextBlock> = emptyList()

    // Undo / Redo history
    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    private fun pushUndoSnapshot() {
        undoStack.add(CanvasSnapshot(_strokes.value, _images.value, _textBlocks.value, _layers.value))
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(CanvasSnapshot(_strokes.value, _images.value, _textBlocks.value, _layers.value))
            _strokes.value = snapshot.strokes
            _images.value = snapshot.images
            _textBlocks.value = snapshot.textBlocks
            _layers.value = snapshot.layers
            clearSelection()
            scheduleAutoSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val snapshot = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(CanvasSnapshot(_strokes.value, _images.value, _textBlocks.value, _layers.value))
            _strokes.value = snapshot.strokes
            _images.value = snapshot.images
            _textBlocks.value = snapshot.textBlocks
            _layers.value = snapshot.layers
            clearSelection()
            scheduleAutoSave()
        }
    }

    // Viewport transforms
    fun panZoomRotate(panDx: Float, panDy: Float, scaleFactor: Float, rotateDeltaDeg: Float) {
        val current = _viewport.value
        val newZoom = (current.zoom * scaleFactor).coerceIn(0.05f, 50.0f)
        var newRot = current.rotationDeg + rotateDeltaDeg

        // Normalize rotation smoothly without jitter or snapping
        while (newRot > 180f) newRot -= 360f
        while (newRot < -180f) newRot += 360f

        // Rotate pan delta relative to canvas angle so pan feels natural
        val rad = -Math.toRadians(current.rotationDeg.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)
        val rotDx = (panDx * cosR - panDy * sinR) / newZoom
        val rotDy = (panDx * sinR + panDy * cosR) / newZoom

        _viewport.value = current.copy(
            panX = current.panX + rotDx,
            panY = current.panY + rotDy,
            zoom = newZoom,
            rotationDeg = newRot
        )
    }

    fun zoomIn() {
        val current = _viewport.value
        val newZoom = (current.zoom * 1.25f).coerceIn(0.05f, 50.0f)
        _viewport.value = current.copy(zoom = newZoom)
    }

    fun zoomOut() {
        val current = _viewport.value
        val newZoom = (current.zoom / 1.25f).coerceIn(0.05f, 50.0f)
        _viewport.value = current.copy(zoom = newZoom)
    }

    fun setFingerMode(mode: FingerMode) {
        _fingerMode.value = mode
        userSettings?.saveFingerMode(mode)
    }

    fun toggleFingerMode() {
        val newMode = if (_fingerMode.value == FingerMode.DRAW) FingerMode.PAN else FingerMode.DRAW
        _fingerMode.value = newMode
        userSettings?.saveFingerMode(newMode)
    }

    fun resetViewport() {
        _viewport.value = ViewportState(panX = 0f, panY = 0f, zoom = 1.0f, rotationDeg = 0f, angleSnapping = _viewport.value.angleSnapping)
        scheduleAutoSave()
    }

    fun resetRotation() {
        _viewport.value = _viewport.value.copy(rotationDeg = 0f)
        scheduleAutoSave()
    }

    fun toggleAngleSnapping() {
        val newSnapping = !_viewport.value.angleSnapping
        _viewport.value = _viewport.value.copy(angleSnapping = newSnapping)
        userSettings?.saveAngleSnapping(newSnapping)
        scheduleAutoSave()
    }

    fun setAngleSnapping(snapping: Boolean) {
        _viewport.value = _viewport.value.copy(angleSnapping = snapping)
        userSettings?.saveAngleSnapping(snapping)
        scheduleAutoSave()
    }

    fun setCanvasBackgroundColor(color: Int) {
        _canvasBackgroundColor.value = color
        userSettings?.saveBackgroundColor(color)
        scheduleAutoSave()
    }

    fun setGridType(grid: GridType) {
        _gridType.value = grid
        userSettings?.saveGridType(grid)
        scheduleAutoSave()
    }

    // S Pen and Inking Flow
    fun startInking(screenPoint: RawPoint, isButtonPressed: Boolean, viewW: Float, viewH: Float) {
        _quickMenuPoint.value = null

        // Determine if drawing lasso: button pressed OR active tool is LASSO
        val isLasso = isButtonPressed || _activeBrushType.value == BrushType.LASSO
        _isCurrentStrokeLasso.value = isLasso

        val worldPt = GeometryMath.screenToWorld(screenPoint.x, screenPoint.y, _viewport.value, viewW, viewH)
        val initialPoint = screenPoint.copy(x = worldPt.x, y = worldPt.y)
        inFlightStrokePoints.clear()
        inFlightStrokePoints.add(initialPoint)

        if (isLasso) {
            _selection.value = _selection.value.copy(
                isLassoActive = true,
                lassoPoints = listOf(initialPoint)
            )
        }
    }

    fun appendInkingPoints(screenPoints: List<RawPoint>, isButtonPressed: Boolean, viewW: Float, viewH: Float) {
        val isLasso = isButtonPressed || _activeBrushType.value == BrushType.LASSO || _isCurrentStrokeLasso.value
        val worldPoints = screenPoints.map { p ->
            val w = GeometryMath.screenToWorld(p.x, p.y, _viewport.value, viewW, viewH)
            p.copy(x = w.x, y = w.y)
        }

        inFlightStrokePoints.addAll(worldPoints)

        if (isLasso) {
            _selection.value = _selection.value.copy(lassoPoints = inFlightStrokePoints.toList())
        }
    }

    fun finishInking(screenPoint: RawPoint, isButtonPressed: Boolean, viewW: Float, viewH: Float) {
        val isLasso = isButtonPressed || _activeBrushType.value == BrushType.LASSO || _isCurrentStrokeLasso.value
        val worldPt = GeometryMath.screenToWorld(screenPoint.x, screenPoint.y, _viewport.value, viewW, viewH)
        val endPoint = screenPoint.copy(x = worldPt.x, y = worldPt.y)

        if (inFlightStrokePoints.isEmpty()) {
            inFlightStrokePoints.add(endPoint)
        } else if (inFlightStrokePoints.last().x != endPoint.x || inFlightStrokePoints.last().y != endPoint.y) {
            inFlightStrokePoints.add(endPoint)
        }

        val rawPoints = inFlightStrokePoints.toList()
        inFlightStrokePoints.clear()
        _currentStrokePoints.value = emptyList()

        if (rawPoints.isNotEmpty()) {
            val effectivePoints = if (rawPoints.size == 1) {
                listOf(rawPoints[0], rawPoints[0].copy(x = rawPoints[0].x + 0.5f, y = rawPoints[0].y + 0.5f))
            } else {
                rawPoints
            }

            if (isLasso) {
                if (effectivePoints.size >= 2) {
                    viewModelScope.launch(calculationDispatcher) {
                        executeLassoSelection(effectivePoints)
                    }
                }
            } else {
                handleCompletedToolStroke(effectivePoints)
            }
        }

        _isCurrentStrokeLasso.value = false
    }

    private fun handleCompletedToolStroke(rawPoints: List<RawPoint>) {
        val activeBrush = _activeBrushType.value
        val activeLayer = _layers.value.find { it.id == _activeLayerId.value }

        // Check if layer locked
        if (activeLayer?.isLocked == true) return

        when (activeBrush) {
            BrushType.SLICE -> {
                // Slice existing strokes that cross rawPoints with spatial bounds culling on background coroutine
                viewModelScope.launch(calculationDispatcher) {
                    pushUndoSnapshot()
                    val currentStrokes = _strokes.value
                    val layerId = _activeLayerId.value
                    val toolBounds = GeometryMath.computePointsBounds(rawPoints)
                    toolBounds.inset(-15f, -15f)
                    val newStrokeList = mutableListOf<VectorStroke>()
                    var modified = false

                    for (s in currentStrokes) {
                        if (s.layerId == layerId && !s.isDeleted && RectF.intersects(s.bounds, toolBounds)) {
                            val sliced = GeometryMath.sliceStroke(s, rawPoints)
                            if (sliced.size > 1) {
                                modified = true
                                newStrokeList.addAll(sliced)
                            } else {
                                newStrokeList.add(s)
                            }
                        } else {
                            newStrokeList.add(s)
                        }
                    }
                    if (modified) {
                        _strokes.value = newStrokeList
                        scheduleAutoSave()
                    }
                }
            }

            BrushType.NUDGE -> {
                // Nudge stroke vertices along drag path with spatial bounds culling on background coroutine
                if (rawPoints.size >= 2) {
                    viewModelScope.launch(calculationDispatcher) {
                        pushUndoSnapshot()
                        val pStart = rawPoints.first()
                        val pEnd = rawPoints.last()
                        val dx = pEnd.x - pStart.x
                        val dy = pEnd.y - pStart.y
                        val radius = _activeStrokeWidth.value * 5f
                        val currentStrokes = _strokes.value
                        val layerId = _activeLayerId.value
                        val toolBounds = RectF(pStart.x - radius, pStart.y - radius, pStart.x + radius, pStart.y + radius)
                        val updated = currentStrokes.map { s ->
                            if (s.layerId == layerId && !s.isDeleted && RectF.intersects(s.bounds, toolBounds)) {
                                GeometryMath.nudgeStroke(s, pStart.x, pStart.y, radius, dx, dy)
                            } else s
                        }
                        _strokes.value = updated
                        scheduleAutoSave()
                    }
                }
            }

            BrushType.ERASER_HARD -> {
                // Hard vector eraser: removes strokes on contact with spatial bounds culling
                viewModelScope.launch(calculationDispatcher) {
                    pushUndoSnapshot()
                    val eraserRadius = _activeStrokeWidth.value
                    val currentStrokes = _strokes.value
                    val layerId = _activeLayerId.value
                    val toolBounds = GeometryMath.computePointsBounds(rawPoints)
                    toolBounds.inset(-eraserRadius, -eraserRadius)

                    val filtered = currentStrokes.filter { s ->
                        if (s.layerId != layerId || s.isDeleted) return@filter true
                        if (!RectF.intersects(s.bounds, toolBounds)) return@filter true
                        // Detailed point-level collision check only for spatially candidate strokes
                        var collides = false
                        for (ep in rawPoints) {
                            for (sp in s.points) {
                                val dist = Math.hypot((ep.x - sp.x).toDouble(), (ep.y - sp.y).toDouble()).toFloat()
                                if (dist < eraserRadius) {
                                    collides = true
                                    break
                                }
                            }
                            if (collides) break
                        }
                        !collides
                    }
                    _strokes.value = filtered
                    scheduleAutoSave()
                }
            }

            BrushType.ERASER_MASK -> {
                // Mask eraser marks strokes as masked with spatial bounds culling
                viewModelScope.launch(calculationDispatcher) {
                    pushUndoSnapshot()
                    val eraserRadius = _activeStrokeWidth.value
                    val currentStrokes = _strokes.value
                    val layerId = _activeLayerId.value
                    val toolBounds = GeometryMath.computePointsBounds(rawPoints)
                    toolBounds.inset(-eraserRadius, -eraserRadius)

                    val updated = currentStrokes.map { s ->
                        if (s.layerId != layerId || s.isDeleted) return@map s
                        if (!RectF.intersects(s.bounds, toolBounds)) return@map s
                        var collides = false
                        for (ep in rawPoints) {
                            for (sp in s.points) {
                                val dist = Math.hypot((ep.x - sp.x).toDouble(), (ep.y - sp.y).toDouble()).toFloat()
                                if (dist < eraserRadius) {
                                    collides = true
                                    break
                                }
                            }
                            if (collides) break
                        }
                        if (collides) s.copy(isMasked = true) else s
                    }
                    _strokes.value = updated
                    scheduleAutoSave()
                }
            }

            else -> {
                // Standard vector stroke creation (Pen, Pencils, Fountain Pen, Wire, Marker)
                pushUndoSnapshot()
                val smoothedPoints = GeometryMath.smoothPoints(rawPoints, _activeSmoothing.value)
                val newStroke = VectorStroke(
                    id = UUID.randomUUID().toString(),
                    layerId = _activeLayerId.value,
                    brushType = activeBrush,
                    color = _activeColor.value,
                    baseWidth = _activeStrokeWidth.value,
                    opacity = _activeOpacity.value,
                    smoothing = _activeSmoothing.value,
                    points = smoothedPoints
                )
                _strokes.value = _strokes.value + newStroke
                scheduleAutoSave()
            }
        }
    }

    private fun executeLassoSelection(lassoPoints: List<RawPoint>) {
        val unlockedLayerIds = _layers.value.filter { it.isVisible && !it.isLocked }.map { it.id }.toSet()
        val allStrokes = _strokes.value
        val allImages = _images.value

        val selectedStrokes = mutableSetOf<String>()
        val selectedImages = mutableSetOf<String>()
        val lassoBounds = GeometryMath.computePointsBounds(lassoPoints)

        for (s in allStrokes) {
            if (s.layerId in unlockedLayerIds && !s.isDeleted) {
                if (RectF.intersects(s.bounds, lassoBounds) && GeometryMath.isStrokeSelectedByLasso(s, lassoPoints)) {
                    selectedStrokes.add(s.id)
                }
            }
        }

        for (img in allImages) {
            if (img.layerId in unlockedLayerIds) {
                if (RectF.intersects(img.bounds, lassoBounds) && GeometryMath.isImageSelectedByLasso(img, lassoPoints)) {
                    selectedImages.add(img.id)
                }
            }
        }

        val strokesSubset = allStrokes.filter { it.id in selectedStrokes }
        val imagesSubset = allImages.filter { it.id in selectedImages }
        val bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset, ignoreMicroDots = true)

        _selection.value = SelectionState(
            selectedStrokeIds = selectedStrokes,
            selectedImageIds = selectedImages,
            lassoPoints = emptyList(),
            isLassoActive = false,
            bounds = bounds,
            isCopyPending = false
        )
    }

    // S Pen Dwell long-press quick menu
    fun triggerQuickMenuAt(screenX: Float, screenY: Float) {
        // Requirement 4: Intercept and erase preliminary point/dot generated by initial ACTION_DOWN stroke
        _currentStrokePoints.value = emptyList()
        _isCurrentStrokeLasso.value = false
        _quickMenuPoint.value = PointF(screenX, screenY)
    }

    fun dismissQuickMenu() {
        _quickMenuPoint.value = null
    }

    // Return to default Pan mode after a drawing tool touch finishes
    fun returnToPanMode() {
        _fingerMode.value = FingerMode.PAN
    }

    // Project State Management & Auto-Save
    fun openProject(projectId: String, title: String, data: ProjectDrawingData) {
        _currentProjectId.value = projectId
        _currentProjectTitle.value = title
        _layers.value = data.layers
        _activeLayerId.value = data.layers.firstOrNull()?.id ?: UUID.randomUUID().toString()
        _strokes.value = data.strokes
        _images.value = data.images
        _textBlocks.value = data.textBlocks
        _viewport.value = data.viewport
        _gridType.value = data.gridType
        _canvasBackgroundColor.value = data.backgroundColor
        _fingerMode.value = FingerMode.PAN
        userSettings?.saveLastOpenProjectId(projectId)
        clearSelection()
        undoStack.clear()
        redoStack.clear()
    }

    fun closeCurrentProject() {
        saveCurrentProjectNow(immediate = true)
        userSettings?.saveLastOpenProjectId(null)
        _currentProjectId.value = null
        clearSelection()
    }

    fun saveCurrentProjectNow(immediate: Boolean = false) {
        val projId = _currentProjectId.value ?: return
        val repo = repository ?: return
        val data = ProjectDrawingData(
            layers = _layers.value,
            strokes = _strokes.value,
            images = _images.value,
            textBlocks = _textBlocks.value,
            viewport = _viewport.value,
            gridType = _gridType.value,
            backgroundColor = _canvasBackgroundColor.value
        )
        repo.autoSaveProjectAsync(projId, _currentProjectTitle.value, data, immediate = immediate)
    }

    fun scheduleAutoSave() {
        saveCurrentProjectNow(immediate = false)
    }

    // Text Block Management
    fun addTextBlock(text: String, worldX: Float? = null, worldY: Float? = null, color: Int? = null) {
        if (text.isBlank()) return
        pushUndoSnapshot()
        val vp = _viewport.value
        val posX = worldX ?: (-vp.panX)
        val posY = worldY ?: (-vp.panY)
        val tb = CanvasTextBlock(
            id = UUID.randomUUID().toString(),
            layerId = _activeLayerId.value,
            text = text,
            worldX = posX,
            worldY = posY,
            fontSize = 24f / vp.zoom.coerceIn(0.5f, 2.0f),
            color = color ?: _activeColor.value
        )
        _textBlocks.value = _textBlocks.value + tb
        scheduleAutoSave()
    }

    fun deleteTextBlock(id: String) {
        pushUndoSnapshot()
        _textBlocks.value = _textBlocks.value.filter { it.id != id }
        scheduleAutoSave()
    }

    // .concept File Format Integration
    fun exportConceptFilePayload(): ConceptFilePayload {
        val currentData = ProjectDrawingData(
            layers = _layers.value,
            strokes = _strokes.value,
            images = _images.value,
            textBlocks = _textBlocks.value,
            viewport = _viewport.value,
            gridType = _gridType.value,
            backgroundColor = _canvasBackgroundColor.value
        )
        return ConceptFilePayload(
            projectTitle = _currentProjectTitle.value,
            drawingData = currentData,
            toolSlots = _toolSlots.value,
            activeSlotIndex = _activeSlotIndex.value
        )
    }

    fun loadConceptFilePayload(payload: ConceptFilePayload, projectId: String? = null) {
        val id = projectId ?: _currentProjectId.value ?: UUID.randomUUID().toString()
        _currentProjectId.value = id
        _currentProjectTitle.value = payload.projectTitle
        _layers.value = payload.drawingData.layers
        _activeLayerId.value = payload.drawingData.layers.firstOrNull()?.id ?: UUID.randomUUID().toString()
        _strokes.value = payload.drawingData.strokes
        _images.value = payload.drawingData.images
        _textBlocks.value = payload.drawingData.textBlocks
        _viewport.value = payload.drawingData.viewport
        _gridType.value = payload.drawingData.gridType
        _canvasBackgroundColor.value = payload.drawingData.backgroundColor
        if (payload.toolSlots.isNotEmpty()) {
            _toolSlots.value = payload.toolSlots
            userSettings?.saveToolSlots(payload.toolSlots)
        }
        val slotIdx = payload.activeSlotIndex.coerceIn(0, (_toolSlots.value.size - 1).coerceAtLeast(0))
        selectToolSlot(slotIdx)

        userSettings?.saveGridType(payload.drawingData.gridType)
        userSettings?.saveBackgroundColor(payload.drawingData.backgroundColor)
        userSettings?.saveAngleSnapping(payload.drawingData.viewport.angleSnapping)
        userSettings?.saveLastOpenProjectId(id)

        clearSelection()
        undoStack.clear()
        redoStack.clear()
        scheduleAutoSave()
    }

    // Selection actions (Minimal floating toolbar parity & Concepts-grade Smart Clipboard)
    fun copySelection() {
        val sel = _selection.value
        if (sel.isEmpty) return

        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()

        // Smart Clipboard: Ignore invisible micro-dots to ensure pure center-of-mass alignment
        val validStrokes = _strokes.value.filter {
            it.id in sel.selectedStrokeIds && it.layerId in unlockedLayerIds && !GeometryMath.isMicroDot(it)
        }
        val validImages = _images.value.filter {
            it.id in sel.selectedImageIds && it.layerId in unlockedLayerIds
        }
        val validText = _textBlocks.value.filter {
            it.id in sel.selectedTextIds && it.layerId in unlockedLayerIds
        }

        if (validStrokes.isEmpty() && validImages.isEmpty() && validText.isEmpty()) return

        clipboardStrokes = validStrokes
        clipboardImages = validImages
        clipboardTextBlocks = validText

        // Immediate in-place duplicate with offset for instant visual feedback
        duplicateSelection()
    }

    fun duplicateSelection(offset: Float = 40f) {
        val sel = _selection.value
        if (sel.isEmpty) return

        pushUndoSnapshot()
        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()
        val targetLayerId = _activeLayerId.value

        val clonedStrokes = mutableListOf<VectorStroke>()
        val clonedStrokeIds = mutableSetOf<String>()

        for (s in _strokes.value) {
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds && !GeometryMath.isMicroDot(s)) {
                val newId = UUID.randomUUID().toString()
                val movedPoints = s.points.map { p -> p.copy(x = p.x + offset, y = p.y + offset) }
                val clone = s.copy(
                    id = newId,
                    layerId = targetLayerId,
                    points = movedPoints,
                    bounds = VectorStroke.calculateBounds(movedPoints)
                )
                clonedStrokes.add(clone)
                clonedStrokeIds.add(newId)
            }
        }

        val clonedImages = mutableListOf<CanvasImageElement>()
        val clonedImageIds = mutableSetOf<String>()

        for (img in _images.value) {
            if (img.id in sel.selectedImageIds && img.layerId in unlockedLayerIds) {
                val newId = UUID.randomUUID().toString()
                val clone = img.copy(
                    id = newId,
                    layerId = targetLayerId,
                    worldX = img.worldX + offset,
                    worldY = img.worldY + offset
                )
                clonedImages.add(clone)
                clonedImageIds.add(newId)
            }
        }

        val clonedText = mutableListOf<CanvasTextBlock>()
        val clonedTextIds = mutableSetOf<String>()

        for (tb in _textBlocks.value) {
            if (tb.id in sel.selectedTextIds && tb.layerId in unlockedLayerIds) {
                val newId = UUID.randomUUID().toString()
                val clone = tb.copy(
                    id = newId,
                    layerId = targetLayerId,
                    worldX = tb.worldX + offset,
                    worldY = tb.worldY + offset
                )
                clonedText.add(clone)
                clonedTextIds.add(newId)
            }
        }

        _strokes.value = _strokes.value + clonedStrokes
        _images.value = _images.value + clonedImages
        _textBlocks.value = _textBlocks.value + clonedText

        val newBounds = GeometryMath.computeSelectionBounds(clonedStrokes, clonedImages, clonedText, ignoreMicroDots = true)
        _selection.value = SelectionState(
            selectedStrokeIds = clonedStrokeIds,
            selectedImageIds = clonedImageIds,
            selectedTextIds = clonedTextIds,
            bounds = newBounds,
            isCopyPending = true
        )
        scheduleAutoSave()
    }

    /**
     * Pastes clipboard or selection elements centered precisely at [targetWorldX, targetWorldY]
     * (e.g. S Pen dwell anchor point or camera viewport center).
     * Prevents pasted elements from flying off-screen or shifting center-of-mass due to micro-dots.
     */
    fun pasteAt(targetWorldX: Float, targetWorldY: Float) {
        val sourceStrokes = if (clipboardStrokes.isNotEmpty()) {
            clipboardStrokes
        } else {
            val sel = _selection.value
            _strokes.value.filter { it.id in sel.selectedStrokeIds && !GeometryMath.isMicroDot(it) }
        }

        val sourceImages = if (clipboardImages.isNotEmpty()) {
            clipboardImages
        } else {
            val sel = _selection.value
            _images.value.filter { it.id in sel.selectedImageIds }
        }

        val sourceText = if (clipboardTextBlocks.isNotEmpty()) {
            clipboardTextBlocks
        } else {
            val sel = _selection.value
            _textBlocks.value.filter { it.id in sel.selectedTextIds }
        }

        if (sourceStrokes.isEmpty() && sourceImages.isEmpty() && sourceText.isEmpty()) return

        pushUndoSnapshot()

        val sourceBounds = GeometryMath.computeSelectionBounds(sourceStrokes, sourceImages, sourceText, ignoreMicroDots = true)
            ?: return

        val sourceCenterX = sourceBounds.centerX()
        val sourceCenterY = sourceBounds.centerY()
        val dx = targetWorldX - sourceCenterX
        val dy = targetWorldY - sourceCenterY

        val targetLayerId = _activeLayerId.value
        val pastedStrokes = mutableListOf<VectorStroke>()
        val pastedStrokeIds = mutableSetOf<String>()

        for (s in sourceStrokes) {
            val newId = UUID.randomUUID().toString()
            val movedPoints = s.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) }
            val clone = s.copy(
                id = newId,
                layerId = targetLayerId,
                points = movedPoints,
                bounds = VectorStroke.calculateBounds(movedPoints)
            )
            pastedStrokes.add(clone)
            pastedStrokeIds.add(newId)
        }

        val pastedImages = mutableListOf<CanvasImageElement>()
        val pastedImageIds = mutableSetOf<String>()

        for (img in sourceImages) {
            val newId = UUID.randomUUID().toString()
            val clone = img.copy(
                id = newId,
                layerId = targetLayerId,
                worldX = img.worldX + dx,
                worldY = img.worldY + dy
            )
            pastedImages.add(clone)
            pastedImageIds.add(newId)
        }

        val pastedText = mutableListOf<CanvasTextBlock>()
        val pastedTextIds = mutableSetOf<String>()

        for (tb in sourceText) {
            val newId = UUID.randomUUID().toString()
            val clone = tb.copy(
                id = newId,
                layerId = targetLayerId,
                worldX = tb.worldX + dx,
                worldY = tb.worldY + dy
            )
            pastedText.add(clone)
            pastedTextIds.add(newId)
        }

        _strokes.value = _strokes.value + pastedStrokes
        _images.value = _images.value + pastedImages
        _textBlocks.value = _textBlocks.value + pastedText

        val newBounds = GeometryMath.computeSelectionBounds(pastedStrokes, pastedImages, pastedText, ignoreMicroDots = true)
        _selection.value = SelectionState(
            selectedStrokeIds = pastedStrokeIds,
            selectedImageIds = pastedImageIds,
            selectedTextIds = pastedTextIds,
            bounds = newBounds,
            isCopyPending = true
        )
        scheduleAutoSave()
    }

    fun pasteFromClipboard() {
        // Center paste in active viewport camera
        val vp = _viewport.value
        pasteAt(-vp.panX, -vp.panY)
    }

    fun deleteSelection() {
        val sel = _selection.value
        if (sel.isEmpty) return

        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()

        pushUndoSnapshot()
        _strokes.value = _strokes.value.filter { it.id !in sel.selectedStrokeIds || it.layerId !in unlockedLayerIds }
        _images.value = _images.value.filter { it.id !in sel.selectedImageIds || it.layerId !in unlockedLayerIds }
        _textBlocks.value = _textBlocks.value.filter { it.id !in sel.selectedTextIds || it.layerId !in unlockedLayerIds }
        clearSelection()
        scheduleAutoSave()
    }

    fun mirrorSelectionHorizontal() {
        val sel = _selection.value
        val bounds = sel.bounds ?: return
        pushUndoSnapshot()

        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()
        val centerX = bounds.centerX()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) {
                GeometryMath.mirrorStrokeHorizontal(s, centerX)
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds && img.layerId in unlockedLayerIds) {
                img.copy(
                    worldX = 2 * centerX - img.worldX,
                    scaleX = -img.scaleX,
                    isMirroredH = !img.isMirroredH
                )
            } else img
        }

        val strokesSubset = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val imagesSubset = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset, ignoreMicroDots = true))
        scheduleAutoSave()
    }

    fun mirrorSelectionVertical() {
        val sel = _selection.value
        val bounds = sel.bounds ?: return
        pushUndoSnapshot()

        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()
        val centerY = bounds.centerY()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) {
                GeometryMath.mirrorStrokeVertical(s, centerY)
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds && img.layerId in unlockedLayerIds) {
                img.copy(
                    worldY = 2 * centerY - img.worldY,
                    scaleY = -img.scaleY,
                    isMirroredV = !img.isMirroredV
                )
            } else img
        }

        val strokesSubset = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val imagesSubset = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset, ignoreMicroDots = true))
        scheduleAutoSave()
    }

    fun moveSelectionBy(dx: Float, dy: Float) {
        val sel = _selection.value
        if (sel.isEmpty) return

        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()

        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) {
                val moved = s.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) }
                s.copy(points = moved, bounds = VectorStroke.calculateBounds(moved))
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds && img.layerId in unlockedLayerIds) {
                img.copy(worldX = img.worldX + dx, worldY = img.worldY + dy)
            } else img
        }

        _textBlocks.value = _textBlocks.value.map { tb ->
            if (tb.id in sel.selectedTextIds && tb.layerId in unlockedLayerIds) {
                tb.copy(worldX = tb.worldX + dx, worldY = tb.worldY + dy)
            } else tb
        }

        val strokesSubset = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val imagesSubset = _images.value.filter { it.id in sel.selectedImageIds }
        val textSubset = _textBlocks.value.filter { it.id in sel.selectedTextIds }
        _selection.value = sel.copy(bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset, textSubset, ignoreMicroDots = true))
        scheduleAutoSave()
    }

    // Requirement 3: SELECTION GESTURES & OVERRIDES
    // When 1 or more objects are selected: 2-finger rotate rotates objects around their geometric
    // bounding center instead of canvas; 2-finger scale scales objects up/down instead of canvas zoom.
    fun transformSelection(
        panDx: Float,
        panDy: Float,
        scaleFactor: Float,
        rotateDeltaDeg: Float
    ) {
        val sel = _selection.value
        val bounds = sel.bounds ?: return
        if (sel.isEmpty) return

        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()

        val currentVp = _viewport.value
        val radVp = -Math.toRadians(currentVp.rotationDeg.toDouble()).toFloat()
        val cosVp = cos(radVp)
        val sinVp = sin(radVp)
        val worldPanDx = (panDx * cosVp - panDy * sinVp) / currentVp.zoom
        val worldPanDy = (panDx * sinVp + panDy * cosVp) / currentVp.zoom

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val rotRad = Math.toRadians(rotateDeltaDeg.toDouble()).toFloat()
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)

        // Transform strokes around geometric bounding center (strictly pure floating-point math)
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) {
                val transformedPoints = s.points.map { p ->
                    val dx = p.x - centerX
                    val dy = p.y - centerY
                    val scaledDx = dx * scaleFactor
                    val scaledDy = dy * scaleFactor
                    val rx = scaledDx * cosR - scaledDy * sinR
                    val ry = scaledDx * sinR + scaledDy * cosR
                    p.copy(
                        x = centerX + rx + worldPanDx,
                        y = centerY + ry + worldPanDy
                    )
                }
                s.copy(
                    points = transformedPoints,
                    bounds = VectorStroke.calculateBounds(transformedPoints),
                    baseWidth = (s.baseWidth * scaleFactor).coerceIn(0.5f, 200f)
                )
            } else s
        }

        // Transform images around geometric bounding center
        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds && img.layerId in unlockedLayerIds) {
                val dx = img.worldX - centerX
                val dy = img.worldY - centerY
                val scaledDx = dx * scaleFactor
                val scaledDy = dy * scaleFactor
                val rx = scaledDx * cosR - scaledDy * sinR
                val ry = scaledDx * sinR + scaledDy * cosR

                img.copy(
                    worldX = centerX + rx + worldPanDx,
                    worldY = centerY + ry + worldPanDy,
                    scaleX = img.scaleX * scaleFactor,
                    scaleY = img.scaleY * scaleFactor,
                    rotationDeg = (img.rotationDeg + rotateDeltaDeg)
                )
            } else img
        }

        val updatedStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val updatedImages = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(
            bounds = GeometryMath.computeSelectionBounds(updatedStrokes, updatedImages, ignoreMicroDots = true)
        )
        scheduleAutoSave()
    }

    fun clearSelection() {
        _selection.value = SelectionState()
        _quickMenuPoint.value = null
    }

    fun modifySelectionColor(newColor: Int) {
        val sel = _selection.value
        if (sel.selectedStrokeIds.isEmpty()) return
        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()
        pushUndoSnapshot()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) s.copy(color = newColor) else s
        }
        scheduleAutoSave()
    }

    fun modifySelectionWidth(newWidth: Float) {
        val sel = _selection.value
        if (sel.selectedStrokeIds.isEmpty()) return
        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()
        pushUndoSnapshot()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) s.copy(baseWidth = newWidth) else s
        }
        scheduleAutoSave()
    }

    fun modifySelectionBrush(newBrush: BrushType) {
        val sel = _selection.value
        if (sel.selectedStrokeIds.isEmpty()) return
        val unlockedLayerIds = _layers.value.filter { !it.isLocked }.map { it.id }.toSet()
        pushUndoSnapshot()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds && s.layerId in unlockedLayerIds) s.copy(brushType = newBrush) else s
        }
        scheduleAutoSave()
    }

    // Requirement 2: IMAGE IMPORT & ASSET BEHAVIOR
    // - Fixed Screen Scale & Neutral Orientation (0 deg relative to screen)
    // - Canvas Rotation Compensation (-θ)
    // - Top-level canvas object parity (Delete, Copy, Mirror, Transform)
    fun addImportedImage(
        bitmap: Bitmap,
        title: String = "Imported Media",
        viewWidth: Float = 1200f,
        viewHeight: Float = 800f
    ) {
        pushUndoSnapshot()
        val currentVp = _viewport.value

        // Fixed initial display resolution relative to viewport
        val targetDisplayScreenWidth = (viewWidth * 0.45f).coerceIn(360f, 650f)
        val maxBmpDim = Math.max(bitmap.width, bitmap.height).toFloat().coerceAtLeast(1f)
        val initialScale = (targetDisplayScreenWidth / maxBmpDim) / currentVp.zoom

        // Canvas Rotation Compensation:
        // If viewport is rotated at angle θ, apply inverse rotation (-θ) to image transform matrix
        // so it appears straight (0° rotation relative to physical device screen and user's vision)
        var compensatedRotation = -currentVp.rotationDeg
        while (compensatedRotation > 180f) compensatedRotation -= 360f
        while (compensatedRotation < -180f) compensatedRotation += 360f

        val newImg = CanvasImageElement(
            id = UUID.randomUUID().toString(),
            layerId = _activeLayerId.value,
            title = title,
            bitmap = bitmap,
            worldX = -currentVp.panX,
            worldY = -currentVp.panY,
            width = bitmap.width.toFloat(),
            height = bitmap.height.toFloat(),
            rotationDeg = compensatedRotation,
            scaleX = initialScale,
            scaleY = initialScale,
            isMirroredH = false,
            isMirroredV = false,
            opacity = 1f
        )
        _images.value = _images.value + newImg
        scheduleAutoSave()
    }

    // Layer Operations
    fun addLayer() {
        pushUndoSnapshot()
        val newIdx = _layers.value.size
        val newLayer = CanvasLayer(
            id = UUID.randomUUID().toString(),
            name = "Layer ${newIdx + 1}",
            orderIndex = newIdx
        )
        _layers.value = _layers.value + newLayer
        _activeLayerId.value = newLayer.id
    }

    fun selectLayer(layerId: String) {
        _activeLayerId.value = layerId
    }

    fun toggleLayerVisibility(layerId: String) {
        pushUndoSnapshot()
        _layers.value = _layers.value.map { l ->
            if (l.id == layerId) l.copy(isVisible = !l.isVisible) else l
        }
    }

    fun toggleLayerLock(layerId: String) {
        pushUndoSnapshot()
        _layers.value = _layers.value.map { l ->
            if (l.id == layerId) l.copy(isLocked = !l.isLocked) else l
        }
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        _layers.value = _layers.value.map { l ->
            if (l.id == layerId) l.copy(opacity = opacity) else l
        }
    }

    fun deleteLayer(layerId: String) {
        if (_layers.value.size <= 1) return
        pushUndoSnapshot()
        _layers.value = _layers.value.filter { it.id != layerId }
        _strokes.value = _strokes.value.filter { it.layerId != layerId }
        _images.value = _images.value.filter { it.layerId != layerId }
        if (_activeLayerId.value == layerId) {
            _activeLayerId.value = _layers.value.first().id
        }
    }

    // Tool Wheel & Attribute setters
    fun selectToolSlot(index: Int) {
        if (index in _toolSlots.value.indices) {
            _activeSlotIndex.value = index
            val slot = _toolSlots.value[index]
            _activeBrushType.value = slot.brushType
            if (slot.color != Color.TRANSPARENT) {
                _activeColor.value = slot.color
            }
            _activeStrokeWidth.value = slot.strokeWidth
            _activeOpacity.value = slot.opacity
            _activeSmoothing.value = slot.smoothing

            userSettings?.saveActiveSlotIndex(index)
            userSettings?.saveActiveBrushType(slot.brushType)
            userSettings?.saveActiveColor(slot.color)
            userSettings?.saveActiveSize(slot.strokeWidth)
            userSettings?.saveActiveOpacity(slot.opacity)
            userSettings?.saveActiveSmoothing(slot.smoothing)
        }
    }

    fun setActiveColor(color: Int) {
        _activeColor.value = color
        updateCurrentSlot { it.copy(color = color) }
        userSettings?.saveActiveColor(color)
        userSettings?.saveToolSlots(_toolSlots.value)
    }

    fun setActiveBrush(brushType: BrushType) {
        _activeBrushType.value = brushType
        updateCurrentSlot { it.copy(brushType = brushType) }
        userSettings?.saveActiveBrushType(brushType)
        userSettings?.saveToolSlots(_toolSlots.value)
    }

    fun setActiveStrokeWidth(width: Float) {
        _activeStrokeWidth.value = width
        updateCurrentSlot { it.copy(strokeWidth = width) }
        userSettings?.saveActiveSize(width)
        userSettings?.saveToolSlots(_toolSlots.value)
    }

    fun setActiveOpacity(opacity: Float) {
        _activeOpacity.value = opacity
        updateCurrentSlot { it.copy(opacity = opacity) }
        userSettings?.saveActiveOpacity(opacity)
        userSettings?.saveToolSlots(_toolSlots.value)
    }

    fun setActiveSmoothing(smoothing: Float) {
        _activeSmoothing.value = smoothing
        updateCurrentSlot { it.copy(smoothing = smoothing) }
        userSettings?.saveActiveSmoothing(smoothing)
        userSettings?.saveToolSlots(_toolSlots.value)
    }

    private fun updateCurrentSlot(transform: (ToolSlot) -> ToolSlot) {
        val idx = _activeSlotIndex.value
        _toolSlots.value = _toolSlots.value.mapIndexed { i, s ->
            if (i == idx) transform(s) else s
        }
    }

    fun commitSelectionTransform(
        worldPanDx: Float,
        worldPanDy: Float,
        scaleFactor: Float,
        rotateDeltaDeg: Float
    ) {
        val sel = _selection.value
        val bounds = sel.bounds ?: return
        if (sel.isEmpty) return

        pushUndoSnapshot()

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val rotRad = Math.toRadians(rotateDeltaDeg.toDouble()).toFloat()
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)

        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) {
                val transformedPoints = s.points.map { p ->
                    val dx = p.x - centerX
                    val dy = p.y - centerY
                    val scaledDx = dx * scaleFactor
                    val scaledDy = dy * scaleFactor
                    val rx = scaledDx * cosR - scaledDy * sinR
                    val ry = scaledDx * sinR + scaledDy * cosR
                    p.copy(
                        x = centerX + rx + worldPanDx,
                        y = centerY + ry + worldPanDy
                    )
                }
                s.copy(
                    points = transformedPoints,
                    bounds = VectorStroke.calculateBounds(transformedPoints),
                    baseWidth = (s.baseWidth * scaleFactor).coerceIn(0.5f, 200f)
                )
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds) {
                val dx = img.worldX - centerX
                val dy = img.worldY - centerY
                val scaledDx = dx * scaleFactor
                val scaledDy = dy * scaleFactor
                val rx = scaledDx * cosR - scaledDy * sinR
                val ry = scaledDx * sinR + scaledDy * cosR

                img.copy(
                    worldX = centerX + rx + worldPanDx,
                    worldY = centerY + ry + worldPanDy,
                    scaleX = img.scaleX * scaleFactor,
                    scaleY = img.scaleY * scaleFactor,
                    rotationDeg = (img.rotationDeg + rotateDeltaDeg)
                )
            } else img
        }

        val updatedStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val updatedImages = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(
            bounds = GeometryMath.computeSelectionBounds(updatedStrokes, updatedImages)
        )
        scheduleAutoSave()
    }

    fun commitSelectionMove(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        pushUndoSnapshot()
        moveSelectionBy(dx, dy)
    }
}
