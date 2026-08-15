package com.example.zodiactableapp.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GroupInitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupInitEntity)

    @Query("SELECT * FROM group_init")
    suspend fun getAll(): List<GroupInitEntity>

    @Query("SELECT * FROM group_init WHERE groupId = :groupId LIMIT 1")
    suspend fun get(groupId: Int): GroupInitEntity?

    @Query("DELETE FROM group_init WHERE groupId = :groupId")
    suspend fun delete(groupId: Int)

    @Query("DELETE FROM group_init")
    suspend fun clear()
}