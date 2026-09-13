package com.saarthi.ai.ai

import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun testPromptContainsUserIntentAndInstructions() {
        val userIntent = "Where can I check my bank balance?"
        val fullTree = "[1] Button text=\"Check Balance\" bounds=(100,200,300,400)"
        val actionableTree = fullTree

        val prompt = PromptBuilder.buildInferencePrompt(
            userIntent = userIntent,
            fullScreenTree = fullTree,
            actionableTree = actionableTree,
            userLanguage = "Hindi"
        )

        assertTrue(prompt.contains(userIntent))
        assertTrue(prompt.contains("Check Balance"))
        assertTrue(prompt.contains("NODE_INDEX:"))
        assertTrue(prompt.contains("BOUNDS:"))
        assertTrue(prompt.contains("GUIDANCE:"))
        assertTrue(prompt.contains("Hindi"))
    }

    @Test
    fun testPromptUsesCompactTreeWhenFullTreeExceedsLimit() {
        val userIntent = "Send Money"
        // Generate a large tree > 3000 chars
        val fullTree = "a".repeat(3500)
        val compactTree = "[1] Button text=\"Send Money\" [clickable] bounds=(0,0,100,100)"

        val prompt = PromptBuilder.buildInferencePrompt(
            userIntent = userIntent,
            fullScreenTree = fullTree,
            actionableTree = compactTree,
            userLanguage = "Marathi"
        )

        assertTrue(prompt.contains(compactTree))
        assertFalse(prompt.contains("a".repeat(100)))
        assertTrue(prompt.contains("Marathi"))
    }
}
