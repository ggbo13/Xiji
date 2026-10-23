package com.xiji.habits

import android.content.Context
import androidx.startup.Initializer

/**
 * 占位说明：本项目不使用 androidx.startup 初始化任何组件。
 *
 * 依赖 Glance 时其传递依赖 androidx.work 会被排除（见 app/build.gradle.kts），
 * 因此不会创建 WorkManager 数据库，也不会有周期后台任务。
 */
internal object Noop
