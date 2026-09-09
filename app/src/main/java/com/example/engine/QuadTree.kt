package com.example.engine

import android.graphics.RectF
import com.example.model.VectorStroke

/**
 * 2D Spatial Partitioning QuadTree for infinite canvas viewport culling and hit-testing.
 */
class QuadTree(
    val bounds: RectF,
    val capacity: Int = 16,
    val maxDepth: Int = 10,
    val depth: Int = 0
) {
    private val strokes = mutableListOf<VectorStroke>()
    private var divided = false
    private var northWest: QuadTree? = null
    private var northEast: QuadTree? = null
    private var southWest: QuadTree? = null
    private var southEast: QuadTree? = null

    private fun subdivide() {
        val x = bounds.left
        val y = bounds.top
        val hw = bounds.width() / 2f
        val hh = bounds.height() / 2f

        northWest = QuadTree(RectF(x, y, x + hw, y + hh), capacity, maxDepth, depth + 1)
        northEast = QuadTree(RectF(x + hw, y, bounds.right, y + hh), capacity, maxDepth, depth + 1)
        southWest = QuadTree(RectF(x, y + hh, x + hw, bounds.bottom), capacity, maxDepth, depth + 1)
        southEast = QuadTree(RectF(x + hw, y + hh, bounds.right, bounds.bottom), capacity, maxDepth, depth + 1)
        divided = true
    }

    fun insert(stroke: VectorStroke): Boolean {
        if (!RectF.intersects(bounds, stroke.bounds)) {
            return false
        }

        if (strokes.size < capacity || depth >= maxDepth) {
            strokes.add(stroke)
            return true
        }

        if (!divided) {
            subdivide()
        }

        var inserted = false
        if (northWest?.insert(stroke) == true) inserted = true
        if (northEast?.insert(stroke) == true) inserted = true
        if (southWest?.insert(stroke) == true) inserted = true
        if (southEast?.insert(stroke) == true) inserted = true

        return inserted
    }

    fun query(range: RectF, found: MutableSet<VectorStroke>) {
        if (!RectF.intersects(bounds, range)) {
            return
        }

        for (s in strokes) {
            if (RectF.intersects(s.bounds, range)) {
                found.add(s)
            }
        }

        if (divided) {
            northWest?.query(range, found)
            northEast?.query(range, found)
            southWest?.query(range, found)
            southEast?.query(range, found)
        }
    }

    fun clear() {
        strokes.clear()
        northWest?.clear()
        northEast?.clear()
        southWest?.clear()
        southEast?.clear()
        northWest = null
        northEast = null
        southWest = null
        southEast = null
        divided = false
    }

    companion object {
        fun build(strokes: List<VectorStroke>): QuadTree {
            if (strokes.isEmpty()) {
                return QuadTree(RectF(-20000f, -20000f, 20000f, 20000f))
            }
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (s in strokes) {
                val b = s.bounds
                if (b.left < minX) minX = b.left
                if (b.top < minY) minY = b.top
                if (b.right > maxX) maxX = b.right
                if (b.bottom > maxY) maxY = b.bottom
            }
            val pad = 500f
            val w = (maxX - minX).coerceAtLeast(2000f)
            val h = (maxY - minY).coerceAtLeast(2000f)
            val size = Math.max(w, h) + pad * 2
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val rootBounds = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            val tree = QuadTree(rootBounds)
            for (s in strokes) {
                tree.insert(s)
            }
            return tree
        }
    }
}
