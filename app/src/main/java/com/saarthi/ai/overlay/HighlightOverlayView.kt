package com.saarthi.ai.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * The glowing highlight overlay drawn PRECISELY over the target UI element.
 *
 * When Gemini Nano identifies the correct button, this view is placed as a
 * transparent overlay on top of the entire screen, and draws:
 *
 * 1. A semi-transparent dim mask over the ENTIRE screen EXCEPT the target area
 * 2. A vivid 4dp glowing Warm Amber (#F59E0B) rounded bounding box with
 *    soft 8dp radial blur around the target element
 * 3. An attached high-contrast tooltip card with guidance text and an
 *    arrow pointing at the target
 *
 * This creates a "spotlight" effect that draws the elder's eye
 * directly to the button they need to press.
 */
class HighlightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Colors
    private val warmAmber = Color.parseColor("#F59E0B")
    private val warmAmberGlow = Color.parseColor("#FEF3C7")
    private val sapphireBlue = Color.parseColor("#0F294A")
    private val dimColor = Color.argb(120, 0, 0, 0) // Semi-transparent black mask
    private val tooltipBg = Color.WHITE
    private val tooltipText = Color.parseColor("#0B192C")

    // Target bounds (set by the overlay manager after AI inference)
    private var targetRect: RectF? = null

    // Guidance text shown in the tooltip
    var guidanceText: String = "👉 इस बटन को दबाएं"
        set(value) {
            field = value
            invalidate()
        }

    // Action badge text
    var actionText: String = "Tap Here • यहाँ दबाएं"
        set(value) {
            field = value
            invalidate()
        }

    // Paints
    private val dimPaint = Paint().apply {
        color = dimColor
        style = Paint.Style.FILL
    }

    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmAmber
        style = Paint.Style.STROKE
        strokeWidth = 8f
        pathEffect = CornerPathEffect(16f)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmAmberGlow
        style = Paint.Style.STROKE
        strokeWidth = 20f
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        pathEffect = CornerPathEffect(16f)
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tooltipBg
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 4f, Color.argb(80, 0, 0, 0))
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmAmber
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val guidanceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tooltipText
        textSize = 44f // ~20sp equivalent at ~2.2x density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val actionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sapphireBlue
        textSize = 36f // ~16sp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmAmber
        style = Paint.Style.FILL
    }

    // Animation
    private var glowAlpha = 200
    private val glowAnimator = ValueAnimator.ofInt(120, 255).apply {
        duration = 800L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { anim ->
            glowAlpha = anim.animatedValue as Int
            highlightStrokePaint.alpha = glowAlpha
            glowPaint.alpha = (glowAlpha * 0.4f).toInt()
            invalidate()
        }
    }

    // Dismiss callback
    var onDismiss: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for blur/shadow effects
        // Tap anywhere to dismiss
        setOnClickListener {
            onDismiss?.invoke()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glowAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator.cancel()
    }

    /**
     * Sets the target bounding box to highlight.
     * @param rect The screen-coordinate bounds of the target element
     */
    fun setTargetBounds(rect: Rect) {
        setTargetBounds(com.saarthi.ai.model.ScreenRect(rect.left, rect.top, rect.right, rect.bottom))
    }

    fun setTargetBounds(rect: com.saarthi.ai.model.ScreenRect) {
        // Add slight padding around the target for visual breathing room
        targetRect = RectF(
            rect.left - 8f,
            rect.top - 8f,
            rect.right + 8f,
            rect.bottom + 8f
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val target = targetRect ?: return

        // ── 1. Draw the dim mask with a cutout for the target ────────
        canvas.save()
        val screenRect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Create a path that covers the entire screen minus the target area
        val maskPath = Path().apply {
            addRect(screenRect, Path.Direction.CW)
            addRoundRect(target, 12f, 12f, Path.Direction.CCW)
        }
        maskPath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(maskPath, dimPaint)
        canvas.restore()

        // ── 2. Draw the outer glow (blurred amber) ──────────────────
        canvas.drawRoundRect(target, 12f, 12f, glowPaint)

        // ── 3. Draw the sharp highlight border ──────────────────────
        canvas.drawRoundRect(target, 12f, 12f, highlightStrokePaint)

        // ── 4. Draw the tooltip card ────────────────────────────────
        drawTooltipCard(canvas, target)
    }

    private fun drawTooltipCard(canvas: Canvas, target: RectF) {
        val tooltipPadding = 20f
        val tooltipHeight = 120f
        val tooltipMargin = 16f

        // Measure text
        val guidanceWidth = guidanceTextPaint.measureText(guidanceText)
        val actionWidth = actionTextPaint.measureText(actionText)
        val tooltipWidth = Math.max(guidanceWidth, actionWidth) + tooltipPadding * 2
        val maxWidth = width * 0.85f
        val finalWidth = Math.min(tooltipWidth, maxWidth)

        // Position tooltip: above the target by default, below if not enough space
        val tooltipTop: Float
        val arrowPointsDown: Boolean

        if (target.top > tooltipHeight + tooltipMargin + 40) {
            // Place above
            tooltipTop = target.top - tooltipHeight - tooltipMargin - 12f
            arrowPointsDown = true
        } else {
            // Place below
            tooltipTop = target.bottom + tooltipMargin + 12f
            arrowPointsDown = false
        }

        // Center tooltip horizontally relative to target, but keep within screen
        var tooltipLeft = target.centerX() - finalWidth / 2
        tooltipLeft = tooltipLeft.coerceIn(16f, width - finalWidth - 16f)

        val tooltipRect = RectF(
            tooltipLeft,
            tooltipTop,
            tooltipLeft + finalWidth,
            tooltipTop + tooltipHeight
        )

        // Draw tooltip background
        canvas.drawRoundRect(tooltipRect, 20f, 20f, tooltipBgPaint)
        canvas.drawRoundRect(tooltipRect, 20f, 20f, tooltipBorderPaint)

        // Draw arrow triangle pointing to target
        val arrowPath = Path()
        val arrowCx = target.centerX().coerceIn(tooltipRect.left + 30f, tooltipRect.right - 30f)

        if (arrowPointsDown) {
            arrowPath.moveTo(arrowCx - 14f, tooltipRect.bottom)
            arrowPath.lineTo(arrowCx, tooltipRect.bottom + 12f)
            arrowPath.lineTo(arrowCx + 14f, tooltipRect.bottom)
        } else {
            arrowPath.moveTo(arrowCx - 14f, tooltipRect.top)
            arrowPath.lineTo(arrowCx, tooltipRect.top - 12f)
            arrowPath.lineTo(arrowCx + 14f, tooltipRect.top)
        }
        arrowPath.close()
        canvas.drawPath(arrowPath, tooltipBgPaint)
        canvas.drawPath(arrowPath, arrowPaint)

        // Draw guidance text
        val textX = tooltipRect.left + tooltipPadding
        val textY = tooltipRect.top + 50f
        canvas.drawText(
            guidanceText,
            textX,
            textY,
            guidanceTextPaint
        )

        // Draw action text
        canvas.drawText(
            actionText,
            textX,
            textY + 44f,
            actionTextPaint
        )
    }

    /**
     * Clears the highlight and hides the overlay.
     */
    fun clearHighlight() {
        targetRect = null
        invalidate()
    }
}
