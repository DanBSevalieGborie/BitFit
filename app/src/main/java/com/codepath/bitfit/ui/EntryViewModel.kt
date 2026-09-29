package com.codepath.bitfit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.codepath.bitfit.BitFitApplication
import com.codepath.bitfit.data.EntryEntity
import com.codepath.bitfit.util.Formatters
import com.codepath.bitfit.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Holds the create/edit form state so it survives rotation (and process death via SavedStateHandle).
 */
class EntryViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {

    private val dao = (app as BitFitApplication).db.entryDao()

    val epochDay: StateFlow<Long> = state.getStateFlow(KEY_DAY, Formatters.today())
    val photoPath: StateFlow<String?> = state.getStateFlow(KEY_PHOTO, null)

    var pendingCameraPath: String?
        get() = state[KEY_PENDING]
        set(value) { state[KEY_PENDING] = value }

    /** True once the form has been filled from the database (so rotation doesn't overwrite edits). */
    var populated: Boolean
        get() = state[KEY_POPULATED] ?: false
        set(value) { state[KEY_POPULATED] = value }

    var existing: EntryEntity? = null
        private set

    private var saved = false

    fun setDay(day: Long) { state[KEY_DAY] = day }

    fun setPhoto(path: String?) {
        val previous = photoPath.value
        // Clean up a photo that was taken in this session but replaced before saving
        if (previous != null && previous != existing?.photoPath && previous != path) {
            PhotoStorage.delete(previous)
        }
        state[KEY_PHOTO] = path
    }

    suspend fun load(id: Long): EntryEntity? {
        if (id <= 0) return null
        val entry = withContext(Dispatchers.IO) { dao.getById(id) }
        existing = entry
        if (entry != null && !populated) {
            state[KEY_DAY] = entry.epochDay
            state[KEY_PHOTO] = entry.photoPath
        }
        return entry
    }

    /** Food name → calories for previously logged foods (powers autocomplete). */
    suspend fun recentFoods(): Map<String, Int> = withContext(Dispatchers.IO) {
        dao.getAll().distinctBy { it.foodName.lowercase() }.associate { it.foodName to it.calories }
    }

    suspend fun save(
        foodName: String,
        calories: Int,
        waterCups: Int,
        sleepHours: Float?,
        mood: Int?,
        notes: String?,
    ) = withContext(Dispatchers.IO) {
        val current = existing
        val entry = EntryEntity(
            id = current?.id ?: 0,
            epochDay = epochDay.value,
            foodName = foodName,
            calories = calories,
            waterCups = waterCups,
            sleepHours = sleepHours,
            mood = mood,
            notes = notes,
            photoPath = photoPath.value,
            createdAt = current?.createdAt ?: System.currentTimeMillis(),
        )
        if (current == null) dao.insert(entry) else dao.update(entry)
        // Old photo was replaced/removed -> delete the file
        if (current?.photoPath != null && current.photoPath != photoPath.value) {
            PhotoStorage.delete(current.photoPath)
        }
        saved = true
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        existing?.let {
            dao.delete(it)
            PhotoStorage.delete(it.photoPath)
            if (photoPath.value != it.photoPath) PhotoStorage.delete(photoPath.value)
        }
        saved = true
    }

    override fun onCleared() {
        // User backed out without saving: remove any photo captured during this session
        val path = photoPath.value
        if (!saved && path != null && path != existing?.photoPath) PhotoStorage.delete(path)
    }

    companion object {
        private const val KEY_DAY = "day"
        private const val KEY_PHOTO = "photo"
        private const val KEY_PENDING = "pending_camera"
        private const val KEY_POPULATED = "populated"
    }
}
