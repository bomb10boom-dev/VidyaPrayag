# Enroll+ Logo — Website Rebuild Guide

The website now renders the **exact mobile-app "Setu" bridge mark**, faithfully
rebuilt from the app's single source of truth and **recoloured** to the website
UI design system (premium, minimal, $100M-raise level). This document records
the design, where the assets live, and **exactly which mobile-app files would
be updated later** to bring the app's *colour* in line — the app geometry is
already correct.

> No mobile-app (`composeApp/`) files were modified by this change — per the
> brief, the website is rebuilt first; the app touchpoints are only
> **documented** here for a later pass.

---

## 1. The design

The mobile app's mark is the **"Setu" bridge** — a quadratic arc spanning two
grounded pillar caps over a deck, with three suspension cables and a solid apex
node, signalling the connection between school and home. It is defined once in
`composeApp/.../ui/v2/components/VBrandLogo.kt → drawBridge` (and mirrored in
`VLogo.kt`) on a **56-unit viewBox**.

The website mark is **that exact geometry, rebuilt 1:1** — the app's 56-unit
bridge centred in a 64-unit plate (translate **+4,+4**). Nothing about the shape
was "evolved"; only the **colour** changed, from the app's teal accent to the
website's lavender / violet system:

| Element | App geometry (56u) | Website (64u, +4,+4) | Colour (website) |
|---|---|---|---|
| Span arc | `M12 32 Q28 12 44 32` | `M16 36 Q32 16 48 36` | lavender-violet gradient `#544AB8`→`#6C5CE0`→`#544AB8` (app was teal) |
| Deck | `M10 40 H46` | `M14 44 H50` | navy `#26234D` |
| Cables (×3) | x=18/28/38, top y=32/22/32 → 40 | x=22/32/42, top y=36/26/36 → 44 | navy `#26234D` @ 0.78 (matches app's 0.78) |
| Pillar caps | circles `r2.6` at (12,32)/(44,32) | `r2.9` at (16,36)/(48,36) | navy `#26234D` |
| Apex node | circle `r2.4` at (28,22) | `r3` at (32,26) | accent violet `#6C5CE0` (light) / lavender `#E6E6FA` (dark) |
| Plate | white-glass on teal hero | rounded square | lavender `#E6E6FA` (light) / navy `#1A1838` (dark) |

> **Design rule: the graphic mark contains NO "+" symbol.** The apex is the
> app's single solid centre node — confident infrastructure, not a literal plus.
> The "+" lives only in the *wordmark text*.

Recognisable at **16×16** (favicon), premium at **400×400** (embossed on a black
card → use the dark variant).

The **wordmark "Enroll+" is unchanged** — same spelling, casing, and the "+" in
the *text* (this is the only place the "+" appears).

---

## 2. Website assets (already replaced)

| Form | File | Use |
|---|---|---|
| Mark only (light) | `website/public/brand/enrollplus-mark.svg` | favicon, app icon, inline |
| Mark only (dark) | `website/public/brand/enrollplus-mark-dark.svg` | embossed-on-black card, Apple touch icon |
| Full lockup | `website/public/brand/enrollplus-lockup.svg` | mark + wordmark side by side |
| Stacked lockup | `website/public/brand/enrollplus-stacked.svg` | mark above wordmark |
| React component (single source) | `website/src/components/ui/Logo.tsx` | `<Logo/>`, `<EnrollMark/>`, `<EnrollWordmark/>` — geometry identical to the SVGs |

The component is the live source for the header (`components/Header.tsx`), footer
(`components/Footer.tsx`), the admin sidebar, and the favicon is wired in
`src/app/layout.tsx → metadata.icons`.

---

## 3. Mobile-app files to update later (NOT modified here)

The app's bridge **geometry is already correct** — the website matches it. To
bring the app in line we only need to **recolour** (teal → lavender/violet) and
refresh the launcher/wordmark in a later pass:

| # | Path | What to change |
|---|---|---|
| 1 | `composeApp/src/commonMain/kotlin/com.littlebridge.enrollplus/ui/v2/components/VLogo.kt` | Keep the geometry. **Recolour only:** swap the teal arc accent for the lavender-violet gradient (`#544AB8`→`#6C5CE0`→`#544AB8`); keep deck + cables navy; make the centre node accent violet. Plate → lavender `#E6E6FA`. |
| 2 | `composeApp/src/commonMain/kotlin/com.littlebridge.enrollplus/ui/v2/components/VBrandLogo.kt` | Keep `drawBridge` geometry. For the dark/hero plate: white deck/cables + lavender apex node (mirror `enrollplus-mark-dark.svg`). |
| 3 | `composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml` | Regenerate from `enrollplus-mark.svg` (vector drawable). The launcher icon should be the **mark only** (no "+"). |
| 4 | `composeApp/src/androidMain/res/drawable-v24/ic_launcher_monochrome.xml` | Regenerate single-colour silhouette of the new mark (deck + arc + apex node). |
| 5 | `composeApp/src/androidMain/res/drawable/ic_launcher_background.xml` | Set background to lavender `#E6E6FA` (light) to match the new plate. |
| 6 | `composeApp/src/androidMain/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.webp` | Re-export the raster launcher icons (`ic_launcher.webp`, `ic_launcher_round.webp`, `ic_launcher_foreground.webp`) at every density from the new mark. |
| 7 | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` (+ any other sizes) | Replace the iOS app icon PNG(s) by exporting `enrollplus-mark-dark.svg` (navy card) at all required sizes. |
| 8 | Wordmark usages (search `VidyaSetu`/`buildAnnotatedString` in `VLogo.kt`) | The brief keeps the **"Enroll+"** wordmark text — ensure the app's wordmark reads `Enroll` + accent `+`, matching `EnrollWordmark` in the website. The "+" stays in the *text only*. |

> Tip: the SVG `path` data in `website/public/brand/enrollplus-mark.svg` can be
> pasted almost verbatim into an Android `<vector>`/`<path>` (convert the
> `viewBox` to `viewportWidth/Height="64"`). Because the website geometry is the
> app's geometry +4,+4, recolouring is the only real work on the app side.

---

## 4. Verification checklist

- [ ] Favicon shows the new mark in the browser tab (`/brand/enrollplus-mark.svg`).
- [ ] Header + footer render the full lockup via `<Logo/>`.
- [ ] Admin sidebar shows the mark + school name.
- [ ] Dark variant legible when embossed on a black/navy card.
- [ ] Mark legible at 16px (zoom the favicon).
- [ ] **No "+" sign anywhere in the graphic mark** (only in the wordmark text).
- [ ] (Mobile, when applied) launcher icon + in-app `VLogo` match the website.
