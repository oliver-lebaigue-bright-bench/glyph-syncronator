import socket
import pyaudio
import numpy as np
import time
import sys
import threading
import queue
import asyncio
import math
import webbrowser

try:
    from pynput import keyboard
    HAS_PYNPUT = True
except ImportError:
    HAS_PYNPUT = False

import customtkinter as ctk
from tkinter import messagebox

try:
    from openrgb import OpenRGBClient
    from openrgb.utils import RGBColor
    OPENRGB_AVAILABLE = True
except ImportError:
    OPENRGB_AVAILABLE = False
    OpenRGBClient = None
    RGBColor = None

UDP_PORT = 12347
DISCOVERY_PORT = 12348
OPENRGB_PORT = 6742
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 44100 

# ==============================================================================
# PURE MONOCHROME / NOTHING OS DESIGN TOKENS (ZERO RED, ZERO GREEN)
# ==============================================================================
COLOR_BG = "#0B0C0E"              # Deep Obsidian Background
COLOR_SURFACE = "#14161A"         # Elevated Surface Card
COLOR_SURFACE_INNER = "#1B1D23"   # Nested Container / Input Surface
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

FONT_FAMILY = "SF Pro Display" if sys.platform == "darwin" else "Inter"
FONT_MONO = "Menlo" if sys.platform == "darwin" else "Consolas"
FONT_LOGO = "Courier New"

class KeyboardHookWatcher:
    def __init__(self, on_press_callback):
        self.on_press_callback = on_press_callback
        self.listener = None

    def start(self):
        if not HAS_PYNPUT: return
        self.listener = keyboard.Listener(on_press=lambda k: self.on_press_callback())
        self.listener.start()

    def stop(self):
        if self.listener:
            self.listener.stop()
            self.listener = None

class PureBassEngine:
    def __init__(self, sample_rate=44100):
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

class OpenRGBManager:
    def __init__(self, logger=None):
        self.logger = logger
        self.client = None
        self.connected = False
        self.devices = []
        self.last_sync_time = 0.0

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
            if self.logger: self.logger(f"OpenRGB: Connected to {len(self.devices)} devices.")
            return True
        except Exception as e:
            self.connected = False
            if self.logger: self.logger(f"OpenRGB Error: {e}")
            return False

    def sync(self, r, g, b):
        if not self.connected: return
        now = time.perf_counter()
        if now - self.last_sync_time < 0.02: return 
        color = RGBColor(int(r * 255), int(g * 255), int(b * 255))
        for dev in self.devices:
            try: dev.set_color(color)
            except: pass
        self.last_sync_time = now

    def stop(self):
        if not self.connected: return
        black = RGBColor(0, 0, 0)
        for dev in self.devices:
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

def get_macos_audio_devices():
    p = pyaudio.PyAudio()
    devices = []
    try:
        for i in range(p.get_device_count()):
            info = p.get_device_info_by_index(i)
            if info["maxInputChannels"] > 0:
                devices.append({
                    "index": i,
                    "name": info["name"],
                    "is_default": False,
                    "rate": int(info["defaultSampleRate"]),
                    "channels": int(info["maxInputChannels"])
                })
    except Exception: pass
    finally: p.terminate()
    return devices

# ==============================================================================
# MAIN APPLICATION WINDOW (macOS - MONOCHROME / NOTHING OS STYLE)
# ==============================================================================
class CompanionApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("GLYPHIX // macOS COMPANION")
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
        
        self.audio_devices = get_macos_audio_devices()
        self.spectrum_points = [0.0] * 64
        self.viz_peaks = [0.0] * 64
        self.viz_peak_holds = [0.0] * 64
        self.log_queue = queue.Queue()
        self.level_queue = queue.Queue()
        
        self.rgb_manager = OpenRGBManager(logger=self.log)
        self.hook_watcher = KeyboardHookWatcher(self._on_key_press)
        self.last_key_time = 0.0

        self._setup_ui()
        self._refresh_audio_sources()
        self._check_blackhole_status()
        self._start_pc_discovery_responder()
        self._update_loop()
        self.log("Ready. For system audio, please ensure BlackHole 2ch is selected.")

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
                        sock.sendto(b"GLYPHIX_PC_DISCOVERY_RESPONSE", addr)
                        self.log(f"Discovery: Sent Mac announcement to Phone ({addr[0]})")
                except socket.timeout:
                    continue
                except Exception:
                    time.sleep(1)
        except Exception as e:
            self.log(f"Discovery responder error: {e}")
        finally:
            try: sock.close()
            except: pass

    def _check_blackhole_status(self):
        has_blackhole = any("BlackHole" in d["name"] for d in self.audio_devices)
        if not has_blackhole:
            if messagebox.askyesno("BlackHole Missing", 
                                  "Glyphix requires the 'BlackHole' virtual audio driver to capture system audio on macOS.\n\n"
                                  "Without it, you can only stream from your microphone.\n\n"
                                  "Would you like to visit the download page now?"):
                webbrowser.open("https://existential.audio/blackhole/")

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
            logo_row, text=" macOS",
            font=ctk.CTkFont(family=FONT_FAMILY, size=11, weight="bold"),
            text_color=COLOR_ACCENT
        )
        self.ver_badge.pack(side="left", padx=(4, 0), pady=(4, 0))

        self.sub_label = ctk.CTkLabel(
            brand_frame, text="AUDIO REACTIVE GLYPH STREAMER",
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

        # CARD 1: AUDIO SOURCE & SPECTRUM
        self._create_card(self.main_container, "AUDIO SPECTRUM & SOURCE", "Real-time FFT audio visualizer & loopback input")

        self.audio_combo = ctk.CTkOptionMenu(
            self.last_card_body, values=[],
            fg_color=COLOR_SURFACE_INNER,
            button_color=COLOR_BORDER,
            button_hover_color=COLOR_BORDER_LIGHT,
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

        # CARD 2: WI-FI CONNECTIVITY (UDP ONLY)
        self._create_card(self.main_container, "WI-FI CONNECTIVITY", "Ultra-fast UDP audio stream to Nothing Phone")

        addr_row = ctk.CTkFrame(self.last_card_body, fg_color="transparent")
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

        # CARD 3: HARDWARE SYNC & AUDIO DSP
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
            command=lambda v: self.sens_val_lbl.configure(text=f"{float(v):.2f}x")
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
            command=lambda v: self.decay_val_lbl.configure(text=f"{int(float(v) * 100)}%")
        )
        self.decay_slider.pack(fill="x", pady=(2, 4))

        # CARD 4: ADVANCED LOGS
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

        # 3. FLOATING ACTION FOOTER
        self.footer = ctk.CTkFrame(self, fg_color=COLOR_BG, height=84, corner_radius=0)
        self.footer.grid(row=2, column=0, sticky="ew", padx=20, pady=(4, 16))

        self.stream_btn = ctk.CTkButton(
            self.footer, text="▶ START STREAMING TO PHONE",
            height=54, corner_radius=16,
            fg_color=COLOR_WHITE, hover_color=COLOR_ACCENT_HOVER,
            text_color="#000000",
            font=ctk.CTkFont(family=FONT_FAMILY, size=13, weight="bold"),
            command=self._toggle_streaming
        )
        self.stream_btn.pack(fill="x")

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

    def _refresh_audio_sources(self):
        names = [d["name"] for d in self.audio_devices]
        self.audio_combo.configure(values=names)
        if names: self.audio_combo.set(names[0])

    def _toggle_openrgb(self):
        if self.use_openrgb.get(): threading.Thread(target=self.rgb_manager.connect, daemon=True).start()
        else: self.rgb_manager.stop()

    def _toggle_discovery(self):
        if self.is_discovering: self.stop_discovery_event.set()
        else:
            self.is_discovering = True
            self.stop_discovery_event.clear()
            self.discover_btn.configure(text="CANCEL", fg_color=COLOR_BORDER, text_color=COLOR_TEXT_PRIMARY)
            self.status_pill.configure(text="SEARCHING...", text_color=COLOR_TEXT_PRIMARY)
            self.status_dot.configure(text_color=COLOR_WHITE)
            threading.Thread(target=self._discovery_worker, daemon=True).start()

    def _discovery_worker(self):
        found = None
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
        self.discover_btn.configure(text="🔍 DISCOVER", fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY)
        if found:
            self.addr_entry.delete(0, "end")
            self.addr_entry.insert(0, found)
            self.status_pill.configure(text="PHONE FOUND", text_color=COLOR_WHITE)
            self.status_dot.configure(text_color=COLOR_WHITE)
            self.log(f"Discovery: Found {found}")
        else:
            self.status_pill.configure(text="STANDBY", text_color=COLOR_TEXT_SECONDARY)
            self.status_dot.configure(text_color=COLOR_TEXT_MUTED)
            self.log("Discovery: No device found.")

    def _toggle_streaming(self):
        if self.is_streaming: self.stop_stream_event.set()
        else:
            addr = self.addr_entry.get().strip()
            if not addr:
                messagebox.showerror("Error", "Enter IP address first.")
                return
            selected_name = self.audio_combo.get()
            device = next((d for d in self.audio_devices if d["name"] == selected_name), None)
            if not device: return
            self.is_streaming = True
            self.stop_stream_event.clear()
            self.stream_btn.configure(text="⏹ STOP STREAMING", fg_color=COLOR_SURFACE_HOVER, text_color=COLOR_TEXT_PRIMARY)
            self.status_pill.configure(text="STREAMING TO PHONE", text_color=COLOR_WHITE)
            self.status_dot.configure(text_color=COLOR_WHITE)
            if self.typing_suppression.get(): self.hook_watcher.start()
            threading.Thread(target=self._stream_worker, args=(addr, device), daemon=True).start()

    def _stream_worker(self, addr, device):
        p = pyaudio.PyAudio()
        chunk_size = 512
        try:
            stream = p.open(format=FORMAT, channels=device["channels"], rate=device["rate"], input=True, input_device_index=device["index"], frames_per_buffer=chunk_size)
            actual_rate = device["rate"]
        except Exception as e:
            self.after(0, self._on_stream_error, str(e))
            return
        threading.Thread(target=self._viz_worker, args=(actual_rate,), daemon=True).start()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        interp_indices = None
        if actual_rate != TARGET_RATE: interp_indices = np.linspace(0, chunk_size - 1, int(chunk_size * TARGET_RATE / actual_rate))
        try:
            while not self.stop_stream_event.is_set():
                raw = stream.read(chunk_size, exception_on_overflow=False)
                samples = np.frombuffer(raw, dtype=np.int16)
                mono = samples.reshape(-1, device["channels"]).mean(axis=1).astype(np.int16) if device["channels"] > 1 else samples
                try: self.viz_queue.put_nowait(mono)
                except queue.Full: pass
                if interp_indices is not None: data = np.interp(interp_indices, np.arange(len(mono)), mono).astype(np.int16).tobytes()
                else: data = mono.tobytes()
                self._last_packet_time = time.time()
                sock.sendto(data, (addr, UDP_PORT))
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
                    self.level_queue.put(list(np.clip(points * 15, 0, 1)))
                    if self.use_openrgb.get():
                        typing_pause = self.typing_suppression.get() and (time.perf_counter() - self.last_key_time < 1.5)
                        if not typing_pause:
                            pulse = bass_engine.process(mono, decay=self.rgb_decay.get()) * self.rgb_sensitivity.get()
                            br, bg, bb = self.selected_rgb
                            self.rgb_manager.sync((br/255)*pulse, (bg/255)*pulse, (bb/255)*pulse)
                        else: self.rgb_manager.stop()
                    last_viz_time = now
            except queue.Empty: continue

    def _on_stream_error(self, err):
        messagebox.showerror("Stream Error", err); self._on_stream_stopped()

    def _on_stream_stopped(self):
        self.is_streaming = False
        self.stream_btn.configure(text="▶ START STREAMING TO PHONE", fg_color=COLOR_WHITE, text_color="#000000")
        self.status_pill.configure(text="STANDBY", text_color=COLOR_TEXT_SECONDARY)
        self.status_dot.configure(text_color=COLOR_TEXT_MUTED)
        self.level_queue.put([0.0]*64)
        self.hook_watcher.stop()

    def _update_loop(self):
        self._anim_phase += 0.08
        now = time.perf_counter()

        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            self.console.configure(state="normal"); self.console.insert("end", f"[{time.strftime('%H:%M:%S')}] {msg}\n"); self.console.see("end"); self.console.configure(state="disabled")
        
        if self.is_streaming:
            pulse_brightness = (math.sin(self._anim_phase * 3.2) * 0.5 + 0.5)
            glow_val = int(140 + pulse_brightness * 115)
            glow_col = f"#{glow_val:02x}{glow_val:02x}{glow_val:02x}"
            self.status_dot.configure(text_color=glow_col)
            wave_glyphs = [" ▂▃▅▆▇▆▅▃▂ ", "  ▂▃▅▇█▇▅▃ ", "   ▂▃▅███▅▃ ", " ▃▅▇███▇▅▃ "]
            cur_glyph = wave_glyphs[int(self._anim_phase * 2.5) % len(wave_glyphs)]
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

        last_points = None
        while not self.level_queue.empty(): last_points = self.level_queue.get_nowait()
        num_dots = len(self.spectrum_points)
        is_audio_active = (time.time() - self._last_packet_time < 0.5) and (last_points is not None and max(last_points) > 0.01)

        if is_audio_active and last_points:
            interpolated = np.interp(np.linspace(0, 63, num_dots), np.arange(64), last_points)
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
        self.after(16, self._update_loop)

    def _init_viz_dots(self, count=64):
        if self.viz_dots:
            for col in self.viz_dots:
                for dot in col: self.viz_canvas.delete(dot)
        self.viz_dots = []; self.spectrum_points = [0.0] * count
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
        w = self.viz_canvas.winfo_width(); h = self.viz_canvas.winfo_height()
        if w <= 1: return
        target_spacing = 13; new_count = max(8, w // target_spacing)
        if new_count != len(self.viz_dots):
            self._init_viz_dots(new_count)
        dot_spacing = w / new_count; dot_size = max(4.0, dot_spacing - 4.5)
        for i in range(new_count):
            x = i * dot_spacing + dot_spacing / 2.0
            for d in range(8):
                dy = h - (d * 9.5 + 12); dot = self.viz_dots[i][d]
                self.viz_canvas.coords(dot, x - dot_size/2.0, dy - dot_size/2.0, x + dot_size/2.0, dy + dot_size/2.0)

    def _draw_viz(self):
        if not self.viz_dots: return
        num_cols = len(self.viz_dots)
        if len(self._viz_cache) != num_cols: self._viz_cache = [(-1, -1)] * num_cols
        gradient_colors = [
            "#22262E", "#323844", "#444C5C", "#5A657A",
            "#7B89A0", "#A2B0C7", "#CBD5E1", "#FFFFFF"
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
                        color = "#FFFFFF"
                    else:
                        color = "#15171E"
                    self.viz_canvas.itemconfig(self.viz_dots[i][d], fill=color)
                self._viz_cache[i] = cache_state

if __name__ == "__main__":
    ctk.set_appearance_mode("Dark"); app = CompanionApp(); app.mainloop()
