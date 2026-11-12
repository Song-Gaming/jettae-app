package com.example.hyundai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VisitDao {

    @Insert
    suspend fun insert(visit: VisitEntity): Long

    @Insert
    suspend fun insertAll(visits: List<VisitEntity>)

    @Query("UPDATE visits SET departedAtMillis = :departedAtMillis WHERE id = :id")
    suspend fun setDeparted(id: Long, departedAtMillis: Long)

    @Query("SELECT * FROM visits WHERE arrivedAtMillis >= :sinceMillis ORDER BY arrivedAtMillis")
    suspend fun since(sinceMillis: Long): List<VisitEntity>

    @Query("DELETE FROM visits WHERE arrivedAtMillis < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long)

    @Query("DELETE FROM visits")
    suspend fun deleteAll()
}

@Dao
interface PlaceLabelDao {

    @Query("SELECT * FROM place_labels")
    suspend fun all(): List<PlaceLabelEntity>

    @Upsert
    suspend fun upsert(label: PlaceLabelEntity)

    @Query("DELETE FROM place_labels WHERE placeKey = :placeKey")
    suspend fun delete(placeKey: String)

    @Query("DELETE FROM place_labels")
    suspend fun deleteAll()
}
