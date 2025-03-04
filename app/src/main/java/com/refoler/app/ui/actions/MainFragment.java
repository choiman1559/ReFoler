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
import com.refoler.app.process.SyncFileListProcess;
import com.refoler.app.process.actions.FSEditSyncJob;
import com.refoler.app.process.service.SyncFileListService;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.ui.actions.side.ChatFragment;
import com.refoler.app.ui.actions.side.DeviceDetailFragment;
import com.refoler.app.ui.actions.side.ReFileFragment;
import com.refoler.app.ui.holder.InfoViewHolder;
import com.refoler.app.ui.holder.OptionActivityHolder;
import com.refoler.app.ui.holder.SideFragmentHolder;
import com.refoler.app.ui.options.InfoActivity;
import com.refoler.app.ui.utils.PrefsCard;
import com.refoler.app.ui.utils.ToastHelper;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainFragment extends Fragment {

    Activity mContext;
    SharedPreferences prefs;
    SharedPreferences cachePrefs;

    LinearLayout deviceListLayout;
    InfoViewHolder emptyLayout;
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
        emptyLayout = new InfoViewHolder(mContext, baseView.findViewById(R.id.deviceEmptyInfo));

        ExtendedFloatingActionButton reloadDeviceListButton = baseView.findViewById(R.id.reloadDeviceListButton);
        ExtendedFloatingActionButton refolerAiActionButton = baseView.findViewById(R.id.refolerAiActionButton);

        reloadDeviceListButton.setOnClickListener((v) -> {
            reloadProgressBar.setVisibility(View.VISIBLE);
            SideFragmentHolder.getInstance().clearFragment(mContext);

            try {
                loadDeviceList(true);
                Applications.initFileActionWorker(mContext);
            } catch (JSONException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        refolerAiActionButton.setOnClickListener((v) -> SideFragmentHolder.getInstance().replaceFragment(mContext, new ChatFragment()));
        View.OnClickListener searchListener = (v) -> mContext.startActivity(new Intent(mContext, SearchActivity.class));

        View searchHint = baseView.findViewById(R.id.searchHint);
        View searchButton = baseView.findViewById(R.id.searchButton);
        searchHint.setOnClickListener(searchListener);
        searchButton.setOnClickListener(searchListener);

        PrefsCard settingsAction = baseView.findViewById(R.id.settingsAction);
        PrefsCard accountAction = baseView.findViewById(R.id.accountAction);
        PrefsCard appInfoAction = baseView.findViewById(R.id.appInfoAction);

        settingsAction.setOnClickListener((v) -> openOptionsActivity(OptionActivityHolder.OPTION_TYPE_SETTINGS));
        accountAction.setOnClickListener((v) -> openOptionsActivity(OptionActivityHolder.OPTION_TYPE_ACCOUNT));
        appInfoAction.setOnClickListener((v) -> startActivity(new Intent(mContext, InfoActivity.class)));

        if(!SyncFileListProcess.getInstance().getCachedResult(mContext).exists()) {
            SyncFileListService.startService(mContext);
        }

        try {
            loadDeviceList(false);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            ToastHelper.show(mContext, "Failed to load device list", ToastHelper.LENGTH_SHORT);
        }
    }

    private void openOptionsActivity(String type) {
        Intent intent = new Intent(mContext, OptionActivityHolder.class);
        intent.putExtra(OptionActivityHolder.INTENT_EXTRA_TYPE, type);
        startActivity(intent);
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
                if (response.gotOk()) {
                    List<String> responseDevices = response.getRefolerPacket().getExtraDataList();
                    JSONArray jsonArray = new JSONArray();

                    for (String deviceString : responseDevices) {
                        jsonArray.put(deviceString);
                        devices.add(DeviceWrapper.parseFrom(deviceString));
                    }

                    cachePrefs.edit().putString(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE, jsonArray.toString()).apply();
                    renderDeviceList();
                } else if(response.getRefolerPacket().getErrorCause().equals(RecordConst.ERROR_DATA_DEVICE_INFO_NOT_AVAILABLE)) {
                    devices.clear();
                    cachePrefs.edit().remove(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE).apply();
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
                emptyLayout.setVisibility(View.VISIBLE);
                DeviceWrapper.registerSelf(mContext, (response) -> {
                    Log.d("DeviceRegistration", "Self device info has been registered!");
                    FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString(PrefsKeyConst.PREFS_KEY_UID, ""));
                });
            } else {
                boolean isSelfNotRegistered = true;
                boolean isOnlySelfDevice = true;

                for (Refoler.Device device : devices) {
                    if (DeviceWrapper.isSelfDevice(mContext, device)) {
                        isSelfNotRegistered = false;
                        continue;
                    }

                    isOnlySelfDevice = false;
                    DeviceItemHolder deviceItemHolder = new DeviceItemHolder(mContext, device);
                    deviceItemMap.put(device, deviceItemHolder);
                    deviceListLayout.addView(deviceItemHolder.createView());
                    deviceListLayout.addView(getMarginView(mContext, 24));
                }

                emptyLayout.setVisibility(isOnlySelfDevice ? View.VISIBLE :View.GONE);
                if(isSelfNotRegistered) {
                    DeviceWrapper.registerSelf(mContext, (response) -> {
                        Log.d("DeviceRegistration", "Self device info has been registered!");
                        FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString(PrefsKeyConst.PREFS_KEY_UID, ""));
                    });
                }
            }
            reloadProgressBar.setVisibility(View.GONE);
        });
    }

    public static Space getMarginView(Context context, int height) {
        Space space = new Space(context);
        LinearLayout.LayoutParams spaceLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
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
            this.itemHolderView.setOnClickListener((v) -> SideFragmentHolder.getInstance().replaceFragment(context, new ReFileFragment(device, null)));
            this.deviceDetail.setOnClickListener((v) -> SideFragmentHolder.getInstance().replaceFragment(context, false, new DeviceDetailFragment().setDevice(device)));
            return itemHolderView;
        }
    }
}
