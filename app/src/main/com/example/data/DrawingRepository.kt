package com.example.data

import android.content.Context
import com.example.model.CanvasLayer
import com.example.model.GridType
import com.example.model.ViewportState
import com.example.util.SampleMediaGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DrawingRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.drawingDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allProjects: Flow<List<DrawingProjectEntity>> = dao.getAllProjectsFlow()

    suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
        dao.getProjectCount()
    }

    suspend fun getProjectById(id: String): DrawingProjectEntity? = withContext(Dispatchers.IO) {
        dao.getProjectById(id)
    }

    suspend fun createNewProject(customTitle: String? = null): DrawingProjectEntity = withContext(Dispatchers.IO) {
        val count = dao.getProjectCount()
        val now = System.currentTimeMillis()
        val title = if (!customTitle.isNullOrBlank()) {
            customTitle.trim()
        } else {
            "Untitled Drawing ${count + 1}"
        }
        val id = UUID.randomUUID().toString()

        val entity = DrawingProjectEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            strokeCount = 0,
            layerCount = 1,
            imageCount = 0,
            viewportPanX = 0f,
            viewportPanY = 0f,
            viewportZoom = 1.0f,
            viewportRotationDeg = 0f,
            gridType = GridType.DOT.name
        )

        dao.insertOrUpdate(entity)

        // Initialize blank drawing file
        val initialData = ProjectDrawingData(
            layers = listOf(
                CanvasLayer(id = UUID.randomUUID().toString(), name = "Sketches", orderIndex = 0)
            ),
            strokes = emptyList(),
            images = emptyList(),
            viewport = ViewportState(),
            gridType = GridType.DOT
        )
        DrawingStorage.saveDrawingFile(context, id, initialData)

        entity
    }

    suspend fun createInitialSampleProjectIfNeeded() = withContext(Dispatchers.IO) {
        if (dao.getProjectCount() == 0) {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val sampleBmp = SampleMediaGenerator.createSampleBlueprintBitmap()
            val sampleImg = com.example.model.CanvasImageElement(
                id = UUID.randomUUID().toString(),
                layerId = "layer_blueprint",
                title = "Elevation Schematic A-1",
                bitmap = sampleBmp,
                worldX = 0f,
                worldY = 0f,
                width = 800f,
                height = 560f
            )

            val sampleData = ProjectDrawingData(
                layers = listOf(
                    CanvasLayer(id = "layer_blueprint", name = "Architectural Elevation", orderIndex = 0),
                    CanvasLayer(id = UUID.randomUUID().toString(), name = "Notes & Annotations", orderIndex = 1)
                ),
                strokes = emptyList(),
                images = listOf(sampleImg),
                viewport = ViewportState(),
                gridType = GridType.DOT
            )

            val thumb = DrawingStorage.generateThumbnailBase64(emptyList(), listOf(sampleImg))
            DrawingStorage.saveDrawingFile(context, id, sampleData)

            val entity = DrawingProjectEntity(
                id = id,
                title = "Elevation Schematic A-1",
                createdAt = now,
                updatedAt = now,
                thumbnailBase64 = thumb,
                strokeCount = 0,
                layerCount = 2,
                imageCount = 1,
                viewportPanX = 0f,
                viewportPanY = 0f,
                viewportZoom = 1.0f,
                viewportRotationDeg = 0f,
                gridType = GridType.DOT.name
            )
            dao.insertOrUpdate(entity)
        }
    }

    suspend fun seedSampleProjectIfNeeded() = createInitialSampleProjectIfNeeded()

    suspend fun loadProjectData(projectId: String): ProjectDrawingData? = withContext(Dispatchers.IO) {
        DrawingStorage.loadDrawingFile(context, projectId)
    }

    suspend fun loadProjectDrawingData(projectId: String): ProjectDrawingData = withContext(Dispatchers.IO) {
        DrawingStorage.loadDrawingFile(context, projectId) ?: ProjectDrawingData(
            layers = listOf(
                CanvasLayer(id = UUID.randomUUID().toString(), name = "Sketches", orderIndex = 0)
            ),
            strokes = emptyList(),
            images = emptyList(),
            viewport = ViewportState(),
            gridType = GridType.DOT
        )
    }

    fun autoSaveProjectAsync(
        projectId: String,
        title: String,
        data: ProjectDrawingData
    ) {
        repositoryScope.launch {
            try {
                // 1. Save data file
                DrawingStorage.saveDrawingFile(context, projectId, data)

                // 2. Generate thumbnail preview
                val thumb = DrawingStorage.generateThumbnailBase64(data.strokes, data.images)

                // 3. Update Room entity
                val existing = dao.getProjectById(projectId)
                val entity = DrawingProjectEntity(
                    id = projectId,
                    title = title,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    thumbnailBase64 = thumb,
                    strokeCount = data.strokes.count { !it.isDeleted },
                    layerCount = data.layers.size,
                    imageCount = data.images.size,
                    viewportPanX = data.viewport.panX,
                    viewportPanY = data.viewport.panY,
                    viewportZoom = data.viewport.zoom,
                    viewportRotationDeg = data.viewport.rotationDeg,
                    gridType = data.gridType.name
                )
                dao.insertOrUpdate(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        dao.deleteProject(projectId)
        DrawingStorage.deleteProjectFiles(context, projectId)
    }

    companion object {
        @Volatile
        private var INSTANCE: DrawingRepository? = null

        fun getInstance(context: Context): DrawingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DrawingRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
