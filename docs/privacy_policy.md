# Privacy Policy for EdgeCue

**Effective Date:** November 3, 2026  
**Last Updated:** November 5, 2026  
**Developer / Publisher:** Himmler Benitez  
**Contact Email:** hbenitez@hectisai.com  
**Official Hosting URL:** https://eduardoben.github.io/ai-interview/privacy.html  
**Repository:** https://github.com/eduardoben/ai-interview  
**Application ID:** `com.edgecue.app`

---

## 1. Overview & Core Privacy Guarantee

EdgeCue ("the Application", "we", "us", or "our") is designed from the ground up as a **privacy-first, confidential, fully on-device AI cognitive rehearsal and speech teleprompter tool** for interview preparation and practice.

Our architectural commitment is absolute:
- **Zero Cloud Transmission:** All audio recording, voice activity detection (Silero VAD), speech-to-text recognition (Sherpa-ONNX Zipformer), question classification, and large language model inference (Google LiteRT-LM Gemma) execute **100% locally on your device hardware** (Neural Processing Unit, mobile GPU, or CPU). No audio, spoken words, questions, or answers ever leave your device.
- **Zero Voice/Biometric Retention:** Audio captured via your microphone is buffered ephemerally in volatile system RAM only while speech is active and is discarded immediately after acoustic processing. We never record, persist, serialize, or transmit audio files or acoustic voiceprints to disk or any remote server.
- **Zero Developer Telemetry:** We do not track, collect, monetize, sell, or transmit personal data, interview questions, transcripts, usage statistics, or telemetry.

---

## 2. Information We Process & How It Is Handled

### A. Microphone Audio (`android.permission.RECORD_AUDIO`)
- **Purpose:** Used strictly for real-time speech-to-text recognition during active rehearsal sessions to detect questions and display assistive talking points.
- **Lifecycle & Storage:** Ephemeral only. Audio is digitized into 16 kHz float PCM buffers in volatile RAM, processed through the local Silero VAD and Sherpa-ONNX Zipformer neural network, and immediately overwritten in memory upon utterance endpointing.
- **Foreground Service Notice:** Microphone capture runs under Android's `FOREGROUND_SERVICE_MICROPHONE` with an ongoing system notification providing an accessible, one-tap "Stop Assistant" action button.
- **Recording Laws & Consent Notice:** Before audio capture begins, the Application presents a prominent disclosure requiring explicit acknowledgment of applicable local recording consent and two-party wiretapping laws.

### B. Local Session History & Transcripts
- **Storage Location:** Stored exclusively in a sandboxed local Android SQLite database (`interview_assistant.db`) within your device's private application storage directory (`/data/data/com.edgecue.app/databases/`).
- **Cloud Backup Exclusion:** Explicit Android Auto Backup rules (`res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`) exclude all database files, preferences, and downloaded model weights from Google Cloud and device-to-device transfers. Your interview content never uploads to Google Drive backups.

### C. Downloaded AI Pipeline Model Weights
- **Purpose:** To perform on-device speech transcription and generative AI inference without third-party cloud APIs.
- **Download Source:** Model weights (`.onnx`, `.litertlm`, and tokenizer vocabularies) are downloaded directly over encrypted HTTPS from Hugging Face (`huggingface.co` / `hf.co`). All downloads are cryptographically verified via streaming SHA-256 checksums and exact byte size validation.
- **Network Metadata:** When downloading models, your device connects directly to Hugging Face CDN servers. We operate no intermediate proxy servers and capture no connection metadata. Once downloaded, the application operates completely offline in Airplane Mode.

### D. User-Initiated Support & Feedback (Optional)
- **Mechanism:** The Application includes a voluntary "Send Feedback" option. Feedback is never transmitted automatically or in the background.
- **User Review & Control:** When initiated, the Application composes a plain-text/Markdown message and opens your device's native email or sharing client (`Intent.ACTION_SEND`) addressed to `hbenitez@hectisai.com`. You can review, edit, or delete any content before deciding to send.
- **Optional Diagnostic Logs & Transcripts:** Users may explicitly opt in by checking boxes to attach device diagnostics (app version, hardware SoC tier, model verification status, recent process logs) and/or saved session transcripts. These checkboxes are disabled (unchecked) by default. If included, this data is used strictly by the publisher to diagnose technical issues and reply to your inquiry; it is never sold, shared with third parties, or used for advertising.

---

## 3. In-App Data Deletion & Storage Management Rights

You maintain complete sovereignty over your data at all times:
1. **Purge Interview History:** Navigate to **Activity** (or **Settings**) and select **"Clear History"** to permanently delete all stored interview questions, answers, and local latency metrics from the SQLite database.
2. **Purge Downloaded AI Models:** Navigate to **System & Diagnostics** in the Application and select **"Purge Downloaded AI Models"** to permanently delete all on-device neural network weights (~3.1 GB) and reclaim storage without uninstalling the app.
3. **Application Uninstallation:** Uninstalling the Application through Android OS settings immediately and irreversibly destroys all application databases, preferences, cache files, and local model files.

---

## 4. Children's Privacy (COPPA & Google Play Families Policy)

EdgeCue is targeted exclusively at adults aged 18 and older preparing for professional, collegiate, or technical job interviews. We do not knowingly target, collect, or solicit information from children under the age of 13 (or under the age of 16 in applicable jurisdictions).

---

## 5. Security & Network Protections

- **Strict HTTPS Enforcement:** The Application declares a system-level Network Security Configuration (`res/xml/network_security_config.xml`) that prohibits all cleartext HTTP traffic globally.
- **No Dynamic Code Loading:** The Application loads only passive numerical weights (`.onnx` and `.litertlm`) into native C++ runtimes (Sherpa-ONNX, LiteRT-LM). It never uses `DexClassLoader` or downloads executable code.
- **R8 / ProGuard Log Stripping:** All verbose, debug, and informational logging is permanently stripped from production release builds to prevent accidental transcript leakage to Android system logs (`logcat`).

---

## 6. Policy Updates & Inquiries

We may update this Privacy Policy from time to time to reflect operational or regulatory improvements. Any revisions will be published at this public URL and noted in application release notes.

If you have questions or concerns regarding this Privacy Policy or on-device data processing, please contact:
- **Publisher:** Himmler Benitez
- **Email:** hbenitez@hectisai.com
- **Official Policy URL:** https://eduardoben.github.io/ai-interview/privacy.html
- **Repository:** https://github.com/eduardoben/ai-interview
