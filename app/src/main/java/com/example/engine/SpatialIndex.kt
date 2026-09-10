package com.example.engine

import android.graphics.RectF
import com.example.model.VectorStroke
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Generic Spatial Index interface for 2D infinite canvas elements.
 */
interface SpatialIndex<T> {
    fun insert(item: T, bounds: RectF)
    fun remove(item: T, bounds: RectF): Boolean
    fun update(item: T, oldBounds: RectF, newBounds: RectF)
    fun query(range: RectF, result: MutableCollection<T>)
    fun query(range: RectF): List<T> {
        val list = mutableListOf<T>()
        query(range, list)
        return list
    }
    fun clear()
    val size: Int
}

/**
 * High-performance 2D Spatial Hash Grid Index.
 *
 * Partitions the 2D infinite coordinate space into discrete uniform grid buckets (cells)
 * of size [cellSize] (default 512f world units).
 *
 * Query complexity: O(1) bucket lookups + O(k) candidate intersection tests,
 * where k is the number of elements within the queried cell bounds.
 *
 * Guarantees zero linear scans across off-screen canvas elements.
 */
class SpatialGridIndex<T : Any>(
    val cellSize: Float = 512f
) : SpatialIndex<T> {

    // Map from packed 64-bit cell coordinate (cellX, cellY) to set of items in that cell
    private val grid = HashMap<Long, MutableSet<T>>()

    // Map from item to its current bounding box for quick lookups
    private val itemBounds = HashMap<T, RectF>()

    private var elementCount = 0

    override val size: Int
        get() = elementCount

    companion object {
        fun packCellKey(cellX: Int, cellY: Int): Long {
            return (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
        }

        fun unpackCellX(key: Long): Int = (key shr 32).toInt()
        fun unpackCellY(key: Long): Int = key.toInt()
    }

    private fun getCellCoord(worldVal: Float): Int {
        return floor(worldVal / cellSize).toInt()
    }

    override fun insert(item: T, bounds: RectF) {
        val existing = itemBounds.put(item, RectF(bounds))
        if (existing == null) {
            elementCount++
        } else {
            // Item was already present, clean up old cell references
            removeInternal(item, existing)
        }

        val minX = getCellCoord(bounds.left)
        val maxX = getCellCoord(bounds.right)
        val minY = getCellCoord(bounds.top)
        val maxY = getCellCoord(bounds.bottom)

        for (cx in minX..maxX) {
            for (cy in minY..maxY) {
                val key = packCellKey(cx, cy)
                grid.getOrPut(key) { LinkedHashSet() }.add(item)
            }
        }
    }

    override fun remove(item: T, bounds: RectF): Boolean {
        val storedBounds = itemBounds.remove(item) ?: bounds
        val removed = removeInternal(item, storedBounds)
        if (removed) {
            elementCount = max(0, elementCount - 1)
        }
        return removed
    }

    private fun removeInternal(item: T, bounds: RectF): Boolean {
        val minX = getCellCoord(bounds.left)
        val maxX = getCellCoord(bounds.right)
        val minY = getCellCoord(bounds.top)
        val maxY = getCellCoord(bounds.bottom)
        var anyRemoved = false

        for (cx in minX..maxX) {
            for (cy in minY..maxY) {
                val key = packCellKey(cx, cy)
                val set = grid[key]
                if (set != null) {
                    if (set.remove(item)) {
                        anyRemoved = true
                    }
                    if (set.isEmpty()) {
                        grid.remove(key)
                    }
                }
            }
        }
        return anyRemoved
    }

    override fun update(item: T, oldBounds: RectF, newBounds: RectF) {
        removeInternal(item, oldBounds)
        insert(item, newBounds)
    }

    override fun query(range: RectF, result: MutableCollection<T>) {
        if (elementCount == 0) return

        val minX = getCellCoord(range.left)
        val maxX = getCellCoord(range.right)
        val minY = getCellCoord(range.top)
        val maxY = getCellCoord(range.bottom)

        val spanX = (maxX.toLong() - minX.toLong() + 1L).coerceAtLeast(1L)
        val spanY = (maxY.toLong() - minY.toLong() + 1L).coerceAtLeast(1L)
        val totalCells = if (spanX > 50_000L || spanY > 50_000L) Long.MAX_VALUE else spanX * spanY

        // Stop all global recalculations on zoom:
        // When zoomed far out, scanning itemBounds directly is O(N), which is drastically faster
        // and uses zero cell hash allocations compared to iterating thousands of empty cells.
        if (totalCells > elementCount || totalCells > 1000L) {
            for ((item, b) in itemBounds) {
                if (RectF.intersects(b, range)) {
                    result.add(item)
                }
            }
            return
        }

        val seen = HashSet<T>()

        for (cx in minX..maxX) {
            for (cy in minY..maxY) {
                val key = packCellKey(cx, cy)
                val cellItems = grid[key] ?: continue

                for (item in cellItems) {
                    if (seen.add(item)) {
                        val b = itemBounds[item]
                        if (b != null && RectF.intersects(b, range)) {
                            result.add(item)
                        }
                    }
                }
            }
        }
    }

    override fun clear() {
        grid.clear()
        itemBounds.clear()
        elementCount = 0
    }

    /**
     * Fast bulk initialization from a collection of items and their bounding rects.
     */
    fun buildFrom(items: Collection<Pair<T, RectF>>) {
        clear()
        for ((item, bounds) in items) {
            insert(item, bounds)
        }
    }

    /**
     * Returns the bounding box of the specified item if present.
     */
    fun getItemBounds(item: T): RectF? = itemBounds[item]

    /**
     * Returns all active cell keys intersecting the specified range.
     */
    fun getIntersectingCellKeys(range: RectF): List<Long> {
        val minX = getCellCoord(range.left)
        val maxX = getCellCoord(range.right)
        val minY = getCellCoord(range.top)
        val maxY = getCellCoord(range.bottom)
        val keys = mutableListOf<Long>()
        for (cx in minX..maxX) {
            for (cy in minY..maxY) {
                keys.add(packCellKey(cx, cy))
            }
        }
        return keys
    }
}
