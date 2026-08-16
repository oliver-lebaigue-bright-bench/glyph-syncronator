<div align="center">

# glyph-syncronator

**Feel your music on Nothing Phones—precisely synced to every frequency.**

![Downloads](https://img.shields.io/github/downloads/Aleks-Levet/better-nothing-music-visualizer/total?style=flat-square&logo=github&label=Devices%20Made%20Better&color=ff0000&labelColor=000000)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)
![Status](https://img.shields.io/badge/status-Active-brightgreen?style=flat-square)

[**🚀 Download APK**](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/releases) • [**🌐 Visit Site**](coming soon) • [**💬 Join Discord**](#community) • [**📖 Full Docs**](#documentation)

</div>

---

## What is this?

Real-time music visualization for Nothing Phone Glyphs, haptics, and flashlight, uses **FFT audio analysis** for pixel-perfect frequency response, in addition to offering extensive customization. Unlike the stock implementation.

---

## Why it exists

The stock glyph visualizer is very boring and regular. Glyph-syncronator uses mathematical precision to address every single glyph zone or matrix pixel, making a use of every bit instead of the stock visualizer which simply lights up the entire strip. As well as allowing for brightness adjusting in a range of the full 4,096 levels (12-bit)

---

## What you get

| Feature | Stock | **glyph-syncronator** |
|:---|:---|:---|
| **Light depth** | ~3 levels | **4,096 levels** (12-bit) |
| **Frame rate** | 20 FPS | **60 FPS** |
| **Precision** | Low, unreliable | **FFT-based, deterministic** |
| **Control** | Full glyphs only | **Every zone independently** |

---

## Quick start

### Download & Install
1. [Grab the latest APK](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/releases) and install
2. Pick the capture source of your choosing (Media projection recommended)
3. Press **Start** and play music

### Adjust sync
- Playing over Bluetooth? Use the **Audio** tab to add/remove latency
- Customize frequency ranges in `zones.config` (see [detailed docs](Docs/ZONES_CONFIG.md))

---

## Supported devices

### ✨ Full Glyph support
- Nothing Phone (1)
- Nothing Phone (2), (2a), (2a) plus 
- Nothing Phone (3), (3a), (3a) Pro
- Nothing Phone (4a), (4b), (4a) pro

### 🔊 Haptics & Flashlight
Any Android phone works for haptic and flashlight modes.

---

## How it works (under the hood)

```
Audio Stream → FFT Analysis (20ms window) → Frequency Mapping → 
→ Glyph Zones / Haptic / Flashlight → Display
```

**The secret sauce:**
- **FFT (Fast Fourier Transform)** breaks audio into frequencies every frame
- Each glyph zone is assigned a frequency range—brightness = peak magnitude in that range
- **Downward-only smoothing** keeps animations responsive without jitter
- Fully deterministic—no randomness

**For haptics & flashlight:** Uses bass amplitude for continuous glow, or beat detection via derivative for pulse effects.

---

<a id="documentation"></a>

## 📖 Documentation

- **[zones.config guide](Docs/ZONES_CONFIG.md)** — Customize presets, add phone models
- **[Python script (legacy)](https://github.com/Aleks-Levet/better-nothing-music-visualizer/wiki/)** — Bulk audio file processing
- **[Issue tracker](https://github.com/Aleks-Levet/better-nothing-music-visualizer/issues)** — Bugs? Feature requests?

---

<a id="community"></a>

## 💬 Join the community

Got ideas? Found a bug? Want to contribute?

- **[Discord server](https://discord.gg/cQ4hxNE8fX)** — Chat with devs and users
- **[GitHub Discussions](https://github.com/Aleks-Levet/better-nothing-music-visualizer/discussions)** — Feature ideas or bug reports 

---

## 🛠️ Contributing

We welcome contributions! Ideas:
- New visualization presets
- UI/UX improvements
- Translation help
- Bug reports & fixes

[Open an issue](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/issues) or submit a pull request.

---

## 🔓 Privacy & Security

- **Screen Capture**: We don't collect or process any info from your screen, Screen Capture is only used to process the audio that the feature exposes. Because this is the most reliable audio source, we suggest in using it
- **Audio**: Only captured for visualization—never stored or transmitted
- **Analytics**: Google Firebase collects anonymous usage stats to help us fix issues
- **Scan**: [VirusTotal report](https://www.virustotal.com/gui/url/c92c1ff82b56eb60bfd1e159592d09f949f0ea2d195e01f7f5adbef0e0b0385b)

---

## Devs

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=80&mask=circle" width="80" alt="oliver-lebaigue-bright-bench" />
        <br/><b>Oliver Lebaigue</b>
        <br/><small>Founder & Core Dev</small>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/m-uvex">
        <img src="https://github.com/m-uvex.png?size=80&mask=circle" width="80" alt="oliver-lebaigue-bright-bench" />
        <br/><b>m_uvex</b>
        <br/><small>Web Dev</small>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/gabibrawl">
        <img src="https://github.com/gabibrawl.png?size=80&mask=circle" width="80" alt="gabibrawl" />
        <br/><b>GabiBrawl</b>
        <br/><small>Core Dev</small>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/p-1749">
        <img src="https://github.com/p-1749.png?size=80&mask=circle" width="80" alt="p-1749" />
        <br/><b>P.1749</b>
        <br/><small>Core Dev</small>
      </a>
    </td>
  </tr>
</table>

---

<a id="download"></a>

<div align="center">

### 🚀 Ready to vibe?

[**Download Latest APK**](https://github.com/Aleks-Levet/better-nothing-music-visualizer/releases) • [Join Discord](https://discord.gg/cQ4hxNE8fX)

</div>

---

<div align="center">

Made with ❤️ by the glyph-syncronator community

</div>
