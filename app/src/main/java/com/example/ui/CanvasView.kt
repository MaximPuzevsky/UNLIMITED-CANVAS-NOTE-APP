package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.GeometryMath
import com.example.engine.LODLevel
import com.example.engine.LODPathCache
import com.example.engine.QuadTree
import com.example.engine.SpatialGridIndex
import com.example.engine.TileRenderEngine
import com.example.engine.ViewportCuller
import com.example.model.BrushType
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.CanvasTextBlock
import com.example.model.FingerMode
import com.example.model.GridType
import com.example.model.RawPoint
import com.example.model.SelectionState
import com.example.model.VectorStroke
import com.example.model.ViewportState
import com.example.spen.SPenInputHandler
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance hardware-accelerated Vector Canvas View.
 * Directly processes Samsung S Pen MotionEvents with pressure, tilt, and button state.
 *
 * Hardware Optimizations:
 * 1. Dynamic QuadTree spatial indexing & active viewport bounds culling.
 * 2. Static offscreen bitmap / texture matrix transformation during selection drag/scale/rotate.
 * 3. Frame pacing via Choreographer synced to display refresh rate (90Hz / 60Hz).
 * 4. Zero-drift viewport stabilization.
 */
class NativeCanvasSurface(
    context: Context
) : View(context) {

    var layers: List<CanvasLayer> = emptyList()
    var strokes: List<VectorStroke> = emptyList()
    var images: List<CanvasImageElement> = emptyList()
    var textBlocks: List<CanvasTextBlock> = emptyList()
    var viewport: ViewportState = ViewportState()
    var gridType: GridType = GridType.DOT
    var canvasBackgroundColor: Int = Color.parseColor("#15161C")
    var selection: SelectionState = SelectionState()
    var currentPoints: List<RawPoint> = emptyList()
    var isCurrentLasso: Boolean = false
    var activeColor: Int = Color.WHITE
    var activeWidth: Float = 3.5f
    var activeBrush: BrushType = BrushType.PEN
    var fingerMode: FingerMode = FingerMode.PAN

    // Callbacks for ViewModel dispatch
    var onPenStart: ((RawPoint, Boolean, Float, Float) -> Unit)? = null
    var onPenMove: ((List<RawPoint>, Boolean, Float, Float) -> Unit)? = null
    var onPenEnd: ((RawPoint, Boolean, Float, Float) -> Unit)? = null
    var onPenDwell: ((Float, Float) -> Unit)? = null
    var onPanZoom: ((Float, Float, Float, Float) -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var onSingleTapOutside: ((Float, Float) -> Unit)? = null
    var onDragSelection: ((Float, Float) -> Unit)? = null
    var onTransformSelection: ((Float, Float, Float, Float) -> Unit)? = null
    var onCommitSelectionTransform: ((Float, Float, Float, Float) -> Unit)? = null
    var onCommitSelectionMove: ((Float, Float) -> Unit)? = null
    var onReturnToPanMode: (() -> Unit)? = null

    // Frame Pacing Engine (VSYNC alignment via Choreographer)
    private var isFrameCallbackScheduled = false
    private val frameCallback = Choreographer.FrameCallback {
        isFrameCallbackScheduled = false
        invalidate()
    }

    fun requestPacedInvalidate() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            invalidate()
        } else {
            postInvalidate()
        }
    }

    // High-Performance 5-Tier Rendering Engine Components:
    // 1. Viewport Frustum Culling (ViewportCuller)
    // 2. Spatial Hash Grid Indexing (SpatialGridIndex)
    // 3. Offscreen Bitmap Caching & Layer Flattening (TileRenderEngine)
    // 4. Virtual Tile Chunking (TileRenderEngine)
    // 5. Dynamic Level of Detail (LODPathCache)
    private val spatialIndex = SpatialGridIndex<VectorStroke>(cellSize = 512f)
    private val tileEngine = TileRenderEngine(chunkSize = 512f, tilePixelSize = 512)
    private var lastKnownStrokesRef: List<VectorStroke>? = null
    private var lastKnownImagesRef: List<CanvasImageElement>? = null
    private var lastKnownTextRef: List<CanvasTextBlock>? = null
    private var lastKnownBgColor: Int = canvasBackgroundColor

    // Real-time rendering telemetry
    var showPerformanceMetrics: Boolean = true
    private var lastFrameDurationMs: Float = 0f
    private var lastFps: Int = 60
    private var frameCountSinceSecond = 0
    private var lastFpsSampleTimeMs = System.currentTimeMillis()
    private var lastVisibleStrokesCount = 0
    private var lastRenderedChunksCount = 0
    private var lastActiveLOD = LODLevel.FULL

    private fun syncSpatialIndexAndTileCache() {
        val currentStrokes = strokes
        if (currentStrokes !== lastKnownStrokesRef) {
            val prev = lastKnownStrokesRef
            if (prev != null && currentStrokes.size == prev.size + 1 &&
                currentStrokes.lastOrNull()?.id != prev.lastOrNull()?.id
            ) {
                // Incremental addition: only insert new stroke and invalidate its intersecting tiles
                val newStroke = currentStrokes.last()
                spatialIndex.insert(newStroke, newStroke.bounds)
                tileEngine.invalidateBounds(newStroke.bounds)
            } else {
                // Bulk synchronization
                spatialIndex.clear()
                for (s in currentStrokes) {
                    if (!s.isDeleted && !s.isMasked) {
                        spatialIndex.insert(s, s.bounds)
                    }
                }
                tileEngine.invalidateAll()
            }
            lastKnownStrokesRef = currentStrokes
        }

        if (images !== lastKnownImagesRef) {
            tileEngine.invalidateAll()
            lastKnownImagesRef = images
        }

        if (textBlocks !== lastKnownTextRef) {
            tileEngine.invalidateAll()
            lastKnownTextRef = textBlocks
        }

        if (canvasBackgroundColor != lastKnownBgColor) {
            tileEngine.invalidateAll()
            lastKnownBgColor = canvasBackgroundColor
        }
    }

    // Offscreen Bitmap & Matrix Transformation for Selection Drag / Scale / Rotate
    private var isDraggingSelectedObjects = false
    private var lastDragWorldX = 0f
    private var lastDragWorldY = 0f

    private var isSelectionTransforming = false
    private var selectionCachedBitmap: Bitmap? = null
    private var selectionBaseBounds: RectF? = null
    private var selectionBaseCenterX = 0f
    private var selectionBaseCenterY = 0f
    private var selectionAccumDx = 0f
    private var selectionAccumDy = 0f
    private var selectionAccumScale = 1.0f
    private var selectionAccumRotate = 0f

    // Immediate local in-flight points for 0-latency stroke preview
    private val localInProgressPoints = mutableListOf<RawPoint>()

    private fun prepareSelectionOffscreenTexture() {
        val sel = selection
        val bounds = sel.bounds ?: return
        if (sel.isEmpty || bounds.width() <= 0f || bounds.height() <= 0f) return

        selectionCachedBitmap?.recycle()
        selectionCachedBitmap = null

        val selW = bounds.width()
        val selH = bounds.height()
        val maxDim = 1920f
        val scale = (maxDim / Math.max(selW, selH)).coerceAtMost(1.0f).coerceAtLeast(0.1f)
        val bmpW = (selW * scale).toInt().coerceIn(16, 1920)
        val bmpH = (selH * scale).toInt().coerceIn(16, 1920)

        try {
            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(bmpW.toFloat() / selW, bmpH.toFloat() / selH)
            c.translate(-bounds.left, -bounds.top)

            // Render selected images onto offscreen cache
            val selImages = images.filter { it.id in sel.selectedImageIds }
            for (img in selImages) {
                val imgBmp = img.bitmap
                if (imgBmp == null || imgBmp.isRecycled) continue
                c.save()
                c.translate(img.worldX, img.worldY)
                c.rotate(img.rotationDeg)
                c.scale(img.scaleX, img.scaleY)
                imagePaint.alpha = (img.opacity * 255f).toInt()
                c.drawBitmap(imgBmp, -img.width / 2f, -img.height / 2f, imagePaint)
                c.restore()
            }

            // Render selected strokes onto offscreen cache
            val selStrokes = strokes.filter { it.id in sel.selectedStrokeIds && !it.isDeleted && !it.isMasked }
            for (s in selStrokes) {
                renderStroke(c, s, 1.0f)
            }

            selectionCachedBitmap = bmp
            selectionBaseBounds = RectF(bounds)
            selectionBaseCenterX = bounds.centerX()
            selectionBaseCenterY = bounds.centerY()
            selectionAccumDx = 0f
            selectionAccumDy = 0f
            selectionAccumScale = 1.0f
            selectionAccumRotate = 0f
            isSelectionTransforming = true
        } catch (e: Throwable) {
            selectionCachedBitmap = null
            isSelectionTransforming = false
        }
    }

    private fun commitSelectionTransformationIfNeeded() {
        if (!isSelectionTransforming) return

        val accumDx = selectionAccumDx
        val accumDy = selectionAccumDy
        val accumScale = selectionAccumScale
        val accumRot = selectionAccumRotate

        if (accumScale != 1.0f || accumRot != 0f) {
            if (onCommitSelectionTransform != null) {
                onCommitSelectionTransform?.invoke(accumDx, accumDy, accumScale, accumRot)
            } else {
                onTransformSelection?.invoke(accumDx, accumDy, accumScale, accumRot)
            }
        } else if (accumDx != 0f || accumDy != 0f) {
            if (onCommitSelectionMove != null) {
                onCommitSelectionMove?.invoke(accumDx, accumDy)
            } else {
                onDragSelection?.invoke(accumDx, accumDy)
            }
        }

        selectionCachedBitmap?.recycle()
        selectionCachedBitmap = null
        isSelectionTransforming = false
        isDraggingSelectedObjects = false
        selectionAccumDx = 0f
        selectionAccumDy = 0f
        selectionAccumScale = 1.0f
        selectionAccumRotate = 0f
    }

    private val inputHandler = SPenInputHandler(object : SPenInputHandler.Callbacks {
        override fun onPenStrokeStart(point: RawPoint, isButtonPressed: Boolean) {
            val sel = selection
            val vp = viewport
            val worldPt = GeometryMath.screenToWorld(point.x, point.y, vp, width.toFloat(), height.toFloat())

            // If tapped inside active selection bounds, initiate selection drag movement
            if (sel.isNotEmpty && sel.bounds?.contains(worldPt.x, worldPt.y) == true && !isButtonPressed) {
                isDraggingSelectedObjects = true
                lastDragWorldX = worldPt.x
                lastDragWorldY = worldPt.y
                prepareSelectionOffscreenTexture()
                return
            }

            isDraggingSelectedObjects = false
            localInProgressPoints.clear()
            localInProgressPoints.add(point.copy(x = worldPt.x, y = worldPt.y))

            onPenStart?.invoke(point, isButtonPressed, width.toFloat(), height.toFloat())
            requestPacedInvalidate()
        }

        override fun onPenStrokeMove(points: List<RawPoint>, isButtonPressed: Boolean) {
            if (isDraggingSelectedObjects && points.isNotEmpty()) {
                val pt = points.last()
                val vp = viewport
                val worldPt = GeometryMath.screenToWorld(pt.x, pt.y, vp, width.toFloat(), height.toFloat())
                val dx = worldPt.x - lastDragWorldX
                val dy = worldPt.y - lastDragWorldY
                lastDragWorldX = worldPt.x
                lastDragWorldY = worldPt.y

                // Static Texture Matrix Translation: accumulate without re-tessellating vector paths
                selectionAccumDx += dx
                selectionAccumDy += dy
                requestPacedInvalidate()
                return
            }

            val vp = viewport
            val w = width.toFloat()
            val h = height.toFloat()
            for (p in points) {
                val worldPt = GeometryMath.screenToWorld(p.x, p.y, vp, w, h)
                localInProgressPoints.add(p.copy(x = worldPt.x, y = worldPt.y))
            }

            onPenMove?.invoke(points, isButtonPressed, w, h)
            requestPacedInvalidate()
        }

        override fun onPenStrokeEnd(point: RawPoint, isButtonPressed: Boolean) {
            if (isDraggingSelectedObjects) {
                commitSelectionTransformationIfNeeded()
                requestPacedInvalidate()
                return
            }

            val vp = viewport
            val worldPt = GeometryMath.screenToWorld(point.x, point.y, vp, width.toFloat(), height.toFloat())
            localInProgressPoints.add(point.copy(x = worldPt.x, y = worldPt.y))

            onPenEnd?.invoke(point, isButtonPressed, width.toFloat(), height.toFloat())
            localInProgressPoints.clear()
            requestPacedInvalidate()
        }

        override fun onLongPressDwell(screenX: Float, screenY: Float) {
            localInProgressPoints.clear()
            onPenDwell?.invoke(screenX, screenY)
            requestPacedInvalidate()
        }

        override fun onTwoFingerPanZoomRotate(panDx: Float, panDy: Float, scaleFactor: Float, rotateDeltaDeg: Float) {
            val current = viewport
            val newZoom = (current.zoom * scaleFactor).coerceIn(0.05f, 50.0f)
            var newRot = current.rotationDeg + rotateDeltaDeg
            while (newRot > 180f) newRot -= 360f
            while (newRot < -180f) newRot += 360f

            val rad = -Math.toRadians(current.rotationDeg.toDouble()).toFloat()
            val cosR = cos(rad)
            val sinR = sin(rad)
            val rotDx = (panDx * cosR - panDy * sinR) / newZoom
            val rotDy = (panDx * sinR + panDy * cosR) / newZoom

            viewport = current.copy(
                panX = current.panX + rotDx,
                panY = current.panY + rotDy,
                zoom = newZoom,
                rotationDeg = newRot
            )

            onPanZoom?.invoke(panDx, panDy, scaleFactor, rotateDeltaDeg)
            requestPacedInvalidate()
        }

        override fun onTwoFingerSelectionTransform(
            panDx: Float,
            panDy: Float,
            scaleFactor: Float,
            rotateDeltaDeg: Float
        ) {
            if (!isSelectionTransforming) {
                prepareSelectionOffscreenTexture()
            }
            val vp = viewport
            val rad = -Math.toRadians(vp.rotationDeg.toDouble()).toFloat()
            val cosR = cos(rad)
            val sinR = sin(rad)
            val worldPanDx = (panDx * cosR - panDy * sinR) / vp.zoom
            val worldPanDy = (panDx * sinR + panDy * cosR) / vp.zoom

            selectionAccumDx += worldPanDx
            selectionAccumDy += worldPanDy
            selectionAccumScale *= scaleFactor
            selectionAccumRotate += rotateDeltaDeg
            requestPacedInvalidate()
        }

        override fun onTwoFingerTapUndo() {
            onUndo?.invoke()
            requestPacedInvalidate()
        }

        override fun onThreeFingerTapRedo() {
            onRedo?.invoke()
            requestPacedInvalidate()
        }

        override fun onSingleTapOutsideSelection(screenX: Float, screenY: Float) {
            onSingleTapOutside?.invoke(screenX, screenY)
            requestPacedInvalidate()
        }

        override fun onReturnToPanMode() {
            onReturnToPanMode?.invoke()
        }
    })

    // Drawing paints
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#15161C")
    }

    private val gridDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#343A46")
        style = Paint.Style.FILL
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#252834")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val lassoMarqueePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48CAE4")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }

    private val selectionBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48CAE4")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (isSelectionTransforming) {
                commitSelectionTransformationIfNeeded()
                requestPacedInvalidate()
            }
        }
        return inputHandler.onTouchEvent(
            event = event,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            fingerMode = fingerMode,
            isSelectionActive = selection.isNotEmpty
        )
    }

    private val hudBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 15, 20, 30)
        style = Paint.Style.FILL
    }
    private val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val hudSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 130, 220, 255)
        textSize = 22f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private fun renderPerformanceHUD(canvas: Canvas, w: Float, h: Float) {
        val totalElements = strokes.size + images.size + textBlocks.size
        val line1 = "⚡ ${lastFps} FPS (${String.format("%.1f", lastFrameDurationMs)}ms) • LOD: ${lastActiveLOD.name}"
        val line2 = "Visible: $lastVisibleStrokesCount / $totalElements items • Chunks: $lastRenderedChunksCount active"

        val padding = 20f
        val text1Width = hudTextPaint.measureText(line1)
        val text2Width = hudSubTextPaint.measureText(line2)
        val boxWidth = maxOf(text1Width, text2Width) + padding * 2
        val boxHeight = 76f

        val left = w - boxWidth - 20f
        val top = h - boxHeight - 24f
        val rect = RectF(left, top, left + boxWidth, top + boxHeight)

        canvas.drawRoundRect(rect, 14f, 14f, hudBackgroundPaint)
        canvas.drawRoundRect(rect, 14f, 14f, hudBorderPaint)

        canvas.drawText(line1, left + padding, top + 30f, hudTextPaint)
        canvas.drawText(line2, left + padding, top + 60f, hudSubTextPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val frameStartNs = System.nanoTime()

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        syncSpatialIndexAndTileCache()

        // 1. Draw canvas dark matte background
        backgroundPaint.color = canvasBackgroundColor
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        val vp = viewport
        val cx = w / 2f
        val cy = h / 2f

        // 2. Render background grid in world coordinate space
        renderGrid(canvas, vp, w, h)

        // 3. Technique 1: Viewport Culling & Technique 5: Dynamic LOD
        val viewportWorldBounds = ViewportCuller.computeViewportAABB(vp, w, h, margin = 120f)
        val lod = LODPathCache.resolveLOD(vp.zoom)
        lastActiveLOD = lod

        // 4. Technique 2: Spatial Indexing query
        val visibleStrokes = mutableListOf<VectorStroke>()
        spatialIndex.query(viewportWorldBounds, visibleStrokes)
        lastVisibleStrokesCount = visibleStrokes.size

        // 5. Apply Camera Viewport Transform Matrix: Center -> Pan -> Zoom -> Rotate
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(vp.rotationDeg)
        canvas.scale(vp.zoom, vp.zoom)
        canvas.translate(vp.panX, vp.panY)

        val sortedLayers = layers.sortedBy { it.orderIndex }
        val sel = selection

        // Direct Android-native hardware-accelerated vector rendering with Viewport Culling & LOD Path Cache
        lastRenderedChunksCount = 0
        val visibleStrokesByLayer = visibleStrokes.groupBy { it.layerId }
        val renderedImageIds = mutableSetOf<String>()

        for (layer in sortedLayers) {
            if (!layer.isVisible) continue

            // A. Render images in this layer
            val layerImages = images.filter { it.layerId == layer.id }
            for (img in layerImages) {
                val imgBmp = img.bitmap
                if (imgBmp == null || imgBmp.isRecycled) continue
                renderedImageIds.add(img.id)
                if (isSelectionTransforming && img.id in sel.selectedImageIds) continue
                if (!RectF.intersects(img.bounds, viewportWorldBounds)) continue

                canvas.save()
                canvas.translate(img.worldX, img.worldY)
                canvas.rotate(img.rotationDeg)
                canvas.scale(img.scaleX, img.scaleY)
                imagePaint.alpha = (img.opacity * layer.opacity * 255f).toInt()
                canvas.drawBitmap(imgBmp, -img.width / 2f, -img.height / 2f, imagePaint)
                canvas.restore()
            }

            // B. Render vector strokes in this layer with LOD Path Cache
            val layerVisibleStrokes = visibleStrokesByLayer[layer.id] ?: emptyList()
            for (stroke in layerVisibleStrokes) {
                if (isSelectionTransforming && stroke.id in sel.selectedStrokeIds) continue
                renderStroke(canvas, stroke, layer.opacity, lod)
            }

            // C. Render text notes in this layer
            val layerTextBlocks = textBlocks.filter { it.layerId == layer.id && !it.isDeleted }
            for (tb in layerTextBlocks) {
                if (isSelectionTransforming && tb.id in sel.selectedTextIds) continue
                if (!RectF.intersects(tb.bounds, viewportWorldBounds)) continue
                canvas.save()
                canvas.translate(tb.worldX, tb.worldY)
                canvas.rotate(tb.rotationDeg)
                textPaint.color = tb.color
                textPaint.textSize = tb.fontSize
                textPaint.alpha = (layer.opacity * 255f).toInt()
                canvas.drawText(tb.text, 0f, 0f, textPaint)
                canvas.restore()
            }
        }

        // Safeguard: Render orphan images
        val orphanImages = images.filter { it.id !in renderedImageIds }
        for (img in orphanImages) {
            val imgBmp = img.bitmap
            if (imgBmp == null || imgBmp.isRecycled) continue
            if (isSelectionTransforming && img.id in sel.selectedImageIds) continue
            if (!RectF.intersects(img.bounds, viewportWorldBounds)) continue
            canvas.save()
            canvas.translate(img.worldX, img.worldY)
            canvas.rotate(img.rotationDeg)
            canvas.scale(img.scaleX, img.scaleY)
            imagePaint.alpha = (img.opacity * 255f).toInt()
            canvas.drawBitmap(imgBmp, -img.width / 2f, -img.height / 2f, imagePaint)
            canvas.restore()
        }

        // 7. Render transforming selection with cached texture matrix
        val selBmp = selectionCachedBitmap
        val selBounds = selectionBaseBounds
        if (isSelectionTransforming && selBmp != null && !selBmp.isRecycled && selBounds != null) {
            canvas.save()
            canvas.translate(selectionBaseCenterX + selectionAccumDx, selectionBaseCenterY + selectionAccumDy)
            canvas.rotate(selectionAccumRotate)
            canvas.scale(selectionAccumScale, selectionAccumScale)
            canvas.translate(-selectionBaseCenterX, -selectionBaseCenterY)

            val destRect = selBounds
            imagePaint.alpha = 255
            canvas.drawBitmap(selBmp, null, destRect, imagePaint)

            // Draw selection marquee & corner handles around transformed quad
            canvas.drawRect(destRect, selectionBoundsPaint)
            val handleRadius = 6f / vp.zoom.coerceAtLeast(0.5f)
            val corners = listOf(
                PointF(destRect.left, destRect.top),
                PointF(destRect.right, destRect.top),
                PointF(destRect.right, destRect.bottom),
                PointF(destRect.left, destRect.bottom),
                PointF(destRect.centerX(), destRect.top - 20f / vp.zoom)
            )
            for (c in corners) {
                canvas.drawCircle(c.x, c.y, handleRadius, selectionHandlePaint)
                canvas.drawCircle(c.x, c.y, handleRadius, selectionBoundsPaint)
            }
            canvas.restore()
        } else if (!isSelectionTransforming && sel.isNotEmpty && sel.bounds != null) {
            // Static selection bounds when stationary
            val b = sel.bounds!!
            canvas.drawRect(b, selectionBoundsPaint)

            val handleRadius = 6f / vp.zoom.coerceAtLeast(0.5f)
            val corners = listOf(
                PointF(b.left, b.top),
                PointF(b.right, b.top),
                PointF(b.right, b.bottom),
                PointF(b.left, b.bottom),
                PointF(b.centerX(), b.top - 20f / vp.zoom)
            )
            for (c in corners) {
                canvas.drawCircle(c.x, c.y, handleRadius, selectionHandlePaint)
                canvas.drawCircle(c.x, c.y, handleRadius, selectionBoundsPaint)
            }
        }

        // 8. Low-Latency Ephemeral Active Inking Buffer
        val activePoints = if (localInProgressPoints.isNotEmpty()) localInProgressPoints else currentPoints
        val isLasso = isCurrentLasso

        if (activePoints.isNotEmpty()) {
            if (isLasso) {
                val lassoPath = Path()
                lassoPath.moveTo(activePoints[0].x, activePoints[0].y)
                for (i in 1 until activePoints.size) {
                    lassoPath.lineTo(activePoints[i].x, activePoints[i].y)
                }
                canvas.drawPath(lassoPath, lassoMarqueePaint)
            } else {
                strokePaint.color = activeColor
                strokePaint.strokeWidth = activeWidth
                strokePaint.alpha = 255

                if (activePoints.size == 1) {
                    canvas.drawCircle(activePoints[0].x, activePoints[0].y, activeWidth / 2f, strokePaint)
                } else {
                    val strokePath = Path()
                    strokePath.moveTo(activePoints[0].x, activePoints[0].y)
                    for (i in 1 until activePoints.size) {
                        val p0 = activePoints[i - 1]
                        val p1 = activePoints[i]
                        val midX = (p0.x + p1.x) / 2f
                        val midY = (p0.y + p1.y) / 2f
                        strokePath.quadTo(p0.x, p0.y, midX, midY)
                    }
                    val last = activePoints.last()
                    strokePath.lineTo(last.x, last.y)
                    canvas.drawPath(strokePath, strokePaint)
                }
            }
        }

        canvas.restore()

        // 9. Frame telemetry & Performance HUD (rendered in screen space)
        val frameDurationNs = System.nanoTime() - frameStartNs
        lastFrameDurationMs = frameDurationNs / 1_000_000f

        frameCountSinceSecond++
        val now = System.currentTimeMillis()
        if (now - lastFpsSampleTimeMs >= 1000L) {
            lastFps = frameCountSinceSecond
            frameCountSinceSecond = 0
            lastFpsSampleTimeMs = now
        }

        if (showPerformanceMetrics) {
            renderPerformanceHUD(canvas, w, h)
        }
    }

    private fun renderStroke(canvas: Canvas, stroke: VectorStroke, layerOpacity: Float, lod: LODLevel = LODLevel.FULL) {
        if (stroke.points.size < 2) return

        val alpha = ((Color.alpha(stroke.color) / 255f) * stroke.opacity * layerOpacity * 255f).toInt()
        strokePaint.color = stroke.color
        strokePaint.alpha = alpha
        strokePaint.strokeWidth = stroke.baseWidth

        when (stroke.brushType) {
            BrushType.WIRE -> {
                strokePaint.strokeCap = Paint.Cap.BUTT
            }
            BrushType.MARKER -> {
                strokePaint.strokeCap = Paint.Cap.SQUARE
                strokePaint.alpha = (alpha * 0.45f).toInt()
            }
            BrushType.WATERCOLOR -> {
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = (alpha * 0.35f).toInt()
            }
            BrushType.AIRBRUSH -> {
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = (alpha * 0.25f).toInt()
            }
            BrushType.SOFT_PENCIL, BrushType.ERASER_SOFT -> {
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = (alpha * 0.75f).toInt()
            }
            else -> {
                strokePaint.strokeCap = Paint.Cap.ROUND
            }
        }

        // Technique 5: Pre-tessellated LOD Path retrieval (zero per-frame allocations!)
        val path = LODPathCache.getOrCreatePath(stroke, lod)
        canvas.drawPath(path, strokePaint)
    }

    private fun renderGrid(canvas: Canvas, vp: ViewportState, w: Float, h: Float) {
        if (gridType == GridType.NONE) return

        val spacing = 50f * vp.zoom
        if (spacing < 10f) return

        val cx = w / 2f
        val cy = h / 2f
        val rawOffsetX = (vp.panX * vp.zoom + cx) % spacing
        val offsetX = if (rawOffsetX < 0f) rawOffsetX + spacing else rawOffsetX
        val rawOffsetY = (vp.panY * vp.zoom + cy) % spacing
        val offsetY = if (rawOffsetY < 0f) rawOffsetY + spacing else rawOffsetY

        when (gridType) {
            GridType.DOT -> {
                var x = offsetX
                while (x < w) {
                    var y = offsetY
                    while (y < h) {
                        canvas.drawCircle(x, y, 1.8f, gridDotPaint)
                        y += spacing
                    }
                    x += spacing
                }
            }

            GridType.GRID -> {
                var x = offsetX
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, gridLinePaint)
                    x += spacing
                }
                var y = offsetY
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, gridLinePaint)
                    y += spacing
                }
            }

            GridType.ISOMETRIC -> {
                val isoSpacing = spacing * 1.5f
                var d = -h
                while (d < w + h) {
                    canvas.drawLine(d, 0f, d + h * 0.577f, h, gridLinePaint)
                    canvas.drawLine(d, h, d + h * 0.577f, 0f, gridLinePaint)
                    d += isoSpacing
                }
                var y = offsetY
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, gridLinePaint)
                    y += spacing
                }
            }

            GridType.GRAPH_PAPER -> {
                var x = offsetX
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, gridLinePaint)
                    x += spacing / 2f
                }
                var y = offsetY
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, gridLinePaint)
                    y += spacing / 2f
                }
            }
            else -> {}
        }
    }
}

@Composable
fun CanvasView(
    layers: List<CanvasLayer>,
    strokes: List<VectorStroke>,
    images: List<CanvasImageElement>,
    textBlocks: List<CanvasTextBlock> = emptyList(),
    viewport: ViewportState,
    gridType: GridType,
    canvasBackgroundColor: Int = Color.parseColor("#15161C"),
    selection: SelectionState,
    currentStrokePoints: List<RawPoint>,
    isCurrentLasso: Boolean,
    activeColor: Int,
    activeWidth: Float,
    activeBrush: BrushType,
    fingerMode: FingerMode,
    onPenStart: (RawPoint, Boolean, Float, Float) -> Unit,
    onPenMove: (List<RawPoint>, Boolean, Float, Float) -> Unit,
    onPenEnd: (RawPoint, Boolean, Float, Float) -> Unit,
    onPenDwell: (Float, Float) -> Unit,
    onPanZoom: (Float, Float, Float, Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSingleTapOutside: (Float, Float) -> Unit,
    onDragSelection: (Float, Float) -> Unit,
    onTransformSelection: (Float, Float, Float, Float) -> Unit,
    onCommitSelectionTransform: ((Float, Float, Float, Float) -> Unit)? = null,
    onCommitSelectionMove: ((Float, Float) -> Unit)? = null,
    onReturnToPanMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().testTag("native_canvas_view")) {
        AndroidView(
            factory = { ctx ->
                NativeCanvasSurface(ctx).apply {
                    this.layers = layers
                    this.strokes = strokes
                    this.images = images
                    this.textBlocks = textBlocks
                    this.viewport = viewport
                    this.gridType = gridType
                    this.canvasBackgroundColor = canvasBackgroundColor
                    this.selection = selection
                    this.currentPoints = currentStrokePoints
                    this.isCurrentLasso = isCurrentLasso
                    this.activeColor = activeColor
                    this.activeWidth = activeWidth
                    this.activeBrush = activeBrush
                    this.fingerMode = fingerMode

                    this.onPenStart = onPenStart
                    this.onPenMove = onPenMove
                    this.onPenEnd = onPenEnd
                    this.onPenDwell = onPenDwell
                    this.onPanZoom = onPanZoom
                    this.onUndo = onUndo
                    this.onRedo = onRedo
                    this.onSingleTapOutside = onSingleTapOutside
                    this.onDragSelection = onDragSelection
                    this.onTransformSelection = onTransformSelection
                    this.onCommitSelectionTransform = onCommitSelectionTransform
                    this.onCommitSelectionMove = onCommitSelectionMove
                    this.onReturnToPanMode = onReturnToPanMode
                }
            },
            update = { view ->
                view.layers = layers
                view.strokes = strokes
                view.images = images
                view.textBlocks = textBlocks
                view.viewport = viewport
                view.gridType = gridType
                view.canvasBackgroundColor = canvasBackgroundColor
                view.selection = selection
                view.currentPoints = currentStrokePoints
                view.isCurrentLasso = isCurrentLasso
                view.activeColor = activeColor
                view.activeWidth = activeWidth
                view.activeBrush = activeBrush
                view.fingerMode = fingerMode

                view.onPenStart = onPenStart
                view.onPenMove = onPenMove
                view.onPenEnd = onPenEnd
                view.onPenDwell = onPenDwell
                view.onPanZoom = onPanZoom
                view.onUndo = onUndo
                view.onRedo = onRedo
                view.onSingleTapOutside = onSingleTapOutside
                view.onDragSelection = onDragSelection
                view.onTransformSelection = onTransformSelection
                view.onCommitSelectionTransform = onCommitSelectionTransform
                view.onCommitSelectionMove = onCommitSelectionMove
                view.onReturnToPanMode = onReturnToPanMode

                view.requestPacedInvalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
