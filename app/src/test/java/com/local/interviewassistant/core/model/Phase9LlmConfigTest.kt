package com.local.interviewassistant.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase9LlmConfigTest {

    @Test
    fun testPromptFormattingAcrossAllStyles() {
        val config = AppConfig(File("tmp"))
        val question = "What is polymorphism?"

        val conciseConfig = LlmGenerationConfig(promptStyle = PromptStyle.CONCISE)
        val concisePrompt = config.formatGemmaPrompt(question, conciseConfig)
        assertTrue("Should contain concise instructions", concisePrompt.contains(PromptStyle.CONCISE.systemInstruction))
        
        val verboseConfig = LlmGenerationConfig(promptStyle = PromptStyle.VERBOSE)
        val verbosePrompt = config.formatGemmaPrompt(question, verboseConfig)
        assertTrue("Should contain verbose instructions", verbosePrompt.contains(PromptStyle.VERBOSE.systemInstruction))
        
        val simpleConfig = LlmGenerationConfig(promptStyle = PromptStyle.SIMPLE)
        val simplePrompt = config.formatGemmaPrompt(question, simpleConfig)
        assertTrue("Should contain simple instructions", simplePrompt.contains(PromptStyle.SIMPLE.systemInstruction))
    }

    @Test
    fun testInterviewContextInjectionWhenProvidedAndOmitted() {
        val config = AppConfig(File("tmp"))
        val question = "What is your approach to testing?"

        // Omitted
        val blankContextConfig = LlmGenerationConfig(interviewContext = "   ")
        val blankPrompt = config.formatGemmaPrompt(question, blankContextConfig)
        assertFalse("Should not contain context line if blank", blankPrompt.contains("Interview Context"))

        // Provided
        val providedContextConfig = LlmGenerationConfig(interviewContext = "Staff QA Engineer, Python, Playwright, FastAPI")
        val providedPrompt = config.formatGemmaPrompt(question, providedContextConfig)
        assertTrue("Should contain context line", providedPrompt.contains("Interview Context (Role, Program & Domain): Staff QA Engineer, Python, Playwright, FastAPI"))
    }

    @Test
    fun testHyperparameterValidationClamping() {
        val invalidConfig = LlmGenerationConfig(
            temperature = 2.5f,
            topK = -5,
            maxTokens = 2000,
            interviewContext = "   Role   "
        )
        
        val validated = invalidConfig.validated()
        
        assertEquals("Temperature should clamp to 1.0f", 1.0f, validated.temperature)
        assertEquals("TopK should clamp to 1", 1, validated.topK)
        assertEquals("MaxTokens should clamp to 512", 512, validated.maxTokens)
        assertEquals("Interview context should be trimmed", "Role", validated.interviewContext)
        
        val invalidConfig2 = LlmGenerationConfig(
            temperature = -0.5f,
            topK = 200,
            maxTokens = 10
        )
        
        val validated2 = invalidConfig2.validated()
        assertEquals("Temperature should clamp to 0.0f", 0.0f, validated2.temperature)
        assertEquals("TopK should clamp to 100", 100, validated2.topK)
        assertEquals("MaxTokens should clamp to 64", 64, validated2.maxTokens)
    }

    @Test
    fun testUpdateLlmConfigValidatesInput() {
        val config = AppConfig(File("tmp"))

        val invalidConfig = LlmGenerationConfig(
            temperature = 2.0f,
            topK = 200,
            maxTokens = 1000,
            interviewContext = "   Role   "
        )

        config.updateLlmConfig(invalidConfig)

        assertEquals("Temperature should be clamped", 1.0f, config.temperature)
        assertEquals("TopK should be clamped", 100, config.topK)
        assertEquals("MaxTokens should be clamped", 512, config.maxTokens)
    }
}
