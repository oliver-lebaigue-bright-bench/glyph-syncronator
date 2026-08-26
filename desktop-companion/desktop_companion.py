import socket
import pyaudiowpatch as pyaudio
import numpy as np
import time
import argparse
import sys
import threading
import queue
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext

# Config
UDP_PORT = 12347
DISCOVERY_PORT = 12348
CHUNK = 1024
FORMAT = pyaudio.paInt16
TARGET_RATE = 48000

# Theme Colors
BG_MAIN = "#121212"
BG_CARD = "#1E1E1E"
BG_ENTRY = "#2A2A2A"
FG_TEXT = "#F5F5F5"
FG_MUTED = "#9E9E9E"
COLOR_ACCENT = "#00B0FF"   # Bright Blue (Cyan-ish)
COLOR_SUCCESS = "#00E676"  # Neon Green
COLOR_WARNING = "#FFD700"  # Gold
COLOR_DANGER = "#FF5252"   # Light Red

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
    """Returns a list of available WASAPI loopback devices."""
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

# ----------------- CLI IMPLEMENTATION -----------------
def discover_phone_cli():
    print("Searching for Glyphix app on the network...")
    print("TIP: Make sure you've pressed 'START' in the app and 'Desktop Companion' is selected.")
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(1.0)
    
    discovery_msg = b"GLYPHIX_DISCOVERY_REQUEST"
    broadcast_addrs = get_broadcast_addresses()
    print(f"Broadcasting to: {broadcast_addrs}")
    
    start_time = time.time()
    while time.time() - start_time < 30:
        try:
            for addr in broadcast_addrs:
                sock.sendto(discovery_msg, (addr, DISCOVERY_PORT))
            
            data, addr = sock.recvfrom(1024)
            if data == b"GLYPHIX_DISCOVERY_RESPONSE":
                print(f"\nFound Glyphix at {addr[0]}")
                return addr[0]
            elif data == b"GLYPHIX_DISCOVERY_REQUEST":
                continue
        except socket.timeout:
            print(".", end="", flush=True)
            continue
        except Exception as e:
            print(f"\nDiscovery error: {e}")
            break
    
    print("\nDiscovery timed out.")
    return None

def run_companion_cli(ip):
    p = pyaudio.PyAudio()
    try:
        wasapi_info = p.get_host_api_info_by_type(pyaudio.paWASAPI)
    except OSError:
        print("WASAPI not found. This script requires Windows.")
        return

    default_speakers = p.get_device_info_by_index(wasapi_info["defaultOutputDevice"])
    if not default_speakers["isLoopbackDevice"]:
        for loopback in p.get_loopback_device_info_generator():
            if default_speakers["name"] in loopback["name"]:
                default_speakers = loopback
                break
        else:
            print("Default loopback device not found.")
            return

    native_rate = int(default_speakers["defaultSampleRate"])
    channels = int(default_speakers["maxInputChannels"])
    
    print(f"\nCapturing from: {default_speakers['name']}")
    print(f"Native Rate: {native_rate}Hz -> Streaming at: {TARGET_RATE}Hz")
    print(f"Channels: {channels} (Downmixing to Mono)")
    print(f"Streaming to: {ip}:{UDP_PORT}")

    try:
        stream = p.open(format=FORMAT,
                        channels=channels,
                        rate=TARGET_RATE,
                        input=True,
                        input_device_index=default_speakers["index"],
                        frames_per_buffer=CHUNK)
        actual_rate = TARGET_RATE
    except Exception:
        try:
            stream = p.open(format=FORMAT,
                            channels=channels,
                            rate=native_rate,
                            input=True,
                            input_device_index=default_speakers["index"],
                            frames_per_buffer=CHUNK)
            actual_rate = native_rate
        except Exception as e:
            print(f"\nFailed to open audio stream: {e}")
            return

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 256 * 1024)
    except:
        pass

    interp_indices = None
    if actual_rate != TARGET_RATE:
        num_samples = CHUNK
        target_num_samples = int(num_samples * TARGET_RATE / actual_rate)
        interp_indices = np.linspace(0, num_samples - 1, target_num_samples)

    try:
        last_debug_time = time.time()
        packet_count = 0
        while True:
            raw_data = stream.read(CHUNK, exception_on_overflow=False)
            samples = np.frombuffer(raw_data, dtype=np.int16)
            
            if channels > 1:
                samples = samples.reshape(-1, channels).mean(axis=1).astype(np.int16)
            
            if interp_indices is not None:
                resampled_samples = np.interp(interp_indices, np.arange(len(samples)), samples).astype(np.int16)
                data_to_send = resampled_samples.tobytes()
            else:
                data_to_send = samples.tobytes()
                
            sock.sendto(data_to_send, (ip, UDP_PORT))
            packet_count += 1

            if time.time() - last_debug_time > 2.0:
                peak = np.abs(samples).max()
                print(f"Streaming... [Packets sent: {packet_count}] [Peak amplitude: {peak}]", end="\r")
                last_debug_time = time.time()
    except KeyboardInterrupt:
        print("\nStopping...")
    except Exception as e:
        print(f"\nStream error: {e}")
    finally:
        stream.stop_stream()
        stream.close()
        p.terminate()


# ----------------- GUI IMPLEMENTATION -----------------
class CompanionGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Glyphix Desktop Companion")
        self.root.geometry("640x560")
        self.root.configure(bg=BG_MAIN)
        self.root.resizable(False, False)

        # Threading Flags & State
        self.is_streaming = False
        self.is_discovering = False
        self.stop_stream_event = threading.Event()
        self.stop_discovery_event = threading.Event()
        self.stream_thread = None
        self.discovery_thread = None

        # Message queues for thread safety
        self.log_queue = queue.Queue()
        self.level_queue = queue.Queue()

        self.wasapi_devices = get_wasapi_devices()
        self.spectrum_points = [0.0] * 128  # 128 resolution points for the visualizer curve

        self.setup_styles()
        self.build_ui()
        self.refresh_device_list()

        # Start periodic GUI updates
        self.update_gui_loop()

    def setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")
        
        # Configure frames and backgrounds
        style.configure(".", background=BG_MAIN, foreground=FG_TEXT)
        style.configure("TFrame", background=BG_MAIN)
        style.configure("Card.TFrame", background=BG_CARD, relief="flat")
        
        # Configure Labels
        style.configure("TLabel", background=BG_MAIN, foreground=FG_TEXT, font=("Segoe UI", 10))
        style.configure("Title.TLabel", background=BG_CARD, foreground=COLOR_ACCENT, font=("Segoe UI Semibold", 16))
        style.configure("Muted.TLabel", background=BG_CARD, foreground=FG_MUTED, font=("Segoe UI", 9))
        style.configure("Status.TLabel", background=BG_CARD, foreground=FG_TEXT, font=("Segoe UI Bold", 11))
        
        # Configure Buttons
        style.configure("TButton", font=("Segoe UI Semibold", 10), borderwidth=0, focuscolor="none")
        style.map("TButton",
                  background=[("active", COLOR_ACCENT), ("!disabled", BG_ENTRY)],
                  foreground=[("active", BG_MAIN), ("!disabled", FG_TEXT)])
        
        # Combo box styling
        style.configure("TCombobox", fieldbackground=BG_ENTRY, background=BG_CARD, foreground=FG_TEXT, borderwidth=0)
        style.map("TCombobox", fieldbackground=[("readonly", BG_ENTRY)])

    def build_ui(self):
        # Header Container
        header_frame = ttk.Frame(self.root, style="Card.TFrame")
        header_frame.pack(fill="x", padx=15, pady=(15, 10))
        
        title_label = ttk.Label(header_frame, text="GLYPHIX COMPANION", style="Title.TLabel")
        title_label.pack(side="left", padx=15, pady=15)
        
        self.status_badge = ttk.Label(header_frame, text="IDLE", style="Status.TLabel", foreground=FG_MUTED)
        self.status_badge.pack(side="right", padx=20, pady=15)

        # Main Card (Controls and Configuration)
        card_frame = ttk.Frame(self.root, style="Card.TFrame")
        card_frame.pack(fill="both", expand=True, padx=15, pady=5)

        # 1. Device Selection Row
        device_row = ttk.Frame(card_frame, style="Card.TFrame")
        device_row.pack(fill="x", padx=15, pady=(15, 5))
        
        lbl_device = ttk.Label(device_row, text="Audio Source:", style="Muted.TLabel")
        lbl_device.pack(side="left", padx=5)
        
        self.device_combo = ttk.Combobox(device_row, state="readonly", width=55, style="TCombobox")
        self.device_combo.pack(side="left", padx=10, fill="x", expand=True)
        
        # 2. IP Address Row
        ip_row = ttk.Frame(card_frame, style="Card.TFrame")
        ip_row.pack(fill="x", padx=15, pady=10)
        
        lbl_ip = ttk.Label(ip_row, text="Phone IP Address:", style="Muted.TLabel")
        lbl_ip.pack(side="left", padx=5)
        
        self.ip_entry = tk.Entry(ip_row, bg=BG_ENTRY, fg=FG_TEXT, insertbackground=FG_TEXT, 
                                 relief="flat", font=("Segoe UI", 11), width=18)
        self.ip_entry.pack(side="left", padx=10)
        
        self.discover_btn = ttk.Button(ip_row, text="Auto-Discover", command=self.toggle_discovery)
        self.discover_btn.pack(side="left", padx=5)

        # 3. Frequency Spectrum Area-fill visualizer
        visualizer_row = ttk.Frame(card_frame, style="Card.TFrame")
        visualizer_row.pack(fill="x", padx=15, pady=10)
        
        lbl_level = ttk.Label(visualizer_row, text="Live Spectrum:", style="Muted.TLabel")
        lbl_level.pack(anchor="w", padx=5, pady=(0, 3))
        
        self.meter_canvas = tk.Canvas(visualizer_row, height=85, bg=BG_ENTRY, highlightthickness=0)
        self.meter_canvas.pack(fill="x", padx=5)
        self.draw_visualizer(self.spectrum_points)

        # 4. Action Stream Button
        self.stream_btn = tk.Button(card_frame, text="Start Streaming", font=("Segoe UI Bold", 12),
                                    bg=COLOR_ACCENT, fg=BG_MAIN, activebackground=COLOR_SUCCESS, 
                                    activeforeground=BG_MAIN, relief="flat", command=self.toggle_streaming)
        self.stream_btn.pack(fill="x", padx=20, pady=(10, 10))

        # Bottom Frame: Status Logs
        log_frame = ttk.Frame(self.root)
        log_frame.pack(fill="both", expand=True, padx=15, pady=(5, 15))
        
        self.log_text = scrolledtext.ScrolledText(log_frame, bg=BG_CARD, fg=FG_TEXT, insertbackground=FG_TEXT,
                                                  state="disabled", font=("Consolas", 9), relief="flat", height=5)
        self.log_text.pack(fill="both", expand=True)

        self.log("Glyphix GUI Companion Initialized.")
        if not self.wasapi_devices:
            self.log("[ERROR] No WASAPI Loopback devices detected. Please verify your Windows Sound Settings.")

    def log(self, message):
        self.log_queue.put(message)

    def draw_visualizer(self, spectrum_points):
        self.meter_canvas.delete("all")
        width = self.meter_canvas.winfo_width()
        if width <= 1:
            width = 570  # Fallback width
        height = 85
        
        num_points = len(spectrum_points)
        if num_points < 2:
            return
            
        # Draw the spectrum polygon area (gradient block simulation)
        coords = [0, height]
        line_coords = []
        
        for i in range(num_points):
            frac = i / (num_points - 1)
            x = frac * width
            val = spectrum_points[i]
            
            # Non-linear height scaling
            y = height - (val * (height - 10)) - 5
            
            coords.extend([x, y])
            line_coords.extend([x, y])
            
        coords.extend([width, height])
        
        # Area fill: deep theme-matching primary/accent glow
        self.meter_canvas.create_polygon(coords, fill="#0a324a", outline="")
        
        # Smooth line on top
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
            self.log("Starting network discovery...")
            
            self.discovery_thread = threading.Thread(target=self.discovery_worker, daemon=True)
            self.discovery_thread.start()

    def discovery_worker(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.settimeout(0.5)
        
        discovery_msg = b"GLYPHIX_DISCOVERY_REQUEST"
        broadcast_addrs = get_broadcast_addresses()
        self.log(f"Broadcasting discovery packet to {broadcast_addrs}")
        
        found_ip = None
        start_time = time.time()
        
        while time.time() - start_time < 15 and not self.stop_discovery_event.is_set():
            try:
                for addr in broadcast_addrs:
                    sock.sendto(discovery_msg, (addr, DISCOVERY_PORT))
                
                # Check for responses
                data, addr = sock.recvfrom(1024)
                if data == b"GLYPHIX_DISCOVERY_RESPONSE":
                    found_ip = addr[0]
                    break
            except socket.timeout:
                continue
            except Exception as e:
                self.log(f"[Discovery Error] {e}")
                break
        
        sock.close()
        
        # Notify the UI thread
        self.root.after(0, self.on_discovery_complete, found_ip)

    def on_discovery_complete(self, ip):
        self.is_discovering = False
        self.discover_btn.configure(text="Auto-Discover")
        
        if ip:
            self.ip_entry.delete(0, tk.END)
            self.ip_entry.insert(0, ip)
            self.log(f"Discovery Success: Found Glyphix app at {ip}")
            self.status_badge.configure(text="FOUND DEVICE", foreground=COLOR_SUCCESS)
        else:
            if self.stop_discovery_event.is_set():
                self.log("Discovery cancelled by user.")
            else:
                self.log("Discovery timed out. Please enter target IP address manually.")
            self.status_badge.configure(text="IDLE", foreground=FG_MUTED)

    def toggle_streaming(self):
        if self.is_streaming:
            self.log("Stopping stream...")
            self.stop_stream_event.set()
        else:
            ip = self.ip_entry.get().strip()
            if not ip:
                messagebox.showerror("Error", "Please discover or enter the Phone IP Address.")
                return

            device_idx = self.device_combo.current()
            if device_idx < 0 or device_idx >= len(self.wasapi_devices):
                messagebox.showerror("Error", "Please select a valid WASAPI Audio source.")
                return

            selected_device = self.wasapi_devices[device_idx]
            
            self.is_streaming = True
            self.stop_stream_event.clear()
            self.stream_btn.configure(text="Stop Streaming", bg=COLOR_DANGER)
            self.status_badge.configure(text="STREAMING", foreground=COLOR_SUCCESS)
            
            # Start streaming worker
            self.stream_thread = threading.Thread(
                target=self.stream_worker, 
                args=(ip, selected_device), 
                daemon=True
            )
            self.stream_thread.start()

    def stream_worker(self, ip, device):
        self.log(f"Opening Stream: {device['name']}")
        p = pyaudio.PyAudio()
        
        # Audio Initialization
        native_rate = device["rate"]
        channels = device["channels"]
        
        try:
            stream = p.open(format=FORMAT,
                            channels=channels,
                            rate=TARGET_RATE,
                            input=True,
                            input_device_index=device["index"],
                            frames_per_buffer=CHUNK)
            actual_rate = TARGET_RATE
        except Exception:
            try:
                stream = p.open(format=FORMAT,
                                channels=channels,
                                rate=native_rate,
                                input=True,
                                input_device_index=device["index"],
                                frames_per_buffer=CHUNK)
                actual_rate = native_rate
            except Exception as e:
                self.log(f"[Stream Error] Failed to open stream: {e}")
                self.root.after(0, self.on_stream_stopped, f"Initialization Failed: {e}")
                p.terminate()
                return

        self.log(f"Audio open: {actual_rate}Hz, {channels} channels.")
        
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 256 * 1024)
        except:
            pass

        interp_indices = None
        if actual_rate != TARGET_RATE:
            num_samples = CHUNK
            target_num_samples = int(num_samples * TARGET_RATE / actual_rate)
            interp_indices = np.linspace(0, num_samples - 1, target_num_samples)

        packet_count = 0
        last_meter_time = 0
        
        try:
            while not self.stop_stream_event.is_set():
                raw_data = stream.read(CHUNK, exception_on_overflow=False)
                samples = np.frombuffer(raw_data, dtype=np.int16)
                
                # Downmix to mono if needed
                if channels > 1:
                    samples = samples.reshape(-1, channels).mean(axis=1).astype(np.int16)
                
                # Frequency FFT analysis for the continuous spectrum visualizer (128 points)
                current_time = time.time()
                if current_time - last_meter_time > 0.05: # Send level data every 50ms
                    # Normalize samples to [-1.0, 1.0] range
                    samples_float = samples.astype(np.float32) / 32768.0
                    
                    # Compute FFT and normalize magnitude
                    fft_mag = np.abs(np.fft.rfft(samples_float)) / (len(samples_float) / 2.0)
                    num_points = 128
                    
                    # Logarithmic spacing from 30Hz to 16kHz
                    freqs = np.geomspace(30, 16000, num_points)
                    bin_width = actual_rate / len(samples_float)
                    bin_indices = freqs / bin_width
                    bin_indices = np.clip(bin_indices, 0, len(fft_mag) - 1)
                    
                    # Log-interpolate magnitude values to prevent duplicates
                    points = np.interp(bin_indices, np.arange(len(fft_mag)), fft_mag)
                    
                    # Apply treble emphasis boost (high frequency energies are naturally lower)
                    boost = 1.0 + (bin_indices / len(fft_mag)) * 3.5
                    val = points * boost
                    
                    # Convert to decibels (range -60dB to 0dB)
                    db_val = 20 * np.log10(val + 1e-5)
                    norm_val = np.clip((db_val + 60.0) / 60.0, 0.0, 1.0)
                    
                    self.level_queue.put(list(norm_val))
                    last_meter_time = current_time

                # Resample if target rate differs
                if interp_indices is not None:
                    resampled_samples = np.interp(interp_indices, np.arange(len(samples)), samples).astype(np.int16)
                    data_to_send = resampled_samples.tobytes()
                else:
                    data_to_send = samples.tobytes()
                    
                sock.sendto(data_to_send, (ip, UDP_PORT))
                packet_count += 1
                
        except Exception as e:
            self.log(f"[Stream Error] {e}")
        finally:
            stream.stop_stream()
            stream.close()
            p.terminate()
            sock.close()
            self.root.after(0, self.on_stream_stopped, f"Streaming finished. Total packets sent: {packet_count}")

    def on_stream_stopped(self, message):
        self.is_streaming = False
        self.stream_btn.configure(text="Start Streaming", bg=COLOR_ACCENT)
        self.status_badge.configure(text="STOPPED", foreground=COLOR_DANGER)
        self.log(message)
        self.level_queue.put([0.0] * 128)  # Clear the visualizer spectrum

    def update_gui_loop(self):
        # 1. Drain Logs
        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            self.log_text.configure(state="normal")
            self.log_text.insert(tk.END, f"[{time.strftime('%H:%M:%S')}] {msg}\n")
            self.log_text.see(tk.END)
            self.log_text.configure(state="disabled")

        # 2. Drain and Decay Visualizer
        last_points = None
        while not self.level_queue.empty():
            last_points = self.level_queue.get_nowait()
            
        # Smooth spectrum decay curve over time
        decay_rate = 0.82
        for i in range(128):
            self.spectrum_points[i] *= decay_rate
            if self.spectrum_points[i] < 0.01:
                self.spectrum_points[i] = 0.0

        # Update with new values
        if last_points is not None and isinstance(last_points, list):
            for i in range(min(128, len(last_points))):
                self.spectrum_points[i] = max(self.spectrum_points[i], last_points[i])

        self.draw_visualizer(self.spectrum_points)

        # Reschedule loop
        self.root.after(50, self.update_gui_loop)


def main():
    parser = argparse.ArgumentParser(description="Glyphix Desktop Companion")
    parser.add_argument("--ip", help="Force a specific IP address (CLI mode)")
    parser.add_argument("--cli", action="store_true", help="Launch in CLI mode")
    args = parser.parse_args()

    # Determine if CLI mode is requested or needed
    if args.cli or args.ip:
        print("Glyphix Desktop Companion v1.2 (CLI)")
        target_ip = args.ip
        if not target_ip:
            target_ip = discover_phone_cli()
        
        if target_ip:
            run_companion_cli(target_ip)
        else:
            print("\nCould not find your phone automatically.")
            print("Try running with --ip YOUR_IP")
            sys.exit(1)
    else:
        # GUI mode is default
        try:
            root = tk.Tk()
            app = CompanionGUI(root)
            root.mainloop()
        except Exception as e:
            print(f"Error launching GUI: {e}")
            print("Falling back to CLI mode...")
            target_ip = discover_phone_cli()
            if target_ip:
                run_companion_cli(target_ip)
            else:
                sys.exit(1)

if __name__ == "__main__":
    main()
