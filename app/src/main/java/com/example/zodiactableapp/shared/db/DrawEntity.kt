package com.example.zodiactableapp.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.zodiactableapp.model.Zodiac

@Entity(tableName = "draws")
data class DrawEntity(
    @PrimaryKey val dateKey: String,   // yyyy-MM-dd
    val zodiac: Zodiac                 // Converters 负责存成 String
)