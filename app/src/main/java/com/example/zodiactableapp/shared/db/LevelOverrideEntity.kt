package com.example.zodiactableapp.shared.db

import androidx.room.Entity

/**
 * 指定日期对某个 groupId 的等级进行“覆盖”
 * 该覆盖在当天计算后生效，并影响后续日期的推演起点
 */
@Entity(
    tableName = "level_override",
    primaryKeys = ["dateKey", "groupId"]
)
data class LevelOverrideEntity(
    val dateKey: String,
    val groupId: Int,
    val level: Int
)