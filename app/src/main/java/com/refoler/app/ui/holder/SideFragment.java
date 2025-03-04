package com.refoler.app.ui.holder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.app.Applications;
import com.refoler.app.R;

public abstract class SideFragment extends Fragment {

    protected AppCompatActivity mContext;
    protected SharedPreferences prefs;
    private boolean useParentToolbar = false;

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

    public MaterialToolbar getToolbar(boolean useParentToolbar) {
        this.useParentToolbar = useParentToolbar;
        if(Applications.isLayoutTablet(mContext) || !useParentToolbar) {
            return requireView().findViewById(R.id.toolbar);
        } else {
            return mContext.findViewById(R.id.toolbarParent);
        }
    }

    public void setToolbar(MaterialToolbar toolbar) {
        toolbar.setNavigationOnClickListener((v) -> finishScreen());
    }

    public void setToolbar(MaterialToolbar toolbar, String toolbarTitle) {
        toolbar.setNavigationOnClickListener((v) -> finishScreen());
        toolbar.setTitle(toolbarTitle);
    }

    public void setToolbarBackground(View parent) {
        if (Applications.isLayoutTablet(mContext)) {
            parent.setBackgroundColor(ContextCompat.getColor(mContext, R.color.ui_bg));
            getToolbar(useParentToolbar).setBackgroundColor(ContextCompat.getColor(mContext, R.color.ui_bg));
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