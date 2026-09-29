package com.codepath.bitfit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    /** Emits a new list every time the table changes — the UI's single source of truth. */
    @Query("SELECT * FROM entries ORDER BY date_epoch_day DESC, created_at DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries ORDER BY date_epoch_day DESC, created_at DESC")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE date_epoch_day = :epochDay")
    suspend fun countForDay(epochDay: Long): Int

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}
