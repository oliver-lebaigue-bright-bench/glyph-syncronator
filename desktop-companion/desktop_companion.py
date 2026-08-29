import socket
import pyaudiowpatch as pyaudio
import numpy as np
import time
import argparse
import sys
import threading
import queue
import asyncio
import ctypes
from ctypes import wintypes
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext

try:
    from openrgb import OpenRGBClient
    from openrgb.utils import RGBColor
    OPENRGB_AVAILABLE = True
except ImportError:
    OPENRGB_AVAILABLE = False

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

UDP_PORT = 12347
DISCOVERY_PORT = 12348
BLE_SERVICE_UUID = "7d9c63c0-37b1-4122-861f-36655c687e46"
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 48000

BG_MAIN = "#121212"
BG_CARD = "#1E1E1E"
BG_ENTRY = "#2A2A2A"
FG_TEXT = "#F5F5F5"
FG_MUTED = "#9E9E9E"
COLOR_ACCENT = "#00B0FF"
COLOR_SUCCESS = "#00E676"
COLOR_DANGER = "#FF5252"

# 7 логарифмических диапазонов спектра NP4A (Гц)
NP4A_RANGES = [
    (60, 120),       # Саб-бас
    (120, 240),      # Бас
    (240, 480),      # Низкая середина
    (480, 960),      # Середина
    (960, 1920),     # Высокая середина
    (1920, 3840),    # Презенс
    (3840, 15000)    # Воздух / тарелки
]

def get_broadcast_addresses():
    broadcasts = {'255.255.255.255'}
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = info[4][0]
            if ip != '127.0.0.1':
                parts = ip.split('.')
                broadcasts.add(".".join(parts[:-1] + ["255"]))
    except Exception:
        pass
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
        else:
            default_loopback_idx = default_speakers["index"]

        for loopback in p.get_loopback_device_info_generator():
            devices.append({
                "index": loopback["index"],
                "name": loopback["name"],
                "is_default": (loopback["index"] == default_loopback_idx),
                "rate": int(loopback["defaultSampleRate"]),
                "channels": int(loopback["maxInputChannels"])
            })
    except Exception:
        pass
    finally:
        p.terminate()
    return devices

WH_KEYBOARD_LL = 13
WM_KEYDOWN = 0x0100
WM_SYSKEYDOWN = 0x0104
HOOKPROC = ctypes.WINFUNCTYPE(ctypes.c_long, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM)

class KeyboardHookWatcher:
    def __init__(self, on_press_callback):
        self.on_press_callback = on_press_callback
        self.hook = None
        self.thread = None
        self.running = False
        self._c_proc = None

    def start(self):
        if self.running:
            return
        self.running = True
        self.thread = threading.Thread(target=self._hook_loop, daemon=True)
        self.thread.start()

    def _hook_loop(self):
        def _hook_proc(nCode, wParam, lParam):
            if nCode >= 0 and wParam in (WM_KEYDOWN, WM_SYSKEYDOWN):
                self.on_press_callback()
            return user32.CallNextHookEx(self.hook, nCode, wParam, lParam)

        self._c_proc = HOOKPROC(_hook_proc)
        self.hook = user32.SetWindowsHookExW(
            WH_KEYBOARD_LL,
            self._c_proc,
            kernel32.GetModuleHandleW(None),
            0
        )

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
            user32.PostThreadMessageW(self.thread.ident, 0x0012, 0, 0)

class OpenRGBManager:
    def __init__(self, logger=None):
        self.logger = logger
        self.client = None
        self.connected = False
        self.running = False
        self.is_paused = False
        self.last_key_time = 0.0

        self.raw_spectrum = [0.0] * 128
        self.smooth_spectrum = np.zeros(128, dtype=np.float32)

        self.hook_watcher = KeyboardHookWatcher(self._on_key_event)
        self.worker_thread = None

    def connect(self):
        if not OPENRGB_AVAILABLE:
            return False
        try:
            self.client = OpenRGBClient()
            self.connected = True
            
            for dev in self.client.devices:
                for mode in dev.modes:
                    if mode.name.lower() in ("custom", "direct"):
                        try:
                            dev.set_mode(mode)
                        except Exception:
                            pass
                        break
                        
            if self.logger:
                self.logger(f"OpenRGB Connected: {len(self.client.devices)} device(s) active.")
            return True
        except Exception as e:
            self.connected = False
            if self.logger:
                self.logger(f"OpenRGB Error: {e}")
            return False

    def _on_key_event(self):
        if not self.running:
            return
        self.last_key_time = time.perf_counter()
        if not self.is_paused:
            self.is_paused = True
            if self.logger:
                self.logger("Key pressed: Pausing RGB stream.")

    def start(self):
        if not self.connected or self.running:
            return
        self.running = True
        self.is_paused = False
        self.last_key_time = 0.0
        self.raw_spectrum = [0.0] * 128
        self.smooth_spectrum = np.zeros(128, dtype=np.float32)

        self.hook_watcher.start()
        self.worker_thread = threading.Thread(target=self._render_loop, daemon=True)
        self.worker_thread.start()

    def stop(self):
        self.running = False
        self.hook_watcher.stop()
        if self.connected:
            color_black = RGBColor(0, 0, 0)
            for dev in self.client.devices:
                try:
                    dev.set_color(color_black)
                except Exception:
                    pass

    def update_spectrum(self, norm_val):
        if not self.connected or not self.running:
            return
        self.raw_spectrum = norm_val

    def _render_loop(self):
        color_black = RGBColor(0, 0, 0)
        smooth_f_spark = 0.0
        cleared_on_pause = False

        while self.running:
            start_frame = time.perf_counter()
            now = time.perf_counter()

            if self.is_paused:
                if now - self.last_key_time < 3.0:
                    if not cleared_on_pause:
                        for dev in self.client.devices:
                            try:
                                dev.set_color(color_black)
                            except Exception:
                                pass
                        cleared_on_pause = True
                    time.sleep(0.025)
                    continue
                else:
                    self.is_paused = False
                    cleared_on_pause = False
                    if self.logger:
                        self.logger("Resuming RGB stream.")

            target = np.array(self.raw_spectrum, dtype=np.float32)
            if len(target) == 128:
                self.smooth_spectrum = np.where(
                    target > self.smooth_spectrum,
                    self.smooth_spectrum + (target - self.smooth_spectrum) * 0.70,
                    self.smooth_spectrum * 0.85
                )

            high_energy = float(np.mean(self.smooth_spectrum[85:120]))
            if high_energy > smooth_f_spark:
                smooth_f_spark = high_energy
            else:
                smooth_f_spark *= 0.80

            freqs = np.geomspace(30, 16000, 128)
            np4a_band_levels = []
            for low_f, high_f in NP4A_RANGES:
                mask = (freqs >= low_f) & (freqs <= high_f)
                if np.any(mask):
                    np4a_band_levels.append(float(np.mean(self.smooth_spectrum[mask])))
                else:
                    np4a_band_levels.append(0.0)

            for dev in self.client.devices:
                num_leds = len(dev.leds)
                if num_leds == 0:
                    continue

                f_row_count = min(16, int(num_leds * 0.18))
                main_count = num_leds - f_row_count

                colors = []

                # F-ряд
                f_val = int(np.clip(smooth_f_spark * 255, 0, 255))
                colors.extend([RGBColor(f_val, f_val, f_val)] * f_row_count)

                # Основная сетка NP4A
                if main_count > 0:
                    band_indices = np.linspace(0, len(np4a_band_levels) - 1, main_count)
                    interpolated_vals = np.interp(band_indices, np.arange(len(np4a_band_levels)), np4a_band_levels)
                    for v in interpolated_vals:
                        byte_v = int(np.clip(v * 255, 0, 255))
                        colors.append(RGBColor(byte_v, byte_v, byte_v))

                try:
                    dev.set_colors(colors)
                except Exception:
                    pass

            elapsed = time.perf_counter() - start_frame
            sleep_time = max(0.0, 0.033 - elapsed) # Стабильные 30 FPS без забивания сокета
            if sleep_time > 0:
                time.sleep(sleep_time)

def discover_phone_cli():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(1.0)
    discovery_msg = b"GLYPHIX_DISCOVERY_REQUEST"
    broadcast_addrs = get_broadcast_addresses()

    start_time = time.time()
    while time.time() - start_time < 30:
        try:
            for addr in broadcast_addrs:
                sock.sendto(discovery_msg, (addr, DISCOVERY_PORT))
            data, addr = sock.recvfrom(1024)
            if data == b"GLYPHIX_DISCOVERY_RESPONSE":
                return addr[0]
        except socket.timeout:
            continue
        except Exception:
            break
    return None

def discover_phone_bt():
    try:
        from bleak import BleakScanner
    except Exception:
        return []

    async def scan():
        devices = []
        def detection_callback(device, advertisement_data):
            uuids = [s.lower() for s in advertisement_data.service_uuids]
            sd_uuids = [s.lower() for s in advertisement_data.service_data.keys()]
            target = BLE_SERVICE_UUID.lower()
            is_match = (target in uuids or target in sd_uuids)
            for i, (addr, name, _) in enumerate(devices):
                if addr == device.address:
                    if name == "Unknown" and device.name:
                        devices[i] = (device.address, device.name, is_match)
                    return
            devices.append((device.address, device.name or "Unknown", is_match))

        scanner = BleakScanner(detection_callback)
        await scanner.start()
        for _ in range(15):
            await asyncio.sleep(1.0)
        await scanner.stop()
        return devices

    try:
        return asyncio.run(scan())
    except Exception:
        return []

def discover_phone_bt_cli():
    devices = discover_phone_bt()
    if not devices:
        return None
    for addr, _, is_match in devices:
        if is_match:
            return addr
    return None

class CompanionGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Glyphix Desktop Companion")
        self.root.geometry("640x590")
        self.root.configure(bg=BG_MAIN)
        self.root.resizable(False, False)

        self.is_streaming = False
        self.is_discovering = False
        self.stop_stream_event = threading.Event()
        self.stop_discovery_event = threading.Event()
        self.stream_thread = None
        self.discovery_thread = None

        self.conn_type = tk.StringVar(value="UDP")
        self.enable_openrgb = tk.BooleanVar(value=True)

        self.log_queue = queue.Queue()
        self.level_queue = queue.Queue()

        self.wasapi_devices = get_wasapi_devices()
        self.spectrum_points = [0.0] * 128

        self.rgb_controller = OpenRGBManager(logger=self.log)

        self.setup_styles()
        self.build_ui()
        self.refresh_device_list()

        threading.Thread(target=self.rgb_controller.connect, daemon=True).start()
        self.update_gui_loop()

    def setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure(".", background=BG_MAIN, foreground=FG_TEXT)
        style.configure("TFrame", background=BG_MAIN)
        style.configure("Card.TFrame", background=BG_CARD, relief="flat")
        style.configure("TLabel", background=BG_MAIN, foreground=FG_TEXT, font=("Segoe UI", 10))
        style.configure("Title.TLabel", background=BG_CARD, foreground=COLOR_ACCENT, font=("Segoe UI Semibold", 16))
        style.configure("Muted.TLabel", background=BG_CARD, foreground=FG_MUTED, font=("Segoe UI", 9))
        style.configure("Status.TLabel", background=BG_CARD, foreground=FG_TEXT, font=("Segoe UI Bold", 11))
        style.configure("TButton", font=("Segoe UI Semibold", 10), borderwidth=0, focuscolor="none")
        style.map("TButton",
                  background=[("active", COLOR_ACCENT), ("!disabled", BG_ENTRY)],
                  foreground=[("active", BG_MAIN), ("!disabled", FG_TEXT)])
        style.configure("TCombobox", fieldbackground=BG_ENTRY, background=BG_CARD, foreground=FG_TEXT, borderwidth=0)
        style.map("TCombobox", fieldbackground=[("readonly", BG_ENTRY)])

    def build_ui(self):
        header_frame = ttk.Frame(self.root, style="Card.TFrame")
        header_frame.pack(fill="x", padx=15, pady=(15, 10))

        title_label = ttk.Label(header_frame, text="GLYPHIX COMPANION", style="Title.TLabel")
        title_label.pack(side="left", padx=15, pady=15)

        self.status_badge = ttk.Label(header_frame, text="IDLE", style="Status.TLabel", foreground=FG_MUTED)
        self.status_badge.pack(side="right", padx=20, pady=15)

        card_frame = ttk.Frame(self.root, style="Card.TFrame")
        card_frame.pack(fill="both", expand=True, padx=15, pady=5)

        device_row = ttk.Frame(card_frame, style="Card.TFrame")
        device_row.pack(fill="x", padx=15, pady=(15, 5))

        lbl_device = ttk.Label(device_row, text="Audio Source:", style="Muted.TLabel")
        lbl_device.pack(side="left", padx=5)

        self.device_combo = ttk.Combobox(device_row, state="readonly", width=55, style="TCombobox")
        self.device_combo.pack(side="left", padx=10, fill="x", expand=True)

        conn_row = ttk.Frame(card_frame, style="Card.TFrame")
        conn_row.pack(fill="x", padx=15, pady=5)

        ttk.Label(conn_row, text="Connection:", style="Muted.TLabel").pack(side="left", padx=5)

        rb_udp = tk.Radiobutton(conn_row, text="Network (UDP)", variable=self.conn_type, value="UDP",
                                bg=BG_CARD, fg=FG_TEXT, selectcolor=BG_MAIN, activebackground=BG_CARD,
                                activeforeground=COLOR_ACCENT, font=("Segoe UI", 9))
        rb_udp.pack(side="left", padx=5)

        rb_bt = tk.Radiobutton(conn_row, text="Bluetooth (RFCOMM)", variable=self.conn_type, value="BT",
                               bg=BG_CARD, fg=FG_TEXT, selectcolor=BG_MAIN, activebackground=BG_CARD,
                               activeforeground=COLOR_ACCENT, font=("Segoe UI", 9))
        rb_bt.pack(side="left", padx=5)

        cb_openrgb = tk.Checkbutton(conn_row, text="Sync OpenRGB", variable=self.enable_openrgb,
                                    bg=BG_CARD, fg=COLOR_SUCCESS if OPENRGB_AVAILABLE else FG_MUTED,
                                    selectcolor=BG_MAIN, activebackground=BG_CARD,
                                    activeforeground=COLOR_SUCCESS, font=("Segoe UI Semibold", 9))
        cb_openrgb.pack(side="right", padx=10)

        ip_row = ttk.Frame(card_frame, style="Card.TFrame")
        ip_row.pack(fill="x", padx=15, pady=10)

        self.lbl_addr = ttk.Label(ip_row, text="Phone IP / MAC:", style="Muted.TLabel")
        self.lbl_addr.pack(side="left", padx=5)

        self.ip_entry = tk.Entry(ip_row, bg=BG_ENTRY, fg=FG_TEXT, insertbackground=FG_TEXT,
                                 relief="flat", font=("Segoe UI", 11), width=18)
        self.ip_entry.insert(0, "127.0.0.1")
        self.ip_entry.pack(side="left", padx=10)

        self.discover_btn = ttk.Button(ip_row, text="Auto-Discover", command=self.toggle_discovery)
        self.discover_btn.pack(side="left", padx=5)

        visualizer_row = ttk.Frame(card_frame, style="Card.TFrame")
        visualizer_row.pack(fill="x", padx=15, pady=10)

        lbl_level = ttk.Label(visualizer_row, text="Live Spectrum:", style="Muted.TLabel")
        lbl_level.pack(anchor="w", padx=5, pady=(0, 3))

        self.meter_canvas = tk.Canvas(visualizer_row, height=85, bg=BG_ENTRY, highlightthickness=0)
        self.meter_canvas.pack(fill="x", padx=5)
        self.draw_visualizer(self.spectrum_points)

        self.stream_btn = tk.Button(card_frame, text="Start Streaming", font=("Segoe UI Bold", 12),
                                    bg=COLOR_ACCENT, fg=BG_MAIN, activebackground=COLOR_SUCCESS,
                                    activeforeground=BG_MAIN, relief="flat", command=self.toggle_streaming)
        self.stream_btn.pack(fill="x", padx=20, pady=(10, 10))

        log_frame = ttk.Frame(self.root)
        log_frame.pack(fill="both", expand=True, padx=15, pady=(5, 15))

        self.log_text = scrolledtext.ScrolledText(log_frame, bg=BG_CARD, fg=FG_TEXT, insertbackground=FG_TEXT,
                                                  state="disabled", font=("Consolas", 9), relief="flat", height=5)
        self.log_text.pack(fill="both", expand=True)

        self.log("Glyphix Companion Ready.")

    def log(self, message):
        self.log_queue.put(message)

    def draw_visualizer(self, spectrum_points):
        self.meter_canvas.delete("all")
        width = self.meter_canvas.winfo_width()
        if width <= 1:
            width = 570
        height = 85

        num_points = len(spectrum_points)
        if num_points < 2:
            return

        coords = [0, height]
        line_coords = []

        for i in range(num_points):
            frac = i / (num_points - 1)
            x = frac * width
            val = spectrum_points[i]
            y = height - (val * (height - 10)) - 5
            coords.extend([x, y])
            line_coords.extend([x, y])

        coords.extend([width, height])
        self.meter_canvas.create_polygon(coords, fill="#0a324a", outline="")
        self.meter_canvas.create_line(line_coords, fill=COLOR_ACCENT, width=3, smooth=True)

    def refresh_device_list(self):
        vals = []
        default_index = 0
        for i, dev in enumerate(self.wasapi_devices):
            display_name = dev["name"]
            if dev["is_default"]:
                display_name += " (Default)"
                default_index = i
            vals.append(display_name)

        self.device_combo["values"] = vals
        if vals:
            self.device_combo.current(default_index)

    def toggle_discovery(self):
        if self.is_discovering:
            self.stop_discovery_event.set()
        else:
            self.is_discovering = True
            self.stop_discovery_event.clear()
            self.discover_btn.configure(text="Cancel Search")
            self.status_badge.configure(text="SEARCHING", foreground=COLOR_ACCENT)
            self.discovery_thread = threading.Thread(target=self.discovery_worker, daemon=True)
            self.discovery_thread.start()

    def discovery_worker(self):
        if self.conn_type.get() == "BT":
            devices = discover_phone_bt()
            self.root.after(0, self.on_discovery_complete, devices)
            return

        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.settimeout(0.5)

        discovery_msg = b"GLYPHIX_DISCOVERY_REQUEST"
        broadcast_addrs = get_broadcast_addresses()
        found_ip = None
        start_time = time.time()

        while time.time() - start_time < 15 and not self.stop_discovery_event.is_set():
            try:
                for addr in broadcast_addrs:
                    sock.sendto(discovery_msg, (addr, DISCOVERY_PORT))
                data, addr = sock.recvfrom(1024)
                if data == b"GLYPHIX_DISCOVERY_RESPONSE":
                    found_ip = addr[0]
                    break
            except socket.timeout:
                continue
            except Exception:
                break

        sock.close()
        self.root.after(0, self.on_discovery_complete, found_ip)

    def on_discovery_complete(self, result):
        self.is_discovering = False
        self.discover_btn.configure(text="Auto-Discover")
        if self.conn_type.get() == "BT":
            if result:
                matches = [d for d in result if d[2]]
                if matches:
                    self.ip_entry.delete(0, tk.END)
                    self.ip_entry.insert(0, matches[0][0])
                    self.status_badge.configure(text="FOUND DEVICE", foreground=COLOR_SUCCESS)
        else:
            if result:
                self.ip_entry.delete(0, tk.END)
                self.ip_entry.insert(0, result)
                self.status_badge.configure(text="FOUND DEVICE", foreground=COLOR_SUCCESS)
            else:
                self.status_badge.configure(text="IDLE", foreground=FG_MUTED)

    def toggle_streaming(self):
        if self.is_streaming:
            self.log("Stopping stream...")
            self.stop_stream_event.set()
            self.rgb_controller.stop()
        else:
            addr = self.ip_entry.get().strip() or "127.0.0.1"
            device_idx = self.device_combo.current()
            if device_idx < 0 or device_idx >= len(self.wasapi_devices):
                messagebox.showerror("Error", "Please select a valid audio device.")
                return

            selected_device = self.wasapi_devices[device_idx]

            self.is_streaming = True
            self.stop_stream_event.clear()
            self.stream_btn.configure(text="Stop Streaming", bg=COLOR_DANGER)
            self.status_badge.configure(text="STREAMING", foreground=COLOR_SUCCESS)

            if self.enable_openrgb.get():
                self.rgb_controller.start()

            self.stream_thread = threading.Thread(
                target=self.stream_worker,
                args=(addr, selected_device),
                daemon=True
            )
            self.stream_thread.start()

    def stream_worker(self, addr, device):
        self.log(f"Opening Stream: {device['name']}")
        p = pyaudio.PyAudio()

        native_rate = device["rate"]
        channels = device["channels"]

        try:
            stream = p.open(format=FORMAT, channels=channels, rate=TARGET_RATE,
                            input=True, input_device_index=device["index"],
                            frames_per_buffer=CHUNK)
            actual_rate = TARGET_RATE
        except Exception:
            try:
                stream = p.open(format=FORMAT, channels=channels, rate=native_rate,
                                input=True, input_device_index=device["index"],
                                frames_per_buffer=CHUNK)
                actual_rate = native_rate
            except Exception as e:
                self.root.after(0, self.on_stream_stopped, f"WASAPI Error: {e}")
                p.terminate()
                return

        conn_type = self.conn_type.get()
        if conn_type == "BT":
            sock = None
            for port in range(1, 21):
                try:
                    temp_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
                    temp_sock.settimeout(2.0)
                    temp_sock.connect((addr, port))
                    sock = temp_sock
                    break
                except Exception:
                    continue
            if not sock:
                self.root.after(0, self.on_stream_stopped, "BT Failed.")
                stream.close()
                p.terminate()
                return
            sock.settimeout(None)
        else:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 256 * 1024)
            except Exception:
                pass

        interp_indices = None
        if actual_rate != TARGET_RATE:
            interp_indices = np.linspace(0, CHUNK - 1, int(CHUNK * TARGET_RATE / actual_rate))

        packet_count = 0
        last_meter_time = 0

        try:
            while not self.stop_stream_event.is_set():
                raw_data = stream.read(CHUNK, exception_on_overflow=False)
                samples = np.frombuffer(raw_data, dtype=np.int16)

                if channels > 1:
                    samples = samples.reshape(-1, channels).mean(axis=1).astype(np.int16)

                current_time = time.time()
                if current_time - last_meter_time > 0.020:
                    samples_float = samples.astype(np.float32) / 32768.0
                    fft_mag = np.abs(np.fft.rfft(samples_float)) / (len(samples_float) / 2.0)

                    freqs = np.geomspace(30, 16000, 128)
                    bin_width = actual_rate / len(samples_float)
                    bin_indices = np.clip(freqs / bin_width, 0, len(fft_mag) - 1)

                    points = np.interp(bin_indices, np.arange(len(fft_mag)), fft_mag)
                    boost = 1.0 + (bin_indices / len(fft_mag)) * 3.5
                    val = points * boost

                    db_val = 20 * np.log10(val + 1e-5)
                    norm_val = np.clip((db_val + 50.0) / 50.0, 0.0, 1.0)

                    self.level_queue.put(list(norm_val))

                    if self.enable_openrgb.get():
                        self.rgb_controller.update_spectrum(list(norm_val))

                    last_meter_time = current_time

                if interp_indices is not None:
                    data_to_send = np.interp(interp_indices, np.arange(len(samples)), samples).astype(np.int16).tobytes()
                else:
                    data_to_send = samples.tobytes()

                if conn_type == "BT":
                    sock.sendall(data_to_send)
                else:
                    sock.sendto(data_to_send, (addr, UDP_PORT))
                packet_count += 1

        except Exception as e:
            self.log(f"Stream Error: {e}")
        finally:
            stream.stop_stream()
            stream.close()
            p.terminate()
            sock.close()
            self.root.after(0, self.on_stream_stopped, f"Stream finished. Packets: {packet_count}")

    def on_stream_stopped(self, message):
        self.is_streaming = False
        self.stream_btn.configure(text="Start Streaming", bg=COLOR_ACCENT)
        self.status_badge.configure(text="STOPPED", foreground=COLOR_DANGER)
        self.log(message)
        self.level_queue.put([0.0] * 128)
        self.rgb_controller.stop()

    def update_gui_loop(self):
        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            self.log_text.configure(state="normal")
            self.log_text.insert(tk.END, f"[{time.strftime('%H:%M:%S')}] {msg}\n")
            self.log_text.see(tk.END)
            self.log_text.configure(state="disabled")

        last_points = None
        while not self.level_queue.empty():
            last_points = self.level_queue.get_nowait()

        decay_rate = 0.82
        for i in range(128):
            self.spectrum_points[i] *= decay_rate
            if self.spectrum_points[i] < 0.01:
                self.spectrum_points[i] = 0.0

        if last_points is not None and isinstance(last_points, list):
            for i in range(min(128, len(last_points))):
                self.spectrum_points[i] = max(self.spectrum_points[i], last_points[i])

        self.draw_visualizer(self.spectrum_points)
        self.root.after(33, self.update_gui_loop)

def main():
    root = tk.Tk()
    app = CompanionGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()