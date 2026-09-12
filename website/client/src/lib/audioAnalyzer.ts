/**
 * FFT-based audio analyzer for real-time frequency domain visualization.
 * Processes frequency data into musical bands and provides interpolated spectrogram.
 */

export interface FrequencyBands {
  bass: number;        // 20-90 Hz
  kick: number;        // 90-200 Hz
  lowMid: number;      // 200-500 Hz
  mid: number;         // 500-2kHz
  highMid: number;     // 2-5kHz
  treble: number;      // 5-12kHz
  air: number;         // 12-20kHz
  overall: number;     // 20-20kHz
}

export interface SpectrumFrame {
  bins: Uint8Array;
  timestamp: number;
  bands: FrequencyBands;
}

export class AudioAnalyzer {
  private analyser: AnalyserNode;
  private fftSize: number;
  private frequencyData: Uint8Array;
  private smoothedData: Float32Array;
  private smoothingFactor: number;
  private nyquistHz: number;
  private peakHistory: Float32Array;
  private peakDecay: number;

  constructor(analyser: AnalyserNode, fftSize: number = 1024, smoothing: number = 0.7) {
    this.analyser = analyser;
    this.analyser.fftSize = fftSize;
    this.fftSize = fftSize;
    this.frequencyData = new Uint8Array(analyser.frequencyBinCount);
    this.smoothedData = new Float32Array(analyser.frequencyBinCount);
    this.smoothingFactor = smoothing;
    this.nyquistHz = 48000 / 2; // Assume 48kHz sample rate
    this.peakHistory = new Float32Array(analyser.frequencyBinCount);
    this.peakDecay = 0.992;

    // Initialize smoothed data
    this.smoothedData.fill(0);
  }

  /**
   * Get the frequency (in Hz) corresponding to a bin index.
   */
  private binToHz(bin: number): number {
    return (bin / this.fftSize) * this.nyquistHz * 2;
  }

  /**
   * Get the bin index for a given frequency in Hz.
   */
  private hzToBin(hz: number): number {
    return Math.round((hz / (this.nyquistHz * 2)) * this.fftSize);
  }

  /**
   * Extract the average energy in a frequency range.
   */
  private getBandEnergy(startHz: number, endHz: number, data: Uint8Array): number {
    const startBin = Math.max(1, this.hzToBin(startHz));
    const endBin = Math.min(data.length - 1, this.hzToBin(endHz));
    let sum = 0;
    const binCount = Math.max(1, endBin - startBin);

    for (let i = startBin; i < endBin; i++) {
      sum += data[i] || 0;
    }

    return (sum / binCount / 255) ** 1.2; // Apply gamma for perceptual uniformity
  }

  /**
   * Analyze the current audio and return frequency bands + raw spectrum.
   */
  analyze(): SpectrumFrame {
    this.analyser.getByteFrequencyData(this.frequencyData);

    // Apply smoothing to the raw data
    for (let i = 0; i < this.frequencyData.length; i++) {
      const normalized = this.frequencyData[i] / 255;
      this.smoothedData[i] = this.smoothedData[i] * this.smoothingFactor + normalized * (1 - this.smoothingFactor);

      // Update peak history with decay
      const current = this.smoothedData[i];
      this.peakHistory[i] = Math.max(current, this.peakHistory[i] * this.peakDecay);
    }

    // Extract frequency bands
    const bands: FrequencyBands = {
      bass: this.getBandEnergy(20, 90, this.frequencyData),
      kick: this.getBandEnergy(90, 200, this.frequencyData),
      lowMid: this.getBandEnergy(200, 500, this.frequencyData),
      mid: this.getBandEnergy(500, 2000, this.frequencyData),
      highMid: this.getBandEnergy(2000, 5000, this.frequencyData),
      treble: this.getBandEnergy(5000, 12000, this.frequencyData),
      air: this.getBandEnergy(12000, 20000, this.frequencyData),
      overall: 0,
    };

    // Calculate overall energy
    bands.overall = Math.sqrt(
      bands.bass * bands.bass +
      bands.kick * bands.kick +
      bands.mid * bands.mid +
      bands.treble * bands.treble
    ) / 2;

    return {
      bins: new Uint8Array(this.smoothedData.map(v => Math.min(255, v * 255))),
      timestamp: performance.now(),
      bands,
    };
  }

  /**
   * Get peak-normalized spectrum (normalized by recent peaks).
   */
  getNormalizedSpectrum(): Float32Array {
    const normalized = new Float32Array(this.smoothedData.length);
    for (let i = 0; i < normalized.length; i++) {
      const peak = this.peakHistory[i] || 0.001;
      normalized[i] = Math.min(1, this.smoothedData[i] / peak);
    }
    return normalized;
  }

  /**
   * Get downsampled spectrum for visualization (more performant).
   */
  getDownsampledSpectrum(targetBins: number = 64): Float32Array {
    const downsampled = new Float32Array(targetBins);
    const binSize = this.smoothedData.length / targetBins;

    for (let i = 0; i < targetBins; i++) {
      const startBin = Math.floor(i * binSize);
      const endBin = Math.floor((i + 1) * binSize);
      let sum = 0;

      for (let j = startBin; j < endBin; j++) {
        sum += this.smoothedData[j] || 0;
      }

      downsampled[i] = sum / (endBin - startBin);
    }

    return downsampled;
  }

  /**
   * Reset all history (call when audio stops).
   */
  reset(): void {
    this.smoothedData.fill(0);
    this.peakHistory.fill(0);
  }
}
