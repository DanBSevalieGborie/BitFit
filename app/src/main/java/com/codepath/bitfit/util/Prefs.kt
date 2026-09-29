package com.codepath.bitfit.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

/** Thin wrapper around the default SharedPreferences used by the Settings screen. */
class Prefs(context: Context) {

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val calorieGoal: Int get() = sp.getString(KEY_CALORIE_GOAL, null)?.toIntOrNull() ?: DEFAULT_CALORIE_GOAL
    val waterGoal: Int get() = sp.getString(KEY_WATER_GOAL, null)?.toIntOrNull() ?: DEFAULT_WATER_GOAL
    val sleepGoal: Float get() = sp.getString(KEY_SLEEP_GOAL, null)?.toFloatOrNull() ?: DEFAULT_SLEEP_GOAL

    var reminderEnabled: Boolean
        get() = sp.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()

    /** Minutes after midnight. */
    var reminderMinutes: Int
        get() = sp.getInt(KEY_REMINDER_TIME, DEFAULT_REMINDER_MINUTES)
        set(value) = sp.edit().putInt(KEY_REMINDER_TIME, value).apply()

    val theme: String get() = sp.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    companion object {
        const val KEY_CALORIE_GOAL = "calorie_goal"
        const val KEY_WATER_GOAL = "water_goal"
        const val KEY_SLEEP_GOAL = "sleep_goal"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_TIME = "reminder_time"
        const val KEY_THEME = "theme"
        const val KEY_EXPORT = "export_csv"
        const val KEY_SAMPLE_DATA = "sample_data"
        const val KEY_CLEAR = "clear_all"
        const val KEY_VERSION = "version"

        const val DEFAULT_CALORIE_GOAL = 2000
        const val DEFAULT_WATER_GOAL = 8
        const val DEFAULT_SLEEP_GOAL = 8f
        const val DEFAULT_REMINDER_MINUTES = 20 * 60

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        fun applyTheme(theme: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
