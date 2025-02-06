package com.refoler.app.ui.holder;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.refoler.app.R;

public class SideHolderActivity extends AppCompatActivity {

    public static OnSideHolderActivityCreateListener onSideHolderActivityCreateListener;
    public interface OnSideHolderActivityCreateListener {
        void onCreate(AppCompatActivity activity);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_side_holder);

        if(onSideHolderActivityCreateListener != null) {
            onSideHolderActivityCreateListener.onCreate(this);
        }
    }
}