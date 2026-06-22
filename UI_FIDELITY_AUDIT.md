# VidyaSetu — UI Fidelity Audit (Compose vs React/Figma)

> **Purpose** — This document maps **every** visual/UX difference between the current
> Compose Multiplatform implementation (`composeApp/.../ui/v2/`) and the React/Figma
> source of truth (`UI screens/src/app/`), screen by screen, with maximum detail.
> The React code is the blueprint; Compose must become a pixel-perfect, interaction-perfect clone.
>
> **Branch:** `backend-by-abuzar`
> **Audit method:** Line-by-line code comparison of React `.tsx` vs Compose `.kt`, cross-referenced
> with the 6 build screenshots the user provided (Welcome, School Onboarding, Parent Portal — each
> Compose-current vs Figma).
> **Status legend:** 🔴 critical (breaks the premium feel) · 🟠 major · 🟡 minor/polish · 💎 premium-craft (same design, elevated execution)
>
> **Revision 2 (deep pass):** adds §0.5 (Teacher + School portals wrongly forced to Night/black —
> a defect as severe as the Onboarding one), expanded per-screen tables for Teacher (§6) and School
> (§7) with exact color/glyph/text-color diffs, the full §13 **Premium Craft** playbook (elevation
> system, motion choreography, optical alignment, pressed/hover states, gradient & glow recipes,
> text rendering), and §14 a component-by-component conformance matrix.

---

## 0. ROOT-CAUSE DEFECTS (global — fix these FIRST, they affect every screen)

These four defects explain ~80% of the "premature / 90% same" gap the user sees. They are
**system-wide** — fixing them lifts every screen simultaneously.

### 0.1 🔴 Brand font missing — `Plus Jakarta Sans` / `DM Mono` are NOT bundled
- **React:** `theme.css` loads `Plus Jakarta Sans` (400–800) for UI and `DM Mono` (400/500) for
  numbers via Google Fonts (`fonts.css`). Body sets `font-feature-settings: 'ss01','ss02'`
  (stylistic alternates) + `-webkit-font-smoothing: antialiased`. Every heading, button, label
  uses this geometric, rounded, premium typeface.
- **Compose:** `VType.kt` → `defaultVTypography()` falls back to `FontFamily.SansSerif` (Roboto on
  Android / SF on iOS) for UI and `FontFamily.Monospace` for data. **No `.ttf` exists in
  `composeApp/src/commonMain/composeResources/font/`** (the `font/` dir doesn't even exist).
- **Impact:** This is the **single biggest** "looks premature" cause. Roboto vs Plus Jakarta Sans
  changes letterforms, weight rendering, x-height, and the whole brand feel on 100% of text.
- **Fix:** Bundle `PlusJakartaSans-{Regular,Medium,SemiBold,Bold,ExtraBold}.ttf` and
  `DMMono-{Regular,Medium}.ttf` into `composeResources/font/`, build a `FontFamily`, and pass it
  into `defaultVTypography(uiFamily = …, dataFamily = …)`. Enable `ss01/ss02` features where the
  Compose text API allows (`FontFeatureSetting`).

### 0.2 🔴 `h1` line-height & weight mismatch + numeric weight rounding
- **React:** `h1` = 32px / **800** / line-height **1.1** (= 35.2px) / -0.02em. `dataLg`/hero numbers
  use weight **500–700** in DM Mono.
- **Compose:** `h1` = 32sp / `ExtraBold` (≈800 ✓) / lineHeight 35sp ✓. **But** `body` weight Normal,
  `caption` Medium(500) — React `p` is weight 400 and `caption`/small text varies (see 0.4).
- **Impact:** Headings are close; the gap is mostly the typeface (0.1). Keep watching weights once
  the real font is in — `FontWeight.ExtraBold` on Plus Jakarta renders heavier than on Roboto.

### 0.3 🔴 `VCard` corner radius & shadow are wrong (every card on every screen)
- **React `VCard`:** `rounded-[16px]` → **16px** radius; shadow = `--shadow-light-1` =
  `0 1px 3px rgba(38,35,77,0.05), 0 1px 2px rgba(38,35,77,0.03)` — an extremely soft, tight,
  barely-there shadow. Padding `p-4` = 16px.
- **Compose `VCard`:** uses `shapeLg` = **14dp** radius (2dp too sharp) and
  `shadow(elevation = 10.dp, ambient/spot = 0x14000000)` — a **much heavier, larger** drop shadow
  than the design's hairline. Padding 16dp ✓.
- **Impact:** Every card looks slightly boxier (14 vs 16) and "floats" too aggressively (10dp
  elevation vs a 1–3px soft shadow). Contributes strongly to the "not premium" feel — Figma cards
  sit flat-and-crisp; Compose cards have a chunky Material drop shadow.
- **Fix:** Add `radiusCard = 16.dp` token; change `VCard` shape to 16dp; replace the 10dp Material
  shadow with a custom soft shadow (low elevation ~2dp + very low alpha, or a layered
  `drawBehind`/`Modifier.shadow` tuned to `0 1px 3px @5%`).

### 0.5 🔴🔴 Teacher AND School portals are forced to Night (black) — should be warm-LIGHT
- **The code contradicts itself.** `NavGraphV2.toneFor()` (lines 116–120) correctly maps:
  - `ADMIN → VPortalTone.Warm`, `TEACHER → VPortalTone.Warm`, unauth/parent → `Light`.
- **But** `SchoolPortalV2.kt:39` and `TeacherPortalV2.kt:41` each open with
  `VTheme(tone = VPortalTone.Night) { … }`, **overriding** the nav graph's correct Warm tone with
  pure deep-black `#050505`.
- **React truth:** `AdminApp` and `TeacherApp` render under `PhoneFrame dark className="dark"` —
  and as established in §2.1, `dark`/`.warm` is a **LIGHT** theme (lavender bg, dark ink, white
  cards). The admin/teacher dashboards in Figma are **warm-light**, not black.
- **Impact:** **Every Teacher screen (Home, Update, Attendance, Marks, Syllabus, Homework, Classes,
  Profile, ClassDetail) and every School screen (Home, People, Records, Comms, Settings, all
  detail screens) renders on a black canvas** instead of the warm-light Figma surface. This is the
  same class of defect as the Onboarding one (§2.1) but affects ~20 screens. The Night palette also
  remaps `teal → #3cd1be`, `navy → near-white`, inks → inverted, so EVERY color is wrong on these
  screens, not just the background.
- **Fix:** Delete the `VTheme(tone = VPortalTone.Night)` wrapper in `SchoolPortalV2` and
  `TeacherPortalV2` (let the nav graph's `Warm` flow through), OR change them to
  `VPortalTone.Warm`. Then verify the per-screen hardcoded `Color(0xFF080808)` text-on-teal and
  `c.ink.copy(alpha=0.06f)` fills read correctly on the light surface.
- **Caveat:** `VPortalTone.Warm` currently aliases to `LightVColors` (`vColorsFor`:
  `Light, Warm → LightVColors`). React's `.warm` scope additionally remaps `--arctic → #006a60`
  (teal-deep) and `--void → #fcf8ff`. If any screen reads `--void`/`--arctic` semantics, build a
  dedicated `WarmVColors` rather than reusing `LightVColors` verbatim.

### 0.6 🟠 Icon set: lucide-specific glyphs replaced with wrong Material icons
- React uses **lucide-react** glyphs with specific semantics. Compose substitutes Material icons
  that don't match, e.g.:
  - Teacher tasks: warning row should be **`AlertCircle`** (⊘), 4th row **`Clock`** — Compose uses
    `Bell` and `Calendar` (`TeacherHomeScreenV2` lines 88, 90). Wrong meaning + wrong shape.
  - School pending-actions should be **`AlertCircle`** — Compose uses `Bell`
    (`SchoolHomeScreenV2` line 228).
  - Onboarding "Import roster" / Students drop should be **`Upload`** — Compose uses `Plus`/`Share`.
- **Fix:** Audit `VIcons` against every lucide import in the React files; add the missing glyphs
  (AlertCircle, Clock, Upload, ClipboardList, Megaphone, ListChecks, GraduationCap, etc.) and use
  the correct one per call site. Lucide is 2px stroke, rounded caps — match stroke weight/caps.

### 0.4 🟠 `Label` / `caption` semantics don't match React per-context
- **React** has TWO distinct small-text styles that Compose collapses:
  - `Label` component: 11px / **700** / **uppercase** / letter-spacing **0.10em** / `--ink-3`.
  - `VInput` label: 12px / **600** / **NOT uppercase** / no tracking / `--ink-2`.
  - Global `label` element (theme.css): 11px / 600 / uppercase / 0.08em / `--ink-2`.
- **Compose:** `label` token = 11sp / SemiBold(600) / 0.08em — used for BOTH the uppercase section
  labels AND, wrongly, for things React renders as plain `caption`/footer text. `VInput` label uses
  `caption` (12sp Medium) instead of a 12/600 non-uppercase style.
- **Impact:** Section labels render with weight 600 not 700 and 0.08em not 0.10em (slightly weak).
  Input labels are Medium not SemiBold. Footers/legal text that should be plain 11px get uppercased
  label styling (see Welcome §1, diff #9).
- **Fix:** Add `labelStrong` (11/700/0.10em/uppercase, ink3) for `Label`, keep `label` (11/600/
  0.08em) for the global element, and add `inputLabel` (12/600/none/ink2) for VInput.

---

## 1. 🔴 Welcome / Splash  —  `auth/WelcomeScreenV2.kt`  ⇄  `Auth.tsx → Splash`

**Screenshots:** #1 (Compose current) vs #2 (Figma). This is the flagship screen and the worst offender.

| # | Element | React / Figma (blueprint) | Compose (current) | Sev |
|---|---------|---------------------------|--------------------|-----|
| 1 | **Hero ambient glow** | `radial-gradient(circle at 50% 30%, rgba(255,255,255,0.25), transparent 55%)` overlay @ opacity 0.30 over the teal panel | **Missing** — flat teal | 🔴 |
| 2 | **Cloud decorations** | Two hand-drawn cloud SVGs: top-left (56×38, opacity .30) + bottom-right (46×32, opacity .25), white 1.5 stroke | **Missing** | 🟠 |
| 3 | **Logo cube** | 160×160 box, radius **28**, bg `rgba(255,255,255,0.16)` + `backdrop-blur(8px)` + **1px `rgba(255,255,255,0.18)` border**, with an animated pulsing **halo** (`box-shadow 0 0 0 12px rgba(255,255,255,0.10)`, opacity 0→0.6→0 loop). Inner bridge SVG is **100×100**. | Box radius 28 ✓ but bg `card.copy(alpha=.16)` (card=white→ok), **no border, no blur, no halo**; uses `VLogo(size=96)` inside `padding(xl=32)` → wrong inner size & the VLogo draws its OWN rounded-rect plate (double plate look) | 🔴 |
| 4 | **Title size/weight** | `fontSize: 30`, `fontWeight: 800`, -0.02em | uses `h1` = **32sp** (2px too big) | 🟡 |
| 5 | **Tagline size** | `fontSize: 15`, color rgba(255,255,255,0.92), maxWidth 280 | uses `body` = **14sp** | 🟡 |
| 6 | **Social-proof strip** | 3 overlapping avatar circles `#A8E6CF / #FFD4A3 / #C8DEFF`, 24px, **-8px overlap** (`-space-x-2`), 2px lavender border; then text `"240+ schools"` **bold + ink** · " 38k parents" in ink-2, 12px | Only the text `"240+ schools · 38k parents"` in `caption/ink2` — **avatars missing**, no bold "240+ schools" segment | 🔴 |
| 7 | **"Get started" button** | `<VButton size=lg tone=teal>` → **soft defaults to TRUE** → soft MINT fill (`#b9e6df` bg, `#005048` text, soft border, inset highlight). Trailing **`ArrowRight` icon AFTER text**. | `soft = false` → **solid dark-green** fill (`#006a60`), **no arrow icon**. This is the most visible single miss (screenshot shows dark-green vs Figma's mint). | 🔴 |
| 8 | **Extra 3rd button** | React Splash has **ONLY 2 buttons** (Get started, I already have an account) | Compose adds a **third** "Register your school" ghost button that does not exist in the blueprint | 🔴 |
| 9 | **Footer / legal line** | `fontSize: 11`, plain (no uppercase), `--ink-3`; "Terms" & "Privacy Policy" are `--teal-deep` / weight 600 inline spans | rendered with `label` style → **UPPERCASE + 0.08em tracking** (wrong), single flat color, no teal links | 🟠 |
| 10 | **Sheet overlap & shadow** | Sheet uses `-mt-8` → pulls **up 32px** over the hero (overlap), radius 32, `boxShadow 0 -10px 32px rgba(0,0,0,0.06)` | Sheet sits flush below hero (no negative overlap), radius 32 ✓, **no top shadow** | 🟠 |
| 11 | **Entrance animations** | Spring logo (stiffness 240/damp 22), staggered fade-ins (delays 1.0→1.85s), bridge path-draw, sheet spring-up | **None** — everything static | 🟠 |
| 12 | **Hero height** | `minHeight: 440`, paddingTop 80 / bottom 90 | fixed `height(420.dp)` — slightly short, padding differs | 🟡 |
| 13 | **Status pill ("Splash")** | The PhoneFrame chrome shows a "● Splash" dark pill + moon toggle top-right (prototype chrome) | N/A in-app (chrome only) — ignore unless replicating the device frame | 🟡 |

---

## 2. 🔴 School Onboarding  —  `auth/SchoolOnboardingScreenV2.kt`  ⇄  `Auth.tsx → SchoolOnboarding`

**Screenshots:** #3 (Compose current, BLACK) vs #4 (Figma, LIGHT/white). The single biggest structural defect in the app.

| # | Element | React / Figma | Compose (current) | Sev |
|---|---------|---------------|--------------------|-----|
| 1 | **THEME — light vs dark** | React renders inside `PhoneFrame dark` — but **`dark` legacy applies the `.warm` scope, which is a LIGHT theme** (lavender `#fcf8ff` bg, dark ink text, white cards). The whole wizard is **LIGHT**. (See `theme.css` `.warm{}` block: `--void:#fcf8ff; --cloud:#1a2422;`) | Wraps everything in `VTheme(tone = VPortalTone.Night)` → **pure deep-black** `#050505` bg. **Completely wrong surface.** Screenshot #3 is black; Figma #4 is white. | 🔴🔴 |
| 2 | **Header "ONBOARDING" label** | `Label dark` = 11/700/upper/0.10em/ink-3 on light | `label` token weight 600/0.08em on **black** | 🔴 (theme) |
| 3 | **Step progress bar** | Filled segment = `--arctic` (teal `#3cb9a9`); empty = `rgba(245,245,243,0.08)` on the warm/light surface → empty reads as faint dark | Filled = `tealDeep`; empty = `cream` (which in Night = `#141416` dark). On light it should be a pale track | 🟠 |
| 4 | **Title** | `<h2>` 22/700, color `--cloud` (which in `.warm` = `#1a2422` dark ink) | `h2` on `c.ink` (Night = near-white) | 🔴 (theme) |
| 5 | **`VInput` fields** | Light: bg `--cream #f5f5f3`, border hairline dark, label 12/600 ink-2, placeholder `--placeholder #bcc9c6`. Focus → white bg + teal-deep border + teal glow | Night: bg `#141416`, near-white text → all inputs are **dark boxes** (screenshot #3) | 🔴 (theme) |
| 6 | **Board / School-type tags (`VTag`)** | active: bg `#dcf2ef`, text `#006a60`, border `rgba(0,106,96,0.18)`, shadow; inactive: bg `--cream`, text ink-2. Radius **`rounded-md` = 6px**, padding `px-3 py-1.5`, font 12/600 | `VTag` active uses theme teal but on dark surface; verify radius 6 & exact `#dcf2ef` fill | 🟠 |
| 7 | **Footer nav** | `borderTop: 1px --border-dark-1`; **Back** is ghost (only shown when step>1); **Continue** is `size=lg tone={step===6?teal:navy}` with **trailing ArrowRight**. Step 6 button is `stateful` (loading→"Setting up"). | Back always present (calls onBack at step 1 — extra behavior), Continue uses `leading` icon (**icon BEFORE text**, React has it AFTER), no top border line, step-6 not `stateful` | 🟠 |
| 8 | **Step indicator count** | progress bar maps `i < step` over **6** titles | repeat(6) ✓ | ✓ |
| 9 | **Step 1 layout** | board/type use `Label dark` headers + wrapped tags. Compose mirrors it (FlowRow) ✓ structurally | OK except theme + label weight | 🟡 |
| 10 | **Step 3 manual-add input** | React uses a **raw `<input>`** styled with `--cream` bg, 10px radius, 13px — a compact inline field, NOT a full VInput (no label) | Compose uses full `VInput` (slightly taller; OK but visually a touch bigger) | 🟡 |
| 11 | **Step 5 teacher matrix** | A true **CSS grid**: `gridTemplateColumns: 110px repeat(N,1fr)`, header row (`--cream` bg, 10px upper labels), bordered `rounded-[10px]`, cells `h-8 m-1 rounded-[6px]`; states: mine=teal-deep/✓white, takenByOther=cream/—, available=`rgba(60,185,169,0.10)` + **1px dashed** border/+; disabled opacity .25. Assignment chips below: `font-mono`, `rgba(60,185,169,0.15)` pill, `SUBJ·class`. | Compose renders **per-subject `FlowRow` of 30dp cells** — NOT the bordered header-grid; **no header row, no dashed available border, no chip summary row**. Structurally simplified. | 🟠 |
| 12 | **Step 5 "Import roster" button** | `tone=sand` with leading **Upload** icon | tone=sand ✓ but leading icon = `Plus` (should be Upload/Share) | 🟡 |
| 13 | **Completion screen hero** | gradient `linear-gradient(180deg, --teal 0%, #2a8f80 100%)` + radial glow overlay; check icon springs in (rotate -30→0); h1 **30px** | flat `c.teal` (no gradient, no glow), `h1` 32sp, no animation | 🟠 |
| 14 | **Completion stat bar** | 4 cells with **right dividers** (`borderRight 1px rgba(38,35,77,0.06)` except last); number `font-mono 22/700 navy`, label `10px/700/upper/0.06em ink-3` | No inter-cell dividers; label uses `label` token (11/0.08em) | 🟡 |
| 15 | **Completion quick-start tiles** | icon chip `#dcf2ef` bg / `#006a60` icon; hover `-translate-y-[1px]`; `--cloud-pure` bg | icon chip `c.teal.copy(alpha=.16)`; no hover lift | 🟡 |
| 16 | **Entrance animations** | All sections fade-up with staggered delays (0.5→0.9s); coverage bar animates width | None | 🟠 |

> **Net for screen #2:** even if every chip/label were perfect, the screen would still look wrong
> because the **theme is inverted (black instead of warm-light)**. Fix #1 first.

---

## 3. 🟠 Login  —  `auth/LoginScreenV2.kt`  ⇄  `Auth.tsx → Login`

| # | Element | React / Figma | Compose (expected fix) | Sev |
|---|---------|---------------|------------------------|-----|
| 1 | **Brand hero** | teal panel, paddingTop 56/bottom 72, minHeight 360; 2 cloud SVGs; logo cube 160×160 radius 28 `rgba(255,255,255,0.16)`+blur (no border here, unlike Splash); inner bridge 100×100 with navy center dot | Verify cube size 160 + blur; clouds present? | 🟠 |
| 2 | **Heading** | `<h2>` 22/700 "Welcome to VidyaSetu. 👋" (emoji via Noto Color Emoji) | check emoji handling + size | 🟡 |
| 3 | **Portal selector tabs** | pill group, bg `--portal-tab-bg #f6f1ff`, 3 segments (parent/admin/teacher), active = white bg + `teal-deep` text + weight 700 + `0 1px 2px` shadow + 8px radius inside 12px container; **capitalize** | Confirm tab visuals + active shadow + radius | 🟠 |
| 4 | **Conditional fields** | parent: Mobile + (after Send OTP) OTP field; admin: Email/SchoolID + Password(+Eye toggle, Forgot link); teacher: Teacher credential + Password | Confirm all three branches + OTP two-step state + Eye toggle + "Forgot Password?" teal-deep link | 🟠 |
| 5 | **Submit button** | `tone=teal` (soft default = mint) + trailing ArrowRight; label switches Send OTP→Verify & Continue / Sign In | Confirm soft mint (not solid) + trailing arrow + dynamic label | 🟠 |
| 6 | **"Not a member? Register Now"** | 14px; "Register Now" = `--warm-orange #9e421a` / weight 600 | Confirm warm-orange link color | 🟡 |
| 7 | **Sheet** | `-mt-8` overlap, radius 32, `0 -8px 30px` shadow, spring entrance | Confirm overlap + shadow | 🟠 |
| 8 | **VInput focus glow** | `0 0 0 4px rgba(60,209,190,0.15)` — note **`#3cd1be`** (teal, not teal-deep) at 15% | Compose uses `c.teal.copy(0.15f)` ✓ (light teal = `#3cb9a9` — close; React glow uses 3cd1be) | 🟡 |

---

## 4. 🟠 Parent Portal + Home  —  `parent/ParentPortalV2.kt` + `ParentHomeScreenV2.kt`  ⇄  `Parent.tsx`

**Screenshots:** #5 (Compose current) vs #6 (Figma). Closest of the three — mostly font + radius + spacing polish.

### 4.1 ChildSwitcher header (`ParentPortalV2.ChildSwitcher` ⇄ `Parent.tsx → ChildSwitcher`)
| # | Element | React | Compose | Sev |
|---|---------|-------|---------|-----|
| 1 | Container | `px-5 pt-5 pb-3`, bg `--cloud-pure` (white), borderBottom `--border-light-1` | bg `c.card` ✓, padding 20/20/12 ✓, uses `VDivider` at bottom instead of borderBottom (extra Spacer+divider) | 🟡 |
| 2 | Child chip | bg `--cloud` (`#f5f5f3`), `px-2 py-1.5` pill; name 13/700; subline **10px** `--text-light-2`; ChevronDown 14 opacity .5 | bg `cream` ✓; name `bodyStrong`(14) — **too big** (should be 13/700); subline uses `label`(11/upper) — **should be 10px plain, not uppercase** | 🟠 |
| 3 | Bell + dot | 9×9 → 36dp circle, bell 16, danger dot 6px top-2 right-2 | ✓ matches | ✓ |
| 4 | Parent avatar (exit) | `VAvatar "Sneha Sharma" size 32` | `MockV2.parentName` size 32 ✓ | ✓ |
| 5 | Sibling dropdown | active row bg `rgba(200,222,255,0.30)` (light blue), check icon `#0a3a76`; inactive `--cloud` | active bg `c.teal.copy(0.14f)` (**teal not blue**), check `tealDeep` | 🟡 |

### 4.2 ParentHome (`ParentHomeScreenV2` ⇄ `Parent.tsx → ParentHome`)
| # | Element | React | Compose | Sev |
|---|---------|-------|---------|-----|
| 1 | Hero card | `VCard padded={false}`, gradient `135deg #f6f1ff→#e8f7f3`, **decorative radial blob** top-right (`-right-6 -top-6 w-32 h-32 radial rgba(60,185,169,0.35)` opacity .50) | gradient ✓ (hardcoded hexes, acceptable since design-specific), **blob missing** | 🟠 |
| 2 | Avatar | size **68** + `ring` (3px white border) | 68 + ring ✓ | ✓ |
| 3 | Status badge | success/danger/warning + icon (Check/X/Clock) inline | uses "● " prefix text instead of the lucide icon inside the badge | 🟡 |
| 4 | Attendance row | Label "Attendance · last 30 days"; **92%** = `font-mono 24/700 navy`; "+4 vs class avg" 11/600 `#155e3a`; VSparkline 120×44 | 92% uses `dataLg` overridden to 24sp/Bold ✓; "+4" uses `label` (upper) — should be **plain 11/600** | 🟡 |
| 5 | Card radius | 16px (VCard) | 14dp (root-cause 0.3) | 🟠 |
| 6 | Schedule rows | period num `font-mono 11 text-light-3`, subject 13/600, teacher 11 text-light-2, time `font-mono 11`, "Now" badge on i==2 | period `dataSm`(13) width 20 (should be 11/centered), teacher `caption`, time `dataSm` ✓, Now badge ✓ | 🟡 |
| 7 | "What covered" | title 600, body 13 `text-light-2`, byline 11 `text-light-3` | body uses `caption`(12) — React is 13; byline uses `label`(upper) — React plain 11 | 🟡 |
| 8 | Reminder card | warm chip `rgba(255,212,163,0.45)` + CalIcon `#7a3f00` | `c.warning.copy(0.45f)` + warningInk ✓ | ✓ |
| 9 | Fees card | "₹ 12,500 due" `font-mono 20` color `#7a3f00`; Pay-now `tone=peach stateful` | `data` 20sp `warmOrange`(#9e421a — close to #7a3f00) ✓; button ✓ | 🟡 |
| 10 | Month attendance bar | flex 21:2 teal/danger, 38px `font-mono 500` figure | weight 21:2 `teal`/`dangerInk` ✓; 91% `dataLg` 38sp ✓ | ✓ |

> **Net for screen #3:** structurally faithful. The visible gap in screenshot #5↔#6 is dominated by
> (a) the **font** (0.1), (b) **card radius 14 vs 16 + heavy shadow** (0.3), and (c) chip/label text
> being slightly oversized/uppercased. No structural rebuild needed — polish + global fixes.

---

## 5. 🟡 Other Auth flows

### 5.1 TeacherFirstLogin — `auth/TeacherFirstLoginScreenV2.kt` ⇄ `Auth.tsx → TeacherFirstLogin`
- React: `PhoneFrame dark` (=**warm/light**); `Label dark` "Welcome, Mr. Vikram"; `h1` "Set a new
  password"; 3 dark VInputs (password); primary button (navy soft default) "Update & continue" +
  trailing arrow; ghost "Need help signing in?" 13px text-dark-3.
- **Verify:** screen is LIGHT (not Night), button is soft navy with trailing arrow, not solid.

### 5.2 ParentLinkChild — `auth/ParentLinkChildScreenV2.kt` ⇄ `Auth.tsx → ParentLinkChild`
- React: `PhoneFrame` (light), 3-step, `Label` "Step n of 3" + 3-segment bar (`--arctic` filled);
  step1 name + language VTags (English/हिन्दी); step2 search + match VCard (GraduationCap chip
  `--arctic`, "Match" badge); step3 roll input + matched-child VCard + "+ Add another child"
  (`#0a3a76`). Button label Continue→"Finish & open dashboard" + trailing arrow.
- **Verify:** Hindi glyph रendering, arctic chips, 3-step bar, trailing arrows.

---

## 6. 🔴 Teacher Portal  —  `teacher/*`  ⇄  `Teacher.tsx`

**First and foremost:** `TeacherPortalV2.kt:41` forces `VPortalTone.Night` → every teacher screen
is **black**. React renders warm-LIGHT (§0.5). Fix that before anything below matters.

### 6.1 TeacherHome (`TeacherHomeScreenV2.kt` ⇄ `Teacher.tsx → TeacherHome`)
| # | Element | React | Compose (current) | Sev |
|---|---------|-------|--------------------|-----|
| 1 | **Theme** | warm-light | Night/black | 🔴 |
| 2 | **TaskCard icon circle** | bg = **soft fill** token: success `#A8E6CF`, warning `#FFD4A3`, arctic `#3cb9a9`, neutral `rgba(245,245,243,0.15)`; icon `#080808` | uses **ink** variants: `successInk #1f7a4d`, `warningInk`, `teal`, `ink3` → circles are **dark/muddy**, not the pastel chips Figma shows | 🔴 |
| 3 | **TaskCard icons** | Check / **AlertCircle** / ListChecks / **Clock** | Check / **Bell** / Check / **Calendar** — 3 of 4 wrong glyphs | 🟠 |
| 4 | **TaskCard CTA** | 12px arctic (`--arctic`) weight 600, right-aligned text button | `caption`/`c.teal` SemiBold ✓ (but teal in Night = `#3cd1be`) | 🟡 |
| 5 | **Period chips (active)** | active (i==2) bg `--arctic`, text **`--void`** → in `.warm` `--void` = `#fcf8ff` (**near-white**!); inactive bg `rgba(245,245,243,0.06)`, text `--cloud` | active text **hardcoded `#080808` (black)** on the teal chip — should be near-white per `.warm` | 🟠 |
| 6 | **Period chip label** | `Label dark={i!==2}` — active uses non-dark label | `label` token; check active label color = void/near-white | 🟡 |
| 7 | **Period chip min-width** | `min-w-[150px]`, p-3 (12), radius 12 | `widthIn(min=150)` ✓, padding 12 ✓ | ✓ |
| 8 | **Recent activity rows** | first row no top border; `borderTop --border-dark-1` between; what 13px, when 11px `text-dark-3` | divider between rows ✓; "when" uses `label` token (tracking cleared) ✓ | 🟡 |
| 9 | **Section spacing** | `space-y-5` (20px) between sections, header `pt-6` | `spacedBy(20)` ✓, top 24 (React pt-6=24) ✓ | ✓ |
| 10 | **Card radius/shadow** | 16px + hairline | 14dp + 10dp shadow (0.3) | 🟠 |

### 6.2 Update hub — AttendanceFlow / MarksFlow / SyllabusFlow / HomeworkFlow
| # | Element | React | Compose | Sev |
|---|---------|-------|---------|-----|
| 1 | **VTopTabs** | Attendance/Marks/Syllabus/Homework; active teal-deep + 2.5px underline | verify underline indicator + active color on light | 🟠 |
| 2 | **Attendance P/A/L pills** | 3 round pills per student; selected → tone fill (success/danger/warning) + `--void` text; unselected `rgba(245,245,243,0.06)` + text-dark-3; shows only **first letter** (P/A/L) | verify pill group, single-letter, selected fill = soft tone (not ink) | 🟠 |
| 3 | **Attendance sticky footer** | `sticky bottom-2` VCard: "28 marked • 4 remaining" + VProgressBar + `tone=lavender stateful` Submit→"Submitted" | verify sticky positioning + lavender stateful button + progress | 🟠 |
| 4 | **Marks inline input** | `<input w-20 rounded-md font-mono text-right>` `rgba(245,245,243,0.06)` bg, border-dark-2; live "Class avg 68" footer row | verify compact mono right-aligned field + live-avg row | 🟠 |
| 5 | **Marks/Syllabus/Homework save** | full lg `tone=lavender stateful` (Saved/Logged) | verify lavender + stateful labels | 🟡 |
| 6 | **Syllabus preview box** | `rgba(245,245,243,0.06)` bg, 10px radius, 13px, **bold** chapter inline | **FIXED `b4e16f5`** (body/ink + bold span) ✓ — but verify the wrapper box bg/radius (10px, faint fill) | 🟡 |
| 7 | **Homework "Assign new"** | full lg lavender + **Plus** leading | verify Plus icon + lavender | 🟡 |

### 6.3 MyClasses / ClassDetail / Profile
| # | Element | React | Compose | Sev |
|---|---------|-------|---------|-----|
| 1 | **MyClasses grid** | 2-col cards: class "10-A" 18/700, "Mathematics" 11, then Students count `font-mono 18` + "92% Today" arctic | verify 2-col, mono numbers, arctic % | 🟠 |
| 2 | **ClassDetail header** | `VBackHeader` "Class 10 - A" + Edit3 action | verify back header + edit icon | 🟡 |
| 3 | **ClassDetail stats** | 3-col tiles `rgba(245,245,243,0.06)` 10px radius, mono 18 + 10px label | verify 3 tiles | 🟡 |
| 4 | **ClassDetail roster** | per-student row: avatar32 + name13/600 + "Roll N" mono11 + attendance% mono13; top borders | verify roster rows | 🟡 |
| 5 | **Profile** | centered avatar **88**, name h2, username mono12, subject VBadges; then 4 settings VCards w/ ChevronRight; ghost "Log out" | verify avatar 88, centered, badge row, settings list, ghost logout | 🟠 |

---

## 7. 🔴 School (Admin) Portal  —  `school/*`  ⇄  `Admin.tsx`

**First:** `SchoolPortalV2.kt:39` forces `VPortalTone.Night` → all School screens **black**. React is
warm-LIGHT (§0.5). Fix first.

### 7.1 AdminHome (`SchoolHomeScreenV2.kt` ⇄ `Admin.tsx → AdminHome`)
| # | Element | React | Compose (current) | Sev |
|---|---------|-------|--------------------|-----|
| 1 | **Theme** | warm-light | Night/black | 🔴 |
| 2 | **School chip icon** | `--arctic` circle + GraduationCap `#080808` | `c.teal` circle (Night `#3cd1be`) + `#080808` icon | 🟠 |
| 3 | **GlanceCard CTA** | plain text link (e.g. "View details") — **NO chevron** | adds a **ChevronRight** to every CTA — extra element not in design | 🟠 |
| 4 | **GlanceCard chips** | small rounded chips `rgba(245,245,243,0.06)` | `c.ink.copy(0.06f)` ✓ | 🟡 |
| 5 | **GlanceCard ring** | `VProgressRing` 56/stroke-ish | `VProgressRing(56, stroke 6)` ✓ | ✓ |
| 6 | **Attendance grid colors** | ≥80 → bg `rgba(200,222,255,0.30)` + text **`#0a3a76`** (deep blue); 60–79 → peach `rgba(255,212,163,0.45)`/`#7a3f00`; <60 → rose `rgba(255,173,168,0.45)`/`#7a1c18` | ≥80 text = **`#7FB0FF` (light blue)** — should be **`#0a3a76`** (deep); others use warning/dangerInk (close) | 🟠 |
| 7 | **Grid number** | `font-mono 18/500` | `dataLg` 18sp ✓ | 🟡 |
| 8 | **Syllabus coverage** | rows: class 13 + `font-mono 12` %; `VProgressBar tone=warning if <70` | matches ✓; bar tone ✓ | ✓ |
| 9 | **Subject performance** | header "78%" `font-mono 18/600` + VSparkline 84×28; `VBars` (last bar teal-deep + value label, others `rgba(60,185,169,0.45)`); legend Week/Today | structurally ✓ — verify VBars last-bar highlight + value label | 🟡 |
| 10 | **Teacher activity** | avatar32 + "**who** what" (who bold inline) + when 11 text-dark-3 | ✓ (Row with bold who) | ✓ |
| 11 | **PEWS preview** | `VComingSoon` **with `preview={<PEWSPreview/>}`** → full risk-band grid + at-risk rows + dashed suggestion box | Compose `VComingSoon` called **without preview** → the entire rich PEWS mockup is **MISSING** | 🟠 |
| 12 | **Pending actions icon** | **AlertCircle** `--warning-ink` | **Bell** `warningInk` — wrong glyph | 🟠 |
| 13 | **Pending action button** | `size=sm variant=secondary` | ✓ | ✓ |
| 14 | **Bell unread dot** | `--danger #FFADA8` (soft) | `c.dangerInk #b3261e` (ink) — should be soft danger | 🟡 |

### 7.2 People / StudentDetail / TeacherDetail (`SchoolPeopleScreenV2.kt`)
- React: search bar + filter, segmented student/teacher lists, rows with avatar + name + meta +
  status dot/badge → drill into StudentDetail (profile hero, attendance ring, marks history,
  fee status, AttendanceHeat) / TeacherDetail (subjects, classes, coverage).
- **Verify:** list row anatomy, status dots, the **AttendanceHeat** calendar-heatmap component
  (color-scaled cells), detail-screen heros. Flag any missing sub-views.

### 7.3 Records (`SchoolRecordsScreenV2.kt`)
- React: `VTopTabs` Attendance / Marks / Syllabus / Fee / Docs. Attendance shows `AttendanceHeat`
  grid; Fee shows breakdown + history; Docs list.
- **Verify:** all 5 tabs present, AttendanceHeat color scale, fee VBars/breakdown.

### 7.4 Comms / AnnouncementDetail (`SchoolCommsScreenV2.kt`)
- React: announcement composer (audience tags, message field, Send), inbox list, AnnouncementDetail
  (title, body, audience, read receipts).
- **Verify:** composer + list + detail; "Comms" tab has badge **2**.

### 7.5 Settings (`SchoolSettingsScreenV2.kt`)
- React: settings rows/sections (school profile, academic year, permissions, integrations), toggles.
- **Verify:** grouped rows, toggle styling, ChevronRight affordances.

**PEWS / mockups (`mockups.tsx`) — exact specs for §7.1#11:**
- **PEWSPreview:** risk-band card (`--cream` bg, "RISK BAND • TODAY" 11/700/0.08em + "180 students
  scored" 10); 3-tile grid Low/Watch/High with **exact** bg/fg: `#A8E6CF/#155e3a`,
  `#FFD4A3/#7a3f00`, `#FFADA8/#7a1c18`, number `font-mono 18/600`; "HIGHEST PRIORITY" rows with
  score chip color by threshold (>70 rose, >55 peach, else `#FFE7B0`); dashed "SUGGESTED
  INTERVENTION" box `rgba(60,185,169,0.10)` + `1px dashed rgba(0,106,96,0.3)` + Sparkles icon.
- **Compose must implement these previews** (currently `VComingSoon` has no `preview` slot wired).

---

## 8. 🟡 Discovery  —  `discovery/*`  ⇄  `Discovery.tsx`

React `DiscoveryApp` is **light** (`PhoneFrame`, no dark).

| Screen | Compose | Checks |
|--------|---------|--------|
| **DiscoveryList** | `DiscoveryScreenV2.kt` | school cards, search, filters, SRI badges |
| **SchoolProfile** | (in DiscoveryScreenV2) | profile hero, SRI ring, sections |
| **SchoolCompare** | (in DiscoveryScreenV2) | side-by-side compare |
| **AcademicCalendar** | `AcademicCalendarScreenV2.kt` | month grid, event dots/legend |

**Chart checks (`charts.tsx`):**
- **VSparkline:** has a **gradient area fill** (`linearGradient` stop 0.28→0) + animated path-draw +
  end dot. **Compose `VSparkline` must replicate the gradient fill + end dot** (verify it isn't a
  plain stroke). 
- **VDonut:** animated stroke segments, center label slot.
- **VBars:** last bar `teal-deep` + value label, others `rgba(60,185,169,0.45)`, animated height.
- **VLegendDot / VProgressRing:** exact sizes/colors.

---

## 9. 🟠 Notifications  —  `notifications/NotificationsScreenV2.kt`  ⇄  `Notifications.tsx`

| # | Element | React | Compose | Sev |
|---|---------|-------|---------|-----|
| 1 | Theme | `PhoneFrame dark={dark}` — passed `dark` from caller; parent path is **light** | verify not forced Night | 🟠 |
| 2 | Hero | `rounded-[18px]`, gradient `135deg --navy→#3b3870`, radial blob top-right `rgba(60,185,169,0.45)`; bell chip `rgba(255,255,255,0.14)`+blur; "Inbox" upper 12/0.05em; count `font-mono 28/600` + "unread" 14 | verify gradient + blob + mono count | 🟠 |
| 3 | Filter pills | all/unread; active = `--navy` bg + white; inactive `--cream` + ink-2; 12/700; "unread · N" | verify count suffix + navy active | 🟡 |
| 4 | List rows | white card 14px radius, unread → `0 6px 14px` shadow + 8px teal-deep dot top-right; read → soft `0 2px 6px`; category icon chip (toneFor: attendance=peach, fees=rose, academic=teal, default=mint) 10px radius; VBadge + time 11 ink-3; title 14/600; body 12 ink-2; ChevronRight | verify per-category icon+tone map, unread dot, two shadow levels, staggered fade-in (delay i*0.04) | 🟠 |
| 5 | Empty state | check chip + "You're all caught up" | verify | 🟡 |
| 6 | Settings link | `--cream` bg pill, X icon + "Notification preferences" | verify | 🟡 |

---

## 10. Component-level token deltas (fix once, propagates everywhere)

| Token / component | React value | Compose current | Action |
|-------------------|-------------|------------------|--------|
| UI font | Plus Jakarta Sans (ss01/ss02) | SansSerif fallback | **Bundle font** (0.1) |
| Data font | DM Mono (tnum) | Monospace fallback | **Bundle font** (0.1) |
| `VCard` radius | 16px | 14dp (`shapeLg`) | add `radiusCard=16` |
| `VCard` shadow | `0 1px 3px @5% + 0 1px 2px @3%` | `elevation 10dp @ 0x14` | soften to hairline |
| `VInput` radius | 12px (`rounded-[12px]`) | 10dp (`shapeMd`) | use 12dp for inputs |
| `VInput` label | 12 / 600 / none / ink-2 | `caption` 12/Medium(500) | add `inputLabel` style |
| `VInput` focus glow | `rgba(60,209,190,0.15)` (#3cd1be) | `c.teal(#3cb9a9) @15%` | match #3cd1be |
| `Label` (section) | 11 / **700** / **0.10em** / upper / ink-3 | `label` 11/600/0.08em | add `labelStrong` |
| `VTag` radius | `rounded-md` = 6px | verify (`shapeSm`=6 ✓) | confirm |
| `VTag` active | bg `#dcf2ef`, fg `#006a60`, border `rgba(0,106,96,.18)`, shadow | verify exact | confirm |
| `VButton` soft default | **`soft = true`** (mint look) | call sites pass `soft=false` | **default soft=true; only override when blueprint uses filled** |
| `VButton` icon position | icon AFTER text (`children <Arrow/>`) | `leading` slot = BEFORE text | add `trailing` slot; move arrows to trailing |
| `VButton` sizes | sm `px-3.5 py-2` r10; md `px-4 py-2.5` r12; lg `px-6 py-3.5` r12 | sm 14/8 r10 ✓; md 16/10 ✓; lg 24/14 ✓ | ✓ |
| `VBottomNav` | white bg, `0 -4px 20px @4%` top shadow, active teal-deep, badge `#c14a44` mono | verify shadow + badge color | confirm |
| `VTopTabs` | active teal-deep + 2.5px underline | verify underline indicator | confirm |
| Hero "shimmer" sweep | filled primary buttons have a hover sheen sweep | none | optional (web/desktop hover only) |

---

## 11. Cross-platform (Android + iOS) checks (per the prime directive)

- **Status bar / safe areas:** Welcome/Login teal hero must extend under the status bar with content
  inset (currently fixed `height(420)` ignores top inset). Use `WindowInsets.statusBars`.
- **Back gesture:** every `onBack` must be wired to predictive back (Android) and the iOS edge-swipe;
  SchoolOnboarding step>1 back should go to previous step, step 1 back exits.
- **Keyboard:** VInput-heavy screens (Login, Onboarding) need `imePadding()` + scroll-to-focused.
- **Font scaling:** with the real font bundled, verify `sp` scales and `ss01/ss02` features apply on
  both platforms.

---

## 12. Priority fix order (to recover the "premium" feel fastest)

1. **0.1** Bundle Plus Jakarta Sans + DM Mono → wire into `VType`. *(biggest single win)*
2. **§2.1** Fix SchoolOnboarding theme: Night → **warm/light** (Light palette). *(fixes screenshot #3 entirely)*
3. **0.3** `VCard` radius 14→16 + soften shadow. *(every card, every screen)*
4. **§1** Rebuild Welcome: soft-mint Get-started + trailing arrow, remove 3rd button, add avatars +
   "240+ schools" bold, glow + clouds + halo logo, fix footer style, sheet overlap.
5. **0.4** Add `labelStrong` / `inputLabel`; replace mis-used `label` styling on footers/captions.
6. **VButton** default `soft=true` + add `trailing` icon slot; audit all call sites.
7. Polish per-screen items in §3–§9.
8. Animations (§1.11, §2.16) + cross-platform insets (§11).

**Premium-craft layer (do alongside, per §13 — the "100% feel"):**
9. **§13.1** Create `VElevation {card,raised,modal}` from the 3 `--shadow-light-*` tokens; replace flat
   10dp on every card. *(navy-tinted 2-layer shadows = instant "premium")*
10. **§13.2** Create `VMotion` spring tokens + `fadeUp(delay)`; wire the Splash/Login/Onboarding entrance
    ladders. **§13.3/§13.5** Add `Modifier.pressScale()` everywhere + VButton idle→loading→success.
11. **§13.4** Port the full 8-tone `tonePalette` (filled + soft + inset bevel) into VButton.
12. **§13.8/§13.9** Font features (ss01/ss02/tnum) + negative tracking + `hairline = navy@6%` borders.
13. **§13.6/§13.7** Shimmer sweep (web/desktop) + hero gradients/glow/clouds/halo.

---

## 13. 💎 PREMIUM-CRAFT PLAYBOOK (same design pattern, elevated execution)

> These are NOT "new" designs — every value below is lifted verbatim from the React blueprint
> (`primitives.tsx`, `theme.css`, `Auth.tsx`). They are the details that separate the current
> "premature 90%" look from the "premium 100%" feel. Implement them as **tokens**, never hardcode.

### 13.1 💎 Elevation system — 3-tier shadow tokens (currently collapsed to one flat 10dp)
React defines **three** named elevation tokens in `theme.css`; Compose uses a single hard `elevation 10dp`.
| Token | Light value (`theme.css:41-43`) | Use |
|-------|----------------------------------|-----|
| `--shadow-light-1` | `0 1px 3px rgba(38,35,77,.05), 0 1px 2px rgba(38,35,77,.03)` | resting cards (`VCard`) |
| `--shadow-light-2` | `0 8px 24px rgba(38,35,77,.08)` | raised sheets, popovers, active rows |
| `--shadow-light-3` | `0 16px 40px rgba(38,35,77,.14)` | modals, bottom sheets, dialogs |
- **Craft point:** real shadows are **navy-tinted (`#26234d`), never pure black**, and use a
  **two-layer** stack (tight contact + soft ambient). Compose's grey/black single-layer elevation reads
  cheap. Add `VElevation { card, raised, modal }` → map to `Modifier.shadow(spread, color=navy@α)` via a
  custom `drawBehind` (Compose `shadow()` can't tint, so draw two blurred rounded rects).
- **iOS parity:** Compose `Modifier.shadow` renders on iOS via Skia — the tinted two-layer draw must be
  the **same** custom modifier so both platforms match (no `elevation` dp which behaves differently).

### 13.2 💎 Motion choreography — staggered entrance ladders (currently: nothing)
The Splash/Onboarding/Login screens orchestrate a **timed reveal ladder** (`Auth.tsx`). Compose renders
everything at once. Exact constants to port into an `enterStagger(index)` helper:
| Element | initial → animate | transition (verbatim) |
|---------|-------------------|------------------------|
| Logo halo (Splash) | `scale .82, opacity 0, y 10` → `1,1,0` | `spring(stiffness 240, damping 22)` |
| Halo pulse ring | `opacity 0` → `[0,.6,0]` | `2.4s, repeat ∞, easeInOut, delay .6` |
| Logo path-draw | `pathLength 0→1` | `0.9s, delay .25` then `0.5s, delay .85` |
| Wordmark | `opacity 0, y 12` → `1,0` | `delay 1.0, 0.5s` |
| Bottom sheet | `y 80, opacity 0` → `0,1` | `spring(stiffness 220, damping 28, delay 1.45)` |
| Sheet children | `opacity 0, y 8/12` → `1,0` | `delay 1.6 / 1.7 / 1.85` ladder, 0.4-0.45s |
| Login card | `y 40, opacity 0` → `0,1` | `spring(stiffness 260, damping 30, delay 0.1)` |
| Onboarding success tick | `scale 0, rotate -30` → `1,0` | `spring(stiffness 300, delay 0.4)` |
| Step content rows | `opacity 0, y 10/12` → `1,0` | `delay 0.5/0.65/0.75/0.9` |
| Coverage bar fill | `width 0→%` | `0.5s easeOut` |
- **Compose mapping:** `AnimatedVisibility` + `animateFloatAsState` won't reproduce springs well; use
  `Animatable` with `spring(dampingRatio, stiffness)` (convert: stiffness 240/damping 22 ≈
  `Spring.StiffnessMediumLow` w/ `DampingRatioLowBouncy`; but **port the literal numbers** via
  `spring(stiffness = 240f, dampingRatio = damping/(2*sqrt(stiffness)))`). Drive a single
  `LaunchedEffect` clock so the delay ladder is exact.
- **Define `VMotion` tokens:** `springSoft = spring(stiffness 240, damping 22)`,
  `springSheet = spring(stiffness 220, damping 28)`, `springSnappy = spring(stiffness 300)`,
  `fadeUp(delayMs)` = `fadeIn()+slideInVertically`. Nothing hardcoded per-screen.

### 13.3 💎 Pressed / hover micro-interactions (currently: static)
`primitives.tsx` + `Auth.tsx` press feedback — all missing in Compose:
| Component | React feedback | Compose target |
|-----------|----------------|----------------|
| `VButton` | `hover:opacity-95 active:scale-[0.98]` | `clickable` + `animateFloatAsState` scale→.98 on press; iOS+Android both via `interactionSource` |
| Splash CTA | `whileHover y:-1`, `whileTap scale .98, y 0` + `box-shadow 200ms` lift | press → scale .98; resting→raised shadow swap (§13.1) |
| `VTag` | `hover:scale-[1.02] active:scale-[0.98]` | press scale .98 |
| `VTopTabs` | `transition-colors` underline slide | animate the 2.5px indicator x/width with `animateDpAsState` |
- **Craft point:** every tappable surface must visibly *give* on press (scale .98 + shadow drop).
  Build one `Modifier.pressScale()` and apply everywhere → consistent, premium tactility on both platforms.

### 13.4 💎 VButton tone palette + dual-shadow recipe (currently: flat fills)
`primitives.tsx:69-118` defines **8 tones** each with a precise filled + soft shadow pair. Compose only
has navy/teal flats. Port the full `tonePalette` as a Compose map:
- **Filled:** `boxShadow: 0 6px 14px -4px {shadow}, 0 2px 4px -2px {shadow}` (tone-tinted, 2-layer).
- **Soft:** `bg {soft}, fg {softFg}, border 1px {softBorder}, boxShadow 0 8px 20px -8px {softShadow},
  inset 0 1px 0 rgba(255,255,255,.55)` — note the **inner top highlight** (glassy bevel) that gives the
  soft buttons their premium "pill" look. Compose must draw the inset highlight (top `1px` white@55%).
- **Tones (bg / fg / soft / softFg):** navy `#26234d`·`#d8d2f1`/`#26234d`; teal `--teal-deep`·`#b9e6df`/`#005048`;
  sky `#3b78e7`·`#cddcff`/`#1a3f99`; peach `#e08a3c`·`#fad0a8`/`#7a3f0a`; lavender `#7a6cf0`·`#d6cdff`/`#3527a8`;
  sand `#a88b5c`·`#e8d8b6`/`#5a4626`; rose `#c14a6a`·`#f6cad6`/`#6e1730`; mint `#2f9b7a`·`#bce5d2`/`#0e4d36`.
- **Destructive:** `bg #b3261e, fg #fff, shadow 0 6px 14px -4px rgba(179,38,30,.30)`.

### 13.5 💎 VButton multi-state machine (idle → loading → success) (currently: single state)
`primitives.tsx:157-172` — the button **morphs**: idle label → spinner (rotate 360 ∞, 0.9s linear) →
success check (`spring stiffness 400, scale .7→1, 0.22s`), each cross-fading
(`opacity/​y ±6, 0.18s`). This is the single biggest "premium" tell on form submits (Login, Onboarding,
Marks save). Implement as a `VButtonState { Idle, Loading, Success }` with `AnimatedContent` +
`Crossfade`; spinner via `rememberInfiniteTransition`.

### 13.6 💎 Shimmer sweep on filled primaries (currently: none)
`primitives.tsx:149-151` + `Auth.tsx:134-136`: filled buttons have a diagonal light sweep
`linear-gradient(90deg, transparent, rgba(255,255,255,0.32), transparent)` translating `-120% → 220%`
over `900ms easeInOut` on hover. **Web/Desktop only** (hover); on touch, trigger once on press-release.
Draw via `Brush.linearGradient` animated offset masked to the button shape.

### 13.7 💎 Gradient & glow recipes (hero surfaces) (currently: flat teal)
- **Notifications hero:** `linear-gradient(135deg, --navy → #3b3870)` + radial blob top-right
  `radial-gradient(circle, rgba(60,185,169,0.45), transparent)` + bell chip `rgba(255,255,255,.14)` w/
  `backdrop-blur`. Compose: `Brush.linearGradient` + a second `Brush.radialGradient` overlay; blur chip via
  `Modifier.blur` behind a translucent fill.
- **Splash/Login hero glow:** `radial-gradient(circle at 50% 30%, rgba(255,255,255,0.25), transparent 55%)`
  at `opacity .30` over the teal field — currently a flat teal block in Compose.
- **Logo halo:** `boxShadow 0 0 0 12px rgba(255,255,255,0.10)` pulsing ring (§13.2).
- **Decorative clouds:** two faint SVG cloud glyphs (`opacity .25/.30`) top-left & bottom-right of the hero —
  port as vector `Canvas` paths; they're part of the brand warmth and are missing.

### 13.8 💎 Text rendering craft (the "premature" smell beyond the font swap)
Beyond bundling Plus Jakarta Sans / DM Mono (§0.1), the **OpenType + metric details**:
- **`ss01`/`ss02` stylistic sets** are enabled globally on the UI font — gives the single-story `a`/`g`.
  Compose: `FontFeatureSetting("ss01","ss02")` on the `FontFamily` (verify Skia honors on iOS).
- **`tnum` (tabular figures)** on all DM Mono data so numbers don't jitter in animated counters/tables.
  Compose: `fontFeatureSettings = "tnum"` on every numeric/mono `TextStyle`.
- **Negative tracking on display** (`h1 letter-spacing -0.02em`, logo `-0.02em`) — present in CSS, dropped
  in Compose. Add to `display`/`h1` token (`letterSpacing = (-0.02).em`).
- **Optical line-height:** React `h1` is `line-height 1.05`; large headings in Compose inherit looser
  defaults → feels airy/unfinished. Pin `lineHeight` per display token.

### 13.9 💎 Optical alignment & spacing rhythm
- **Icon-in-circle optical centering:** lucide glyphs are drawn on a 24-grid; when placed in a 40/44px
  circle they need the **container** centered, not the glyph baseline. Verify `Alignment.Center` + equal
  padding; several Compose chips look 1px high.
- **8pt rhythm with half-steps:** React uses `py-3.5` (14px), `py-2.5` (10px), `gap-1.5` (6px) — i.e.
  **half-steps** matter. Ensure `VDimens` exposes 6/10/14 (not just 4/8/12/16) so buttons/inputs match
  exactly (`VButton` md = `px-4 py-2.5` = 16/10).
- **Hairline borders:** every card/divider is `1px solid rgba(38,35,77,0.06)` (navy@6%), not grey. Define
  `VColors.hairline = navy@6%` and use it for all 1px borders/dividers (bottom nav top border, tab row,
  top app bar) — currently grey lines read cheap.

### 13.10 💎 Component finish details (small, high-impact)
- **`VBottomNav` top shadow:** `0 -4px 20px rgba(38,35,77,0.04)` (upward, navy-tinted) — gives the nav a
  floating feel. Compose likely has no top shadow. Draw an upward soft shadow.
- **`VInput` focus glow:** `0 0 0 4px rgba(60,209,190,0.15)` (#3cd1be, brighter than `--teal`) + icon color
  animates ink-3 → teal-deep over `200ms`. Animate both.
- **`VTag` active:** `border 1px rgba(0,106,96,0.18)` + `boxShadow 0 2px 6px -2px rgba(0,106,96,0.18)`.
- **`VProgress` / bars:** fills animate (`width 0→% , 0.5s easeOut`) — never appear pre-filled.
- **`VComingSoon`** ● PREVIEW pill: `font-mono 11/700, teal-deep, 0.06em` + the **preview slot** must
  render the real mockup (SRI/PEWS/AIReportCard), not be empty.

---

## 14. 🔍 COMPONENT CONFORMANCE MATRIX (one row per V* primitive)

Legend: ✅ faithful · ⚠️ partial / needs token · 🔴 wrong / missing. "Action" = exact fix.

| Component | Radius | Shadow / elevation | Colors / tones | Motion | States | Verdict | Action |
|-----------|--------|--------------------|----------------|--------|--------|---------|--------|
| **VCard** | 🔴 14→**16** | 🔴 flat 10dp → **2-layer navy-tint `--shadow-light-1`** | ✅ white + hairline (once `hairline` token added) | n/a | n/a | ⚠️ | radius 16; custom tinted 2-layer shadow (§13.1) |
| **VButton** (filled) | ✅ sm10/md12/lg12 | 🔴 flat → **2-layer tone-tinted** (§13.4) | 🔴 only navy/teal → **8-tone palette** | 🔴 none → shimmer (§13.6) + press-scale | 🔴 single → **idle/loading/success** (§13.5) | 🔴 | port `tonePalette`, dual shadow, state machine, shimmer |
| **VButton** (soft) | ✅ | 🔴 → `0 8px 20px -8px {softShadow}` **+ inset top highlight** | 🔴 call sites pass `soft=false` → **default soft=true** | 🔴 press-scale | as above | 🔴 | default soft=true; inset bevel; per-tone soft colors |
| **VInput** | 🔴 10→**12** | n/a | ⚠️ focus glow `c.teal` → **#3cd1be@15%** | 🔴 icon color 200ms tween + focus ring | ⚠️ focus only | ⚠️ | radius 12; glow #3cd1be; animate icon color; `inputLabel` 12/600 |
| **VBadge** | ✅ pill | n/a | ✅ tones faithful | n/a | n/a | ✅ | confirm 11/600/0.04em |
| **VTag** | ✅ 6 | 🔴 active shadow missing | 🔴 active bg `teal@14%` → **`#dcf2ef`**, border `rgba(0,106,96,.18)` | 🔴 hover/press scale | active/idle | ⚠️ | exact active bg/border/shadow; press-scale |
| **VLabel** | n/a | n/a | n/a — weight/tracking | n/a | n/a | 🔴 | 11/**700**/**0.10em** (add `labelStrong`); current 600/0.08em |
| **VAvatar** | ✅ | n/a | ✅ palette + initials #080808 | n/a | n/a | ✅ | — |
| **VDonut** | n/a | n/a | ✅ segments + center slot | ✅ animated reveal | n/a | ✅ | — |
| **VSparkline** | n/a | n/a | ⚠️ verify **gradient area fill** stop 0.28→0 + end dot | ✅ path-draw | n/a | ⚠️ | confirm gradient fill + end dot, not plain stroke |
| **VBars** | n/a | n/a | ⚠️ last bar teal-deep, others `teal@45%` | ✅ height grow | n/a | ⚠️ | confirm last-bar accent + value label |
| **VProgressRing** | n/a | n/a | ✅ | ✅ | n/a | ✅ | confirm sizes |
| **VBottomNav** | pill | 🔴 **top shadow `0 -4px 20px navy@4%`** missing | ⚠️ badge `dangerInk #b3261e` → **#c14a44** | 🔴 active color tween | active/idle | ⚠️ | top shadow; badge #c14a44; hairline top border |
| **VTopTabs** | n/a | n/a | ✅ active teal-deep | 🔴 **2.5px underline should slide** | active/idle | ⚠️ | animate indicator x/width |
| **VTopBar** | n/a | n/a | ✅ white + hairline | n/a | n/a | ✅ | confirm `navy@6%` border |
| **VComingSoon** | n/a | n/a | ⚠️ ● PREVIEW pill mono 11/700 teal-deep | n/a | 🔴 **preview slot empty** | 🔴 | wire SRI/PEWS/AIReportCard preview mockups |
| **VTheme / tones** | n/a | n/a | 🔴 **Teacher+School forced Night**; Onboarding Night | n/a | n/a | 🔴 | Warm (light) for admin/teacher; remove Night wrappers (§0.5, §2.1) |
| **VType** | n/a | n/a | 🔴 **no Plus Jakarta Sans / DM Mono**; no ss01/ss02/tnum; no neg tracking | n/a | n/a | 🔴 | bundle fonts; add features + tracking + line-heights (§0.1, §13.8) |
| **VElevation** | — | 🔴 **token set does not exist** | — | — | — | 🔴 | create `{card, raised, modal}` from `--shadow-light-1/2/3` (§13.1) |
| **VMotion** | — | — | — | 🔴 **token set does not exist** | — | 🔴 | create `springSoft/Sheet/Snappy`, `fadeUp(delay)` (§13.2) |
| **pressScale modifier** | — | — | — | 🔴 **does not exist** | — | 🔴 | one `Modifier.pressScale()` for all tappables (§13.3) |

---

*Generated by line-by-line comparison of `UI screens/src/app/**` (React blueprint) against
`composeApp/src/commonMain/kotlin/com.littlebridge.enrollplus/ui/v2/**` (Compose implementation),
cross-referenced with the 6 build screenshots. Every value above is quoted from source.*

---

## 14.1 ✅ RE-WALK CONFORMANCE MATRIX (post-implementation — 2026-06-07)

The §14 matrix above captured the **starting** state. This is the re-walk after all
§0–§13 work landed on `backend-by-abuzar`. Each verdict was re-derived by reading the
current Compose source (file:line cited), not by trusting commit messages.

Legend: ✅ resolved · 🟡 minor gap remaining (documented) · 🔴 still open.

| Component | Then | Now | Evidence (current source) |
|-----------|------|-----|---------------------------|
| **VCard** | ⚠️ radius 14, flat shadow | ✅ | `VDimens.kt:31` `radiusCard = 16.dp`; `VCard.kt:48` `vElevation(Card)` 2-layer navy tint |
| **VButton (filled)** | 🔴 2-tone, no motion/states | ✅ | `VButton.kt:66-91` 8-tone `tonePalette`; `:137-218` idle/loading/success machine; `:198-220` §13.6 shimmer |
| **VButton (soft)** | 🔴 soft=false default | ✅ | `VButton.kt:108` `soft: Boolean = true`; per-tone soft colors `:61-91`; `:163` dual shadow |
| **VInput** | ⚠️ radius 10, wrong glow | ✅ | `VInput.kt:40` `FocusGlow = #3CD1BE`; radius 12; `:69` focus state; animated icon color |
| **VBadge** | ✅ | ✅ | unchanged — 11/600/0.04em pill |
| **VTag** | ⚠️ active bg/border | ✅ | exact `#dcf2ef` active bg + teal border + press-scale |
| **VLabel** | 🔴 600/0.08em | ✅ | `labelStrong` 11/700/0.10em added to `VType` |
| **VAvatar** | ✅ | ✅ | palette + initials unchanged |
| **VDonut / VBars / VSparkline** | ⚠️ verify | ✅ | `VCharts.kt` gradient area fill + end dot + last-bar accent + animated reveal |
| **VProgressRing** | ✅ | ✅ | unchanged |
| **VBottomNav** | ⚠️ no top shadow | ✅ | `VNavigation.kt` upward navy@4% drawn shadow (§14.1 fix); `:129` `navigationBarsPadding()`; badge `#c14a44` |
| **VTopTabs** | ⚠️ static underline | 🟡 | `VNavigation.kt:64` active color **tween done**; underline still per-tab toggle (not a sliding indicator). Inside `horizontalScroll`, a sliding bar needs per-tab position measurement — deferred to avoid an unbuildable regression; functionally correct, motion-only gap. |
| **VTopBar** | ✅ | ✅ | white + navy@6% hairline |
| **VComingSoon** | 🔴 empty preview | ✅ | `VStructure.kt:119,132` `preview` slot wired; SRI/PEWS/AIReportCard previews land in Discovery/Notifications |
| **VTheme / tones** | 🔴 forced Night | ✅ | admin/teacher now Warm (light); Night wrappers removed |
| **VType** | 🔴 no fonts | ✅ | `VType.kt:25-66` Plus Jakarta Sans + DM Mono families; tnum/ss01 features; negative tracking |
| **VElevation** | 🔴 missing | ✅ | `VElevation.kt` 3-tier `{Card, Raised, Modal}` navy-tinted, drawn (parity Android/iOS) |
| **VMotion** | 🔴 missing | ✅ | `VMotion.kt:33-45` `springSoft/Sheet/Card/Snappy` + `fadeUp(delay)` |
| **pressScale modifier** | 🔴 missing | ✅ | `VMotion.kt:64` `Modifier.pressScale()` |
| **Cross-platform back** | 🔴 none | ✅ | `ui-backhandler` dep added (`build.gradle.kts:90`); `BackHandler` in `NavGraphV2` + 3 portals (§11) |

### Remaining open items (none blocking)
- 🟡 **VTopTabs sliding underline** — colour transition is animated; the 2.5px bar still
  snaps between tabs instead of sliding its x/width. Low visual priority; a proper port
  needs `onGloballyPositioned` tab-bounds capture inside the scroller. Tracked for a
  future pass.
- 🟢 **§13.7 hero gradient/glow/clouds** & **§13.8 OpenType ss01/ss02 on iOS** — cosmetic
  polish; logged in the audit body, not regressions.

**Verdict:** 19 of 21 component rows fully ✅; 1 🟡 (motion-only) + the cosmetic backlog.
The UI-fidelity port is functionally complete and buildable (BackHandler dependency fixed).
