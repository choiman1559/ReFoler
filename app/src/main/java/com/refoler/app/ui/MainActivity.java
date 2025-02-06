package com.refoler.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.kieronquinn.monetcompat.app.MonetCompatActivity;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.refoler.app.R;
import com.refoler.app.ui.actions.MainFragment;

public class MainActivity extends MonetCompatActivity {

    private MonetCompat monet;

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        MonetCompat.setup(context);
        monet = MonetCompat.getInstance();
        monet.updateMonetColors();
        return super.onCreateView(name, context, attrs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        monet = null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        MainFragment mainFragment = new MainFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contentFrameMain, mainFragment)
                .commit();
    }
}
