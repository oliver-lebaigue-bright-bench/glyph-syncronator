using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows.Media;
using Color = System.Windows.Media.Color;

namespace GlyphixDesktopCompanion
{
    public class CaseFanVisualizer
    {
        private float _vuLevel = 0f;
        private float _peakLed = 0f;
        private DateTime _peakHoldUntil = DateTime.MinValue;
        private double _angle = 0;
        private double _ripplePhase = 0;
        private DateTime _lastTime = DateTime.Now;

        private float[] _bandLevels = new float[3];
        private float[] _bandPeaks = new float[3];
        private DateTime[] _bandHolds = new DateTime[3];

        public CaseFanVisualizer()
        {
            for (int i = 0; i < 3; i++) _bandHolds[i] = DateTime.MinValue;
        }

        public List<Color> RenderFanRing(int numLeds, float rawLevel, float energy, float pulse, string mode,
                                       Color baseColor, bool clockwise, float speedMult, int fanIdx, int totalFans,
                                       float[]? spectrum, float decayRate)
        {
            var now = DateTime.Now;
            double dt = Math.Min((now - _lastTime).TotalSeconds, 0.08);
            _lastTime = now;
            numLeds = Math.Max(4, numLeds);

            var colors = new List<Color>();

            switch (mode.ToLower())
            {
                case "spinner":
                    RenderSpinner(colors, numLeds, energy, pulse, baseColor, clockwise, speedMult, fanIdx, dt);
                    break;
                case "vu_meter":
                    RenderVUMeter(colors, numLeds, rawLevel, baseColor, clockwise, decayRate, dt, now);
                    break;
                case "vu_meter_dual":
                    RenderVUMeterDual(colors, numLeds, rawLevel, baseColor, decayRate, dt, now);
                    break;
                case "spectrum":
                    RenderSpectrum(colors, numLeds, spectrum, pulse, rawLevel, baseColor, clockwise, fanIdx, totalFans, dt, now);
                    break;
                case "ripple":
                    RenderRipple(colors, numLeds, pulse, baseColor, fanIdx, totalFans, dt);
                    break;
                case "pulse":
                default:
                    RenderPulse(colors, numLeds, pulse, baseColor);
                    break;
            }

            return colors;
        }

        private void RenderSpinner(List<Color> colors, int numLeds, float energy, float pulse, Color baseColor, bool clockwise, float speedMult, int fanIdx, double dt)
        {
            double speed = (2.5 + energy * 9.0 + pulse * 7.0) * speedMult;
            double dirMult = clockwise ? 1.0 : -1.0;
            _angle = (_angle + dirMult * speed * dt) % (2.0 * Math.PI);
            double fanAngle = (_angle + fanIdx * (0.5 * Math.PI)) % (2.0 * Math.PI);

            double tailLen = Math.PI * 1.2;
            for (int i = 0; i < numLeds; i++)
            {
                double phi = (2.0 * Math.PI * i) / numLeds;
                double diff = clockwise ? (fanAngle - phi) : (phi - fanAngle);
                while (diff < 0) diff += 2.0 * Math.PI;
                diff %= 2.0 * Math.PI;

                double brightness = diff <= tailLen ? Math.Pow(1.0 - diff / tailLen, 1.6) : 0.0;
                brightness = Math.Clamp(brightness + pulse * 0.45, 0.0, 1.0);
                colors.Add(Color.FromRgb((byte)(baseColor.R * brightness), (byte)(baseColor.G * brightness), (byte)(baseColor.B * brightness)));
            }
        }

        private void RenderVUMeter(List<Color> colors, int numLeds, float rawLevel, Color baseColor, bool clockwise, float decayRate, double dt, DateTime now)
        {
            float target = Math.Clamp(rawLevel, 0f, 1f);
            if (target > _vuLevel) _vuLevel = _vuLevel * 0.25f + target * 0.75f;
            else _vuLevel = Math.Max(0f, _vuLevel * Math.Clamp(decayRate, 0.75f, 0.98f));

            int activeCount = (int)Math.Round(_vuLevel * numLeds);
            if (activeCount >= _peakLed)
            {
                _peakLed = activeCount;
                _peakHoldUntil = now.AddSeconds(0.25);
            }
            else if (now > _peakHoldUntil)
            {
                _peakLed = (float)Math.Max(0, _peakLed - 14.0 * dt);
            }
            int peakIdx = (int)_peakLed;

            for (int i = 0; i < numLeds; i++)
            {
                int fillIdx = clockwise ? i : ((numLeds - i) % numLeds);
                if (fillIdx < activeCount)
                {
                    float frac = fillIdx / (float)Math.Max(1, numLeds - 1);
                    colors.Add(GetMeterColor(frac, baseColor));
                }
                else if (fillIdx == peakIdx && peakIdx > 0)
                {
                    colors.Add(Colors.White);
                }
                else
                {
                    colors.Add(Colors.Black);
                }
            }
        }

        private void RenderVUMeterDual(List<Color> colors, int numLeds, float rawLevel, Color baseColor, float decayRate, double dt, DateTime now)
        {
            float target = Math.Clamp(rawLevel, 0f, 1f);
            if (target > _vuLevel) _vuLevel = _vuLevel * 0.25f + target * 0.75f;
            else _vuLevel = Math.Max(0f, _vuLevel * Math.Clamp(decayRate, 0.75f, 0.98f));

            int half = Math.Max(2, numLeds / 2);
            int activeHalf = (int)Math.Round(_vuLevel * half);
            if (activeHalf >= _peakLed)
            {
                _peakLed = activeHalf;
                _peakHoldUntil = now.AddSeconds(0.25);
            }
            else if (now > _peakHoldUntil)
            {
                _peakLed = (float)Math.Max(0, _peakLed - 10.0 * dt);
            }
            int peakHalf = (int)_peakLed;

            for (int i = 0; i < numLeds; i++)
            {
                int pos = i < half ? i : (numLeds - 1 - i);
                if (pos < activeHalf)
                {
                    float frac = pos / (float)Math.Max(1, half - 1);
                    colors.Add(GetMeterColor(frac, baseColor));
                }
                else if (pos == peakHalf && peakHalf > 0)
                {
                    colors.Add(Colors.White);
                }
                else
                {
                    colors.Add(Colors.Black);
                }
            }
        }

        private void RenderSpectrum(List<Color> colors, int numLeds, float[]? spectrum, float pulse, float rawLevel, Color baseColor, bool clockwise, int fanIdx, int totalFans, double dt, DateTime now)
        {
            float bRaw, mRaw, hRaw;
            if (spectrum != null && spectrum.Length >= 32)
            {
                bRaw = spectrum.Take(6).Average() * 3.5f;
                mRaw = spectrum.Skip(8).Take(20).Average() * 4.0f;
                hRaw = spectrum.Skip(32).Take(26).Average() * 4.5f;
            }
            else
            {
                bRaw = pulse; mRaw = rawLevel; hRaw = rawLevel * 0.8f;
            }

            float[] targets = { Math.Clamp(bRaw, 0, 1), Math.Clamp(mRaw, 0, 1), Math.Clamp(hRaw, 0, 1) };
            Color[] bandColors = { Colors.White, Color.FromRgb(0, 210, 255), Color.FromRgb(168, 85, 247) };

            if (totalFans > 1)
            {
                int bIdx = fanIdx % 3;
                float tgt = targets[bIdx];
                if (tgt > _bandLevels[bIdx]) _bandLevels[bIdx] = _bandLevels[bIdx] * 0.25f + tgt * 0.75f;
                else _bandLevels[bIdx] = Math.Max(0f, _bandLevels[bIdx] * 0.85f);

                int active = (int)Math.Round(_bandLevels[bIdx] * numLeds);
                if (active >= _bandPeaks[bIdx]) { _bandPeaks[bIdx] = active; _bandHolds[bIdx] = now.AddSeconds(0.25); }
                else if (now > _bandHolds[bIdx]) _bandPeaks[bIdx] = (float)Math.Max(0, _bandPeaks[bIdx] - 12.0 * dt);
                int peak = (int)_bandPeaks[bIdx];

                Color col = (baseColor == Colors.White) ? bandColors[bIdx] : baseColor;
                for (int i = 0; i < numLeds; i++)
                {
                    int fillIdx = clockwise ? i : ((numLeds - i) % numLeds);
                    if (fillIdx < active) colors.Add(GetMeterColor(fillIdx / (float)Math.Max(1, numLeds - 1), col));
                    else if (fillIdx == peak && peak > 0) colors.Add(Colors.White);
                    else colors.Add(Colors.Black);
                }
            }
            else
            {
                int sectorSize = numLeds / 3;
                for (int i = 0; i < numLeds; i++)
                {
                    int sIdx = Math.Min(2, i / Math.Max(1, sectorSize));
                    int offset = i - sIdx * sectorSize;
                    int secLen = (sIdx < 2) ? sectorSize : (numLeds - 2 * sectorSize);
                    int active = (int)Math.Round(targets[sIdx] * secLen);
                    if (offset < active) colors.Add((baseColor == Colors.White) ? bandColors[sIdx] : baseColor);
                    else colors.Add(Colors.Black);
                }
            }
        }

        private void RenderRipple(List<Color> colors, int numLeds, float pulse, Color baseColor, int fanIdx, int totalFans, double dt)
        {
            _ripplePhase = (_ripplePhase + (3.0 + pulse * 6.0) * dt) % (totalFans + 1);
            double dist = Math.Abs(_ripplePhase - fanIdx);
            double waveInt = Math.Max(Math.Pow(Math.Clamp(1.0 - dist, 0.0, 1.0), 2.0), pulse * 0.3);
            for (int i = 0; i < numLeds; i++)
                colors.Add(Color.FromRgb((byte)(baseColor.R * waveInt), (byte)(baseColor.G * waveInt), (byte)(baseColor.B * waveInt)));
        }

        private void RenderPulse(List<Color> colors, int numLeds, float pulse, Color baseColor)
        {
            float p = Math.Clamp(pulse * 1.2f, 0f, 1f);
            for (int i = 0; i < numLeds; i++)
                colors.Add(Color.FromRgb((byte)(baseColor.R * p), (byte)(baseColor.G * p), (byte)(baseColor.B * p)));
        }

        private Color GetMeterColor(float frac, Color baseColor)
        {
            if (baseColor == Colors.White)
            {
                byte val = (byte)(100 + frac * 155);
                return Color.FromRgb(val, val, val);
            }
            else
            {
                if (frac < 0.65f) return Color.FromRgb((byte)(baseColor.R * 0.7), (byte)(baseColor.G * 0.7), (byte)(baseColor.B * 0.7));
                if (frac < 0.88f) return baseColor;
                float blend = (frac - 0.88f) / 0.12f;
                return Color.FromRgb(
                    (byte)(baseColor.R * (1 - blend) + 255 * blend),
                    (byte)(baseColor.G * (1 - blend) + 255 * blend),
                    (byte)(baseColor.B * (1 - blend) + 255 * blend));
            }
        }
    }
}
