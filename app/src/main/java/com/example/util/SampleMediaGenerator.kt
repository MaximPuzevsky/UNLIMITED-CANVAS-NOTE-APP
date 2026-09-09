package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object SampleMediaGenerator {

    /**
     * Generates a sample architectural elevation schematic to demonstrate imported source media.
     */
    fun createSampleBlueprintBitmap(): Bitmap {
        val width = 720
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: Deep blueprint navy
        canvas.drawColor(Color.parseColor("#0B2545"))

        val gridPaint = Paint().apply {
            color = Color.parseColor("#133C6B")
            strokeWidth = 1f
        }

        // Blueprint sub-grid
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += 24f
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += 24f
        }

        // Architectural lines
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8DA9C4")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEF4F8")
            textSize = 20f
            isFakeBoldText = true
        }

        // Title box
        canvas.drawRect(RectF(40f, 40f, width - 40f, height - 40f), linePaint)
        canvas.drawText("SOURCE: ARCHITECTURAL ELEVATION A-1", 60f, 75f, textPaint)

        // Building structure silhouette
        canvas.drawRect(RectF(120f, 160f, 600f, 400f), linePaint)
        canvas.drawLine(120f, 160f, 360f, 90f, linePaint)
        canvas.drawLine(360f, 90f, 600f, 160f, linePaint)

        // Windows
        canvas.drawRect(RectF(160f, 200f, 260f, 280f), linePaint)
        canvas.drawRect(RectF(460f, 200f, 560f, 280f), linePaint)
        canvas.drawRect(RectF(320f, 260f, 400f, 400f), linePaint)

        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64DFDF")
            textSize = 14f
        }
        canvas.drawText("• First-class Canvas Object: Draw right on top with S Pen!", 60f, 425f, hintPaint)

        return bitmap
    }
}
