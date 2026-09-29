package com.codepath.bitfit.ui

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.codepath.bitfit.R
import com.codepath.bitfit.databinding.FragmentDashboardBinding
import com.codepath.bitfit.util.Formatters
import com.codepath.bitfit.util.Metric
import com.codepath.bitfit.util.StatsRange
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Averages, streaks, goals and a trend chart for the tracked metrics. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private var lastMetric: Metric? = null
    private var lastRange: StatsRange? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashboardBinding.bind(view)

        binding.rangeToggle.check(
            when (viewModel.range.value) {
                StatsRange.WEEK -> R.id.range_week
                StatsRange.MONTH -> R.id.range_month
                StatsRange.ALL -> R.id.range_all
            }
        )
        binding.rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.range.value = when (checkedId) {
                R.id.range_month -> StatsRange.MONTH
                R.id.range_all -> StatsRange.ALL
                else -> StatsRange.WEEK
            }
        }

        val trend = binding.trendCard
        trend.metricChips.check(
            when (viewModel.metric.value) {
                Metric.CALORIES -> R.id.chip_calories
                Metric.WATER -> R.id.chip_water
                Metric.SLEEP -> R.id.chip_sleep
                Metric.MOOD -> R.id.chip_mood
            }
        )
        trend.metricChips.setOnCheckedStateChangeListener { _, ids ->
            viewModel.metric.value = when (ids.firstOrNull()) {
                R.id.chip_water -> Metric.WATER
                R.id.chip_sleep -> Metric.SLEEP
                R.id.chip_mood -> Metric.MOOD
                else -> Metric.CALORIES
            }
        }
        binding.dashboardContent.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
                (activity as? MainActivity)?.onContentScrolled(y - oldY)
            }
        )
        binding.emptyAction.setOnClickListener { startActivity(EntryActivity.newIntent(requireContext())) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dashboard.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshGoals() // goals may have changed in Settings
    }

    private fun render(ui: DashboardUi) {
        binding.emptyState.isVisible = !ui.hasAnyEntries
        binding.dashboardContent.isVisible = ui.hasAnyEntries
        if (!ui.hasAnyEntries) return

        val s = ui.stats
        val g = ui.goals

        // Today card
        val today = binding.todayCard
        today.todayCalories.text = getString(R.string.today_calories, Formatters.number(s.todayCalories), Formatters.number(g.calories))
        today.todayCaloriesProgress.max = g.calories.coerceAtLeast(1)
        today.todayCaloriesProgress.setProgressCompat(s.todayCalories.coerceAtMost(g.calories), true)
        val overGoal = s.todayCalories > g.calories
        today.todayCaloriesProgress.setIndicatorColor(
            if (overGoal) MaterialColors.getColor(today.root, com.google.android.material.R.attr.colorError)
            else ContextCompat.getColor(requireContext(), R.color.metric_calories)
        )
        today.todayCaloriesHint.text = if (overGoal) {
            getString(R.string.today_over_goal, Formatters.number(s.todayCalories - g.calories))
        } else {
            getString(R.string.today_remaining, Formatters.number(g.calories - s.todayCalories))
        }
        today.todayWater.text = getString(R.string.today_water, s.todayWater, g.water)
        today.todayWaterProgress.max = g.water.coerceAtLeast(1)
        today.todayWaterProgress.setProgressCompat(s.todayWater.coerceAtMost(g.water), true)
        today.streakValue.text = resources.getQuantityString(R.plurals.streak_days, s.currentStreak, s.currentStreak)
        today.bestStreakValue.text = getString(R.string.best_streak, s.bestStreak)

        // Stats grid
        val st = binding.statsCard
        st.statAvgCalories.text = Formatters.number(s.avgCalories)
        st.statMinCalories.text = s.minCalories?.let { Formatters.number(it) } ?: "—"
        st.statMaxCalories.text = s.maxCalories?.let { Formatters.number(it) } ?: "—"
        st.statAvgWater.text = Formatters.oneDecimal(s.avgWater)
        st.statAvgSleep.text = s.avgSleep?.let { getString(R.string.hours_short, Formatters.oneDecimal(it)) } ?: "—"
        st.statAvgMood.text = s.avgMood?.let { "${Formatters.moodEmoji(it)} ${Formatters.oneDecimal(it)}" } ?: "—"
        st.statDaysLogged.text = s.daysLogged.toString()
        st.statOverGoal.text = s.daysOverCalorieGoal.toString()
        st.statsTitle.text = getString(
            when (ui.range) {
                StatsRange.WEEK -> R.string.stats_title_week
                StatsRange.MONTH -> R.string.stats_title_month
                StatsRange.ALL -> R.string.stats_title_all
            }
        )

        // Trend chart
        val trend = binding.trendCard
        val colorRes = when (ui.metric) {
            Metric.CALORIES -> R.color.metric_calories
            Metric.WATER -> R.color.metric_water
            Metric.SLEEP -> R.color.metric_sleep
            Metric.MOOD -> R.color.metric_mood
        }
        val goal: Float? = when (ui.metric) {
            Metric.CALORIES -> g.calories.toFloat()
            Metric.WATER -> g.water.toFloat()
            Metric.SLEEP -> g.sleep
            Metric.MOOD -> null
        }
        val formatter: (Float) -> String = when (ui.metric) {
            Metric.SLEEP -> { v -> Formatters.oneDecimal(v) }
            Metric.MOOD -> { v -> Formatters.oneDecimal(v) }
            else -> { v -> v.roundToInt().toString() }
        }
        val animate = ui.metric != lastMetric || ui.range != lastRange
        lastMetric = ui.metric
        lastRange = ui.range
        trend.trendChart.setData(
            points = ui.series,
            color = ContextCompat.getColor(requireContext(), colorRes),
            goal = goal,
            fixedMax = if (ui.metric == Metric.MOOD) 5f else null,
            formatter = formatter,
            animate = animate,
        )

        val change = ui.weekChange
        trend.trendChange.isVisible = change != null
        if (change != null) {
            val arrow = if (change >= 0) "▲" else "▼"
            trend.trendChange.text = getString(R.string.trend_change, arrow, abs(change).roundToInt())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
