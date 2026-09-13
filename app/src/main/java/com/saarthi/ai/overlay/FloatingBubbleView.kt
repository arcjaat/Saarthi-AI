package com.saarthi.ai.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The draggable floating microphone bubble that sits on the screen edge.
 *
 * Visual Design:
 * - 64x64dp circular badge
 * - Deep Sapphire Blue (#0F294A) fill
 * - Warm Amber (#F59E0B) pulsing glow ring
 * - White microphone icon centered
 *
 * Behavior:
 * - Draggable to any screen edge
 * - Tap to activate listening mode
 * - Subtle ambient pulsing animation when idle
 */
class FloatingBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Colors from design system
    private val sapphireBlue = Color.parseColor("#0F294A")
    private val warmAmber = Color.parseColor("#F59E0B")
    private val warmAmberGlow = Color.parseColor("#FEF3C7")
    private val micWhite = Color.WHITE

    // Paints
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sapphireBlue
        style = Paint.Style.FILL
    }

    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmAmber
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }

    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = micWhite
        style = Paint.Style.FILL
    }

    private val micStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = micWhite
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    // Animation
    private var pulseScale = 1.0f
    private var pulseAlpha = 80

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            pulseScale = 1.0f + (fraction * 0.15f)
            pulseAlpha = (40 + (fraction * 60)).toInt()
            invalidate()
        }
    }

    // Touch handling for drag
    var onBubbleTapped: (() -> Unit)? = null
    var onDragPositionChanged: ((Int, Int) -> Unit)? = null

    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var dragThreshold = 10f

    /** Whether the bubble is in active/listening state */
    var isActive: Boolean = false
        set(value) {
            field = value
            if (value) {
                glowRingPaint.color = Color.parseColor("#EF4444") // Red glow when recording
            } else {
                glowRingPaint.color = warmAmber
            }
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for shadow/glow effects
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(width, height) / 2f - 16f

        // Outer ambient glow (pulsing)
        outerGlowPaint.shader = RadialGradient(
            cx, cy, radius * pulseScale * 1.3f,
            intArrayOf(
                Color.argb(pulseAlpha, 245, 158, 11),
                Color.argb(pulseAlpha / 2, 254, 243, 199),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.5f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * pulseScale * 1.3f, outerGlowPaint)

        // Main amber ring
        canvas.drawCircle(cx, cy, radius + 3f, glowRingPaint)

        // Main circle (Deep Sapphire)
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // Microphone icon (drawn manually for sharp rendering)
        drawMicrophoneIcon(canvas, cx, cy, radius * 0.35f)
    }

    private fun drawMicrophoneIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // Mic body (rounded rectangle)
        val micRect = RectF(
            cx - size * 0.35f,
            cy - size * 0.9f,
            cx + size * 0.35f,
            cy + size * 0.2f
        )
        canvas.drawRoundRect(micRect, size * 0.35f, size * 0.35f, micPaint)

        // Mic arc (capturing shape below the body)
        val arcRect = RectF(
            cx - size * 0.55f,
            cy - size * 0.3f,
            cx + size * 0.55f,
            cy + size * 0.7f
        )
        canvas.drawArc(arcRect, 0f, 180f, false, micStrokePaint)

        // Stem line
        canvas.drawLine(cx, cy + size * 0.7f, cx, cy + size * 1.0f, micStrokePaint)

        // Base line
        canvas.drawLine(
            cx - size * 0.3f, cy + size * 1.0f,
            cx + size * 0.3f, cy + size * 1.0f,
            micStrokePaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold) {
                    isDragging = true
                    onDragPositionChanged?.invoke(
                        (initialX + dx).toInt(),
                        (initialY + dy).toInt()
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    performClick()
                    onBubbleTapped?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setInitialPosition(x: Int, y: Int) {
        initialX = x
        initialY = y
    }
}
