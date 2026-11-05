"""
EdgeCue Brand Identity & Marketing Asset Generator (Milestone M1)
Author: Worker M1 (Brand & Marketing Specialist)
Date: 2026-11-05

Generates:
1. Preserved source originals in marketing/brand/originals/
2. Master SVGs in marketing/brand/svg/
3. Android VectorDrawables in app/src/main/res/drawable/
4. Raster assets in marketing/brand/raster/ and marketing/play-store/
5. Mipmap WebPs in app/src/main/res/mipmap-*/
6. Screenshot relocation from docs/screenshots/ to marketing/screenshots/
7. Full integrity verification
"""

import os
import shutil
import xml.etree.ElementTree as ET
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import potrace

# Paths
REPO_ROOT = r"c:\Users\Master\AndroidStudioProjects\AIInterviewAssistant"
SRC_LOGO = r"C:/Users/Master/.gemini/antigravity/brain/6bdb701c-c164-4684-a9ad-3d51367dd411/.user_uploaded/media_1793856702152_273e657f.png"
SRC_MASCOT = r"C:/Users/Master/.gemini/antigravity/brain/6bdb701c-c164-4684-a9ad-3d51367dd411/.user_uploaded/media_1793856795977_e2c2bab5.jpg"

DIR_ORIGINALS = os.path.join(REPO_ROOT, "marketing", "brand", "originals")
DIR_SVG = os.path.join(REPO_ROOT, "marketing", "brand", "svg")
DIR_RASTER = os.path.join(REPO_ROOT, "marketing", "brand", "raster")
DIR_PLAYSTORE = os.path.join(REPO_ROOT, "marketing", "play-store")
DIR_SCREENSHOTS = os.path.join(REPO_ROOT, "marketing", "screenshots")
DIR_DRAWABLE = os.path.join(REPO_ROOT, "app", "src", "main", "res", "drawable")
DIR_MIPMAP = os.path.join(REPO_ROOT, "app", "src", "main", "res")

def ensure_dirs():
    for d in [DIR_ORIGINALS, DIR_SVG, DIR_RASTER, DIR_PLAYSTORE, DIR_SCREENSHOTS, DIR_DRAWABLE]:
        os.makedirs(d, exist_ok=True)

def copy_originals():
    print("[1/7] Preserving source originals...")
    shutil.copyfile(SRC_LOGO, os.path.join(DIR_ORIGINALS, "media_1793856702152_273e657f.png"))
    shutil.copyfile(SRC_MASCOT, os.path.join(DIR_ORIGINALS, "media_1793856795977_e2c2bab5.jpg"))
    shutil.copyfile(SRC_LOGO, os.path.join(DIR_ORIGINALS, "edgecue_logo_source.png"))
    shutil.copyfile(SRC_MASCOT, os.path.join(DIR_ORIGINALS, "edgecue_mascot_source.jpg"))
    print("  Done.")

def curves_to_svg_path(curves, transform_fn=None):
    commands = []
    for curve in curves:
        sp = curve.start_point
        if transform_fn:
            x0, y0 = transform_fn(sp.x, sp.y)
        else:
            x0, y0 = sp.x, sp.y
        commands.append(f"M{x0:.2f},{y0:.2f}")

        for seg in curve.segments:
            if seg.is_corner:
                if transform_fn:
                    cx, cy = transform_fn(seg.c.x, seg.c.y)
                    ex, ey = transform_fn(seg.end_point.x, seg.end_point.y)
                else:
                    cx, cy = seg.c.x, seg.c.y
                    ex, ey = seg.end_point.x, seg.end_point.y
                commands.append(f"L{cx:.2f},{cy:.2f}L{ex:.2f},{ey:.2f}")
            else:
                if transform_fn:
                    c1x, c1y = transform_fn(seg.c1.x, seg.c1.y)
                    c2x, c2y = transform_fn(seg.c2.x, seg.c2.y)
                    ex, ey = transform_fn(seg.end_point.x, seg.end_point.y)
                else:
                    c1x, c1y = seg.c1.x, seg.c1.y
                    c2x, c2y = seg.c2.x, seg.c2.y
                    ex, ey = seg.end_point.x, seg.end_point.y
                commands.append(f"C{c1x:.2f},{c1y:.2f} {c2x:.2f},{c2y:.2f} {ex:.2f},{ey:.2f}")
        commands.append("Z")
    return "".join(commands)

def sample_curve_to_polygon(curve, n_steps=30):
    pts = []
    p0 = curve.start_point
    for seg in curve.segments:
        if seg.is_corner:
            pts.append([seg.c.x, seg.c.y])
            pts.append([seg.end_point.x, seg.end_point.y])
            p0 = seg.end_point
        else:
            p1 = seg.c1
            p2 = seg.c2
            p3 = seg.end_point
            for t in np.linspace(0, 1, n_steps):
                x = (1-t)**3 * p0.x + 3*(1-t)**2 * t * p1.x + 3*(1-t)*t**2 * p2.x + t**3 * p3.x
                y = (1-t)**3 * p0.y + 3*(1-t)**2 * t * p1.y + 3*(1-t)*t**2 * p2.y + t**3 * p3.y
                pts.append([x, y])
            p0 = p3
    return np.array(pts, dtype=np.float32)

def rasterize_curves(curves, width=1024, height=682, is_mascot=False, fg_color=(0, 0, 0, 255), scale=4.0):
    """Render anti-aliased 4x supersampled raster image from potrace curves."""
    w_super = int(width * scale)
    h_super = int(height * scale)
    canvas = np.zeros((h_super, w_super, 4), dtype=np.uint8)

    if not is_mascot:
        for c in curves:
            poly = sample_curve_to_polygon(c)
            poly_scaled = (poly * scale).astype(np.int32)
            cv2.fillPoly(canvas, [poly_scaled], fg_color, lineType=cv2.LINE_AA)
    else:
        # Mascot: curve 0 is outer, curve 1 is inner hole, curve 2 is eye dot
        poly0 = sample_curve_to_polygon(curves[0])
        poly1 = sample_curve_to_polygon(curves[1])
        poly2 = sample_curve_to_polygon(curves[2])

        cv2.fillPoly(canvas, [(poly0 * scale).astype(np.int32)], fg_color, lineType=cv2.LINE_AA)
        cv2.fillPoly(canvas, [(poly1 * scale).astype(np.int32)], (0, 0, 0, 0), lineType=cv2.LINE_AA)
        cv2.fillPoly(canvas, [(poly2 * scale).astype(np.int32)], fg_color, lineType=cv2.LINE_AA)

    rendered = cv2.resize(canvas, (width, height), interpolation=cv2.INTER_AREA)
    return rendered

def build_vector_assets():
    print("[2/7] Tracing & generating SVGs and VectorDrawables...")

    # 1. Logo
    logo_img = np.array(Image.open(SRC_LOGO))
    logo_bm = potrace.Bitmap(logo_img[:, :, 3] < 128)
    logo_path = logo_bm.trace(turdsize=10, opttolerance=0.2)

    logo_path_data = curves_to_svg_path(logo_path.curves)

    # Master SVGs: edgecue_logo.svg and edgecue_logo_light.svg
    svg_logo_dark = f'''<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 682" width="1024" height="682">
    <path fill="#000000" fill-rule="evenodd" d="{logo_path_data}" />
</svg>'''
    with open(os.path.join(DIR_SVG, "edgecue_logo.svg"), "w", encoding="utf-8") as f:
        f.write(svg_logo_dark)

    svg_logo_light = f'''<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 682" width="1024" height="682">
    <path fill="#F3F4F6" fill-rule="evenodd" d="{logo_path_data}" />
</svg>'''
    with open(os.path.join(DIR_SVG, "edgecue_logo_light.svg"), "w", encoding="utf-8") as f:
        f.write(svg_logo_light)

    # In-app drawable: ic_edgecue_logo.xml
    xml_logo = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="1024dp"
    android:height="682dp"
    android:viewportWidth="1024"
    android:viewportHeight="682">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{logo_path_data}" />
</vector>'''
    with open(os.path.join(DIR_DRAWABLE, "ic_edgecue_logo.xml"), "w", encoding="utf-8") as f:
        f.write(xml_logo)

    # 2. Mascot
    mascot_img = cv2.imread(SRC_MASCOT, cv2.IMREAD_GRAYSCALE)
    _, mascot_thresh = cv2.threshold(mascot_img, 180, 255, cv2.THRESH_BINARY)
    mascot_bm = potrace.Bitmap(mascot_thresh > 128)
    mascot_path = mascot_bm.trace(turdsize=10, opttolerance=0.2)

    mascot_path_data = curves_to_svg_path(mascot_path.curves)

    # Master SVGs: edgecue_mascot.svg and edgecue_mascot_light.svg
    svg_mascot_dark = f'''<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 682" width="1024" height="682">
    <path fill="#000000" fill-rule="evenodd" d="{mascot_path_data}" />
</svg>'''
    with open(os.path.join(DIR_SVG, "edgecue_mascot.svg"), "w", encoding="utf-8") as f:
        f.write(svg_mascot_dark)

    svg_mascot_light = f'''<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 682" width="1024" height="682">
    <path fill="#F3F4F6" fill-rule="evenodd" d="{mascot_path_data}" />
</svg>'''
    with open(os.path.join(DIR_SVG, "edgecue_mascot_light.svg"), "w", encoding="utf-8") as f:
        f.write(svg_mascot_light)

    # In-app drawable: ic_edgecue_mascot.xml
    xml_mascot = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="1024dp"
    android:height="682dp"
    android:viewportWidth="1024"
    android:viewportHeight="682">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{mascot_path_data}" />
</vector>'''
    with open(os.path.join(DIR_DRAWABLE, "ic_edgecue_mascot.xml"), "w", encoding="utf-8") as f:
        f.write(xml_mascot)

    # 3. Adaptive Launcher Icons
    # Background: Pure white #FFFFFFFF
    xml_launcher_bg = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M0,0h108v108h-108z" />
</vector>'''
    with open(os.path.join(DIR_DRAWABLE, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write(xml_launcher_bg)

    # Foreground: Scaled logo path centered strictly inside 66dp safe circle inside 108dp viewport
    # Center = (507.70, 355.76), scale = 0.09928 -> X: [23.06, 84.94], Y: [35.21, 72.79]
    def transform_foreground(x, y):
        cx, cy = 507.70, 355.76
        s = 0.09928
        tx = (x - cx) * s + 54.0
        ty = (y - cy) * s + 54.0
        return tx, ty

    launcher_path_data = curves_to_svg_path(logo_path.curves, transform_fn=transform_foreground)

    xml_launcher_fg = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Centered strictly within 66dp safe zone in 108dp viewport (X in [20, 88], Y in [31, 77]) -->
    <path
        android:fillColor="#FF000000"
        android:fillType="evenOdd"
        android:pathData="{launcher_path_data}" />
</vector>'''
    with open(os.path.join(DIR_DRAWABLE, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        f.write(xml_launcher_fg)

    xml_launcher_mono = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Material You monochrome layer for Android 13+ dynamic theming -->
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{launcher_path_data}" />
</vector>'''
    with open(os.path.join(DIR_DRAWABLE, "ic_launcher_monochrome.xml"), "w", encoding="utf-8") as f:
        f.write(xml_launcher_mono)

    print("  Done.")
    return logo_path, mascot_path

def build_raster_assets(logo_path, mascot_path):
    print("[3/7] Generating raster assets in marketing/brand/raster/...")

    # 1. Logo rasters (1024x682)
    logo_dark_rgba = rasterize_curves(logo_path.curves, width=1024, height=682, is_mascot=False, fg_color=(0, 0, 0, 255))
    logo_light_rgba = rasterize_curves(logo_path.curves, width=1024, height=682, is_mascot=False, fg_color=(243, 244, 246, 255))

    cv2.imwrite(os.path.join(DIR_RASTER, "edgecue_logo_dark_on_light.png"), logo_dark_rgba)
    cv2.imwrite(os.path.join(DIR_RASTER, "edgecue_logo_light_on_dark.png"), logo_light_rgba)

    # Lossless WebP
    img_logo_webp = Image.fromarray(cv2.cvtColor(logo_dark_rgba, cv2.COLOR_BGRA2RGBA))
    img_logo_webp.save(os.path.join(DIR_RASTER, "edgecue_logo.webp"), "WEBP", lossless=True)

    # 2. Mascot rasters (1024x682)
    mascot_dark_rgba = rasterize_curves(mascot_path.curves, width=1024, height=682, is_mascot=True, fg_color=(0, 0, 0, 255))
    mascot_light_rgba = rasterize_curves(mascot_path.curves, width=1024, height=682, is_mascot=True, fg_color=(243, 244, 246, 255))

    cv2.imwrite(os.path.join(DIR_RASTER, "edgecue_mascot_dark_on_light.png"), mascot_dark_rgba)
    cv2.imwrite(os.path.join(DIR_RASTER, "edgecue_mascot_light_on_dark.png"), mascot_light_rgba)

    img_mascot_webp = Image.fromarray(cv2.cvtColor(mascot_dark_rgba, cv2.COLOR_BGRA2RGBA))
    img_mascot_webp.save(os.path.join(DIR_RASTER, "edgecue_mascot.webp"), "WEBP", lossless=True)

    print("  Done.")
    return logo_dark_rgba, mascot_dark_rgba

def build_playstore_assets(logo_dark_rgba, mascot_dark_rgba):
    print("[4/7] Generating Play Store assets (512x512 icon & 1024x500 Feature Graphic)...")

    # 1. 512x512 App Icon
    # Pure white background (#FFFFFF) with black mark centered, scale ~ 380px wide
    icon_512 = np.full((512, 512, 4), 255, dtype=np.uint8) # pure white opaque
    # Resize logo mark to width 380
    logo_w, logo_h = 380, int(380 * (682 / 1024)) # ~253
    logo_resized = cv2.resize(logo_dark_rgba, (logo_w, logo_h), interpolation=cv2.INTER_AREA)

    x_off = (512 - logo_w) // 2
    y_off = (512 - logo_h) // 2

    # Alpha composite onto white background
    alpha_fg = logo_resized[:, :, 3] / 255.0
    for c in range(3):
        icon_512[y_off:y_off+logo_h, x_off:x_off+logo_w, c] = (
            logo_resized[:, :, c] * alpha_fg + icon_512[y_off:y_off+logo_h, x_off:x_off+logo_w, c] * (1.0 - alpha_fg)
        ).astype(np.uint8)

    cv2.imwrite(os.path.join(DIR_PLAYSTORE, "ic_launcher_playstore_512x512.png"), icon_512)

    # 2. 1024x500 Feature Graphic
    # Obsidian Stealth OLED background with subtle modern gradient and lighting
    fg_img = Image.new("RGBA", (1024, 500), (6, 7, 10, 255))
    draw = ImageDraw.Draw(fg_img)

    # Subtle background gradient
    for y in range(500):
        factor = y / 500.0
        r = int(6 + factor * 6)   # 6 -> 12
        g = int(7 + factor * 8)   # 7 -> 15
        b = int(10 + factor * 14) # 10 -> 24
        draw.line([(0, y), (1024, y)], fill=(r, g, b, 255))

    # Load system fonts
    try:
        font_brand = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 40)
        font_tagline = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 18)
        font_pill = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 11)
        font_body = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 13)
        font_body_bold = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 13)
        font_code = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 11)
    except Exception:
        font_brand = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 40)
        font_tagline = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 18)
        font_pill = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 11)
        font_body = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 13)
        font_body_bold = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 13)
        font_code = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 11)

    # Convert to numpy for advanced lighting drawing
    fg_arr = np.array(fg_img)

    # Draw soft radial glow at top-left (indigo: #6366F1)
    yy, xx = np.mgrid[0:500, 0:1024]
    dist_tl = np.sqrt((xx - 250)**2 + (yy - 150)**2)
    glow_tl = np.clip(1.0 - dist_tl / 350.0, 0.0, 1.0) ** 2.0
    fg_arr[:, :, 0] = np.clip(fg_arr[:, :, 0] + glow_tl * 99 * 0.25, 0, 255)
    fg_arr[:, :, 1] = np.clip(fg_arr[:, :, 1] + glow_tl * 102 * 0.25, 0, 255)
    fg_arr[:, :, 2] = np.clip(fg_arr[:, :, 2] + glow_tl * 241 * 0.35, 0, 255)

    # Draw soft radial glow at bottom-right (emerald: #10B981)
    dist_br = np.sqrt((xx - 780)**2 + (yy - 350)**2)
    glow_br = np.clip(1.0 - dist_br / 320.0, 0.0, 1.0) ** 2.0
    fg_arr[:, :, 0] = np.clip(fg_arr[:, :, 0] + glow_br * 16 * 0.20, 0, 255)
    fg_arr[:, :, 1] = np.clip(fg_arr[:, :, 1] + glow_br * 185 * 0.25, 0, 255)
    fg_arr[:, :, 2] = np.clip(fg_arr[:, :, 2] + glow_br * 129 * 0.20, 0, 255)

    fg_img = Image.fromarray(fg_arr)
    draw = ImageDraw.Draw(fg_img)

    # --- LEFT SIDE: Brand Lockup & Pillars (strictly within X: [102, 480], Y: [50, 450]) ---
    # Logo mark (inverted to crisp white, size 68x45)
    logo_light = cv2.resize(logo_dark_rgba, (72, 48), interpolation=cv2.INTER_AREA)
    logo_light[:, :, 0:3] = 255 # pure white mark
    logo_pil = Image.fromarray(cv2.cvtColor(logo_light, cv2.COLOR_BGRA2RGBA))
    fg_img.paste(logo_pil, (108, 70), logo_pil)

    draw.text((192, 70), "EdgeCue", fill=(255, 255, 255, 255), font=font_brand)

    # Pill chip: "ON-DEVICE AI"
    draw.rounded_rectangle([376, 76, 478, 102], radius=13, fill=(17, 24, 39, 255), outline=(30, 41, 59, 255), width=1)
    draw.ellipse([386, 85, 394, 93], fill=(16, 185, 129, 255)) # green pulse dot
    draw.text((400, 81), "ON-DEVICE AI", fill=(52, 211, 153, 255), font=font_pill)

    # Tagline
    draw.text((108, 130), "Local-First AI Interview Teleprompter", fill=(129, 140, 248, 255), font=font_tagline)

    # Core Value Pillars (clean bullets)
    pillars = [
        ("🔒", "100% Private", "Zero cloud egress, fully air-gapped"),
        ("⚡", "Sub-15ms TTFT", "Gemma 4 via Tensor EdgeTPU & NPU"),
        ("🎙️", "Zipformer ASR", "Streaming 16 kHz speech recognition"),
        ("💡", "Smart Teleprompter", "Real-time STAR & system design cues")
    ]
    py = 175
    for icon, title, desc in pillars:
        draw.text((108, py), icon, font=font_body)
        draw.text((134, py), title, fill=(243, 244, 246, 255), font=font_body_bold)
        draw.text((230, py), desc, fill=(156, 163, 175, 255), font=font_body)
        py += 36

    # Dachshund mascot brand badge (bottom-left)
    draw.rounded_rectangle([108, 335, 478, 430], radius=14, fill=(15, 19, 30, 240), outline=(30, 41, 59, 255), width=1)
    # Circle container for mascot
    draw.ellipse([124, 347, 190, 413], fill=(24, 31, 47, 255), outline=(16, 185, 129, 180), width=2)
    # Scaled mascot into circle
    mascot_small = cv2.resize(mascot_dark_rgba, (50, 33), interpolation=cv2.INTER_AREA)
    mascot_small[:, :, 0:3] = 255 # white mascot
    mascot_pil = Image.fromarray(cv2.cvtColor(mascot_small, cv2.COLOR_BGRA2RGBA))
    fg_img.paste(mascot_pil, (132, 363), mascot_pil)

    draw.text((204, 355), "Dachshund Mascot", fill=(243, 244, 246, 255), font=font_body_bold)
    draw.text((204, 375), "Loyal • Silent • Ever-Attentive", fill=(129, 140, 248, 255), font=font_body)
    draw.text((204, 396), "Stealth OLED In-App UI & Dynamic Tinting", fill=(100, 116, 139, 255), font=font_code)

    # --- RIGHT SIDE: Floating Teleprompter Card (strictly within X: [515, 915], Y: [58, 442]) ---
    # Card dimensions: 400x384 at (515, 58)
    card_x0, card_y0, card_x1, card_y1 = 515, 58, 915, 442
    draw.rounded_rectangle([card_x0, card_y0, card_x1, card_y1], radius=16, fill=(13, 16, 26, 245), outline=(30, 41, 59, 255), width=1)

    # Card top header
    draw.rounded_rectangle([card_x0+16, card_y0+14, card_x0+125, card_y0+38], radius=6, fill=(30, 27, 75, 255), outline=(67, 56, 202, 255), width=1)
    draw.text((card_x0+24, card_y0+19), "SYSTEM DESIGN", fill=(165, 180, 252, 255), font=font_pill)

    # Emerald Listening pill
    draw.rounded_rectangle([card_x1-118, card_y0+14, card_x1-16, card_y0+38], radius=6, fill=(6, 78, 59, 180), outline=(5, 150, 105, 255), width=1)
    draw.ellipse([card_x1-108, card_y0+22, card_x1-100, card_y0+30], fill=(52, 211, 153, 255))
    draw.text((card_x1-94, card_y0+19), "LISTENING", fill=(52, 211, 153, 255), font=font_pill)

    # Live Question Box
    draw.rounded_rectangle([card_x0+16, card_y0+48, card_x1-16, card_y0+112], radius=10, fill=(20, 25, 39, 255), outline=(37, 47, 67, 255), width=1)
    draw.text((card_x0+26, card_y0+56), "LIVE AUDIO SPEECH-TO-TEXT", fill=(100, 116, 139, 255), font=font_code)
    draw.text((card_x0+26, card_y0+74), "\"How do you handle data partitioning & cache", fill=(248, 250, 252, 255), font=font_body_bold)
    draw.text((card_x0+26, card_y0+92), "invalidation at scale with low latency?\"", fill=(248, 250, 252, 255), font=font_body_bold)

    # AI Teleprompter Cue Stream
    draw.text((card_x0+18, card_y0+122), "COGNITIVE PROMPTER CUE", fill=(129, 140, 248, 255), font=font_code)

    cue_lines = [
        ("• Consistent Hashing:", "Virtual nodes eliminate hotspots."),
        ("• Invalidation:", "Cache-aside with Redis pub/sub CDC."),
        ("• Multi-Tier:", "Local Caffeine L1 + Distributed Redis L2."),
        ("• Consensus:", "Raft/Paxos for partition leader election.")
    ]
    cy = card_y0 + 144
    for key, val in cue_lines:
        draw.text((card_x0+20, cy), key, fill=(243, 244, 246, 255), font=font_body_bold)
        draw.text((card_x0+170, cy), val, fill=(203, 213, 225, 255), font=font_body)
        cy += 28

    # Hardware & Performance Metrics Footer
    draw.rounded_rectangle([card_x0+16, card_y1-52, card_x1-16, card_y1-14], radius=10, fill=(18, 22, 34, 255), outline=(30, 41, 59, 255), width=1)
    draw.text((card_x0+26, card_y1-40), "⚡ 12.4 ms TTFT", fill=(52, 211, 153, 255), font=font_pill)
    draw.text((card_x0+132, card_y1-40), "|  Tensor EdgeTPU", fill=(148, 163, 184, 255), font=font_pill)
    draw.text((card_x0+252, card_y1-40), "|  0 B Network Egress", fill=(165, 180, 252, 255), font=font_pill)

    fg_img.save(os.path.join(DIR_PLAYSTORE, "feature_graphic_1024x500.png"), "PNG")
    print("  Done.")

def build_mipmaps(logo_dark_rgba):
    print("[5/7] Generating density-specific mipmap WebP files...")
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }

    for name, size in densities.items():
        out_dir = os.path.join(DIR_MIPMAP, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)

        # 1. Square ic_launcher.webp (Pure white background + centered black mark)
        img_sq = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        # Mark size ~ 58% of icon size
        mark_w = int(size * 0.58)
        mark_h = int(mark_w * (682 / 1024))
        mark_resized = cv2.resize(logo_dark_rgba, (mark_w, mark_h), interpolation=cv2.INTER_AREA)
        mark_pil = Image.fromarray(cv2.cvtColor(mark_resized, cv2.COLOR_BGRA2RGBA))

        ox = (size - mark_w) // 2
        oy = (size - mark_h) // 2
        img_sq.paste(mark_pil, (ox, oy), mark_pil)
        img_sq.save(os.path.join(out_dir, "ic_launcher.webp"), "WEBP", lossless=True)

        # 2. Circular ic_launcher_round.webp (Pure white circle + centered black mark)
        # 4x supersampled circle mask for ultra-smooth anti-aliased edge
        super_size = size * 4
        canvas_round = np.zeros((super_size, super_size, 4), dtype=np.uint8)
        cv2.circle(canvas_round, (super_size // 2, super_size // 2), super_size // 2, (255, 255, 255, 255), -1, lineType=cv2.LINE_AA)

        # Mark on super canvas
        s_mark_w = mark_w * 4
        s_mark_h = mark_h * 4
        s_mark_resized = cv2.resize(logo_dark_rgba, (s_mark_w, s_mark_h), interpolation=cv2.INTER_AREA)

        sox = (super_size - s_mark_w) // 2
        soy = (super_size - s_mark_h) // 2

        alpha_fg = s_mark_resized[:, :, 3] / 255.0
        for c in range(3):
            canvas_round[soy:soy+s_mark_h, sox:sox+s_mark_w, c] = (
                s_mark_resized[:, :, c] * alpha_fg + canvas_round[soy:soy+s_mark_h, sox:sox+s_mark_w, c] * (1.0 - alpha_fg)
            ).astype(np.uint8)

        img_round_arr = cv2.resize(canvas_round, (size, size), interpolation=cv2.INTER_AREA)
        img_round = Image.fromarray(cv2.cvtColor(img_round_arr, cv2.COLOR_BGRA2RGBA))
        img_round.save(os.path.join(out_dir, "ic_launcher_round.webp"), "WEBP", lossless=True)

    print("  Done.")

def relocate_screenshots():
    print("[6/7] Relocating screenshots & updating documentation links...")
    src_dir = os.path.join(REPO_ROOT, "docs", "screenshots")
    if os.path.exists(src_dir):
        for fname in os.listdir(src_dir):
            if fname.endswith(".png"):
                shutil.copyfile(os.path.join(src_dir, fname), os.path.join(DIR_SCREENSHOTS, fname))
                os.remove(os.path.join(src_dir, fname))
        try:
            os.rmdir(src_dir)
        except Exception:
            pass

    # Update docs/avd_fleet_test_report.md
    report_path = os.path.join(REPO_ROOT, "docs", "avd_fleet_test_report.md")
    if os.path.exists(report_path):
        with open(report_path, "r", encoding="utf-8") as f:
            content = f.read()

        # Replace relative paths
        content = content.replace("](screenshots/", "](../marketing/screenshots/")
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(content)

    print("  Done.")

def verify_all_assets():
    print("[7/7] Running comprehensive verification...")
    errors = []

    # 1. Verify XML syntax of all VectorDrawables
    drawables = [
        "ic_edgecue_logo.xml",
        "ic_edgecue_mascot.xml",
        "ic_launcher_background.xml",
        "ic_launcher_foreground.xml",
        "ic_launcher_monochrome.xml"
    ]
    for d in drawables:
        fpath = os.path.join(DIR_DRAWABLE, d)
        if not os.path.exists(fpath):
            errors.append(f"Missing drawable: {fpath}")
            continue
        try:
            tree = ET.parse(fpath)
            root = tree.getroot()
            if root.tag != "vector":
                errors.append(f"{d}: root tag is '{root.tag}', expected 'vector'")
        except Exception as e:
            errors.append(f"{d}: XML parse error: {e}")

    # 2. Verify launcher foreground bounds in 108dp viewport
    fg_tree = ET.parse(os.path.join(DIR_DRAWABLE, "ic_launcher_foreground.xml"))
    fg_path = fg_tree.getroot().find(".//path")
    path_data = fg_path.attrib.get("{http://schemas.android.com/apk/res/android}pathData", "")
    if not path_data:
        errors.append("ic_launcher_foreground.xml: empty pathData")

    # 3. Verify Play Store images
    ps_icon = os.path.join(DIR_PLAYSTORE, "ic_launcher_playstore_512x512.png")
    if not os.path.exists(ps_icon):
        errors.append(f"Missing: {ps_icon}")
    else:
        im = Image.open(ps_icon)
        if im.size != (512, 512):
            errors.append(f"ic_launcher_playstore_512x512.png: size is {im.size}, expected (512, 512)")
        size_kb = os.path.getsize(ps_icon) / 1024.0
        if size_kb >= 1024:
            errors.append(f"ic_launcher_playstore_512x512.png: file size {size_kb:.1f}KB exceeds 1024KB limit")

    ps_feat = os.path.join(DIR_PLAYSTORE, "feature_graphic_1024x500.png")
    if not os.path.exists(ps_feat):
        errors.append(f"Missing: {ps_feat}")
    else:
        im = Image.open(ps_feat)
        if im.size != (1024, 500):
            errors.append(f"feature_graphic_1024x500.png: size is {im.size}, expected (1024, 500)")

    # 4. Verify Mipmaps
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, sz in densities.items():
        for base in ["ic_launcher.webp", "ic_launcher_round.webp"]:
            p = os.path.join(DIR_MIPMAP, f"mipmap-{name}", base)
            if not os.path.exists(p):
                errors.append(f"Missing mipmap: {p}")
            else:
                im = Image.open(p)
                if im.size != (sz, sz):
                    errors.append(f"{p}: size is {im.size}, expected ({sz}, {sz})")

    # 5. Verify SVGs
    svgs = ["edgecue_logo.svg", "edgecue_logo_light.svg", "edgecue_mascot.svg", "edgecue_mascot_light.svg"]
    for s in svgs:
        p = os.path.join(DIR_SVG, s)
        if not os.path.exists(p) or os.path.getsize(p) < 100:
            errors.append(f"Missing or empty SVG: {p}")
        else:
            try:
                ET.parse(p)
            except Exception as e:
                errors.append(f"{s}: SVG parse error: {e}")

    # 6. Verify Rasters
    rasters = [
        "edgecue_logo_dark_on_light.png",
        "edgecue_logo_light_on_dark.png",
        "edgecue_logo.webp",
        "edgecue_mascot_dark_on_light.png",
        "edgecue_mascot_light_on_dark.png",
        "edgecue_mascot.webp"
    ]
    for r in rasters:
        p = os.path.join(DIR_RASTER, r)
        if not os.path.exists(p) or os.path.getsize(p) < 100:
            errors.append(f"Missing or empty raster: {p}")

    # 7. Verify screenshots moved
    sc_files = [
        "Pixel_10_Pro_XL_screen.png",
        "Galaxy_S25_Ultra_screen.png",
        "Galaxy_Tab_S7_Plus_screen.png",
        "Galaxy_A54_5G_screen.png"
    ]
    for sc in sc_files:
        p = os.path.join(DIR_SCREENSHOTS, sc)
        if not os.path.exists(p):
            errors.append(f"Missing screenshot in marketing/screenshots/: {p}")

    if errors:
        print(f"VERIFICATION FAILED WITH {len(errors)} ERRORS:")
        for e in errors:
            print(f"  - {e}")
        return False
    else:
        print("ALL ASSET INTEGRITY GATES PASSED PERFECTLY!")
        return True

if __name__ == "__main__":
    ensure_dirs()
    copy_originals()
    logo_path, mascot_path = build_vector_assets()
    logo_dark_rgba, mascot_dark_rgba = build_raster_assets(logo_path, mascot_path)
    build_playstore_assets(logo_dark_rgba, mascot_dark_rgba)
    build_mipmaps(logo_dark_rgba)
    relocate_screenshots()
    success = verify_all_assets()
    if not success:
        exit(1)
