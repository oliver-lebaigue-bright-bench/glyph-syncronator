using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Media;
using OpenRGB.NET;
using Color = System.Windows.Media.Color;

namespace GlyphixDesktopCompanion
{
    public class OpenRGBManager : IDisposable
    {
        private OpenRgbClient? _client;
        private Device[] _devices = Array.Empty<Device>();
        private bool _connected = false;
        private DateTime _lastSyncTime = DateTime.MinValue;
        private readonly Dictionary<string, CaseFanVisualizer> _fanVisualizers = new();
        private Dictionary<string, HeaderConfig> _headerConfigs = new();

        public Action<List<string>>? OnHeadersDetected;
        public Action<string>? OnLog;

        public bool Connected => _connected;

        public void Connect(bool autoLaunch = true)
        {
            if (!IsPortOpen("127.0.0.1", 6742))
            {
                if (autoLaunch)
                {
                    OnLog?.Invoke("OpenRGB: Server not detected. Launching elevated...");
                    if (LaunchOpenRGBElevated())
                    {
                        var start = DateTime.Now;
                        while ((DateTime.Now - start).TotalSeconds < 8.0)
                        {
                            if (IsPortOpen("127.0.0.1", 6742))
                            {
                                Thread.Sleep(500);
                                break;
                            }
                            Thread.Sleep(500);
                        }
                    }
                }
            }

            try
            {
                _client = new OpenRgbClient("127.0.0.1", 6742, "GlyphixCompanion");
                _client.Connect();
                _devices = _client.GetAllControllerData();

                foreach (var device in _devices)
                {
                    // Set to direct mode if possible
                    var directMode = device.Modes.FirstOrDefault(m =>
                        m.Name.Equals("Direct", StringComparison.OrdinalIgnoreCase) ||
                        m.Name.Equals("Custom", StringComparison.OrdinalIgnoreCase) ||
                        m.Name.Equals("Static", StringComparison.OrdinalIgnoreCase));
                    if (directMode != null)
                    {
                        _client.UpdateMode(device.Index, device.Modes.ToList().IndexOf(directMode));
                    }
                }

                _connected = true;
                UpdateHeaderConfigs(_headerConfigs);
                OnHeadersDetected?.Invoke(GetDetectedHeaders());
                OnLog?.Invoke($"OpenRGB: Connected to {_devices.Length} devices.");
            }
            catch (Exception ex)
            {
                _connected = false;
                OnLog?.Invoke($"OpenRGB Connection Error: {ex.Message}");
            }
        }

        private bool IsPortOpen(string host, int port)
        {
            try
            {
                using var client = new TcpClient();
                var result = client.BeginConnect(host, port, null, null);
                var success = result.AsyncWaitHandle.WaitOne(TimeSpan.FromMilliseconds(300));
                if (!success) return false;
                client.EndConnect(result);
                return true;
            }
            catch { return false; }
        }

        private bool LaunchOpenRGBElevated()
        {
            string? exe = FindOpenRGBExecutable();
            if (exe == null) return false;

            try
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = exe,
                    Arguments = "--server --startminimized",
                    Verb = "runas",
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Minimized
                };
                Process.Start(psi);
                return true;
            }
            catch { return false; }
        }

        private string? FindOpenRGBExecutable()
        {
            string[] candidates = {
                @"C:\Program Files\OpenRGB\OpenRGB.exe",
                @"C:\Program Files (x86)\OpenRGB\OpenRGB.exe",
                @"C:\OpenRGB\OpenRGB.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Programs\OpenRGB\OpenRGB.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"OpenRGB\OpenRGB.exe"),
            };
            return candidates.FirstOrDefault(File.Exists);
        }

        public List<string> GetDetectedHeaders()
        {
            if (!_connected) return new List<string> { "No OpenRGB Connected" };

            var headers = new List<string>();
            foreach (var dev in _devices)
            {
                foreach (var zone in dev.Zones)
                {
                    if (IsFanZone(zone, dev))
                    {
                        headers.Add($"{zone.Name} ({dev.Name})");
                    }
                }
            }

            if (headers.Count == 0) return new List<string> { "No ARGB Devices Connected" };
            if (headers.Count > 1) headers.Insert(0, "All Connected ARGB (Sync All)");
            return headers;
        }

        private bool IsFanZone(Zone zone, Device dev)
        {
            string name = zone.Name.ToLower();
            string[] nonFan = { "jrgb", "12v", "onboard", "audio", "pcie", "io_cover", "chipset", "pch", "logo" };
            if (nonFan.Any(ex => name.Contains(ex))) return false;

            if (zone.Type == ZoneType.Single && zone.LedCount <= 1) return false;

            // Safe check for device types
            string devTypeStr = dev.Type.ToString().ToLower();
            if (devTypeStr.Contains("cooler") || devTypeStr.Contains("case") || devTypeStr.Contains("strip") || devTypeStr.Contains("dram") || devTypeStr.Contains("fan"))
                return true;

            string[] fanKeywords = { "rainbow", "jrainbow", "d_led", "add_header", "add_gen2", "addr_led", "addressable", "argb", "polychrome", "fan", "cooler", "pump", "aio", "hub" };
            return fanKeywords.Any(k => name.Contains(k));
        }

        public void UpdateHeaderConfigs(Dictionary<string, HeaderConfig> configs)
        {
            _headerConfigs = configs;
            if (!_connected) return;

            foreach (var dev in _devices)
            {
                for (int z = 0; z < dev.Zones.Length; z++)
                {
                    var zone = dev.Zones[z];
                    if (IsFanZone(zone, dev))
                    {
                        var cfg = GetZoneConfig(zone, dev);
                        int needed = cfg.FanRingSize * cfg.FanCount;
                        if (zone.LedCount < needed)
                        {
                            try { _client?.ResizeZone(dev.Index, z, needed); } catch { }
                        }
                    }
                }
            }
        }

        private HeaderConfig GetZoneConfig(Zone zone, Device dev)
        {
            string key = $"{zone.Name} ({dev.Name})";
            if (_headerConfigs.TryGetValue(key, out var cfg)) return cfg;
            if (_headerConfigs.TryGetValue("All Connected ARGB (Sync All)", out var syncAll)) return syncAll;
            return new HeaderConfig();
        }

        public void Sync(Color ambient, float rawLevel, float energy, float pulse, float[]? spectrum, float decayRate)
        {
            if (!_connected || _client == null) return;
            if ((DateTime.Now - _lastSyncTime).TotalMilliseconds < 20) return;

            foreach (var dev in _devices)
            {
                var leds = new OpenRGB.NET.Color[dev.Leds.Length];
                int globalFanIdx = 0;
                int zStart = 0;

                for (int z = 0; z < dev.Zones.Length; z++)
                {
                    var zone = dev.Zones[z];
                    int zLen = (int)zone.LedCount;

                    if (IsFanZone(zone, dev))
                    {
                        var cfg = GetZoneConfig(zone, dev);
                        if (!cfg.Enabled)
                        {
                            FillZone(leds, zStart, zLen, ambient);
                            zStart += zLen;
                            continue;
                        }

                        var viz = GetVisualizer(zone.Name + dev.Name);
                        int ringSize = cfg.FanRingSize;
                        int fanCount = Math.Max(1, zLen / ringSize);

                        for (int f = 0; f < fanCount; f++)
                        {
                            var fanColors = viz.RenderFanRing(ringSize, rawLevel, energy, pulse, cfg.Mode,
                                cfg.CustomColor, cfg.Clockwise, cfg.Speed, globalFanIdx + f, fanCount, spectrum, decayRate);

                            for (int i = 0; i < Math.Min(fanColors.Count, ringSize); i++)
                            {
                                int idx = zStart + f * ringSize + i;
                                if (idx < leds.Length)
                                    leds[idx] = new OpenRGB.NET.Color(fanColors[i].R, fanColors[i].G, fanColors[i].B);
                            }
                        }
                        globalFanIdx += fanCount;
                    }
                    else
                    {
                        FillZone(leds, zStart, zLen, ambient);
                    }

                    zStart += zLen;
                }
                _client.UpdateLeds(dev.Index, leds);
            }
            _lastSyncTime = DateTime.Now;
        }

        private void FillZone(OpenRGB.NET.Color[] leds, int start, int len, Color color)
        {
            var orgbColor = new OpenRGB.NET.Color(color.R, color.G, color.B);
            for (int i = 0; i < len; i++)
            {
                if (start + i < leds.Length) leds[start + i] = orgbColor;
            }
        }

        private CaseFanVisualizer GetVisualizer(string key)
        {
            if (!_fanVisualizers.TryGetValue(key, out var viz))
            {
                viz = new CaseFanVisualizer();
                _fanVisualizers[key] = viz;
            }
            return viz;
        }

        public void Stop()
        {
            if (!_connected || _client == null) return;
            foreach (var dev in _devices)
            {
                try
                {
                    var blackLeds = Enumerable.Repeat(new OpenRGB.NET.Color(0, 0, 0), dev.Leds.Length).ToArray();
                    _client.UpdateLeds(dev.Index, blackLeds);
                }
                catch { }
            }
        }

        public void Dispose()
        {
            Stop();
            _client?.Dispose();
        }
    }

    public class HeaderConfig
    {
        public string Mode { get; set; } = "vu_meter";
        public Color CustomColor { get; set; } = Colors.White;
        public int FanRingSize { get; set; } = 16;
        public int FanCount { get; set; } = 1;
        public bool Clockwise { get; set; } = true;
        public float Speed { get; set; } = 1.0f;
        public bool Enabled { get; set; } = true;
    }
}
