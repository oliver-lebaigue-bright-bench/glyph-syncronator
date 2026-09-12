import socket
import pyaudiowpatch as pyaudio
import numpy as np
import time
import argparse
import sys
import os
import shutil
import subprocess
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
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 48000

# ==============================================================================
# OPENRGB AUTO-START & ELEVATED ADMIN LAUNCH ENGINE
# ==============================================================================
def find_openrgb_executable():
    candidates = [
        r"C:\Program Files\OpenRGB\OpenRGB.exe",
        r"C:\Program Files (x86)\OpenRGB\OpenRGB.exe",
        r"C:\OpenRGB\OpenRGB.exe",
        os.path.expandvars(r"%PROGRAMFILES%\OpenRGB\OpenRGB.exe"),
        os.path.expandvars(r"%PROGRAMFILES(X86)%\OpenRGB\OpenRGB.exe"),
        os.path.expandvars(r"%LOCALAPPDATA%\Programs\OpenRGB\OpenRGB.exe"),
        os.path.expandvars(r"%LOCALAPPDATA%\OpenRGB\OpenRGB.exe"),
        shutil.which("OpenRGB.exe") or "",
        shutil.which("OpenRGB") or "",
        shutil.which("openrgb") or "",
    ]
    for path in candidates:
        if path and os.path.isfile(path):
            return os.path.abspath(path)
    return None

def is_port_open(host="127.0.0.1", port=OPENRGB_PORT, timeout=0.3):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except (socket.timeout, ConnectionRefusedError, OSError):
        return False

def launch_openrgb_elevated(logger=None):
    exe = find_openrgb_executable()
    if not exe:
        if logger:
            logger("OpenRGB Error: Executable 'OpenRGB.exe' not found in standard directories.")
        return False

    if sys.platform == "win32" and HAS_CTYPES:
        try:
            if logger:
                logger(f"OpenRGB: Launching '{exe}' with Administrator privileges (--server --startminimized)...")
            ret = ctypes.windll.shell32.ShellExecuteW(
                None,
                "runas",
                exe,
                "--server --startminimized",
                os.path.dirname(exe),
                6  # SW_MINIMIZE
            )
            if ret > 32:
                if logger:
                    logger("OpenRGB: Admin elevation prompt sent. Waiting for SDK server on port 6742...")
                return True
            else:
                if logger:
                    logger(f"OpenRGB: ShellExecute returned status code {ret}")
        except Exception as ex:
            if logger:
                logger(f"OpenRGB: Elevation failed ({ex}). Falling back to subprocess...")

    try:
        subprocess.Popen([exe, "--server", "--startminimized"], shell=False)
        if logger:
            logger("OpenRGB: Started process. Waiting for SDK server on port 6742...")
        return True
    except Exception as ex:
        if logger:
            logger(f"OpenRGB: Failed to start process: {ex}")
        return False

# ==============================================================================
# PURE MONOCHROME / NOTHING OS DESIGN TOKENS (ZERO RED, ZERO GREEN)
# ==============================================================================
COLOR_BG = "#0B0C0E"              # Deep Obsidian Background
COLOR_SURFACE = "#14161A"         # Elevated Surface Card
COLOR_SURFACE_INNER = "#1B1D23"   # Inner Card Surface / Input
COLOR_SURFACE_HOVER = "#242730"   # Surface Hover State
COLOR_BORDER = "#272A33"          # Subtle Structural Border
COLOR_BORDER_LIGHT = "#3E4352"    # Highlight Border
COLOR_ACCENT = "#FFFFFF"          # Crisp Glyph White Accent
COLOR_ACCENT_HOVER = "#E2E8F0"    # Light Silver Hover
COLOR_ACCENT_MUTED = "#1E222A"    # Subtle Container Tint
COLOR_TEXT_PRIMARY = "#FFFFFF"    # Crisp White (High Emphasis)
COLOR_TEXT_SECONDARY = "#94A3B8"  # Soft Slate (Medium Emphasis)
COLOR_TEXT_MUTED = "#64748B"      # Disabled / Helper Text
COLOR_WHITE = "#FFFFFF"

FONT_FAMILY = "Segoe UI Variable Display" if sys.platform == "win32" else "Inter"
FONT_MONO = "Consolas" if sys.platform == "win32" else "Menlo"
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
                        theme="white", clockwise=True, speed_mult=1.0, fan_idx=0, total_fans=1,
                        spectrum=None, decay_rate=0.85, custom_color=None):
        now = time.perf_counter()
        dt = min(now - self.last_time, 0.08)
        self.last_time = now
        num_leds = max(4, num_leds)

        if theme in ("white", "glyph_white"):
            base_rgb = (255, 255, 255)
        elif theme in ("custom", "Custom Spectrum Color..."):
            base_rgb = custom_color or (255, 255, 255)
        elif theme == "cyan":
            base_rgb = (0, 210, 255)
        elif theme == "purple":
            base_rgb = (168, 85, 247)
        elif theme == "orange":
            base_rgb = (249, 115, 22)
        elif theme == "magenta":
            base_rgb = (236, 72, 153)
        elif theme == "ice_blue":
            base_rgb = (56, 189, 248)
        elif theme == "violet":
            base_rgb = (139, 92, 246)
        elif theme == "rainbow":
            hue = (now * 0.35 + fan_idx * (1.0 / max(1, total_fans)) + pulse * 0.15) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.95, 1.0)
            base_rgb = (int(r * 255), int(g * 255), int(b * 255))
        elif isinstance(theme, (tuple, list)) and len(theme) == 3:
            base_rgb = tuple(int(c) for c in theme)
        else:
            base_rgb = custom_color or (255, 255, 255)

        def get_meter_color(frac):
            if base_rgb == (255, 255, 255) or base_rgb is None:
                val = int(100 + frac * 155)
                return (val, val, val)
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
            speed = (2.5 + energy * 9.0 + pulse * 7.0) * speed_mult
            dir_mult = 1.0 if clockwise else -1.0
            self.angle = (self.angle + dir_mult * speed * dt) % (2.0 * math.pi)
            fan_angle = (self.angle + fan_idx * (0.5 * math.pi)) % (2.0 * math.pi)

            tail_len = math.pi * 1.2
            s_rgb = base_rgb or (255, 255, 255)
            for i in range(num_leds):
                phi = (2.0 * math.pi * i) / num_leds
                diff = (fan_angle - phi) % (2.0 * math.pi) if clockwise else (phi - fan_angle) % (2.0 * math.pi)
                brightness = (1.0 - diff / tail_len) ** 1.6 if diff <= tail_len else 0.0
                brightness = float(np.clip(brightness + pulse * 0.45, 0.0, 1.0))
                colors.append((int(s_rgb[0] * brightness),
                               int(s_rgb[1] * brightness),
                               int(s_rgb[2] * brightness)))

        elif mode == "vu_meter":
            target = float(np.clip(raw_level, 0.0, 1.0))
            if target > self.vu_level:
                self.vu_level = self.vu_level * 0.25 + target * 0.75
            else:
                d = float(np.clip(decay_rate, 0.75, 0.98))
                self.vu_level = max(0.0, self.vu_level * d)

            active_count = int(round(self.vu_level * num_leds))
            if active_count >= self.peak_led:
                self.peak_led = float(active_count)
                self.peak_hold_until = now + 0.25
            elif now > self.peak_hold_until:
                self.peak_led = max(0.0, self.peak_led - 14.0 * dt)

            peak_idx = int(self.peak_led)

            for i in range(num_leds):
                fill_idx = i if clockwise else ((num_leds - i) % num_leds)
                if fill_idx < active_count:
                    frac = fill_idx / max(1, num_leds - 1)
                    colors.append(get_meter_color(frac))
                elif fill_idx == peak_idx and peak_idx > 0:
                    colors.append((255, 255, 255))
                else:
                    colors.append((0, 0, 0))

        elif mode == "vu_meter_dual":
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
                (255, 255, 255),  # Bass: Glyph White
                (0, 210, 255),    # Mids: Cyber Cyan
                (168, 85, 247)    # Treble: Electric Purple
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

                b_col = band_colors[b_idx] if base_rgb == (255, 255, 255) else base_rgb
                for i in range(num_leds):
                    fill_idx = i if clockwise else ((num_leds - i) % num_leds)
                    if fill_idx < b_active:
                        frac = fill_idx / max(1, num_leds - 1)
                        colors.append(get_meter_color(frac))
                    elif fill_idx == b_peak and b_peak > 0:
                        colors.append((255, 255, 255))
                    else:
                        colors.append((0, 0, 0))
            else:
                sector_size = num_leds // 3
                for i in range(num_leds):
                    s_idx = min(2, i // max(1, sector_size))
                    offset_in_sector = i - s_idx * sector_size
                    sec_len = sector_size if s_idx < 2 else (num_leds - 2 * sector_size)
                    b_active = int(round(band_targets[s_idx] * sec_len))
                    if offset_in_sector < b_active:
                        b_col = band_colors[s_idx] if base_rgb == (255, 255, 255) else base_rgb
                        colors.append(b_col)
                    else:
                        colors.append((0, 0, 0))

        elif mode == "ripple":
            self.ripple_phase = (self.ripple_phase + (3.0 + pulse * 6.0) * dt) % (total_fans + 1)
            dist = abs(self.ripple_phase - fan_idx)
            wave_int = max(float(np.clip(1.0 - dist, 0.0, 1.0)) ** 2.0, pulse * 0.3)
            r_rgb = base_rgb or (255, 255, 255)
            for i in range(num_leds):
                colors.append((int(r_rgb[0] * wave_int),
                               int(r_rgb[1] * wave_int),
                               int(r_rgb[2] * wave_int)))

        else:  # "pulse"
            p = float(np.clip(pulse * 1.2, 0.0, 1.0))
            p_rgb = base_rgb or (255, 255, 255)
            for i in range(num_leds):
                colors.append((int(p_rgb[0] * p),
                               int(p_rgb[1] * p),
                               int(p_rgb[2] * p)))

        return colors

THEME_PRESET_COLORS = {
    "Glyph White": (255, 255, 255),
    "Cyber Cyan": (0, 210, 255),
    "Electric Purple": (168, 85, 247),
    "Solar Orange": (249, 115, 22),
    "Neon Magenta": (236, 72, 153),
    "Ice Blue": (56, 189, 248),
    "Deep Violet": (139, 92, 246),
    "Reactive Rainbow": (255, 0, 0),
    "Custom Spectrum Color...": (255, 255, 255)
}

FAN_THEME_DISPLAY = {
    "Glyph White": "white",
    "Cyber Cyan": "cyan",
    "Electric Purple": "purple",
    "Solar Orange": "orange",
    "Neon Magenta": "magenta",
    "Ice Blue": "ice_blue",
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
        self.selected_rgb = (255, 255, 255)
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

        non_fan_keywords = ("jrgb", "12v", "onboard", "audio", "pcie", "io_cover", "chipset", "pch", "logo")
        if any(ex in zname for ex in non_fan_keywords):
            return False

        if getattr(zone, "type", None) == 0 and len(getattr(zone, "leds", [])) <= 1:
            return False

        if dev_type in (DeviceType.COOLER, DeviceType.CASE, DeviceType.LEDSTRIP, DeviceType.ACCESSORY, DeviceType.DRAM):
            return True

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

            elif d_type in (DeviceType.COOLER, DeviceType.CASE, DeviceType.LEDSTRIP, DeviceType.ACCESSORY):
                for z in zones:
                    if self.is_fan_zone(z, dev):
                        connected_rgb.append(f"{d_short}: {z.name} (ARGB)")

            elif d_type == DeviceType.DRAM:
                connected_rgb.append(f"RAM: {d_name} (DRAM)")

            elif d_type == DeviceType.GPU:
                for z in zones:
                    if self.is_fan_zone(z, dev):
                        connected_rgb.append(f"GPU: {d_short} {z.name} (ARGB)")

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

    def connect(self, auto_launch=True):
        if not OPENRGB_AVAILABLE:
            if self.logger: self.logger("OpenRGB: 'openrgb-python' SDK library is not installed.")
            return False

        if not is_port_open("127.0.0.1", OPENRGB_PORT, timeout=0.3):
            if auto_launch:
                if self.logger: self.logger("OpenRGB: Server not detected on port 6742. Auto-launching OpenRGB as Administrator...")
                started = launch_openrgb_elevated(logger=self.logger)
                if started:
                    start_t = time.time()
                    connected_to_port = False
                    while time.time() - start_t < 8.0:
                        if is_port_open("127.0.0.1", OPENRGB_PORT, timeout=0.4):
                            connected_to_port = True
                            if self.logger: self.logger("OpenRGB: SDK server detected online on port 6742!")
                            time.sleep(0.4)
                            break
                        time.sleep(0.5)
                    if not connected_to_port:
                        if self.logger: self.logger("OpenRGB: Timed out waiting for OpenRGB server to start on port 6742.")
            else:
                if self.logger: self.logger("OpenRGB: SDK server is not listening on port 6742.")

        try:
            self.client = OpenRGBClient("localhost", OPENRGB_PORT)
            self.devices = self.client.devices
            for dev in self.devices:
                for mode in getattr(dev, "modes", []):
                    if mode.name.lower() in ("direct", "custom", "static"):
                        try:
                            dev.set_mode(mode)
                        except Exception:
                            pass
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
                self.logger(f"OpenRGB: Successfully connected to {len(self.devices)} device(s) ({fan_count} ARGB controller(s)).")
            return True
        except Exception as e:
            self.connected = False
            if self.logger: self.logger(f"OpenRGB Connection Error: {e}")
            return False

    def sync(self, r, g, b, raw_audio_level=0.0, energy=0.0, pulse=0.0, spectrum=None,
             fan_viz_enabled=True, fan_mode="vu_meter", fan_theme="white",
             fan_clockwise=True, fan_speed=1.0, fan_leds=16, fan_count=1, decay_rate=0.85,
             custom_color=(255, 255, 255)):
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
            if ip and not ip.startswith('127.'):
                parts = ip.split('.')
                if len(parts) == 4:
                    broadcasts.add(".".join(parts[:-1] + ["255"]))
    except Exception: pass

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        if local_ip and not local_ip.startswith('127.'):
            parts = local_ip.split('.')
            if len(parts) == 4:
                broadcasts.add(".".join(parts[:-1] + ["255"]))
    except Exception: pass

    return list(broadcasts)

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

# ==============================================================================
# MAIN APPLICATION WINDOW (MONOCHROME / NOTHING OS STYLE)
# ==============================================================================
class CompanionApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("GLYPHIX // DESKTOP COMPANION")
        self.geometry("640x760")
        self.minsize(560, 660)
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
        self.use_openrgb = ctk.BooleanVar(value=False)
        self.typing_suppression = ctk.BooleanVar(value=True)
        self.rgb_sensitivity = ctk.DoubleVar(value=1.0)
        self.rgb_decay = ctk.DoubleVar(value=0.82)
        self.selected_rgb = (255, 255, 255)
        
        self.viz_dots = []
        self.viz_queue = queue.Queue(maxsize=1)
        self._viz_cache = []
        self._anim_phase = 0.0
        self._last_packet_time = 0.0
        
        self.wasapi_devices = get_wasapi_devices()
        self.spectrum_points = [0.0] * 64
        self.viz_peaks = [0.0] * 64
        self.viz_peak_holds = [0.0] * 64
        self.log_queue = queue.Queue()
        self.level_queue = queue.Queue()
        
        # Case Fan Visualization & Per-Header Configuration
        self.current_header = ctk.StringVar(value="All Connected ARGB (Sync All)")
        self.detected_headers = ["All Connected ARGB (Sync All)"]
        self.custom_hex = "#FFFFFF"
        self.custom_rgb = (255, 255, 255)
        self.header_configs = {
            "All Connected ARGB (Sync All)": {
                "mode": "Radial VU Meter (Full Ring)",
                "theme": "Glyph White",
                "custom_color": (255, 255, 255),
                "custom_hex": "#FFFFFF",
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
        self.fan_theme_str = ctk.StringVar(value="Glyph White")
        self.fan_led_count_str = ctk.StringVar(value="16 LEDs (Standard)")
        self.fan_count_str = ctk.StringVar(value="1 Fan")
        self.fan_clockwise = ctk.BooleanVar(value=True)
        self.fan_speed = ctk.DoubleVar(value=1.0)
        self.fan_preview_queue = queue.Queue(maxsize=1)
        self.fan_preview_dots = []
        self._fan_preview_cache = []
        self.fan_preview_angle = 0.0
        self.fan_pulse_glow = 0.0
        self.fan_ripple_r = 0.0
        self.fan_ripple_alpha = 0.0
        self.discovery_anim_counter = 0

        self.rgb_manager = OpenRGBManager(logger=self.log)
        self.rgb_manager.on_connected_callback = lambda hdrs: self.after(0, self._on_openrgb_headers_detected, hdrs)
        self.hook_watcher = KeyboardHookWatcher(self._on_key_press)
        self.last_key_time = 0.0

        self._setup_ui()
        self._refresh_audio_sources()
        self._on_direction_changed("📥 Phone → PC (Sync PC RGB)")
        self._start_pc_discovery_responder()
        self._update_loop()

    # ==========================================
    # UI SETUP & MATERIAL 3 MONOCHROME BUILDER
    # ==========================================
    def _setup_ui(self):
        # 1. TOP APP BAR / BRAND HEADER
        self.header = ctk.CTkFrame(self, fg_color=COLOR_BG, corner_radius=0, height=72)
        self.header.grid(row=0, column=0, sticky="ew", padx=24, pady=(12, 4))
        self.header.grid_columnconfigure(0, weight=1)

        brand_frame = ctk.CTkFrame(self.header, fg_color="transparent")
        brand_frame.pack(side="left", pady=8)

        logo_row = ctk.CTkFrame(brand_frame, fg_color="transparent")
        logo_row.pack(anchor="w")

        self.logo_label = ctk.CTkLabel(
            logo_row, text="GLYPHIX",
            font=ctk.CTkFont(family=FONT_LOGO, size=24, weight="bold"),
            text_color=COLOR_TEXT_PRIMARY
        )
        self.logo_label.pack(side="left")

        self.ver_badge = ctk.CTkLabel(
            logo_row, text=" DESKTOP",
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        )
        self.ver_badge.pack(side="left", padx=(4, 0), pady=(4, 0))

        self.sub_label = ctk.CTkLabel(
            brand_frame, text="AUDIO REACTIVE GLYPH & ARGB SYNC ENGINE",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_MUTED
        )
        self.sub_label.pack(anchor="w", pady=(1, 0))

        # Status Capsule Pill
        self.status_capsule = ctk.CTkFrame(
            self.header, fg_color=COLOR_SURFACE,
            corner_radius=20, border_color=COLOR_BORDER, border_width=1
        )
        self.status_capsule.pack(side="right", pady=14, padx=(0, 4))

        self.status_dot = ctk.CTkLabel(
            self.status_capsule, text="●",
            font=ctk.CTkFont(size=12), text_color=COLOR_TEXT_MUTED
        )
        self.status_dot.pack(side="left", padx=(12, 6), pady=6)

        self.status_pill = ctk.CTkLabel(
            self.status_capsule, text="STANDBY",
            text_color=COLOR_TEXT_SECONDARY,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold")
        )
        self.status_pill.pack(side="left", padx=(0, 14), pady=6)

        # 2. MAIN SCROLLABLE CONTAINER
        self.main_container = ctk.CTkScrollableFrame(
            self, fg_color=COLOR_BG, corner_radius=0,
            scrollbar_button_color=COLOR_BORDER,
            scrollbar_button_hover_color=COLOR_BORDER_LIGHT
        )
        self.main_container.grid(row=1, column=0, sticky="nsew", padx=12, pady=(0, 4))

        # CARD 1: SYNC DIRECTION
        self._create_card(self.main_container, "SYNC DIRECTION", "Choose audio flow between Nothing Phone & PC")
        
        self.dir_segmented = ctk.CTkSegmentedButton(
            self.last_card_body,
            values=["📥 Phone → PC (Sync PC RGB)", "📤 PC → Phone (Glyphs)"],
            command=self._on_direction_changed,
            selected_color=COLOR_ACCENT,
            selected_hover_color=COLOR_ACCENT_HOVER,
            unselected_color=COLOR_SURFACE_INNER,
            unselected_hover_color=COLOR_SURFACE_HOVER,
            text_color="#000000",
            font=ctk.CTkFont(family=FONT_FAMILY, size=12, weight="bold"),
            height=38, corner_radius=10
        )
        self.dir_segmented.set("📥 Phone → PC (Sync PC RGB)")
        self.dir_segmented.pack(fill="x", pady=(0, 4))

        # CARD 2: AUDIO SOURCE & SPECTRUM VISUALIZER
        self._create_card(self.main_container, "AUDIO SPECTRUM & SOURCE", "Real-time FFT audio visualizer & loopback input")
        
        self.audio_combo = ctk.CTkOptionMenu(
            self.last_card_body, values=[],
            fg_color=COLOR_SURFACE_INNER,
            button_color=COLOR_BORDER,
            button_hover_color=COLOR_SURFACE_HOVER,
            dropdown_fg_color=COLOR_SURFACE_INNER,
            dropdown_hover_color=COLOR_SURFACE_HOVER,
            dropdown_text_color=COLOR_TEXT_PRIMARY,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8, height=36,
            font=ctk.CTkFont(family=FONT_FAMILY, size=12)
        )
        self.audio_combo.pack(fill="x", pady=(0, 10))

        viz_box = ctk.CTkFrame(
            self.last_card_body, fg_color="#08090B",
            corner_radius=10, border_color=COLOR_BORDER, border_width=1
        )
        viz_box.pack(fill="x", pady=(0, 4))

        self.viz_canvas = ctk.CTkCanvas(
            viz_box, height=92, bg="#08090B", highlightthickness=0
        )
        self.viz_canvas.pack(fill="x", padx=10, pady=(10, 4))
        self.viz_canvas.bind("<Configure>", self._resize_viz)

        freq_row = ctk.CTkFrame(viz_box, fg_color="transparent")
        freq_row.pack(fill="x", padx=14, pady=(0, 8))
        
        for band in ("SUB-BASS", "BASS", "LOW-MID", "MID", "PRESENCE", "BRILLIANCE"):
            ctk.CTkLabel(
                freq_row, text=band,
                font=ctk.CTkFont(family=FONT_FAMILY, size=8, weight="bold"),
                text_color=COLOR_TEXT_MUTED
            ).pack(side="left", expand=True)

        # CARD 3: WI-FI CONNECTIVITY (UDP ONLY - NO BLUETOOTH)
        self._create_card(self.main_container, "WI-FI CONNECTIVITY", "Ultra-fast low latency UDP audio streaming & discovery")

        self.pc_ip_card = ctk.CTkFrame(
            self.last_card_body, fg_color=COLOR_SURFACE_INNER,
            corner_radius=10, border_color=COLOR_BORDER, border_width=1
        )
        self.pc_ip_card.pack(fill="x", pady=(0, 4))

        ip_info_frame = ctk.CTkFrame(self.pc_ip_card, fg_color="transparent")
        ip_info_frame.pack(side="left", padx=14, pady=10)

        ctk.CTkLabel(
            ip_info_frame, text="YOUR PC IP (PORT 12347)",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_MUTED
        ).pack(anchor="w")

        self.pc_ip_label = ctk.CTkLabel(
            ip_info_frame, text=f"{self.local_pc_ip} : 12347",
            font=ctk.CTkFont(family=FONT_MONO, size=15, weight="bold"),
            text_color=COLOR_TEXT_PRIMARY
        )
        self.pc_ip_label.pack(anchor="w")

        self.copy_ip_btn = ctk.CTkButton(
            self.pc_ip_card, text="COPY IP", width=84, height=32,
            fg_color=COLOR_SURFACE_HOVER, hover_color=COLOR_BORDER_LIGHT,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold"),
            command=self._copy_pc_ip
        )
        self.copy_ip_btn.pack(side="right", padx=12, pady=10)

        self.pc_to_phone_frame = ctk.CTkFrame(self.last_card_body, fg_color="transparent")

        addr_row = ctk.CTkFrame(self.pc_to_phone_frame, fg_color="transparent")
        addr_row.pack(fill="x", pady=(0, 4))

        self.addr_entry = ctk.CTkEntry(
            addr_row, placeholder_text="Enter Phone IP Address (e.g. 192.168.1.55)",
            fg_color=COLOR_SURFACE_INNER, border_color=COLOR_BORDER,
            text_color=COLOR_TEXT_PRIMARY,
            placeholder_text_color=COLOR_TEXT_MUTED,
            height=38, corner_radius=8,
            font=ctk.CTkFont(family=FONT_MONO, size=12)
        )
        self.addr_entry.pack(side="left", fill="x", expand=True, padx=(0, 8))

        self.discover_btn = ctk.CTkButton(
            addr_row, text="🔍 DISCOVER", width=105, height=38,
            fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY,
            hover_color=COLOR_BORDER_LIGHT, corner_radius=8,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold"),
            command=self._toggle_discovery
        )
        self.discover_btn.pack(side="right")

        # CARD 4: HARDWARE SYNC & AUDIO DSP
        self._create_card(self.main_container, "HARDWARE SYNC & AUDIO DSP", "OpenRGB lighting sync and transient response")

        switches_row = ctk.CTkFrame(self.last_card_body, fg_color="transparent")
        switches_row.pack(fill="x", pady=(0, 12))

        self.openrgb_switch = ctk.CTkSwitch(
            switches_row, text="OpenRGB Sync", variable=self.use_openrgb,
            progress_color=COLOR_WHITE, button_color=COLOR_WHITE,
            button_hover_color=COLOR_ACCENT_HOVER,
            font=ctk.CTkFont(family=FONT_FAMILY, size=12, weight="bold"),
            command=self._toggle_openrgb
        )
        self.openrgb_switch.pack(side="left", padx=(0, 24))

        self.typing_switch = ctk.CTkSwitch(
            switches_row, text="Typing Suppression", variable=self.typing_suppression,
            progress_color=COLOR_WHITE, button_color=COLOR_WHITE,
            button_hover_color=COLOR_ACCENT_HOVER,
            font=ctk.CTkFont(family=FONT_FAMILY, size=12, weight="bold")
        )
        self.typing_switch.pack(side="left")

        sens_hdr = ctk.CTkFrame(self.last_card_body, fg_color="transparent")
        sens_hdr.pack(fill="x", pady=(2, 0))
        ctk.CTkLabel(
            sens_hdr, text="AUDIO SENSITIVITY",
            font=ctk.CTkFont(family=FONT_FAMILY, size=10, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(side="left")
        self.sens_val_lbl = ctk.CTkLabel(
            sens_hdr, text=f"{self.rgb_sensitivity.get():.2f}x",
            font=ctk.CTkFont(family=FONT_MONO, size=11, weight="bold"),
            text_color=COLOR_TEXT_PRIMARY
        )
        self.sens_val_lbl.pack(side="right")

        self.sens_slider = ctk.CTkSlider(
            self.last_card_body, from_=0.1, to=3.0, variable=self.rgb_sensitivity,
            button_color=COLOR_WHITE, button_hover_color=COLOR_ACCENT_HOVER,
            progress_color=COLOR_WHITE, fg_color=COLOR_SURFACE_INNER,
            command=self._on_sens_slider_changed
        )
        self.sens_slider.pack(fill="x", pady=(2, 10))

        decay_hdr = ctk.CTkFrame(self.last_card_body, fg_color="transparent")
        decay_hdr.pack(fill="x", pady=(2, 0))
        ctk.CTkLabel(
            decay_hdr, text="PULSE DECAY / BALLISTICS",
            font=ctk.CTkFont(family=FONT_FAMILY, size=10, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(side="left")
        self.decay_val_lbl = ctk.CTkLabel(
            decay_hdr, text=f"{int(self.rgb_decay.get() * 100)}%",
            font=ctk.CTkFont(family=FONT_MONO, size=11, weight="bold"),
            text_color=COLOR_TEXT_PRIMARY
        )
        self.decay_val_lbl.pack(side="right")

        self.decay_slider = ctk.CTkSlider(
            self.last_card_body, from_=0.5, to=0.99, variable=self.rgb_decay,
            button_color=COLOR_WHITE, button_hover_color=COLOR_ACCENT_HOVER,
            progress_color=COLOR_WHITE, fg_color=COLOR_SURFACE_INNER,
            command=self._on_decay_slider_changed
        )
        self.decay_slider.pack(fill="x", pady=(2, 4))

        # CARD 5: CASE FAN ARGB VISUALIZATION
        self._create_card(self.main_container, "CASE FAN ARGB VISUALIZATION", "Per-header radial lighting effects & live ring preview")

        fan_sw_row = ctk.CTkFrame(self.last_card_body, fg_color="transparent")
        fan_sw_row.pack(fill="x", pady=(0, 10))

        self.fan_enable_switch = ctk.CTkSwitch(
            fan_sw_row, text="Enable Fan Ring FX", variable=self.fan_viz_enabled,
            progress_color=COLOR_WHITE, button_color=COLOR_WHITE,
            button_hover_color=COLOR_ACCENT_HOVER,
            font=ctk.CTkFont(family=FONT_FAMILY, size=12, weight="bold"),
            command=self._on_fan_config_changed
        )
        self.fan_enable_switch.pack(side="left", padx=(0, 24))

        self.fan_clockwise_switch = ctk.CTkSwitch(
            fan_sw_row, text="Clockwise", variable=self.fan_clockwise,
            progress_color=COLOR_WHITE, button_color=COLOR_WHITE,
            button_hover_color=COLOR_ACCENT_HOVER,
            font=ctk.CTkFont(family=FONT_FAMILY, size=12, weight="bold"),
            command=self._on_fan_config_changed
        )
        self.fan_clockwise_switch.pack(side="left")

        fan_body = ctk.CTkFrame(self.last_card_body, fg_color="transparent")
        fan_body.pack(fill="x", pady=(0, 6))

        fan_left = ctk.CTkFrame(fan_body, fg_color="transparent")
        fan_left.pack(side="left", fill="x", expand=True, padx=(0, 14))

        hdr_top_row = ctk.CTkFrame(fan_left, fg_color="transparent")
        hdr_top_row.pack(fill="x", pady=(0, 2))
        ctk.CTkLabel(
            hdr_top_row, text="CONNECTED ARGB HEADER",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(side="left")

        self.rescan_btn = ctk.CTkButton(
            hdr_top_row, text="↻ SCAN RGB", width=80, height=22,
            fg_color=COLOR_SURFACE_INNER, hover_color=COLOR_SURFACE_HOVER,
            text_color=COLOR_TEXT_PRIMARY, corner_radius=6,
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            command=self._rescan_rgb
        )
        self.rescan_btn.pack(side="right")

        self.header_combo = ctk.CTkOptionMenu(
            fan_left, values=self.detected_headers,
            variable=self.current_header,
            fg_color=COLOR_SURFACE_INNER, button_color=COLOR_BORDER,
            button_hover_color=COLOR_BORDER_LIGHT,
            dropdown_fg_color=COLOR_SURFACE_INNER,
            dropdown_hover_color=COLOR_SURFACE_HOVER,
            dropdown_text_color=COLOR_TEXT_PRIMARY,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8, height=32,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold"),
            command=self._on_target_header_selected
        )
        self.header_combo.pack(fill="x", pady=(2, 8))

        ctk.CTkLabel(
            fan_left, text="ANIMATION EFFECT",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(anchor="w")

        self.fan_mode_menu = ctk.CTkOptionMenu(
            fan_left, values=list(FAN_MODE_DISPLAY.keys()),
            variable=self.fan_mode_str,
            fg_color=COLOR_SURFACE_INNER, button_color=COLOR_BORDER,
            button_hover_color=COLOR_BORDER_LIGHT,
            dropdown_fg_color=COLOR_SURFACE_INNER,
            dropdown_hover_color=COLOR_SURFACE_HOVER,
            dropdown_text_color=COLOR_TEXT_PRIMARY,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8, height=32,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11),
            command=self._on_fan_config_changed
        )
        self.fan_mode_menu.pack(fill="x", pady=(2, 8))

        ctk.CTkLabel(
            fan_left, text="COLOR THEME",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(anchor="w")

        self.fan_theme_menu = ctk.CTkOptionMenu(
            fan_left, values=list(FAN_THEME_DISPLAY.keys()),
            variable=self.fan_theme_str,
            fg_color=COLOR_SURFACE_INNER, button_color=COLOR_BORDER,
            button_hover_color=COLOR_BORDER_LIGHT,
            dropdown_fg_color=COLOR_SURFACE_INNER,
            dropdown_hover_color=COLOR_SURFACE_HOVER,
            dropdown_text_color=COLOR_TEXT_PRIMARY,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8, height=32,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11),
            command=self._on_fan_theme_changed
        )
        self.fan_theme_menu.pack(fill="x", pady=(2, 8))

        swatch_row = ctk.CTkFrame(fan_left, fg_color="transparent")
        swatch_row.pack(fill="x", pady=(0, 8))

        self.spectrum_btn = ctk.CTkButton(
            swatch_row, text="🎨 SPECTRUM CHOOSER",
            height=30, fg_color=COLOR_SURFACE_INNER, hover_color=COLOR_SURFACE_HOVER,
            text_color=COLOR_TEXT_PRIMARY, corner_radius=8,
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold"),
            command=self._open_spectrum_chooser
        )
        self.spectrum_btn.pack(side="left", fill="x", expand=True, padx=(0, 6))

        self.color_swatch_btn = ctk.CTkButton(
            swatch_row, text=self.custom_hex, width=82, height=30,
            fg_color=self.custom_hex, hover_color=self.custom_hex,
            text_color="#000000", corner_radius=8,
            font=ctk.CTkFont(family=FONT_MONO, size=11, weight="bold"),
            command=self._open_spectrum_chooser
        )
        self.color_swatch_btn.pack(side="right")

        counts_row = ctk.CTkFrame(fan_left, fg_color="transparent")
        counts_row.pack(fill="x", pady=(0, 8))

        led_col = ctk.CTkFrame(counts_row, fg_color="transparent")
        led_col.pack(side="left", fill="x", expand=True, padx=(0, 4))
        ctk.CTkLabel(
            led_col, text="RING LEDS",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(anchor="w")
        self.fan_leds_menu = ctk.CTkOptionMenu(
            led_col, values=list(FAN_LEDS_DISPLAY.keys()),
            variable=self.fan_led_count_str,
            fg_color=COLOR_SURFACE_INNER, button_color=COLOR_BORDER,
            button_hover_color=COLOR_BORDER_LIGHT,
            dropdown_fg_color=COLOR_SURFACE_INNER,
            dropdown_hover_color=COLOR_SURFACE_HOVER,
            dropdown_text_color=COLOR_TEXT_PRIMARY,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8, height=30,
            font=ctk.CTkFont(family=FONT_FAMILY, size=10),
            command=self._on_fan_config_changed
        )
        self.fan_leds_menu.pack(fill="x", pady=(2, 0))

        fan_cnt_col = ctk.CTkFrame(counts_row, fg_color="transparent")
        fan_cnt_col.pack(side="right", fill="x", expand=True, padx=(4, 0))
        ctk.CTkLabel(
            fan_cnt_col, text="FAN COUNT",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(anchor="w")
        self.fan_count_menu = ctk.CTkOptionMenu(
            fan_cnt_col, values=list(FAN_COUNT_DISPLAY.keys()),
            variable=self.fan_count_str,
            fg_color=COLOR_SURFACE_INNER, button_color=COLOR_BORDER,
            button_hover_color=COLOR_BORDER_LIGHT,
            dropdown_fg_color=COLOR_SURFACE_INNER,
            dropdown_hover_color=COLOR_SURFACE_HOVER,
            dropdown_text_color=COLOR_TEXT_PRIMARY,
            text_color=COLOR_TEXT_PRIMARY,
            corner_radius=8, height=30,
            font=ctk.CTkFont(family=FONT_FAMILY, size=10),
            command=self._on_fan_config_changed
        )
        self.fan_count_menu.pack(fill="x", pady=(2, 0))

        speed_hdr = ctk.CTkFrame(fan_left, fg_color="transparent")
        speed_hdr.pack(fill="x", pady=(2, 0))
        ctk.CTkLabel(
            speed_hdr, text="ROTATION / WAVE SPEED",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        ).pack(side="left")
        self.speed_val_lbl = ctk.CTkLabel(
            speed_hdr, text=f"{self.fan_speed.get():.2f}x",
            font=ctk.CTkFont(family=FONT_MONO, size=10, weight="bold"),
            text_color=COLOR_TEXT_PRIMARY
        )
        self.speed_val_lbl.pack(side="right")

        self.speed_slider = ctk.CTkSlider(
            fan_left, from_=0.2, to=3.0, variable=self.fan_speed,
            button_color=COLOR_WHITE, button_hover_color=COLOR_ACCENT_HOVER,
            progress_color=COLOR_WHITE, fg_color=COLOR_SURFACE_INNER,
            command=self._on_speed_slider_changed
        )
        self.speed_slider.pack(fill="x", pady=(2, 0))

        fan_right_box = ctk.CTkFrame(
            fan_body, fg_color="#08090B",
            corner_radius=12, border_color=COLOR_BORDER, border_width=1
        )
        fan_right_box.pack(side="right", padx=(4, 0), pady=0)

        ctk.CTkLabel(
            fan_right_box, text="LIVE ARGB PREVIEW",
            font=ctk.CTkFont(family=FONT_FAMILY, size=9, weight="bold"),
            text_color=COLOR_TEXT_MUTED
        ).pack(pady=(8, 0))

        self.fan_canvas = ctk.CTkCanvas(
            fan_right_box, width=142, height=142, bg="#08090B", highlightthickness=0
        )
        self.fan_canvas.pack(padx=12, pady=(4, 12))
        self._init_fan_preview_canvas()

        # CARD 6: ADVANCED LOGS
        self.adv_btn = ctk.CTkButton(
            self.main_container, text="▼ SHOW SYSTEM LOGS",
            fg_color="transparent", text_color=COLOR_TEXT_MUTED,
            hover_color=COLOR_SURFACE, corner_radius=8, height=28,
            font=ctk.CTkFont(family=FONT_FAMILY, size=10, weight="bold"),
            command=self._toggle_advanced
        )
        self.adv_btn.pack(pady=(4, 12))

        self.console = ctk.CTkTextbox(
            self.main_container, height=120, fg_color="#08090B",
            border_color=COLOR_BORDER, border_width=1, corner_radius=8,
            font=ctk.CTkFont(family=FONT_MONO, size=10),
            text_color=COLOR_TEXT_SECONDARY
        )
        self.console.configure(state="disabled")

        # ==============================================================================
        # 3. FLOATING ACTION FOOTER (CRISP WHITE FAB)
        # ==============================================================================
        self.footer = ctk.CTkFrame(self, fg_color=COLOR_BG, height=84, corner_radius=0)
        self.footer.grid(row=2, column=0, sticky="ew", padx=20, pady=(4, 16))

        self.stream_btn = ctk.CTkButton(
            self.footer, text="▶ START LISTENER (SYNC PC RGB)",
            height=54, corner_radius=16,
            fg_color=COLOR_WHITE, hover_color=COLOR_ACCENT_HOVER,
            text_color="#000000",
            font=ctk.CTkFont(family=FONT_FAMILY, size=13, weight="bold"),
            command=self._toggle_streaming
        )
        self.stream_btn.pack(fill="x")

    # ==========================================
    # CARD FACTORY HELPER
    # ==========================================
    def _create_card(self, parent, title, subtitle=None):
        card = ctk.CTkFrame(
            parent, fg_color=COLOR_SURFACE,
            corner_radius=14, border_width=1, border_color=COLOR_BORDER
        )
        card.pack(fill="x", padx=12, pady=7)

        header_row = ctk.CTkFrame(card, fg_color="transparent")
        header_row.pack(fill="x", padx=18, pady=(14, 4))

        title_lbl = ctk.CTkLabel(
            header_row, text=title,
            font=ctk.CTkFont(family=FONT_FAMILY, size=10, weight="bold"),
            text_color=COLOR_TEXT_SECONDARY
        )
        title_lbl.pack(anchor="w")

        if subtitle:
            sub_lbl = ctk.CTkLabel(
                header_row, text=subtitle,
                font=ctk.CTkFont(family=FONT_FAMILY, size=9),
                text_color=COLOR_TEXT_MUTED
            )
            sub_lbl.pack(anchor="w", pady=(1, 0))

        body = ctk.CTkFrame(card, fg_color="transparent")
        body.pack(fill="x", padx=18, pady=(4, 16))

        self.last_card = card
        self.last_card_body = body

    # ==========================================
    # EVENT HANDLERS & SLIDER BADGES
    # ==========================================
    def _on_sens_slider_changed(self, val):
        self.sens_val_lbl.configure(text=f"{float(val):.2f}x")

    def _on_decay_slider_changed(self, val):
        self.decay_val_lbl.configure(text=f"{int(float(val) * 100)}%")

    def _on_speed_slider_changed(self, val):
        self.speed_val_lbl.configure(text=f"{float(val):.2f}x")
        self._on_fan_config_changed()

    def _toggle_advanced(self):
        if self.show_advanced.get():
            self.console.pack_forget()
            self.adv_btn.configure(text="▼ SHOW SYSTEM LOGS")
            self.show_advanced.set(False)
        else:
            self.console.pack(fill="x", padx=12, pady=(0, 16))
            self.adv_btn.configure(text="▲ HIDE SYSTEM LOGS")
            self.show_advanced.set(True)

    def log(self, msg):
        self.log_queue.put(msg)

    def _on_key_press(self):
        self.last_key_time = time.perf_counter()

    def _copy_pc_ip(self):
        self.clipboard_clear()
        self.clipboard_append(self.local_pc_ip)
        self.copy_ip_btn.configure(text="COPIED!", fg_color=COLOR_WHITE, text_color="#000000")
        self.after(1600, lambda: self.copy_ip_btn.configure(text="COPY IP", fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY))

    def _on_direction_changed(self, selected_val=None):
        if selected_val and "Phone → PC" in selected_val:
            self.direction.set("PHONE_TO_PC")
        elif selected_val and "PC → Phone" in selected_val:
            self.direction.set("PC_TO_PHONE")

        is_phone_to_pc = (self.direction.get() == "PHONE_TO_PC")
        if is_phone_to_pc:
            self.pc_ip_card.pack(fill="x", pady=(0, 4))
            self.pc_to_phone_frame.pack_forget()
            self.audio_combo.configure(state="disabled")
            if not self.is_streaming:
                self.stream_btn.configure(text="▶ START LISTENER (SYNC PC RGB)", fg_color=COLOR_WHITE, text_color="#000000")
        else:
            self.pc_ip_card.pack_forget()
            self.pc_to_phone_frame.pack(fill="x", pady=(0, 4))
            self.audio_combo.configure(state="normal")
            if not self.is_streaming:
                self.stream_btn.configure(text="▶ START STREAMING TO PHONE", fg_color=COLOR_WHITE, text_color="#000000")

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
            title="GLYPHIX - ARGB Spectrum Chooser"
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
            self.fan_theme_str.set(cfg.get("theme_str", "Glyph White"))
            self.fan_led_count_str.set(cfg.get("led_count_str", "16 LEDs (Standard)"))
            self.fan_count_str.set(cfg.get("fan_count_str", "1 Fan"))
            self.fan_speed.set(cfg.get("speed", 1.0))
            self.speed_val_lbl.configure(text=f"{self.fan_speed.get():.2f}x")
            if "custom_hex" in cfg:
                self.custom_hex = cfg["custom_hex"]
            if "custom_color" in cfg:
                self.custom_rgb = cfg["custom_color"]
            self._update_color_swatch(self.custom_hex)
        else:
            self._save_current_header_config()

        hub_text = "FAN 1"
        if selected_header and "sync all" not in selected_header.lower() and not selected_header.startswith("No ") and not selected_header.startswith("OpenRGB"):
            hub_text = selected_header.split()[0][:8]
        if hasattr(self, "fan_hub_text"):
            self.fan_canvas.itemconfig(self.fan_hub_text, text=hub_text)

    def _rescan_rgb(self):
        self.rescan_btn.configure(text="SCANNING...", text_color=COLOR_TEXT_MUTED)
        def _scan():
            if not self.use_openrgb.get():
                self.use_openrgb.set(True)
            connected = self.rgb_manager.connect()
            headers = self.rgb_manager.get_detected_headers()
            self.after(0, lambda: self._on_rescan_done(headers, connected))
        threading.Thread(target=_scan, daemon=True).start()

    def _on_rescan_done(self, headers, connected):
        self.rescan_btn.configure(text="↻ SCAN RGB", text_color=COLOR_TEXT_PRIMARY)
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
        theme_val = FAN_THEME_DISPLAY.get(self.fan_theme_str.get(), "white")
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

    # ==========================================
    # UDP AUTO-DISCOVERY ENGINE
    # ==========================================
    def _toggle_discovery(self):
        if self.is_discovering:
            self.stop_discovery_event.set()
        else:
            self.is_discovering = True
            self.stop_discovery_event.clear()
            self.discover_btn.configure(text="CANCEL", fg_color=COLOR_BORDER, text_color=COLOR_TEXT_PRIMARY)
            self.status_pill.configure(text="SEARCHING...", text_color=COLOR_TEXT_PRIMARY)
            self.status_dot.configure(text_color=COLOR_WHITE)
            threading.Thread(target=self._discovery_worker, daemon=True).start()

    def _start_pc_discovery_responder(self):
        threading.Thread(target=self._pc_discovery_responder_loop, daemon=True).start()

    def _pc_discovery_responder_loop(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, "SO_REUSEPORT"):
            try: sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            except: pass
        try:
            sock.bind(("0.0.0.0", DISCOVERY_PORT))
            sock.settimeout(1.0)
            while True:
                try:
                    data, addr = sock.recvfrom(1024)
                    if not data: continue
                    msg = data.decode('utf-8', errors='ignore')
                    if "GLYPHIX" in msg or "DISCOVERY" in msg:
                        resp_msg = f"GLYPHIX_PC_DISCOVERY_RESPONSE:{self.local_pc_ip}".encode('utf-8')
                        sock.sendto(resp_msg, addr)
                        self.log(f"Discovery: Sent PC announcement to {addr[0]} (PC IP: {self.local_pc_ip})")
                except socket.timeout:
                    continue
                except Exception:
                    time.sleep(1)
        except Exception as e:
            self.log(f"Discovery responder error: {e}")
        finally:
            try: sock.close()
            except: pass

    def _discovery_worker(self):
        found = None
        self.log("Broadcasting UDP discovery...")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, "SO_REUSEPORT"):
            try: sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            except: pass
        sock.settimeout(0.3)
        msg = b"GLYPHIX_DISCOVERY_REQUEST"
        addrs = get_broadcast_addresses()
        start = time.time()
        while time.time() - start < 8 and not self.stop_discovery_event.is_set():
            try:
                for a in addrs:
                    try: sock.sendto(msg, (a, DISCOVERY_PORT))
                    except: pass
                for _ in range(5):
                    try:
                        data, addr = sock.recvfrom(1024)
                        if b"GLYPHIX" in data:
                            found = addr[0]
                            break
                    except socket.timeout:
                        break
                    except: pass
                if found:
                    break
                time.sleep(0.2)
            except: continue
        sock.close()
        
        self.after(0, self._on_discovery_done, found)

    def _on_discovery_done(self, found):
        self.is_discovering = False
        self.discover_btn.configure(text="🔍 DISCOVER", fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY)
        if found:
            self.addr_entry.delete(0, "end")
            self.addr_entry.insert(0, found)
            self.status_pill.configure(text="PHONE FOUND", text_color=COLOR_WHITE)
            self.status_dot.configure(text_color=COLOR_WHITE)
            self.log(f"Discovery: Found Nothing Phone at {found}")
        else:
            self.status_pill.configure(text="STANDBY", text_color=COLOR_TEXT_SECONDARY)
            self.status_dot.configure(text_color=COLOR_TEXT_MUTED)
            self.log("Discovery: No Nothing Phone detected on local subnet.")

    # ==========================================
    # STREAMING & LISTENER WORKERS (UDP)
    # ==========================================
    def _toggle_streaming(self):
        if self.is_streaming:
            self.stop_stream_event.set()
        else:
            is_phone_to_pc = (self.direction.get() == "PHONE_TO_PC")
            if is_phone_to_pc:
                self.is_streaming = True
                self.stop_stream_event.clear()
                self.stream_btn.configure(text="⏹ STOP LISTENER", fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY)
                self.status_pill.configure(text="LISTENING :12347", text_color=COLOR_WHITE)
                self.status_dot.configure(text_color=COLOR_WHITE)
                if self.typing_suppression.get(): self.hook_watcher.start()
                threading.Thread(target=self._listener_worker, daemon=True).start()
            else:
                addr = self.addr_entry.get().strip()
                if not addr:
                    messagebox.showerror("Error", "Enter Phone IP Address first.")
                    return
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
                self.stream_btn.configure(text="⏹ STOP STREAMING", fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY)
                self.status_pill.configure(text="STREAMING TO PHONE", text_color=COLOR_WHITE)
                self.status_dot.configure(text_color=COLOR_WHITE)
                
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
                    self._last_packet_time = time.time()
                    if packets == 1:
                        self.after(0, lambda a=addr[0]: self._on_phone_stream_connected(a))
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

        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try: sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 256 * 1024)
        except: pass

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
                
                self._last_packet_time = time.time()
                sock.sendto(data, (addr, port))
                
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
                    
                    rms = float(np.sqrt(np.mean(sf ** 2)))
                    peak_val = float(np.max(np.abs(sf)))
                    sensitivity = self.rgb_sensitivity.get()
                    decay_rate = self.rgb_decay.get()

                    raw_audio_level = float(np.clip((rms * 2.8 * 0.75 + peak_val * 1.5 * 0.25) * sensitivity, 0.0, 1.0))
                    pulse = bass_engine.process(mono, decay=decay_rate) * sensitivity
                    energy = float(np.clip(raw_audio_level * 1.2, 0.0, 1.0))

                    self.level_queue.put(list(np.clip(points * 15 * sensitivity, 0, 1)))

                    fan_mode_val = FAN_MODE_DISPLAY.get(self.fan_mode_str.get(), "vu_meter")
                    fan_theme_val = FAN_THEME_DISPLAY.get(self.fan_theme_str.get(), "white")
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

    def _on_phone_stream_connected(self, phone_ip):
        self.status_pill.configure(text=f"CONNECTED ({phone_ip})", text_color=COLOR_WHITE)
        self.status_dot.configure(text_color=COLOR_WHITE)
        self.log(f"Phone stream active from {phone_ip}")

    def _on_stream_stopped(self):
        self.is_streaming = False
        btn_txt = "▶ START LISTENER (SYNC PC RGB)" if self.direction.get() == "PHONE_TO_PC" else "▶ START STREAMING TO PHONE"
        self.stream_btn.configure(text=btn_txt, fg_color=COLOR_WHITE, text_color="#000000")
        self.status_pill.configure(text="STANDBY", text_color=COLOR_TEXT_SECONDARY)
        self.status_dot.configure(text_color=COLOR_TEXT_MUTED)
        self.level_queue.put([0.0]*64)
        try:
            self.fan_preview_queue.put_nowait({"__all__": [(0, 0, 0)] * 16})
        except:
            pass
        self.hook_watcher.stop()

    # ==============================================================================
    # DYNAMIC ANIMATED GUI RENDER & UPDATE LOOP (60 FPS FLUID ENGINE)
    # ==============================================================================
    def _update_loop(self):
        self._anim_phase += 0.08
        now = time.perf_counter()

        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            self.console.configure(state="normal")
            self.console.insert("end", f"[{time.strftime('%H:%M:%S')}] {msg}\n")
            self.console.see("end")
            self.console.configure(state="disabled")

        # 1. Header Status Capsule Animation
        if self.is_streaming:
            pulse_brightness = (math.sin(self._anim_phase * 3.2) * 0.5 + 0.5)
            glow_val = int(140 + pulse_brightness * 115)
            glow_col = f"#{glow_val:02x}{glow_val:02x}{glow_val:02x}"
            self.status_dot.configure(text_color=glow_col)
            
            # Dynamic FAB text & wave glyph animation
            wave_glyphs = [" ▂▃▅▆▇▆▅▃▂ ", "  ▂▃▅▇█▇▅▃ ", "   ▂▃▅███▅▃ ", " ▃▅▇███▇▅▃ "]
            cur_glyph = wave_glyphs[int(self._anim_phase * 2.5) % len(wave_glyphs)]
            if self.direction.get() == "PHONE_TO_PC":
                self.stream_btn.configure(text=f"⏹ STOP LISTENER   [{cur_glyph}]")
            else:
                self.stream_btn.configure(text=f"⏹ STOP STREAMING   [{cur_glyph}]")
        elif self.is_discovering:
            pulse_brightness = (math.sin(self._anim_phase * 5.0) * 0.5 + 0.5)
            glow_val = int(160 + pulse_brightness * 95)
            self.status_dot.configure(text_color=f"#{glow_val:02x}{glow_val:02x}{glow_val:02x}")
            radar_dots = [".  ", ".. ", "...", " ..", "  ."]
            radar_str = radar_dots[int(self._anim_phase * 2.0) % len(radar_dots)]
            self.status_pill.configure(text=f"SEARCHING {radar_str}", text_color=COLOR_WHITE)
        else:
            self.status_dot.configure(text_color="#4A5060")

        # 2. Audio Spectrum & Ballistic Peak Physics
        last_points = None
        while not self.level_queue.empty():
            last_points = self.level_queue.get_nowait()
        
        num_dots = len(self.spectrum_points)
        is_audio_active = (time.time() - self._last_packet_time < 0.5) and (last_points is not None and max(last_points) > 0.01)

        if is_audio_active and last_points:
            interpolated = np.interp(
                np.linspace(0, 63, num_dots),
                np.arange(64),
                last_points
            )
            for i in range(num_dots):
                target_val = float(interpolated[i])
                if target_val > self.spectrum_points[i]:
                    self.spectrum_points[i] = self.spectrum_points[i] * 0.35 + target_val * 0.65
                else:
                    self.spectrum_points[i] = max(0.0, self.spectrum_points[i] * 0.82)

                val = self.spectrum_points[i]
                if i < len(self.viz_peaks):
                    if val >= self.viz_peaks[i]:
                        self.viz_peaks[i] = val
                        self.viz_peak_holds[i] = now + 0.30
                    elif now > self.viz_peak_holds[i]:
                        self.viz_peaks[i] = max(0.0, self.viz_peaks[i] - 1.6 * 0.016)
        else:
            for i in range(num_dots):
                wave = (math.sin(self._anim_phase * 1.2 + i * 0.22) * 0.5 + 0.5) * 0.16 + 0.03
                self.spectrum_points[i] = self.spectrum_points[i] * 0.88 + wave * 0.12
                if i < len(self.viz_peaks):
                    self.viz_peaks[i] = max(0.0, self.viz_peaks[i] * 0.90)

        self._draw_viz()

        # 3. Case Fan Preview Animation
        speed_mult = self.fan_speed.get()
        dir_mult = 1.0 if self.fan_clockwise.get() else -1.0
        self.fan_preview_angle = (self.fan_preview_angle + dir_mult * speed_mult * 0.06) % (2.0 * math.pi)

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
        elif not self.is_streaming:
            idle_ring = []
            num_leds = 16
            for i in range(num_leds):
                phi = (2.0 * math.pi * i) / num_leds
                diff = (self.fan_preview_angle - phi) % (2.0 * math.pi) if self.fan_clockwise.get() else (phi - self.fan_preview_angle) % (2.0 * math.pi)
                tail_len = math.pi * 1.4
                brightness = (1.0 - diff / tail_len) ** 1.8 if diff <= tail_len else 0.0
                cr, cg, cb = self.custom_rgb
                idle_ring.append((int(cr * brightness * 0.8), int(cg * brightness * 0.8), int(cb * brightness * 0.8)))
            self._draw_fan_preview(idle_ring)

        self.after(16, self._update_loop)

    # ==========================================
    # RADIAL FAN ARGB CANVAS PREVIEW
    # ==========================================
    def _init_fan_preview_canvas(self):
        self.fan_preview_dots = []
        self._fan_preview_cache = []
        w, h = 142, 142
        cx, cy = w / 2.0, h / 2.0
        
        self.fan_canvas.create_oval(cx - 58, cy - 58, cx + 58, cy + 58, fill="#0C0D10", outline="#22252E", width=2)
        
        # Center Pulse Shockwave Ring
        self.fan_ripple_ring = self.fan_canvas.create_oval(cx - 24, cy - 24, cx + 24, cy + 24, fill="", outline="#343946", width=1.5)
        
        # Center Hub Disc
        self.fan_hub_disc = self.fan_canvas.create_oval(cx - 24, cy - 24, cx + 24, cy + 24, fill="#16181F", outline="#2B2F3B", width=1.5)
        
        self.fan_hub_text = self.fan_canvas.create_text(
            cx, cy, text="FAN 1", fill=COLOR_TEXT_SECONDARY,
            font=ctk.CTkFont(family=FONT_FAMILY, size=8, weight="bold")
        )
        
        r_ring = 42.0
        dot_radius = 4.8
        num_dots = 16
        for i in range(num_dots):
            angle = (2.0 * math.pi * i) / num_dots - (math.pi / 2.0)
            x = cx + r_ring * math.cos(angle)
            y = cy + r_ring * math.sin(angle)
            dot = self.fan_canvas.create_oval(
                x - dot_radius, y - dot_radius,
                x + dot_radius, y + dot_radius,
                fill="#1E2028", outline="#14151C", width=1
            )
            self.fan_preview_dots.append(dot)
            self._fan_preview_cache.append("#1E2028")

    def _draw_fan_preview(self, colors):
        if not self.fan_preview_dots:
            return
        num_dots = len(self.fan_preview_dots)
        if not colors or not self.fan_viz_enabled.get():
            for i, dot in enumerate(self.fan_preview_dots):
                if self._fan_preview_cache[i] != "#1E2028":
                    self.fan_canvas.itemconfig(dot, fill="#1E2028")
                    self._fan_preview_cache[i] = "#1E2028"
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

    # ==========================================
    # SPECTRUM VISUALIZER CANVAS (MONOCHROME/SILVER/BALLISTICS)
    # ==========================================
    def _init_viz_dots(self, count=64):
        if self.viz_dots:
            for col in self.viz_dots:
                for dot in col:
                    self.viz_canvas.delete(dot)
        
        self.viz_dots = []
        self.spectrum_points = [0.0] * count
        self.viz_peaks = [0.0] * count
        self.viz_peak_holds = [0.0] * count
        self._viz_cache = [(-1, -1)] * count

        for i in range(count):
            col_dots = []
            for d in range(8):
                dot = self.viz_canvas.create_oval(0, 0, 0, 0, fill="#15171E", outline="")
                col_dots.append(dot)
            self.viz_dots.append(col_dots)

    def _resize_viz(self, event=None):
        w = self.viz_canvas.winfo_width()
        h = self.viz_canvas.winfo_height()
        if w <= 1: return
        
        target_spacing = 13
        new_count = max(8, w // target_spacing)
        
        if new_count != len(self.viz_dots):
            self._init_viz_dots(new_count)
            
        dot_spacing = w / new_count
        dot_size = max(4.0, dot_spacing - 4.5)
        
        for i in range(new_count):
            x = i * dot_spacing + dot_spacing / 2.0
            for d in range(8):
                dy = h - (d * 9.5 + 12)
                dot = self.viz_dots[i][d]
                self.viz_canvas.coords(dot, x - dot_size/2.0, dy - dot_size/2.0, x + dot_size/2.0, dy + dot_size/2.0)

    def _draw_viz(self):
        if not self.viz_dots: return
        
        num_cols = len(self.viz_dots)
        if len(self._viz_cache) != num_cols:
            self._viz_cache = [(-1, -1)] * num_cols
            
        gradient_colors = [
            "#22262E",  # Row 0: Slate floor
            "#323844",  # Row 1
            "#444C5C",  # Row 2
            "#5A657A",  # Row 3
            "#7B89A0",  # Row 4: Silver slate
            "#A2B0C7",  # Row 5: Light silver
            "#CBD5E1",  # Row 6: Bright silver
            "#FFFFFF"   # Row 7: Pure glyph white
        ]

        for i in range(num_cols):
            val = self.spectrum_points[i]
            dots_to_draw = int(val * 8)
            peak_idx = min(7, int(self.viz_peaks[i] * 7.99)) if i < len(self.viz_peaks) else 0
            
            cache_state = (dots_to_draw, peak_idx)
            if cache_state != self._viz_cache[i]:
                for d in range(8):
                    if d < dots_to_draw:
                        color = gradient_colors[d]
                    elif d == peak_idx and peak_idx > 0 and peak_idx >= dots_to_draw:
                        color = "#FFFFFF"  # Ballistic peak indicator
                    else:
                        color = "#15171E"
                    dot = self.viz_dots[i][d]
                    self.viz_canvas.itemconfig(dot, fill=color)
                self._viz_cache[i] = cache_state

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="GLYPHIX Desktop Companion")
    parser.add_argument("--material", "--kivymd", action="store_true", help="Launch the KivyMD Material UI Desktop Companion")
    args, _ = parser.parse_known_args()

    if args.material:
        try:
            from desktop_companion_kivymd import GlyphixMaterialApp
            GlyphixMaterialApp().run()
        except ImportError:
            subprocess.run(["py", "-3.12", "desktop_companion_kivymd.py"])
    else:
        ctk.set_appearance_mode("Dark")
        app = CompanionApp()
        app.mainloop()
