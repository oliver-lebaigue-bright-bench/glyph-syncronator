# glyph-syncronator - AI Coding Instructions

## Project Overview
This is an Android music visualizer app for Nothing phones that enhances the stock Glyph music visualization using FFT analysis. The app captures live audio via Media Projection API and drives the phone's glyph LEDs with 60 FPS, 12-bit depth precision. A companion Python script provides offline audio file processing.

## Architecture & Key Components

### Core Audio Processing Pipeline
- **AudioProcessor.java**: Handles FFT analysis with 20ms windows at 60 FPS using JTransforms library
- **GlyphRenderer.java**: Computes glyph brightness states, applies gamma correction, manages idle breathing effects
- **AudioCaptureService.java**: Manages Media Projection audio capture from device/system audio
- **zones.config**: JSON configuration defining frequency ranges (Hz) for each glyph zone per phone model

### Real-Time Flow
```
Audio Capture → FFT Analysis → Frequency Zone Mapping → Brightness Calculation → Glyph Rendering
```

### Python Script Flow (Offline Processing)
```
Audio File → FFT Analysis → .nglyph Generation → GlyphModder.py → Glyphed OGG Output
```

## App Structure & Compilation

### Android App Architecture
- **MainActivity.kt**: Entry point, handles permissions, service binding, and UI navigation
- **AudioCaptureService.java**: Background service for Media Projection audio capture
- **AudioProcessor.java**: Core FFT processing and frequency analysis
- **GlyphRenderer.java**: Brightness computation and glyph state management
- **HapticEngine.kt**: Beat detection and vibration feedback
- **UI Screens**: SettingsScreen.kt, GlyphSettingsScreen.kt, HapticsScreen.kt (Compose-based)
- **DeviceProfile.java**: Phone model configurations and LED mappings

### Build System
- **Gradle**: Android Gradle Plugin 9.2.0 with Kotlin 2.3.20
- **Compile SDK**: 36, Min SDK: 34, Target SDK: 36
- **Java/Kotlin**: JVM 17 compatibility
- **Build Types**: Debug (with debuggable flag) and Release (with ProGuard)

### Compilation Commands
- **Debug Build**: `./gradlew assembleDebug` - Creates debug APK with debugging enabled
- **Release Build**: `./gradlew assembleRelease` - Creates optimized release APK with code shrinking
- **Clean Build**: `./gradlew clean assembleDebug` - Clean and rebuild from scratch
- **Install Debug**: `./gradlew installDebug` - Build and install debug APK to connected device

### Key Dependencies
- **glyph-matrix-sdk-2_0.aar**: Local library for glyph hardware control (in libs/)
- **JTransforms**: FFT processing (com.github.wendykierp:JTransforms:3.2)
- **Compose BOM**: UI framework (androidx.compose:compose-bom:2026.04.01)
- **Material 3**: UI components (androidx.compose.material3:material3:1.4.0)

## Critical Developer Workflows

### Android App Development
- **Build Commands**: Use `./gradlew assembleDebug` or `./gradlew assembleRelease`
- **Phone Setup**: For Phone (1), enable debug mode: `adb shell settings put global nt_glyph_interface_debug_enable 1`
- **Permissions**: Requires Media Projection and Notification access
- **Testing**: Deploy APK to device, grant permissions, play audio from any app (Spotify, YouTube, etc.)

### Python Script Development
- **Dependencies**: Install via `pip install -r PYTHON\ SCRIPT/requirements.txt` (numpy, scipy, pydub, soundfile)
- **Usage**: `python PYTHON\ SCRIPT/musicViz.py <audio_file> <preset>` 
- **Output**: Generates .nglyph files, optionally converts to glyphed OGG using GlyphModder.py

## Project-Specific Patterns & Conventions

### Frequency Analysis
- **FFT Window**: Fixed 4096 samples for temporal precision
- **Frame Rate**: 60 FPS with ~16.7ms frame time
- **Smoothing**: Downward-only decay (PEAK_FALLOFF = 0.9995f) preserves responsiveness
- **Gain**: SPECTRUM_GAIN = 4f amplifies frequency magnitudes

### Glyph Control
- **Brightness Depth**: 12-bit (0-4095) vs stock 2-bit (3 levels)
- **Zone Mapping**: Each glyph zone maps to specific frequency ranges defined in zones.config
- **Normalization**: Quadratic scaling applied to frequency peaks before brightness conversion
- **Gamma Correction**: Applied to final brightness values for perceptual uniformity

### Configuration Format
```json
"zones": [
  [200, 600, "Camera glyph"],
  [2000, 6000, "Essential glyph"]
]
```
- Frequency ranges in Hz pairs [low, high]
- Optional progress bar mode with additional [min%, max%] parameters

### Code Organization
- **Java Classes**: Core audio processing and glyph rendering logic
- **Kotlin Files**: UI screens, haptic engine, main activity
- **Mixed Language**: AudioProcessor.java calls into Kotlin haptic engine for beat detection

## Integration Points & Dependencies

### Android Dependencies
- **glyph-matrix-sdk-2_0.aar**: Local AAR library for glyph hardware control
- **JTransforms**: FFT processing library
- **Media Projection API**: For system-wide audio capture
- **Compose BOM**: UI framework with Material 3

### Python Dependencies
- **scipy**: FFT analysis (rfft, rfftfreq)
- **numpy**: Numerical computations
- **soundfile**: Audio file I/O
- **GlyphModder.py**: External tool for embedding glyph data into OGG files

### External Tools
- **FFmpeg**: Audio format conversion in Python pipeline
- **ADB**: Device debugging and glyph debug mode enablement

## Common Development Tasks

### Adding New Phone Models
1. Define glyph zones in zones.config with frequency ranges
2. Update DeviceProfile.java with LED count and zone mappings
3. Test visualization accuracy across frequency spectrum

### Tuning Visualization Presets
1. Modify frequency ranges in zones.config
2. Adjust gain/smoothing parameters in AudioProcessor.java
3. Test with various music genres for responsiveness

### Debugging Audio Issues
- Check Media Projection permissions granted
- Verify audio routing (Bluetooth/wired latency compensation)
- Use logcat to monitor FFT magnitudes and glyph brightness values

## File Structure Reference
- `app/src/main/java/com/better/nothing/music/vizualizer/`: Core app logic
- `zones.config`: Glyph frequency zone definitions
- `PYTHON SCRIPT/`: Offline processing tools
- `glyph-matrix-sdk-2_0.aar`: Hardware control library</content>
<parameter name="filePath">/home/oliver/Projects/glyph-syncronator/.github/copilot-instructions.md
