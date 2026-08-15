package com.example.zodiactableapp.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * groupId 第一次出现在任何大单中时，你给它的自定义初始等级 + 激活日期
 */
@Entity(tableName = "group_init")
data class GroupInitEntity(
    @PrimaryKey val groupId: Int,      // 0..494
    val activationDateKey: String,     // yyyy-MM-dd
    val initLevel: Int                 // 1..15
)