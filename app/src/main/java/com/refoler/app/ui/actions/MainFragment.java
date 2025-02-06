package com.refoler.app.ui.actions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.messaging.FirebaseMessaging;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.service.SyncFileListService;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.ui.utils.ToastHelper;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.ktor.http.HttpStatusCode;

public class MainFragment extends Fragment {

    Activity mContext;
    SharedPreferences prefs;
    SharedPreferences cachePrefs;

    LinearLayout deviceListLayout;
    ProgressBar reloadProgressBar;
    ArrayList<Refoler.Device> devices;
    HashMap<Refoler.Device, DeviceItemHolder> deviceItemMap;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View baseView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(baseView, savedInstanceState);
        prefs = Applications.getPrefs(mContext);
        cachePrefs = Applications.getPrefs(mContext, Applications.PREFS_DEVICE_LIST_CACHE_SUFFIX);

        DrawerLayout drawerLayout = baseView.findViewById(R.id.drawer_layout);
        baseView.findViewById(R.id.sideNavButton).setOnClickListener((v) -> {
            if (drawerLayout.isOpen()) {
                drawerLayout.closeDrawer((GravityCompat.START));
            } else {
                drawerLayout.openDrawer((GravityCompat.START));
            }
        });

        deviceListLayout = baseView.findViewById(R.id.deviceListLayout);
        reloadProgressBar = baseView.findViewById(R.id.reloadProgressBar);

        ExtendedFloatingActionButton reloadDeviceListButton = baseView.findViewById(R.id.reloadDeviceListButton);
        ExtendedFloatingActionButton refolerAiActionButton = baseView.findViewById(R.id.refolerAiActionButton);

        reloadDeviceListButton.setOnClickListener((v) -> {
            reloadProgressBar.setVisibility(View.VISIBLE);
            try {
                loadDeviceList(true);
            } catch (JSONException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        refolerAiActionButton.setOnClickListener((v) -> {

        });

        Button uploadFileNow = baseView.findViewById(R.id.uploadFileNow);
        Button registerDeviceNow = baseView.findViewById(R.id.registerDeviceNow);

        uploadFileNow.setOnClickListener((v) -> {
            SyncFileListService.startService(mContext);
        });

        registerDeviceNow.setOnClickListener((v) -> {
            Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
            requestBuilder.setActionName(RecordConst.SERVICE_ACTION_TYPE_POST);
            requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
            JsonRequest.postRequestPacket(mContext, RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION, requestBuilder, (response) -> {
                Log.d("BackendImplementation", "Device Registered!");
                FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString(PrefsKeyConst.PREFS_KEY_UID, ""));
            });
        });

        try {
            loadDeviceList(false);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            ToastHelper.show(mContext, "Failed to load device list", ToastHelper.LENGTH_SHORT);
        }
    }

    private void loadDeviceList(boolean enforceFetchFromServer) throws JSONException, IOException {
        devices = new ArrayList<>();

        if (!enforceFetchFromServer && !cachePrefs.getAll().isEmpty()) {
            JSONArray jsonArray = new JSONArray(cachePrefs.getString(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE, ""));
            for (int i = 0; i < jsonArray.length(); i++) {
                devices.add(DeviceWrapper.parseFrom(jsonArray.getString(i)));
            }

            renderDeviceList();
            return;
        }

        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        JsonRequest.postRequestPacket(mContext, RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION, requestBuilder, (response) -> {
            try {
                if (response.getStatusCode().equals(HttpStatusCode.Companion.getOK())) {
                    List<String> responseDevices = response.getRefolerPacket().getExtraDataList();
                    JSONArray jsonArray = new JSONArray();

                    for (String deviceString : responseDevices) {
                        jsonArray.put(deviceString);
                        devices.add(DeviceWrapper.parseFrom(deviceString));
                    }

                    cachePrefs.edit().putString(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE, jsonArray.toString()).apply();
                    renderDeviceList();
                } else {
                    ToastHelper.show(mContext, "Failed to load device list", ToastHelper.LENGTH_SHORT);
                    Log.e("BackendImplementation", "Failed to fetch device list: " + response.getStatusCode());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void renderDeviceList() {
        mContext.runOnUiThread(() -> {
            deviceListLayout.removeAllViews();
            deviceItemMap = new HashMap<>();

            if (devices.isEmpty()) {
                //TODO: Show empty info views
            } else for (Refoler.Device device : devices) {
                if (DeviceWrapper.isSelfDevice(mContext, device)) {
                    continue;
                }

                DeviceItemHolder deviceItemHolder = new DeviceItemHolder(mContext, device);
                deviceItemMap.put(device, deviceItemHolder);
                deviceListLayout.addView(deviceItemHolder.createView());
                deviceListLayout.addView(getMarginView());
            }
            reloadProgressBar.setVisibility(View.GONE);
        });
    }

    private Space getMarginView() {
        Space space = new Space(mContext);
        LinearLayout.LayoutParams spaceLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24);
        space.setLayoutParams(spaceLayoutParams);
        return space;
    }

    /**
     * @noinspection FieldCanBeLocal
     */
    private static class DeviceItemHolder {

        public DeviceItemHolder(Context context, Refoler.Device device) {
            this.context = context;
            this.device = device;
            this.itemHolderView = (RelativeLayout) View.inflate(context, R.layout.cardview_pair_device, null);
            this.deviceIcon = this.itemHolderView.findViewById(R.id.deviceIcon);
            this.deviceName = this.itemHolderView.findViewById(R.id.deviceName);
            this.deviceDetail = this.itemHolderView.findViewById(R.id.deviceDetail);
        }

        private final Context context;
        private final Refoler.Device device;
        private final RelativeLayout itemHolderView;
        private final ImageView deviceIcon;
        private final TextView deviceName;
        private final ImageView deviceDetail;

        public RelativeLayout createView() {
            this.deviceIcon.setImageResource(DeviceWrapper.getDeviceFormBitmap(device.getDeviceFormfactor()));
            this.deviceName.setText(device.getDeviceName());

            this.itemHolderView.setOnClickListener((v) -> {
                context.startActivity(DeviceWrapper.attachIntentDevice(new Intent(context, ReFileActivity.class), device));
            });

            this.deviceDetail.setOnClickListener((v) -> {
                // TODO: Add device detail screen
            });

            return itemHolderView;
        }

        public void setFocused(boolean isFocused) {
            // TODO: Implement focused color change for tablet density
        }

        public boolean isFocused() {
            return false;
        }
    }
}
