package com.example.zodiactableapp.shared.db

import androidx.room.TypeConverter
import com.example.zodiactableapp.model.Zodiac

class Converters {

    @TypeConverter
    fun zodiacToString(z: Zodiac?): String? = z?.name

    @TypeConverter
    fun stringToZodiac(s: String?): Zodiac? = s?.let { Zodiac.valueOf(it) }
}