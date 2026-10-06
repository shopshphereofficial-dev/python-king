package com.pythonking.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** ImageView with pinch-to-zoom, panning and double-tap-to-reset. */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private var scale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastTap = 0L

    private val detector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                applyScale(scale * d.scaleFactor)
                return true
            }
        }
    )

    fun resetZoom() {
        scale = 1f
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    }

    fun zoomIn() = applyScale(scale * 1.4f)

    fun zoomOut() = applyScale(scale / 1.4f)

    private fun applyScale(target: Float) {
        val next = target.coerceIn(1f, 8f)
        val ratio = next / scale
        scale = next
        scaleX = scale
        scaleY = scale
        translationX *= ratio
        translationY *= ratio
        clamp()
    }

    private fun clamp() {
        val mx = (scale - 1f) * width / 2f
        val my = (scale - 1f) * height / 2f
        translationX = translationX.coerceIn(-mx, mx)
        translationY = translationY.coerceIn(-my, my)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!detector.isInProgress && scale > 1f) {
                    translationX += event.x - lastX
                    translationY += event.y - lastY
                    clamp()
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = Math.abs(event.x - downX) > 16f || Math.abs(event.y - downY) > 16f
                if (!moved) {
                    val now = System.currentTimeMillis()
                    if (now - lastTap < 300) resetZoom()
                    lastTap = now
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
