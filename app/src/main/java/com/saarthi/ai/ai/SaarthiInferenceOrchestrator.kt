package com.saarthi.ai.ai

import android.content.Context
import android.util.Log
import com.saarthi.ai.parser.AccessibilityTreeParser
import com.saarthi.ai.parser.ParseResult
import com.saarthi.ai.service.SaarthiAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top-level orchestrator that ties together the entire Saarthi AI pipeline:
 *
 *   Voice Intent → Screen Parse → Redaction → Prompt → Gemini Nano → Target Coordinates
 *
 * This is the single entry point called by the FloatingOverlayService when
 * the user speaks a request. It coordinates:
 *
 * 1. Fetching the current screen tree via SaarthiAccessibilityService
 * 2. Formatting the redacted tree into prompt-ready strings
 * 3. Building the complete prompt via PromptBuilder
 * 4. Running on-device inference via GeminiNanoClient
 * 5. Parsing the AI response via GeminiResponseParser
 * 6. Returning a clean InferenceResult for the overlay to consume
 * 7. CLEARING all volatile data from memory
 *
 * PRIVACY LIFECYCLE (enforced here):
 * ┌──────────────────────────────────┐
 * │  Screen tree enters RAM          │ ← parseCurrentScreen()
 * │  PII redacted in-place           │ ← SensitiveDataRedactor (Phase 3)
 * │  Prompt built from redacted tree │ ← PromptBuilder
 * │  Inference runs on-device        │ ← Gemini Nano (AICore)
 * │  Result extracted                │ ← GeminiResponseParser
 * │  ALL volatile data destroyed     │ ← clearVolatileData()
 * └──────────────────────────────────┘
 */
class SaarthiInferenceOrchestrator(context: Context) {

    companion object {
        private const val TAG = "SaarthiOrchestrator"
    }

    private val geminiClient = GeminiNanoClient(context)

    /** User's preferred guidance language (set from Settings dashboard) */
    var userLanguage: String = "Hindi"

    /**
     * Initializes the Gemini Nano model. Must be called once before
     * [processUserIntent]. Typically called when FloatingOverlayService starts.
     *
     * @return true if Gemini Nano is ready for inference
     */
    suspend fun initialize(): Boolean {
        return geminiClient.initialize()
    }

    /**
     * The main pipeline entry point.
     *
     * Called when the user taps the floating mic and speaks a request.
     * Orchestrates the full flow from screen parsing to target coordinates.
     *
     * @param userIntent The transcribed English text of what the user said
     *                   (e.g., "Where is my bank balance?")
     * @return An [InferenceResult] indicating success (with target bounds),
     *         not-found, or error.
     */
    suspend fun processUserIntent(userIntent: String): InferenceResult =
        withContext(Dispatchers.Default) {

            Log.i(TAG, "═══ Starting Saarthi inference pipeline ═══")
            Log.d(TAG, "User intent: \"$userIntent\"")

            // ── Step 1: Check prerequisites ──────────────────────────
            if (!geminiClient.isAvailable()) {
                Log.e(TAG, "Gemini Nano is not available")
                return@withContext InferenceResult.Unavailable(
                    "Gemini Nano is not available on this device. " +
                    "Please ensure Android AICore is installed and the model is downloaded."
                )
            }

            val accessibilityService = SaarthiAccessibilityService.instance
            if (accessibilityService == null) {
                Log.e(TAG, "Accessibility service is not connected")
                return@withContext InferenceResult.Error(
                    "Saarthi's screen reading service is not active. " +
                    "Please enable it in Accessibility Settings."
                )
            }

            // ── Step 2: Parse the current screen (redaction happens in-place) ─
            Log.d(TAG, "Step 2: Parsing active window...")
            val parseResult: ParseResult = try {
                accessibilityService.parseCurrentScreen()
            } catch (e: Exception) {
                Log.e(TAG, "Screen parsing failed: ${e.message}", e)
                return@withContext InferenceResult.Error(
                    "Failed to read the current screen: ${e.message}",
                    e
                )
            }

            if (!parseResult.hasContent) {
                Log.w(TAG, "Screen parse returned empty — screen may be loading")
                return@withContext InferenceResult.NotFound(
                    reason = "No UI elements detected on the current screen",
                    suggestion = "स्क्रीन लोड हो रही है, कृपया कुछ सेकंड बाद फिर बोलें"
                )
            }

            Log.d(TAG, "Parsed ${parseResult.totalNodesExtracted} nodes " +
                    "from ${parseResult.activePackageName}")

            // ── Step 3: Format for prompt ────────────────────────────
            Log.d(TAG, "Step 3: Building prompt...")
            val fullTree = AccessibilityTreeParser.formatForPrompt(parseResult)
            val actionableTree = AccessibilityTreeParser.formatActionableForPrompt(parseResult)

            val prompt = PromptBuilder.buildInferencePrompt(
                userIntent = userIntent,
                fullScreenTree = fullTree,
                actionableTree = actionableTree,
                userLanguage = userLanguage
            )

            Log.d(TAG, "Prompt built (${prompt.length} chars)")

            // ── Step 4: Run on-device Gemini Nano inference ──────────
            Log.d(TAG, "Step 4: Running Gemini Nano inference (on-device)...")
            val rawResponse: String?
            try {
                rawResponse = geminiClient.runInference(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Inference exception: ${e.message}", e)
                // Clear volatile data even on error
                accessibilityService.clearVolatileData()
                return@withContext InferenceResult.Error(
                    "AI inference failed: ${e.message}", e
                )
            }

            // ── Step 5: Parse AI response ────────────────────────────
            Log.d(TAG, "Step 5: Parsing AI response...")
            val result = GeminiResponseParser.parse(rawResponse, parseResult)

            when (result) {
                is InferenceResult.Success -> {
                    Log.i(TAG, "✓ Target found: node=${result.target.nodeIndex} " +
                            "bounds=${result.target.bounds} " +
                            "confidence=${result.target.confidence}")
                }
                is InferenceResult.NotFound -> {
                    Log.i(TAG, "○ Not found: ${result.reason}")
                }
                is InferenceResult.Error -> {
                    Log.e(TAG, "✗ Parse error: ${result.message}")
                }
                is InferenceResult.Unavailable -> {
                    Log.e(TAG, "✗ Unavailable: ${result.reason}")
                }
            }

            // ── Step 6: DESTROY all volatile data ────────────────────
            // This is the critical privacy enforcement point.
            // After this call, the screen tree, prompt string, and AI
            // response are all eligible for garbage collection.
            accessibilityService.clearVolatileData()
            Log.d(TAG, "Step 6: Volatile screen data cleared from RAM ✓")

            Log.i(TAG, "═══ Saarthi inference pipeline complete ═══")

            return@withContext result
        }

    /**
     * Processes a general informational query (not a "find this button" request).
     * Returns a friendly text response to be spoken aloud.
     */
    suspend fun processGeneralQuery(userQuery: String): String =
        withContext(Dispatchers.Default) {

            val accessibilityService = SaarthiAccessibilityService.instance
                ?: return@withContext "Saarthi screen reading is not active."

            val parseResult = accessibilityService.parseCurrentScreen()
            val screenTree = AccessibilityTreeParser.formatActionableForPrompt(parseResult)

            val prompt = PromptBuilder.buildGeneralQueryPrompt(
                userIntent = userQuery,
                screenTree = screenTree,
                userLanguage = userLanguage
            )

            val response = geminiClient.runInference(prompt)

            // Clear volatile data
            accessibilityService.clearVolatileData()

            return@withContext response ?: "I could not understand the screen right now."
        }

    /**
     * Releases all resources. Call when the service is shutting down.
     */
    fun release() {
        geminiClient.release()
        // Force clear the accessibility service's volatile data too
        SaarthiAccessibilityService.instance?.clearVolatileData()
        Log.d(TAG, "Orchestrator released — all resources freed")
    }
}
