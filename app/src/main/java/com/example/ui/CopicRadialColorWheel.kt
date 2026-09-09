package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Authentic Concepts Multi-Tiered COPIC Radial Color Swatch Wheel.
 * Organizes COPIC markers into radial color family slices with 4 concentric tiers:
 * - Tier 0 (Inner): Pastel / Tint
 * - Tier 1 (Mid-Inner): Light Pure
 * - Tier 2 (Mid-Outer): Signature Saturated Tone
 * - Tier 3 (Outer): Deep / Shadow Tone
 *
 * Supports continuous infinite 2-way touch rotation physics with angular inertia.
 */
data class CopicSwatchItem(
    val code: String,
    val name: String,
    val colorInt: Int
)

data class CopicSlice(
    val familyCode: String,
    val familyName: String,
    val tiers: List<CopicSwatchItem> // Exactly 4 tiers: 0=Tint, 1=Light, 2=Pure, 3=Deep
)

object CopicPaletteData {
    val slices: List<CopicSlice> = listOf(
        // 1. Cool Gray
        CopicSlice(
            familyCode = "C",
            familyName = "Cool Gray",
            tiers = listOf(
                CopicSwatchItem("C-1", "Cool Gray 1", android.graphics.Color.parseColor("#E2E4E6")),
                CopicSwatchItem("C-3", "Cool Gray 3", android.graphics.Color.parseColor("#CBD0D4")),
                CopicSwatchItem("C-7", "Cool Gray 7", android.graphics.Color.parseColor("#686E74")),
                CopicSwatchItem("100", "Black", android.graphics.Color.parseColor("#111111"))
            )
        ),
        // 2. Warm Gray
        CopicSlice(
            familyCode = "W",
            familyName = "Warm Gray",
            tiers = listOf(
                CopicSwatchItem("W-1", "Warm Gray 1", android.graphics.Color.parseColor("#E6E4DF")),
                CopicSwatchItem("W-3", "Warm Gray 3", android.graphics.Color.parseColor("#D0CDC7")),
                CopicSwatchItem("W-7", "Warm Gray 7", android.graphics.Color.parseColor("#6C6863")),
                CopicSwatchItem("0", "Colorless White", android.graphics.Color.parseColor("#FFFFFF"))
            )
        ),
        // 3. Earth / Browns
        CopicSlice(
            familyCode = "E",
            familyName = "Earth Tone",
            tiers = listOf(
                CopicSwatchItem("E00", "Skin White", android.graphics.Color.parseColor("#FDF1E6")),
                CopicSwatchItem("E11", "Barley Beige", android.graphics.Color.parseColor("#F6DEC8")),
                CopicSwatchItem("E29", "Burnt Umber", android.graphics.Color.parseColor("#6C4334")),
                CopicSwatchItem("E37", "Sepia", android.graphics.Color.parseColor("#9C6839"))
            )
        ),
        // 4. Red-Violets
        CopicSlice(
            familyCode = "RV",
            familyName = "Red Violet",
            tiers = listOf(
                CopicSwatchItem("RV00", "Water Lily", android.graphics.Color.parseColor("#FAEEF3")),
                CopicSwatchItem("RV11", "Bastel Pink", android.graphics.Color.parseColor("#F5ABC2")),
                CopicSwatchItem("RV29", "Crimson", android.graphics.Color.parseColor("#D8285D")),
                CopicSwatchItem("RV69", "Peony", android.graphics.Color.parseColor("#7A2548"))
            )
        ),
        // 5. Reds
        CopicSlice(
            familyCode = "R",
            familyName = "Red",
            tiers = listOf(
                CopicSwatchItem("R00", "Pinkish White", android.graphics.Color.parseColor("#FBECE7")),
                CopicSwatchItem("R14", "Light Crimson", android.graphics.Color.parseColor("#F7776F")),
                CopicSwatchItem("R29", "Lipstick Red", android.graphics.Color.parseColor("#E62B34")),
                CopicSwatchItem("R59", "Cardinal", android.graphics.Color.parseColor("#8C3845"))
            )
        ),
        // 6. Yellow-Reds / Oranges
        CopicSlice(
            familyCode = "YR",
            familyName = "Yellow Red",
            tiers = listOf(
                CopicSwatchItem("YR00", "Powder Pink", android.graphics.Color.parseColor("#FDEAE0")),
                CopicSwatchItem("YR02", "Light Orange", android.graphics.Color.parseColor("#FDC8AA")),
                CopicSwatchItem("YR04", "Chrome Orange", android.graphics.Color.parseColor("#FA8223")),
                CopicSwatchItem("YR09", "Chinese Orange", android.graphics.Color.parseColor("#DE4C17"))
            )
        ),
        // 7. Yellows
        CopicSlice(
            familyCode = "Y",
            familyName = "Yellow",
            tiers = listOf(
                CopicSwatchItem("Y00", "Barium Yellow", android.graphics.Color.parseColor("#FEFBE3")),
                CopicSwatchItem("Y06", "Yellow", android.graphics.Color.parseColor("#FEE842")),
                CopicSwatchItem("Y15", "Cadmium Yellow", android.graphics.Color.parseColor("#FBD429")),
                CopicSwatchItem("Y17", "Golden Yellow", android.graphics.Color.parseColor("#F9B417"))
            )
        ),
        // 8. Yellow-Greens
        CopicSlice(
            familyCode = "YG",
            familyName = "Yellow Green",
            tiers = listOf(
                CopicSwatchItem("YG00", "Mimosa Yellow", android.graphics.Color.parseColor("#F5F8D5")),
                CopicSwatchItem("YG03", "Yellow Green", android.graphics.Color.parseColor("#DCE875")),
                CopicSwatchItem("YG17", "Grass Green", android.graphics.Color.parseColor("#74B238")),
                CopicSwatchItem("YG67", "Moss", android.graphics.Color.parseColor("#578B3F"))
            )
        ),
        // 9. Greens
        CopicSlice(
            familyCode = "G",
            familyName = "Green",
            tiers = listOf(
                CopicSwatchItem("G00", "Jade Green", android.graphics.Color.parseColor("#E4F4E8")),
                CopicSwatchItem("G05", "Emerald Green", android.graphics.Color.parseColor("#5DBA66")),
                CopicSwatchItem("G17", "Forest Green", android.graphics.Color.parseColor("#008C4A")),
                CopicSwatchItem("G28", "Ocean Green", android.graphics.Color.parseColor("#00673B"))
            )
        ),
        // 10. Blue-Greens
        CopicSlice(
            familyCode = "BG",
            familyName = "Blue Green",
            tiers = listOf(
                CopicSwatchItem("BG000", "Pale Aqua", android.graphics.Color.parseColor("#E8F6F6")),
                CopicSwatchItem("BG05", "Holiday Blue", android.graphics.Color.parseColor("#6BC2CA")),
                CopicSwatchItem("BG15", "Nile Blue", android.graphics.Color.parseColor("#3AAFA9")),
                CopicSwatchItem("BG49", "Duck Blue", android.graphics.Color.parseColor("#00798C"))
            )
        ),
        // 11. Robin & Sky Blues
        CopicSlice(
            familyCode = "B",
            familyName = "Sky Blue",
            tiers = listOf(
                CopicSwatchItem("B00", "Frost Blue", android.graphics.Color.parseColor("#E3EFF6")),
                CopicSwatchItem("B02", "Robin's Egg", android.graphics.Color.parseColor("#9DD5EE")),
                CopicSwatchItem("B05", "Process Blue", android.graphics.Color.parseColor("#3FB1E5")),
                CopicSwatchItem("B24", "Sky Blue", android.graphics.Color.parseColor("#4A90E2"))
            )
        ),
        // 12. Deep Royal Blues
        CopicSlice(
            familyCode = "B-D",
            familyName = "Royal Blue",
            tiers = listOf(
                CopicSwatchItem("B12", "Ice Blue", android.graphics.Color.parseColor("#CBE3F3")),
                CopicSwatchItem("B28", "Royal Blue", android.graphics.Color.parseColor("#2364AA")),
                CopicSwatchItem("B29", "Night Blue", android.graphics.Color.parseColor("#005DA4")),
                CopicSwatchItem("B39", "Prussian Blue", android.graphics.Color.parseColor("#1E385B"))
            )
        ),
        // 13. Blue-Violets
        CopicSlice(
            familyCode = "BV",
            familyName = "Blue Violet",
            tiers = listOf(
                CopicSwatchItem("BV00", "Mauve Shadow", android.graphics.Color.parseColor("#EAE3ED")),
                CopicSwatchItem("BV04", "Blue Berry", android.graphics.Color.parseColor("#8A8BBF")),
                CopicSwatchItem("BV08", "Iris", android.graphics.Color.parseColor("#585481")),
                CopicSwatchItem("BV29", "Slate", android.graphics.Color.parseColor("#2A2A38"))
            )
        ),
        // 14. Violets
        CopicSlice(
            familyCode = "V",
            familyName = "Violet",
            tiers = listOf(
                CopicSwatchItem("V00", "Pale Lavender", android.graphics.Color.parseColor("#F0E5F0")),
                CopicSwatchItem("V04", "Lilac", android.graphics.Color.parseColor("#CA8AC0")),
                CopicSwatchItem("V09", "Violet", android.graphics.Color.parseColor("#6A2A80")),
                CopicSwatchItem("V17", "Amethyst", android.graphics.Color.parseColor("#5C3C75"))
            )
        ),
        // 15. Warm Ochres & Sands
        CopicSlice(
            familyCode = "E-W",
            familyName = "Sand Ochre",
            tiers = listOf(
                CopicSwatchItem("E33", "Sand", android.graphics.Color.parseColor("#DFB186")),
                CopicSwatchItem("E35", "Chamois", android.graphics.Color.parseColor("#D29B63")),
                CopicSwatchItem("E49", "Dark Bark", android.graphics.Color.parseColor("#422918")),
                CopicSwatchItem("E79", "Cashew", android.graphics.Color.parseColor("#331B10"))
            )
        ),
        // 16. Neutral Grays
        CopicSlice(
            familyCode = "N",
            familyName = "Neutral Gray",
            tiers = listOf(
                CopicSwatchItem("N-1", "Neutral Gray 1", android.graphics.Color.parseColor("#E6E6E6")),
                CopicSwatchItem("N-3", "Neutral Gray 3", android.graphics.Color.parseColor("#CCCCCC")),
                CopicSwatchItem("N-5", "Neutral Gray 5", android.graphics.Color.parseColor("#999999")),
                CopicSwatchItem("N-9", "Neutral Gray 9", android.graphics.Color.parseColor("#333333"))
            )
        )
    )
}

/**
 * Concepts Radial Multi-Tiered COPIC Color Wheel Composable.
 * Features:
 * - Concentric Tiers of authentic COPIC swatches.
 * - Smooth touch-drag infinite 2-way rotation physics with inertial fling.
 * - Center preview disc showing active swatch code and color.
 * - Direct tap-to-select on any radial swatch segment.
 */
@Composable
fun CopicRadialColorWheel(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val slices = CopicPaletteData.slices
    val numSlices = slices.size
    val sliceAngle = 360f / numSlices

    var currentActiveColor by remember { mutableIntStateOf(selectedColor) }
    var activeSwatchItem by remember {
        mutableStateOf(
            slices.flatMap { it.tiers }.firstOrNull { it.colorInt == selectedColor }
                ?: CopicSwatchItem("ACTIVE", "Custom Color", selectedColor)
        )
    }

    // Infinite 2-way rotation angle state
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Radii in DP
    val wheelRadiusDp = 185.dp
    val wheelSizeDp = wheelRadiusDp * 2 // 370dp

    // Tier metrics:
    // Tier 0: 52dp to 84dp (Pastels)
    // Tier 1: 86dp to 118dp (Light tones)
    // Tier 2: 120dp to 152dp (Vivid tones)
    // Tier 3: 154dp to 184dp (Deep tones)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(wheelSizeDp)
            .testTag("copic_radial_color_wheel")
    ) {
        // Multi-tiered Radial Canvas
        Canvas(
            modifier = Modifier
                .size(wheelSizeDp)
                .pointerInput(Unit) {
                    var prevX = 0f
                    var prevY = 0f
                    var touchDownX = 0f
                    var touchDownY = 0f
                    var touchDownTime = 0L

                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            angularVelocity = 0f
                            prevX = offset.x
                            prevY = offset.y
                            touchDownX = offset.x
                            touchDownY = offset.y
                            touchDownTime = System.currentTimeMillis()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val currX = change.position.x
                            val currY = change.position.y
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            val anglePrev = Math.toDegrees(
                                atan2((prevY - centerY).toDouble(), (prevX - centerX).toDouble())
                            ).toFloat()
                            val angleCurr = Math.toDegrees(
                                atan2((currY - centerY).toDouble(), (currX - centerX).toDouble())
                            ).toFloat()

                            var dAngle = angleCurr - anglePrev
                            while (dAngle > 180f) dAngle -= 360f
                            while (dAngle < -180f) dAngle += 360f

                            rotationAngle = (rotationAngle + dAngle + 3600f) % 360f
                            angularVelocity = dAngle

                            prevX = currX
                            prevY = currY
                        },
                        onDragEnd = {
                            isDragging = false
                            // Apply smooth momentum physics fling
                            scope.launch {
                                while (kotlin.math.abs(angularVelocity) > 0.04f && !isDragging) {
                                    rotationAngle = (rotationAngle + angularVelocity + 3600f) % 360f
                                    angularVelocity *= 0.93f // inertial friction decay
                                    delay(16)
                                }
                                angularVelocity = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            angularVelocity = 0f
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = tapOffset.x - centerX
                        val dy = tapOffset.y - centerY
                        val distPx = hypot(dx, dy)
                        val density = this.density
                        val distDp = distPx / density

                        // Tap on center hub: dismiss/confirm
                        if (distDp < 50f) {
                            onClose()
                            return@detectTapGestures
                        }

                        // Check which tier was tapped (0..3)
                        val tierIndex = when {
                            distDp in 50f..84f -> 0
                            distDp in 85f..118f -> 1
                            distDp in 119f..152f -> 2
                            distDp in 153f..186f -> 3
                            else -> -1
                        }

                        if (tierIndex >= 0) {
                            val tapAngleDeg = (Math.toDegrees(
                                atan2(dy.toDouble(), dx.toDouble())
                            ).toFloat() + 3600f) % 360f

                            // Subtract current wheel rotation to get relative angle
                            val relAngle = (tapAngleDeg - rotationAngle + 3600f) % 360f
                            val sliceIndex = ((relAngle / sliceAngle).toInt()) % numSlices
                            val slice = slices[sliceIndex]
                            val swatch = slice.tiers[tierIndex]

                            currentActiveColor = swatch.colorInt
                            activeSwatchItem = swatch
                            onColorSelected(swatch.colorInt)
                        }
                    }
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val density = this.density

            // Outer backdrop ring
            drawCircle(
                color = Color(0xFF13151F),
                radius = 186.dp.toPx(),
                center = Offset(centerX, centerY)
            )

            val tierRadiiDp = listOf(67f, 101f, 135f, 169f) // Center of each tier stroke
            val tierWidthPx = 32.dp.toPx()
            val sweepGapDeg = 1.0f
            val effectiveSweep = sliceAngle - sweepGapDeg

            // Draw each concentric tier arc for all slices
            for (t in 0..3) {
                val radiusPx = tierRadiiDp[t] * density
                val strokeStyle = Stroke(width = tierWidthPx, cap = StrokeCap.Butt)

                for (s in 0 until numSlices) {
                    val slice = slices[s]
                    val swatch = slice.tiers[t]
                    val startAngle = (rotationAngle + s * sliceAngle) % 360f
                    val arcColor = Color(swatch.colorInt)

                    // Draw the radial wedge arc
                    drawArc(
                        color = arcColor,
                        startAngle = startAngle,
                        sweepAngle = effectiveSweep,
                        useCenter = false,
                        topLeft = Offset(centerX - radiusPx, centerY - radiusPx),
                        size = Size(radiusPx * 2f, radiusPx * 2f),
                        style = strokeStyle
                    )

                    // Highlight selected swatch border
                    if (swatch.colorInt == currentActiveColor) {
                        drawArc(
                            color = Color.White,
                            startAngle = startAngle - 0.5f,
                            sweepAngle = effectiveSweep + 1f,
                            useCenter = false,
                            topLeft = Offset(centerX - radiusPx, centerY - radiusPx),
                            size = Size(radiusPx * 2f, radiusPx * 2f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Radial hairline dividers
            for (s in 0 until numSlices) {
                val angleDeg = (rotationAngle + s * sliceAngle) % 360f
                val rad = Math.toRadians(angleDeg.toDouble())
                val innerR = 50.dp.toPx()
                val outerR = 186.dp.toPx()

                val x1 = centerX + (innerR * cos(rad)).toFloat()
                val y1 = centerY + (innerR * sin(rad)).toFloat()
                val x2 = centerX + (outerR * cos(rad)).toFloat()
                val y2 = centerY + (outerR * sin(rad)).toFloat()

                drawLine(
                    color = Color(0xFF10121A),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Outer protective rim ring
            drawCircle(
                color = Color(0xFF2C3246),
                radius = 186.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner hub boundary ring
            drawCircle(
                color = Color(0xFF222634),
                radius = 50.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Center Hub Preview Disc (Diameter 96dp)
        Surface(
            shape = CircleShape,
            color = Color(currentActiveColor),
            shadowElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable { onClose() }
                .testTag("copic_wheel_center_hub")
        ) {
            val isLightColor = Color(currentActiveColor).run {
                (red * 0.299 + green * 0.587 + blue * 0.114) > 0.65
            }
            val textColor = if (isLightColor) Color.Black else Color.White

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                Text(
                    text = activeSwatchItem.code,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = activeSwatchItem.name,
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Apply Color",
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Floating Close button on top-right of wheel
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp)
                .background(Color(0xFF1E212E), CircleShape)
                .border(1.dp, Color(0xFF383D54), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Color Wheel",
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
