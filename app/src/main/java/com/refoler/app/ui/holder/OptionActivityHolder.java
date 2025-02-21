package com.refoler.app.ui.holder;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.refoler.app.R;
import com.refoler.app.ui.options.AccountPreferences;
import com.refoler.app.ui.options.SettingsPreferences;

public class OptionActivityHolder extends AppCompatActivity {

    public final static String INTENT_EXTRA_TYPE = "extra_fragment_type";
    public final static String OPTION_TYPE_SETTINGS = "option_settings";
    public final static String OPTION_TYPE_ACCOUNT = "option_account";

    private static String title = "Default Message";
    private static String lastType = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment fragment;
        fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);

        if (savedInstanceState == null || fragment == null) {
            String type = getIntent().getStringExtra(INTENT_EXTRA_TYPE);
            if (type != null) lastType = type;

            switch (lastType) {
                case OPTION_TYPE_SETTINGS:
                    fragment = new SettingsPreferences();
                    title = getString(R.string.options_toolbar_title_settings);
                    break;

                case OPTION_TYPE_ACCOUNT:
                    fragment = new AccountPreferences();
                    title = getString(R.string.options_toolbar_title_account);
                    break;

                default:
                    fragment = null;
                    title = getString(R.string.app_name);
                    break;
            }
        }

        setContentView(R.layout.activity_options);
        DynamicColors.applyToActivityIfAvailable(this);

        Bundle bundle = new Bundle(0);
        if (fragment != null) {
            fragment.setArguments(bundle);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}
