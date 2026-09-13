package com.saarthi.ai.model

import android.graphics.Rect

/**
 * Lightweight, volatile-memory-only representation of a single UI element
 * extracted from the AccessibilityNodeInfo tree.
 *
 * PRIVACY CONTRACT:
 * - Instances exist ONLY in RAM during the inference lifecycle.
 * - Never serialized to disk, SharedPreferences, or SQLite.
 * - All text fields are pre-sanitized by SensitiveDataRedactor before
 *   being passed to Gemini Nano.
 * - Garbage collected immediately after AI inference completes.
 */
data class UiNode(
    /** Sequential index assigned during tree traversal (used by Gemini to reference nodes) */
    val nodeIndex: Int,

    /** Depth in the accessibility tree (0 = root). Aids the LLM in understanding hierarchy. */
    val depth: Int,

    /** Android widget class name, e.g. "android.widget.Button", "android.widget.TextView" */
    val className: String,

    /** The user-visible text displayed on the widget (redacted) */
    val text: String?,

    /** The content description for accessibility (redacted) */
    val contentDescription: String?,

    /** The Android resource view ID, e.g. "com.google.android.apps.nbu.paisa.user:id/send_money_btn" */
    val viewIdResourceName: String?,

    /** Absolute screen bounds [left, top, right, bottom] in pixels */
    val boundsInScreen: ScreenRect,

    /** Whether this node is clickable (critical for identifying actionable targets) */
    val isClickable: Boolean,

    /** Whether this node is currently enabled and interactable */
    val isEnabled: Boolean,

    /** Whether this node is visible to the user */
    val isVisibleToUser: Boolean,

    /** Whether this node is checkable (checkbox, switch, radio) */
    val isCheckable: Boolean,

    /** Whether this node is currently checked */
    val isChecked: Boolean,

    /** Whether this node is editable (input field) */
    val isEditable: Boolean,

    /** Whether this node is focusable */
    val isFocusable: Boolean,

    /** Whether this node is scrollable (list, scroll view) */
    val isScrollable: Boolean,

    /** The package name of the app owning this node */
    val packageName: String?
) {
    /**
     * Formats this node into a compact, LLM-friendly string representation.
     * Example:
     *   [3] Button "Send Money" clickable bounds=(120,450,540,520) id=send_money_btn
     */
    fun toPromptString(): String {
        val sb = StringBuilder()

        // Indent by depth for hierarchy clarity
        repeat(depth) { sb.append("  ") }

        // Node index for AI to reference
        sb.append("[$nodeIndex] ")

        // Simplified class name (strip android.widget. prefix for brevity)
        val shortClass = className
            .removePrefix("android.widget.")
            .removePrefix("android.view.")
            .removePrefix("androidx.compose.ui.platform.")
            .removePrefix("android.webkit.")
        sb.append(shortClass)

        // Text content
        if (!text.isNullOrBlank()) {
            sb.append(" text=\"$text\"")
        }

        // Content description
        if (!contentDescription.isNullOrBlank() && contentDescription != text) {
            sb.append(" desc=\"$contentDescription\"")
        }

        // Interaction properties (only append if true for brevity)
        val props = mutableListOf<String>()
        if (isClickable) props.add("clickable")
        if (isEditable) props.add("editable")
        if (isCheckable) props.add(if (isChecked) "checked" else "checkable")
        if (isScrollable) props.add("scrollable")
        if (!isEnabled) props.add("disabled")
        if (props.isNotEmpty()) {
            sb.append(" [${props.joinToString(",")}]")
        }

        // Bounding box coordinates
        sb.append(" bounds=(${boundsInScreen.left},${boundsInScreen.top},${boundsInScreen.right},${boundsInScreen.bottom})")

        // View ID (shortened: strip package prefix)
        if (!viewIdResourceName.isNullOrBlank()) {
            val shortId = viewIdResourceName.substringAfterLast("/", viewIdResourceName)
            sb.append(" id=$shortId")
        }

        return sb.toString()
    }
}
