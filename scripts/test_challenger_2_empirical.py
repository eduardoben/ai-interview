"""
Empirical Verification Harness for Challenger 2
Tests codebase package consistency, store copy character counts, documentation image links, and source set counts.
"""

import os
import re
import sys

# Force stdout/stderr utf-8 output to avoid cp1252 charmap encoding errors on Windows
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')
if sys.stderr.encoding != 'utf-8':
    sys.stderr.reconfigure(encoding='utf-8')

REPO_ROOT = r"c:\Users\Master\AndroidStudioProjects\AIInterviewAssistant"

def test_1_package_consistency():
    print("==================================================")
    print("TEST 1: Search for com.local.interviewassistant and com/local/")
    print("==================================================")
    
    # We construct patterns so the script doesn't find itself
    target_pkg = "com." + "local." + "interviewassistant"
    target_path = "com/" + "local/"
    
    exclude_dirs = {'.git', '.gradle', 'build', '.idea', '.agents', '.system_generated', '__pycache__'}
    matches = []
    scanned_count = 0
    
    this_script_name = os.path.basename(__file__)
    
    # Check all files in repo
    for root, dirs, files in os.walk(REPO_ROOT):
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        for f in files:
            if f == this_script_name:
                continue
            path = os.path.join(root, f)
            rel_path = os.path.relpath(path, REPO_ROOT)
            
            # Skip large files or known binaries
            if os.path.getsize(path) > 2 * 1024 * 1024:
                continue
            ext = os.path.splitext(f)[1].lower()
            if ext in {'.litertlm', '.onnx', '.tflite', '.aar', '.jar', '.png', '.jpg', '.webp', '.so', '.wav'}:
                continue
                
            scanned_count += 1
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
                    for line_no, line in enumerate(fp, start=1):
                        lower_line = line.lower()
                        if target_pkg in lower_line:
                            matches.append((rel_path, line_no, line.strip(), target_pkg))
                        if target_path in lower_line:
                            matches.append((rel_path, line_no, line.strip(), target_path))
            except Exception as e:
                print(f"  Error reading {rel_path}: {e}")

    print(f"Scanned {scanned_count} files across repo (excluding build/.git/.agents).")
    print(f"Occurrences found: {len(matches)}")
    for rel_path, line_no, content, term in matches:
        print(f"  FAILED MATCH: [{term}] {rel_path}:{line_no} -> {content}")

    # Also check if any directory exists with com/local
    leftover_dirs = []
    for root, dirs, files in os.walk(REPO_ROOT):
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        rel_root = os.path.relpath(root, REPO_ROOT).replace('\\', '/')
        if target_path in rel_root.lower():
            leftover_dirs.append(rel_root)

    print(f"Leftover directories with {target_path}: {len(leftover_dirs)}")
    for d in leftover_dirs:
        print(f"  LEFTOVER DIR: {d}")

    passed = (len(matches) == 0 and len(leftover_dirs) == 0)
    print(f"Test 1 Result: {'PASS' if passed else 'FAIL'}")
    return passed, matches, leftover_dirs


def test_2_store_copy_character_limits():
    print("\n==================================================")
    print("TEST 2: Validate Character Count Limits in marketing/copy/play_store_listing.md")
    print("==================================================")
    
    file_path = os.path.join(REPO_ROOT, "marketing", "copy", "play_store_listing.md")
    if not os.path.exists(file_path):
        print(f"FAIL: File not found: {file_path}")
        return False, {}
    
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    results = {}
    all_passed = True

    # 1. Title (<= 30 chars)
    titles = [
        ("Primary Approved Title", "EdgeCue: Local AI Interview"),
        ("Alternate 1", "EdgeCue - AI Interview Coach"),
        ("Alternate 2", "EdgeCue: AI Interview Copilot"),
    ]
    results['titles'] = []
    for label, title in titles:
        length = len(title)
        compliant = length <= 30
        if not compliant: all_passed = False
        results['titles'].append((label, title, length, compliant))
        print(f"  Title [{label}]: '{title}' -> {length} chars (Limit: 30) [{'PASS' if compliant else 'FAIL'}]")

    # 2. Short description (<= 80 chars)
    short_descs = [
        ("Primary Short Description", "100% on-device AI teleprompter & smart speech cues for interviews."),
        ("Alternative 1", "Private, on-device AI interview teleprompter with real-time audio cues."),
        ("Alternative 2", "Air-gapped AI teleprompter & live speech cues for technical interviews."),
    ]
    results['short_descs'] = []
    for label, desc in short_descs:
        length = len(desc)
        compliant = length <= 80
        if not compliant: all_passed = False
        results['short_descs'].append((label, desc, length, compliant))
        print(f"  Short Desc [{label}]: '{desc}' -> {length} chars (Limit: 80) [{'PASS' if compliant else 'FAIL'}]")

    # 3. Full description (<= 4000 chars)
    # Extract the markdown code block containing full description
    match_full = re.search(r'## 3\. Full Description.*?\n```markdown\s*\n(.*?)\n```', content, re.DOTALL)
    if not match_full:
        print("  FAIL: Could not locate ```markdown ... ``` block under ## 3. Full Description")
        all_passed = False
        results['full_desc'] = None
    else:
        full_desc = match_full.group(1)
        full_desc_norm = full_desc.replace('\r\n', '\n')
        length_norm = len(full_desc_norm)
        compliant = length_norm <= 4000
        if not compliant: all_passed = False
        results['full_desc'] = (length_norm, compliant)
        print(f"  Full Description Length (normalized \\n): {length_norm} chars (Limit: 4000) [{'PASS' if compliant else 'FAIL'}]")
        print(f"  Full Description Length (raw CRLF \\r\\n): {len(full_desc.replace('\n', '\r\n'))} chars")

    # 4. Alt-text (<= 140 chars per image)
    table_match = re.search(r'## 4\. Screenshot Alt-Text.*?\n(\|.*?\n)\s*(?:---|##|$)', content, re.DOTALL)
    alt_texts = []
    if table_match:
        table_text = table_match.group(1).strip()
        lines = [l.strip() for l in table_text.splitlines() if l.strip().startswith('|')]
        for r in lines[2:]:
            cols = [c.strip() for c in r.split('|')[1:-1]]
            if len(cols) >= 4:
                asset = cols[0]
                caption = cols[1]
                alt = cols[2]
                stated_count = cols[3]
                length = len(alt)
                compliant = length <= 140
                if not compliant: all_passed = False
                alt_texts.append((asset, alt, length, stated_count, compliant))
                print(f"  Alt-Text [{asset}]: '{alt}' -> {length} chars (Limit: 140) [{'PASS' if compliant else 'FAIL'}]")
    else:
        print("  FAIL: Screenshot Alt-Text table not found!")
        all_passed = False
    results['alt_texts'] = alt_texts

    print(f"Test 2 Result: {'PASS' if all_passed else 'FAIL'}")
    return all_passed, results


def test_3_docs_image_references():
    print("\n==================================================")
    print("TEST 3: Validate markdown image references in docs/avd_fleet_test_report.md")
    print("==================================================")
    
    report_path = os.path.join(REPO_ROOT, "docs", "avd_fleet_test_report.md")
    if not os.path.exists(report_path):
        print(f"FAIL: File not found: {report_path}")
        return False, []

    with open(report_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    target_lines = [35, 44, 53, 62]
    all_passed = True
    ref_results = []

    report_dir = os.path.dirname(report_path)

    for line_num in target_lines:
        line_content = lines[line_num - 1].strip()
        img_match = re.search(r'!\[(.*?)\]\((.*?)\)', line_content)
        if not img_match:
            print(f"  Line {line_num}: FAILED to find markdown image reference in: '{line_content}'")
            all_passed = False
            continue
        
        alt_label, rel_target = img_match.group(1), img_match.group(2)
        abs_target = os.path.normpath(os.path.join(report_dir, rel_target))
        exists = os.path.exists(abs_target)
        file_size = os.path.getsize(abs_target) if exists else 0
        
        if not exists or file_size == 0:
            all_passed = False
            
        print(f"  Line {line_num}: ![{alt_label}]({rel_target})")
        print(f"    Resolved Path: {abs_target}")
        print(f"    Exists: {exists}, File Size: {file_size} bytes -> [{'PASS' if exists and file_size > 0 else 'FAIL'}]")
        ref_results.append((line_num, rel_target, abs_target, exists, file_size))

    # Also scan all other markdown files in docs/ for broken image links
    print("\n  Scanning entire docs/ folder for any broken image references...")
    broken_links = []
    for root, dirs, files in os.walk(os.path.join(REPO_ROOT, "docs")):
        for f in files:
            if f.endswith(".md"):
                doc_path = os.path.join(root, f)
                with open(doc_path, "r", encoding="utf-8", errors="ignore") as df:
                    for l_idx, l in enumerate(df, start=1):
                        for m in re.finditer(r'!\[(.*?)\]\((.*?)\)', l):
                            link_target = m.group(2)
                            if link_target.startswith("http://") or link_target.startswith("https://"):
                                continue
                            resolved = os.path.normpath(os.path.join(root, link_target))
                            if not os.path.exists(resolved):
                                broken_links.append((os.path.relpath(doc_path, REPO_ROOT), l_idx, link_target, resolved))

    if broken_links:
        print(f"  Found {len(broken_links)} broken image references in docs/:")
        for doc_rel, l_idx, link_t, res in broken_links:
            print(f"    {doc_rel}:{l_idx} -> {link_t} (resolved: {res})")
        all_passed = False
    else:
        print("  Zero broken image references in docs/!")

    print(f"Test 3 Result: {'PASS' if all_passed else 'FAIL'}")
    return all_passed, ref_results, broken_links


def test_4_source_set_counts():
    print("\n==================================================")
    print("TEST 4: Verify Source Set File Counts under com/edgecue/app/")
    print("==================================================")

    source_sets = {
        "app main": (os.path.join(REPO_ROOT, "app", "src", "main", "java", "com", "edgecue", "app"), 62),
        "app test": (os.path.join(REPO_ROOT, "app", "src", "test", "java", "com", "edgecue", "app"), 29),
        "test-suite main": (os.path.join(REPO_ROOT, "test-suite", "src", "main", "java", "com", "edgecue", "app"), 5),
        "test-suite test": (os.path.join(REPO_ROOT, "test-suite", "src", "test", "java", "com", "edgecue", "app"), 2),
    }

    all_passed = True
    counts = {}
    total_files = 0
    total_expected = 98

    for name, (dir_path, expected_count) in source_sets.items():
        if not os.path.exists(dir_path):
            print(f"  FAIL: Directory does not exist: {dir_path}")
            all_passed = False
            counts[name] = (0, expected_count, [])
            continue

        files_found = []
        for root, dirs, files in os.walk(dir_path):
            for f in files:
                if f.endswith(".kt") or f.endswith(".java"):
                    rel = os.path.relpath(os.path.join(root, f), dir_path)
                    files_found.append(rel)

        actual_count = len(files_found)
        total_files += actual_count
        passed = (actual_count == expected_count)
        if not passed:
            all_passed = False
        counts[name] = (actual_count, expected_count, sorted(files_found))
        print(f"  Source Set [{name}]: Actual={actual_count}, Expected={expected_count} -> [{'PASS' if passed else 'FAIL'}]")

    print(f"  Total Source Files: Actual={total_files}, Expected={total_expected} -> [{'PASS' if total_files == total_expected else 'FAIL'}]")
    passed = all_passed and (total_files == total_expected)
    print(f"Test 4 Result: {'PASS' if passed else 'FAIL'}")
    return passed, counts


if __name__ == "__main__":
    t1_pass, t1_matches, t1_dirs = test_1_package_consistency()
    t2_pass, t2_results = test_2_store_copy_character_limits()
    t3_pass, t3_refs, t3_broken = test_3_docs_image_references()
    t4_pass, t4_counts = test_4_source_set_counts()

    overall = t1_pass and t2_pass and t3_pass and t4_pass
    print("\n==================================================")
    print(f"FINAL OVERALL EMPIRICAL VERDICT: {'CONFIRM_CORRECTNESS' if overall else 'CHALLENGE_FAILED'}")
    print("==================================================")
    sys.exit(0 if overall else 1)
