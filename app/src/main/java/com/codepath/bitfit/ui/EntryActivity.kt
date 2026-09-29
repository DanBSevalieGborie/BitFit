package com.codepath.bitfit.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.codepath.bitfit.R
import com.codepath.bitfit.databinding.ActivityEntryBinding
import com.codepath.bitfit.util.Formatters
import com.codepath.bitfit.util.PhotoStorage
import com.codepath.bitfit.util.padForSystemBars
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The "create entry" screen (also used to edit an existing entry).
 * Saves straight into Room — the main list then updates itself from the database.
 */
class EntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryBinding
    private val viewModel: EntryViewModel by viewModels()
    private val entryId by lazy { intent.getLongExtra(EXTRA_ENTRY_ID, -1L) }
    private var recentFoods: Map<String, Int> = emptyMap()

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val pending = viewModel.pendingCameraPath
        viewModel.pendingCameraPath = null
        if (success && pending != null) viewModel.setPhoto(pending) else PhotoStorage.delete(pending)
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { PhotoStorage.copyFromUri(this@EntryActivity, uri) }
            if (file != null) viewModel.setPhoto(file.absolutePath)
            else Toast.makeText(this@EntryActivity, R.string.photo_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.padForSystemBars(top = true)
        binding.bottomBar.padForSystemBars(bottom = true, ime = true)

        val isEdit = entryId > 0
        binding.toolbar.title = getString(if (isEdit) R.string.edit_entry else R.string.new_entry)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = isEdit
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete) { confirmDelete(); true } else false
        }
        binding.saveButton.setText(if (isEdit) R.string.update_entry else R.string.save_entry)

        setupForm()
        observeState()

        lifecycleScope.launch {
            val entry = viewModel.load(entryId)
            if (isEdit && entry == null) { finish(); return@launch }
            if (!viewModel.populated) {
                entry?.let { populate(it) }
                viewModel.populated = true
            }
            recentFoods = viewModel.recentFoods()
            binding.foodInput.setAdapter(
                ArrayAdapter(this@EntryActivity, android.R.layout.simple_dropdown_item_1line, recentFoods.keys.toList())
            )
        }
    }

    private fun setupForm() {
        binding.dateButton.setOnClickListener { pickDate() }

        binding.waterSlider.setLabelFormatter { getString(R.string.cups_value, it.toInt()) }
        binding.waterSlider.addOnChangeListener { _, value, _ -> updateWaterLabel(value) }
        updateWaterLabel(binding.waterSlider.value)

        binding.sleepSlider.setLabelFormatter { getString(R.string.hours_value, Formatters.oneDecimal(it)) }
        binding.sleepSlider.addOnChangeListener { _, value, _ -> updateSleepLabel(value) }
        updateSleepLabel(binding.sleepSlider.value)

        // Picking a food you've logged before fills in its calories
        binding.foodInput.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position) as String
            recentFoods[name]?.let { binding.caloriesInput.setText(it.toString()) }
        }

        binding.takePhotoButton.setOnClickListener {
            val file = PhotoStorage.newPhotoFile(this)
            viewModel.pendingCameraPath = file.absolutePath
            runCatching { takePicture.launch(PhotoStorage.uriFor(this, file)) }
                .onFailure {
                    viewModel.pendingCameraPath = null
                    Toast.makeText(this, R.string.no_camera, Toast.LENGTH_SHORT).show()
                }
        }
        binding.pickPhotoButton.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.removePhotoButton.setOnClickListener { viewModel.setPhoto(null) }
        binding.photoPreview.setOnClickListener { viewModel.photoPath.value?.let { showFullPhoto(it) } }

        binding.saveButton.setOnClickListener { save() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.epochDay.collect { day ->
                        binding.dateButton.text = Formatters.fullDate(day)
                    }
                }
                launch {
                    viewModel.photoPath.collect { path ->
                        val hasPhoto = path != null
                        binding.photoPreview.isVisible = hasPhoto
                        binding.photoEmpty.isVisible = !hasPhoto
                        binding.removePhotoButton.isVisible = hasPhoto
                        if (path != null) {
                            Glide.with(this@EntryActivity).load(File(path)).centerCrop().into(binding.photoPreview)
                        } else {
                            Glide.with(this@EntryActivity).clear(binding.photoPreview)
                        }
                    }
                }
            }
        }
    }

    private fun populate(entry: com.codepath.bitfit.data.EntryEntity) {
        binding.foodInput.setText(entry.foodName, false)
        binding.caloriesInput.setText(entry.calories.toString())
        binding.waterSlider.value = entry.waterCups.toFloat().coerceIn(binding.waterSlider.valueFrom, binding.waterSlider.valueTo)
        binding.sleepSlider.value = (entry.sleepHours ?: 0f).coerceIn(binding.sleepSlider.valueFrom, binding.sleepSlider.valueTo)
        val chip = when (entry.mood) {
            1 -> R.id.mood_1
            2 -> R.id.mood_2
            3 -> R.id.mood_3
            4 -> R.id.mood_4
            5 -> R.id.mood_5
            else -> null
        }
        if (chip != null) binding.moodChips.check(chip) else binding.moodChips.clearCheck()
        binding.notesInput.setText(entry.notes.orEmpty())
    }

    private fun updateWaterLabel(value: Float) {
        binding.waterValue.text = resources.getQuantityString(R.plurals.cups, value.toInt(), value.toInt())
    }

    private fun updateSleepLabel(value: Float) {
        binding.sleepValue.text = if (value <= 0f) getString(R.string.not_logged)
        else getString(R.string.hours_value, Formatters.oneDecimal(value))
    }

    private fun pickDate() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_date)
            .setSelection(viewModel.epochDay.value * MILLIS_PER_DAY)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            viewModel.setDay(Math.floorDiv(millis, MILLIS_PER_DAY))
        }
        picker.show(supportFragmentManager, "date")
    }

    private fun save() {
        val food = binding.foodInput.text?.toString()?.trim().orEmpty()
        val calories = binding.caloriesInput.text?.toString()?.trim()?.toIntOrNull()

        binding.foodLayout.error = if (food.isEmpty()) getString(R.string.error_food) else null
        binding.caloriesLayout.error = when {
            calories == null -> getString(R.string.error_calories)
            calories !in 0..20000 -> getString(R.string.error_calories_range)
            else -> null
        }
        if (binding.foodLayout.error != null || binding.caloriesLayout.error != null || calories == null) return

        val sleep = binding.sleepSlider.value.takeIf { it > 0f }
        val mood = when (binding.moodChips.checkedChipId) {
            R.id.mood_1 -> 1
            R.id.mood_2 -> 2
            R.id.mood_3 -> 3
            R.id.mood_4 -> 4
            R.id.mood_5 -> 5
            else -> null
        }
        val notes = binding.notesInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        binding.saveButton.isEnabled = false
        lifecycleScope.launch {
            viewModel.save(food, calories, binding.waterSlider.value.toInt(), sleep, mood, notes)
            Toast.makeText(this@EntryActivity, R.string.entry_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    viewModel.delete()
                    finish()
                }
            }
            .show()
    }

    private fun showFullPhoto(path: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val image = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.photo_full_description)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(image)
        Glide.with(this).load(File(path)).into(image)
        dialog.show()
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "entry_id"
        private const val MILLIS_PER_DAY = 86_400_000L

        fun newIntent(context: Context) = Intent(context, EntryActivity::class.java)

        fun editIntent(context: Context, id: Long) =
            Intent(context, EntryActivity::class.java).putExtra(EXTRA_ENTRY_ID, id)
    }
}
