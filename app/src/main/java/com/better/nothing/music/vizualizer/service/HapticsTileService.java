package com.better.nothing.music.vizualizer.service;

import com.better.nothing.music.vizualizer.R;
import com.better.nothing.music.vizualizer.util.PermissionTrampolineActivity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HapticsTileService extends TileService {
    @Override public void onStartListening() { super.onStartListening(); refresh(); }
    @Override public void onClick() {
        super.onClick();
        boolean running = AudioCaptureService.isRunning();
        boolean currentHaptics = AudioCaptureService.isHapticEnabledGlobal(this);
        boolean newState = !currentHaptics;
        
        // Always update the preference immediately for consistency
        getSharedPreferences("viz_prefs", MODE_PRIVATE)
                .edit().putBoolean("haptic_motor_enabled", newState).apply();
        
        if (!running) {
            refresh(newState);
            if (newState) {
                unlockAndRun(() -> {
                    Intent i = new Intent(this, PermissionTrampolineActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            this,
                            4,
                            i,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    startActivityAndCollapse(pendingIntent);
                });
            }
        } else {
            // Service is running, toggle haptics via intent
            Intent intent = new Intent(this, AudioCaptureService.class);
            intent.setAction(AudioCaptureService.ACTION_TOGGLE_HAPTICS);
            startService(intent);
            refresh(newState); // Immediate UI feedback
        }
    }
    
    public static void requestRefresh(Context context) {
        TileService.requestListeningState(context, new ComponentName(context, HapticsTileService.class));
    }
    
    private void refresh() {
        refresh(AudioCaptureService.isHapticEnabledGlobal(this));
    }

    private void refresh(boolean hapticsEnabled) {
        boolean vizRunning = AudioCaptureService.isRunning();
        
        Tile t = getQsTile(); if (t == null) return;
        
        // Active if haptics are enabled in preferences
        t.setState(hapticsEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        
        t.setLabel("Haptic viz");
        if (!vizRunning) {
            t.setSubtitle(hapticsEnabled ? "Enabled" : "Disabled");
        } else {
            t.setSubtitle(hapticsEnabled ? "Active" : "Ready");
        }
        
        // Use the vibration icon
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_vibration));
        t.updateTile();
    }
}
