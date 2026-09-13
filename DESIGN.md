# Saarthi AI — Design System & UI/UX Specification (Google Stitch AI)

## 1. Vision & Core Philosophy

**Saarthi AI (सारथी)** is a screen-aware, voice-driven digital companion designed specifically for older adults and digitally hesitant citizens. It demystifies complex, high-friction mobile interfaces (such as UPI banking apps, pensions, and government portals) by acting as an empathetic, private guide.

### Core Principles
1. **Zero Cognitive Friction**: Maximize clarity, minimize visual noise. One primary action per viewport state.
2. **WCAG AAA Compliance**:
   - High contrast ratios ($\ge 7:1$ for normal text, $\ge 4.5:1$ for large headings).
   - Generous touch targets ($\ge 56\text{dp} \times 56\text{dp}$, exceeding standard 48dp).
   - Large, legible type scale: Base body $\ge 18\text{sp}$, subheaders $20\text{--}24\text{sp}$, headings $28\text{--}34\text{sp}$.
   - Never rely on icons alone; every icon is accompanied by a descriptive text label.
3. **Radical Privacy by Design**:
   - Zero cloud screen telemetry; 100% on-device Gemini Nano inference.
   - Screen tree ephemeral in-memory processing with automatic garbage collection.
   - Prominent, instant "Kill Switch" to give users total control.

---

## 2. Color Palette & Theming Tokens

| Token Name | Hex Code | Semantic Role & Rationale |
| :--- | :--- | :--- |
| `primary-sapphire` | `#0F294A` | Deep Sapphire Blue. Conveys institutional trust, security, and stability. Used for headers, top bars, primary cards. |
| `primary-sapphire-dark` | `#0A1C33` | Deep midnight anchor for dark modes and high-contrast text. |
| `accent-amber` | `#D97706` | Warm Amber. Conveys human warmth, guidance, and active attention without triggering panic or alarm. |
| `accent-amber-light` | `#F59E0B` | Glowing highlight box border, pulsing mic aura, active states. |
| `accent-amber-glow` | `#FEF3C7` | Soft amber ambient background tint and focus rings. |
| `background-surface` | `#FBFBF9` | Warm off-white / soft cream linen. Eliminates harsh screen glare for aging eyes while maintaining maximum contrast against sapphire typography. |
| `surface-card` | `#FFFFFF` | Crisp pure white for elevated content cards with gentle border delineation. |
| `text-primary` | `#0B192C` | Deep ink tone ensuring $>10:1$ contrast against card surfaces. |
| `text-secondary` | `#334155` | Slate grey for secondary guidance instructions ($\ge 7:1$ contrast). |
| `status-secure-green` | `#047857` | Deep emerald green for privacy badges and "Service Active" indicators. |
| `status-alert-red` | `#B91C1C` | Crimson red reserved strictly for the Instant Kill Switch / Emergency Stop. |

---

## 3. Typography Hierarchy

- **Font Family**: Inter, Roboto, or system sans-serif with medium/semi-bold weights for improved legibility.
- **Headline 1**: 32sp / Bold / Line-height 40sp (Screen titles)
- **Headline 2**: 24sp / Semi-Bold / Line-height 32sp (Section headers)
- **Body Large**: 20sp / Medium / Line-height 28sp (Primary instructions, button labels)
- **Body Regular**: 18sp / Regular / Line-height 26sp (Explanatory copy, status descriptions)
- **Caption / Badge**: 16sp / Semi-Bold / Line-height 22sp (Privacy badges, state tags)

---

## 4. UI Screens Overview

### Screen 1: Onboarding & Permissions Flow (`screen_onboarding`)
- **Objective**: Explain the two critical system permissions (**Accessibility Service** and **System Alert Window**) in simple, reassuring, grandparent-friendly language.
- **Key Elements**:
  - Warm header with Saarthi icon and greeting.
  - "Grandson Guarantee" privacy banner highlighting on-device security & zero data storage.
  - Step 1: "Allow Saarthi to See Button Names" (Accessibility Service permission).
  - Step 2: "Allow Saarthi to Draw Guidance Box" (Display Over Other Apps permission).
  - Big, inviting primary CTA: "Enable Guidance" with tactile visual feedback.

### Screen 2: Main Settings & Control Dashboard (`screen_dashboard`)
- **Objective**: Provide a safe, transparent home base where the senior or their caregiver can configure languages, test guidance, and verify privacy.
- **Key Elements**:
  - Top Hero Status Banner: Active state toggle with pulsing green shield ("Saarthi is Protecting & Ready").
  - Emergency Kill Switch Button: Prominent full-width button to instantly terminate accessibility service.
  - Regional Language Selector: Large cards/chips with native scripts (e.g., "हिन्दी", "मराठी", "தமிழ்", "తెలుగు", "English").
  - Voice Speed & Volume Slider: Simple segmented control ("Slow", "Comfortable", "Normal").
  - Privacy Audit Card: Live indicator showing "On-Device RAM Only", "Screen Telemetry: 0 Bytes Sent", "Redactor: Active".
  - Sandbox Button: "Try Asking Saarthi (Practice Run)".

### Screen 3: Floating Companion Overlay (`screen_overlay_widget`)
- **Objective**: An always-accessible, floating assistant widget that sits unobtrusively on any third-party app (e.g., PhonePe, GPay, DigiLocker).
- **States**:
  1. **Idle / Floating Head**: Draggable circular badge (64x64dp) with Deep Sapphire fill, glowing Warm Amber ring, and high-contrast microphone icon.
  2. **Active Listening Modal / Bottom Sheet**: Expands when tapped into a comforting bottom sheet. Shows an animated warm amber voice wave, regional prompt: *"Listening... बोलिए, मैं सुन रहा हूँ"* with a large cancel button.
  3. **Visual Highlight Overlay**: A glowing amber rounded rectangle ($4\text{dp}$ stroke + soft radial blur) placed directly over the target button coordinates, accompanied by an adjacent high-contrast speech bubble explaining the action.
