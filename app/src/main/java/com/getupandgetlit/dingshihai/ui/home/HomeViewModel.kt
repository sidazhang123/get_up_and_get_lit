package com.getupandgetlit.dingshihai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getupandgetlit.dingshihai.data.repo.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    repository: TaskRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeTasks(),
        repository.observeRuntimeState(),
    ) { tasks, runtime ->
        HomeUiState(
            tasks = tasks,
            batchActive = runtime.batchActive,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

