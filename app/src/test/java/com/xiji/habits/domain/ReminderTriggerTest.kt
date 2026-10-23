package com.xiji.habits.domain

import com.xiji.habits.reminder.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTriggerTest {

    private fun toLocal(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    @Test
    fun `same weekday later today fires today`() {
        // 2026-03-09 是周一（index0 = 0）
        val now = LocalDateTime.of(2026, 3, 9, 7, 0)
        val trigger = ReminderScheduler.nextTrigger(8, 30, 0, now)
        assertEquals(LocalDateTime.of(2026, 3, 9, 8, 30), toLocal(trigger))
    }

    @Test
    fun `same weekday already passed fires next week`() {
        val now = LocalDateTime.of(2026, 3, 9, 9, 0)
        val trigger = ReminderScheduler.nextTrigger(8, 30, 0, now)
        assertEquals(LocalDateTime.of(2026, 3, 16, 8, 30), toLocal(trigger))
    }

    @Test
    fun `later weekday in same week`() {
        val now = LocalDateTime.of(2026, 3, 9, 9, 0) // 周一
        val trigger = ReminderScheduler.nextTrigger(21, 0, 4, now) // 周五
        assertEquals(LocalDateTime.of(2026, 3, 13, 21, 0), toLocal(trigger))
    }

    @Test
    fun `always strictly in the future`() {
        val now = LocalDateTime.of(2026, 3, 9, 8, 30)
        val trigger = ReminderScheduler.nextTrigger(8, 30, 0, now)
        assertTrue(toLocal(trigger).isAfter(now))
    }
}
