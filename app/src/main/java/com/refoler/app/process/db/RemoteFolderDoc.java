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
}
