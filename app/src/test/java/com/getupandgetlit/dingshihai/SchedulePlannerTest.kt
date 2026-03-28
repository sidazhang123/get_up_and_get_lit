package com.getupandgetlit.dingshihai

import com.getupandgetlit.dingshihai.domain.scheduler.SchedulePlanner
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePlannerTest {
    @Test
    fun `uses same day when target time not passed`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 27, 8, 0, 10)
            set(Calendar.MILLISECOND, 0)
        }

        val result = SchedulePlanner.computeTriggerTime(calendar.timeInMillis, 9, 15)
        val expected = Calendar.getInstance().apply {
            timeInMillis = calendar.timeInMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, result)
    }

    @Test
    fun `uses next day when target time passed`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 27, 9, 15, 1)
            set(Calendar.MILLISECOND, 0)
        }

        val result = SchedulePlanner.computeTriggerTime(calendar.timeInMillis, 9, 15)
        val expected = Calendar.getInstance().apply {
            timeInMillis = calendar.timeInMillis
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, result)
    }

    @Test
    fun `normalizes seconds to zero`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 27, 9, 14, 59)
            set(Calendar.MILLISECOND, 900)
        }

        val result = SchedulePlanner.computeTriggerTime(calendar.timeInMillis, 9, 15)
        val computed = Calendar.getInstance().apply { timeInMillis = result }

        assertEquals(0, computed.get(Calendar.SECOND))
        assertEquals(0, computed.get(Calendar.MILLISECOND))
        assertTrue(result > calendar.timeInMillis)
    }
}
