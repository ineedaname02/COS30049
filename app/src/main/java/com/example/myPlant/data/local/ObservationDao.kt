package com.example.myPlant.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myPlant.data.model.Observation

@Dao
interface ObservationDao {

    /**
     * Inserts a list of observations into the database. If an observation
     * with the same primary key already exists, it will be replaced.
     * This is perfect for refreshing the cache.
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(observations: List<Observation>)

    /**
     * Fetches all observations from the database, ordered by timestamp descending.
     * Returns LiveData, so the UI will automatically update when the data changes.
     */
    @Query("SELECT * FROM observations ORDER BY timestamp DESC")
    fun getAllObservations(): LiveData<List<Observation>>

    /**
     * Fetches all observations for a specific user, ordered by timestamp descending.
     * Also returns LiveData for automatic UI updates.
     */
    @Query("SELECT * FROM observations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getUserObservations(userId: String): LiveData<List<Observation>>

    /**
     * Deletes all observations from the 'observations' table.
     * We'll use this to clear the cache before inserting fresh data.
     */
    @Query("DELETE FROM observations")
    suspend fun clearAll()

    /**
     * Deletes all observations for a specific user.
     * Useful for clearing just the user's cache.
     */
    @Query("DELETE FROM observations WHERE userId = :userId")
    suspend fun clearUserObservations(userId: String)
}