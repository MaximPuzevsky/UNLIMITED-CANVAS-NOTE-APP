package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawing_projects")
data class DrawingProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailBase64: String? = null,
    val strokeCount: Int = 0,
    val layerCount: Int = 1,
    val imageCount: Int = 0,
    val viewportPanX: Float = 0f,
    val viewportPanY: Float = 0f,
    val viewportZoom: Float = 1.0f,
    val viewportRotationDeg: Float = 0f,
    val gridType: String = "DOT"
)
