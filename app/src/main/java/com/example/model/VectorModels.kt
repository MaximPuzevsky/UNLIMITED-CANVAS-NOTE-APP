package com.example.model

import android.graphics.Bitmap
import android.graphics.RectF
import java.util.UUID

/**
 * High-precision point sampled from hardware S Pen or touch digitizer.
 */
data class RawPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,      // 0.0f .. 1.0f (S Pen provides 4096 levels)
    val tiltX: Float = 0f,          // Radians
    val tiltY: Float = 0f,          // Radians
    val tiltAngle: Float = 0f,      // Angle relative to normal
    val orientation: Float = 0f,    // Azimuth in radians
    val velocity: Float = 0f,       // Pixels / ms
    val timestampNs: Long = System.nanoTime()
)

/**
 * Parity brush types matching Concepts.
 */
enum class BrushType(val displayName: String, val isUtility: Boolean = false) {
    PEN("Pen"),
    SOFT_PENCIL("Soft Pencil"),
    HARD_PENCIL("Hard Pencil"),
    FOUNTAIN_PEN("Fountain Pen"),
    WIRE("Wire"),
    MARKER("Marker"),
    WATERCOLOR("Watercolor"),
    AIRBRUSH("Airbrush"),
    SLICE("Slice Tool", isUtility = true),
    NUDGE("Nudge Tool", isUtility = true),
    ERASER_HARD("Hard Eraser", isUtility = true),
    ERASER_SOFT("Soft Eraser", isUtility = true),
    ERASER_MASK("Mask Eraser", isUtility = true),
    LASSO("Vector Lasso", isUtility = true)
}

/**
 * Parametric Bezier curve segment representing mathematical vector spline curves.
 * Holds control points and variable thickness parameters along the curve for infinite zooming.
 */
data class BezierCurveSegment(
    val startX: Float,
    val startY: Float,
    val cp1X: Float,
    val cp1Y: Float,
    val endX: Float,
    val endY: Float,
    val startWidth: Float,
    val endWidth: Float
)

/**
 * Fully parametric vector stroke stored in infinite world coordinate space.
 * Stores mathematical curve parameters (Bezier control points, thickness, smoothing)
 * rather than pixel raster arrays to support smooth infinite zooming.
 */
data class VectorStroke(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val brushType: BrushType,
    val color: Int,               // 32-bit ARGB
    val baseWidth: Float,         // World coordinate base stroke width
    val opacity: Float = 1.0f,    // 0.0f .. 1.0f
    val smoothing: Float = 0.5f,  // Streamline smoothing coefficient 0.0f .. 1.0f
    val points: List<RawPoint>,
    val bounds: RectF = calculateBounds(points),
    val isDeleted: Boolean = false,
    val isMasked: Boolean = false,
    val bezierSegments: List<BezierCurveSegment> = computeBezierSegments(points, baseWidth)
) {
    companion object {
        fun calculateBounds(points: List<RawPoint>): RectF {
            if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            val pad = 1.0f
            return RectF(minX - pad, minY - pad, maxX + pad, maxY + pad)
        }

        fun computeBezierSegments(points: List<RawPoint>, baseWidth: Float): List<BezierCurveSegment> {
            if (points.size < 2) return emptyList()
            val segments = ArrayList<BezierCurveSegment>(points.size - 1)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val midX = (p0.x + p1.x) / 2f
                val midY = (p0.y + p1.y) / 2f
                val w0 = baseWidth * (0.5f + p0.pressure * 0.5f)
                val w1 = baseWidth * (0.5f + p1.pressure * 0.5f)
                val startX = if (i == 1) p0.x else (points[i - 2].x + p0.x) / 2f
                val startY = if (i == 1) p0.y else (points[i - 2].y + p0.y) / 2f
                segments.add(
                    BezierCurveSegment(
                        startX = startX,
                        startY = startY,
                        cp1X = p0.x,
                        cp1Y = p0.y,
                        endX = midX,
                        endY = midY,
                        startWidth = w0,
                        endWidth = (w0 + w1) / 2f
                    )
                )
            }
            val lastP = points.last()
            val prevP = points[points.size - 2]
            val lastMidX = (prevP.x + lastP.x) / 2f
            val lastMidY = (prevP.y + lastP.y) / 2f
            val lastW = baseWidth * (0.5f + lastP.pressure * 0.5f)
            segments.add(
                BezierCurveSegment(
                    startX = lastMidX,
                    startY = lastMidY,
                    cp1X = (lastMidX + lastP.x) / 2f,
                    cp1Y = (lastMidY + lastP.y) / 2f,
                    endX = lastP.x,
                    endY = lastP.y,
                    startWidth = (baseWidth * (0.5f + prevP.pressure * 0.5f) + lastW) / 2f,
                    endWidth = lastW
                )
            )
            return segments
        }
    }
}

/**
 * First-class canvas element for imported screenshots, photos, or documents.
 * Can be drawn on directly, selected, moved, duplicated, mirrored, or scaled.
 */
data class CanvasImageElement(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val title: String = "Imported Image",
    val bitmap: Bitmap,
    val worldX: Float,
    val worldY: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val isMirroredH: Boolean = false,
    val isMirroredV: Boolean = false,
    val opacity: Float = 1f
) {
    val bounds: RectF
        get() {
            val halfW = (width * Math.abs(scaleX)) / 2f
            val halfH = (height * Math.abs(scaleY)) / 2f
            return RectF(worldX - halfW, worldY - halfH, worldX + halfW, worldY + halfH)
        }
}

/**
 * Concepts / Photoshop drawing layer.
 */
data class CanvasLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1.0f,
    val orderIndex: Int = 0
)

/**
 * Precision background grids.
 */
enum class GridType(val displayName: String) {
    NONE("Blank Canvas"),
    DOT("Dot Grid"),
    GRID("Line Grid"),
    ISOMETRIC("Isometric 3D"),
    GRAPH_PAPER("Graph Paper")
}

/**
 * Infinite viewport camera transform.
 */
data class ViewportState(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1.0f,          // 0.05f to 50.0f
    val rotationDeg: Float = 0f,     // Canvas rotation
    val angleSnapping: Boolean = true
)

/**
 * Text block element in infinite world coordinate space.
 */
data class CanvasTextBlock(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val text: String,
    val worldX: Float,
    val worldY: Float,
    val fontSize: Float = 22f,
    val color: Int = android.graphics.Color.WHITE,
    val rotationDeg: Float = 0f,
    val isDeleted: Boolean = false
) {
    val bounds: RectF
        get() {
            val estimatedWidth = (text.length * fontSize * 0.55f).coerceAtLeast(40f)
            val estimatedHeight = (fontSize * 1.3f).coerceAtLeast(24f)
            return RectF(worldX, worldY - estimatedHeight, worldX + estimatedWidth, worldY + 6f)
        }
}

/**
 * State of selected items (strokes, imported images, and text blocks).
 */
data class SelectionState(
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedImageIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val lassoPoints: List<RawPoint> = emptyList(),
    val isLassoActive: Boolean = false,
    val bounds: RectF? = null,
    val isCopyPending: Boolean = false // When true, only copied items are actively selected and movable
) {
    val isEmpty: Boolean get() = selectedStrokeIds.isEmpty() && selectedImageIds.isEmpty() && selectedTextIds.isEmpty()
    val isNotEmpty: Boolean get() = !isEmpty
}

/**
 * Configurable slot on the Concepts Tool Wheel.
 */
data class ToolSlot(
    val id: Int,
    val brushType: BrushType,
    val color: Int,
    val strokeWidth: Float,
    val opacity: Float,
    val smoothing: Float
)

/**
 * Mode for single finger / normal touch input on the infinite canvas.
 */
enum class FingerMode(val displayName: String) {
    DRAW("Finger Draw"),
    PAN("Finger Pan")
}
