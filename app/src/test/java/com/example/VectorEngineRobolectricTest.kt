package com.example

import com.example.engine.GeometryMath
import com.example.model.BrushType
import com.example.model.RawPoint
import com.example.model.VectorStroke
import com.example.viewmodel.CanvasViewModel
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
        val vm = CanvasViewModel()

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
}
