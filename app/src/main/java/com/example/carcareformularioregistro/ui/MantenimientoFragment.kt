package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.MaintenanceAdapter
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.FragmentMantenimientoBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MantenimientoFragment : Fragment() {
    private var _binding: FragmentMantenimientoBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MaintenanceAdapter
    // Activity scope retains the query when using the existing bottom navigation.
    private val viewModel by lazy {
        ViewModelProvider(requireActivity())[MaintenanceViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMantenimientoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = MaintenanceAdapter(
            onItemClick = { maintenance ->
                openActivity(Intent(requireContext(), AddMaintenanceActivity::class.java)
                    .putExtra("maintenance_id", maintenance.id))
            },
            onDeleteClick = ::confirmDeleteMaintenance
        )
        binding.rvMaintenances.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMaintenances.adapter = adapter
        binding.rvMaintenances.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) clearSearchFocus()
            }
        })
        setupListeners()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.deleteFailed.collect { failed ->
                        if (failed) {
                            Snackbar.make(binding.root, R.string.maintenance_delete_error, Snackbar.LENGTH_LONG).show()
                            viewModel.dismissDeleteError()
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.etSearchMaintenance.setText(viewModel.query.value)
        binding.tabFilters.getTabAt(viewModel.selectedTab.value)?.select()
        binding.etSearchMaintenance.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.etSearchMaintenance.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                clearSearchFocus()
                true
            } else false
        }
        binding.fabAddMaintenance.setOnClickListener {
            openActivity(Intent(requireContext(), AddMaintenanceActivity::class.java))
        }
        binding.btnEmptyAdd.setOnClickListener {
            openActivity(Intent(requireContext(), AddMaintenanceActivity::class.java))
        }
        binding.btnViewHistory.setOnClickListener {
            openActivity(Intent(requireContext(), HistorialActivity::class.java))
        }
        binding.tabFilters.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { viewModel.setTab(tab?.position ?: 0) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun render(state: MaintenanceUiState) {
        adapter.updateData(state.items)
        binding.tvMonitoredSubtitle.text = resources.getQuantityString(
            R.plurals.maintenance_monitored, state.totalCount, state.totalCount
        )
        binding.progressLoading.isVisible = state.loading
        val empty = !state.loading && state.items.isEmpty()
        binding.containerEmpty.isVisible = empty
        binding.rvMaintenances.isVisible = !state.loading && !empty
        val filtered = state.query.isNotBlank() || state.filter != MaintenanceFilter.ALL
        binding.tvEmptyTitle.setText(when {
            state.loadFailed -> R.string.maintenance_load_error
            filtered -> R.string.maintenance_no_results
            else -> R.string.empty_mantenimiento
        })
        binding.tvEmptyMessage.setText(when {
            state.loadFailed -> R.string.maintenance_load_retry
            state.query.isNotBlank() -> R.string.maintenance_try_another_term
            filtered -> R.string.maintenance_try_another_tab
            else -> R.string.maintenance_add_first
        })
        binding.btnEmptyAdd.isVisible = !filtered && !state.loadFailed
    }

    private fun confirmDeleteMaintenance(maintenance: Maintenance) {
        clearSearchFocus()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.maintenance_search_delete_title)
            .setMessage(getString(R.string.maintenance_search_delete_message, maintenance.type))
            .setPositiveButton(R.string.maintenance_delete) { _, _ -> viewModel.delete(maintenance) }
            .setNegativeButton(R.string.maintenance_cancel, null)
            .show()
    }

    private fun openActivity(intent: Intent) {
        clearSearchFocus()
        startActivity(intent)
    }

    private fun clearSearchFocus() {
        _binding?.let {
            WindowCompat.getInsetsController(requireActivity().window, it.etSearchMaintenance).hide(WindowInsetsCompat.Type.ime())
            it.etSearchMaintenance.clearFocus()
            it.root.requestFocus()
        }
    }

    override fun onPause() {
        clearSearchFocus()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.rvMaintenances.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
