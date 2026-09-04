import socket
import pyaudiowpatch as pyaudio
import numpy as np
import time
import argparse
import sys
import threading
import queue
import asyncio
import math
import colorsys

try:
    import ctypes
    from ctypes import wintypes
    HAS_CTYPES = True
except ImportError:
    HAS_CTYPES = False
    ctypes = None
    wintypes = None

import customtkinter as ctk
from tkinter import messagebox, colorchooser

try:
    from openrgb import OpenRGBClient
    from openrgb.utils import RGBColor, DeviceType
    OPENRGB_AVAILABLE = True
except ImportError:
    OPENRGB_AVAILABLE = False
    OpenRGBClient = None
    DeviceType = None
    class RGBColor:
        def __init__(self, red=0, green=0, blue=0):
            self.red = red
            self.green = green
            self.blue = blue

UDP_PORT = 12347
DISCOVERY_PORT = 12348
OPENRGB_PORT = 6742
BLE_SERVICE_UUID = "7d9c63c0-37b1-4122-861f-36655c687e46"
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 48000

COLOR_BG = "#080808"
COLOR_CARD = "#121212"
COLOR_CARD_HOVER = "#1A1A1A"
COLOR_BORDER = "#222222"
COLOR_ACCENT = "#E01B22"
COLOR_WHITE = "#FFFFFF"
COLOR_MUTED = "#666666"
COLOR_TEXT = "#EAEAEA"

FONT_MAIN = "Segoe UI" if sys.platform == "win32" else "Inter"
FONT_LOGO = "Courier New"

user32 = None
kernel32 = None
if HAS_CTYPES and sys.platform == "win32":
    try:
        user32 = ctypes.windll.user32
        kernel32 = ctypes.windll.kernel32
    except:
        HAS_CTYPES = False

WH_KEYBOARD_LL = 13
WM_KEYDOWN = 0x0100
WM_SYSKEYDOWN = 0x0104

class KeyboardHookWatcher:
    def __init__(self, on_press_callback):
        self.on_press_callback = on_press_callback
        self.hook = None
        self.thread = None
        self.running = False
        self._c_proc = None

    def start(self):
        if not HAS_CTYPES or sys.platform != "win32": return
        if self.running: return
        self.running = True
        self.thread = threading.Thread(target=self._hook_loop, daemon=True)
        self.thread.start()

    def _hook_loop(self):
        HOOKPROC = ctypes.WINFUNCTYPE(ctypes.c_long, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM)
        def _hook_proc(nCode, wParam, lParam):
            if nCode >= 0 and wParam in (WM_KEYDOWN, WM_SYSKEYDOWN):
                self.on_press_callback()
            return user32.CallNextHookEx(self.hook, nCode, wParam, lParam)

        self._c_proc = HOOKPROC(_hook_proc)
        self.hook = user32.SetWindowsHookExW(WH_KEYBOARD_LL, self._c_proc, kernel32.GetModuleHandleW(None), 0)
        msg = wintypes.MSG()
        while self.running and user32.GetMessageW(ctypes.byref(msg), None, 0, 0) != 0:
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))
        if self.hook:
            user32.UnhookWindowsHookEx(self.hook)
            self.hook = None

    def stop(self):
        self.running = False
        if self.thread and self.thread.is_alive():
            if HAS_CTYPES and sys.platform == "win32":
                user32.PostThreadMessageW(self.thread.ident, 0x0012, 0, 0)

class PureBassEngine:
    def __init__(self, sample_rate=48000):
        self.sample_rate = sample_rate
        self.prev_bass = 0.0
        self.rolling_floor = 0.002
        self.recent_peak = 0.03
        self.last_beat_time = 0.0
        self.smooth_val = 0.0

    def process(self, samples_int16: np.ndarray, decay: float = 0.82) -> float:
        samples_f = samples_int16.astype(np.float32) / 32768.0
        n = len(samples_f)
        if n < 64: return 0.0

        windowed = samples_f * np.hanning(n)
        mag = np.abs(np.fft.rfft(windowed)) / (n / 2.0)
        freqs = np.fft.rfftfreq(n, 1.0 / self.sample_rate)

        sub_mask = (freqs >= 35.0) & (freqs <= 110.0)
        mid_mask = (freqs >= 300.0) & (freqs <= 3000.0)

        sub_energy = float(np.sum(mag[sub_mask])) if np.any(sub_mask) else 0.0
        mid_energy = float(np.sum(mag[mid_mask])) if np.any(mid_mask) else 0.0

        if mid_energy > sub_energy * 1.5: sub_energy *= 0.1

        self.rolling_floor = self.rolling_floor * 0.95 + sub_energy * 0.05
        self.recent_peak = max(sub_energy, self.recent_peak * 0.985)
        if self.recent_peak < 0.01: self.recent_peak = 0.01

        delta = sub_energy - self.prev_bass
        self.prev_bass = sub_energy

        now = time.perf_counter()
        threshold = max(0.003, self.rolling_floor * 1.30)

        if delta > threshold and sub_energy > 0.004 and (now - self.last_beat_time) > 0.075:
            self.last_beat_time = now
            flash = float(np.clip(sub_energy / self.recent_peak, 0.45, 1.0))
            if flash > self.smooth_val: self.smooth_val = flash

        self.smooth_val *= decay
        return float(np.clip(self.smooth_val, 0.0, 1.0))

class CaseFanVisualizer:
    def __init__(self):
        self.vu_level = 0.0
        self.peak_led = 0.0
        self.peak_hold_until = 0.0
        self.angle = 0.0
        self.ripple_phase = 0.0
        self.last_time = time.perf_counter()
        self.band_levels = [0.0, 0.0, 0.0]
        self.band_peaks = [0.0, 0.0, 0.0]
        self.band_holds = [0.0, 0.0, 0.0]

    def render_fan_ring(self, num_leds, raw_level=0.0, energy=0.0, pulse=0.0, mode="vu_meter",
                        theme="classic", clockwise=True, speed_mult=1.0, fan_idx=0, total_fans=1,
                        spectrum=None, decay_rate=0.85, custom_color=None):
        now = time.perf_counter()
        dt = min(now - self.last_time, 0.08)
        self.last_time = now
        num_leds = max(4, num_leds)

        # Base RGB Color resolution
        if theme == "classic":
            base_rgb = None
        elif theme in ("custom", "Custom Spectrum Color..."):
            base_rgb = custom_color or (0, 230, 255)
        elif theme == "red":
            base_rgb = (224, 27, 34)       # Nothing Red
        elif theme == "white":
            base_rgb = (255, 255, 255)     # Glyph White
        elif theme == "cyan":
            base_rgb = (0, 230, 255)       # Cyber Cyan
        elif theme == "magenta":
            base_rgb = (255, 0, 140)       # Neon Magenta
        elif theme == "purple":
            base_rgb = (157, 0, 255)       # Electric Purple
        elif theme == "lime":
            base_rgb = (0, 255, 102)       # Acid Lime
        elif theme == "orange":
            base_rgb = (255, 102, 0)       # Solar Orange
        elif theme == "gold":
            base_rgb = (255, 215, 0)       # Gold Rush
        elif theme == "ice_blue":
            base_rgb = (0, 119, 255)       # Ice Blue
        elif theme == "coral":
            base_rgb = (255, 112, 112)     # Sunset Coral
        elif theme == "mint":
            base_rgb = (0, 255, 179)       # Mint Green
        elif theme == "violet":
            base_rgb = (120, 0, 255)       # Deep Violet
        elif theme == "rainbow":
            hue = (now * 0.35 + fan_idx * (1.0 / max(1, total_fans)) + pulse * 0.15) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.95, 1.0)
            base_rgb = (int(r * 255), int(g * 255), int(b * 255))
        elif isinstance(theme, (tuple, list)) and len(theme) == 3:
            base_rgb = tuple(int(c) for c in theme)
        else:
            base_rgb = custom_color or (224, 27, 34)

        def get_meter_color(frac):
            if theme == "classic" or base_rgb is None:
                if frac < 0.60:
                    return (0, 240, 45)   # Lime Green
                elif frac < 0.85:
                    return (255, 210, 0)  # Amber Yellow
                else:
                    return (255, 25, 25)  # Burning Red
            else:
                if frac < 0.65:
                    return (int(base_rgb[0] * 0.7), int(base_rgb[1] * 0.7), int(base_rgb[2] * 0.7))
                elif frac < 0.88:
                    return base_rgb
                else:
                    blend = (frac - 0.88) / 0.12
                    return (int(base_rgb[0] * (1 - blend) + 255 * blend),
                            int(base_rgb[1] * (1 - blend) + 255 * blend),
                            int(base_rgb[2] * (1 - blend) + 255 * blend))

        colors = []

        if mode == "spinner":
            # Circular rotating comet beam (Nothing Glyph Ring)
            speed = (2.5 + energy * 9.0 + pulse * 7.0) * speed_mult
            dir_mult = 1.0 if clockwise else -1.0
            self.angle = (self.angle + dir_mult * speed * dt) % (2.0 * math.pi)
            fan_angle = (self.angle + fan_idx * (0.5 * math.pi)) % (2.0 * math.pi)

            tail_len = math.pi * 1.2
            s_rgb = base_rgb or (224, 27, 34)
            for i in range(num_leds):
                phi = (2.0 * math.pi * i) / num_leds
                diff = (fan_angle - phi) % (2.0 * math.pi) if clockwise else (phi - fan_angle) % (2.0 * math.pi)
                brightness = (1.0 - diff / tail_len) ** 1.6 if diff <= tail_len else 0.0
                brightness = float(np.clip(brightness + pulse * 0.45, 0.0, 1.0))
                colors.append((int(s_rgb[0] * brightness),
                               int(s_rgb[1] * brightness),
                               int(s_rgb[2] * brightness)))

        elif mode == "vu_meter":
            # Radial VU Meter (Full 360-degree ring with ballistic physics & gravity peak)
            target = float(np.clip(raw_level, 0.0, 1.0))
            if target > self.vu_level:
                self.vu_level = self.vu_level * 0.25 + target * 0.75  # Instant attack
            else:
                d = float(np.clip(decay_rate, 0.75, 0.98))
                self.vu_level = max(0.0, self.vu_level * d)          # Smooth ballistic decay

            active_count = int(round(self.vu_level * num_leds))
            if active_count >= self.peak_led:
                self.peak_led = float(active_count)
                self.peak_hold_until = now + 0.25  # Hold peak for 250ms
            elif now > self.peak_hold_until:
                self.peak_led = max(0.0, self.peak_led - 14.0 * dt)  # Gravity fall

            peak_idx = int(self.peak_led)

            for i in range(num_leds):
                fill_idx = i if clockwise else ((num_leds - i) % num_leds)
                if fill_idx < active_count:
                    frac = fill_idx / max(1, num_leds - 1)
                    colors.append(get_meter_color(frac))
                elif fill_idx == peak_idx and peak_idx > 0:
                    colors.append((255, 255, 255))  # Pure white peak hold dot
                else:
                    colors.append((0, 0, 0))

        elif mode == "vu_meter_dual":
            # Radial VU (Dual Symmetrical): rises symmetrically from bottom to top on both sides
            target = float(np.clip(raw_level, 0.0, 1.0))
            if target > self.vu_level:
                self.vu_level = self.vu_level * 0.25 + target * 0.75
            else:
                d = float(np.clip(decay_rate, 0.75, 0.98))
                self.vu_level = max(0.0, self.vu_level * d)

            half = max(2, num_leds // 2)
            active_half = int(round(self.vu_level * half))
            if active_half >= self.peak_led:
                self.peak_led = float(active_half)
                self.peak_hold_until = now + 0.25
            elif now > self.peak_hold_until:
                self.peak_led = max(0.0, self.peak_led - 10.0 * dt)
            peak_half = int(self.peak_led)

            for i in range(num_leds):
                pos = i if i < half else (num_leds - 1 - i)
                if pos < active_half:
                    frac = pos / max(1, half - 1)
                    colors.append(get_meter_color(frac))
                elif pos == peak_half and peak_half > 0:
                    colors.append((255, 255, 255))
                else:
                    colors.append((0, 0, 0))

        elif mode == "spectrum":
            # Multi-Fan Frequency Equalizer
            if spectrum is not None and len(spectrum) >= 32:
                b_raw = float(np.mean(spectrum[:6])) * 3.5
                m_raw = float(np.mean(spectrum[8:28])) * 4.0
                h_raw = float(np.mean(spectrum[32:58])) * 4.5
            else:
                b_raw = pulse
                m_raw = raw_level
                h_raw = raw_level * 0.8

            band_targets = [
                float(np.clip(b_raw, 0.0, 1.0)),
                float(np.clip(m_raw, 0.0, 1.0)),
                float(np.clip(h_raw, 0.0, 1.0))
            ]

            band_colors = [
                (255, 30, 30),   # Bass: Crimson Red
                (255, 170, 0),   # Mids: Golden Amber
                (0, 230, 255)    # Treble: Electric Cyan
            ]

            if total_fans > 1:
                b_idx = fan_idx % 3
                tgt = band_targets[b_idx]
                if tgt > self.band_levels[b_idx]:
                    self.band_levels[b_idx] = self.band_levels[b_idx] * 0.25 + tgt * 0.75
                else:
                    self.band_levels[b_idx] = max(0.0, self.band_levels[b_idx] * 0.85)

                b_active = int(round(self.band_levels[b_idx] * num_leds))
                if b_active >= self.band_peaks[b_idx]:
                    self.band_peaks[b_idx] = float(b_active)
                    self.band_holds[b_idx] = now + 0.25
                elif now > self.band_holds[b_idx]:
                    self.band_peaks[b_idx] = max(0.0, self.band_peaks[b_idx] - 12.0 * dt)
                b_peak = int(self.band_peaks[b_idx])

                b_col = band_colors[b_idx] if (theme == "classic" or base_rgb is None) else base_rgb
                for i in range(num_leds):
                    fill_idx = i if clockwise else ((num_leds - i) % num_leds)
                    if fill_idx < b_active:
                        frac = fill_idx / max(1, num_leds - 1)
                        if theme == "classic" or base_rgb is None:
                            colors.append(b_col)
                        else:
                            colors.append(get_meter_color(frac))
                    elif fill_idx == b_peak and b_peak > 0:
                        colors.append((255, 255, 255))
                    else:
                        colors.append((0, 0, 0))
            else:
                # Single fan divided into 3 equal equalizer sectors
                sector_size = num_leds // 3
                for i in range(num_leds):
                    s_idx = min(2, i // max(1, sector_size))
                    offset_in_sector = i - s_idx * sector_size
                    sec_len = sector_size if s_idx < 2 else (num_leds - 2 * sector_size)
                    b_active = int(round(band_targets[s_idx] * sec_len))
                    if offset_in_sector < b_active:
                        b_col = band_colors[s_idx] if (theme == "classic" or base_rgb is None) else base_rgb
                        colors.append(b_col)
                    else:
                        colors.append((0, 0, 0))

        elif mode == "ripple":
            self.ripple_phase = (self.ripple_phase + (3.0 + pulse * 6.0) * dt) % (total_fans + 1)
            dist = abs(self.ripple_phase - fan_idx)
            wave_int = max(float(np.clip(1.0 - dist, 0.0, 1.0)) ** 2.0, pulse * 0.3)
            r_rgb = base_rgb or (224, 27, 34)
            for i in range(num_leds):
                colors.append((int(r_rgb[0] * wave_int),
                               int(r_rgb[1] * wave_int),
                               int(r_rgb[2] * wave_int)))

        else:  # "pulse" (Bass Strobe)
            p = float(np.clip(pulse * 1.2, 0.0, 1.0))
            p_rgb = base_rgb or (224, 27, 34)
            for i in range(num_leds):
                colors.append((int(p_rgb[0] * p),
                               int(p_rgb[1] * p),
                               int(p_rgb[2] * p)))

        return colors

THEME_PRESET_COLORS = {
    "Classic VU (Green-Yellow-Red)": (0, 240, 45),
    "Nothing Red": (224, 27, 34),
    "Glyph White": (255, 255, 255),
    "Cyber Cyan": (0, 230, 255),
    "Neon Magenta": (255, 0, 140),
    "Electric Purple": (157, 0, 255),
    "Acid Lime": (0, 255, 102),
    "Solar Orange": (255, 102, 0),
    "Gold Rush": (255, 215, 0),
    "Ice Blue": (0, 119, 255),
    "Sunset Coral": (255, 112, 112),
    "Mint Green": (0, 255, 179),
    "Deep Violet": (120, 0, 255),
    "Reactive Rainbow": (255, 0, 0),
    "Custom Spectrum Color...": (0, 230, 255)
}

FAN_THEME_DISPLAY = {
    "Classic VU (Green-Yellow-Red)": "classic",
    "Nothing Red": "red",
    "Glyph White": "white",
    "Cyber Cyan": "cyan",
    "Neon Magenta": "magenta",
    "Electric Purple": "purple",
    "Acid Lime": "lime",
    "Solar Orange": "orange",
    "Gold Rush": "gold",
    "Ice Blue": "ice_blue",
    "Sunset Coral": "coral",
    "Mint Green": "mint",
    "Deep Violet": "violet",
    "Reactive Rainbow": "rainbow",
    "Custom Spectrum Color...": "custom"
}

FAN_MODE_DISPLAY = {
    "Radial VU Meter (Full Ring)": "vu_meter",
    "Radial VU (Dual Symmetrical)": "vu_meter_dual",
    "Spinner (Glyph Ring)": "spinner",
    "Multi-Fan Spectrum (EQ)": "spectrum",
    "Wave Ripple": "ripple",
    "Bass Strobe": "pulse"
}

FAN_LEDS_DISPLAY = {
    "8 LEDs": 8,
    "12 LEDs": 12,
    "16 LEDs (Standard)": 16,
    "24 LEDs": 24,
    "32 LEDs": 32
}

FAN_COUNT_DISPLAY = {
    "1 Fan": 1,
    "2 Fans": 2,
    "3 Fans (Standard Case)": 3,
    "4 Fans": 4,
    "5 Fans": 5,
    "6 Fans": 6
}

class OpenRGBManager:
    def __init__(self, logger=None):
        self.logger = logger
        self.client = None
        self.connected = False
        self.devices = []
        self.last_sync_time = 0.0
        self.fan_visualizers = {}
        self.selected_rgb = (224, 27, 34)
        self.header_configs = {}
        self.on_connected_callback = None

    @property
    def fan_visualizer(self):
        return self.get_visualizer("__default__")

    def update_fan_config(self, ring_size=16, num_fans=1):
        if not self.connected: return
        needed = ring_size * num_fans
        for dev in self.devices:
            for z in getattr(dev, "zones", []):
                if self.is_fan_zone(z, dev):
                    if len(z.leds) < needed:
                        try:
                            z.resize(needed)
                            if self.logger:
                                self.logger(f"OpenRGB: Resized ARGB zone '{z.name}' to {needed} LEDs.")
                        except Exception as ex:
                            if self.logger:
                                self.logger(f"OpenRGB: Could not resize '{z.name}': {ex}")

    def get_visualizer(self, key):
        if key not in self.fan_visualizers:
            self.fan_visualizers[key] = CaseFanVisualizer()
        return self.fan_visualizers[key]

    def is_fan_zone(self, zone, dev=None):
        zname = (getattr(zone, "name", "") or "").lower()
        dev_type = getattr(dev, "type", None) if dev else None

        # 1. Strictly exclude non-addressable 12V analog RGB headers and motherboard SMD accent LEDs
        non_fan_keywords = ("jrgb", "12v", "onboard", "audio", "pcie", "io_cover", "chipset", "pch", "logo")
        if any(ex in zname for ex in non_fan_keywords):
            return False

        # If zone is 1-LED single zone (ZoneType.SINGLE = 0), it cannot display individual fan ring effects
        if getattr(zone, "type", None) == 0 and len(getattr(zone, "leds", [])) <= 1:
            return False

        # 2. If parent device is a dedicated cooler/fan controller or LED strip (Corsair, Razer, Lian Li, NZXT, etc.)
        if dev_type in (DeviceType.COOLER, DeviceType.CASE, DeviceType.LEDSTRIP, DeviceType.ACCESSORY, DeviceType.DRAM):
            return True

        # 3. Motherboard Addressable RGB fan/strip headers (MSI JRAINBOW, ASUS ADD_HEADER, Gigabyte D_LED, ASRock)
        fan_keywords = (
            "rainbow", "jrainbow", "d_led", "add_header", "add_gen2", "addr_led",
            "addressable", "argb", "polychrome addressable", "fan", "cooler", "pump",
            "aio", "commander", "unifan", "hub", "node"
        )
        if any(k in zname for k in fan_keywords):
            return True

        return False

    def is_fan_device(self, dev):
        name = (getattr(dev, "name", "") or "").lower()
        dev_type = getattr(dev, "type", None)
        if dev_type in (DeviceType.COOLER, DeviceType.CASE, DeviceType.LEDSTRIP, DeviceType.ACCESSORY, DeviceType.DRAM):
            return True
        for z in getattr(dev, "zones", []):
            if self.is_fan_zone(z, None):
                return True
        return False

    def get_detected_headers(self):
        if not self.connected:
            return ["No OpenRGB Connected"]

        connected_rgb = []
        for dev in self.devices:
            d_name = dev.name
            d_type = getattr(dev, "type", None)
            d_short = d_name.split()[0]
            zones = getattr(dev, "zones", [])

            # 1. Motherboards: only include real addressable ARGB headers (e.g. JRAINBOW, ADD_HEADER, D_LED)
            if d_type == DeviceType.MOTHERBOARD:
                mfg = "MSI" if "msi" in d_name.lower() else (
                    "ASUS" if "asus" in d_name.lower() else (
                        "Gigabyte" if "gigabyte" in d_name.lower() or "aorus" in d_name.lower() else (
                            "ASRock" if "asrock" in d_name.lower() else "Motherboard"
                        )
                    )
                )
                for z in zones:
                    if self.is_fan_zone(z, dev):
                        connected_rgb.append(f"{z.name} ({mfg} ARGB)")

            # 2. Dedicated Fan Controllers / Hubs (Corsair, Razer, Lian Li, NZXT, etc.)
            elif d_type in (DeviceType.COOLER, DeviceType.CASE, DeviceType.LEDSTRIP, DeviceType.ACCESSORY):
                for z in zones:
                    if self.is_fan_zone(z, dev):
                        connected_rgb.append(f"{d_short}: {z.name} (ARGB)")

            # 3. RAM Modules
            elif d_type == DeviceType.DRAM:
                connected_rgb.append(f"RAM: {d_name} (DRAM)")

            # 4. GPUs with addressable RGB
            elif d_type == DeviceType.GPU:
                for z in zones:
                    if self.is_fan_zone(z, dev):
                        connected_rgb.append(f"GPU: {d_short} {z.name} (ARGB)")

            # 5. Any other device with detected fan zones
            else:
                for z in zones:
                    if self.is_fan_zone(z, dev):
                        connected_rgb.append(f"{d_short}: {z.name} (ARGB)")

        if not connected_rgb:
            return ["No ARGB Devices Connected"]

        if len(connected_rgb) > 1:
            return ["All Connected ARGB (Sync All)"] + connected_rgb
        return connected_rgb

    def resolve_ring_size(self, total_leds, user_setting):
        if isinstance(user_setting, int) and user_setting in (8, 12, 16, 20, 24, 32):
            return user_setting
        for candidate in (16, 12, 8, 24, 32, 20):
            if total_leds % candidate == 0:
                return candidate
        if total_leds <= 32:
            return total_leds
        return 16

    def get_zone_config(self, zone, dev, default_cfg):
        zname = zone.name.lower() if hasattr(zone, "name") else ""
        dname = (dev.name.lower() if dev and hasattr(dev, "name") else "")
        for k, cfg in self.header_configs.items():
            if "sync all" in k.lower():
                continue
            k_clean = k.split()[0].lower()
            if k_clean and (k_clean in zname or (dname and k_clean in dname)):
                return cfg
        for k in self.header_configs:
            if "sync all" in k.lower():
                return self.header_configs[k]
        return default_cfg

    def update_header_configs(self, configs):
        self.header_configs = configs
        if not self.connected:
            return
        for dev in self.devices:
            for z in getattr(dev, "zones", []):
                if self.is_fan_zone(z, dev):
                    cfg = self.get_zone_config(z, dev, {})
                    ring_size = cfg.get("fan_ring_size", 16)
                    fan_count = cfg.get("fan_count", 1)
                    needed = ring_size * fan_count
                    if len(z.leds) < needed:
                        try:
                            z.resize(needed)
                            if self.logger:
                                self.logger(f"OpenRGB: Resized ARGB zone '{z.name}' to {needed} LEDs.")
                        except Exception as ex:
                            if self.logger:
                                self.logger(f"OpenRGB: Could not resize '{z.name}': {ex}")

    def connect(self):
        if not OPENRGB_AVAILABLE: return False
        try:
            self.client = OpenRGBClient("localhost", OPENRGB_PORT)
            self.devices = self.client.devices
            for dev in self.devices:
                for mode in dev.modes:
                    if mode.name.lower() in ("direct", "custom", "static"):
                        dev.set_mode(mode)
                        break
            self.connected = True
            self.update_header_configs(self.header_configs)
            detected = self.get_detected_headers()
            if self.on_connected_callback:
                try:
                    self.on_connected_callback(detected)
                except Exception as ex:
                    if self.logger: self.logger(f"Header callback error: {ex}")
            fan_count = sum(1 for d in self.devices if self.is_fan_device(d))
            if self.logger:
                self.logger(f"OpenRGB: Connected to {len(self.devices)} devices ({fan_count} fan/cooler controllers).")
            return True
        except Exception as e:
            self.connected = False
            if self.logger: self.logger(f"OpenRGB Error: {e}")
            return False

    def sync(self, r, g, b, raw_audio_level=0.0, energy=0.0, pulse=0.0, spectrum=None,
             fan_viz_enabled=True, fan_mode="vu_meter", fan_theme="classic",
             fan_clockwise=True, fan_speed=1.0, fan_leds=16, fan_count=1, decay_rate=0.85,
             custom_color=(0, 230, 255)):
        if not self.connected: return None
        now = time.perf_counter()
        if now - self.last_sync_time < 0.02: return None

        ambient_color = RGBColor(int(r * 255), int(g * 255), int(b * 255))
        previews = {}
        global_fan_idx = 0

        default_cfg = {
            "mode": fan_mode,
            "theme": fan_theme,
            "clockwise": fan_clockwise,
            "speed": fan_speed,
            "fan_ring_size": fan_leds,
            "fan_count": fan_count,
            "custom_color": custom_color,
            "enabled": fan_viz_enabled
        }

        for dev in self.devices:
            try:
                num_leds = len(dev.leds) if hasattr(dev, "leds") else 0
                if num_leds == 0: continue

                zones = getattr(dev, "zones", [])
                has_fan_zone = any(self.is_fan_zone(z, dev) for z in zones)

                if has_fan_zone and fan_viz_enabled:
                    dev_colors = []
                    for z in zones:
                        z_len = len(z.leds) if hasattr(z, "leds") else 0
                        if z_len == 0: continue
                        if self.is_fan_zone(z, dev):
                            cfg = self.get_zone_config(z, dev, default_cfg)
                            if not cfg.get("enabled", True):
                                dev_colors.extend([ambient_color] * z_len)
                                continue

                            z_ring_size = self.resolve_ring_size(z_len, cfg.get("fan_ring_size", fan_leds))
                            num_fans_in_zone = max(1, z_len // z_ring_size)
                            viz = self.get_visualizer(z.name)

                            for f in range(num_fans_in_zone):
                                fan_idx = global_fan_idx + f
                                fan_colors = viz.render_fan_ring(
                                    num_leds=z_ring_size,
                                    raw_level=raw_audio_level,
                                    energy=energy,
                                    pulse=pulse,
                                    mode=cfg.get("mode", fan_mode),
                                    theme=cfg.get("theme", fan_theme),
                                    clockwise=cfg.get("clockwise", fan_clockwise),
                                    speed_mult=cfg.get("speed", fan_speed),
                                    fan_idx=fan_idx,
                                    total_fans=num_fans_in_zone,
                                    spectrum=spectrum,
                                    decay_rate=decay_rate,
                                    custom_color=cfg.get("custom_color", custom_color)
                                )
                                if f == 0:
                                    previews[z.name] = fan_colors
                                    if "__all__" not in previews:
                                        previews["__all__"] = fan_colors
                                for cr, cg, cb in fan_colors:
                                    dev_colors.append(RGBColor(cr, cg, cb))

                            extra = z_len % z_ring_size
                            if extra > 0:
                                dev_colors.extend([ambient_color] * extra)
                            global_fan_idx += num_fans_in_zone
                        else:
                            cfg = self.get_zone_config(z, dev, {})
                            if cfg.get("theme") in ("custom", "Custom Spectrum Color...") and "custom_color" in cfg:
                                cr, cg, cb = cfg["custom_color"]
                                zone_col = RGBColor(int(cr * pulse), int(cg * pulse), int(cb * pulse))
                            else:
                                zone_col = ambient_color
                            dev_colors.extend([zone_col] * z_len)

                    if len(dev_colors) < num_leds:
                        dev_colors.extend([ambient_color] * (num_leds - len(dev_colors)))
                    dev.set_colors(dev_colors[:num_leds], fast=True)

                elif self.is_fan_device(dev) and fan_viz_enabled:
                    cfg = default_cfg
                    ring_size = self.resolve_ring_size(num_leds, cfg.get("fan_ring_size", fan_leds))
                    num_fans = max(1, num_leds // ring_size)
                    dev_colors = []
                    viz = self.get_visualizer(dev.name)
                    for f in range(num_fans):
                        fan_idx = global_fan_idx + f
                        fan_colors = viz.render_fan_ring(
                            num_leds=ring_size,
                            raw_level=raw_audio_level,
                            energy=energy,
                            pulse=pulse,
                            mode=cfg.get("mode", fan_mode),
                            theme=cfg.get("theme", fan_theme),
                            clockwise=cfg.get("clockwise", fan_clockwise),
                            speed_mult=cfg.get("speed", fan_speed),
                            fan_idx=fan_idx,
                            total_fans=num_fans,
                            spectrum=spectrum,
                            decay_rate=decay_rate,
                            custom_color=cfg.get("custom_color", custom_color)
                        )
                        if f == 0 and "__all__" not in previews:
                            previews["__all__"] = fan_colors
                        for cr, cg, cb in fan_colors:
                            dev_colors.append(RGBColor(cr, cg, cb))
                    if len(dev_colors) < num_leds:
                        dev_colors.extend([ambient_color] * (num_leds - len(dev_colors)))
                    dev.set_colors(dev_colors[:num_leds], fast=True)
                    global_fan_idx += num_fans
                else:
                    dev.set_color(ambient_color)
            except Exception:
                pass

        self.last_sync_time = now
        return previews

    def stop(self):
        if not self.connected: return
        black = RGBColor(0, 0, 0)
        for dev in self.devices:
            try:
                if hasattr(dev, "leds") and len(dev.leds) > 0:
                    dev.set_colors([black] * len(dev.leds), fast=True)
                else:
                    dev.set_color(black)
            except:
                try: dev.set_color(black)
                except: pass

def get_broadcast_addresses():
    broadcasts = {'255.255.255.255'}
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = info[4][0]
            if ip != '127.0.0.1':
                parts = ip.split('.')
                broadcasts.add(".".join(parts[:-1] + ["255"]))
    except Exception: pass
    return [b for b in broadcasts]

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def get_wasapi_devices():
    p = pyaudio.PyAudio()
    devices = []
    try:
        wasapi_info = p.get_host_api_info_by_type(pyaudio.paWASAPI)
        default_idx = wasapi_info["defaultOutputDevice"]
        default_speakers = p.get_device_info_by_index(default_idx)
        
        default_loopback_idx = -1
        if not default_speakers["isLoopbackDevice"]:
            for loopback in p.get_loopback_device_info_generator():
                if default_speakers["name"] in loopback["name"]:
                    default_loopback_idx = loopback["index"]
                    break
        else: default_loopback_idx = default_speakers["index"]

        for loopback in p.get_loopback_device_info_generator():
            devices.append({
                "index": loopback["index"],
                "name": loopback["name"],
                "is_default": (loopback["index"] == default_loopback_idx),
                "rate": int(loopback["defaultSampleRate"]),
                "channels": int(loopback["maxInputChannels"])
            })
    except Exception: pass
    finally: p.terminate()
    return devices

def discover_phone_bt():
    try:
        from bleak import BleakScanner
    except Exception: return []
    async def scan():
        devices = []
        def detection_callback(device, advertisement_data):
            uuids = [s.lower() for s in advertisement_data.service_uuids]
            target = BLE_SERVICE_UUID.lower()
            is_match = target in uuids
            for i, (addr, name, _) in enumerate(devices):
                if addr == device.address:
                    if name == "Unknown" and device.name:
                        devices[i] = (device.address, device.name, is_match)
                    return
            devices.append((device.address, device.name or "Unknown", is_match))
        scanner = BleakScanner(detection_callback)
        await scanner.start()
        for _ in range(12): await asyncio.sleep(1.0)
        await scanner.stop()
        return devices
    try: return asyncio.run(scan())
    except: return []

class CompanionApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("GLYPHIX")
        self.geometry("600x720")
        self.resizable(True, True)
        self.configure(fg_color=COLOR_BG)
        
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        self.is_streaming = False
        self.is_discovering = False
        self.show_advanced = ctk.BooleanVar(value=False)
        self.stop_stream_event = threading.Event()
        self.stop_discovery_event = threading.Event()
        
        self.direction = ctk.StringVar(value="PHONE_TO_PC")
        self.local_pc_ip = get_local_ip()
        self.conn_type = ctk.StringVar(value="UDP")
        self.use_openrgb = ctk.BooleanVar(value=False)
        self.typing_suppression = ctk.BooleanVar(value=True)
        self.rgb_sensitivity = ctk.DoubleVar(value=1.0)
        self.rgb_decay = ctk.DoubleVar(value=0.82)
        self.selected_rgb = (215, 25, 32)
        self.viz_dots = []
        self.viz_queue = queue.Queue(maxsize=1)
        self._viz_cache = []
        
        self.wasapi_devices = get_wasapi_devices()
        self.spectrum_points = [0.0] * 64
        self.log_queue = queue.Queue()
        self.level_queue = queue.Queue()
        
        # Case Fan Visualization & Per-Header Configuration
        self.current_header = ctk.StringVar(value="All Connected ARGB (Sync All)")
        self.detected_headers = ["All Connected ARGB (Sync All)"]
        self.custom_hex = "#00E6FF"
        self.custom_rgb = (0, 230, 255)
        self.header_configs = {
            "All Connected ARGB (Sync All)": {
                "mode": "Radial VU Meter (Full Ring)",
                "theme": "Classic VU (Green-Yellow-Red)",
                "custom_color": (0, 230, 255),
                "custom_hex": "#00E6FF",
                "fan_ring_size": 16,
                "fan_count": 1,
                "clockwise": True,
                "speed": 1.0,
                "enabled": True,
                "led_count_str": "16 LEDs (Standard)",
                "fan_count_str": "1 Fan"
            }
        }
        self.fan_viz_enabled = ctk.BooleanVar(value=True)
        self.fan_mode_str = ctk.StringVar(value="Radial VU Meter (Full Ring)")
        self.fan_theme_str = ctk.StringVar(value="Classic VU (Green-Yellow-Red)")
        self.fan_led_count_str = ctk.StringVar(value="16 LEDs (Standard)")
        self.fan_count_str = ctk.StringVar(value="1 Fan")
        self.fan_clockwise = ctk.BooleanVar(value=True)
        self.fan_speed = ctk.DoubleVar(value=1.0)
        self.fan_preview_queue = queue.Queue(maxsize=1)
        self.fan_preview_dots = []
        self._fan_preview_cache = []

        self.rgb_manager = OpenRGBManager(logger=self.log)
        self.rgb_manager.on_connected_callback = lambda hdrs: self.after(0, self._on_openrgb_headers_detected, hdrs)
        self.hook_watcher = KeyboardHookWatcher(self._on_key_press)
        self.last_key_time = 0.0

        self._setup_ui()
        self._refresh_audio_sources()
        self._on_direction_changed()
        self._update_loop()

    def _setup_ui(self):
        self.header = ctk.CTkFrame(self, fg_color=COLOR_BG, corner_radius=0, height=70)
        self.header.grid(row=0, column=0, sticky="ew", padx=20, pady=(10, 0))
        
        self.logo_label = ctk.CTkLabel(self.header, text="GLYPHIX", 
                                      font=ctk.CTkFont(family=FONT_LOGO, size=28, weight="bold"),
                                      text_color=COLOR_ACCENT)
        self.logo_label.pack(side="left", pady=15)
        
        self.status_frame = ctk.CTkFrame(self.header, fg_color="transparent")
        self.status_frame.pack(side="right", pady=18)
        
        self.status_dot = ctk.CTkLabel(self.status_frame, text="●", font=ctk.CTkFont(size=14), text_color=COLOR_MUTED)
        self.status_dot.pack(side="left", padx=(0, 5))
        
        self.status_pill = ctk.CTkLabel(self.status_frame, text="DISCONNECTED", 
                                       text_color=COLOR_MUTED,
                                       font=ctk.CTkFont(family=FONT_MAIN, size=11, weight="bold"))
        self.status_pill.pack(side="left")

        self.main_container = ctk.CTkScrollableFrame(self, fg_color=COLOR_BG, corner_radius=0)
        self.main_container.grid(row=1, column=0, sticky="nsew", padx=5)

        self._create_card(self.main_container, "SYNC DIRECTION")
        dir_frame = ctk.CTkFrame(self.last_card, fg_color="transparent")
        dir_frame.pack(fill="x", padx=20, pady=(5, 12))
        
        ctk.CTkRadioButton(dir_frame, text="Phone -> PC (Sync PC RGB to Phone Music)", 
                           variable=self.direction, value="PHONE_TO_PC",
                           command=self._on_direction_changed,
                           hover_color=COLOR_ACCENT, fg_color=COLOR_ACCENT, 
                           font=ctk.CTkFont(family=FONT_MAIN, size=12, weight="bold")).pack(anchor="w", pady=(0, 6))
        ctk.CTkRadioButton(dir_frame, text="PC -> Phone (Stream PC Audio to Phone Glyphs)", 
                           variable=self.direction, value="PC_TO_PHONE",
                           command=self._on_direction_changed,
                           hover_color=COLOR_ACCENT, fg_color=COLOR_ACCENT, 
                           font=ctk.CTkFont(family=FONT_MAIN, size=12, weight="bold")).pack(anchor="w")

        self._create_card(self.main_container, "AUDIO SOURCE")
        self.audio_combo = ctk.CTkOptionMenu(self.last_card, values=[], 
                                            fg_color=COLOR_BORDER, button_color=COLOR_BORDER,
                                            button_hover_color=COLOR_ACCENT, dropdown_fg_color=COLOR_CARD,
                                            font=ctk.CTkFont(family=FONT_MAIN, size=12))
        self.audio_combo.pack(fill="x", padx=20, pady=(5, 15))

        self.viz_canvas = ctk.CTkCanvas(self.last_card, height=100, bg=COLOR_CARD, highlightthickness=0)
        self.viz_canvas.pack(fill="x", padx=20, pady=(0, 20))
        self.viz_canvas.bind("<Configure>", self._resize_viz)
        
        self._create_card(self.main_container, "CONNECTIVITY")

        # PC IP Frame for Phone -> PC
        self.pc_ip_frame = ctk.CTkFrame(self.last_card, fg_color=COLOR_BG, corner_radius=8, border_color=COLOR_BORDER, border_width=1)
        self.pc_ip_label = ctk.CTkLabel(self.pc_ip_frame, text=f"PC IP: {self.local_pc_ip}:12347",
                                        font=ctk.CTkFont(family=FONT_MAIN, size=13, weight="bold"),
                                        text_color=COLOR_TEXT)
        self.pc_ip_label.pack(side="left", padx=15, pady=10)
        self.copy_ip_btn = ctk.CTkButton(self.pc_ip_frame, text="COPY IP", width=80, height=28,
                                         fg_color=COLOR_BORDER, hover_color=COLOR_ACCENT,
                                         font=ctk.CTkFont(family=FONT_MAIN, size=11, weight="bold"),
                                         command=self._copy_pc_ip)
        self.copy_ip_btn.pack(side="right", padx=10, pady=10)
        
        self.conn_switch_frame = ctk.CTkFrame(self.last_card, fg_color="transparent")
        self.conn_switch_frame.pack(fill="x", padx=20, pady=(5, 10))
        
        ctk.CTkRadioButton(self.conn_switch_frame, text="UDP (Wi-Fi)", variable=self.conn_type, value="UDP",
                           hover_color=COLOR_ACCENT, fg_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12)).pack(side="left", padx=(0, 20))
        ctk.CTkRadioButton(self.conn_switch_frame, text="Bluetooth", variable=self.conn_type, value="BT",
                           hover_color=COLOR_ACCENT, fg_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12)).pack(side="left")
        
        self.addr_frame = ctk.CTkFrame(self.last_card, fg_color="transparent")
        self.addr_frame.pack(fill="x", padx=20, pady=(0, 20))
        
        self.addr_entry = ctk.CTkEntry(self.addr_frame, placeholder_text="Phone IP or MAC Address",
                                      fg_color=COLOR_BG, border_color=COLOR_BORDER, height=36,
                                      font=ctk.CTkFont(family=FONT_MAIN, size=12))
        self.addr_entry.pack(side="left", fill="x", expand=True, padx=(0, 10))
        
        self.discover_btn = ctk.CTkButton(self.addr_frame, text="DISCOVER", width=90, height=36,
                                         fg_color=COLOR_WHITE, text_color=COLOR_BG, hover_color=COLOR_ACCENT,
                                         font=ctk.CTkFont(family=FONT_MAIN, size=12, weight="bold"), 
                                         command=self._toggle_discovery)
        self.discover_btn.pack(side="right")

        self._create_card(self.main_container, "HARDWARE SYNC")
        
        hw_grid = ctk.CTkFrame(self.last_card, fg_color="transparent")
        hw_grid.pack(fill="x", padx=20, pady=10)
        
        self.openrgb_switch = ctk.CTkSwitch(hw_grid, text="OpenRGB Sync", variable=self.use_openrgb,
                                           progress_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12),
                                           command=self._toggle_openrgb)
        self.openrgb_switch.pack(side="left", padx=(0, 30))
        
        self.typing_switch = ctk.CTkSwitch(hw_grid, text="Typing Suppression", variable=self.typing_suppression,
                                          progress_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12))
        self.typing_switch.pack(side="left")
        
        slider_frame = ctk.CTkFrame(self.last_card, fg_color="transparent")
        slider_frame.pack(fill="x", padx=20, pady=(5, 20))
        
        ctk.CTkLabel(slider_frame, text="SENSITIVITY", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        ctk.CTkSlider(slider_frame, from_=0.1, to=3.0, variable=self.rgb_sensitivity, 
                      button_color=COLOR_WHITE, button_hover_color=COLOR_ACCENT, progress_color=COLOR_ACCENT).pack(fill="x", pady=(2, 12))
        
        ctk.CTkLabel(slider_frame, text="PULSE DECAY", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        ctk.CTkSlider(slider_frame, from_=0.5, to=0.99, variable=self.rgb_decay,
                      button_color=COLOR_WHITE, button_hover_color=COLOR_ACCENT, progress_color=COLOR_ACCENT).pack(fill="x", pady=(2, 0))

        self._create_card(self.main_container, "CASE FAN VISUALIZATION")
        
        fan_switches = ctk.CTkFrame(self.last_card, fg_color="transparent")
        fan_switches.pack(fill="x", padx=20, pady=(5, 10))
        
        self.fan_enable_switch = ctk.CTkSwitch(
            fan_switches, text="Enable Fan Ring FX", variable=self.fan_viz_enabled,
            progress_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12),
            command=self._on_fan_config_changed
        )
        self.fan_enable_switch.pack(side="left", padx=(0, 25))
        
        self.fan_clockwise_switch = ctk.CTkSwitch(
            fan_switches, text="Clockwise", variable=self.fan_clockwise,
            progress_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12),
            command=self._on_fan_config_changed
        )
        self.fan_clockwise_switch.pack(side="left")

        fan_body = ctk.CTkFrame(self.last_card, fg_color="transparent")
        fan_body.pack(fill="x", padx=20, pady=(0, 15))
        
        fan_ctrls = ctk.CTkFrame(fan_body, fg_color="transparent")
        fan_ctrls.pack(side="left", fill="x", expand=True, padx=(0, 15))

        hdr_row = ctk.CTkFrame(fan_ctrls, fg_color="transparent")
        hdr_row.pack(fill="x", pady=(0, 2))
        ctk.CTkLabel(hdr_row, text="CONNECTED ARGB HARDWARE", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(side="left")
        self.rescan_btn = ctk.CTkButton(
            hdr_row, text="↻ SCAN RGB", width=78, height=20,
            fg_color="#1A1A1A", hover_color="#282828",
            text_color=COLOR_WHITE, font=ctk.CTkFont(family=FONT_MAIN, size=9, weight="bold"),
            command=self._rescan_rgb
        )
        self.rescan_btn.pack(side="right")

        self.header_combo = ctk.CTkOptionMenu(
            fan_ctrls, values=self.detected_headers,
            variable=self.current_header, fg_color=COLOR_BORDER,
            button_color=COLOR_BORDER, button_hover_color=COLOR_ACCENT,
            dropdown_fg_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=11, weight="bold"),
            command=self._on_target_header_selected
        )
        self.header_combo.pack(fill="x", pady=(2, 8))
        
        ctk.CTkLabel(fan_ctrls, text="ANIMATION MODE", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        self.fan_mode_menu = ctk.CTkOptionMenu(
            fan_ctrls, values=list(FAN_MODE_DISPLAY.keys()),
            variable=self.fan_mode_str, fg_color=COLOR_BORDER,
            button_color=COLOR_BORDER, button_hover_color=COLOR_ACCENT,
            dropdown_fg_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=11),
            command=self._on_fan_config_changed
        )
        self.fan_mode_menu.pack(fill="x", pady=(2, 8))
        
        ctk.CTkLabel(fan_ctrls, text="COLOR THEME", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        self.fan_theme_menu = ctk.CTkOptionMenu(
            fan_ctrls, values=list(FAN_THEME_DISPLAY.keys()),
            variable=self.fan_theme_str, fg_color=COLOR_BORDER,
            button_color=COLOR_BORDER, button_hover_color=COLOR_ACCENT,
            dropdown_fg_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=11),
            command=self._on_fan_theme_changed
        )
        self.fan_theme_menu.pack(fill="x", pady=(2, 6))

        # Spectrum Chooser & Live Color Swatch row
        spectrum_row = ctk.CTkFrame(fan_ctrls, fg_color="transparent")
        spectrum_row.pack(fill="x", pady=(0, 8))

        self.spectrum_btn = ctk.CTkButton(
            spectrum_row, text="🎨 SPECTRUM CHOOSER",
            height=28, fg_color="#1F1F1F", hover_color="#2D2D2D",
            text_color=COLOR_WHITE,
            font=ctk.CTkFont(family=FONT_MAIN, size=11, weight="bold"),
            command=self._open_spectrum_chooser
        )
        self.spectrum_btn.pack(side="left", fill="x", expand=True, padx=(0, 6))

        self.color_swatch_btn = ctk.CTkButton(
            spectrum_row, text=self.custom_hex, width=80, height=28,
            fg_color=self.custom_hex, hover_color=self.custom_hex,
            text_color="#000000",
            font=ctk.CTkFont(family="Consolas", size=11, weight="bold"),
            command=self._open_spectrum_chooser
        )
        self.color_swatch_btn.pack(side="right")

        ctk.CTkLabel(fan_ctrls, text="FAN RING LED COUNT", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        self.fan_leds_menu = ctk.CTkOptionMenu(
            fan_ctrls, values=list(FAN_LEDS_DISPLAY.keys()),
            variable=self.fan_led_count_str, fg_color=COLOR_BORDER,
            button_color=COLOR_BORDER, button_hover_color=COLOR_ACCENT,
            dropdown_fg_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=11),
            command=self._on_fan_config_changed
        )
        self.fan_leds_menu.pack(fill="x", pady=(2, 8))

        ctk.CTkLabel(fan_ctrls, text="NUMBER OF FANS", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        self.fan_count_menu = ctk.CTkOptionMenu(
            fan_ctrls, values=list(FAN_COUNT_DISPLAY.keys()),
            variable=self.fan_count_str, fg_color=COLOR_BORDER,
            button_color=COLOR_BORDER, button_hover_color=COLOR_ACCENT,
            dropdown_fg_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=11),
            command=self._on_fan_config_changed
        )
        self.fan_count_menu.pack(fill="x", pady=(2, 8))

        ctk.CTkLabel(fan_ctrls, text="ROTATION / WAVE SPEED", font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED).pack(anchor="w")
        ctk.CTkSlider(fan_ctrls, from_=0.2, to=3.0, variable=self.fan_speed,
                      button_color=COLOR_WHITE, button_hover_color=COLOR_ACCENT, progress_color=COLOR_ACCENT,
                      command=self._on_fan_config_changed).pack(fill="x", pady=(2, 0))

        fan_prev_box = ctk.CTkFrame(fan_body, fg_color=COLOR_BG, corner_radius=10, border_color=COLOR_BORDER, border_width=1)
        fan_prev_box.pack(side="right", padx=(5, 0), pady=0)
        
        ctk.CTkLabel(fan_prev_box, text="FAN PREVIEW", font=ctk.CTkFont(family=FONT_MAIN, size=9, weight="bold"), text_color=COLOR_MUTED).pack(pady=(6, 0))
        self.fan_canvas = ctk.CTkCanvas(fan_prev_box, width=130, height=130, bg=COLOR_BG, highlightthickness=0)
        self.fan_canvas.pack(padx=10, pady=(2, 8))
        self._init_fan_preview_canvas()

        self.adv_btn = ctk.CTkButton(self.main_container, text="SHOW LOGS",
                                    fg_color="transparent", text_color=COLOR_MUTED, 
                                    hover_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"),
                                    command=self._toggle_advanced)
        self.adv_btn.pack(pady=10)
        
        self.console = ctk.CTkTextbox(self.main_container, height=120, fg_color=COLOR_BG, 
                                     border_color=COLOR_BORDER, border_width=1,
                                     font=ctk.CTkFont(family="Consolas", size=10), text_color=COLOR_MUTED)
        self.console.configure(state="disabled")

        self.footer = ctk.CTkFrame(self, fg_color=COLOR_BG, height=80, corner_radius=0)
        self.footer.grid(row=2, column=0, sticky="ew")
        
        self.stream_btn = ctk.CTkButton(self.footer, text="START STREAMING", 
                                       height=50, corner_radius=10, fg_color=COLOR_ACCENT, 
                                       font=ctk.CTkFont(family=FONT_MAIN, size=14, weight="bold"),
                                       hover_color="#B0151A", command=self._toggle_streaming)
        self.stream_btn.pack(fill="x", padx=20, pady=15)

    def _create_card(self, parent, title):
        card = ctk.CTkFrame(parent, fg_color=COLOR_CARD, corner_radius=12, border_width=1, border_color=COLOR_BORDER)
        card.pack(fill="x", padx=20, pady=10)
        title_lbl = ctk.CTkLabel(card, text=title, font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"), text_color=COLOR_MUTED)
        title_lbl.pack(anchor="w", padx=20, pady=(15, 5))
        self.last_card = card

    def _toggle_advanced(self):
        if self.show_advanced.get():
            self.console.pack_forget()
            self.adv_btn.configure(text="SHOW ADVANCED LOGS")
            self.show_advanced.set(False)
        else:
            self.console.pack(fill="x", padx=20, pady=(0, 20))
            self.adv_btn.configure(text="HIDE ADVANCED LOGS")
            self.show_advanced.set(True)

    def log(self, msg):
        self.log_queue.put(msg)

    def _on_key_press(self):
        self.last_key_time = time.perf_counter()

    def _copy_pc_ip(self):
        self.clipboard_clear()
        self.clipboard_append(self.local_pc_ip)
        self.copy_ip_btn.configure(text="COPIED!", fg_color="#4CAF50")
        self.after(1500, lambda: self.copy_ip_btn.configure(text="COPY IP", fg_color=COLOR_BORDER))

    def _on_direction_changed(self):
        is_phone_to_pc = (self.direction.get() == "PHONE_TO_PC")
        if is_phone_to_pc:
            self.pc_ip_frame.pack(fill="x", padx=20, pady=(0, 15))
            self.conn_switch_frame.pack_forget()
            self.addr_frame.pack_forget()
            self.audio_combo.configure(state="disabled")
            if not self.is_streaming:
                self.stream_btn.configure(text="START LISTENER (SYNC PC RGB)")
        else:
            self.pc_ip_frame.pack_forget()
            self.conn_switch_frame.pack(fill="x", padx=20, pady=(5, 10))
            self.addr_frame.pack(fill="x", padx=20, pady=(0, 20))
            self.audio_combo.configure(state="normal")
            if not self.is_streaming:
                self.stream_btn.configure(text="START STREAMING TO PHONE")

    def _refresh_audio_sources(self):
        names = [d["name"] + (" (Default)" if d["is_default"] else "") for d in self.wasapi_devices]
        self.audio_combo.configure(values=names)
        if names:
            default_idx = 0
            for i, d in enumerate(self.wasapi_devices):
                if d["is_default"]:
                    default_idx = i
                    break
            self.audio_combo.set(names[default_idx])

    def _toggle_openrgb(self):
        if self.use_openrgb.get():
            self._save_current_header_config()
            threading.Thread(target=self.rgb_manager.connect, daemon=True).start()
        else:
            self.rgb_manager.stop()
            self.detected_headers = ["OpenRGB Disconnected"]
            self.header_combo.configure(values=self.detected_headers)
            self.current_header.set("OpenRGB Disconnected")
            if hasattr(self, "fan_hub_text"):
                self.fan_canvas.itemconfig(self.fan_hub_text, text="OFFLINE")

    def _open_spectrum_chooser(self):
        color_tuple = colorchooser.askcolor(
            color=self.custom_hex,
            title="GLYPHIX - Fan ARGB Color Spectrum Chooser"
        )
        if color_tuple and color_tuple[1]:
            hex_code = color_tuple[1].upper()
            rgb_code = tuple(int(c) for c in color_tuple[0])
            self.custom_hex = hex_code
            self.custom_rgb = rgb_code
            self.fan_theme_str.set("Custom Spectrum Color...")
            self._update_color_swatch(hex_code)
            self._on_fan_config_changed()

    def _on_fan_theme_changed(self, val=None):
        theme_str = self.fan_theme_str.get()
        if theme_str == "Custom Spectrum Color...":
            self._open_spectrum_chooser()
            return
        if theme_str in THEME_PRESET_COLORS:
            r, g, b = THEME_PRESET_COLORS[theme_str]
            hex_c = f"#{r:02x}{g:02x}{b:02x}".upper()
            self._update_color_swatch(hex_c)
        self._on_fan_config_changed()

    def _update_color_swatch(self, hex_val):
        if not hasattr(self, "color_swatch_btn"):
            return
        self.color_swatch_btn.configure(
            text=hex_val,
            fg_color=hex_val,
            hover_color=hex_val
        )
        try:
            r = int(hex_val[1:3], 16)
            g = int(hex_val[3:5], 16)
            b = int(hex_val[5:7], 16)
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            text_col = "#000000" if lum > 0.55 else "#FFFFFF"
            self.color_swatch_btn.configure(text_color=text_col)
        except Exception:
            pass

    def _on_target_header_selected(self, selected_header=None):
        if selected_header is None:
            selected_header = self.current_header.get()
        
        cfg = self.header_configs.get(selected_header)
        if cfg:
            self.fan_viz_enabled.set(cfg.get("enabled", True))
            self.fan_clockwise.set(cfg.get("clockwise", True))
            self.fan_mode_str.set(cfg.get("mode_str", "Radial VU Meter (Full Ring)"))
            self.fan_theme_str.set(cfg.get("theme_str", "Classic VU (Green-Yellow-Red)"))
            self.fan_led_count_str.set(cfg.get("led_count_str", "16 LEDs (Standard)"))
            self.fan_count_str.set(cfg.get("fan_count_str", "1 Fan"))
            self.fan_speed.set(cfg.get("speed", 1.0))
            if "custom_hex" in cfg:
                self.custom_hex = cfg["custom_hex"]
            if "custom_color" in cfg:
                self.custom_rgb = cfg["custom_color"]
            self._update_color_swatch(self.custom_hex)
        else:
            self._save_current_header_config()

        hub_text = "FAN 1"
        if selected_header and "sync all" not in selected_header.lower() and not selected_header.startswith("No ") and not selected_header.startswith("OpenRGB"):
            hub_text = selected_header.split()[0][:9]
        if hasattr(self, "fan_hub_text"):
            self.fan_canvas.itemconfig(self.fan_hub_text, text=hub_text)

    def _rescan_rgb(self):
        self.rescan_btn.configure(text="SCANNING...", text_color=COLOR_MUTED)
        def _scan():
            if not self.use_openrgb.get():
                self.use_openrgb.set(True)
            connected = self.rgb_manager.connect()
            headers = self.rgb_manager.get_detected_headers()
            self.after(0, lambda: self._on_rescan_done(headers, connected))
        threading.Thread(target=_scan, daemon=True).start()

    def _on_rescan_done(self, headers, connected):
        self.rescan_btn.configure(text="↻ SCAN RGB", text_color=COLOR_WHITE)
        if not connected:
            self.log("OpenRGB: Server not found on port 6742. Is OpenRGB running?")
            self.detected_headers = ["No OpenRGB Connected"]
            self.header_combo.configure(values=self.detected_headers)
            self.current_header.set("No OpenRGB Connected")
            return
        self._on_openrgb_headers_detected(headers)

    def _save_current_header_config(self):
        hdr = self.current_header.get()
        mode_val = FAN_MODE_DISPLAY.get(self.fan_mode_str.get(), "vu_meter")
        theme_val = FAN_THEME_DISPLAY.get(self.fan_theme_str.get(), "classic")
        ring_size = FAN_LEDS_DISPLAY.get(self.fan_led_count_str.get(), 16)
        num_fans = FAN_COUNT_DISPLAY.get(self.fan_count_str.get(), 1)
        
        cfg = {
            "mode": mode_val,
            "theme": theme_val,
            "mode_str": self.fan_mode_str.get(),
            "theme_str": self.fan_theme_str.get(),
            "custom_color": self.custom_rgb,
            "custom_hex": self.custom_hex,
            "fan_ring_size": ring_size,
            "fan_count": num_fans,
            "clockwise": self.fan_clockwise.get(),
            "speed": self.fan_speed.get(),
            "enabled": self.fan_viz_enabled.get(),
            "led_count_str": self.fan_led_count_str.get(),
            "fan_count_str": self.fan_count_str.get()
        }
        self.header_configs[hdr] = cfg

        if "sync all" in hdr.lower():
            for h in self.detected_headers:
                if "sync all" not in h.lower():
                    self.header_configs[h] = dict(cfg)

        self.rgb_manager.update_header_configs(self.header_configs)
        self.rgb_manager.update_fan_config(ring_size, num_fans)

    def _on_fan_config_changed(self, val=None):
        self._save_current_header_config()

    def _on_openrgb_headers_detected(self, headers):
        if not headers: return
        self.detected_headers = headers
        self.header_combo.configure(values=headers)
        if self.current_header.get() not in headers:
            self.current_header.set(headers[0])
            self._on_target_header_selected(headers[0])

        sync_all_key = headers[0] if "sync all" in headers[0].lower() else "All Connected ARGB (Sync All)"
        current_cfg = self.header_configs.get(sync_all_key, self.header_configs.get("All Connected ARGB (Sync All)", {}))
        for h in headers:
            if h not in self.header_configs:
                self.header_configs[h] = dict(current_cfg)
        self.rgb_manager.update_header_configs(self.header_configs)
        count = len(headers) - (1 if "sync all" in headers[0].lower() else 0)
        self.log(f"OpenRGB Auto-Detect: Found {count} connected ARGB lighting device(s).")

    def _toggle_discovery(self):
        if self.is_discovering:
            self.stop_discovery_event.set()
        else:
            self.is_discovering = True
            self.stop_discovery_event.clear()
            self.discover_btn.configure(text="CANCEL", fg_color=COLOR_BORDER, text_color=COLOR_TEXT)
            self.status_pill.configure(text="SEARCHING", text_color=COLOR_ACCENT)
            self.status_dot.configure(text_color=COLOR_ACCENT)
            threading.Thread(target=self._discovery_worker, daemon=True).start()

    def _discovery_worker(self):
        mode = self.conn_type.get()
        found = None
        if mode == "BT":
            self.log("BLE Scan started...")
            devices = discover_phone_bt()
            matches = [d for d in devices if d[2]]
            if matches: found = matches[0][0]
        else:
            self.log("Broadcasting UDP discovery...")
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(0.5)
            msg = b"GLYPHIX_DISCOVERY_REQUEST"
            addrs = get_broadcast_addresses()
            start = time.time()
            while time.time() - start < 10 and not self.stop_discovery_event.is_set():
                try:
                    for a in addrs: sock.sendto(msg, (a, DISCOVERY_PORT))
                    data, addr = sock.recvfrom(1024)
                    if data == b"GLYPHIX_DISCOVERY_RESPONSE":
                        found = addr[0]
                        break
                except: continue
            sock.close()
        
        self.after(0, self._on_discovery_done, found)

    def _on_discovery_done(self, found):
        self.is_discovering = False
        self.discover_btn.configure(text="DISCOVER", fg_color=COLOR_WHITE, text_color=COLOR_BG)
        if found:
            self.addr_entry.delete(0, "end")
            self.addr_entry.insert(0, found)
            self.status_pill.configure(text="READY", text_color=COLOR_TEXT)
            self.status_dot.configure(text_color="#00FF00")
            self.log(f"Discovery: Found {found}")
        else:
            self.status_pill.configure(text="IDLE", text_color=COLOR_MUTED)
            self.status_dot.configure(text_color=COLOR_MUTED)
            self.log("Discovery: No device found.")

    def _toggle_streaming(self):
        if self.is_streaming:
            self.stop_stream_event.set()
        else:
            is_phone_to_pc = (self.direction.get() == "PHONE_TO_PC")
            if is_phone_to_pc:
                self.is_streaming = True
                self.stop_stream_event.clear()
                self.stream_btn.configure(text="STOP LISTENER", fg_color=COLOR_BORDER, text_color=COLOR_TEXT)
                self.status_pill.configure(text="LISTENING FOR PHONE", text_color=COLOR_ACCENT)
                self.status_dot.configure(text_color=COLOR_ACCENT)
                if self.typing_suppression.get(): self.hook_watcher.start()
                threading.Thread(target=self._listener_worker, daemon=True).start()
            else:
                addr = self.addr_entry.get().strip()
                if not addr:
                    messagebox.showerror("Error", "Enter Phone IP or MAC address first.")
                    return
                # Sanitize address if port was included (e.g. 192.168.1.55:12347)
                port = UDP_PORT
                if ":" in addr and not addr.count(":") > 1:
                    parts = addr.split(":")
                    if len(parts) == 2 and parts[1].isdigit():
                        addr = parts[0]
                        port = int(parts[1])
                
                selected_name = self.audio_combo.get()
                device = None
                for d in self.wasapi_devices:
                    if d["name"] in selected_name:
                        device = d
                        break
                if not device: return

                self.is_streaming = True
                self.stop_stream_event.clear()
                self.stream_btn.configure(text="STOP STREAMING", fg_color=COLOR_BORDER, text_color=COLOR_TEXT)
                self.status_pill.configure(text="STREAMING TO PHONE", text_color=COLOR_ACCENT)
                self.status_dot.configure(text_color=COLOR_ACCENT)
                
                if self.typing_suppression.get(): self.hook_watcher.start()
                
                threading.Thread(target=self._stream_worker, args=(addr, device, port), daemon=True).start()

    def _listener_worker(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", UDP_PORT))
            sock.settimeout(0.5)
        except Exception as e:
            self.after(0, self._on_stream_error, f"Port {UDP_PORT} error: {e}")
            return

        self.log(f"Listener active on port {UDP_PORT}. Waiting for Nothing Phone stream...")
        threading.Thread(target=self._viz_worker, args=(TARGET_RATE,), daemon=True).start()
        threading.Thread(target=self._pc_discovery_responder, daemon=True).start()

        packets = 0
        last_report = time.time()
        try:
            while not self.stop_stream_event.is_set():
                try:
                    data, addr = sock.recvfrom(8192)
                    if not data: continue
                    samples = np.frombuffer(data, dtype=np.int16)
                    try:
                        self.viz_queue.put_nowait(samples)
                    except queue.Full:
                        pass
                    packets += 1
                    now = time.time()
                    if now - last_report >= 2.0:
                        self.log(f"Received {packets} packets from Phone ({addr[0]})")
                        last_report = now
                except socket.timeout:
                    continue
                except Exception as e:
                    if not self.stop_stream_event.is_set():
                        self.log(f"Listener socket error: {e}")
        finally:
            self.rgb_manager.stop()
            try: sock.close()
            except: pass
            self.after(0, self._on_stream_stopped)

    def _pc_discovery_responder(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", DISCOVERY_PORT))
            sock.settimeout(1.0)
            while not self.stop_stream_event.is_set():
                try:
                    data, addr = sock.recvfrom(1024)
                    msg = data.decode('utf-8', errors='ignore')
                    if "GLYPHIX" in msg:
                        sock.sendto(b"GLYPHIX_PC_DISCOVERY_RESPONSE", addr)
                        self.log(f"Discovery: Sent PC announcement to Phone ({addr[0]})")
                except socket.timeout:
                    continue
                except:
                    break
        except Exception as e:
            self.log(f"Discovery responder error: {e}")
        finally:
            try: sock.close()
            except: pass

    def _stream_worker(self, addr, device, port=UDP_PORT):
        p = pyaudio.PyAudio()
        native_rate = device["rate"]
        channels = device["channels"]
        chunk_size = 512
        
        try:
            stream = p.open(format=FORMAT, channels=channels, rate=TARGET_RATE,
                            input=True, input_device_index=device["index"],
                            frames_per_buffer=chunk_size)
            actual_rate = TARGET_RATE
        except:
            try:
                stream = p.open(format=FORMAT, channels=channels, rate=native_rate,
                                input=True, input_device_index=device["index"],
                                frames_per_buffer=chunk_size)
                actual_rate = native_rate
            except Exception as e:
                self.after(0, self._on_stream_error, str(e))
                return

        threading.Thread(target=self._viz_worker, args=(actual_rate,), daemon=True).start()

        conn_type = self.conn_type.get()
        sock = None
        if conn_type == "BT":
            for p_port in range(1, 10):
                try:
                    s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
                    s.settimeout(2.0); s.connect((addr, p_port)); sock = s
                    break
                except: continue
        else:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try: sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 256 * 1024)
            except: pass

        if not sock:
            self.after(0, self._on_stream_error, "Connection failed.")
            stream.close(); p.terminate(); return

        interp_indices = None
        if actual_rate != TARGET_RATE:
            interp_indices = np.linspace(0, chunk_size - 1, int(chunk_size * TARGET_RATE / actual_rate))
        
        try:
            while not self.stop_stream_event.is_set():
                raw = stream.read(chunk_size, exception_on_overflow=False)
                samples = np.frombuffer(raw, dtype=np.int16)
                mono = samples.reshape(-1, channels).mean(axis=1).astype(np.int16) if channels > 1 else samples
                
                try: self.viz_queue.put_nowait(mono)
                except queue.Full: pass

                if interp_indices is not None:
                    data = np.interp(interp_indices, np.arange(len(mono)), mono).astype(np.int16).tobytes()
                else:
                    data = mono.tobytes()
                
                if conn_type == "BT": sock.sendall(data)
                else: sock.sendto(data, (addr, port))
                
        except Exception as e: self.log(f"Stream error: {e}")
        finally:
            self.rgb_manager.stop()
            stream.stop_stream(); stream.close(); p.terminate(); sock.close()
            self.after(0, self._on_stream_stopped)

    def _viz_worker(self, actual_rate):
        bass_engine = PureBassEngine(sample_rate=actual_rate)
        last_viz_time = 0
        
        while not self.stop_stream_event.is_set():
            try:
                mono = self.viz_queue.get(timeout=0.1)
                now = time.time()
                
                if now - last_viz_time > 0.03:
                    sf = mono.astype(np.float32) / 32768.0
                    n = len(sf)
                    fft = np.abs(np.fft.rfft(sf * np.hanning(n))) / (n / 2.0)
                    
                    freqs = np.geomspace(40, 15000, 64)
                    bin_idx = np.clip(freqs / (actual_rate / n), 0, len(fft) - 1)
                    points = np.interp(bin_idx, np.arange(len(fft)), fft)
                    
                    # True RMS and peak transient volume calculation
                    rms = float(np.sqrt(np.mean(sf ** 2)))
                    peak_val = float(np.max(np.abs(sf)))
                    sensitivity = self.rgb_sensitivity.get()
                    decay_rate = self.rgb_decay.get()

                    # Scale raw audio level by sensitivity: 75% RMS (body) + 25% transient peak
                    raw_audio_level = float(np.clip((rms * 2.8 * 0.75 + peak_val * 1.5 * 0.25) * sensitivity, 0.0, 1.0))
                    pulse = bass_engine.process(mono, decay=decay_rate) * sensitivity
                    energy = float(np.clip(raw_audio_level * 1.2, 0.0, 1.0))

                    # Send points scaled by sensitivity to top GUI dot-matrix visualizer
                    self.level_queue.put(list(np.clip(points * 15 * sensitivity, 0, 1)))

                    fan_mode_val = FAN_MODE_DISPLAY.get(self.fan_mode_str.get(), "vu_meter")
                    fan_theme_val = FAN_THEME_DISPLAY.get(self.fan_theme_str.get(), "classic")
                    fan_leds_val = FAN_LEDS_DISPLAY.get(self.fan_led_count_str.get(), 16)
                    fan_count_val = FAN_COUNT_DISPLAY.get(self.fan_count_str.get(), 1)

                    fan_preview = None
                    if self.use_openrgb.get():
                        typing_pause = self.typing_suppression.get() and (time.perf_counter() - self.last_key_time < 1.5)
                        if not typing_pause:
                            br, bg, bb = self.selected_rgb
                            fan_preview = self.rgb_manager.sync(
                                (br/255)*pulse, (bg/255)*pulse, (bb/255)*pulse,
                                raw_audio_level=raw_audio_level,
                                energy=energy, pulse=pulse, spectrum=points,
                                fan_viz_enabled=self.fan_viz_enabled.get(),
                                fan_mode=fan_mode_val,
                                fan_theme=fan_theme_val,
                                fan_clockwise=self.fan_clockwise.get(),
                                fan_speed=self.fan_speed.get(),
                                fan_leds=fan_leds_val,
                                fan_count=fan_count_val,
                                decay_rate=decay_rate,
                                custom_color=self.custom_rgb
                            )
                        else:
                            self.rgb_manager.stop()

                    if fan_preview is None and self.fan_viz_enabled.get():
                        single_fan_colors = self.rgb_manager.fan_visualizer.render_fan_ring(
                            num_leds=fan_leds_val,
                            raw_level=raw_audio_level,
                            energy=energy,
                            pulse=pulse,
                            mode=fan_mode_val,
                            theme=fan_theme_val,
                            clockwise=self.fan_clockwise.get(),
                            speed_mult=self.fan_speed.get(),
                            fan_idx=0,
                            total_fans=fan_count_val,
                            spectrum=points,
                            decay_rate=decay_rate,
                            custom_color=self.custom_rgb
                        )
                        fan_preview = {"__all__": single_fan_colors}

                    if fan_preview:
                        try:
                            self.fan_preview_queue.put_nowait(fan_preview)
                        except queue.Full:
                            pass
                    
                    last_viz_time = now
            except queue.Empty:
                continue

    def _on_stream_error(self, err):
        messagebox.showerror("Stream Error", err)
        self._on_stream_stopped()

    def _on_stream_stopped(self):
        self.is_streaming = False
        btn_txt = "START LISTENER (SYNC PC RGB)" if self.direction.get() == "PHONE_TO_PC" else "START STREAMING TO PHONE"
        self.stream_btn.configure(text=btn_txt, fg_color=COLOR_ACCENT, text_color=COLOR_WHITE)
        self.status_pill.configure(text="DISCONNECTED", text_color=COLOR_MUTED)
        self.status_dot.configure(text_color=COLOR_MUTED)
        self.level_queue.put([0.0]*64)
        try:
            self.fan_preview_queue.put_nowait({"__all__": [(0, 0, 0)] * 16})
        except:
            pass
        self.hook_watcher.stop()

    def _update_loop(self):
        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            self.console.configure(state="normal")
            self.console.insert("end", f"[{time.strftime('%H:%M:%S')}] {msg}\n")
            self.console.see("end")
            self.console.configure(state="disabled")

        last_points = None
        while not self.level_queue.empty(): last_points = self.level_queue.get_nowait()
        
        num_dots = len(self.spectrum_points)
        if last_points:
            interpolated = np.interp(
                np.linspace(0, 63, num_dots),
                np.arange(64),
                last_points
            )
            for i in range(num_dots):
                self.spectrum_points[i] = max(self.spectrum_points[i] * 0.8, interpolated[i])
        else:
            for i in range(num_dots): self.spectrum_points[i] *= 0.8

        self._draw_viz()

        last_fan_preview = None
        while not self.fan_preview_queue.empty():
            last_fan_preview = self.fan_preview_queue.get_nowait()
        if last_fan_preview is not None:
            if isinstance(last_fan_preview, dict):
                cur_hdr = self.current_header.get()
                chosen = None
                if cur_hdr in last_fan_preview:
                    chosen = last_fan_preview[cur_hdr]
                else:
                    cur_clean = cur_hdr.split()[0].lower()
                    for k, v in last_fan_preview.items():
                        if k.lower() in cur_clean or cur_clean in k.lower():
                            chosen = v
                            break
                    if chosen is None:
                        chosen = last_fan_preview.get("__all__")
                    if chosen is None and last_fan_preview:
                        chosen = next(iter(last_fan_preview.values()))
                if chosen:
                    self._draw_fan_preview(chosen)
            elif isinstance(last_fan_preview, list):
                self._draw_fan_preview(last_fan_preview)

        self.after(30, self._update_loop)

    def _init_fan_preview_canvas(self):
        self.fan_preview_dots = []
        self._fan_preview_cache = []
        w, h = 130, 130
        cx, cy = w / 2.0, h / 2.0
        
        self.fan_canvas.create_oval(cx - 24, cy - 24, cx + 24, cy + 24, fill="#161616", outline=COLOR_BORDER, width=1)
        self.fan_hub_text = self.fan_canvas.create_text(cx, cy, text="FAN 1", fill=COLOR_MUTED, font=ctk.CTkFont(family=FONT_MAIN, size=8, weight="bold"))
        
        r_ring = 46.0
        dot_radius = 4.5
        num_dots = 16
        for i in range(num_dots):
            angle = (2.0 * math.pi * i) / num_dots - (math.pi / 2.0)
            x = cx + r_ring * math.cos(angle)
            y = cy + r_ring * math.sin(angle)
            dot = self.fan_canvas.create_oval(
                x - dot_radius, y - dot_radius,
                x + dot_radius, y + dot_radius,
                fill=COLOR_BORDER, outline=""
            )
            self.fan_preview_dots.append(dot)
            self._fan_preview_cache.append(COLOR_BORDER)

    def _draw_fan_preview(self, colors):
        if not self.fan_preview_dots:
            return
        num_dots = len(self.fan_preview_dots)
        if not colors or not self.fan_viz_enabled.get():
            for i, dot in enumerate(self.fan_preview_dots):
                if self._fan_preview_cache[i] != COLOR_BORDER:
                    self.fan_canvas.itemconfig(dot, fill=COLOR_BORDER)
                    self._fan_preview_cache[i] = COLOR_BORDER
            return
            
        if len(colors) == num_dots:
            chosen = colors
        else:
            indices = np.linspace(0, len(colors) - 1, num_dots).astype(int)
            chosen = [colors[idx] for idx in indices]

        for i, (dot, (r, g, b)) in enumerate(zip(self.fan_preview_dots, chosen)):
            hex_c = f"#{r:02x}{g:02x}{b:02x}"
            if self._fan_preview_cache[i] != hex_c:
                self.fan_canvas.itemconfig(dot, fill=hex_c)
                self._fan_preview_cache[i] = hex_c

    def _init_viz_dots(self, count=64):
        if self.viz_dots:
            for col in self.viz_dots:
                for dot in col:
                    self.viz_canvas.delete(dot)
        
        self.viz_dots = []
        self.spectrum_points = [0.0] * count
        for i in range(count):
            col_dots = []
            for d in range(8):
                dot = self.viz_canvas.create_oval(0, 0, 0, 0, fill=COLOR_BORDER, outline="")
                col_dots.append(dot)
            self.viz_dots.append(col_dots)

    def _resize_viz(self, event=None):
        w = self.viz_canvas.winfo_width()
        h = self.viz_canvas.winfo_height()
        if w <= 1: return
        
        target_spacing = 12
        new_count = max(8, w // target_spacing)
        
        if new_count != len(self.viz_dots):
            self._init_viz_dots(new_count)
            self._viz_cache = [-1] * new_count
            
        dot_spacing = w / new_count
        dot_size = max(4, dot_spacing - 4)
        
        for i in range(new_count):
            x = i * dot_spacing + dot_spacing/2
            for d in range(8):
                dy = h - (d * 10 + 15)
                dot = self.viz_dots[i][d]
                self.viz_canvas.coords(dot, x-dot_size/2, dy-dot_size/2, x+dot_size/2, dy+dot_size/2)

    def _draw_viz(self):
        if not self.viz_dots: return
        
        num_cols = len(self.viz_dots)
        if len(self._viz_cache) != num_cols:
            self._viz_cache = [-1] * num_cols
            
        for i in range(num_cols):
            val = self.spectrum_points[i]
            dots_to_draw = int(val * 8)
            
            if dots_to_draw != self._viz_cache[i]:
                for d in range(8):
                    color = COLOR_ACCENT if d < dots_to_draw else COLOR_BORDER
                    dot = self.viz_dots[i][d]
                    self.viz_canvas.itemconfig(dot, fill=color)
                self._viz_cache[i] = dots_to_draw

if __name__ == "__main__":
    ctk.set_appearance_mode("Dark")
    app = CompanionApp()
    app.mainloop()
