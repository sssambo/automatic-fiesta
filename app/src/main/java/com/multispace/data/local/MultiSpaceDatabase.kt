package com.multispace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProfileEntity::class], version = 1, exportSchema = false)
abstract class MultiSpaceDatabase : RoomDatabase() {
    
    abstract fun profileDao(): ProfileDao
    
    companion object {
        @Volatile
        private var INSTANCE: MultiSpaceDatabase? = null
        
        fun getInstance(context: Context): MultiSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MultiSpaceDatabase::class.java,
                    "multispace_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
