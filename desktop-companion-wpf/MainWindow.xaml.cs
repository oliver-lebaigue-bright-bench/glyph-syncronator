using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using Color = System.Windows.Media.Color;
using Rectangle = System.Windows.Shapes.Rectangle;
using System.Windows.Threading;
using NAudio.CoreAudioApi;

namespace GlyphixDesktopCompanion
{
    public partial class MainWindow : Window
    {
        private readonly AudioEngine _audioEngine = new();
        private readonly NetworkEngine _networkEngine = new();
        private readonly OpenRGBManager _rgbManager = new();
        private readonly CaseFanVisualizer _fanVisualizer = new();
        private KeyboardHookWatcher? _keyboardHook;

        private float[] _spectrum = new float[64];
        private float[] _peaks = new float[64];
        private DateTime[] _peakHolds = new DateTime[64];
        private float _lastRms = 0;
        private float _lastPulse = 0;

        private List<Rectangle[]> _spectrumRects = new();
        private Ellipse[] _fanDots = new Ellipse[0];
        private Color _customColor = Colors.White;

        private bool _isStreaming = false;
        private bool _isDiscovering = false;
        private DateTime _lastKeyTime = DateTime.MinValue;
        private DateTime _lastPacketTime = DateTime.MinValue;

        private double _animPhase = 0;
        private double _fanPreviewAngle = 0;

        public MainWindow()
        {
            InitializeComponent();

            _audioEngine.OnAudioDataProcessed = (spec, rms, pulse) =>
            {
                Array.Copy(spec, _spectrum, 64);
                _lastRms = rms;
                _lastPulse = pulse;
                _lastPacketTime = DateTime.Now;
            };

            _networkEngine.OnLog = Log;
            _networkEngine.OnAudioDataReceived = (data, ep) =>
            {
                // In Phone -> PC, we'd process audio here, but the AudioEngine
                // is usually used locally. For Phone -> PC, we'd need a way to feed
                // this data into the same processing logic.
                // For simplicity in this port, we'll assume the user might want
                // to see the spectrum from the network data too.
                _lastPacketTime = DateTime.Now;
            };

            _rgbManager.OnLog = Log;
            _rgbManager.OnHeadersDetected = headers =>
            {
                Dispatcher.Invoke(() =>
                {
                    HeaderCombo.ItemsSource = headers;
                    if (headers.Count > 0) HeaderCombo.SelectedIndex = 0;
                });
            };

            InitializeUI();

            CompositionTarget.Rendering += OnRendering;

            PcIpText.Text = $"{_networkEngine.LocalIP} : 12347";
            _networkEngine.StartDiscoveryResponder();

            _keyboardHook = new KeyboardHookWatcher(() => _lastKeyTime = DateTime.Now);
        }

        private void InitializeUI()
        {
            AudioSourceCombo.ItemsSource = AudioEngine.GetDevices().Select(d => d.FriendlyName).ToList();
            AudioSourceCombo.SelectedIndex = 0;

            FanModeCombo.ItemsSource = new[] { "Radial VU Meter (Full Ring)", "Radial VU (Dual Symmetrical)", "Spinner (Glyph Ring)", "Multi-Fan Spectrum (EQ)", "Wave Ripple", "Bass Strobe" };
            FanModeCombo.SelectedIndex = 0;

            FanThemeCombo.ItemsSource = new[] { "Glyph White", "Cyber Cyan", "Electric Purple", "Solar Orange", "Neon Magenta", "Ice Blue", "Deep Violet", "Reactive Rainbow", "Custom Spectrum Color..." };
            FanThemeCombo.SelectedIndex = 0;

            RingLedCombo.ItemsSource = new[] { "8 LEDs", "12 LEDs", "16 LEDs (Standard)", "24 LEDs", "32 LEDs" };
            RingLedCombo.SelectedIndex = 2;

            FanCountCombo.ItemsSource = new[] { "1 Fan", "2 Fans", "3 Fans (Standard Case)", "4 Fans", "5 Fans", "6 Fans" };
            FanCountCombo.SelectedIndex = 0;

            InitializeSpectrumCanvas();
            InitializeFanCanvas();
        }

        private void InitializeSpectrumCanvas()
        {
            SpectrumCanvas.Children.Clear();
            _spectrumRects.Clear();

            double width = 580; // Approximate
            double step = width / 64.0;

            for (int i = 0; i < 64; i++)
            {
                var col = new Rectangle[8];
                for (int d = 0; d < 8; d++)
                {
                    var r = new Rectangle
                    {
                        Width = step - 4,
                        Height = 8,
                        Fill = new SolidColorBrush(Color.FromRgb(21, 23, 30)),
                        RadiusX = 2, RadiusY = 2
                    };
                    Canvas.SetLeft(r, i * step + 2);
                    Canvas.SetBottom(r, d * 10 + 2);
                    SpectrumCanvas.Children.Add(r);
                    col[d] = r;
                }
                _spectrumRects.Add(col);
            }
        }

        private void InitializeFanCanvas()
        {
            FanCanvas.Children.Clear();
            double cx = 71, cy = 71;

            var outer = new Ellipse { Width = 116, Height = 116, Stroke = new SolidColorBrush(Color.FromRgb(34, 37, 46)), StrokeThickness = 2, Fill = new SolidColorBrush(Color.FromRgb(12, 13, 16)) };
            Canvas.SetLeft(outer, cx - 58); Canvas.SetTop(outer, cy - 58);
            FanCanvas.Children.Add(outer);

            var inner = new Ellipse { Width = 48, Height = 48, Stroke = new SolidColorBrush(Color.FromRgb(43, 47, 59)), StrokeThickness = 1.5, Fill = new SolidColorBrush(Color.FromRgb(22, 24, 31)) };
            Canvas.SetLeft(inner, cx - 24); Canvas.SetTop(inner, cy - 24);
            FanCanvas.Children.Add(inner);

            int numLeds = 16;
            _fanDots = new Ellipse[numLeds];
            double r = 42;
            for (int i = 0; i < numLeds; i++)
            {
                double angle = (2.0 * Math.PI * i) / numLeds - (Math.PI / 2.0);
                var dot = new Ellipse { Width = 9.6, Height = 9.6, Fill = new SolidColorBrush(Color.FromRgb(30, 32, 40)) };
                Canvas.SetLeft(dot, cx + r * Math.Cos(angle) - 4.8);
                Canvas.SetTop(dot, cy + r * Math.Sin(angle) - 4.8);
                FanCanvas.Children.Add(dot);
                _fanDots[i] = dot;
            }
        }

        private void OnRendering(object? sender, EventArgs e)
        {
            _animPhase += 0.08;
            UpdateStatusUI();
            UpdateSpectrumUI();
            UpdateFanUI();

            if (_isStreaming)
            {
                float sensitivity = (float)SensSlider.Value;
                float decay = (float)DecaySlider.Value;

                bool suppress = TypingSuppression.IsChecked == true && (DateTime.Now - _lastKeyTime).TotalSeconds < 1.5;

                if (_rgbManager.Connected && !suppress)
                {
                    _rgbManager.Sync(Colors.White, _lastRms * sensitivity, _lastRms * sensitivity * 1.2f, _lastPulse * sensitivity, _spectrum, decay);
                }
            }
        }

        private void UpdateStatusUI()
        {
            if (_isStreaming)
            {
                double pulse = (Math.Sin(_animPhase * 3.2) * 0.5 + 0.5);
                byte val = (byte)(140 + pulse * 115);
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(val, val, val));
            }
            else if (_isDiscovering)
            {
                double pulse = (Math.Sin(_animPhase * 5.0) * 0.5 + 0.5);
                byte val = (byte)(160 + pulse * 95);
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(val, val, val));
            }
            else
            {
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(74, 80, 96));
            }
        }

        private void UpdateSpectrumUI()
        {
            bool active = (DateTime.Now - _lastPacketTime).TotalSeconds < 0.5;
            float sensitivity = (float)SensSlider.Value;

            Color[] gradient = {
                Color.FromRgb(34, 38, 46), Color.FromRgb(50, 56, 68), Color.FromRgb(68, 76, 92),
                Color.FromRgb(90, 101, 122), Color.FromRgb(123, 137, 160), Color.FromRgb(162, 176, 199),
                Color.FromRgb(203, 213, 225), Colors.White
            };

            for (int i = 0; i < 64; i++)
            {
                float val = active ? _spectrum[i] * 15 * sensitivity : (float)((Math.Sin(_animPhase * 1.2 + i * 0.22) * 0.5 + 0.5) * 0.16 + 0.03);
                int dots = (int)(val * 8);

                if (val >= _peaks[i]) { _peaks[i] = val; _peakHolds[i] = DateTime.Now.AddSeconds(0.3); }
                else if (DateTime.Now > _peakHolds[i]) _peaks[i] = Math.Max(0, _peaks[i] - 0.02f);

                int peakIdx = (int)(_peaks[i] * 7.99f);

                for (int d = 0; d < 8; d++)
                {
                    var brush = (d < dots) ? new SolidColorBrush(gradient[d]) :
                                (d == peakIdx && peakIdx > 0 && peakIdx >= dots) ? System.Windows.Media.Brushes.White :
                                new SolidColorBrush(Color.FromRgb(21, 23, 30));
                    _spectrumRects[i][d].Fill = brush;
                }
            }
        }

        private void UpdateFanUI()
        {
            float sensitivity = (float)SensSlider.Value;
            float decay = (float)DecaySlider.Value;
            float speedMult = (float)SpeedSlider.Value;
            bool clockwise = FanClockwise.IsChecked == true;

            string mode = FanModeCombo.SelectedItem?.ToString() ?? "vu_meter";
            if (mode.Contains("VU Meter")) mode = "vu_meter";
            else if (mode.Contains("Dual")) mode = "vu_meter_dual";
            else if (mode.Contains("Spinner")) mode = "spinner";
            else if (mode.Contains("Spectrum")) mode = "spectrum";
            else if (mode.Contains("Ripple")) mode = "ripple";
            else mode = "pulse";

            var colors = _fanVisualizer.RenderFanRing(16, _lastRms * sensitivity, _lastRms * sensitivity * 1.2f, _lastPulse * sensitivity,
                                           mode, _customColor, clockwise, speedMult, 0, 1, _spectrum, decay);

            for (int i = 0; i < Math.Min(colors.Count, _fanDots.Length); i++)
            {
                _fanDots[i].Fill = new SolidColorBrush(colors[i]);
            }
        }

        private void Start_Click(object sender, RoutedEventArgs e)
        {
            if (_isStreaming) StopStreaming();
            else StartStreaming();
        }

        private void StartStreaming()
        {
            _isStreaming = true;
            StartBtn.Content = "⏹ STOP LISTENER";
            StatusPill.Text = "LISTENING";

            if (RbPhoneToPc.IsChecked == true)
            {
                _networkEngine.StartListener();
            }
            else
            {
                var device = AudioEngine.GetDevices().FirstOrDefault(d => d.FriendlyName == AudioSourceCombo.SelectedItem.ToString());
                if (device != null) _audioEngine.Start(device);
            }

            if (TypingSuppression.IsChecked == true) _keyboardHook?.Start();
        }

        private void StopStreaming()
        {
            _isStreaming = false;
            StartBtn.Content = "▶ START LISTENER";
            StatusPill.Text = "STANDBY";
            _audioEngine.Stop();
            _networkEngine.StopListener();
            _keyboardHook?.Stop();
            _rgbManager.Stop();
        }

        private void Discover_Click(object sender, RoutedEventArgs e)
        {
            if (_isDiscovering) return;
            _isDiscovering = true;
            StatusPill.Text = "SEARCHING...";
            Task.Run(async () =>
            {
                var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
                var ip = await _networkEngine.DiscoverPhoneAsync(cts.Token);
                Dispatcher.Invoke(() =>
                {
                    _isDiscovering = false;
                    if (ip != null)
                    {
                        PhoneIpEntry.Text = ip;
                        StatusPill.Text = "PHONE FOUND";
                    }
                    else StatusPill.Text = "STANDBY";
                });
            });
        }

        private void OpenRgb_Toggle(object sender, RoutedEventArgs e)
        {
            if (OpenRgbEnable.IsChecked == true) Task.Run(() => _rgbManager.Connect());
            else _rgbManager.Stop();
        }

        private void SyncDir_Checked(object sender, RoutedEventArgs e)
        {
            if (PcIpBox == null || PhoneIpBox == null) return;
            if (RbPhoneToPc.IsChecked == true)
            {
                PcIpBox.Visibility = Visibility.Visible;
                PhoneIpBox.Visibility = Visibility.Collapsed;
                AudioSourceCombo.IsEnabled = false;
            }
            else
            {
                PcIpBox.Visibility = Visibility.Collapsed;
                PhoneIpBox.Visibility = Visibility.Visible;
                AudioSourceCombo.IsEnabled = true;
            }
        }

        private void CopyIp_Click(object sender, RoutedEventArgs e)
        {
            System.Windows.Clipboard.SetText(_networkEngine.LocalIP);
            Log("IP Copied to clipboard");
        }

        private void ScanRgb_Click(object sender, RoutedEventArgs e)
        {
            Task.Run(() => _rgbManager.Connect());
        }

        private void ColorPicker_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new System.Windows.Forms.ColorDialog();
            if (dialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
            {
                _customColor = Color.FromRgb(dialog.Color.R, dialog.Color.G, dialog.Color.B);
                ColorSwatch.Background = new SolidColorBrush(_customColor);
            }
        }

        private void ToggleLogs_Click(object sender, RoutedEventArgs e)
        {
            if (LogBox.Visibility == Visibility.Visible)
            {
                LogBox.Visibility = Visibility.Collapsed;
                ToggleLogsBtn.Content = "▼ SHOW SYSTEM LOGS";
            }
            else
            {
                LogBox.Visibility = Visibility.Visible;
                ToggleLogsBtn.Content = "▲ HIDE SYSTEM LOGS";
            }
        }

        private void Log(string msg)
        {
            Dispatcher.Invoke(() =>
            {
                LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {msg}\n");
                LogBox.ScrollToEnd();
            });
        }
    }
}
