package com.refoler.app.ui.actions.side;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.refoler.app.R;
import com.refoler.app.ui.holder.SideFragment;

public class DefaultFragment extends SideFragment {

    Activity mContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_side_default, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View baseView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(baseView, savedInstanceState);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return DefaultFragment.class.getName();
    }
}
