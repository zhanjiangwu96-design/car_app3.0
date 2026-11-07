package com.example.mecanumcar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 布局 (横屏):
 *   左侧:  ↑ 前进 / ↓ 后退 (大按键, 竖着排)
 *   右侧:  上排 A 左转 | B 右转 | C 加速 | D 减速     下排 ← 左移 | 巡线 | → 右移   (按行列对齐)
 * 左手按 ↑↓, 右手按 ←→, 同时按 (例如 ↑ + →) 就是斜向移动; 相反方向同时按会抵消。
 * 对外: joyX / joyY (-100, 0, 100)、pressed[A,B,C,D]、lineOn (巡线开关, 点一下切换)
 * 发送协议: DD 77 X Y A B C D  (A 字节的 bit1 = 巡线开)
 */
class ControlView(ctx: Context) : View(ctx) {

    var joyX = 0
        private set
    var joyY = 0
        private set
    val pressed = BooleanArray(4)           // A B C D
    var lineOn = false
        private set

    private val on = BooleanArray(9)
    private var lineDownPrev = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 序号: 0=↑ 1=↓ 2=← 3=→ 4=A 5=B 6=C 7=D 8=巡线
    private val label = arrayOf("↑", "↓", "←", "→", "A", "B", "C", "D", "巡线")
    private val hint = arrayOf("", "", "", "", "左转", "右转", "加速", "减速", "")
    private val bx = floatArrayOf(0.22f, 0.22f, 0.60f, 0.92f, 0.60f, 0.7067f, 0.8133f, 0.92f, 0.76f)
    private val by = floatArrayOf(0.38f, 0.72f, 0.70f, 0.70f, 0.34f, 0.34f, 0.34f, 0.34f, 0.70f)
    private val br = floatArrayOf(0.15f, 0.15f, 0.15f, 0.15f, 0.075f, 0.075f, 0.075f, 0.075f, 0.11f)  // 相对高度

    fun setLineOn(v: Boolean) {
        lineOn = v
        invalidate()
    }

    private fun hit(i: Int, x: Float, y: Float) =
        hypot(x - bx[i] * width, y - by[i] * height) <= br[i] * height * 1.1f

    private fun resetAll() {
        joyX = 0
        joyY = 0
        for (i in 0..8) on[i] = false
        for (i in 0..3) pressed[i] = false
        lineDownPrev = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        if (act == MotionEvent.ACTION_CANCEL || act == MotionEvent.ACTION_UP) {
            resetAll()
            invalidate()
            return true
        }
        val upIdx = if (act == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val now = BooleanArray(9)
        for (p in 0 until e.pointerCount) {
            if (p == upIdx) continue
            val x = e.getX(p)
            val y = e.getY(p)
            for (i in 0..8) if (hit(i, x, y)) now[i] = true
        }
        for (i in 0..8) on[i] = now[i]

        // 方向: ↑ ↓ ← →
        var sx = 0
        var sy = 0
        if (now[0]) sy += 1
        if (now[1]) sy -= 1
        if (now[2]) sx -= 1
        if (now[3]) sx += 1
        joyX = sx * 100
        joyY = sy * 100
        for (b in 0..3) pressed[b] = now[4 + b]

        // 巡线键: 按下的瞬间切换一次
        if (now[8] && !lineDownPrev) lineOn = !lineOn
        lineDownPrev = now[8]

        invalidate()
        return true
    }

    private fun drawCenteredText(c: Canvas, s: String, cx: Float, cy: Float) {
        c.drawText(s, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0B1B2E.toInt())
        paint.textAlign = Paint.Align.CENTER

        for (i in 0..8) {
            val cx = bx[i] * width
            val cy = by[i] * height
            val r = br[i] * height
            val active = if (i == 8) lineOn else on[i]

            paint.style = Paint.Style.FILL
            paint.color = when {
                i == 8 && lineOn -> 0xFF66BB6A.toInt()         // 巡线开: 绿色
                active -> 0xFF4FC3F7.toInt()
                else -> 0x44FFFFFF
            }
            c.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0x99FFFFFF.toInt()
            c.drawCircle(cx, cy, r, paint)

            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFFFFF.toInt()
            when {
                i <= 3 -> {                                     // 方向键: 大箭头
                    paint.textSize = r * 1.1f
                    drawCenteredText(c, label[i], cx, cy)
                }
                i <= 7 -> {                                     // A B C D
                    paint.textSize = r * 0.8f
                    drawCenteredText(c, label[i], cx, cy - r * 0.12f)
                    paint.textSize = r * 0.30f
                    paint.color = 0xCCFFFFFF.toInt()
                    drawCenteredText(c, hint[i], cx, cy + r * 0.55f)
                }
                else -> {                                       // 巡线
                    paint.textSize = r * 0.55f
                    drawCenteredText(c, label[i], cx, cy - r * 0.12f)
                    paint.textSize = r * 0.32f
                    paint.color = 0xCCFFFFFF.toInt()
                    drawCenteredText(c, if (lineOn) "开" else "关", cx, cy + r * 0.5f)
                }
            }
        }

        // 调试信息
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB0BEC5.toInt()
        paint.textSize = height * 0.045f
        drawCenteredText(c, "X=$joyX  Y=$joyY  巡线:" + (if (lineOn) "开" else "关"), width * 0.5f, height * 0.94f)
    }
}
