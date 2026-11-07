# Google Play Closed Testing: Action Items & Operator TODO 📋

**Application:** EdgeCue  
**Package Name:** `com.edgecue.app`  
**Current Version:** `v1.13.0` (Version Code: `17`)  
**Release AAB Artifact:** [`app/build/outputs/bundle/release/app-release.aab`](file:///app/build/outputs/bundle/release/app-release.aab) (41.4 MB, R8 minified, cryptographically signed)  
**Keystore Location:** `keystore/upload-key.jks` (`keystore.properties` configured, git-ignored)  
**Date Created:** November 3, 2026  

---

## 1. Summary of Completed Codebase Changes (v1.13.0 & Signing Fixes)

All engineering, architecture, and regulatory requirements inside the codebase are **100% complete and passing**:

1. **Production Upload Signing:**
   - Generated RSA 2048-bit release upload key in `keystore/upload-key.jks`.
   - Configured `keystore.properties` (password: `interviewassistant2026`, alias: `upload`).
   - Fixed Gradle store file resolution in [`app/build.gradle.kts`](file:///app/build.gradle.kts) to resolve from `rootProject.file()`.
   - Successfully signed [`app-release.aab`](file:///app/build/outputs/bundle/release/app-release.aab) and verified with `jarsigner`.
2. **Public Privacy Policy Integration:**
   - Authored legal policies in [`docs/privacy_policy.md`](file:///docs/privacy_policy.md) and [`docs/privacy.html`](file:///docs/privacy.html).
   - Updated [`AppConfig.PRIVACY_POLICY_URL`](file:///app/src/main/java/com/edgecue/app/core/model/AppConfig.kt) to `https://eduardoben.github.io/ai-interview/privacy.html`.
   - Added direct launch buttons in [`InfoScreen.kt`](file:///app/src/main/java/com/edgecue/app/ui/info/InfoScreen.kt) and [`SettingsScreen.kt`](file:///app/src/main/java/com/edgecue/app/ui/settings/SettingsScreen.kt).
3. **Storage Purge Controls (~3.1 GB Reclamation):**
   - Implemented `purgeDownloadedModels()` in [`AppConfig.kt`](file:///app/src/main/java/com/edgecue/app/core/model/AppConfig.kt).
   - Added user confirmation dialog in [`InfoScreen.kt`](file:///app/src/main/java/com/edgecue/app/ui/info/InfoScreen.kt) allowing complete neural model weight deletion without uninstalling.
4. **Notification Vector Drawables:**
   - Created in-tree vector drawables (`ic_notification_mic.xml`, `ic_notification_stop.xml`, etc.) replacing framework drawables in [`AssistantForegroundService.kt`](file:///app/src/main/java/com/edgecue/app/service/AssistantForegroundService.kt) and [`ModelDownloadService.kt`](file:///app/src/main/java/com/edgecue/app/service/ModelDownloadService.kt).
5. **Predictive Back & Android 17 Support:**
   - Enabled `android:enableOnBackInvokedCallback="true"` in [`AndroidManifest.xml`](file:///app/src/main/AndroidManifest.xml).
   - Documented `useLegacyPackaging = true` rationale for LiteRT EdgeTPU NPU execution in [`app/build.gradle.kts`](file:///app/build.gradle.kts).
6. **Verification:**
   - `./gradlew testDebugUnitTest`: **174 / 174 unit tests passing**.
   - `./gradlew :app:lintRelease`: **0 errors**.
   - Merged and tagged on `main` and `dev` (`v1.13.0`).

---

## 2. Operator Checklist: Action Items for Closed Testing

Follow this checklist step-by-step when you are ready to upload and begin your Closed Testing track:

### Step 1: Activate GitHub Pages (Hosting the Live Privacy Policy)
> [!IMPORTANT]
> Google Play's automated web crawlers verify that your Privacy Policy URL returns HTTP `200 OK`. If it returns a 404 or DNS error, your submission will be rejected.

- [ ] 1. Go to your repository on GitHub: `https://github.com/eduardoben/ai-interview`
- [ ] 2. Click **Settings** (top menu bar) -> **Pages** (left sidebar under "Code and automation").
- [ ] 3. Under **Build and deployment**:
  - **Source:** Select `Deploy from a branch`.
  - **Branch:** Select `main` and change the folder from `/(root)` to `/docs`.
- [ ] 4. Click **Save**.
- [ ] 5. Wait 2–3 minutes, then open `https://eduardoben.github.io/ai-interview/privacy.html` in your browser and confirm it loads properly.

---

### Step 2: Record the Two 45-Second Foreground Service Demo Videos
> [!NOTE]
> Google Play requires unlisted YouTube or Google Drive video links demonstrating why `microphone` and `dataSync` foreground services cannot be replaced by background workers. Use screen recordings on your phone or emulator.

- [ ] **Video 1: Microphone Foreground Service (`AssistantForegroundService`) (~45 sec)**
  - *Action:* Open app -> tap **"Start Live Copilot"**.
  - *Show:* Green Android microphone privacy dot in status bar.
  - *Show:* Pull down notification shade to highlight the ongoing `"EdgeCue Active"` notification and its **"Stop Assistant"** button.
  - *Show:* Switch to another app (e.g. Chrome with an interview question) with the floating teleprompter HUD visible over it.
  - *Show:* Speak a few words, observe live transcription in the HUD, then pull down the notification shade and tap **"Stop Assistant"** to cleanly terminate.
- [ ] **Video 2: DataSync Foreground Service (`ModelDownloadService`) (~45 sec)**
  - *Action:* On the System screen, tap **"Download Missing Assets"**.
  - *Show:* The persistent notification showing ongoing download progress and the **"Pause / Cancel"** action.
  - *Show:* Lock the screen or switch apps, then return to show the download continued without Android OS killing the background transfer.
- [ ] Upload both videos as **Unlisted** to YouTube (or Google Drive with "Anyone with link can view").

---

### Step 3: Google Play Console — App Setup & Metadata
- [ ] 1. Log in to [Google Play Console](https://play.google.com/console) and select **EdgeCue**.
- [ ] 2. Go to **Policy and programs -> App content**:
  - **Privacy Policy:** Paste `https://eduardoben.github.io/ai-interview/privacy.html`.
  - **Target Audience:** Select **18 and older** (ensures COPPA/Families policy exemptions).
  - **AI-Generated Content:** Confirm your app includes safety guardrails and an in-app reporting button.
  - **Foreground Service Permissions:**
    - Under `microphone`: Paste the justification text from Section 1.1 of [`docs/play_store_dossier.md`](file:///docs/play_store_dossier.md#L44-L46) and add Video 1 link.
    - Under `dataSync`: Paste the justification text from Section 1.2 of [`docs/play_store_dossier.md`](file:///docs/play_store_dossier.md#L84-L86) and add Video 2 link.
  - **Data Safety Form:** Declare that:
    - *No user data is collected or shared with third parties.*
    - Audio is processed ephemerally in RAM only and not stored.
    - Session history is stored locally on-device.
- [ ] 3. Go to **Grow -> Store presence -> Main store listing**:
  - **App Name:** `EdgeCue` (22/30 chars)
  - **Short Description:** `Private on-device AI interview practice coach & assistive speech teleprompter.` (78/80 chars)
  - **Full Description:** Copy from Section 7.3 of [`docs/play_store_dossier.md`](file:///docs/play_store_dossier.md#L743-L770).

---

### Step 4: Device Catalog RAM Exclusion (Preventing OOM Crashes)
> [!WARNING]
> Because LiteRT-LM allocates a 2.58 GB neural graph into RAM, distributing the app to phones with < 7 GB RAM will trigger immediate Out-Of-Memory (OOM) crashes and spike your Android Vitals crash rate above Google's 1.09% bad-behavior threshold.

- [ ] 1. In Play Console, go to **Release -> Reach and devices -> Device catalog**.
- [ ] 2. Select the **RAM** filter.
- [ ] 3. Create an exclusion rule excluding all devices with **RAM less than 7,168 MB (7 GB)**.

---

### Step 5: Upload AAB to Closed Testing Track (Alpha)
- [ ] 1. Go to **Release -> Testing -> Closed testing**.
- [ ] 2. Click **Create new release**.
- [ ] 3. Upload the signed bundle:
  - File: [`app/build/outputs/bundle/release/app-release.aab`](file:///app/build/outputs/bundle/release/app-release.aab)
  - Release name: `1.13.0`
  - Release notes: Copy the release notes from Section 2 of [`CHANGELOG.md`](file:///CHANGELOG.md).
- [ ] 4. Save and click **Review release**, then submit for review.

---

### Step 6: Execute the 12×14 Tester Cohort (Personal Account Requirement)
> [!IMPORTANT]
> For personal accounts created after Nov 13, 2023, you must have **at least 12 testers continuously opted in for 14 consecutive days**. If the active count drops to 11 at any point, the 14-day clock resets to Day 0.

- [ ] 1. Create a Google Group (`edgecue-beta@googlegroups.com`) to manage tester email addresses easily without waiting for Play Console sync delays.
- [ ] 2. In Play Console **Closed testing -> Testers tab**, select your Google Group.
- [ ] 3. **Recruit 20–22 testers** (an 8–10 tester safety buffer over the 12 minimum) using the recruitment script in Section 3 of [`docs/closed_testing_runbook.md`](file:///docs/closed_testing_runbook.md#L100-L200).
- [ ] 4. Share the Closed Testing opt-in link: `https://play.google.com/apps/testing/com.edgecue.app`.
- [ ] 5. Confirm that at least **14–16 testers have clicked "Become a tester"** before marking Day 1 started.
- [ ] 6. **Day 5–6:** Push an iterative bug fix update (e.g., `v1.13.1`) to demonstrate active engagement to Google review evaluators.
- [ ] 7. **Day 14:** Once 336 continuous hours are fulfilled, the **"Apply for Production"** button will unlock on your dashboard.
- [ ] 8. Fill out the 10-question Production Application form using the pre-written answers in Section 8 of [`docs/closed_testing_runbook.md`](file:///docs/closed_testing_runbook.md#L600-L750).

---

*This document is saved in [`docs/closed_testing_todo.md`](file:///docs/closed_testing_todo.md) for quick offline reference.*

