package com.example

import android.graphics.Canvas
import android.graphics.RectF
import com.example.engine.LODLevel
import com.example.engine.LODPathCache
import com.example.engine.SpatialGridIndex
import com.example.engine.TileCoord
import com.example.engine.TileRenderEngine
import com.example.engine.ViewportCuller
import com.example.model.BrushType
import com.example.model.CanvasLayer
import com.example.model.RawPoint
import com.example.model.VectorStroke
import com.example.model.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RenderingEnginePerformanceTest {

    // 1. Viewport Culling Tests
    @Test
    fun `test viewport frustum AABB calculation and culling`() {
        val viewport = ViewportState(panX = 0f, panY = 0f, zoom = 1.0f, rotationDeg = 0f)
        val screenW = 1080f
        val screenH = 2400f
        val margin = 100f

        val aabb = ViewportCuller.computeViewportAABB(viewport, screenW, screenH, margin)

        // The world bounding box should be centered around screen center
        // Center is (540, 1200), so world space spans from -540 to +540 and -1200 to +1200 plus margin
        assertEquals(-540f - margin, aabb.left, 1.0f)
        assertEquals(540f + margin, aabb.right, 1.0f)
        assertEquals(-1200f - margin, aabb.top, 1.0f)
        assertEquals(1200f + margin, aabb.bottom, 1.0f)

        // Inside viewport element
        val insideBounds = RectF(0f, 0f, 50f, 50f)
        assertTrue(ViewportCuller.isVisible(insideBounds, aabb))

        // Far off-screen element (e.g. at x = 5000)
        val offscreenBounds = RectF(5000f, 5000f, 5100f, 5100f)
        assertFalse(ViewportCuller.isVisible(offscreenBounds, aabb))
    }

    // 2. Spatial Indexing Tests
    @Test
    fun `test spatial grid index scales efficiently with 1000 elements`() {
        val grid = SpatialGridIndex<VectorStroke>(cellSize = 512f)

        // Insert 1,000 scattered strokes across a 10,000 x 10,000 infinite world
        for (i in 0 until 1000) {
            val wx = (i % 50) * 200f // 0 .. 10,000
            val wy = (i / 50) * 200f // 0 .. 4,000
            val stroke = VectorStroke(
                id = "stroke_$i",
                layerId = "layer1",
                brushType = BrushType.PEN,
                color = 0,
                baseWidth = 4f,
                opacity = 1f,
                smoothing = 0f,
                points = listOf(RawPoint(wx, wy), RawPoint(wx + 40f, wy + 40f))
            )
            grid.insert(stroke, stroke.bounds)
        }

        assertEquals(1000, grid.size)

        // Query a small viewport of 500x500 at origin (0, 0)
        val queryBox = RectF(0f, 0f, 500f, 500f)
        val visible = mutableListOf<VectorStroke>()
        grid.query(queryBox, visible)

        // Should return only the small subset intersecting the box, NOT all 1,000!
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.size < 50) // drastically culled
        for (s in visible) {
            assertTrue(RectF.intersects(s.bounds, queryBox))
        }
    }

    // 3. Level of Detail (LOD) & Zero-Allocation Path Cache Tests
    @Test
    fun `test LOD tier resolution and zero allocation path caching`() {
        assertEquals(LODLevel.FULL, LODPathCache.resolveLOD(1.5f))
        assertEquals(LODLevel.FULL, LODPathCache.resolveLOD(0.8f))
        assertEquals(LODLevel.SIMPLIFIED, LODPathCache.resolveLOD(0.5f))
        assertEquals(LODLevel.COARSE, LODPathCache.resolveLOD(0.15f))

        val points = mutableListOf<RawPoint>()
        for (i in 0..100) {
            points.add(RawPoint(i * 10f, (i % 5) * 5f))
        }
        val stroke = VectorStroke(
            id = "test_lod_stroke",
            layerId = "layer1",
            brushType = BrushType.PEN,
            color = 0xFF0000,
            baseWidth = 5f,
            opacity = 1f,
            smoothing = 0f,
            points = points
        )

        // Request path at FULL LOD
        val path1 = LODPathCache.getOrCreatePath(stroke, LODLevel.FULL)
        assertNotNull(path1)

        // Request again - must return identical cached instance (zero allocation!)
        val path2 = LODPathCache.getOrCreatePath(stroke, LODLevel.FULL)
        assertSame(path1, path2)

        // Request at COARSE LOD
        val coarsePath = LODPathCache.getOrCreatePath(stroke, LODLevel.COARSE)
        assertNotNull(coarsePath)
    }

    // 4. Chunking & Tile-Based Loading Tests
    @Test
    fun `test tile render engine chunk bounds and selective invalidation`() {
        val engine = TileRenderEngine(chunkSize = 512f, tilePixelSize = 256)

        val tileCoord = engine.worldToTileCoord(600f, 1100f)
        assertEquals(1, tileCoord.x) // floor(600 / 512) = 1
        assertEquals(2, tileCoord.y) // floor(1100 / 512) = 2

        val bounds = tileCoord.toWorldBounds(512f)
        assertEquals(512f, bounds.left)
        assertEquals(1024f, bounds.top)
        assertEquals(1024f, bounds.right)
        assertEquals(1536f, bounds.bottom)

        // Query tiles intersecting a small 100x100 box spanning tiles (0,0) and (1,0)
        val spanBounds = RectF(500f, 100f, 520f, 200f)
        val tiles = engine.getIntersectingTiles(spanBounds)
        assertEquals(2, tiles.size)
        assertTrue(tiles.contains(TileCoord(0, 0)))
        assertTrue(tiles.contains(TileCoord(1, 0)))
    }

    // 5. Layer Flattening & Bitmap Caching Test
    @Test
    fun `test tile render engine flattens layers into cached bitmaps`() {
        val engine = TileRenderEngine(chunkSize = 512f, tilePixelSize = 256)

        val layer1 = CanvasLayer(id = "layer1", name = "Background Layer", orderIndex = 0, isVisible = true)
        val layer2 = CanvasLayer(id = "layer2", name = "Ink Layer", orderIndex = 1, isVisible = true)

        val strokes = listOf(
            VectorStroke(
                id = "s1",
                layerId = "layer1",
                brushType = BrushType.PEN,
                color = 0xFF00FF,
                baseWidth = 3f,
                opacity = 1f,
                smoothing = 0f,
                points = listOf(RawPoint(50f, 50f), RawPoint(100f, 100f))
            ),
            VectorStroke(
                id = "s2",
                layerId = "layer2",
                brushType = BrushType.MARKER,
                color = 0x00FFFF,
                baseWidth = 10f,
                opacity = 0.8f,
                smoothing = 0f,
                points = listOf(RawPoint(80f, 80f), RawPoint(150f, 150f))
            )
        )

        val dummyBmp = android.graphics.Bitmap.createBitmap(500, 500, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dummyBmp)

        val viewportBounds = RectF(0f, 0f, 512f, 512f)
        val renderedChunks = engine.renderVisibleChunks(
            canvas = canvas,
            viewportBounds = viewportBounds,
            strokes = strokes,
            images = emptyList(),
            textBlocks = emptyList(),
            layers = listOf(layer1, layer2),
            lod = LODLevel.FULL
        )

        // 1 chunk (Tile 0,0) is rendered and cached
        assertEquals(1, renderedChunks)

        // Second render without invalidation uses cached bitmap
        val reRendered = engine.renderVisibleChunks(
            canvas = canvas,
            viewportBounds = viewportBounds,
            strokes = strokes,
            images = emptyList(),
            textBlocks = emptyList(),
            layers = listOf(layer1, layer2),
            lod = LODLevel.FULL
        )
        assertEquals(1, reRendered)
    }
}
