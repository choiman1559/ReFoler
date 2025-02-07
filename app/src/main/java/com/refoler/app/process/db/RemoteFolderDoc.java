package com.refoler.app.process.db;

import android.util.Log;

import com.refoler.app.BuildConfig;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RemoteFolderDoc implements Serializable {
    Map<String, Object> lists;

    public RemoteFolderDoc(int indexMaximumSize, boolean indexHiddenFiles, File baseFolder) {
        lists = new HashMap<>();
        if(baseFolder.canRead()) {
            lists.put(ReFileConst.DATA_TYPE_IS_FILE, false);
            lists.put(ReFileConst.DATA_TYPE_LAST_MODIFIED, baseFolder.lastModified());
            lists.put(ReFileConst.DATA_TYPE_PERMISSION, getPermissions(baseFolder));

            if(BuildConfig.DEBUG) Log.d("Added File", "Added:" + baseFolder);
            File[] fileList = baseFolder.listFiles();

            if (fileList != null) {
                boolean isIndexSkipped = fileList.length > indexMaximumSize;
                if(isIndexSkipped) {
                    lists.put(ReFileConst.DATA_TYPE_IS_SKIPPED, true);
                } else {
                    lists.put(ReFileConst.DATA_TYPE_IS_SKIPPED, false);
                    for (File files : fileList) {
                        if (files.canRead() && (indexHiddenFiles || !files.getName().startsWith("."))) {
                            if (files.isDirectory()) {
                                lists.put(files.getName(), new RemoteFolderDoc(indexMaximumSize, indexHiddenFiles, files).getLists());
                            } else {
                                lists.put(files.getName(), new RemoteFileDoc(files).getList());
                            }
                        }
                    }
                }
            }
        }
    }

    public Map<String, Object> getLists() {
        return lists;
    }

    public static int getPermissions(File baseFile) {
        int permission = ReFileConst.PERMISSION_NONE;
        if (baseFile.canRead()) {
            permission |= ReFileConst.PERMISSION_READABLE;
        }
        if (baseFile.canWrite()) {
            permission |= ReFileConst.PERMISSION_WRITABLE;
        }
        if (baseFile.canExecute()) {
            permission |= ReFileConst.PERMISSION_EXECUTABLE;
        }
        return permission;
    }
}
