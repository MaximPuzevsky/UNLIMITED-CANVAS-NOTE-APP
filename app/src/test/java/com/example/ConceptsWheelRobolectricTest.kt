package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.BrushType
import com.example.model.ToolSlot
import com.example.ui.ConceptsWheelSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConceptsWheelRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test concepts wheel renders outer ring tool wedges and handles clicks`() {
        var selectedSlot = -1
        var undoClicked = false
        var redoClicked = false

        val sampleSlots = listOf(
            ToolSlot(0, BrushType.PEN, 0xFF000000.toInt(), 2f, 1f, 0.5f),
            ToolSlot(1, BrushType.SOFT_PENCIL, 0xFF333333.toInt(), 4f, 0.9f, 0.3f),
            ToolSlot(2, BrushType.HARD_PENCIL, 0xFF222222.toInt(), 1.5f, 0.8f, 0.2f),
            ToolSlot(3, BrushType.FOUNTAIN_PEN, 0xFF111111.toInt(), 3f, 1f, 0.6f),
            ToolSlot(4, BrushType.MARKER, 0xFFE63946.toInt(), 12f, 0.7f, 0.4f),
            ToolSlot(5, BrushType.WATERCOLOR, 0xFF457B9D.toInt(), 18f, 0.5f, 0.5f),
            ToolSlot(6, BrushType.AIRBRUSH, 0xFFA8DADC.toInt(), 24f, 0.4f, 0.3f),
            ToolSlot(7, BrushType.ERASER_HARD, 0xFFFFFFFF.toInt(), 15f, 1f, 0f)
        )

        composeTestRule.setContent {
            ConceptsWheelSystem(
                toolSlots = sampleSlots,
                activeSlotIndex = 0,
                activeColor = 0xFF000000.toInt(),
                activeBrush = BrushType.PEN,
                activeSize = 2f,
                activeOpacity = 1f,
                activeSmoothing = 0.5f,
                onSelectSlot = { selectedSlot = it },
                onChangeBrushType = {},
                onSelectColor = {},
                onSizeChange = {},
                onOpacityChange = {},
                onSmoothingChange = {},
                onUndo = { undoClicked = true },
                onRedo = { redoClicked = true },
                isColorTorusOpen = false,
                onToggleColorTorus = {},
                onCloseColorTorus = {}
            )
        }

        // Verify all 8 tool wedge buttons exist and are tappable
        for (i in 0 until 8) {
            composeTestRule.onNodeWithTag("tool_slot_$i").assertExists()
        }

        // Tap slot 4 (Marker)
        composeTestRule.onNodeWithTag("tool_slot_4").performClick()
        assertEquals(4, selectedSlot)

        // Verify Undo & Redo outer ring wedges
        composeTestRule.onNodeWithTag("wheel_undo_button").assertExists().performClick()
        assertTrue(undoClicked)

        composeTestRule.onNodeWithTag("wheel_redo_button").assertExists().performClick()
        assertTrue(redoClicked)
    }

    @Test
    fun `test middle ring 3-part division controls and labels`() {
        var sizeChanged = false
        var opacityChanged = false
        var smoothingChanged = false

        composeTestRule.setContent {
            ConceptsWheelSystem(
                toolSlots = listOf(ToolSlot(0, BrushType.PEN, 0xFF000000.toInt(), 5f, 0.8f, 0.4f)),
                activeSlotIndex = 0,
                activeColor = 0xFF000000.toInt(),
                activeBrush = BrushType.PEN,
                activeSize = 5f,
                activeOpacity = 0.8f,
                activeSmoothing = 0.4f,
                onSelectSlot = {},
                onChangeBrushType = {},
                onSelectColor = {},
                onSizeChange = { sizeChanged = true },
                onOpacityChange = { opacityChanged = true },
                onSmoothingChange = { smoothingChanged = true },
                onUndo = {},
                onRedo = {},
                isColorTorusOpen = false,
                onToggleColorTorus = {},
                onCloseColorTorus = {}
            )
        }

        // Verify 3 distinct pen control segments exist with direct tap/drag hit-boxes
        composeTestRule.onNodeWithTag("pen_control_size").assertExists().performClick()
        assertTrue(sizeChanged)

        composeTestRule.onNodeWithTag("pen_control_opacity").assertExists().performClick()
        assertTrue(opacityChanged)

        composeTestRule.onNodeWithTag("pen_control_smoothing").assertExists().performClick()
        assertTrue(smoothingChanged)
    }

    @Test
    fun `test central core color trigger and minimize collapse dock pill`() {
        var torusToggled = false

        composeTestRule.setContent {
            ConceptsWheelSystem(
                toolSlots = listOf(ToolSlot(0, BrushType.PEN, 0xFFFF0000.toInt(), 3f, 1f, 0.5f)),
                activeSlotIndex = 0,
                activeColor = 0xFFFF0000.toInt(),
                activeBrush = BrushType.PEN,
                activeSize = 3f,
                activeOpacity = 1f,
                activeSmoothing = 0.5f,
                onSelectSlot = {},
                onChangeBrushType = {},
                onSelectColor = {},
                onSizeChange = {},
                onOpacityChange = {},
                onSmoothingChange = {},
                onUndo = {},
                onRedo = {},
                isColorTorusOpen = false,
                onToggleColorTorus = { torusToggled = true },
                onCloseColorTorus = {}
            )
        }

        // Tap central core circle to toggle color torus
        composeTestRule.onNodeWithTag("center_color_circle_trigger").assertExists().performClick()
        assertTrue(torusToggled)

        // Tap collapse button to minimize wheel into dock pill
        composeTestRule.onNodeWithTag("wheel_collapse_button").assertExists().performClick()
        composeTestRule.onNodeWithTag("collapsed_wheel_dock_pill").assertExists().performClick()

        // After clicking dock pill, full wheel expands again
        composeTestRule.onNodeWithTag("center_color_circle_trigger").assertExists()
    }
}
