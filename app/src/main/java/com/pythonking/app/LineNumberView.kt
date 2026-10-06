package com.pythonking.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat

/** Gutter that draws line numbers next to an EditText. */
class LineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var target: EditText? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = ContextCompat.getColor(context, R.color.gutter_text)
        textAlign = Paint.Align.RIGHT
    }
    private val bgColor = ContextCompat.getColor(context, R.color.gutter_bg)

    fun setTextSizePx(px: Float) {
        paint.textSize = px
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bgColor)
        val et = target ?: return
        val layout = et.layout ?: return
        val text = et.text ?: return

        canvas.save()
        canvas.translate(0f, -et.scrollY.toFloat())
        val x = width - paddingEnd.toFloat() - 6f
        var logical = 0
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val isStart = start == 0 || (start > 0 && text[start - 1] == '\n')
            if (!isStart) continue
            logical++
            val baseline = layout.getLineBaseline(i).toFloat()
            canvas.drawText(logical.toString(), x, baseline, paint)
        }
        canvas.restore()
    }
}
