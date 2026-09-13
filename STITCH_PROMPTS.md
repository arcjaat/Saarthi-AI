# Google Stitch AI — UI Generation Prompts for Saarthi AI

Use these prompts directly in the Google Stitch AI console or prompt bar. Each prompt is engineered with explicit layout hierarchies, exact hex color codes, typography constraints, WCAG AAA accessibility rules, and component requirements tailored for senior citizens.

---

## Prompt 1: Onboarding & Permissions Flow (`screen_onboarding`)

```text
Design a warm, reassuring, and ultra-accessible Android onboarding screen for "Saarthi AI", a digital assistant for elderly users (60+ years old) in India navigating complex banking and government apps.

Design Style & Theme:
- Background: Glare-free soft cream off-white (#FBFBF9).
- Primary Color: Deep Sapphire Blue (#0F294A) for headers and main cards.
- Accent Color: Warm Amber (#D97706) for interactive primary action buttons and focus states.
- Typography: Clean sans-serif (Inter/Roboto), minimum 18sp for body text, 28sp-32sp for headings, high contrast dark slate (#0B192C) text.
- Accessibility: WCAG AAA compliant, minimum touch targets 60dp, clear icons paired with legible Indian English/Hindi subtext.

Layout Hierarchy (Top to Bottom):
1. Top Reassurance Header:
   - Centered app avatar showing a glowing warm lantern/compass icon with a sapphire blue shield badge.
   - App title: "Saarthi (सारथी)" in bold 32sp Deep Sapphire Blue.
   - Subtitle in 18sp: "Your trusted companion for safe & simple digital navigation."

2. Privacy Guarantee Banner (Prominent Trust Card):
   - Solid pure white card (#FFFFFF) with a 2dp border in emerald green (#047857) and subtle shadow.
   - Icon: Green lock badge.
   - Heading (20sp bold): "100% Private & On-Device".
   - Explanatory bullet points (18sp):
     • "No passwords, PINs, or bank balances are ever uploaded or stored."
     • "Processed entirely inside your phone via local Gemini Nano AI."
     • "Emergency Kill Switch always available."

3. Permission Cards (Two Step Cards with Toggle / Action states):
   - Card A: "Step 1: Allow Saarthi to Read Button Names"
     • Subtitle: "Enables Accessibility Service so Saarthi can locate buttons like 'Send Money' or 'Check Balance'."
     • Visual button: Large rounded toggle button with clear text "Grant Permission" (#D97706 fill, 60dp height, white 20sp bold text).
   - Card B: "Step 2: Allow Saarthi to Highlight the Screen"
     • Subtitle: "Enables 'Draw Over Other Apps' so Saarthi can place a bright glowing box over the button you need."
     • Visual button: Large rounded button "Enable Highlight Box" (#0F294A fill, 60dp height, white 20sp bold text).

4. Bottom Action & Helpline:
   - Primary sticky CTA button: "Start Using Saarthi" (Warm Amber #D97706, full width, 64dp height, 22sp bold white text).
   - Caregiver link at the bottom: "Setting this up for a parent? Tap for Quick Guide."
```

---

## Prompt 2: Main Settings & Control Dashboard (`screen_dashboard`)

```text
Design a clean, high-contrast, zero-clutter Android Settings and Control Dashboard for "Saarthi AI", built for older adults and their family caregivers.

Design Style & Theme:
- Background: Glare-free warm linen off-white (#FBFBF9).
- Primary Elements: Deep Sapphire Blue (#0F294A).
- Accent / Highlights: Warm Amber (#D97706) and Alert Crimson (#B91C1C).
- Typography: High legibility sans-serif, minimum 18sp body, 24sp section headers, high contrast ink text (#0B192C).

Layout Hierarchy (Top to Bottom):
1. Top App Bar & Live Status:
   - Title: "Saarthi Assistant" (28sp bold, Deep Sapphire Blue).
   - Subtitle: "Active & Listening on Standby".
   - Status Badge: Pulsing Emerald Green (#047857) pill with text "✓ Shield Active (Zero Cloud Data)".

2. Emergency Privacy Kill Switch (High Priority Card):
   - Full-width prominent emergency card with 2dp border in Alert Crimson (#B91C1C).
   - Big Red Button: "Turn Off Saarthi Immediately (Kill Switch)" with a power-off icon, 60dp height, bold 20sp white text on #B91C1C.
   - Reassuring subtext: "Instantly stops screen reading and floating overlay."

3. Voice & Language Selector Card:
   - Card Title: "Choose Your Speaking Language" (22sp bold Deep Sapphire).
   - Grid of large language chips (minimum 56dp tall each, 20sp text):
     • "हिन्दी (Hindi)" - Selected state with warm amber border and checkmark
     • "English"
     • "मराठी (Marathi)"
     • "தமிழ் (Tamil)"
     • "తెలుగు (Telugu)"
     • "বাংলা (Bengali)"

4. Voice Speed & Guidance Controls:
   - Card Title: "Speaking Speed" (22sp bold).
   - 3 large segmented buttons: "Slow (धीमी)", "Comfortable (सामान्य)" [Selected], "Fast (तेज़)".
   - Volume boost toggle: "Extra Loud Voice Guidance" with large high-contrast switch.

5. Privacy & Security Live Audit:
   - White card with 3 verified status rows:
     • 🛡️ "RAM-Only Processing: Screen destroyed immediately after guidance."
     • 🔒 "Data Redactor: All bank account & phone numbers masked."
     • 📡 "Cloud Telemetry: 0 KB sent (100% Offline AI Core)."

6. Practice Sandbox Card:
   - Big friendly button: "Practice Asking Saarthi (Safe Test Run)" with microphone icon.
```

---

## Prompt 3: Floating Companion Overlay & Highlight Widget (`screen_overlay_widget`)

```text
Design a multi-state Android Floating Overlay System for "Saarthi AI", showing how the assistive companion appears on top of any 3rd party mobile app (e.g., a complex UPI payment screen).

Design Style & Theme:
- Floating Widget Background: Deep Sapphire Blue (#0F294A) with Warm Amber (#F59E0B) glowing border aura.
- Target Highlight Box: 4dp Warm Amber (#F59E0B) rounded stroke with soft 8dp ambient radial glow (#FEF3C7).
- Voice Modal Background: Pure White (#FFFFFF) elevated card with rounded corners (24dp) and soft drop shadow.
- Typography: Minimum 20sp bold text for voice feedback, 18sp for secondary hints.

Include Three Visual States in the Presentation:

State 1: Collapsed Floating Head (Idle on Screen Margin)
- A 64x64dp circular floating bubble anchored to the right screen edge.
- Deep Sapphire Blue (#0F294A) surface, glowing Warm Amber (#F59E0B) 3dp border ring.
- Crisp white microphone icon (32x32dp) inside with a small gentle pulsing ripple around it.
- Accessible drag indicator handle.

State 2: Active Listening Voice Sheet (User tapped the floating bubble)
- An elevated bottom floating card appearing over the dimmed app screen.
- Header with senior-friendly reassurance: "Saarthi is Listening... बोलिए" (24sp bold Sapphire Blue).
- Audio waveform visualizer rendered in warm amber bars pulsating to simulate speech input.
- Real-time speech transcription text: "Checking your account balance... (खाता बैलेंस ढूंढ रहे हैं)" in 20sp dark slate.
- Large 56dp "Cancel (बंद करें)" button in soft grey with high-contrast text.

State 3: Visual Highlight Guidance Box (AI found the button)
- Background: Simulated banking app screen with dummy account cards.
- The exact target button (e.g., "Check Bank Balance") is encased in a vivid 4dp glowing Warm Amber (#F59E0B) bounding box.
- Directly above the target button, an attached high-contrast tooltip card with arrow pointer:
  • "👉 Tap Here to Check Balance (यहाँ दबाएं)" in 20sp bold Deep Sapphire Blue.
  • Voice caption badge: "Speaking: 'Please press this button to view your balance'".
  • "Done / Dismiss" checkmark button (48x48dp).
```
