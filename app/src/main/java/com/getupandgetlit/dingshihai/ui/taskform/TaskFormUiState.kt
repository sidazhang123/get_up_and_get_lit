package com.getupandgetlit.dingshihai.ui.taskform

import com.getupandgetlit.dingshihai.domain.model.TaskDraft

data class TaskFormUiState(
    val isEditMode: Boolean = false,
    val draft: TaskDraft = TaskDraft(),
)

sealed class TaskSaveResult {
    data object Success : TaskSaveResult()
    data class Error(val messageRes: Int) : TaskSaveResult()
}

