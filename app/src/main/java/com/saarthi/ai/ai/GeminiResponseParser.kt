package com.saarthi.ai.ai

import android.util.Log
import com.saarthi.ai.model.ScreenRect
import com.saarthi.ai.model.UiNode
import com.saarthi.ai.parser.ParseResult

/**
 * Parses the raw text response from Gemini Nano into a structured
 * [InferenceResult], cross-referencing against the original [ParseResult]
 * to extract exact screen coordinates.
 *
 * Gemini Nano responds in a strict KEY:VALUE line protocol:
 *
 * SUCCESS CASE:
 *   NODE_INDEX: 7
 *   BOUNDS: 120,450,540,520
 *   GUIDANCE: इस बटन को दबाएं अपना बैलेंस देखने के लिए
 *   ACTION: TAP
 *   CONFIDENCE: HIGH
 *
 * NOT FOUND CASE:
 *   NOT_FOUND: No balance check button visible on this screen
 *   SUGGESTION: पहले मुख्य मेनू पर जाएं और "अकाउंट" पर टैप करें
 *
 * The parser is tolerant of minor formatting variations (extra spaces,
 * mixed case keys, trailing punctuation) to handle LLM output variability.
 */
object GeminiResponseParser {

    private const val TAG = "SaarthiParser"

    /**
     * Parses Gemini Nano's raw response text and cross-references it
     * against the parse result to produce a validated [InferenceResult].
     *
     * @param rawResponse The raw text output from Gemini Nano's generateContent()
     * @param parseResult The original screen parse (used to look up and validate bounds)
     * @return A sealed [InferenceResult]: Success, NotFound, or Error
     */
    fun parse(rawResponse: String?, parseResult: ParseResult): InferenceResult {
        if (rawResponse.isNullOrBlank()) {
            Log.e(TAG, "Empty response from Gemini Nano")
            return InferenceResult.Error("AI returned an empty response")
        }

        val lines = rawResponse.trim().lines().map { it.trim() }

        // ── Check for NOT_FOUND response ─────────────────────────────
        val notFoundLine = lines.firstOrNull {
            it.uppercase().startsWith("NOT_FOUND")
        }
        if (notFoundLine != null) {
            val reason = extractValue(notFoundLine)
            val suggestionLine = lines.firstOrNull {
                it.uppercase().startsWith("SUGGESTION")
            }
            val suggestion = if (suggestionLine != null) {
                extractValue(suggestionLine)
            } else {
                "Please try asking differently or navigate to the correct screen."
            }
            Log.d(TAG, "AI result: NOT_FOUND — $reason")
            return InferenceResult.NotFound(reason = reason, suggestion = suggestion)
        }

        // ── Parse SUCCESS response fields ────────────────────────────
        val nodeIndexStr = findFieldValue(lines, "NODE_INDEX")
        val boundsStr = findFieldValue(lines, "BOUNDS")
        val guidance = findFieldValue(lines, "GUIDANCE")
        val action = findFieldValue(lines, "ACTION") ?: "TAP"
        val confidence = findFieldValue(lines, "CONFIDENCE") ?: "MEDIUM"

        // Validate NODE_INDEX
        val nodeIndex = nodeIndexStr?.toIntOrNull()
        if (nodeIndex == null) {
            Log.e(TAG, "Failed to parse NODE_INDEX from: $nodeIndexStr")
            return InferenceResult.Error(
                "AI response did not contain a valid node index. Raw: ${rawResponse.take(200)}"
            )
        }

        // ── Resolve bounds: prefer looking up from our parse result ──
        // The AI might return slightly different bounds, so we trust our
        // own extracted coordinates keyed by nodeIndex.
        val bounds = resolveTargetBounds(nodeIndex, boundsStr, parseResult)
        if (bounds == null) {
            Log.e(TAG, "Could not resolve bounds for nodeIndex=$nodeIndex")
            return InferenceResult.Error(
                "AI identified node [$nodeIndex] but coordinates could not be resolved"
            )
        }

        val guidanceText = guidance ?: "इस बटन को दबाएं" // Fallback Hindi guidance

        val result = TargetElementResult(
            nodeIndex = nodeIndex,
            bounds = bounds,
            guidanceText = guidanceText,
            confidence = confidence.uppercase(),
            actionDescription = action.uppercase()
        )

        Log.d(TAG, "AI result: SUCCESS — node=$nodeIndex bounds=$bounds " +
                "confidence=$confidence action=$action")

        return if (result.isValid) {
            InferenceResult.Success(result)
        } else {
            InferenceResult.Error("AI returned invalid bounds: $bounds")
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Finds a field value from the response lines by key prefix.
     * Tolerates variations like "NODE_INDEX:", "Node_Index :", "node_index:".
     */
    private fun findFieldValue(lines: List<String>, key: String): String? {
        val upperKey = key.uppercase()
        val matchingLine = lines.firstOrNull { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex <= 0) return@firstOrNull false
            val lineKey = line.substring(0, colonIndex).trim().uppercase()
                .replace(" ", "_")
            lineKey == upperKey
        }
        return matchingLine?.let { extractValue(it) }?.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts the value portion after the first colon in a "KEY: value" line.
     */
    private fun extractValue(line: String): String {
        val colonIndex = line.indexOf(':')
        return if (colonIndex >= 0 && colonIndex < line.length - 1) {
            line.substring(colonIndex + 1).trim()
        } else {
            line
        }
    }

    /**
     * Resolves the target element's screen bounds by:
     * 1. Looking up the nodeIndex in our trusted ParseResult (preferred)
     * 2. Falling back to parsing the AI's reported bounds string
     *
     * We trust our own extracted bounds over the AI's reported bounds
     * because the AI may round or misformat coordinates.
     */
    private fun resolveTargetBounds(
        nodeIndex: Int,
        aiBoundsStr: String?,
        parseResult: ParseResult
    ): ScreenRect? {
        // Primary: look up from our own parse result
        val matchingNode = parseResult.nodes.firstOrNull { it.nodeIndex == nodeIndex }
        if (matchingNode != null) {
            return matchingNode.boundsInScreen
        }

        // Fallback: parse the AI's bounds string "left,top,right,bottom"
        if (!aiBoundsStr.isNullOrBlank()) {
            return parseBoundsString(aiBoundsStr)
        }

        return null
    }

    /**
     * Parses a comma-separated bounds string "left,top,right,bottom" into a ScreenRect.
     * Tolerates spaces and parentheses: "(120, 450, 540, 520)" → ScreenRect(120,450,540,520)
     */
    private fun parseBoundsString(boundsStr: String): ScreenRect? {
        return try {
            val cleaned = boundsStr
                .replace("(", "")
                .replace(")", "")
                .replace(" ", "")
            val parts = cleaned.split(",")
            if (parts.size == 4) {
                ScreenRect(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt(),
                    parts[3].toInt()
                )
            } else null
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Failed to parse bounds string: $boundsStr", e)
            null
        }
    }
}
