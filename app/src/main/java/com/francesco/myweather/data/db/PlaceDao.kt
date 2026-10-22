package com.francesco.myweather.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SavedPlace>>

    @Insert
    suspend fun insert(place: SavedPlace): Long

    @Delete
    suspend fun delete(place: SavedPlace)

    @Update
    suspend fun updateAll(places: List<SavedPlace>)
}
