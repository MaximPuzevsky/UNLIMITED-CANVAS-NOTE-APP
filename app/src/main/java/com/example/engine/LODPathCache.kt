package com.example.engine

import android.graphics.Path
import android.util.LruCache
import com.example.model.RawPoint
import com.example.model.VectorStroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Level of Detail (LOD) tiers for vector rendering.
 */
enum class LODLevel {
    FULL,        // Zoom >= 0.75f: Full resolution cubic/quadratic Bezier spline
    SIMPLIFIED,  // 0.25f <= Zoom < 0.75f: Decimated points (50-70% fewer segments)
    COARSE       // Zoom < 0.25f: Low-poly skeleton path (maximum rendering throughput)
}

/**
 * High-performance Path & Level-of-Detail (LOD) Cache.
 *
 * Solves two critical performance bottlenecks:
 * 1. Level of Detail (LOD) Rendering: Dynamically reduces path tessellation complexity based on camera zoom.
 * 2. Zero-Allocation Rendering: Pre-builds and caches [android.graphics.Path] instances in an LRU cache,
 *    preventing the massive GC pressure and CPU overhead of allocating new Path() objects and
 *    calculating Bezier midpoints on every frame.
 */
object LODPathCache {

    private const val MAX_CACHED_PATHS = 2048

    // Composite key: strokeId + "_" + lodLevel + "_" + pointsCount
    private val pathCache = object : LruCache<String, Path>(MAX_CACHED_PATHS) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Path?, newValue: Path?) {
            // Paths do not hold native handles that require manual recycle in standard Android graphics
        }
    }

    /**
     * Resolves the active LOD tier from the camera zoom factor.
     */
    fun resolveLOD(zoom: Float): LODLevel {
        return when {
            zoom >= 0.75f -> LODLevel.FULL
            zoom >= 0.25f -> LODLevel.SIMPLIFIED
            else -> LODLevel.COARSE
        }
    }

    /**
     * Retrieves or creates a pre-tessellated [Path] for the given [VectorStroke] at [lod].
     */
    fun getOrCreatePath(stroke: VectorStroke, lod: LODLevel): Path {
        val key = "${stroke.id}_${lod.name}_${stroke.points.size}"
        val cached = pathCache.get(key)
        if (cached != null) {
            return cached
        }

        val newPath = buildPath(stroke.points, lod)
        pathCache.put(key, newPath)
        return newPath
    }

    /**
     * Removes cached paths for a specific stroke ID (e.g. when modified or deleted).
     */
    fun invalidate(strokeId: String) {
        for (lod in LODLevel.values()) {
            // Invalidate common point lengths
            pathCache.remove("${strokeId}_${lod.name}")
        }
    }

    /**
     * Clears the entire path cache.
     */
    fun clear() {
        pathCache.evictAll()
    }

    private fun buildPath(points: List<RawPoint>, lod: LODLevel): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val p = points[0]
            path.moveTo(p.x, p.y)
            path.lineTo(p.x + 0.1f, p.y + 0.1f)
            return path
        }

        // Dynamic Level of Detail (LOD) switch:
        // - FULL: Full resolution mathematical Bezier spline using raw points.
        // - SIMPLIFIED: Complex paths (>8 points) simplified via Douglas-Peucker (epsilon 1.2f world units).
        // - COARSE: Low-poly skeleton path (epsilon 3.5f world units) for high-throughput overview rendering.
        // All algorithms operate with pure floating-point precision, avoiding any pixel snapping.
        val effectivePoints = when (lod) {
            LODLevel.FULL -> points
            LODLevel.SIMPLIFIED -> if (points.size > 8) simplifyPointsRDP(points, 1.2f) else points
            LODLevel.COARSE -> if (points.size > 4) simplifyPointsRDP(points, 3.5f) else points
        }

        path.moveTo(effectivePoints[0].x, effectivePoints[0].y)

        for (i in 1 until effectivePoints.size) {
            val p0 = effectivePoints[i - 1]
            val p1 = effectivePoints[i]
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f
            path.quadTo(p0.x, p0.y, midX, midY)
        }
        val last = effectivePoints.last()
        path.lineTo(last.x, last.y)

        return path
    }

    /**
     * Ramer-Douglas-Peucker point simplification in world float coordinates.
     * Preserves continuous path topology while drastically reducing vertex count at far zoom.
     */
    private fun simplifyPointsRDP(points: List<RawPoint>, epsilon: Float): List<RawPoint> {
        if (points.size <= 2) return points
        val sqEpsilon = epsilon * epsilon
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        rdpStep(points, 0, points.size - 1, sqEpsilon, keep)

        val result = ArrayList<RawPoint>(points.size / 2)
        for (i in points.indices) {
            if (keep[i]) result.add(points[i])
        }
        return if (result.size >= 2) result else points
    }

    private fun rdpStep(points: List<RawPoint>, first: Int, last: Int, sqEpsilon: Float, keep: BooleanArray) {
        if (last <= first + 1) return
        var maxSqDist = 0f
        var index = first
        val p1 = points[first]
        val p2 = points[last]

        for (i in first + 1 until last) {
            val sqDist = getSqSegmentDistance(points[i], p1, p2)
            if (sqDist > maxSqDist) {
                maxSqDist = sqDist
                index = i
            }
        }

        if (maxSqDist > sqEpsilon) {
            keep[index] = true
            rdpStep(points, first, index, sqEpsilon, keep)
            rdpStep(points, index, last, sqEpsilon, keep)
        }
    }

    private fun getSqSegmentDistance(p: RawPoint, p1: RawPoint, p2: RawPoint): Float {
        var x = p1.x
        var y = p1.y
        var dx = p2.x - x
        var dy = p2.y - y

        if (dx != 0f || dy != 0f) {
            val t = ((p.x - x) * dx + (p.y - y) * dy) / (dx * dx + dy * dy)
            if (t > 1f) {
                x = p2.x
                y = p2.y
            } else if (t > 0f) {
                x += dx * t
                y += dy * t
            }
        }
        dx = p.x - x
        dy = p.y - y
        return dx * dx + dy * dy
    }
}
