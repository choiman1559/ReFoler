package com.refoler.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class Applications extends Application {

    public static final String PREFS_PACKAGE = "com.refoler.app";
    public static final String PREFS_MAIN_SUFFIX = "preferences";
    public static final String PREFS_DEVICE_LIST_CACHE_SUFFIX = "device_list_Cache";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static boolean isLayoutTablet(Context context) {
        return context.getResources().getBoolean(R.bool.is_tablet);
    }

    public static SharedPreferences getPrefs(Context context, String SUFFIX) {
        return context.getSharedPreferences(String.format("%s_%s", PREFS_PACKAGE, SUFFIX), Context.MODE_PRIVATE);
    }

    public static SharedPreferences getPrefs(Context context) {
        return getPrefs(context, PREFS_MAIN_SUFFIX);
    }
}
