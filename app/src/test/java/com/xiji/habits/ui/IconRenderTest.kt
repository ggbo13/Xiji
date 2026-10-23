package com.xiji.habits.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 用真实资源渲染启动器图标，确认不会被填充成实心块、且在圆形/圆角遮罩下构图合理。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconRenderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun renderLauncherIcons() {
        val outDir = File("build/icons").apply { mkdirs() }
        // 108dp 前景 + 背景，按自适应图标的 72dp 安全区裁切
        val size = 432
        val full = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        val bg = context.getDrawable(com.xiji.habits.R.drawable.ic_launcher_background)!!
        val fg = context.getDrawable(com.xiji.habits.R.drawable.ic_launcher_foreground)!!
        bg.setBounds(0, 0, size, size)
        bg.draw(canvas)
        // 前景在自适应图标中被放大 1.5 倍（108dp 内容映射到 72dp 可视区）
        val inset = (size * (1f - 72f / 108f) / 2f).toInt()
        fg.setBounds(-inset, -inset, size + inset, size + inset)
        fg.draw(canvas)
        File(outDir, "adaptive-full.png").outputStream().use {
            full.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // 圆形遮罩预览
        val masked = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c2 = Canvas(masked)
        val path = android.graphics.Path().apply {
            addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW)
        }
        c2.clipPath(path)
        c2.drawBitmap(full, 0f, 0f, null)
        File(outDir, "adaptive-circle.png").outputStream().use {
            masked.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // legacy PNG（API 26 以下）
        val legacy = context.getDrawable(com.xiji.habits.R.mipmap.ic_launcher)!!
        val lb = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c3 = Canvas(lb)
        legacy.setBounds(0, 0, 192, 192)
        legacy.draw(c3)
        File(outDir, "legacy.png").outputStream().use {
            lb.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
