package com.refoler.app.ui.holder;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public abstract class SideFragment extends Fragment {
    @NonNull
    public String getFragmentId() {
        throw new IllegalStateException("Fragment ID is not defined");
    }
}