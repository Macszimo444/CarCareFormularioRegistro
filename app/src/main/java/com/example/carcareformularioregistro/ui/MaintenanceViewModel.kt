package com.example.carcareformularioregistro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.data.MaintenanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MaintenanceUiState(
    val items: List<Maintenance> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val filter: MaintenanceFilter = MaintenanceFilter.ALL,
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

class MaintenanceViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = MaintenanceRepository(AppDatabase.getInstance(application).maintenanceDao())
    val query = savedState.getStateFlow("maintenance_query", "")
    val selectedTab = savedState.getStateFlow("maintenance_tab", 0)
    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed = _deleteFailed.asStateFlow()

    val uiState: StateFlow<MaintenanceUiState> = combine(
        repository.observeMaintenances(), query, selectedTab
    ) { items, text, tab ->
        val filter = MaintenanceFilter.entries.getOrElse(tab) { MaintenanceFilter.ALL }
        MaintenanceUiState(
            items = MaintenanceSearch.filter(items, text, filter),
            totalCount = items.size,
            query = text,
            filter = filter,
            loading = false
        )
    }
        // Filtering does not block typing and never re-queries Room per keystroke.
        .flowOn(Dispatchers.Default)
        .catch { emit(MaintenanceUiState(loading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaintenanceUiState())

    fun setQuery(value: String) { savedState["maintenance_query"] = value }

    fun setTab(position: Int) {
        savedState["maintenance_tab"] = position.coerceIn(0, MaintenanceFilter.entries.lastIndex)
    }

    fun delete(maintenance: Maintenance) {
        viewModelScope.launch {
            try {
                repository.delete(maintenance)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _deleteFailed.value = true
            }
        }
    }

    fun dismissDeleteError() { _deleteFailed.value = false }
}
