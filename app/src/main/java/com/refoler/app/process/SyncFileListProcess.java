package com.refoler.app.process;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.backend.ResponseWrapper;
import com.refoler.app.process.db.ReFileConst;
import com.refoler.app.process.db.RemoteFolderDoc;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class SyncFileListProcess {

    private static SyncFileListProcess instance;
    private ArrayList<OnSyncFileListProcessListener> listProcessListeners;
    private Thread workerThread;

    public interface OnSyncFileListProcessListener {
        void onSyncFileListProcessFinished(ResponseWrapper responseWrapper);
        void onSyncFileListProcessFailed(Throwable throwable);
    }

    public static SyncFileListProcess getInstance() {
        if (instance == null) {
            instance = new SyncFileListProcess();
            instance.listProcessListeners = new ArrayList<>();
        }

        return instance;
    }

    public void addListener(OnSyncFileListProcessListener listProcessListener) {
        this.listProcessListeners.add(listProcessListener);
    }

    public void removeListener(OnSyncFileListProcessListener listProcessListener) {
        this.listProcessListeners.remove(listProcessListener);
    }

    public boolean isProcessRunning() {
        return workerThread != null && workerThread.isAlive();
    }

    public void interruptWorker() {
        if (isProcessRunning()) {
            workerThread.interrupt();
        }
    }

    public void startSyncProcess(Context context) {
        if (isProcessRunning()) {
            throw new IllegalStateException("Process is already running");
        }

        SharedPreferences prefs = Applications.getPrefs(context);
        workerThread = new Thread(() -> {
            try {
                runFileListWork(context, null,
                        prefs.getInt(PrefsKeyConst.PREFS_KEY_INDEX_MAX_SIZE, 150),
                        prefs.getBoolean(PrefsKeyConst.PREFS_KEY_INDEX_HIDDEN_FILES, false));
            } catch (Throwable throwable) {
                if(!listProcessListeners.isEmpty()) {
                    for (OnSyncFileListProcessListener listener : listProcessListeners) {
                        listener.onSyncFileListProcessFailed(throwable);
                    }
                    listProcessListeners.clear();
                }
            }
        });
        workerThread.start();
    }

    public void runFileListWork(Context context, @Nullable String basePath, int indexMaximumSize, boolean indexHiddenFiles) {
        Map<String, Object> drives = new HashMap<>();
        File[] allExternalFilesDirs;

        if (basePath == null) {
            allExternalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);
        } else {
            allExternalFilesDirs = new File(basePath).listFiles();
        }

        if (allExternalFilesDirs != null) for (File filesDir : allExternalFilesDirs) {
            if (filesDir != null) {
                int nameSubPos = filesDir.getAbsolutePath().lastIndexOf("/Android/data");
                if (nameSubPos > 0) {
                    String filesDirName = filesDir.getAbsolutePath().substring(0, nameSubPos);
                    RemoteFolderDoc remoteFolderDoc = new RemoteFolderDoc(indexMaximumSize, indexHiddenFiles, new File(filesDirName));
                    drives.put(filesDirName, remoteFolderDoc.getLists());
                }
            }
        }
        else if (!listProcessListeners.isEmpty()) {
            for (OnSyncFileListProcessListener listener : listProcessListeners) {
                listener.onSyncFileListProcessFailed(new FileNotFoundException(String.format("Base file folder is not available: %s", basePath)));
            }
            listProcessListeners.clear();
            return;
        }

        drives.put(ReFileConst.DATA_TYPE_LAST_MODIFIED, Calendar.getInstance().getTimeInMillis());
        final String finalFileListString = new JSONObject(drives).toString();

        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_POST);
        requestPacket.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
        requestPacket.setExtraData(finalFileListString);

        JsonRequest.postRequestPacket(context, RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST, requestPacket, receivedPacket -> {
            if (!listProcessListeners.isEmpty()) {
                for (OnSyncFileListProcessListener listener : listProcessListeners) {
                    listener.onSyncFileListProcessFinished(receivedPacket);
                }
                listProcessListeners.clear();
            }
        });
    }
}
