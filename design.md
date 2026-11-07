# Design System Specification: EdgeCue Solid Luxury & High-Contrast OLED

> **Document Type:** `design.md`  
> **Target:** EdgeCue AI Copilot (`com.edgecue.app`)  
> **Platform Mode:** Android-Native Premium (Jetpack Compose / Material 3 Custom Dark Scheme)  
> **Focus Scope:** Solid Color Palette, Solid Surface Hierarchy, Machined Hairline Borders & High-Contrast Typography *(Strictly preserves existing UI layout, component hierarchy, and screen distribution)*

---

## 1. Design Philosophy & Color Theory

EdgeCue operates as an on-device, zero-latency AI copilot for high-stakes technical interviews. Based on `/high-end-visual-design` principles, this visual language rejects the muddy, washed-out greyscale trap in favor of **Solid Luxury Hardware Aesthetics** (drawing inspiration from Linear, Apple Pro display instruments, and Teenage Engineering OP-1 Field).

### Key Chromatic & Art Direction Rules
1. **Solid, Confident Jewel Accents:** Never use desaturated grey to convey "minimal". Active listening uses solid botanical pine emerald (`#30A46C`, non-neon); hardware and AI acceleration use solid electric indigo (`#6366F1`); processing states use solid warm amber gold (`#FFA800`); and destructive stops use precision crimson coral (`#FF453A`).
2. **Deep Solid OLED Canvas with Rich Surface Undertones:** Canvas is true pitch obsidian (`#07080B`), while card surfaces feature solid midnight slate (`#0E1118` → `#161B26` → `#1E2433`) with subtle dark spectral depth, eliminating washed-out grey tones.
3. **Solid Machined Hairline Borders:** Replace milky translucent alpha washes with **solid, mathematically tuned hairline borders** (`#1D2230` / `#2A3247` / `#3E4A68`) that look like crisp, CNC-milled hardware edges.
4. **Option A High-Contrast Solid Primary CTA:** Primary execution triggers (`"Start Practice Copilot"`, `"Answer Now"`, `"Apply Golden Preset"`) utilize a **Solid Pure White (`#FFFFFF`)** surface with **Pure Pitch Black (`#000000`)** SemiBold typography for instant peripheral recognition and maximum tactile authority (19.5:1 contrast).
5. **Zero Structural Disruption:** Every screen (`PrompterScreen`, `SettingsScreen`, `UsageScreen`, `InfoScreen`, `FloatingHudOverlay`, and `AppShell`) retains its exact layout, spacing rhythm, and component distribution.

---

## 2. Solid Color Palette & Token Specification

### A. Solid Base & Background Surfaces
| Design Token | Compose Token (`OledTheme.kt`) | Hex / ARGB | Purpose / Usage |
| :--- | :--- | :--- | :--- |
| `surface-canvas` | `OledBackground` | `#07080B` (`0xFF07080B`) | Deepest root canvas; true solid pitch obsidian |
| `surface-card-base` | `OledSurface` | `#0E1118` (`0xFF0E1118`) | Primary structural cards (`TeleprompterDisplay`, `SettingsGroupCard`, `NavigationBar`) |
| `surface-card-elevated` | `OledSurfaceHigh` | `#161B26` (`0xFF161B26`) | Floating HUD overlays, question callouts, active tab containers, modal sheets |
| `surface-card-subtle` | `OledSurfaceElevated` | `#1E2433` (`0xFF1E2433`) | Secondary nested chips, Lite RAG document badges, interactive transcript turns |
| `surface-input` | `OledSurfaceInput` | `#0A0C11` (`0xFF0A0C11`) | Inset text fields, custom question wells, fenced code block cards (`CodeBlockCard`) |

### B. High-Contrast Typography & Text Hierarchy
| Design Token | Compose Token (`OledTheme.kt`) | Hex / ARGB | Weight & Role | Contrast / Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `text-primary` | `OledTextPrimary` | `#FFFFFF` (`0xFFFFFFFF`) | Regular (`400`) – SemiBold (`600`) | 100% Solid White: Titles, active questions, bold STAR keywords |
| `text-secondary` | `OledTextSecondary` | `#CBD5E1` (`0xFFCBD5E1`) | Regular (`400`) | Solid Crisp Silver: Teleprompter body stream, descriptions, secondary copy |
| `text-tertiary` | `OledTextMuted` | `#8892B0` (`0xFF8892B0`) | Regular (`400`) – Medium (`500`) | Solid Slate Steel: Section overlines, TTFT metadata, disclaimers |
| `text-whisper` | `OledTextWhisper` | `#5A647E` (`0xFF5A647E`) | Medium (`500`) | Solid Subtle Steel: Inactive tags, hardware spec strings |
| `text-inverted` | `OledTextInverted` | `#000000` (`0xFF000000`) | SemiBold (`600`) | Pure pitch black label text inside solid white (`#FFFFFF`) primary buttons |

### C. Solid Jewel Accents (High Clarity & State Distinction)
| Semantic State | Design Token | Compose Token (`OledTheme.kt`) | Hex / ARGB | Solid Role in EdgeCue |
| :--- | :--- | :--- | :--- | :--- |
| **Active / Listening** | `status-active` | `OledStatusEmerald` | `#30A46C` (`0xFF30A46C`) | **Solid Botanical Pine Emerald:** Calm, non-neon solid listening indicator, live speech bar, 4dp bullet markers |
| **Hardware / NPU / Accent** | `status-npu` | `OledAccentIndigo` / `OledAccentIndigoSoft` | `#6366F1` (`0xFF6366F1`) | **Solid Electric Indigo:** Tensor G5 NPU badges, question accent bar, slider active tracks |
| **Synthesizing / Standby** | `status-standby` | `OledStatusAmber` | `#FFA800` (`0xFFFFA800`) | **Solid Radiant Amber Gold:** "Synthesizing..." spinner, Paused state, Thermal badge |
| **Alert / Destructive** | `status-alert` | `OledStatusCoral` | `#FF453A` (`0xFFFF453A`) | **Solid Precision Crimson Coral:** Error banners, Stop button tint & border |
| **Subtle Neutral** | `status-neutral` | `OledStatusNeutral` | `#5A647E` (`0xFF5A647E`) | **Solid Slate Graphite:** Inactive status dots & background badges |

### D. Solid Machined Hairline Borders (`0.5dp` Precision)
| Design Token | Compose Token (`OledTheme.kt`) | Hex / ARGB | Purpose |
| :--- | :--- | :--- | :--- |
| `border-hairline` | `OledBorder` | `#1D2230` (`0xFF1D2230`) | Solid 0.5dp card separation (eliminates milky alpha haze) |
| `border-subtle` | `OledBorderSubtle` | `#2A3247` (`0xFF2A3247`) | Solid interactive button outlines, unfocused text inputs |
| `border-selected` | `OledBorderHigh` | `#3E4A68` (`0xFF3E4A68`) | Solid active tab pill outline, selected segmented tab border |

### E. Interactive Action Tokens (Option A: Solid Pure White)
| Design Token | Compose Mapping | Background | Foreground | Behavior |
| :--- | :--- | :--- | :--- | :--- |
| `btn-primary-bg` | `OledPrimaryAction` | `#FFFFFF` (Solid Pure White) | `#000000` (`OledTextInverted`) | Hero execution CTA (`"Start Practice Copilot"`, `"Answer Now"`) |
| `btn-primary-pressed` | `OledPrimaryActionPressed` | `#E2E8F0` | `#000000` | Tactile tap feedback state |
| `btn-secondary-bg` | `OledSurfaceHigh` | `#161B26` | `#CBD5E1` | Secondary controls (`"Pause"`, `"Tuning Guide"`) with solid border |

---

## 3. Typography Scale & Pairings

### Font Families
- **Primary UI & Teleprompter Typeface (`SansSerif`):** `Inter` or `Geist Sans` (fallback to Android `Roboto` / `FontFamily.SansSerif`).
- **Technical & Telemetry Typeface (`Monospace`):** `JetBrains Mono` or `Geist Mono` (`FontFamily.Monospace` with `"tnum"`).

### Type Scale Breakdown (`OledTypography`)
| Role | Compose Style | Size & Line Height | Weight & Tracking | Color Token |
| :--- | :--- | :--- | :--- | :--- |
| **Display / App Brand** | Header Brand / HUD Title | `15.sp` / `20.sp` | Bold (`700`), uppercase, `letterSpacing = 0.12.em` | `#FFFFFF` (`text-primary`) |
| **Screen & Card Header** | `headlineSmall` / `titleMedium` | `16.sp – 20.sp` / `24.sp` | SemiBold (`600`), `letterSpacing = (-0.15).sp` | `#FFFFFF` (`text-primary`) |
| **Section Kicker / Overline** | `labelSmall` (`RESPONSE STYLE`) | `10.sp` / `14.sp` | Medium (`500`), uppercase, `letterSpacing = 1.0.sp` | `#8892B0` (`text-tertiary`) |
| **Teleprompter Stream** | `FormattedTeleprompterAnswer` | `20.sp`, `lineHeight = 1.52x` | Regular (`400`) body in `#CBD5E1`; SemiBold (`600`) bold keywords in `#FFFFFF` | `#CBD5E1` / `#FFFFFF` |
| **Body & List Copy** | `bodyMedium` | `13.sp` / `20.sp` | Regular (`400`), `letterSpacing = 0.1.sp` | `#CBD5E1` (`text-secondary`) |
| **Telemetry & Numbers** | Monospace pills, TTFT | `12.sp` / `18.sp` | Medium (`500`), `FontFamily.Monospace`, `"tnum"` | `#FFFFFF` or `#6366F1` |

---

## 4. Component-Level Implementation

1. **`PrompterHeader` (`PrompterHeader.kt`)**
   - **Status Pill:** `#0E1118` surface with solid `#1D2230` border. Precision 6dp solid dot in `#30A46C` (Botanical Pine Emerald, non-neon).
   - **TTFT Latency:** Monospace readout in `#8892B0` (Solid Slate Steel).
   - **SoC Hardware Pill:** CPU chip icon in vibrant `#6366F1` (Electric Indigo), text in `#CBD5E1`.
2. **`TeleprompterDisplay` & `FormattedTeleprompterAnswer`**
   - **Active Question Callout:** `#161B26` surface with a **3dp solid Electric Indigo (`#6366F1`)** accent bar on the left; question in pure solid white (`#FFFFFF`).
   - **Teleprompter Stream Body:** Body in `#CBD5E1` with **solid Botanical Pine Emerald (`#30A46C`)** 4dp geometric bullets. Key `**STAR anchors & technical terms**` in pure solid white (`#FFFFFF`) bold.
   - **Fenced Code Blocks:** Deep solid `#0A0C11` well with solid `#1D2230` border; code in `#CBD5E1` `Monospace`.
3. **`ThumbControlDock` (`ThumbControlDock.kt`)**
   - **Hero CTA ("Start Practice Copilot" / "Answer Now"):** Solid pure white (`#FFFFFF`) with pure pitch black (`#000000`) SemiBold text.
   - **"Pause" / "Clear":** Solid card surface `#0E1118` with `#2A3247` border and `#CBD5E1` text.
   - **"Stop":** Solid crimson tint `rgba(255, 69, 58, 0.14)` with solid `#FF453A` border and text.
4. **`SettingsScreen` (`SettingsScreen.kt`)**
   - **Response Style Segmented Control:** Selected pill in solid pure white (`#FFFFFF`) with pitch-black text (`#000000`), unselected in `#CBD5E1`.
   - **Sliders & Switches:** Active tracks in solid `#6366F1`, thumbs in solid pure white (`#FFFFFF`).
   - **Lite RAG Chips:** Active status dots in solid `#30A46C` (Botanical Pine Emerald).
5. **`FloatingHudOverlay` (`FloatingHudOverlay.kt`)**
   - Deep solid `#07080B` (96% alpha) with solid `#1D2230` border and pure dark ambient drop shadow.
