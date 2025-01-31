package com.refoler.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.refoler.app.ui.MainActivity;
import com.refoler.app.ui.PrefsKeyConst;

import java.util.UUID;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);

        SharedPreferences prefs = Applications.getPrefs(this);
        if (!prefs.contains(PrefsKeyConst.PREFS_KEY_DEVICE_ID)) {
            prefs.edit().putString(PrefsKeyConst.PREFS_KEY_DEVICE_ID, UUID.randomUUID().toString()).apply();
        }

        //TODO: TestUid, Change on release
        prefs.edit().putString(PrefsKeyConst.PREFS_KEY_UID, "test_uid01").apply();

        startActivity(new Intent(this, MainActivity.class));
    }
}
