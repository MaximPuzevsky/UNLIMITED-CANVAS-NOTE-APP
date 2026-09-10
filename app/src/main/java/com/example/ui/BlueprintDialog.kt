package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun BlueprintDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14161D),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3344)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("blueprint_technical_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Architecture",
                            tint = Color(0xFF48CAE4),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "Concepts 1:1 Architecture & Technical Blueprint",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Samsung Galaxy Tab S-Pen & Infinite Vector Engine Specification",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Architecture Blueprint
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    BlueprintSection(
                        title = "SECTION 1: ARCHITECTURE & VECTOR ENGINE",
                        content = """
1. Hybrid Vector-on-Infinite-Canvas Rendering Engine:
   • Architecture: Powered by Google Skia (C++20 NDK) / Vulkan 1.3 backing an Android SurfaceView.
   • Multi-buffering: Background 512x512 world-tile texture cache with asynchronous GPU rendering. Inking overlay renders in <4ms directly to hardware front buffer.
   • Viewport Transform: Affine matrix mapping World (infinite double precision) to Screen pixels with real-time zoom (0.05x - 50.0x), pan, and angle snapping at 0°, 45°, 90°, 180°.
   • Spatial Indexing: 2D QuadTree (capacity 16, max depth 12) for O(log N + k) viewport frustum culling.

2. Stroke Representation Data Structures:
   • RawPoint: x, y, pressure (4096 levels), tiltX, tiltY, tiltAngle, orientation, velocity, monotonic timestamp.
   • VectorStroke: id, layerId, brushType, color, baseWidth, opacity, smoothing, points, cubic bezier segments, AABB bounding box.
                        """.trimIndent()
                    )

                    BlueprintSection(
                        title = "SECTION 2: SAMSUNG S PEN & TOUCH INTEGRATION",
                        content = """
1. Ultra-Low Latency & Prediction:
   • Digitizer sampling: 240Hz - 480Hz via Wacom EMR.
   • MotionEvent batching: Full event.historySize extraction between VSYNC frames.
   • Hardware prediction: AndroidX MotionEventPredictor (Kalman filter / polynomial extrapolation 15-30ms ahead).

2. S Pen Barrel Button & Dwell Mechanics:
   • BUTTON_STYLUS_PRIMARY / BUTTON_SECONDARY: Held down automatically switches to Vector Lasso Selection mode!
   • Selection Rule: All objects completely inside OR touched/intersected by the lasso loop are selected.
   • Post-Lasso Minimal Floating Toolbar:
     - Copy: Duplicates selection; then ONLY the copied part is movable & selected with the options until clicked outside!
     - Delete: Permanently deletes selected elements.
     - Mirror Left: Horizontally flips coordinates (x' = 2*cx - x).
     - Mirror Top: Vertically flips coordinates (y' = 2*cy - y).
   • Pen Dwell (Long-Press): Tip stationary (<12px) for >=450ms triggers haptic radial Paste / Quick Actions menu.

3. Multi-Touch Gestures & Palm Rejection:
   • Strict palm rejection: When stylus is detected or within 350ms, large touch areas (touchMajor > 35dp) are ignored.
   • Two-Finger Pan/Zoom/Rotate.
   • Two-Finger Quick Tap: Instant Undo.
   • Three-Finger Quick Tap: Instant Redo.
                        """.trimIndent()
                    )

                    BlueprintSection(
                        title = "SECTION 3: CORE TOOLSET (CONCEPTS PARITY)",
                        content = """
1. Vector Brushes:
   • Pen (variable pressure), Soft Pencil (graphite texture & tilt shading), Hard Pencil (crisp fine line), Fountain Pen (velocity-based calligraphy), Wire (constant CAD line), Marker (multiply blend mode).
   • Slice Tool: Dynamic vector knife splits crossed strokes into multiple independent stroke segments.
   • Nudge Tool: Distorts control points within radius R by drag delta with quadratic falloff.
   • Erasers: Hard vector eraser (deletes whole stroke on hit) & Mask eraser.

2. Live Line Smoothing:
   • Streamline EMA algorithm + Catmull-Rom to Cubic Bezier curve fitting on-the-fly.

3. Object Selection & Editing:
   • Full transform handles: Move, duplicate, mirror, delete, and modify color/width/brush post-drawing.

4. Color Engine:
   • COPIC spectrum color wheel with Warm Greys, Cool Greys, Earth, Vibrant tones, and HSL sliders.
                        """.trimIndent()
                    )

                    BlueprintSection(
                        title = "SECTION 4: LAYER SYSTEM, GRIDS, AND PRECISION",
                        content = """
1. Infinite Layer System:
   • Photoshop/Concepts style stack with Visibility eye, Lock padlock, Opacity slider, Reorder, and Add/Delete.
   • Media element support: Imported images/screenshots and PDF pages live in the vector coordinate space. Users can draw right on top of them, and lasso select them together or individually.

2. Precision Grids & Guides:
   • Blank Canvas, Dot Grid, Line Grid, Isometric 3D Grid, and Graph Paper with real-time snap.
                        """.trimIndent()
                    )

                    BlueprintSection(
                        title = "SECTION 5: EXPORT ENGINE",
                        content = """
1. SVG Export: Standard W3C XML with path data (M, L, C), stroke widths, colors, and grouped layer hierarchies (<g id="layer_...">).
2. High-Resolution Raster Export: Up to 4096px PNG export with transparent background option.
3. Native JSON serialization for complete infinite drawing persistence.
                        """.trimIndent()
                    )
                }
            }
        }
    }
}

@Composable
private fun BlueprintSection(title: String, content: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1B1D27),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C3040)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = Color(0xFF48CAE4),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
