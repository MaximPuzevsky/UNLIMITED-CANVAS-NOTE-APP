package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.VectorStroke
import java.io.ByteArrayOutputStream
import java.util.Locale

object VectorExporter {

    /**
     * Exports canvas layers and strokes to standard W3C SVG vector format.
     */
    fun exportToSvg(
        layers: List<CanvasLayer>,
        strokes: List<VectorStroke>,
        images: List<CanvasImageElement>
    ): String {
        // Calculate global canvas bounds
        val allBounds = GeometryMath.computeSelectionBounds(strokes, images)
            ?: RectF(0f, 0f, 1920f, 1080f)

        val padding = 80f
        val viewBoxLeft = allBounds.left - padding
        val viewBoxTop = allBounds.top - padding
        val viewBoxWidth = (allBounds.width() + padding * 2).coerceAtLeast(100f)
        val viewBoxHeight = (allBounds.height() + padding * 2).coerceAtLeast(100f)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        sb.append(
            String.format(
                Locale.US,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" " +
                        "viewBox=\"%.2f %.2f %.2f %.2f\" width=\"%.2f\" height=\"%.2f\">\n",
                viewBoxLeft, viewBoxTop, viewBoxWidth, viewBoxHeight, viewBoxWidth, viewBoxHeight
            )
        )
        sb.append("  <title>Concepts Vector Export</title>\n")
        sb.append("  <desc>Exported from Concepts Infinite Sketch Vector Engine</desc>\n")

        // Group by layers in order
        val sortedLayers = layers.sortedBy { it.orderIndex }
        for (layer in sortedLayers) {
            if (!layer.isVisible) continue
            sb.append(
                String.format(
                    Locale.US,
                    "  <g id=\"layer_%s\" name=\"%s\" opacity=\"%.2f\">\n",
                    layer.id, layer.name, layer.opacity
                )
            )

            val layerStrokes = strokes.filter { it.layerId == layer.id && !it.isDeleted }
            for (stroke in layerStrokes) {
                if (stroke.points.size < 2) continue

                val hexColor = String.format(
                    Locale.US,
                    "#%06X",
                    0xFFFFFF and stroke.color
                )
                val alpha = ((stroke.color ushr 24) and 0xFF) / 255f * stroke.opacity

                val pathData = StringBuilder()
                pathData.append(String.format(Locale.US, "M %.2f %.2f", stroke.points[0].x, stroke.points[0].y))

                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    pathData.append(String.format(Locale.US, " L %.2f %.2f", p.x, p.y))
                }

                sb.append(
                    String.format(
                        Locale.US,
                        "    <path d=\"%s\" stroke=\"%s\" stroke-width=\"%.2f\" stroke-opacity=\"%.2f\" " +
                                "fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\" />\n",
                        pathData.toString(), hexColor, stroke.baseWidth, alpha
                    )
                )
            }
            sb.append("  </g>\n")
        }

        sb.append("</svg>\n")
        return sb.toString()
    }

    /**
     * Renders vector canvas into a high-resolution standalone raster Bitmap.
     */
    fun exportToBitmap(
        layers: List<CanvasLayer>,
        strokes: List<VectorStroke>,
        images: List<CanvasImageElement>,
        transparentBg: Boolean = false,
        maxWidth: Int = 2048
    ): Bitmap {
        val allBounds = GeometryMath.computeSelectionBounds(strokes, images)
            ?: RectF(0f, 0f, 1920f, 1080f)

        val padding = 60f
        val w = (allBounds.width() + padding * 2).coerceAtLeast(200f)
        val h = (allBounds.height() + padding * 2).coerceAtLeast(200f)

        val scale = (maxWidth.toFloat() / w).coerceAtMost(2f).coerceAtLeast(0.5f)
        val bitmapW = (w * scale).toInt().coerceIn(200, 4096)
        val bitmapH = (h * scale).toInt().coerceIn(200, 4096)

        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (!transparentBg) {
            canvas.drawColor(Color.parseColor("#1C1C22"))
        }

        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-allBounds.left + padding, -allBounds.top + padding)

        // Draw images first
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (img in images) {
            canvas.save()
            canvas.translate(img.worldX, img.worldY)
            canvas.rotate(img.rotationDeg)
            canvas.scale(img.scaleX, img.scaleY)
            canvas.drawBitmap(
                img.bitmap,
                -img.width / 2f,
                -img.height / 2f,
                paint
            )
            canvas.restore()
        }

        // Draw strokes layer by layer
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (layer in layers.sortedBy { it.orderIndex }) {
            if (!layer.isVisible) continue
            val layerStrokes = strokes.filter { it.layerId == layer.id && !it.isDeleted }

            for (s in layerStrokes) {
                if (s.points.size < 2) continue
                strokePaint.color = s.color
                strokePaint.strokeWidth = s.baseWidth
                strokePaint.alpha = ((Color.alpha(s.color) / 255f) * s.opacity * layer.opacity * 255f).toInt()

                val path = android.graphics.Path()
                path.moveTo(s.points[0].x, s.points[0].y)
                for (i in 1 until s.points.size) {
                    val p = s.points[i]
                    path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, strokePaint)
            }
        }

        canvas.restore()
        return bitmap
    }

    /**
     * Exports canvas data in Concepts Vector JSON schema.
     */
    fun exportToJson(
        layers: List<CanvasLayer>,
        strokes: List<VectorStroke>,
        images: List<CanvasImageElement>
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"generator\": \"Concepts S Pen Infinite Vector Engine\",\n")
        sb.append("  \"version\": \"2.0\",\n")
        sb.append("  \"timestamp\": ${System.currentTimeMillis()},\n")
        sb.append("  \"layerCount\": ${layers.size},\n")
        sb.append("  \"strokeCount\": ${strokes.filter { !it.isDeleted }.size},\n")
        sb.append("  \"imageCount\": ${images.size},\n")
        sb.append("  \"layers\": [\n")
        for (i in layers.indices) {
            val l = layers[i]
            sb.append("    {\"id\": \"${l.id}\", \"name\": \"${l.name}\", \"opacity\": ${l.opacity}, \"order\": ${l.orderIndex}}")
            if (i < layers.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"strokes\": [\n")
        val activeStrokes = strokes.filter { !it.isDeleted }
        for (i in activeStrokes.indices) {
            val s = activeStrokes[i]
            sb.append("    {\n")
            sb.append("      \"id\": \"${s.id}\",\n")
            sb.append("      \"layerId\": \"${s.layerId}\",\n")
            sb.append("      \"brush\": \"${s.brushType.name}\",\n")
            sb.append("      \"color\": \"#${String.format("%08X", s.color)}\",\n")
            sb.append("      \"width\": ${s.baseWidth},\n")
            sb.append("      \"opacity\": ${s.opacity},\n")
            sb.append("      \"smoothing\": ${s.smoothing},\n")
            sb.append("      \"pointCount\": ${s.points.size}\n")
            sb.append("    }")
            if (i < activeStrokes.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }
}
