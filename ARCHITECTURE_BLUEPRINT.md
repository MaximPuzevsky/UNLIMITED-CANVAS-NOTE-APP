# CONCEPTS 1:1 ARCHITECTURAL BLUEPRINT & TECHNICAL IMPLEMENTATION SPECIFICATION
## Native Android System Architecture Tailored for Samsung Galaxy Tablets with S Pen Support
**Author:** Principal Software Architect & Lead Android Graphics Engineer  
**Target Hardware:** Samsung Galaxy Tab S8 / S9 / S10 Ultra Series (Wacom EMR Digitizer, 120Hz/144Hz AMOLED)  
**OS Target:** Android 14+ (API 34/35/36), One UI 6.0+

---

## EXECUTIVE SUMMARY & SYSTEM TOPOLOGY

This specification provides the production-ready technical architecture for building a native, high-performance, 1:1 functional equivalent of the **Concepts** infinite vector sketching application for Android.

The system is designed around four foundational pillars:
1. **Hybrid Vector Engine:** Infinite floating-point world coordinate space powered by Skia (C++ NDK) / Vulkan with hierarchical QuadTree/R-Tree spatial indexing and level-of-detail (LOD) curve decimation.
2. **Sub-5ms S Pen Latency Pipeline:** Direct `SurfaceView` / HardwareComposer integration utilizing Android 14 `MotionEventPredictor` alongside Samsung S Pen SDK extensions for zero-lag predictive inking, tilt angle mapping, and hardware palm rejection.
3. **Parametric Vector Stroke Model:** Every mark remains editable post-creation—allowing live slicing, geometric nudging, Catmull-Rom bezier refitting, stroke width/color swapping, and COPIC palette recoloring.
4. **First-Class Document & Image Substrate:** Imported images, screenshots, and PDF pages sit in the vector plane as transformable elements that support direct ink overdrawing, joint vector-lasso grouping, mirroring, and duplication.

```
+---------------------------------------------------------------------------------------+
|                                    UI LAYER (Compose)                                 |
|  +---------------------+  +-------------------------+  +---------------------------+  |
|  | Concepts Tool Wheel |  | Photoshop Layer Manager |  | Floating Selection Toolbar|  |
|  +---------------------+  +-------------------------+  +---------------------------+  |
+---------------------------------------------------------------------------------------+
                                          |
+---------------------------------------------------------------------------------------+
|                             S PEN & GESTURE DISPATCH                                  |
|  - S Pen Button Interceptor (BUTTON_STYLUS_PRIMARY -> Auto Lasso Selection)           |
|  - Stylus Dwell Timer (Stationary Pen -> Radial Paste/Action Menu)                     |
|  - Multi-Touch Arbiter (2-finger Pan/Zoom/Rotate, 2-tap Undo, 3-tap Redo)             |
|  - Palm Rejection Filter (Tool Type + Touch Major/Minor Ellipse)                      |
+---------------------------------------------------------------------------------------+
                                          |
+---------------------------------------------------------------------------------------+
|                               VECTOR ENGINE & STATE                                   |
|  - Live Streamline Smoother (Catmull-Rom -> Cubic Bezier)                             |
|  - QuadTree Spatial Index (Culling, Ray-Casting & Segment-Segment Intersection)        |
|  - Vector Slicer & Nudge Deformer                                                     |
|  - Infinite Layer Stack (Opacity, Blend Modes, Lock, Visibility)                      |
+---------------------------------------------------------------------------------------+
                                          |
+---------------------------------------------------------------------------------------+
|                          HARDWARE RENDERING & LOW LATENCY                             |
|  - Skia C++ NDK / Vulkan 1.3 SurfaceView Pipeline                                     |
|  - MotionEventPredictor (AndroidX Input / Samsung Low Latency Display Engine)         |
|  - Multi-buffering: Background Vector Tile Cache + Ephemeral Predicted Inking Buffer   |
+---------------------------------------------------------------------------------------+
```

---

## SECTION 1: ARCHITECTURE & VECTOR ENGINE

### 1.1 Rendering Engine Strategy: Hybrid Vector on Infinite Canvas

To achieve smooth 120 FPS rendering on Galaxy Tab Ultra displays (e.g., 2960x1848 resolution) with millions of vector control points:

- **Primary Pipeline:** Built using Google **Skia** (`libskia.so`) embedded via C++20 NDK, drawing directly to a hardware `ANativeWindow` or Vulkan 1.3 `VkSurfaceKHR` backing an Android `SurfaceView`.
- **Dual-Surface Multi-Buffering Strategy:**
  1. **Committed Vector Buffer (Base Plane):** Displays all completed vector strokes, imported images, and grid layers. This buffer is partitioned into viewport-aligned vector tiles ($512 \times 512$ world units) rendered asynchronously to an Skia Picture / Vulkan texture cache. When panning or zooming, cached tiles are transformed in GPU memory, avoiding immediate CPU vector re-rasterization.
  2. **Active Inking Buffer (Ephemeral Low-Latency Overlay):** Uses a dedicated front-buffered or triple-buffered overlay surface. When the S Pen touches down, new input points (including hardware-predicted points) render directly into this overlay in $< 4\,\text{ms}$, bypassing the compositor pass.
  3. **Stroke Finalization:** Upon `ACTION_UP`, the raw smoothed vector path is merged into the active layer's QuadTree, the active inking buffer is cleared, and the tile cache for the stroke's bounding box is dirtied and invalidated.

### 1.2 Stroke Representation: Core Vector Data Structures

Every stroke is stored as an immutable parametric spline with variable-width vertices.

```kotlin
/**
 * Raw point sampled from hardware digitizer.
 */
data class RawPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,     // Normalized 0.0f .. 1.0f (4096 levels on S Pen)
    val tiltX: Float,        // Tilt on X axis in radians (-PI/2 .. PI/2)
    val tiltY: Float,        // Tilt on Y axis in radians (-PI/2 .. PI/2)
    val tiltAngle: Float,    // Elevation angle relative to screen normal
    val orientation: Float,  // Azimuth orientation in radians (0 .. 2*PI)
    val velocity: Float,     // Pixels per millisecond
    val timestampNs: Long    // High-resolution monotonic timestamp
)

/**
 * Geometric cubic Bezier segment for vector evaluation.
 */
data class CubicBezierSegment(
    val p0: PointF,
    val p1: PointF, // First control point
    val p2: PointF, // Second control point
    val p3: PointF,
    val startWidth: Float,
    val endWidth: Float
)

/**
 * Complete vector stroke entity.
 */
data class VectorStroke(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val brushType: BrushType,
    val color: Long,              // 32-bit ARGB or 64-bit wide-gamut Display P3
    val baseWidth: Float,         // World coordinate stroke width
    val opacity: Float = 1.0f,    // 0.0f .. 1.0f
    val smoothing: Float = 0.5f,  // 0.0f (raw) .. 1.0f (geometric streamline)
    val points: List<RawPoint>,
    val segments: List<CubicBezierSegment> = emptyList(),
    val bounds: RectF,            // Axis-aligned bounding box (AABB) in world space
    val isDeleted: Boolean = false,
    val isMasked: Boolean = false
)
```

#### Brush Parameter Dynamic Evaluation
- **Pen:** Width $w = w_0 \times (0.2 + 1.6 \cdot \text{pressure})$.
- **Soft Pencil / Hard Pencil:** Width responds to tilt angle $\theta$: $w = w_0 \times (1.0 + 2.5 \cdot \sin(\theta))$; opacity modulated by pressure and procedural graphite noise texture.
- **Fountain Pen:** Width varies with velocity $v$ and orientation $\phi$: $w = w_0 \times (1.2 - 0.4 \cdot \tanh(v / 500)) \cdot (0.6 + 0.8 \cdot |\cos(\phi - \text{angle})|)$.
- **Wire:** Fixed width $w = w_0$, unaffected by pressure or velocity.
- **Marker:** Semi-transparent with `SkBlendMode::kMultiply` and rectangular chisel-tip orientation.

### 1.3 Canvas Operations: Infinite Viewport & QuadTree Indexing

- **Viewport Transform Matrix:**
  $$\begin{bmatrix} x_{\text{screen}} \\ y_{\text{screen}} \\ 1 \end{bmatrix} = \begin{bmatrix} s \cos\theta & -s \sin\theta & t_x \\ s \sin\theta & s \cos\theta & t_y \\ 0 & 0 & 1 \end{bmatrix} \begin{bmatrix} x_{\text{world}} \\ y_{\text{world}} \\ 1 \end{bmatrix}$$
  where $s \in [0.02, 50.0]$ is zoom, $\theta$ is canvas rotation, and $(t_x, t_y)$ is pan offset.
- **Angle Snapping:** If rotation $\theta$ approaches $0^\circ, 45^\circ, 90^\circ, 180^\circ$ within a threshold of $\pm 2.5^\circ$, the canvas snaps with subtle haptic feedback.
- **Spatial Partitioning:** An adaptive **QuadTree** index with max depth 12 and node capacity 16. Viewport culling performs an AABB intersection test against the screen's world-space frustum, rendering only visible strokes ($O(\log N + k)$ instead of $O(N)$).

---

## SECTION 2: SAMSUNG S PEN & TOUCH INTEGRATION

### 2.1 S Pen Low-Latency Pipeline & Hardware Prediction

- **Hardware Sampling:** Samsung Galaxy Tab S-Pen digitizers sample at $240\,\text{Hz} - 480\,\text{Hz}$ via Wacom EMR.
- **`MotionEvent` Extraction:**
  ```kotlin
  val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                 event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
  val pressure = event.getPressure(0)
  val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, 0)
  val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, 0)
  ```
- **Batch Point Processing:** Always iterate through `event.historySize` to capture high-rate digitizer events between VSYNC boundaries:
  ```kotlin
  for (h in 0 until event.historySize) {
      val hx = event.getHistoricalX(0, h)
      val hy = event.getHistoricalY(0, h)
      val hp = event.getHistoricalPressure(0, h)
      val ht = event.getHistoricalEventTime(h)
      processSample(hx, hy, hp, ht)
  }
  ```
- **Hardware-Accelerated Prediction:** Integrate `androidx.input.motionprediction.MotionEventPredictor`. The predictor uses a Kalman filter with polynomial extrapolation to predict $15 - 30\,\text{ms}$ ahead, rendering ephemeral prediction curves directly to the display overlay to compensate for panel display latency.

### 2.2 S Pen Side Button & Dwell Behaviors (Concepts 1:1 Parity)

1. **S Pen Button Pressed (Down):**
   - In Android, the S Pen barrel button sets the button state flag:
     ```kotlin
     val isButtonPressed = (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                           (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
     ```
   - **Default Behavior:** Instantly activates **Vector Lasso Selection** while held.
   - **Intersection Rule:** The lasso selects every vector stroke and imported element that is **either completely enclosed inside OR intersected/touched** by the lasso boundary line.
2. **Post-Lasso Minimal Floating Toolbar:**
   Once the lasso loop completes, an elegant floating action bar appears directly above the selection bounding box with 4 minimal icon actions:
   - **Copy (Duplicate):** Duplicates the selection. The original elements remain intact; the copied clone immediately becomes active and draggable with the toolbar until the user clicks outside.
   - **Delete:** Removes the selected strokes/elements from the canvas with instant undo support.
   - **Mirror Left / Mirror Right:** Horizontally flips the selected vector points and elements across their bounding box center ($x' = 2x_c - x$).
   - **Mirror Top / Mirror Bottom:** Vertically flips the selection across its center ($y' = 2y_c - y$).
3. **Pen Dwell (Stationary Long-Press):**
   - If the stylus tip is held in one spot without moving ($\Delta r < 6\,\text{px}$) for $\ge 450\,\text{ms}$, trigger haptic feedback and display the **Radial Quick Action / Paste Menu** at the pen tip.

### 2.3 Multi-Touch Gestures & Palm Rejection

- **Strict Palm Rejection:**
  - If a stylus is currently touching or hovered within $12\,\text{mm}$ (or was active within the last $350\,\text{ms}$), all `TOOL_TYPE_FINGER` events with broad contact area (`touchMajor > 35dp`) or single-finger drag are rejected.
- **Two-Finger Navigation:**
  - When two fingers touch without a stylus active: smoothly Pan, Pinch-Zoom, and Rotate the infinite canvas.
- **Quick Tap Shortcuts:**
  - **Two-finger Quick Tap ($\Delta t < 250\,\text{ms}$):** Triggers **Undo**.
  - **Three-finger Quick Tap ($\Delta t < 250\,\text{ms}$):** Triggers **Redo**.

---

## SECTION 3: CORE TOOLSET & VECTOR MANIPULATION

### 3.1 Concepts-Parity Brushes
- **Pen:** Variable line weight, ultra-responsive ink.
- **Soft Pencil & Hard Pencil:** Graphite simulation with tilt shading.
- **Fountain Pen:** Calligraphic stroke dynamics.
- **Wire:** Exact fixed millimeter/pixel width for CAD and architectural diagrams.
- **Marker:** Semi-transparent multiply-blended strokes.
- **Slice Tool:** Dynamic vector knife. When the user swipes across strokes, computes geometric intersections and splits each intersected stroke into separate sub-strokes.
- **Nudge Tool:** Interactive vector deformer. As the tool moves, points within radius $R$ are displaced by $\vec{d} \cdot (1 - r/R)^2$.
- **Hard Vector Eraser:** Deletes entire strokes upon touch.
- **Mask Eraser:** Non-destructively subtracts or masks vector segments.

### 3.2 Live Line Smoothing: Streamline Algorithm

Streamline uses an exponential moving average (EMA) with a virtual drag anchor to filter physiological hand tremor:

$$\vec{P}_{\text{filtered}}(t) = \vec{P}_{\text{filtered}}(t-1) + \alpha \cdot (\vec{P}_{\text{raw}}(t) - \vec{P}_{\text{filtered}}(t-1))$$

where $\alpha = 1.0 - \text{smoothing}^{0.75}$. Then, Catmull-Rom splines are converted to cubic Bezier control points:

$$\vec{C}_1 = \vec{P}_1 + \frac{\vec{P}_2 - \vec{P}_0}{6}, \quad \vec{C}_2 = \vec{P}_2 - \frac{\vec{P}_3 - \vec{P}_1}{6}$$

### 3.3 Color Engine: COPIC Spectrum Parity

Implements a circular multi-tiered color wheel featuring:
1. **Hue Dial:** 360-degree color wheel segmented into COPIC color families (Warm Greys W0-W10, Cool Greys C0-C10, Toner Greys T0-T10, Earth, Floral, Blues, Greens, Yellows, Oranges, Reds, Violets).
2. **Saturation & Value Ring:** Real-time HSL/HSV selector.
3. **Hex / RGB Input & Custom Palette Swatches.**

---

## SECTION 4: LAYER SYSTEM, GRIDS, AND PRECISION

### 4.1 Infinite Layer System
- Independent layer stack with:
  - Add / Delete / Duplicate layers.
  - Reorder layers via drag-and-drop.
  - Layer Visibility toggle (eye icon).
  - Layer Lock toggle (padlock icon).
  - Layer Opacity slider ($0\% - 100\%$).
- Vector objects belong to the layer active at draw time.

### 4.2 Precision Grids & Guides
- **Grid Modes:** Blank, Dot Grid, Line Grid, Isometric Grid (for 3D sketching), and Graph Paper.
- **Grid Snapping:** Automatically snaps stroke endpoints or guide lines to grid intersections.
- **Real-World Scaling:** Metric (mm, cm, m) or Imperial (in, ft) with customizable scale ratios (e.g., 1:50, 1:100).

---

## SECTION 5: UI/UX & EXPORT ENGINE

### 5.1 Concepts-Style Floating Tool Wheel
- **Wheel Geometry:** Radial dial with 8 fast-access tool slots, active color preview center, brush size arc, and opacity slider.
- **Docking:** Draggable to any screen corner or edge, collapsing to a compact floating arc on smaller screens.

### 5.2 Source Imports (Images, Screenshots, Documents)
- Users can import pictures, screenshots, and PDF pages into the canvas.
- Imported media are first-class canvas elements:
  - Users can sketch directly on top with zero latency.
  - S Pen lasso selection (pressed button) selects both drawings and image elements together.
  - Supports duplicate (copy), mirror horizontal/vertical, rotate, and resize.

### 5.3 Export Engine
- **SVG Export:** Pure W3C XML SVG with path data (`M`, `C`), colors, and layer groupings.
- **PNG / JPG Export:** Multi-megapixel raster export with transparent background option.
- **Vector JSON Export:** Complete native project serialization.
