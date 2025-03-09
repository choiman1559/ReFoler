package com.refoler.app.process.actions;

import android.content.Context;
import android.content.SharedPreferences;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.BuildConfig;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.ResponseWrapper;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.SyncFileListProcess;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.process.db.RemoteFolderDoc;
import com.refoler.app.process.service.SyncFileListService;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.utils.IOUtils;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class FSEditSyncJob {
    public record FSEditQuery(String rootKey, String queryPath) implements Comparable<FSEditQuery> {

        private static final String FILE_PATH_SEPARATOR = "/";

        public FSEditQuery {
            if (queryPath.endsWith(FILE_PATH_SEPARATOR)) {
                queryPath = queryPath.substring(0, queryPath.length() - 1);
            }
        }

        public RemoteFolderDoc getRemoteFolderDoc(Context context) {
            SharedPreferences prefs = Applications.getPrefs(context);
            return new RemoteFolderDoc(
                    prefs.getInt(PrefsKeyConst.PREFS_KEY_INDEX_MAX_SIZE, 150),
                    prefs.getBoolean(PrefsKeyConst.PREFS_KEY_INDEX_HIDDEN_FILES, false),
                    new File(queryPath));
        }

        public String[] getPathKeys() {
            String[] keyList = queryPath.split(FILE_PATH_SEPARATOR);
            ArrayList<String> newList = new ArrayList<>();
            for (String s : keyList) {
                if (!s.isEmpty()) {
                    newList.add(s);
                }
            }
            return newList.toArray(new String[0]);
        }

        public String getName() {
            String[] pathKeys = getPathKeys();
            return pathKeys[pathKeys.length - 1];
        }

        public FSEditQuery getParent() {
            String[] pathKeys = getPathKeys();
            StringBuilder parentPath = new StringBuilder();
            for (int i = 0; i < pathKeys.length - 1; i++) {
                parentPath.append(FILE_PATH_SEPARATOR).append(pathKeys[i]);
            }
            return new FSEditQuery(rootKey, parentPath.toString());
        }

        private String encodePath() {
            StringBuilder encodedPath = new StringBuilder();
            encodedPath.append("$");
            encodedPath.append(String.format("['%s']", rootKey));
            String queryPath = this.queryPath.replace(rootKey, "");

            String[] pathKeys = queryPath.split("/");
            for (String pathKey : pathKeys) {
                if (pathKey.isEmpty()) continue;
                encodedPath.append(String.format("['%s']", pathKey));
            }
            return encodedPath.toString();
        }

        public boolean isChildOf(FSEditQuery parent) {
            return (this.queryPath + FILE_PATH_SEPARATOR).startsWith(parent.queryPath + FILE_PATH_SEPARATOR);
        }

        @Override
        public int compareTo(FSEditQuery o) {
            return Integer.compare(getPathKeys().length, o.getPathKeys().length);
        }

        @Override
        public int hashCode() {
            return queryPath.hashCode();
        }
    }

    public interface TaskResultCallBack {
        void onEditSuccess(boolean needsQueryEntireList);

        void onUploadSuccess();

        void onUploadFailed(Throwable throwable);
    }

    private volatile static FSEditSyncJob instance;
    private final ConcurrentSkipListSet<String> queries = new ConcurrentSkipListSet<>();
    private final CopyOnWriteArrayList<TaskResultCallBack> taskResultCallBacks = new CopyOnWriteArrayList<>();
    private final HashSet<FSEditQuery> effectiveQueries = new HashSet<>();
    private final ArrayList<String> rootPathKeyList = new ArrayList<>();
    private JSONObject entireFsListData;

    public static FSEditSyncJob getInstance() {
        if (instance == null) {
            instance = new FSEditSyncJob();
        }
        return instance;
    }

    public void addCallBack(TaskResultCallBack callBack) {
        taskResultCallBacks.add(callBack);
    }

    public void addQuery(String queryPath) {
        queries.add(queryPath);
    }

    private void addEffectiveQuery(FSEditQuery newQuery) {
        for (FSEditQuery query : effectiveQueries) {
            if (query.isChildOf(newQuery)) {
                effectiveQueries.remove(query);
                addEffectiveQuery(newQuery);
                return;
            } else if (newQuery.isChildOf(query)) {
                return;
            }
        }
        effectiveQueries.add(newQuery);
    }

    private String getRootKeyOf(String queryPath) {
        for (String rootKey : rootPathKeyList) {
            if (new FSEditQuery("", queryPath).isChildOf(new FSEditQuery("", rootKey))) {
                return rootKey;
            }
        }
        return "";
    }

    private FSEditQuery getValidQuery(String queryPath) {
        File checkFile = new File(queryPath);
        FSEditQuery query = new FSEditQuery(getRootKeyOf(queryPath), queryPath);

        if(!checkFile.exists() || checkFile.isFile()) {
            return query.getParent();
        } else return query;
    }

    public void execute(Context context) {
        try {
            if (finallyEmit(context)) {
                for (TaskResultCallBack callBack : taskResultCallBacks) {
                    callBack.onEditSuccess(false);
                }

                Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
                requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_POST);
                requestPacket.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
                requestPacket.setExtraData(entireFsListData.toString());

                IOUtils.writeTo(SyncFileListProcess.getInstance().getCachedResult(context), entireFsListData.toString());
                JsonRequest.postRequestPacket(context, RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST, requestPacket, receivedPacket -> {
                    for (TaskResultCallBack callBack : taskResultCallBacks) {
                        callBack.onUploadSuccess();
                    }
                    taskResultCallBacks.clear();
                });
            }
        } catch (Throwable e) {
            if(BuildConfig.DEBUG) {
                e.printStackTrace();
            }

            for (TaskResultCallBack callBack : taskResultCallBacks) {
                callBack.onUploadFailed(e);
            }
            taskResultCallBacks.clear();
        } finally {
            queries.clear();
        }
    }

    private boolean finallyEmit(Context context) throws IOException, JSONException {
        File cachedFile = SyncFileListProcess.getInstance().getCachedResult(context);
        if (!cachedFile.exists()) {
            for (TaskResultCallBack callBack : taskResultCallBacks) {
                callBack.onEditSuccess(true);
            }

            SyncFileListProcess.getInstance().addListener(new SyncFileListProcess.OnSyncFileListProcessListener() {
                @Override
                public void onSyncFileListProcessFinished(ResponseWrapper responseWrapper) {
                    for (TaskResultCallBack callBack : taskResultCallBacks) {
                        callBack.onUploadSuccess();
                    }
                }

                @Override
                public void onSyncFileListProcessFailed(Throwable throwable) {
                    for (TaskResultCallBack callBack : taskResultCallBacks) {
                        callBack.onUploadFailed(throwable);
                    }
                }
            });

            if (!SyncFileListProcess.getInstance().isProcessRunning()) {
                SyncFileListService.startService(context);
            }
            return false;
        }

        effectiveQueries.clear();
        rootPathKeyList.clear();
        entireFsListData = new JSONObject(IOUtils.readFrom(cachedFile));

        for (Iterator<String> keys = entireFsListData.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (RemoteFile.isKeyNotMetadata(key)) {
                rootPathKeyList.add(key);
            }
        }

        for (String queryPath : queries) {
            addEffectiveQuery(getValidQuery(queryPath));
        }

        ArrayList<FSEditQuery> sortedQueries = new ArrayList<>(effectiveQueries);
        Collections.sort(sortedQueries);

        for (FSEditQuery query : sortedQueries) {
            DocumentContext object = JsonPath.parse(new JSONObject(query.getRemoteFolderDoc(context).getLists()).toString());
            DocumentContext documentContext = JsonPath.parse(entireFsListData.toString()).put(query.getParent().encodePath(), query.getName(), object.json());
            entireFsListData = new JSONObject(documentContext.jsonString());
        }
        return true;
    }
}
