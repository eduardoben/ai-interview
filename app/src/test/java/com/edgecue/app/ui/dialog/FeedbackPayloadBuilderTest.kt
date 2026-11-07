package com.edgecue.app.ui.dialog

import com.edgecue.app.core.model.AppConfig
import com.edgecue.app.data.history.AiReportReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPayloadBuilderTest {

    @Test
    fun `buildSubject includes version and category title`() {
        val subject = FeedbackPayloadBuilder.buildSubject(
            category = FeedbackCategory.BUG_REPORT,
            versionName = "1.13.0"
        )
        assertEquals("[EdgeCue v1.13.0] Bug Report", subject)
        assertTrue(AppConfig.FEEDBACK_EMAIL.contains("@"))
    }

    @Test
    fun `buildBody with logs and transcripts disabled does not invoke providers or attach data`() {
        var logsInvoked = false
        var transcriptsInvoked = false

        val body = FeedbackPayloadBuilder.buildBody(
            category = FeedbackCategory.FEATURE_REQUEST,
            userMessage = "Please add dark mode scheduled presets.",
            includeLogs = false,
            includeTranscripts = false,
            diagnosticLogsProvider = {
                logsInvoked = true
                "SECRET_DIAGNOSTIC_LOG"
            },
            transcriptsProvider = {
                transcriptsInvoked = true
                "SECRET_TRANSCRIPT"
            },
            timestampMs = 1730000000000L
        )

        assertFalse("Diagnostic provider must not be called when includeLogs is false", logsInvoked)
        assertFalse("Transcript provider must not be called when includeTranscripts is false", transcriptsInvoked)
        assertTrue(body.contains("Please add dark mode scheduled presets."))
        assertTrue(body.contains("- **Diagnostics Attached:** No"))
        assertTrue(body.contains("- **Session Transcripts Attached:** No"))
        assertFalse(body.contains("## Diagnostic Logs"))
        assertFalse(body.contains("## Session Transcripts"))
        assertFalse(body.contains("SECRET_DIAGNOSTIC_LOG"))
        assertFalse(body.contains("SECRET_TRANSCRIPT"))
    }

    @Test
    fun `buildBody with includeLogs enabled attaches diagnostic logs only`() {
        var logsInvoked = false
        var transcriptsInvoked = false

        val body = FeedbackPayloadBuilder.buildBody(
            category = FeedbackCategory.BUG_REPORT,
            userMessage = "Model download paused unexpectedly.",
            includeLogs = true,
            includeTranscripts = false,
            diagnosticLogsProvider = {
                logsInvoked = true
                "SoC: Tensor G5 | Delegate: NPU_GOOGLE_TENSOR"
            },
            transcriptsProvider = {
                transcriptsInvoked = true
                "Q: Explain Kotlin Coroutines"
            }
        )

        assertTrue(logsInvoked)
        assertFalse(transcriptsInvoked)
        assertTrue(body.contains("- **Diagnostics Attached:** Yes (Opt-In)"))
        assertTrue(body.contains("- **Session Transcripts Attached:** No"))
        assertTrue(body.contains("## Diagnostic Logs"))
        assertTrue(body.contains("SoC: Tensor G5 | Delegate: NPU_GOOGLE_TENSOR"))
        assertFalse(body.contains("## Session Transcripts"))
        assertFalse(body.contains("Explain Kotlin Coroutines"))
    }

    @Test
    fun `buildBody with includeTranscripts enabled attaches session transcripts only`() {
        var logsInvoked = false
        var transcriptsInvoked = false

        val body = FeedbackPayloadBuilder.buildBody(
            category = FeedbackCategory.AI_ANSWER_QUALITY,
            userMessage = "The STAR answer could be more concise.",
            includeLogs = false,
            includeTranscripts = true,
            diagnosticLogsProvider = {
                logsInvoked = true
                "SoC: Tensor G5"
            },
            transcriptsProvider = {
                transcriptsInvoked = true
                "### Question 1 [Behavioral (STAR)]\n**Q:** Tell me about a challenge."
            }
        )

        assertFalse(logsInvoked)
        assertTrue(transcriptsInvoked)
        assertTrue(body.contains("- **Diagnostics Attached:** No"))
        assertTrue(body.contains("- **Session Transcripts Attached:** Yes (Opt-In)"))
        assertFalse(body.contains("## Diagnostic Logs"))
        assertTrue(body.contains("## Session Transcripts"))
        assertTrue(body.contains("Tell me about a challenge."))
    }

    @Test
    fun `buildBody with both logs and transcripts enabled includes both sections and handles blank message`() {
        val body = FeedbackPayloadBuilder.buildBody(
            category = FeedbackCategory.SPEECH_RECOGNITION,
            userMessage = "   ",
            includeLogs = true,
            includeTranscripts = true,
            diagnosticLogsProvider = { "VAD: Ready, Zipformer: Ready" },
            transcriptsProvider = { "Q: What is REST API?" }
        )

        assertTrue(body.contains("*(No additional written message provided)*"))
        assertTrue(body.contains("## Diagnostic Logs"))
        assertTrue(body.contains("VAD: Ready, Zipformer: Ready"))
        assertTrue(body.contains("## Session Transcripts"))
        assertTrue(body.contains("Q: What is REST API?"))
    }

    @Test
    fun `feedback email is configured to hbenitez@hectisai dot com`() {
        assertEquals("hbenitez@hectisai.com", AppConfig.FEEDBACK_EMAIL)
    }

    @Test
    fun `buildReportSubject includes version and reason title`() {
        val subject = FeedbackPayloadBuilder.buildReportSubject(
            reason = AiReportReason.FACTUAL_INACCURACY,
            versionName = "1.18.0"
        )
        assertEquals("[EdgeCue v1.18.0] AI Content Report: ${AiReportReason.FACTUAL_INACCURACY.title}", subject)
    }

    @Test
    fun `buildReportBody includes reason, user comment, question, and answer`() {
        val body = FeedbackPayloadBuilder.buildReportBody(
            question = "What is the time complexity of quicksort?",
            answer = "Quicksort has an average time complexity of O(n log n).",
            reason = AiReportReason.FACTUAL_INACCURACY,
            comment = "Please verify average vs worst case explanation.",
            timestampMs = 1730000000000L
        )

        assertTrue(body.contains("# EdgeCue — AI Content Report"))
        assertTrue(body.contains("- **Reason:** Factual Inaccuracy"))
        assertTrue(body.contains("## User Comment"))
        assertTrue(body.contains("Please verify average vs worst case explanation."))
        assertTrue(body.contains("## Question / Prompt"))
        assertTrue(body.contains("What is the time complexity of quicksort?"))
        assertTrue(body.contains("## AI Generated Response"))
        assertTrue(body.contains("Quicksort has an average time complexity of O(n log n)."))
    }
}

