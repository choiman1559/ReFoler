package com.refoler.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.refoler.Refoler;
import com.refoler.app.backend.DeviceWrapper;

public class Applications extends Application {

    private static Applications instance = new Applications();
    public static final String PREFS_PACKAGE = "com.refoler.app";
    public static final String PREFS_MAIN_SUFFIX = "preferences";

    public Refoler.Device selfDeviceInfo;

    @Override
    public void onCreate() {
        super.onCreate();
        Applications.instance = this;
        selfDeviceInfo = DeviceWrapper.getSelfDeviceInfo();
    }

    public static Application getApplicationInstance() {
        if(instance == null) {
            throw new IllegalStateException("Application is not initialized");
        }
        return instance;
    }

    public static SharedPreferences getPrefs() {
        return getApplicationInstance()
                .getSharedPreferences(String.format("%s_%s", PREFS_PACKAGE, PREFS_MAIN_SUFFIX), Context.MODE_PRIVATE);
    }
}
