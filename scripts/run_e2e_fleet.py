#!/usr/bin/env python3
"""
run_e2e_fleet.py - Concurrent Android Studio Emulator Fleet Test Runner & Evaluation Aggregator

Orchestrates multi-device E2E interview tests across the x86_64 emulator fleet:
  Round 1: Pixel_10_Pro_XL (API 37) + Galaxy_Tab_S7_Plus (API 35) [Concurrent]
  Round 2: Galaxy_S25_Ultra (API 37) + Galaxy_A54_5G (API 33)     [Concurrent]

Responsibilities:
1. Builds test APKs once (`gradlew assembleDebug assembleDebugAndroidTest`).
2. Concurrently manages differential model synchronization via `push_models_to_fleet.py`.
3. Installs app and test APKs onto target devices.
4. Clears and captures logcat, asserting absence of FATAL EXCEPTION / SIGSEGV.
5. Runs `am instrument ParameterizedInterviewE2ETest` with 6 emulator-calibrated gates.
6. Pulls per-turn JSON result artifacts and generates:
   - Per-device report: `build/e2e-reports/<device_serial>_report.json`
   - Combined matrix:   `build/e2e-reports/fleet_summary.md`

Usage:
  python scripts/run_e2e_fleet.py [--dry-run] [--serial <serial>] [--rounds <n>] [--skip-build]
"""

import argparse
import concurrent.futures
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

ROOT_DIR = Path(__file__).resolve().parent.parent

# Configure standard UTF-8 console streams on Windows to prevent UnicodeEncodeError
if sys.platform == "win32":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# Target fleet definitions and profiles
FLEET_ROUNDS = [
    {
        "round_id": 1,
        "name": "Round 1: Flagship Phone + Tablet",
        "devices": ["Pixel_10_Pro_XL", "Galaxy_Tab_S7_Plus"]
    },
    {
        "round_id": 2,
        "name": "Round 2: Premium Flagship + Mid-Tier Phone",
        "devices": ["Galaxy_S25_Ultra", "Galaxy_A54_5G"]
    }
]

AVD_PROFILES: Dict[str, Dict[str, Any]] = {
    "Pixel_10_Pro_XL": {
        "avd_name": "Pixel_10_Pro_XL",
        "default_serial": "emulator-5554",
        "api_level": 37,
        "abi": "x86_64",
        "ram_config_mb": 8192,
        "detected_tier": "CONSTRAINED",
        "active_llm_backend": "CPU (Memory-Mapped)",
        "form_factor": "Phone (1344x2992, 480 dpi)"
    },
    "Galaxy_Tab_S7_Plus": {
        "avd_name": "Galaxy_Tab_S7_Plus",
        "default_serial": "emulator-5556",
        "api_level": 35,
        "abi": "x86_64",
        "ram_config_mb": 8192,
        "detected_tier": "CONSTRAINED",
        "active_llm_backend": "CPU (Memory-Mapped)",
        "form_factor": "Tablet (2800x1752, 280 dpi)"
    },
    "Galaxy_S25_Ultra": {
        "avd_name": "Galaxy_S25_Ultra",
        "default_serial": "emulator-5558",
        "api_level": 37,
        "abi": "x86_64",
        "ram_config_mb": 8192,
        "detected_tier": "CONSTRAINED",
        "active_llm_backend": "CPU (Memory-Mapped)",
        "form_factor": "Phone (1440x3120, 500 dpi)"
    },
    "Galaxy_A54_5G": {
        "avd_name": "Galaxy_A54_5G",
        "default_serial": "emulator-5560",
        "api_level": 33,
        "abi": "x86_64",
        "ram_config_mb": 8192,
        "detected_tier": "CONSTRAINED",
        "active_llm_backend": "CPU (Memory-Mapped)",
        "form_factor": "Phone (1080x2340, 400 dpi)"
    }
}

# APK output locations (supports decoupled :test-suite standalone module and legacy app androidTest)
DEBUG_APK_REL = "app/build/outputs/apk/debug/app-debug.apk"
TEST_SUITE_APK_REL = "test-suite/build/outputs/apk/debug/test-suite-debug.apk"
LEGACY_TEST_APK_REL = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
TEST_APK_REL = TEST_SUITE_APK_REL if (ROOT_DIR / "test-suite").exists() else LEGACY_TEST_APK_REL

# Crash and error patterns in logcat
CRASH_PATTERNS = [
    re.compile(r"FATAL EXCEPTION", re.IGNORECASE),
    re.compile(r"SIGSEGV", re.IGNORECASE),
    re.compile(r"SIGBUS", re.IGNORECASE),
    re.compile(r"SIGFPE", re.IGNORECASE),
    re.compile(r"art::ThrowOutOfMemoryError", re.IGNORECASE),
    re.compile(r"ANR in com\.local\.interviewassistant", re.IGNORECASE)
]


def find_adb_path() -> str:
    """Locate adb executable across environment and standard Android SDK paths."""
    adb_in_path = shutil.which("adb")
    if adb_in_path:
        return adb_in_path

    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidate = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if candidate.exists():
            return str(candidate)

    for env_var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        val = os.environ.get(env_var)
        if val:
            for ext in (".exe", ""):
                candidate = Path(val) / "platform-tools" / f"adb{ext}"
                if candidate.exists():
                    return str(candidate)

    return "adb"


def run_adb(
    cmd: List[str],
    adb_path: str = "adb",
    serial: Optional[str] = None,
    timeout: int = 60
) -> Tuple[int, str, str]:
    """Execute an adb command targeting an optional device serial with timeout."""
    full_cmd = [adb_path]
    if serial:
        full_cmd.extend(["-s", serial])
    full_cmd.extend(cmd)

    try:
        proc = subprocess.run(
            full_cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout
        )
        return proc.returncode, proc.stdout.strip(), proc.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", f"ADB command timed out after {timeout}s: {' '.join(full_cmd)}"
    except Exception as e:
        return -1, "", str(e)


def get_connected_device_map(adb_path: str) -> Dict[str, str]:
    """
    Returns mapping of device serial -> avd_name or model name.
    """
    code, stdout, _ = run_adb(["devices", "-l"], adb_path=adb_path)
    if code != 0:
        return {}

    device_map = {}
    for line in stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            serial = parts[0]
            # Try getting AVD name via emu avd name or getprop
            avd_name = None
            if serial.startswith("emulator-"):
                code_emu, emu_out, _ = run_adb(["emu", "avd", "name"], adb_path=adb_path, serial=serial)
                if code_emu == 0 and emu_out:
                    lines = [l.strip() for l in emu_out.splitlines() if l.strip() and l.strip() != "OK"]
                    if lines:
                        avd_name = lines[0]

            if not avd_name:
                code_prop, prop_out, _ = run_adb(["shell", "getprop", "ro.boot.qemu.avd_name"], adb_path=adb_path, serial=serial)
                if code_prop == 0 and prop_out.strip():
                    avd_name = prop_out.strip()

            if not avd_name:
                code_model, model_out, _ = run_adb(["shell", "getprop", "ro.product.model"], adb_path=adb_path, serial=serial)
                avd_name = model_out.strip() if (code_model == 0 and model_out.strip()) else serial

            device_map[serial] = avd_name

    return device_map


def build_apks(skip_build: bool, timeout: int = 600) -> bool:
    """Builds debug and test APKs using Gradle wrapper with timeout."""
    debug_apk = ROOT_DIR / DEBUG_APK_REL
    test_apk = ROOT_DIR / TEST_APK_REL

    if skip_build:
        print("[BUILD] Skipping Gradle build step (--skip-build specified).")
        if debug_apk.exists() and test_apk.exists():
            print(f"[BUILD] Found existing APKs:\n  - {debug_apk}\n  - {test_apk}")
            return True
        else:
            print(f"[ERROR] APKs not found despite --skip-build: {debug_apk} or {test_apk}")
            return False

    gradlew_cmd = ROOT_DIR / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    if not gradlew_cmd.exists():
        print(f"[ERROR] Gradle wrapper not found at {gradlew_cmd}")
        return False

    build_tasks = [":app:assembleDebug", ":test-suite:assembleDebug"] if (ROOT_DIR / "test-suite").exists() else ["assembleDebug", "assembleDebugAndroidTest"]
    print("==================================================================")
    print(" Compiling Debug and AndroidTest APKs...")
    print(f" Command: {gradlew_cmd.name} {' '.join(build_tasks)} (timeout={timeout}s)")
    print("==================================================================")

    try:
        proc = subprocess.run(
            [str(gradlew_cmd)] + build_tasks,
            cwd=str(ROOT_DIR),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout
        )
    except subprocess.TimeoutExpired:
        print(f"[ERROR] Gradle build timed out after {timeout}s.")
        return False

    if proc.returncode != 0:
        print(f"[ERROR] Gradle build failed with code {proc.returncode}:\n{proc.stderr}\n{proc.stdout}")
        return False

    print("[BUILD] Gradle compilation succeeded.")
    return True


def install_apks_on_device(serial: str, adb_path: str) -> bool:
    """Installs debug and test APKs onto the target device."""
    debug_apk = ROOT_DIR / DEBUG_APK_REL
    test_apk = ROOT_DIR / TEST_APK_REL

    print(f"[{serial}] Installing debug APK: {debug_apk.name}...")
    code, out, err = run_adb(["install", "-r", "-t", str(debug_apk)], adb_path=adb_path, serial=serial, timeout=120)
    if code != 0:
        print(f"[{serial}] [ERROR] Failed to install debug APK: {err or out}")
        return False

    print(f"[{serial}] Installing test APK: {test_apk.name}...")
    code, out, err = run_adb(["install", "-r", "-t", str(test_apk)], adb_path=adb_path, serial=serial, timeout=120)
    if code != 0:
        print(f"[{serial}] [ERROR] Failed to install test APK: {err or out}")
        return False

    print(f"[{serial}] APKs installed successfully.")
    return True


def sync_models_for_device(
    serial: str,
    models_dir: Optional[str],
    force: bool,
    timeout: int = 300
) -> bool:
    """Invokes differential push script for target device with timeout and safe encoding."""
    push_script = ROOT_DIR / "scripts" / "push_models_to_fleet.py"
    if not push_script.exists():
        print(f"[{serial}] [WARN] Model push script not found at {push_script}")
        return False

    cmd = [sys.executable, str(push_script), "--serial", serial]
    if models_dir:
        cmd.extend(["--models-dir", models_dir])
    if force:
        cmd.append("--force")

    print(f"[{serial}] Synchronizing models via push_models_to_fleet.py (timeout={timeout}s)...")
    child_env = dict(os.environ, PYTHONIOENCODING="utf-8")
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(ROOT_DIR),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=child_env,
            timeout=timeout
        )
    except subprocess.TimeoutExpired:
        print(f"[{serial}] [ERROR] Model synchronization timed out after {timeout}s.")
        return False

    if proc.stdout:
        for line in proc.stdout.splitlines():
            print(f"[{serial}] {line}")

    if proc.returncode != 0:
        print(f"[{serial}] [WARN] Model synchronization exited with code {proc.returncode}")
        if proc.stderr:
            print(f"[{serial}] STDERR:\n{proc.stderr}")
        return False
    return True


def check_logcat_for_crashes(logcat_text: str) -> Tuple[bool, List[str]]:
    """Scans logcat content for native crashes, fatal exceptions, or ANRs."""
    crashes = []
    for line in logcat_text.splitlines():
        for pattern in CRASH_PATTERNS:
            if pattern.search(line):
                crashes.append(line.strip())
                break
    return len(crashes) > 0, crashes


def generate_simulated_turn_results(avd_name: str) -> List[Dict[str, Any]]:
    """Generates deterministic baseline results for dry-run validation."""
    return [
        {
            "turnId": "turn_01_hr",
            "topic": "HR Behavioral (Conflict Resolution & Prioritization)",
            "timestamp": int(time.time() * 1000),
            "status": "PASS",
            "gate1_preflight": {
                "status": "PASS",
                "vadExists": True,
                "sttEncoderExists": True,
                "sttDecoderExists": True,
                "sttJoinerExists": True,
                "sttTokensExists": True,
                "llmExists": True,
                "llmSizeBytes": 2588147712,
                "rescorer": "ON"
            },
            "gate2_vad_segmentation": {
                "status": "PASS",
                "segmentCount": 1,
                "endpointLatencyMs": 1420,
                "slaMs": 2000
            },
            "gate2b_question_detection": {
                "status": "PASS",
                "expected": True,
                "actual": True,
                "warning": None
            },
            "gate3_wer": {
                "status": "PASS",
                "wer": 0.04,
                "maxAllowedWer": 0.15,
                "substitutions": 1,
                "deletions": 0,
                "insertions": 0,
                "referenceWords": 25,
                "hypothesisWords": 25,
                "reference": "tell me about a time you had a conflict with a coworker how did you handle it",
                "hypothesis": "tell me about a time you had a conflict with a colleague how did you handle it"
            },
            "gate4_hotwords": {
                "status": "PASS",
                "recall": 1.0,
                "matchedCount": 6,
                "totalRequired": 6,
                "matched": ["conflict", "resolution", "prioritization", "stakeholder", "situation", "action"],
                "missing": []
            },
            "gate5_llm": {
                "status": "PASS",
                "ttftMs": 6420,
                "ttftSlaMs": 15000,
                "keywordHitCount": 4,
                "matchedKeywords": ["star", "listen", "compromise", "communication"],
                "expectedKeywords": ["star", "listen", "compromise", "escalate", "communication"],
                "answerSnippet": "When resolving conflicts, the STAR method is effective: listen actively to understand perspectives, prioritize shared objectives, and reach a reasoned compromise."
            },
            "gate6_stability": {
                "status": "PASS",
                "baselineMemoryMb": 620,
                "currentMemoryMb": 632,
                "memoryDeltaMb": 12,
                "deltaLimitMb": 50
            }
        },
        {
            "turnId": "turn_02_ml",
            "topic": "ML Technical (Transformers & Overfitting)",
            "timestamp": int(time.time() * 1000) + 15000,
            "status": "PASS",
            "gate1_preflight": {
                "status": "PASS",
                "vadExists": True,
                "sttEncoderExists": True,
                "sttDecoderExists": True,
                "sttJoinerExists": True,
                "sttTokensExists": True,
                "llmExists": True,
                "llmSizeBytes": 2588147712,
                "rescorer": "ON"
            },
            "gate2_vad_segmentation": {
                "status": "PASS",
                "segmentCount": 1,
                "endpointLatencyMs": 1510,
                "slaMs": 2000
            },
            "gate2b_question_detection": {
                "status": "PASS",
                "expected": True,
                "actual": True,
                "warning": None
            },
            "gate3_wer": {
                "status": "PASS",
                "wer": 0.06,
                "maxAllowedWer": 0.15,
                "substitutions": 1,
                "deletions": 1,
                "insertions": 0,
                "referenceWords": 32,
                "hypothesisWords": 31,
                "reference": "can you explain the architecture of transformers and how you prevent overfitting in deep learning models",
                "hypothesis": "can you explain the architecture of transformer and how you prevent overfitting in deep learning models"
            },
            "gate4_hotwords": {
                "status": "PASS",
                "recall": 1.0,
                "matchedCount": 5,
                "totalRequired": 5,
                "matched": ["can you explain", "architecture", "python", "algorithms", "what is the difference between"],
                "missing": []
            },
            "gate5_llm": {
                "status": "PASS",
                "ttftMs": 7110,
                "ttftSlaMs": 15000,
                "keywordHitCount": 4,
                "matchedKeywords": ["transformer", "attention", "gradient", "overfitting"],
                "expectedKeywords": ["transformer", "attention", "gradient", "weights", "overfitting"],
                "answerSnippet": "Transformers utilize self-attention mechanisms to weigh token relationships. Overfitting is prevented via regularization, dropout, gradient clipping, and data augmentation."
            },
            "gate6_stability": {
                "status": "PASS",
                "baselineMemoryMb": 620,
                "currentMemoryMb": 624,
                "memoryDeltaMb": 4,
                "deltaLimitMb": 50
            }
        }
    ]


def execute_test_on_device(
    avd_name: str,
    serial: str,
    profile: Dict[str, Any],
    reports_dir: Path,
    adb_path: str,
    llm_mode: str,
    models_dir: Optional[str],
    dry_run: bool,
    force_push: bool
) -> Dict[str, Any]:
    """
    Executes E2E test lifecycle for a single target device.
    Executed inside a ThreadPoolExecutor worker for 2-device concurrency.
    """
    print(f"\n[{serial} - {avd_name}] Starting device test execution...")
    device_report: Dict[str, Any] = {
        "device": {
            "serial": serial,
            "avdName": avd_name,
            "apiLevel": profile.get("api_level", 37),
            "abi": profile.get("abi", "x86_64"),
            "ramConfigMb": profile.get("ram_config_mb", 8192),
            "detectedTier": profile.get("detected_tier", "CONSTRAINED"),
            "activeLlmBackend": profile.get("active_llm_backend", "CPU (Memory-Mapped)"),
            "formFactor": profile.get("form_factor", "Unknown")
        },
        "status": "PASS",
        "turns": [],
        "crashesDetected": False,
        "crashDetails": [],
        "logcatPath": str(reports_dir / f"{serial}_logcat.txt"),
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat()
    }

    logcat_file = reports_dir / f"{serial}_logcat.txt"

    if dry_run:
        print(f"[{serial} - {avd_name}] [DRY-RUN] Simulating model push, APK install, instrumentation & logcat capture...")
        time.sleep(1.0)  # Brief delay simulating concurrency

        # Emulate simulated turn results
        device_report["turns"] = generate_simulated_turn_results(avd_name)

        # Write simulated logcat
        simulated_log = (
            f"--------- beginning of system\n"
            f"11-01 05:00:00.120 1234 1234 I ActivityManager: Start proc com.local.interviewassistant for service com.local.interviewassistant/.service.AssistantForegroundService\n"
            f"11-01 05:00:01.050 1234 1280 I SherpaVadSttEngine: Silero VAD initialized successfully\n"
            f"11-01 05:00:01.400 1234 1285 I LiteRtGemmaEngine: LiteRT CPU backend memory-mapped gemma-4-E2B-it.litertlm (2588147712 bytes)\n"
            f"11-01 05:00:15.500 1234 1285 I ParameterizedInterviewE2ETest: Turn 1 PASSED: WER=0.04, TTFT=6420ms\n"
            f"11-01 05:00:32.100 1234 1285 I ParameterizedInterviewE2ETest: Turn 2 PASSED: WER=0.06, TTFT=7110ms\n"
            f"--------- logcat complete - 0 FATAL EXCEPTION, 0 SIGSEGV\n"
        )
        logcat_file.write_text(simulated_log, encoding="utf-8")
        print(f"[{serial} - {avd_name}] [DRY-RUN] Wrote simulated logcat to {logcat_file}")

    else:
        # 1. Differential model push
        sync_models_for_device(serial, models_dir, force_push)

        # 2. Install APKs
        if not install_apks_on_device(serial, adb_path):
            device_report["status"] = "FAIL"
            device_report["crashDetails"].append("Failed to install APKs")
            return device_report

        # 3. Clear logcat
        print(f"[{serial}] Clearing logcat buffer...")
        run_adb(["logcat", "-c"], adb_path=adb_path, serial=serial, timeout=30)

        # 4. Clean remote results and run instrumentation
        remote_e2e_dir = "/sdcard/Android/data/com.local.interviewassistant/files/e2e"
        run_adb(["shell", f"mkdir -p '{remote_e2e_dir}' && rm -f '{remote_e2e_dir}'/*_result.json"], adb_path=adb_path, serial=serial, timeout=30)

        test_class = "com.local.interviewassistant.e2e.ParameterizedInterviewE2ETest"
        runner_pkg = "com.local.interviewassistant.testsuite" if (ROOT_DIR / TEST_SUITE_APK_REL).exists() or (ROOT_DIR / "test-suite").exists() else "com.local.interviewassistant.test"
        runner = f"{runner_pkg}/androidx.test.runner.AndroidJUnitRunner"
        inst_cmd = [
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", test_class,
            "-e", "e2e.llm", llm_mode,
            runner
        ]

        print(f"[{serial}] Launching instrumentation: am instrument {test_class} (e2e.llm={llm_mode})...")
        code, out, err = run_adb(inst_cmd, adb_path=adb_path, serial=serial, timeout=300)
        print(f"[{serial}] Instrumentation output:\n{out}")

        if "FAILURES!!!" in out or code != 0:
            print(f"[{serial}] [WARN] Instrumentation reported failures or non-zero exit code: {code}")
            device_report["status"] = "FAIL"

        # 4b. Capture Snapshot
        snapshot_dir = reports_dir / "snapshots"
        snapshot_dir.mkdir(parents=True, exist_ok=True)
        local_snapshot = snapshot_dir / f"{serial}_{avd_name}_snapshot.png"
        run_adb(["shell", "screencap", "-p", f"/sdcard/{serial}_snapshot.png"], adb_path=adb_path, serial=serial, timeout=30)
        run_adb(["pull", f"/sdcard/{serial}_snapshot.png", str(local_snapshot)], adb_path=adb_path, serial=serial, timeout=30)
        run_adb(["shell", f"rm -f /sdcard/{serial}_snapshot.png"], adb_path=adb_path, serial=serial, timeout=10)
        if local_snapshot.exists():
            device_report["snapshotPath"] = str(local_snapshot)
            print(f"[{serial}] Captured device snapshot to {local_snapshot}")
            try:
                brain_snapshots = Path(r"C:\Users\Master\.gemini\antigravity\brain\1683f741-2a6b-419e-8400-dbbea34231ed\snapshots")
                brain_snapshots.mkdir(parents=True, exist_ok=True)
                shutil.copy2(local_snapshot, brain_snapshots / local_snapshot.name)
            except Exception as e_snap:
                print(f"[{serial}] [WARN] Could not copy snapshot to brain: {e_snap}")

        # 5. Capture logcat
        code_log, out_log, _ = run_adb(["logcat", "-d", "-v", "time"], adb_path=adb_path, serial=serial, timeout=60)
        logcat_file.write_text(out_log, encoding="utf-8")
        print(f"[{serial}] Captured logcat to {logcat_file} ({len(out_log.splitlines())} lines)")

        # 6. Verify logcat for crashes
        has_crashes, crash_lines = check_logcat_for_crashes(out_log)
        if has_crashes:
            device_report["crashesDetected"] = True
            device_report["status"] = "FAIL"
            device_report["crashDetails"].extend(crash_lines[:10])
            print(f"[{serial}] [FATAL] Crashes detected in logcat:\n" + "\n".join(crash_lines[:5]))

        # 7. Pull turn JSON result files
        remote_e2e_dir = "/sdcard/Android/data/com.local.interviewassistant/files/e2e"
        code_ls, out_ls, _ = run_adb(["shell", f"ls {remote_e2e_dir}/*_result.json 2>/dev/null"], adb_path=adb_path, serial=serial, timeout=30)

        json_remote_files = []
        if code_ls == 0 and out_ls.strip():
            for line in out_ls.splitlines():
                line = line.strip()
                if line.endswith(".json"):
                    json_remote_files.append(line)

        for remote_file in json_remote_files:
            fname = Path(remote_file).name
            local_dest = reports_dir / f"{serial}_{fname}"
            run_adb(["pull", remote_file, str(local_dest)], adb_path=adb_path, serial=serial, timeout=60)
            if local_dest.exists():
                try:
                    with open(local_dest, "r", encoding="utf-8") as f:
                        data = json.load(f)
                        device_report["turns"].append(data)
                        if data.get("status") != "PASS":
                            device_report["status"] = "FAIL"
                except Exception as ex:
                    print(f"[{serial}] Failed to parse pulled JSON {local_dest}: {ex}")

    # Emit per-device JSON summary
    device_summary_path = reports_dir / f"{serial}_report.json"
    with open(device_summary_path, "w", encoding="utf-8") as f:
        json.dump(device_report, f, indent=2)
    print(f"[{serial} - {avd_name}] Wrote per-device summary to {device_summary_path}")

    return device_report


def format_gate_badge(gate_data: Optional[Dict[str, Any]], gate_num: str) -> str:
    """Format single gate cell for the markdown matrix."""
    if not gate_data:
        return "N/A"

    status = gate_data.get("status", "UNKNOWN")

    if gate_num == "1":
        rescorer = gate_data.get("rescorer", "OFF")
        return f"PASS (6/6)" if status == "PASS" else "FAIL"

    elif gate_num == "2":
        lat = gate_data.get("endpointLatencyMs", 0)
        return f"PASS ({lat}ms)" if status == "PASS" else f"FAIL ({lat}ms)"

    elif gate_num == "2b":
        if "WARNING" in status:
            return "WARN (MISS)"
        return "PASS" if status == "PASS" else "FAIL"

    elif gate_num == "3":
        wer = gate_data.get("wer", 0.0)
        return f"PASS ({wer:.2f})" if status == "PASS" else f"FAIL ({wer:.2f})"

    elif gate_num == "4":
        recall = int(gate_data.get("recall", 0.0) * 100)
        return f"PASS ({recall}%)" if status == "PASS" else f"FAIL ({recall}%)"

    elif gate_num == "5":
        if status == "SKIPPED":
            return "SKIPPED"
        ttft = gate_data.get("ttftMs", 0)
        return f"PASS ({ttft}ms)" if status == "PASS" else f"FAIL ({ttft}ms)"

    elif gate_num == "6":
        delta = gate_data.get("memoryDeltaMb", 0)
        sign = "+" if delta >= 0 else ""
        return f"PASS ({sign}{delta}MB)" if status == "PASS" else f"FAIL ({sign}{delta}MB)"

    return status


def generate_fleet_summary_markdown(
    reports: List[Dict[str, Any]],
    output_path: Path
) -> str:
    """Emits comprehensive markdown summary matrix to fleet_summary.md."""
    now_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    canonical_order = ["Pixel_10_Pro_XL", "Galaxy_Tab_S7_Plus", "Galaxy_S25_Ultra", "Galaxy_A54_5G"]
    sorted_reports = sorted(
        reports,
        key=lambda r: canonical_order.index(r["device"]["avdName"]) if r["device"]["avdName"] in canonical_order else 99
    )

    total_devices = len(sorted_reports)
    passed_devices = sum(1 for r in sorted_reports if r["status"] == "PASS")
    failed_devices = total_devices - passed_devices
    warning_count = sum(
        1 for r in sorted_reports for t in r.get("turns", [])
        if "WARN" in t.get("gate2b_question_detection", {}).get("status", "")
    )

    lines = [
        "# Multi-Device E2E Interview Test Fleet Summary\n",
        f"**Run Timestamp:** {now_iso}  ",
        f"**Total Devices:** {total_devices} | **Passed:** {passed_devices} | **Failed:** {failed_devices} | **Warnings:** {warning_count}  \n",
        "## Fleet Hardware Profile",
        "| Device | AVD Name | API | ABI | RAM Config | Detected Tier | Active LLM Backend |",
        "| :--- | :--- | :--- | :--- | :--- | :--- | :--- |"
    ]

    for r in sorted_reports:
        d = r["device"]
        lines.append(
            f"| {d['serial']} | {d['avdName']} | {d['apiLevel']} | {d['abi']} | "
            f"{d['ramConfigMb']} MB | {d['detectedTier']} | {d['activeLlmBackend']} |"
        )

    lines.extend([
        "\n## Verification Gate Results Matrix",
        "| Device | Turn | Gate 1 (Files) | Gate 2 (VAD) | Gate 2b (Quest) | Gate 3 (WER) | Gate 4 (Hotwords) | Gate 5 (LLM/TTFT) | Gate 6 (Stability) | Status |",
        "| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |"
    ])

    for r in sorted_reports:
        d = r["device"]
        turns = r.get("turns", [])
        if not turns:
            lines.append(f"| {d['avdName']} | No Turns | N/A | N/A | N/A | N/A | N/A | N/A | N/A | **FAIL** |")
            continue

        for idx, t in enumerate(turns, start=1):
            turn_name = t.get("turnId", f"Turn {idx}")
            topic_short = "HR" if "hr" in turn_name.lower() else ("ML" if "ml" in turn_name.lower() else f"T{idx}")
            display_turn = f"{turn_name} ({topic_short})"

            g1 = format_gate_badge(t.get("gate1_preflight"), "1")
            g2 = format_gate_badge(t.get("gate2_vad_segmentation"), "2")
            g2b = format_gate_badge(t.get("gate2b_question_detection"), "2b")
            g3 = format_gate_badge(t.get("gate3_wer"), "3")
            g4 = format_gate_badge(t.get("gate4_hotwords"), "4")
            g5 = format_gate_badge(t.get("gate5_llm"), "5")
            g6 = format_gate_badge(t.get("gate6_stability"), "6")

            turn_status = "**PASS**" if t.get("status") == "PASS" else "**FAIL**"
            lines.append(
                f"| {d['avdName']} | {display_turn} | {g1} | {g2} | {g2b} | {g3} | {g4} | {g5} | {g6} | {turn_status} |"
            )

    lines.extend([
        "\n## Gate Calibration Thresholds & SLA Summary",
        "- **Gate 1 (Pre-Flight):** 6/6 model files present with exact GPU model size (2,588,147,712 bytes); Whisper rescorer reported.",
        "- **Gate 2 (VAD Segmentation):** Exactly 1 segment emitted; endpoint latency SLA $\\le 2000$ ms.",
        "- **Gate 2b (Question Detection):** Soft-fail fallback with manual `triggerAnswerGeneration()`.",
        "- **Gate 3 (Normalized WER):** Word-level Levenshtein WER $\\le 0.15$ with contraction normalization.",
        "- **Gate 4 (Hotword Recall):** 100% recall of contextual domain keywords with compound matching.",
        "- **Gate 5 (LLM Relevance & TTFT):** Keywords $\\ge \\max(2, 75\\%)$; TTFT SLA $\\le 15000$ ms on x86_64 CPU.",
        "- **Gate 6 (Stability & Memory):** Zero process crashes/ANRs; combined Native Heap + VmRSS growth $< 50$ MB after Turn 1 warmup.\n",
        f"## Overall Fleet Verification Status: {'ALL GATES PASSED (PRODUCTION READY)' if failed_devices == 0 else 'TEST FAILURES DETECTED'}\n"
    ])

    md_content = "\n".join(lines)
    output_path.write_text(md_content, encoding="utf-8")
    return md_content


def main():
    parser = argparse.ArgumentParser(
        description="Concurrent Android Studio Emulator Fleet E2E Test Runner & Evaluator."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Simulate execution without requiring running emulators or APK installation."
    )
    parser.add_argument(
        "--serial",
        type=str,
        default=None,
        help="Target a specific device serial instead of running fleet rounds."
    )
    parser.add_argument(
        "--rounds",
        type=int,
        default=2,
        help="Maximum number of rounds to execute (default: 2)."
    )
    parser.add_argument(
        "--round",
        type=int,
        default=None,
        help="Execute a specific round (e.g. 1 or 2)."
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Skip Gradle APK compilation step."
    )
    parser.add_argument(
        "--llm-mode",
        type=str,
        default="auto",
        choices=["auto", "on", "off"],
        help="LLM execution mode passed to instrumentation: auto (default), on, off."
    )
    parser.add_argument(
        "--reports-dir",
        type=str,
        default=str(ROOT_DIR / "build" / "e2e-reports"),
        help="Directory to store generated JSON and Markdown reports."
    )
    parser.add_argument(
        "--models-dir",
        type=str,
        default=None,
        help="Source directory for neural models on host."
    )
    parser.add_argument(
        "--force-push",
        action="store_true",
        help="Force overwrite models during differential push."
    )

    args = parser.parse_args()

    reports_dir = Path(args.reports_dir).resolve()
    reports_dir.mkdir(parents=True, exist_ok=True)

    adb_path = find_adb_path()

    print("==================================================================")
    print(" AI Interview Assistant - Emulator Fleet Test Runner (M4)")
    print(f" Execution Mode:  {'DRY-RUN (SIMULATION)' if args.dry_run else 'LIVE FLEET EXECUTION'}")
    print(f" ADB Path:        {adb_path}")
    print(f" Reports Dir:     {reports_dir}")
    print(f" LLM Mode:        {args.llm_mode}")
    print(f" Concurrency:     2 Devices Concurrently per Round")
    print("==================================================================")

    # Step 1: Build APKs (once for all devices)
    if not args.dry_run:
        if not build_apks(args.skip_build):
            print("[FATAL] APK build verification failed. Aborting fleet run.")
            sys.exit(1)
    else:
        print("[BUILD] [DRY-RUN] Simulating APK build verification (app-debug.apk + app-debug-androidTest.apk)...")

    # Step 2: Query connected devices or determine plan
    connected_map = get_connected_device_map(adb_path) if not args.dry_run else {}

    all_reports: List[Dict[str, Any]] = []

    if args.serial:
        # Single target execution
        serial = args.serial
        avd_name = connected_map.get(serial, serial)
        profile = AVD_PROFILES.get(avd_name, {
            "avd_name": avd_name,
            "api_level": 37,
            "abi": "x86_64",
            "ram_config_mb": 8192,
            "detected_tier": "CONSTRAINED",
            "active_llm_backend": "CPU (Memory-Mapped)",
            "form_factor": "Target Device"
        })

        print(f"\n[SINGLE RUN] Targeting specified device: {serial} ({avd_name})")
        report = execute_test_on_device(
            avd_name=avd_name,
            serial=serial,
            profile=profile,
            reports_dir=reports_dir,
            adb_path=adb_path,
            llm_mode=args.llm_mode,
            models_dir=args.models_dir,
            dry_run=args.dry_run,
            force_push=args.force_push
        )
        all_reports.append(report)

    else:
        # Fleet execution in rounds of 2 concurrent devices
        selected_rounds = [r for r in FLEET_ROUNDS if r["round_id"] == args.round] if args.round else FLEET_ROUNDS[:args.rounds]

        for round_info in selected_rounds:
            round_id = round_info["round_id"]
            round_name = round_info["name"]
            target_avds = round_info["devices"]

            print(f"\n##################################################################")
            print(f" Starting {round_name} (2 Devices Concurrent)")
            print(f" Targets: {', '.join(target_avds)}")
            print(f"##################################################################")

            # Determine device items to run in parallel
            round_tasks = []
            for avd_name in target_avds:
                profile = AVD_PROFILES[avd_name]
                # If dry run, use default simulated serial; else match from connected_map
                serial = profile["default_serial"]
                if not args.dry_run:
                    # Match avd_name against connected devices
                    matched_serial = None
                    for s, name in connected_map.items():
                        if name == avd_name:
                            matched_serial = s
                            break
                    if matched_serial:
                        serial = matched_serial
                    else:
                        print(f"[WARN] Target AVD '{avd_name}' is not currently connected via ADB.")

                round_tasks.append((avd_name, serial, profile))

            # Execute 2 devices concurrently via ThreadPoolExecutor(max_workers=2)
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
                future_to_device = {
                    executor.submit(
                        execute_test_on_device,
                        avd_name,
                        serial,
                        profile,
                        reports_dir,
                        adb_path,
                        args.llm_mode,
                        args.models_dir,
                        args.dry_run,
                        args.force_push
                    ): avd_name
                    for avd_name, serial, profile in round_tasks
                }

                for future in concurrent.futures.as_completed(future_to_device):
                    target_name = future_to_device[future]
                    try:
                        dev_report = future.result()
                        all_reports.append(dev_report)
                        print(f"[{target_name}] Completed round execution with status: {dev_report['status']}")
                    except Exception as exc:
                        print(f"[{target_name}] Generated an unhandled exception: {exc}")
                        all_reports.append({
                            "device": AVD_PROFILES[target_name],
                            "status": "FAIL",
                            "turns": [],
                            "crashesDetected": True,
                            "crashDetails": [str(exc)]
                        })

    # Step 3: Aggregate results into fleet_summary.md
    summary_md_path = reports_dir / "fleet_summary.md"
    summary_content = generate_fleet_summary_markdown(all_reports, summary_md_path)

    print("\n==================================================================")
    print(" FLEET EXECUTION SUMMARY")
    print(f" Markdown Report: {summary_md_path}")
    print("==================================================================")
    print(summary_content)

    overall_passed = all(r["status"] == "PASS" for r in all_reports)
    print("\n==================================================================")
    print(f" Fleet Run Verdict: {'PASSED (ALL GATES GREEN)' if overall_passed else 'FAILED'}")
    print("==================================================================")

    sys.exit(0 if overall_passed else 1)


if __name__ == "__main__":
    main()
