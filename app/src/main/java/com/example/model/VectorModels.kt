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
 * Fully parametric vector stroke stored in infinite world coordinate space.
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
    val isMasked: Boolean = false
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
 * State of selected items (strokes and imported images).
 */
data class SelectionState(
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedImageIds: Set<String> = emptySet(),
    val lassoPoints: List<RawPoint> = emptyList(),
    val isLassoActive: Boolean = false,
    val bounds: RectF? = null,
    val isCopyPending: Boolean = false // When true, only copied items are actively selected and movable
) {
    val isEmpty: Boolean get() = selectedStrokeIds.isEmpty() && selectedImageIds.isEmpty()
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
 * COPIC-inspired color spectrum entry.
 */
data class CopicColor(
    val code: String,
    val name: String,
    val colorInt: Int,
    val family: String
)

/**
 * Mode for single finger / normal touch input on the infinite canvas.
 */
enum class FingerMode(val displayName: String) {
    DRAW("Finger Draw"),
    PAN("Finger Pan")
}
