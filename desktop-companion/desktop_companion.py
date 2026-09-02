import socket
import pyaudiowpatch as pyaudio
import numpy as np
import time
import argparse
import sys
import threading
import queue
import asyncio

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
    RGBColor = None
    DeviceType = None

# Constants
UDP_PORT = 12347
DISCOVERY_PORT = 12348
OPENRGB_PORT = 6742
BLE_SERVICE_UUID = "7d9c63c0-37b1-4122-861f-36655c687e46"
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 48000

# Theme & Colors (Refined Pro Identity)
COLOR_BG = "#080808"
COLOR_CARD = "#121212"
COLOR_CARD_HOVER = "#1A1A1A"
COLOR_BORDER = "#222222"
COLOR_ACCENT = "#E01B22"
COLOR_WHITE = "#FFFFFF"
COLOR_MUTED = "#666666"
COLOR_TEXT = "#EAEAEA"

FONT_MAIN = "Segoe UI" if sys.platform == "win32" else "Inter"
FONT_LOGO = "Courier New" # Keeping brand identity for logo

# Windows Hook Setup
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
    """Detects keystrokes to optionally suppress RGB pulses while typing."""
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
    """High-precision bass impulse detector."""
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

class OpenRGBManager:
    """Handles synchronization with PC peripherals."""
    def __init__(self, logger=None):
        self.logger = logger
        self.client = None
        self.connected = False
        self.devices = []
        self.r_val, self.g_val, self.b_val = 0.0, 0.0, 0.0
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
            if ip != '127.0.0.1':
                parts = ip.split('.')
                broadcasts.add(".".join(parts[:-1] + ["255"]))
    except Exception: pass
    return [b for b in broadcasts]

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
        
        self.rgb_manager = OpenRGBManager(logger=self.log)
        self.hook_watcher = KeyboardHookWatcher(self._on_key_press)
        self.last_key_time = 0.0

        self._setup_ui()
        self._refresh_audio_sources()
        self._update_loop()

    def _setup_ui(self):
        # Header
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

        # Main Scrollable Area
        self.main_container = ctk.CTkScrollableFrame(self, fg_color=COLOR_BG, corner_radius=0)
        self.main_container.grid(row=1, column=0, sticky="nsew", padx=5)
        
        # Audio Module
        self._create_card(self.main_container, "AUDIO SOURCE")
        self.audio_combo = ctk.CTkOptionMenu(self.last_card, values=[], 
                                            fg_color=COLOR_BORDER, button_color=COLOR_BORDER,
                                            button_hover_color=COLOR_ACCENT, dropdown_fg_color=COLOR_CARD,
                                            font=ctk.CTkFont(family=FONT_MAIN, size=12))
        self.audio_combo.pack(fill="x", padx=20, pady=(5, 15))

        self.viz_canvas = ctk.CTkCanvas(self.last_card, height=100, bg=COLOR_CARD, highlightthickness=0)
        self.viz_canvas.pack(fill="x", padx=20, pady=(0, 20))
        self.viz_canvas.bind("<Configure>", self._resize_viz)
        
        # Connectivity Module
        self._create_card(self.main_container, "CONNECTIVITY")
        
        conn_switch_frame = ctk.CTkFrame(self.last_card, fg_color="transparent")
        conn_switch_frame.pack(fill="x", padx=20, pady=(5, 10))
        
        ctk.CTkRadioButton(conn_switch_frame, text="UDP (Wi-Fi)", variable=self.conn_type, value="UDP",
                           hover_color=COLOR_ACCENT, fg_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12)).pack(side="left", padx=(0, 20))
        ctk.CTkRadioButton(conn_switch_frame, text="Bluetooth", variable=self.conn_type, value="BT",
                           hover_color=COLOR_ACCENT, fg_color=COLOR_ACCENT, font=ctk.CTkFont(family=FONT_MAIN, size=12)).pack(side="left")
        
        addr_frame = ctk.CTkFrame(self.last_card, fg_color="transparent")
        addr_frame.pack(fill="x", padx=20, pady=(0, 20))
        
        self.addr_entry = ctk.CTkEntry(addr_frame, placeholder_text="IP or MAC Address",
                                      fg_color=COLOR_BG, border_color=COLOR_BORDER, height=36,
                                      font=ctk.CTkFont(family=FONT_MAIN, size=12))
        self.addr_entry.pack(side="left", fill="x", expand=True, padx=(0, 10))
        
        self.discover_btn = ctk.CTkButton(addr_frame, text="DISCOVER", width=90, height=36,
                                         fg_color=COLOR_WHITE, text_color=COLOR_BG, hover_color=COLOR_ACCENT,
                                         font=ctk.CTkFont(family=FONT_MAIN, size=12, weight="bold"), 
                                         command=self._toggle_discovery)
        self.discover_btn.pack(side="right")

        # Hardware Module
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

        # Advanced / Logs Section
        self.adv_btn = ctk.CTkButton(self.main_container, text="SHOW LOGS",
                                    fg_color="transparent", text_color=COLOR_MUTED, 
                                    hover_color=COLOR_CARD, font=ctk.CTkFont(family=FONT_MAIN, size=10, weight="bold"),
                                    command=self._toggle_advanced)
        self.adv_btn.pack(pady=10)
        
        self.console = ctk.CTkTextbox(self.main_container, height=120, fg_color=COLOR_BG, 
                                     border_color=COLOR_BORDER, border_width=1,
                                     font=ctk.CTkFont(family="Consolas", size=10), text_color=COLOR_MUTED)
        # Hidden by default in the toggle method
        self.console.configure(state="disabled")

        # Footer Action
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
            threading.Thread(target=self.rgb_manager.connect, daemon=True).start()
        else:
            self.rgb_manager.stop()

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
            addr = self.addr_entry.get().strip()
            if not addr:
                messagebox.showerror("Error", "Enter IP/MAC address first.")
                return
            
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
            self.status_pill.configure(text="STREAMING", text_color=COLOR_ACCENT)
            self.status_dot.configure(text_color=COLOR_ACCENT)
            
            if self.typing_suppression.get(): self.hook_watcher.start()
            
            threading.Thread(target=self._stream_worker, args=(addr, device), daemon=True).start()

    def _stream_worker(self, addr, device):
        p = pyaudio.PyAudio()
        native_rate = device["rate"]
        channels = device["channels"]
        chunk_size = 512 # Smaller chunks to fit UDP MTU
        
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
            for port in range(1, 10):
                try:
                    s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
                    s.settimeout(2.0); s.connect((addr, port)); sock = s
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
                
                # Offload viz processing to stay under GIL limits
                try: self.viz_queue.put_nowait(mono)
                except queue.Full: pass

                # Network send should be as immediate as possible
                if interp_indices is not None:
                    data = np.interp(interp_indices, np.arange(len(mono)), mono).astype(np.int16).tobytes()
                else:
                    data = mono.tobytes()
                
                if conn_type == "BT": sock.sendall(data)
                else: sock.sendto(data, (addr, UDP_PORT))
                
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
                    # FFT for visualizer dots
                    sf = mono.astype(np.float32) / 32768.0
                    n = len(sf)
                    fft = np.abs(np.fft.rfft(sf * np.hanning(n))) / (n / 2.0)
                    
                    freqs = np.geomspace(40, 15000, 64)
                    bin_idx = np.clip(freqs / (actual_rate / n), 0, len(fft) - 1)
                    points = np.interp(bin_idx, np.arange(len(fft)), fft)
                    
                    self.level_queue.put(list(np.clip(points * 15, 0, 1)))
                    
                    # RGB Hardware Sync
                    if self.use_openrgb.get():
                        typing_pause = self.typing_suppression.get() and (time.perf_counter() - self.last_key_time < 1.5)
                        if not typing_pause:
                            pulse = bass_engine.process(mono, decay=self.rgb_decay.get()) * self.rgb_sensitivity.get()
                            br, bg, bb = self.selected_rgb
                            self.rgb_manager.sync((br/255)*pulse, (bg/255)*pulse, (bb/255)*pulse)
                        else:
                            self.rgb_manager.stop()
                    
                    last_viz_time = now
            except queue.Empty:
                continue

    def _on_stream_error(self, err):
        messagebox.showerror("Stream Error", err)
        self._on_stream_stopped()

    def _on_stream_stopped(self):
        self.is_streaming = False
        self.stream_btn.configure(text="START STREAMING", fg_color=COLOR_ACCENT, text_color=COLOR_WHITE)
        self.status_pill.configure(text="STOPPED", text_color=COLOR_MUTED)
        self.status_dot.configure(text_color=COLOR_MUTED)
        self.level_queue.put([0.0]*64)
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
            # Interpolate 64 FFT bins to current column count
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
        self.after(30, self._update_loop)

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
                # Using a rounded rectangle for a more modern dot look
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
