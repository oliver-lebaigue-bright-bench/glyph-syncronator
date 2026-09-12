import { useEffect, useRef, useState } from "react";
import { AudioAnalyzer, type FrequencyBands } from "@/lib/audioAnalyzer";

const SILENT_BANDS: FrequencyBands = {
  bass: 0,
  kick: 0,
  lowMid: 0,
  mid: 0,
  highMid: 0,
  treble: 0,
  air: 0,
  overall: 0,
};

interface UseAudioVisualizerProps {
  audioContext: AudioContext | null;
  analyser: AnalyserNode | null;
  isPlaying: boolean;
}

/**
 * Hook for real-time audio visualization with FFT analysis.
 * Returns frequency bands and raw spectrum data for visualizing the matrix background.
 */
export function useAudioVisualizer({
  audioContext,
  analyser,
  isPlaying,
}: UseAudioVisualizerProps) {
  const [bands, setBands] = useState<FrequencyBands>(SILENT_BANDS);
  const analyzerRef = useRef<AudioAnalyzer | null>(null);
  const animationRef = useRef(0);

  // Initialize the audio analyzer when analyser node is available
  useEffect(() => {
    if (!analyser) return;
    
    analyzerRef.current = new AudioAnalyzer(analyser, 1024, 0.75);
    
    return () => {
      analyzerRef.current?.reset();
    };
  }, [analyser]);

  // Animation loop for continuous visualization
  useEffect(() => {
    if (!isPlaying || !analyzerRef.current) {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
        animationRef.current = 0;
      }
      setBands(SILENT_BANDS);
      return;
    }

    const animate = () => {
      const frame = analyzerRef.current?.analyze();
      if (frame) {
        setBands(frame.bands);
      }
      animationRef.current = requestAnimationFrame(animate);
    };

    animationRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [isPlaying]);

  return {
    bands,
    analyzer: analyzerRef.current,
  };
}

/**
 * Hook to paint FFT-driven spectrum visualization onto a matrix grid.
 * Maps frequency bands to matrix cell brightness and colors.
 */
export function useMatrixVisualization(
  gridRef: React.RefObject<HTMLDivElement>,
  fieldRef: React.RefObject<HTMLDivElement>,
  bands: FrequencyBands,
  isPlaying: boolean
) {
  useEffect(() => {
    const grid = gridRef.current;
    const field = fieldRef.current;
    if (!grid || !field) return;

    const children = grid.children;
    field.classList.toggle("music-playing", isPlaying && bands.overall > 0.025);

    // Iterate through grid cells and update based on frequency bands
    for (let i = 0; i < children.length; i++) {
      const cell = children[i] as HTMLElement;
      const column = i % 64; // Adjust based on grid columns
      const row = Math.floor(i / 64); // Adjust based on grid rows

      // Create a spectral wave pattern using mid/high frequencies
      const waveInfluence = Math.sin(column * 0.22 + bands.highMid * 14) * (2 + bands.mid * 9);
      const waveY = 22 + waveInfluence + (bands.lowMid - 0.5) * 6;
      const waveDistance = Math.abs(row - waveY);

      let intensity = 0;
      let lime = false;

      // Wave pattern (most responsive to mid frequencies)
      if (waveDistance < 0.55 && bands.mid > 0.06) {
        intensity = 3;
      } else if (waveDistance < 1.3 && bands.mid > 0.035) {
        intensity = 2;
      } else if (waveDistance < 2.05 && bands.overall > 0.05) {
        intensity = 1;
      }

      // Vertical bar pattern for bass/kick
      const barCenters = [11, 25, 39, 53];
      const barLevels = [bands.bass, bands.lowMid, bands.mid, bands.highMid];

      for (let j = 0; j < barCenters.length; j++) {
        const barHeight = Math.round(barLevels[j] * 18);
        const horizontalDist = Math.abs(column - barCenters[j]);
        const verticalDist = Math.abs(row - 37);

        if (horizontalDist < 1.25 && verticalDist < barHeight) {
          const newIntensity = verticalDist < barHeight * 0.3 ? 3 : verticalDist < barHeight * 0.67 ? 2 : 1;
          intensity = Math.max(intensity, newIntensity);
        }
      }

      // Radial bass ring
      const ringRadius = Math.hypot(column - 47, row - 15);
      const bassRing = Math.abs(ringRadius - (3 + bands.bass * 15));

      if (bassRing < 0.5 && bands.bass > 0.07) {
        intensity = Math.max(intensity, 3);
      } else if (bassRing < 1.15 && bands.bass > 0.04) {
        intensity = Math.max(intensity, 1);
      }

      // Determine lime highlight based on intense peak activity
      lime = intensity === 3 && (bands.bass > 0.24 || (bands.highMid > 0.35 && column % 3 === 0));

      // Update cell classes and data attributes
      cell.classList.toggle("music-lit", intensity > 0);
      cell.classList.toggle("music-lime", lime);

      if (intensity > 0) {
        cell.dataset.intensity = String(intensity);
      } else {
        delete cell.dataset.intensity;
      }
    }
  }, [gridRef, fieldRef, bands, isPlaying]);
}
