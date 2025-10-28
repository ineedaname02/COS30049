package com.example.myPlant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myPlant.data.local.ObservationDao
import com.example.myPlant.data.model.Observation

@Database(entities = [Observation::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // This abstract function returns the DAO. Room will generate the implementation.
    abstract fun observationDao(): ObservationDao

    // Companion object allows us to create a single instance of the database (Singleton pattern).
    // This is crucial to prevent memory leaks and performance issues.
    companion object {
        // @Volatile ensures that the INSTANCE variable is always up-to-date and the same
        // to all execution threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Return the existing instance if it's not null.
            // If it is null, create the database in a synchronized block.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plant_database" // The name of your database file.
                )
                    // We'll add migration strategies here in the future if we change the schema.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}