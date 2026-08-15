package com.example.zodiactableapp.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Long,
    val orderNo: Int,
    val createdDateKey: String
)