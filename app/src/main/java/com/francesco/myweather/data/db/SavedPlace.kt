package com.francesco.myweather.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
)
