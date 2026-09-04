# Improve Navbar Tab Switching Animation

The goal is to make the navbar tab switching animation more fluid and premium. The current "Collapse -> Bounce -> Expand" sequence feels a bit disconnected as the indicator almost disappears during the move.

## Proposed Changes

### [MockupDesignSystem.kt](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/ui/MockupDesignSystem.kt)

#### [MODIFY] [FloatingBottomBar](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/ui/MockupDesignSystem.kt#L234)
- **Simplify Animation Logic**: Consolidate the two redundant `LaunchedEffect(selectedTab)` blocks.
- **Implement Overlapping Transitions**: Instead of a sequential collapse-move-expand, we will overlap these animations. The indicator will start moving while it's still partially expanded, creating a "sliding" feel.
- **Refined Spring Dynamics**: Use higher stiffness for the movement to make it feel more responsive, and a slightly higher damping ratio to keep it professional but snappy.
- **Icon "Pop" Effect**: Add a scale and rotation "pop" to the icons as they are selected using `animateFloatAsState`.
- **Liquid Indicator**: Adjust the `getCenterForIndex` and `pillWidth` logic to allow for a slight "stretching" effect during the transition if possible, or at least ensure the indicator never fully collapses to 0 (minimum width = icon size).

## Verification Plan

### Manual Verification
- Deploy the app and switch between tabs (Audio, Leaderboard, Info, Settings).
- Observe the fluidity of the white indicator pill.
- Check the icon animations to ensure they "pop" satisfyingly.
- Verify that "Banana Mode" and "Penis Mode" still work correctly with the new animations.
