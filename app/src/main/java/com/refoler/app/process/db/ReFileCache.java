package com.refoler.app.process.db;

import android.annotation.SuppressLint;
import android.content.Context;

import com.refoler.Refoler;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.service.FirebaseMessageService;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.utils.IOUtils;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

public class ReFileCache {

    public interface ReFileFetchListener {
        void onReceive(@Nullable RemoteFile remoteFile);

        void onEmpty();

        void onError(Exception e);
    }

    public interface DirectRequestListener {
        void onReceive(Refoler.ResponsePacket responsePacket);
    }

    @SuppressLint("StaticFieldLeak")
    private static ReFileCache reFileCacheInstance;
    private final ConcurrentHashMap<String, RemoteFile> memoryCache;
    private final CopyOnWriteArrayList<DirectRequestListener> directRequestListeners;
    private final File fileListDataDir;
    private Context mContext;

    /**
     * @noinspection ResultOfMethodCallIgnored
     */
    private ReFileCache(Context context) {
        fileListDataDir = new File(context.getCacheDir(), PrefsKeyConst.DIR_FILE_LIST_CACHE);
        fileListDataDir.mkdirs();

        memoryCache = new ConcurrentHashMap<>();
        directRequestListeners = new CopyOnWriteArrayList<>();
    }

    public static ReFileCache getInstance(Context context) {
        if (reFileCacheInstance == null) {
            reFileCacheInstance = new ReFileCache(context);
        }

        reFileCacheInstance.mContext = context;
        return reFileCacheInstance;
    }

    public void clearAllCaches() {
        memoryCache.clear();
        for(File file : Objects.requireNonNull(fileListDataDir.listFiles())) {
            if(file.isFile() && !file.delete()) {
                break;
            }
        }
    }

    public void postDirectRequestListeners(Refoler.ResponsePacket responsePacket) {
        if (directRequestListeners != null && !directRequestListeners.isEmpty()) {
            for (DirectRequestListener directRequestListener : directRequestListeners) {
                directRequestListener.onReceive(responsePacket);
            }
            directRequestListeners.clear();
        }
    }

    public void fetchList(boolean renewCache, Refoler.Device device, ReFileFetchListener listener) {
        try {
            final File targetFile = new File(fileListDataDir, device.getDeviceId() + ".json");
            if (device.hasLastQueriedTime() && !renewCache) {
                if (memoryCache.containsKey(device.getDeviceId())) {
                    listener.onReceive(memoryCache.get(device.getDeviceId()));
                } else if (targetFile.exists()) {
                    listener.onReceive(new RemoteFile(new JSONObject(IOUtils.readFrom(targetFile))));
                } else {
                    fetchList(true, device, listener);
                }
            } else {
                Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
                requestBuilder.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
                requestBuilder.addDevice(device);
                JsonRequest.postRequestPacket(mContext, RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST, requestBuilder, (response) -> {
                    try {
                        if (response.gotOk()) {
                            String rawData = response.getRefolerPacket().getExtraData(0);
                            RemoteFile remoteFile = new RemoteFile(new JSONObject(rawData));

                            listener.onReceive(remoteFile);
                            memoryCache.put(device.getDeviceId(), remoteFile);
                            IOUtils.writeTo(targetFile, rawData);
                        } else if (response.getRefolerPacket().getErrorCause().equals(RecordConst.ERROR_DATA_DEVICE_FILE_INFO_NOT_FOUND)) {
                            listener.onEmpty();
                        } else {
                            listener.onError(new Exception("Failed to fetch file list: " + response.getRefolerPacket().getErrorCause()));
                        }
                    } catch (Exception e) {
                        listener.onError(e);
                    }
                });
            }
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    public void requestDeviceFileList(Refoler.Device device, DirectRequestListener listener) throws IOException, JSONException {
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
        requestBuilder.addDevice(device);

        directRequestListeners.add(listener);
        FirebaseMessageService.postRequestMessage(mContext, RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST, requestBuilder.build());
    }
}
