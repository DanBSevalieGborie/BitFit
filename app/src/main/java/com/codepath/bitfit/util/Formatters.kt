package com.codepath.bitfit.util

import android.content.Context
import com.codepath.bitfit.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

object Formatters {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    private val numberFormat = NumberFormat.getIntegerInstance()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun friendlyDate(context: Context, epochDay: Long): String {
        val today = today()
        return when (epochDay) {
            today -> context.getString(R.string.today)
            today - 1 -> context.getString(R.string.yesterday)
            else -> LocalDate.ofEpochDay(epochDay).format(dateFormat)
        }
    }

    fun fullDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(longDate)

    fun number(value: Int): String = numberFormat.format(value)

    fun number(value: Float?): String = if (value == null) "—" else numberFormat.format(value.roundToInt())

    fun oneDecimal(value: Float?): String = if (value == null) "—" else String.format("%.1f", value)

    fun moodEmoji(mood: Int?): String = when (mood) {
        1 -> "😫"
        2 -> "😕"
        3 -> "😐"
        4 -> "🙂"
        5 -> "😄"
        else -> "—"
    }

    fun moodEmoji(mood: Float?): String = moodEmoji(mood?.roundToInt())

    fun minutesToTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%d:%02d %s", h12, m, amPm)
    }
}
