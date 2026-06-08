# Revamp F1 Schedule Widget UI

Revamp the `F1ScheduleWidget` to improve data density, align schedule data to the right 50% of tiles, and enhance the header with a glassy/blurry aesthetic over a flag background banner.

## Proposed Changes

### Resources

#### [f1_banner_fade.xml](file:///C:/Users/T3lluz/AndroidStudioProjects/MacroTracker/app/src/main/res/drawable/f1_banner_fade.xml)
- Strengthen the fade effect by making it opaque earlier (`centerX="0.5"`) to better hide the flag image edge.

#### [f1_surface_fade.xml](file:///C:/Users/T3lluz/AndroidStudioProjects/MacroTracker/app/src/main/res/drawable/f1_surface_fade.xml)
- Similar update to strengthen the fade into the surface color.

---

### Widget UI

#### [F1ScheduleWidget.kt](file:///C:/Users/T3lluz/AndroidStudioProjects/MacroTracker/app/src/main/kotlin/com/macrotracker/widget/F1ScheduleWidget.kt)

##### Header Revamp (`HeroCardFull`):
- Update `HeroCardFull` to use a "glassy" container for the race info and countdown.
- Use semi-transparent backgrounds (e.g., `Color(0x33FFFFFF)`) to simulate the frosted glass effect.
- Reorganize layout for better density and "coolness".

##### Schedule List Revamp (`RaceRow`):
- Change layout to a `Row` with two equal weights.
- Left half remains empty (showing the flag background).
- Right half contains all data: Round, Race Name, and a compact grid of session times (Quali, Sprint, Race).
- Align data to be clean and dense.
- Increase tile height slightly (to `64.dp`) to fit more info comfortably.

##### Helpers:
- Update `fmtLocalTime` or add a variant if needed to ensure times are displayed concisely.

---

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator.
- Add the F1 Schedule Widget (4x3 or 5x3 size).
- Verify the header has the flag background with a smooth fade and glassy tiles.
- Verify the schedule list has data aligned to the right 50% of the tiles.
- Verify all session times (Quali, Sprint, Race) are visible and correctly aligned.
- Check different widget sizes (if applicable) to ensure scaling works correctly via `WScale`.

### Automated Tests
- None planned as this is a purely UI/Glance change which is best verified visually.
