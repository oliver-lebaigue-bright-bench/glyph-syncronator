package com.better.nothing.music.vizualizer.logic;

import com.better.nothing.music.vizualizer.model.DeviceProfile;

import android.util.Log;
import java.util.Arrays;

/**
 * Handles glyph state computation, breathing effects, and frame rendering.
 */
public class GlyphRenderer {

    private static final int MAX_BRIGHTNESS = 4500;
    private static final float PEAK_FALLOFF = 0.998f;
    private static final float EPSILON = 0.000001f;
    private static final float SILENCE_THRESHOLD = 0.0005f;
    private static final long BREATH_DELAY_MS = 2500L;

    private float mGamma;
    private float mSpectrumGain = 4f;
    private int mMaxBrightness = MAX_BRIGHTNESS;
    private boolean mIdleBreathingEnabled;
    private boolean mStrobeEnabled = false;
    private int mDeviceType;
    private String mIdlePattern = "pulse";

    private float[] mCurrentLightState = new float[0];
    private float[] mZonePeaks = new float[0];
    private float[] mDecayedFrequencyState = new float[0];
    private int mLastHash = Integer.MIN_VALUE;
    private long mSilenceStartTimeMs = 0;
    private long mLastFrameMs = 0;
    private float mBreathingEnvelope = 0f;

    public GlyphRenderer(float gamma, boolean idleBreathingEnabled, int deviceType) {
        this.mGamma = gamma;
        this.mIdleBreathingEnabled = idleBreathingEnabled;
        this.mDeviceType = deviceType;
    }

    public void setIdleBreathingEnabled(boolean enabled) {
        mIdleBreathingEnabled = enabled;
        if (!enabled) {
            mSilenceStartTimeMs = 0;
        }
    }

    public void setIdlePattern(String pattern) {
        this.mIdlePattern = pattern;
    }

    public void setStrobeEnabled(boolean enabled) {
        mStrobeEnabled = enabled;
    }

    public void setGamma(float gamma) {
        mGamma = gamma;
        mLastHash = Integer.MIN_VALUE; // Force redraw with new gamma
    }

    public void setSpectrumGain(float gain) {
        mSpectrumGain = gain;
        mLastHash = Integer.MIN_VALUE;
    }

    public float getSpectrumGain() {
        return mSpectrumGain;
    }

    public void setMaxBrightness(int brightness) {
        mMaxBrightness = Math.max(0, Math.min(MAX_BRIGHTNESS, brightness));
        mLastHash = Integer.MIN_VALUE;
    }

    public void setDeviceType(int deviceType) {
        this.mDeviceType = deviceType;
        mLastHash = Integer.MIN_VALUE;
    }

    public void resetState(AudioProcessor.VisualizerConfig config) {
        if (config == null) {
            mCurrentLightState = new float[0];
            mZonePeaks = new float[0];
            mDecayedFrequencyState = new float[0];
        } else {
            mCurrentLightState = new float[config.zones.length];
            mZonePeaks = new float[config.zones.length];
            Arrays.fill(mZonePeaks, EPSILON);
            mDecayedFrequencyState = new float[config.uniqueRanges.length];
        }
        mLastHash = Integer.MIN_VALUE;
        mSilenceStartTimeMs = 0;
        mLastFrameMs = 0;
        mBreathingEnvelope = 0f;
    }

    public int[] processFrame(float[] uniqueMagnitudes, AudioProcessor.VisualizerConfig config, long nowMs) {
        if (config == null || uniqueMagnitudes == null) {
            return new int[0];
        }

        try {
            int hardwareCount = DeviceProfile.getLedCount(mDeviceType);
            int zoneCount = Math.max(config.zones.length, hardwareCount);

            ensureStateArrays(zoneCount, config.uniqueRanges.length);

            float[] nextLightState = computeNextLightState(uniqueMagnitudes, config, zoneCount);
            if (nextLightState == null) return new int[0];

            // Apply gamma to music state FIRST, before idle breathing, so breathing bypasses gamma
            for (int i = 0; i < nextLightState.length; i++) {
                nextLightState[i] = applyGamma(nextLightState[i]);
            }

            try {
                applyIdleBreathing(nextLightState, uniqueMagnitudes, nowMs);
            } catch (Exception e) {
                Log.e("GlyphRenderer", "Error applying idle breathing", e);
            }

            if (mStrobeEnabled) {
                boolean phase = (nowMs / 10) % 2 == 0;
                for (int i = 0; i < nextLightState.length; i++) {
                    if ((i % 2 == 0) != phase) {
                        nextLightState[i] = 0;
                    }
                }
            }

            System.arraycopy(nextLightState, 0, mCurrentLightState, 0, Math.min(nextLightState.length, mCurrentLightState.length));

            int[] frameColors = buildFrameColors(nextLightState, zoneCount);
            int frameHash = Arrays.hashCode(frameColors);
            if (frameHash == mLastHash) {
                return null; // No change
            }

            mLastHash = frameHash;
            return frameColors;
        } catch (Exception e) {
            Log.e("GlyphRenderer", "processFrame failed", e);
            return new int[0];
        }
    }

    public float[] getCurrentLightState() {
        return mCurrentLightState != null ? Arrays.copyOf(mCurrentLightState, mCurrentLightState.length) : new float[0];
    }

    private float[] computeNextLightState(float[] uniqueMagnitudes, AudioProcessor.VisualizerConfig config, int totalCount) {
        try {
            float[] decayedFrequencyState = computeDecayedFrequencyState(uniqueMagnitudes, config);
            float[] nextState = new float[totalCount];

            int zonesToProcess = Math.min(config.zones.length, mZonePeaks.length);
            for (int i = 0; i < zonesToProcess; i++) {
                float rawZonePeak = 0f;
                int[] overlappingRanges = config.zoneToRangeIndices[i];
                if (overlappingRanges != null) {
                    for (int rangeIndex : overlappingRanges) {
                        if (rangeIndex >= 0 && rangeIndex < decayedFrequencyState.length) {
                            rawZonePeak = Math.max(rawZonePeak, decayedFrequencyState[rangeIndex]);
                        }
                    }
                }

                mZonePeaks[i] = Math.max(rawZonePeak, mZonePeaks[i] * PEAK_FALLOFF);
                if (mZonePeaks[i] < EPSILON) {
                    mZonePeaks[i] = EPSILON;
                }

                float normalized = rawZonePeak / mZonePeaks[i];
                float shaped = normalized * normalized;
                float mapped = applyPercentSlice(shaped, config.zones[i]);
                nextState[i] = mapped < EPSILON ? 0f : mapped;
            }

            return nextState;
        } catch (Exception e) {
            Log.e("GlyphRenderer", "computeNextLightState failed", e);
            return new float[totalCount];
        }
    }

    private float[] computeDecayedFrequencyState(float[] uniqueMagnitudes, AudioProcessor.VisualizerConfig config) {
        float[] next = new float[mDecayedFrequencyState.length];
        for (int i = 0; i < next.length; i++) {
            float current = (i < uniqueMagnitudes.length ? uniqueMagnitudes[i] : 0f) * mSpectrumGain;
            float risen = Math.max(mDecayedFrequencyState[i], current);
            float decayed = (config.decay * risen) + ((1f - config.decay) * current);
            next[i] = decayed < EPSILON ? 0f : decayed;
        }
        System.arraycopy(next, 0, mDecayedFrequencyState, 0, next.length);
        return next;
    }

    private void applyIdleBreathing(float[] nextState, float[] uniqueMagnitudes, long nowMs) {
        long deltaMs = (mLastFrameMs > 0) ? (nowMs - mLastFrameMs) : 16;
        mLastFrameMs = nowMs;

        boolean isSilent = true;
        for (float peak : uniqueMagnitudes) {
            if (peak * mSpectrumGain > SILENCE_THRESHOLD) {
                isSilent = false;
                break;
            }
        }

        if (isSilent) {
            if (mSilenceStartTimeMs == 0) {
                mSilenceStartTimeMs = nowMs;
            }
        } else {
            mSilenceStartTimeMs = 0;
        }

        long silenceDuration = (mSilenceStartTimeMs > 0) ? (nowMs - mSilenceStartTimeMs) : 0;
        boolean shouldBreathe = mIdleBreathingEnabled && (silenceDuration > BREATH_DELAY_MS);
        float targetEnvelope = shouldBreathe ? .4f : 0.0f;

        // Smooth envelope transition
        if (mBreathingEnvelope < targetEnvelope) {
            // Fade in slowly (~1.5s)
            mBreathingEnvelope += (float) deltaMs / 2500f;
            if (mBreathingEnvelope > targetEnvelope) mBreathingEnvelope = targetEnvelope;
        } else if (mBreathingEnvelope > targetEnvelope) {
            // Fade out faster (~300ms) for responsiveness
            mBreathingEnvelope -= (float) deltaMs / 300f;
            if (mBreathingEnvelope < targetEnvelope) mBreathingEnvelope = targetEnvelope;
        }

        if (mBreathingEnvelope > 0.01f) {
            int zoneCount = nextState.length;
            for (int i = 0; i < zoneCount; i++) {
                float intensity = switch (mIdlePattern) {
                    case "wave" -> {
                        double timeProg = (double) (nowMs % 2000L) / 2000L;
                        float phaseShift = (float) i / zoneCount;
                        yield (float) (0.1 + 0.5 * Math.sin(2.0 * Math.PI * (timeProg - phaseShift)));
                    }
                    case "scanner" -> {
                        double timeProg = (double) (nowMs % 2500L) / 2500L;
                        float scannerPos = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * timeProg));
                        float ledPos = (float) i / zoneCount;
                        float dist = Math.abs(ledPos - scannerPos);
                        yield (float) Math.exp(-dist * dist * 40.0);
                    }
                    case "zebra" -> {
                        double timeProg = (double) (nowMs % 2500L) / 2500L;
                        boolean even = (i % 2 == 0);
                        double sinVal = Math.sin(2.0 * Math.PI * timeProg);
                        yield even ? (float) (0.5 + 0.5 * sinVal) : (float) (0.5 - 0.5 * sinVal);
                    }
                    case "heartbeat" -> {
                        double t = (double) (nowMs % 2000L) / 2000L;
                        float val = 0f;
                        if (t < 0.12) val = (float) Math.sin(Math.PI * t / 0.12);
                        else if (t > 0.22 && t < 0.34) val = (float) Math.sin(Math.PI * (t - 0.22) / 0.12) * 0.7f;
                        yield val;
                    }
                    case "orbit" -> {
                        double t = (double) (nowMs % 3000L) / 3000L;
                        float pos1 = (float) t;
                        float pos2 = 1.0f - (float) t;
                        float ledPos = (float) i / zoneCount;
                        float d1 = Math.abs(ledPos - pos1);
                        float d2 = Math.abs(ledPos - pos2);
                        yield (float) (Math.exp(-d1 * d1 * 60.0) + Math.exp(-d2 * d2 * 60.0));
                    }
                    case "rain" -> {
                        double t = (double) (nowMs % 2000L) / 2000L;
                        float ledPos = (float) i / zoneCount;
                        float dropPos = (float) t;
                        float dist = Math.abs(ledPos - dropPos);
                        yield (float) Math.exp(-dist * dist * 100.0);
                    }
                    case "pulse" -> {
                        double timeProg = (double) (nowMs % 3000L) / 3000L;
                        float phaseShift = (float) i * 0.02f;
                        yield (float) (0.2 + 0.5 * Math.sin(2.0 * Math.PI * (timeProg + phaseShift) - Math.PI / 2.0));
                    }
                    default -> {
                        double timeProg = (double) (nowMs % 3000L) / 3000L;
                        float phaseShift = (float) i * 0.02f;
                        yield (float) (0.2 + 0.5 * Math.sin(2.0 * Math.PI * (timeProg + phaseShift) - Math.PI / 2.0));
                    }
                };

                float breathVal = (0.02f + intensity * 0.48f) * mBreathingEnvelope;
                if (nextState[i] < breathVal) {
                    nextState[i] = breathVal;
                }
            }
        }
    }

    private int[] buildFrameColors(float[] normalizedLightState, int expectedLength) {
        int[] frameColors = new int[expectedLength];
        int count = Math.min(normalizedLightState.length, expectedLength);
        float multiplier = (float) mMaxBrightness;
        for (int i = 0; i < count; i++) {
            // Gamma is already applied to music state in processFrame, and breathing bypasses it
            int val = Math.round(normalizedLightState[i] * multiplier);
            frameColors[i] = Math.max(0, Math.min(4095, val)); // Hard clamp to 12-bit max (4095)
        }
        return frameColors;
    }

    private float applyGamma(float normalizedValue) {
        if (normalizedValue <= 0f) {
            return 0f;
        }
        return (float) Math.pow(normalizedValue, mGamma);
    }

    private void ensureStateArrays(int zoneCount, int uniqueRangeCount) {
        if (mCurrentLightState.length == zoneCount
                && mZonePeaks.length == zoneCount
                && mDecayedFrequencyState.length == uniqueRangeCount) {
            return;
        }

        mCurrentLightState = new float[zoneCount];
        mZonePeaks = new float[zoneCount];
        Arrays.fill(mZonePeaks, EPSILON);
        mDecayedFrequencyState = new float[uniqueRangeCount];
        mLastHash = Integer.MIN_VALUE;
    }

    private static float applyPercentSlice(float normalizedValue, AudioProcessor.ZoneSpec zone) {
        if (!zone.hasPercentSlice()) {
            return normalizedValue;
        }

        float low = Math.min(zone.lowPercent, zone.highPercent);
        float high = Math.max(zone.lowPercent, zone.highPercent);
        float percent = normalizedValue * 100f;

        if (percent <= low) {
            return 0f;
        }
        if (percent >= high || high == low) {
            return 1f;
        }
        return (percent - low) / (high - low);
    }
}
