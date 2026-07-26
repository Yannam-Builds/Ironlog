---
scope: github-and-product-brand
source: PRODUCT.md and app/src/main/java/com/ironlog/app/ui/theme/IronLogThemes.kt
status: active
---

# IronLog design system

## 1. Direction

IronLog should read like a precise training instrument: dark, direct, and energetic at the moment of action. The visual signature is a forge-orange signal moving through a restrained black-and-steel environment. Recovery green and earned gold appear only when they carry meaning.

## 2. Color

| Role | Dark | Light | Use |
|---|---:|---:|---|
| Background | `#000000` | `#F6F7F8` | Main field |
| Surface | `#0D0D0D` | `#FFFFFF` | Raised information |
| Border | `#252525` | `#D8DCE1` | Structure |
| Primary text | `#FFFFFF` | `#111111` | Headlines and values |
| Secondary text | `#A8A8A8` | `#525861` | Supporting copy |
| Forge | `#FF4500` | `#D93600` | Primary action and motion |
| Recovery | `#00C170` | `#087F4D` | Ready/complete states |
| Earned | `#FFD700` | `#9A6A00` | Milestones only |

## 3. Typography

Use Lexend in the Android product where bundled and system sans-serif in repository graphics. Headlines are heavy and compact; metrics are large with tabular numerals; explanatory text stays regular and readable. All-caps labels are reserved for short operational signals, with visible tracking.

## 4. Shape and layout

Prefer one dominant composition over repeated feature cards. Use 10–18 px radii for product surfaces, 1 px structural borders, and square or pill forms only when they communicate a control or status. Keep the GitHub hero editorial: wordmark and promise on one side, a single animated training instrument on the other.

## 5. Motion

Motion must explain a training loop: load, effort, recovery, record, repeat. Use simple transforms, stroke dashes, and opacity changes; avoid elastic motion and perpetual high-frequency effects. Typical duration is 1.8–6 seconds, with slow ambient rotations no faster than 12 seconds. Under `prefers-reduced-motion`, disable every animation and render the meaningful final state.

## 6. Usage rules

Do not use gradients as decoration, glass effects, emoji icons, stock gym photography, or hand-drawn pseudo-product art. Do not make orange the background of large text areas. Claims in README graphics must match implemented code and current validation. SVGs need semantic titles/descriptions, adaptive colors, unique IDs, and successful XML/render checks before publication.
