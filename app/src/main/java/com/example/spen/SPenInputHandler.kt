package com.example.spen

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.example.model.FingerMode
import com.example.model.RawPoint
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * High-performance S Pen and Multi-Touch event processor for Galaxy Tablets.
 * Manages:
 * 1. Default Pan/Navigation mode vs dynamic drawing tool override.
 * 2. 2-finger selection transform (scale/rotate around selection center) vs canvas zoom/rotate.
 * 3. 2-finger tap (Undo) and 3-finger tap (Redo) in unselected state.
 * 4. Press-and-hold (>500ms) long-press context menu with preliminary dot erasure and stroke suppression.
 */
class SPenInputHandler(
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onPenStrokeStart(point: RawPoint, isButtonPressed: Boolean)
        fun onPenStrokeMove(points: List<RawPoint>, isButtonPressed: Boolean)
        fun onPenStrokeEnd(point: RawPoint, isButtonPressed: Boolean)
        fun onLongPressDwell(screenX: Float, screenY: Float)
        fun onTwoFingerPanZoomRotate(panDx: Float, panDy: Float, scaleFactor: Float, rotateDeltaDeg: Float)
        fun onTwoFingerSelectionTransform(panDx: Float, panDy: Float, scaleFactor: Float, rotateDeltaDeg: Float)
        fun onTwoFingerTapUndo()
        fun onThreeFingerTapRedo()
        fun onSingleTapOutsideSelection(screenX: Float, screenY: Float)
        fun onReturnToPanMode()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingLongPressRunnable: Runnable? = null

    private var lastStylusTimestamp = 0L
    private var isStylusActive = false
    private var isPenDrawing = false
    private var penDownX = 0f
    private var penDownY = 0f
    private var isButtonPressedAtStart = false

    // Stroke suppression & dot cleanup flag
    private var isStrokeSuppressed = false
    private var isLongPressTriggered = false

    // Finger drawing & touch tracking
    private var isFingerDrawing = false
    private var fingerDownX = 0f
    private var fingerDownY = 0f
    private var multiTouchStartTime = 0L
    private var pointerCountAtDown = 0
    private var prevFingerDist = 0f
    private var prevFingerAngle = 0f
    private var prevFingerMidX = 0f
    private var prevFingerMidY = 0f
    private var hasMultiTouchMoved = false
    private var isPinchZoomPanActive = false

    fun onTouchEvent(
        event: MotionEvent,
        viewWidth: Float,
        viewHeight: Float,
        fingerMode: FingerMode = FingerMode.PAN,
        isSelectionActive: Boolean = false
    ): Boolean {
        val action = event.actionMasked
        val pointerCount = event.pointerCount
        val now = SystemClock.uptimeMillis()

        // Detect if any pointer is an S Pen stylus
        var eventHasStylus = false
        var stylusIndex = 0
        for (i in 0 until pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                eventHasStylus = true
                stylusIndex = i
                break
            }
        }

        if (eventHasStylus) {
            if (isFingerDrawing) {
                cancelPendingLongPress()
                val pt = RawPoint(event.x, event.y, pressure = 1.0f, timestampNs = System.nanoTime())
                if (!isStrokeSuppressed) {
                    callbacks.onPenStrokeEnd(pt, isButtonPressed = false)
                }
                isFingerDrawing = false
            }
            lastStylusTimestamp = now
            isStylusActive = true
            handleStylusEvent(event, stylusIndex, now)
            return true
        }

        // Strict Palm Rejection:
        // If S Pen was used within last 350ms, ignore accidental palm or single-finger taps
        if (now - lastStylusTimestamp < 350L) {
            return true
        }

        isStylusActive = false

        // Multi-touch finger gestures and finger drawing/panning
        handleFingerEvent(event, now, fingerMode, isSelectionActive)
        return true
    }

    private fun handleStylusEvent(event: MotionEvent, index: Int, now: Long) {
        val action = event.actionMasked
        val sx = event.getX(index)
        val sy = event.getY(index)
        val rawPressure = event.getAxisValue(MotionEvent.AXIS_PRESSURE, index).takeIf { it > 0.001f }
            ?: event.getPressure(index)
        val pressure = rawPressure.coerceIn(0.01f, 1.0f)
        val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, index)
        val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, index)

        // Detect S Pen side barrel button
        val buttonState = event.buttonState
        val isButtonPressed = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                (buttonState and MotionEvent.BUTTON_SECONDARY) != 0

        val currentPoint = RawPoint(
            x = sx,
            y = sy,
            pressure = pressure,
            tiltAngle = tilt,
            orientation = orientation,
            timestampNs = System.nanoTime()
        )

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingLongPress()
                isPenDrawing = true
                penDownX = sx
                penDownY = sy
                isButtonPressedAtStart = isButtonPressed
                isStrokeSuppressed = false
                isLongPressTriggered = false

                // Notify stroke start
                callbacks.onPenStrokeStart(currentPoint, isButtonPressed)

                // Schedule 500ms press-and-hold timer for blank canvas quick menu
                scheduleLongPressTimer(sx, sy)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isPenDrawing || isStrokeSuppressed) return

                val distFromStart = hypot(sx - penDownX, sy - penDownY)
                if (distFromStart >= 15f) {
                    cancelPendingLongPress()
                }

                // Collect historical batch points for high frequency sampling
                val points = mutableListOf<RawPoint>()
                for (h in 0 until event.historySize) {
                    val hx = event.getHistoricalX(index, h)
                    val hy = event.getHistoricalY(index, h)
                    val rawHp = event.getHistoricalAxisValue(MotionEvent.AXIS_PRESSURE, index, h).takeIf { it > 0.001f }
                        ?: event.getHistoricalPressure(index, h)
                    val hp = rawHp.coerceIn(0.01f, 1.0f)
                    val htilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, h)
                    val horient = event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, index, h)
                    val htime = event.getHistoricalEventTime(h)
                    points.add(
                        RawPoint(
                            x = hx,
                            y = hy,
                            pressure = hp,
                            tiltAngle = htilt,
                            orientation = horient,
                            timestampNs = htime * 1_000_000L
                        )
                    )
                }
                points.add(currentPoint)
                callbacks.onPenStrokeMove(points, isButtonPressed || isButtonPressedAtStart)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                if (isPenDrawing) {
                    isPenDrawing = false
                    if (!isStrokeSuppressed) {
                        callbacks.onPenStrokeEnd(currentPoint, isButtonPressed || isButtonPressedAtStart)
                    }
                    isStrokeSuppressed = false
                }
            }
        }
    }

    private fun handleFingerEvent(
        event: MotionEvent,
        now: Long,
        fingerMode: FingerMode,
        isSelectionActive: Boolean
    ) {
        val action = event.actionMasked
        val count = event.pointerCount

        val currentFingerPressure = (event.getAxisValue(MotionEvent.AXIS_PRESSURE).takeIf { it > 0.01f }
            ?: event.getPressure(0).takeIf { it > 0.01f }
            ?: 0.5f).coerceIn(0.05f, 1.0f)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingLongPress()
                multiTouchStartTime = now
                pointerCountAtDown = 1
                hasMultiTouchMoved = false
                prevFingerMidX = event.x
                prevFingerMidY = event.y
                fingerDownX = event.x
                fingerDownY = event.y
                isStrokeSuppressed = false
                isLongPressTriggered = false

                if (fingerMode == FingerMode.DRAW) {
                    isFingerDrawing = true
                    val pt = RawPoint(
                        x = event.x,
                        y = event.y,
                        pressure = currentFingerPressure,
                        timestampNs = System.nanoTime()
                    )
                    callbacks.onPenStrokeStart(pt, isButtonPressed = false)
                    // Schedule 500ms press-and-hold timer for blank area context menu
                    scheduleLongPressTimer(event.x, event.y)
                } else {
                    isFingerDrawing = false
                    // Also allow long press in pan mode
                    scheduleLongPressTimer(event.x, event.y)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelPendingLongPress()
                pointerCountAtDown = max(pointerCountAtDown, count)
                if (isFingerDrawing) {
                    val pt = RawPoint(
                        x = event.x,
                        y = event.y,
                        pressure = currentFingerPressure,
                        timestampNs = System.nanoTime()
                    )
                    if (!isStrokeSuppressed) {
                        callbacks.onPenStrokeEnd(pt, isButtonPressed = false)
                    }
                    isFingerDrawing = false
                }

                if (count == 2) {
                    isPinchZoomPanActive = true
                    val x1 = event.getX(0)
                    val y1 = event.getY(0)
                    val x2 = event.getX(1)
                    val y2 = event.getY(1)
                    prevFingerDist = hypot(x2 - x1, y2 - y1)
                    prevFingerAngle = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
                    prevFingerMidX = (x1 + x2) / 2f
                    prevFingerMidY = (y1 + y2) / 2f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (count == 1) {
                    if (isPinchZoomPanActive) {
                        // User lifted one finger from 2-finger gesture: do not pan with remaining finger
                        return
                    }
                    val dist = hypot(event.x - fingerDownX, event.y - fingerDownY)
                    if (dist > 15f) {
                        hasMultiTouchMoved = true
                        cancelPendingLongPress()
                    }

                    if (fingerMode == FingerMode.DRAW && isFingerDrawing && !isStrokeSuppressed) {
                        val points = mutableListOf<RawPoint>()
                        for (h in 0 until event.historySize) {
                            val histPress = (event.getHistoricalAxisValue(MotionEvent.AXIS_PRESSURE, 0, h).takeIf { it > 0.01f }
                                ?: event.getHistoricalPressure(0, h).takeIf { it > 0.01f }
                                ?: currentFingerPressure).coerceIn(0.05f, 1.0f)
                            points.add(
                                RawPoint(
                                    x = event.getHistoricalX(h),
                                    y = event.getHistoricalY(h),
                                    pressure = histPress,
                                    timestampNs = event.getHistoricalEventTime(h) * 1_000_000L
                                )
                            )
                        }
                        points.add(
                            RawPoint(
                                x = event.x,
                                y = event.y,
                                pressure = currentFingerPressure,
                                timestampNs = System.nanoTime()
                            )
                        )
                        callbacks.onPenStrokeMove(points, isButtonPressed = false)
                    } else if (fingerMode == FingerMode.PAN) {
                        val panDx = event.x - prevFingerMidX
                        val panDy = event.y - prevFingerMidY
                        callbacks.onTwoFingerPanZoomRotate(panDx, panDy, 1.0f, 0f)
                        prevFingerMidX = event.x
                        prevFingerMidY = event.y
                    }
                } else if (count == 2) {
                    cancelPendingLongPress()
                    isPinchZoomPanActive = true
                    val x1 = event.getX(0)
                    val y1 = event.getY(0)
                    val x2 = event.getX(1)
                    val y2 = event.getY(1)

                    val midX = (x1 + x2) / 2f
                    val midY = (y1 + y2) / 2f
                    val dist = hypot(x2 - x1, y2 - y1)
                    val angle = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()

                    val panDx = midX - prevFingerMidX
                    val panDy = midY - prevFingerMidY
                    val scaleFactor = if (prevFingerDist > 10f) dist / prevFingerDist else 1f
                    var rotateDelta = angle - prevFingerAngle

                    // Normalize angle delta to [-180, 180]
                    while (rotateDelta > 180) rotateDelta -= 360
                    while (rotateDelta < -180) rotateDelta += 360

                    if (hypot(panDx, panDy) > 4f || Math.abs(scaleFactor - 1f) > 0.015f || Math.abs(rotateDelta) > 1f) {
                        hasMultiTouchMoved = true
                    }

                    // Requirement 3: SELECTION GESTURES & OVERRIDES
                    // When objects are selected: 2-finger rotate/scale rotates and scales the selected object(s)
                    // around their bounding center instead of rotating/zooming the canvas.
                    if (isSelectionActive) {
                        callbacks.onTwoFingerSelectionTransform(panDx, panDy, scaleFactor, rotateDelta)
                    } else {
                        callbacks.onTwoFingerPanZoomRotate(panDx, panDy, scaleFactor, rotateDelta)
                    }

                    prevFingerDist = dist
                    prevFingerAngle = angle
                    prevFingerMidX = midX
                    prevFingerMidY = midY
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val duration = now - multiTouchStartTime
                // Zero-Drift: Freeze viewport immediately at ACTION_POINTER_UP
                isPinchZoomPanActive = false
                prevFingerDist = 0f
                prevFingerAngle = 0f

                // Re-anchor remaining pointer so subsequent move never jumps
                val upIndex = event.actionIndex
                val remainingIndex = if (upIndex == 0) 1 else 0
                if (count > 1) {
                    val rx = event.getX(remainingIndex)
                    val ry = event.getY(remainingIndex)
                    prevFingerMidX = rx
                    prevFingerMidY = ry
                    fingerDownX = rx
                    fingerDownY = ry
                }

                // Requirement 3: Unselected Canvas State Context:
                // 2-Finger Tap triggers Undo; 3-Finger Tap triggers Redo.
                if (!isSelectionActive && !hasMultiTouchMoved && duration < 320L) {
                    if (pointerCountAtDown == 2) {
                        callbacks.onTwoFingerTapUndo()
                    } else if (pointerCountAtDown == 3) {
                        callbacks.onThreeFingerTapRedo()
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                cancelPendingLongPress()
                isPinchZoomPanActive = false
                prevFingerDist = 0f
                prevFingerAngle = 0f
                val duration = now - multiTouchStartTime
                val dist = hypot(event.x - fingerDownX, event.y - fingerDownY)

                if (isFingerDrawing) {
                    val pt = RawPoint(
                        x = event.x,
                        y = event.y,
                        pressure = 1.0f,
                        timestampNs = System.nanoTime()
                    )
                    if (!isStrokeSuppressed) {
                        callbacks.onPenStrokeEnd(pt, isButtonPressed = false)
                    }
                    isFingerDrawing = false

                    // Requirement 1: Drawing Tools override this state dynamically when touch is registered,
                    // then return to Pan mode.
                    callbacks.onReturnToPanMode()
                }

                if (!hasMultiTouchMoved && dist < 12f && duration < 250L && pointerCountAtDown == 1 && !isLongPressTriggered) {
                    // Single finger tap outside selection clears selection
                    callbacks.onSingleTapOutsideSelection(event.x, event.y)
                }

                pointerCountAtDown = 0
                hasMultiTouchMoved = false
                isStrokeSuppressed = false
                isLongPressTriggered = false
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                if (isFingerDrawing) {
                    val pt = RawPoint(
                        x = event.x,
                        y = event.y,
                        pressure = 1.0f,
                        timestampNs = System.nanoTime()
                    )
                    if (!isStrokeSuppressed) {
                        callbacks.onPenStrokeEnd(pt, isButtonPressed = false)
                    }
                    isFingerDrawing = false
                    callbacks.onReturnToPanMode()
                }
                pointerCountAtDown = 0
                hasMultiTouchMoved = false
                isStrokeSuppressed = false
                isLongPressTriggered = false
            }
        }
    }

    /**
     * Requirement 4: PRESS-AND-HOLD CONTEXT MENU (DOT CLEANUP)
     * When held for >500ms:
     * - Erase preliminary point/dot from initial ACTION_DOWN.
     * - Suppress stroke committing.
     * - Show floating context menu with Paste and Import Media.
     */
    private fun scheduleLongPressTimer(x: Float, y: Float) {
        cancelPendingLongPress()
        val runnable = Runnable {
            isLongPressTriggered = true
            isStrokeSuppressed = true
            isPenDrawing = false
            isFingerDrawing = false
            callbacks.onLongPressDwell(x, y)
        }
        pendingLongPressRunnable = runnable
        mainHandler.postDelayed(runnable, 500L)
    }

    private fun cancelPendingLongPress() {
        pendingLongPressRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingLongPressRunnable = null
        }
    }
}
