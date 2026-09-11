package com.volxsy.vastypr.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Skill: android-room-database — entity tipis, migrasi eksplisit, schema export aktif.
// Project = 1 gambar tall (720x16000+) + daftar layer (JSON di ву file terpisah MVP-1).
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val width: Int,
    val height: Int,
    val sourceUri: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Insert
    suspend fun insert(e: ProjectEntity): Long

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}
