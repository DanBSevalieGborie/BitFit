package com.codepath.bitfit.util

import com.codepath.bitfit.data.EntryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The metrics a user can chart on the dashboard. */
enum class Metric { CALORIES, WATER, SLEEP, MOOD }

/** Time window used by the dashboard. `days == null` means "all time". */
enum class StatsRange(val days: Int?) { WEEK(7), MONTH(30), ALL(null) }

/** All entries for one calendar day rolled up together. */
data class DailySummary(
    val epochDay: Long,
    val calories: Int,
    val water: Int,
    val sleep: Float?,
    val mood: Float?,
    val entryCount: Int,
) {
    fun value(metric: Metric): Float? = when (metric) {
        Metric.CALORIES -> calories.toFloat()
        Metric.WATER -> water.toFloat()
        Metric.SLEEP -> sleep
        Metric.MOOD -> mood
    }
}

data class DashboardStats(
    val totalEntries: Int,
    val daysLogged: Int,
    val avgCalories: Float?,
    val minCalories: Int?,
    val maxCalories: Int?,
    val avgWater: Float?,
    val avgSleep: Float?,
    val avgMood: Float?,
    val currentStreak: Int,
    val bestStreak: Int,
    val todayCalories: Int,
    val todayWater: Int,
    val daysOverCalorieGoal: Int,
)

data class ChartPoint(val epochDay: Long, val label: String, val value: Float?)

/**
 * Pure-Kotlin helpers that turn raw entries into dashboard numbers.
 * Kept free of Android classes so they are easy to unit test.
 */
object StatsCalculator {

    private val labelFormat = DateTimeFormatter.ofPattern("M/d")

    /** Groups entries by day. Calories & water are summed, sleep is the max logged, mood is averaged. */
    fun dailySummaries(entries: List<EntryEntity>): List<DailySummary> =
        entries.groupBy { it.epochDay }
            .map { (day, list) ->
                val sleeps = list.mapNotNull { it.sleepHours }.filter { it > 0f }
                val moods = list.mapNotNull { it.mood }
                DailySummary(
                    epochDay = day,
                    calories = list.sumOf { it.calories },
                    water = list.sumOf { it.waterCups },
                    sleep = sleeps.maxOrNull(),
                    mood = if (moods.isEmpty()) null else moods.average().toFloat(),
                    entryCount = list.size,
                )
            }
            .sortedBy { it.epochDay }

    fun inRange(summaries: List<DailySummary>, today: Long, range: StatsRange): List<DailySummary> {
        val days = range.days ?: return summaries
        val start = today - (days - 1)
        return summaries.filter { it.epochDay in start..today }
    }

    fun stats(
        entries: List<EntryEntity>,
        today: Long,
        range: StatsRange,
        calorieGoal: Int,
    ): DashboardStats {
        val all = dailySummaries(entries)
        val window = inRange(all, today, range)
        val todaySummary = all.firstOrNull { it.epochDay == today }
        val loggedDays = all.map { it.epochDay }
        return DashboardStats(
            totalEntries = window.sumOf { it.entryCount },
            daysLogged = window.size,
            avgCalories = window.map { it.calories.toFloat() }.averageOrNull(),
            minCalories = window.minOfOrNull { it.calories },
            maxCalories = window.maxOfOrNull { it.calories },
            avgWater = window.map { it.water.toFloat() }.averageOrNull(),
            avgSleep = window.mapNotNull { it.sleep }.averageOrNull(),
            avgMood = window.mapNotNull { it.mood }.averageOrNull(),
            currentStreak = currentStreak(loggedDays, today),
            bestStreak = bestStreak(loggedDays),
            todayCalories = todaySummary?.calories ?: 0,
            todayWater = todaySummary?.water ?: 0,
            daysOverCalorieGoal = if (calorieGoal > 0) window.count { it.calories > calorieGoal } else 0,
        )
    }

    /** Consecutive logged days ending today (or yesterday, so the streak isn't "lost" before you log today). */
    fun currentStreak(loggedDays: Collection<Long>, today: Long): Int {
        val set = loggedDays.toHashSet()
        var day = if (today in set) today else today - 1
        var streak = 0
        while (day in set) {
            streak++
            day--
        }
        return streak
    }

    fun bestStreak(loggedDays: Collection<Long>): Int {
        val sorted = loggedDays.toSortedSet().toList()
        if (sorted.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * Points for the trend chart. For 7/30 day ranges every calendar day is present
     * (missing days are null so the line shows a gap). For "all time" only logged days are used.
     */
    fun series(entries: List<EntryEntity>, metric: Metric, today: Long, range: StatsRange): List<ChartPoint> {
        val summaries = dailySummaries(entries)
        val days = range.days
        return if (days == null) {
            summaries.map { ChartPoint(it.epochDay, label(it.epochDay), it.value(metric)) }
        } else {
            val byDay = summaries.associateBy { it.epochDay }
            (today - (days - 1)..today).map { day ->
                ChartPoint(day, label(day), byDay[day]?.value(metric))
            }
        }
    }

    /**
     * Average of the metric over the last [days] days compared with the [days] before that.
     * Returns the percent change, or null if either window has no data.
     */
    fun percentChange(entries: List<EntryEntity>, metric: Metric, today: Long, days: Int = 7): Float? {
        val summaries = dailySummaries(entries)
        val current = summaries.filter { it.epochDay in (today - days + 1)..today }
            .mapNotNull { it.value(metric) }.averageOrNull()
        val previous = summaries.filter { it.epochDay in (today - 2 * days + 1)..(today - days) }
            .mapNotNull { it.value(metric) }.averageOrNull()
        if (current == null || previous == null || previous == 0f) return null
        return (current - previous) / previous * 100f
    }

    private fun label(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(labelFormat)

    private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
}
