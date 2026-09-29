package com.codepath.bitfit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codepath.bitfit.BitFitApplication
import com.codepath.bitfit.data.EntryEntity
import com.codepath.bitfit.util.ChartPoint
import com.codepath.bitfit.util.DashboardStats
import com.codepath.bitfit.util.Formatters
import com.codepath.bitfit.util.Metric
import com.codepath.bitfit.util.PhotoStorage
import com.codepath.bitfit.util.Prefs
import com.codepath.bitfit.util.SampleData
import com.codepath.bitfit.util.StatsCalculator
import com.codepath.bitfit.util.StatsRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Goals(val calories: Int, val water: Int, val sleep: Float)

data class DashboardUi(
    val stats: DashboardStats,
    val series: List<ChartPoint>,
    val metric: Metric,
    val range: StatsRange,
    val goals: Goals,
    val weekChange: Float?,
    val hasAnyEntries: Boolean,
)

/**
 * Shared by the Entries + Dashboard tabs. Everything shown on screen is derived from the
 * Room database Flow, so the database stays the single source of truth.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as BitFitApplication).db.entryDao()
    private val prefs = Prefs(app)

    /** null until the first database emission (so we don't flash the empty state). */
    val entries: StateFlow<List<EntryEntity>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val query = MutableStateFlow("")
    val range = MutableStateFlow(StatsRange.WEEK)
    val metric = MutableStateFlow(Metric.CALORIES)
    private val goals = MutableStateFlow(readGoals())

    val filteredEntries: Flow<List<EntryEntity>> =
        combine(entries.filterNotNull(), query) { list, q ->
            val needle = q.trim()
            if (needle.isEmpty()) list
            else list.filter {
                it.foodName.contains(needle, ignoreCase = true) ||
                    (it.notes?.contains(needle, ignoreCase = true) == true)
            }
        }

    val dashboard: Flow<DashboardUi> =
        combine(entries.filterNotNull(), range, metric, goals) { list, r, m, g ->
            val today = Formatters.today()
            DashboardUi(
                stats = StatsCalculator.stats(list, today, r, g.calories),
                series = StatsCalculator.series(list, m, today, r),
                metric = m,
                range = r,
                goals = g,
                weekChange = StatsCalculator.percentChange(list, m, today),
                hasAnyEntries = list.isNotEmpty(),
            )
        }

    fun refreshGoals() {
        goals.value = readGoals()
    }

    private fun readGoals() = Goals(prefs.calorieGoal, prefs.waterGoal, prefs.sleepGoal)

    fun delete(entry: EntryEntity) = viewModelScope.launch(Dispatchers.IO) { dao.delete(entry) }

    /** Undo for swipe-to-delete: re-insert with the same id. */
    fun restore(entry: EntryEntity) = viewModelScope.launch(Dispatchers.IO) { dao.insert(entry) }

    fun addSampleData() = viewModelScope.launch(Dispatchers.IO) {
        dao.insertAll(SampleData.generate(Formatters.today()))
    }

    fun clearAll() = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteAll()
        PhotoStorage.deleteAll(getApplication())
    }

    suspend fun allEntries(): List<EntryEntity> = withContext(Dispatchers.IO) { dao.getAll() }
}
