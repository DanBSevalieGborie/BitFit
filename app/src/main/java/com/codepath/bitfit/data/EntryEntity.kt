package com.codepath.bitfit.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One BitFit log entry. A user can log one (or several) entries per day.
 *
 * Metrics tracked:
 *  - food + calories (nutrition)
 *  - water (cups)
 *  - sleep (hours)
 *  - mood (1..5)
 *
 * Photos are NOT stored in the database (too big) — we store the file path instead.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Day of the entry stored as LocalDate.toEpochDay() so it sorts and groups easily. */
    @ColumnInfo(name = "date_epoch_day") val epochDay: Long,
    @ColumnInfo(name = "food_name") val foodName: String,
    @ColumnInfo(name = "calories") val calories: Int,
    @ColumnInfo(name = "water_cups") val waterCups: Int = 0,
    /** null = not logged */
    @ColumnInfo(name = "sleep_hours") val sleepHours: Float? = null,
    /** 1 (awful) .. 5 (great), null = not logged */
    @ColumnInfo(name = "mood") val mood: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    /** Absolute path of the daily photo inside the app's private storage. */
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
