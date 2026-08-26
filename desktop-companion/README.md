# Glyphix Desktop Companion

This script captures system audio from your Windows PC and streams it to the Glyphix Android app via UDP. It allows the Glyphs, haptics, and visualizers to react to your computer's audio in real-time.

## Prerequisites

- **Windows 10/11** (Required for WASAPI loopback capture).
- **Python 3.8+** installed.
- **Glyphix Android App** installed on a phone on the same Wi-Fi network.

## Installation

1. Install the required Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```

## Usage

1. Open the **Glyphix** app on your phone.
2. Go to the **Audio** tab.
3. Select **Desktop Companion (UDP)** as the capture source.
4. On your PC, run the script:
   ```bash
   python desktop_companion.py
   ```

The script will automatically search for your phone on the network and start streaming.

## Manual Connection

If auto-discovery fails, you can force a connection to a specific IP address:
```bash
python desktop_companion.py --ip 192.168.1.50
```
(Replace with the IP address shown in the Glyphix app).
