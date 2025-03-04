package com.refoler.app.ui.holder;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.refoler.app.R;

public class SideHolderActivity extends AppCompatActivity {

    public static final String SIDE_HOLDER_OVERRIDE_TOOLBAR = "SIDE_HOLDER_OVERRIDE_TOOLBAR";
    public static OnSideHolderActivityCreateListener onSideHolderActivityCreateListener;
    public interface OnSideHolderActivityCreateListener {
        void onCreate(AppCompatActivity activity);
        void onDestroy(AppCompatActivity activity);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_side_holder);

        if(getIntent().getBooleanExtra(SIDE_HOLDER_OVERRIDE_TOOLBAR, false)) {
            findViewById(R.id.app_bar_layout).setVisibility(View.GONE);
        }

        if(onSideHolderActivityCreateListener != null) {
            onSideHolderActivityCreateListener.onCreate(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(onSideHolderActivityCreateListener != null) {
            onSideHolderActivityCreateListener.onDestroy(this);
        }
    }
}