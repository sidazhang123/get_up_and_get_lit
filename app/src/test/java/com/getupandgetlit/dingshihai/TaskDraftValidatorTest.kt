package com.getupandgetlit.dingshihai

import com.getupandgetlit.dingshihai.domain.model.PlayMode
import com.getupandgetlit.dingshihai.domain.model.TaskDraft
import com.getupandgetlit.dingshihai.domain.model.TaskDraftValidator
import com.getupandgetlit.dingshihai.domain.model.TaskValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDraftValidatorTest {
    @Test
    fun `accepts valid single task`() {
        val result = TaskDraftValidator.validate(
            draft = TaskDraft(
                name = "任务1",
                startHour = 8,
                startMinute = 30,
                fileUri = "content://demo/file.mp3",
                fileName = "demo.mp3",
                playMode = PlayMode.SINGLE,
            ),
            duplicateExists = false,
        )

        assertEquals(TaskValidationResult.Valid, result)
    }

    @Test
    fun `rejects duplicate time`() {
        val result = TaskDraftValidator.validate(
            draft = TaskDraft(
                startHour = 8,
                startMinute = 30,
                fileUri = "content://demo/file.mp3",
                fileName = "demo.mp3",
            ),
            duplicateExists = true,
        )

        assertEquals(TaskValidationResult.DuplicateTime, result)
    }

    @Test
    fun `rejects invalid interval parameters`() {
        val result = TaskDraftValidator.validate(
            draft = TaskDraft(
                startHour = 8,
                startMinute = 30,
                fileUri = "content://demo/file.wav",
                fileName = "demo.wav",
                playMode = PlayMode.INTERVAL,
                loopCount = 5,
                intervalMinSec = 20,
                intervalMaxSec = 10,
            ),
            duplicateExists = false,
        )

        assertEquals(TaskValidationResult.InvalidInterval, result)
    }
}

