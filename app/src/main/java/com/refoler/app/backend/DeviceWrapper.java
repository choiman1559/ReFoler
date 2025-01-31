package com.refoler.app.backend;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.ui.PrefsKeyConst;

public class DeviceWrapper {

    public static String getDeviceName() {
        return String.format("%s %s", Build.MANUFACTURER, Build.MODEL);
    }

    @SuppressLint("HardwareIds")
    public static String getUniqueID(Context context) {
        SharedPreferences prefs = Applications.getPrefs(context);
        return prefs.getString(PrefsKeyConst.PREFS_KEY_DEVICE_ID, "");
    }

    public static Refoler.Device getSelfDeviceInfo(Context context) {
        Refoler.Device.Builder device = Refoler.Device.newBuilder();
        device.setDeviceName(getDeviceName());
        device.setDeviceId(getUniqueID(context));
        device.setDeviceType(Refoler.DeviceType.DEVICE_TYPE_ANDROID);
        return device.build();
    }
}
