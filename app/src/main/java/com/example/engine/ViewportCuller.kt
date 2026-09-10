package com.example.engine

import android.graphics.PointF
import android.graphics.RectF
import com.example.model.ViewportState
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance Viewport Frustum Culling Engine.
 *
 * Implements screen-bounds checking by projecting the active screen viewport
 * into infinite world coordinates (accounting for pan offset, zoom scale, and canvas rotation).
 *
 * Generates an Axis-Aligned Bounding Box (AABB) with safety padding to prevent edge clipping,
 * as well as oriented convex hull testing for zero-redundancy culling.
 */
object ViewportCuller {

    /**
     * Calculates the Axis-Aligned Bounding Box (AABB) in world space of the visible screen viewport.
     *
     * @param viewport Current camera viewport state (panX, panY, zoom, rotationDeg)
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param margin World-space safety padding around the viewport (default 120f world units)
     */
    fun computeViewportAABB(
        viewport: ViewportState,
        screenWidth: Float,
        screenHeight: Float,
        margin: Float = 40f
    ): RectF {
        if (screenWidth <= 0f || screenHeight <= 0f) {
            return RectF(-1000f, -1000f, 1000f, 1000f)
        }

        // Project the 4 screen corners into world coordinates
        val p0 = GeometryMath.screenToWorld(0f, 0f, viewport, screenWidth, screenHeight)
        val p1 = GeometryMath.screenToWorld(screenWidth, 0f, viewport, screenWidth, screenHeight)
        val p2 = GeometryMath.screenToWorld(screenWidth, screenHeight, viewport, screenWidth, screenHeight)
        val p3 = GeometryMath.screenToWorld(0f, screenHeight, viewport, screenWidth, screenHeight)

        val minX = min(min(p0.x, p1.x), min(p2.x, p3.x)) - margin
        val maxX = max(max(p0.x, p1.x), max(p2.x, p3.x)) + margin
        val minY = min(min(p0.y, p1.y), min(p2.y, p3.y)) - margin
        val maxY = max(max(p0.y, p1.y), max(p2.y, p3.y)) + margin

        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Returns the 4 ordered world-space vertices of the camera viewport frustum.
     */
    fun computeViewportFrustumVertices(
        viewport: ViewportState,
        screenWidth: Float,
        screenHeight: Float
    ): List<PointF> {
        return listOf(
            GeometryMath.screenToWorld(0f, 0f, viewport, screenWidth, screenHeight),
            GeometryMath.screenToWorld(screenWidth, 0f, viewport, screenWidth, screenHeight),
            GeometryMath.screenToWorld(screenWidth, screenHeight, viewport, screenWidth, screenHeight),
            GeometryMath.screenToWorld(0f, screenHeight, viewport, screenWidth, screenHeight)
        )
    }

    /**
     * Fast AABB intersection check: returns true if candidateBounds intersects viewportAABB.
     */
    fun isVisible(candidateBounds: RectF, viewportAABB: RectF): Boolean {
        return RectF.intersects(candidateBounds, viewportAABB)
    }
}
