package com.refoler.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;

import com.kieronquinn.monetcompat.app.MonetCompatActivity;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.ui.actions.MainFragment;
import com.refoler.app.ui.holder.SideFragmentHolder;

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

        if(Applications.isLayoutTablet(this)) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.ui_bg_toolbar));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ui_bg_toolbar));
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        SideFragmentHolder.initInstance(this);
        MainFragment mainFragment = new MainFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contentFrameMain, mainFragment)
                .commit();
    }
}
