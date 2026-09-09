package com.example.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import com.example.data.DrawingRepository
import com.example.data.ProjectDrawingData
import com.example.data.SettingsPreferences
import com.example.data.TopBarPreferences
import com.example.data.UserSettingsManager
import com.example.data.WheelPreferencesData
import com.example.engine.GeometryMath
import com.example.model.BrushType
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.CopicColor
import com.example.model.FingerMode
import com.example.model.GridType
import com.example.model.RawPoint
import com.example.model.SelectionState
import com.example.model.ToolSlot
import com.example.model.VectorStroke
import com.example.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

data class CanvasSnapshot(
    val strokes: List<VectorStroke>,
    val images: List<CanvasImageElement>,
    val layers: List<CanvasLayer>
)

class CanvasViewModel : ViewModel() {

    private var repository: DrawingRepository? = null
    private var userSettings: UserSettingsManager? = null

    fun setRepository(repo: DrawingRepository) {
        this.repository = repo
    }

    fun setUserSettings(settings: UserSettingsManager) {
        this.userSettings = settings
        // Restore persistent configurations from user_wheel_preferences.json
        val loaded = settings.wheelPreferencesManager.loadPreferences(initialSlots)
        val savedSlots = loaded.toolSlots
        _toolSlots.value = savedSlots
        val slotIdx = loaded.activeSlotIndex.coerceIn(0, savedSlots.size - 1)
        _activeSlotIndex.value = slotIdx
        val activeSlot = savedSlots[slotIdx]
        _activeBrushType.value = activeSlot.brushType
        _activeColor.value = activeSlot.color
        _activeStrokeWidth.value = activeSlot.strokeWidth
        _activeOpacity.value = activeSlot.opacity
        _activeSmoothing.value = activeSlot.smoothing
        _gridType.value = loaded.topBar.gridType
        _canvasBackgroundColor.value = loaded.topBar.canvasBgColor
        _fingerMode.value = loaded.topBar.fingerMode
        _viewport.value = _viewport.value.copy(angleSnapping = loaded.topBar.angleSnapping)
        _pressureCurve.value = loaded.settings.pressureCurve
    }

    fun saveUserWheelPreferences() {
        userSettings?.let { s ->
            val data = WheelPreferencesData(
                activeSlotIndex = _activeSlotIndex.value,
                toolSlots = _toolSlots.value,
                topBar = TopBarPreferences(
                    gridType = _gridType.value,
                    canvasBgColor = _canvasBackgroundColor.value,
                    angleSnapping = _viewport.value.angleSnapping,
                    fingerMode = _fingerMode.value
                ),
                settings = SettingsPreferences(
                    pressureCurve = _pressureCurve.value,
                    spenShortcut = "Eraser / Lasso Toggle"
                )
            )
            s.wheelPreferencesManager.savePreferences(data)
        }
    }

    private val _pressureCurve = MutableStateFlow(0.5f)
    val pressureCurve: StateFlow<Float> = _pressureCurve.asStateFlow()

    fun setPressureCurve(curve: Float) {
        _pressureCurve.value = curve
        saveUserWheelPreferences()
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

    private val _isCurrentStrokeLasso = MutableStateFlow(false)
    val isCurrentStrokeLasso: StateFlow<Boolean> = _isCurrentStrokeLasso.asStateFlow()

    // S Pen Long-press Dwell / Paste menu
    private val _quickMenuPoint = MutableStateFlow<PointF?>(null)
    val quickMenuPoint: StateFlow<PointF?> = _quickMenuPoint.asStateFlow()

    // Clipboard for copy-paste
    private var clipboardStrokes: List<VectorStroke> = emptyList()
    private var clipboardImages: List<CanvasImageElement> = emptyList()

    // Undo / Redo history
    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    // Color swatches (COPIC spectrum organized by color families: E, B, R, YG, BV, Grays)
    val copicSpectrum = listOf(
        // Monochrome & Grays
        CopicColor("100", "Special Black", Color.parseColor("#111111"), "Black & Grays"),
        CopicColor("C-1", "Cool Gray 1", Color.parseColor("#E7EAEB"), "Cool Grays (C)"),
        CopicColor("C-3", "Cool Gray 3", Color.parseColor("#CBD0D4"), "Cool Grays (C)"),
        CopicColor("C-5", "Cool Gray 5", Color.parseColor("#9EA5AA"), "Cool Grays (C)"),
        CopicColor("C-7", "Cool Gray 7", Color.parseColor("#686E74"), "Cool Grays (C)"),
        CopicColor("W-1", "Warm Gray 1", Color.parseColor("#EAE7E1"), "Warm Grays (W)"),
        CopicColor("W-3", "Warm Gray 3", Color.parseColor("#D4CEC5"), "Warm Grays (W)"),
        CopicColor("W-5", "Warm Gray 5", Color.parseColor("#B3ABA0"), "Warm Grays (W)"),
        CopicColor("W-7", "Warm Gray 7", Color.parseColor("#7D756C"), "Warm Greys (W)"),
        CopicColor("N-1", "Neutral Gray 1", Color.parseColor("#E4E4E4"), "Neutral Grays (N)"),
        CopicColor("N-3", "Neutral Gray 3", Color.parseColor("#C8C8C8"), "Neutral Grays (N)"),
        CopicColor("N-5", "Neutral Gray 5", Color.parseColor("#9A9A9A"), "Neutral Grays (N)"),
        CopicColor("N-7", "Neutral Gray 7", Color.parseColor("#646464"), "Neutral Grays (N)"),

        // Earth (E)
        CopicColor("E00", "Cotton Pearl", Color.parseColor("#FBE8DF"), "Earth (E)"),
        CopicColor("E21", "Baby Skin Pink", Color.parseColor("#F6D2B8"), "Earth (E)"),
        CopicColor("E33", "Sand", Color.parseColor("#DFB186"), "Earth (E)"),
        CopicColor("E37", "Sepia", Color.parseColor("#9C6839"), "Earth (E)"),
        CopicColor("E29", "Burnt Umber", Color.parseColor("#6C4334"), "Earth (E)"),
        CopicColor("E49", "Dark Bark", Color.parseColor("#3B261D"), "Earth (E)"),

        // Reds (R)
        CopicColor("R00", "Pinkish White", Color.parseColor("#FFEAEA"), "Reds (R)"),
        CopicColor("R20", "Seashell Pink", Color.parseColor("#F8B7B2"), "Reds (R)"),
        CopicColor("R29", "Lipstick Red", Color.parseColor("#E62B34"), "Reds (R)"),
        CopicColor("R39", "Garnet", Color.parseColor("#BD2653"), "Reds (R)"),
        CopicColor("R59", "Cardinal", Color.parseColor("#7B243B"), "Reds (R)"),

        // Yellow-Red / Orange & Yellow (YR, Y)
        CopicColor("YR04", "Chrome Orange", Color.parseColor("#FA8223"), "Yellow-Red (YR)"),
        CopicColor("YR07", "Cadmium Orange", Color.parseColor("#E8561C"), "Yellow-Red (YR)"),
        CopicColor("Y11", "Pale Yellow", Color.parseColor("#FFF5A5"), "Yellows (Y)"),
        CopicColor("Y15", "Cadmium Yellow", Color.parseColor("#FBD429"), "Yellows (Y)"),
        CopicColor("Y17", "Golden Yellow", Color.parseColor("#F8B600"), "Yellows (Y)"),

        // Yellow-Green & Green (YG, G)
        CopicColor("YG03", "Yellow Green", Color.parseColor("#C8DE6D"), "Yellow-Green (YG)"),
        CopicColor("YG17", "Grass Green", Color.parseColor("#74B238"), "Yellow-Green (YG)"),
        CopicColor("YG67", "Moss", Color.parseColor("#4B7B34"), "Yellow-Green (YG)"),
        CopicColor("G05", "Emerald Green", Color.parseColor("#38A856"), "Greens (G)"),
        CopicColor("G17", "Forest Green", Color.parseColor("#008C4A"), "Greens (G)"),

        // Blues (B)
        CopicColor("B00", "Frost Blue", Color.parseColor("#D7EDF9"), "Blues (B)"),
        CopicColor("B05", "Robin's Egg Blue", Color.parseColor("#3FB1E5"), "Blues (B)"),
        CopicColor("B24", "Sky Blue", Color.parseColor("#53A4DA"), "Blues (B)"),
        CopicColor("B29", "Night Blue", Color.parseColor("#005DA4"), "Blues (B)"),
        CopicColor("B39", "Prussian Blue", Color.parseColor("#1B3C73"), "Blues (B)"),

        // Blue-Violet & Violet (BV, V)
        CopicColor("BV00", "Mauve Shadow", Color.parseColor("#DFD8EB"), "Blue-Violet (BV)"),
        CopicColor("BV04", "Blue Berry", Color.parseColor("#7C83BF"), "Blue-Violet (BV)"),
        CopicColor("BV17", "Deep Reddish Blue", Color.parseColor("#525B88"), "Blue-Violet (BV)"),
        CopicColor("V09", "Violet", Color.parseColor("#6A2A80"), "Violets (V)")
    )

    private fun pushUndoSnapshot() {
        undoStack.add(CanvasSnapshot(_strokes.value, _images.value, _layers.value))
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(CanvasSnapshot(_strokes.value, _images.value, _layers.value))
            _strokes.value = snapshot.strokes
            _images.value = snapshot.images
            _layers.value = snapshot.layers
            clearSelection()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val snapshot = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(CanvasSnapshot(_strokes.value, _images.value, _layers.value))
            _strokes.value = snapshot.strokes
            _images.value = snapshot.images
            _layers.value = snapshot.layers
            clearSelection()
        }
    }

    // Viewport transforms
    fun panZoomRotate(panDx: Float, panDy: Float, scaleFactor: Float, rotateDeltaDeg: Float) {
        val current = _viewport.value
        val newZoom = (current.zoom * scaleFactor).coerceIn(0.05f, 50.0f)
        var newRot = current.rotationDeg + rotateDeltaDeg

        // Normalize rotation
        while (newRot > 180f) newRot -= 360f
        while (newRot < -180f) newRot += 360f

        // Angle snapping near 0, 45, 90, 180
        if (current.angleSnapping) {
            val snapAngles = listOf(0f, 45f, -45f, 90f, -90f, 180f, -180f)
            for (target in snapAngles) {
                if (Math.abs(newRot - target) < 2.5f) {
                    newRot = target
                    break
                }
            }
        }

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
        saveUserWheelPreferences()
    }

    fun toggleFingerMode() {
        val newMode = if (_fingerMode.value == FingerMode.DRAW) FingerMode.PAN else FingerMode.DRAW
        _fingerMode.value = newMode
        userSettings?.saveFingerMode(newMode)
        saveUserWheelPreferences()
    }

    fun resetViewport() {
        _viewport.value = ViewportState(panX = 0f, panY = 0f, zoom = 1.0f, rotationDeg = 0f)
    }

    fun resetRotation() {
        _viewport.value = _viewport.value.copy(rotationDeg = 0f)
    }

    fun toggleAngleSnapping() {
        _viewport.value = _viewport.value.copy(angleSnapping = !_viewport.value.angleSnapping)
        saveUserWheelPreferences()
    }

    fun setCanvasBackgroundColor(color: Int) {
        _canvasBackgroundColor.value = color
        userSettings?.saveBackgroundColor(color)
        saveUserWheelPreferences()
    }

    fun setGridType(grid: GridType) {
        _gridType.value = grid
        userSettings?.saveGridType(grid)
        saveUserWheelPreferences()
    }

    // S Pen and Inking Flow
    fun startInking(screenPoint: RawPoint, isButtonPressed: Boolean, viewW: Float, viewH: Float) {
        _quickMenuPoint.value = null

        // Determine if drawing lasso: button pressed OR active tool is LASSO
        val isLasso = isButtonPressed || _activeBrushType.value == BrushType.LASSO
        _isCurrentStrokeLasso.value = isLasso

        val worldPt = GeometryMath.screenToWorld(screenPoint.x, screenPoint.y, _viewport.value, viewW, viewH)
        val initialPoint = screenPoint.copy(x = worldPt.x, y = worldPt.y)
        _currentStrokePoints.value = listOf(initialPoint)

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

        val updated = _currentStrokePoints.value + worldPoints
        _currentStrokePoints.value = updated

        if (isLasso) {
            _selection.value = _selection.value.copy(lassoPoints = updated)
        }
    }

    fun finishInking(screenPoint: RawPoint, isButtonPressed: Boolean, viewW: Float, viewH: Float) {
        val isLasso = isButtonPressed || _activeBrushType.value == BrushType.LASSO || _isCurrentStrokeLasso.value
        val worldPt = GeometryMath.screenToWorld(screenPoint.x, screenPoint.y, _viewport.value, viewW, viewH)
        val endPoint = screenPoint.copy(x = worldPt.x, y = worldPt.y)
        var rawPoints = _currentStrokePoints.value
        if (rawPoints.isEmpty()) {
            rawPoints = listOf(endPoint)
        } else if (rawPoints.last().x != endPoint.x || rawPoints.last().y != endPoint.y) {
            rawPoints = rawPoints + endPoint
        }

        if (rawPoints.isNotEmpty()) {
            val effectivePoints = if (rawPoints.size == 1) {
                listOf(rawPoints[0], rawPoints[0].copy(x = rawPoints[0].x + 0.5f, y = rawPoints[0].y + 0.5f))
            } else {
                rawPoints
            }

            if (isLasso) {
                if (effectivePoints.size >= 2) {
                    executeLassoSelection(effectivePoints)
                }
            } else {
                handleCompletedToolStroke(effectivePoints)
            }
        }

        _currentStrokePoints.value = emptyList()
        _isCurrentStrokeLasso.value = false
    }

    private fun handleCompletedToolStroke(rawPoints: List<RawPoint>) {
        val activeBrush = _activeBrushType.value
        val activeLayer = _layers.value.find { it.id == _activeLayerId.value }

        // Check if layer locked
        if (activeLayer?.isLocked == true) return

        when (activeBrush) {
            BrushType.SLICE -> {
                // Slice existing strokes that cross rawPoints
                pushUndoSnapshot()
                val currentStrokes = _strokes.value
                val newStrokeList = mutableListOf<VectorStroke>()
                var modified = false

                for (s in currentStrokes) {
                    if (s.layerId == _activeLayerId.value && !s.isDeleted) {
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
                }
            }

            BrushType.NUDGE -> {
                // Nudge stroke vertices along drag path
                if (rawPoints.size >= 2) {
                    pushUndoSnapshot()
                    val pStart = rawPoints.first()
                    val pEnd = rawPoints.last()
                    val dx = pEnd.x - pStart.x
                    val dy = pEnd.y - pStart.y
                    val radius = _activeStrokeWidth.value * 5f

                    _strokes.value = _strokes.value.map { s ->
                        if (s.layerId == _activeLayerId.value && !s.isDeleted) {
                            GeometryMath.nudgeStroke(s, pStart.x, pStart.y, radius, dx, dy)
                        } else s
                    }
                }
            }

            BrushType.ERASER_HARD -> {
                // Hard vector eraser: removes strokes on contact
                pushUndoSnapshot()
                val eraserRadius = _activeStrokeWidth.value
                _strokes.value = _strokes.value.filter { s ->
                    if (s.layerId != _activeLayerId.value) return@filter true
                    // Keep strokes that don't collide
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
            }

            BrushType.ERASER_MASK -> {
                // Mask eraser marks strokes as masked
                pushUndoSnapshot()
                val eraserRadius = _activeStrokeWidth.value
                _strokes.value = _strokes.value.map { s ->
                    if (s.layerId != _activeLayerId.value) return@map s
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
            }
        }
        scheduleAutoSave()
    }

    private fun executeLassoSelection(lassoPoints: List<RawPoint>) {
        val selectedStrokes = mutableSetOf<String>()
        val selectedImages = mutableSetOf<String>()

        // Check all strokes across visible and unlocked layers
        val unlockedLayerIds = _layers.value.filter { it.isVisible && !it.isLocked }.map { it.id }.toSet()

        for (s in _strokes.value) {
            if (s.layerId in unlockedLayerIds && !s.isDeleted) {
                if (GeometryMath.isStrokeSelectedByLasso(s, lassoPoints)) {
                    selectedStrokes.add(s.id)
                }
            }
        }

        for (img in _images.value) {
            if (img.layerId in unlockedLayerIds) {
                if (GeometryMath.isImageSelectedByLasso(img, lassoPoints)) {
                    selectedImages.add(img.id)
                }
            }
        }

        val strokesSubset = _strokes.value.filter { it.id in selectedStrokes }
        val imagesSubset = _images.value.filter { it.id in selectedImages }
        val bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset)

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
        _viewport.value = data.viewport
        _gridType.value = data.gridType
        _fingerMode.value = FingerMode.PAN
        clearSelection()
        undoStack.clear()
        redoStack.clear()
    }

    fun closeCurrentProject() {
        saveCurrentProjectNow()
        _currentProjectId.value = null
        clearSelection()
    }

    fun saveCurrentProjectNow() {
        val projId = _currentProjectId.value ?: return
        val repo = repository ?: return
        val data = ProjectDrawingData(
            layers = _layers.value,
            strokes = _strokes.value,
            images = _images.value,
            viewport = _viewport.value,
            gridType = _gridType.value
        )
        repo.autoSaveProjectAsync(projId, _currentProjectTitle.value, data)
    }

    fun scheduleAutoSave() {
        saveCurrentProjectNow()
    }

    // Selection actions (Minimal floating toolbar parity)
    fun copySelection() {
        val sel = _selection.value
        if (sel.isEmpty) return

        pushUndoSnapshot()

        // Clone selected strokes with offset
        val offset = 40f
        val clonedStrokes = mutableListOf<VectorStroke>()
        val clonedStrokeIds = mutableSetOf<String>()

        for (s in _strokes.value) {
            if (s.id in sel.selectedStrokeIds) {
                val newId = UUID.randomUUID().toString()
                val movedPoints = s.points.map { p -> p.copy(x = p.x + offset, y = p.y + offset) }
                val clone = s.copy(
                    id = newId,
                    points = movedPoints,
                    bounds = VectorStroke.calculateBounds(movedPoints)
                )
                clonedStrokes.add(clone)
                clonedStrokeIds.add(newId)
            }
        }

        // Clone selected images with offset
        val clonedImages = mutableListOf<CanvasImageElement>()
        val clonedImageIds = mutableSetOf<String>()

        for (img in _images.value) {
            if (img.id in sel.selectedImageIds) {
                val newId = UUID.randomUUID().toString()
                val clone = img.copy(
                    id = newId,
                    worldX = img.worldX + offset,
                    worldY = img.worldY + offset
                )
                clonedImages.add(clone)
                clonedImageIds.add(newId)
            }
        }

        _strokes.value = _strokes.value + clonedStrokes
        _images.value = _images.value + clonedImages

        val newBounds = GeometryMath.computeSelectionBounds(clonedStrokes, clonedImages)
        _selection.value = SelectionState(
            selectedStrokeIds = clonedStrokeIds,
            selectedImageIds = clonedImageIds,
            bounds = newBounds,
            isCopyPending = true
        )
        scheduleAutoSave()
    }

    fun deleteSelection() {
        val sel = _selection.value
        if (sel.isEmpty) return

        pushUndoSnapshot()
        _strokes.value = _strokes.value.filter { it.id !in sel.selectedStrokeIds }
        _images.value = _images.value.filter { it.id !in sel.selectedImageIds }
        clearSelection()
        scheduleAutoSave()
    }

    fun mirrorSelectionHorizontal() {
        val sel = _selection.value
        val bounds = sel.bounds ?: return
        pushUndoSnapshot()

        val centerX = bounds.centerX()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) {
                GeometryMath.mirrorStrokeHorizontal(s, centerX)
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds) {
                img.copy(
                    worldX = 2 * centerX - img.worldX,
                    scaleX = -img.scaleX,
                    isMirroredH = !img.isMirroredH
                )
            } else img
        }

        val strokesSubset = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val imagesSubset = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset))
        scheduleAutoSave()
    }

    fun mirrorSelectionVertical() {
        val sel = _selection.value
        val bounds = sel.bounds ?: return
        pushUndoSnapshot()

        val centerY = bounds.centerY()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) {
                GeometryMath.mirrorStrokeVertical(s, centerY)
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds) {
                img.copy(
                    worldY = 2 * centerY - img.worldY,
                    scaleY = -img.scaleY,
                    isMirroredV = !img.isMirroredV
                )
            } else img
        }

        val strokesSubset = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val imagesSubset = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset))
        scheduleAutoSave()
    }

    fun moveSelectionBy(dx: Float, dy: Float) {
        val sel = _selection.value
        if (sel.isEmpty) return

        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) {
                val moved = s.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) }
                s.copy(points = moved, bounds = VectorStroke.calculateBounds(moved))
            } else s
        }

        _images.value = _images.value.map { img ->
            if (img.id in sel.selectedImageIds) {
                img.copy(worldX = img.worldX + dx, worldY = img.worldY + dy)
            } else img
        }

        val strokesSubset = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val imagesSubset = _images.value.filter { it.id in sel.selectedImageIds }
        _selection.value = sel.copy(bounds = GeometryMath.computeSelectionBounds(strokesSubset, imagesSubset))
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

        // Transform strokes around geometric bounding center
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

        // Transform images around geometric bounding center
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

    fun clearSelection() {
        _selection.value = SelectionState()
        _quickMenuPoint.value = null
    }

    fun modifySelectionColor(newColor: Int) {
        val sel = _selection.value
        if (sel.selectedStrokeIds.isEmpty()) return
        pushUndoSnapshot()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) s.copy(color = newColor) else s
        }
        scheduleAutoSave()
    }

    fun modifySelectionWidth(newWidth: Float) {
        val sel = _selection.value
        if (sel.selectedStrokeIds.isEmpty()) return
        pushUndoSnapshot()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) s.copy(baseWidth = newWidth) else s
        }
        scheduleAutoSave()
    }

    fun modifySelectionBrush(newBrush: BrushType) {
        val sel = _selection.value
        if (sel.selectedStrokeIds.isEmpty()) return
        pushUndoSnapshot()
        _strokes.value = _strokes.value.map { s ->
            if (s.id in sel.selectedStrokeIds) s.copy(brushType = newBrush) else s
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
            saveUserWheelPreferences()
        }
    }

    fun setActiveColor(color: Int) {
        _activeColor.value = color
        updateCurrentSlot { it.copy(color = color) }
        userSettings?.saveActiveColor(color)
        userSettings?.saveToolSlots(_toolSlots.value)
        saveUserWheelPreferences()
    }

    fun setActiveBrush(brushType: BrushType) {
        _activeBrushType.value = brushType
        updateCurrentSlot { it.copy(brushType = brushType) }
        userSettings?.saveActiveBrushType(brushType)
        userSettings?.saveToolSlots(_toolSlots.value)
        saveUserWheelPreferences()
    }

    fun setActiveStrokeWidth(width: Float) {
        _activeStrokeWidth.value = width
        updateCurrentSlot { it.copy(strokeWidth = width) }
        userSettings?.saveActiveSize(width)
        userSettings?.saveToolSlots(_toolSlots.value)
        saveUserWheelPreferences()
    }

    fun setActiveOpacity(opacity: Float) {
        _activeOpacity.value = opacity
        updateCurrentSlot { it.copy(opacity = opacity) }
        userSettings?.saveActiveOpacity(opacity)
        userSettings?.saveToolSlots(_toolSlots.value)
        saveUserWheelPreferences()
    }

    fun setActiveSmoothing(smoothing: Float) {
        _activeSmoothing.value = smoothing
        updateCurrentSlot { it.copy(smoothing = smoothing) }
        userSettings?.saveActiveSmoothing(smoothing)
        userSettings?.saveToolSlots(_toolSlots.value)
        saveUserWheelPreferences()
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
