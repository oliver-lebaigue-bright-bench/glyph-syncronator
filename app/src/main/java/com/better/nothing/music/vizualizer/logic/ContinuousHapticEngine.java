package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Drop-in haptic controller for a visualizer-style amplitude stream.
 * <p>
 * Manifest:
 * <uses-permission android:name="android.permission.VIBRATE" />
 * <p>
 * Notes:
 * - minSdk 14 compatible.
 * - Uses VibratorManager on API 31+.
 * - Keeps a repeating waveform alive and only resubmits when the amplitude changes.
 * - Android 16 envelope APIs exist, but they do not give you a mutable "live motor";
 *   repeating waveform is the practical continuous solution across your API range.
 */
public final class ContinuousHapticEngine {

    private static final String TAG = "ContinuousHapticEngine";

    // Tune this to match your input cadence.
    private static final int HAPTIC_DURATION_MS = 100;

    // Don't spam the vibrator service faster than this.
    private static final long MIN_RESUBMIT_INTERVAL_MS = 20L;


    private static final float SPECTRUM_GAIN = 12.0f;
    private static final int MAX_AMPLITUDE = 255;

    private final Vibrator vibrator;

    private float hapticMultiplier = 1.0f;
    private float hapticAudioGain = 1.0f;
    private float hapticGamma = 2.0f;

    private int lastAmplitude = -1;
    private long lastSubmitMs = 0L;

    public ContinuousHapticEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                this.vibrator = vm.getDefaultVibrator();
            } else {
                this.vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } else {
            this.vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    public synchronized void setHapticMultiplier(float multiplier) {
        this.hapticMultiplier = Math.max(0.3f, Math.min(1.5f, multiplier));
    }

    public synchronized void setHapticAudioGain(float gain) {
        this.hapticAudioGain = Math.max(0.1f, gain);
    }

    public synchronized void setHapticGamma(float gamma) {
        this.hapticGamma = Math.max(0.1f, gamma);
    }

    public synchronized void performHapticFeedback(float rawPeak, @Nullable AudioProcessor.VisualizerConfig config) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        // Apply raw gain, user multiplier, and audio gain.
        float current = Math.max(0f, rawPeak) * SPECTRUM_GAIN * hapticMultiplier * hapticAudioGain;

        // Apply Gamma shaping
        float shaped = (float) Math.pow(current, hapticGamma);

        int nextAmplitude = clampInt(Math.round(shaped * MAX_AMPLITUDE), 0, MAX_AMPLITUDE);
        if (shaped >= 0.95f) nextAmplitude = MAX_AMPLITUDE;

        if (nextAmplitude <= 0) {
            if (lastAmplitude != 0) {
                submitOneShot(0);
            }
            return;
        }

        final long now = SystemClock.elapsedRealtime();

        // Only resubmit if change is significant AND enough time has passed
        boolean cooldownOver = (now - lastSubmitMs) >= MIN_RESUBMIT_INTERVAL_MS;

        if (!cooldownOver) {
            return;
        }

        submitOneShot(nextAmplitude);
    }

    public synchronized void stopHaptics() {
        if (vibrator != null) {
            vibrator.cancel();
        }
        lastAmplitude = -1;
        lastSubmitMs = 0L;
    }

    private void submitOneShot(int amplitude) {
        try {
            // One-shot lasts 100ms, ensuring overlap with the next frame (~16ms-30ms away)
            VibrationEffect effect = VibrationEffect.createOneShot(HAPTIC_DURATION_MS, amplitude);
            vibrator.vibrate(effect);

            lastAmplitude = amplitude;
            lastSubmitMs = SystemClock.elapsedRealtime();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to submit one-shot haptic", e);
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
