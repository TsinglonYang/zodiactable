package com.example.zodiactableapp.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DrawDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DrawEntity)

    @Query("SELECT * FROM draws WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDate(dateKey: String): DrawEntity?

    @Query("SELECT * FROM draws ORDER BY dateKey ASC")
    suspend fun getAll(): List<DrawEntity>

    @Query("DELETE FROM draws")
    suspend fun clear()
}