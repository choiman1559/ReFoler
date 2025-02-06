package com.refoler.app.process.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.protobuf.util.JsonFormat;

import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.BuildConfig;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.ResponseWrapper;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.EndPointConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.SyncFileListProcess;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class FirebaseMessageService extends FirebaseMessagingService {

    SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Applications.getPrefs(this);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        try {
            Map<String, String> rawDataMap = message.getData();
            if (rawDataMap.containsKey(EndPointConst.KEY_EXTRA_DATA)) {
                processMessage(new JSONObject(rawDataMap.get(EndPointConst.KEY_EXTRA_DATA)));
            }
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processMessage(JSONObject message) throws JSONException, IOException {
        if(BuildConfig.DEBUG) {
            Log.d("FirebaseMessageService", message.toString());
        }

        final String actionType = message.getString(DirectActionConst.KEY_ACTION_TYPE);
        switch (message.getString(DirectActionConst.KEY_ACTION_SIDE)) {
            case DirectActionConst.ACTION_SIDE_REQUESTER:
                processRequester(actionType, ResponseWrapper.parseRequestPacket(message.getString(DirectActionConst.KEY_REQUEST_PACKET)));
                break;

            case DirectActionConst.ACTION_SIDE_RESPONSER:
                processResponser(actionType, ResponseWrapper.parseResponsePacket(message.getString(DirectActionConst.KEY_RESPONSE_PACKET)));
                break;
        }
    }

    private void processResponser(String actionType, Refoler.ResponsePacket responsePacket) {
        if (isTargeted(this, responsePacket.getDeviceList())) switch (actionType) {
            case RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST -> // Download file list
                    ReFileCache.getInstance(this).postDirectRequestListeners(responsePacket);
            case RecordConst.SERVICE_TYPE_TRANSFER_FILE -> {
                // TODO: Download file blob
            }
        }
    }

    private void processRequester(String actionType, Refoler.RequestPacket requestPacket) {
        if (isTargeted(this, requestPacket.getDeviceList())) switch (actionType) {
            case RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST -> {
                // Upload file list
                Refoler.ResponsePacket.Builder responseBuilder = Refoler.ResponsePacket.newBuilder();
                responseBuilder.addDevice(requestPacket.getDevice(1));
                responseBuilder.addDevice(requestPacket.getDevice(0));

                SyncFileListProcess.getInstance().addListener(new SyncFileListProcess.OnSyncFileListProcessListener() {
                    @Override
                    public void onSyncFileListProcessFinished(ResponseWrapper responseWrapper) {
                        responseBuilder.setStatus(responseWrapper.getRefolerPacket().getStatus());
                        responseBuilder.setErrorCause(responseWrapper.getRefolerPacket().getErrorCause());

                        try {
                            postResponseMessage(FirebaseMessageService.this, RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST, responseBuilder.build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onSyncFileListProcessFailed(Throwable throwable) {
                        responseBuilder.setStatus(PacketConst.STATUS_ERROR);
                        responseBuilder.setErrorCause(throwable.toString());

                        try {
                            postResponseMessage(FirebaseMessageService.this, RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST, responseBuilder.build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                SyncFileListService.startService(this);
            }

            case RecordConst.SERVICE_TYPE_TRANSFER_FILE -> {
                // TODO: Upload file blob
            }
        }
    }

    protected boolean isTargeted(Context context, List<Refoler.Device> devices) {
        return !devices.isEmpty() && devices.size() >= 2 &&
                !DeviceWrapper.isSelfDevice(context, devices.get(0)) &&
                DeviceWrapper.isSelfDevice(context, devices.get(1));
    }

    public static void postResponseMessage(Context context, String actionType, Refoler.ResponsePacket responsePacket) throws JSONException, IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(DirectActionConst.KEY_ACTION_TYPE, actionType);
        jsonObject.put(DirectActionConst.KEY_ACTION_SIDE, DirectActionConst.ACTION_SIDE_RESPONSER);
        jsonObject.put(DirectActionConst.KEY_RESPONSE_PACKET, JsonFormat.printer().print(responsePacket));
        postFcmMessage(context, jsonObject);
    }

    public static void postRequestMessage(Context context, String actionType, Refoler.RequestPacket requestPacket) throws JSONException, IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(DirectActionConst.KEY_ACTION_TYPE, actionType);
        jsonObject.put(DirectActionConst.KEY_ACTION_SIDE, DirectActionConst.ACTION_SIDE_REQUESTER);
        jsonObject.put(DirectActionConst.KEY_REQUEST_PACKET, JsonFormat.printer().print(requestPacket));
        postFcmMessage(context, jsonObject);
    }

    public static void postFcmMessage(Context mContext, JSONObject jsonObject) {
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
        requestBuilder.setExtraData(jsonObject.toString());
        JsonRequest.postRequestPacket(mContext, EndPointConst.SERVICE_TYPE_FCM_POST, requestBuilder, (response) ->
                Log.d("FcmPostResult", String.format("status: %s, message: %s",
                        response.getStatusCode().toString(),
                        response.getRefolerPacket().getExtraData(0))));
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (!prefs.getString(PrefsKeyConst.PREFS_KEY_UID, "").isEmpty())
            FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString(PrefsKeyConst.PREFS_KEY_UID, ""));
    }
}
