package com.refoler.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class Applications extends Application {

    public static final String PREFS_PACKAGE = "com.refoler.app";
    public static final String PREFS_MAIN_SUFFIX = "preferences";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(String.format("%s_%s", PREFS_PACKAGE, PREFS_MAIN_SUFFIX), Context.MODE_PRIVATE);
    }
}
