package com.saarthi.ai.ai

import com.saarthi.ai.model.ScreenRect

/**
 * Represents the AI's determination of which UI element matches the user's intent.
 *
 * Produced by [GeminiResponseParser] from the raw Gemini Nano response text.
 * Consumed by the Floating Overlay to draw the glowing highlight box.
 *
 * LIFECYCLE: Volatile RAM only. Destroyed after the highlight is drawn.
 */
data class TargetElementResult(
    /** The node index from the UI tree that the AI identified as the target */
    val nodeIndex: Int,

    /** Exact screen bounding box of the target element (pixels) */
    val bounds: ScreenRect,

    /** Short, elder-friendly instruction in the user's language to show in the tooltip */
    val guidanceText: String,

    /** Confidence note from the AI (e.g., "high", "medium", "guessing") */
    val confidence: String,

    /** The simplified action description (e.g., "Tap this button") */
    val actionDescription: String
) {
    /** Center X coordinate of the target element */
    val centerX: Int get() = bounds.centerX

    /** Center Y coordinate of the target element */
    val centerY: Int get() = bounds.centerY

    /** Whether the bounds look valid (non-zero area, within reasonable screen range) */
    val isValid: Boolean get() = bounds.isValid
}

/**
 * Sealed class representing the outcome of a Gemini Nano inference cycle.
 */
sealed class InferenceResult {
    /** AI successfully identified a target element on screen */
    data class Success(val target: TargetElementResult) : InferenceResult()

    /** AI understood the intent but could not find a matching element on the current screen */
    data class NotFound(
        val reason: String,
        val suggestion: String
    ) : InferenceResult()

    /** AI inference failed due to a technical error */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : InferenceResult()

    /** AICore / Gemini Nano is not available on this device */
    data class Unavailable(val reason: String) : InferenceResult()
}
