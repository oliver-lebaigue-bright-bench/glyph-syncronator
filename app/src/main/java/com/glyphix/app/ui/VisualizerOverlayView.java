package com.glyphix.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class VisualizerOverlayView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    private static final int NUM_BARS = 32;
    private final float[] mSmoothedMagnitudesTop = new float[NUM_BARS];
    private final float[] mSmoothedMagnitudesBottom = new float[NUM_BARS];
    private final float[] mTargetMagnitudes = new float[NUM_BARS];
    
    private long mLastDrawTime = 0;
    private int mColor = Color.WHITE;
    
    private boolean mTopEnabled = true;
    private boolean mBottomEnabled = false;
    private float mTopSensitivity = 1.0f;
    private float mBottomSensitivity = 1.0f;
    private int mTopHeightPx = 0;
    private int mBottomHeightPx = 0;

    public VisualizerOverlayView(Context context) {
        super(context);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
    }

    public void setColor(int color) {
        this.mColor = color;
        mPaint.setColor(color);
        invalidate();
    }

    public void setTopEnabled(boolean enabled) {
        this.mTopEnabled = enabled;
        invalidate();
    }

    public void setBottomEnabled(boolean enabled) {
        this.mBottomEnabled = enabled;
        invalidate();
    }

    public void setTopSensitivity(float sensitivity) {
        this.mTopSensitivity = sensitivity;
    }

    public void setBottomSensitivity(float sensitivity) {
        this.mBottomSensitivity = sensitivity;
    }

    public void setHeights(int topPx, int bottomPx) {
        this.mTopHeightPx = topPx;
        this.mBottomHeightPx = bottomPx;
        invalidate();
    }

    public void updateMagnitudes(float[] magnitudes, int sampleRate) {
        if (magnitudes == null || magnitudes.length == 0) return;
        this.mMagnitudes = magnitudes;
        
        int binsPerBar = magnitudes.length / NUM_BARS;
        for (int i = 0; i < NUM_BARS; i++) {
            float maxInBar = 0f;
            for (int j = i * binsPerBar; j < (i + 1) * binsPerBar && j < magnitudes.length; j++) {
                if (magnitudes[j] > maxInBar) maxInBar = magnitudes[j];
            }
            // Switch back to MAX to restore punchiness; underlying data is now smoothed anyway
            mTargetMagnitudes[i] = maxInBar;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMagnitudes == null) return;

        long now = android.os.SystemClock.elapsedRealtime();
        float dt = (mLastDrawTime == 0) ? 16.6f : (now - mLastDrawTime);
        mLastDrawTime = now;

        // Visual smoothing constants (liquid feel)
        float interpolation = Math.min(1.0f, dt / 40.0f); // 40ms window for smoothing
        float decay = (float) Math.pow(0.88f, dt / 16.6f);

        int width = getWidth();
        float barWidth = (float) width / NUM_BARS;
        float spacing = 1.0f;
        float cornerRadius = 2f;

        float baselineY = mTopEnabled ? mTopHeightPx : 0;

        for (int i = 0; i < NUM_BARS; i++) {
            float target = mTargetMagnitudes[i] * 1.5f;
            
            if (mTopEnabled) {
                float val = target * mTopSensitivity;
                if (val > mSmoothedMagnitudesTop[i]) {
                    mSmoothedMagnitudesTop[i] = mSmoothedMagnitudesTop[i] + (val - mSmoothedMagnitudesTop[i]) * interpolation;
                } else {
                    mSmoothedMagnitudesTop[i] *= decay;
                }
                
                float barHeightTop = mSmoothedMagnitudesTop[i] * mTopHeightPx;
                if (barHeightTop > mTopHeightPx) barHeightTop = mTopHeightPx;
                if (barHeightTop < 1.0f) barHeightTop = 1.0f;

                float left = i * barWidth + spacing;
                float right = (i + 1) * barWidth - spacing;
                canvas.drawRoundRect(left, baselineY - barHeightTop, right, baselineY, cornerRadius, cornerRadius, mPaint);
            }

            if (mBottomEnabled) {
                float val = target * mBottomSensitivity;
                if (val > mSmoothedMagnitudesBottom[i]) {
                    mSmoothedMagnitudesBottom[i] = mSmoothedMagnitudesBottom[i] + (val - mSmoothedMagnitudesBottom[i]) * interpolation;
                } else {
                    mSmoothedMagnitudesBottom[i] *= decay;
                }
                
                float barHeightBottom = mSmoothedMagnitudesBottom[i] * mBottomHeightPx;
                if (barHeightBottom > mBottomHeightPx) barHeightBottom = mBottomHeightPx;
                if (barHeightBottom < 1.0f) barHeightBottom = 1.0f;

                float left = i * barWidth + spacing;
                float right = (i + 1) * barWidth - spacing;
                canvas.drawRoundRect(left, baselineY, right, baselineY + barHeightBottom, cornerRadius, cornerRadius, mPaint);
            }
        }
        
        // Keep animating if we have active bars
        postInvalidateOnAnimation();
    }
}
