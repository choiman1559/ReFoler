package com.refoler.app.backend;

import static android.content.Context.UI_MODE_SERVICE;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;


public class DeviceWrapper {

    @Nullable
    public static Intent attachIntentDevice(Intent intent, Refoler.Device device) {
        try {
            intent.putExtra(PrefsKeyConst.PREFS_KEY_DEVICE_ID, toString(device));
            return intent;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static Refoler.Device detachIntentDevice(Intent intent) {
        try {
            return parseFrom(intent.getStringExtra(PrefsKeyConst.PREFS_KEY_DEVICE_ID));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String toString(Refoler.Device device) throws IOException {
        return JsonFormat.printer().print(device);
    }

    public static Refoler.Device parseFrom(String rawData) throws IOException {
        Refoler.Device.Builder builder = Refoler.Device.newBuilder();
        JsonFormat.parser().merge(rawData, builder);
        return builder.build();
    }

    public static String getSelfDeviceName() {
        return String.format("%s %s", Build.MANUFACTURER, Build.MODEL);
    }

    @SuppressLint("HardwareIds")
    public static String getSelfUniqueID(Context context) {
        SharedPreferences prefs = Applications.getPrefs(context);
        return prefs.getString(PrefsKeyConst.PREFS_KEY_DEVICE_ID, "");
    }

    public static Refoler.Device getSelfDeviceInfo(Context context) {
        Refoler.Device.Builder device = Refoler.Device.newBuilder();
        device.setDeviceName(getSelfDeviceName());
        device.setDeviceId(getSelfUniqueID(context));
        device.setDeviceFormfactor(getThisDeviceForm(context));
        device.setDeviceType(Refoler.DeviceType.DEVICE_TYPE_ANDROID);
        return device.build();
    }

    public static boolean isSelfDevice(Context context, Refoler.Device device) {
        return equals(getSelfDeviceInfo(context), device);
    }

    public static boolean equals(Object o1, Object o2) {
        if (o1 instanceof Refoler.Device device1 && o2 instanceof Refoler.Device device2) {
            return device1.getDeviceId().equals(device2.getDeviceId());
        } else return false;
    }

    public static Refoler.DeviceFormfactor getThisDeviceForm(Context context) {
        Refoler.DeviceFormfactor deviceFormfactor;
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);

        switch (uiModeManager.getCurrentModeType()) {
            case Configuration.UI_MODE_TYPE_TELEVISION:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_TV;
                break;

            case Configuration.UI_MODE_TYPE_WATCH:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_WATCH;
                break;

            case Configuration.UI_MODE_TYPE_DESK:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_DESKTOP;
                break;

            case Configuration.UI_MODE_TYPE_VR_HEADSET:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_GOGGLES;
                break;

            case Configuration.UI_MODE_TYPE_CAR:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_AUTOMOBILE;
                break;

            case Configuration.UI_MODE_TYPE_APPLIANCE:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_EMBEDDED;
                break;

            case Configuration.UI_MODE_TYPE_NORMAL:
                if (Applications.isLayoutTablet(context)) {
                    deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_TABLET;
                } else {
                    deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_SMARTPHONE;
                }
                break;

            case Configuration.UI_MODE_TYPE_UNDEFINED:
            default:
                deviceFormfactor = Refoler.DeviceFormfactor.DEVICE_FORM_UNKNOWN;
                break;
        }
        return deviceFormfactor;
    }

    public static int getDeviceFormBitmap(@Nullable Refoler.DeviceFormfactor formfactor) {
        if (formfactor == null) {
            return com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_developer_board_24_regular;
        }

        return switch (formfactor) {
            case Refoler.DeviceFormfactor.DEVICE_FORM_SMARTPHONE ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_phone_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_TABLET ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_tablet_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_TV ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_tv_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_DESKTOP ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_desktop_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_LAPTOP ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_laptop_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_WATCH ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_smartwatch_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_EMBEDDED ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_iot_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_GOGGLES ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_headset_vr_24_regular;
            case Refoler.DeviceFormfactor.DEVICE_FORM_AUTOMOBILE ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_vehicle_car_24_regular;
            default ->
                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_developer_board_24_regular;
        };
    }

    public static List<Refoler.Device> getAllRegisteredDeviceList(Context context) {
        ArrayList<Refoler.Device> devices = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(Applications.getPrefs(context, Applications.PREFS_DEVICE_LIST_CACHE_SUFFIX)
                    .getString(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE, ""));
            for (int i = 0; i < jsonArray.length(); i++) {
                devices.add(DeviceWrapper.parseFrom(jsonArray.getString(i)));
            }
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }
        return devices;
    }

    public static void registerSelf(Context mContext, JsonRequest.OnReceivedCompleteListener listener) {
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.setActionName(RecordConst.SERVICE_ACTION_TYPE_POST);
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
        JsonRequest.postRequestPacket(mContext, RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION, requestBuilder, listener);
    }
}
