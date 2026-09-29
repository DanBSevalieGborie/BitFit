package com.codepath.bitfit

import com.codepath.bitfit.data.EntryEntity
import com.codepath.bitfit.util.CsvExporter
import com.codepath.bitfit.util.Metric
import com.codepath.bitfit.util.StatsCalculator
import com.codepath.bitfit.util.StatsRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private val today = 20_000L

    private fun entry(day: Long, cal: Int, water: Int = 0, sleep: Float? = null, mood: Int? = null) =
        EntryEntity(epochDay = day, foodName = "Food", calories = cal, waterCups = water, sleepHours = sleep, mood = mood)

    @Test
    fun dailySummaries_sumsCaloriesAndWater_perDay() {
        val summaries = StatsCalculator.dailySummaries(
            listOf(entry(today, 500, 2), entry(today, 700, 3), entry(today - 1, 1800, 8))
        )
        assertEquals(2, summaries.size)
        assertEquals(today - 1, summaries[0].epochDay) // sorted ascending
        assertEquals(1200, summaries[1].calories)
        assertEquals(5, summaries[1].water)
        assertEquals(2, summaries[1].entryCount)
    }

    @Test
    fun dailySummaries_usesMaxSleep_andAverageMood() {
        val s = StatsCalculator.dailySummaries(
            listOf(entry(today, 100, sleep = 7f, mood = 2), entry(today, 100, sleep = 8f, mood = 4))
        ).single()
        assertEquals(8f, s.sleep!!, 0.001f)
        assertEquals(3f, s.mood!!, 0.001f)
    }

    @Test
    fun stats_computesAveragesMinMax_inRange() {
        val entries = listOf(
            entry(today, 2000, 8, 8f, 5),
            entry(today - 1, 1500, 6, 7f, 4),
            entry(today - 2, 2500, 4, 6f, 3),
            entry(today - 30, 9999), // outside the 7 day window
        )
        val stats = StatsCalculator.stats(entries, today, StatsRange.WEEK, calorieGoal = 2200)
        assertEquals(3, stats.daysLogged)
        assertEquals(2000f, stats.avgCalories!!, 0.001f)
        assertEquals(1500, stats.minCalories)
        assertEquals(2500, stats.maxCalories)
        assertEquals(6f, stats.avgWater!!, 0.001f)
        assertEquals(7f, stats.avgSleep!!, 0.001f)
        assertEquals(4f, stats.avgMood!!, 0.001f)
        assertEquals(1, stats.daysOverCalorieGoal)
        assertEquals(2000, stats.todayCalories)
    }

    @Test
    fun stats_allTime_includesEverything() {
        val entries = listOf(entry(today, 1000), entry(today - 100, 3000))
        val stats = StatsCalculator.stats(entries, today, StatsRange.ALL, 2000)
        assertEquals(2, stats.daysLogged)
        assertEquals(2000f, stats.avgCalories!!, 0.001f)
    }

    @Test
    fun stats_emptyList_returnsNulls() {
        val stats = StatsCalculator.stats(emptyList(), today, StatsRange.WEEK, 2000)
        assertNull(stats.avgCalories)
        assertNull(stats.minCalories)
        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.bestStreak)
    }

    @Test
    fun currentStreak_countsBackFromToday() {
        assertEquals(3, StatsCalculator.currentStreak(listOf(today, today - 1, today - 2, today - 4), today))
    }

    @Test
    fun currentStreak_startsFromYesterday_ifTodayNotLoggedYet() {
        assertEquals(2, StatsCalculator.currentStreak(listOf(today - 1, today - 2), today))
        assertEquals(0, StatsCalculator.currentStreak(listOf(today - 2), today))
    }

    @Test
    fun bestStreak_findsLongestRun() {
        assertEquals(4, StatsCalculator.bestStreak(listOf(1, 2, 3, 4, 7, 8, 10).map { it.toLong() }))
    }

    @Test
    fun series_fillsMissingDaysWithNull() {
        val points = StatsCalculator.series(listOf(entry(today, 1800), entry(today - 2, 1600)), Metric.CALORIES, today, StatsRange.WEEK)
        assertEquals(7, points.size)
        assertEquals(1800f, points.last().value!!, 0.001f)
        assertNull(points[5].value)
        assertEquals(1600f, points[4].value!!, 0.001f)
    }

    @Test
    fun percentChange_comparesThisWeekToLastWeek() {
        val entries = listOf(entry(today, 2200), entry(today - 8, 2000))
        assertEquals(10f, StatsCalculator.percentChange(entries, Metric.CALORIES, today)!!, 0.01f)
        assertNull(StatsCalculator.percentChange(listOf(entry(today, 2000)), Metric.CALORIES, today))
    }

    @Test
    fun csv_escapesCommasAndQuotes() {
        val csv = CsvExporter.toCsv(listOf(entry(today, 500).copy(foodName = "Rice, beans", notes = "said \"yum\"")))
        assertTrue(csv.contains("\"Rice, beans\""))
        assertTrue(csv.contains("\"said \"\"yum\"\"\""))
    }
}
