package com.refoler.app.process.service;

import android.content.Context;

import com.refoler.Refoler;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.process.db.RemoteFile;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class FileAction {

    protected static FileAction fileAction;
    protected ConcurrentHashMap<String, ReFileCache.DirectRequestListener> requestListenerMap;

    public static FileAction getInstance() {
        if(fileAction == null) {
            fileAction = new FileAction();
            fileAction.requestListenerMap = new ConcurrentHashMap<>();
        }
        return fileAction;
    }

    private void addRequestListener(String key, ReFileCache.DirectRequestListener listener) {
        requestListenerMap.put(key, listener);
    }

    public void requestFileHash(Context context, Refoler.Device device, RemoteFile remoteFile, ReFileCache.DirectRequestListener listener) throws JSONException, IOException {
        String requestTicket = String.format(Locale.getDefault(),"%d:%d", remoteFile.getPath().hashCode(), listener.hashCode());
        addRequestListener(requestTicket, listener);

        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
        requestBuilder.addDevice(device);
        requestBuilder.setDataDecryptKey(requestTicket);
        requestBuilder.setExtraData(printListRemoteFile(DirectActionConst.FILE_ACTION_HASH, remoteFile));

        FirebaseMessageService.postRequestMessage(context, DirectActionConst.SERVICE_TYPE_FILE_ACTION, requestBuilder.build());
    }

    protected String printListRemoteFile(String fileActionType, RemoteFile... remoteFiles) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(fileActionType);

        for(RemoteFile remoteFile : remoteFiles) {
            jsonArray.put(remoteFile.getPath());
        }
        return jsonArray.toString();
    }
}
