package com.saarthi.ai.ai

/**
 * Prompt engineering for Gemini Nano on-device inference.
 *
 * Constructs the structured prompt that combines:
 * 1. A system-role preamble defining Saarthi's assistant persona
 * 2. The redacted screen UI tree (from AccessibilityTreeParser)
 * 3. The user's transcribed voice intent (from Bhashini)
 * 4. A strict response-format instruction for reliable parsing
 *
 * DESIGN DECISIONS:
 * - Prompt is kept under ~1500 tokens to fit Gemini Nano's context window.
 * - The response format uses a simple KEY:VALUE line protocol instead of
 *   JSON to minimize hallucination and parsing failures on a small model.
 * - The system preamble is deliberately short and direct.
 * - When the full tree is too large, we fall back to actionable-only mode.
 */
object PromptBuilder {

    private const val MAX_TREE_CHARS = 3000 // Approximate character budget for the UI tree

    /**
     * Builds the complete inference prompt for Gemini Nano.
     *
     * @param userIntent The transcribed user query in English
     *                   (e.g., "Where is my bank balance?" or "How to send money?")
     * @param fullScreenTree The full formatted UI tree from AccessibilityTreeParser.formatForPrompt()
     * @param actionableTree The compact actionable-only tree from formatActionableForPrompt()
     * @param userLanguage The user's preferred language for the guidance text (e.g., "Hindi", "Tamil")
     * @return The complete prompt string ready for Gemini Nano generateContent()
     */
    fun buildInferencePrompt(
        userIntent: String,
        fullScreenTree: String,
        actionableTree: String,
        userLanguage: String = "Hindi"
    ): String {
        // Use compact tree if full tree exceeds budget
        val screenTree = if (fullScreenTree.length > MAX_TREE_CHARS) {
            actionableTree
        } else {
            fullScreenTree
        }

        return """
You are Saarthi, an accessibility assistant for elderly Indian users who need help navigating mobile apps. You analyze a screen's UI element tree and the user's spoken intent to find the correct button or element they should tap.

SCREEN UI TREE:
$screenTree

USER'S REQUEST: "$userIntent"

INSTRUCTIONS:
1. Find the UI element from the tree above that best matches what the user wants to do.
2. If you find a match, respond EXACTLY in this format (one field per line):
NODE_INDEX: <the number in square brackets of the matching element>
BOUNDS: <left,top,right,bottom from that element's bounds>
GUIDANCE: <A short, warm, reassuring instruction in $userLanguage telling the elder exactly what to tap and why, max 15 words>
ACTION: <one of: TAP, SCROLL_DOWN, SCROLL_UP, TYPE, LONG_PRESS>
CONFIDENCE: <HIGH, MEDIUM, or LOW>

3. If no element matches the user's intent on this screen, respond EXACTLY:
NOT_FOUND: <brief reason why in English>
SUGGESTION: <what the user should do instead, in $userLanguage, max 20 words>

RULES:
- Pick ONLY elements marked as [clickable] or [editable] or [checkable] when possible.
- Prefer elements whose text or desc closely matches the user intent.
- Never guess wildly. If unsure, set CONFIDENCE to LOW.
- The GUIDANCE text must be warm, simple, and in $userLanguage script.
- Do NOT output anything other than the specified format. No explanations.
""".trimIndent()
    }

    /**
     * Builds a simplified prompt for when the user asks a general question
     * (not requesting to find a specific button), e.g., "What is this app?"
     */
    fun buildGeneralQueryPrompt(
        userIntent: String,
        screenTree: String,
        userLanguage: String = "Hindi"
    ): String {
        return """
You are Saarthi, an accessibility assistant for elderly users. The user has a question about what they see on screen.

SCREEN UI TREE:
$screenTree

USER'S QUESTION: "$userIntent"

Respond with a brief, warm, reassuring answer in $userLanguage (max 2 sentences). Explain what the screen shows and what the user can do. Do not reveal any redacted information.
""".trimIndent()
    }
}
