package com.codepath.bitfit.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlin.concurrent.thread

@Database(entities = [EntryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    companion object {
        private const val DB_NAME = "bitfit-db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }

        /** Lets tests start each run with a fresh database. */
        @VisibleForTesting
        fun resetForTests(context: Context) {
            val db = getInstance(context)
            // Room forbids clearing tables on the main thread
            thread { db.clearAllTables() }.join()
        }
    }
}
