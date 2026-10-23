package com.xiji.habits.ui.navigation

object Routes {
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    const val HABIT_EDIT = "habit/edit"        // habit/edit?habitId={id}
    const val HABIT_DETAIL = "habit/detail/{habitId}"
    const val BACKUP = "backup"
    const val CATEGORIES = "categories"
    const val ABOUT = "about"

    fun habitEdit(habitId: Long? = null) =
        if (habitId == null) "$HABIT_EDIT?habitId=-1" else "$HABIT_EDIT?habitId=$habitId"

    fun habitDetail(habitId: Long) = "habit/detail/$habitId"
}
