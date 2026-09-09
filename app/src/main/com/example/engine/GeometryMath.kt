package com.example.engine

import android.graphics.PointF
import android.graphics.RectF
import com.example.model.CanvasImageElement
import com.example.model.RawPoint
import com.example.model.VectorStroke
import com.example.model.ViewportState
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeometryMath {

    /**
     * Converts a 2D screen coordinate to infinite world coordinates based on the viewport transform.
     */
    fun screenToWorld(
        screenX: Float,
        screenY: Float,
        viewport: ViewportState,
        viewWidth: Float,
        viewHeight: Float
    ): PointF {
        val cx = viewWidth / 2f
        val cy = viewHeight / 2f
        // Offset relative to center
        var dx = (screenX - cx)
        var dy = (screenY - cy)

        // Inverse rotate
        val rad = -Math.toRadians(viewport.rotationDeg.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)
        val rx = dx * cosR - dy * sinR
        val ry = dx * sinR + dy * cosR

        // Inverse scale and pan
        val wx = (rx / viewport.zoom) - viewport.panX
        val wy = (ry / viewport.zoom) - viewport.panY
        return PointF(wx, wy)
    }

    /**
     * Converts an infinite world coordinate to screen pixels.
     */
    fun worldToScreen(
        worldX: Float,
        worldY: Float,
        viewport: ViewportState,
        viewWidth: Float,
        viewHeight: Float
    ): PointF {
        val cx = viewWidth / 2f
        val cy = viewHeight / 2f

        val sx = (worldX + viewport.panX) * viewport.zoom
        val sy = (worldY + viewport.panY) * viewport.zoom

        // Rotate
        val rad = Math.toRadians(viewport.rotationDeg.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)
        val rx = sx * cosR - sy * sinR
        val ry = sx * sinR + sy * cosR

        return PointF(rx + cx, ry + cy)
    }

    /**
     * Applies Streamline smoothing to a series of raw points.
     * Uses trailing exponential moving average (EMA) interpolation.
     */
    fun smoothPoints(rawPoints: List<RawPoint>, smoothing: Float): List<RawPoint> {
        if (rawPoints.size < 3 || smoothing <= 0.01f) return rawPoints
        val alpha = (1.0f - smoothing * 0.85f).coerceIn(0.12f, 1.0f)
        val smoothed = ArrayList<RawPoint>(rawPoints.size)
        smoothed.add(rawPoints.first())

        var currX = rawPoints.first().x
        var currY = rawPoints.first().y

        for (i in 1 until rawPoints.size) {
            val target = rawPoints[i]
            currX += alpha * (target.x - currX)
            currY += alpha * (target.y - currY)
            smoothed.add(
                target.copy(
                    x = currX,
                    y = currY,
                    pressure = target.pressure
                )
            )
        }
        return smoothed
    }

    /**
     * Checks if a point (px, py) is strictly inside a polygon using ray-casting.
     */
    fun isPointInPolygon(px: Float, py: Float, polygon: List<RawPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > py) != (pj.y > py) &&
                (px < (pj.x - pi.x) * (py - pi.y) / ((pj.y - pi.y).takeIf { it != 0f } ?: 0.0001f) + pi.x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Checks if line segment AB intersects line segment CD.
     */
    fun doSegmentsIntersect(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float
    ): Boolean {
        fun ccw(p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float): Boolean {
            return (p3y - p1y) * (p2x - p1x) > (p2y - p1y) * (p3x - p1x)
        }
        return (ccw(ax, ay, cx, cy, dx, dy) != ccw(bx, by, cx, cy, dx, dy)) &&
                (ccw(ax, ay, bx, by, cx, cy) != ccw(ax, ay, bx, by, dx, dy))
    }

    /**
     * Computes the exact intersection point between segment AB and CD, if one exists.
     */
    fun findSegmentIntersection(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float
    ): PointF? {
        val denom = (by - ay) * (dx - cx) - (bx - ax) * (dy - cy)
        if (denom == 0f) return null

        val numA = (ax - cx) * (dy - cy) - (ay - cy) * (dx - cx)
        val numB = (ax - cx) * (by - ay) - (ay - cy) * (bx - ax)

        val ua = numA / denom
        val ub = numB / denom

        return if (ua in 0f..1f && ub in 0f..1f) {
            PointF(ax + ua * (bx - ax), ay + ua * (by - ay))
        } else null
    }

    /**
     * Vector Lasso test: Returns true if the stroke is inside OR touched by the lasso boundary.
     */
    fun isStrokeSelectedByLasso(stroke: VectorStroke, lasso: List<RawPoint>): Boolean {
        if (lasso.size < 3 || stroke.points.isEmpty()) return false

        // 1. Quick AABB reject
        val lassoBounds = VectorStroke.calculateBounds(lasso)
        if (!RectF.intersects(stroke.bounds, lassoBounds)) return false

        // 2. Check if any point of the stroke is inside the lasso
        for (p in stroke.points) {
            if (isPointInPolygon(p.x, p.y, lasso)) return true
        }

        // 3. Check if any segment of the stroke intersects any segment of the lasso
        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            for (j in lasso.indices) {
                val l1 = lasso[j]
                val l2 = lasso[(j + 1) % lasso.size]
                if (doSegmentsIntersect(p1.x, p1.y, p2.x, p2.y, l1.x, l1.y, l2.x, l2.y)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Vector Lasso test for imported images: returns true if inside OR touched.
     */
    fun isImageSelectedByLasso(image: CanvasImageElement, lasso: List<RawPoint>): Boolean {
        if (lasso.size < 3) return false
        val imgBounds = image.bounds
        val lassoBounds = VectorStroke.calculateBounds(lasso)
        if (!RectF.intersects(imgBounds, lassoBounds)) return false

        // Check center or corners inside lasso
        if (isPointInPolygon(imgBounds.centerX(), imgBounds.centerY(), lasso)) return true
        if (isPointInPolygon(imgBounds.left, imgBounds.top, lasso)) return true
        if (isPointInPolygon(imgBounds.right, imgBounds.bottom, lasso)) return true

        // Check boundary edge intersections
        val corners = listOf(
            PointF(imgBounds.left, imgBounds.top),
            PointF(imgBounds.right, imgBounds.top),
            PointF(imgBounds.right, imgBounds.bottom),
            PointF(imgBounds.left, imgBounds.bottom)
        )
        for (c in 0..3) {
            val c1 = corners[c]
            val c2 = corners[(c + 1) % 4]
            for (j in lasso.indices) {
                val l1 = lasso[j]
                val l2 = lasso[(j + 1) % lasso.size]
                if (doSegmentsIntersect(c1.x, c1.y, c2.x, c2.y, l1.x, l1.y, l2.x, l2.y)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Vector Slicer Tool: Cuts a stroke into multiple sub-strokes where intersected by slicePath.
     */
    fun sliceStroke(stroke: VectorStroke, slicePath: List<RawPoint>): List<VectorStroke> {
        if (stroke.points.size < 2 || slicePath.size < 2) return listOf(stroke)
        if (!RectF.intersects(stroke.bounds, VectorStroke.calculateBounds(slicePath))) {
            return listOf(stroke)
        }

        val resultStrokes = mutableListOf<VectorStroke>()
        var currentSegmentPoints = mutableListOf<RawPoint>()
        currentSegmentPoints.add(stroke.points[0])

        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            var intersected = false

            for (j in 0 until slicePath.size - 1) {
                val s1 = slicePath[j]
                val s2 = slicePath[j + 1]
                val hit = findSegmentIntersection(p1.x, p1.y, p2.x, p2.y, s1.x, s1.y, s2.x, s2.y)
                if (hit != null) {
                    // Split at this intersection!
                    val midPoint = RawPoint(
                        x = hit.x,
                        y = hit.y,
                        pressure = (p1.pressure + p2.pressure) / 2f
                    )
                    currentSegmentPoints.add(midPoint)
                    if (currentSegmentPoints.size >= 2) {
                        resultStrokes.add(
                            stroke.copy(
                                id = UUID.randomUUID().toString(),
                                points = currentSegmentPoints.toList(),
                                bounds = VectorStroke.calculateBounds(currentSegmentPoints)
                            )
                        )
                    }
                    currentSegmentPoints = mutableListOf(midPoint)
                    currentSegmentPoints.add(p2)
                    intersected = true
                    break
                }
            }
            if (!intersected) {
                currentSegmentPoints.add(p2)
            }
        }

        if (currentSegmentPoints.size >= 2) {
            resultStrokes.add(
                stroke.copy(
                    id = UUID.randomUUID().toString(),
                    points = currentSegmentPoints.toList(),
                    bounds = VectorStroke.calculateBounds(currentSegmentPoints)
                )
            )
        }

        return if (resultStrokes.isNotEmpty()) resultStrokes else listOf(stroke)
    }

    /**
     * Vector Nudge Tool: Distorts control points within radius R by drag vector (dx, dy).
     */
    fun nudgeStroke(
        stroke: VectorStroke,
        centerX: Float,
        centerY: Float,
        radius: Float,
        dx: Float,
        dy: Float
    ): VectorStroke {
        var modified = false
        val newPoints = stroke.points.map { p ->
            val dist = sqrt((p.x - centerX) * (p.x - centerX) + (p.y - centerY) * (p.y - centerY))
            if (dist < radius) {
                modified = true
                val influence = (1.0f - dist / radius) * (1.0f - dist / radius)
                p.copy(
                    x = p.x + dx * influence,
                    y = p.y + dy * influence
                )
            } else {
                p
            }
        }
        return if (modified) {
            stroke.copy(
                points = newPoints,
                bounds = VectorStroke.calculateBounds(newPoints)
            )
        } else stroke
    }

    /**
     * Mirrors vector stroke horizontally across centerX.
     */
    fun mirrorStrokeHorizontal(stroke: VectorStroke, centerX: Float): VectorStroke {
        val flippedPoints = stroke.points.map { p ->
            p.copy(x = 2 * centerX - p.x)
        }
        return stroke.copy(
            points = flippedPoints,
            bounds = VectorStroke.calculateBounds(flippedPoints)
        )
    }

    /**
     * Mirrors vector stroke vertically across centerY.
     */
    fun mirrorStrokeVertical(stroke: VectorStroke, centerY: Float): VectorStroke {
        val flippedPoints = stroke.points.map { p ->
            p.copy(y = 2 * centerY - p.y)
        }
        return stroke.copy(
            points = flippedPoints,
            bounds = VectorStroke.calculateBounds(flippedPoints)
        )
    }

    /**
     * Calculates collective bounding box for selected elements.
     */
    fun computeSelectionBounds(
        strokes: List<VectorStroke>,
        images: List<CanvasImageElement>
    ): RectF? {
        if (strokes.isEmpty() && images.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (s in strokes) {
            if (s.bounds.left < minX) minX = s.bounds.left
            if (s.bounds.top < minY) minY = s.bounds.top
            if (s.bounds.right > maxX) maxX = s.bounds.right
            if (s.bounds.bottom > maxY) maxY = s.bounds.bottom
        }

        for (img in images) {
            val b = img.bounds
            if (b.left < minX) minX = b.left
            if (b.top < minY) minY = b.top
            if (b.right > maxX) maxX = b.right
            if (b.bottom > maxY) maxY = b.bottom
        }

        if (minX > maxX || minY > maxY) return null
        return RectF(minX, minY, maxX, maxY)
    }
}
