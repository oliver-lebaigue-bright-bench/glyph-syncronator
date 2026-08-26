# ⚙️ zones.config Documentation

The `zones.config` file is the heart of the visualizer's frequency mapping. it defines how audio frequencies (in Hz) map to specific Glyph LEDs or segments on different Nothing Phone models.

This file is used by both the **Android App** and the **Python Visualizer Script**.

---

## 📂 File Structure

The file is a JSON document containing global settings and multiple phone-specific **presets**.

### 🌍 Global Values

These settings apply to all presets unless overridden locally.

| Key | Type | Description |
| :--- | :--- | :--- |
| `version` | String | Configuration version (displayed in the App's "About" screen). |
| `decay-alpha` | Number | Controls the "fade-out" speed of the LEDs. Values range from `0.0` to `1.0`. <br> • **Higher values** = Longer, smoother fade. <br> • **Lower values** = Faster, snappier response. <br> *Default: 0.8* |

### 🎨 Preset Configuration

Each top-level key (e.g., `"np2"`, `"np3a-bass"`) defines a unique visualization style.

| Key | Type | Description |
| :--- | :--- | :--- |
| `description` | String | What this preset does (shown in the app). |
| `phone_model` | String | The target device. <br> Supported: `PHONE1`, `PHONE2`, `PHONE2A`, `PHONE3A`, `PHONE4A`, `PHONE4B`, `PHONE3`. |
| `decay-alpha` | Number | *(Optional)* Override the global decay speed for this preset. |
| `zones` | Array | **The Mapping Table.** Defines which frequencies go to which LEDs. |

---

## ⚡ Frequency Zone Definition

The `zones` array is an ordered list. Each entry corresponds to one physical LED or addressable segment on the phone, in the hardware's specific order.

**Format:** `[lowHz, highHz, label, startPercent, endPercent]`

### 1. `lowHz` & `highHz` (Required)
The frequency range in Hertz (Hz) that this LED will "listen" to. 
- **Bass:** `20Hz - 250Hz`
- **Mids:** `250Hz - 4000Hz`
- **Highs:** `4000Hz - 20000Hz`

### 2. `label` (Required)
A human-readable string describing the zone (e.g., `"Camera Glyph"`). This is strictly for documentation and is ignored by the app's processing logic.

### 3. `startPercent` & `endPercent` (Optional)
These values (0-100 or 0.0-1.0) allow you to fine-tune the **intensity response** of an LED. They act as a "gate" for the brightness:
- **`startPercent` (Noise Floor):** The LED will remain off until the frequency intensity reaches this percentage.
- **`endPercent` (Ceiling):** The LED will reach maximum brightness at this percentage of intensity.

**Example:** `[40, 100, "Kick Drum", 20, 80]`
The LED listens to 40-100Hz. It stays dark if the bass is quiet (<20%), and hits full brightness once it reaches 80% intensity, providing a punchier look.

---

## 📱 Device Mapping Guide

The number of entries in the `zones` array must match the hardware capability of the selected `phone_model`:

| Model | Zone Count | Notes |
| :--- | :--- | :--- |
| **Phone (1)** | 5 or 15 | Can address 5 main areas or 15 segments including the battery bar. |
| **Phone (2)** | 33 | Includes 16 segments for the main ring and 8 for the battery bar. |
| **Phone (2a)** | 26 | Optimized for the three main glyph parts. |
| **Phone (3a)** | 36 | Full addressable matrix segments. |
| **Phone (4a)** | 7 | Simple segmented bar + red accent. |
| **Phone (4b)** | 5 | Narrower segmented bar. |

---

## 🛠️ Tips for Custom Presets

1. **Mirroring:** To create a "center-out" effect on a progress bar (like the Phone 2 ring), map the same frequencies to segments on both sides, but reverse the `startPercent` / `endPercent` ranges.
2. **Zebra Patterns:** Alternate between two frequency ranges (e.g., Bass and Mids) for consecutive LEDs in a bar to create a "strobe" or "intertwined" effect.
3. **Bass Gauges:** Use the same low-frequency range (e.g., `40-120Hz`) for an entire addressable bar, but increment the `startPercent` for each segment (e.g., 0-10, 10-20, 20-30...). This turns the bar into a physical volume/intensity meter for the bass.
