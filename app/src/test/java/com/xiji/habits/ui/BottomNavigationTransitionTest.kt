package com.xiji.habits.ui

import com.xiji.habits.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavigationTransitionTest {

    @Test
    fun `switching to a tab on the right moves forward`() {
        assertEquals(1, bottomTabSwitchDirection(Routes.TODAY, Routes.CALENDAR))
        assertEquals(1, bottomTabSwitchDirection(Routes.CALENDAR, Routes.STATS))
    }

    @Test
    fun `switching back moves in the opposite direction`() {
        assertEquals(-1, bottomTabSwitchDirection(Routes.SETTINGS, Routes.TODAY))
        assertEquals(-1, bottomTabSwitchDirection(Routes.STATS, Routes.CALENDAR))
    }

    @Test
    fun `detail pages keep the existing transition`() {
        assertNull(bottomTabSwitchDirection(Routes.TODAY, Routes.habitDetail(1)))
        assertNull(bottomTabSwitchDirection(Routes.BACKUP, Routes.SETTINGS))
        assertNull(bottomTabSwitchDirection(Routes.TODAY, Routes.TODAY))
    }
}
