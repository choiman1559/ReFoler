package com.refoler.app.process.db;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class RemoteFile implements Comparable<RemoteFile>, Serializable {

    private static final HashSet<String> metaKeys = new HashSet<>();
    private static final String FILE_PATH_SEPARATOR = "/";

    long size;
    long lastModified;
    boolean isFile;
    boolean isIndexSkipped;
    int permission;
    String path;
    RemoteFile parent;
    transient List<RemoteFile> list;

    public RemoteFile(JSONObject jsonObject) throws JSONException {
        path = "";
        list = new ArrayList<>();

        for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
            String key = it.next();
            Object obj = jsonObject.get(key);

            if (key.equals(ReFileConst.DATA_TYPE_LAST_MODIFIED)) {
                lastModified = (Long) obj;
            } else {
                list.add(new RemoteFile(this, (JSONObject) obj, key, false));
            }
        }
    }

    protected RemoteFile(@Nullable RemoteFile parent, JSONObject jsonObject, String basePath, boolean singleObj) throws JSONException {
        path = basePath;
        list = new ArrayList<>();
        this.parent = parent;

        if (jsonObject.has(ReFileConst.DATA_TYPE_PERMISSION)) {
            permission = jsonObject.getInt(ReFileConst.DATA_TYPE_PERMISSION);
        } else {
            permission = ReFileConst.PERMISSION_UNKNOWN;
        }

        if (jsonObject.getBoolean(ReFileConst.DATA_TYPE_IS_FILE)) {
            isFile = true;
            size = jsonObject.getLong(ReFileConst.DATA_TYPE_SIZE);
            lastModified = jsonObject.getLong(ReFileConst.DATA_TYPE_LAST_MODIFIED);
        } else {
            isFile = false;
            lastModified = jsonObject.getLong(ReFileConst.DATA_TYPE_LAST_MODIFIED);
            isIndexSkipped = jsonObject.getBoolean(ReFileConst.DATA_TYPE_IS_SKIPPED);

            if (!singleObj) {
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (isKeyNotMetadata(key)) {
                        Object obj = jsonObject.get(key);
                        list.add(new RemoteFile(this, (JSONObject) obj, basePath + FILE_PATH_SEPARATOR + key, false));
                    }
                }
            }
        }
    }

    public static RemoteFile getSingleRemoteFile(JSONObject object) throws JSONException {
        return new RemoteFile(null, object, object.getString(ReFileConst.DATA_TYPE_PATH), true);
    }

    public boolean isFile() {
        return isFile;
    }

    public boolean isIndexSkipped() {
        return isIndexSkipped;
    }

    public List<RemoteFile> getList() {
        return list;
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getSize() {
        return size;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        String[] pathArrays = path.split(FILE_PATH_SEPARATOR);
        return pathArrays[pathArrays.length - 1];
    }

    public RemoteFile getParent() {
        return parent;
    }

    public boolean hasPermissionInfo() {
        return permission != ReFileConst.PERMISSION_UNKNOWN;
    }

    public boolean canRead() {
        return (permission & ReFileConst.PERMISSION_READABLE) == ReFileConst.PERMISSION_READABLE;
    }

    public boolean canWrite() {
        return (permission & ReFileConst.PERMISSION_WRITABLE) == ReFileConst.PERMISSION_WRITABLE;
    }

    public boolean canExecute() {
        return (permission & ReFileConst.PERMISSION_EXECUTABLE) == ReFileConst.PERMISSION_EXECUTABLE;
    }

    @Override
    public int compareTo(RemoteFile o) {
        return o.getName().compareTo(this.getName());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return (obj instanceof RemoteFile) && ((RemoteFile) obj).getPath().equals(this.getPath());
    }

    public boolean isChildOf(RemoteFile parent, boolean recursive) {
        if(recursive) {
            return (this.getPath() + FILE_PATH_SEPARATOR).startsWith(parent.getPath() + FILE_PATH_SEPARATOR);
        } else if(getParent() != null) {
            return this.getParent().equals(parent);
        } else {
            return getPath().replace(getName(), "").equals(parent.getPath() + FILE_PATH_SEPARATOR);
        }
    }

    public RemoteFile getSerializeOptimized() {
        RemoteFile tmp = this;
        list.clear();
        parent = null;
        return tmp;
    }

    public static boolean isKeyNotMetadata(String key) {
        if(metaKeys.isEmpty()) {
            metaKeys.add(ReFileConst.DATA_TYPE_IS_FILE);
            metaKeys.add(ReFileConst.DATA_TYPE_LAST_MODIFIED);
            metaKeys.add(ReFileConst.DATA_TYPE_IS_SKIPPED);
            metaKeys.add(ReFileConst.DATA_TYPE_SIZE);
            metaKeys.add(ReFileConst.DATA_TYPE_PERMISSION);
        }
        return !metaKeys.contains(key);
    }
}
