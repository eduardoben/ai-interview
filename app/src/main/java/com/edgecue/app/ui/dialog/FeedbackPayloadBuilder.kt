package com.edgecue.app.ui.dialog

import android.content.Context
import android.os.Build
import android.os.Process
import com.edgecue.app.BuildConfig
import com.edgecue.app.core.di.AppContainer
import com.edgecue.app.data.hardware.DeviceHardwareDetector
import com.edgecue.app.data.hardware.HardwareProfile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Categorizes user-initiated feedback submissions.
 */
enum class FeedbackCategory(val title: String, val description: String) {
    BUG_REPORT(
        title = "Bug Report",
        description = "Crashes, freezes, or unexpected behavior"
    ),
    SPEECH_RECOGNITION(
        title = "Speech & Audio Quality",
        description = "Missed words, VAD endpointing, or transcript accuracy"
    ),
    AI_ANSWER_QUALITY(
        title = "AI Answer Quality",
        description = "Response accuracy, formatting, or generation speed"
    ),
    FEATURE_REQUEST(
        title = "Feature Request",
        description = "Ideas for new capabilities or UI improvements"
    ),
    GENERAL(
        title = "General Feedback",
        description = "Other comments, questions, or suggestions"
    )
}

/**
 * Pure, testable payload builder for user-initiated feedback reports with explicit opt-in
 * inclusion of diagnostic logs and/or session transcripts.
 */
object FeedbackPayloadBuilder {

    private const val MAX_LOGCAT_LINES = 100
    private const val MAX_DIAGNOSTIC_CHARS = 16_384
    private const val MAX_TRANSCRIPT_CHARS = 32_768

    fun buildSubject(
        category: FeedbackCategory,
        versionName: String = BuildConfig.VERSION_NAME
    ): String {
        return "[EdgeCue v$versionName] ${category.title}"
    }

    /**
     * Builds the complete plain-text/Markdown feedback payload.
     *
     * Privacy Guarantee:
     * - [diagnosticLogsProvider] is ONLY invoked when [includeLogs] is true.
     * - [transcriptsProvider] is ONLY invoked when [includeTranscripts] is true.
     */
    fun buildBody(
        category: FeedbackCategory,
        userMessage: String,
        includeLogs: Boolean,
        includeTranscripts: Boolean,
        diagnosticLogsProvider: () -> String,
        transcriptsProvider: () -> String,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'Z", Locale.US)
        val trimmedMessage = userMessage.trim()
        val sb = StringBuilder()

        sb.append("# EdgeCue — User Feedback\n\n")
        sb.append("- **Category:** ${category.title}\n")
        sb.append("- **Submitted:** ${sdf.format(Date(timestampMs))}\n")
        sb.append("- **Diagnostics Attached:** ${if (includeLogs) "Yes (Opt-In)" else "No"}\n")
        sb.append("- **Session Transcripts Attached:** ${if (includeTranscripts) "Yes (Opt-In)" else "No"}\n\n")

        sb.append("## User Message\n\n")
        if (trimmedMessage.isNotEmpty()) {
            sb.append(trimmedMessage).append("\n\n")
        } else {
            sb.append("*(No additional written message provided)*\n\n")
        }

        if (includeLogs) {
            val rawLogs = diagnosticLogsProvider().trim()
            val boundedLogs = if (rawLogs.length > MAX_DIAGNOSTIC_CHARS) {
                rawLogs.takeLast(MAX_DIAGNOSTIC_CHARS) + "\n...[truncated]..."
            } else {
                rawLogs
            }
            sb.append("---\n\n")
            sb.append("## Diagnostic Logs\n\n")
            sb.append(boundedLogs.ifEmpty { "*(No diagnostic logs available)*" }).append("\n\n")
        }

        if (includeTranscripts) {
            val rawTranscripts = transcriptsProvider().trim()
            val boundedTranscripts = if (rawTranscripts.length > MAX_TRANSCRIPT_CHARS) {
                rawTranscripts.take(MAX_TRANSCRIPT_CHARS) + "\n...[truncated]..."
            } else {
                rawTranscripts
            }
            sb.append("---\n\n")
            sb.append("## Session Transcripts\n\n")
            sb.append(boundedTranscripts.ifEmpty { "*(No session transcripts recorded)*" }).append("\n\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Collects on-device diagnostic metadata (app version, hardware profile, model status,
     * aggregate telemetry, and own-process logcat tail) when the user explicitly opts in.
     */
    fun collectDeviceDiagnostics(
        context: Context,
        hardwareProfile: HardwareProfile? = null
    ): String {
        val sb = StringBuilder()
        val container = AppContainer.getInstance(context)
        val hw = hardwareProfile ?: try {
            DeviceHardwareDetector.detectHardware(context)
        } catch (_: Throwable) {
            null
        }
        val uiState = container.pipelineCoordinator.copilotUiState.value
        val telemetry = container.sessionHistoryRepository.telemetry.value
        val modelReport = try {
            container.appConfig.validateModelsOnDisk()
        } catch (_: Throwable) {
            null
        }

        sb.append("### Environment & Hardware\n")
        sb.append("- App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        sb.append("- Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
        sb.append("- Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        if (hw != null) {
            sb.append("- SoC: ${hw.socName}\n")
            sb.append("- Total RAM: ${hw.totalRamMb} MB (${hw.ramTier.name})\n")
            sb.append("- Preferred Delegate: ${hw.preferredDelegate.name}\n")
            sb.append("- Recommended LLM: ${hw.recommendedLlmFileName}\n")
        }

        sb.append("\n### Pipeline & Telemetry\n")
        sb.append("- Pipeline State: ${uiState.pipelineState}\n")
        sb.append("- Selected Category: ${uiState.selectedCategory.displayName}\n")
        sb.append("- Thermal Throttled: ${uiState.isThermalThrottled}\n")
        sb.append("- Total Questions Answered: ${telemetry.totalQuestionsAnswered}\n")
        sb.append("- Avg TTFT: ${telemetry.avgTtftMs} ms | Avg Speed: ${String.format(Locale.US, "%.1f", telemetry.avgDecodeTps)} tok/s\n")

        if (modelReport != null) {
            sb.append("\n### On-Disk Model Status (All Present: ${modelReport.allPresent})\n")
            modelReport.files.forEach { fileStatus ->
                val sizeMb = String.format(Locale.US, "%.1f MB", fileStatus.sizeBytes / (1024f * 1024f))
                sb.append("- ${fileStatus.name}: ${if (fileStatus.exists) sizeMb else "MISSING"}\n")
            }
        }

        val logcatTail = captureOwnProcessLogcatTail()
        if (logcatTail.isNotBlank()) {
            sb.append("\n### Recent Process Logcat (Last $MAX_LOGCAT_LINES Lines)\n")
            sb.append("```text\n")
            sb.append(logcatTail)
            sb.append("\n```\n")
        }

        return sb.toString()
    }

    /**
     * Collects formatted session transcripts from [SessionHistoryRepository] when the user
     * explicitly opts in.
     */
    fun collectSessionTranscripts(context: Context): String {
        return try {
            AppContainer.getInstance(context).sessionHistoryRepository.exportToMarkdown()
        } catch (t: Throwable) {
            "Unable to export transcripts: ${t.message}"
        }
    }

    private fun captureOwnProcessLogcatTail(): String {
        return try {
            val pid = Process.myPid()
            val process = ProcessBuilder(
                "logcat",
                "-d",
                "-t",
                MAX_LOGCAT_LINES.toString(),
                "--pid=$pid"
            ).redirectErrorStream(true).start()

            val lines = BufferedReader(InputStreamReader(process.inputStream)).useLines { seq ->
                seq.take(MAX_LOGCAT_LINES).toList()
            }
            process.destroy()
            lines.joinToString("\n")
        } catch (_: Throwable) {
            ""
        }
    }
}
