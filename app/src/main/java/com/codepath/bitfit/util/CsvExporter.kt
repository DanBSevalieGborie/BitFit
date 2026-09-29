package com.codepath.bitfit.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.codepath.bitfit.R
import com.codepath.bitfit.data.EntryEntity
import java.io.File
import java.time.LocalDate

object CsvExporter {

    fun toCsv(entries: List<EntryEntity>): String = buildString {
        appendLine("date,food,calories,water_cups,sleep_hours,mood,notes,has_photo")
        entries.sortedBy { it.epochDay }.forEach { e ->
            appendLine(
                listOf(
                    LocalDate.ofEpochDay(e.epochDay).toString(),
                    escape(e.foodName),
                    e.calories.toString(),
                    e.waterCups.toString(),
                    e.sleepHours?.toString() ?: "",
                    e.mood?.toString() ?: "",
                    escape(e.notes ?: ""),
                    (e.photoPath != null).toString(),
                ).joinToString(",")
            )
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** Writes the CSV into the cache dir and returns a share Intent for it. */
    fun shareIntent(context: Context, entries: List<EntryEntity>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "bitfit_export.csv")
        file.writeText(toCsv(entries))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.export_csv))
    }
}
