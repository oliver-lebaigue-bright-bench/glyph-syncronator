package com.glyphix.app.service;

import com.glyphix.app.R;
import com.glyphix.app.util.PermissionTrampolineActivity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class VisualizerTileService extends TileService {
    @Override public void onStartListening() { super.onStartListening(); refresh(); }
    @Override public void onClick() {
        super.onClick();
        if (AudioCaptureService.isRunning()) {
            Intent stopIntent = AudioCaptureService.createStopIntent(this);
            startService(stopIntent);
            refresh(false);
        } else {
            refresh(true); // Immediate UI feedback
            unlockAndRun(() -> {
                Intent i = new Intent(this, PermissionTrampolineActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        3,
                        i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                startActivityAndCollapse(pendingIntent);
            });
        }
    }
    private void refresh() { refresh(AudioCaptureService.isRunning()); }
    private void refresh(boolean on) {
        Tile t=getQsTile(); if(t==null) return;
        t.setState(on?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        t.setLabel("Glyphix");
        t.setSubtitle(on?"Running":"Glyphix");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_monochrome));
        t.updateTile();
    }
}
