package com.refoler.app.ui.actions.side;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.refoler.Refoler;
import com.refoler.app.ui.holder.SideFragment;

public class DeviceDetailFragment extends SideFragment {

    Refoler.Device device;

    public DeviceDetailFragment() {
        // Default constructor for fragment manager
    }

    public DeviceDetailFragment setDevice(Refoler.Device device) {
        this.device = device;
        return this;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("device", device);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            device = (Refoler.Device) savedInstanceState.getSerializable("device");
        }
    }
}
