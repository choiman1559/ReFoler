package com.refoler.app.utils;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

public class PermissionUtil {

    protected Context context;

    public static PermissionUtil getInstance(Context context) {
        PermissionUtil permissionUtil = new PermissionUtil();
        permissionUtil.context = context;
        return permissionUtil;
    }

    public boolean checkNotificationPermission() {
        return ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled();
    }

    public boolean checkPowerPermission() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public boolean checkFilePermission() {
        boolean isPermissionGranted = false;
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            isPermissionGranted = true;
        } else if (Build.VERSION.SDK_INT > 28 &&
                (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            isPermissionGranted = true;
        } else if (Build.VERSION.SDK_INT <= 28) {
            isPermissionGranted = true;
        }

        return isPermissionGranted;
    }


}
