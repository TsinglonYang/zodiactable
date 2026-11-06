package com.example.zodiactableapp.shared.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 一单 15 行，每行一个 groupId + 你自定义的 initLevel（用于激活当日的等级）
 */
@Entity(
    tableName = "order_items",
    primaryKeys = ["orderId", "pos"],
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class OrderItemEntity(
    val orderId: Long,
    val pos: Int,       // 1..15
    val groupId: Int,   // 0..494
    val initLevel: Int  // 1..9
)