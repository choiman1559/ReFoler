package com.refoler.app.process.actions;

import android.content.Context;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.process.service.FirebaseMessageService;

import org.json.JSONException;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FileActionRequester {

    private static FileActionRequester requesterInstance;

    public static FileActionRequester getInstance() {
        if (requesterInstance == null) {
            requesterInstance = new FileActionRequester();
        }
        return requesterInstance;
    }

    private final ConcurrentHashMap<String, @NonNull ActionCallback> callbacks = new ConcurrentHashMap<>();
    public interface ActionCallback {
        void onFinish(Refoler.Device device, FileAction.ActionResponse response);
    }

    public void responseAction(Refoler.Device device, FileAction.ActionResponse response) {
        if(callbacks.containsKey(response.getChallengeCode())) {
            Objects.requireNonNull(callbacks.get(response.getChallengeCode())).onFinish(device, response);
            callbacks.remove(response.getChallengeCode());
        }
    }

    public static void setChallengeCode(Refoler.Device device, FileAction.ActionRequest.Builder action) {
        if(Objects.requireNonNullElse(action.getChallengeCode(), "").isBlank()) {
            action.setChallengeCode(String.format(Locale.getDefault(),
                    "%s_%s_%d", device.getDeviceId(), action.getActionType().getDescriptorForType().getName(), System.currentTimeMillis()));
        }
    }

    public void requestAction(Context context, Refoler.Device device, FileAction.ActionRequest.Builder action, ActionCallback callback) throws JSONException, IOException {
        setChallengeCode(device, action);
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
        requestBuilder.addDevice(device);
        requestBuilder.setFileAction(action);

        callbacks.put(action.getChallengeCode(), callback);
        FirebaseMessageService.postRequestMessage(context, DirectActionConst.SERVICE_TYPE_FILE_ACTION, requestBuilder.build());
    }
}
