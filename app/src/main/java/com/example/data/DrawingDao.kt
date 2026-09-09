package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawing_projects ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<DrawingProjectEntity>>

    @Query("SELECT * FROM drawing_projects WHERE id = :id")
    suspend fun getProjectById(id: String): DrawingProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: DrawingProjectEntity)

    @Query("DELETE FROM drawing_projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Query("SELECT COUNT(*) FROM drawing_projects")
    suspend fun getProjectCount(): Int
}
