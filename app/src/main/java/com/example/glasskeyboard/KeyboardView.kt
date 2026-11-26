package com.example.glasskeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onPaste()
        fun onHide()
    }

    private enum class Kind { CHAR, SHIFT, BACKSPACE, MODE, LANG, SPACE, ENTER, PASTE, HIDE }

    private class Key(val kind: Kind, val label: String = "", val weight: Float = 1f) {
        val rect = RectF()
    }

    var listener: Listener? = null

    private var russian = false
    private var symbols = false
    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val topBarH = 52 * d
    private val rowH = 54 * d
    private val gap = 6 * d
    private val sidePad = 6 * d
    private val bottomPad = 16 * d
    private val keyRadius = 12 * d
    private val panelRadius = 28 * d

    private var rows: List<List<Key>> = emptyList()
    private val topKeys = listOf(Key(Kind.PASTE), Key(Kind.HIDE))
    private val pressed = HashMap<Int, Key>()

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * d
        color = 0x33FFFFFF
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * d
        color = 0x22FFFFFF
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 22 * sp
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val panelRect = RectF()

    private val handler = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            handler.postDelayed(this, 45)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (topBarH + rowH * 4 + bottomPad).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        panelPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xE0273240.toInt(), 0xEE121820.toInt(), Shader.TileMode.CLAMP
        )
        layoutKeys()
    }

    private fun buildRows() {
        val lines = when {
            symbols -> listOf("1234567890", "-/:;()₽&@\"", "")
            russian -> listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        val r = ArrayList<List<Key>>()
        r.add(lines[0].map { Key(Kind.CHAR, it.toString()) })
        r.add(lines[1].map { Key(Kind.CHAR, it.toString()) })
        if (symbols) {
            r.add(
                ".,?!'%*".map { Key(Kind.CHAR, it.toString()) } + Key(Kind.BACKSPACE, "", 1.5f)
            )
        } else {
            r.add(
                listOf(Key(Kind.SHIFT, "", 1.5f)) +
                    lines[2].map { Key(Kind.CHAR, it.toString()) } +
                    Key(Kind.BACKSPACE, "", 1.5f)
            )
        }
        r.add(
            listOf(
                Key(Kind.MODE, if (symbols) "ABC" else "123", 1.5f),
                Key(Kind.CHAR, ",", 1f),
                Key(Kind.SPACE, "", 4f),
                Key(Kind.LANG, if (russian) "RU" else "EN", 1.3f),
                Key(Kind.ENTER, "", 1.7f)
            )
        )
        rows = r
    }

    private fun layoutKeys() {
        if (width == 0) return
        buildRows()
        val w = width - 2 * sidePad
        val sum0 = rows[0].sumOf { it.weight.toDouble() }.toFloat()
        val unit0 = (w - gap * (rows[0].size - 1)) / sum0
        for ((i, row) in rows.withIndex()) {
            val sum = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = if (i == 3) (w - gap * (row.size - 1)) / sum else unit0
            val total = sum * unit + gap * (row.size - 1)
            var x = sidePad + (w - total) / 2
            val top = topBarH + i * rowH
            for (k in row) {
                val kw = k.weight * unit
                k.rect.set(x, top + gap / 2, x + kw, top + rowH - gap / 2)
                x += kw + gap
            }
        }
        val btn = 38 * d
        val cy = topBarH / 2 + 2 * d
        val hide = topKeys[1].rect
        hide.set(width - sidePad - 10 * d - btn, cy - btn / 2, width - sidePad - 10 * d, cy + btn / 2)
        val paste = topKeys[0].rect
        paste.set(hide.left - 12 * d - btn, cy - btn / 2, hide.left - 12 * d, cy + btn / 2)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        panelRect.set(0f, 0f, width.toFloat(), height + panelRadius)
        c.drawRoundRect(panelRect, panelRadius, panelRadius, panelPaint)
        c.drawRoundRect(panelRect, panelRadius, panelRadius, panelStroke)

        for (k in topKeys) drawTopKey(c, k)
        for (row in rows) for (k in row) drawKey(c, k)
    }

    private fun isPressed(k: Key) = pressed.containsValue(k)

    private fun drawTopKey(c: Canvas, k: Key) {
        keyPaint.color = if (isPressed(k)) 0x66FFFFFF else 0x33FFFFFF
        c.drawOval(k.rect, keyPaint)
        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        iconPaint.style = Paint.Style.STROKE
        iconPaint.color = 0xFFFFFFFF.toInt()
        when (k.kind) {
            Kind.PASTE -> {
                val w = 7 * d
                val h = 9 * d
                c.drawRoundRect(cx - w, cy - h, cx + w, cy + h, 3 * d, 3 * d, iconPaint)
                c.drawLine(cx - 3.5f * d, cy - 2 * d, cx + 3.5f * d, cy - 2 * d, iconPaint)
                c.drawLine(cx - 3.5f * d, cy + 2 * d, cx + 3.5f * d, cy + 2 * d, iconPaint)
            }
            Kind.HIDE -> {
                path.reset()
                path.moveTo(cx - 6 * d, cy - 2.5f * d)
                path.lineTo(cx, cy + 3.5f * d)
                path.lineTo(cx + 6 * d, cy - 2.5f * d)
                c.drawPath(path, iconPaint)
            }
            else -> {}
        }
    }

    private fun drawKey(c: Canvas, k: Key) {
        val isLetterLike = k.kind == Kind.CHAR || k.kind == Kind.SPACE
        val active = k.kind == Kind.SHIFT && (shifted || capsLock)
        keyPaint.color = when {
            isPressed(k) -> 0x88FFFFFF.toInt()
            active -> 0xDDFFFFFF.toInt()
            isLetterLike -> 0x42FFFFFF
            else -> 0x28FFFFFF
        }
        c.drawRoundRect(k.rect, keyRadius, keyRadius, keyPaint)
        c.drawRoundRect(k.rect, keyRadius, keyRadius, keyStroke)

        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        val fg = if (active) 0xFF1B2430.toInt() else 0xFFFFFFFF.toInt()
        iconPaint.color = fg
        textPaint.color = fg

        when (k.kind) {
            Kind.CHAR -> {
                val label = if (symbols) k.label else k.label.uppercase()
                textPaint.textSize = 22 * sp
                c.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            Kind.MODE, Kind.LANG -> {
                textPaint.textSize = 17 * sp
                c.drawText(k.label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            Kind.SPACE -> {
                textPaint.textSize = 14 * sp
                textPaint.color = 0x99FFFFFF.toInt()
                val name = if (russian) "Русский" else "English"
                c.drawText(name, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            Kind.ENTER -> {
                textPaint.textSize = 20 * sp
                c.drawText("↵", cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            Kind.SHIFT -> {
                val s = 9 * d
                path.reset()
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s * 0.4f, cy)
                path.lineTo(cx + s * 0.4f, cy + s * 0.9f)
                path.lineTo(cx - s * 0.4f, cy + s * 0.9f)
                path.lineTo(cx - s * 0.4f, cy)
                path.lineTo(cx - s, cy)
                path.close()
                iconPaint.style = if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                c.drawPath(path, iconPaint)
                if (capsLock) {
                    c.drawLine(cx - s * 0.4f, cy + s * 1.35f, cx + s * 0.4f, cy + s * 1.35f, iconPaint)
                }
                iconPaint.style = Paint.Style.STROKE
            }
            Kind.BACKSPACE -> {
                val s = 8 * d
                path.reset()
                path.moveTo(cx - s * 1.3f, cy)
                path.lineTo(cx - s * 0.5f, cy - s)
                path.lineTo(cx + s * 1.3f, cy - s)
                path.lineTo(cx + s * 1.3f, cy + s)
                path.lineTo(cx - s * 0.5f, cy + s)
                path.close()
                c.drawPath(path, iconPaint)
                c.drawLine(cx - s * 0.1f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f, iconPaint)
                c.drawLine(cx + s * 0.7f, cy - s * 0.4f, cx - s * 0.1f, cy + s * 0.4f, iconPaint)
            }
            else -> {}
        }
        textPaint.color = 0xFFFFFFFF.toInt()
    }

    private fun hit(x: Float, y: Float): Key? {
        if (y < topBarH) {
            return topKeys.firstOrNull { it.rect.contains(x, y) }
        }
        val i = ((y - topBarH) / rowH).toInt()
        if (i !in rows.indices) return null
        val row = rows[i]
        for ((j, k) in row.withIndex()) {
            val left = if (j == 0) 0f else k.rect.left - gap / 2
            val right = if (j == row.size - 1) width.toFloat() else k.rect.right + gap / 2
            if (x in left..right) return k
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = hit(e.getX(i), e.getY(i))
                if (k != null) {
                    pressed[e.getPointerId(i)] = k
                    perform(k)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val k = pressed.remove(e.getPointerId(e.actionIndex))
                if (k?.kind == Kind.BACKSPACE) handler.removeCallbacks(repeatRunnable)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed.clear()
                handler.removeCallbacks(repeatRunnable)
                invalidate()
            }
        }
        return true
    }

    private fun perform(k: Key) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (k.kind) {
            Kind.CHAR -> {
                val upper = !symbols && (shifted || capsLock)
                listener?.onText(if (upper) k.label.uppercase() else k.label)
                if (shifted && !capsLock) shifted = false
            }
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                when {
                    now - lastShiftTap < 350 -> { capsLock = true; shifted = true }
                    capsLock -> { capsLock = false; shifted = false }
                    else -> shifted = !shifted
                }
                lastShiftTap = now
            }
            Kind.BACKSPACE -> {
                listener?.onBackspace()
                handler.removeCallbacks(repeatRunnable)
                handler.postDelayed(repeatRunnable, 400)
            }
            Kind.MODE -> { symbols = !symbols; layoutKeys() }
            Kind.LANG -> { russian = !russian; symbols = false; layoutKeys() }
            Kind.SPACE -> listener?.onText(" ")
            Kind.ENTER -> listener?.onEnter()
            Kind.PASTE -> listener?.onPaste()
            Kind.HIDE -> listener?.onHide()
        }
    }

    fun resetState() {
        shifted = false
        capsLock = false
        symbols = false
        pressed.clear()
        handler.removeCallbacks(repeatRunnable)
        layoutKeys()
    }
} 
