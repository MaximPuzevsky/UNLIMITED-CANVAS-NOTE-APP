package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import com.example.model.BrushType
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.GridType
import com.example.model.RawPoint
import com.example.model.VectorStroke
import com.example.model.ViewportState
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ProjectDrawingData(
    val layers: List<CanvasLayer>,
    val strokes: List<VectorStroke>,
    val images: List<CanvasImageElement>,
    val viewport: ViewportState,
    val gridType: GridType
)

object DrawingStorage {

    private fun getProjectDir(context: Context, projectId: String): File {
        val dir = File(context.filesDir, "drawings/$projectId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getImagesDir(context: Context, projectId: String): File {
        val dir = File(getProjectDir(context, projectId), "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveDrawingFile(
        context: Context,
        projectId: String,
        data: ProjectDrawingData
    ) {
        val projectDir = getProjectDir(context, projectId)
        val imagesDir = getImagesDir(context, projectId)

        // 1. Save image bitmaps
        val imageJsonArray = JSONArray()
        for (img in data.images) {
            val imgFile = File(imagesDir, "${img.id}.png")
            if (!imgFile.exists() && img.bitmap != null && !img.bitmap.isRecycled) {
                try {
                    FileOutputStream(imgFile).use { out ->
                        img.bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val imgObj = JSONObject().apply {
                put("id", img.id)
                put("layerId", img.layerId)
                put("title", img.title)
                put("worldX", img.worldX.toDouble())
                put("worldY", img.worldY.toDouble())
                put("width", img.width.toDouble())
                put("height", img.height.toDouble())
                put("rotationDeg", img.rotationDeg.toDouble())
                put("scaleX", img.scaleX.toDouble())
                put("scaleY", img.scaleY.toDouble())
                put("isMirroredH", img.isMirroredH)
                put("isMirroredV", img.isMirroredV)
                put("opacity", img.opacity.toDouble())
            }
            imageJsonArray.put(imgObj)
        }

        // 2. Save layers
        val layersArray = JSONArray()
        for (layer in data.layers) {
            val lObj = JSONObject().apply {
                put("id", layer.id)
                put("name", layer.name)
                put("isVisible", layer.isVisible)
                put("isLocked", layer.isLocked)
                put("opacity", layer.opacity.toDouble())
                put("orderIndex", layer.orderIndex)
            }
            layersArray.put(lObj)
        }

        // 3. Save vector strokes
        val strokesArray = JSONArray()
        for (stroke in data.strokes) {
            val sObj = JSONObject().apply {
                put("id", stroke.id)
                put("layerId", stroke.layerId)
                put("brushType", stroke.brushType.name)
                put("color", stroke.color)
                put("baseWidth", stroke.baseWidth.toDouble())
                put("opacity", stroke.opacity.toDouble())
                put("smoothing", stroke.smoothing.toDouble())
                put("isDeleted", stroke.isDeleted)
                put("isMasked", stroke.isMasked)

                val pointsArray = JSONArray()
                for (p in stroke.points) {
                    val pObj = JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("p", p.pressure.toDouble())
                        if (p.tiltAngle != 0f) put("ta", p.tiltAngle.toDouble())
                        if (p.orientation != 0f) put("or", p.orientation.toDouble())
                    }
                    pointsArray.put(pObj)
                }
                put("points", pointsArray)
            }
            strokesArray.put(sObj)
        }

        // 4. Viewport and Grid
        val root = JSONObject().apply {
            put("version", 1)
            put("projectId", projectId)
            put("gridType", data.gridType.name)
            put("panX", data.viewport.panX.toDouble())
            put("panY", data.viewport.panY.toDouble())
            put("zoom", data.viewport.zoom.toDouble())
            put("rotationDeg", data.viewport.rotationDeg.toDouble())
            put("layers", layersArray)
            put("strokes", strokesArray)
            put("images", imageJsonArray)
        }

        val targetFile = File(projectDir, "drawing.appdraw")
        targetFile.writeText(root.toString())
    }

    fun loadDrawingFile(context: Context, projectId: String): ProjectDrawingData? {
        val targetFile = File(getProjectDir(context, projectId), "drawing.appdraw")
        if (!targetFile.exists()) return null

        return try {
            val text = targetFile.readText()
            val root = JSONObject(text)

            val gridType = try {
                GridType.valueOf(root.optString("gridType", "DOT"))
            } catch (e: Exception) {
                GridType.DOT
            }

            val viewport = ViewportState(
                panX = root.optDouble("panX", 0.0).toFloat(),
                panY = root.optDouble("panY", 0.0).toFloat(),
                zoom = root.optDouble("zoom", 1.0).toFloat(),
                rotationDeg = root.optDouble("rotationDeg", 0.0).toFloat()
            )

            // Layers
            val layers = mutableListOf<CanvasLayer>()
            val layersArray = root.optJSONArray("layers")
            if (layersArray != null) {
                for (i in 0 until layersArray.length()) {
                    val lObj = layersArray.getJSONObject(i)
                    layers.add(
                        CanvasLayer(
                            id = lObj.getString("id"),
                            name = lObj.getString("name"),
                            isVisible = lObj.optBoolean("isVisible", true),
                            isLocked = lObj.optBoolean("isLocked", false),
                            opacity = lObj.optDouble("opacity", 1.0).toFloat(),
                            orderIndex = lObj.optInt("orderIndex", i)
                        )
                    )
                }
            }

            // Strokes
            val strokes = mutableListOf<VectorStroke>()
            val strokesArray = root.optJSONArray("strokes")
            if (strokesArray != null) {
                for (i in 0 until strokesArray.length()) {
                    val sObj = strokesArray.getJSONObject(i)
                    val pts = mutableListOf<RawPoint>()
                    val pArray = sObj.optJSONArray("points")
                    if (pArray != null) {
                        for (j in 0 until pArray.length()) {
                            val pObj = pArray.getJSONObject(j)
                            pts.add(
                                RawPoint(
                                    x = pObj.getDouble("x").toFloat(),
                                    y = pObj.getDouble("y").toFloat(),
                                    pressure = pObj.optDouble("p", 0.5).toFloat(),
                                    tiltAngle = pObj.optDouble("ta", 0.0).toFloat(),
                                    orientation = pObj.optDouble("or", 0.0).toFloat()
                                )
                            )
                        }
                    }

                    val brushType = try {
                        BrushType.valueOf(sObj.optString("brushType", "PEN"))
                    } catch (e: Exception) {
                        BrushType.PEN
                    }

                    strokes.add(
                        VectorStroke(
                            id = sObj.getString("id"),
                            layerId = sObj.getString("layerId"),
                            brushType = brushType,
                            color = sObj.getInt("color"),
                            baseWidth = sObj.optDouble("baseWidth", 3.0).toFloat(),
                            opacity = sObj.optDouble("opacity", 1.0).toFloat(),
                            smoothing = sObj.optDouble("smoothing", 0.5).toFloat(),
                            points = pts,
                            bounds = VectorStroke.calculateBounds(pts),
                            isDeleted = sObj.optBoolean("isDeleted", false),
                            isMasked = sObj.optBoolean("isMasked", false)
                        )
                    )
                }
            }

            // Images
            val images = mutableListOf<CanvasImageElement>()
            val imagesDir = getImagesDir(context, projectId)
            val imgArray = root.optJSONArray("images")
            if (imgArray != null) {
                for (i in 0 until imgArray.length()) {
                    val imgObj = imgArray.getJSONObject(i)
                    val imgId = imgObj.getString("id")
                    val imgFile = File(imagesDir, "$imgId.png")
                    val bmp = if (imgFile.exists()) {
                        BitmapFactory.decodeFile(imgFile.absolutePath)
                    } else null

                    if (bmp != null) {
                        images.add(
                            CanvasImageElement(
                                id = imgId,
                                layerId = imgObj.getString("layerId"),
                                title = imgObj.optString("title", "Imported Image"),
                                bitmap = bmp,
                                worldX = imgObj.optDouble("worldX", 0.0).toFloat(),
                                worldY = imgObj.optDouble("worldY", 0.0).toFloat(),
                                width = imgObj.optDouble("width", bmp.width.toDouble()).toFloat(),
                                height = imgObj.optDouble("height", bmp.height.toDouble()).toFloat(),
                                rotationDeg = imgObj.optDouble("rotationDeg", 0.0).toFloat(),
                                scaleX = imgObj.optDouble("scaleX", 1.0).toFloat(),
                                scaleY = imgObj.optDouble("scaleY", 1.0).toFloat(),
                                isMirroredH = imgObj.optBoolean("isMirroredH", false),
                                isMirroredV = imgObj.optBoolean("isMirroredV", false),
                                opacity = imgObj.optDouble("opacity", 1.0).toFloat()
                            )
                        )
                    }
                }
            }

            ProjectDrawingData(
                layers = if (layers.isNotEmpty()) layers else listOf(
                    CanvasLayer(id = UUID.randomUUID().toString(), name = "Sketches", orderIndex = 0)
                ),
                strokes = strokes,
                images = images,
                viewport = viewport,
                gridType = gridType
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteProjectFiles(context: Context, projectId: String) {
        val dir = getProjectDir(context, projectId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    /**
     * Renders a small, high-quality thumbnail preview bitmap and encodes to Base64.
     */
    fun generateThumbnailBase64(
        strokes: List<VectorStroke>,
        images: List<CanvasImageElement>,
        width: Int = 300,
        height: Int = 200
    ): String {
        val thumbnailBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumbnailBitmap)

        // Dark matte background matching the Concepts canvas
        canvas.drawColor(Color.parseColor("#15161C"))

        // Determine bounding box of all strokes & images
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        val activeStrokes = strokes.filter { !it.isDeleted && !it.isMasked }
        for (s in activeStrokes) {
            if (s.bounds.left < minX) minX = s.bounds.left
            if (s.bounds.top < minY) minY = s.bounds.top
            if (s.bounds.right > maxX) maxX = s.bounds.right
            if (s.bounds.bottom > maxY) maxY = s.bounds.bottom
        }

        for (img in images) {
            val b = img.bounds
            if (b.left < minX) minX = b.left
            if (b.top < minY) minY = b.top
            if (b.right > maxX) maxX = b.right
            if (b.bottom > maxY) maxY = b.bottom
        }

        if (minX < maxX && minY < maxY) {
            val contentW = (maxX - minX).coerceAtLeast(10f)
            val contentH = (maxY - minY).coerceAtLeast(10f)
            val pad = 24f
            val scale = Math.min((width - pad * 2) / contentW, (height - pad * 2) / contentH).coerceIn(0.01f, 10f)

            canvas.save()
            canvas.translate(width / 2f, height / 2f)
            canvas.scale(scale, scale)
            canvas.translate(-(minX + maxX) / 2f, -(minY + maxY) / 2f)

            // Draw images
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for (img in images) {
                if (img.bitmap != null && !img.bitmap.isRecycled) {
                    canvas.save()
                    canvas.translate(img.worldX, img.worldY)
                    canvas.rotate(img.rotationDeg)
                    canvas.scale(img.scaleX, img.scaleY)
                    imgPaint.alpha = (img.opacity * 255f).toInt()
                    canvas.drawBitmap(img.bitmap, -img.width / 2f, -img.height / 2f, imgPaint)
                    canvas.restore()
                }
            }

            // Draw strokes
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (s in activeStrokes) {
                if (s.points.size < 2) continue
                strokePaint.color = s.color
                strokePaint.strokeWidth = s.baseWidth
                val path = Path()
                path.moveTo(s.points[0].x, s.points[0].y)
                for (i in 1 until s.points.size) {
                    val p0 = s.points[i - 1]
                    val p1 = s.points[i]
                    path.quadTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                }
                path.lineTo(s.points.last().x, s.points.last().y)
                canvas.drawPath(path, strokePaint)
            }

            canvas.restore()
        } else {
            // Draw subtle blank canvas grid placeholder
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2A2E3D")
            }
            var x = 20f
            while (x < width) {
                var y = 20f
                while (y < height) {
                    canvas.drawCircle(x, y, 1.5f, dotPaint)
                    y += 30f
                }
                x += 30f
            }
        }

        val stream = ByteArrayOutputStream()
        thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        thumbnailBitmap.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
