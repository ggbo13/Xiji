package com.xiji.habits.ui.components

/**
 * 习惯图标：使用系统 emoji 作为矢量无关的轻量图标，
 * 不依赖网络字体，也不增加 APK 体积。
 */
object HabitIcons {
    data class IconOption(val key: String, val emoji: String, val label: String)

    val options: List<IconOption> = listOf(
        IconOption("book", "📖", "阅读"),
        IconOption("run", "🏃", "运动"),
        IconOption("water", "💧", "喝水"),
        IconOption("meditate", "🧘", "冥想"),
        IconOption("sleep", "🌙", "睡眠"),
        IconOption("study", "✏️", "学习"),
        IconOption("code", "💻", "工作"),
        IconOption("music", "🎧", "音乐"),
        IconOption("food", "🥗", "饮食"),
        IconOption("no_sugar", "🚫", "戒除"),
        IconOption("smoke", "🚭", "戒烟"),
        IconOption("phone", "📵", "少刷手机"),
        IconOption("clean", "🧹", "整理"),
        IconOption("walk", "🚶", "散步"),
        IconOption("write", "📝", "写作"),
        IconOption("heart", "❤️", "健康"),
        IconOption("star", "⭐", "目标"),
        IconOption("sun", "☀️", "早起"),
        IconOption("leaf", "🌿", "生活"),
        IconOption("dumbbell", "🏋️", "健身")
    )

    fun emojiFor(key: String): String = options.firstOrNull { it.key == key }?.emoji ?: "⭐"
    fun labelFor(key: String): String = options.firstOrNull { it.key == key }?.label ?: "目标"
}
