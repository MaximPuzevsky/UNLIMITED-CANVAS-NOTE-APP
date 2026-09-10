package com.example

import com.example.engine.GeometryMath
import com.example.model.BrushType
import com.example.model.RawPoint
import com.example.model.VectorStroke
import com.example.viewmodel.CanvasViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VectorEngineRobolectricTest {

    @Test
    fun `test streamline smoothing reduces noise`() {
        val raw = listOf(
            RawPoint(0f, 0f),
            RawPoint(10f, 12f),
            RawPoint(20f, 18f),
            RawPoint(30f, 32f),
            RawPoint(40f, 40f)
        )
        val smoothed = GeometryMath.smoothPoints(raw, 0.5f)
        assertEquals(raw.size, smoothed.size)
        assertEquals(0f, smoothed.first().x, 0.01f)
        assertEquals(0f, smoothed.first().y, 0.01f)
    }

    @Test
    fun `test lasso polygon contains point`() {
        val square = listOf(
            RawPoint(0f, 0f),
            RawPoint(100f, 0f),
            RawPoint(100f, 100f),
            RawPoint(0f, 100f)
        )

        assertTrue(GeometryMath.isPointInPolygon(50f, 50f, square))
        assertFalse(GeometryMath.isPointInPolygon(150f, 50f, square))
    }

    @Test
    fun `test stroke slice splits stroke on line intersection`() {
        val stroke = VectorStroke(
            id = UUID.randomUUID().toString(),
            layerId = "layer1",
            brushType = BrushType.PEN,
            color = 0,
            baseWidth = 2f,
            opacity = 1f,
            smoothing = 0f,
            points = listOf(
                RawPoint(0f, 50f),
                RawPoint(100f, 50f)
            )
        )

        // Slicing line cutting perpendicularly across (50, 0) to (50, 100)
        val cutter = listOf(
            RawPoint(50f, 0f),
            RawPoint(50f, 100f)
        )

        val sliced = GeometryMath.sliceStroke(stroke, cutter)
        assertEquals(2, sliced.size)
    }

    @Test
    fun `test selection mirror horizontal flips coordinates`() {
        val stroke = VectorStroke(
            id = "stroke1",
            layerId = "layer1",
            brushType = BrushType.PEN,
            color = 0,
            baseWidth = 2f,
            opacity = 1f,
            smoothing = 0f,
            points = listOf(RawPoint(10f, 20f), RawPoint(30f, 40f))
        )

        val mirrored = GeometryMath.mirrorStrokeHorizontal(stroke, 50f)
        // x' = 2*50 - 10 = 90
        assertEquals(90f, mirrored.points[0].x, 0.01f)
        // x' = 2*50 - 30 = 70
        assertEquals(70f, mirrored.points[1].x, 0.01f)
    }

    @Test
    fun `test canvas viewmodel selection copy and delete operations`() {
        val vm = CanvasViewModel(Dispatchers.Unconfined)

        // Draw a test stroke
        vm.startInking(RawPoint(10f, 10f), isButtonPressed = false, 1000f, 1000f)
        vm.appendInkingPoints(listOf(RawPoint(20f, 20f), RawPoint(30f, 30f)), false, 1000f, 1000f)
        vm.finishInking(RawPoint(30f, 30f), isButtonPressed = false, 1000f, 1000f)

        assertEquals(1, vm.strokes.value.size)

        // Select with lasso using barrel button = true
        vm.startInking(RawPoint(0f, 0f), isButtonPressed = true, 1000f, 1000f)
        vm.appendInkingPoints(
            listOf(
                RawPoint(100f, 0f),
                RawPoint(100f, 100f),
                RawPoint(0f, 100f),
                RawPoint(0f, 0f)
            ),
            isButtonPressed = true,
            1000f,
            1000f
        )
        vm.finishInking(RawPoint(0f, 0f), isButtonPressed = true, 1000f, 1000f)

        assertEquals(1, vm.selection.value.selectedStrokeIds.size)

        // Test Copy: clones selection and keeps ONLY copied part selected
        vm.copySelection()
        assertEquals(2, vm.strokes.value.size)
        assertTrue(vm.selection.value.isCopyPending)
        assertEquals(1, vm.selection.value.selectedStrokeIds.size)

        // Test Delete: removes the copied object
        vm.deleteSelection()
        assertEquals(1, vm.strokes.value.size)
        assertTrue(vm.selection.value.isEmpty)
    }

    @Test
    fun `test true vector data structure computes bezier curve segments`() {
        val points = listOf(
            RawPoint(0f, 0f, 0.5f),
            RawPoint(50f, 25f, 0.8f),
            RawPoint(100f, 100f, 1.0f)
        )
        val stroke = VectorStroke(
            id = "vector_test",
            layerId = "layer1",
            brushType = BrushType.PEN,
            color = 0xFF0000,
            baseWidth = 4f,
            points = points
        )

        assertTrue("True vector stroke must compute Bezier curve segments", stroke.bezierSegments.isNotEmpty())
        val firstSeg = stroke.bezierSegments.first()
        assertEquals(0f, firstSeg.startX, 0.01f)
        assertEquals(0f, firstSeg.startY, 0.01f)
        assertEquals(0f, firstSeg.cp1X, 0.01f)
        assertEquals(0f, firstSeg.cp1Y, 0.01f)
        assertEquals(25f, firstSeg.endX, 0.01f)
        assertEquals(12.5f, firstSeg.endY, 0.01f)
        assertTrue(firstSeg.startWidth > 0f)
        assertTrue(firstSeg.endWidth > 0f)
    }

    @Test
    fun `test smart clipboard ignores invisible micro-dots and preserves center of mass`() {
        val normalStroke = VectorStroke(
            id = "normal_stroke",
            layerId = "layer1",
            brushType = BrushType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(RawPoint(100f, 100f), RawPoint(200f, 200f))
        )
        val microDot = VectorStroke(
            id = "micro_dot",
            layerId = "layer1",
            brushType = BrushType.PEN,
            color = 0,
            baseWidth = 1f,
            points = listOf(RawPoint(0.1f, 0.1f))
        )

        assertTrue(GeometryMath.isMicroDot(microDot))
        assertFalse(GeometryMath.isMicroDot(normalStroke))

        // Bounding box without micro-dots should center strictly around normal stroke
        val boundsFiltered = GeometryMath.computeSelectionBounds(
            listOf(normalStroke, microDot),
            emptyList(),
            ignoreMicroDots = true
        )
        assertNotNull(boundsFiltered)
        assertEquals(150f, boundsFiltered!!.centerX(), 2.0f)
        assertEquals(150f, boundsFiltered.centerY(), 2.0f)

        // Test centered pasteAt
        val vm = CanvasViewModel(Dispatchers.Unconfined)
        vm.pasteAt(500f, 500f) // empty clipboard does nothing safely
    }

    @Test
    fun `test layer locking prevents edits and deletions on locked layers`() {
        val vm = CanvasViewModel(Dispatchers.Unconfined)
        val defaultLayerId = vm.layers.value.first().id

        // Create stroke on default layer
        vm.startInking(RawPoint(50f, 50f), false, 1000f, 1000f)
        vm.appendInkingPoints(listOf(RawPoint(60f, 60f)), false, 1000f, 1000f)
        vm.finishInking(RawPoint(60f, 60f), false, 1000f, 1000f)
        assertEquals(1, vm.strokes.value.size)

        // Lock the layer
        vm.toggleLayerLock(defaultLayerId)
        assertTrue(vm.layers.value.first { it.id == defaultLayerId }.isLocked)

        // Attempt to inking on locked layer -> must be rejected
        vm.startInking(RawPoint(10f, 10f), false, 1000f, 1000f)
        vm.finishInking(RawPoint(20f, 20f), false, 1000f, 1000f)
        assertEquals("Locked layer must reject new strokes", 1, vm.strokes.value.size)

        // Attempt lasso selection on locked layer -> must be excluded
        vm.startInking(RawPoint(0f, 0f), true, 1000f, 1000f)
        vm.appendInkingPoints(
            listOf(RawPoint(100f, 0f), RawPoint(100f, 100f), RawPoint(0f, 100f), RawPoint(0f, 0f)),
            true, 1000f, 1000f
        )
        vm.finishInking(RawPoint(0f, 0f), true, 1000f, 1000f)
        assertTrue("Locked layer elements cannot be selected", vm.selection.value.isEmpty)
    }
}
