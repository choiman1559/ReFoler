package com.refoler.app.ui.holder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.app.Applications;
import com.refoler.app.R;

import java.util.Objects;

public abstract class SideFragment extends Fragment {

    public AppCompatActivity mContext;
    public SharedPreferences prefs;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            mContext = (AppCompatActivity) context;
            prefs = Applications.getPrefs(mContext);
        } else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Override
    public AppCompatActivity getContext() {
        return mContext;
    }

    @NonNull
    public String getFragmentId() {
        throw new IllegalStateException("Fragment ID is not defined");
    }

    public void finishScreen() {
        if (getContext() == null) throw new IllegalStateException("Context is null!");
        SideFragmentHolder.getInstance().popFragment(getContext());
    }

    public void setToolbar(MaterialToolbar toolbar) {
        setToolbar(toolbar, true);
    }

    public void setToolbar(MaterialToolbar toolbar, boolean setBackground) {
        toolbar.setNavigationOnClickListener((v) -> finishScreen());
        if (setBackground && Applications.isLayoutTablet(mContext)) {
            Drawable drawable = AppCompatResources.getDrawable(mContext, R.drawable.round_corner_toolbar);
            Objects.requireNonNull(drawable).setColorFilter(mContext.getColor(R.color.ui_bg), PorterDuff.Mode.SRC_ATOP);
            toolbar.setBackground(drawable);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getOnBackDispatcher() != null) {
            mContext.getOnBackPressedDispatcher().addCallback(mContext, getOnBackDispatcher());
        }
    }

    @Nullable
    public OnBackPressedCallback getOnBackDispatcher() {
        return null;
    }
}