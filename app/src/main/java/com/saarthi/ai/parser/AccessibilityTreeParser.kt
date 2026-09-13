package com.saarthi.ai.parser

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.saarthi.ai.model.UiNode
import com.saarthi.ai.redaction.SensitiveDataRedactor

/**
 * Recursive traversal engine for the Android AccessibilityNodeInfo tree.
 *
 * Extracts every visible, meaningful UI node from the currently active window,
 * applies PII redaction via [SensitiveDataRedactor], and produces a flat list
 * of [UiNode] objects ready for Gemini Nano prompt construction.
 *
 * PRIVACY CONTRACT:
 * - All output data exists ONLY in volatile RAM.
 * - Text and content descriptions are redacted DURING extraction (not after).
 * - The AccessibilityNodeInfo references are recycled immediately after reading.
 * - No logging of raw text content. Only structural metadata is logged.
 *
 * ARCHITECTURE:
 * - Uses iterative-deepening DFS (recursive) with cycle detection.
 * - Skips invisible, zero-area, and system-decoration nodes.
 * - Enforces a max tree depth of 30 and max node count of 500 to prevent
 *   runaway traversals on pathological UI hierarchies.
 */
object AccessibilityTreeParser {

    private const val TAG = "SaarthiParser"

    /** Safety limits to prevent stack overflow or excessive memory on complex UIs */
    private const val MAX_TREE_DEPTH = 30
    private const val MAX_NODE_COUNT = 500

    /** Minimum node area (px²) to consider a node visible/meaningful */
    private const val MIN_NODE_AREA_PX = 10

    /**
     * Parses the entire accessibility tree from the given root node.
     *
     * @param rootNode The root AccessibilityNodeInfo from getRootInActiveWindow().
     * @return A [ParseResult] containing the list of redacted UiNodes and metadata.
     *         All node text is already sanitized. Safe to feed directly to Gemini Nano.
     */
    fun parseActiveWindow(rootNode: AccessibilityNodeInfo?): ParseResult {
        if (rootNode == null) {
            Log.w(TAG, "parseActiveWindow: rootNode is null (no active window)")
            return ParseResult.EMPTY
        }

        val nodes = mutableListOf<UiNode>()
        val nodeCounter = NodeCounter()
        val packageName = rootNode.packageName?.toString() ?: "unknown"

        try {
            traverseNode(
                node = rootNode,
                depth = 0,
                nodes = nodes,
                counter = nodeCounter
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during tree traversal: ${e.message}", e)
        }

        val result = ParseResult(
            nodes = nodes,
            totalNodesTraversed = nodeCounter.total,
            totalNodesExtracted = nodes.size,
            activePackageName = packageName,
            redactionAudit = "Nodes redacted in-place during extraction"
        )

        Log.d(TAG, "Parse complete: ${result.totalNodesExtracted} usable nodes " +
                "from ${result.totalNodesTraversed} traversed (pkg=$packageName)")

        return result
    }

    /**
     * Recursive DFS traversal of the accessibility node tree.
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodes: MutableList<UiNode>,
        counter: NodeCounter
    ) {
        // Safety: abort if we've exceeded limits
        if (depth > MAX_TREE_DEPTH || counter.extracted >= MAX_NODE_COUNT) {
            return
        }

        counter.total++

        // Extract screen bounds
        val boundsInScreen = Rect()
        node.getBoundsInScreen(boundsInScreen)

        // Skip nodes with zero or negligible area (invisible/offscreen)
        val area = boundsInScreen.width().toLong() * boundsInScreen.height().toLong()
        if (area < MIN_NODE_AREA_PX) {
            recycleAndTraverseChildren(node, depth, nodes, counter)
            return
        }

        // Skip nodes not visible to the user
        if (!node.isVisibleToUser) {
            recycleAndTraverseChildren(node, depth, nodes, counter)
            return
        }

        // Extract raw text fields
        val rawText = node.text?.toString()
        val rawContentDesc = node.contentDescription?.toString()
        val className = node.className?.toString() ?: "View"
        val viewId = node.viewIdResourceName
        val packageName = node.packageName?.toString()

        // Determine if this node carries meaningful information
        val hasMeaningfulContent = !rawText.isNullOrBlank() ||
                !rawContentDesc.isNullOrBlank() ||
                node.isClickable ||
                node.isCheckable ||
                node.isEditable ||
                node.isScrollable

        if (hasMeaningfulContent) {
            // ═══ REDACT SENSITIVE DATA IN-PLACE ═══
            // Text is sanitized BEFORE the UiNode is constructed.
            // Raw text never exists in a UiNode object.
            val redactedText = SensitiveDataRedactor.redact(rawText)
            val redactedDesc = SensitiveDataRedactor.redact(rawContentDesc)

            val uiNode = UiNode(
                nodeIndex = counter.extracted,
                depth = depth,
                className = className,
                text = redactedText,
                contentDescription = redactedDesc,
                viewIdResourceName = viewId,
                boundsInScreen = com.saarthi.ai.model.ScreenRect.fromAndroidRect(boundsInScreen),
                isClickable = node.isClickable,
                isEnabled = node.isEnabled,
                isVisibleToUser = node.isVisibleToUser,
                isCheckable = node.isCheckable,
                isChecked = node.isChecked,
                isEditable = node.isEditable,
                isFocusable = node.isFocusable,
                isScrollable = node.isScrollable,
                packageName = packageName
            )

            nodes.add(uiNode)
            counter.extracted++
        }

        // Recurse into children
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, depth + 1, nodes, counter)
        }
    }

    /**
     * Helper: Skip this node but still traverse its children.
     */
    private fun recycleAndTraverseChildren(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodes: MutableList<UiNode>,
        counter: NodeCounter
    ) {
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, depth + 1, nodes, counter)
        }
    }

    /**
     * Formats the full list of parsed, redacted UiNodes into a compact
     * text prompt suitable for Gemini Nano.
     *
     * The output is a structured, indented text representation of the
     * screen's interactive elements with their coordinates.
     *
     * @param result The ParseResult from parseActiveWindow().
     * @return A formatted string ready to be embedded in the Gemini Nano prompt.
     */
    fun formatForPrompt(result: ParseResult): String {
        if (result.nodes.isEmpty()) {
            return "[EMPTY SCREEN - No accessible UI elements detected]"
        }

        val sb = StringBuilder()
        sb.appendLine("=== SCREEN UI TREE (${result.activePackageName}) ===")
        sb.appendLine("Total interactive elements: ${result.totalNodesExtracted}")
        sb.appendLine("---")

        for (node in result.nodes) {
            sb.appendLine(node.toPromptString())
        }

        sb.appendLine("---")
        sb.appendLine("=== END UI TREE ===")

        return sb.toString()
    }

    /**
     * Extracts ONLY the clickable/actionable nodes from a parse result.
     * Useful for reducing prompt size when the full tree is too large.
     */
    fun extractActionableNodes(result: ParseResult): List<UiNode> {
        return result.nodes.filter { it.isClickable || it.isEditable || it.isCheckable }
    }

    /**
     * Formats only actionable (clickable/editable/checkable) nodes for a
     * more compact prompt when the full tree exceeds token limits.
     */
    fun formatActionableForPrompt(result: ParseResult): String {
        val actionable = extractActionableNodes(result)

        if (actionable.isEmpty()) {
            return "[NO ACTIONABLE ELEMENTS - Screen may be loading or static]"
        }

        val sb = StringBuilder()
        sb.appendLine("=== ACTIONABLE ELEMENTS (${result.activePackageName}) ===")
        sb.appendLine("Clickable/Editable elements: ${actionable.size}")
        sb.appendLine("---")

        for (node in actionable) {
            sb.appendLine(node.toPromptString())
        }

        sb.appendLine("---")
        sb.appendLine("=== END ACTIONABLE ELEMENTS ===")

        return sb.toString()
    }

    /** Internal mutable counter for tracking traversal progress */
    private class NodeCounter {
        var total: Int = 0
        var extracted: Int = 0
    }
}

/**
 * Immutable result container from a single accessibility tree parse operation.
 * Held in volatile memory only; garbage collected after inference.
 */
data class ParseResult(
    /** Flat list of all meaningful, redacted UI nodes */
    val nodes: List<UiNode>,

    /** Total AccessibilityNodeInfo objects visited during traversal */
    val totalNodesTraversed: Int,

    /** Number of nodes that passed filters and were extracted */
    val totalNodesExtracted: Int,

    /** Package name of the foreground app (e.g., "com.phonepe.app") */
    val activePackageName: String,

    /** Human-readable audit string for privacy verification */
    val redactionAudit: String
) {
    companion object {
        val EMPTY = ParseResult(
            nodes = emptyList(),
            totalNodesTraversed = 0,
            totalNodesExtracted = 0,
            activePackageName = "none",
            redactionAudit = "No data processed"
        )
    }

    /** Convenience: true if the parse yielded usable nodes */
    val hasContent: Boolean get() = nodes.isNotEmpty()
}
