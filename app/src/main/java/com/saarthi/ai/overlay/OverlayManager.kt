package com.saarthi.ai.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/**
 * Central coordinator for all Saarthi overlay views drawn via WindowManager.
 *
 * Manages three overlay layers:
 * 1. [FloatingBubbleView] — Always-visible draggable mic bubble
 * 2. [ListeningSheetView] — Voice listening bottom sheet (shown during recording)
 * 3. [HighlightOverlayView] — Glowing bounding box (shown after AI inference)
 *
 * All views use TYPE_APPLICATION_OVERLAY (requires SYSTEM_ALERT_WINDOW permission).
 */
class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "SaarthiOverlay"
        private const val BUBBLE_SIZE_DP = 72
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    // Overlay views
    var bubbleView: FloatingBubbleView? = null
        private set
    var listeningSheet: ListeningSheetView? = null
        private set
    var highlightOverlay: HighlightOverlayView? = null
        private set

    // Layout params
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var isBubbleShowing = false
    private var isListeningShowing = false
    private var isHighlightShowing = false

    // ── Bubble Management ────────────────────────────────────────────

    /**
     * Shows the floating microphone bubble on the right edge of the screen.
     */
    fun showBubble() {
        if (isBubbleShowing) return

        val bubbleSizePx = (BUBBLE_SIZE_DP * density).toInt()

        bubbleParams = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = getScreenWidth() - bubbleSizePx - (16 * density).toInt()
            y = getScreenHeight() / 2
        }

        bubbleView = FloatingBubbleView(context).apply {
            onDragPositionChanged = { newX, newY ->
                updateBubblePosition(newX, newY)
            }
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
            isBubbleShowing = true
            bubbleView?.setInitialPosition(bubbleParams!!.x, bubbleParams!!.y)
            Log.d(TAG, "Floating bubble shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble: ${e.message}", e)
        }
    }

    /**
     * Removes the floating bubble from the screen.
     */
    fun hideBubble() {
        if (!isBubbleShowing || bubbleView == null) return
        try {
            windowManager.removeView(bubbleView)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing bubble: ${e.message}")
        }
        bubbleView = null
        isBubbleShowing = false
        Log.d(TAG, "Floating bubble hidden")
    }

    private fun updateBubblePosition(x: Int, y: Int) {
        bubbleParams?.let { params ->
            params.x = x
            params.y = y
            try {
                windowManager.updateViewLayout(bubbleView, params)
                bubbleView?.setInitialPosition(x, y)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating bubble position: ${e.message}")
            }
        }
    }

    // ── Listening Sheet Management ───────────────────────────────────

    /**
     * Shows the full-screen listening overlay (voice recording mode).
     */
    fun showListeningSheet() {
        if (isListeningShowing) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        listeningSheet = ListeningSheetView(context)

        try {
            windowManager.addView(listeningSheet, params)
            isListeningShowing = true
            Log.d(TAG, "Listening sheet shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show listening sheet: ${e.message}", e)
        }
    }

    /**
     * Hides the listening overlay.
     */
    fun hideListeningSheet() {
        if (!isListeningShowing || listeningSheet == null) return
        try {
            windowManager.removeView(listeningSheet)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing listening sheet: ${e.message}")
        }
        listeningSheet = null
        isListeningShowing = false
        Log.d(TAG, "Listening sheet hidden")
    }

    /**
     * Updates the listening sheet's amplitude for waveform visualization.
     */
    fun updateListeningAmplitude(amplitude: Float) {
        listeningSheet?.amplitude = amplitude
    }

    /**
     * Updates the real-time transcription text shown in the listening sheet.
     */
    fun updateTranscription(text: String) {
        listeningSheet?.transcriptionText = text
    }

    /**
     * Shows the processing state on the listening sheet.
     */
    fun setListeningProcessing(isProcessing: Boolean) {
        listeningSheet?.isProcessing = isProcessing
    }

    // ── Highlight Overlay Management ─────────────────────────────────

    /**
     * Shows the glowing highlight bounding box over the target element.
     *
     * @param bounds Screen-coordinate bounds of the target element
     * @param guidance The instruction text for the tooltip (in user's language)
     * @param action The action description (e.g., "Tap Here • यहाँ दबाएं")
     */
    fun showHighlight(bounds: Rect, guidance: String, action: String) {
        showHighlight(com.saarthi.ai.model.ScreenRect(bounds.left, bounds.top, bounds.right, bounds.bottom), guidance, action)
    }

    fun showHighlight(bounds: com.saarthi.ai.model.ScreenRect, guidance: String, action: String) {
        // Remove existing highlight if any
        hideHighlight()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        highlightOverlay = HighlightOverlayView(context).apply {
            guidanceText = guidance
            actionText = action
            setTargetBounds(bounds)
            onDismiss = {
                hideHighlight()
            }
        }

        try {
            windowManager.addView(highlightOverlay, params)
            isHighlightShowing = true
            Log.d(TAG, "Highlight overlay shown at bounds=$bounds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show highlight: ${e.message}", e)
        }
    }

    /**
     * Removes the highlight overlay.
     */
    fun hideHighlight() {
        if (!isHighlightShowing || highlightOverlay == null) return
        try {
            windowManager.removeView(highlightOverlay)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing highlight: ${e.message}")
        }
        highlightOverlay = null
        isHighlightShowing = false
        Log.d(TAG, "Highlight overlay hidden")
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    /**
     * Removes ALL overlay views. Called during service shutdown or kill switch.
     */
    fun removeAllOverlays() {
        hideHighlight()
        hideListeningSheet()
        hideBubble()
        Log.d(TAG, "All overlays removed")
    }

    // ── Utilities ────────────────────────────────────────────────────

    private fun getScreenWidth(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels
    }

    private fun getScreenHeight(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }
}
