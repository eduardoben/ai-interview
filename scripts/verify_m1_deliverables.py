"""
Independent Verification Suite for Milestone M1 Deliverables
Author: Worker M1 QA Specialist
"""

import os
import re
import xml.etree.ElementTree as ET
from PIL import Image

REPO_ROOT = r"c:\Users\Master\AndroidStudioProjects\AIInterviewAssistant"

def check(condition, message):
    if not condition:
        raise AssertionError(f"FAILED: {message}")
    print(f"  [PASS] {message}")

def verify():
    print("=== STARTING MILESTONE M1 VERIFICATION ===")

    # 1. Originals
    print("\n--- 1. Original Source Uploads ---")
    orig_files = [
        "media_1793856702152_273e657f.png",
        "media_1793856795977_e2c2bab5.jpg",
        "edgecue_logo_source.png",
        "edgecue_mascot_source.jpg"
    ]
    for f in orig_files:
        p = os.path.join(REPO_ROOT, "marketing", "brand", "originals", f)
        check(os.path.exists(p), f"Original exists: {f}")
        check(os.path.getsize(p) > 1000, f"Original non-empty: {f} ({os.path.getsize(p)} bytes)")

    # 2. SVGs
    print("\n--- 2. Master SVGs in marketing/brand/svg/ ---")
    svgs = {
        "edgecue_logo.svg": "#000000",
        "edgecue_logo_light.svg": "#F3F4F6",
        "edgecue_mascot.svg": "#000000",
        "edgecue_mascot_light.svg": "#F3F4F6"
    }
    for s, expected_fill in svgs.items():
        p = os.path.join(REPO_ROOT, "marketing", "brand", "svg", s)
        check(os.path.exists(p), f"SVG exists: {s}")
        tree = ET.parse(p)
        root = tree.getroot()
        check(root.attrib.get("viewBox") == "0 0 1024 682", f"{s} viewBox is 0 0 1024 682")
        check(root.attrib.get("width") == "1024", f"{s} width is 1024")
        check(root.attrib.get("height") == "682", f"{s} height is 682")
        path = root.find(".//{http://www.w3.org/2000/svg}path")
        if path is None:
            path = root.find(".//path")
        check(path is not None, f"{s} contains path element")
        check(path.attrib.get("fill") == expected_fill, f"{s} fill is {expected_fill}")

    # 3. Android VectorDrawables
    print("\n--- 3. Android VectorDrawables in app/src/main/res/drawable/ ---")
    drawables = [
        ("ic_edgecue_logo.xml", "1024", "682", "#FFFFFFFF"),
        ("ic_edgecue_mascot.xml", "1024", "682", "#FFFFFFFF"),
        ("ic_launcher_background.xml", "108", "108", "#FFFFFFFF"),
        ("ic_launcher_foreground.xml", "108", "108", "#FF000000"),
        ("ic_launcher_monochrome.xml", "108", "108", "#FFFFFFFF")
    ]
    for name, vw, vh, expected_fill in drawables:
        p = os.path.join(REPO_ROOT, "app", "src", "main", "res", "drawable", name)
        check(os.path.exists(p), f"VectorDrawable exists: {name}")
        tree = ET.parse(p)
        root = tree.getroot()
        check(root.tag == "vector", f"{name} root is <vector>")
        check(root.attrib.get("{http://schemas.android.com/apk/res/android}viewportWidth") == vw, f"{name} viewportWidth is {vw}")
        check(root.attrib.get("{http://schemas.android.com/apk/res/android}viewportHeight") == vh, f"{name} viewportHeight is {vh}")
        path = root.find(".//path")
        check(path is not None, f"{name} contains <path>")
        fill = path.attrib.get("{http://schemas.android.com/apk/res/android}fillColor")
        check(fill == expected_fill, f"{name} fillColor is {expected_fill} (actual: {fill})")

    # Safe zone coordinates check
    print("\n--- 3b. Launcher Foreground Safe Zone Bounds Check ---")
    fg_path = os.path.join(REPO_ROOT, "app", "src", "main", "res", "drawable", "ic_launcher_foreground.xml")
    fg_tree = ET.parse(fg_path)
    path_data = fg_tree.getroot().find(".//path").attrib.get("{http://schemas.android.com/apk/res/android}pathData")
    coords = [float(x) for x in re.findall(r"[-+]?\d*\.?\d+", path_data)]
    xs = coords[0::2]
    ys = coords[1::2]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    check(20.0 <= min_x and max_x <= 88.0, f"Foreground X bounds [{min_x:.2f}, {max_x:.2f}] inside [20.0, 88.0]")
    check(31.0 <= min_y and max_y <= 77.0, f"Foreground Y bounds [{min_y:.2f}, {max_y:.2f}] inside [31.0, 77.0]")

    # 4. Play Store Assets
    print("\n--- 4. Google Play Store Assets ---")
    icon512_p = os.path.join(REPO_ROOT, "marketing", "play-store", "ic_launcher_playstore_512x512.png")
    check(os.path.exists(icon512_p), "Play Store 512x512 icon exists")
    im_icon = Image.open(icon512_p)
    check(im_icon.size == (512, 512), f"Icon size is 512x512 (actual: {im_icon.size})")
    icon_kb = os.path.getsize(icon512_p) / 1024.0
    check(icon_kb < 1024, f"Icon file size {icon_kb:.1f} KB is < 1024 KB")

    feat_p = os.path.join(REPO_ROOT, "marketing", "play-store", "feature_graphic_1024x500.png")
    check(os.path.exists(feat_p), "Feature Graphic 1024x500 exists")
    im_feat = Image.open(feat_p)
    check(im_feat.size == (1024, 500), f"Feature graphic size is 1024x500 (actual: {im_feat.size})")

    # 5. Raster Assets
    print("\n--- 5. Brand Raster Assets in marketing/brand/raster/ ---")
    rasters = [
        ("edgecue_logo_dark_on_light.png", (1024, 682)),
        ("edgecue_logo_light_on_dark.png", (1024, 682)),
        ("edgecue_logo.webp", (1024, 682)),
        ("edgecue_mascot_dark_on_light.png", (1024, 682)),
        ("edgecue_mascot_light_on_dark.png", (1024, 682)),
        ("edgecue_mascot.webp", (1024, 682))
    ]
    for r, sz in rasters:
        p = os.path.join(REPO_ROOT, "marketing", "brand", "raster", r)
        check(os.path.exists(p), f"Raster asset exists: {r}")
        im = Image.open(p)
        check(im.size == sz, f"{r} size is {sz} (actual: {im.size})")

    # 6. Density Mipmaps
    print("\n--- 6. Mipmaps Across All 5 Densities ---")
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, sz in densities.items():
        for b in ["ic_launcher.webp", "ic_launcher_round.webp"]:
            p = os.path.join(REPO_ROOT, "app", "src", "main", "res", f"mipmap-{d}", b)
            check(os.path.exists(p), f"Mipmap exists: {d}/{b}")
            im = Image.open(p)
            check(im.size == (sz, sz), f"{d}/{b} size is {sz}x{sz} (actual: {im.size})")

    # 7. Screenshots Relocation
    print("\n--- 7. Screenshots & Link Updates ---")
    old_dir = os.path.join(REPO_ROOT, "docs", "screenshots")
    check(not os.path.exists(old_dir), "docs/screenshots/ directory has been removed")
    screenshots = [
        "Pixel_10_Pro_XL_screen.png",
        "Galaxy_S25_Ultra_screen.png",
        "Galaxy_Tab_S7_Plus_screen.png",
        "Galaxy_A54_5G_screen.png"
    ]
    for sc in screenshots:
        p = os.path.join(REPO_ROOT, "marketing", "screenshots", sc)
        check(os.path.exists(p), f"Relocated screenshot exists: {sc}")

    report_p = os.path.join(REPO_ROOT, "docs", "avd_fleet_test_report.md")
    with open(report_p, "r", encoding="utf-8") as f:
        rep_content = f.read()
    check("screenshots/" not in rep_content or "../marketing/screenshots/" in rep_content,
          "avd_fleet_test_report.md references ../marketing/screenshots/")
    for sc in screenshots:
        check(f"../marketing/screenshots/{sc}" in rep_content, f"Report links to ../marketing/screenshots/{sc}")

    # 8. Documentation
    print("\n--- 8. Documentation Files ---")
    bg_p = os.path.join(REPO_ROOT, "marketing", "brand", "BRAND_GUIDELINES.md")
    check(os.path.exists(bg_p), "BRAND_GUIDELINES.md exists")
    check(os.path.getsize(bg_p) > 2000, f"BRAND_GUIDELINES.md is comprehensive ({os.path.getsize(bg_p)} bytes)")

    listing_p = os.path.join(REPO_ROOT, "marketing", "copy", "play_store_listing.md")
    check(os.path.exists(listing_p), "play_store_listing.md exists")
    with open(listing_p, "r", encoding="utf-8") as f:
        list_content = f.read()
    # Check title
    t_match = re.search(r"### Primary Approved Title\s*\n`([^`]+)`", list_content)
    check(t_match is not None, "Title found in listing copy")
    title = t_match.group(1)
    check(len(title) <= 30, f"App title '{title}' length {len(title)} <= 30")

    s_match = re.search(r"## 2\. Short Description.*?\n\n`([^`]+)`", list_content, re.DOTALL)
    check(s_match is not None, "Short description found in listing copy")
    short_desc = s_match.group(1)
    check(len(short_desc) <= 80, f"Short description length {len(short_desc)} <= 80")

    f_match = re.search(r"```markdown\n(.*?)\n```", list_content, re.DOTALL)
    check(f_match is not None, "Full description found in listing copy")
    full_desc = f_match.group(1)
    check(len(full_desc) <= 4000, f"Full description length {len(full_desc)} <= 4000")

    print("\n==============================================")
    print("ALL MILESTONE M1 DELIVERABLES FULLY VERIFIED!")
    print("==============================================")
    return True

if __name__ == "__main__":
    verify()
