package com.refoler.app.utils;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.refoler.app.Applications;

public class PowerUtils {
    private static final String TAG = PowerUtils.class.getSimpleName();
    private static PowerUtils instance = null;
    private static PowerManager.WakeLock wakeLock;

    private PowerUtils() {
    }

    @Nullable
    public static PowerUtils getInstance(Context context) {
        if(!PermissionUtil.getInstance(context).checkPowerPermission()) return null;

        if (instance == null) instance = new PowerUtils();
        if (wakeLock == null) {
            PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }

        return instance;
    }

    public boolean isHeld() {
        return wakeLock != null && wakeLock.isHeld();
    }

    public void acquire() {
        //3 second timeout for battery save
        acquire(3 * 60 * 1000L);
    }

    public void acquire(long timeout) {
        if (!isHeld()) {
            wakeLock.acquire(timeout);
        }
    }

    public void release() {
        if (isHeld()) {
            wakeLock.release();
        }
    }
}