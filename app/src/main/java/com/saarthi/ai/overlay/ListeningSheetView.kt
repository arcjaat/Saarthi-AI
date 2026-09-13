package com.saarthi.ai.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The voice listening overlay sheet displayed when the user taps the floating bubble.
 *
 * Designed specifically for older adults:
 * - Proper DP-scaled typography and touch targets.
 * - Prominent top-right '✕' dismiss button + bottom 'Cancel' button.
 * - Animated waveform bars.
 * - Live speech transcription display.
 * - Quick Action Chips: allows tapping common actions (Check Balance, Send Money, Scan QR)
 *   even in noisy environments or without speaking.
 */
class ListeningSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    // Colors
    private val sapphireBlue = Color.parseColor("#0F294A")
    private val warmAmber = Color.parseColor("#F59E0B")
    private val warmAmberGlow = Color.parseColor("#FEF3C7")
    private val cardWhite = Color.WHITE
    private val textDark = Color.parseColor("#0B192C")
    private val textSecondary = Color.parseColor("#475569")
    private val dimBackground = Color.argb(140, 0, 0, 0)
    private val cancelGrey = Color.parseColor("#E2E8F0")
    private val cancelText = Color.parseColor("#1E293B")
    private val chipBg = Color.parseColor("#F1F5F9")
    private val chipBorder = Color.parseColor("#CBD5E1")

    // Paints
    private val dimPaint = Paint().apply { color = dimBackground }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardWhite
        setShadowLayer(24f * density, 0f, -6f * density, Color.argb(80, 0, 0, 0))
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sapphireBlue
        textSize = 22f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary
        textSize = 15f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val transcriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textDark
        textSize = 17f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        textSize = 16f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val closeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9")
    }

    private val closeXPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chipBg
    }

    private val chipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chipBorder
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sapphireBlue
        textSize = 13f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Dynamic State
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
                statusText = "Processing... स्क्रीन पर बटन ढूंढ रहे हैं"
            }
            invalidate()
        }

    var amplitude: Float = 0.2f
        set(value) {
            field = value.coerceIn(0.1f, 1f)
            invalidate()
        }

    // Waveform
    private val barCount = 7
    private val barAmplitudes = FloatArray(barCount) { 0.3f }
    private var animPhase = 0f

    private val waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1400L
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
    var onQuickActionTapped: ((String) -> Unit)? = null

    // Touch Target Rectangles
    private val closeButtonRect = RectF()
    private val cancelButtonRect = RectF()
    private val chipRects = mutableListOf<RectF>()
    private val quickActionIntents = listOf(
        "Check Bank Balance",
        "Send Money",
        "Scan QR Code"
    )
    private val quickActionLabels = listOf(
        "💳 बैलेंस देखें",
        "💸 पैसे भेजें",
        "📷 QR स्कैन"
    )

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
            barAmplitudes[i] = 0.2f + (sinWave * 0.4f) + (amplitude * 0.4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Dim background
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        // 2. Bottom Card: min 380dp tall or 48% screen height
        val cardHeight = Math.max(390f * density, h * 0.48f)
        val cardTop = h - cardHeight
        val cardRect = RectF(0f, cardTop, w, h + 50f * density)

        canvas.drawRoundRect(cardRect, 32f * density, 32f * density, cardPaint)

        // 3. Header Title & Top Close '✕' Button
        val headerY = cardTop + 42f * density
        canvas.drawText("Saarthi is Listening...", 24f * density, headerY, headerPaint)

        // Top Close '✕' Button (48dp x 48dp touch target)
        val closeBtnSize = 36f * density
        val closeRight = w - 20f * density
        val closeLeft = closeRight - closeBtnSize
        val closeTop = cardTop + 18f * density
        val closeBottom = closeTop + closeBtnSize
        closeButtonRect.set(closeLeft - 6f * density, closeTop - 6f * density, closeRight + 6f * density, closeBottom + 6f * density)

        val closeCx = (closeLeft + closeRight) / 2
        val closeCy = (closeTop + closeBottom) / 2
        canvas.drawCircle(closeCx, closeCy, closeBtnSize / 2, closeCirclePaint)

        val xRadius = 7f * density
        canvas.drawLine(closeCx - xRadius, closeCy - xRadius, closeCx + xRadius, closeCy + xRadius, closeXPaint)
        canvas.drawLine(closeCx + xRadius, closeCy - xRadius, closeCx - xRadius, closeCy + xRadius, closeXPaint)

        // 4. Waveform Bars
        val barAreaTop = cardTop + 72f * density
        val barMaxHeight = 44f * density
        val barWidth = 8f * density
        val barSpacing = 8f * density
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val barStartX = (w - totalBarsWidth) / 2

        for (i in 0 until barCount) {
            val barHeight = barMaxHeight * barAmplitudes[i]
            val x = barStartX + i * (barWidth + barSpacing)
            val barTop = barAreaTop + (barMaxHeight - barHeight) / 2
            val barRect = RectF(x, barTop, x + barWidth, barTop + barHeight)
            barPaint.alpha = (160 + barAmplitudes[i] * 95).toInt().coerceIn(160, 255)
            canvas.drawRoundRect(barRect, barWidth / 2, barWidth / 2, barPaint)
        }

        // 5. Status text
        val statusY = barAreaTop + barMaxHeight + 28f * density
        canvas.drawText(statusText, w / 2, statusY, statusPaint)

        // 6. Real-time Live Transcription Text
        val transY = statusY + 28f * density
        if (transcriptionText.isNotBlank()) {
            val displayed = if (transcriptionText.length > 36) {
                "\"..." + transcriptionText.takeLast(33) + "\""
            } else {
                "\"$transcriptionText\""
            }
            canvas.drawText(displayed, w / 2, transY, transcriptionPaint)
        } else {
            canvas.drawText("(Speak in Hindi, English, or your regional language)", w / 2, transY, statusPaint)
        }

        // 7. Quick Action Chips (Tap to ask immediately without speech)
        val chipsTop = transY + 22f * density
        val chipHeight = 44f * density
        val chipSpacing = 10f * density
        val chipWidth = (w - (48f * density) - (chipSpacing * 2)) / 3

        chipRects.clear()
        for (i in quickActionLabels.indices) {
            val cx = 24f * density + i * (chipWidth + chipSpacing)
            val rect = RectF(cx, chipsTop, cx + chipWidth, chipsTop + chipHeight)
            chipRects.add(rect)

            canvas.drawRoundRect(rect, 16f * density, 16f * density, chipBgPaint)
            canvas.drawRoundRect(rect, 16f * density, 16f * density, chipBorderPaint)

            val textCenterY = chipsTop + (chipHeight / 2) + 5f * density
            canvas.drawText(quickActionLabels[i], rect.centerX(), textCenterY, chipTextPaint)
        }

        // 8. Bottom Cancel Button (Properly positioned above navigation bar)
        val cancelWidth = w - 48f * density
        val cancelHeight = 52f * density
        val cancelLeft = 24f * density
        val cancelTop = cardTop + cardHeight - cancelHeight - 34f * density
        cancelButtonRect.set(cancelLeft, cancelTop, cancelLeft + cancelWidth, cancelTop + cancelHeight)

        canvas.drawRoundRect(cancelButtonRect, 18f * density, 18f * density, cancelBgPaint)
        canvas.drawText("Cancel • बंद करें", w / 2, cancelTop + (cancelHeight / 2) + 6f * density, cancelTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y

            // Top close button
            if (closeButtonRect.contains(x, y)) {
                onCancelTapped?.invoke()
                return true
            }

            // Bottom cancel button
            if (cancelButtonRect.contains(x, y)) {
                onCancelTapped?.invoke()
                return true
            }

            // Quick action chips
            for (i in chipRects.indices) {
                if (chipRects[i].contains(x, y)) {
                    onQuickActionTapped?.invoke(quickActionIntents[i])
                    return true
                }
            }

            // Tap on upper dimmed background to dismiss
            val cardHeight = Math.max(390f * density, height.toFloat() * 0.48f)
            val cardTop = height.toFloat() - cardHeight
            if (y < cardTop) {
                onCancelTapped?.invoke()
                return true
            }
        }
        return true
    }
}
