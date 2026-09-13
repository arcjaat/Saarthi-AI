package com.saarthi.ai.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.saarthi.ai.parser.AccessibilityTreeParser
import com.saarthi.ai.parser.ParseResult

/**
 * Screen-Aware Accessibility Service for Saarthi AI.
 *
 * This is the core system-level component that enables Saarthi to "see"
 * what is on the user's screen WITHOUT screenshots. It leverages
 * [getRootInActiveWindow] to access the live AccessibilityNodeInfo tree,
 * which works even on apps with FLAG_SECURE (e.g., banking apps).
 *
 * PRIVACY ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────┐
 * │  Active App (PhonePe, GPay, DigiLocker, etc.)           │
 * │  ↓ getRootInActiveWindow()                              │
 * │  AccessibilityNodeInfo tree (system-managed, volatile)  │
 * │  ↓ AccessibilityTreeParser.parseActiveWindow()          │
 * │  Recursive DFS → extract text + bounds                  │
 * │  ↓ SensitiveDataRedactor.redact() [IN-PLACE]            │
 * │  Sanitized UiNode list (RAM only)                       │
 * │  ↓ formatForPrompt()                                    │
 * │  Clean text prompt string                               │
 * │  ↓ Gemini Nano (AICore, on-device)  [Phase 4]           │
 * │  Target node index + coordinates                        │
 * │  ↓ WindowManager overlay            [Phase 5]           │
 * │  Glowing highlight box drawn on screen                  │
 * │  ↓ GC: All UiNode + prompt strings destroyed            │
 * └─────────────────────────────────────────────────────────┘
 *
 * ZERO PERSISTENCE: Nothing is written to disk. The ParseResult and all
 * UiNode objects are garbage collected after each inference cycle.
 */
class SaarthiAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SaarthiA11y"

        /** Singleton reference for the floating overlay to request screen parses */
        @Volatile
        var instance: SaarthiAccessibilityService? = null
            private set

        /** Minimum time between automatic tree parses to avoid excessive CPU usage */
        private const val MIN_PARSE_INTERVAL_MS = 1500L
    }

    /** Timestamp of the last automatic parse (throttle guard) */
    private var lastParseTimestamp: Long = 0L

    /** The most recent parse result, held ONLY in volatile memory */
    @Volatile
    private var latestParseResult: ParseResult = ParseResult.EMPTY

    /** Callback listener for when a new parse result is available */
    @Volatile
    var onScreenParsed: ((ParseResult) -> Unit)? = null

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Saarthi Accessibility Service CONNECTED")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        latestParseResult = ParseResult.EMPTY
        onScreenParsed = null
        Log.i(TAG, "Saarthi Accessibility Service DESTROYED — all volatile data cleared")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    // ── Event Handling ───────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // We track window state changes and content changes so that
        // when the user speaks and we need a fresh parse, we know
        // the current window context. We do NOT auto-parse on every
        // event to save battery — parsing is triggered on-demand by
        // the floating widget when the user speaks.

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return

                // Skip our own app's events
                if (pkg == packageName) return

                Log.d(TAG, "Window changed → $pkg")
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content changed — the latest cached parse may be stale.
                // We intentionally do NOT auto-re-parse here to save CPU.
                // The floating widget will request a fresh parse when needed.
            }
        }
    }

    // ── Public API (called by FloatingOverlayService / Gemini pipeline) ─

    /**
     * Performs an on-demand parse of the currently active window.
     *
     * Called by the floating overlay when the user taps the mic and speaks.
     * The full tree is traversed, all text is redacted in-place, and the
     * result is stored in volatile memory only.
     *
     * @return A [ParseResult] with redacted UiNodes and their screen coordinates.
     *         The caller must NOT persist this to disk.
     */
    fun parseCurrentScreen(): ParseResult {
        val rootNode: AccessibilityNodeInfo? = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get rootInActiveWindow: ${e.message}")
            null
        }

        if (rootNode == null) {
            Log.w(TAG, "parseCurrentScreen: No active window available")
            return ParseResult.EMPTY
        }

        val result = AccessibilityTreeParser.parseActiveWindow(rootNode)

        // Store in volatile memory for immediate use
        latestParseResult = result

        // Notify listener (FloatingOverlayService / Gemini pipeline)
        onScreenParsed?.invoke(result)

        Log.d(TAG, "On-demand parse complete: ${result.totalNodesExtracted} nodes " +
                "from ${result.activePackageName}")

        return result
    }

    /**
     * Returns the formatted, redacted UI tree string ready to be embedded
     * in a Gemini Nano prompt alongside the user's voice intent.
     *
     * @param compactMode If true, returns only actionable (clickable/editable)
     *                    elements to reduce prompt token count.
     * @return Formatted string. All PII is already redacted.
     */
    fun getFormattedScreenForPrompt(compactMode: Boolean = false): String {
        val result = parseCurrentScreen()

        return if (compactMode) {
            AccessibilityTreeParser.formatActionableForPrompt(result)
        } else {
            AccessibilityTreeParser.formatForPrompt(result)
        }
    }

    /**
     * Returns the latest cached parse result WITHOUT re-traversing the tree.
     * Use this when you need the last known state and freshness is not critical.
     */
    fun getLatestParseResult(): ParseResult = latestParseResult

    /**
     * Force-clears all cached parse data from volatile memory.
     * Called after Gemini Nano inference completes to ensure zero data retention.
     */
    fun clearVolatileData() {
        latestParseResult = ParseResult.EMPTY
        Log.d(TAG, "Volatile parse data explicitly cleared from RAM")
    }
}
