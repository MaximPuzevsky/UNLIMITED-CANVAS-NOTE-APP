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
import com.example.engine.QuadTree
import com.example.model.BrushType
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
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
    private val choreographer = Choreographer.getInstance()
    private var isFrameCallbackScheduled = false
    private val frameCallback = Choreographer.FrameCallback {
        isFrameCallbackScheduled = false
        invalidate()
    }

    fun requestPacedInvalidate() {
        if (!isFrameCallbackScheduled) {
            isFrameCallbackScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    // Spatial Indexing (QuadTree Culling)
    private var cachedQuadTree: QuadTree? = null
    private var cachedStrokesRef: List<VectorStroke>? = null

    private fun getOrCreateQuadTree(): QuadTree {
        val currentList = strokes
        if (cachedQuadTree == null || cachedStrokesRef !== currentList) {
            cachedQuadTree = QuadTree.build(currentList)
            cachedStrokesRef = currentList
        }
        return cachedQuadTree!!
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
            c.scale(bmpW / selW, bmpH / selH)
            c.translate(-bounds.left, -bounds.top)

            // Render selected images onto offscreen cache
            val selImages = images.filter { it.id in sel.selectedImageIds }
            for (img in selImages) {
                c.save()
                c.translate(img.worldX, img.worldY)
                c.rotate(img.rotationDeg)
                c.scale(img.scaleX, img.scaleY)
                imagePaint.alpha = (img.opacity * 255f).toInt()
                c.drawBitmap(img.bitmap, -img.width / 2f, -img.height / 2f, imagePaint)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Draw canvas dark matte background
        backgroundPaint.color = canvasBackgroundColor
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        val vp = viewport
        val cx = w / 2f
        val cy = h / 2f

        // 2. Render background grid in world coordinate space
        renderGrid(canvas, vp, w, h)

        // 3. Compute viewport world coordinate bounding box for QuadTree spatial culling
        val p0 = GeometryMath.screenToWorld(0f, 0f, vp, w, h)
        val p1 = GeometryMath.screenToWorld(w, 0f, vp, w, h)
        val p2 = GeometryMath.screenToWorld(w, h, vp, w, h)
        val p3 = GeometryMath.screenToWorld(0f, h, vp, w, h)
        val minX = minOf(p0.x, p1.x, p2.x, p3.x) - 100f
        val maxX = maxOf(p0.x, p1.x, p2.x, p3.x) + 100f
        val minY = minOf(p0.y, p1.y, p2.y, p3.y) - 100f
        val maxY = maxOf(p0.y, p1.y, p2.y, p3.y) + 100f
        val viewportWorldBounds = RectF(minX, minY, maxX, maxY)

        val visibleStrokes = mutableSetOf<VectorStroke>()
        getOrCreateQuadTree().query(viewportWorldBounds, visibleStrokes)

        // 4. Apply Camera Viewport Transform Matrix: Center -> Pan -> Zoom -> Rotate
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(vp.rotationDeg)
        canvas.scale(vp.zoom, vp.zoom)
        canvas.translate(vp.panX, vp.panY)

        val sortedLayers = layers.sortedBy { it.orderIndex }
        val allStrokes = strokes
        val allImages = images
        val sel = selection

        // 5. Render layers
        for (layer in sortedLayers) {
            if (!layer.isVisible) continue

            // A. Render images in this layer
            val layerImages = allImages.filter { it.layerId == layer.id }
            for (img in layerImages) {
                // If this image is part of transforming selection, skip drawing it in base layer
                if (isSelectionTransforming && img.id in sel.selectedImageIds) continue

                canvas.save()
                canvas.translate(img.worldX, img.worldY)
                canvas.rotate(img.rotationDeg)
                canvas.scale(img.scaleX, img.scaleY)
                imagePaint.alpha = (img.opacity * layer.opacity * 255f).toInt()
                canvas.drawBitmap(img.bitmap, -img.width / 2f, -img.height / 2f, imagePaint)
                canvas.restore()
            }

            // B. Render vector strokes in this layer with QuadTree culling
            val layerStrokes = allStrokes.filter { it.layerId == layer.id && !it.isDeleted && !it.isMasked }
            for (stroke in layerStrokes) {
                // If stroke is part of transforming selection, skip base render
                if (isSelectionTransforming && stroke.id in sel.selectedStrokeIds) continue
                // Viewport culling: only draw strokes within active viewport
                if (visibleStrokes.isNotEmpty() && !visibleStrokes.contains(stroke)) continue
                renderStroke(canvas, stroke, layer.opacity)
            }
        }

        // 6. If selection is currently transforming, render the cached texture with static transform matrix!
        if (isSelectionTransforming && selectionCachedBitmap != null && selectionBaseBounds != null) {
            canvas.save()
            canvas.translate(selectionBaseCenterX + selectionAccumDx, selectionBaseCenterY + selectionAccumDy)
            canvas.rotate(selectionAccumRotate)
            canvas.scale(selectionAccumScale, selectionAccumScale)
            canvas.translate(-selectionBaseCenterX, -selectionBaseCenterY)

            val destRect = selectionBaseBounds!!
            imagePaint.alpha = 255
            canvas.drawBitmap(selectionCachedBitmap!!, null, destRect, imagePaint)

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
            val b = sel.bounds
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

        // 7. Render active in-progress stroke
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
    }

    private fun renderStroke(canvas: Canvas, stroke: VectorStroke, layerOpacity: Float) {
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

        val path = Path()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)

        for (i in 1 until stroke.points.size) {
            val p0 = stroke.points[i - 1]
            val p1 = stroke.points[i]
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f
            path.quadTo(p0.x, p0.y, midX, midY)
        }
        val last = stroke.points.last()
        path.lineTo(last.x, last.y)

        canvas.drawPath(path, strokePaint)
    }

    private fun renderGrid(canvas: Canvas, vp: ViewportState, w: Float, h: Float) {
        if (gridType == GridType.NONE) return

        val spacing = 50f * vp.zoom
        if (spacing < 10f) return

        val cx = w / 2f
        val cy = h / 2f
        val offsetX = (vp.panX * vp.zoom + cx) % spacing
        val offsetY = (vp.panY * vp.zoom + cy) % spacing

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
