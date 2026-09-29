package com.codepath.bitfit.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.codepath.bitfit.R
import com.codepath.bitfit.databinding.FragmentEntriesBinding
import com.codepath.bitfit.util.Formatters
import com.codepath.bitfit.util.PhotoStorage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class EntriesFragment : Fragment(R.layout.fragment_entries) {

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEntriesBinding.bind(view)

        val adapter = EntryAdapter { entry ->
            startActivity(EntryActivity.editIntent(requireContext(), entry.id))
        }
        binding.entriesRecycler.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.entry_columns))
        binding.entriesRecycler.adapter = adapter
        attachSwipeToDelete(adapter)
        binding.entriesRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                (activity as? MainActivity)?.onContentScrolled(dy)
            }
        })

        binding.searchInput.setText(viewModel.query.value)
        binding.searchInput.doAfterTextChanged { viewModel.query.value = it?.toString().orEmpty() }
        binding.emptyAction.setOnClickListener { startActivity(EntryActivity.newIntent(requireContext())) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredEntries.collect { list ->
                        adapter.submitList(list)
                        val searching = viewModel.query.value.isNotBlank()
                        binding.emptyState.isVisible = list.isEmpty()
                        binding.entriesRecycler.isVisible = list.isNotEmpty()
                        binding.emptyTitle.setText(if (searching) R.string.empty_search_title else R.string.empty_title)
                        binding.emptyBody.setText(if (searching) R.string.empty_search_body else R.string.empty_body)
                        binding.emptyAction.isVisible = !searching
                    }
                }
                launch {
                    viewModel.entries.collect { all ->
                        if (all == null) return@collect
                        binding.header.isVisible = all.isNotEmpty()
                        val today = Formatters.today()
                        val todayCalories = all.filter { it.epochDay == today }.sumOf { it.calories }
                        binding.header.text = resources.getQuantityString(
                            R.plurals.entries_header, all.size, all.size, Formatters.number(todayCalories)
                        )
                    }
                }
            }
        }
    }

    private fun attachSwipeToDelete(adapter: EntryAdapter) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val entry = adapter.currentList[position]
                viewModel.delete(entry)
                Snackbar.make(binding.root, getString(R.string.entry_deleted, entry.foodName), Snackbar.LENGTH_LONG)
                    .setAnchorView((activity as? MainActivity)?.snackbarAnchor())
                    .setAction(R.string.undo) { viewModel.restore(entry) }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            // Only delete the photo file once the user can no longer undo
                            if (event != DISMISS_EVENT_ACTION) PhotoStorage.delete(entry.photoPath)
                        }
                    })
                    .show()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.entriesRecycler)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
