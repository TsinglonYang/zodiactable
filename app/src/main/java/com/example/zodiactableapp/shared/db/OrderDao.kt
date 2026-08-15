package com.example.zodiactableapp.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<OrderItemEntity>)

    @Transaction
    @Query("SELECT * FROM orders ORDER BY orderNo ASC")
    suspend fun getAllWithItems(): List<OrderWithItems>

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItemsOfOrder(orderId: Long)

    @Query("UPDATE orders SET orderNo = :newOrderNo WHERE id = :orderId")
    suspend fun updateOrderNo(orderId: Long, newOrderNo: Int)

    @Query("UPDATE order_items SET groupId = :groupId, initLevel = :initLevel WHERE orderId = :orderId AND pos = :pos")
    suspend fun updateItem(orderId: Long, pos: Int, groupId: Int, initLevel: Int)

    @Query("DELETE FROM orders")
    suspend fun clearAll()
}