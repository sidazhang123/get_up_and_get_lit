package com.getupandgetlit.dingshihai.ui.home

import com.getupandgetlit.dingshihai.data.entity.TaskEntity

data class HomeUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val batchActive: Boolean = false,
)

