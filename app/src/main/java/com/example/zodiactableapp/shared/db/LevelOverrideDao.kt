package com.example.zodiactableapp.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LevelOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LevelOverrideEntity)

    @Query("SELECT * FROM level_override")
    suspend fun getAll(): List<LevelOverrideEntity>

    @Query("DELETE FROM level_override WHERE dateKey = :dateKey AND groupId = :groupId")
    suspend fun deleteOne(dateKey: String, groupId: Int)

    @Query("DELETE FROM level_override WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: Int)
}
