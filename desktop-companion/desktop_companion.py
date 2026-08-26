import socket
import pyaudiowpatch as pyaudio
import numpy as np
import time
import argparse
import sys

# Config
UDP_PORT = 12347
DISCOVERY_PORT = 12348
CHUNK = 512
FORMAT = pyaudio.paInt16
TARGET_RATE = 44100

def get_broadcast_addresses():
    broadcasts = {'255.255.255.255'}
    try:
        hostname = socket.gethostname()
        # Get all IPv4 addresses for the current host
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = info[4][0]
            if ip != '127.0.0.1':
                parts = ip.split('.')
                # Crude broadcast address by changing last octet to 255
                broadcasts.add(".".join(parts[:-1] + ["255"]))
    except Exception:
        pass
    return [b for b in broadcasts]

def discover_phone():
    print("Searching for Glyphix app on the network...")
    print("TIP: Make sure you've pressed 'START' in the app and 'Desktop Companion' is selected.")
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(1.0)
    
    discovery_msg = b"GLYPHIX_DISCOVERY_REQUEST"
    broadcast_addrs = get_broadcast_addresses()
    print(f"Broadcasting to: {broadcast_addrs}")
    
    start_time = time.time()
    while time.time() - start_time < 30: # Search for 30 seconds
        try:
            # Broadcast the discovery request to all interfaces
            for addr in broadcast_addrs:
                sock.sendto(discovery_msg, (addr, DISCOVERY_PORT))
            
            # Wait for response
            data, addr = sock.recvfrom(1024)
            if data == b"GLYPHIX_DISCOVERY_RESPONSE":
                print(f"\nFound Glyphix at {addr[0]}")
                return addr[0]
            elif data == b"GLYPHIX_DISCOVERY_REQUEST":
                # Ignore our own broadcast
                continue
        except socket.timeout:
            print(".", end="", flush=True)
            continue
        except Exception as e:
            print(f"\nDiscovery error: {e}")
            break
    
    print("\nDiscovery timed out.")
    return None

def run_companion(ip):
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
                        rate=native_rate,
                        input=True,
                        input_device_index=default_speakers["index"],
                        frames_per_buffer=CHUNK)
    except Exception as e:
        print(f"\nFailed to open audio stream: {e}")
        print("TIP: Try changing your Windows 'Default Format' in Sound Settings to 44100Hz or 48000Hz.")
        return

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    # Increase send buffer size to 128KB
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 128 * 1024)
    except:
        pass

    try:
        last_debug_time = time.time()
        packet_count = 0
        while True:
            raw_data = stream.read(CHUNK, exception_on_overflow=False)
            samples = np.frombuffer(raw_data, dtype=np.int16)
            
            # Downmix to mono if needed
            if channels > 1:
                samples = samples.reshape(-1, channels)
                # Mean of channels
                samples = samples.mean(axis=1).astype(np.int16)
            
            # If native rate is different from 44.1k, we need to resample
            if native_rate != TARGET_RATE:
                # Simple linear interpolation for resampling
                num_samples = len(samples)
                target_num_samples = int(num_samples * TARGET_RATE / native_rate)
                resampled_samples = np.interp(
                    np.linspace(0, num_samples - 1, target_num_samples),
                    np.arange(num_samples),
                    samples
                ).astype(np.int16)
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

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Glyphix Desktop Companion")
    parser.add_argument("--ip", help="Force a specific IP address (skips discovery)")
    args = parser.parse_args()
    
    print("Glyphix Desktop Companion v1.1")
    
    target_ip = args.ip
    if not target_ip:
        target_ip = discover_phone()
    
    if target_ip:
        run_companion(target_ip)
    else:
        print("\nCould not find your phone automatically.")
        print(f"If you see an IP in the app, try: python desktop_companion.py --ip YOUR_IP")
        sys.exit(1)
