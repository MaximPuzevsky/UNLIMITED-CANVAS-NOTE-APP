package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.model.BrushType
import com.example.model.CanvasImageElement
import com.example.model.CanvasLayer
import com.example.model.CanvasTextBlock
import com.example.model.GridType
import com.example.model.RawPoint
import com.example.model.ToolSlot
import com.example.model.VectorStroke
import com.example.model.ViewportState
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Data container for complete .concept file import/export.
 */
data class ConceptFilePayload(
    val projectTitle: String,
    val drawingData: ProjectDrawingData,
    val toolSlots: List<ToolSlot> = emptyList(),
    val activeSlotIndex: Int = 0
)

/**
 * Custom .concept File Engine
 * Handles serialization, packaging, file picking, and system sharing for Concepts vector projects.
 */
object ConceptFileManager {

    const val CONCEPT_EXTENSION = ".concept"
    const val CONCEPT_MIME_TYPE = "application/octet-stream"

    /**
     * Serializes complete canvas state, tools, and vector assets into clean .concept JSON.
     */
    fun serializeToConceptJson(payload: ConceptFilePayload): String {
        val root = JSONObject()
        root.put("format", "concepts-canvas")
        root.put("extension", CONCEPT_EXTENSION)
        root.put("version", 1)
        root.put("generator", "Concepts S Pen Infinite Vector Engine")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("projectTitle", payload.projectTitle)

        // 1. Canvas Environment (Grid, Background, Viewport)
        val data = payload.drawingData
        root.put("gridType", data.gridType.name)
        root.put("backgroundColor", data.backgroundColor)

        val vpObj = JSONObject().apply {
            put("panX", data.viewport.panX.toDouble())
            put("panY", data.viewport.panY.toDouble())
            put("zoom", data.viewport.zoom.toDouble())
            put("rotationDeg", data.viewport.rotationDeg.toDouble())
            put("angleSnapping", data.viewport.angleSnapping)
        }
        root.put("viewport", vpObj)

        // 2. Layers
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
        root.put("layers", layersArray)

        // 3. Vector Strokes
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
        root.put("strokes", strokesArray)

        // 4. Text Blocks
        val textArray = JSONArray()
        for (tb in data.textBlocks) {
            val tbObj = JSONObject().apply {
                put("id", tb.id)
                put("layerId", tb.layerId)
                put("text", tb.text)
                put("worldX", tb.worldX.toDouble())
                put("worldY", tb.worldY.toDouble())
                put("fontSize", tb.fontSize.toDouble())
                put("color", tb.color)
                put("rotationDeg", tb.rotationDeg.toDouble())
                put("isDeleted", tb.isDeleted)
            }
            textArray.put(tbObj)
        }
        root.put("textBlocks", textArray)

        // 5. Embedded Images (Encoded as compressed PNG Base64 strings for full cross-device portability)
        val imagesArray = JSONArray()
        for (img in data.images) {
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

                // Encode bitmap bytes
                if (img.bitmap != null && !img.bitmap.isRecycled) {
                    try {
                        val stream = ByteArrayOutputStream()
                        // Downscale high-res images slightly if over 1600px to ensure lightweight transfer
                        val maxDim = maxOf(img.bitmap.width, img.bitmap.height)
                        val exportBmp = if (maxDim > 1600) {
                            val ratio = 1600f / maxDim
                            Bitmap.createScaledBitmap(
                                img.bitmap,
                                (img.bitmap.width * ratio).toInt(),
                                (img.bitmap.height * ratio).toInt(),
                                true
                            )
                        } else {
                            img.bitmap
                        }
                        exportBmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        put("base64Png", base64)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            imagesArray.put(imgObj)
        }
        root.put("images", imagesArray)

        // 6. Tool slots configuration
        if (payload.toolSlots.isNotEmpty()) {
            val toolsArray = JSONArray()
            for (slot in payload.toolSlots) {
                val tObj = JSONObject().apply {
                    put("id", slot.id)
                    put("brushType", slot.brushType.name)
                    put("color", slot.color)
                    put("strokeWidth", slot.strokeWidth.toDouble())
                    put("opacity", slot.opacity.toDouble())
                    put("smoothing", slot.smoothing.toDouble())
                }
                toolsArray.put(tObj)
            }
            root.put("toolSlots", toolsArray)
            root.put("activeSlotIndex", payload.activeSlotIndex)
        }

        return root.toString(2)
    }

    /**
     * Parses .concept JSON format and decodes embedded vector geometry and image assets.
     */
    fun deserializeFromConceptJson(jsonText: String): ConceptFilePayload {
        val root = JSONObject(jsonText)
        val projectTitle = root.optString("projectTitle", "Imported Project")

        val gridType = try {
            GridType.valueOf(root.optString("gridType", "DOT"))
        } catch (e: Exception) {
            GridType.DOT
        }

        val bgColor = root.optInt("backgroundColor", Color.parseColor("#15161C"))

        val vpObj = root.optJSONObject("viewport")
        val viewport = if (vpObj != null) {
            ViewportState(
                panX = vpObj.optDouble("panX", 0.0).toFloat(),
                panY = vpObj.optDouble("panY", 0.0).toFloat(),
                zoom = vpObj.optDouble("zoom", 1.0).toFloat(),
                rotationDeg = vpObj.optDouble("rotationDeg", 0.0).toFloat(),
                angleSnapping = vpObj.optBoolean("angleSnapping", true)
            )
        } else {
            ViewportState(
                panX = root.optDouble("panX", 0.0).toFloat(),
                panY = root.optDouble("panY", 0.0).toFloat(),
                zoom = root.optDouble("zoom", 1.0).toFloat(),
                rotationDeg = root.optDouble("rotationDeg", 0.0).toFloat(),
                angleSnapping = root.optBoolean("angleSnapping", true)
            )
        }

        // 1. Layers
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
        if (layers.isEmpty()) {
            layers.add(CanvasLayer(id = UUID.randomUUID().toString(), name = "Sketches", orderIndex = 0))
        }

        // 2. Strokes
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
                        id = sObj.optString("id", UUID.randomUUID().toString()),
                        layerId = sObj.optString("layerId", layers.first().id),
                        brushType = brushType,
                        color = sObj.optInt("color", Color.WHITE),
                        baseWidth = sObj.optDouble("baseWidth", 3.0).toFloat(),
                        opacity = sObj.optDouble("opacity", 1.0).toFloat(),
                        smoothing = sObj.optDouble("smoothing", 0.45).toFloat(),
                        points = pts,
                        bounds = VectorStroke.calculateBounds(pts),
                        isDeleted = sObj.optBoolean("isDeleted", false),
                        isMasked = sObj.optBoolean("isMasked", false)
                    )
                )
            }
        }

        // 3. Text Blocks
        val textBlocks = mutableListOf<CanvasTextBlock>()
        val tbArray = root.optJSONArray("textBlocks")
        if (tbArray != null) {
            for (i in 0 until tbArray.length()) {
                val tbObj = tbArray.getJSONObject(i)
                textBlocks.add(
                    CanvasTextBlock(
                        id = tbObj.optString("id", UUID.randomUUID().toString()),
                        layerId = tbObj.optString("layerId", layers.first().id),
                        text = tbObj.getString("text"),
                        worldX = tbObj.optDouble("worldX", 0.0).toFloat(),
                        worldY = tbObj.optDouble("worldY", 0.0).toFloat(),
                        fontSize = tbObj.optDouble("fontSize", 22.0).toFloat(),
                        color = tbObj.optInt("color", Color.WHITE),
                        rotationDeg = tbObj.optDouble("rotationDeg", 0.0).toFloat(),
                        isDeleted = tbObj.optBoolean("isDeleted", false)
                    )
                )
            }
        }

        // 4. Images
        val images = mutableListOf<CanvasImageElement>()
        val imgArray = root.optJSONArray("images")
        if (imgArray != null) {
            for (i in 0 until imgArray.length()) {
                val imgObj = imgArray.getJSONObject(i)
                val base64 = imgObj.optString("base64Png", "")
                val bmp = if (base64.isNotEmpty()) {
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                if (bmp != null) {
                    images.add(
                        CanvasImageElement(
                            id = imgObj.optString("id", UUID.randomUUID().toString()),
                            layerId = imgObj.optString("layerId", layers.first().id),
                            title = imgObj.optString("title", "Imported Media"),
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

        // 5. Tool Slots
        val toolSlots = mutableListOf<ToolSlot>()
        val toolsArray = root.optJSONArray("toolSlots")
        if (toolsArray != null) {
            for (i in 0 until toolsArray.length()) {
                val tObj = toolsArray.getJSONObject(i)
                val brush = try {
                    BrushType.valueOf(tObj.getString("brushType"))
                } catch (e: Exception) {
                    BrushType.PEN
                }
                toolSlots.add(
                    ToolSlot(
                        id = tObj.getInt("id"),
                        brushType = brush,
                        color = tObj.getInt("color"),
                        strokeWidth = tObj.getDouble("strokeWidth").toFloat(),
                        opacity = tObj.getDouble("opacity").toFloat(),
                        smoothing = tObj.getDouble("smoothing").toFloat()
                    )
                )
            }
        }
        val activeSlotIndex = root.optInt("activeSlotIndex", 0)

        val drawingData = ProjectDrawingData(
            layers = layers,
            strokes = strokes,
            images = images,
            textBlocks = textBlocks,
            viewport = viewport,
            gridType = gridType,
            backgroundColor = bgColor
        )

        return ConceptFilePayload(
            projectTitle = projectTitle,
            drawingData = drawingData,
            toolSlots = toolSlots,
            activeSlotIndex = activeSlotIndex
        )
    }

    /**
     * Writes .concept JSON to app cache and provides File ready for sharing.
     */
    fun saveConceptFileToCache(context: Context, rawTitle: String, jsonContent: String): File {
        val cacheDir = File(context.cacheDir, "shared_concepts")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val sanitizedTitle = rawTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(30)
        val file = File(cacheDir, "${sanitizedTitle}${CONCEPT_EXTENSION}")
        FileOutputStream(file).use { it.write(jsonContent.toByteArray(Charsets.UTF_8)) }
        return file
    }

    /**
     * Launches Android Share Sheet with the .concept file.
     */
    fun shareConceptFile(context: Context, file: File, title: String) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "$title (Concepts Infinite Drawing)")
            putExtra(Intent.EXTRA_TEXT, "Opening $title in Concepts format with full vector strokes and layers.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share .concept Project File")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Writes .concept JSON to an Android Storage Access Framework (SAF) Uri.
     */
    fun writeConceptToUri(context: Context, uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads .concept JSON from an Android Storage Access Framework (SAF) Uri.
     */
    fun readConceptFromUri(context: Context, uri: Uri): ConceptFilePayload? {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null
            deserializeFromConceptJson(content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deserializeConceptPayload(jsonText: String): ConceptFilePayload {
        return deserializeFromConceptJson(jsonText)
    }

    fun exportToFile(context: Context, payload: ConceptFilePayload): File {
        val json = serializeToConceptJson(payload)
        return saveConceptFileToCache(context, payload.projectTitle, json)
    }

    fun createShareIntent(context: Context, payload: ConceptFilePayload): Intent {
        val file = exportToFile(context, payload)
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "${payload.projectTitle} (.concept file)")
            putExtra(Intent.EXTRA_TEXT, "Opening ${payload.projectTitle} in Concepts infinite canvas.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
