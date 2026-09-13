package com.saarthi.ai.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The voice listening overlay sheet displayed when the user taps the floating bubble.
 *
 * Appears as an elevated bottom card over a dimmed screen showing:
 * - "Saarthi is Listening... बोलिए" header
 * - Animated warm amber waveform bars (driven by real-time audio amplitude)
 * - Real-time speech transcription text
 * - A large "Cancel / बंद करें" button
 */
class ListeningSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Colors
    private val sapphireBlue = Color.parseColor("#0F294A")
    private val warmAmber = Color.parseColor("#F59E0B")
    private val warmAmberLight = Color.parseColor("#FEF3C7")
    private val cardWhite = Color.WHITE
    private val textDark = Color.parseColor("#0B192C")
    private val textSecondary = Color.parseColor("#334155")
    private val dimBackground = Color.argb(100, 0, 0, 0)
    private val cancelGrey = Color.parseColor("#E2E8F0")
    private val cancelText = Color.parseColor("#334155")

    // Paints
    private val dimPaint = Paint().apply { color = dimBackground }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardWhite
        setShadowLayer(20f, 0f, -8f, Color.argb(60, 0, 0, 0))
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sapphireBlue
        textSize = 52f // ~24sp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val transcriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textDark
        textSize = 44f // ~20sp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary
        textSize = 36f // ~16sp
        textAlign = Paint.Align.CENTER
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmAmber
        style = Paint.Style.FILL
    }

    private val cancelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cancelGrey
    }

    private val cancelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cancelText
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // State
    var transcriptionText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var statusText: String = "Listening... बोलिए, मैं सुन रहा हूँ"
        set(value) {
            field = value
            invalidate()
        }

    var isProcessing: Boolean = false
        set(value) {
            field = value
            if (value) {
                statusText = "Processing... समझ रहा हूँ"
            }
            invalidate()
        }

    /** Normalized audio amplitude [0.0, 1.0] for waveform visualization */
    var amplitude: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Waveform bar animation
    private val barCount = 7
    private val barAmplitudes = FloatArray(barCount) { 0.3f }
    private var animPhase = 0f

    private val waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            animPhase = anim.animatedValue as Float
            updateBarAmplitudes()
            invalidate()
        }
    }

    // Callbacks
    var onCancelTapped: (() -> Unit)? = null

    private var cancelButtonRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        waveAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator.cancel()
    }

    private fun updateBarAmplitudes() {
        for (i in 0 until barCount) {
            val phase = animPhase + (i * 360f / barCount)
            val sinWave = (Math.sin(Math.toRadians(phase.toDouble())).toFloat() + 1f) / 2f
            barAmplitudes[i] = 0.2f + (sinWave * 0.6f) + (amplitude * 0.4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // ── 1. Dim background ────────────────────────────────────────
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        // ── 2. Bottom card ───────────────────────────────────────────
        val cardHeight = h * 0.38f
        val cardTop = h - cardHeight
        val cardRect = RectF(0f, cardTop, w, h)

        canvas.drawRoundRect(
            RectF(0f, cardTop, w, h + 40f), // Extend below screen for rounded corners
            40f, 40f,
            cardPaint
        )

        // ── 3. Header text ───────────────────────────────────────────
        val headerY = cardTop + 65f
        canvas.drawText("Saarthi is Listening...", w / 2, headerY, headerPaint)

        // ── 4. Waveform bars ─────────────────────────────────────────
        val barAreaTop = headerY + 30f
        val barMaxHeight = 80f
        val barWidth = 18f
        val barSpacing = 14f
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val barStartX = (w - totalBarsWidth) / 2

        for (i in 0 until barCount) {
            val barHeight = barMaxHeight * barAmplitudes[i]
            val x = barStartX + i * (barWidth + barSpacing)
            val barTop = barAreaTop + (barMaxHeight - barHeight) / 2
            val barRect = RectF(x, barTop, x + barWidth, barTop + barHeight)
            barPaint.alpha = (180 + barAmplitudes[i] * 75).toInt()
            canvas.drawRoundRect(barRect, barWidth / 2, barWidth / 2, barPaint)
        }

        // ── 5. Status text ───────────────────────────────────────────
        val statusY = barAreaTop + barMaxHeight + 40f
        canvas.drawText(statusText, w / 2, statusY, statusPaint)

        // ── 6. Transcription text ────────────────────────────────────
        if (transcriptionText.isNotBlank()) {
            val transY = statusY + 50f
            // Truncate if too long for single line
            val displayed = if (transcriptionText.length > 40) {
                "..." + transcriptionText.takeLast(37)
            } else {
                transcriptionText
            }
            canvas.drawText(displayed, w / 2, transY, transcriptionPaint)
        }

        // ── 7. Cancel button ─────────────────────────────────────────
        val cancelWidth = 280f
        val cancelHeight = 60f
        val cancelLeft = (w - cancelWidth) / 2
        val cancelTop = h - cancelHeight - 50f
        cancelButtonRect = RectF(cancelLeft, cancelTop, cancelLeft + cancelWidth, cancelTop + cancelHeight)

        canvas.drawRoundRect(cancelButtonRect, 30f, 30f, cancelBgPaint)
        canvas.drawText("Cancel • बंद करें", w / 2, cancelTop + 42f, cancelTextPaint)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            if (cancelButtonRect.contains(event.x, event.y)) {
                onCancelTapped?.invoke()
                return true
            }
            // Tap on upper dimmed area outside bottom card to dismiss
            val cardHeight = height.toFloat() * 0.38f
            val cardTop = height.toFloat() - cardHeight
            if (event.y < cardTop) {
                onCancelTapped?.invoke()
                return true
            }
        }
        return true // Consume all touch events to prevent pass-through
    }
}
