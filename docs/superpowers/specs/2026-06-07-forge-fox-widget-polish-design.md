# Forge Fox Widget Polish Design

**Goal:** Turn the current functional Forge Fox widgets from placeholder-looking cards into a cleaner, more intentional first production pass.

**Approved Direction:** The existing implementation is useful for data wiring, but visually weak. The polish pass will keep Jetpack Glance, the existing widget receivers, and the existing `WidgetState` data flow. It will improve the visual result without rewriting app architecture.

## Design

The widget system keeps one consistent grammar: a streak lockup at the top-left with the official flaming dumbbell icon immediately left of the streak number, bold state text below, and Forge Fox composed for each size. The lockup remains anchored in all sizes. The mascot art remains raster PNG for now because the available 2D chibi poses are bitmap assets; the streak icon becomes the extracted reference-based PNG instead of the rough temporary vector.

Small widgets should be simple: icon, number, short label, and a cropped mascot face or readable upper body. Medium widgets can show a weekly row, but it should be a compact strip in its own translucent band instead of raw dots floating near the edge. Tall widgets should use stronger vertical composition with a larger mascot and weekly row at the bottom. Wide widgets should stop reading like stretched squares: left column for lockup/status, right side for Forge Fox, bottom row only when it fits.

## Implementation Boundaries

Modify only the widget implementation, widget tests, and preview artifacts unless compilation requires a local import fix. Do not change ObjectBox schema, app navigation, workout completion, or gamification logic outside the existing widget resolver hook.

## Verification

Unit tests should prove the state resolver returns active streak for a safe streak state, the layout classifier chooses the expected size classes, and the single streak icon resource remains the official `ic_forge_streak_dumbbell`. Build verification should run `:app:testDebugUnitTest` and `:app:assembleDebug`. A generated local preview board should be produced because no emulator is currently attached.
