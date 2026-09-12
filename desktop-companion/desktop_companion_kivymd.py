import os
import sys
import time
import math
import socket
import threading
import queue
import shutil
import subprocess
import colorsys
import numpy as np

# Set environment variables for clean OpenGL rendering
os.environ["KIVY_NO_ARGS"] = "1"
os.environ["KIVY_WINDOW"] = "sdl2"

import pyaudiowpatch as pyaudio

try:
    import ctypes
    from ctypes import wintypes
    HAS_CTYPES = True
except ImportError:
    HAS_CTYPES = False
    ctypes = None
    wintypes = None

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

from tkinter import colorchooser
import tkinter as tk

# Kivy & KivyMD Imports
from kivy.config import Config
Config.set('graphics', 'resizable', '1')
Config.set('graphics', 'width', '680')
Config.set('graphics', 'height', '840')
Config.set('graphics', 'minimum_width', '560')
Config.set('graphics', 'minimum_height', '700')

from kivy.core.window import Window
from kivy.clock import Clock
from kivy.graphics import Color, Ellipse, Rectangle, Line, RoundedRectangle
from kivy.uix.widget import Widget
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.anchorlayout import AnchorLayout
from kivy.uix.scrollview import ScrollView
from kivy.metrics import dp

import kivymd
from kivymd.app import MDApp
from kivymd.uix.label import MDLabel
from kivymd.uix.card import MDCard
from kivymd.uix.slider import MDSlider, MDSliderHandle, MDSliderValueLabel
from kivymd.uix.selectioncontrol import MDSwitch
from kivymd.uix.button import MDButton, MDButtonText, MDIconButton
from kivymd.uix.textfield import MDTextField, MDTextFieldHintText
from kivymd.uix.menu import MDDropdownMenu

UDP_PORT = 12347
DISCOVERY_PORT = 12348
OPENRGB_PORT = 6742
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 48000

# ==============================================================================
# PURE MONOCHROME / NOTHING OS DESIGN TOKENS (ZERO RED, ZERO GREEN)
# ==============================================================================
COLOR_BG = (11/255, 12/255, 14/255, 1.0)           # Deep Obsidian #0B0C0E
COLOR_SURFACE = (20/255, 22/255, 26/255, 1.0)      # Elevated Surface Card #14161A
COLOR_SURFACE_INNER = (27/255, 29/255, 35/255, 1.0)# Inner Card Surface #1B1D23
COLOR_SURFACE_HOVER = (36/255, 39/255, 48/255, 1.0)# Hover State #242730
COLOR_BORDER = (39/255, 42/255, 51/255, 1.0)       # Border #272A33
COLOR_BORDER_LIGHT = (62/255, 67/255, 82/255, 1.0) # Light Border #3E4352
COLOR_ACCENT = (1.0, 1.0, 1.0, 1.0)                # Crisp Glyph White #FFFFFF
COLOR_TEXT_PRIMARY = (1.0, 1.0, 1.0, 1.0)          # Primary Text #FFFFFF
COLOR_TEXT_SECONDARY = (148/255, 163/255, 184/255, 1.0) # Silver Slate #94A3B8
COLOR_TEXT_MUTED = (100/255, 116/255, 139/255, 1.0)     # Muted Slate #64748B

Window.clearcolor = COLOR_BG

THEME_PRESET_COLORS = {
    "Glyph White": (255, 255, 255),
    "Cyber Cyan": (0, 210, 255),
    "Electric Purple": (168, 85, 247),
    "Solar Orange": (249, 115, 22),
    "Neon Magenta": (236, 72, 153),
    "Ice Blue": (56, 189, 248),
    "Deep Violet": (139, 92, 246),
    "Reactive Rainbow": (255, 255, 255),
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
# KEYBOARD HOOK WATCHER (TYPING SUPPRESSION)
# ==============================================================================
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

# ==============================================================================
# AUDIO DSP & SUB-BASS TRANSIENT ENGINE
# ==============================================================================
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

# ==============================================================================
# CASE FAN ARGB EFFECT GENERATOR
# ==============================================================================
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

# ==============================================================================
# OPENRGB CONTROLLER & DEVICE MANAGER
# ==============================================================================
class OpenRGBManager:
    def __init__(self, logger=None):
        self.logger = logger
        self.client = None
        self.connected = False
        self.devices = []
        self.last_sync_time = 0.0
        self.fan_visualizers = {}
        self.header_configs = {}
        self.on_connected_callback = None

    @property
    def fan_visualizer(self):
        return self.get_visualizer("__default__")

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
                        except Exception:
                            pass
    def update_fan_config(self, ring_size=16, num_fans=1):
        pass

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
                pass

# ==============================================================================
# NETWORK & AUDIO SOURCE HELPERS
# ==============================================================================
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
# OPENGL HARDWARE-ACCELERATED SPECTRUM VISUALIZER WIDGET
# ==============================================================================
class SpectrumVisualizerWidget(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.points = [0.0] * 64
        self.peaks = [0.0] * 64
        self.bind(pos=self.redraw, size=self.redraw)

    def update_spectrum(self, points, peaks):
        self.points = points
        self.peaks = peaks
        self.redraw()

    def redraw(self, *args):
        self.canvas.clear()
        w, h = self.size
        x0, y0 = self.pos
        if w < 10 or h < 10: return

        with self.canvas:
            # Background
            Color(8/255, 9/255, 11/255, 1.0)
            RoundedRectangle(pos=(x0, y0), size=(w, h), radius=[dp(8)])

            num_cols = min(64, len(self.points))
            spacing = w / max(1, num_cols)
            dot_w = max(dp(3), spacing - dp(2.5))
            dot_h = max(dp(3), (h - dp(18)) / 8.0 - dp(2.0))

            gradient_colors = [
                (34/255, 38/255, 46/255, 1.0),   # Row 0
                (50/255, 56/255, 68/255, 1.0),   # Row 1
                (68/255, 76/255, 92/255, 1.0),   # Row 2
                (90/255, 101/255, 122/255, 1.0), # Row 3
                (123/255, 137/255, 160/255, 1.0),# Row 4: Silver
                (162/255, 176/255, 199/255, 1.0),# Row 5: Light silver
                (203/255, 215/255, 232/255, 1.0),# Row 6: Silver white
                (1.0, 1.0, 1.0, 1.0)              # Row 7: Pure glyph white
            ]

            for i in range(num_cols):
                col_x = x0 + i * spacing + (spacing - dot_w) / 2.0
                val = self.points[i]
                dots_to_draw = int(val * 8)
                peak_idx = min(7, int(self.peaks[i] * 7.99)) if i < len(self.peaks) else 0

                for d in range(8):
                    dot_y = y0 + dp(9) + d * ((h - dp(18)) / 8.0)
                    if d < dots_to_draw:
                        r, g, b, a = gradient_colors[d]
                        Color(r, g, b, a)
                    elif d == peak_idx and peak_idx > 0 and peak_idx >= dots_to_draw:
                        Color(1.0, 1.0, 1.0, 1.0)  # Ballistic peak indicator
                    else:
                        Color(21/255, 23/255, 30/255, 1.0)  # Off state

                    Ellipse(pos=(col_x, dot_y), size=(dot_w, dot_h))

# ==============================================================================
# OPENGL RADIAL CASE FAN ARGB PREVIEW WIDGET
# ==============================================================================
class RadialFanPreviewWidget(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.colors = [(0, 0, 0)] * 16
        self.sweep_angle = 0.0
        self.pulse_energy = 0.0
        self.hub_text = "FAN 1"
        self.bind(pos=self.redraw, size=self.redraw)

    def update_fan(self, colors, sweep_angle, pulse=0.0, hub_text="FAN 1"):
        self.colors = colors
        self.sweep_angle = sweep_angle
        self.pulse_energy = pulse
        self.hub_text = hub_text
        self.redraw()

    def redraw(self, *args):
        self.canvas.clear()
        w, h = self.size
        x0, y0 = self.pos
        if w < 10 or h < 10: return

        cx = x0 + w / 2.0
        cy = y0 + h / 2.0
        r_outer = min(w, h) * 0.44
        r_ring = min(w, h) * 0.32
        r_hub = min(w, h) * 0.18

        with self.canvas:
            # Housing Frame
            Color(12/255, 13/255, 16/255, 1.0)
            Ellipse(pos=(cx - r_outer, cy - r_outer), size=(r_outer * 2, r_outer * 2))
            Color(34/255, 37/255, 46/255, 1.0)
            Line(ellipse=(cx - r_outer, cy - r_outer, r_outer * 2, r_outer * 2), width=1.2)

            # Reactive Shockwave Pulse Ripple
            if self.pulse_energy > 0.05:
                rip_r = r_hub + (r_outer - r_hub) * min(1.0, self.pulse_energy * 1.5)
                alpha = max(0.0, 1.0 - self.pulse_energy)
                Color(1.0, 1.0, 1.0, alpha * 0.6)
                Line(ellipse=(cx - rip_r, cy - rip_r, rip_r * 2, rip_r * 2), width=1.5)

            # Center Hub Disc
            hub_glow = int(22 + self.pulse_energy * 60) / 255.0
            Color(hub_glow, hub_glow, hub_glow + 0.04, 1.0)
            Ellipse(pos=(cx - r_hub, cy - r_hub), size=(r_hub * 2, r_hub * 2))
            Color(47/255, 51/255, 63/255, 1.0)
            Line(ellipse=(cx - r_hub, cy - r_hub, r_hub * 2, r_hub * 2), width=1.2)

            # 16 ARGB LED Dots
            num_dots = 16
            dot_rad = dp(4.5)
            chosen_colors = self.colors
            if len(chosen_colors) != num_dots:
                indices = np.linspace(0, max(1, len(chosen_colors) - 1), num_dots).astype(int)
                chosen_colors = [chosen_colors[idx] for idx in indices]

            for i in range(num_dots):
                angle = (2.0 * math.pi * i) / num_dots - (math.pi / 2.0)
                px = cx + r_ring * math.cos(angle)
                py = cy + r_ring * math.sin(angle)
                cr, cg, cb = chosen_colors[i]
                
                if (cr, cg, cb) == (0, 0, 0):
                    Color(30/255, 32/255, 40/255, 1.0)
                else:
                    Color(cr / 255.0, cg / 255.0, cb / 255.0, 1.0)

                Ellipse(pos=(px - dot_rad, py - dot_rad), size=(dot_rad * 2, dot_rad * 2))

# ==============================================================================
# RESPONSIVE CUSTOM MATERIAL BUTTON & CONTAINERS
# ==============================================================================
class GlyphixButton(MDButton):
    def __init__(self, *args, align="center", **kwargs):
        self.text_align = align
        kwargs.setdefault("theme_width", "Custom")
        kwargs.setdefault("theme_height", "Custom")
        super().__init__(*args, **kwargs)
        self.theme_width = "Custom"
        self.theme_height = "Custom"
        self.bind(size=lambda *args: self.do_layout())

    def adjust_pos(self, *args):
        if self._button_text and not self._button_icon:
            if self.text_align == "center":
                self._button_text.pos_hint = {"center_x": 0.5, "center_y": 0.5}
            else:
                self._button_text.pos_hint = {"center_y": 0.5}
                self._button_text.x = dp(14)
        elif self._button_icon:
            super().adjust_pos(*args)

class GlyphixCard(BoxLayout):
    def __init__(self, *args, **kwargs):
        kwargs.setdefault("orientation", "vertical")
        kwargs.setdefault("size_hint_x", 1.0)
        super().__init__(*args, **kwargs)
        self.bind(width=lambda c, w: c.do_layout())
        with self.canvas.before:
            Color(20/255, 22/255, 26/255, 1.0)
            self._rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(12)])
            Color(39/255, 42/255, 51/255, 1.0)
            self._border = Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(12)), width=1.0)
        self.bind(pos=self._update_canvas, size=self._update_canvas)

    def _update_canvas(self, *args):
        self._rect.pos = self.pos
        self._rect.size = self.size
        self._border.rounded_rectangle = (self.x, self.y, self.width, self.height, dp(12))

class ResponsiveRow(BoxLayout):
    def __init__(self, *args, **kwargs):
        kwargs.setdefault("orientation", "horizontal")
        kwargs.setdefault("size_hint_x", 1.0)
        super().__init__(*args, **kwargs)
        self.bind(width=lambda r, w: r.do_layout())

# ==============================================================================
# MAIN KIVYMD MATERIAL APPLICATION
# ==============================================================================
class GlyphixMaterialApp(MDApp):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.title = "GLYPHIX // Material Desktop Companion"
        
        self.direction = "PHONE_TO_PC"
        self.is_streaming = False
        self.is_discovering = False
        self.local_pc_ip = get_local_ip()
        
        self.stop_stream_event = threading.Event()
        self.stop_discovery_event = threading.Event()
        
        self.viz_queue = queue.Queue(maxsize=1)
        self.log_queue = queue.Queue()
        self.level_queue = queue.Queue()
        self.fan_preview_queue = queue.Queue(maxsize=1)
        
        self.spectrum_points = [0.0] * 64
        self.viz_peaks = [0.0] * 64
        self.viz_peak_holds = [0.0] * 64
        self.anim_phase = 0.0
        self.last_packet_time = 0.0
        self.fan_sweep_angle = 0.0
        self.last_pulse = 0.0
        
        self.rgb_sensitivity = 1.0
        self.rgb_decay = 0.82
        self.use_openrgb = False
        self.typing_suppression = True
        self.selected_rgb = (255, 255, 255)
        self.custom_hex = "#FFFFFF"
        self.custom_rgb = (255, 255, 255)
        
        self.fan_viz_enabled = True
        self.fan_clockwise = True
        self.fan_mode_str = "Radial VU Meter (Full Ring)"
        self.fan_theme_str = "Glyph White"
        self.fan_leds_str = "16 LEDs (Standard)"
        self.fan_count_str = "1 Fan"
        self.fan_speed = 1.0
        
        self.current_header = "All Connected ARGB (Sync All)"
        self.detected_headers = ["All Connected ARGB (Sync All)"]
        self.header_configs = {
            "All Connected ARGB (Sync All)": {
                "mode": "vu_meter",
                "theme": "white",
                "mode_str": "Radial VU Meter (Full Ring)",
                "theme_str": "Glyph White",
                "fan_ring_size": 16,
                "fan_count": 1,
                "clockwise": True,
                "speed": 1.0,
                "enabled": True,
                "custom_color": (255, 255, 255),
                "custom_hex": "#FFFFFF",
                "led_count_str": "16 LEDs (Standard)",
                "fan_count_str": "1 Fan"
            }
        }
        
        self.wasapi_devices = get_wasapi_devices()
        self.selected_device_name = self.wasapi_devices[0]["name"] if self.wasapi_devices else ""
        
        self.rgb_manager = OpenRGBManager(logger=self.log)
        self.rgb_manager.on_connected_callback = lambda hdrs: Clock.schedule_once(lambda dt: self._on_openrgb_headers_detected(hdrs))
        self.hook_watcher = KeyboardHookWatcher(self._on_key_press)
        self.last_key_time = 0.0
        
        # Menu References
        self.header_menu = None
        self.effect_menu = None
        self.theme_menu = None
        self.leds_menu = None
        self.fans_menu = None
        self.audio_menu = None

    def build(self):
        self.theme_cls.theme_style = "Dark"
        self.theme_cls.primary_palette = "Gray"
        
        # Centered Root Container
        root_anchor = AnchorLayout(anchor_x="center", anchor_y="top")
        
        # Inner Responsive Container (Fixed max width 680dp)
        self.main_container = BoxLayout(
            orientation="vertical",
            spacing=dp(10),
            padding=[dp(16), dp(12), dp(16), dp(16)],
            size_hint=(None, 1.0),
            width=min(Window.width - dp(24), dp(680))
        )
        Window.bind(size=self._on_window_resize)
        
        # 1. Top Brand Header & Status Capsule
        header_card = GlyphixCard(
            size_hint_y=None,
            height=dp(64),
            padding=[dp(16), dp(8), dp(16), dp(8)]
        )
        
        hdr_box = ResponsiveRow(orientation="horizontal")
        brand_box = BoxLayout(orientation="vertical", spacing=dp(1))
        
        logo_row = ResponsiveRow(orientation="horizontal", spacing=dp(6))
        logo_lbl = MDLabel(
            text="GLYPHIX",
            font_style="Headline",
            role="small",
            theme_text_color="Custom",
            text_color=COLOR_TEXT_PRIMARY,
            bold=True
        )
        ver_lbl = MDLabel(
            text="DESKTOP",
            font_style="Label",
            role="small",
            theme_text_color="Custom",
            text_color=COLOR_TEXT_SECONDARY,
            bold=True
        )
        logo_row.add_widget(logo_lbl)
        logo_row.add_widget(ver_lbl)
        
        sub_lbl = MDLabel(
            text="AUDIO REACTIVE GLYPH & ARGB SYNC ENGINE",
            font_style="Label",
            role="small",
            theme_text_color="Custom",
            text_color=COLOR_TEXT_MUTED
        )
        brand_box.add_widget(logo_row)
        brand_box.add_widget(sub_lbl)
        
        # Status Capsule
        self.status_capsule_lbl = MDLabel(
            text="● STANDBY",
            font_style="Label",
            role="medium",
            theme_text_color="Custom",
            text_color=COLOR_TEXT_SECONDARY,
            halign="right",
            bold=True
        )
        
        hdr_box.add_widget(brand_box)
        hdr_box.add_widget(self.status_capsule_lbl)
        header_card.add_widget(hdr_box)
        self.main_container.add_widget(header_card)
        
        # 2. Scrollable Content Area
        scroll = ScrollView(do_scroll_x=False, bar_width=dp(4), size_hint=(1.0, 1.0))
        content_box = BoxLayout(orientation="vertical", spacing=dp(10), size_hint=(1.0, None))
        content_box.bind(minimum_height=content_box.setter('height'))
        scroll.bind(width=lambda s, w: setattr(content_box, 'width', w))
        
        # -------------------------------------------------------------
        # CARD 1: SYNC DIRECTION
        # -------------------------------------------------------------
        dir_card = GlyphixCard(size_hint_y=None, height=dp(94), padding=dp(12), spacing=dp(8))
        dir_card.add_widget(MDLabel(text="SYNC DIRECTION", font_style="Label", role="small", bold=True, size_hint_y=None, height=dp(16), theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        
        btn_row = ResponsiveRow(orientation="horizontal", spacing=dp(10), size_hint=(1.0, None), height=dp(44))
        self.btn_phone_to_pc = GlyphixButton(style="filled", size_hint=(0.5, 1.0), md_bg_color=COLOR_ACCENT, align="center", on_release=lambda x: self._set_direction("PHONE_TO_PC"))
        self.btn_phone_to_pc_text = MDButtonText(text="📥 Phone → PC (Sync RGB)", font_style="Label", role="medium", theme_text_color="Custom", text_color=(0, 0, 0, 1), bold=True)
        self.btn_phone_to_pc.add_widget(self.btn_phone_to_pc_text)
        
        self.btn_pc_to_phone = GlyphixButton(style="outlined", size_hint=(0.5, 1.0), md_bg_color=(0, 0, 0, 0), align="center", on_release=lambda x: self._set_direction("PC_TO_PHONE"))
        self.btn_pc_to_phone_text = MDButtonText(text="📤 PC → Phone (Glyphs)", font_style="Label", role="medium", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY, bold=True)
        self.btn_pc_to_phone.add_widget(self.btn_pc_to_phone_text)
        
        btn_row.add_widget(self.btn_phone_to_pc)
        btn_row.add_widget(self.btn_pc_to_phone)
        dir_card.add_widget(btn_row)
        content_box.add_widget(dir_card)
        
        # -------------------------------------------------------------
        # CARD 2: SPECTRUM VISUALIZER & LOOPBACK INPUT
        # -------------------------------------------------------------
        viz_card = GlyphixCard(size_hint_y=None, height=dp(215), padding=dp(12), spacing=dp(6))
        
        viz_hdr_row = ResponsiveRow(orientation="horizontal", size_hint=(1.0, None), height=dp(38))
        viz_hdr_row.add_widget(MDLabel(text="AUDIO SPECTRUM & LOOPBACK INPUT", font_style="Label", role="small", bold=True, theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        
        self.audio_menu_btn = GlyphixButton(style="tonal", size_hint=(None, 1.0), width=dp(240), align="left", on_release=self._open_audio_menu)
        self.audio_menu_btn_text = MDButtonText(text=f"🎙 {self.selected_device_name[:20]} ▼", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY, bold=True)
        self.audio_menu_btn.add_widget(self.audio_menu_btn_text)
        viz_hdr_row.add_widget(self.audio_menu_btn)
        viz_card.add_widget(viz_hdr_row)
        
        self.viz_widget = SpectrumVisualizerWidget(size_hint=(1.0, None), height=dp(96))
        viz_card.add_widget(self.viz_widget)
        
        freq_row = ResponsiveRow(orientation="horizontal", size_hint=(1.0, None), height=dp(18))
        for band in ("SUB-BASS", "BASS", "LOW-MID", "MID", "PRESENCE", "BRILLIANCE"):
            freq_row.add_widget(MDLabel(text=band, font_style="Label", role="small", halign="center", theme_text_color="Custom", text_color=COLOR_TEXT_MUTED))
        viz_card.add_widget(freq_row)
        content_box.add_widget(viz_card)
        
        # -------------------------------------------------------------
        # CARD 3: WI-FI CONNECTIVITY (UDP ONLY, ZERO BLUETOOTH)
        # -------------------------------------------------------------
        wifi_card = GlyphixCard(size_hint_y=None, height=dp(145), padding=dp(12), spacing=dp(8))
        wifi_card.add_widget(MDLabel(text="WI-FI CONNECTIVITY (UDP LOW LATENCY STREAMING)", font_style="Label", role="small", bold=True, size_hint_y=None, height=dp(16), theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        
        ip_row = ResponsiveRow(orientation="horizontal", spacing=dp(8), size_hint=(1.0, None), height=dp(38))
        ip_row.add_widget(MDLabel(text=f"PC IP: {self.local_pc_ip} : 12347", font_style="Title", role="medium", bold=True, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY))
        
        self.copy_btn = GlyphixButton(style="tonal", size_hint=(None, 1.0), width=dp(110), align="center", on_release=lambda x: self._copy_ip())
        self.copy_btn_text = MDButtonText(text="COPY IP", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY, bold=True)
        self.copy_btn.add_widget(self.copy_btn_text)
        ip_row.add_widget(self.copy_btn)
        wifi_card.add_widget(ip_row)
        
        phone_row = ResponsiveRow(orientation="horizontal", spacing=dp(8), size_hint=(1.0, None), height=dp(44))
        self.phone_ip_field = MDTextField(mode="outlined", size_hint=(0.70, 1.0))
        self.phone_ip_field.add_widget(MDTextFieldHintText(text="Enter Phone IP Address (e.g. 192.168.1.55)"))
        phone_row.add_widget(self.phone_ip_field)
        
        self.discover_btn = GlyphixButton(style="filled", size_hint=(0.30, 1.0), md_bg_color=COLOR_SURFACE_HOVER, align="center", on_release=lambda x: self._toggle_discovery())
        self.discover_btn_text = MDButtonText(text="🔍 DISCOVER", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY, bold=True)
        self.discover_btn.add_widget(self.discover_btn_text)
        phone_row.add_widget(self.discover_btn)
        wifi_card.add_widget(phone_row)
        content_box.add_widget(wifi_card)
        
        # -------------------------------------------------------------
        # CARD 4: HARDWARE LIGHTING SYNC & AUDIO DSP
        # -------------------------------------------------------------
        dsp_card = GlyphixCard(size_hint_y=None, height=dp(190), padding=dp(12), spacing=dp(8))
        dsp_card.add_widget(MDLabel(text="HARDWARE LIGHTING SYNC & AUDIO DSP", font_style="Label", role="small", bold=True, size_hint_y=None, height=dp(16), theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        
        sw_row = ResponsiveRow(orientation="horizontal", spacing=dp(16), size_hint=(1.0, None), height=dp(38))
        
        sw_col1 = ResponsiveRow(orientation="horizontal", spacing=dp(8), size_hint_x=0.5)
        sw_col1.add_widget(MDLabel(text="OpenRGB Sync", font_style="Body", role="medium", bold=True, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY))
        self.openrgb_switch = MDSwitch()
        self.openrgb_switch.bind(active=self._on_openrgb_switch_toggle)
        sw_col1.add_widget(self.openrgb_switch)
        sw_row.add_widget(sw_col1)
        
        sw_col2 = ResponsiveRow(orientation="horizontal", spacing=dp(8), size_hint_x=0.5)
        sw_col2.add_widget(MDLabel(text="Typing Filter", font_style="Body", role="medium", bold=True, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY))
        self.typing_switch = MDSwitch()
        self.typing_switch.bind(active=self._on_typing_switch_toggle)
        sw_col2.add_widget(self.typing_switch)
        sw_row.add_widget(sw_col2)
        dsp_card.add_widget(sw_row)
        
        # Sensitivity Slider
        sens_row = ResponsiveRow(orientation="horizontal", spacing=dp(8), size_hint=(1.0, None), height=dp(34))
        sens_row.add_widget(MDLabel(text="SENSITIVITY", font_style="Label", role="small", bold=True, size_hint_x=0.28, theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        self.sens_slider = MDSlider(min=0.1, max=3.0, value=1.0, size_hint_x=0.58)
        self.sens_slider.add_widget(MDSliderHandle())
        self.sens_slider.bind(value=self._on_sens_slider)
        self.sens_lbl = MDLabel(text="1.00x", font_style="Body", role="small", bold=True, size_hint_x=0.14, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        sens_row.add_widget(self.sens_slider)
        sens_row.add_widget(self.sens_lbl)
        dsp_card.add_widget(sens_row)
        
        # Decay Slider
        decay_row = ResponsiveRow(orientation="horizontal", spacing=dp(8), size_hint=(1.0, None), height=dp(34))
        decay_row.add_widget(MDLabel(text="PULSE DECAY", font_style="Label", role="small", bold=True, size_hint_x=0.28, theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        self.decay_slider = MDSlider(min=0.5, max=0.98, value=0.82, size_hint_x=0.58)
        self.decay_slider.add_widget(MDSliderHandle())
        self.decay_slider.bind(value=self._on_decay_slider)
        self.decay_lbl = MDLabel(text="82%", font_style="Body", role="small", bold=True, size_hint_x=0.14, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        decay_row.add_widget(self.decay_slider)
        decay_row.add_widget(self.decay_lbl)
        dsp_card.add_widget(decay_row)
        content_box.add_widget(dsp_card)
        
        # -------------------------------------------------------------
        # CARD 5: CASE FAN ARGB PREVIEW & FULL PER-HEADER CONTROLS
        # -------------------------------------------------------------
        fan_card = GlyphixCard(size_hint_y=None, height=dp(350), padding=dp(12), spacing=dp(8))
        fan_card.add_widget(MDLabel(text="CASE FAN ARGB VISUALIZATION & PER-HEADER CONFIG", font_style="Label", role="small", bold=True, size_hint_y=None, height=dp(16), theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        
        fan_body = ResponsiveRow(orientation="horizontal", spacing=dp(12), size_hint=(1.0, 1.0))
        fan_left = BoxLayout(orientation="vertical", spacing=dp(8), size_hint=(0.64, 1.0))
        fan_left.bind(width=lambda l, w: l.do_layout())
        
        # Top Header Row with SCAN RGB
        hdr_row = ResponsiveRow(orientation="horizontal", spacing=dp(6), size_hint=(1.0, None), height=dp(38))
        self.header_btn = GlyphixButton(style="tonal", size_hint=(0.72, 1.0), align="left", on_release=self._open_header_menu)
        self.header_btn_text = MDButtonText(text="🎯 All Connected ARGB ▼", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY, bold=True)
        self.header_btn.add_widget(self.header_btn_text)
        
        self.scan_btn = GlyphixButton(style="filled", size_hint=(0.28, 1.0), md_bg_color=COLOR_SURFACE_HOVER, align="center", on_release=lambda x: self._rescan_rgb())
        self.scan_btn_text = MDButtonText(text="↻ SCAN", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY, bold=True)
        self.scan_btn.add_widget(self.scan_btn_text)
        hdr_row.add_widget(self.header_btn)
        hdr_row.add_widget(self.scan_btn)
        fan_left.add_widget(hdr_row)
        
        # Effect Selector Menu
        eff_row = ResponsiveRow(orientation="horizontal", size_hint=(1.0, None), height=dp(38))
        self.effect_btn = GlyphixButton(style="outlined", size_hint=(1.0, 1.0), align="left", on_release=self._open_effect_menu)
        self.effect_btn_text = MDButtonText(text=f"✨ {self.fan_mode_str} ▼", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        self.effect_btn.add_widget(self.effect_btn_text)
        eff_row.add_widget(self.effect_btn)
        fan_left.add_widget(eff_row)
        
        # Theme & Color Swatch Row
        theme_row = ResponsiveRow(orientation="horizontal", spacing=dp(6), size_hint=(1.0, None), height=dp(38))
        self.theme_btn = GlyphixButton(style="outlined", size_hint=(0.68, 1.0), align="left", on_release=self._open_theme_menu)
        self.theme_btn_text = MDButtonText(text=f"🎨 {self.fan_theme_str} ▼", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        self.theme_btn.add_widget(self.theme_btn_text)
        
        self.swatch_btn = GlyphixButton(style="filled", size_hint=(0.32, 1.0), md_bg_color=COLOR_ACCENT, align="center", on_release=lambda x: self._open_color_picker())
        self.swatch_btn_text = MDButtonText(text=self.custom_hex, font_style="Label", role="small", theme_text_color="Custom", text_color=(0, 0, 0, 1), bold=True)
        self.swatch_btn.add_widget(self.swatch_btn_text)
        theme_row.add_widget(self.theme_btn)
        theme_row.add_widget(self.swatch_btn)
        fan_left.add_widget(theme_row)
        
        # LEDs & Fans Count Row
        counts_row = ResponsiveRow(orientation="horizontal", spacing=dp(6), size_hint=(1.0, None), height=dp(38))
        self.leds_btn = GlyphixButton(style="outlined", size_hint=(0.50, 1.0), align="left", on_release=self._open_leds_menu)
        self.leds_btn_text = MDButtonText(text=f"💡 {self.fan_leds_str} ▼", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        self.leds_btn.add_widget(self.leds_btn_text)
        
        self.fans_btn = GlyphixButton(style="outlined", size_hint=(0.50, 1.0), align="left", on_release=self._open_fans_menu)
        self.fans_btn_text = MDButtonText(text=f"🌀 {self.fan_count_str} ▼", font_style="Label", role="small", theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        self.fans_btn.add_widget(self.fans_btn_text)
        counts_row.add_widget(self.leds_btn)
        counts_row.add_widget(self.fans_btn)
        fan_left.add_widget(counts_row)
        
        # Switches Row (Fan FX, Clockwise)
        sw_fan_row = ResponsiveRow(orientation="horizontal", spacing=dp(12), size_hint=(1.0, None), height=dp(34))
        sw_fan_row.add_widget(MDLabel(text="Enable Fan FX", font_style="Body", role="small", bold=True, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY))
        self.fan_fx_switch = MDSwitch()
        self.fan_fx_switch.bind(active=self._on_fan_switch_toggle)
        sw_fan_row.add_widget(self.fan_fx_switch)
        
        sw_fan_row.add_widget(MDLabel(text="Clockwise", font_style="Body", role="small", bold=True, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY))
        self.fan_cw_switch = MDSwitch()
        self.fan_cw_switch.bind(active=self._on_fan_cw_switch_toggle)
        sw_fan_row.add_widget(self.fan_cw_switch)
        fan_left.add_widget(sw_fan_row)
        
        # Speed Slider
        speed_row = ResponsiveRow(orientation="horizontal", spacing=dp(6), size_hint=(1.0, None), height=dp(32))
        speed_row.add_widget(MDLabel(text="SPEED", font_style="Label", role="small", bold=True, size_hint_x=0.25, theme_text_color="Custom", text_color=COLOR_TEXT_SECONDARY))
        self.speed_slider = MDSlider(min=0.2, max=3.0, value=1.0, size_hint_x=0.58)
        self.speed_slider.add_widget(MDSliderHandle())
        self.speed_slider.bind(value=self._on_speed_slider)
        self.speed_lbl = MDLabel(text="1.00x", font_style="Body", role="small", bold=True, size_hint_x=0.17, theme_text_color="Custom", text_color=COLOR_TEXT_PRIMARY)
        speed_row.add_widget(self.speed_slider)
        speed_row.add_widget(self.speed_lbl)
        fan_left.add_widget(speed_row)
        
        fan_body.add_widget(fan_left)
        
        # Live OpenGL Fan Canvas Widget
        self.fan_widget = RadialFanPreviewWidget(size_hint=(0.36, 1.0))
        fan_body.add_widget(self.fan_widget)
        fan_card.add_widget(fan_body)
        content_box.add_widget(fan_card)
        
        scroll.add_widget(content_box)
        self.main_container.add_widget(scroll)
        
        # 3. Bottom Action FAB Button
        footer_card = GlyphixCard(size_hint_y=None, height=dp(58), padding=dp(4))
        self.stream_btn = GlyphixButton(style="filled", size_hint=(1.0, 1.0), md_bg_color=COLOR_ACCENT, align="center", on_release=lambda x: self._toggle_streaming())
        self.stream_btn_text = MDButtonText(
            text="▶ START LISTENER (SYNC PC RGB)",
            font_style="Title",
            role="medium",
            theme_text_color="Custom",
            text_color=(0, 0, 0, 1),
            bold=True
        )
        self.stream_btn.add_widget(self.stream_btn_text)
        footer_card.add_widget(self.stream_btn)
        self.main_container.add_widget(footer_card)
        
        root_anchor.add_widget(self.main_container)
        
        # Start background loop & responder
        self._start_pc_discovery_responder()
        Clock.schedule_interval(self._update_loop, 1.0 / 60.0)
        
        return root_anchor

    def _on_window_resize(self, instance, size):
        w, h = size
        self.main_container.width = min(w - dp(24), dp(680))
        Clock.schedule_once(self._refresh_layout, 0.05)

    def on_start(self):
        Clock.schedule_once(self._init_switches, 0.1)
        Clock.schedule_once(self._refresh_layout, 0.15)

    def _refresh_layout(self, dt=None):
        try:
            for w in self.root.walk():
                if isinstance(w, BoxLayout):
                    w.do_layout()
                elif isinstance(w, GlyphixButton):
                    w.adjust_pos()
        except Exception:
            pass

    def _init_switches(self, dt):
        try:
            self.openrgb_switch.active = self.use_openrgb
            self.typing_switch.active = self.typing_suppression
            self.fan_fx_switch.active = self.fan_viz_enabled
            self.fan_cw_switch.active = self.fan_clockwise
        except Exception:
            pass

    def log(self, msg):
        self.log_queue.put(msg)

    def _on_key_press(self):
        self.last_key_time = time.perf_counter()

    def _copy_ip(self):
        try:
            from kivy.core.clipboard import Clipboard
            Clipboard.copy(self.local_pc_ip)
            self.copy_btn_text.text = "COPIED ✓"
            Clock.schedule_once(lambda dt: setattr(self.copy_btn_text, 'text', 'COPY IP'), 1.5)
        except Exception:
            pass

    def _set_direction(self, new_dir):
        self.direction = new_dir
        if new_dir == "PHONE_TO_PC":
            self.btn_phone_to_pc.style = "filled"
            self.btn_phone_to_pc.md_bg_color = COLOR_ACCENT
            self.btn_phone_to_pc_text.text_color = (0, 0, 0, 1)
            
            self.btn_pc_to_phone.style = "outlined"
            self.btn_pc_to_phone.md_bg_color = (0, 0, 0, 0)
            self.btn_pc_to_phone_text.text_color = COLOR_TEXT_PRIMARY
            
            if not self.is_streaming:
                self.stream_btn_text.text = "▶ START LISTENER (SYNC PC RGB)"
        else:
            self.btn_pc_to_phone.style = "filled"
            self.btn_pc_to_phone.md_bg_color = COLOR_ACCENT
            self.btn_pc_to_phone_text.text_color = (0, 0, 0, 1)
            
            self.btn_phone_to_pc.style = "outlined"
            self.btn_phone_to_pc.md_bg_color = (0, 0, 0, 0)
            self.btn_phone_to_pc_text.text_color = COLOR_TEXT_PRIMARY
            
            if not self.is_streaming:
                self.stream_btn_text.text = "▶ START STREAMING TO PHONE"

    def _on_openrgb_switch_toggle(self, switch, value):
        self.use_openrgb = value
        if value:
            threading.Thread(target=self.rgb_manager.connect, args=(True,), daemon=True).start()
        else:
            self.rgb_manager.stop()
            self.header_btn_text.text = "🎯 OpenRGB Disconnected ▼"

    def _on_typing_switch_toggle(self, switch, value):
        self.typing_suppression = value

    def _on_fan_switch_toggle(self, switch, value):
        self.fan_viz_enabled = value
        self._save_current_header_config()

    def _on_fan_cw_switch_toggle(self, switch, value):
        self.fan_clockwise = value
        self._save_current_header_config()

    def _on_sens_slider(self, slider, value):
        self.rgb_sensitivity = float(value)
        self.sens_lbl.text = f"{self.rgb_sensitivity:.2f}x"

    def _on_decay_slider(self, slider, value):
        self.rgb_decay = float(value)
        self.decay_lbl.text = f"{int(self.rgb_decay * 100)}%"

    def _on_speed_slider(self, slider, value):
        self.fan_speed = float(value)
        self.speed_lbl.text = f"{self.fan_speed:.2f}x"
        self._save_current_header_config()

    # Dropdown Menus
    def _open_audio_menu(self, *args):
        if not self.wasapi_devices: return
        items = [
            {"text": d["name"][:32], "on_release": lambda x=d["name"]: self._select_audio_device(x)}
            for d in self.wasapi_devices
        ]
        self.audio_menu = MDDropdownMenu(caller=self.audio_menu_btn, items=items, width_mult=4)
        self.audio_menu.open()

    def _select_audio_device(self, dev_name):
        self.selected_device_name = dev_name
        self.audio_menu_btn_text.text = f"🎙 {dev_name[:20]} ▼"
        if self.audio_menu: self.audio_menu.dismiss()

    def _open_header_menu(self, *args):
        items = [
            {"text": h[:32], "on_release": lambda x=h: self._select_header(x)}
            for h in self.detected_headers
        ]
        self.header_menu = MDDropdownMenu(caller=self.header_btn, items=items, width_mult=4)
        self.header_menu.open()

    def _select_header(self, header_name):
        self.current_header = header_name
        self.header_btn_text.text = f"🎯 {header_name[:20]} ▼"
        if self.header_menu: self.header_menu.dismiss()
        
        cfg = self.header_configs.get(header_name)
        if cfg:
            self.fan_viz_enabled = cfg.get("enabled", True)
            self.fan_clockwise = cfg.get("clockwise", True)
            self.fan_mode_str = cfg.get("mode_str", "Radial VU Meter (Full Ring)")
            self.fan_theme_str = cfg.get("theme_str", "Glyph White")
            self.fan_leds_str = cfg.get("led_count_str", "16 LEDs (Standard)")
            self.fan_count_str = cfg.get("fan_count_str", "1 Fan")
            self.fan_speed = cfg.get("speed", 1.0)
            self.custom_hex = cfg.get("custom_hex", "#FFFFFF")
            self.custom_rgb = cfg.get("custom_color", (255, 255, 255))
            
            self.effect_btn_text.text = f"✨ {self.fan_mode_str} ▼"
            self.theme_btn_text.text = f"🎨 {self.fan_theme_str} ▼"
            self.leds_btn_text.text = f"💡 {self.fan_leds_str} ▼"
            self.fans_btn_text.text = f"🌀 {self.fan_count_str} ▼"
            self.speed_slider.value = self.fan_speed
            self.speed_lbl.text = f"{self.fan_speed:.2f}x"
            self.swatch_btn_text.text = self.custom_hex
            self.fan_fx_switch.active = self.fan_viz_enabled
            self.fan_cw_switch.active = self.fan_clockwise
        else:
            self._save_current_header_config()

    def _open_effect_menu(self, *args):
        items = [
            {"text": k, "on_release": lambda x=k: self._select_effect(x)}
            for k in FAN_MODE_DISPLAY
        ]
        self.effect_menu = MDDropdownMenu(caller=self.effect_btn, items=items, width_mult=4)
        self.effect_menu.open()

    def _select_effect(self, effect_name):
        self.fan_mode_str = effect_name
        self.effect_btn_text.text = f"✨ {effect_name} ▼"
        if self.effect_menu: self.effect_menu.dismiss()
        self._save_current_header_config()

    def _open_theme_menu(self, *args):
        items = [
            {"text": k, "on_release": lambda x=k: self._select_theme(x)}
            for k in FAN_THEME_DISPLAY
        ]
        self.theme_menu = MDDropdownMenu(caller=self.theme_btn, items=items, width_mult=4)
        self.theme_menu.open()

    def _select_theme(self, theme_name):
        self.fan_theme_str = theme_name
        self.theme_btn_text.text = f"🎨 {theme_name} ▼"
        if self.theme_menu: self.theme_menu.dismiss()
        
        if theme_name == "Custom Spectrum Color...":
            self._open_color_picker()
            return
        elif theme_name in THEME_PRESET_COLORS:
            r, g, b = THEME_PRESET_COLORS[theme_name]
            self.custom_hex = f"#{r:02x}{g:02x}{b:02x}".upper()
            self.custom_rgb = (r, g, b)
            self.swatch_btn_text.text = self.custom_hex
        self._save_current_header_config()

    def _open_leds_menu(self, *args):
        items = [
            {"text": k, "on_release": lambda x=k: self._select_leds(x)}
            for k in FAN_LEDS_DISPLAY
        ]
        self.leds_menu = MDDropdownMenu(caller=self.leds_btn, items=items, width_mult=3)
        self.leds_menu.open()

    def _select_leds(self, leds_name):
        self.fan_leds_str = leds_name
        self.leds_btn_text.text = f"💡 {leds_name} ▼"
        if self.leds_menu: self.leds_menu.dismiss()
        self._save_current_header_config()

    def _open_fans_menu(self, *args):
        items = [
            {"text": k, "on_release": lambda x=k: self._select_fans(x)}
            for k in FAN_COUNT_DISPLAY
        ]
        self.fans_menu = MDDropdownMenu(caller=self.fans_btn, items=items, width_mult=3)
        self.fans_menu.open()

    def _select_fans(self, fans_name):
        self.fan_count_str = fans_name
        self.fans_btn_text.text = f"🌀 {fans_name} ▼"
        if self.fans_menu: self.fans_menu.dismiss()
        self._save_current_header_config()

    def _open_color_picker(self):
        try:
            root_tk = tk.Tk()
            root_tk.withdraw()
            color_tuple = colorchooser.askcolor(color=self.custom_hex, title="GLYPHIX ARGB Spectrum Color Chooser")
            root_tk.destroy()
            if color_tuple and color_tuple[1]:
                hex_c = color_tuple[1].upper()
                rgb_c = tuple(int(c) for c in color_tuple[0])
                self.custom_hex = hex_c
                self.custom_rgb = rgb_c
                self.fan_theme_str = "Custom Spectrum Color..."
                self.theme_btn_text.text = f"🎨 {self.fan_theme_str} ▼"
                self.swatch_btn_text.text = hex_c
                self._save_current_header_config()
        except Exception as e:
            self.log(f"Color chooser error: {e}")

    def _save_current_header_config(self):
        hdr = self.current_header
        mode_val = FAN_MODE_DISPLAY.get(self.fan_mode_str, "vu_meter")
        theme_val = FAN_THEME_DISPLAY.get(self.fan_theme_str, "white")
        ring_size = FAN_LEDS_DISPLAY.get(self.fan_leds_str, 16)
        num_fans = FAN_COUNT_DISPLAY.get(self.fan_count_str, 1)
        
        cfg = {
            "mode": mode_val,
            "theme": theme_val,
            "mode_str": self.fan_mode_str,
            "theme_str": self.fan_theme_str,
            "custom_color": self.custom_rgb,
            "custom_hex": self.custom_hex,
            "fan_ring_size": ring_size,
            "fan_count": num_fans,
            "clockwise": self.fan_clockwise,
            "speed": self.fan_speed,
            "enabled": self.fan_viz_enabled,
            "led_count_str": self.fan_leds_str,
            "fan_count_str": self.fan_count_str
        }
        self.header_configs[hdr] = cfg

        if "sync all" in hdr.lower():
            for h in self.detected_headers:
                if "sync all" not in h.lower():
                    self.header_configs[h] = dict(cfg)

        self.rgb_manager.update_header_configs(self.header_configs)
        self.rgb_manager.update_fan_config(ring_size, num_fans)

    def _rescan_rgb(self):
        self.scan_btn_text.text = "SCAN..."
        def _scan():
            self.use_openrgb = True
            connected = self.rgb_manager.connect(auto_launch=True)
            headers = self.rgb_manager.get_detected_headers()
            Clock.schedule_once(lambda dt: self._on_rescan_done(headers, connected))
        threading.Thread(target=_scan, daemon=True).start()

    def _on_rescan_done(self, headers, connected):
        self.scan_btn_text.text = "↻ SCAN"
        if not connected:
            self.header_btn_text.text = "🎯 No OpenRGB Connected ▼"
            return
        self._on_openrgb_headers_detected(headers)

    def _on_openrgb_headers_detected(self, headers):
        if not headers: return
        self.detected_headers = headers
        self.current_header = headers[0]
        self.header_btn_text.text = f"🎯 {headers[0][:20]} ▼"
        self.log(f"OpenRGB: Detected {len(headers)} connected ARGB devices/headers.")

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

    def _toggle_discovery(self):
        if self.is_discovering:
            self.stop_discovery_event.set()
        else:
            self.is_discovering = True
            self.stop_discovery_event.clear()
            self.discover_btn_text.text = "CANCEL"
            self.status_capsule_lbl.text = "● SEARCHING..."
            threading.Thread(target=self._discovery_worker, daemon=True).start()

    def _discovery_worker(self):
        found = None
        self.log("Broadcasting UDP discovery...")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
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
                if found: break
                time.sleep(0.2)
            except: continue
        sock.close()
        Clock.schedule_once(lambda dt: self._on_discovery_done(found))

    def _on_discovery_done(self, found):
        self.is_discovering = False
        self.discover_btn_text.text = "🔍 DISCOVER"
        if found:
            self.phone_ip_field.text = found
            self.status_capsule_lbl.text = f"● PHONE FOUND ({found})"
            self.log(f"Discovery: Found Nothing Phone at {found}")
        else:
            self.status_capsule_lbl.text = "● STANDBY"
            self.log("Discovery: No Nothing Phone detected on local subnet.")

    def _toggle_streaming(self):
        if self.is_streaming:
            self.stop_stream_event.set()
        else:
            if self.direction == "PHONE_TO_PC":
                self.is_streaming = True
                self.stop_stream_event.clear()
                self.stream_btn_text.text = "⏹ STOP LISTENER"
                self.status_capsule_lbl.text = "● LISTENING :12347"
                if self.typing_suppression: self.hook_watcher.start()
                threading.Thread(target=self._listener_worker, daemon=True).start()
            else:
                addr = self.phone_ip_field.text.strip()
                if not addr:
                    self.log("Error: Enter Nothing Phone IP address first.")
                    return
                
                device = None
                for d in self.wasapi_devices:
                    if d["name"] == self.selected_device_name:
                        device = d
                        break
                if not device and self.wasapi_devices:
                    device = self.wasapi_devices[0]
                if not device: return

                self.is_streaming = True
                self.stop_stream_event.clear()
                self.stream_btn_text.text = "⏹ STOP STREAMING"
                self.status_capsule_lbl.text = f"● STREAMING TO {addr}"
                if self.typing_suppression: self.hook_watcher.start()
                threading.Thread(target=self._stream_worker, args=(addr, device, UDP_PORT), daemon=True).start()

    def _listener_worker(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", UDP_PORT))
            sock.settimeout(0.5)
        except Exception as e:
            self.log(f"Port {UDP_PORT} error: {e}")
            Clock.schedule_once(lambda dt: self._on_stream_stopped())
            return

        self.log(f"Listener active on port {UDP_PORT}. Waiting for Nothing Phone stream...")
        threading.Thread(target=self._viz_worker, args=(TARGET_RATE,), daemon=True).start()

        packets = 0
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
                    self.last_packet_time = time.time()
                except socket.timeout:
                    continue
                except Exception:
                    time.sleep(0.01)
        finally:
            self.rgb_manager.stop()
            try: sock.close()
            except: pass
            Clock.schedule_once(lambda dt: self._on_stream_stopped())

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
                self.log(f"Audio open error: {e}")
                Clock.schedule_once(lambda dt: self._on_stream_stopped())
                return

        threading.Thread(target=self._viz_worker, args=(actual_rate,), daemon=True).start()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

        try:
            while not self.stop_stream_event.is_set():
                raw = stream.read(chunk_size, exception_on_overflow=False)
                samples = np.frombuffer(raw, dtype=np.int16)
                mono = samples.reshape(-1, channels).mean(axis=1).astype(np.int16) if channels > 1 else samples
                
                try: self.viz_queue.put_nowait(mono)
                except queue.Full: pass

                self.last_packet_time = time.time()
                sock.sendto(mono.tobytes(), (addr, port))
        except Exception as e:
            self.log(f"Stream worker error: {e}")
        finally:
            self.rgb_manager.stop()
            try: stream.stop_stream(); stream.close(); p.terminate(); sock.close()
            except: pass
            Clock.schedule_once(lambda dt: self._on_stream_stopped())

    def _viz_worker(self, actual_rate):
        bass_engine = PureBassEngine(sample_rate=actual_rate)
        last_viz_time = 0
        
        while not self.stop_stream_event.is_set():
            try:
                mono = self.viz_queue.get(timeout=0.1)
                now = time.time()
                
                if now - last_viz_time > 0.025:
                    sf = mono.astype(np.float32) / 32768.0
                    n = len(sf)
                    fft = np.abs(np.fft.rfft(sf * np.hanning(n))) / (n / 2.0)
                    
                    freqs = np.geomspace(40, 15000, 64)
                    bin_idx = np.clip(freqs / (actual_rate / n), 0, len(fft) - 1)
                    points = np.interp(bin_idx, np.arange(len(fft)), fft)
                    
                    rms = float(np.sqrt(np.mean(sf ** 2)))
                    peak_val = float(np.max(np.abs(sf)))
                    sensitivity = self.rgb_sensitivity
                    decay_rate = self.rgb_decay

                    raw_audio_level = float(np.clip((rms * 2.8 * 0.75 + peak_val * 1.5 * 0.25) * sensitivity, 0.0, 1.0))
                    pulse = bass_engine.process(mono, decay=decay_rate) * sensitivity
                    energy = float(np.clip(raw_audio_level * 1.2, 0.0, 1.0))
                    self.last_pulse = pulse

                    self.level_queue.put(list(np.clip(points * 15 * sensitivity, 0, 1)))

                    fan_mode_val = FAN_MODE_DISPLAY.get(self.fan_mode_str, "vu_meter")
                    fan_theme_val = FAN_THEME_DISPLAY.get(self.fan_theme_str, "white")
                    fan_leds_val = FAN_LEDS_DISPLAY.get(self.fan_leds_str, 16)
                    fan_count_val = FAN_COUNT_DISPLAY.get(self.fan_count_str, 1)

                    fan_preview = None
                    if self.use_openrgb:
                        typing_pause = self.typing_suppression and (time.perf_counter() - self.last_key_time < 1.5)
                        if not typing_pause:
                            br, bg, bb = self.selected_rgb
                            fan_preview = self.rgb_manager.sync(
                                (br/255)*pulse, (bg/255)*pulse, (bb/255)*pulse,
                                raw_audio_level=raw_audio_level,
                                energy=energy, pulse=pulse, spectrum=points,
                                fan_viz_enabled=self.fan_viz_enabled,
                                fan_mode=fan_mode_val,
                                fan_theme=fan_theme_val,
                                fan_clockwise=self.fan_clockwise,
                                fan_speed=self.fan_speed,
                                fan_leds=fan_leds_val,
                                fan_count=fan_count_val,
                                decay_rate=decay_rate,
                                custom_color=self.custom_rgb
                            )
                        else:
                            self.rgb_manager.stop()

                    if fan_preview is None and self.fan_viz_enabled:
                        single_fan_colors = self.rgb_manager.fan_visualizer.render_fan_ring(
                            num_leds=fan_leds_val,
                            raw_level=raw_audio_level,
                            energy=energy,
                            pulse=pulse,
                            mode=fan_mode_val,
                            theme=fan_theme_val,
                            clockwise=self.fan_clockwise,
                            speed_mult=self.fan_speed,
                            fan_idx=0,
                            total_fans=fan_count_val,
                            spectrum=points,
                            decay_rate=decay_rate,
                            custom_color=self.custom_rgb
                        )
                        fan_preview = {"__all__": single_fan_colors}

                    if fan_preview:
                        try: self.fan_preview_queue.put_nowait(fan_preview)
                        except queue.Full: pass
                    
                    last_viz_time = now
            except queue.Empty:
                continue

    def _on_stream_stopped(self):
        self.is_streaming = False
        btn_txt = "▶ START LISTENER (SYNC PC RGB)" if self.direction == "PHONE_TO_PC" else "▶ START STREAMING TO PHONE"
        self.stream_btn_text.text = btn_txt
        self.status_capsule_lbl.text = "● STANDBY"
        self.level_queue.put([0.0]*64)
        try: self.fan_preview_queue.put_nowait({"__all__": [(0, 0, 0)] * 16})
        except: pass
        self.hook_watcher.stop()

    def _update_loop(self, dt):
        self.anim_phase += 0.08
        now = time.perf_counter()

        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            print(f"[{time.strftime('%H:%M:%S')}] {msg}")

        # 1. Status Capsule Pulse
        if self.is_streaming:
            wave_glyphs = [" ▂▃▅▆▇▆▅▃▂ ", "  ▂▃▅▇█▇▅▃ ", "   ▂▃▅███▅▃ ", " ▃▅▇███▇▅▃ "]
            cur_glyph = wave_glyphs[int(self.anim_phase * 2.5) % len(wave_glyphs)]
            lbl = "⏹ STOP LISTENER" if self.direction == "PHONE_TO_PC" else "⏹ STOP STREAMING"
            self.stream_btn_text.text = f"{lbl}   [{cur_glyph}]"
        elif self.is_discovering:
            radar_dots = [".  ", ".. ", "...", " ..", "  ."]
            radar_str = radar_dots[int(self.anim_phase * 2.0) % len(radar_dots)]
            self.status_capsule_lbl.text = f"● SEARCHING {radar_str}"

        # 2. Audio Spectrum Ballistics
        last_points = None
        while not self.level_queue.empty():
            last_points = self.level_queue.get_nowait()
        
        num_dots = len(self.spectrum_points)
        is_audio_active = (time.time() - self.last_packet_time < 0.5) and (last_points is not None and max(last_points) > 0.01)

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
                        self.viz_peaks[i] = max(0.0, self.viz_peaks[i] - 1.6 * dt)
        else:
            for i in range(num_dots):
                wave = (math.sin(self.anim_phase * 1.2 + i * 0.22) * 0.5 + 0.5) * 0.16 + 0.03
                self.spectrum_points[i] = self.spectrum_points[i] * 0.88 + wave * 0.12
                if i < len(self.viz_peaks):
                    self.viz_peaks[i] = max(0.0, self.viz_peaks[i] * 0.90)

        self.viz_widget.update_spectrum(self.spectrum_points, self.viz_peaks)

        # 3. Case Fan Preview Animation
        dir_mult = 1.0 if self.fan_clockwise else -1.0
        self.fan_sweep_angle = (self.fan_sweep_angle + dir_mult * self.fan_speed * 0.06) % (2.0 * math.pi)

        last_fan_preview = None
        while not self.fan_preview_queue.empty():
            last_fan_preview = self.fan_preview_queue.get_nowait()
        
        if last_fan_preview is not None:
            chosen = None
            if isinstance(last_fan_preview, dict):
                cur_hdr = self.current_header
                if cur_hdr in last_fan_preview:
                    chosen = last_fan_preview[cur_hdr]
                else:
                    chosen = last_fan_preview.get("__all__")
            elif isinstance(last_fan_preview, list):
                chosen = last_fan_preview
            if chosen:
                self.fan_widget.update_fan(chosen, self.fan_sweep_angle, pulse=self.last_pulse, hub_text=self.current_header.split()[0][:6])
        elif not self.is_streaming:
            idle_ring = []
            num_leds = 16
            for i in range(num_leds):
                phi = (2.0 * math.pi * i) / num_leds
                diff = (self.fan_sweep_angle - phi) % (2.0 * math.pi) if self.fan_clockwise else (phi - self.fan_sweep_angle) % (2.0 * math.pi)
                tail_len = math.pi * 1.4
                brightness = (1.0 - diff / tail_len) ** 1.8 if diff <= tail_len else 0.0
                cr, cg, cb = self.custom_rgb
                idle_ring.append((int(cr * brightness * 0.8), int(cg * brightness * 0.8), int(cb * brightness * 0.8)))
            self.fan_widget.update_fan(idle_ring, self.fan_sweep_angle, pulse=0.0, hub_text="GLYPH")

if __name__ == "__main__":
    GlyphixMaterialApp().run()
