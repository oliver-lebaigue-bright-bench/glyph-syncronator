# Renaming Project from "Glyph Syncronator" to "Glyphix"

This plan outlines the steps to rename all occurrences of "Glyph Syncronator" (and its variations) to "Glyphix" across the codebase, resources, and documentation.

## User Review Required

> [!IMPORTANT]
> This change involves renaming core UI components and theme names. While the package name `com.glyphix.app` is already set, the internal class and function names still refer to the old project name.

## Proposed Changes

### Project Configuration

#### [MODIFY] [settings.gradle](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/settings.gradle)
- Update `rootProject.name` to `"Glyphix"`.

### Android Resources

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/AndroidManifest.xml)
- Update `attribution android:tag` from `"glyph-syncronatorTag"` to `"glyphixTag"`.
- Update `android:theme` from `"@style/Theme.glyph_syncronator"` to `"@style/Theme.Glyphix"`.
- Update `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value to `"glyphix"`.

#### [MODIFY] [themes.xml](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/res/values/themes.xml)
- Rename `Theme.glyph_syncronator` to `Theme.Glyphix`.
- Rename `Theme.glyph_syncronator.NothingRed` to `Theme.Glyphix.NothingRed`.

#### [MODIFY] [strings.xml (all)](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/res/values/strings.xml)
- Replace all instances of "glyph-syncronator", "GLYPH-SYNCRONATOR", and "Glyph Syncronator" with "Glyphix", "GLYPHIX", and "Glyphix" respectively.
- This includes all localized files in `res/values-*/strings.xml`.

### Kotlin/Java Code

#### [MODIFY] [FuckingThemes.kt](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/ui/FuckingThemes.kt)
- Rename `GlyphSyncronatorTheme` to `GlyphixTheme`.

#### [MODIFY] [UIComponents.kt](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/ui/UIComponents.kt)
- Rename `GlyphSyncronatorBackground` to `GlyphixBackground`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/ui/MainActivity.kt)
- Update usages of `GlyphSyncronatorTheme`, `GlyphSyncronatorBackground`, and rename `GlyphSyncronatorApp` to `GlyphixApp`.

#### [MODIFY] [HapticsTileService.java](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/service/HapticsTileService.java)
- Update tile label from `"Glyph Syncronator Haptics"` to `"Glyphix Haptics"`.

#### [MODIFY] [VisualizerTileService.java](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/app/src/main/java/com/glyphix/app/service/VisualizerTileService.java)
- Update tile label from `"Glyph Syncronator"` to `"Glyphix"`.

### Documentation and Scripts

#### [MODIFY] [README.md](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/README.md)
- Replace all mentions of the old name.
- Update GitHub links if necessary (keeping in mind the actual repo URL might still be the old one, but display text should change).

#### [MODIFY] [PYTHON SCRIPT/musicViz.py](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/PYTHON%20SCRIPT/musicViz.py)
- Update URL and comments.

#### [MODIFY] [website/](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/website/)
- Update `index.html`, `404.html`, `script.js`, and `README.md` to use the new name.

### GitHub Templates

#### [MODIFY] [.github/ISSUE_TEMPLATE/feature_request.md](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/.github/ISSUE_TEMPLATE/feature_request.md)
- Update "about" description.

#### [MODIFY] [.github/copilot-instructions.md](file:///C:/Users/olive/AndroidStudioProjects/glyph-syncronator/.github/copilot-instructions.md)
- Update project name in instructions.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure all references are correctly updated and the app compiles.

### Manual Verification
- Deploy the app and verify the launcher name and tile names.
- Verify that the app theme still works correctly.
- Verify that documentation looks correct.
