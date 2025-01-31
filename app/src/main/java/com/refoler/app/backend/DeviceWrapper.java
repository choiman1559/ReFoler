package com.refoler.app.backend;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;

import com.refoler.Refoler;
import com.refoler.app.Applications;

public class DeviceWrapper {

    public static String getDeviceName() {
        return String.format("%s %s", Build.MANUFACTURER, Build.MODEL);
    }

    @SuppressLint("HardwareIds")
    public static String getUniqueID() {
        SharedPreferences prefs = Applications.getPrefs();
        return prefs.getString("Unique_DeviceID", "");
    }

    public static Refoler.Device getSelfDeviceInfo() {
        Refoler.Device.Builder device = Refoler.Device.newBuilder();
        device.setDeviceName(getDeviceName());
        device.setDeviceId(getUniqueID());
        device.setDeviceType(Refoler.DeviceType.DEVICE_TYPE_ANDROID);
        return device.build();
    }
}
