package com.codepath.bitfit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.codepath.bitfit.BuildConfig
import com.codepath.bitfit.R
import com.codepath.bitfit.reminder.ReminderScheduler
import com.codepath.bitfit.util.CsvExporter
import com.codepath.bitfit.util.Formatters
import com.codepath.bitfit.util.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch

/** Goals, daily reminder, theme, and data tools (export / demo data / clear). */
class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: MainViewModel by activityViewModels()
    private val prefs by lazy { Prefs(requireContext()) }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val switch = findPreference<SwitchPreferenceCompat>(Prefs.KEY_REMINDER_ENABLED)
            if (granted) {
                switch?.isChecked = true
                enableReminder()
            } else {
                switch?.isChecked = false
                Toast.makeText(requireContext(), R.string.reminder_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        listOf(Prefs.KEY_CALORIE_GOAL, Prefs.KEY_WATER_GOAL).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.setSelection(it.text.length)
            }
        }
        findPreference<EditTextPreference>(Prefs.KEY_SLEEP_GOAL)?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            it.setSelection(it.text.length)
        }
        listOf(Prefs.KEY_CALORIE_GOAL, Prefs.KEY_WATER_GOAL, Prefs.KEY_SLEEP_GOAL).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnPreferenceChangeListener { _, newValue ->
                val ok = (newValue as? String)?.toFloatOrNull()?.let { it > 0 } == true
                if (!ok) Toast.makeText(requireContext(), R.string.goal_invalid, Toast.LENGTH_SHORT).show()
                ok
            }
        }

        findPreference<SwitchPreferenceCompat>(Prefs.KEY_REMINDER_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                if (needsNotificationPermission()) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnPreferenceChangeListener false
                }
                enableReminder()
            } else {
                ReminderScheduler.cancel(requireContext())
            }
            true
        }

        findPreference<Preference>(Prefs.KEY_REMINDER_TIME)?.apply {
            summary = Formatters.minutesToTime(prefs.reminderMinutes)
            setOnPreferenceClickListener { pickReminderTime(); true }
        }

        findPreference<ListPreference>(Prefs.KEY_THEME)?.setOnPreferenceChangeListener { _, newValue ->
            Prefs.applyTheme(newValue as String)
            true
        }

        findPreference<Preference>(Prefs.KEY_EXPORT)?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                val entries = viewModel.allEntries()
                if (entries.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.export_empty, Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(CsvExporter.shareIntent(requireContext(), entries))
                }
            }
            true
        }

        findPreference<Preference>(Prefs.KEY_SAMPLE_DATA)?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sample_data_title)
                .setMessage(R.string.sample_data_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add) { _, _ ->
                    viewModel.addSampleData()
                    Toast.makeText(requireContext(), R.string.sample_data_added, Toast.LENGTH_SHORT).show()
                }
                .show()
            true
        }

        findPreference<Preference>(Prefs.KEY_CLEAR)?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_everything) { _, _ ->
                    viewModel.clearAll()
                    Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
                }
                .show()
            true
        }

        findPreference<Preference>(Prefs.KEY_VERSION)?.summary =
            getString(R.string.version_summary, BuildConfig.VERSION_NAME)
    }

    private fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun enableReminder() {
        ReminderScheduler.schedule(requireContext(), prefs.reminderMinutes)
        Toast.makeText(
            requireContext(),
            getString(R.string.reminder_set, Formatters.minutesToTime(prefs.reminderMinutes)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun pickReminderTime() {
        val current = prefs.reminderMinutes
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(current / 60)
            .setMinute(current % 60)
            .setTitleText(R.string.reminder_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            prefs.reminderMinutes = picker.hour * 60 + picker.minute
            findPreference<Preference>(Prefs.KEY_REMINDER_TIME)?.summary = Formatters.minutesToTime(prefs.reminderMinutes)
            if (prefs.reminderEnabled) enableReminder()
        }
        picker.show(childFragmentManager, "time")
    }
}
