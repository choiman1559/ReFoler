package com.refoler.app.process.db;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RemoteFileDoc implements Serializable {
    long size;
    long lastModified;
    boolean isFile;
    int permission;

    public RemoteFileDoc(File baseFile) {
        this.lastModified = baseFile.lastModified();
        this.size = baseFile.length();
        this.isFile = true;
        this.permission = RemoteFolderDoc.getPermissions(baseFile);
    }

    public long getSize() {
        return size;
    }

    @SuppressWarnings("unused")
    public long getLastModified() {
        return lastModified;
    }

    public boolean isFile() {
        return isFile;
    }

    public Map<String, Object> getList() {
        Map<String, Object> map = new HashMap<>();
        map.put(ReFileConst.DATA_TYPE_LAST_MODIFIED, lastModified);
        map.put(ReFileConst.DATA_TYPE_IS_FILE, isFile);
        map.put(ReFileConst.DATA_TYPE_SIZE, size);
        map.put(ReFileConst.DATA_TYPE_PERMISSION, permission);
        return map;
    }
}
