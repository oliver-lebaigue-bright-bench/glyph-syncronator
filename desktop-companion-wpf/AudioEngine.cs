using System;
using System.Linq;
using NAudio.Wave;
using NAudio.Dsp;
using NAudio.CoreAudioApi;
using System.Numerics;
using System.Collections.Generic;
using Complex = NAudio.Dsp.Complex;

namespace GlyphixDesktopCompanion
{
    public class AudioEngine : IDisposable
    {
        private WasapiLoopbackCapture? _capture;
        private int _sampleRate;
        private int _channels;
        private readonly PureBassEngine _bassEngine;

        public Action<float[], float, float>? OnAudioDataProcessed;

        private const int FFT_SIZE = 2048; // Must be power of 2
        private readonly Complex[] _fftBuffer = new Complex[FFT_SIZE];
        private int _fftPos = 0;
        private readonly float[] _hanningWindow;

        public AudioEngine()
        {
            _bassEngine = new PureBassEngine(48000); // Default, will update
            _hanningWindow = new float[FFT_SIZE];
            for (int i = 0; i < FFT_SIZE; i++)
            {
                _hanningWindow[i] = (float)(0.5 * (1.0 - Math.Cos(2.0 * Math.PI * i / (FFT_SIZE - 1))));
            }
        }

        public void Start(MMDevice device)
        {
            Stop();
            _capture = new WasapiLoopbackCapture(device);
            _sampleRate = _capture.WaveFormat.SampleRate;
            _channels = _capture.WaveFormat.Channels;
            _bassEngine.SampleRate = _sampleRate;

            _capture.DataAvailable += (s, e) =>
            {
                ProcessAudio(e.Buffer, e.BytesRecorded);
            };
            _capture.StartRecording();
        }

        public void Stop()
        {
            if (_capture != null)
            {
                _capture.StopRecording();
                _capture.Dispose();
                _capture = null;
            }
        }

        private void ProcessAudio(byte[] buffer, int bytesRecorded)
        {
            int bytesPerSample = _capture!.WaveFormat.BitsPerSample / 8;
            int sampleCount = bytesRecorded / bytesPerSample;
            float[] samples = new float[sampleCount / _channels];

            for (int i = 0; i < samples.Length; i++)
            {
                float sum = 0;
                for (int c = 0; c < _channels; c++)
                {
                    int index = (i * _channels + c) * bytesPerSample;
                    if (bytesPerSample == 2)
                        sum += BitConverter.ToInt16(buffer, index) / 32768f;
                    else if (bytesPerSample == 4)
                        sum += BitConverter.ToSingle(buffer, index);
                }
                samples[i] = sum / _channels;

                // Add to FFT buffer
                _fftBuffer[_fftPos].X = samples[i] * _hanningWindow[_fftPos];
                _fftBuffer[_fftPos].Y = 0;
                _fftPos++;

                if (_fftPos >= FFT_SIZE)
                {
                    ProcessFFT();
                    _fftPos = 0;
                }
            }
        }

        private void ProcessFFT()
        {
            FastFourierTransform.FFT(true, (int)Math.Log(FFT_SIZE, 2), _fftBuffer);

            int halfSize = FFT_SIZE / 2;
            float[] mags = new float[halfSize];
            for (int i = 0; i < halfSize; i++)
            {
                mags[i] = (float)Math.Sqrt(_fftBuffer[i].X * _fftBuffer[i].X + _fftBuffer[i].Y * _fftBuffer[i].Y) / (FFT_SIZE / 2f);
            }

            // Map to 64 geometric bins
            float[] spectrum = new float[64];
            double minFreq = 40;
            double maxFreq = 15000;
            double logStep = Math.Pow(maxFreq / minFreq, 1.0 / 63.0);

            for (int i = 0; i < 64; i++)
            {
                double targetFreq = minFreq * Math.Pow(logStep, i);
                int binIdx = (int)(targetFreq / (_sampleRate / (double)FFT_SIZE));
                binIdx = Math.Clamp(binIdx, 0, halfSize - 1);
                spectrum[i] = mags[binIdx];
            }

            // Calculate RMS and Peak
            float rms = (float)Math.Sqrt(_fftBuffer.Take(FFT_SIZE).Average(c => c.X * c.X));
            float peak = _fftBuffer.Take(FFT_SIZE).Max(c => Math.Abs((float)c.X));

            // Bass Engine
            float pulse = _bassEngine.Process(mags, _sampleRate);

            OnAudioDataProcessed?.Invoke(spectrum, rms, pulse);
        }

        public void Dispose()
        {
            Stop();
        }

        public static List<MMDevice> GetDevices()
        {
            var enumerator = new MMDeviceEnumerator();
            return enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active).ToList();
        }
    }

    public class PureBassEngine
    {
        public int SampleRate { get; set; }
        private float _prevBass = 0f;
        private float _rollingFloor = 0.002f;
        private float _recentPeak = 0.03f;
        private DateTime _lastBeatTime = DateTime.MinValue;
        private float _smoothVal = 0f;

        public PureBassEngine(int sampleRate)
        {
            SampleRate = sampleRate;
        }

        public float Process(float[] magnitudes, int sampleRate, float decay = 0.82f)
        {
            int n = magnitudes.Length;
            double binFreq = (double)sampleRate / (n * 2);

            float subEnergy = 0;
            float midEnergy = 0;

            for (int i = 0; i < n; i++)
            {
                double freq = i * binFreq;
                if (freq >= 35 && freq <= 110) subEnergy += magnitudes[i];
                if (freq >= 300 && freq <= 3000) midEnergy += magnitudes[i];
            }

            if (midEnergy > subEnergy * 1.5f) subEnergy *= 0.1f;

            _rollingFloor = _rollingFloor * 0.95f + subEnergy * 0.05f;
            _recentPeak = Math.Max(subEnergy, _recentPeak * 0.985f);
            if (_recentPeak < 0.01f) _recentPeak = 0.01f;

            float delta = subEnergy - _prevBass;
            _prevBass = subEnergy;

            var now = DateTime.Now;
            float threshold = Math.Max(0.003f, _rollingFloor * 1.30f);

            if (delta > threshold && subEnergy > 0.004f && (now - _lastBeatTime).TotalSeconds > 0.075)
            {
                _lastBeatTime = now;
                float flash = Math.Clamp(subEnergy / _recentPeak, 0.45f, 1.0f);
                if (flash > _smoothVal) _smoothVal = flash;
            }

            _smoothVal *= decay;
            return Math.Clamp(_smoothVal, 0f, 1f);
        }
    }
}
