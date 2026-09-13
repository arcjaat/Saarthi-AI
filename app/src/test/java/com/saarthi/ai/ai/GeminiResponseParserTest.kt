package com.saarthi.ai.ai

import com.saarthi.ai.model.ScreenRect
import com.saarthi.ai.model.UiNode
import com.saarthi.ai.parser.ParseResult
import org.junit.Assert.*
import org.junit.Test

class GeminiResponseParserTest {

    private fun createDummyParseResult(): ParseResult {
        val node0 = UiNode(
            nodeIndex = 0,
            depth = 0,
            className = "android.widget.FrameLayout",
            text = null,
            contentDescription = null,
            viewIdResourceName = "root",
            boundsInScreen = ScreenRect(0, 0, 1080, 2400),
            isClickable = false,
            isEnabled = true,
            isVisibleToUser = true,
            isCheckable = false,
            isChecked = false,
            isEditable = false,
            isFocusable = false,
            isScrollable = false,
            packageName = "com.phonepe.app"
        )

        val node5 = UiNode(
            nodeIndex = 5,
            depth = 2,
            className = "android.widget.Button",
            text = "Check Balance",
            contentDescription = "Check your bank account balance",
            viewIdResourceName = "com.phonepe.app:id/check_balance_btn",
            boundsInScreen = ScreenRect(100, 500, 450, 620),
            isClickable = true,
            isEnabled = true,
            isVisibleToUser = true,
            isCheckable = false,
            isChecked = false,
            isEditable = false,
            isFocusable = true,
            isScrollable = false,
            packageName = "com.phonepe.app"
        )

        return ParseResult(
            nodes = listOf(node0, node5),
            totalNodesTraversed = 10,
            totalNodesExtracted = 2,
            activePackageName = "com.phonepe.app",
            redactionAudit = "Test audit"
        )
    }

    @Test
    fun testParseValidSuccessResponse() {
        val rawAiResponse = """
            NODE_INDEX: 5
            BOUNDS: 100,500,450,620
            GUIDANCE: इस बटन को दबाएं बैलेंस देखने के लिए
            ACTION: TAP
            CONFIDENCE: HIGH
        """.trimIndent()

        val parseResult = createDummyParseResult()
        val result = GeminiResponseParser.parse(rawAiResponse, parseResult)

        if (result !is InferenceResult.Success) {
            fail("Expected Success but got: $result")
            return
        }
        val success = result as InferenceResult.Success
        assertEquals(5, success.target.nodeIndex)
        assertEquals(ScreenRect(100, 500, 450, 620), success.target.bounds)
        assertEquals("HIGH", success.target.confidence)
        assertEquals("TAP", success.target.actionDescription)
        assertTrue(success.target.guidanceText.contains("बैलेंस"))
    }

    @Test
    fun testParseNotFoundResponse() {
        val rawAiResponse = """
            NOT_FOUND: No balance button visible on this screen
            SUGGESTION: कृपया पहले बैंक अकाउंट वाले विकल्प पर जाएं
        """.trimIndent()

        val parseResult = createDummyParseResult()
        val result = GeminiResponseParser.parse(rawAiResponse, parseResult)

        assertTrue(result is InferenceResult.NotFound)
        val notFound = result as InferenceResult.NotFound
        assertTrue(notFound.reason.contains("No balance button"))
        assertTrue(notFound.suggestion.contains("बैंक अकाउंट"))
    }

    @Test
    fun testParseBoundsFallbackWhenNodeNotFoundInResult() {
        // If for some reason nodeIndex is 99 (not in parseResult), it falls back to bounds string
        val rawAiResponse = """
            NODE_INDEX: 99
            BOUNDS: 50,200,300,400
            GUIDANCE: Tap here
            ACTION: TAP
            CONFIDENCE: MEDIUM
        """.trimIndent()

        val parseResult = createDummyParseResult()
        val result = GeminiResponseParser.parse(rawAiResponse, parseResult)

        if (result !is InferenceResult.Success) {
            fail("Expected Success but got: $result")
            return
        }
        val success = result as InferenceResult.Success
        assertEquals(99, success.target.nodeIndex)
        assertEquals(ScreenRect(50, 200, 300, 400), success.target.bounds)
    }
}
