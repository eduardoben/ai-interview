# EdgeCue Brand Identity & Design System Guidelines

**Version:** 1.0.0  
**Effective Date:** 2026-11-05  
**Developer / Publisher:** Himmler Benitez  
**Contact Email:** hbenitez@hectisai.com  
**Package Namespace:** `com.edgecue.app`  
**Brand Architecture:** Edge + Cue (Zero Cloud Egress • Real-Time Cognitive Teleprompter)

---

## 1. Executive Summary & Brand Architecture

**EdgeCue** is a zero-cloud, local-first artificial intelligence teleprompter application engineered for technical, behavioral, and system design interviews. The brand identity fuses cutting-edge on-device machine intelligence with acute auditory attentiveness and candidate composure.

```
       ┌──────────────────────────┐        ┌──────────────────────────┐
       │           EDGE           │   +    │           CUE            │
       │  100% On-Device Neural   │        │   Real-Time Cognitive    │
       │   Execution & Privacy    │        │  Prompts & Teleprompter  │
       └──────────────────────────┘        └──────────────────────────┘
                                        ▼
       ┌──────────────────────────────────────────────────────────────┐
       │                           EDGECUE                            │
       │           "Local-First AI Interview Teleprompter"            │
       └──────────────────────────────────────────────────────────────┘
```

### The Two Pillars of EdgeCue
1. **Edge (Neural Autonomy & Privacy Guarantee):**
   - 100% on-device speech-to-text (Sherpa-ONNX streaming Zipformer transducer) and large language model inference (LiteRT-LM Gemma 4).
   - Zero network egress. The device functions seamlessly in full airplane/air-gapped mode. Audio frames never leave the handset's volatile RAM.
2. **Cue (Real-Time Cognitive Assistance):**
   - Low-latency cognitive prompts, structural STAR framework cues, and architectural bullet points rendered on an unobtrusive floating HUD or full-screen teleprompter.
   - Designed to support candidate recall and confidence under high-stress interview conditions.

---

## 2. Mascot Identity: The Dachshund Symbol

The official mascot of EdgeCue is the **minimalist line-art Dachshund**. 

```
                               __
                       \_____/'  )____
                        /            /
                       /  /____/  /
                      (_/      (_/
```

### Mascot Symbolism
- **Loyalty & Unwavering Support:** The Dachshund remains steadfastly at the candidate's side throughout the interview journey, reducing anxiety and instilling confidence.
- **Acute Auditory Attentiveness:** Characterized by its distinctive droop ear and alert head posture, the Dachshund symbolizes vigilant, continuous listening and acoustic precision.
- **Tenacity & Problem-Solving:** Historically bred for persistence and problem-solving, the Dachshund embodies technical thoroughness and perseverance.
- **Calm, Stealth Demeanor:** Silent, unobtrusive, and composed, perfectly matching the zero-glare, dark Stealth OLED visual aesthetic.

---

## 3. Visual Identity & Logo System

The EdgeCue visual identity comprises two core vector marks:

### 3.1 Primary Brand Mark: "EC" Monogram with Integrated Dachshund
- **Geometry:** A slanted, dynamic italic "E" interlocking with a sweeping "C" that fluidly integrates the alert silhouette, droop ear, circular eye dot, and elongated back of the Dachshund.
- **Aspect Ratio:** 3:2 horizontal landscape format (1024 × 682 px master canvas).
- **Master Files:**
  - `marketing/brand/svg/edgecue_logo.svg` (Solid black `#000000` mark on transparent background).
  - `marketing/brand/svg/edgecue_logo_light.svg` (Solid light `#F3F4F6` mark on transparent background).
  - `app/src/main/res/drawable/ic_edgecue_logo.xml` (Android VectorDrawable with `#FFFFFFFF` tintable base fill).

### 3.2 Mascot Mark: Standalone Full-Body Dachshund
- **Geometry:** Minimalist continuous stroke line-art depicting an alert Dachshund facing right. Features smooth rounded stroke caps (`round`), rounded corner joins (`round`), uniform stroke weight, and an enclosed circular eye dot.
- **Aspect Ratio:** 3:2 landscape format (1024 × 682 px master canvas).
- **Master Files:**
  - `marketing/brand/svg/edgecue_mascot.svg` (Solid black `#000000` mark on transparent background).
  - `marketing/brand/svg/edgecue_mascot_light.svg` (Solid light `#F3F4F6` mark on transparent background).
  - `app/src/main/res/drawable/ic_edgecue_mascot.xml` (Android VectorDrawable with `#FFFFFFFF` tintable base fill).

---

## 4. Color Palette & Token Architecture

EdgeCue employs a dual-palette architecture: the **Stealth OLED System** for low-glare in-app ergonomics, and the **High-Contrast Pure White System** for home screen launcher recognition.

### 4.1 Stealth OLED In-App Palette (Ocular Ergonomics)
Engineered specifically for OLED smartphone screens to eliminate eyeglass reflections during webcam interviews, minimize peripheral distraction, and conserve battery power during multi-hour interview sessions.

| Token | Hex Code | RGB | Role / Usage |
| :--- | :--- | :--- | :--- |
| `OledBackground` | `#06070A` | `(6, 7, 10)` | Primary screen canvas; true black OLED background |
| `OledSurface` | `#0E1017` | `(14, 16, 23)` | Card containers, teleprompter docks, sheet surfaces |
| `OledSurfaceElevated` | `#161922` | `(22, 25, 34)` | Floating HUD overlay background, modal dialogs |
| `OledBorder` | `#1E2330` | `(30, 35, 48)` | Subtle 1dp structural dividers and container outlines |
| `OledTextPrimary` | `#F3F4F6` | `(243, 244, 246)` | High-legibility teleprompter text, headings, primary labels |
| `OledTextSecondary` | `#9CA3AF` | `(156, 163, 175)` | Secondary cues, metadata, category labels, timestamps |
| `OledTextMuted` | `#4B5563` | `(75, 85, 99)` | Placeholder text, disabled actions, fine print |
| `OledAccentIndigo` | `#6366F1` | `(99, 102, 241)` | Primary brand accent, active selection chips, CTA button |
| `OledAccentIndigoSoft` | `#818CF8` | `(129, 140, 248)` | Subtitles, glowing borders, Dachshund standby badge |
| `OledStatusEmerald` | `#10B981` | `(16, 185, 129)` | Active listening state, STT speech detection, TTFT metrics |
| `OledStatusAmber` | `#F59E0B` | `(245, 158, 11)` | LLM synthesizing state, thermal warning notices |
| `OledStatusRose` | `#EF4444` | `(239, 68, 68)` | Error state, microphone permission denied, battery critical |

### 4.2 Launcher Icon High-Contrast System (21:1 Contrast Ratio)
Android home screens and Google Play Store listings require instant optical recognition across light, dark, and wallpaper-heavy environments.

| Asset | Background | Foreground Mark | Contrast Ratio | Specification |
| :--- | :--- | :--- | :--- | :--- |
| **Adaptive Launcher Icon** | Pure White (`#FFFFFFFF`) | Solid Black (`#FF000000`) | **21:1 (Maximum)** | Centered inside 66dp safe circle |
| **Material You Monochrome** | System Dynamic Tint | Solid White (`#FFFFFFFF`) | Dynamic (Android 13+) | Scaled identical to foreground |
| **Play Store App Icon** | Pure White (`#FFFFFF`) | Solid Black (`#000000`) | **21:1** | Exact 512×512 32-bit PNG, <1024KB |

---

## 5. Safe Zones, Geometry & Viewport Specifications

### 5.1 Android Adaptive Launcher Icon (`108dp × 108dp` Viewport)
Android adaptive icons use a `108dp × 108dp` viewport with a centered **`66dp` diameter circular safe zone** ($R = 33\text{dp}$). OEM masks (circles, squircles, rounded squares, pebbles) crop anything outside this diameter.

```
       0                    54                   108
     0 ┌─────────────────────────────────────────┐
       │                   108dp                 │
       │             ┌───────────────┐           │
       │          .-'                 '-.        │
       │        .'                       '.      │
    31 │       /     ┌───────────────┐     \     │
       │      │      │ X: [20, 88]   │      │    │
    54 │------│------│ Y: [31, 77]   │------│----│ Center (54, 54)
       │      │      │  Safe Circle  │      │    │
    77 │       \     └───────────────┘     /     │
       │        '.                       .'      │
       │          '-.                 .-'        │
       │             └───────────────┘           │
       │              66dp Safe Zone             │
   108 └─────────────────────────────────────────┘
```

- **Bounding Box Rules:** All path coordinates in `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` are mathematically guaranteed to reside within:
  - $X \in [20.0, 88.0]$ (Measured: $[22.95, 84.94]$)
  - $Y \in [31.0, 77.0]$ (Measured: $[35.17, 72.79]$)
- **Zero OEM Mask Clipping:** Guarantees that neither the "E" nor the Dachshund snout/paw is clipped on any Samsung OneUI, Google Pixel, Xiaomi HyperOS, or OnePlus OxygenOS launcher.

### 5.2 Google Play Store Feature Graphic (`1024 × 500 px`)
- **Canvas Size:** `1024 × 500 px` PNG.
- **Safe Zone:** `820 × 400 px` centered ($X \in [102, 922]$, $Y \in [50, 450]$).
- **Margin Rules:**
  - Horizontal margin: $102\text{ px}$ on left and right borders.
  - Vertical margin: $50\text{ px}$ on top and bottom borders.
- **Content Placement:** All typography, logos, mascot badges, and teleprompter card elements are strictly confined inside the safe zone to avoid edge cropping on small-screen mobile Play Store layouts.

---

## 6. Dynamic In-App Integration Rules

### 6.1 State-Driven Mascot Tinting
The Dachshund mascot adapts dynamically to pipeline lifecycle states using Jetpack Compose `ColorFilter.tint()`:

```kotlin
val mascotTint = when (pipelineState) {
    PipelineState.LISTENING -> OledStatusEmerald       // Active speech recognition
    PipelineState.GENERATING -> OledStatusAmber         // On-device LLM synthesizing
    PipelineState.STANDBY -> OledAccentIndigoSoft       // Ready & waiting for questions
    PipelineState.ERROR -> OledStatusRose               // Error / mic blocked
    else -> OledTextPrimary                             // Default neutral
}
```

### 6.2 UI Component Placement
1. **Prompter Header Bar:** EdgeCue logo rendered at `24.dp` height next to the active pipeline status badge.
2. **Teleprompter Standby State:** `56.dp` circular elevated surface badge displaying the Dachshund mascot tinted `OledStatusEmerald` or `OledAccentIndigoSoft`.
3. **Minimized Floating HUD Pill:** Dachshund mascot icon at `16.dp` followed by `"EdgeCue"` label and status indicator dot, conforming to WCAG/Google Play `48 × 48 dp` minimum touch target rules.
4. **Expanded HUD Header:** Mini Dachshund mascot icon anchoring the top-left status bar.
5. **Settings About Card:** OLED elevated badge featuring the EdgeCue logo, title `"EdgeCue"`, subtitle `"Local-First AI Interview Cue"`, and application version.

---

## 7. Brand Governance: Do's and Don'ts

### Do
- Always use the pure white (`#FFFFFFFF`) background layer for `ic_launcher_background.xml` and Google Play Store icons to ensure maximum 21:1 contrast.
- Always preserve the Dachshund's 3:2 aspect ratio when scaling in UI composables or raster exports.
- Always use vector XML drawables (`ic_edgecue_logo.xml`, `ic_edgecue_mascot.xml`) for in-app UI to support dynamic runtime tinting.
- Always adhere to the 820×400 safe zone for all promotional and marketing artwork.

### Don't
- Do not apply dark or black background layers to the adaptive launcher icon (creates low-contrast home-screen visibility).
- Do not bake rounded or squircle corners into the 512×512 Google Play Store app icon (Google Play automatically applies the mask).
- Do not distort, stretch, or vertically compress the Dachshund mascot geometry.
- Do not introduce non-white base fills into `ic_edgecue_logo.xml` or `ic_edgecue_mascot.xml` (interferes with Compose `ColorFilter.tint()`).
- Do not claim cloud or server connectivity in any marketing copy (EdgeCue is 100% on-device and air-gapped).

---

## 8. Master Asset Directory Manifest

```
marketing/
├── brand/
│   ├── BRAND_GUIDELINES.md                 # This authoritative document
│   ├── originals/                          # Preserved original user uploads
│   │   ├── edgecue_logo_source.png         # 1024x682 original PNG
│   │   ├── edgecue_mascot_source.jpg       # 1024x682 original JPEG
│   │   ├── media_1793856702152_273e657f.png
│   │   └── media_1793856795977_e2c2bab5.jpg
│   ├── svg/                                # Master scalable vector files
│   │   ├── edgecue_logo.svg                # Dark mark (#000000)
│   │   ├── edgecue_logo_light.svg          # Light mark (#F3F4F6)
│   │   ├── edgecue_mascot.svg              # Standalone Dachshund (#000000)
│   │   └── edgecue_mascot_light.svg        # Standalone Dachshund (#F3F4F6)
│   └── raster/                             # High-resolution trimmed exports
│       ├── edgecue_logo_dark_on_light.png  # 1024x682 transparent PNG
│       ├── edgecue_logo_light_on_dark.png  # 1024x682 transparent PNG
│       ├── edgecue_logo.webp               # 1024x682 lossless WebP
│       ├── edgecue_mascot_dark_on_light.png
│       ├── edgecue_mascot_light_on_dark.png
│       └── edgecue_mascot.webp
├── play-store/                             # Google Play Store listing assets
│   ├── ic_launcher_playstore_512x512.png   # 512x512 32-bit PNG (<1MB)
│   └── feature_graphic_1024x500.png        # 1024x500 Feature Graphic (820x400 safe zone)
├── copy/
│   └── play_store_listing.md               # Character-validated ASO store copy
└── screenshots/                            # Relocated AVD fleet test screenshots
    ├── Galaxy_A54_5G_screen.png
    ├── Galaxy_S25_Ultra_screen.png
    ├── Galaxy_Tab_S7_Plus_screen.png
    └── Pixel_10_Pro_XL_screen.png
```
