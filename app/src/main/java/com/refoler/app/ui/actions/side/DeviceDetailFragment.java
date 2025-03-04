package com.refoler.app.ui.actions.side;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.ui.holder.SideFragment;
import com.refoler.app.ui.utils.PrefsCard;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public class DeviceDetailFragment extends SideFragment {

    SharedPreferences devicePrefs;
    Refoler.Device device;

    ImageView deviceIcon;
    TextView deviceName;
    Button forgetButton;
    Button queryButton;
    PrefsCard lastQueriedData;
    PrefsCard denyFileAction;

    public DeviceDetailFragment() {
        // Default constructor for fragment manager
    }

    public DeviceDetailFragment setDevice(Refoler.Device device) {
        this.device = device;
        return this;
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return String.format("%s, %s", this.getClass().getName(), device.getDeviceId());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("device", device);
    }

    @Override
    public OnBackPressedCallback getOnBackDispatcher() {
        return new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishScreen();
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            device = (Refoler.Device) savedInstanceState.getSerializable("device");
        }

        MaterialToolbar toolbar = getToolbar(true);
        setToolbar(toolbar, mContext.getString(R.string.device_detail_title));
        devicePrefs = Applications.getPrefs(mContext, Applications.PREFS_DEVICE_LIST_CACHE_SUFFIX);

        deviceName = view.findViewById(R.id.deviceName);
        deviceIcon = view.findViewById(R.id.deviceIcon);
        forgetButton = view.findViewById(R.id.forgetButton);
        queryButton = view.findViewById(R.id.queryButton);
        lastQueriedData = view.findViewById(R.id.lastQueriedData);
        denyFileAction = view.findViewById(R.id.denyFileAction);

        deviceName.setText(device.getDeviceName());
        deviceIcon.setImageDrawable(ContextCompat.getDrawable(mContext, DeviceWrapper.getDeviceFormBitmap(device.getDeviceFormfactor())));

        if (device.hasLastQueriedTime()) {
            lastQueriedData.setDescription(
                    new SimpleDateFormat(mContext.getString(R.string.device_detail_last_queried_desc), Locale.getDefault())
                            .format(new Date(device.getLastQueriedTime())));
        } else {
            lastQueriedData.setDescription(mContext.getString(R.string.device_detail_last_queried_desc_none));
        }

        denyFileAction.setOnClickListener(v -> denyFileAction.setSwitchChecked(!denyFileAction.isChecked()));
        denyFileAction.setSwitchChecked(isDeviceInSet(PrefsKeyConst.PREFS_KEY_FILE_ACTION_DENY, device.getDeviceId()));
        denyFileAction.setOnCheckedChangedListener((v, changed) -> {
            denyFileAction.setSwitchChecked(changed);
            setDeviceInSet(PrefsKeyConst.PREFS_KEY_FILE_ACTION_DENY, device.getDeviceId(), changed);
        });

        forgetButton.setOnClickListener(v -> {
            //Todo: Remove from device list
            finishScreen();
        });

        queryButton.setOnClickListener(v -> {
            try {
                queryButton.setEnabled(false);
                ReFileCache.getInstance(mContext).requestDeviceFileList(device, responsePacket -> mContext.runOnUiThread(() -> queryButton.setEnabled(true)));
            } catch (IOException | JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean isDeviceInSet(String key, String value) {
        HashSet<String> set = new HashSet<>(devicePrefs.getStringSet(key, new HashSet<>()));
        return set.contains(value);
    }

    public void setDeviceInSet(String key, String value, boolean isInclude) {
        HashSet<String> set = new HashSet<>(devicePrefs.getStringSet(key, new HashSet<>()));
        if (isInclude) {
            set.add(value);
        } else set.remove(value);
        devicePrefs.edit().putStringSet(key, set).apply();
    }
}
