package com.getupandgetlit.dingshihai.domain.model

object TaskDraftValidator {
    fun validate(draft: TaskDraft, duplicateExists: Boolean): TaskValidationResult {
        val hour = draft.startHour ?: return TaskValidationResult.MissingRequired
        val minute = draft.startMinute ?: return TaskValidationResult.MissingRequired
        if (hour !in 0..23 || minute !in 0..59) {
            return TaskValidationResult.InvalidTime
        }
        if (draft.fileUri.isBlank() || draft.fileName.isBlank()) {
            return TaskValidationResult.MissingRequired
        }
        val lowerName = draft.fileName.lowercase()
        if (!lowerName.endsWith(".mp3") && !lowerName.endsWith(".wav")) {
            return TaskValidationResult.InvalidFileType
        }
        if (duplicateExists) {
            return TaskValidationResult.DuplicateTime
        }
        if (draft.playMode == PlayMode.INTERVAL) {
            val loopCount = draft.loopCount
            val min = draft.intervalMinSec
            val max = draft.intervalMaxSec
            if (loopCount == null || min == null || max == null) {
                return TaskValidationResult.InvalidInterval
            }
            if (loopCount !in 1..99 || min !in 1..9999 || max !in 1..9999 || max < min) {
                return TaskValidationResult.InvalidInterval
            }
        } else if (
            draft.loopCount != null ||
            draft.intervalMinSec != null ||
            draft.intervalMaxSec != null
        ) {
            return TaskValidationResult.InvalidInterval
        }
        return TaskValidationResult.Valid
    }
}

