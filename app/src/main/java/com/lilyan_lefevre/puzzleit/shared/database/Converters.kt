package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.TypeConverter

/**
 * Type converters for Room database
 */
class Converters {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): java.util.Date? {
        return value?.let { java.util.Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: java.util.Date?): Long? {
        return date?.time
    }
}
